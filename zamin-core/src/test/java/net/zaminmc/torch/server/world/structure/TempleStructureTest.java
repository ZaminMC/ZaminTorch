package net.zaminmc.torch.server.world.structure;

import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.BlockRegistryBuilder;
import net.zaminmc.torch.server.block.BlockRegistryBuilder.FrozenBlockRegistry;
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
 * The temple placement (Slice 12f — the reference port of
 * TempleStructure + TemplePieces over the engine's deterministic
 * generator): the distance/min-distance grid pick, the biome dispatch
 * (the desert pyramid / the jungle temple), the pieces' writes, and the
 * same-seed regeneration identity.
 */
class TempleStructureTest {

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

    /** The recording world: a desert everywhere with the surface at 70. */
    private static class RecordingWorld implements StructureWorld {
        static final int SURFACE = 70;

        final Map<Long, BlockType> writes = new HashMap<>();
        final List<int[]> chests = new ArrayList<>();
        final List<int[]> dispensers = new ArrayList<>();
        Biome biome = Biome.DESERT;

        @Override
        public BlockType blockAt(int x, int y, int z) {
            BlockType written = writes.get(pack(x, y, z));
            if (written != null) {
                return written;
            }
            if (y < 0 || y > 255) {
                return null;
            }
            if (y < SURFACE) {
                return BuiltinBlocks.STONE;
            }
            if (y == SURFACE) {
                return BuiltinBlocks.SAND;
            }
            return BuiltinBlocks.AIR;
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
        public int surfaceHeight(int x, int z) {
            return SURFACE;
        }

        @Override
        public Biome biomeAt(int x, int z) {
            return biome;
        }

        @Override
        public void placeGenChest(int x, int y, int z, List<GenLootSlot> slots) {
            chests.add(new int[]{x, y, z, slots.size()});
        }

        @Override
        public void placeGenDispenser(int x, int y, int z, int facing, List<GenLootSlot> slots) {
            dispensers.add(new int[]{x, y, z, facing, slots.size()});
        }

        private static long pack(int x, int y, int z) {
            // 20 bits x | 20 bits z | 24 bits y — no field overlap.
            return ((long) (x & 0xFFFFF) << 44) | ((long) (z & 0xFFFFF) << 24) | (y & 0xFFFFFF);
        }
    }

    // ------------------------------------------------------- the placement

    @Test
    void theGridPicksOneChunkPerCellWhereTheBiomeAgrees() {
        RecordingWorld world = new RecordingWorld();
        TempleStructure temples = new TempleStructure(12345L);
        int hits = 0;
        for (int regionX = -130; regionX <= 130; regionX++) {
            for (int regionZ = -130; regionZ <= 130; regionZ++) {
                StructureStart start = temples.createStart(regionX, regionZ, world);
                if (start != null) {
                    int floorX = regionX < 0 ? regionX - 31 : regionX;
                    int floorZ = regionZ < 0 ? regionZ - 31 : regionZ;
                    int cellX = floorX / 32;
                    int cellZ = floorZ / 32;
                    Random gate = new Random(cellX * 341873128712L + cellZ * 132897987541L
                            + 12345L + 14357617L);
                    assertEquals(cellX * 32 + gate.nextInt(24), regionX, "the x pick");
                    assertEquals(cellZ * 32 + gate.nextInt(24), regionZ, "the z pick");
                    hits++;
                }
            }
        }
        assertTrue(hits > 0, "the desert world hosts temples (got " + hits + ")");
        assertTrue(hits <= 70, "one pick per 32x32 cell keeps the density (got " + hits
                + " — the 261-chunk band spans ~67 cells)");
    }

    @Test
    void theBiomeDispatchBuildsThePyramidInTheDesert() {
        RecordingWorld world = new RecordingWorld();
        TempleStructure temples = new TempleStructure(12345L);
        StructureStart hit = null;
        int hitX = 0;
        int hitZ = 0;
        for (int regionX = -130; regionX <= 130 && hit == null; regionX++) {
            for (int regionZ = -130; regionZ <= 130; regionZ++) {
                StructureStart start = temples.createStart(regionX, regionZ, world);
                if (start != null) {
                    hit = start;
                    hitX = regionX;
                    hitZ = regionZ;
                    break;
                }
            }
        }
        assertNotNull(hit, "some region carries a temple");
        assertTrue(hit.getPieces().getFirst() instanceof TemplePieces.DesertPyramid,
                "the desert biome dispatches the pyramid");
        // The populate over the whole piece.
        StructureBox all = hit.getBounds();
        for (int chunkX = all.minX >> 4; chunkX <= all.maxX >> 4; chunkX++) {
            for (int chunkZ = all.minZ >> 4; chunkZ <= all.maxZ >> 4; chunkZ++) {
                temples.populate(chunkX, chunkZ, world);
            }
        }
        int sandstone = 0;
        int smooth = 0;
        int clay = 0;
        int tnt = 0;
        for (BlockType type : world.writes.values()) {
            String value = type.identifier().value();
            switch (value) {
                case "sandstone" -> sandstone++;
                case "smooth_sandstone" -> smooth++;
                case "stained_clay_orange", "stained_clay_yellow" -> clay++;
                case "tnt" -> tnt++;
                default -> {
                }
            }
        }
        assertTrue(sandstone > 2000, "the pyramid shell (got " + sandstone + ")");
        assertTrue(smooth > 50, "the smooth accents (got " + smooth + ")");
        assertTrue(clay > 40, "the blueprint floor (got " + clay + ")");
        assertTrue(tnt >= 9, "the hidden TNT floor (got " + tnt + ")");
        // The four direction chests (the offsets land inside the clip).
        assertTrue(world.chests.size() >= 1, "the chests land (got " + world.chests.size() + ")");
    }

    @Test
    void theJungleBiomeBuildsTheTempleWithItsTraps() {
        RecordingWorld world = new RecordingWorld();
        world.biome = Biome.JUNGLE;
        TempleStructure temples = new TempleStructure(987654321L);
        StructureStart hit = null;
        for (int regionX = -130; regionX <= 130 && hit == null; regionX++) {
            for (int regionZ = -130; regionZ <= 130; regionZ++) {
                StructureStart start = temples.createStart(regionX, regionZ, world);
                if (start != null) {
                    hit = start;
                    break;
                }
            }
        }
        assertNotNull(hit, "the jungle world hosts temples");
        assertTrue(hit.getPieces().getFirst() instanceof TemplePieces.JungleTemple,
                "the jungle biome dispatches the temple");
        StructureBox all = hit.getBounds();
        for (int chunkX = all.minX >> 4; chunkX <= all.maxX >> 4; chunkX++) {
            for (int chunkZ = all.minZ >> 4; chunkZ <= all.maxZ >> 4; chunkZ++) {
                temples.populate(chunkX, chunkZ, world);
            }
        }
        int mossy = 0;
        int hooks = 0;
        int wire = 0;
        int pistons = 0;
        int vines = 0;
        for (BlockType type : world.writes.values()) {
            String value = type.identifier().value();
            if (value.equals("mossy_cobblestone")) {
                mossy++;
            } else if (value.startsWith("tripwire_hook")) {
                hooks++;
            } else if (value.equals("redstone_wire")) {
                wire++;
            } else if (value.startsWith("sticky_piston")) {
                pistons++;
            } else if (value.equals("vine")) {
                vines++;
            }
        }
        assertTrue(mossy > 200, "the mossy cobblestone body (got " + mossy + ")");
        assertEquals(4, hooks, "the two trap hook pairs");
        assertTrue(wire > 10, "the trap redstone (got " + wire + ")");
        assertEquals(3, pistons, "the piston puzzle trio");
        assertEquals(3, vines, "the vine camouflages");
        // The two traps + the two chests.
        assertTrue(world.dispensers.size() >= 1, "the trap dispensers land (got "
                + world.dispensers.size() + ")");
        assertTrue(world.chests.size() >= 1, "the chests land (got " + world.chests.size() + ")");
    }

    @Test
    void theSameSeedRegeneratesTheIdenticalPieceSet() {
        RecordingWorld world = new RecordingWorld();
        List<String> first = describe(555L, Biome.DESERT, world);
        List<String> second = describe(555L, Biome.DESERT, world);
        assertEquals(first, second, "the same seed regenerates the identical piece set");
        List<String> jungleFirst = describe(556L, Biome.JUNGLE, world);
        List<String> jungleSecond = describe(556L, Biome.JUNGLE, world);
        assertEquals(jungleFirst, jungleSecond, "the jungle walk regenerates identically");
    }

    /** The piece set's fingerprint over every temple in a region band. */
    private static List<String> describe(long seed, Biome biome, RecordingWorld world) {
        RecordingWorld local = new RecordingWorld();
        local.biome = biome;
        TempleStructure temples = new TempleStructure(seed);
        List<String> lines = new ArrayList<>();
        for (int regionX = -130; regionX <= 130; regionX++) {
            for (int regionZ = -130; regionZ <= 130; regionZ++) {
                StructureStart start = temples.createStart(regionX, regionZ, local);
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
}
