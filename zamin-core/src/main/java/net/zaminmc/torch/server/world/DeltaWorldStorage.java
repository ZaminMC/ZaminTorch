package net.zaminmc.torch.server.world;

import net.zaminmc.torch.World;

import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.util.Identifier;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * File-backed world delta store ("ZWD" format, version 1).
 *
 * <p>Layout: magic 'Z','W','D',1 - totalTicks(long) - timeOfDay(long) -
 * palette(varint count + identifier strings) - deltas(varint count; each:
 * chunkKey(long) + entryCount(varint) + (localIndex(int) + paletteIndex(varint)))*.
 *
 * <p>Writes are atomic: data goes to a temp file which then replaces the target,
 * so a crash mid-save never destroys the previous snapshot (§646 basics).
 * Corrupt files load as empty (predictable failure, world regenerates from its
 * generator) and never silently destroy data — the corrupt file is preserved
 * beside the fresh one (§286).</p>
 */
public final class DeltaWorldStorage implements WorldStorage {

    private static final Logger LOGGER = Logger.getLogger(DeltaWorldStorage.class.getName());
    private static final int MAGIC_0 = 'Z';
    private static final int MAGIC_1 = 'W';
    private static final int MAGIC_2 = 'D';
    /** The written version (the serializer's own v2 — VIII14; the load walk accepts v1 and v2). */
    private static final int FORMAT_VERSION = 2;

    private final Path file;
    private final java.util.function.Function<Identifier, BlockType> typeResolver;

    public DeltaWorldStorage(Path file, java.util.function.Function<Identifier, BlockType> typeResolver) {
        this.file = java.util.Objects.requireNonNull(file, "file");
        this.typeResolver = java.util.Objects.requireNonNull(typeResolver, "typeResolver");
    }

    @Override
    public void save(WorldDeltaSnapshot snapshot) {
        // The S1 split: the serialization is the PURE function
        // (WorldDeltaSerializer — the Phase-5 adoption's immutable-input
        // contract), the publication is this walk's own file half. The
        // on-tick path keeps producing the same bytes (the same code,
        // moved) — the equivalence test proves it 1:1.
        writeSerialized(WorldDeltaSerializer.serialize(snapshot));
    }

    /**
     * Publishes already-serialized ZWD bytes: the temp-file write + the
     * atomic move, the historical save walk's own file half. Safe from
     * any thread that holds the publication right (the save pipeline's
     * per-storage lock orders the async publications; the synchronous
     * save is uncontended).
     */
    public void writeSerialized(byte[] data) {
        try {
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.createDirectories(file.getParent());
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                    Files.newOutputStream(temp)))) {
                out.write(data);
            }
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            // Persistence failure is loud: data loss must never look like success (§54).
            throw new IllegalStateException("World save failed for " + file, e);
        }
    }

    @Override
    public Optional<WorldDeltaSnapshot> load() {
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                Files.newInputStream(file)))) {
            if (in.readByte() != MAGIC_0 || in.readByte() != MAGIC_1 || in.readByte() != MAGIC_2) {
                throw new IOException("Not a ZWD file");
            }
            int version = in.readByte();
            if (version != 1 && version != 2) {
                throw new IOException("Unsupported ZWD version: " + version);
            }
            long totalTicks = in.readLong();
            long timeOfDay = in.readLong();
            // v2 carries the raw day clock (VIII14); a v1 file migrates it
            // as the wrapped value — day 0, the historical fresh-moon read
            // the pre-VIII14 engine always carried.
            long rawTimeOfDay = version >= 2 ? in.readLong() : timeOfDay;

            int paletteSize = in.readInt();
            Identifier[] palette = new Identifier[paletteSize];
            for (int i = 0; i < paletteSize; i++) {
                palette[i] = Identifier.parse(readString(in));
            }

            Map<Long, Map<Integer, BlockType>> deltas = new HashMap<>();
            int chunkCount = in.readInt();
            for (int c = 0; c < chunkCount; c++) {
                long chunkKey = in.readLong();
                int entryCount = in.readInt();
                Map<Integer, BlockType> entries = new HashMap<>();
                for (int e = 0; e < entryCount; e++) {
                    int localIndex = in.readInt();
                    int paletteIndex = readVarInt(in);
                    BlockType type = typeResolver.apply(palette[paletteIndex]);
                    entries.put(localIndex, type);
                }
                deltas.put(chunkKey, entries);
            }
            // The inhabited tail (v2 — VIII14): chunkKey long + value long
            // each, the count an int like the delta section's own head.
            Map<Long, Long> inhabitedTimes = new HashMap<>();
            if (version >= 2) {
                int inhabitedCount = in.readInt();
                for (int i = 0; i < inhabitedCount; i++) {
                    inhabitedTimes.put(in.readLong(), in.readLong());
                }
            }
            return Optional.of(new WorldDeltaSnapshot(
                    totalTicks, timeOfDay, rawTimeOfDay, inhabitedTimes, deltas));
        } catch (IOException corrupt) {
            // Preserve the unreadable data beside the storage path; the world
            // regenerates from its generator. Losing deltas beats corrupting more.
            Path quarantine = file.resolveSibling(file.getFileName() + ".corrupt");
            try {
                Files.move(file, quarantine, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.warning("World delta file was unreadable; preserved as " + quarantine
                        + " (" + corrupt.getMessage() + ")");
            } catch (IOException moveFailure) {
                LOGGER.warning("World delta file was unreadable and could not be preserved: "
                        + moveFailure);
            }
            return Optional.empty();
        }
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = readVarInt(in);
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while (true) {
            if ((value & ~0x7F) == 0) {
                out.write(value);
                return;
            }
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int value = 0;
        int shift = 0;
        while (true) {
            int b = in.readUnsignedByte();
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return value;
            }
            shift += 7;
            if (shift > 28) {
                throw new IOException("VarInt too large in world delta file");
            }
        }
    }
}
