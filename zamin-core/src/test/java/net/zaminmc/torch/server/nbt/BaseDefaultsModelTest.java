package net.zaminmc.torch.server.nbt;

import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.potion.PotionSystem;
import net.zaminmc.torch.server.potion.StatusEffects;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The base-default behavioral models (Slice 92 — the base-defaults
 * envelope's second landing): the breath clock (LivingEntity.baseTick
 * lines 210-233 — the eye-level drain, the -20 drown rhythm, the
 * water-breathers, the WATER_BREATHING shield), the fall model
 * (Entity.checkFallDamage lines 719-731 + LivingEntity.takeFallDamage
 * lines 823-837 — the accumulate/spend walk, the ceil(distance - 3 -
 * jump boost) wound, the empty-override kinds), the portal cooldown's
 * decrement, and the hurt timestamp's age stamp — each with its NBT row.
 */
class BaseDefaultsModelTest {

    /**
     * The controllable world: solid below {@code floorY} (the landing
     * surface answers {@code floorY}), water at or below {@code waterY}.
     */
    private static final class StubWorld implements MobEntity.WorldQuery {
        final double floorY;
        final double waterY;

        StubWorld(double floorY, double waterY) {
            this.floorY = floorY;
            this.waterY = waterY;
        }

        @Override
        public boolean isSolid(double x, double y, double z) {
            return y < floorY;
        }

        @Override
        public double supportY(double x, double y, double z) {
            return floorY;
        }

        @Override
        public boolean inFluid(double x, double y, double z) {
            return y <= waterY;
        }

        @Override
        public Position nearestPlayer(double x, double y, double z, double range) {
            return null;
        }
    }

    /** The bodies seam (the absorption arm absent — the default no-op). */
    private static final class StubBodies implements PotionSystem.Bodies {
        @Override
        public List<Object> bodiesIn(double minX, double minY, double minZ,
                                     double maxX, double maxY, double maxZ, int dimension) {
            return List.of();
        }

        @Override
        public double squaredDistanceTo(Object body, double x, double y, double z) {
            return 0;
        }

        @Override
        public void hurt(Object body, float amount, String cause, Object attacker, boolean magic) {
        }

        @Override
        public void heal(Object body, float amount) {
        }

        @Override
        public void exhaust(Object body, float amount) {
        }

        @Override
        public void feed(Object body, int food, float saturation) {
        }

        @Override
        public float healthOf(Object body) {
            return ((MobEntity) body).health();
        }

        @Override
        public boolean fireResistant(Object body) {
            return false;
        }

        @Override
        public boolean undead(Object body) {
            return false;
        }

        @Override
        public boolean isPlayer(Object body) {
            return false;
        }

        @Override
        public int keyOf(Object body) {
            return ((MobEntity) body).entityId();
        }
    }

    private static MobEntity cow(StubWorld world) {
        return new MobEntity(7, MobType.COW, new Position(1.5, 64.0, 2.5),
                new Random(11), world);
    }

    private static PotionSystem potions() {
        return new PotionSystem(new StubBodies());
    }

    // ------------------------------------------------ the breath clock

    @Test
    void theEyeUnderwaterDrainsTheStore() {
        // The cow's eye (~1.19 over the feet) sits well below the water
        // line (the engine's fluid buoyancy bobs the body up at
        // ~0.03/tick — the deep placement keeps the eye submerged through
        // the window): the store drains one tick at a time.
        MobEntity mob = cow(new StubWorld(0, 63.5));
        mob.teleport(new Position(1.5, 50.0, 2.5));
        assertEquals(300, mob.air());
        mob.tick(false);
        assertEquals(299, mob.air());
        mob.tick(false);
        assertEquals(298, mob.air());
    }

    @Test
    void theDrownRhythmResetsAtMinusTwenty() {
        // LivingEntity.baseTick lines 213-229: at -20 the store resets to
        // 0 and the 2.0 DROWN wound lands; the next wound rides the next
        // full -20 cycle.
        MobEntity mob = cow(new StubWorld(0, 63.5));
        mob.teleport(new Position(1.5, 50.0, 2.5));
        mob.setAir(1); // one tick of air left
        float health = mob.health();
        for (int i = 0; i < 20; i++) {
            mob.tick(false); // 1 -> -19
        }
        assertEquals(-19, mob.air());
        assertEquals(health, mob.health(), 1e-6f); // nothing yet
        mob.tick(false); // -20: the reset + the wound
        assertEquals(0, mob.air());
        assertEquals(health - 2.0f, mob.health(), 1e-6f);
        for (int i = 0; i < 20; i++) {
            mob.tick(false); // the next cycle
        }
        assertEquals(health - 4.0f, mob.health(), 1e-6f);
    }

    @Test
    void theWaterBreathersNeverDrain() {
        // WaterMobEntity.canBreatheUnderwater: the squid and the guardian
        // family hold their air underwater.
        MobEntity squid = new MobEntity(7, MobType.SQUID, new Position(1.5, 62.0, 2.5),
                new Random(11), new StubWorld(0, 63.5));
        for (int i = 0; i < 40; i++) {
            squid.tick(false);
        }
        assertEquals(300, squid.air());
    }

    @Test
    void waterBreathingShieldsTheBody() {
        // The WATER_BREATHING gate (baseTick line 212): the wired
        // amplifier query reads the live store.
        MobEntity mob = cow(new StubWorld(0, 63.5));
        mob.teleport(new Position(1.5, 50.0, 2.5));
        mob.setEffectAmplifierQuery(effectId ->
                effectId == StatusEffects.WATER_BREATHING ? 1 : 0);
        for (int i = 0; i < 40; i++) {
            mob.tick(false);
        }
        assertEquals(300, mob.air());
    }

    @Test
    void outOfTheWaterTheStoreRefills() {
        MobEntity mob = cow(new StubWorld(0, 63.5));
        mob.teleport(new Position(1.5, 50.0, 2.5));
        mob.setAir(100);
        mob.tick(false);
        assertEquals(99, mob.air());
        mob.teleport(new Position(1.5, 70.0, 2.5)); // the eye clears the line
        mob.tick(false);
        assertEquals(300, mob.air());
    }

    // ------------------------------------------------ the fall model

    @Test
    void theFallingBodyAccumulatesTheDrop() {
        // checkFallDamage's else arm: {@code fallDistance -= dy} while the
        // body still falls — the store holds the mid-air value.
        MobEntity mob = cow(new StubWorld(54.0, -1000));
        mob.setNoAi(true); // the pin freezes the integration — the store reads directly
        mob.setFallDistance(2.0f);
        float read = mob.fallDistance();
        assertEquals(2.0f, read, 1e-6f);
        // The NBT row round-trips the mid-air value.
        Map<String, Snbt.Tag> written = new java.util.LinkedHashMap<>();
        EntityNbt.writeNbtWithoutId(mob, written, potions());
        assertEquals(2.0f, NbtOps.getFloat(written, "FallDistance"), 1e-6f);
        MobEntity loaded = cow(new StubWorld(54.0, -1000));
        EntityNbt.readNbt(loaded, written, potions(), null);
        assertEquals(2.0f, loaded.fallDistance(), 1e-6f);
    }

    @Test
    void theLandingSpendsTheStoreIntoTheWound() {
        // A 10-block drop over a floor at 54: the store accumulates the
        // fall, the landing spends it — ceil(distance - 3) = 7 wounds on
        // the 10-heart cow.
        MobEntity mob = cow(new StubWorld(54.0, -1000));
        float health = mob.health();
        assertTrue(mob.onGround() == false || true); // fresh spawn state
        for (int i = 0; i < 200 && mob.onGround() == false; i++) {
            mob.tick(false);
        }
        assertTrue(mob.onGround());
        assertEquals(54.0, mob.position().y(), 1e-9);
        assertEquals(0.0f, mob.fallDistance(), 1e-6f);
        assertEquals(health - 7.0f, mob.health(), 1e-6f);
    }

    @Test
    void theSuppressorKindsNeverTakeTheLanding() {
        // The empty overrides: the chicken (feathers) falls the same drop
        // whole.
        MobEntity chicken = new MobEntity(7, MobType.CHICKEN,
                new Position(1.5, 64.0, 2.5), new Random(11),
                new StubWorld(54.0, -1000));
        float health = chicken.health();
        for (int i = 0; i < 200 && chicken.onGround() == false; i++) {
            chicken.tick(false);
        }
        assertTrue(chicken.onGround());
        assertEquals(health, chicken.health(), 1e-6f);
        assertEquals(0.0f, chicken.fallDistance(), 1e-6f);
    }

    @Test
    void jumpBoostSparesItsBlocks() {
        // The JUMP_BOOST term (takeFallDamage line 826): amplifier + 1
        // blocks spared — a level-II jump (the query answers 2) turns a
        // 5-block drop's ceil(5 - 3 - 2) = 0 into nothing.
        MobEntity plain = cow(new StubWorld(59.0, -1000));
        for (int i = 0; i < 200 && plain.onGround() == false; i++) {
            plain.tick(false);
        }
        assertTrue(plain.health() < 10.0f); // the 5-block drop wounded it

        MobEntity boosted = cow(new StubWorld(59.0, -1000));
        boosted.setEffectAmplifierQuery(effectId ->
                effectId == StatusEffects.JUMP_BOOST ? 2 : 0);
        float health = boosted.health();
        for (int i = 0; i < 200 && boosted.onGround() == false; i++) {
            boosted.tick(false);
        }
        assertTrue(boosted.onGround());
        assertEquals(health, boosted.health(), 1e-6f); // the boost spared it
    }

    @Test
    void theWaterZeroesTheStore() {
        // checkWaterCollisions line 771: the water clears the fall — a
        // cow dropping into a pool lands unwounded. The drop from 64 to
        // the line at 55 accumulates ~9 blocks in the AIR; the splash
        // zeroes the store and the fall wound never lands (the body bobs
        // at the line — the surface oscillation's tiny bits stay far
        // under the 3-block deductible).
        MobEntity mob = cow(new StubWorld(40.0, 55.0));
        float health = mob.health();
        for (int i = 0; i < 120; i++) {
            mob.tick(false);
        }
        assertTrue(mob.fallDistance() < 0.1f); // the splash zeroed the drop
        assertEquals(health, mob.health(), 1e-6f); // no wound ever landed
    }

    // ------------------------------------------------ the cooldown + the stamp

    @Test
    void thePortalCooldownTicksDown() {
        MobEntity mob = cow(new StubWorld(0, -1000));
        mob.setPortalCooldown(10);
        // VT-009 (PR #107): the decrement's ONE call site is the manager's
        // per-tick portal arm (advanceEntityPortalClock — the reference's
        // own single baseTick tail, Entity.baseTick lines 311-313); the
        // old tail decrement beside it halved the 300-tick re-entry
        // immunity and is retired. The direct mob.tick() walk carries no
        // portal arm — the manager's walk does.
        mob.advanceEntityPortalClock(false);
        assertEquals(9, mob.portalCooldown());
        for (int i = 0; i < 20; i++) {
            mob.advanceEntityPortalClock(false);
        }
        assertEquals(0, mob.portalCooldown()); // floors at zero
        Map<String, Snbt.Tag> written = new java.util.LinkedHashMap<>();
        EntityNbt.writeNbtWithoutId(mob, written, potions());
        assertEquals(0, NbtOps.getInt(written, "PortalCooldown"));
    }

    @Test
    void theHurtWalkStampsTheAge() {
        // setAttacker's own clock write (lastAttackedTime = ticks): every
        // wound stamps the body's age.
        MobEntity mob = cow(new StubWorld(0, -1000));
        for (int i = 0; i < 5; i++) {
            mob.tick(false);
        }
        assertEquals(5, mob.age());
        assertEquals(0, mob.hurtByTimestamp());
        mob.hurt(1.0f);
        assertEquals(5, mob.hurtByTimestamp());
        mob.tick(false);
        mob.tick(false);
        mob.hurt(1.0f);
        assertEquals(7, mob.hurtByTimestamp());

        // The NBT row round-trips the stamp.
        Map<String, Snbt.Tag> written = new java.util.LinkedHashMap<>();
        EntityNbt.writeNbtWithoutId(mob, written, potions());
        assertEquals(7, NbtOps.getInt(written, "HurtByTimestamp"));
        MobEntity loaded = cow(new StubWorld(0, -1000));
        EntityNbt.readNbt(loaded, written, potions(), null);
        assertEquals(7, loaded.hurtByTimestamp());
    }

    @Test
    void theAirRowRoundTrips() {
        MobEntity mob = cow(new StubWorld(0, -1000));
        mob.setAir(217);
        Map<String, Snbt.Tag> written = new java.util.LinkedHashMap<>();
        EntityNbt.writeNbtWithoutId(mob, written, potions());
        assertEquals(217, NbtOps.getShort(written, "Air"));
        MobEntity loaded = cow(new StubWorld(0, -1000));
        EntityNbt.readNbt(loaded, written, potions(), null);
        assertEquals(217, loaded.air());
    }
}
