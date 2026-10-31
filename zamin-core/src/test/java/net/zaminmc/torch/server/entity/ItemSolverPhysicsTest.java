package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.WorldSolidity;
import net.zaminmc.torch.server.physics.EntityCollisionWorld;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The physics-solver slice's item-body contract (Slice M4): the dropped
 * item's own 0.25 box and the falling block's 0.98 box clip through the
 * reference's swept-box {@code Entity.move} when the ground provides the
 * collision surface — the leading-face sample stand-in RETIRED for the
 * items (the exact face stop), the centre-point landing RETIRED for the
 * falling blocks (the box FOOTPRINT catches the slab's edge and the
 * fence's post the centre point misses).
 */
class ItemSolverPhysicsTest {

    /** The solver-backed stub ground: a block map + the collision surface. */
    private static class SolverGround implements ItemEntity.Ground, FallingBlockEntity.Ground {
        final Map<Long, BlockType> cells = new HashMap<>();

        @Override
        public void setActiveDimension(int dimension) {
            // The stub ground's own no-op (the two Ground interfaces'
            // dimension flips meet here — the map is one world's).
        }

        void put(int x, int y, int z, BlockType type) {
            cells.put(((long) x << 44) | ((long) z << 32) | (y & 0xFFFFFFFFL), type);
        }

        BlockType read(int x, int y, int z) {
            if (y == 0) {
                return BuiltinBlocks.BEDROCK;
            }
            BlockType type = cells.get(
                    ((long) x << 44) | ((long) z << 32) | (y & 0xFFFFFFFFL));
            return type != null ? type : BuiltinBlocks.AIR;
        }

        /** The stone floor: cells at y=63 — the top at 64.0. */
        void floor() {
            for (int x = -8; x <= 8; x++) {
                for (int z = -8; z <= 8; z++) {
                    put(x, 63, z, BuiltinBlocks.STONE);
                }
            }
        }

        @Override
        public boolean isSolid(double x, double y, double z) {
            return WorldSolidity.isSolid(read(
                    (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)));
        }

        // Both Ground families declare their own isSolidAt/supportY
        // defaults — explicit overrides settle the diamonds (the same
        // folds).
        @Override
        public boolean isSolidAt(double x, double y, double z) {
            return isSolid(x, y, z);
        }

        @Override
        public double supportY(double x, double y, double z) {
            return isSolid(x, y, z) ? Math.floor(y) + 1.0 : Double.NEGATIVE_INFINITY;
        }

        private final EntityCollisionWorld surface = new EntityCollisionWorld() {
            @Override
            public BlockType blockAt(int x, int y, int z) {
                return read(x, y, z);
            }

            @Override
            public boolean chunkLoaded(int x, int z) {
                return true;
            }
        };

        @Override
        public EntityCollisionWorld collisionWorld() {
            return surface;
        }
    }

    // --- the item bodies ----------------------------------------------------

    @Test
    void theItemLandsOnTheSlabsEdgeThroughItsBox() {
        SolverGround ground = new SolverGround();
        ground.floor();
        // A bottom slab at (5, 64, 5): the box 5..6, 64..64.5. The item's
        // 0.25 box drifting east at x 4.95 — the box 4.825..5.075 OVERLAPS
        // the slab's west face: the landing catches the slab's half even
        // though the CENTRE (4.95) still sits over the empty cell.
        ground.put(5, 64, 5, BuiltinBlocks.OAK_SLAB);
        ItemEntity item = new ItemEntity(3, new Position(4.95, 65.5, 5.5),
                new net.zaminmc.torch.item.ItemStack(
                        net.zaminmc.torch.server.item.BuiltinItems.DIAMOND,
                        1, 0, null, java.util.Map.of()), 0);
        for (int i = 0; i < 30; i++) {
            item.tick(ground);
        }
        assertEquals(64.5 + ItemEntity.HALF_HEIGHT, item.position().y(), 1e-9,
                "the box's overlap rests the item on the slab's half");
        assertTrue(item.onGround());
    }

    @Test
    void theItemStopsAtTheWallsExactFace() {
        SolverGround ground = new SolverGround();
        ground.floor();
        // A wall at x=7: the face 7.0. The item thrown east at 2.0/tick.
        for (int y = 64; y <= 66; y++) {
            for (int z = 3; z <= 7; z++) {
                ground.put(7, y, z, BuiltinBlocks.STONE);
            }
        }
        ItemEntity item = new ItemEntity(3, new Position(5.5, 64.5, 5.5),
                new net.zaminmc.torch.item.ItemStack(
                        net.zaminmc.torch.server.item.BuiltinItems.DIAMOND,
                        1, 0, null, java.util.Map.of()), 0);
        item.setVelocity(2.0, 0.0, 0.0);
        item.tick(ground);
        // The item's east face (x + 0.125) stops exactly at 7.0: the
        // centre 6.875 — the CLIP, never the leading-face full stop.
        assertEquals(7.0 - 0.125, item.position().x(), 1e-9,
                "the slide clips at the wall's face");
        // The velocity zeroed by the clip; the second tick holds.
        item.tick(ground);
        assertEquals(7.0 - 0.125, item.position().x(), 1e-9);
    }

    @Test
    void theItemNeverSinksThroughTheFencesTallBand() {
        SolverGround ground = new SolverGround();
        ground.floor();
        // A fence at (5, 64, 5): the 1.5 band 64..65.5. An item dropped
        // from above the post rests ON the band's top.
        ground.put(5, 64, 5, BuiltinBlocks.FENCE);
        ItemEntity item = new ItemEntity(3, new Position(5.5, 67.0, 5.5),
                new net.zaminmc.torch.item.ItemStack(
                        net.zaminmc.torch.server.item.BuiltinItems.DIAMOND,
                        1, 0, null, java.util.Map.of()), 0);
        for (int i = 0; i < 30; i++) {
            item.tick(ground);
        }
        assertEquals(65.5 + ItemEntity.HALF_HEIGHT, item.position().y(), 1e-9,
                "the item rests on the fence's 1.5 ceiling");
        assertTrue(item.onGround());
    }

    // --- the falling blocks ---------------------------------------------------

    @Test
    void theFallingBlockLandsOnTheSlabsEdgeThroughItsBox() {
        SolverGround ground = new SolverGround();
        ground.floor();
        // The slab at (5, 64, 5): the box 5..6, 64..64.5. The falling
        // block's 0.98 box at x 4.6 (the box 4.11..5.09) overlaps the
        // slab's west edge — the footprint catches the half the centre
        // point (4.6, still over the empty cell 4) would miss.
        ground.put(5, 64, 5, BuiltinBlocks.OAK_SLAB);
        FallingBlockEntity block = new FallingBlockEntity(7,
                BuiltinBlocks.STONE, new Position(4.6, 68.0, 5.5));
        FallingBlockEntity.Step step = FallingBlockEntity.Step.MOVED;
        for (int i = 0; i < 40 && step != FallingBlockEntity.Step.LANDED; i++) {
            step = block.tick(ground);
        }
        assertEquals(FallingBlockEntity.Step.LANDED, step, "the block landed");
        assertEquals(64.5, block.position().y(), 1e-9,
                "the 0.98 box's overlap rests the block on the slab's half");
        assertTrue(block.onGround());
    }

    @Test
    void theFallingBlockKeepsFallingPastTheCentresMiss() {
        SolverGround ground = new SolverGround();
        ground.floor();
        // The slab at (5, 64, 5): a block whose whole box (0.98 wide at
        // x 3.6: 3.11..4.09) misses the slab cell entirely falls THROUGH
        // the slab's cell to the floor below — the footprint rule cuts
        // both ways.
        ground.put(5, 64, 5, BuiltinBlocks.OAK_SLAB);
        FallingBlockEntity block = new FallingBlockEntity(7,
                BuiltinBlocks.STONE, new Position(3.6, 68.0, 5.5));
        FallingBlockEntity.Step step = FallingBlockEntity.Step.MOVED;
        for (int i = 0; i < 40 && step != FallingBlockEntity.Step.LANDED; i++) {
            step = block.tick(ground);
        }
        assertEquals(FallingBlockEntity.Step.LANDED, step);
        assertEquals(64.0, block.position().y(), 1e-9,
                "the block misses the slab and lands on the floor");
    }

    @Test
    void theFallingBlockLandsOnTheFencesTallBand() {
        SolverGround ground = new SolverGround();
        ground.floor();
        ground.put(5, 64, 5, BuiltinBlocks.FENCE);
        FallingBlockEntity block = new FallingBlockEntity(7,
                BuiltinBlocks.STONE, new Position(5.5, 68.0, 5.5));
        FallingBlockEntity.Step step = FallingBlockEntity.Step.MOVED;
        for (int i = 0; i < 40 && step != FallingBlockEntity.Step.LANDED; i++) {
            step = block.tick(ground);
        }
        assertEquals(FallingBlockEntity.Step.LANDED, step);
        assertEquals(65.5, block.position().y(), 1e-9,
                "the 0.98 box rests on the fence's 1.5 band");
    }

    @Test
    void theLegacyPointFoldStillLandsTheCentre() {
        // The stub WITHOUT the collision surface keeps the centre-point
        // landing fold (the pre-solver convention, the 366's own pin).
        SolverGround ground = new SolverGround() {
            @Override
            public EntityCollisionWorld collisionWorld() {
                return null;
            }
        };
        ground.floor();
        ground.put(5, 64, 5, BuiltinBlocks.OAK_SLAB);
        FallingBlockEntity block = new FallingBlockEntity(7,
                BuiltinBlocks.STONE, new Position(5.5, 68.0, 5.5));
        FallingBlockEntity.Step step = FallingBlockEntity.Step.MOVED;
        for (int i = 0; i < 40 && step != FallingBlockEntity.Step.LANDED; i++) {
            step = block.tick(ground);
        }
        assertEquals(FallingBlockEntity.Step.LANDED, step);
        // This stub's legacy fold is the BOOLEAN one (the full-cube
        // snap): the centre over the slab cell lands on the CELL's top,
        // the pre-shape convention the point walk carried.
        assertEquals(65.0, block.position().y(), 1e-9,
                "the boolean fold's full-cube snap: the cell's ceiling");
        assertFalse(block.position().y() < 64.0);
    }
}
