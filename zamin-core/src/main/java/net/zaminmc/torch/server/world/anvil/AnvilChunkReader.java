package net.zaminmc.torch.server.world.anvil;

import net.zaminmc.torch.block.BlockRegistry;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.server.nbt.BinaryNbtReader;
import net.zaminmc.torch.server.nbt.NbtOps;
import net.zaminmc.torch.server.nbt.Snbt;
import net.zaminmc.torch.server.world.EngineChunk;
import net.zaminmc.torch.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The Anvil chunk transform (the Anvil canvas' block leg — the reference's
 * {@code AnvilChunkStorage.createChunkFromNbt} read walk, ported 1:1 with
 * §31.6's no-crash isolation): the chunk NBT (already decoded by
 * {@link BinaryNbtReader} into the engine's {@code Map<String, Snbt.Tag>}
 * model) becomes one detached {@link EngineChunk} ready for the
 * publication path — {@code EngineWorld.installGenerated}, the same route
 * the pregenerated chunks take, so the light engine's existing hook lights
 * the imported blocks exactly like generated ones.
 *
 * <p><b>The reference's own gates (rejections, 1:1):</b> a chunk without a
 * {@code Level} compound is refused ("missing level data"); a chunk whose
 * Level has no {@code Sections} list is refused ("missing block data") —
 * the pre-Anvil MCRegion shape carried its blocks at the Level root, and
 * the reference refuses those parts rather than guessing them.</p>
 *
 * <p><b>The 1.8.8 quirks (ported from the reference):</b> a section's
 * missing {@code Data} nibble array reads as all zeros (the reference's
 * {@code getByteArray} answers {@code new byte[0]} for an absent row —
 * the quirk the task's own format contract names); the optional
 * {@code Add} nibble carries the high id byte
 * ({@code id = (add << 8) | blocks[i]} — the id &gt; 255 space, mods only
 * in practice); the cell index is the YZX order
 * {@code (y << 8) | (z << 4) | x} and the nibble's low half answers even
 * ids ({@code ChunkNibbleStorage.get}); the {@code Biomes} row is
 * {@code z * 16 + x}. A section's xPos/zPos rows that disagree with the
 * entry's position are counted and the ENTRY stays authoritative — the
 * reference's own relocation behavior (the arrays' placement never
 * depends on the rows).</p>
 *
 * <p><b>The isolation discipline (the reference's crashes, replaced):</b>
 * a section Y outside 0..15, a missing {@code Blocks} array, or a wrong
 * {@code Blocks}/{@code Data}/{@code Add} length would crash the
 * reference's array walk ({@code ChunkNibbleArrays should be 2048 bytes}
 * et al.) — here the SECTION is isolated (its cells import as air) with
 * a counted reason, and the rest of the chunk imports. A numeric state
 * the engine has no block for imports as air with a counted unknown
 * state — never a crash, never a silent substitute.</p>
 *
 * <p><b>The carried content rows (the entities/tiles/tile-ticks
 * follow-up):</b> the {@code Entities}, {@code TileEntities}, and
 * {@code TileTicks} lists ride the result RAW — the reader does not
 * interpret them (the session's content seams apply them after the
 * chunk installs, the reference's own order: blocks, then tiles, then
 * the tile ticks, then entities); the tile-tick rows land through the
 * engine's own scheduled block-tick surface ({@code loadScheduledTick}
 * — the reference's import arm). The rejection gates and every
 * block-level quirk are unchanged.</p>
 *
 * <p><b>The ignored rows (the used-vs-ignored ledger):</b>
 * {@code BlockLight}/{@code SkyLight} are NOT imported — light is derived
 * state the engine recomputes at publication (the install hook's initial
 * compute), exactly like generated chunks; {@code HeightMap} is derived
 * state the engine walks on demand. The
 * {@code Entities}/{@code TileEntities}/{@code TileTicks} lists are
 * carried for the seams (each counted by the ledger's imported rows);
 * a blocks-only session (no content seams) counts them as
 * not-imported — the loud answer, never a silent drop. Of the level
 * rows, {@code LastUpdate}/{@code TerrainPopulated}/{@code LightPopulated}
 * are generation bookkeeping with no engine surface;
 * {@code InhabitedTime} IS imported (VIII14 — the local-difficulty
 * completion: the engine's chunk carries the row, the local difficulty's
 * own inhabited term reads it at the mob's position).</p>
 */
public final class AnvilChunkReader {

    /** One read outcome: the detached chunk, or the chunk-level refusal. */
    public sealed interface ReadResult permits ReadChunk, Rejected {
    }

    /**
     * The transformed chunk (detached, ready for installGenerated) plus
     * the RAW content rows the session applies after the install: the
     * entities and tile entities as compound trees, the scheduled tile
     * ticks as compound rows (the reader's own contract: it never
     * interprets them — the seams do, each with its own §31.6
     * isolation).
     */
    public record ReadChunk(EngineChunk chunk,
                            List<Map<String, Snbt.Tag>> entities,
                            List<Map<String, Snbt.Tag>> tileEntities,
                            List<Map<String, Snbt.Tag>> tileTicks) implements ReadResult {
    }

    /** The chunk-level refusal (the reference's own two gates). */
    public record Rejected(String reason) implements ReadResult {
    }

    private final BlockRegistry registry;
    private final BlockType air;

    /**
     * @param registry the engine's frozen block registry (the identifier
     *                 resolution surface — the import's numeric states
     *                 resolve through {@link AnvilBlockIds} then this)
     */
    public AnvilChunkReader(BlockRegistry registry) {
        this.registry = java.util.Objects.requireNonNull(registry, "registry");
        this.air = registry.require(Identifier.parse("minecraft:air"));
    }

    /**
     * Transforms one chunk's NBT into one detached engine chunk.
     *
     * @param position the chunk's position (the region entry — the
     *                 authoritative address; the NBT's own xPos/zPos rows
     *                 are counted anomalies when they disagree)
     */
    public ReadResult read(ChunkPosition position, Map<String, Snbt.Tag> chunkNbt,
                           AnvilImportLedger ledger) {
        // The reference's first gate: "Chunk file at x,z is missing level
        // data, skipping" — the Level compound must exist.
        if (!NbtOps.contains(chunkNbt, "Level", Snbt.Tag.Type.COMPOUND)) {
            ledger.isolated("missing level data", "chunk " + position.x() + "," + position.z());
            return new Rejected("missing level data");
        }
        Map<String, Snbt.Tag> level = NbtOps.getCompound(chunkNbt, "Level");
        // The reference's second gate: "missing block data, skipping" — the
        // old MCRegion shape carried Blocks/Data at the Level root; those
        // parts are REFUSED, never guessed.
        if (!NbtOps.contains(level, "Sections", Snbt.Tag.Type.LIST)) {
            ledger.isolated("missing block data (no Sections — pre-Anvil format parts are rejected)",
                    "chunk " + position.x() + "," + position.z());
            return new Rejected("missing block data (no Sections — pre-Anvil format parts are rejected)");
        }

        // The position rows: the reference relocates a chunk whose rows
        // disagree with the entry (isAt check); the entry stays the
        // authority here (the arrays never depend on the rows) and the
        // disagreement is a counted anomaly.
        int xPos = NbtOps.getInt(level, "xPos");
        int zPos = NbtOps.getInt(level, "zPos");
        if (xPos != position.x() || zPos != position.z()) {
            ledger.isolated("position rows disagree with the region entry",
                    "chunk " + position.x() + "," + position.z() + " claims " + xPos + "," + zPos);
        }

        EngineChunk chunk = new EngineChunk(position, air);
        List<PendingUpper> pendingUppers = new ArrayList<>();

        // The carried content rows (the reference's own order: the blocks
        // walk first, the tiles, the tile ticks, and the entities apply
        // after the install — AnvilWorldImport's seams).
        List<Map<String, Snbt.Tag>> entities = new ArrayList<>();
        if (NbtOps.contains(level, "Entities", Snbt.Tag.Type.LIST)) {
            List<Snbt.Tag> rows = NbtOps.getList(level, "Entities", Snbt.Tag.Type.COMPOUND);
            for (int i = 0; i < rows.size(); i++) {
                entities.add(NbtOps.listCompound(rows, i));
            }
        }
        List<Map<String, Snbt.Tag>> tileEntities = new ArrayList<>();
        if (NbtOps.contains(level, "TileEntities", Snbt.Tag.Type.LIST)) {
            List<Snbt.Tag> rows = NbtOps.getList(level, "TileEntities", Snbt.Tag.Type.COMPOUND);
            for (int i = 0; i < rows.size(); i++) {
                tileEntities.add(NbtOps.listCompound(rows, i));
            }
        }
        List<Map<String, Snbt.Tag>> tileTicks = new ArrayList<>();
        if (NbtOps.contains(level, "TileTicks", Snbt.Tag.Type.LIST)) {
            List<Snbt.Tag> rows = NbtOps.getList(level, "TileTicks", Snbt.Tag.Type.COMPOUND);
            for (int i = 0; i < rows.size(); i++) {
                tileTicks.add(NbtOps.listCompound(rows, i));
            }
        }

        List<Snbt.Tag> sections = NbtOps.getList(level, "Sections", Snbt.Tag.Type.COMPOUND);
        for (int i = 0; i < sections.size(); i++) {
            Map<String, Snbt.Tag> section = NbtOps.listCompound(sections, i);
            readSection(chunk, section, pendingUppers, position, ledger);
        }

        // The double-plant uppers resolve against the FINAL lower cells —
        // the reference's own resolveVirtualProperties (the pos.down()
        // read): the species is virtual in 1.8.8, stored only on the lower.
        for (PendingUpper pending : pendingUppers) {
            BlockType lower = chunk.getBlock(pending.localX(), pending.y() - 1, pending.localZ());
            Identifier upperId = AnvilBlockIds.doublePlantUpperFor(lower.identifier());
            registry.lookup(upperId).ifPresent(upper ->
                    chunk.setBlock(pending.localX(), pending.y(), pending.localZ(), upper));
        }

        // The biome row: exactly 256 bytes, z * 16 + x (the reference's
        // contains gate keeps an absent row at the plains default).
        if (NbtOps.contains(level, "Biomes", Snbt.Tag.Type.LIST)) {
            byte[] biomes = BinaryNbtReader.byteArray(level.get("Biomes"));
            if (biomes == null) {
                ledger.isolated("Biomes row is not a byte array", "chunk " + position.x() + "," + position.z());
            } else if (biomes.length != 16 * 16) {
                ledger.isolated("Biomes length " + biomes.length + " != 256",
                        "chunk " + position.x() + "," + position.z());
            } else {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        chunk.setBiome(x, z, biomes[(z << 4) | x]);
                    }
                }
            }
        }

        // The inhabited row (VIII14 — the local-difficulty completion):
        // the reference's AnvilChunkStorage round trip writes
        // {@code InhabitedTime} beside the blocks (line 183) and reads it
        // back (line 283); the engine's chunk carries the same row now,
        // the local difficulty's own inhabited term reads it. The absent
        // row keeps the fresh 0 (the reference's getLong default's own
        // shape — a never-watched chunk).
        if (level.containsKey("InhabitedTime")
                && level.get("InhabitedTime").type() == Snbt.Tag.Type.LONG) {
            chunk.setInhabitedTime(NbtOps.getLong(level, "InhabitedTime"));
        }

        return new ReadChunk(chunk, entities, tileEntities, tileTicks);
    }

    /** One section's walk (the reference's per-section loop, isolated). */
    private void readSection(EngineChunk chunk, Map<String, Snbt.Tag> section,
                             List<PendingUpper> pendingUppers, ChunkPosition position,
                             AnvilImportLedger ledger) {
        String subject = "chunk " + position.x() + "," + position.z();
        // The reference reads Y as a byte and indexes the section array
        // with it — anything outside 0..15 would crash it; here the
        // section isolates.
        int sectionY = section.get("Y") != null && section.get("Y").type() == Snbt.Tag.Type.BYTE
                ? section.get("Y").asByte() : -1;
        if (sectionY < 0 || sectionY >= EngineChunk.SECTION_COUNT) {
            ledger.isolated(
                    section.containsKey("Y")
                            ? "section Y " + sectionY + " outside 0..15"
                            : "section missing its Y row",
                    subject);
            return;
        }
        subject = subject + " section " + sectionY;

        // Blocks is required, exactly 4096 (the reference's array walk
        // assumes it; a malformed row would crash or half-load it).
        Snbt.Tag blocksTag = section.get("Blocks");
        byte[] blocks = BinaryNbtReader.byteArray(blocksTag);
        if (blocks == null) {
            ledger.isolated("missing Blocks array", subject);
            return;
        }
        if (blocks.length != 16 * 16 * 16) {
            ledger.isolated("Blocks length " + blocks.length + " != 4096", subject);
            return;
        }

        // The 1.8.8 quirk: a MISSING Data nibble reads as all zeros (the
        // reference's getByteArray answers byte[0] for the absent row —
        // this port answers the zeros instead of crashing on the length
        // check the reference would then fail).
        byte[] data = BinaryNbtReader.byteArray(section.get("Data"));
        if (data == null) {
            data = new byte[16 * 16 * 16 / 2];
        } else if (data.length != 2048) {
            ledger.isolated("Data length " + data.length + " != 2048", subject);
            return;
        }
        byte[] add = BinaryNbtReader.byteArray(section.get("Add"));
        if (add != null && add.length != 2048) {
            ledger.isolated("Add length " + add.length + " != 2048", subject);
            return;
        }

        // The light rows are present-but-ignored (derived state, recomputed
        // at publication — the install hook's own initial compute).
        if (section.get("BlockLight") != null) {
            ledger.ignored("BlockLight", 1);
        }
        if (section.get("SkyLight") != null) {
            ledger.ignored("SkyLight", 1);
        }

        for (int i = 0; i < blocks.length; i++) {
            int localX = i & 15;
            int localY = (i >> 8) & 15;
            int localZ = (i >> 4) & 15;
            // ChunkNibbleStorage.get: id = y<<8 | z<<4 | x; low nibble for
            // the even id, high for the odd.
            int dataNibble = (i & 1) == 0
                    ? data[i >> 1] & 0x0F
                    : (data[i >> 1] >> 4) & 0x0F;
            int addNibble = 0;
            if (add != null) {
                addNibble = (i & 1) == 0
                        ? add[i >> 1] & 0x0F
                        : (add[i >> 1] >> 4) & 0x0F;
            }
            int numericId = (addNibble << 8) | (blocks[i] & 0xFF);

            if (AnvilBlockIds.isDoublePlantUpper(numericId, dataNibble)) {
                // The virtual species: the static table's canonical lands
                // now, the lower-cell refinement runs after the whole
                // chunk is read (the reference's own two-pass resolve).
                chunk.setBlock(localX, (sectionY << 4) | localY, localZ,
                        registry.require(Identifier.parse("minecraft:double_grass_upper")));
                pendingUppers.add(new PendingUpper(localX, (sectionY << 4) | localY, localZ));
                continue;
            }

            java.util.Optional<Identifier> identifier = AnvilBlockIds.resolve(numericId, dataNibble);
            BlockType type;
            if (identifier.isEmpty()) {
                // Unknown to the engine (or beyond vanilla's id space):
                // air with a counted state — never a crash, never a
                // silent substitute (§31.6).
                ledger.unknownState(numericId, dataNibble);
                type = air;
            } else {
                type = registry.lookup(identifier.get()).orElse(air);
                if (!type.identifier().equals(identifier.get())) {
                    // The registry moved under the table: count it loudly.
                    ledger.isolated("registry missing the table's block " + identifier.get(),
                            subject);
                }
            }
            chunk.setBlock(localX, (sectionY << 4) | localY, localZ, type);
        }
    }

    /** One virtual upper cell awaiting its lower's species. */
    private record PendingUpper(int localX, int y, int localZ) {
    }
}
