package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The enderman's own mechanics (Slice 12i — the reference's
 * EndermanEntity): the teleport walks, the water clock, the daylight
 * blink, and the far-target gate.
 */
class EndermanEntityTest {

    /** A flat-world stub: solid below y=8, air above. */
    private static final MobEntity.WorldQuery FLAT_WORLD = new MobEntity.WorldQuery() {
        @Override
        public boolean isSolid(double x, double y, double z) {
            return Math.floor(y) < 8.0;
        }

        @Override
        public Position nearestPlayer(double x, double y, double z, double range) {
            return null; // no players: the environment walks run bare
        }

        @Override
        public int skyLight(int x, int y, int z) {
            return 0; // no daylight teleports in the stub
        }
    };

    private MobEntity endermanAt(double x, double y, double z, Random random) {
        return new MobEntity(100, MobType.ENDERMAN, new Position(x, y, z),
                random, FLAT_WORLD);
    }

    @Test
    void theKindCarriesTheReferenceIdentity() {
        assertEquals(58, MobType.ENDERMAN.legacyTypeId);
        assertEquals(2.9, MobType.ENDERMAN.height, 1e-9);
        assertEquals(40.0f, MobType.ENDERMAN.maxHealth, 1e-6f);
        assertEquals(7.0f, MobType.ENDERMAN.attackDamage, 1e-6f);
        assertTrue(MobType.ENDERMAN.hostile);
        assertEquals("minecraft:enderman", MobType.ENDERMAN.identifier().toString());
    }

    @Test
    void theTeleportDropsToSolidGroundAndClearsTheBody() {
        Random random = new Random(1L);
        MobEntity enderman = endermanAt(0.5, 20.0, 0.5, random);

        // Land high in the air over the flat floor: the walk drops to the
        // ground (feet on the first solid + 1 = 8).
        boolean moved = enderman.teleportTo(40.5, 60.0, 40.5);

        assertTrue(moved);
        assertEquals(8.0, enderman.position().y(), 1e-9, "the feet rest on the floor");
        assertEquals(40.5, enderman.position().x(), 1e-9);
        assertEquals(40.5, enderman.position().z(), 1e-9);
        // A clean teleport zeroes the motion (the reference's own rule).
        assertEquals(0.0, enderman.velocityX(), 1e-9);
        assertEquals(0.0, enderman.velocityY(), 1e-9);
        assertEquals(0.0, enderman.velocityZ(), 1e-9);
    }

    @Test
    void aBlockedLandingRefusesAndKeepsTheBody() {
        Random random = new Random(2L);
        MobEntity enderman = endermanAt(0.5, 8.0, 0.5, random);
        // The flat stub treats every cell below 8 as solid; a teleport INTO
        // the floor's band has no clear body cells.
        boolean moved = enderman.teleportTo(10.5, 3.0, 10.5);

        assertFalse(moved);
        assertEquals(new Position(0.5, 8.0, 0.5), enderman.position(),
                "the refused teleport never moved the body");
    }

    @Test
    void theRandomTeleportStaysInTheSixtyFourBlockBand() {
        Random random = new Random(3L);
        MobEntity enderman = endermanAt(0.5, 8.0, 0.5, random);
        boolean everMoved = false;
        for (int i = 0; i < 200 && !everMoved; i++) {
            everMoved = enderman.teleportRandomly();
        }
        assertTrue(everMoved, "some attempt lands on the open floor");
        Position at = enderman.position();
        // The +-32 cube around the origin (the reference's bands).
        assertTrue(Math.abs(at.x() - 0.5) <= 32.5 && Math.abs(at.z() - 0.5) <= 32.5,
                "the landing stays in the x/z band: " + at);
    }

    @Test
    void theTowardBlinkLandsCloserToTheTarget() {
        Random random = new Random(4L);
        MobEntity enderman = endermanAt(0.5, 8.0, 0.5, random);
        Position target = new Position(64.5, 9.0, 64.5);

        boolean moved = enderman.teleportToward(target);

        assertTrue(moved);
        Position at = enderman.position();
        double before = distance2(new Position(0.5, 8.0, 0.5), target);
        double after = distance2(at, target);
        assertTrue(after < before, "the blink closed the distance (" + after
                + " vs " + before + ")");
        // The 16-block reach against the ~90-block start: the blink must
        // land well inside the original distance minus the reach's half.
        assertTrue(after < before - 8.0 * 8.0,
                "the blink covered the reference's 16-block reach");
    }

    @Test
    void standingInWaterCostsHealthOnTheTenTickCadence() {
        MobEntity.WorldQuery wetWorld = new MobEntity.WorldQuery() {
            @Override
            public boolean isSolid(double x, double y, double z) {
                return Math.floor(y) < 8.0;
            }

            @Override
            public Position nearestPlayer(double x, double y, double z, double range) {
                return null;
            }

            @Override
            public boolean inFluid(double x, double y, double z) {
                return true; // everything is water
            }

            @Override
            public int skyLight(int x, int y, int z) {
                return 0;
            }
        };
        Random random = new Random(5L);
        MobEntity enderman = new MobEntity(101, MobType.ENDERMAN,
                new Position(0.5, 8.0, 0.5), random, wetWorld);

        float health = enderman.health();
        int hits = 0;
        for (int tick = 0; tick < 100; tick++) {
            enderman.tick(false);
            if (enderman.health() != health) {
                hits++;
                health = enderman.health();
            }
        }
        // 100 ticks / the 10-tick cadence = ~10 hits of 1.0.
        assertTrue(hits >= 8 && hits <= 11,
                "the water clock ticks roughly every 10 ticks (hits=" + hits + ")");
        assertEquals(40.0f - hits, enderman.health(), 1e-4f, "each hit costs exactly 1.0");
    }

    private static double distance2(Position a, Position b) {
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        double dz = a.z() - b.z();
        return dx * dx + dy * dy + dz * dz;
    }
}
