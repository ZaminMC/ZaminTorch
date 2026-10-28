package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.util.Position;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The flight families' contact arms (the 93/D2 ledger's standing tail,
 * landed): the reference carries NO family skip — Entity.tick lines
 * 337-341's lava arm, Entity.move's fire contact + the water douse,
 * LivingEntity.baseTick lines 195-197's suffocation and the
 * block-collision cactus walk run for every body whatever owns its
 * movement. The engine's early-return branches (ghast, dragon, bat,
 * squid, guardian) skipped them all — a bat or a squid could stand in
 * lava unwounded, a ghast drifted over lava lakes with the air's 0.91
 * drag. This wave hoists the contact block above the branches and
 * splits the squid's and the guardian's swim gates to WATER alone
 * (their own isInWater overrides — a body in LAVA is not swimming).
 */
class FlightFamiliesContactTest {

    /**
     * The controllable world: solid below {@code floorY} (or everywhere
     * when {@code allSolid} — the buried-body shape), water at or below
     * {@code waterY}, lava at or below {@code lavaY} (the two fluid
     * lines independent), and the scripted fire/cactus cells.
     */
    private static final class StubWorld implements MobEntity.WorldQuery {
        double floorY = 0.0;
        double waterY = Double.NEGATIVE_INFINITY;
        double lavaY = Double.NEGATIVE_INFINITY;
        boolean fire;
        boolean cactus;
        boolean allSolid;

        @Override
        public boolean isSolid(double x, double y, double z) {
            return allSolid || y < floorY;
        }

        @Override
        public double supportY(double x, double y, double z) {
            return floorY;
        }

        @Override
        public boolean inFluid(double x, double y, double z) {
            return y <= waterY || y <= lavaY;
        }

        @Override
        public boolean inLava(double x, double y, double z) {
            return y <= lavaY;
        }

        @Override
        public boolean inFire(double x, double y, double z) {
            return fire;
        }

        @Override
        public boolean touchingCactus(double x, double y, double z) {
            return cactus;
        }

        @Override
        public Position nearestPlayer(double x, double y, double z, double range) {
            return null;
        }

        @Override
        public boolean clearLine(Position from, Position to) {
            // The blocked line: the ghast's steering bursts never land (the
            // branch's own else — no velocity write), so the drag-band pins
            // read the drag ALONE. No other consult in this class reads it.
            return false;
        }
    }

    private static final double LAVA_LINE = 62.0;

    private static MobEntity batIn(StubWorld world, long seed) {
        return new MobEntity(980, MobType.BAT, new Position(0.5, 60.0, 0.5),
                new Random(seed), world);
    }

    @Test
    void theBatInLavaWoundsAndBurns() {
        // takeLavaDamage for the flight family: the fifteen-second arm
        // holds the burn at 300 while submerged, and the 4.0 wounds land
        // every 10 ticks — the 6-health bat dies inside three windows.
        StubWorld world = new StubWorld();
        world.lavaY = LAVA_LINE;
        MobEntity bat = batIn(world, 11L);
        for (int i = 0; i < 10; i++) {
            bat.tick(false);
        }
        assertEquals(MobEntity.LAVA_BURN_TICKS, bat.fireTicks());
        assertEquals(6.0f - MobEntity.LAVA_DAMAGE_PER_WOUND, bat.health(), 1e-6f);
        for (int i = 0; i < 15; i++) {
            bat.tick(false);
        }
        assertTrue(bat.dead(), "the lava kills the flight body");
    }

    @Test
    void theSquidInLavaTakesTheStrandedFallAndTheContact() {
        // SquidEntity's own isInWater override gates the stroke on WATER
        // alone: in LAVA the else arm owns the velocity (the stranded
        // fall — x/z zeroed, the reference's -0.08 gravity), never the
        // stroke, while the contact block wounds and burns.
        StubWorld world = new StubWorld();
        world.lavaY = LAVA_LINE;
        MobEntity squid = new MobEntity(970, MobType.SQUID, new Position(0.5, 60.0, 0.5),
                new Random(13L), world);
        squid.tick(false);
        assertEquals(0.0, squid.velocityX(), 1e-9, "the stranded fall zeroes x");
        assertEquals(0.0, squid.velocityZ(), 1e-9, "the stranded fall zeroes z");
        assertEquals((0.0 - 0.08) * 0.98, squid.velocityY(), 1e-9,
                "the stranded fall's own gravity, never the stroke");
        for (int i = 0; i < 9; i++) {
            squid.tick(false);
        }
        assertEquals(MobEntity.LAVA_BURN_TICKS, squid.fireTicks());
        assertEquals(10.0f - MobEntity.LAVA_DAMAGE_PER_WOUND, squid.health(), 1e-6f);
    }

    @Test
    void theGuardianInLavaFallsToTheLandBodyAndTheContact() {
        // GuardianEntity.moveRelative gates the swim on isInWater; a
        // guardian in LAVA falls to the land body (the reference's
        // super.moveRelative fallback — the standard fall, not the swim's
        // decay), while the contact block wounds and burns it.
        StubWorld world = new StubWorld();
        world.lavaY = LAVA_LINE;
        MobEntity guardian = new MobEntity(681, MobType.GUARDIAN, new Position(0.5, 60.0, 0.5),
                new Random(17L), world);
        guardian.tick(false);
        assertEquals((0.0 - MobEntity.GRAVITY_PER_TICK) * 0.98, guardian.velocityY(), 1e-9,
                "the land body's fall, never the swim");
        for (int i = 0; i < 9; i++) {
            guardian.tick(false);
        }
        assertEquals(MobEntity.LAVA_BURN_TICKS, guardian.fireTicks());
        assertEquals(30.0f - MobEntity.LAVA_DAMAGE_PER_WOUND, guardian.health(), 1e-6f);
    }

    @Test
    void theGhastTakesTheFluidDragBandsButNeverWounds() {
        // FlyingMobEntity.moveRelative's drag arms (lines 19-49): the
        // fluid consult picks the band — water *0.8, lava *0.5, the air
        // 0.91 — over all three axes, with no gravity in any. The ghast
        // is fire-immune: the lava contact never wounds or ignites it.
        StubWorld lava = new StubWorld();
        lava.lavaY = LAVA_LINE;
        MobEntity ghastInLava = new MobEntity(7, MobType.GHAST, new Position(1.5, 60.0, 2.5),
                new Random(11L), lava);
        ghastInLava.setVelocity(1.0, 1.0, 1.0);
        ghastInLava.tick(false);
        assertEquals(0.5, ghastInLava.velocityX(), 1e-9, "the lava's *0.5");
        assertEquals(0.5, ghastInLava.velocityY(), 1e-9);
        assertEquals(0.5, ghastInLava.velocityZ(), 1e-9);

        StubWorld water = new StubWorld();
        water.waterY = LAVA_LINE;
        MobEntity ghastInWater = new MobEntity(7, MobType.GHAST, new Position(1.5, 60.0, 2.5),
                new Random(11L), water);
        ghastInWater.setVelocity(1.0, 1.0, 1.0);
        ghastInWater.tick(false);
        assertEquals(0.8, ghastInWater.velocityX(), 1e-9, "the water's *0.8");
        assertEquals(0.8, ghastInWater.velocityY(), 1e-9);
        assertEquals(0.8, ghastInWater.velocityZ(), 1e-9);

        StubWorld air = new StubWorld();
        MobEntity ghastInAir = new MobEntity(7, MobType.GHAST, new Position(1.5, 60.0, 2.5),
                new Random(11L), air);
        ghastInAir.setVelocity(1.0, 1.0, 1.0);
        ghastInAir.tick(false);
        assertEquals(0.91, ghastInAir.velocityX(), 1e-9, "the air's 0.91");
        assertEquals(0.91, ghastInAir.velocityY(), 1e-9);
        assertEquals(0.91, ghastInAir.velocityZ(), 1e-9);

        // The immune ghast in the lava: thirty submerged ticks, no wound,
        // no burn (takeLavaDamage's own gate).
        for (int i = 0; i < 30; i++) {
            ghastInLava.tick(false);
        }
        assertEquals(10.0f, ghastInLava.health(), 1e-6f);
        assertEquals(0, ghastInLava.fireTicks());
    }

    @Test
    void theDragonInLavaNeverWoundsOrBurns() {
        // The noClip dragon rides the contact block like every body, its
        // own immunity exempting the fire/lava arms.
        StubWorld world = new StubWorld();
        world.lavaY = LAVA_LINE;
        MobEntity dragon = new MobEntity(63, MobType.ENDER_DRAGON, new Position(0.5, 60.0, 0.5),
                new Random(19L), world);
        for (int i = 0; i < 30; i++) {
            dragon.tick(false);
        }
        assertEquals(200.0f, dragon.health(), 1e-6f);
        assertEquals(0, dragon.fireTicks());
    }

    @Test
    void theBatInFireIgnitesAndTheBurnClockWounds() {
        // Entity.move's fire contact for the flight family: the standing
        // fire ignites (the 8-second arm), and the burn clock's 1.0
        // residual lands on the per-second rhythm.
        StubWorld world = new StubWorld();
        world.fire = true;
        MobEntity bat = batIn(world, 23L);
        bat.tick(false);
        assertEquals(MobEntity.FIRE_TICKS, bat.fireTicks());
        for (int i = 0; i < 25; i++) {
            bat.tick(false);
        }
        assertEquals(MobEntity.FIRE_TICKS, bat.fireTicks(), "the contact re-arms the burn");
        assertEquals(6.0f - MobEntity.FIRE_DAMAGE_PER_SECOND, bat.health(), 1e-6f);
    }

    @Test
    void theBurningBatDousesInWater() {
        // Entity.move's douse for the flight family: a burning body
        // entering the water extinguishes (the WATER consult alone — a
        // body in lava keeps its burn, pinned by LavaWalkTest).
        StubWorld world = new StubWorld();
        world.waterY = LAVA_LINE;
        MobEntity bat = batIn(world, 29L);
        bat.ignite(MobEntity.FIRE_TICKS);
        assertTrue(bat.fireTicks() > 0);
        bat.tick(false);
        assertEquals(0, bat.fireTicks(), "the water douses the burn");
    }

    @Test
    void theBuriedBatSuffocates() {
        // LivingEntity.baseTick's IN_WALL arm for the flight family: the
        // eight-point eye survey inside solid, the 1.0 bypass-armor wound
        // at the hurt-i-frame cadence.
        StubWorld world = new StubWorld();
        world.allSolid = true;
        MobEntity bat = batIn(world, 31L);
        for (int i = 0; i < 10; i++) {
            bat.tick(false);
        }
        assertEquals(6.0f - MobEntity.IN_WALL_DAMAGE_PER_WOUND, bat.health(), 1e-6f);
    }

    @Test
    void theCactusPricksTheFlightFamily() {
        // The block-collision cactus walk for the flight family: the 1.0
        // prick at the hurt-i-frame rhythm.
        StubWorld world = new StubWorld();
        world.cactus = true;
        MobEntity bat = batIn(world, 37L);
        for (int i = 0; i < 10; i++) {
            bat.tick(false);
        }
        assertEquals(6.0f - 1.0f, bat.health(), 1e-6f);
    }
}
