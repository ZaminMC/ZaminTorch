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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The structure-placement alignments (Wave SA — the reference's placement
 * gates read against the engine's, the stale Slice-12-era biome envelopes
 * retired): the stronghold's baseHeight &gt; 0 land family, the monument's
 * five-row water family, the temple's scattered family whole (the hills
 * pair + the swampland — the witch hut's own arm), and the village's
 * savanna row. Every seat RED-verifiable on its no-op patch: the
 * three-biome stronghold gate, the two-water monument family, the
 * two-biome temple gate and the two-biome village gate each refuse the
 * world these seats build.
 */
class StructurePlacementAlignmentTest {

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
     * The recording world: a mutable biome over a flat stone surface —
     * the placement gates consult {@link #biome} alone, the piece writes
     * land in the map, the witch record rides her own list.
     */
    private static class GateWorld implements StructureWorld {
        static final int SURFACE = 70;

        final Map<Long, BlockType> writes = new HashMap<>();
        final List<int[]> witches = new ArrayList<>();
        Biome biome = Biome.PLAINS;

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
                return BuiltinBlocks.GRASS_BLOCK;
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
            return 63;
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
        public void spawnWitch(int x, int y, int z) {
            witches.add(new int[]{x, y, z});
        }

        private static long pack(int x, int y, int z) {
            // 20 bits x | 20 bits z | 24 bits y — no field overlap.
            return ((long) (x & 0xFFFFF) << 44) | ((long) (z & 0xFFFFF) << 24) | (y & 0xFFFFFF);
        }
    }

    /** The one-column world: DEEP_OCEAN at the center column, the field elsewhere. */
    private static final class CenterFieldWorld extends GateWorld {
        private final int centerX;
        private final int centerZ;
        private final Biome field;

        CenterFieldWorld(int centerX, int centerZ, Biome field) {
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.field = field;
        }

        @Override
        public Biome biomeAt(int x, int z) {
            return x == centerX && z == centerZ ? Biome.DEEP_OCEAN : field;
        }
    }

    // -------------------------------------------------- the stronghold's gate

    /**
     * THE STRONGHOLD'S LAND FAMILY (StrongholdStructure lines 21-29, 1:1):
     * the valid set is EVERY biome with baseHeight &gt; 0 — an all-taiga
     * world snaps the positions (every cell in the ±112 window is valid,
     * the reservoir keeps one), an all-ocean world snaps nothing. RED on
     * the retired three-biome gate: the taiga is refused there, both
     * tables answer the raw ring positions and the two worlds agree.
     */
    @Test
    void theStrongholdPositionsSnapToAnyLandBiome() {
        for (long seed = 1L; seed <= 8L; seed++) {
            GateWorld taiga = new GateWorld();
            taiga.biome = Biome.TAIGA;
            GateWorld ocean = new GateWorld();
            ocean.biome = Biome.OCEAN;
            List<int[]> taigaPositions =
                    new StrongholdStructure(seed, taiga).positions();
            List<int[]> oceanPositions =
                    new StrongholdStructure(seed, ocean).positions();
            boolean differ = false;
            for (int i = 0; i < taigaPositions.size(); i++) {
                if (taigaPositions.get(i)[0] != oceanPositions.get(i)[0]
                        || taigaPositions.get(i)[1] != oceanPositions.get(i)[1]) {
                    differ = true;
                }
            }
            assertTrue(differ, "the taiga world snaps where the ocean world cannot "
                    + "(seed " + seed + ")");
        }
    }

    /**
     * THE MESA ROW (the land family's far end — baseHeight 0.1 &gt; 0): the
     * mesa world snaps too. RED on the retired three-biome gate.
     */
    @Test
    void theStrongholdSnapsTheMesa() {
        GateWorld mesa = new GateWorld();
        mesa.biome = Biome.MESA;
        GateWorld ocean = new GateWorld();
        ocean.biome = Biome.OCEAN;
        boolean differ = false;
        for (long seed = 11L; seed <= 18L; seed++) {
            List<int[]> mesaPositions = new StrongholdStructure(seed, mesa).positions();
            List<int[]> oceanPositions = new StrongholdStructure(seed, ocean).positions();
            for (int i = 0; i < mesaPositions.size(); i++) {
                if (mesaPositions.get(i)[0] != oceanPositions.get(i)[0]
                        || mesaPositions.get(i)[1] != oceanPositions.get(i)[1]) {
                    differ = true;
                }
            }
        }
        assertTrue(differ, "the mesa world snaps (the land family whole)");
    }

    // ---------------------------------------------------- the monument's gate

    /**
     * THE MONUMENT'S FROZEN WATER FAMILY (OceanMonumentStructure line 24,
     * 1:1): the ±29 window accepts the frozen ocean — a monument whose
     * center is DEEP_OCEAN surrounded by FROZEN_OCEAN places. RED on the
     * retired two-water family: the window refuses the frozen ring.
     */
    @Test
    void theMonumentAcceptsTheFrozenOceanWindow() {
        int placed = 0;
        for (long seed = 1L; seed <= 24L && placed < 3; seed++) {
            // The cell (0,0) pick: the all-deep-ocean world answers it.
            GateWorld deep = new GateWorld();
            deep.biome = Biome.DEEP_OCEAN;
            OceanMonumentStructure monuments = new OceanMonumentStructure(seed);
            int pickX = Integer.MIN_VALUE;
            int pickZ = Integer.MIN_VALUE;
            for (int chunkX = 0; chunkX < 32 && pickX == Integer.MIN_VALUE; chunkX++) {
                for (int chunkZ = 0; chunkZ < 32; chunkZ++) {
                    if (monuments.createStart(chunkX, chunkZ, deep) != null) {
                        pickX = chunkX;
                        pickZ = chunkZ;
                        break;
                    }
                }
            }
            if (pickX == Integer.MIN_VALUE) {
                continue; // the cell's pick fell outside the 32-chunk band
            }
            // The frozen ring: DEEP_OCEAN at the pick's own +8 center,
            // FROZEN_OCEAN at every other cell of the ±29 window.
            CenterFieldWorld frozen = new CenterFieldWorld(pickX * 16 + 8, pickZ * 16 + 8,
                    Biome.FROZEN_OCEAN);
            assertNotNull(monuments.createStart(pickX, pickZ, deep),
                    "the all-deep-ocean world places (seed " + seed + ")");
            if (monuments.createStart(pickX, pickZ, frozen) != null) {
                placed++;
            }
        }
        assertTrue(placed >= 3, "the frozen ocean family hosts monuments (got " + placed + ")");
    }

    /** The land world never places a monument (the DEEP_OCEAN center gate). */
    @Test
    void theMonumentRefusesTheLand() {
        GateWorld land = new GateWorld();
        land.biome = Biome.PLAINS;
        OceanMonumentStructure monuments = new OceanMonumentStructure(12345L);
        for (int chunkX = -40; chunkX <= 40; chunkX++) {
            for (int chunkZ = -40; chunkZ <= 40; chunkZ++) {
                assertFalse(monuments.createStart(chunkX, chunkZ, land) != null,
                        "the plains world never hosts a monument");
            }
        }
    }

    // ----------------------------------------------------- the temple's gate

    /**
     * THE WITCH HUT (TempleStructure line 16 + TemplePieces.WitchHut lines
     * 806-985, 1:1): the swampland hosts the hut whole — the spruce shell,
     * the cauldron, the potted red mushroom, the crafting table, the
     * spruce-stairs roof, and the witch's own seat record. RED on the
     * retired two-biome gate: the swampland is refused, no hut at all.
     */
    @Test
    void theSwamplandBuildsTheWitchHutWithHerSeat() {
        GateWorld swamp = new GateWorld();
        swamp.biome = Biome.SWAMPLAND;
        TempleStructure temples = new TempleStructure(424242L);
        StructureStart hut = null;
        for (int regionX = -130; regionX <= 130 && hut == null; regionX++) {
            for (int regionZ = -130; regionZ <= 130; regionZ++) {
                StructureStart start = temples.createStart(regionX, regionZ, swamp);
                if (start != null) {
                    hut = start;
                    break;
                }
            }
        }
        assertNotNull(hut, "the swampland hosts the witch hut");
        assertTrue(hut.getPieces().getFirst() instanceof TemplePieces.WitchHut,
                "the swampland dispatches the witch hut");
        // The populate over the hut's own chunk span.
        StructureBox all = hut.getBounds();
        for (int chunkX = all.minX >> 4; chunkX <= all.maxX >> 4; chunkX++) {
            for (int chunkZ = all.minZ >> 4; chunkZ <= all.maxZ >> 4; chunkZ++) {
                temples.populate(chunkX, chunkZ, swamp);
            }
        }
        int sprucePlanks = 0;
        int spruceStairs = 0;
        int cauldrons = 0;
        int mushrooms = 0;
        int tables = 0;
        int logs = 0;
        for (BlockType type : swamp.writes.values()) {
            String value = type.identifier().value();
            switch (value) {
                case "spruce_planks" -> sprucePlanks++;
                case "spruce_stairs_east", "spruce_stairs_west", "spruce_stairs_south",
                        "spruce_stairs_north" -> spruceStairs++;
                case "cauldron" -> cauldrons++;
                case "potted_red_mushroom" -> mushrooms++;
                case "crafting_table" -> tables++;
                case "oak_log" -> logs++;
                default -> {
                }
            }
        }
        assertTrue(sprucePlanks > 60, "the spruce shell (got " + sprucePlanks + ")");
        assertTrue(spruceStairs >= 20, "the roof ring (got " + spruceStairs + ")");
        assertEquals(1, cauldrons, "the hut's cauldron");
        assertEquals(1, mushrooms, "the potted red mushroom");
        assertEquals(1, tables, "the crafting table");
        assertTrue(logs >= 8, "the pillars + the stilts (got " + logs + ")");
        assertTrue(swamp.witches.size() >= 1, "the witch's seat record (got "
                + swamp.witches.size() + ")");
    }

    /**
     * THE WITCH'S EXACTLY-ONCE LAW: the seat fires once per populate of
     * the chunk that holds it — the same chunk's second populate answers
     * the same record set (the clip gate is deterministic), and no other
     * chunk of the hut's span fires her.
     */
    @Test
    void theWitchFiresInExactlyOneChunkOfTheHut() {
        GateWorld swamp = new GateWorld();
        swamp.biome = Biome.SWAMPLAND;
        TempleStructure temples = new TempleStructure(777L);
        StructureStart hut = null;
        int hutChunkX = 0;
        int hutChunkZ = 0;
        for (int regionX = -130; regionX <= 130 && hut == null; regionX++) {
            for (int regionZ = -130; regionZ <= 130; regionZ++) {
                StructureStart start = temples.createStart(regionX, regionZ, swamp);
                if (start != null) {
                    hut = start;
                    hutChunkX = regionX;
                    hutChunkZ = regionZ;
                    break;
                }
            }
        }
        assertNotNull(hut, "the swampland hosts the witch hut");
        // One chunk at a time: the witch fires only in the chunk holding
        // the seat — count the chunks of the hut's span that fire her.
        int firingChunks = 0;
        int totalWitches = 0;
        StructureBox all = hut.getBounds();
        for (int chunkX = all.minX >> 4; chunkX <= all.maxX >> 4; chunkX++) {
            for (int chunkZ = all.minZ >> 4; chunkZ <= all.maxZ >> 4; chunkZ++) {
                GateWorld one = new GateWorld();
                one.biome = Biome.SWAMPLAND;
                temples.populate(chunkX, chunkZ, one);
                if (!one.witches.isEmpty()) {
                    firingChunks++;
                    totalWitches += one.witches.size();
                }
            }
        }
        // The hut's own span is ONE chunk (the 7x9 hut never leaves its
        // pick chunk), and the ±8 region scan's other huts sit at least
        // 8 chunks away (the 32-chunk grid's own law) — their bounds
        // never reach this chunk. Exactly one chunk of the span fires,
        // with exactly one witch.
        assertEquals(1, firingChunks, "the hut's one chunk fires (got " + firingChunks + ")");
        assertEquals(1, totalWitches, "exactly one witch (got " + totalWitches + ")");
    }

    /**
     * THE HILLS PAIR (the temple's scattered family): the desert hills
     * dispatch the pyramid, the jungle hills the jungle temple. RED on the
     * retired two-biome gate (both hills refused).
     */
    @Test
    void theHillsPairDispatchesItsTemples() {
        GateWorld desertHills = new GateWorld();
        desertHills.biome = Biome.DESERT_HILLS;
        TempleStructure desertTemples = new TempleStructure(31415L);
        StructureStart pyramid = null;
        for (int regionX = -130; regionX <= 130 && pyramid == null; regionX++) {
            for (int regionZ = -130; regionZ <= 130; regionZ++) {
                StructureStart start = desertTemples.createStart(regionX, regionZ, desertHills);
                if (start != null) {
                    pyramid = start;
                    break;
                }
            }
        }
        assertNotNull(pyramid, "the desert hills host temples");
        assertTrue(pyramid.getPieces().getFirst() instanceof TemplePieces.DesertPyramid,
                "the desert hills dispatch the pyramid");

        GateWorld jungleHills = new GateWorld();
        jungleHills.biome = Biome.JUNGLE_HILLS;
        TempleStructure jungleTemples = new TempleStructure(27182L);
        StructureStart jungle = null;
        for (int regionX = -130; regionX <= 130 && jungle == null; regionX++) {
            for (int regionZ = -130; regionZ <= 130; regionZ++) {
                StructureStart start = jungleTemples.createStart(regionX, regionZ, jungleHills);
                if (start != null) {
                    jungle = start;
                    break;
                }
            }
        }
        assertNotNull(jungle, "the jungle hills host temples");
        assertTrue(jungle.getPieces().getFirst() instanceof TemplePieces.JungleTemple,
                "the jungle hills dispatch the jungle temple");
    }

    /** The scattered family's negative: the plains never hosts a temple. */
    @Test
    void thePlainsRefusesTheTemple() {
        GateWorld plains = new GateWorld();
        plains.biome = Biome.PLAINS;
        TempleStructure temples = new TempleStructure(999L);
        for (int regionX = -96; regionX <= 96; regionX++) {
            for (int regionZ = -96; regionZ <= 96; regionZ++) {
                assertFalse(temples.createStart(regionX, regionZ, plains) != null,
                        "the plains world never hosts a temple");
            }
        }
    }

    // ---------------------------------------------------- the village's gate

    /**
     * THE VILLAGE'S SAVANNA ROW (VillageStructure line 14, 1:1): the
     * savanna hosts villages. RED on the retired two-biome gate.
     */
    @Test
    void theSavannaHostsVillages() {
        GateWorld savanna = new GateWorld();
        savanna.biome = Biome.SAVANNA;
        VillageStructure villages = new VillageStructure(654321L);
        int hits = 0;
        for (int regionX = -96; regionX <= 96; regionX++) {
            for (int regionZ = -96; regionZ <= 96; regionZ++) {
                if (villages.createStart(regionX, regionZ, savanna) != null) {
                    hits++;
                }
            }
        }
        assertTrue(hits > 0, "the savanna hosts villages (got " + hits + ")");
    }

    /** The village family's negative: the forest never hosts a village. */
    @Test
    void theForestRefusesTheVillage() {
        GateWorld forest = new GateWorld();
        forest.biome = Biome.FOREST;
        VillageStructure villages = new VillageStructure(654321L);
        for (int regionX = -96; regionX <= 96; regionX++) {
            for (int regionZ = -96; regionZ <= 96; regionZ++) {
                assertFalse(villages.createStart(regionX, regionZ, forest) != null,
                        "the forest world never hosts a village");
            }
        }
    }
}
