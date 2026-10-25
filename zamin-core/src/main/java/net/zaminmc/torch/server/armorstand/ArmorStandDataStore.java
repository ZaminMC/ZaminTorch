package net.zaminmc.torch.server.armorstand;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * File-backed armor stand state ("ZAD" format, version 1), one file per
 * world — the jukebox store's own durability rules.
 *
 * <p>Layout: magic 'Z','A','D',1 - stands(varint count; each: x/y/z
 * (f64) yaw(f32) status(u8) then five equipment slots in the ZCD item
 * form + the six pose triples (f32 x3 each: head, body, leftArm,
 * rightArm, leftLeg, rightLeg)) — the reference's writeCustomNbt fields
 * (Equipment, the status booleans, Pose).</p>
 *
 * <p>Writes are atomic (temp file then atomic move), corrupt files load
 * as absent and are preserved beside the storage path instead of being
 * destroyed, and save failures are loud. Save points are the engine's
 * {@code saveAllNow} (shutdown, console save).</p>
 */
public final class ArmorStandDataStore {

    private static final Logger LOGGER = Logger.getLogger(ArmorStandDataStore.class.getName());
    private static final int MAGIC_0 = 'Z';
    private static final int MAGIC_1 = 'A';
    private static final int MAGIC_2 = 'D';
    private static final int FORMAT_VERSION = 3;

    private final Path file;

    public ArmorStandDataStore(Path file) {
        this.file = Objects.requireNonNull(file, "file");
    }

    /**
     * Saves the stand records. Callers snapshot state on the tick thread
     * before calling (the engine routes this through the ticker). The
     * synchronous form composes the S5 pipeline's own two halves — the
     * pure serialization and the file publication — so the sync save's
     * bytes and the pipeline's bytes are identical by construction (one
     * serializer, one file half).
     */
    public void save(List<ArmorStandSystem.StandRecord> stands) {
        writeSerialized(serialize(stands));
    }

    /**
     * The pure serialization (the compute pool's own half, Slice S5):
     * the immutable snapshot IN (the stand records are the fresh values
     * the system's own snapshot walk builds — the engine's ItemStack is
     * the immutable model), the ZAD bytes OUT — no world read, no I/O.
     * The byte walk is the historical save walk's own (version 1,
     * byte-identical).
     */
    public byte[] serialize(List<ArmorStandSystem.StandRecord> stands) {
        Objects.requireNonNull(stands, "stands");
        java.io.ByteArrayOutputStream sink = new java.io.ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(sink))) {
            out.writeByte(MAGIC_0);
            out.writeByte(MAGIC_1);
            out.writeByte(MAGIC_2);
            out.writeByte(FORMAT_VERSION);
            writeVarInt(out, stands.size());
            for (ArmorStandSystem.StandRecord stand : stands) {
                out.writeDouble(stand.x());
                out.writeDouble(stand.y());
                out.writeDouble(stand.z());
                out.writeFloat(stand.yaw());
                out.writeByte(stand.status());
                for (ItemStack piece : stand.equipment()) {
                    writeStack(out, piece);
                }
                writePose(out, stand.head());
                writePose(out, stand.body());
                writePose(out, stand.leftArm());
                writePose(out, stand.rightArm());
                writePose(out, stand.leftLeg());
                writePose(out, stand.rightLeg());
                // VT-7g — the v2 row: the DisabledSlots bitfield (the
                // reference's NBT row at line 141).
                out.writeInt(stand.disabledSlots());
                // VT-7i — the v3 row: the Invisible flag (the reference's
                // NBT row at line 138).
                out.writeBoolean(stand.invisible());
            }
        } catch (IOException e) {
            // The sink is an in-memory buffer; an IOException here is a
            // defect, not a persistence failure — still loud.
            throw new java.io.UncheckedIOException("Armor-stand serialize failed", e);
        }
        return sink.toByteArray();
    }

    /**
     * Publishes already-serialized ZAD bytes: the temp-file write + the
     * atomic move (the non-atomic fallback preserved) — the historical
     * save walk's own file half with the store's own failure contract
     * 1:1 (the severe log, never a throw). Safe from any thread that
     * holds the publication right (the save pipeline's per-storage lock
     * orders the async publications; the synchronous save is
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
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOGGER.severe("Armor stand state save failed for " + file + ": " + e);
        }
    }

    /**
     * Loads the stand records; an absent or corrupt file loads empty
     * (corrupt files are preserved, not destroyed).
     */
    public List<ArmorStandSystem.StandRecord> load() {
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }
        List<ArmorStandSystem.StandRecord> loaded = new ArrayList<>();
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                Files.newInputStream(file)))) {
            if (in.readByte() != MAGIC_0 || in.readByte() != MAGIC_1
                    || in.readByte() != MAGIC_2) {
                LOGGER.warning("Armor stand state " + file + " is not ZAD; loading empty");
                return new ArrayList<>();
            }
            int version = in.readByte() & 0xFF;
            if (version != FORMAT_VERSION && version != 2 && version != 1) {
                LOGGER.warning("Armor stand state " + file + " version " + version
                        + " unsupported; loading empty");
                return new ArrayList<>();
            }
            int count = readVarInt(in);
            if (count < 0 || count > 100_000) {
                LOGGER.warning("Armor stand state " + file + " implausible count; loading empty");
                return new ArrayList<>();
            }
            for (int i = 0; i < count; i++) {
                double x = in.readDouble();
                double y = in.readDouble();
                double z = in.readDouble();
                float yaw = in.readFloat();
                byte status = (byte) in.readUnsignedByte();
                List<ItemStack> equipment = new ArrayList<>();
                for (int slot = 0; slot < 5; slot++) {
                    equipment.add(readStack(in));
                }
                ArmorStand.Pose head = readPose(in);
                ArmorStand.Pose body = readPose(in);
                ArmorStand.Pose leftArm = readPose(in);
                ArmorStand.Pose rightArm = readPose(in);
                ArmorStand.Pose leftLeg = readPose(in);
                ArmorStand.Pose rightLeg = readPose(in);
                // VT-7g — the v2 row: the v1 files answer the default 0
                // (the reference's own default — every swap legal).
                int disabledSlots = version >= 2 ? in.readInt() : 0;
                // VT-7i — the v3 row: the v2 files answer the default
                // false (the reference's own default — a visible stand).
                boolean invisible = version >= 3 && in.readBoolean();
                loaded.add(new ArmorStandSystem.StandRecord(x, y, z, yaw, equipment, status,
                        head, body, leftArm, rightArm, leftLeg, rightLeg, disabledSlots,
                        invisible));
            }
            return loaded;
        } catch (IOException | RuntimeException corrupt) {
            LOGGER.warning("Armor stand state " + file + " corrupt; loading empty (" + corrupt + ")");
            return new ArrayList<>();
        }
    }

    private static void writePose(DataOutputStream out, ArmorStand.Pose pose) throws IOException {
        if (pose == null) {
            pose = ArmorStand.Pose.ZERO;
        }
        out.writeFloat(pose.pitch());
        out.writeFloat(pose.yaw());
        out.writeFloat(pose.roll());
    }

    private static ArmorStand.Pose readPose(DataInputStream in) throws IOException {
        return new ArmorStand.Pose(in.readFloat(), in.readFloat(), in.readFloat());
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
            LOGGER.warning("Saved stand equipment no longer registered, dropped: " + identifier);
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
