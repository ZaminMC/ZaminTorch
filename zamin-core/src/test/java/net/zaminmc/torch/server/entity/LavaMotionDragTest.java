package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The lava motion drag (Slice 93/F1 — LivingEntity.travel's lava arm,
 * lines 1202-1214): the harder drag triple ({@code v *= 0.5} per
 * component) plus the weak gravity ({@code v -= 0.02} on the vertical) —
 * the GEOMETRIC transient (a plunging body keeps most of its speed the
 * first submerged tick and decelerates over ~5, not the water clamp's
 * instant -0.04) and the horizontal crawl (the *0.5 damping vs the
 * water's 0.8).
 */
class LavaMotionDragTest {

    /**
     * The controllable world: solid below {@code floorY}, lava at or
     * below {@code lavaY}, water at or below {@code waterY} (independent
     * lines). The bodies sit at y=59 with the lines at 60 — the mid-band
     * consult (y + 0.2) reads them submerged.
     */
    private static final class StubWorld implements MobEntity.WorldQuery {
        final double floorY;
        final double lavaY;
        final double waterY;

        StubWorld(double floorY, double lavaY, double waterY) {
            this.floorY = floorY;
            this.lavaY = lavaY;
            this.waterY = waterY;
        }

        @Override public boolean isSolid(double x, double y, double z) { return y < floorY; }
        @Override public double supportY(double x, double y, double z) { return floorY; }
        @Override public boolean inFluid(double x, double y, double z) {
            return y <= lavaY || y <= waterY;
        }
        @Override public boolean inLava(double x, double y, double z) { return y <= lavaY; }
        @Override public Position nearestPlayer(double x, double y, double z, double range) {
            return null;
        }
    }

    private static final double DEEP = -1000.0;
    private static final Position SUBMERGED = new Position(1.5, 59.0, 2.5);

    private static MobEntity body(MobType type, StubWorld world) {
        return new MobEntity(7, type, SUBMERGED, new Random(11), world);
    }

    @Test
    void thePlungeKeepsItsSpeedTheFirstSubmergedTick() {
        // A body ARRIVING at -0.5 (a plunge from height): the first
        // submerged tick keeps most of the speed — the swim goal's +0.05
        // then the drag (v = (v + 0.05) * 0.5 - 0.02 = -0.245) — the
        // geometric transient, NOT the water clamp's instant -0.04 (the
        // reference's move-then-drag order over ~5 ticks; the engine's
        // one-tick-phase note rides the ledger).
        StubWorld lavaWorld = new StubWorld(DEEP, 60.0, DEEP);
        MobEntity inLava = body(MobType.COW, lavaWorld);
        inLava.addVelocity(0.0, -0.5, 0.0);
        double before = inLava.position().y();
        inLava.tick(false);
        double dy = inLava.position().y() - before;
        assertTrue(dy < -0.20,
                "the plunge keeps most of its speed the first tick: dy=" + dy);
        assertTrue(dy > -0.30,
                "the drag is clearly active (the air fall would be -0.53): dy=" + dy);
        // The water contrast: the clamp bites immediately (-0.04).
        StubWorld waterWorld = new StubWorld(DEEP, DEEP, 60.0);
        MobEntity inWater = body(MobType.COW, waterWorld);
        inWater.addVelocity(0.0, -0.5, 0.0);
        double waterBefore = inWater.position().y();
        inWater.tick(false);
        double waterDy = inWater.position().y() - waterBefore;
        assertTrue(waterDy > -0.10,
                "the water clamp bites immediately: dy=" + waterDy);
        assertTrue(waterDy > dy,
                "the lava's first tick sinks faster than the water's");
    }

    @Test
    void theSinkSettlesTowardTheDragFixedPoint() {
        // The interplay's fixed point: the swim goal's +0.05 input against
        // the drag settles NEAR ZERO (the bobbing equilibrium, the
        // reference's own jumpInLava-vs-drag shape) — never the deep sink
        // of an unswimming body, never a runaway rise.
        StubWorld world = new StubWorld(DEEP, 60.0, DEEP);
        MobEntity mob = body(MobType.COW, world);
        mob.addVelocity(0.0, -0.5, 0.0);
        for (int i = 0; i < 40; i++) {
            mob.tick(false);
        }
        double settled = mob.velocityY();
        assertTrue(settled > -0.03 && settled < 0.05,
                "the swim-vs-drag equilibrium (near zero, the slow bob): " + settled);
    }

    @Test
    void theHorizontalCrawlIsHarderThanTheWaterDrag() {
        // A body with horizontal motion in the lava loses half of it per
        // tick (the water's 0.8 keeps far more).
        StubWorld lavaWorld = new StubWorld(DEEP, 60.0, DEEP);
        MobEntity inLava = body(MobType.COW, lavaWorld);
        inLava.addVelocity(0.5, 0.0, 0.0);
        inLava.tick(false);
        assertEquals(0.25, inLava.velocityX(), 1e-9, "the *0.5 lava drag");

        StubWorld waterWorld = new StubWorld(DEEP, DEEP, 60.0);
        MobEntity inWater = body(MobType.COW, waterWorld);
        inWater.addVelocity(0.5, 0.0, 0.0);
        inWater.tick(false);
        assertEquals(0.4, inWater.velocityX(), 1e-9, "the water's 0.8 contrast");
    }

    @Test
    void theFireImmuneBodyStillRidesTheSameDrag() {
        // The motion split is the BODY's own model — the fire-immune
        // zombie pigman (no movement quirks of its own, unlike the
        // hopping magma cube or the hover-dampened blaze) rides the same
        // drag in its element.
        StubWorld world = new StubWorld(DEEP, 60.0, DEEP);
        MobEntity pigman = body(MobType.ZOMBIE_PIGMAN, world);
        pigman.addVelocity(0.0, -0.5, 0.0);
        double before = pigman.position().y();
        pigman.tick(false);
        double dy = pigman.position().y() - before;
        assertTrue(dy < -0.20 && dy > -0.30,
                "the immune body rides the same transient: dy=" + dy);
        float health = pigman.health();
        for (int i = 0; i < 30; i++) {
            pigman.tick(false);
        }
        assertEquals(health, pigman.health(), 1e-6f, "and takes no wound");
    }
}
