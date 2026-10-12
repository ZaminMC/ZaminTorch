package net.zaminmc.torch.server.world.structure;

import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.BlockRegistryBuilder;
import net.zaminmc.torch.server.block.BlockRegistryBuilder.FrozenBlockRegistry;
import net.zaminmc.torch.server.item.BuiltinItems;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The fortress placement (Slice 12d — the reference port of
 * FortressStructure + FortressPieces over the engine's deterministic
 * generator): the cell-based 1-in-3 pick, the weight-table walks with the
 * connector-repeat rule, the BridgeEnd fallback, the band re-base, and
 * the same-seed regeneration identity.
 */
class FortressStructureTest {

    private static FrozenBlockRegistry registry;
    private static StructurePalette palette;

    @BeforeAll
    static void setUp() {
        // The engine's own boot assembly (EngineServer's freeze chain).
        registry = net.zaminmc.torch.server.brewing.BrewingStandBlocks.registerAll(
                net.zaminmc.torch.server.dispenser.DispenserSystem.registerBlocks(
                net.zaminmc.torch.server.hopper.HopperSystem.registerBlocks(
                net.zaminmc.torch.server.piston.PistonSystem.registerBlocks(
                net.zaminmc.torch.server.redstone.TripwireBlocks.registerAll(
                net.zaminmc.torch.server.redstone.RedstoneBlocks.registerAll(
                        net.zaminmc.torch.server.block.FluidBlocks.registerAll(
                                BuiltinBlocks.registerAll(new BlockRegistryBuilder())))))))).freeze();
        palette = StructurePalette.of(registry);
    }

    // ------------------------------------------------------------- the world

    /**
     * The recording world: the nether's bulk shape (netherrack under the
     * ceiling, air above) — the fortress pieces pass avoidAir=false, so
     * no read gates a write.
     */
    private static final class RecordingWorld implements StructureWorld {
        final Map<Long, BlockType> writes = new HashMap<>();
        final List<int[]> chests = new ArrayList<>();

        @Override
        public BlockType blockAt(int x, int y, int z) {
            BlockType written = writes.get(pack(x, y, z));
            if (written != null) {
                return written;
            }
            if (y < 0 || y > 255) {
                return null;
            }
            return y < 100 ? BuiltinBlocks.NETHERRACK : BuiltinBlocks.AIR;
        }

        @Override
        public boolean isLiquid(BlockType type) {
            if (type == null) {
                return false;
            }
            String value = type.identifier().value();
            return value.equals("water") || value.equals("lava");
        }

        @Override
        public void setBlock(int x, int y, int z, BlockType type) {
            writes.put(pack(x, y, z), type);
        }

        @Override
        public int seaLevel() {
            return 62;
        }

        @Override
        public StructurePalette palette() {
            return palette;
        }

        @Override
        public boolean isOpaque(BlockType type) {
            return type != null && type != BuiltinBlocks.AIR;
        }

        @Override
        public void placeGenChest(int x, int y, int z, List<GenLootSlot> slots) {
            chests.add(new int[]{x, y, z, slots.size()});
        }

        private static long pack(int x, int y, int z) {
            // 20 bits x | 20 bits z | 24 bits y — no field overlap.
            return ((long) (x & 0xFFFFF) << 44) | ((long) (z & 0xFFFFF) << 24) | (y & 0xFFFFFF);
        }
    }

    // ------------------------------------------------------- the placement

    @Test
    void theCellPickDrawsOneChunkPerPassingCell() {
        RecordingWorld world = new RecordingWorld();
        FortressStructure fortresses = new FortressStructure(12345L);
        // Every chunk in a cell band: a start appears only at the exact
        // pick (the 1-in-3 gate AND both offset checks).
        int hits = 0;
        for (int regionX = -80; regionX <= 80; regionX++) {
            for (int regionZ = -80; regionZ <= 80; regionZ++) {
                StructureStart start = fortresses.createStart(regionX, regionZ, world);
                if (start != null) {
                    // Verify the pick identity: the chunk matches the cell's
                    // own derivation.
                    int cellX = regionX >> 4;
                    int cellZ = regionZ >> 4;
                    Random gate = new Random();
                    gate.setSeed(cellX ^ (cellZ << 4) ^ 12345L);
                    gate.nextInt();
                    assertTrue(gate.nextInt(3) == 0, "the 1-in-3 gate passed");
                    assertEquals((cellX << 4) + 4 + gate.nextInt(8), regionX, "the x pick");
                    assertEquals((cellZ << 4) + 4 + gate.nextInt(8), regionZ, "the z pick");
                    hits++;
                }
            }
        }
        assertTrue(hits > 0, "the band carries at least one fortress (got " + hits + ")");
        assertTrue(hits < 40, "the 1-in-3 gate keeps the density sparse (got " + hits + ")");
    }

    @Test
    void theStartWalkBuildsAFortressInTheBand() {
        RecordingWorld world = new RecordingWorld();
        FortressStructure fortresses = new FortressStructure(987654321L);
        StructureStart hit = null;
        int hitX = 0;
        int hitZ = 0;
        for (int regionX = -120; regionX <= 120 && hit == null; regionX++) {
            for (int regionZ = -120; regionZ <= 120; regionZ++) {
                StructureStart start = fortresses.createStart(regionX, regionZ, world);
                if (start != null) {
                    hit = start;
                    hitX = regionX;
                    hitZ = regionZ;
                    break;
                }
            }
        }
        assertNotNull(hit, "some region carries a fortress");
        assertTrue(hit.getPieces().size() > 2, "the fortress carries pieces: "
                + hit.getPieces().size());
        assertTrue(hit.getPieces().getFirst() instanceof FortressPieces.FortressStart,
                "the BridgeCrossing shape anchors the walk");
        // The band re-base: the floor lands inside 48..70.
        assertTrue(hit.getBounds().minY >= 48, "the floor lands at or above 48 (got "
                + hit.getBounds().minY + ")");
        assertTrue(hit.getBounds().minY <= 70, "the floor lands at or below 70 (got "
                + hit.getBounds().minY + ")");
    }

    @Test
    void theSameSeedRegeneratesTheIdenticalPieceSet() {
        RecordingWorld world = new RecordingWorld();
        List<String> first = describe(987654321L, world);
        List<String> second = describe(987654321L, world);
        assertEquals(first, second, "the same seed regenerates the identical piece set");
        assertTrue(first.size() > 2, "the walk leaves pieces: " + first.size());
    }

    /** The piece set's fingerprint over every fortress in a region band. */
    private static List<String> describe(long seed, RecordingWorld world) {
        FortressStructure fortresses = new FortressStructure(seed);
        List<String> lines = new ArrayList<>();
        for (int regionX = -120; regionX <= 120; regionX++) {
            for (int regionZ = -120; regionZ <= 120; regionZ++) {
                StructureStart start = fortresses.createStart(regionX, regionZ, world);
                if (start == null) {
                    continue;
                }
                for (StructurePiece piece : start.getPieces()) {
                    lines.add(piece.getClass().getSimpleName() + "@"
                            + piece.getBounds().minX + "," + piece.getBounds().minY + ","
                            + piece.getBounds().minZ + "->" + piece.getBounds().maxX + ","
                            + piece.getBounds().maxY + "," + piece.getBounds().maxZ);
                }
            }
        }
        return lines;
    }

    @Test
    void theFortressWritesItsNetherBricks() {
        // Over several seeds, populate every chunk the fortresses touch
        // and verify the family's blocks land.
        for (long seed = 2L; seed <= 8L; seed++) {
            RecordingWorld world = new RecordingWorld();
            FortressStructure fortresses = new FortressStructure(seed);
            for (int regionX = -60; regionX <= 60; regionX++) {
                for (int regionZ = -60; regionZ <= 60; regionZ++) {
                    StructureStart start = fortresses.createStart(regionX, regionZ, world);
                    if (start == null) {
                        continue;
                    }
                    StructureBox all = start.getBounds();
                    for (int chunkX = all.minX >> 4; chunkX <= all.maxX >> 4; chunkX++) {
                        for (int chunkZ = all.minZ >> 4; chunkZ <= all.maxZ >> 4; chunkZ++) {
                            fortresses.populate(chunkX, chunkZ, world);
                        }
                    }
                    int bricks = 0;
                    int fences = 0;
                    for (BlockType type : world.writes.values()) {
                        String value = type.identifier().value();
                        if (value.equals("nether_bricks")) {
                            bricks++;
                        } else if (value.equals("nether_brick_fence")) {
                            fences++;
                        }
                    }
                    assertTrue(bricks > 500, "the fortress writes its bricks (got " + bricks + ")");
                    assertTrue(fences > 10, "the rail fences land (got " + fences + ")");
                    return; // one fortress verified is enough for the writes
                }
            }
        }
        throw new AssertionError("no fortress found in the seed band");
    }

    @Test
    void theNetherwartFarmPlantsItsBeds() {
        // Scan seeds for a farm piece; verify the soul sand + the wart.
        for (long seed = 1L; seed <= 60L; seed++) {
            RecordingWorld world = new RecordingWorld();
            FortressStructure fortresses = new FortressStructure(seed);
            for (int regionX = -80; regionX <= 80; regionX++) {
                for (int regionZ = -80; regionZ <= 80; regionZ++) {
                    StructureStart start = fortresses.createStart(regionX, regionZ, world);
                    if (start == null) {
                        continue;
                    }
                    boolean hasFarm = false;
                    for (StructurePiece piece : start.getPieces()) {
                        if (piece instanceof FortressPieces.NetherwartFarm) {
                            hasFarm = true;
                            break;
                        }
                    }
                    if (!hasFarm) {
                        continue;
                    }
                    StructureBox all = start.getBounds();
                    for (int chunkX = all.minX >> 4; chunkX <= all.maxX >> 4; chunkX++) {
                        for (int chunkZ = all.minZ >> 4; chunkZ <= all.maxZ >> 4; chunkZ++) {
                            fortresses.populate(chunkX, chunkZ, world);
                        }
                    }
                    int soulSand = 0;
                    int wart = 0;
                    int stairs = 0;
                    for (BlockType type : world.writes.values()) {
                        String value = type.identifier().value();
                        if (value.equals("soul_sand")) {
                            soulSand++;
                        } else if (value.startsWith("nether_wart")) {
                            wart++;
                        } else if (value.startsWith("nether_brick_stairs")) {
                            stairs++;
                        }
                    }
                    assertTrue(soulSand >= 12, "the two planting beds (got " + soulSand + ")");
                    assertTrue(wart >= 12, "the wart rows (got " + wart + ")");
                    assertTrue(stairs > 20, "the stair cascade (got " + stairs + ")");
                    return;
                }
            }
        }
        throw new AssertionError("no nether wart farm found in the seed band");
    }

    @Test
    void theChestLootWalkUsesTheFortressTable() {
        boolean anyChest = false;
        for (long seed = 10L; seed <= 60L; seed++) {
            RecordingWorld world = new RecordingWorld();
            FortressStructure fortresses = new FortressStructure(seed);
            for (int regionX = -80; regionX <= 80; regionX++) {
                for (int regionZ = -80; regionZ <= 80; regionZ++) {
                    StructureStart start = fortresses.createStart(regionX, regionZ, world);
                    if (start == null) {
                        continue;
                    }
                    StructureBox all = start.getBounds();
                    for (int chunkX = all.minX >> 4; chunkX <= all.maxX >> 4; chunkX++) {
                        for (int chunkZ = all.minZ >> 4; chunkZ <= all.maxZ >> 4; chunkZ++) {
                            fortresses.populate(chunkX, chunkZ, world);
                        }
                    }
                }
            }
            anyChest |= !world.chests.isEmpty();
        }
        assertTrue(anyChest, "some fortress carries a loot chest (the turn corridors' nextInt(3) rolls)");
    }

    @Test
    void theFortressBlocksAndItemsRegister() {
        assertNotNull(BuiltinBlocks.NETHER_BRICKS);
        assertNotNull(BuiltinBlocks.NETHER_BRICK_FENCE);
        assertNotNull(BuiltinBlocks.NETHER_WART_STAGE3);
        assertTrue(BuiltinItems.lookup(
                net.zaminmc.torch.util.Identifier.parse("minecraft:nether_bricks")).isPresent());
        assertTrue(BuiltinItems.lookup(
                net.zaminmc.torch.util.Identifier.parse("minecraft:nether_brick_fence")).isPresent());
        assertTrue(BuiltinItems.lookup(
                net.zaminmc.torch.util.Identifier.parse("minecraft:nether_brick_stairs")).isPresent());
    }
}
