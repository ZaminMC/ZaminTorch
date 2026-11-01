package net.zaminmc.torch.server.jukebox;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.util.Identifier;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * File-backed jukebox state ("ZJD" format, version 2), one file per world —
 * the note store's own durability rules.
 *
 * <p>Layout: magic 'Z','J','D',2 - records(varint count; each: x(i32) y(i32)
 * z(i32) then the disc in the ZCD item form (present(u8) then item
 * identifier(string) + count(varint) + damage(varint) + displayName,
 * 0 = none) — the reference's writeNbt "RecordItem" compound) then
 * dimension(i8, Wave FL4-GAP2 Slice C: the row's own world, the chest
 * store's own v+1 law; version 1 files load as overworld rows).</p>
 *
 * <p>Writes are atomic (temp file then atomic move), corrupt files load as
 * absent and are preserved beside the storage path instead of being
 * destroyed, and save failures are loud. Save points are the engine's
 * {@code saveAllNow} (shutdown, console save).</p>
 */
public final class JukeboxDataStore {

    private static final Logger LOGGER = Logger.getLogger(JukeboxDataStore.class.getName());
    private static final int MAGIC_0 = 'Z';
    private static final int MAGIC_1 = 'J';
    private static final int MAGIC_2 = 'D';
    private static final int FORMAT_VERSION = 2;

    private final Path file;

    public JukeboxDataStore(Path file) {
        this.file = Objects.requireNonNull(file, "file");
    }

    /**
     * Saves every dimension's record map (the FL4-GAP2 Slice C union: one
     * file, each row stamped with its own world — a nether jukebox and an
     * overworld jukebox at the same coordinates are TWO tiles). Callers
     * snapshot state on the tick thread before calling (the engine routes
     * this through the ticker). The synchronous form composes the S4
     * pipeline's own two halves — the pure serialization over the
     * already-immutable map, the file publication — so the sync save's
     * bytes and the pipeline's bytes are identical by construction (one
     * serializer, one file half).
     *
     * @param records each dimension's live map (dimension id -> the map its
     *                own JukeboxBlockSystem owns)
     */
    public void save(Map<Integer, Map<BlockPosition, ItemStack>> records) {
        Objects.requireNonNull(records, "records");
        writeSerialized(serialize(records));
    }

    /**
     * The pure serialization (Slice S4 — the compute pool's own half): the
     * immutable union IN (each system's own snapshotRecords() copy — the
     * stacks are the engine's immutable ItemStack, the capture contract
     * already satisfied), the ZJD bytes OUT — no world read, no I/O.
     */
    public byte[] serialize(Map<Integer, Map<BlockPosition, ItemStack>> records) {
        java.io.ByteArrayOutputStream sink = new java.io.ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(
                new java.io.BufferedOutputStream(sink))) {
            out.writeByte(MAGIC_0);
            out.writeByte(MAGIC_1);
            out.writeByte(MAGIC_2);
            out.writeByte(FORMAT_VERSION);
            int total = 0;
            for (Map<BlockPosition, ItemStack> byDimension : records.values()) {
                total += byDimension.size();
            }
            writeVarInt(out, total);
            for (Map.Entry<Integer, Map<BlockPosition, ItemStack>> byDimension
                    : records.entrySet()) {
                int dimension = byDimension.getKey();
                for (Map.Entry<BlockPosition, ItemStack> entry
                        : byDimension.getValue().entrySet()) {
                    BlockPosition position = entry.getKey();
                    out.writeInt(position.x());
                    out.writeInt(position.y());
                    out.writeInt(position.z());
                    writeStack(out, entry.getValue());
                    // The signed byte carries the dimension — -1 lands as u8 255.
                    out.writeByte(dimension);
                }
            }
        } catch (IOException e) {
            // The sink is an in-memory buffer; an IOException here is a
            // defect, not a persistence failure — still loud.
            throw new java.io.UncheckedIOException("Jukebox serialize failed", e);
        }
        return sink.toByteArray();
    }

    /**
     * Publishes already-serialized ZJD bytes: the temp-file write + the
     * atomic move (the non-atomic fallback preserved), the historical save
     * walk's own file half with the store's own failure contract (the
     * severe log, never a throw). Safe from any thread that holds the
     * publication right (the save pipeline's per-storage lock orders the
     * async publications; the synchronous save is uncontended).
     */
    public void writeSerialized(byte[] data) {
        try {
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.createDirectories(file.getParent());
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                    Files.newOutputStream(temp)))) {
                out.write(data);
            }
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOGGER.severe("Jukebox state save failed for " + file + ": " + e);
        }
    }

    /** One loaded record row, stamped with the world it lives in. */
    public record Loaded(BlockPosition position, int dimension, ItemStack record) {
    }

    /**
     * Loads the record rows, each stamped with its dimension. A version 1
     * file's rows carry no dimension byte — they load as overworld rows
     * (the migration law every FL4-GAP2 store carries). An absent or
     * corrupt file loads empty (corrupt files are preserved, not
     * destroyed). A stored disc the registry cannot resolve drops with a
     * warning — everything else survives.
     */
    public java.util.List<Loaded> load() {
        if (!Files.exists(file)) {
            return new java.util.ArrayList<>();
        }
        java.util.List<Loaded> loaded = new java.util.ArrayList<>();
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                Files.newInputStream(file)))) {
            if (in.readByte() != MAGIC_0 || in.readByte() != MAGIC_1
                    || in.readByte() != MAGIC_2) {
                LOGGER.warning("Jukebox state " + file + " is not ZJD; loading empty");
                return new java.util.ArrayList<>();
            }
            int version = in.readByte() & 0xFF;
            if (version < 1 || version > FORMAT_VERSION) {
                LOGGER.warning("Jukebox state " + file + " version " + version
                        + " unsupported; loading empty");
                return new java.util.ArrayList<>();
            }
            int count = readVarInt(in);
            if (count < 0 || count > 100_000) {
                LOGGER.warning("Jukebox state " + file + " implausible count; loading empty");
                return new java.util.ArrayList<>();
            }
            for (int i = 0; i < count; i++) {
                BlockPosition position = new BlockPosition(in.readInt(), in.readInt(),
                        in.readInt());
                ItemStack record = readStack(in);
                int dimension = version >= FORMAT_VERSION ? in.readByte() : 0;
                loaded.add(new Loaded(position, dimension, record));
            }
            return loaded;
        } catch (IOException | RuntimeException corrupt) {
            LOGGER.warning("Jukebox state " + file + " corrupt; loading empty (" + corrupt + ")");
            return new java.util.ArrayList<>();
        }
    }

    /** The ZCD item form (present byte + identifier + count + damage + name). */
    private static void writeStack(DataOutputStream out, ItemStack stack) throws IOException {
        if (stack == null || stack.isEmpty()) {
            out.writeByte(0);
            return;
        }
        out.writeByte(1);
        writeString(out, stack.type().identifier().toString());
        writeVarInt(out, stack.count());
        writeVarInt(out, stack.damage());
        String name = stack.displayName();
        if (name == null) {
            writeVarInt(out, 0);
        } else {
            byte[] bytes = name.getBytes(StandardCharsets.UTF_8);
            writeVarInt(out, bytes.length);
            out.write(bytes);
        }
    }

    /** The ZCD item form's read (unknown items drop with a warning). */
    private static ItemStack readStack(DataInputStream in) throws IOException {
        if (in.readUnsignedByte() != 1) {
            return ItemStack.EMPTY;
        }
        Identifier identifier = Identifier.parse(readString(in));
        int count = readVarInt(in);
        int damage = readVarInt(in);
        int nameLength = readVarInt(in);
        String name = null;
        if (nameLength > 0) {
            if (nameLength > 256) {
                throw new IOException("Implausible name length: " + nameLength);
            }
            byte[] bytes = new byte[nameLength];
            in.readFully(bytes);
            name = new String(bytes, StandardCharsets.UTF_8);
        }
        Optional<net.zaminmc.torch.item.ItemType> type = BuiltinItems.lookup(identifier);
        if (type.isEmpty()) {
            LOGGER.warning("Saved jukebox disc no longer registered, dropped: " + identifier);
            return ItemStack.EMPTY;
        }
        return ItemStack.of(type.get(), count).withDamage(damage).withName(name);
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = readVarInt(in);
        if (length < 0 || length > 256) {
            throw new IOException("Implausible string length: " + length);
        }
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
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
                throw new IOException("VarInt too long");
            }
        }
    }
}
