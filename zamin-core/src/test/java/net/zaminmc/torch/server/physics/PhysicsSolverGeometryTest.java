package net.zaminmc.torch.server.physics;

import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The physics-solver slice's contract, part one — the geometry and the
 * gather: the {@link Aabb} clipping trio is the reference's own
 * {@code Box.intersectX/Y/Z} math (a floor stops a fall exactly at its
 * ceiling, a wall stops a walk exactly at its face, boxes that do not
 * overlap the other two axes never clip), and the {@link EntityCollisions}
 * gather is the reference's own {@code World.getCollisions} walk (the
 * cell band with the one-below start the 1.5-tall families need, the
 * chunk gate, the border's STONE wall, the per-block shape boxes in
 * world coordinates — the same table the point queries read).
 */
class PhysicsSolverGeometryTest {

    // --- the Aabb law (the reference's Box.java 1:1) ----------------------

    @Test
    void expandedGrowsTheSweptFaceOnly() {
        Aabb body = new Aabb(10, 64, 10, 10.6, 65.8, 10.6);
        // A fall (dy = -2): the min Y face lowers, the max stays.
        Aabb sweptDown = body.expanded(0, -2, 0);
        assertEquals(64 - 2, sweptDown.minY, 1e-9);
        assertEquals(65.8, sweptDown.maxY, 1e-9);
        // A walk east (dx = +1): the max X face rises.
        Aabb sweptEast = body.expanded(1, 0, 0);
        assertEquals(10, sweptEast.minX, 1e-9);
        assertEquals(11.6, sweptEast.maxX, 1e-9);
        // A walk west (dx = -1): the min X face lowers.
        Aabb sweptWest = body.expanded(-1, 0, 0);
        assertEquals(9, sweptWest.minX, 1e-9);
        assertEquals(10.6, sweptWest.maxX, 1e-9);
        // Zero expands nothing.
        assertEquals(body, body.expanded(0, 0, 0));
    }

    @Test
    void touchingFacesDoNotIntersect() {
        Aabb floor = new Aabb(10, 63, 10, 11, 64, 11);
        Aabb resting = new Aabb(10, 64, 10, 10.6, 65.8, 10.6);
        // The body's min Y face touches the floor's max Y face: NOT an
        // intersection (the reference's half-open convention).
        assertFalse(floor.intersects(resting));
        assertFalse(resting.intersects(floor));
        // A body beside the floor, overlapping on X/Z and Y: a real
        // overlap both ways (the test's own symmetry law).
        Aabb beside = new Aabb(10.5, 63.2, 10.5, 11.2, 63.8, 11.2);
        assertTrue(floor.intersects(beside));
        assertTrue(beside.intersects(floor));
    }

    @Test
    void intersectYClipsTheFallExactlyAtTheCeiling() {
        // The clip law's own direction (the reference's call shape:
        // collisionBox.intersectY(moverShape, limit)): a body at 64.5
        // over a floor whose ceiling is 64.0 — a fall that would pass
        // THROUGH (0.8) clips to exactly the ceiling (0.5), a fall
        // that never reaches (0.3) passes untouched.
        Aabb body = new Aabb(10, 64.5, 10, 10.6, 66.3, 10.6);
        Aabb floor = new Aabb(9, 63, 9, 12, 64, 12);
        assertEquals(-0.5, floor.intersectY(body, -0.8), 1e-9,
                "the through-fall clips to exactly the floor's ceiling");
        assertEquals(-0.3, floor.intersectY(body, -0.3), 1e-9,
                "the short fall never reaches the floor");
        // The floor is below the body: a positive limit passes untouched.
        assertEquals(0.3, floor.intersectY(body, 0.3), 1e-9);
        // A body beside the floor on X never clips.
        Aabb beside = new Aabb(13, 64.5, 10, 13.6, 66.3, 10.6);
        assertEquals(-0.8, floor.intersectY(beside, -0.8), 1e-9);
    }

    @Test
    void intersectYClipsTheRiseExactlyAtTheUnderside() {
        // A body under a ceiling rising into it.
        Aabb body = new Aabb(10, 63.2, 10, 10.6, 65, 10.6);
        Aabb ceiling = new Aabb(9, 65.5, 9, 12, 66.5, 12);
        assertEquals(0.5, ceiling.intersectY(body, 0.8), 1e-9,
                "the rise clips to exactly the ceiling's underside");
        assertEquals(-0.4, ceiling.intersectY(body, -0.4), 1e-9,
                "a fall passes the ceiling untouched");
    }

    @Test
    void intersectXClipsTheWalkExactlyAtTheWallFace() {
        Aabb body = new Aabb(10, 64, 10, 10.6, 65.8, 10.6);
        Aabb wall = new Aabb(12, 63, 9, 13, 66, 12);
        assertEquals(1.4, wall.intersectX(body, 2.0), 1e-9,
                "the eastward walk clips to the wall's near face");
        assertEquals(-0.5, wall.intersectX(body, -0.5), 1e-9,
                "a westward walk passes untouched");
        // A wall on the other side clips a westward walk.
        Aabb westWall = new Aabb(8, 63, 9, 9, 66, 12);
        assertEquals(-1.0, westWall.intersectX(body, -2.0), 1e-9);
    }

    @Test
    void intersectZClipsLikeXOnItsOwnAxis() {
        Aabb body = new Aabb(10, 64, 10, 10.6, 65.8, 10.6);
        Aabb wall = new Aabb(9, 63, 12, 12, 66, 13);
        assertEquals(1.4, wall.intersectZ(body, 2.0), 1e-9);
        assertEquals(-0.5, wall.intersectZ(body, -0.5), 1e-9);
        // No Y overlap: no clip.
        Aabb highWall = new Aabb(9, 67, 12, 12, 68, 13);
        assertEquals(2.0, highWall.intersectZ(body, 2.0), 1e-9);
    }

    // --- the gather law (the reference's World.getCollisions 1:1) ---------

    /**
     * The stub world: a flat bedrock floor at y=0, air above, one
     * override cell per test. Chunk (0,0) loaded; every other column
     * loaded unless the test names its unloaded set.
     */
    private static class StubWorld implements EntityCollisionWorld {
        final Map<Long, BlockType> cells = new HashMap<>();
        boolean allChunksLoaded = true;

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
            return allChunksLoaded;
        }
    }

    @Test
    void gatherAnswersTheFullCubeInWorldCoordinates() {
        StubWorld world = new StubWorld();
        world.put(5, 4, 5, BuiltinBlocks.STONE);
        List<Aabb> boxes = EntityCollisions.gatherBlockBoxes(world,
                new Aabb(4.9, 3.9, 4.9, 6.1, 5.1, 6.1), true);
        assertEquals(1, boxes.size());
        assertEquals(new Aabb(5, 4, 5, 6, 5, 6), boxes.get(0));
    }

    @Test
    void gatherAnswersTheSlabFractionBox() {
        StubWorld world = new StubWorld();
        world.put(5, 4, 5, BuiltinBlocks.OAK_SLAB);
        List<Aabb> boxes = EntityCollisions.gatherBlockBoxes(world,
                new Aabb(4.9, 3.9, 4.9, 6.1, 5.1, 6.1), true);
        assertEquals(1, boxes.size());
        assertEquals(new Aabb(5, 4, 5, 6, 4.5, 6), boxes.get(0),
                "the bottom slab's box is the lower half only");
    }

    @Test
    void gatherAnswersTheFencesTallBandWithItsArm() {
        StubWorld world = new StubWorld();
        // An oak fence with a stone neighbour east: the W/E band box —
        // the reference's own two-band shape, the post's span extended
        // east to the cell edge as ONE box — 1.5 tall.
        world.put(5, 4, 5, BuiltinBlocks.FENCE);
        world.put(6, 4, 5, BuiltinBlocks.STONE);
        List<Aabb> boxes = EntityCollisions.gatherBlockBoxes(world,
                new Aabb(4.9, 3.9, 4.9, 6.1, 5.6, 6.1), true);
        // The fence's single W/E band: x 5.375..6.0 (the arm to the
        // connected edge), z 5.375..5.625 (the post's span), maxY 5.5.
        assertTrue(boxes.stream().anyMatch(box ->
                box.minX == 5.375 && box.maxX == 6.0
                        && box.minZ == 5.375 && box.maxZ == 5.625
                        && box.minY == 4.0 && box.maxY == 5.5),
                "the connected east band: " + boxes);
        // And no N/S band of the fence's own: no 1.5-tall box spanning
        // the cell's full Z (the stone cube beside it is 1.0 tall).
        assertEquals(1, boxes.stream().filter(box -> box.maxY == 5.5).count(),
                "the fence contributes exactly its one W/E band: " + boxes);
    }

    @Test
    void gatherStartsOneCellBelowForTheTallFamilies() {
        StubWorld world = new StubWorld();
        // A fence at y=4 whose 1.5 box reaches 5.5 — a region entirely
        // INSIDE cell y=5 must still see it.
        world.put(5, 4, 5, BuiltinBlocks.FENCE);
        List<Aabb> boxes = EntityCollisions.gatherBlockBoxes(world,
                new Aabb(5.4, 5.1, 5.4, 5.6, 5.4, 5.6), true);
        assertEquals(1, boxes.size());
        assertEquals(new Aabb(5.375, 4, 5.375, 5.625, 5.5, 5.625), boxes.get(0),
                "the fence pokes through its ceiling into the region above");
    }

    @Test
    void gatherSkipsUnloadedColumns() {
        StubWorld world = new StubWorld();
        world.allChunksLoaded = false;
        world.put(5, 4, 5, BuiltinBlocks.STONE);
        assertTrue(EntityCollisions.gatherBlockBoxes(world,
                new Aabb(4.9, 3.9, 4.9, 6.1, 5.1, 6.1), true).isEmpty(),
                "an unloaded column contributes nothing");
    }

    @Test
    void gatherAnswersTheBorderStoneWall() {
        StubWorld world = new StubWorld() {
            @Override
            public boolean borderContains(int x, int z) {
                // The border ends at x < 7.
                return x < 7;
            }
        };
        // The cell at x=7 lies outside the border: for a body INSIDE it
        // reads as STONE (the wall) whatever the world holds.
        world.put(7, 4, 5, BuiltinBlocks.AIR);
        List<Aabb> boxes = EntityCollisions.gatherBlockBoxes(world,
                new Aabb(6.9, 3.9, 4.9, 8.1, 5.1, 6.1), true);
        assertTrue(boxes.stream().anyMatch(box ->
                box.minX == 7 && box.minY == 4 && box.minZ == 5
                        && box.maxX == 8 && box.maxY == 5 && box.maxZ == 6),
                "the out-of-border cell reads as STONE for a body inside");
        // The same cell reads real for a body already outside.
        boxes = EntityCollisions.gatherBlockBoxes(world,
                new Aabb(6.9, 3.9, 4.9, 8.1, 5.1, 6.1), false);
        assertTrue(boxes.stream().noneMatch(box -> box.minX == 7 && box.minY == 4),
                "the cell reads real (air: no box) for a body outside the border");
    }

    @Test
    void gatherAddsOnlyIntersectingBoxes() {
        StubWorld world = new StubWorld();
        world.put(5, 4, 5, BuiltinBlocks.STONE);
        // A region entirely above the stone cell's box.
        assertTrue(EntityCollisions.gatherBlockBoxes(world,
                new Aabb(5.1, 5.0, 5.1, 5.9, 5.9, 5.9), true).isEmpty(),
                "the region band covers the cell but the box does not intersect it");
    }
}
