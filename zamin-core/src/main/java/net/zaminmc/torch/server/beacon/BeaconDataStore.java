package net.zaminmc.torch.server.beacon;

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
import java.util.Optional;
import java.util.logging.Logger;

/**
 * File-backed beacon state ("ZBT" format, version 2), one file per world —
 * the chest store's own durability rules.
 *
 * <p>Layout: magic 'Z','B','T',2 - beacons(varint count; each: x(i32) y(i32)
 * z(i32) - levels(varint) - primary(varint) - secondary(varint) - payment
 * (present(u8) then item identifier(string) + count(varint) + damage(varint)
 * + displayName(varint-length UTF-8, 0 = none)) then dimension(i8, Wave
 * FL4-GAP2 Slice C: the row's own world, the chest store's own v+1 law;
 * version 1 files load as overworld rows).</p>
 *
 * <p>Writes are atomic (temp file then atomic move), corrupt files load as
 * absent and are preserved beside the storage path, and save failures are
 * loud. Save points are the engine's {@code saveAllNow}.</p>
 */
public final class BeaconDataStore {

    private static final Logger LOGGER = Logger.getLogger(BeaconDataStore.class.getName());
    private static final int MAGIC_0 = 'Z';
    private static final int MAGIC_1 = 'B';
    private static final int MAGIC_2 = 'T';
    private static final int FORMAT_VERSION = 2;

    private final Path file;

    public BeaconDataStore(Path file) {
        this.file = java.util.Objects.requireNonNull(file, "file");
    }

    /**
     * Saves every dimension's beacon map (the FL4-GAP2 Slice C union: one
     * file, each row stamped with its own world — a nether beacon and an
     * overworld beacon at the same coordinates are TWO tiles). The engine
     * routes this through the ticker.
     *
     * @param beacons each dimension's live map (dimension id -> the map its
     *                own BeaconSystem owns)
     */
    public void save(Map<Integer, Map<BlockPosition, BeaconBlockEntity>> beacons) {
        writeSerialized(serialize(capture(beacons)));
    }

    /** One beacon slot's persisted rows ({@code null} = the empty slot). */
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
     * serialization lane's own input contract): every beacon row captured
     * on the owning lane, safe to cross into the compute pool — the
     * serializer never reads the live entities (§31.6's
     * snapshot-before-I/O; the system's own map copy holds LIVE entities,
     * the capture is the deep form).
     */
    public record Snapshot(java.util.List<Row> rows) {

        /** One beacon's persisted rows (the payment slot nullable, the
         *  owning dimension stamped). */
        public record Row(int x, int y, int z, int levels, int primary, int secondary,
                          Slot payment, int dimension) {
        }
    }

    /**
     * The deep capture of every dimension's live maps — called ON the
     * owning tick lane (each system's snapshot() is the map copy; this
     * walks it into the immutable rows). The caller's map is not retained.
     *
     * @param beacons each dimension's live map (the FL4-GAP2 Slice C union)
     */
    public Snapshot capture(Map<Integer, Map<BlockPosition, BeaconBlockEntity>> beacons) {
        java.util.List<Snapshot.Row> rows = new java.util.ArrayList<>();
        for (Map.Entry<Integer, Map<BlockPosition, BeaconBlockEntity>> byDimension
                : beacons.entrySet()) {
            int dimension = byDimension.getKey();
            for (Map.Entry<BlockPosition, BeaconBlockEntity> entry
                    : byDimension.getValue().entrySet()) {
                BlockPosition position = entry.getKey();
                BeaconBlockEntity beacon = entry.getValue();
                rows.add(new Snapshot.Row(position.x(), position.y(), position.z(),
                        beacon.levels(), beacon.primary(), beacon.secondary(),
                        Slot.of(beacon.payment()), dimension));
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
                writeVarInt(out, row.levels());
                writeVarInt(out, row.primary());
                writeVarInt(out, row.secondary());
                Slot payment = row.payment();
                if (payment == null) {
                    out.writeByte(0);
                } else {
                    out.writeByte(1);
                    writeString(out, payment.id());
                    writeVarInt(out, payment.count());
                    writeVarInt(out, payment.damage());
                    writeOptionalString(out, payment.displayName());
                }
                // The signed byte carries the dimension — -1 lands as u8 255.
                out.writeByte(row.dimension());
            }
        } catch (IOException e) {
            // The sink is an in-memory buffer; an IOException here is a
            // defect, not a persistence failure — still loud.
            throw new java.io.UncheckedIOException("Beacon serialize failed", e);
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
            throw new IllegalStateException("Beacon save failed for " + file, e);
        }
    }

    /** One loaded beacon row, stamped with the world it lives in. */
    public record Loaded(BlockPosition position, int dimension, BeaconBlockEntity beacon) {
    }

    /**
     * Loads the beacon rows, each stamped with its dimension (unresolvable
     * payment items drop with a warning). A version 1 file's rows carry no
     * dimension byte — they load as overworld rows (the migration law
     * every FL4-GAP2 store carries).
     */
    public java.util.List<Loaded> load() {
        if (!Files.exists(file)) {
            return java.util.List.of();
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                Files.newInputStream(file)))) {
            if (in.readByte() != MAGIC_0 || in.readByte() != MAGIC_1 || in.readByte() != MAGIC_2) {
                throw new IOException("Not a ZBT file");
            }
            int version = in.readByte();
            if (version < 1 || version > FORMAT_VERSION) {
                throw new IOException("Unsupported ZBT version: " + version);
            }
            int count = readVarInt(in);
            java.util.List<Loaded> loaded = new java.util.ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                int x = in.readInt();
                int y = in.readInt();
                int z = in.readInt();
                BeaconBlockEntity beacon = new BeaconBlockEntity();
                beacon.restoreLevels(readVarInt(in));
                beacon.restorePrimary(readVarInt(in));
                beacon.restoreSecondary(readVarInt(in));
                if (in.readUnsignedByte() == 1) {
                    Identifier identifier = Identifier.parse(readString(in));
                    int stackCount = readVarInt(in);
                    int damage = readVarInt(in);
                    String displayName = readOptionalString(in);
                    Optional<net.zaminmc.torch.item.ItemType> type =
                            BuiltinItems.lookup(identifier);
                    if (type.isPresent() && BeaconBlockEntity.isPaymentItem(type.get())) {
                        beacon.setPayment(ItemStack.of(type.get(), stackCount)
                                .withDamage(damage).withName(displayName));
                    } else {
                        LOGGER.warning("Saved beacon payment no longer registered, dropped: "
                                + identifier);
                    }
                }
                int dimension = version >= FORMAT_VERSION ? in.readByte() : 0;
                loaded.add(new Loaded(new BlockPosition(x, y, z), dimension, beacon));
            }
            return loaded;
        } catch (IOException | RuntimeException corrupt) {
            Path quarantine = file.resolveSibling(file.getFileName() + ".corrupt");
            try {
                Files.move(file, quarantine, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.warning("Beacon data file was unreadable; preserved as " + quarantine
                        + " (" + corrupt.getMessage() + ")");
            } catch (IOException moveFailure) {
                LOGGER.warning("Beacon data file was unreadable and could not be preserved: "
                        + moveFailure);
            }
            return java.util.List.of();
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
                throw new IOException("VarInt too large in beacon data file");
            }
        }
    }
}
