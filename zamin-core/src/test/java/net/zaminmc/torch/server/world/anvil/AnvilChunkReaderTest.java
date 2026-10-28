package net.zaminmc.torch.server.world.anvil;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.server.block.BlockRegistryBuilder;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.world.EngineChunk;
import net.zaminmc.torch.util.Identifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Anvil chunk transform (the Anvil canvas' block leg): the fixture
 * writer's reference-shaped chunk NBT becomes one detached engine chunk
 * — the numeric states map 1:1 through the table, the data nibble rides
 * the reference's own nibble arithmetic, the biomes row lands in the
 * chunk's own row, and every 1.8.8 quirk and isolation branch behaves
 * (the missing-Data-is-zeros quirk, the pre-Anvil rejection, the Add
 * nibble, the double-plant virtual species, the unknown-id air with a
 * counted state — never a crash, never a silent substitute).
 */
class AnvilChunkReaderTest {

    private net.zaminmc.torch.block.BlockRegistry registry;
    private AnvilChunkReader reader;
    private AnvilImportLedger ledger;

    @BeforeEach
    void setUp() {
        // The test registry: BuiltinBlocks + fluids + redstone (the
        // EngineServer chain's Builtin-block subset the reader needs —
        // fluids and redstone carry the table's dynamic families).
        var builder = new BlockRegistryBuilder();
        builder = BuiltinBlocks.registerAll(builder);
        builder = net.zaminmc.torch.server.block.FluidBlocks.registerAll(builder);
        builder = net.zaminmc.torch.server.redstone.RedstoneBlocks.registerAll(builder);
        registry = builder.freeze();
        reader = new AnvilChunkReader(registry);
        ledger = new AnvilImportLedger();
    }

    private EngineChunk read(Map<String, Object> chunkNbt) {
        AnvilChunkReader.ReadResult result = reader.read(
                new ChunkPosition(0, 0), AnvilDocument(chunkNbt), ledger);
        assertTrue(result instanceof AnvilChunkReader.ReadChunk,
                "the well-formed chunk reads: " + result);
        return ((AnvilChunkReader.ReadChunk) result).chunk();
    }

    /** The boxed tree through the reader's own input model. */
    private static Map<String, net.zaminmc.torch.server.nbt.Snbt.Tag> AnvilDocument(
            Map<String, Object> boxed) {
        return net.zaminmc.torch.server.nbt.NbtOps.fromBoxed(boxed);
    }

    // ------------------------------------------------------------------ the round trip

    @Test
    void blocksDataAndBiomesRoundTrip() {
        byte[] blocks = new byte[4096];
        byte[] data = new byte[2048];
        // Stone at (0,0,0); smooth sandstone (id 24 meta 2) at (5,2,3); red
        // wool (id 35 meta 14) at (15,8,15); water source (id 9) at (1,20,1).
        blocks[AnvilFixture.cellIndex(0, 0, 0)] = 1;
        blocks[AnvilFixture.cellIndex(5, 2, 3)] = 24;
        AnvilFixture.nibble(data, AnvilFixture.cellIndex(5, 2, 3), 2);
        blocks[AnvilFixture.cellIndex(15, 8, 15)] = 35;
        AnvilFixture.nibble(data, AnvilFixture.cellIndex(15, 8, 15), 14);
        blocks[AnvilFixture.cellIndex(1, 4, 1)] = 9; // section 0's water
        byte[] upperBlocks = new byte[4096];
        upperBlocks[AnvilFixture.cellIndex(1, 4, 1)] = 9; // section 1's water (world y 20)
        byte[] biomes = new byte[256];
        biomes[(3 << 4) | 2] = 2; // desert at column (2,3)
        biomes[(7 << 4) | 9] = 4; // forest at column (9,7)

        EngineChunk chunk = read(AnvilFixture.chunkNbt(0, 0,
                List.of(AnvilFixture.section(0, blocks, data, null),
                        AnvilFixture.section(1, upperBlocks, new byte[2048], null)),
                biomes));

        assertEquals("minecraft:stone", blockAt(chunk, 0, 0, 0));
        assertEquals("minecraft:smooth_sandstone", blockAt(chunk, 5, 2, 3), "the meta nibble resolves the variant");
        assertEquals("minecraft:red_wool", blockAt(chunk, 15, 8, 15), "the wool color rides the nibble");
        assertEquals("minecraft:water", blockAt(chunk, 1, 4, 1), "the first section's water");
        assertEquals("minecraft:water", blockAt(chunk, 1, 20, 1), "the second section's water");
        assertEquals("minecraft:air", blockAt(chunk, 2, 5, 2), "the untouched cell is air");
        assertEquals(2, chunk.biome(2, 3), "the desert column");
        assertEquals(4, chunk.biome(9, 7), "the forest column");
        assertEquals(0, chunk.biome(0, 0),
                "the row's untouched cells carry their own 0 (ocean) — the whole row replaces the plains default, the reference's setBiomes");
        assertEquals(0, ledger.totalIsolated(), "a clean chunk isolates nothing: " + ledger.summary());
        assertEquals(0, ledger.entriesUnoccupied());
    }

    private static String blockAt(EngineChunk chunk, int x, int y, int z) {
        return chunk.getBlock(x, y, z).identifier().toString();
    }

    @Test
    void theCropStagesAndFarmlandMoistureResolve() {
        byte[] blocks = new byte[4096];
        byte[] data = new byte[2048];
        blocks[AnvilFixture.cellIndex(0, 0, 0)] = 59; // wheat
        AnvilFixture.nibble(data, AnvilFixture.cellIndex(0, 0, 0), 4);
        blocks[AnvilFixture.cellIndex(1, 0, 0)] = 60; // farmland, moisture 7
        AnvilFixture.nibble(data, AnvilFixture.cellIndex(1, 0, 0), 7);
        EngineChunk chunk = read(AnvilFixture.chunkNbt(0, 0,
                List.of(AnvilFixture.section(0, blocks, data, null)), null));
        assertEquals("minecraft:wheat_stage4", blockAt(chunk, 0, 0, 0),
                "the AGE property is the metadata nibble (the engine's own variant nibble)");
        assertEquals("minecraft:farmland_wet", blockAt(chunk, 1, 0, 0),
                "the moisture band 2..7 flattens to the wet skin");
    }

    @Test
    void theInhabitedTimeRowCarriesIntoTheChunk() {
        // VIII14 — the local-difficulty completion: the reference's
        // AnvilChunkStorage round trip writes the row beside the blocks
        // (line 183); the engine's chunk carries the same row, the local
        // difficulty's own inhabited term reads it at the mob's position.
        byte[] blocks = new byte[4096];
        byte[] data = new byte[2048];
        blocks[AnvilFixture.cellIndex(0, 0, 0)] = 1; // stone
        Map<String, Object> chunkNbt = AnvilFixture.chunkNbt(0, 0,
                List.of(AnvilFixture.section(0, blocks, data, null)), null);
        @SuppressWarnings("unchecked")
        Map<String, Object> level = (Map<String, Object>) chunkNbt.get("Level");
        level.put("InhabitedTime", 1_234_567L); // a lived-in vanilla chunk
        EngineChunk chunk = read(chunkNbt);
        assertEquals(1_234_567L, chunk.inhabitedTime(),
                "the inhabited row rides the imported chunk (the local difficulty's own term)");

        // The absent row keeps the fresh 0 (the reference's getLong
        // default's own shape — a never-watched chunk).
        Map<String, Object> absentNbt = AnvilFixture.chunkNbt(0, 0,
                List.of(AnvilFixture.section(0, blocks, data, null)), null);
        @SuppressWarnings("unchecked")
        Map<String, Object> absentLevel = (Map<String, Object>) absentNbt.get("Level");
        absentLevel.remove("InhabitedTime");
        EngineChunk absent = read(absentNbt);
        assertEquals(0L, absent.inhabitedTime(), "the absent row reads as the fresh 0");
    }

    @Test
    void theFluidAndRedstoneLaddersResolve() {
        byte[] blocks = new byte[4096];
        byte[] data = new byte[2048];
        blocks[AnvilFixture.cellIndex(0, 0, 0)] = 9; // still water
        blocks[AnvilFixture.cellIndex(1, 0, 0)] = 8; // flowing water level 3
        AnvilFixture.nibble(data, AnvilFixture.cellIndex(1, 0, 0), 3);
        blocks[AnvilFixture.cellIndex(2, 0, 0)] = 55; // redstone wire power 6
        AnvilFixture.nibble(data, AnvilFixture.cellIndex(2, 0, 0), 6);
        EngineChunk chunk = read(AnvilFixture.chunkNbt(0, 0,
                List.of(AnvilFixture.section(0, blocks, data, null)), null));
        assertEquals("minecraft:water", blockAt(chunk, 0, 0, 0));
        assertEquals("minecraft:flowing_water_3", blockAt(chunk, 1, 0, 0));
        assertEquals("minecraft:redstone_wire_6", blockAt(chunk, 2, 0, 0));
    }

    // ------------------------------------------------------------------ the 1.8.8 quirks

    @Test
    void aMissingDataNibbleReadsAsAllZeros() {
        // The task's own quirk row: a section without Data represents all
        // metadata as 0 (the reference's getByteArray answers byte[0] for
        // the absent row; the crash its length check would then raise is
        // the import's documented replacement — the zeros).
        byte[] blocks = new byte[4096];
        blocks[AnvilFixture.cellIndex(0, 0, 0)] = 1; // stone
        blocks[AnvilFixture.cellIndex(1, 0, 0)] = 1; // stone without meta
        blocks[AnvilFixture.cellIndex(2, 0, 0)] = 35; // wool, meta 0 = white
        EngineChunk chunk = read(AnvilFixture.chunkNbt(0, 0,
                List.of(AnvilFixture.section(0, blocks, null, null)), null));
        assertEquals("minecraft:stone", blockAt(chunk, 0, 0, 0));
        assertEquals("minecraft:stone", blockAt(chunk, 1, 0, 0));
        assertEquals("minecraft:wool", blockAt(chunk, 2, 0, 0), "meta 0 is the white wool");
        assertEquals(0, ledger.totalIsolated(), "the quirk is not an isolation");
    }

    @Test
    void thePreAnvilFormatIsRejectedNotGuessed() {
        // A chunk whose Level has no Sections: the old MCRegion shape
        // (Blocks at the Level root) — the reference's own "missing block
        // data, skipping" refusal, kept as a chunk-level rejection.
        Map<String, Object> level = AnvilFixture.compound();
        level.put("xPos", 0);
        level.put("zPos", 0);
        level.put("Blocks", AnvilFixture.byteArray(new byte[4096])); // the old shape
        Map<String, Object> root = AnvilFixture.compound();
        root.put("Level", level);
        AnvilChunkReader.ReadResult result = reader.read(new ChunkPosition(0, 0),
                AnvilDocument(root), ledger);
        assertTrue(result instanceof AnvilChunkReader.Rejected rejected
                && rejected.reason().contains("missing block data"),
                "the pre-Anvil parts are refused: " + result);
        assertTrue(ledger.summary().contains("missing block data"),
                "the rejection is counted in the ledger");
    }

    @Test
    void aMissingLevelCompoundIsRejected() {
        Map<String, Object> root = AnvilFixture.compound();
        root.put("SomethingElse", AnvilFixture.compound());
        AnvilChunkReader.ReadResult result = reader.read(new ChunkPosition(0, 0),
                AnvilDocument(root), ledger);
        assertTrue(result instanceof AnvilChunkReader.Rejected rejected
                && rejected.reason().contains("missing level data"),
                "the reference's own first gate");
    }

    @Test
    void positionRowsThatDisagreeAreCountedNotObeyed() {
        // The reference relocates a chunk whose rows disagree; the entry
        // stays the authority here and the disagreement is a counted
        // anomaly (the arrays never depend on the rows).
        byte[] blocks = new byte[4096];
        blocks[AnvilFixture.cellIndex(0, 0, 0)] = 1;
        EngineChunk chunk = read(AnvilFixture.chunkNbt(99, -99,
                List.of(AnvilFixture.section(0, blocks, new byte[2048], null)), null));
        assertEquals(new ChunkPosition(0, 0), chunk.position(),
                "the entry's position is the authority");
        assertTrue(ledger.summary().contains("position rows disagree"),
                "the disagreement is a counted anomaly: " + ledger.summary());
    }

    // ------------------------------------------------------------------ the Add nibble

    @Test
    void theAddNibbleCarriesTheHighIdByteAndUnknownHighIdsIsolate() {
        // id = (add << 8) | blocks[i] — the id > 255 space. Vanilla has no
        // such blocks; the Add-present-all-zero case must be a no-op and a
        // genuine high id must isolate as air with a counted state.
        byte[] blocks = new byte[4096];
        byte[] add = new byte[2048];
        blocks[AnvilFixture.cellIndex(0, 0, 0)] = 1; // stone, add 0
        blocks[AnvilFixture.cellIndex(1, 0, 0)] = 1; // add nibble 1 -> id 257
        AnvilFixture.nibble(add, AnvilFixture.cellIndex(1, 0, 0), 1);
        EngineChunk chunk = read(AnvilFixture.chunkNbt(0, 0,
                List.of(AnvilFixture.section(0, blocks, new byte[2048], add)), null));
        assertEquals("minecraft:stone", blockAt(chunk, 0, 0, 0),
                "a zero Add nibble leaves the id untouched");
        assertEquals("minecraft:air", blockAt(chunk, 1, 0, 0),
                "a mod-space id (257) has no vanilla existence");
        assertTrue(ledger.summary().contains("id 257 meta 0 x1"),
                "the unknown state is counted by its exact numeric form: "
                        + ledger.summary());
    }

    // ------------------------------------------------------------------ the double plant

    @Test
    void theDoublePlantUpperResolvesItsSpeciesFromTheLower() {
        // The reference's resolveVirtualProperties: the upper's species is
        // virtual — re-read from the cell below. A sunflower lower at
        // y=4 with an upper (175, 10) at y=5 resolves sunflower_upper; an
        // orphan upper falls back to the tall-grass canonical.
        byte[] blocks = new byte[4096];
        byte[] data = new byte[2048];
        blocks[AnvilFixture.cellIndex(0, 4, 0)] = (byte) 175; // sunflower lower (meta 0)
        blocks[AnvilFixture.cellIndex(0, 5, 0)] = (byte) 175; // the upper (meta 10)
        AnvilFixture.nibble(data, AnvilFixture.cellIndex(0, 5, 0), 10);
        blocks[AnvilFixture.cellIndex(3, 9, 3)] = (byte) 175; // an orphan upper
        AnvilFixture.nibble(data, AnvilFixture.cellIndex(3, 9, 3), 10);
        EngineChunk chunk = read(AnvilFixture.chunkNbt(0, 0,
                List.of(AnvilFixture.section(0, blocks, data, null)), null));
        assertEquals("minecraft:sunflower", blockAt(chunk, 0, 4, 0));
        assertEquals("minecraft:sunflower_upper", blockAt(chunk, 0, 5, 0),
                "the upper's species follows its lower");
        assertEquals("minecraft:double_grass_upper", blockAt(chunk, 3, 9, 3),
                "the orphan upper falls back to the canonical");
    }

    // ------------------------------------------------------------------ the isolation branches

    @Test
    void aSectionYOutsideTheBandIsolates() {
        // The reference would index its section array with the byte and
        // crash; the import isolates the section and keeps the chunk.
        Map<String, Object> root = AnvilFixture.chunkNbt(0, 0,
                List.of(AnvilFixture.section(16, new byte[4096], new byte[2048], null)),
                null);
        EngineChunk chunk = read(root);
        assertEquals("minecraft:air", blockAt(chunk, 0, 0, 0), "the bad section's cells are air");
        assertTrue(ledger.summary().contains("section Y 16 outside 0..15"),
                "the reason names the rule: " + ledger.summary());
    }

    @Test
    void aMissingBlocksArrayIsolatesTheSection() {
        Map<String, Object> section = AnvilFixture.section(0, new byte[4096],
                new byte[2048], null);
        section.remove("Blocks");
        EngineChunk chunk = read(AnvilFixture.chunkNbt(0, 0, List.of(section), null));
        assertEquals("minecraft:air", blockAt(chunk, 0, 0, 0));
        assertTrue(ledger.summary().contains("missing Blocks array"),
                "the reason names the missing row: " + ledger.summary());
    }

    @Test
    void aWrongBlocksLengthIsolatesTheSection() {
        Map<String, Object> section = AnvilFixture.section(0, new byte[2048],
                new byte[2048], null);
        EngineChunk chunk = read(AnvilFixture.chunkNbt(0, 0, List.of(section), null));
        assertTrue(ledger.summary().contains("Blocks length 2048 != 4096"),
                "the reason names the length rule: " + ledger.summary());
    }

    @Test
    void aWrongDataLengthIsolatesTheSectionNotTheChunk() {
        // The reference's ChunkNibbleStorage would throw
        // IllegalArgumentException and crash the load; the import isolates
        // the one section and keeps the rest.
        byte[] blocks = new byte[4096];
        blocks[AnvilFixture.cellIndex(0, 0, 0)] = 1;
        Map<String, Object> good = AnvilFixture.section(0, blocks, new byte[2048], null);
        Map<String, Object> bad = AnvilFixture.section(1, new byte[4096], new byte[1024], null);
        EngineChunk chunk = read(AnvilFixture.chunkNbt(0, 0, List.of(good, bad), null));
        assertEquals("minecraft:stone", blockAt(chunk, 0, 0, 0), "the good section survives");
        assertEquals("minecraft:air", blockAt(chunk, 0, 16, 0), "the bad section is air");
        assertTrue(ledger.summary().contains("Data length 1024 != 2048"),
                "the reason names the nibble rule: " + ledger.summary());
    }

    @Test
    void aWrongAddLengthIsolatesTheSection() {
        Map<String, Object> section = AnvilFixture.section(0, new byte[4096],
                new byte[2048], new byte[512]);
        read(AnvilFixture.chunkNbt(0, 0, List.of(section), null));
        assertTrue(ledger.summary().contains("Add length 512 != 2048"),
                "the reason names the Add rule: " + ledger.summary());
    }

    @Test
    void aWrongBiomesLengthIsolatesTheBiomesRowOnly() {
        EngineChunk chunk = read(AnvilFixture.chunkNbt(0, 0,
                List.of(AnvilFixture.section(0, new byte[4096], new byte[2048], null)),
                new byte[100]));
        assertEquals(1, chunk.biome(0, 0), "the plains default survives the bad row");
        assertTrue(ledger.summary().contains("Biomes length 100 != 256"),
                "the reason names the biome rule: " + ledger.summary());
    }

    @Test
    void anUnknownVanillaIdIsolatesAsAirWithACountedState() {
        // A mod id (999) has no vanilla existence at all — it becomes air
        // with its exact state counted, never a crash. (The banner family
        // 176/177 used to ride this isolation row; the banner wave
        // registered both pairs, and the banner import has its own seat
        // below.)
        byte[] blocks = new byte[4096];
        blocks[AnvilFixture.cellIndex(1, 0, 0)] = (byte) 255; // (255, 0): unknown
        blocks[AnvilFixture.cellIndex(2, 0, 0)] = (byte) 231; // mod space via Add below
        byte[] add = new byte[2048];
        AnvilFixture.nibble(add, AnvilFixture.cellIndex(2, 0, 0), 3); // id 999
        EngineChunk chunk = read(AnvilFixture.chunkNbt(0, 0,
                List.of(AnvilFixture.section(0, blocks, new byte[2048], add)), null));
        assertEquals("minecraft:air", blockAt(chunk, 1, 0, 0));
        assertEquals("minecraft:air", blockAt(chunk, 2, 0, 0), "the mod id");
        assertTrue(ledger.summary().contains("id 999 meta 0 x1"),
                "the mod state is counted: " + ledger.summary());
    }

    @Test
    void theBannerFamilyImportsWithItsRotationAndFacingNibbles() {
        // Block.java lines 1375-1376: 176 the standing pair (the rotation
        // nibble folding to the engine's sign-model cardinals), 177 the
        // wall pair (the FACING nibble 2-5, the byId modulo folding the
        // out-of-band answers).
        byte[] blocks = new byte[4096];
        byte[] meta = new byte[2048];
        blocks[AnvilFixture.cellIndex(0, 0, 0)] = (byte) 176; // rotation 0: south
        AnvilFixture.nibble(meta, AnvilFixture.cellIndex(0, 0, 0), 0);
        blocks[AnvilFixture.cellIndex(1, 0, 0)] = (byte) 176; // rotation 8: north
        AnvilFixture.nibble(meta, AnvilFixture.cellIndex(1, 0, 0), 8);
        blocks[AnvilFixture.cellIndex(2, 0, 0)] = (byte) 177; // FACING 5: east
        AnvilFixture.nibble(meta, AnvilFixture.cellIndex(2, 0, 0), 5);
        blocks[AnvilFixture.cellIndex(3, 0, 0)] = (byte) 177; // nibble 9: byId south
        AnvilFixture.nibble(meta, AnvilFixture.cellIndex(3, 0, 0), 9);
        EngineChunk chunk = read(AnvilFixture.chunkNbt(0, 0,
                List.of(AnvilFixture.section(0, blocks, meta, null)), null));
        assertEquals("minecraft:standing_banner", blockAt(chunk, 0, 0, 0));
        assertEquals("minecraft:standing_banner_north", blockAt(chunk, 1, 0, 0));
        assertEquals("minecraft:wall_banner_east", blockAt(chunk, 2, 0, 0));
        assertEquals("minecraft:wall_banner_south", blockAt(chunk, 3, 0, 0));
    }

    @Test
    void theAgedCanesAndCactiImportWithTheirWholeAgeBand() {
        // The farming growth pass — the reference's AGE 0-15 accumulators
        // (SugarCaneBlock.AGE / CactusBlock.AGE, the getStateFromMetadata
        // nibble bands): a lived-in vanilla world carries every value (the
        // accumulator advances on every random tick a loaded block sits
        // near a player — 15 of 16 are NOT at 0), and the pre-audit
        // nibble-0-only rows isolated the rest AS AIR, deleting the cane
        // stands and cactus fields on import. The single-type flattening
        // carries the whole band (the farmland moisture band's own
        // precedent); the growth cadences ride the random-tick law.
        byte[] blocks = new byte[4096];
        byte[] meta = new byte[2048];
        blocks[AnvilFixture.cellIndex(0, 0, 0)] = (byte) 83; // fresh cane
        AnvilFixture.nibble(meta, AnvilFixture.cellIndex(0, 0, 0), 0);
        blocks[AnvilFixture.cellIndex(1, 0, 0)] = (byte) 83; // one tick from growing
        AnvilFixture.nibble(meta, AnvilFixture.cellIndex(1, 0, 0), 15);
        blocks[AnvilFixture.cellIndex(2, 0, 0)] = (byte) 83; // mid accumulator
        AnvilFixture.nibble(meta, AnvilFixture.cellIndex(2, 0, 0), 7);
        blocks[AnvilFixture.cellIndex(3, 0, 0)] = (byte) 81; // fresh cactus
        AnvilFixture.nibble(meta, AnvilFixture.cellIndex(3, 0, 0), 0);
        blocks[AnvilFixture.cellIndex(4, 0, 0)] = (byte) 81; // aged cactus
        AnvilFixture.nibble(meta, AnvilFixture.cellIndex(4, 0, 0), 5);
        blocks[AnvilFixture.cellIndex(5, 0, 0)] = (byte) 81; // the band's edge
        AnvilFixture.nibble(meta, AnvilFixture.cellIndex(5, 0, 0), 1);
        EngineChunk chunk = read(AnvilFixture.chunkNbt(0, 0,
                List.of(AnvilFixture.section(0, blocks, meta, null)), null));
        assertEquals("minecraft:sugar_cane", blockAt(chunk, 0, 0, 0));
        assertEquals("minecraft:sugar_cane", blockAt(chunk, 1, 0, 0), "age 15 imports");
        assertEquals("minecraft:sugar_cane", blockAt(chunk, 2, 0, 0), "age 7 imports");
        assertEquals("minecraft:cactus", blockAt(chunk, 3, 0, 0));
        assertEquals("minecraft:cactus", blockAt(chunk, 4, 0, 0), "age 5 imports");
        assertEquals("minecraft:cactus", blockAt(chunk, 5, 0, 0), "age 1 imports");
    }

    @Test
    void theLightRowsAreCountedAsIgnoredNotImported() {
        // Light is derived state: the engine recomputes it at publication
        // (the install hook's initial compute), so the stored rows are
        // present-but-ignored — counted in the ledger's not-imported rows.
        read(AnvilFixture.chunkNbt(0, 0,
                List.of(AnvilFixture.section(0, new byte[4096], new byte[2048], null)), null));
        assertTrue(ledger.summary().contains("BlockLight x1"),
                "the light row's presence is counted: " + ledger.summary());
        assertTrue(ledger.summary().contains("SkyLight x1"));
    }

    @Test
    void theEntityAndTileRowsRideTheResultRawAndTileTicksRideToo() {
        // The content follow-up: the reader CARRIES the Entities and
        // TileEntities compounds (the session's seams apply them after the
        // install — the reference's own order); TileTicks rides the same
        // way (the scheduled block-tick surface's own import arm lands the
        // rows after the tiles, before the entities).
        var zombie = AnvilFixture.entityRow("Zombie", 1.5, 1, 1.5);
        zombie.put("CustomName", "Anvil Zombie");
        var chest = AnvilFixture.tileRow("Chest", 1, 1, 1);
        Map<String, Object> root = AnvilFixture.chunkNbt(0, 0,
                List.of(AnvilFixture.section(0, new byte[4096], new byte[2048], null)),
                null, List.of(zombie), List.of(chest));
        @SuppressWarnings("unchecked")
        Map<String, Object> level = (Map<String, Object>) root.get("Level");
        level.put("TileTicks", List.of(AnvilFixture.compound(), AnvilFixture.compound()));
        AnvilChunkReader.ReadResult result = reader.read(
                new ChunkPosition(0, 0), AnvilDocument(root), ledger);
        assertTrue(result instanceof AnvilChunkReader.ReadChunk, "the chunk reads");
        AnvilChunkReader.ReadChunk loaded = (AnvilChunkReader.ReadChunk) result;
        assertEquals(1, loaded.entities().size(), "the entity row rides the result");
        assertEquals("Zombie",
                net.zaminmc.torch.server.nbt.NbtOps.getString(loaded.entities().get(0), "id"),
                "the row is the raw compound (the seam's own interpretation)");
        assertEquals("Anvil Zombie",
                net.zaminmc.torch.server.nbt.NbtOps.getString(loaded.entities().get(0), "CustomName"));
        assertEquals(1, loaded.tileEntities().size(), "the tile row rides the result");
        assertEquals("Chest",
                net.zaminmc.torch.server.nbt.NbtOps.getString(loaded.tileEntities().get(0), "id"));
        assertEquals(2, loaded.tileTicks().size(), "the tick rows ride the result raw");
        assertTrue(ledger.summary().contains("TileTicks") == false,
                "the ticks are no longer counted-ignored (the seam lands them): " + ledger.summary());
        assertTrue(ledger.summary().contains("Entities x") == false,
                "the entities are no longer counted-ignored: " + ledger.summary());
    }

    @Test
    void aSectionListHoldingNonCompoundsIsolatesThoseRows() {
        // The reference's getCompound(l) would crash on a non-compound
        // row; the import isolates the row and keeps the walk.
        List<Object> sections = new ArrayList<>();
        sections.add("not a section");
        sections.add(AnvilFixture.section(0, new byte[4096], new byte[2048], null));
        Map<String, Object> level = AnvilFixture.compound();
        level.put("xPos", 0);
        level.put("zPos", 0);
        level.put("Sections", sections);
        Map<String, Object> root = AnvilFixture.compound();
        root.put("Level", level);
        EngineChunk chunk = read(root);
        assertEquals("minecraft:air", blockAt(chunk, 0, 0, 0), "the good section imported");
        assertTrue(ledger.summary().contains("not a byte array") == false,
                "no bogus isolation");
    }
}
