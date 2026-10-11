package net.zaminmc.torch.server.world.structure;

import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.BlockRegistryBuilder;
import net.zaminmc.torch.server.block.BlockRegistryBuilder.FrozenBlockRegistry;
import net.zaminmc.torch.server.world.Biome;
import net.zaminmc.torch.server.world.NormalWorldGenerator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The village placement (Slice 12b — the reference port of
 * VillageStructure + VillagePieces over the engine's deterministic
 * generator): the 32/8 grid picks one chunk per cell through the
 * setRandomSeed(10387312) pair, the biome gate holds the last word, the
 * road/building walk builds a real village, and the same seed
 * regenerates the identical piece set (the engine's determinism contract
 * replacing the reference's cross-chunk start cache).
 */
class VillageStructureTest {

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

    /** The flat recording world the piece postProcess probes write into. */
    private static final class RecordingWorld implements StructureWorld {
        static final int SURFACE = 70;

        final Map<Long, BlockType> writes = new HashMap<>();
        final List<int[]> villagers = new ArrayList<>();
        final List<int[]> chests = new ArrayList<>();
        Biome biome = Biome.PLAINS;
        /** The desert box (minX, minZ, maxX, maxZ), or null. */
        int[] desertBox;

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
            return value.equals("water") || value.equals("lava")
                    || value.startsWith("flowing_water") || value.startsWith("flowing_lava")
                    || value.startsWith("falling_water") || value.startsWith("falling_lava");
        }

        @Override
        public void setBlock(int x, int y, int z, BlockType type) {
            writes.put(pack(x, y, z), type);
        }

        @Override
        public int seaLevel() {
            return NormalWorldGenerator.SEA_LEVEL;
        }

        @Override
        public StructurePalette palette() {
            return palette;
        }

        @Override
        public boolean isOpaque(BlockType type) {
            return type != null && type != BuiltinBlocks.AIR
                    && type != BuiltinBlocks.OAK_LEAVES;
        }

        @Override
        public int surfaceHeight(int x, int z) {
            return SURFACE;
        }

        @Override
        public Biome biomeAt(int x, int z) {
            if (desertBox != null
                    && x >= desertBox[0] && z >= desertBox[1]
                    && x <= desertBox[2] && z <= desertBox[3]) {
                return Biome.DESERT;
            }
            return biome;
        }

        @Override
        public void spawnVillager(int x, int y, int z, int profession) {
            villagers.add(new int[]{x, y, z, profession});
        }

        @Override
        public void placeGenChest(int x, int y, int z, List<GenLootSlot> slots) {
            chests.add(new int[]{x, y, z, slots.size()});
        }

        BlockType at(int x, int y, int z) {
            return writes.getOrDefault(pack(x, y, z), blockAt(x, y, z));
        }

        private static long pack(int x, int y, int z) {
            // 20 bits x | 20 bits z | 24 bits y — no field overlap.
            return ((long) (x & 0xFFFFF) << 44) | ((long) (z & 0xFFFFF) << 24) | (y & 0xFFFFFF);
        }
    }

    // ------------------------------------------------------- the placement

    @Test
    void theGridPicksExactlyOneChunkPerCellWhereTheBiomeAgrees() {
        VillageStructure villages = new VillageStructure(12345L);
        RecordingWorld world = new RecordingWorld();
        // Every 32x32 cell around the origin: exactly one pick chunk, and
        // the start appears only there (and only while the biome gate
        // agrees — a plains world always agrees).
        Set<Long> picks = new HashSet<>();
        for (int cellX = -4; cellX <= 4; cellX++) {
            for (int cellZ = -4; cellZ <= 4; cellZ++) {
                Random gate = new Random(cellX * 341873128712L + cellZ * 132897987541L
                        + 12345L + 10387312L);
                int pickX = cellX * 32 + gate.nextInt(24);
                int pickZ = cellZ * 32 + gate.nextInt(24);
                picks.add(((long) pickX << 32) | (pickZ & 0xFFFFFFFFL));
            }
        }
        int hits = 0;
        for (int regionX = -128; regionX <= 128; regionX++) {
            for (int regionZ = -128; regionZ <= 128; regionZ++) {
                StructureStart start = villages.createStart(regionX, regionZ, world);
                if (start != null) {
                    assertTrue(picks.contains(((long) regionX << 32) | (regionZ & 0xFFFFFFFFL)),
                            "a start appeared off the pick: " + regionX + "," + regionZ);
                    hits++;
                }
            }
        }
        assertTrue(hits > 50, "the 81 cells pick many chunks (got " + hits + ")");
    }

    @Test
    void theBiomeGateRefusesNonVillageBiomes() {
        VillageStructure villages = new VillageStructure(12345L);
        RecordingWorld world = new RecordingWorld();
        // A forest everywhere: no start ever appears.
        world.biome = Biome.FOREST;
        for (int regionX = -70; regionX <= 70; regionX++) {
            for (int regionZ = -70; regionZ <= 70; regionZ++) {
                assertNull(villages.createStart(regionX, regionZ, world),
                        "a forest world never hosts a village: " + regionX + "," + regionZ);
            }
        }
    }

    @Test
    void theStartWalkBuildsARealVillageOnTheWell() {
        VillageStructure villages = new VillageStructure(987654321L);
        RecordingWorld world = new RecordingWorld();
        StructureStart hit = null;
        int hitX = 0;
        int hitZ = 0;
        for (int regionX = -160; regionX <= 160 && hit == null; regionX++) {
            for (int regionZ = -160; regionZ <= 160; regionZ++) {
                StructureStart start = villages.createStart(regionX, regionZ, world);
                if (start != null) {
                    hit = start;
                    hitX = regionX;
                    hitZ = regionZ;
                    break;
                }
            }
        }
        assertNotNull(hit, "some region must carry a village");
        assertTrue(hit.isValid(), "the walk left more than two non-road pieces");
        assertTrue(hit.getPieces().size() > 2, "the village carries pieces: " + hit.getPieces().size());
        // The well is the first piece, at the region's +2 corner.
        StructurePiece well = hit.getPieces().getFirst();
        assertTrue(well instanceof VillagePieces.Well, "the first piece is the well");
        assertEquals((hitX << 4) + 2, well.getBounds().minX);
        assertEquals((hitZ << 4) + 2, well.getBounds().minZ);
        assertEquals(6, well.getBounds().getSpanX());
        assertEquals(6, well.getBounds().getSpanZ());
        // The village holds at least one road and one building family.
        boolean road = false;
        boolean building = false;
        for (StructurePiece piece : hit.getPieces()) {
            if (piece instanceof VillagePieces.RoadPiece) {
                road = true;
            } else {
                building = true;
            }
        }
        assertTrue(road, "the walk placed roads");
        assertTrue(building, "the walk placed buildings");
    }

    @Test
    void theSameSeedRegeneratesTheIdenticalVillage() {
        VillageStructure villages = new VillageStructure(-42L);
        RecordingWorld world = new RecordingWorld();
        StructureStart first = null;
        int hitX = 0;
        int hitZ = 0;
        for (int regionX = 90; regionX <= 200 && first == null; regionX++) {
            for (int regionZ = 90; regionZ <= 200; regionZ++) {
                StructureStart start = villages.createStart(regionX, regionZ, world);
                if (start != null) {
                    first = start;
                    hitX = regionX;
                    hitZ = regionZ;
                    break;
                }
            }
        }
        assertNotNull(first, "some region must carry a village");
        StructureStart second = villages.createStart(hitX, hitZ, world);
        assertNotNull(second);
        assertEquals(first.getPieces().size(), second.getPieces().size(), "identical piece count");
        for (int i = 0; i < first.getPieces().size(); i++) {
            StructureBox a = first.getPieces().get(i).getBounds();
            StructureBox b = second.getPieces().get(i).getBounds();
            assertEquals(a.minX, b.minX, "piece " + i + " minX");
            assertEquals(a.minY, b.minY, "piece " + i + " minY");
            assertEquals(a.minZ, b.minZ, "piece " + i + " minZ");
            assertEquals(a.maxX, b.maxX, "piece " + i + " maxX");
            assertEquals(a.maxY, b.maxY, "piece " + i + " maxY");
            assertEquals(a.maxZ, b.maxZ, "piece " + i + " maxZ");
        }
    }

    // ------------------------------------------------------- the well writes

    @Test
    void theWellWritesItsShaftWaterAndRoof() {
        VillageStructure villages = new VillageStructure(555L);
        RecordingWorld world = new RecordingWorld();
        StructureStart hit = null;
        for (int regionX = -120; regionX <= 120 && hit == null; regionX++) {
            for (int regionZ = -120; regionZ <= 120; regionZ++) {
                StructureStart start = villages.createStart(regionX, regionZ, world);
                if (start != null) {
                    hit = start;
                    break;
                }
            }
        }
        assertNotNull(hit);
        VillagePieces.Well well = (VillagePieces.Well) hit.getPieces().getFirst();
        well.postProcess(world, new Random(1), fullClip());

        StructureBox box = well.getBounds();
        // The water sits in the shaft's center (the 2x2 opening at y 12).
        int waterX1 = box.minX + 2;
        int waterZ1 = box.minZ + 2;
        int baseY = box.minY;
        assertEquals(palette.water(), world.at(waterX1, baseY + 11, waterZ1),
                "the water column's top (the reference's y-11 below the gravel ring)");
        assertEquals(palette.cobblestone(), world.at(box.minX + 1, baseY + 11, box.minZ + 1),
                "the shaft's cobblestone rim");
        // The gravel ring at the surface level (y 11 on the border cells).
        assertEquals(palette.gravel(), world.at(box.minX, baseY + 11, box.minZ));
        // The fence posts above the ring (y 13-14 on the corners).
        assertEquals(palette.fence(), world.at(box.minX + 1, baseY + 13, box.minZ + 1));
        assertEquals(palette.fence(), world.at(box.minX + 1, baseY + 14, box.minZ + 1));
        // The cobble roof at y 15.
        assertEquals(palette.cobblestone(), world.at(box.minX + 1, baseY + 15, box.minZ + 1));
        // The re-base put the gravel ring at the surface (y 11 == hpos + 1
        // over the 64-based box: minY = hpos - 11, so 11 lands at hpos).
        assertEquals(RecordingWorld.SURFACE, baseY + 11, "the ring sits at the surface");
    }

    // ------------------------------------------------------- the farm writes

    @Test
    void theFarmPlantsItsCropsOverFarmlandAndWater() {
        VillageStructure villages = new VillageStructure(777L);
        RecordingWorld world = new RecordingWorld();
        StructureStart hit = null;
        for (int regionX = -130; regionX <= 130 && hit == null; regionX++) {
            for (int regionZ = -130; regionZ <= 130; regionZ++) {
                StructureStart start = villages.createStart(regionX, regionZ, world);
                if (start != null) {
                    hit = start;
                    break;
                }
            }
        }
        assertNotNull(hit);
        // Walk the pieces for the two farm families (any village carries
        // one at the reference's own weights more often than not; the walk
        // below scans a wide cell range to find one that does).
        VillagePieces.Farm farm = null;
        VillagePieces.DoubleFarm doubleFarm = null;
        for (StructurePiece piece : hit.getPieces()) {
            if (piece instanceof VillagePieces.DoubleFarm foundDouble) {
                doubleFarm = foundDouble;
            } else if (piece instanceof VillagePieces.Farm found) {
                farm = found;
            }
        }
        // Not every village carries a farm: when neither family placed,
        // regenerate from another cell until one does (the deterministic
        // walk answers the same pieces every time — a different region
        // is a different village).
        if (farm == null && doubleFarm == null) {
            outer:
            for (int regionX = 131; regionX <= 400; regionX++) {
                for (int regionZ = 131; regionZ <= 400; regionZ++) {
                    StructureStart other = villages.createStart(regionX, regionZ, world);
                    if (other == null) {
                        continue;
                    }
                    for (StructurePiece piece : other.getPieces()) {
                        if (piece instanceof VillagePieces.DoubleFarm foundDouble) {
                            doubleFarm = foundDouble;
                            break outer;
                        }
                        if (piece instanceof VillagePieces.Farm found) {
                            farm = found;
                            break outer;
                        }
                    }
                }
            }
        }
        assertTrue(farm != null || doubleFarm != null, "some village carries a farm family");
        VillagePieces.VillagePiece plot = farm != null ? farm : doubleFarm;
        plot.postProcess(world, new Random(7), fullClip());

        StructureBox box = plot.getBounds();
        int baseY = box.minY;
        // The farmland row and the water channel (the farm's own layout:
        // farmland 1-2 and 4-5, the water at 3).
        int minX = box.minX;
        int minZ = box.minZ;
        assertEquals(palette.farmland(), world.at(minX + 1, baseY, minZ + 1));
        assertEquals(palette.water(), world.at(minX + 3, baseY, minZ + 1));
        assertEquals(palette.log(), world.at(minX, baseY, minZ), "the log frame corner");
        // A crop stands on the farmland (one of the three families).
        BlockType crop = world.at(minX + 1, baseY + 1, minZ + 1);
        assertNotNull(crop);
        String cropId = crop.identifier().value();
        assertTrue(cropId.startsWith("wheat_stage") || cropId.startsWith("carrots_stage")
                        || cropId.startsWith("potatoes_stage"),
                "a crop family stands on the farmland: " + cropId);
        int stage = net.zaminmc.torch.server.block.WorldSolidity.cropStageOf(crop.identifier());
        assertTrue(stage >= 2 && stage <= 7, "the planted stage rolls 2..7: " + stage);
    }

    // ------------------------------------------------- the desert + chest + villagers

    @Test
    void theDesertVillageSwapsItsPalette() {
        RecordingWorld world = new RecordingWorld();
        // The desert flag rides the START's construction (the reference's
        // own NBT-persisted flag): a world whose biome answers desert
        // builds desert-swapped pieces.
        world.desertBox = new int[]{-100000, -100000, 100000, 100000};
        VillagePieces.VillageStart desertStart = new VillagePieces.VillageStart(
                world, 0, new Random(5), 0, 0,
                VillagePieces.getPieceWeights(new Random(9), 0), 0);
        assertTrue(desertStart.isDesertVillage, "the desert world's start reads desert");
        assertEquals(palette.sandstone(), desertStart.getBlockForVariant(palette.cobblestone(), world));
        assertEquals(palette.smoothSandstone(), desertStart.getBlockForVariant(palette.planks(), world));
        assertEquals(palette.sandstone(), desertStart.getBlockForVariant(palette.log(), world));
        assertEquals(palette.sandstone(), desertStart.getBlockForVariant(palette.gravel(), world));
        assertEquals(palette.sandstoneStairs(0),
                desertStart.getBlockForVariant(palette.oakStairs(0), world));
        assertEquals(palette.sandstoneStairs(0),
                desertStart.getBlockForVariant(palette.stoneStairs(0), world));
        assertEquals(palette.stoneSlab(),
                desertStart.getBlockForVariant(palette.stoneSlab(), world), "the non-swapped pass through");
        assertEquals(palette.torch(), desertStart.getBlockForVariant(palette.torch(), world));
    }

    @Test
    void theBlacksmithPlacesHisChestAndVillagersWithProfessions() {
        VillageStructure villages = new VillageStructure(31337L);
        RecordingWorld world = new RecordingWorld();
        StructureStart hit = null;
        for (int regionX = -200; regionX <= 200 && hit == null; regionX++) {
            for (int regionZ = -200; regionZ <= 200; regionZ++) {
                StructureStart start = villages.createStart(regionX, regionZ, world);
                if (start != null) {
                    hit = start;
                    break;
                }
            }
        }
        assertNotNull(hit);
        // PostProcess every piece over the recording world: the buildings'
        // villagers and (when the village carries a blacksmith) the chest
        // ride the pending records.
        Random random = new Random(99);
        for (StructurePiece piece : hit.getPieces()) {
            piece.postProcess(world, random, fullClip());
        }
        // Every villager record carries a profession (or -1 for the base
        // roll), and the buildings' professions land in the 0..4 band.
        for (int[] villager : world.villagers) {
            assertTrue(villager[3] >= -1 && villager[3] <= 4,
                    "the profession band: " + villager[3]);
        }
        if (world.chests.isEmpty()) {
            return; // this village carries no blacksmith (the weight roll)
        }
        for (int[] chest : world.chests) {
            assertTrue(chest[3] >= 3 && chest[3] <= 8,
                    "the chest carries 3..8 rolled stacks: " + chest[3]);
            assertEquals(BuiltinBlocks.CHEST, world.at(chest[0], chest[1], chest[2]),
                    "the chest block stands at the record");
        }
    }

    /** A clip that contains everything (NOT StructureBox.infinite — that is the empty union accumulator). */
    private static StructureBox fullClip() {
        return new StructureBox(-1_000_000, 1, -1_000_000, 1_000_000, 512, 1_000_000);
    }
}
