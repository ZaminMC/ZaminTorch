package net.zaminmc.torch.server.world.structure;

import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.BlockRegistryBuilder;
import net.zaminmc.torch.server.block.BlockRegistryBuilder.FrozenBlockRegistry;
import net.zaminmc.torch.server.item.BuiltinItems;
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
 * The stronghold placement (Slice 12c — the reference port of
 * StrongholdStructure + StrongholdPieces over the engine's deterministic
 * generator): the three-position table from the world seed, the
 * findBiome reservoir snap, the weight-table walk with the forced
 * FiveWayCrossing after the source stairs, the end-portal-room retry gate,
 * and the same-seed regeneration identity.
 */
class StrongholdStructureTest {

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
     * The flat recording world the piece postProcess probes write into: a
     * stone floor under y 64, air above (the stronghold's pieces all sit
     * below sea level after the re-base, their fills carving into the
     * stone).
     */
    private static final class RecordingWorld implements StructureWorld {
        static final int SURFACE = 70;

        final Map<Long, BlockType> writes = new HashMap<>();
        final List<int[]> chests = new ArrayList<>();
        final List<Map<Integer, Integer>> chestEnchantments = new ArrayList<>();

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
            return NormalWorldGenerator.SEA_LEVEL;
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
            return Biome.PLAINS;
        }

        @Override
        public void placeGenChest(int x, int y, int z, List<GenLootSlot> slots) {
            chests.add(new int[]{x, y, z, slots.size()});
            for (GenLootSlot slot : slots) {
                if (slot.enchantments() != null) {
                    chestEnchantments.add(slot.enchantments());
                }
            }
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
    void thePositionTableCarriesExactlyThreeStrongholds() {
        RecordingWorld world = new RecordingWorld();
        StrongholdStructure strongholds = new StrongholdStructure(12345L, world);
        List<int[]> positions = strongholds.positions();
        assertEquals(3, positions.size(), "the reference's three-position table");
        // The positions are chunk coordinates within a reasonable ring of
        // the origin (the distance-32 spiral with the world-seed angle).
        for (int[] position : positions) {
            double blocks = Math.sqrt(position[0] * (long) position[0]
                    + position[1] * (long) position[1]) * 16.0;
            assertTrue(blocks > 100.0, "the first ring sits beyond ~100 blocks (got "
                    + blocks + " at " + position[0] + "," + position[1] + ")");
        }
        // The same seed regenerates the identical table.
        StrongholdStructure again = new StrongholdStructure(12345L, world);
        for (int i = 0; i < 3; i++) {
            assertEquals(positions.get(i)[0], again.positions().get(i)[0], "position " + i + " x");
            assertEquals(positions.get(i)[1], again.positions().get(i)[1], "position " + i + " z");
        }
    }

    @Test
    void theStartsAppearOnlyAtThePositions() {
        RecordingWorld world = new RecordingWorld();
        StrongholdStructure strongholds = new StrongholdStructure(987654321L, world);
        Set<Long> positions = new HashSet<>();
        for (int[] position : strongholds.positions()) {
            positions.add(((long) position[0] << 32) | (position[1] & 0xFFFFFFFFL));
        }
        int hits = 0;
        for (int regionX = -200; regionX <= 200; regionX++) {
            for (int regionZ = -200; regionZ <= 200; regionZ++) {
                StructureStart start = strongholds.createStart(regionX, regionZ, world);
                if (start != null) {
                    assertTrue(positions.contains(((long) regionX << 32) | (regionZ & 0xFFFFFFFFL)),
                            "a start appeared off the position table: " + regionX + "," + regionZ);
                    hits++;
                }
            }
        }
        assertEquals(3, hits, "every position carries exactly one stronghold (the ±200 scan covers the rings)");
    }

    @Test
    void theRetryGateGuaranteesAnEndPortalRoom() {
        RecordingWorld world = new RecordingWorld();
        for (long seed = 1L; seed <= 12L; seed++) {
            StrongholdStructure strongholds = new StrongholdStructure(seed, world);
            for (int[] position : strongholds.positions()) {
                StructureStart start = strongholds.createStart(position[0], position[1], world);
                assertNotNull(start, "the position " + position[0] + "," + position[1]
                        + " carries a start (the retry loop never hangs)");
                assertTrue(start instanceof StrongholdStructure.StrongholdStructureStart);
                StrongholdStructure.StrongholdStructureStart strongholdStart =
                        (StrongholdStructure.StrongholdStructureStart) start;
                assertNotNull(strongholdStart.endPortalRoom(),
                        "the retry gate leaves an end portal room (seed " + seed + ")");
                assertFalse(start.getPieces().isEmpty(), "the walk leaves pieces");
            }
        }
    }

    @Test
    void theWalkStartsOnTheSpiralStaircaseAndForcesTheCrossing() {
        RecordingWorld world = new RecordingWorld();
        StrongholdStructure strongholds = new StrongholdStructure(4242L, world);
        int[] position = strongholds.positions().get(0);
        StructureStart start = strongholds.createStart(position[0], position[1], world);
        assertNotNull(start);
        StructurePiece first = start.getPieces().getFirst();
        assertTrue(first instanceof StrongholdPieces.StrongholdStart,
                "the source staircase anchors the walk");
        assertTrue(start.getPieces().size() > 2, "the stronghold carries pieces: "
                + start.getPieces().size());
        // The bounds sit below sea level after the re-base (the
        // moveBelowSeaLevel(10) walk).
        assertTrue(start.getBounds().maxY <= world.seaLevel() - 10,
                "the re-base lands below sea level - 10 (got maxY " + start.getBounds().maxY + ")");
    }

    @Test
    void theSameSeedRegeneratesTheIdenticalPieceSet() {
        RecordingWorld world = new RecordingWorld();
        StrongholdStructure strongholds = new StrongholdStructure(777L, world);
        List<String> first = describe(strongholds, world);
        List<String> second = describe(strongholds, world);
        assertEquals(first, second, "the same seed regenerates the identical piece set");
        assertTrue(first.size() > 2, "the walk leaves pieces: " + first.size());
    }

    /** The piece set's fingerprint (the kind + the bounds, per piece). */
    private static List<String> describe(StrongholdStructure strongholds, RecordingWorld world) {
        List<String> lines = new ArrayList<>();
        for (int[] position : strongholds.positions()) {
            StructureStart start = strongholds.createStart(position[0], position[1], world);
            for (StructurePiece piece : start.getPieces()) {
                lines.add(piece.getClass().getSimpleName() + "@"
                        + piece.getBounds().minX + "," + piece.getBounds().minY + ","
                        + piece.getBounds().minZ + "->" + piece.getBounds().maxX + ","
                        + piece.getBounds().maxY + "," + piece.getBounds().maxZ);
            }
        }
        return lines;
    }

    @Test
    void thePortalRoomWritesItsFramesLavaAndSpawner() {
        RecordingWorld world = new RecordingWorld();
        StrongholdStructure strongholds = new StrongholdStructure(555L, world);
        // The position whose start carries the portal room: populate every
        // chunk the portal room's bounds touch, then verify its writes.
        int[] position = strongholds.positions().get(0);
        StrongholdStructure.StrongholdStructureStart start =
                (StrongholdStructure.StrongholdStructureStart) strongholds.createStart(
                        position[0], position[1], world);
        StrongholdPieces.EndPortalRoom room = start.endPortalRoom();
        assertNotNull(room, "the retry gate guarantees the room");
        StructureBox bounds = room.getBounds();
        // Every chunk the room touches populates through the ordinary walk.
        for (int chunkX = bounds.minX >> 4; chunkX <= bounds.maxX >> 4; chunkX++) {
            for (int chunkZ = bounds.minZ >> 4; chunkZ <= bounds.maxZ >> 4; chunkZ++) {
                strongholds.populate(chunkX, chunkZ, world);
            }
        }
        // The portal room's own box holds stone bricks, lava and frames.
        int stoneBrickFamily = 0;
        int lava = 0;
        int frames = 0;
        int spawner = 0;
        int ironBars = 0;
        for (var entry : world.writes.entrySet()) {
            long key = entry.getKey();
            // Sign-extend the packed 20/20/24-bit fields (negative
            // coordinates pack as unsigned offsets).
            int x = (int) ((key >> 44) << 12) >> 12;
            int z = (int) (((key >> 24) & 0xFFFFF) << 12) >> 12;
            int y = (int) ((key & 0xFFFFFF) << 8) >> 8;
            if (x >= bounds.minX && x <= bounds.maxX
                    && y >= bounds.minY && y <= bounds.maxY
                    && z >= bounds.minZ && z <= bounds.maxZ) {
                String value = entry.getValue().identifier().value();
                if (value.equals("stone_bricks") || value.equals("mossy_stone_bricks")
                        || value.equals("cracked_stone_bricks")) {
                    stoneBrickFamily++;
                } else if (value.equals("lava")) {
                    lava++;
                } else if (value.startsWith("end_portal_frame")) {
                    frames++;
                } else if (value.equals("mob_spawner")) {
                    spawner++;
                } else if (value.equals("iron_bars")) {
                    ironBars++;
                }
            }
        }
        assertTrue(stoneBrickFamily > 500, "the room's shell is the stone-brick family (got "
                + stoneBrickFamily + ")");
        assertTrue(lava >= 13, "the moat + the two pools (got " + lava + ")");
        assertEquals(12, frames, "the twelve portal frames");
        assertEquals(1, spawner, "the silverfish spawner");
        assertTrue(ironBars > 10, "the wall columns (got " + ironBars + ")");
        // The frames split between the eye and the plain pair.
        int eyes = 0;
        for (var entry : world.writes.entrySet()) {
            if (entry.getValue().identifier().value().startsWith("end_portal_frame_eye")) {
                eyes++;
            }
        }
        assertTrue(eyes <= 12, "the eye rolls land on at most the twelve frames (got " + eyes + ")");
    }

    @Test
    void theStoneBrickPickerLeavesAllThreeFamilies() {
        RecordingWorld world = new RecordingWorld();
        StrongholdStructure strongholds = new StrongholdStructure(999L, world);
        int[] position = strongholds.positions().get(0);
        StructureStart start = strongholds.createStart(position[0], position[1], world);
        StructureBox all = start.getBounds();
        for (int chunkX = all.minX >> 4; chunkX <= all.maxX >> 4; chunkX++) {
            for (int chunkZ = all.minZ >> 4; chunkZ <= all.maxZ >> 4; chunkZ++) {
                strongholds.populate(chunkX, chunkZ, world);
            }
        }
        int plain = 0;
        int mossy = 0;
        int cracked = 0;
        int infested = 0;
        for (BlockType type : world.writes.values()) {
            String value = type.identifier().value();
            switch (value) {
                case "stone_bricks" -> plain++;
                case "mossy_stone_bricks" -> mossy++;
                case "cracked_stone_bricks" -> cracked++;
                case "infested_stone_bricks" -> infested++;
                default -> {
                }
            }
        }
        assertTrue(plain > 100, "the plain brick dominates (got " + plain + ")");
        assertTrue(mossy > 0, "the mossy roll lands");
        assertTrue(cracked > 0, "the cracked roll lands");
        // The infested band is 5% of the picker's rolls — measured against
        // the plain family's 45% share, the ratio lands near 5/45 = 11% of
        // the plain count (the one-sided headroom covers the band).
        assertTrue(infested <= plain / 8, "the infested band stays inside its roll share (got "
                + infested + " over " + plain + " plain)");
    }

    @Test
    void theChestLootWalkLeavesTheBookEnchantment() {
        // Over several seeds, some library or chest corridor rolls the
        // enchanted-book row; the rolled enchantment rides the slot.
        boolean anyChest = false;
        boolean anyBook = false;
        boolean anyKnownLoot = false;
        for (long seed = 10L; seed <= 40L; seed++) {
            RecordingWorld world = new RecordingWorld();
            StrongholdStructure strongholds = new StrongholdStructure(seed, world);
            for (int[] position : strongholds.positions()) {
                StructureStart start = strongholds.createStart(position[0], position[1], world);
                StructureBox all = start.getBounds();
                for (int chunkX = all.minX >> 4; chunkX <= all.maxX >> 4; chunkX++) {
                    for (int chunkZ = all.minZ >> 4; chunkZ <= all.maxZ >> 4; chunkZ++) {
                        strongholds.populate(chunkX, chunkZ, world);
                    }
                }
            }
            anyChest |= !world.chests.isEmpty();
            anyBook |= !world.chestEnchantments.isEmpty();
            for (int[] chest : world.chests) {
                if (chest[3] > 0) {
                    anyKnownLoot = true;
                }
            }
        }
        assertTrue(anyChest, "some stronghold carries a loot chest");
        assertTrue(anyBook, "some enchanted-book row rolls its enchantment");
        assertTrue(anyKnownLoot, "the chest records carry their rolled slots");
    }

    @Test
    void thePrisonPlacesItsIronDoors() {
        // Scan several seeds for a prison piece; its chunk writes the iron
        // door pair + the bars.
        boolean found = false;
        for (long seed = 100L; seed <= 160L && !found; seed++) {
            RecordingWorld world = new RecordingWorld();
            StrongholdStructure strongholds = new StrongholdStructure(seed, world);
            for (int[] position : strongholds.positions()) {
                StructureStart start = strongholds.createStart(position[0], position[1], world);
                StructureBox all = start.getBounds();
                for (int chunkX = all.minX >> 4; chunkX <= all.maxX >> 4; chunkX++) {
                    for (int chunkZ = all.minZ >> 4; chunkZ <= all.maxZ >> 4; chunkZ++) {
                        strongholds.populate(chunkX, chunkZ, world);
                    }
                }
                boolean hasPrison = false;
                for (StructurePiece piece : start.getPieces()) {
                    if (piece instanceof StrongholdPieces.Prison) {
                        hasPrison = true;
                        break;
                    }
                }
                if (hasPrison) {
                    int ironDoors = 0;
                    for (BlockType type : world.writes.values()) {
                        String value = type.identifier().value();
                        if (value.startsWith("iron_door")) {
                            ironDoors++;
                        }
                    }
                    assertTrue(ironDoors >= 4, "the prison's two iron door pairs (got "
                            + ironDoors + ")");
                    found = true;
                    break;
                }
            }
        }
        assertTrue(found, "some seed in the band carries a prison");
    }

    @Test
    void theStrongholdItemsRegisterOnTheWire() {
        // The loot family's three new items + the iron door resolve.
        assertTrue(BuiltinItems.lookup(
                net.zaminmc.torch.util.Identifier.parse("minecraft:ender_pearl")).isPresent());
        assertTrue(BuiltinItems.lookup(
                net.zaminmc.torch.util.Identifier.parse("minecraft:empty_map")).isPresent());
        assertTrue(BuiltinItems.lookup(
                net.zaminmc.torch.util.Identifier.parse("minecraft:compass")).isPresent());
        assertTrue(BuiltinItems.lookup(
                net.zaminmc.torch.util.Identifier.parse("minecraft:iron_door")).isPresent());
    }
}
