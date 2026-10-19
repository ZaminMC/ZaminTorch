package net.zaminmc.torch.server.furnace;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.item.ItemType;
import net.zaminmc.torch.server.item.BuiltinItems;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
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
 * File-backed furnace state ("ZFD" format, version 2), one file per world.
 *
 * <p>Layout: magic 'Z','F','D',1 - furnaces(varint count; each: x(i32) y(i32)
 * z(i32) - burnRemaining(varint) burnTotal(varint) cookTime(varint) - slots(3
 * entries; each: present(u8) then item identifier(string) + count(varint) +
 * damage(varint) + displayName(varint-length UTF-8, 0 = none)). Version 1
 * files load unchanged (no custom names).</p>
 *
 * <p>The same durability rules as the world and player stores: writes are
 * atomic (temp file then atomic move), corrupt files load as absent and are
 * preserved beside the storage path instead of being destroyed, and save
 * failures are loud (§54/§286). Save points are the engine's
 * {@code saveAllNow} (shutdown, console save) — a crash can lose smelting
 * progress since the last save, the same trade the world delta store makes.</p>
 */
public final class FurnaceDataStore {

    private static final Logger LOGGER = Logger.getLogger(FurnaceDataStore.class.getName());
    private static final int MAGIC_0 = 'Z';
    private static final int MAGIC_1 = 'F';
    private static final int MAGIC_2 = 'D';
    private static final int FORMAT_VERSION = 2;

    private final Path file;

    public FurnaceDataStore(Path file) {
        this.file = java.util.Objects.requireNonNull(file, "file");
    }

    /**
     * Saves the full furnace map. Callers snapshot state on the tick thread
     * before calling (the engine routes this through the ticker). The
     * synchronous form composes the S2 pipeline's own three halves — the
     * deep capture, the pure serialization, the file publication — so the
     * sync save's bytes and the pipeline's bytes are identical by
     * construction (one serializer, one file half).
     */
    public void save(Map<BlockPosition, FurnaceBlockEntity> furnaces) {
        writeSerialized(serialize(capture(furnaces)));
    }

    /** One furnace slot's persisted rows ({@code null} = the empty slot). */
    public record Slot(String id, int count, int damage, String displayName) {

        /** The immutable row of a live stack ({@code null} when empty). */
        public static Slot of(ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                return null;
            }
            return new Slot(stack.type().identifier().toString(), stack.count(),
                    stack.damage(), stack.displayName());
        }
    }

    /**
     * The immutable persistence projection (Slice S2 — the Phase-5
     * serialization lane's own input contract): every furnace row captured
     * on the owning lane, safe to cross into the compute pool — the
     * serializer never reads the live entities (§31.6's snapshot-before-I/O;
     * the manager's own map copy holds LIVE entities, the capture is the
     * deep form).
     */
    public record Snapshot(List<Row> rows) {

        /** One furnace's persisted rows. */
        public record Row(int x, int y, int z, int burnRemaining, int burnTotal,
                          int cookTime, List<Slot> slots) {
        }
    }

    /**
     * The deep capture of the live furnace map — called ON the owning tick
     * lane (the manager's snapshot() is the map copy; this walks it into the
     * immutable rows). The caller's map is not retained.
     */
    public Snapshot capture(Map<BlockPosition, FurnaceBlockEntity> furnaces) {
        List<Snapshot.Row> rows = new ArrayList<>(furnaces.size());
        for (Map.Entry<BlockPosition, FurnaceBlockEntity> entry : furnaces.entrySet()) {
            BlockPosition position = entry.getKey();
            FurnaceBlockEntity furnace = entry.getValue();
            ItemStack[] liveSlots = furnace.snapshotSlots();
            List<Slot> slots = new ArrayList<>(liveSlots.length);
            for (ItemStack stack : liveSlots) {
                slots.add(Slot.of(stack));
            }
            rows.add(new Snapshot.Row(position.x(), position.y(), position.z(),
                    furnace.burnTimeRemaining(), furnace.burnTimeTotal(),
                    furnace.cookTime(), java.util.Collections.unmodifiableList(slots)));
        }
        return new Snapshot(List.copyOf(rows));
    }

    /**
     * The pure serialization (the compute pool's own half): the immutable
     * snapshot IN, the ZFD bytes OUT — no world read, no I/O. The byte walk
     * is the historical save walk's own (version 2, byte-identical).
     */
    public byte[] serialize(Snapshot snapshot) {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(sink))) {
            out.writeByte(MAGIC_0);
            out.writeByte(MAGIC_1);
            out.writeByte(MAGIC_2);
            out.writeByte(FORMAT_VERSION);
            writeVarInt(out, snapshot.rows().size());
            for (Snapshot.Row row : snapshot.rows()) {
                out.writeInt(row.x());
                out.writeInt(row.y());
                out.writeInt(row.z());
                writeVarInt(out, row.burnRemaining());
                writeVarInt(out, row.burnTotal());
                writeVarInt(out, row.cookTime());
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
            }
        } catch (IOException e) {
            // The sink is an in-memory buffer; an IOException here is a
            // defect, not a persistence failure — still loud.
            throw new UncheckedIOException("Furnace serialize failed", e);
        }
        return sink.toByteArray();
    }

    /**
     * Publishes already-serialized ZFD bytes: the temp-file write + the
     * atomic move, the historical save walk's own file half. Safe from any
     * thread that holds the publication right (the save pipeline's
     * per-storage lock orders the async publications; the synchronous save
     * is uncontended).
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
            throw new IllegalStateException("Furnace save failed for " + file, e);
        }
    }

    /**
     * Loads the furnace map. Saved items the current registry cannot resolve
     * are dropped with a warning — the registry moved on, everything else
     * survives.
     */
    public Map<BlockPosition, FurnaceBlockEntity> load() {
        if (!Files.exists(file)) {
            return Map.of();
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                Files.newInputStream(file)))) {
            if (in.readByte() != MAGIC_0 || in.readByte() != MAGIC_1 || in.readByte() != MAGIC_2) {
                throw new IOException("Not a ZFD file");
            }
            int version = in.readByte();
            if (version < 1 || version > FORMAT_VERSION) {
                throw new IOException("Unsupported ZFD version: " + version);
            }
            int count = readVarInt(in);
            Map<BlockPosition, FurnaceBlockEntity> loaded = new LinkedHashMap<>();
            for (int i = 0; i < count; i++) {
                int x = in.readInt();
                int y = in.readInt();
                int z = in.readInt();
                int burnRemaining = readVarInt(in);
                int burnTotal = readVarInt(in);
                int cook = readVarInt(in);
                FurnaceBlockEntity furnace = new FurnaceBlockEntity();
                for (int slot = 0; slot < FurnaceBlockEntity.SLOT_COUNT; slot++) {
                    if (in.readUnsignedByte() == 1) {
                        Identifier identifier = Identifier.parse(readString(in));
                        int stackCount = readVarInt(in);
                        int damage = readVarInt(in);
                        String displayName = version >= 2 ? readOptionalString(in) : null;
                        Optional<ItemType> type = BuiltinItems.lookup(identifier);
                        if (type.isPresent()) {
                            furnace.setSlot(slot,
                                    ItemStack.of(type.get(), stackCount)
                                            .withDamage(damage).withName(displayName));
                        } else {
                            LOGGER.warning("Saved furnace item no longer registered, dropped: "
                                    + identifier);
                        }
                    }
                }
                furnace.restore(burnRemaining, burnTotal, cook);
                loaded.put(new BlockPosition(x, y, z), furnace);
            }
            return loaded;
        } catch (IOException | RuntimeException corrupt) {
            // Malformed values are corruption just as much as a broken header:
            // quarantine, then treat as absent.
            Path quarantine = file.resolveSibling(file.getFileName() + ".corrupt");
            try {
                Files.move(file, quarantine, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.warning("Furnace data file was unreadable; preserved as " + quarantine
                        + " (" + corrupt.getMessage() + ")");
            } catch (IOException moveFailure) {
                LOGGER.warning("Furnace data file was unreadable and could not be preserved: "
                        + moveFailure);
            }
            return Map.of();
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
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
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
                throw new IOException("VarInt too large in furnace data file");
            }
        }
    }
}
