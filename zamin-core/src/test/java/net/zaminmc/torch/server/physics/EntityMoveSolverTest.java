package net.zaminmc.torch.server.physics;

import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The physics-solver slice's contract, part two — the move itself:
 * the 1:1 port of the reference's {@code Entity.move} (lines 390-585).
 * A body's motion clips box by box, axis by axis (Y, then X, then Z),
 * with the reference's own step-height retry for the walkers — the
 * BODY model the ledger deferred from the collision-shape slice:
 * <ul>
 * <li>a fall onto a floor stops exactly at the ceiling and turns the
 * ground flag on (the downward clip only);</li>
 * <li>a walk into a wall stops exactly at the face and zeroes the
 * matching velocity signal;</li>
 * <li>a walker with step height 0.5 steps ONTO the half slab — the
 * up-and-over retry keeping the full intended horizontal motion;</li>
 * <li>a body straddling the fence's 1.5 band from the cell above
 * lands ON the tall post's ceiling, never through it;</li>
 * <li>a body walks UNDER the top slab and ONTO the stairs' ascending
 * two-box form exactly like the reference's own bodies;</li>
 * <li>the cobweb drag, the sneaking edge-guard, and the no-clip
 * pass-through keep their own laws.</li>
 * </ul>
 */
class EntityMoveSolverTest {

    /** The stub world: bedrock floor at y=0, one override cell per test. */
    private static final class StubWorld implements EntityCollisionWorld {
        final Map<Long, BlockType> cells = new HashMap<>();

        void put(int x, int y, int z, BlockType type) {
            cells.put(((long) x << 44) | ((long) z << 32) | (y & 0xFFFFFFFFL), type);
        }

        @Override
        public BlockType blockAt(int x, int y, int z) {
            if (y == 0) {
                return BuiltinBlocks.BEDROCK;
            }
            BlockType type = cells.get(
                    ((long) x << 44) | ((long) z << 32) | (y & 0xFFFFFFFFL));
            return type != null ? type : BuiltinBlocks.AIR;
        }

        @Override
        public boolean chunkLoaded(int x, int z) {
            return true;
        }
    }

    /** A flat stone floor at y=4 spanning x/z 0..20 (cells 1..19 solid). */
    private static void floor(StubWorld world) {
        for (int x = 1; x <= 19; x++) {
            for (int z = 1; z <= 19; z++) {
                world.put(x, 4, z, BuiltinBlocks.STONE);
            }
        }
    }

    // --- the free fall -----------------------------------------------------

    @Test
    void aFallStopsExactlyAtTheFloorCeiling() {
        StubWorld world = new StubWorld();
        floor(world);
        // A player-shaped body at (10.3, 5.2, 10.3) falling 0.7.
        EntityMoveSolver.Body body = new EntityMoveSolver.Body(10.3, 5.2, 10.3, 0.6, 1.8);
        EntityMoveSolver.Result result = EntityMoveSolver.move(world, body,
                0.0, -0.7, 0.0, EntityMoveSolver.MoveLaw.plain());
        assertEquals(0.0, result.dx(), 1e-9);
        assertEquals(-0.2, result.dy(), 1e-9, "the fall clips to the floor's ceiling");
        assertEquals(5.0, body.y, 1e-9, "the body's feet land at 5.0");
        assertTrue(result.onGround(), "the downward clip turns the ground on");
        assertTrue(result.collidingVertically());
        assertTrue(result.zeroVelocityY(), "the vertical clip zeroes velocityY");
        assertFalse(result.collidingHorizontally());
    }

    @Test
    void aRiseDoesNotTurnTheGroundOn() {
        StubWorld world = new StubWorld();
        floor(world);
        // A ceiling over the body: the body (feet 5.0, head 6.8) has
        // room, the ceiling's underside at 7.0.
        for (int x = 9; x <= 11; x++) {
            for (int z = 9; z <= 11; z++) {
                world.put(x, 7, z, BuiltinBlocks.STONE);
            }
        }
        EntityMoveSolver.Body body = new EntityMoveSolver.Body(10.3, 5.0, 10.3, 0.6, 1.8);
        EntityMoveSolver.Result result = EntityMoveSolver.move(world, body,
                0.0, 1.0, 0.0, EntityMoveSolver.MoveLaw.plain());
        // The head (6.8) may rise to the ceiling's underside (7.0):
        // the rise clips to 0.2.
        assertEquals(0.2, result.dy(), 1e-9, "the rise clips at the ceiling's underside");
        assertTrue(result.collidingVertically());
        assertFalse(result.onGround(), "an upward clip never turns the ground on");
        assertTrue(result.zeroVelocityY());
    }

    // --- the wall walk -----------------------------------------------------

    @Test
    void aWalkIntoAWallStopsExactlyAtTheFace() {
        StubWorld world = new StubWorld();
        floor(world);
        // A wall one cell east at x=12.
        for (int y = 5; y <= 6; y++) {
            for (int z = 9; z <= 11; z++) {
                world.put(12, y, z, BuiltinBlocks.STONE);
            }
        }
        EntityMoveSolver.Body body = new EntityMoveSolver.Body(10.7, 5.0, 10.3, 0.6, 1.8);
        EntityMoveSolver.Result result = EntityMoveSolver.move(world, body,
                2.0, 0.0, 0.0, EntityMoveSolver.MoveLaw.plain());
        // The body's east face (10.7 + 0.3 = 11.0) may reach 12.0: 1.0.
        assertEquals(1.0, result.dx(), 1e-9, "the walk clips to the wall's near face");
        assertEquals(12.0 - 0.3, body.x, 1e-9);
        assertTrue(result.collidingHorizontally());
        assertTrue(result.zeroVelocityX(), "the X clip zeroes velocityX");
        assertFalse(result.zeroVelocityZ());
    }

    @Test
    void theDiagonalWalkClipsEachAxisAlone() {
        StubWorld world = new StubWorld();
        floor(world);
        // A wall to the east AND a wall to the south.
        for (int y = 5; y <= 6; y++) {
            for (int z = 9; z <= 11; z++) {
                world.put(12, y, z, BuiltinBlocks.STONE);
            }
            for (int x = 9; x <= 11; x++) {
                world.put(x, y, 12, BuiltinBlocks.STONE);
            }
        }
        EntityMoveSolver.Body body = new EntityMoveSolver.Body(10.7, 5.0, 10.7, 0.6, 1.8);
        EntityMoveSolver.Result result = EntityMoveSolver.move(world, body,
                2.0, 0.0, 2.0, EntityMoveSolver.MoveLaw.plain());
        assertEquals(1.0, result.dx(), 1e-9);
        assertEquals(1.0, result.dz(), 1e-9);
        assertTrue(result.collidingHorizontally());
        assertTrue(result.zeroVelocityX());
        assertTrue(result.zeroVelocityZ());
    }

    // --- the step retry (the walkers' own law) -----------------------------

    @Test
    void aWalkerStepsOntoTheHalfSlab() {
        StubWorld world = new StubWorld();
        floor(world);
        // A bottom slab at (12, 5, 10) — the half-height step.
        world.put(12, 5, 10, BuiltinBlocks.OAK_SLAB);
        EntityMoveSolver.Body body = new EntityMoveSolver.Body(10.7, 5.0, 10.3, 0.6, 1.8);
        body.onGround = true;
        // The grounded walker's own convention (LivingEntity.travel:
        // the move runs BEFORE the gravity, so a standing body enters
        // with the previous tick's small negative drift).
        double drift = -0.0784 * 0.98;
        EntityMoveSolver.Result result = EntityMoveSolver.move(world, body,
                2.0, drift, 0.0, new EntityMoveSolver.MoveLaw(0.5, false, false, false));
        // The step retry walks up and over: the full 2.0 eastward motion
        // is kept, the body lands on the slab's top at 5.5.
        assertEquals(2.0, result.dx(), 1e-9, "the step retry keeps the intended walk");
        assertEquals(5.5, body.y, 1e-9, "the body stands on the slab's half");
        assertTrue(result.onGround());
        assertFalse(result.collidingHorizontally(),
                "the step retry cleared the horizontal clip");
    }

    @Test
    void aSteplessBodyClipsAgainstTheSlab() {
        StubWorld world = new StubWorld();
        floor(world);
        world.put(12, 5, 10, BuiltinBlocks.OAK_SLAB);
        EntityMoveSolver.Body body = new EntityMoveSolver.Body(10.7, 5.0, 10.3, 0.6, 1.8);
        body.onGround = true;
        EntityMoveSolver.Result result = EntityMoveSolver.move(world, body,
                2.0, 0.0, 0.0, EntityMoveSolver.MoveLaw.plain());
        // Without the step height the slab's lower-half box is a wall to
        // the feet: the walk clips at the slab's west face.
        assertEquals(1.0, result.dx(), 1e-9);
        assertTrue(result.collidingHorizontally());
    }

    @Test
    void theStepRetryNeverClimbsAWall() {
        StubWorld world = new StubWorld();
        floor(world);
        // A full wall at x=12 — too tall for the 0.5 step.
        for (int y = 5; y <= 6; y++) {
            for (int z = 9; z <= 11; z++) {
                world.put(12, y, z, BuiltinBlocks.STONE);
            }
        }
        EntityMoveSolver.Body body = new EntityMoveSolver.Body(10.7, 5.0, 10.3, 0.6, 1.8);
        body.onGround = true;
        EntityMoveSolver.Result result = EntityMoveSolver.move(world, body,
                2.0, 0.0, 0.0, new EntityMoveSolver.MoveLaw(0.5, false, false, false));
        assertEquals(1.0, result.dx(), 1e-9, "the full wall still clips the walk");
        assertTrue(result.collidingHorizontally());
        assertEquals(5.0, body.y, 1e-9, "the body stays on its floor");
    }

    // --- the tall families (the ledger's own named case) -------------------

    @Test
    void aBodyLandsOnTheFencesTallPostFromAbove() {
        StubWorld world = new StubWorld();
        floor(world);
        // A fence at (10, 5, 10): its box 10.375..10.625, 5.0..6.5.
        world.put(10, 5, 10, BuiltinBlocks.FENCE);
        // A body straddling the band from the cell above: feet at 6.7.
        EntityMoveSolver.Body body = new EntityMoveSolver.Body(10.5, 6.7, 10.5, 0.6, 1.8);
        EntityMoveSolver.Result result = EntityMoveSolver.move(world, body,
                0.0, -1.2, 0.0, EntityMoveSolver.MoveLaw.plain());
        // The fence's 1.5 box (5.0..6.5) catches the fall: the feet
        // stop at 6.5, exactly the tall post's ceiling.
        assertEquals(-0.2, result.dy(), 1e-9);
        assertEquals(6.5, body.y, 1e-9, "the body rests on the post's 1.5 ceiling");
        assertTrue(result.onGround());
    }

    @Test
    void aBodyWalksUnderTheTopSlab() {
        StubWorld world = new StubWorld();
        floor(world);
        // A top slab at (12, 5, 10): its box 5.5..6.0.
        world.put(12, 5, 10, BuiltinBlocks.STONE_SLAB_TOP);
        // A short body (0.5 tall) walks east through the cell.
        EntityMoveSolver.Body body = new EntityMoveSolver.Body(10.7, 5.0, 10.3, 0.6, 0.5);
        body.onGround = true;
        EntityMoveSolver.Result result = EntityMoveSolver.move(world, body,
                2.0, 0.0, 0.0, new EntityMoveSolver.MoveLaw(0.5, false, false, false));
        assertEquals(2.0, result.dx(), 1e-9, "the short body passes beneath the top slab");
        assertFalse(result.collidingHorizontally());
    }

    @Test
    void aBodyAscendsTheStairsTwoBoxForm() {
        StubWorld world = new StubWorld();
        floor(world);
        // An east-ascending stair at (12, 5, 10): the base half slab
        // (5.0..5.5) + the tall back on the EAST side (x 12.5..13.0,
        // y 5.5..6.0).
        world.put(12, 5, 10, BuiltinBlocks.OAK_STAIRS_EAST);
        EntityMoveSolver.Body body = new EntityMoveSolver.Body(10.7, 5.0, 10.3, 0.6, 1.8);
        body.onGround = true;
        // The grounded walker's small negative drift (the gravity lands
        // AFTER the move — the reference's own order).
        double drift = -0.0784 * 0.98;
        // The first tick: the step retry lifts onto the base slab
        // (5.5) and the walk clips at the tall back's near face (the
        // 0.5 step reaches exactly the back's BOTTOM — the climb takes
        // the second tick, exactly the reference's own per-tick walk).
        EntityMoveSolver.Result first = EntityMoveSolver.move(world, body,
                2.6, drift, 0.0, new EntityMoveSolver.MoveLaw(0.5, false, false, false));
        assertEquals(1.5, first.dx(), 1e-9,
                "the first tick clips at the tall back: 12.5 - 0.3 - 10.7");
        assertEquals(5.5, body.y, 1e-9, "the first tick stands on the base slab");
        assertTrue(first.onGround());
        // The second tick: from the base slab the step retry reaches
        // the tall back's top (6.0) and the walk continues clear.
        EntityMoveSolver.Result second = EntityMoveSolver.move(world, body,
                1.5, drift, 0.0, new EntityMoveSolver.MoveLaw(0.5, false, false, false));
        assertTrue(second.dx() > 1.2, "the second tick clears the tall back: " + second.dx());
        assertTrue(body.y >= 5.5 - 1e-9, "the body stands at least on the base slab");
        assertTrue(second.onGround());
    }

    // --- the special laws ---------------------------------------------------

    @Test
    void theCobwebDragShrinksTheMotion() {
        StubWorld world = new StubWorld();
        // An airborne body in the web (the bedrock floor is 5 blocks
        // below — nothing clips the crawl).
        EntityMoveSolver.Body body = new EntityMoveSolver.Body(10.3, 5.0, 10.3, 0.6, 1.8);
        EntityMoveSolver.Result result = EntityMoveSolver.move(world, body,
                1.0, -0.8, 1.0, new EntityMoveSolver.MoveLaw(0.0, false, false, true));
        assertEquals(0.25, result.dx(), 1e-9);
        assertEquals(-0.04, result.dy(), 1e-9);
        assertEquals(0.25, result.dz(), 1e-9);
        assertTrue(result.cobwebVelocityZero(),
                "the caller zeroes the body's velocities in the web");
    }

    @Test
    void theNoClipPassThroughKeepsEveryFlag() {
        StubWorld world = new StubWorld();
        floor(world);
        EntityMoveSolver.Body body = new EntityMoveSolver.Body(10.3, 5.0, 10.3, 0.6, 1.8);
        EntityMoveSolver.Result result = EntityMoveSolver.move(world, body,
                5.0, -3.0, 5.0, new EntityMoveSolver.MoveLaw(0.0, false, true, false));
        assertEquals(5.0, result.dx());
        assertEquals(-3.0, result.dy());
        assertFalse(result.onGround());
        assertFalse(result.collided());
        assertEquals(10.3 + 5.0, body.x, 1e-9);
        assertEquals(5.0 - 3.0, body.y, 1e-9);
    }

    @Test
    void theSneakingGuardNeverLeavesTheSupport() {
        StubWorld world = new StubWorld();
        // One single floor cell at (10, 4, 10) — the body starts on it.
        world.put(10, 4, 10, BuiltinBlocks.STONE);
        EntityMoveSolver.Body body = new EntityMoveSolver.Body(10.3, 5.0, 10.3, 0.6, 1.8);
        body.onGround = true;
        // A sneaking player walks east 1.5 — fully off the single
        // cell (the body's box 10.0..10.6 would leave the cell edge
        // at 11.0 entirely).
        EntityMoveSolver.Result result = EntityMoveSolver.move(world, body,
                1.5, 0.0, 0.0, new EntityMoveSolver.MoveLaw(0.5, true, false, false));
        // The guard shaves the walk 0.05 at a time until the moved
        // shape still finds its support: the box's west edge stays
        // inside the cell (10.0 + dx < 11.0 → dx < 1.0, the shave
        // landing at 0.95).
        assertEquals(0.95, result.dx(), 1e-9, "the shaved walk keeps the support");
        // The body's own moved box still overlaps the supporting
        // column (the reference's ground read: the shape moved DOWN
        // one finds its floor).
        assertFalse(EntityCollisions.gatherBlockBoxes(world,
                body.shape().moved(0.0, -1.0, 0.0), true).isEmpty(),
                "the body still stands over its support");
    }
}
