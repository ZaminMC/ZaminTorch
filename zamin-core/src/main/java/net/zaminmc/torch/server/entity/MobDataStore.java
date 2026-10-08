package net.zaminmc.torch.server.entity;

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
import java.util.logging.Logger;

/**
 * File-backed mob state ("ZMD" format, version 1), one file per world.
 *
 * <p>Layout: magic 'Z','M','D',1 — mobs(varint count; each: type(string) —
 * x(f64) y(f64) z(f64) — yaw(f32) health(f32)). The payload is exactly
 * {@link MobManager.MobSnapshot}.</p>
 *
 * <p>The same durability rules as the world, furnace, chest and player
 * stores: writes are atomic (temp file then atomic move), corrupt files load
 * as absent and are preserved beside the storage path instead of being
 * destroyed, and save failures are loud (§54/§286). Save points are the
 * engine's {@code saveAllNow} (shutdown, console save).</p>
 */
public final class MobDataStore {

    private static final Logger LOGGER = Logger.getLogger(MobDataStore.class.getName());
    private static final int MAGIC_0 = 'Z';
    private static final int MAGIC_1 = 'M';
    private static final int MAGIC_2 = 'D';
    private static final int FORMAT_VERSION = 1;

    private final Path file;

    public MobDataStore(Path file) {
        this.file = Objects.requireNonNull(file, "file");
    }

    /**
     * Saves the full population snapshot. Callers snapshot state on the tick
     * thread before calling (the engine routes this through the ticker).
     */
    public void save(List<MobManager.MobSnapshot> mobs) {
        Objects.requireNonNull(mobs, "mobs");
        try {
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.createDirectories(file.getParent());
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                    Files.newOutputStream(temp)))) {
                out.writeByte(MAGIC_0);
                out.writeByte(MAGIC_1);
                out.writeByte(MAGIC_2);
                out.writeByte(FORMAT_VERSION);
                writeVarInt(out, mobs.size());
                for (MobManager.MobSnapshot mob : mobs) {
                    writeString(out, mob.type());
                    out.writeDouble(mob.x());
                    out.writeDouble(mob.y());
                    out.writeDouble(mob.z());
                    out.writeFloat(mob.yaw());
                    out.writeFloat(mob.health());
                }
            }
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new IllegalStateException("Mob save failed for " + file, e);
        }
    }

    /**
     * Loads the population snapshot; an absent file means "fresh world".
     * Corrupt files are quarantined and treated as absent.
     */
    public List<MobManager.MobSnapshot> load() {
        if (!Files.exists(file)) {
            return List.of();
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                Files.newInputStream(file)))) {
            if (in.readByte() != MAGIC_0 || in.readByte() != MAGIC_1 || in.readByte() != MAGIC_2) {
                throw new IOException("Not a ZMD file");
            }
            int version = in.readByte();
            if (version != FORMAT_VERSION) {
                throw new IOException("Unsupported ZMD version: " + version);
            }
            int count = readVarInt(in);
            if (count < 0 || count > 1_000_000) {
                throw new IOException("Implausible mob count: " + count);
            }
            List<MobManager.MobSnapshot> loaded = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                String type = readString(in);
                double x = in.readDouble();
                double y = in.readDouble();
                double z = in.readDouble();
                float yaw = in.readFloat();
                float health = in.readFloat();
                loaded.add(new MobManager.MobSnapshot(type, x, y, z, yaw, health));
            }
            return loaded;
        } catch (IOException | RuntimeException corrupt) {
            // Malformed values are corruption just as much as a broken header:
            // quarantine, then treat as absent (the population rebuilds).
            Path quarantine = file.resolveSibling(file.getFileName() + ".corrupt");
            try {
                Files.move(file, quarantine, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.warning("Mob data file was unreadable; preserved as " + quarantine
                        + " (" + corrupt.getMessage() + ")");
            } catch (IOException moveFailure) {
                LOGGER.warning("Mob data file was unreadable and could not be preserved: "
                        + moveFailure);
            }
            return List.of();
        }
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
                throw new IOException("VarInt too large in mob data file");
            }
        }
    }
}
