package net.zaminmc.torch.server.block;

import net.zaminmc.torch.server.entity.FallingBlockEntity;
import net.zaminmc.torch.server.entity.ItemEntity;
import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The collision-shape slice's contract: the shape queries resolve through
 * the {@link CollisionShapes} table — every partial block's own collision
 * boxes ported 1:1 from the reference (the slabs' halves, the fences'
 * 1.5-tall post with its connection arms, the wall's opening post, the
 * closed gate's panel, the trapdoors' floor/ceiling/open panels, the doors'
 * thin panels per facing and hinge, the chest family's inset box with the
 * double form's extended edge, the panes' cross, the thin covers, the
 * partial furniture) — and the three body models (mob, item, falling
 * block) land on the shape surfaces through the shape-aware world queries.
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
    private record ShapeWorld(int slabX, int slabZ, WorldSolidityProbe probe, Position player,
                              boolean corridor)
            implements MobEntity.WorldQuery, ItemEntity.Ground, FallingBlockEntity.Ground {

        /**
         * Wave TF-9 — the two Ground families now both declare the
         * dimension flip default; an explicit no-op keeps the historical
         * stub's overworld-only reads (the plain overworld stub world).
         */
        @Override
        public void setActiveDimension(int dimension) {
        }

        /**
         * The two families' shared consult (Wave IO-F — the friction
         * row): the stub resolves the diamond the two interfaces'
         * identical 0.6 defaults would otherwise raise, keeping the
         * common-case drag both declare.
         */
        public double slipperinessBelow(double x, double feetY, double z) {
            return 0.6;
        }

        /**
         * VT-006 — the two Ground families now both declare the portal
         * consult default; the stub resolves the diamond keeping the
         * no-portals answer (the plain overworld stub world).
         */
        @Override
        public net.zaminmc.torch.server.world.PortalFrameBuilder.PortalShapeMatch portalFrameAt(
                int x, int y, int z) {
            return null;
        }

        /** The open-plains shape: no corridor walls (the historical stub). */
        ShapeWorld(int slabX, int slabZ, WorldSolidityProbe probe, Position player) {
            this(slabX, slabZ, probe, player, false);
        }

        record WorldSolidityProbe(boolean slab, boolean topHalf) {
        }

        private boolean inSlabCell(double x, double y, double z) {
            return probe.slab()
                    && (int) Math.floor(x) == slabX
                    && (int) Math.floor(z) == slabZ
                    && y >= 4.0 && y < 5.0;
        }

        private boolean inCorridorWall(double x, double y, double z) {
            return corridor && y >= 4.0
                    && Math.abs((int) Math.floor(z) - slabZ) >= 1;
        }

        @Override public boolean isSolid(double x, double y, double z) {
            return y < 4.0 || inSlabCell(x, y, z) || inCorridorWall(x, y, z);
        }

        @Override public boolean isSolidAt(double x, double y, double z) {
            if (y < 4.0) {
                return true;
            }
            if (inCorridorWall(x, y, z)) {
                return true;
            }
            if (!inSlabCell(x, y, z)) {
                return false;
            }
            return probe.topHalf() ? (y - Math.floor(y)) >= 0.5 : (y - Math.floor(y)) < 0.5;
        }

        @Override public double supportY(double x, double y, double z) {
            if (inCorridorWall(x, y, z)) {
                return Double.NEGATIVE_INFINITY; // walls are no walking surface
            }
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
        // walks it into the half, up the shape surface, and down the far
        // side. The corridor walls pin the route to the slab row — open
        // ground lets the pathfinder (correctly) walk around the slab.
        ShapeWorld world = new ShapeWorld(5, 0,
                new ShapeWorld.WorldSolidityProbe(true, false), new Position(9.5, 4.0, 0.5),
                true);
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
    void fallingBlocksRestOnSlabHalvesInTheSlabsOwnCell() {
        ShapeWorld world = new ShapeWorld(0, 0,
                new ShapeWorld.WorldSolidityProbe(true, false), null);
        FallingBlockEntity sand = new FallingBlockEntity(9, BuiltinBlocks.SAND,
                new Position(0.5, 8.0, 0.5));
        FallingBlockEntity.Step step = FallingBlockEntity.Step.MOVED;
        for (int i = 0; i < 300 && step == FallingBlockEntity.Step.MOVED; i++) {
            step = sand.tick(world);
        }
        assertEquals(FallingBlockEntity.Step.LANDED, step, "the falling block landed on the slab");
        assertEquals(4.5, sand.position().y(), 1e-9,
                "the box bottom rests on the half (the vanilla moveEntity clamp)");
        assertEquals(4, sand.landingPosition().y(),
                "the landing cell is the slab's own cell (BlockPos floors the resting "
                        + "bottom 4.5 to 4) — the vanilla quirk: canPlace fails on the "
                        + "non-replaceable slab, so the landing pops the stack as an item");
    }

    // --- the collision-shape table (the per-block boxes, 1:1) ---------------

    /** The probe stub: the neighbour map keyed "dx,dy,dz". */
    private static CollisionShapes.NeighborProbe probe(java.util.Map<String, net.zaminmc.torch.block.BlockType> cells) {
        return (dx, dy, dz) -> cells.get(dx + "," + dy + "," + dz);
    }

    private static final java.util.Map<String, net.zaminmc.torch.block.BlockType> NO_NEIGHBORS =
            java.util.Map.of();

    @Test
    void theFencePostCarvesItsCellAndTheArmsReachTheConnections() {
        // The isolated post (FenceBlock.addCollisions): the 0.375-0.625
        // square, 1.5 tall — the body walks the corners of the cell.
        assertTrue(CollisionShapes.contains(BuiltinBlocks.FENCE, 0.5, 64.5, 0.5, probe(NO_NEIGHBORS)),
                "the post's centre column is solid");
        assertFalse(CollisionShapes.contains(BuiltinBlocks.FENCE, 0.2, 64.5, 0.5, probe(NO_NEIGHBORS)),
                "the west shoulder is open");
        assertFalse(CollisionShapes.contains(BuiltinBlocks.FENCE, 0.5, 64.5, 0.2, probe(NO_NEIGHBORS)),
                "the north shoulder is open");
        assertTrue(CollisionShapes.contains(BuiltinBlocks.FENCE, 0.5, 64.9, 0.5, probe(NO_NEIGHBORS)),
                "the post fills its own cell to the top");
        assertEquals(65.5, CollisionShapes.top(BuiltinBlocks.FENCE, 0.5, 64.2, 0.5, probe(NO_NEIGHBORS)),
                1e-9, "the support is the post's 1.5 top");
        // The north arm: a same-material neighbour opens the post's z band.
        java.util.Map<String, net.zaminmc.torch.block.BlockType> north = java.util.Map.of(
                "0,0,-1", BuiltinBlocks.FENCE);
        assertTrue(CollisionShapes.contains(BuiltinBlocks.FENCE, 0.5, 64.5, 0.1, probe(north)),
                "the north arm spans the cell's north edge");
        // The arm band stays inside the post's x: 0.375-0.625.
        assertFalse(CollisionShapes.contains(BuiltinBlocks.FENCE, 0.2, 64.5, 0.1, probe(north)),
                "the arm is as narrow as the post");
        // A full cube connects too (shouldConnectTo's material rule).
        java.util.Map<String, net.zaminmc.torch.block.BlockType> cube = java.util.Map.of(
                "0,0,-1", BuiltinBlocks.STONE);
        assertTrue(CollisionShapes.contains(BuiltinBlocks.FENCE, 0.5, 64.5, 0.1, probe(cube)),
                "a full cube opens the arm");
        // The pumpkin never connects (the Material.PUMPKIN quirk).
        java.util.Map<String, net.zaminmc.torch.block.BlockType> pumpkin = java.util.Map.of(
                "0,0,-1", BuiltinBlocks.PUMPKIN);
        assertFalse(CollisionShapes.contains(BuiltinBlocks.FENCE, 0.5, 64.5, 0.1, probe(pumpkin)),
                "the pumpkin stays excluded");
        // The nether brick fence is Material.STONE: it never joins the woods.
        java.util.Map<String, net.zaminmc.torch.block.BlockType> nether = java.util.Map.of(
                "0,0,-1", BuiltinBlocks.NETHER_BRICK_FENCE);
        assertFalse(CollisionShapes.contains(BuiltinBlocks.FENCE, 0.5, 64.5, 0.1, probe(nether)),
                "the nether brick never joins a wood fence");
        assertTrue(CollisionShapes.contains(BuiltinBlocks.NETHER_BRICK_FENCE, 0.5, 64.5, 0.1,
                        probe(java.util.Map.of("0,0,-1", BuiltinBlocks.NETHER_BRICK_FENCE))),
                "the nether brick joins its own kind");
    }

    @Test
    void theWallOpensItsPostToTheConnectionsAndStandsOneAndAHalf() {
        // The isolated post (WallBlock): the 0.25-0.75 square, 1.5 tall.
        assertTrue(CollisionShapes.contains(WallBlocks.state(0), 0.3, 64.5, 0.3,
                        probe(NO_NEIGHBORS)),
                "the post's inner column is solid");
        assertFalse(CollisionShapes.contains(WallBlocks.state(0), 0.1, 64.5, 0.3,
                        probe(NO_NEIGHBORS)),
                "the west shoulder is open");
        assertTrue(CollisionShapes.contains(WallBlocks.state(0), 0.5, 65.4, 0.5,
                        probe(NO_NEIGHBORS)),
                "the collision maxY is 1.5");
        assertEquals(65.5, CollisionShapes.top(WallBlocks.state(0), 0.5, 64.2, 0.5,
                        probe(NO_NEIGHBORS)), 1e-9, "the wall's support is the 1.5 top");
        // The straight pair (N+S, no W/E): the 0.3125-0.6875 thin band,
        // both z faces open to the cell edge.
        java.util.Map<String, net.zaminmc.torch.block.BlockType> straight = java.util.Map.of(
                "0,0,-1", WallBlocks.state(0),
                "0,0,1", WallBlocks.state(1));
        assertTrue(CollisionShapes.contains(WallBlocks.state(0), 0.35, 64.5, 0.05,
                        probe(straight)),
                "the straight wall opens both z faces (the mossy variant joins: one block in the reference)");
        assertFalse(CollisionShapes.contains(WallBlocks.state(0), 0.3, 64.5, 0.05,
                        probe(straight)),
                "the straight band is the thin 0.3125-0.6875 x range");
        assertTrue(CollisionShapes.contains(WallBlocks.state(0), 0.5, 64.5, 0.95,
                        probe(straight)),
                "the south face joins the same way");
    }

    @Test
    void theClosedGateIsAThinPanelAndTheOpenGateVanishes() {
        // The closed south-facing gate (axis Z): x 0-1, z 0.375-0.625, 1.5 tall.
        net.zaminmc.torch.block.BlockType closedSouth =
                FenceGateBlocks.state("oak_fence_gate", 0, false, false);
        assertTrue(CollisionShapes.contains(closedSouth, 0.1, 64.5, 0.5, probe(NO_NEIGHBORS)),
                "the panel spans the cell's x");
        assertFalse(CollisionShapes.contains(closedSouth, 0.1, 64.5, 0.2, probe(NO_NEIGHBORS)),
                "the passage beside the panel is open");
        assertTrue(CollisionShapes.contains(closedSouth, 0.5, 65.4, 0.5, probe(NO_NEIGHBORS)),
                "the panel stands 1.5 tall");
        assertEquals(65.5, CollisionShapes.top(closedSouth, 0.5, 64.2, 0.5, probe(NO_NEIGHBORS)),
                1e-9, "the closed gate's support is the 1.5 top");
        // The closed west-facing gate (axis X): x 0.375-0.625, z 0-1.
        net.zaminmc.torch.block.BlockType closedWest =
                FenceGateBlocks.state("oak_fence_gate", 1, false, false);
        assertTrue(CollisionShapes.contains(closedWest, 0.5, 64.5, 0.1, probe(NO_NEIGHBORS)),
                "the west gate's panel spans the cell's z");
        assertFalse(CollisionShapes.contains(closedWest, 0.2, 64.5, 0.1, probe(NO_NEIGHBORS)),
                "the passage beside the panel is open");
        // The open gate carries no box at all (the reference's null).
        net.zaminmc.torch.block.BlockType open =
                FenceGateBlocks.state("oak_fence_gate", 0, true, false);
        assertFalse(CollisionShapes.contains(open, 0.5, 64.5, 0.5, probe(NO_NEIGHBORS)),
                "the open gate never blocks");
        assertEquals(Double.NEGATIVE_INFINITY,
                CollisionShapes.top(open, 0.5, 64.2, 0.5, probe(NO_NEIGHBORS)),
                "the open gate holds no body");
    }

    @Test
    void trapdoorsCarryTheFloorTheCeilingAndTheOpenPanel() {
        // The closed bottom trapdoor: the 3/16 floor panel.
        net.zaminmc.torch.block.BlockType closedBottom =
                TrapdoorBlocks.state("trapdoor", 0, false, false);
        assertTrue(CollisionShapes.contains(closedBottom, 0.5, 64.1, 0.5, probe(NO_NEIGHBORS)),
                "the floor panel is solid at its 3/16 height");
        assertFalse(CollisionShapes.contains(closedBottom, 0.5, 64.3, 0.5, probe(NO_NEIGHBORS)),
                "above the panel the cell is open");
        assertEquals(64.1875, CollisionShapes.top(closedBottom, 0.5, 64.05, 0.5,
                        probe(NO_NEIGHBORS)), 1e-9, "the floor's surface is 3/16");
        // The closed top trapdoor: the matching ceiling panel.
        net.zaminmc.torch.block.BlockType closedTop =
                TrapdoorBlocks.state("trapdoor", 0, false, true);
        assertFalse(CollisionShapes.contains(closedTop, 0.5, 64.3, 0.5, probe(NO_NEIGHBORS)),
                "beneath the ceiling panel the cell is open");
        assertTrue(CollisionShapes.contains(closedTop, 0.5, 64.9, 0.5, probe(NO_NEIGHBORS)),
                "the ceiling panel is solid at its 13/16 floor");
        // The open north trapdoor: the vertical panel against the south wall.
        net.zaminmc.torch.block.BlockType openNorth =
                TrapdoorBlocks.state("trapdoor", 0, true, false);
        assertTrue(CollisionShapes.contains(openNorth, 0.5, 64.5, 0.95, probe(NO_NEIGHBORS)),
                "the open panel stands against the cell's south edge");
        assertFalse(CollisionShapes.contains(openNorth, 0.5, 64.5, 0.5, probe(NO_NEIGHBORS)),
                "the open trapdoor's passage is open");
        // The open east trapdoor: the panel against the west wall.
        net.zaminmc.torch.block.BlockType openEast =
                TrapdoorBlocks.state("trapdoor", 3, true, false);
        assertTrue(CollisionShapes.contains(openEast, 0.05, 64.5, 0.5, probe(NO_NEIGHBORS)),
                "the open east panel stands against the cell's west edge");
    }

    @Test
    void theChestFamilyCarriesTheInsetBoxAndTheDoubleExtendsItsEdge() {
        // The single chest (ChestBlock): the 1/16-inset 14/16 box.
        assertTrue(CollisionShapes.contains(BuiltinBlocks.CHEST, 0.5, 64.5, 0.5,
                        probe(NO_NEIGHBORS)),
                "the chest's core is solid");
        assertFalse(CollisionShapes.contains(BuiltinBlocks.CHEST, 0.03, 64.5, 0.5,
                        probe(NO_NEIGHBORS)),
                "the 1/16 inset is open");
        assertFalse(CollisionShapes.contains(BuiltinBlocks.CHEST, 0.5, 64.95, 0.5,
                        probe(NO_NEIGHBORS)),
                "the box stands 14/16 tall");
        assertEquals(64.875, CollisionShapes.top(BuiltinBlocks.CHEST, 0.5, 64.2, 0.5,
                        probe(NO_NEIGHBORS)), 1e-9, "the lid's surface is 14/16");
        // The double form: the joined edge extends to the cell boundary.
        assertTrue(CollisionShapes.contains(BuiltinBlocks.CHEST, 0.5, 64.5, 0.03,
                        probe(java.util.Map.of("0,0,-1", BuiltinBlocks.CHEST))),
                "the north-joined edge opens to the boundary");
        assertFalse(CollisionShapes.contains(BuiltinBlocks.CHEST, 0.5, 64.5, 0.95,
                        probe(java.util.Map.of("0,0,-1", BuiltinBlocks.CHEST))),
                "the unjoined south edge keeps its inset");
        assertFalse(CollisionShapes.contains(BuiltinBlocks.CHEST, 0.03, 64.5, 0.5,
                        probe(java.util.Map.of("0,0,-1", BuiltinBlocks.CHEST))),
                "the x insets never move on a north join");
        // The trapped chest and the ender chest share the family box.
        assertTrue(CollisionShapes.contains(BuiltinBlocks.TRAPPED_CHEST, 0.5, 64.5, 0.5,
                probe(NO_NEIGHBORS)), "the trapped chest carries the same box");
        assertTrue(CollisionShapes.contains(new EngineBlockType(Identifier.parse("minecraft:ender_chest_south"), "Ender Chest"), 0.5, 64.5, 0.5,
                probe(NO_NEIGHBORS)), "the ender chest carries the same box");
    }

    @Test
    void theThinCoversCarryTheirOwnHeights() {
        // The carpet (CarpetBlock): the 1/16 sheet.
        assertTrue(CollisionShapes.contains(new EngineBlockType(DyeFamilyBlocks.carpetBlock(0), "Carpet"), 0.5, 64.03, 0.5,
                        probe(NO_NEIGHBORS)), "the carpet's 1/16 sheet is solid");
        assertFalse(CollisionShapes.contains(new EngineBlockType(DyeFamilyBlocks.carpetBlock(0), "Carpet"), 0.5, 64.1, 0.5,
                        probe(NO_NEIGHBORS)), "above the sheet the cell is open");
        assertEquals(64.0625, CollisionShapes.top(new EngineBlockType(DyeFamilyBlocks.carpetBlock(0), "Carpet"), 0.5, 64.01, 0.5,
                        probe(NO_NEIGHBORS)), 1e-9, "the carpet's surface is 1/16");
        // The snow layer (SnowLayerBlock meta 0): the 2/16 cover.
        assertEquals(64.125, CollisionShapes.top(BuiltinBlocks.SNOW_LAYER, 0.5, 64.01, 0.5,
                        probe(NO_NEIGHBORS)), 1e-9, "the snow layer's surface is 2/16");
        // The lily pad (LilyPadBlock): the 1/64 film over the full cell.
        assertEquals(64.015625, CollisionShapes.top(BuiltinBlocks.WATERLILY, 0.5, 64.005, 0.5,
                        probe(NO_NEIGHBORS)), 1e-9, "the lily pad's surface is 1/64");
        // The stone pressure plate (AbstractPressurePlateBlock): the 1/32 disc.
        assertEquals(64.03125, CollisionShapes.top(net.zaminmc.torch.server.redstone.RedstoneBlocks.plateOf(false, false), 0.5,
                        64.005, 0.5, probe(NO_NEIGHBORS)), 1e-9,
                "the plate's surface is 1/32");
        assertFalse(CollisionShapes.contains(net.zaminmc.torch.server.redstone.RedstoneBlocks.plateOf(false, false), 0.03, 64.01,
                        0.5, probe(NO_NEIGHBORS)), "the plate's 1/16 rim is open");
        // The weighted plates share the disc.
        assertEquals(64.03125, CollisionShapes.top(
                        net.zaminmc.torch.server.redstone.WeightedPlateBlocks.state(0, 0), 0.5, 64.005, 0.5,
                        probe(NO_NEIGHBORS)), 1e-9, "the light plate's surface is 1/32");
    }

    @Test
    void thePartialFurnitureCarriesTheReferenceBoxes() {
        // The flower pot (FlowerPotBlock): the 3/8 box, 0.3125-0.6875 square.
        assertTrue(CollisionShapes.contains(FlowerPotBlocks.state(0), 0.5, 64.2, 0.5,
                        probe(NO_NEIGHBORS)), "the pot's core is solid");
        assertFalse(CollisionShapes.contains(FlowerPotBlocks.state(0), 0.2, 64.2, 0.5,
                        probe(NO_NEIGHBORS)), "the pot's shoulder is open");
        assertEquals(64.375, CollisionShapes.top(FlowerPotBlocks.state(0), 0.5, 64.1, 0.5,
                        probe(NO_NEIGHBORS)), 1e-9, "the pot's rim is 3/8");
        // The cake (CakeBlock): the bite shrinks the eaten side only.
        net.zaminmc.torch.block.BlockType whole = CakeBlocks.state(0);
        assertEquals(64.5, CollisionShapes.top(whole, 0.5, 64.1, 0.5, probe(NO_NEIGHBORS)),
                1e-9, "the whole cake stands 1/2 tall");
        assertTrue(CollisionShapes.contains(whole, 0.1, 64.2, 0.5, probe(NO_NEIGHBORS)),
                "the whole cake's west face is 1/16 inset");
        net.zaminmc.torch.block.BlockType threeBites = CakeBlocks.state(3);
        assertFalse(CollisionShapes.contains(threeBites, 0.1, 64.2, 0.5, probe(NO_NEIGHBORS)),
                "the third bite eats the west face down to 7/16");
        assertTrue(CollisionShapes.contains(threeBites, 0.5, 64.2, 0.5, probe(NO_NEIGHBORS)),
                "the far side keeps its 1/16 inset");
        // The cactus (CactusBlock): the 1/16 inset, full height.
        assertFalse(CollisionShapes.contains(BuiltinBlocks.CACTUS, 0.03, 64.5, 0.5,
                        probe(NO_NEIGHBORS)), "the cactus' inset is open");
        // The farmland (FarmlandBlock): the 15/16 field.
        assertEquals(64.9375, CollisionShapes.top(BuiltinBlocks.FARMLAND, 0.5, 64.2, 0.5,
                        probe(NO_NEIGHBORS)), 1e-9, "the farmland's surface is 15/16");
        // The enchanting table: the 3/4 block.
        assertEquals(64.75, CollisionShapes.top(BuiltinBlocks.ENCHANTING_TABLE, 0.5, 64.2, 0.5,
                        probe(NO_NEIGHBORS)), 1e-9, "the table's surface is 3/4");
        // The daylight detector: the 3/8 slab.
        assertEquals(64.375, CollisionShapes.top(net.zaminmc.torch.server.daylight.DaylightDetectorBlocks.ofPower(0), 0.5, 64.1,
                        0.5, probe(NO_NEIGHBORS)), 1e-9, "the detector's surface is 3/8");
        // The end portal frame: the 13/16 base; the eye adds the centre post.
        assertEquals(64.8125, CollisionShapes.top(BuiltinBlocks.END_PORTAL_FRAME_S, 0.5,
                        64.2, 0.5, probe(NO_NEIGHBORS)), 1e-9,
                "the frame's base is 13/16");
        assertTrue(CollisionShapes.contains(BuiltinBlocks.END_PORTAL_FRAME_EYE_S, 0.5,
                        64.9, 0.5, probe(NO_NEIGHBORS)),
                "the eye's post stands on the base");
        // The bed: the 9/16 mattress.
        assertEquals(64.5625, CollisionShapes.top(BuiltinBlocks.BED_FOOT_SOUTH, 0.5, 64.2, 0.5,
                        probe(NO_NEIGHBORS)), 1e-9, "the bed's surface is 9/16");
    }

    @Test
    void theBrewingStandTheCauldronAndTheHopperCarryTheirOwnColumns() {
        // The brewing stand: the 1/8 base plate + the centre post 7/8 tall.
        assertTrue(CollisionShapes.contains(net.zaminmc.torch.server.brewing.BrewingStandBlocks.of(false, false, false), 0.1, 64.05, 0.1,
                        probe(NO_NEIGHBORS)), "the base plate fills the foot");
        assertFalse(CollisionShapes.contains(net.zaminmc.torch.server.brewing.BrewingStandBlocks.of(false, false, false), 0.1, 64.5, 0.1,
                        probe(NO_NEIGHBORS)), "above the base only the post stands");
        assertTrue(CollisionShapes.contains(net.zaminmc.torch.server.brewing.BrewingStandBlocks.of(false, false, false), 0.5, 64.5, 0.5,
                        probe(NO_NEIGHBORS)), "the post's centre is solid");
        assertEquals(64.875, CollisionShapes.top(net.zaminmc.torch.server.brewing.BrewingStandBlocks.of(false, false, false), 0.5, 64.2, 0.5,
                        probe(NO_NEIGHBORS)), 1e-9, "the post's top is 7/8");
        assertEquals(64.125, CollisionShapes.top(net.zaminmc.torch.server.brewing.BrewingStandBlocks.of(false, false, false), 0.1, 64.05, 0.1,
                        probe(NO_NEIGHBORS)), 1e-9, "the base's surface is 1/8");
        // The cauldron: the 5/16 floor + the full-height leg columns.
        assertTrue(CollisionShapes.contains(net.zaminmc.torch.server.cauldron.CauldronBlocks.of(0), 0.05, 64.5, 0.05,
                        probe(NO_NEIGHBORS)), "the leg column stands full height");
        assertFalse(CollisionShapes.contains(net.zaminmc.torch.server.cauldron.CauldronBlocks.of(0), 0.5, 64.5, 0.5,
                        probe(NO_NEIGHBORS)), "the basin's middle holds the water, not a body");
        assertEquals(65.0, CollisionShapes.top(net.zaminmc.torch.server.cauldron.CauldronBlocks.of(0), 0.05, 64.5, 0.05,
                        probe(NO_NEIGHBORS)), 1e-9, "the leg's top is the cell ceiling");
        // The hopper: the 5/8 funnel + the same leg columns.
        assertTrue(CollisionShapes.contains(net.zaminmc.torch.server.hopper.HopperBlocks.hopperOf(0, true), 0.05, 64.5, 0.05,
                        probe(NO_NEIGHBORS)), "the hopper's leg stands full height");
        assertFalse(CollisionShapes.contains(net.zaminmc.torch.server.hopper.HopperBlocks.hopperOf(0, true), 0.5, 64.9, 0.5,
                        probe(NO_NEIGHBORS)), "above the funnel the middle is open");
        assertEquals(64.625, CollisionShapes.top(net.zaminmc.torch.server.hopper.HopperBlocks.hopperOf(0, true), 0.5, 64.2, 0.5,
                        probe(NO_NEIGHBORS)), 1e-9, "the funnel's surface is 5/8");
    }

    @Test
    void thePanesCarryTheCrossAndTheHalfArms() {
        // The isolated pane (PaneBlock.addCollisions' else arms): the full
        // cross — the E/W bar spans x, the N/S bar spans z.
        assertTrue(CollisionShapes.contains(BuiltinBlocks.GLASS_PANE, 0.1, 64.5, 0.5,
                        probe(NO_NEIGHBORS)), "the isolated cross spans the cell's x");
        assertTrue(CollisionShapes.contains(BuiltinBlocks.GLASS_PANE, 0.5, 64.5, 0.1,
                        probe(NO_NEIGHBORS)), "the isolated cross spans the cell's z");
        assertFalse(CollisionShapes.contains(BuiltinBlocks.GLASS_PANE, 0.1, 64.5, 0.1,
                        probe(NO_NEIGHBORS)), "the cross's corners are open");
        // A west connection: the west half arm only.
        java.util.Map<String, net.zaminmc.torch.block.BlockType> west =
                java.util.Map.of("-1,0,0", BuiltinBlocks.GLASS_PANE);
        assertTrue(CollisionShapes.contains(BuiltinBlocks.GLASS_PANE, 0.25, 64.5, 0.5, probe(west)),
                "the west half arm is solid");
        assertFalse(CollisionShapes.contains(BuiltinBlocks.GLASS_PANE, 0.75, 64.5, 0.5, probe(west)),
                "the east half stays open without a connection");
        assertFalse(CollisionShapes.contains(BuiltinBlocks.GLASS_PANE, 0.5, 64.5, 0.1, probe(west)),
                "the z bar vanishes without a z connection");
        // The glass connects (shouldConnectTo's own glass pair).
        assertTrue(CollisionShapes.contains(BuiltinBlocks.IRON_BARS, 0.25, 64.5, 0.5,
                        probe(java.util.Map.of("-1,0,0", BuiltinBlocks.GLASS))),
                "the plain glass opens the bars' arm");
    }

    @Test
    void theDoorsCarryTheThinPanelsPerFacingAndHinge() {
        // The closed oak lower, band 0 (the reference's EAST facing): the
        // panel against the cell's west edge.
        assertTrue(CollisionShapes.contains(BuiltinBlocks.OAK_DOOR_LOWER_CLOSED_W, 0.1, 64.5,
                        0.5, probe(NO_NEIGHBORS)), "the closed panel hugs the west edge");
        assertFalse(CollisionShapes.contains(BuiltinBlocks.OAK_DOOR_LOWER_CLOSED_W, 0.5, 64.5,
                        0.5, probe(NO_NEIGHBORS)), "the passage is open");
        assertEquals(65.0, CollisionShapes.top(BuiltinBlocks.OAK_DOOR_LOWER_CLOSED_W, 0.1,
                        64.2, 0.5, probe(NO_NEIGHBORS)), 1e-9,
                "the panel's own support is the cell ceiling");
        // Band 2 (the reference's WEST facing): the panel against the east edge.
        assertTrue(CollisionShapes.contains(BuiltinBlocks.OAK_DOOR_LOWER_CLOSED_E, 0.95, 64.5,
                        0.5, probe(NO_NEIGHBORS)), "the east band's panel hugs the east edge");
        // The open door keeps its panel — swung against the hinge wall. The
        // bare lower (band 0, EAST facing) with no hinge bit: the north wall.
        assertTrue(CollisionShapes.contains(BuiltinBlocks.OAK_DOOR_LOWER_OPEN_W, 0.5, 64.5,
                        0.1, probe(NO_NEIGHBORS)), "the open left-hinged panel hugs the north wall");
        assertFalse(CollisionShapes.contains(BuiltinBlocks.OAK_DOOR_LOWER_OPEN_W, 0.5, 64.5,
                        0.5, probe(NO_NEIGHBORS)), "the open passage is clear");
        // The hinge lives on the upper half: a right-hinged upper above the
        // bare lower swings the same lower's panel to the south wall.
        java.util.Map<String, net.zaminmc.torch.block.BlockType> rightHinged =
                java.util.Map.of("0,1,0", new EngineBlockType(Identifier.parse("minecraft:oak_door_upper_right"), "Oak Door"));
        assertTrue(CollisionShapes.contains(BuiltinBlocks.OAK_DOOR_LOWER_OPEN_W, 0.5, 64.5,
                        0.95, probe(rightHinged)),
                "the right hinge swings the open panel to the south wall (the reference's combined read)");
        assertFalse(CollisionShapes.contains(BuiltinBlocks.OAK_DOOR_LOWER_OPEN_W, 0.5, 64.5,
                        0.1, probe(rightHinged)), "the north side opens on the right hinge");
        // The upper half resolves its facing and open bits from the lower
        // below (getCombinedMetadata's own walk).
        java.util.Map<String, net.zaminmc.torch.block.BlockType> openLowerBelow =
                java.util.Map.of("0,-1,0", BuiltinBlocks.OAK_DOOR_LOWER_OPEN_W);
        assertTrue(CollisionShapes.contains(BuiltinBlocks.OAK_DOOR_UPPER, 0.5, 65.5, 0.1,
                        probe(openLowerBelow)),
                "the upper reads the lower's open band: the panel hugs the north wall");
        assertFalse(CollisionShapes.contains(BuiltinBlocks.OAK_DOOR_UPPER, 0.1, 65.5, 0.5,
                        probe(openLowerBelow)), "the closed-west panel is gone when open");
    }
}
