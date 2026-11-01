package net.zaminmc.torch.server.dispenser;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.item.ItemType;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * File-backed dispenser/dropper state ("ZDD" format, version 2), one file
 * per world (Slice 11f — the ledgered slot persistence of the family the
 * 11a slice deferred).
 *
 * <p>Layout: magic 'Z','D','D',2 - containers(varint count; each: x(i32)
 * y(i32) z(i32) - slots(9 entries; each: present(u8) then item
 * identifier(string) + count(varint) + damage(varint) + displayName
 * (varint-length UTF-8, 0 = none)) - dimension(i8, Wave FL4-GAP2 Slice B:
 * the row's own world, the chest store's own stamp law — -1 lands as u8
 * 255)). Version 1 files load every row as an overworld row (the
 * migration law — the pre-Slice-B store was the overworld's single map).</p>
 *
 * <p>The same durability rules as the world, player, furnace and chest
 * stores: writes are atomic (temp file then atomic move), corrupt files
 * load as absent and are preserved beside the storage path instead of
 * being destroyed, and save failures are loud (§54/§286). Save points are
 * the engine's {@code saveAllNow} (shutdown, console save).</p>
 */
public final class DispenserDataStore {

    private static final Logger LOGGER = Logger.getLogger(DispenserDataStore.class.getName());
    private static final int MAGIC_0 = 'Z';
    private static final int MAGIC_1 = 'D';
    private static final int MAGIC_2 = 'D';
    private static final int FORMAT_VERSION = 2;

    private final Path file;

    public DispenserDataStore(Path file) {
        this.file = java.util.Objects.requireNonNull(file, "file");
    }

    /**
     * Saves every dimension's container map (the FL4-GAP2 Slice B union:
     * one file, each row stamped with its own world — the chest store's
     * own law). Callers snapshot state on the tick thread before calling
     * (the engine routes this through the ticker).
     *
     * @param containers each dimension's live map (dimension id -> the map
     *                   its DispenserSystem owns)
     */
    public void save(Map<Integer, Map<BlockPosition, DispenserBlockEntity>> containers) {
        writeSerialized(serialize(capture(containers)));
    }

    /** One dispenser slot's persisted rows ({@code null} = the empty slot). */
    public record Slot(String id, int count, int damage, String displayName) {

        /** The immutable row of a live stack ({@code null} when empty). */
        public static Slot of(net.zaminmc.torch.item.ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                return null;
            }
            return new Slot(stack.type().identifier().toString(), stack.count(),
                    stack.damage(), stack.displayName());
        }
    }

    /**
     * The immutable persistence projection (Slice S4 — the Phase-5
     * serialization lane's own input contract): every dispenser row
     * captured on the owning lane, safe to cross into the compute pool —
     * the serializer never reads the live entities (§31.6's
     * snapshot-before-I/O; the system's own map copy holds LIVE entities,
     * the capture is the deep form).
     */
    public record Snapshot(java.util.List<Row> rows) {

        /** One dispenser's persisted rows, stamped with its own world. */
        public record Row(int x, int y, int z, int dimension, java.util.List<Slot> slots) {
        }
    }

    /**
     * The deep capture of every dimension's live maps — called ON the
     * owning tick lane (each system's snapshot() is the map copy; this
     * walks them into the immutable rows). The caller's maps are not
     * retained.
     */
    public Snapshot capture(Map<Integer, Map<BlockPosition, DispenserBlockEntity>> containers) {
        int total = 0;
        for (Map<BlockPosition, DispenserBlockEntity> map : containers.values()) {
            total += map.size();
        }
        java.util.List<Snapshot.Row> rows = new java.util.ArrayList<>(total);
        for (Map.Entry<Integer, Map<BlockPosition, DispenserBlockEntity>> byDimension
                : containers.entrySet()) {
            int dimension = byDimension.getKey();
            for (Map.Entry<BlockPosition, DispenserBlockEntity> entry
                    : byDimension.getValue().entrySet()) {
                BlockPosition position = entry.getKey();
                DispenserBlockEntity container = entry.getValue();
                net.zaminmc.torch.item.ItemStack[] liveSlots = container.snapshotSlots();
                java.util.List<Slot> slots = new java.util.ArrayList<>(liveSlots.length);
                for (net.zaminmc.torch.item.ItemStack stack : liveSlots) {
                    slots.add(Slot.of(stack));
                }
                rows.add(new Snapshot.Row(position.x(), position.y(), position.z(), dimension,
                        java.util.Collections.unmodifiableList(slots)));
            }
        }
        return new Snapshot(java.util.List.copyOf(rows));
    }

    /**
     * The pure serialization (the compute pool's own half): the immutable
     * snapshot IN, the bytes OUT — no world read, no I/O. The byte walk
     * is the historical save walk's own (byte-identical).
     */
    public byte[] serialize(Snapshot snapshot) {
        java.io.ByteArrayOutputStream sink = new java.io.ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(
                new java.io.BufferedOutputStream(sink))) {
            out.writeByte(MAGIC_0);
            out.writeByte(MAGIC_1);
            out.writeByte(MAGIC_2);
            out.writeByte(FORMAT_VERSION);
            writeVarInt(out, snapshot.rows().size());
            for (Snapshot.Row row : snapshot.rows()) {
                out.writeInt(row.x());
                out.writeInt(row.y());
                out.writeInt(row.z());
                for (Slot slot : row.slots()) {
                    if (slot == null) {
                        out.writeByte(0);
                    } else {
                        out.writeByte(1);
                        writeString(out, slot.id());
                        writeVarInt(out, slot.count());
                        writeVarInt(out, slot.damage());
                        writeOptionalString(out, slot.displayName());
                    }
                }
                // The FL4-GAP2 Slice B stamp (the chest store's own form):
                // the signed byte carries the dimension — -1 lands as u8 255.
                out.writeByte(row.dimension());
            }
        } catch (IOException e) {
            // The sink is an in-memory buffer; an IOException here is a
            // defect, not a persistence failure — still loud.
            throw new java.io.UncheckedIOException("Dispenser serialize failed", e);
        }
        return sink.toByteArray();
    }

    /**
     * Publishes already-serialized bytes: the temp-file write + the atomic
     * move, the historical save walk's own file half. Safe from any thread
     * that holds the publication right (the save pipeline's per-storage
     * lock orders the async publications; the synchronous save is
     * uncontended).
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
            throw new IllegalStateException("Dispenser save failed for " + file, e);
        }
    }

    /** One loaded container's row, stamped with its own world. */
    public record Loaded(BlockPosition position, int dimension, DispenserBlockEntity container) {
    }

    /**
     * Loads the container rows, each stamped with its dimension. A v1
     * file's rows are overworld rows (the pre-Slice-B store was the
     * overworld's single map — the migration law). Saved items the current
     * registry cannot resolve are dropped with a warning — the registry
     * moved on, everything else survives.
     */
    public List<Loaded> load() {
        if (!Files.exists(file)) {
            return List.of();
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                Files.newInputStream(file)))) {
            if (in.readByte() != MAGIC_0 || in.readByte() != MAGIC_1 || in.readByte() != MAGIC_2) {
                throw new IOException("Not a ZDD file");
            }
            int version = in.readByte();
            if (version < 1 || version > FORMAT_VERSION) {
                throw new IOException("Unsupported ZDD version: " + version);
            }
            int count = readVarInt(in);
            List<Loaded> loaded = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                int x = in.readInt();
                int y = in.readInt();
                int z = in.readInt();
                DispenserBlockEntity container = new DispenserBlockEntity();
                ItemStack[] slots = new ItemStack[DispenserBlockEntity.SLOT_COUNT];
                java.util.Arrays.fill(slots, ItemStack.EMPTY);
                for (int slot = 0; slot < DispenserBlockEntity.SLOT_COUNT; slot++) {
                    if (in.readUnsignedByte() == 1) {
                        Identifier identifier = Identifier.parse(readString(in));
                        int stackCount = readVarInt(in);
                        int damage = readVarInt(in);
                        String displayName = readOptionalString(in);
                        Optional<ItemType> type = BuiltinItems.lookup(identifier);
                        if (type.isPresent()) {
                            slots[slot] = ItemStack.of(type.get(), stackCount)
                                    .withDamage(damage).withName(displayName);
                        } else {
                            LOGGER.warning("Saved dispenser item no longer registered, dropped: "
                                    + identifier);
                        }
                    }
                }
                container.restoreSlots(slots);
                int dimension = version >= FORMAT_VERSION ? in.readByte() : 0;
                loaded.add(new Loaded(new BlockPosition(x, y, z), dimension, container));
            }
            return loaded;
        } catch (IOException | RuntimeException corrupt) {
            // Malformed values are corruption just as much as a broken
            // header: quarantine, then treat as absent.
            Path quarantine = file.resolveSibling(file.getFileName() + ".corrupt");
            try {
                Files.move(file, quarantine, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.warning("Dispenser data file was unreadable; preserved as " + quarantine
                        + " (" + corrupt.getMessage() + ")");
            } catch (IOException moveFailure) {
                LOGGER.warning("Dispenser data file was unreadable and could not be preserved: "
                        + moveFailure);
            }
            return List.of();
        }
    }

    /** Varint-length UTF-8; a 0 length is the "absent" marker (never null). */
    private static void writeOptionalString(DataOutputStream out, String value) throws IOException {
        if (value == null) {
            writeVarInt(out, 0);
            return;
        }
        writeString(out, value);
    }

    private static String readOptionalString(DataInputStream in) throws IOException {
        int length = readVarInt(in);
        if (length == 0) {
            return null;
        }
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
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
                throw new IOException("VarInt too large in dispenser data file");
            }
        }
    }
}
