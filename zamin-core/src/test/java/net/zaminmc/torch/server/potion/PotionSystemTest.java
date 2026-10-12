package net.zaminmc.torch.server.potion;

import net.zaminmc.torch.World;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The potion system (Slice 11b — the reference port of
 * entity/living/effect/{PotionHelper, StatusEffect, StatusEffectInstance} +
 * PotionItem + PotionEntity): the 1.8.8 damage-value model over the brewing
 * flags (the recipe tables evaluated exactly as the reference's own flag
 * machine), the per-body effect stores with the vanilla merge, the
 * cadenced periodic application (regeneration every 50 >> amplifier, poison
 * one short of dead, wither freely), the instant family's smite-aware
 * swing, and the splash's proximity-scaled area walk.
 */
class PotionSystemTest {

    // The 1.8.8 damage values (the brewing walk's own arithmetic):
    // water 0; the drinkable carrier bit 13 (8192); regen = flag 0 +
    // carrier; speed = flag 1; poison = flag 2; weakness = flag 3;
    // amplifier II = flag 5 (32); extended = flag 6 (64); splash bit 14.
    private static final int WATER = 0;
    private static final int REGEN_I = 8193;
    private static final int REGEN_II = 8225;
    private static final int REGEN_EXTENDED = 8257;
    private static final int SPEED_I = 8194;
    private static final int POISON_I = 8196;
    private static final int POISON_SPLASH = 24580;
    private static final int WEAKNESS_I = 8200;
    private static final int INSTANT_HEALTH_I = 8197;
    private static final int INSTANT_HEALTH_II = 8229;

    // ------------------------------------------------------------------
    // The metadata -> effect set (PotionHelper.getStatusEffects 1:1)
    // ------------------------------------------------------------------

    @Test
    void theWaterBottleCarriesNoEffects() {
        assertTrue(PotionHelper.getStatusEffects(WATER).isEmpty(),
                "the water bottle's metadata 0 names no effect set");
    }

    @Test
    void theRegenerationPotionDerivesItsVanillaDuration() {
        List<StatusEffectInstance> set = PotionHelper.getStatusEffects(REGEN_I);
        assertEquals(1, set.size(), "the regen metadata names exactly regeneration");
        StatusEffectInstance regen = set.get(0);
        assertEquals(StatusEffects.REGENERATION, regen.id());
        assertEquals(0, regen.amplifier(), "the base brew is level I");
        // 1200 * (1*3 + 0*2) = 3600, the 0.25 duration multiplier -> 900
        // (the historical 0:45 regeneration).
        assertEquals(900, regen.duration());
        assertFalse(regen.splash(), "the plain bottle is the drinkable");
    }

    @Test
    void theAmplifierIIRightShiftsTheDuration() {
        StatusEffectInstance regen = PotionHelper.getStatusEffects(REGEN_II).get(0);
        assertEquals(1, regen.amplifier(), "flag 5 names the amplifier II");
        // 3600 >> 1 = 1800, * 0.25 = 450 (the historical Regeneration II 0:22).
        assertEquals(450, regen.duration());
    }

    @Test
    void theExtendedFlagDoublesTheLevel() {
        StatusEffectInstance regen = PotionHelper.getStatusEffects(REGEN_EXTENDED).get(0);
        assertEquals(0, regen.amplifier(), "the extended brew stays level I");
        // level 2: 1200 * (2*3 + 1*2) = 9600, * 0.25 = 2400 (2:00).
        assertEquals(2400, regen.duration());
    }

    @Test
    void theSplashShavesAQuarterAndFlagsTheInstance() {
        StatusEffectInstance poison = PotionHelper.getStatusEffects(POISON_SPLASH).get(0);
        assertTrue(poison.splash(), "the splash bit marks the instance");
        // 3600 * 0.25 = 900, then round(900 * 0.75 + 0.5) = 676.
        assertEquals(676, poison.duration());
        assertTrue(PotionHelper.isSplashPotion(POISON_SPLASH));
        assertFalse(PotionHelper.isSplashPotion(POISON_I));
    }

    @Test
    void theSpeedAndWeaknessBrewsMatchTheirVanillaClocks() {
        StatusEffectInstance speed = PotionHelper.getStatusEffects(SPEED_I).get(0);
        assertEquals(StatusEffects.SPEED, speed.id());
        assertEquals(3600, speed.duration(), "the 3:00 speed (the plain 1.0 multiplier)");
        StatusEffectInstance weakness = PotionHelper.getStatusEffects(WEAKNESS_I).get(0);
        assertEquals(StatusEffects.WEAKNESS, weakness.id());
        // the harmful 0.5 multiplier: 1200 * 3 * 0.5 = 1800 (1:30).
        assertEquals(1800, weakness.duration());
    }

    @Test
    void theInstantFamilyCarriesTheUnitDuration() {
        StatusEffectInstance heal = PotionHelper.getStatusEffects(INSTANT_HEALTH_I).get(0);
        assertEquals(StatusEffects.INSTANT_HEALTH, heal.id());
        assertEquals(1, heal.duration(), "the instant's duration is the unit");
        assertTrue(StatusEffects.isInstant(heal.id()));
        // The splash bit on an instant keeps the unit duration.
        StatusEffectInstance splashHeal = PotionHelper.getStatusEffects(
                INSTANT_HEALTH_I | PotionHelper.SPLASH_BIT).get(0);
        assertEquals(1, splashHeal.duration());
    }

    @Test
    void thePotionColorAveragesTheEffectSet() {
        assertEquals(3694022, PotionHelper.getColor(WATER),
                "the water bottle's blue-grey");
        // A single effect's color passes through the average unchanged.
        List<StatusEffectInstance> regen = PotionHelper.getStatusEffects(REGEN_I);
        assertEquals(StatusEffects.colorOf(StatusEffects.REGENERATION),
                PotionHelper.getColor(regen));
    }

    // ------------------------------------------------------------------
    // The effect stores (LivingEntity.addStatusEffect 1:1)
    // ------------------------------------------------------------------

    @Test
    void theStoreMergesTheVanillaWay() {
        Bodies bodies = new Bodies();
        PotionSystem system = new PotionSystem(bodies);
        PlayerSession player = bodies.spawn("Drinker", false, 0, 0, 0);
        system.add(player, new StatusEffectInstance(StatusEffects.REGENERATION, 900, 0));
        // The equal amplifier keeps the longer duration.
        system.add(player, new StatusEffectInstance(StatusEffects.REGENERATION, 500, 0));
        assertEquals(900, system.get(player, StatusEffects.REGENERATION).duration());
        // The strictly stronger amplifier replaces (duration reset).
        system.add(player, new StatusEffectInstance(StatusEffects.REGENERATION, 450, 1));
        assertEquals(1, system.get(player, StatusEffects.REGENERATION).amplifier());
        assertEquals(450, system.get(player, StatusEffects.REGENERATION).duration());
        assertTrue(system.hasEffect(player, StatusEffects.REGENERATION));
        // A different id installs beside it.
        system.add(player, new StatusEffectInstance(StatusEffects.SPEED, 3600, 0));
        assertEquals(2, system.allOf(player).size());
    }

    @Test
    void theUndeadRefuseRegenerationAndPoison() {
        Bodies bodies = new Bodies();
        PotionSystem system = new PotionSystem(bodies);
        PlayerSession zombie = bodies.spawn("Zombie", true, 0, 0, 0);
        system.add(zombie, new StatusEffectInstance(StatusEffects.REGENERATION, 900, 0));
        system.add(zombie, new StatusEffectInstance(StatusEffects.POISON, 900, 0));
        assertNull(system.get(zombie, StatusEffects.REGENERATION),
                "canHaveStatusEffect refuses the undead regen");
        assertNull(system.get(zombie, StatusEffects.POISON),
                "canHaveStatusEffect refuses the undead poison");
        // The rest still lands.
        system.add(zombie, new StatusEffectInstance(StatusEffects.WEAKNESS, 1800, 0));
        assertTrue(system.hasEffect(zombie, StatusEffects.WEAKNESS));
    }

    @Test
    void theExpiryClearsTheStoreAndFiresTheListener() {
        Bodies bodies = new Bodies();
        PotionSystem system = new PotionSystem(bodies);
        List<String> log = new ArrayList<>();
        system.addListener(new PotionSystem.Listener() {
            @Override public void onEffectRemoved(PlayerSession p, StatusEffectInstance e) {
                log.add("removed " + e.id());
            }
        });
        PlayerSession player = bodies.spawn("Ticker", false, 0, 0, 0);
        system.add(player, new StatusEffectInstance(StatusEffects.SPEED, 3, 0));
        system.tick(player);
        system.tick(player);
        assertTrue(system.hasEffect(player, StatusEffects.SPEED));
        system.tick(player);
        assertNull(system.get(player, StatusEffects.SPEED), "the 3-tick effect expired");
        assertEquals(List.of("removed " + StatusEffects.SPEED), log);
    }

    // ------------------------------------------------------------------
    // The periodic application (StatusEffect.apply 1:1)
    // ------------------------------------------------------------------

    @Test
    void regenerationHealsOneEveryFiftyTicks() {
        Bodies bodies = new Bodies();
        PotionSystem system = new PotionSystem(bodies);
        PlayerSession player = bodies.spawn("Healing", false, 0, 0, 0);
        bodies.model(player).health = 10.0f;
        system.add(player, new StatusEffectInstance(StatusEffects.REGENERATION, 900, 0));
        // The reference's countdown clock: 900 % 50 == 0 applies on the
        // very first tick, then the next pulse lands when the countdown
        // reads 850 — the fifty-first walk.
        for (int i = 0; i < 51; i++) {
            system.tick(player);
        }
        assertEquals(10.0f + 2.0f, bodies.model(player).health,
                "two heal pulses over the first fifty-one ticks (900 and 850)");
    }

    @Test
    void poisonStopsOneShortOfDead() {
        Bodies bodies = new Bodies();
        PotionSystem system = new PotionSystem(bodies);
        PlayerSession player = bodies.spawn("Dying", false, 0, 0, 0);
        bodies.model(player).health = 1.0f;
        system.add(player, new StatusEffectInstance(StatusEffects.POISON, 900, 0));
        for (int i = 0; i < 100; i++) {
            system.tick(player);
        }
        assertEquals(1.0f, bodies.model(player).health,
                "the health > 1.0 gate holds the last heart");
        assertEquals(0, bodies.model(player).hurts.size(),
                "no damage tick ever fired at the floor");
        // Above the floor the magic damage lands one heart at a time.
        bodies.model(player).health = 3.0f;
        system.tick(player); // 800 % 25 == 0 -> the pulse
        assertEquals(2.0f, bodies.model(player).health);
    }

    @Test
    void witherHurtsFreelyAndHungerExhausts() {
        Bodies bodies = new Bodies();
        PotionSystem system = new PotionSystem(bodies);
        PlayerSession player = bodies.spawn("Withering", false, 0, 0, 0);
        bodies.model(player).health = 1.0f;
        system.add(player, new StatusEffectInstance(StatusEffects.WITHER, 800, 0));
        system.tick(player); // 800 % 40 == 0
        assertEquals(0.0f, bodies.model(player).health,
                "wither crosses the poison floor");
        assertEquals("withered away", bodies.model(player).hurts.get(0));
        system.add(player, new StatusEffectInstance(StatusEffects.HUNGER, 40, 1));
        system.tick(player);
        assertEquals(0.05f, bodies.model(player).exhaustion,
                "the hunger effect's 0.025 * (amplifier + 1)");
    }

    @Test
    void theSixHundredTickResyncFires() {
        Bodies bodies = new Bodies();
        PotionSystem system = new PotionSystem(bodies);
        List<String> log = new ArrayList<>();
        system.addListener(new PotionSystem.Listener() {
            @Override public void onEffectResynced(PlayerSession p, StatusEffectInstance e) {
                log.add("resync " + e.id() + " at " + e.duration());
            }
        });
        PlayerSession player = bodies.spawn("Synced", false, 0, 0, 0);
        system.add(player, new StatusEffectInstance(StatusEffects.SPEED, 3600, 0));
        system.tick(player); // 3599 % 600 != 0
        assertTrue(log.isEmpty());
        for (int i = 0; i < 599; i++) {
            system.tick(player);
        }
        assertEquals(List.of("resync " + StatusEffects.SPEED + " at 3000"),
                log, "the % 600 arm re-sent the packet at 3600 - 600");
    }

    // ------------------------------------------------------------------
    // The instant family (StatusEffect.affectHealth 1:1)
    // ------------------------------------------------------------------

    @Test
    void instantHealthHealsTheLivingAndSmitesTheUndead() {
        Bodies bodies = new Bodies();
        PotionSystem system = new PotionSystem(bodies);
        PlayerSession player = bodies.spawn("Hurt", false, 0, 0, 0);
        bodies.model(player).health = 6.0f;
        system.applyInstant(player, StatusEffects.INSTANT_HEALTH, 0, 1.0);
        assertEquals(10.0f, bodies.model(player).health,
                "4 << 0 at full proximity");
        PlayerSession zombie = bodies.spawn("Smitten", true, 0, 0, 0);
        bodies.model(zombie).health = 20.0f;
        system.applyInstant(zombie, StatusEffects.INSTANT_HEALTH, 0, 1.0);
        assertEquals(14.0f, bodies.model(zombie).health,
                "the undead take the 6 << amplifier mirror");
        // The level II at half proximity: (int)(0.5 * 8 + 0.5) = 4.
        bodies.model(player).health = 6.0f;
        system.applyInstant(player, StatusEffects.INSTANT_HEALTH, 1, 0.5);
        assertEquals(10.0f, bodies.model(player).health);
        // Instant damage mirrors the pair.
        system.applyInstant(player, StatusEffects.INSTANT_DAMAGE, 0, 1.0);
        assertEquals(4.0f, bodies.model(player).health,
                "6 << 0 magic on the living");
        bodies.model(zombie).health = 10.0f;
        system.applyInstant(zombie, StatusEffects.INSTANT_DAMAGE, 0, 1.0);
        assertEquals(14.0f, bodies.model(zombie).health,
                "the undead heal from the harm brew");
    }

    @Test
    void theDrinkAppliesTheSetAndTheInstantAtFullStrength() {
        Bodies bodies = new Bodies();
        PotionSystem system = new PotionSystem(bodies);
        PlayerSession player = bodies.spawn("Drinker", false, 0, 0, 0);
        bodies.model(player).health = 10.0f;
        // The drink of the instant health II: 4 << 1 = 8 at proximity 1.0.
        system.finishDrinking(player, INSTANT_HEALTH_II);
        assertEquals(18.0f, bodies.model(player).health,
                "the drink heals 8 (the fake body's 20 cap)");
        // The drink of the timed brew installs the store.
        system.finishDrinking(player, REGEN_I);
        assertTrue(system.hasEffect(player, StatusEffects.REGENERATION));
        assertEquals(900, system.get(player, StatusEffects.REGENERATION).duration());
        // The water bottle drinks down to nothing.
        bodies.model(player).hurts.clear();
        system.finishDrinking(player, WATER);
        assertFalse(system.hasEffect(player, StatusEffects.SPEED),
                "the water added no effect");
    }

    // ------------------------------------------------------------------
    // The splash (PotionEntity.onCollision 1:1)
    // ------------------------------------------------------------------

    @Test
    void theSplashScalesByProximityAndTheDirectHitAnswersOne() {
        Bodies bodies = new Bodies();
        PotionSystem system = new PotionSystem(bodies);
        PlayerSession direct = bodies.spawn("Direct", false, 2.0, 0, 0);
        bodies.model(direct).health = 10.0f;
        PlayerSession far = bodies.spawn("Far", false, 3.9, 0, 0);
        bodies.model(far).health = 10.0f;
        PlayerSession outside = bodies.spawn("Outside", false, 4.2, 0, 0);
        bodies.model(outside).health = 10.0f;
        system.splash(0.0, 0.0, 0.0, INSTANT_HEALTH_I, direct, null);
        assertEquals(14.0f, bodies.model(direct).health,
                "the direct hit's 1.0 proximity heals 4");
        // 1 - 3.9/4 = 0.025 -> (int)(0.025 * 4 + 0.5) = 0.
        assertEquals(10.0f, bodies.model(far).health,
                "the falloff under a heart heals nothing");
        assertEquals(10.0f, bodies.model(outside).health,
                "the 4.2 body never entered the box");
    }

    @Test
    void theSplashInstallsTheTimedSetAtTheScaledDuration() {
        Bodies bodies = new Bodies();
        PotionSystem system = new PotionSystem(bodies);
        PlayerSession player = bodies.spawn("Soaked", false, 2.0, 0, 0);
        // At distance 2: proximity 0.5 -> (int)(0.5 * 900 + 0.5) = 450.
        system.splash(0.0, 0.0, 0.0, POISON_I, null, null);
        StatusEffectInstance poison = system.get(player, StatusEffects.POISON);
        assertEquals(450, poison.duration(), "the proximity-scaled duration installs");
        assertEquals(0, poison.amplifier());
    }

    @Test
    void theWaterSplashWetsNothing() {
        Bodies bodies = new Bodies();
        PotionSystem system = new PotionSystem(bodies);
        PlayerSession player = bodies.spawn("Dry", false, 0, 0, 0);
        system.splash(0.0, 0.0, 0.0, WATER, player, null);
        assertTrue(system.allOf(player).isEmpty());
        assertEquals(20.0f, bodies.model(player).health);
    }

    @Test
    void theSplashSkipsTheTinyDuration() {
        Bodies bodies = new Bodies();
        PotionSystem system = new PotionSystem(bodies);
        // Weakness at distance ~3.94: proximity 0.015 -> duration
        // (int)(0.015 * 1800 + 0.5) = 27 — installs (27 > 20). At distance
        // 3.98: proximity 0.005 -> 9 — skipped (9 <= 20).
        PlayerSession near = bodies.spawn("Near", false, 3.94, 0, 0);
        PlayerSession far = bodies.spawn("Far", false, 3.98, 0, 0);
        system.splash(0.0, 0.0, 0.0, WEAKNESS_I, null, null);
        assertTrue(system.hasEffect(near, StatusEffects.WEAKNESS));
        assertFalse(system.hasEffect(far, StatusEffects.WEAKNESS),
                "the 20-tick floor skips the whisper-level exposure");
    }

    // ------------------------------------------------------------------
    // The brewing step (applyIngredient 1:1 — the brewing stand's surface)
    // ------------------------------------------------------------------

    @Test
    void theIngredientsWalkTheFlags() {
        // The sugar on plain water: the &4 requires the wart's flag the
        // water lacks -> the metadata zeroes, but the walk continues and
        // +13 still sets the carrier -> 8192, the mundane potion (the
        // reference's own zero-out-then-continue arithmetic).
        assertEquals(8192, PotionHelper.applyIngredient(WATER, PotionHelper.SUGAR),
                "the sugar on water brews the mundane");
        // The sugar on the awkward (the wart's flag 4 set): +1 names speed,
        // the &4-4 takes the wart's flag back, +13 keeps the drinkable
        // carrier.
        assertEquals(8194, PotionHelper.applyIngredient(16, PotionHelper.SUGAR),
                "the sugar on the awkward names speed");
        // The redstone on the speed: -5+6-7 -> the extended flag.
        assertEquals(8194 | 64, PotionHelper.applyIngredient(8194, PotionHelper.REDSTONE));
        // The glowstone: +5-6-7 -> the amplifier II.
        assertEquals(8194 | 32, PotionHelper.applyIngredient(8194, PotionHelper.GLOWSTONE));
        // The gunpowder: +14&13-13 -> the splash bit lands, the drinkable
        // carrier clears -> 16386 (the splash brew carries no bit 13).
        assertEquals(16386, PotionHelper.applyIngredient(8194, PotionHelper.GUNPOWDER));
        // The fermented eye's corruption: "-0+3-4+13" sets flag 3 BESIDE
        // speed's flag 1 — the recipe tables themselves do the swap
        // (slowness = !0 & 1 & !2 & 3 -> 8202, the corruption's own brew).
        assertEquals(8202, PotionHelper.applyIngredient(8194,
                PotionHelper.FERMENTED_SPIDER_EYE),
                "the corruption swaps speed for slowness through the flags");
        // The ingredient's mask keeps the fifteen flags (no splash past 32767).
        assertEquals(0, PotionHelper.applyIngredient(16384, "-14"));
    }

    // ------------------------------------------------------------------
    // The test bodies
    // ------------------------------------------------------------------

    /** A minimal body surface: real sessions, a health model, a hurt log. */
    private static final class Bodies implements PotionSystem.Bodies {
        final List<PlayerSession> all = new ArrayList<>();
        final Map<PlayerSession, Model> models = new HashMap<>();

        PlayerSession spawn(String name, boolean undead, double x, double y, double z) {
            ClientLink link = new ClientLink() {
                @Override public boolean isActive() { return true; }
                @Override public void kick(String reason) { }
            };
            PlayerSession session = new PlayerSession(
                    UUID.nameUUIDFromBytes(name.getBytes()), name, link);
            session.authenticate();
            session.beginJoin(stubWorld(), Position.ZERO);
            session.markPlaying();
            session.applyMovement(new Position(x, y, z), Rotation.ZERO, true);
            all.add(session);
            models.put(session, new Model(undead, x, y, z));
            return session;
        }

        Model model(PlayerSession session) {
            return models.get(session);
        }

        @Override
        public List<Object> bodiesIn(double minX, double minY, double minZ,
                                       double maxX, double maxY, double maxZ) {
            List<Object> found = new ArrayList<>();
            for (PlayerSession session : all) {
                Model model = models.get(session);
                if (model.x >= minX - 0.3 && model.x <= maxX + 0.3
                        && model.z >= minZ - 0.3 && model.z <= maxZ + 0.3
                        && model.y >= minY && model.y <= maxY + 1.8) {
                    found.add(session);
                }
            }
            return found;
        }

        @Override
        public double squaredDistanceTo(Object body, double x, double y, double z) {
            Model at = models.get((PlayerSession) body);
            double dx = at.x - x;
            double dy = at.y - y;
            double dz = at.z - z;
            return dx * dx + dy * dy + dz * dz;
        }

        @Override
        public void hurt(Object body, float amount, String cause, Object attacker,
                         boolean magic) {
            Model target = models.get((PlayerSession) body);
            target.health = Math.max(0.0f, target.health - amount);
            target.hurts.add(cause);
        }

        @Override
        public void heal(Object body, float amount) {
            Model target = models.get((PlayerSession) body);
            target.health = Math.min(20.0f, target.health + amount);
            target.heals++;
        }

        @Override
        public void exhaust(Object body, float amount) {
            models.get((PlayerSession) body).exhaustion += amount;
        }

        @Override
        public void feed(Object body, int food, float saturation) {
            models.get((PlayerSession) body).food += food;
        }

        @Override
        public float healthOf(Object body) {
            return models.get((PlayerSession) body).health;
        }

        @Override
        public boolean fireResistant(Object body) {
            return false;
        }

        @Override
        public boolean undead(Object body) {
            return models.get((PlayerSession) body).undead;
        }

        @Override
        public boolean isPlayer(Object body) {
            return true; // the listeners and the resync arm need it
        }

        @Override
        public int keyOf(Object body) {
            return (PlayerSession) body == all.get(0) ? 1 : all.indexOf(body) + 1;
        }
    }

    /** One living body's model under test. */
    static final class Model {
        final boolean undead;
        final double x;
        final double y;
        final double z;
        float health = 20.0f;
        float exhaustion;
        int food;
        int heals;
        final List<String> hurts = new ArrayList<>();

        Model(boolean undead, double x, double y, double z) {
            this.undead = undead;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    /** Minimal world stub: sessions need a World reference only. */
    private static World stubWorld() {
        return new World() {
            @Override public String name() { return "stub"; }
            @Override public long timeOfDay() { return 0; }
            @Override public long totalTicks() { return 0; }
            @Override public Position spawnPosition() { return Position.ZERO; }
            @Override public net.zaminmc.torch.block.BlockType getBlock(
                    net.zaminmc.torch.block.BlockPosition p) { return null; }
            @Override public boolean setBlock(
                    net.zaminmc.torch.block.BlockPosition p,
                    net.zaminmc.torch.block.BlockType t) { return false; }
            @Override public boolean isChunkLoaded(
                    net.zaminmc.torch.block.ChunkPosition p) { return true; }
            @Override public java.util.Collection<net.zaminmc.torch.entity.LivingEntity> entities() { return java.util.List.of(); }
            @Override public void execute(Runnable work) { work.run(); }

            @Override public void setTimeOfDay(long timeOfDay) { }
            @Override public int blockLightAt(net.zaminmc.torch.block.BlockPosition p) { return 0; }
            @Override public int skyLightAt(net.zaminmc.torch.block.BlockPosition p) { return 15; }
            @Override public String biomeAt(net.zaminmc.torch.block.BlockPosition p) { return "plains"; }

        };
    }
}
