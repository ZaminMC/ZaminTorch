package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.enchantment.Enchantments;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.potion.PotionSystem;
import net.zaminmc.torch.server.potion.StatusEffectInstance;
import net.zaminmc.torch.server.potion.StatusEffects;
import net.zaminmc.torch.server.entity.projectile.ProjectileEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WAVE EK — THE MOB BODIES' EQUIPMENT-ENCHANT CONSULTS (the census's family
 * pick, the standing "the equipment model carries no enchant store" row
 * retired): the reference's own damage walks over the mob bodies' five-slot
 * equipment rows, each law read 1:1 from reference/1.8.8:
 *
 * <ul>
 *   <li>the VICTIM's armor + enchant walk (LivingEntity.applyDamage lines
 *       909-930 over getDamageAfterArmor 865-880 + getDamageAfterEffectsAnd
 *       Enchantments 882-907): the armor envelope, the bypass family's skip,
 *       the Resistance halving and the Protection EPF consult — the walk the
 *       engine's mob bodies NEVER ran (the armor family landed as a read
 *       surface alone; a zombie spawned in full iron paid every wound whole);</li>
 *   <li>the FIRE-RESISTANCE refusal (takeDamage line 645): a fire-resistant
 *       body refuses the fire family whole, before any walk runs;</li>
 *   <li>the MELEE RIDERS (MonsterEntity.tryDamage lines 78-107): the held
 *       weapon's damage family, the Knockback impulse along the attacker's
 *       own yaw with the 60% recoil, and the Fire Aspect ignite — at both the
 *       mob-vs-mob and the mob-vs-player sites (the player site never carried
 *       the base knockback or the hurt window either);</li>
 *   <li>the ZOMBIE'S BURNING IGNITE (ZombieEntity.tryDamage lines 251-258);</li>
 *   <li>the SKELETON'S BOW RIDERS (doRangedAttack lines 252-271): POWER's
 *       {@code i * 0.5 + 0.5}, PUNCH's arrow level, FLAME's — or the wither
 *       variant's own — hundred-second flight burn.</li>
 * </ul>
 */
class EquipmentEnchantParityTest {

    @TempDir
    Path dataDir;

    // ------------------------------------------------------------------
    // The unit rig (the MobManagerTest harness's shape)
    // ------------------------------------------------------------------

    private static MobEntity.WorldQuery worldAt() {
        return new MobEntity.WorldQuery() {
            @Override public boolean isSolid(double x, double y, double z) {
                return y < 4.0;
            }

            @Override public Position nearestPlayer(double x, double y, double z, double range) {
                return null;
            }
        };
    }

    /** Surface at y=4 everywhere (the population rolls land on it). */
    private static final java.util.function.BiFunction<Integer, Integer, Integer>
            SURFACE = (x, z) -> 4;

    private static final class Loot implements MobManager.LootSink {
        final List<ItemStack> drops = new java.util.ArrayList<>();

        @Override public void spawnLootDrop(Position position, ItemStack stack, int dimension) {
            drops.add(stack);
        }
    }

    /**
     * The minimal potion harness (PotionSystem.Bodies over the rig's mob
     * list): the effect store keys on the engine id, the damage and heal
     * arms answer the mob manager's own walks, and the fire-resistance /
     * undead consults read the body's kind.
     */
    private static final class RigBodies implements PotionSystem.Bodies {
        final List<MobEntity> mobs = new java.util.ArrayList<>();

        @Override public List<Object> bodiesIn(double minX, double minY, double minZ,
                double maxX, double maxY, double maxZ, int dimension) {
            return List.of();
        }

        @Override public double squaredDistanceTo(Object body, double x, double y, double z) {
            Position p = ((MobEntity) body).position();
            double dx = p.x() - x;
            double dy = p.y() - y;
            double dz = p.z() - z;
            return dx * dx + dy * dy + dz * dz;
        }

        @Override public void hurt(Object body, float amount, String cause,
                Object attacker, boolean magic) {
        }

        @Override public void heal(Object body, float amount) {
        }

        @Override public void exhaust(Object body, float amount) {
        }

        @Override public void feed(Object body, int food, float saturation) {
        }

        @Override public float healthOf(Object body) {
            return ((MobEntity) body).health();
        }

        @Override public boolean fireResistant(Object body) {
            return ((MobEntity) body).fireImmune();
        }

        @Override public boolean undead(Object body) {
            return ((MobEntity) body).type().damageCategory() == 1;
        }

        @Override public boolean isPlayer(Object body) {
            return false;
        }

        @Override public int keyOf(Object body) {
            return ((MobEntity) body).entityId();
        }
    }

    private static final class Rig {
        final MobManager manager;
        final PotionSystem potions;
        final RigBodies bodies;

        Rig(long seed) {
            Loot loot = new Loot();
            manager = new MobManager(worldAt(), new Random(seed), loot,
                    1_100_000, SURFACE);
            bodies = new RigBodies();
            potions = new PotionSystem(bodies);
            manager.setPotionSystem(potions);
        }

        MobEntity spawn(MobType type, Position at) {
            MobEntity mob = manager.spawnAt(type, at);
            bodies.mobs.add(mob);
            return mob;
        }
    }

    private static ItemStack ironFull(int slot) {
        return switch (slot) {
            case 4 -> ItemStack.of(BuiltinItems.IRON_HELMET, 1);
            case 3 -> ItemStack.of(BuiltinItems.IRON_CHESTPLATE, 1);
            case 2 -> ItemStack.of(BuiltinItems.IRON_LEGGINGS, 1);
            default -> ItemStack.of(BuiltinItems.IRON_BOOTS, 1);
        };
    }

    // ------------------------------------------------------------------
    // The victim's armor + enchant walk
    // ------------------------------------------------------------------

    @Test
    void theArmoredVictimPaysTheArmorEnvelope() {
        Rig rig = new Rig(21L);
        MobEntity zombie = rig.spawn(MobType.ZOMBIE, new Position(0.5, 4.0, 0.5));
        // The full iron row (helmet 2 + chest 6 + legs 5 + boots 2 = 15
        // points): a 10.0 melee wound pays 10 * (25 - 15) / 25 = 4.0 —
        // getDamageAfterArmor lines 868-877.
        for (int slot = 1; slot <= 4; slot++) {
            zombie.setEquipment(slot, ironFull(slot));
        }
        assertTrue(rig.manager.hurt(zombie, 10.0f, 0.0,
                net.zaminmc.torch.event.DamageCause.MELEE, false),
                "the fresh wound lands");
        assertEquals(MobType.ZOMBIE.maxHealth - 4.0f, zombie.health(), 1e-4f,
                "the full iron row halves-plus the wound (10 -> 4.0)");
    }

    @Test
    void theBypassFamilySkipsTheArmorEnvelope() {
        Rig rig = new Rig(22L);
        MobEntity zombie = rig.spawn(MobType.ZOMBIE, new Position(0.5, 4.0, 0.5));
        for (int slot = 1; slot <= 4; slot++) {
            zombie.setEquipment(slot, ironFull(slot));
        }
        // FALL is setBypassesArmor (DamageSource line 22): the envelope
        // never runs — getDamageAfterArmor's own gate (line 869).
        assertTrue(rig.manager.hurt(zombie, 10.0f, 0.0,
                net.zaminmc.torch.event.DamageCause.FALL, false),
                "the fall lands");
        assertEquals(MobType.ZOMBIE.maxHealth - 10.0f, zombie.health(), 1e-4f,
                "the bypass family pays whole through the iron row");
    }

    @Test
    void theProtectionRowFoldsTheEnchantmentEnvelope() {
        Rig rig = new Rig(23L);
        MobEntity zombie = rig.spawn(MobType.ZOMBIE, new Position(0.5, 4.0, 0.5));
        // ONE Protection IV helmet (2 armor points + EPF floor((6+16)/3 *
        // 0.75) = 5): the envelope pays 10 * 23/25 = 9.2, then the EPF roll
        // answers 3 + nextInt(3) in [3, 5] — the wound lands in
        // [9.2 * 20/25, 9.2 * 22/25] = [7.36, 8.096].
        zombie.setEquipment(4, ItemStack.of(BuiltinItems.IRON_HELMET, 1)
                .withEnchantment(Enchantments.PROTECTION.id, 4));
        assertTrue(rig.manager.hurt(zombie, 10.0f, 0.0,
                net.zaminmc.torch.event.DamageCause.MELEE, false),
                "the fresh wound lands");
        float loss = MobType.ZOMBIE.maxHealth - zombie.health();
        assertTrue(loss >= 7.36f && loss <= 8.096f,
                "the Protection IV fold lands in the EPF band (saw " + loss + ")");
    }

    @Test
    void theExcessArmPaysTheArmoredDifference() {
        Rig rig = new Rig(24L);
        MobEntity zombie = rig.spawn(MobType.ZOMBIE, new Position(0.5, 4.0, 0.5));
        for (int slot = 1; slot <= 4; slot++) {
            zombie.setEquipment(slot, ironFull(slot));
        }
        // The window's first hit (5.0) pays its armored 2.0; the stronger
        // second hit inside the window (10.0) pays the EXCESS (5.0) through
        // the same applyDamage — armored 2.0 again (takeDamage lines
        // 660-662 over applyDamage's own halves).
        assertTrue(rig.manager.hurt(zombie, 5.0f, 0.0,
                net.zaminmc.torch.event.DamageCause.MELEE, false));
        assertTrue(rig.manager.hurt(zombie, 10.0f, 0.0,
                net.zaminmc.torch.event.DamageCause.MELEE, false));
        assertEquals(MobType.ZOMBIE.maxHealth - 4.0f, zombie.health(), 1e-4f,
                "the excess arm reduces through the armor envelope (2.0 + 2.0)");
    }

    @Test
    void theFireResistantBodyRefusesTheFireFamily() {
        Rig rig = new Rig(25L);
        MobEntity zombie = rig.spawn(MobType.ZOMBIE, new Position(0.5, 4.0, 0.5));
        rig.potions.add(zombie, new StatusEffectInstance(
                StatusEffects.FIRE_RESISTANCE, 20 * 60, 0));
        // takeDamage line 645: the fire family NEVER lands on a
        // fire-resistant body — the gate sits before any walk.
        assertFalse(rig.manager.hurt(zombie, 5.0f, 0.0,
                net.zaminmc.torch.event.DamageCause.LAVA, false),
                "the lava refuses the fire-resistant body");
        assertFalse(rig.manager.hurt(zombie, 5.0f, 0.0,
                net.zaminmc.torch.event.DamageCause.IN_FIRE, false),
                "the in-fire cell refuses too");
        assertEquals(MobType.ZOMBIE.maxHealth, zombie.health(), 1e-4f,
                "the fire family paid nothing");
        // The ordinary family still lands (the gate is fire-only).
        assertTrue(rig.manager.hurt(zombie, 3.0f, 0.0,
                net.zaminmc.torch.event.DamageCause.MELEE, false),
                "the melee still lands");
        assertTrue(zombie.health() < MobType.ZOMBIE.maxHealth,
                "the ordinary wound paid");
    }

    @Test
    void theResistanceEffectHalvesTheWound() {
        Rig rig = new Rig(26L);
        MobEntity zombie = rig.spawn(MobType.ZOMBIE, new Position(0.5, 4.0, 0.5));
        // Resistance I (amplifier 0): (0 + 1) * 5 = 5 points — the wound
        // halves through (25 - 5) / 25 = 0.8 (lines 888-893).
        rig.potions.add(zombie, new StatusEffectInstance(
                StatusEffects.RESISTANCE, 20 * 60, 0));
        assertTrue(rig.manager.hurt(zombie, 10.0f, 0.0,
                net.zaminmc.torch.event.DamageCause.MELEE, false));
        assertEquals(MobType.ZOMBIE.maxHealth - 8.0f, zombie.health(), 1e-4f,
                "the Resistance I body pays 80% of the wound (10 -> 8)");
    }

    // ------------------------------------------------------------------
    // The melee riders (the mob-vs-mob site)
    // ------------------------------------------------------------------

    @Test
    void theSharpnessSwordSinksTheMobMelee() {
        Rig rig = new Rig(27L);
        MobEntity attacker = rig.spawn(MobType.ZOMBIE, new Position(0.5, 4.0, 0.5));
        MobEntity victim = rig.spawn(MobType.PIG, new Position(2.5, 4.0, 0.5));
        // Sharpness V: 5 * 1.25 = 6.25 extra (DamageEnchantment's TARGET_ALL
        // row) — tryDamage lines 87-90's {@code f += modifyDamage(...)}.
        attacker.setHeldEquipment(ItemStack.of(BuiltinItems.IRON_SWORD, 1)
                .withEnchantment(Enchantments.SHARPNESS.id, 5));
        assertTrue(rig.manager.hurtByMob(attacker, victim, 3.0f));
        assertEquals(MobType.PIG.maxHealth - 9.25f, victim.health(), 1e-4f,
                "the Sharpness V sword sinks the swing (3.0 + 6.25)");
    }

    @Test
    void theSmiteSwordRefusesTheLivingVictim() {
        Rig rig = new Rig(28L);
        MobEntity attacker = rig.spawn(MobType.ZOMBIE, new Position(0.5, 4.0, 0.5));
        MobEntity victim = rig.spawn(MobType.PIG, new Position(2.5, 4.0, 0.5));
        // Smite V (12.5 on the undead) never lands on a DEFAULT-category
        // body — extraDamage's own subtype ladder (lines 205-211).
        attacker.setHeldEquipment(ItemStack.of(BuiltinItems.IRON_SWORD, 1)
                .withEnchantment(Enchantments.SMITE.id, 5));
        assertTrue(rig.manager.hurtByMob(attacker, victim, 3.0f));
        assertEquals(MobType.PIG.maxHealth - 3.0f, victim.health(), 1e-4f,
                "the Smite row refuses the living victim (3.0 flat)");
        // The same sword against an UNDEAD victim pays the full 15.5.
        MobEntity zombieVictim = rig.spawn(MobType.ZOMBIE, new Position(4.5, 4.0, 0.5));
        assertTrue(rig.manager.hurtByMob(attacker, zombieVictim, 3.0f));
        assertEquals(MobType.ZOMBIE.maxHealth - 15.5f, zombieVictim.health(), 1e-4f,
                "the Smite V row bites the undead victim (3.0 + 12.5)");
    }

    @Test
    void theKnockbackSwordThrowsTheVictimAlongTheYaw() {
        Rig rig = new Rig(29L);
        MobEntity attacker = rig.spawn(MobType.ZOMBIE, new Position(0.5, 4.0, 0.5));
        MobEntity baseline = rig.spawn(MobType.PIG, new Position(2.5, 4.0, 0.5));
        attacker.setBodyYaw(0.0f);
        // The baseline swing (no enchant): the walk's own knockbackFrom
        // alone.
        assertTrue(rig.manager.hurtByMob(attacker, baseline, 3.0f));
        double baseVx = baseline.velocityX();
        double baseVy = baseline.velocityY();
        double baseVz = baseline.velocityZ();

        // The Knockback II swing: the extra impulse ADDS
        // (-sin(0) * 2 * 0.5, 0.1, cos(0) * 2 * 0.5) = (0, +0.1, +1.0)
        // along the ATTACKER'S OWN yaw (tryDamage lines 96-102), and the
        // attacker's own horizontal motion decays to 60%.
        MobEntity thrown = rig.spawn(MobType.PIG, new Position(2.5, 4.0, 0.5));
        attacker.setHeldEquipment(ItemStack.of(BuiltinItems.IRON_SWORD, 1)
                .withEnchantment(Enchantments.KNOCKBACK.id, 2));
        attacker.setVelocity(1.0, 0.0, 1.0);
        assertTrue(rig.manager.hurtByMob(attacker, thrown, 3.0f));
        assertEquals(baseVx, thrown.velocityX(), 1e-9,
                "the yaw-0 impulse carries no x component");
        assertEquals(baseVy + 0.1, thrown.velocityY(), 1e-9,
                "the enchant's own 0.1 rise lands on top of the base knock");
        assertEquals(baseVz + 1.0, thrown.velocityZ(), 1e-9,
                "the Knockback II impulse throws one block along the yaw");
        assertEquals(0.6, attacker.velocityX(), 1e-9,
                "the attacker's horizontal motion decays to 60% (x)");
        assertEquals(0.6, attacker.velocityZ(), 1e-9,
                "the attacker's horizontal motion decays to 60% (z)");
    }

    @Test
    void theFireAspectSwordIgnitesTheVictim() {
        Rig rig = new Rig(30L);
        MobEntity attacker = rig.spawn(MobType.ZOMBIE, new Position(0.5, 4.0, 0.5));
        MobEntity victim = rig.spawn(MobType.PIG, new Position(2.5, 4.0, 0.5));
        // Fire Aspect II: setOnFireFor(2 * 4) = 8 seconds = 160 ticks
        // (tryDamage lines 104-107 over Entity.setOnFireFor lines 365-371 —
        // an unarmored victim carries no Fire Protection shortening).
        attacker.setHeldEquipment(ItemStack.of(BuiltinItems.IRON_SWORD, 1)
                .withEnchantment(Enchantments.FIRE_ASPECT.id, 2));
        assertTrue(rig.manager.hurtByMob(attacker, victim, 3.0f));
        assertEquals(160, victim.fireTicks(),
                "the Fire Aspect II sword ignites the victim for 160 ticks");
    }

    @Test
    void theFlatOverrideKindsNeverRideTheSword() {
        Rig rig = new Rig(31L);
        MobEntity attacker = rig.spawn(MobType.WOLF, new Position(0.5, 4.0, 0.5));
        MobEntity victim = rig.spawn(MobType.PIG, new Position(2.5, 4.0, 0.5));
        // WolfEntity.tryDamage (lines 274-281) never calls the super: the
        // flat attribute wound, whatever the held stack carries.
        attacker.setHeldEquipment(ItemStack.of(BuiltinItems.IRON_SWORD, 1)
                .withEnchantment(Enchantments.SHARPNESS.id, 5)
                .withEnchantment(Enchantments.FIRE_ASPECT.id, 2));
        assertTrue(rig.manager.hurtByMob(attacker, victim, 3.0f));
        assertEquals(MobType.PIG.maxHealth - 3.0f, victim.health(), 1e-4f,
                "the wolf's flat swing never rides the sword's rows");
        assertEquals(0, victim.fireTicks(), "no ignite either");
    }

    // ------------------------------------------------------------------
    // The integration seats (the MobCombatAcceptanceTest harness's shape)
    // ------------------------------------------------------------------

    /** The current test's engine — the tick clock the shared await reads. */
    private EngineServer activeServer;

    @Test
    void theZombiesSwingRidesItsSwordAndKnocksThePlayerBack() throws Exception {
        EngineServer server = boot();
        try {
            PlayerSession victim = join(server, "Victim");
            server.chatService().submitChat(victim, "/time set night");
            await(() -> server.world().timeOfDay() >= MobManager.NIGHT_START,
                    "night fell");
            server.chatService().submitChat(victim, "/spawnmob zombie 1");
            MobEntity zombie = awaitMob(server, MobType.ZOMBIE);
            // The Sharpness V sword: the swing carries the rider's +6.25 on
            // top of the easy-difficulty 2.0 base (MonsterEntity.tryDamage
            // lines 87-90) — the wound the retired walk never paid.
            zombie.setHeldEquipment(ItemStack.of(BuiltinItems.IRON_SWORD, 1)
                    .withEnchantment(Enchantments.SHARPNESS.id, 5));
            standNextTo(server, victim, zombie);
            await(() -> victim.health() <= PlayerSession.MAX_HEALTH - 8.25f,
                    "the Sharpness V swing paid its 8.25 (easy base 2.0 + 6.25)");
            // THE MELEE KNOCKBACK (takeDamage lines 695-712 + applyKnockback
            // 778-793): the recipe's +0.4 vertical rise (capped) — the
            // retired walk paid the wound without lifting the body an inch
            // (the mob-player push family moves the horizontal axes alone).
            assertTrue(victim.motionY() > 0.2,
                    "the swing's knockback lifted the player (motionY="
                            + victim.motionY() + ")");
            // THE HURT WINDOW (takeDamage lines 652-669): the landed hit
            // arms the 10-tick invulnerability frame.
            assertTrue(victim.hurtInvulnerable(),
                    "the landed swing armed the hurt window");
            server.shutdown(null);
        } finally {
            activeServer = null;
        }
    }

    @Test
    void theSkeletonsBowRidersLandOnTheArrow() throws Exception {
        EngineServer server = boot();
        try {
            PlayerSession target = join(server, "Target");
            server.chatService().submitChat(target, "/time set night");
            await(() -> server.world().timeOfDay() >= MobManager.NIGHT_START,
                    "night fell");
            server.chatService().submitChat(target, "/spawnmob skeleton 1");
            MobEntity skeleton = awaitMob(server, MobType.SKELETON);
            // The fully-enchanted bow: POWER V (+3.0), PUNCH II (the arrow's
            // own knockback level), FLAME II (the hundred-second flight).
            skeleton.setHeldEquipment(ItemStack.of(BuiltinItems.BOW, 1)
                    .withEnchantment(Enchantments.POWER.id, 5)
                    .withEnchantment(Enchantments.PUNCH.id, 2)
                    .withEnchantment(Enchantments.FLAME.id, 2));
            // Stand ten blocks off (inside the 15-block stand band, far
            // enough the arrow flies a few ticks before arrival).
            Position at = skeleton.position();
            server.teleportPlayer(target, new Position(at.x(), at.y(), at.z() + 10.0));
            await(() -> server.projectiles().all().stream()
                            .anyMatch(p -> p.kind() == ProjectileEntity.Kind.ARROW
                                    && p.throwerId() == skeleton.entityId()),
                    "the skeleton loosed its arrow");
            Optional<ProjectileEntity> arrow = server.projectiles().all().stream()
                    .filter(p -> p.kind() == ProjectileEntity.Kind.ARROW
                            && p.throwerId() == skeleton.entityId())
                    .findFirst();
            assertTrue(arrow.isPresent(), "the arrow still flies");
            // POWER V: the field + 5 * 0.5 + 0.5 = +3.0 — a full-charge
            // loose (2.0 * 1.0 + 0.22) answers 5.22; the close form stays
            // above the unenchanted pair by exactly 3.0.
            double field = arrow.get().damageOverride();
            assertTrue(field >= 0.42d + 3.0d,
                    "the POWER V rider adds its 3.0 on top of the field (saw "
                            + field + ")");
            // PUNCH II: the arrow's own knockback level (line 263's
            // setPunchLevel(j)).
            assertEquals(2, arrow.get().punchLevel(),
                    "the PUNCH II rider armed the arrow's knockback level");
            // FLAME II: the hundred-second flight burn (line 268's
            // setOnFireFor(100) = 2000 ticks).
            assertEquals(2_000, arrow.get().fireTicks(),
                    "the FLAME rider ignited the arrow's flight");
            server.shutdown(null);
        } finally {
            activeServer = null;
        }
    }

    @Test
    void theWitherSkeletonsArrowsBurnWhateverTheBowCarries() throws Exception {
        EngineServer server = boot();
        try {
            PlayerSession target = join(server, "Target");
            server.chatService().submitChat(target, "/time set night");
            await(() -> server.world().timeOfDay() >= MobManager.NIGHT_START,
                    "night fell");
            server.chatService().submitChat(target, "/spawnmob skeleton 1");
            MobEntity wither = awaitMob(server, MobType.SKELETON);
            // The wither variant (the /summon vocabulary carries no
            // wither_skeleton row — the variant stamp is the engine's own
            // test-side write, the WitherSkeletonTest harness's shape).
            server.ticker().submit(() -> wither.setSkeletonWither(true));
            await(() -> wither.skeletonWither(), "the wither variant stamped");
            // The PLAIN bow (no FLAME): the variant's own fire arm burns the
            // arrow anyway (doRangedAttack line 268's
            // {@code getType() == 1} disjunction).
            wither.setHeldEquipment(ItemStack.of(BuiltinItems.BOW, 1));
            Position at = wither.position();
            server.teleportPlayer(target, new Position(at.x(), at.y(), at.z() + 10.0));
            await(() -> server.projectiles().all().stream()
                            .anyMatch(p -> p.kind() == ProjectileEntity.Kind.ARROW
                                    && p.throwerId() == wither.entityId()),
                    "the wither skeleton loosed its arrow");
            Optional<ProjectileEntity> arrow = server.projectiles().all().stream()
                    .filter(p -> p.kind() == ProjectileEntity.Kind.ARROW
                            && p.throwerId() == wither.entityId())
                    .findFirst();
            assertTrue(arrow.isPresent(), "the arrow still flies");
            assertEquals(2_000, arrow.get().fireTicks(),
                    "the wither variant's arrows burn with no FLAME on the bow");
            server.shutdown(null);
        } finally {
            activeServer = null;
        }
    }

    // ------------------------------------------------------------------ harness

    private MobEntity awaitMob(EngineServer server, MobType type) throws InterruptedException {
        await(() -> server.mobs().all().stream().anyMatch(m -> m.type() == type),
                type + " present");
        Optional<MobEntity> found = server.mobs().all().stream()
                .filter(m -> m.type() == type).findFirst();
        return found.orElseThrow();
    }

    private void standNextTo(EngineServer server, PlayerSession player, MobEntity mob) {
        Position at = mob.position();
        server.teleportPlayer(player, new Position(at.x(), at.y(), at.z() + 0.8));
    }

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "ekeench", "it", 20, 2, 20,
                dataDir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        activeServer = server;
        return server;
    }

    private PlayerSession join(EngineServer server, String name) throws InterruptedException {
        var accepted = server.joinRequest(link(), name, UUID.randomUUID());
        PlayerSession session = ((EngineBridge.Accepted) accepted).session();
        await(() -> server.playerRegistry().byName(name).isPresent(), name + " registered");
        server.joinCompleted(session);
        server.ticker().submit(() -> session.setOpLevel(2));
        await(() -> session.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                name + " playing");
        return session;
    }

    private ClientLink link() {
        return new ClientLink() {
            @Override public boolean isActive() {
                return true;
            }

            @Override public void kick(String reason) {
            }
        };
    }

    private void await(BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }
}
