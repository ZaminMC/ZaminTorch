package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The golem pair (Slice 17d — IronGolemEntity + SnowGolemEntity): the
 * kinds' identities, the heavy hit's band, the melts, and the hostile
 * hunt.
 */
class GolemEntityTest {

    /** A flat-world stub with a scriptable hostile neighbor. */
    private static final class ScriptedWorld implements MobEntity.WorldQuery {
        boolean water;
        boolean hot;
        MobEntity hostile;
        double hostileRange = 32.0;

        @Override
        public boolean isSolid(double x, double y, double z) {
            return Math.floor(y) < 8.0;
        }

        @Override
        public boolean inFluid(double x, double y, double z) {
            return water;
        }

        @Override
        public boolean biomeHotAt(double x, double y, double z) {
            return hot;
        }

        @Override
        public Position nearestPlayer(double x, double y, double z, double range) {
            return null;
        }

        @Override
        public MobEntity nearestHostileMob(double x, double y, double z, double range) {
            if (hostile == null || hostile.dead()) {
                return null;
            }
            double dx = hostile.position().x() - x;
            double dy = hostile.position().y() - y;
            double dz = hostile.position().z() - z;
            return dx * dx + dy * dy + dz * dz <= hostileRange * hostileRange
                    ? hostile : null;
        }
    }

    private static MobEntity golemIn(ScriptedWorld world, MobType type, long seed) {
        return new MobEntity(998, type, new Position(0.5, 8.0, 0.5),
                new Random(seed), world);
    }

    @Test
    void theGolemsCarryTheIdentity() {
        // Entities.java lines 281 + 283: the SnowMan 97, the VillagerGolem 99.
        assertEquals(97, MobType.SNOW_GOLEM.legacyTypeId);
        assertEquals(99, MobType.IRON_GOLEM.legacyTypeId);
        assertEquals(0.7, MobType.SNOW_GOLEM.width, 1e-9);
        assertEquals(1.9, MobType.SNOW_GOLEM.height, 1e-9);
        assertEquals(4.0f, MobType.SNOW_GOLEM.maxHealth, 1e-6f);
        assertEquals(1.4, MobType.IRON_GOLEM.width, 1e-9);
        assertEquals(2.9, MobType.IRON_GOLEM.height, 1e-9);
        assertEquals(100.0f, MobType.IRON_GOLEM.maxHealth, 1e-6f);
        // GolemEntity.canDespawn (line 33): never — the passive read agrees.
        assertFalse(MobType.IRON_GOLEM.hostile);
        assertFalse(MobType.SNOW_GOLEM.hostile);
        // GolemEntity's null sound family (lines 21-29); the iron golem's
        // own overrides (lines 212-219).
        assertEquals("", MobType.SNOW_GOLEM.hurtSound);
        assertEquals("mob.irongolem.hit", MobType.IRON_GOLEM.hurtSound);
        assertEquals("mob.irongolem.death", MobType.IRON_GOLEM.deathSound);
        // SnowGolemEntity.getEyeHeight line 97: the flat 1.7F.
        MobEntity snowGolem = golemIn(new ScriptedWorld(), MobType.SNOW_GOLEM, 1L);
        assertEquals(1.7, snowGolem.eyePosition().y() - snowGolem.position().y(), 1e-9);
    }

    @Test
    void theHeavyHitRollsTheReferenceBand() {
        MobEntity golem = golemIn(new ScriptedWorld(), MobType.IRON_GOLEM, 2L);
        // tryDamage line 202: 7 + nextInt(15) — the 7..21 band.
        for (int i = 0; i < 200; i++) {
            float roll = golem.golemDamageRoll();
            assertTrue(roll >= 7.0f && roll <= 21.0f, "the roll left the band: " + roll);
        }
        // The launch (tryDamage line 204): the flat +0.4F.
        assertEquals(0.4, golem.golemLaunchY(), 1e-9);
    }

    @Test
    void theIronGolemHuntsTheHostileKinds() {
        ScriptedWorld world = new ScriptedWorld();
        MobEntity golem = golemIn(world, MobType.IRON_GOLEM, 3L);
        // No hostile: the plain stroll (no chase mode held).
        golem.tick(true);
        assertEquals(MobEntity.Mode.IDLE, golem.mode());

        // A zombie within the 32-block hunt range: the chase owns the walk.
        MobEntity zombie = new MobEntity(999, MobType.ZOMBIE,
                new Position(6.0, 8.0, 0.5), new Random(4L), world);
        world.hostile = zombie;
        for (int i = 0; i < 3; i++) {
            golem.tick(true);
        }
        assertEquals(zombie, golem.golemTarget());
        assertEquals(MobEntity.Mode.CHASE, golem.mode(), "beyond the melee band");
    }

    @Test
    void theSnowGolemVolleysAndMelts() {
        ScriptedWorld world = new ScriptedWorld();
        MobEntity snowGolem = golemIn(world, MobType.SNOW_GOLEM, 5L);
        MobEntity creeper = new MobEntity(999, MobType.CREEPER,
                new Position(4.0, 8.0, 0.5), new Random(6L), world);
        world.hostile = creeper;
        world.hostileRange = 10.0; // the volley's own range
        boolean volleyed = false;
        for (int i = 0; i < 25; i++) {
            snowGolem.tick(true);
            if (snowGolem.consumeSnowGolemShot()) {
                volleyed = true;
            }
        }
        assertTrue(volleyed, "the 20-tick volley fired inside the range");

        // The melts (mobTick lines 43-55): the water contact, the hot biome.
        world.hostile = null;
        world.water = true;
        snowGolem.tick(true);
        assertEquals(3.0f, snowGolem.health(), 1e-6f, "the water melt costs 1");
        world.water = false;
        world.hot = true;
        snowGolem.tick(true);
        assertEquals(2.0f, snowGolem.health(), 1e-6f, "the hot-biome melt costs 1");
        world.hot = false;
        float held = snowGolem.health();
        snowGolem.tick(true);
        assertEquals(held, snowGolem.health(), 1e-6f, "no melt out of the pair");
    }
}
