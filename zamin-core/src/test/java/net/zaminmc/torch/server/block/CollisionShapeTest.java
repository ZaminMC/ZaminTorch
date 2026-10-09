package net.zaminmc.torch.server.block;

import net.zaminmc.torch.server.entity.FallingBlockEntity;
import net.zaminmc.torch.server.entity.ItemEntity;
import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The collision-shape slice's contract: the shape queries carve the partial
 * blocks out of the full-cube rule — bottom slabs and stairs answer their
 * lower half, top slabs their upper, the fence its historical 1.5 height —
 * and the three body models (mob, item, falling block) land on the shape
 * surfaces through the shape-aware world queries.
 */
class CollisionShapeTest {

    // --- the WorldSolidity matrix -----------------------------------------

    @Test
    void bottomSlabsOpenTheirUpperHalf() {
        assertTrue(WorldSolidity.isSolidAt(BuiltinBlocks.OAK_SLAB, 64.25),
                "the lower half is solid");
        assertFalse(WorldSolidity.isSolidAt(BuiltinBlocks.OAK_SLAB, 64.75),
                "the upper half is open: a body stands inside the cell");
        assertEquals(64.5, WorldSolidity.supportY(BuiltinBlocks.OAK_SLAB, 64.25), 1e-9,
                "the support is the half");
        assertEquals(64.5, WorldSolidity.supportY(BuiltinBlocks.OAK_SLAB, 64.75), 1e-9,
                "the support is the half regardless of the point's fraction");
    }

    @Test
    void topSlabsOpenTheirLowerHalf() {
        assertFalse(WorldSolidity.isSolidAt(BuiltinBlocks.STONE_SLAB_TOP, 64.25),
                "the lower half is open: an arrow flies beneath");
        assertTrue(WorldSolidity.isSolidAt(BuiltinBlocks.STONE_SLAB_TOP, 64.75),
                "the upper half is solid");
        assertEquals(65.0, WorldSolidity.supportY(BuiltinBlocks.STONE_SLAB_TOP, 64.9), 1e-9,
                "a top slab supports at the cell's ceiling");
    }

    @Test
    void stairsCountAsTheirBottomHalf() {
        assertTrue(WorldSolidity.isSolidAt(BuiltinBlocks.OAK_STAIRS_EAST, 64.4));
        assertFalse(WorldSolidity.isSolidAt(BuiltinBlocks.OAK_STAIRS_NORTH, 64.6));
        assertEquals(64.5, WorldSolidity.supportY(BuiltinBlocks.COBBLESTONE_STAIRS_WEST, 64.1), 1e-9);
    }

    @Test
    void fullCubesAndTheFenceKeepTheirRules() {
        assertTrue(WorldSolidity.isSolidAt(BuiltinBlocks.STONE, 64.99));
        assertEquals(65.0, WorldSolidity.supportY(BuiltinBlocks.STONE, 64.2), 1e-9);
        // The fence: solid everywhere in the cell, support at the 1.5 top.
        assertTrue(WorldSolidity.isSolidAt(BuiltinBlocks.FENCE, 64.9));
        assertEquals(65.5, WorldSolidity.supportY(BuiltinBlocks.FENCE, 64.0), 1e-9);
        // Non-solid types have no shape at all.
        assertFalse(WorldSolidity.isSolidAt(BuiltinBlocks.TALL_GRASS, 64.25));
        assertEquals(Double.NEGATIVE_INFINITY,
                WorldSolidity.supportY(BuiltinBlocks.TALL_GRASS, 64.25));
        assertFalse(WorldSolidity.isSolidAt(null, 64.25));
    }

    // --- the shape-aware stub world ----------------------------------------

    /** Ground at y<4 everywhere plus one partial cell at (slabX, 4, slabZ). */
    private record ShapeWorld(int slabX, int slabZ, WorldSolidityProbe probe, Position player)
            implements MobEntity.WorldQuery, ItemEntity.Ground, FallingBlockEntity.Ground {

        record WorldSolidityProbe(boolean slab, boolean topHalf) {
        }

        private boolean inSlabCell(double x, double y, double z) {
            return probe.slab()
                    && (int) Math.floor(x) == slabX
                    && (int) Math.floor(z) == slabZ
                    && y >= 4.0 && y < 5.0;
        }

        @Override public boolean isSolid(double x, double y, double z) {
            return y < 4.0 || inSlabCell(x, y, z);
        }

        @Override public boolean isSolidAt(double x, double y, double z) {
            if (y < 4.0) {
                return true;
            }
            if (!inSlabCell(x, y, z)) {
                return false;
            }
            return probe.topHalf() ? (y - Math.floor(y)) >= 0.5 : (y - Math.floor(y)) < 0.5;
        }

        @Override public double supportY(double x, double y, double z) {
            if (inSlabCell(x, y, z)) {
                return probe.topHalf() ? 5.0 : 4.5;
            }
            return y < 4.0 ? Math.floor(y) + 1.0 : Double.NEGATIVE_INFINITY;
        }

        @Override public Position nearestPlayer(double x, double y, double z, double range) {
            if (player == null) {
                return null;
            }
            double dx = player.x() - x;
            double dy = player.y() - y;
            double dz = player.z() - z;
            return dx * dx + dy * dy + dz * dz <= range * range ? player : null;
        }
    }

    @Test
    void aWalkingMobStepsOntoTheSlabHalfAndDownTheOtherSide() {
        // The zombie hunts a player east of the slab cell at x=5: the chase
        // walks it into the half, up the shape surface, and down the far side.
        ShapeWorld world = new ShapeWorld(5, 0,
                new ShapeWorld.WorldSolidityProbe(true, false), new Position(9.5, 4.0, 0.5));
        MobEntity zombie = new MobEntity(1, MobType.ZOMBIE,
                new Position(2.5, 4.0, 0.5), new Random(7), world);
        double maxY = 0.0;
        boolean crossed = false;
        for (int i = 0; i < 400; i++) {
            zombie.tick();
            maxY = Math.max(maxY, zombie.position().y());
            if (zombie.position().x() > 6.5 && zombie.position().y() <= 4.0001) {
                crossed = true;
                break;
            }
        }
        assertTrue(crossed, "the zombie crossed the slab cell");
        assertEquals(4.5, maxY, 0.0001, "the step surface is the slab's half, never a full jump");
    }

    @Test
    void theFenceHeightRefusesTheStepLikeAWall() {
        // A fence wall across the whole z line at x=5: its 1.5 shape support
        // (5.5) exceeds the one-block step, so the body stays west of it.
        MobEntity.WorldQuery fenceWall = new MobEntity.WorldQuery() {
            @Override public boolean isSolid(double x, double y, double z) {
                return y < 4.0;
            }

            @Override public boolean isSolidAt(double x, double y, double z) {
                return y < 4.0
                        || ((int) Math.floor(x) == 5 && y >= 4.0 && y < 5.5);
            }

            @Override public double supportY(double x, double y, double z) {
                if ((int) Math.floor(x) == 5 && y >= 4.0 && y < 5.5) {
                    return 5.5;
                }
                return y < 4.0 ? Math.floor(y) + 1.0 : Double.NEGATIVE_INFINITY;
            }

            @Override public Position nearestPlayer(double x, double y, double z, double range) {
                Position player = new Position(9.5, 4.0, 0.5);
                double dx = player.x() - x;
                double dy = player.y() - y;
                double dz = player.z() - z;
                return dx * dx + dy * dy + dz * dz <= range * range ? player : null;
            }
        };
        MobEntity zombie = new MobEntity(1, MobType.ZOMBIE,
                new Position(2.5, 4.0, 0.5), new Random(7), fenceWall);
        for (int i = 0; i < 300; i++) {
            zombie.tick();
            assertTrue(zombie.position().x() < 5.05,
                    "the body never entered the fence cell: " + zombie.position());
            assertTrue(zombie.position().y() < 4.9,
                    "the body never stepped the fence's 1.5: " + zombie.position().y());
        }
    }

    @Test
    void aTopSlabSupportsAtTheCellCeiling() {
        ShapeWorld world = new ShapeWorld(5, 0,
                new ShapeWorld.WorldSolidityProbe(true, true), new Position(9.5, 4.0, 0.5));
        MobEntity zombie = new MobEntity(1, MobType.ZOMBIE,
                new Position(2.5, 4.0, 0.5), new Random(7), world);
        boolean crossed = false;
        for (int i = 0; i < 400; i++) {
            zombie.tick();
            if (zombie.position().x() > 6.5 && zombie.position().y() <= 4.0001) {
                crossed = true;
                break;
            }
        }
        assertTrue(crossed, "the zombie crossed the top-slab cell on its ceiling");
    }

    @Test
    void itemsRestOnTheSlabSurface() {
        ShapeWorld world = new ShapeWorld(0, 0,
                new ShapeWorld.WorldSolidityProbe(true, false), null);
        var stack = net.zaminmc.torch.item.ItemStack.of(
                net.zaminmc.torch.server.item.BuiltinItems.DIRT, 1);
        ItemEntity item = new ItemEntity(7, new Position(0.5, 8.0, 0.5), stack, 0);
        for (int i = 0; i < 300; i++) {
            item.tick(world);
        }
        assertTrue(item.onGround(), "the item settled");
        assertEquals(4.5 + ItemEntity.HALF_HEIGHT, item.position().y(), 1e-9,
                "the item's bottom rests on the slab half");
    }

    @Test
    void fallingBlocksLandOnSlabsAsTheCellAbove() {
        ShapeWorld world = new ShapeWorld(0, 0,
                new ShapeWorld.WorldSolidityProbe(true, false), null);
        FallingBlockEntity sand = new FallingBlockEntity(9, BuiltinBlocks.SAND,
                new Position(0.5, 8.0, 0.5));
        FallingBlockEntity.Step step = FallingBlockEntity.Step.MOVED;
        for (int i = 0; i < 300 && step == FallingBlockEntity.Step.MOVED; i++) {
            step = sand.tick(world);
        }
        assertEquals(FallingBlockEntity.Step.LANDED, step, "the falling block landed on the slab");
        assertEquals(4.5 + FallingBlockEntity.HALF_HEIGHT, sand.position().y(), 1e-9,
                "the body rests on the half");
        assertEquals(5, sand.landingPosition().y(), "the block form lands on the cell above");
    }
}
