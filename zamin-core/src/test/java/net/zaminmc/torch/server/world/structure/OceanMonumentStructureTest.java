package net.zaminmc.torch.server.world.structure;

import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.BlockRegistryBuilder;
import net.zaminmc.torch.server.block.BlockRegistryBuilder.FrozenBlockRegistry;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.world.Biome;
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
 * The ocean monument placement (Slice 12e — the reference port of
 * OceanMonumentStructure + OceanMonumentPieces over the engine's
 * deterministic generator): the spacing/separation pick with the
 * two-roll average, the deep-ocean center gate with the all-water
 * window, the room-graph walk, and the same-seed regeneration
 * identity.
 */
class OceanMonumentStructureTest {

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
     * The recording world: the ocean floor at y 40 under the water body
     * (the sea level 62 — the deep-ocean biome everywhere, the monument's
     * y 39..61 band sits on the floor and in the water).
     */
    private static class RecordingWorld implements StructureWorld {
        static final int FLOOR = 40;
        static final int SURFACE = 62;

        final Map<Long, BlockType> writes = new HashMap<>();

        @Override
        public BlockType blockAt(int x, int y, int z) {
            BlockType written = writes.get(pack(x, y, z));
            if (written != null) {
                return written;
            }
            if (y < 0 || y > 255) {
                return null;
            }
            if (y <= FLOOR) {
                return BuiltinBlocks.STONE;
            }
            if (y <= SURFACE) {
                return BuiltinBlocks.WOOL; // a non-air, non-water stand-in for the body
            }
            return BuiltinBlocks.AIR;
        }

        @Override
        public boolean isLiquid(BlockType type) {
            // The recording world's water body is the wool stand-in —
            // fillWater only reads isAir.
            return false;
        }

        @Override
        public void setBlock(int x, int y, int z, BlockType type) {
            writes.put(pack(x, y, z), type);
        }

        @Override
        public int seaLevel() {
            return SURFACE;
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
        public Biome biomeAt(int x, int z) {
            return Biome.DEEP_OCEAN;
        }

        private static long pack(int x, int y, int z) {
            // 20 bits x | 20 bits z | 24 bits y — no field overlap.
            return ((long) (x & 0xFFFFF) << 44) | ((long) (z & 0xFFFFF) << 24) | (y & 0xFFFFFF);
        }
    }

    // ------------------------------------------------------- the placement

    @Test
    void theSpacingPickLandsOneChunkPerCell() {
        RecordingWorld world = new RecordingWorld();
        OceanMonumentStructure monuments = new OceanMonumentStructure(12345L);
        int hits = 0;
        for (int regionX = -130; regionX <= 130; regionX++) {
            for (int regionZ = -130; regionZ <= 130; regionZ++) {
                StructureStart start = monuments.createStart(regionX, regionZ, world);
                if (start != null) {
                    // The pick identity: the two-roll average derivation.
                    int floorX = regionX < 0 ? regionX - (32 - 1) : regionX;
                    int floorZ = regionZ < 0 ? regionZ - (32 - 1) : regionZ;
                    int cellX = floorX / 32;
                    int cellZ = floorZ / 32;
                    Random gate = new Random(cellX * 341873128712L + cellZ * 132897987541L
                            + 12345L + 10387313L);
                    int pickX = cellX * 32 + (gate.nextInt(27) + gate.nextInt(27)) / 2;
                    int pickZ = cellZ * 32 + (gate.nextInt(27) + gate.nextInt(27)) / 2;
                    assertEquals(pickX, regionX, "the x pick");
                    assertEquals(pickZ, regionZ, "the z pick");
                    hits++;
                }
            }
        }
        assertTrue(hits > 0, "the all-deep-ocean world hosts monuments (got " + hits + ")");
        assertTrue(hits <= 80, "one pick per 32x32 cell keeps the density (got " + hits
                + " — the 261-chunk band spans ~67 cells)");
    }

    @Test
    void theBiomeGatesRefuseNonWaterCenters() {
        RecordingWorld world = new RecordingWorld();
        OceanMonumentStructure monuments = new OceanMonumentStructure(12345L);
        // A plains world: no start ever appears.
        RecordingWorld land = new RecordingWorld() {
            @Override
            public Biome biomeAt(int x, int z) {
                return Biome.PLAINS;
            }
        };
        for (int regionX = -100; regionX <= 100; regionX++) {
            for (int regionZ = -100; regionZ <= 100; regionZ++) {
                assertEquals(null, monuments.createStart(regionX, regionZ, land),
                        "a plains world never hosts a monument: " + regionX + "," + regionZ);
            }
        }
    }

    @Test
    void theSameSeedRegeneratesTheIdenticalPieceSet() {
        RecordingWorld world = new RecordingWorld();
        List<String> first = describe(987654321L, world);
        List<String> second = describe(987654321L, world);
        assertEquals(first, second, "the same seed regenerates the identical piece set");
    }

    /** The piece set's fingerprint over every monument in a region band. */
    private static List<String> describe(long seed, RecordingWorld world) {
        OceanMonumentStructure monuments = new OceanMonumentStructure(seed);
        List<String> lines = new ArrayList<>();
        for (int regionX = -130; regionX <= 130; regionX++) {
            for (int regionZ = -130; regionZ <= 130; regionZ++) {
                StructureStart start = monuments.createStart(regionX, regionZ, world);
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
    void theMonumentWritesItsPrismarineAndWater() {
        RecordingWorld world = new RecordingWorld();
        OceanMonumentStructure monuments = new OceanMonumentStructure(555L);
        StructureStart hit = null;
        int hitX = 0;
        int hitZ = 0;
        for (int regionX = -130; regionX <= 130 && hit == null; regionX++) {
            for (int regionZ = -130; regionZ <= 130; regionZ++) {
                StructureStart start = monuments.createStart(regionX, regionZ, world);
                if (start != null) {
                    hit = start;
                    hitX = regionX;
                    hitZ = regionZ;
                    break;
                }
            }
        }
        assertNotNull(hit, "some region carries a monument");
        StructureBox all = hit.getBounds();
        for (int chunkX = all.minX >> 4; chunkX <= all.maxX >> 4; chunkX++) {
            for (int chunkZ = all.minZ >> 4; chunkZ <= all.maxZ >> 4; chunkZ++) {
                monuments.populate(chunkX, chunkZ, world);
            }
        }
        int rough = 0;
        int bricks = 0;
        int dark = 0;
        int lanterns = 0;
        int water = 0;
        int gold = 0;
        for (BlockType type : world.writes.values()) {
            String value = type.identifier().value();
            switch (value) {
                case "prismarine_rough" -> rough++;
                case "prismarine_bricks" -> bricks++;
                case "prismarine_dark" -> dark++;
                case "sea_lantern" -> lanterns++;
                case "water" -> water++;
                case "gold_block" -> gold++;
                default -> {
                }
            }
        }
        assertTrue(rough > 2000, "the monument's rough shell (got " + rough + ")");
        assertTrue(bricks > 1000, "the lit shell (got " + bricks + ")");
        assertTrue(dark > 10, "the dark accents (got " + dark + ")");
        assertTrue(lanterns > 20, "the sea lanterns (got " + lanterns + ")");
        assertTrue(water > 1000, "the flooded interior (got " + water + ")");
        assertTrue(gold >= 8, "the core room's gold block (got " + gold + ")");
    }

    @Test
    void theMonumentBlocksAndItemsRegister() {
        assertNotNull(BuiltinBlocks.PRISMARINE_ROUGH);
        assertNotNull(BuiltinBlocks.SEA_LANTERN);
        assertNotNull(BuiltinBlocks.WET_SPONGE);
        assertNotNull(BuiltinBlocks.GOLD_BLOCK);
        assertTrue(BuiltinItems.lookup(
                net.zaminmc.torch.util.Identifier.parse("minecraft:prismarine")).isPresent());
        assertTrue(BuiltinItems.lookup(
                net.zaminmc.torch.util.Identifier.parse("minecraft:prismarine_crystals")).isPresent());
        assertTrue(BuiltinItems.lookup(
                net.zaminmc.torch.util.Identifier.parse("minecraft:sea_lantern")).isPresent());
        assertTrue(BuiltinItems.lookup(
                net.zaminmc.torch.util.Identifier.parse("minecraft:gold_block")).isPresent());
    }
}
