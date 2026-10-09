package net.zaminmc.torch.server.player;

import net.zaminmc.torch.entity.Player;

import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;

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
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * File-backed player data store ("ZPD" format, version 5), one file per player.
 *
 * <p>Layout: magic 'Z','P','D',5 - name(string) - x(double) y(double) z(double)
 * - yaw(float) pitch(float) - heldSlot(varint) - slots(varint count of non-empty
 * entries; each: slot(varint) + item identifier(string) + count(varint) +
 * damage(varint) + displayName(varint-length UTF-8, 0 = none)) - health(float)
 * food(varint) saturation(float). Version 1 files (pre-body) load with the
 * historical defaults (20 / 20 / 5); version 2 files (pre-name) load with no
 * custom names; version 4 files (pre-armor) load with empty armor. ZPD v5
 * carries the armor row inside the slot namespace at indexes 36-39
 * (head, chest, legs, feet) — the count-prefixed list is self-describing, so
 * older readers never see entries they cannot place.</p>
 *
 * <p>The same durability rules as the world store: writes are atomic (temp file
 * then atomic move), corrupt files load as absent and are preserved beside the
 * storage path instead of being destroyed, and save failures are loud (§54/§286).</p>
 */
public final class PlayerDataStore {

    private static final Logger LOGGER = Logger.getLogger(PlayerDataStore.class.getName());
    private static final int MAGIC_0 = 'Z';
    private static final int MAGIC_1 = 'P';
    private static final int MAGIC_2 = 'D';
    private static final int FORMAT_VERSION = 5;

    private final Path directory;

    public PlayerDataStore(Path directory) {
        this.directory = java.util.Objects.requireNonNull(directory, "directory");
    }

    public void save(PlayerSnapshot snapshot) {
        Path file = fileOf(snapshot.uuid());
        try {
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.createDirectories(directory);
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                    Files.newOutputStream(temp)))) {
                out.writeByte(MAGIC_0);
                out.writeByte(MAGIC_1);
                out.writeByte(MAGIC_2);
                out.writeByte(FORMAT_VERSION);
                writeString(out, snapshot.name());
                out.writeDouble(snapshot.position().x());
                out.writeDouble(snapshot.position().y());
                out.writeDouble(snapshot.position().z());
                out.writeFloat(snapshot.rotation().yaw());
                out.writeFloat(snapshot.rotation().pitch());
                writeVarInt(out, snapshot.heldSlot());
                List<PlayerSnapshot.SlotStack> filled = snapshot.slots();
                writeVarInt(out, filled.size());
                for (PlayerSnapshot.SlotStack stack : filled) {
                    writeVarInt(out, stack.slot());
                    writeString(out, stack.item().toString());
                    writeVarInt(out, stack.count());
                    writeVarInt(out, stack.damage());
                    writeOptionalString(out, stack.displayName());
                }
                out.writeFloat(snapshot.health());
                writeVarInt(out, snapshot.food());
                out.writeFloat(snapshot.saturation());
                out.writeByte(snapshot.gamemodeId());
            }
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new IllegalStateException("Player save failed for " + file, e);
        }
    }

    public Optional<PlayerSnapshot> load(UUID uuid) {
        Path file = fileOf(uuid);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                Files.newInputStream(file)))) {
            if (in.readByte() != MAGIC_0 || in.readByte() != MAGIC_1 || in.readByte() != MAGIC_2) {
                throw new IOException("Not a ZPD file");
            }
            int version = in.readByte();
            if (version < 1 || version > FORMAT_VERSION) {
                throw new IOException("Unsupported ZPD version: " + version);
            }
            String name = readString(in);
            Position position = new Position(in.readDouble(), in.readDouble(), in.readDouble());
            Rotation rotation = new Rotation(in.readFloat(), in.readFloat());
            int heldSlot = readVarInt(in);

            int filledCount = readVarInt(in);
            List<PlayerSnapshot.SlotStack> stacks = new ArrayList<>(filledCount);
            for (int i = 0; i < filledCount; i++) {
                int slot = readVarInt(in);
                Identifier item = Identifier.parse(readString(in));
                int count = readVarInt(in);
                int damage = readVarInt(in);
                String displayName = version >= 3 ? readOptionalString(in) : null;
                stacks.add(new PlayerSnapshot.SlotStack(slot, item, count, damage, displayName));
            }
            // Version 1 predates the body: defaults apply (the historical state
            // of every player before survival had hunger).
            float health = PlayerSnapshot.LEGACY_HEALTH;
            int food = PlayerSnapshot.LEGACY_FOOD;
            float saturation = PlayerSnapshot.LEGACY_SATURATION;
            if (version >= 2) {
                health = in.readFloat();
                food = readVarInt(in);
                saturation = in.readFloat();
            }
            // Version 4 adds the per-player game mode; older saves read as
            // "unspecified" and the server default applies.
            int gamemodeId = version >= 4 ? in.readByte() : -1;
            return Optional.of(new PlayerSnapshot(uuid, name, position, rotation, heldSlot,
                    stacks, health, food, saturation, gamemodeId));
        } catch (IOException | RuntimeException corrupt) {
            // A malformed value (bad identifier, out-of-range slot) is corruption
            // just as much as a broken header: quarantine, then treat as absent.
            Path quarantine = file.resolveSibling(file.getFileName() + ".corrupt");
            try {
                Files.move(file, quarantine, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.warning("Player data file was unreadable; preserved as " + quarantine
                        + " (" + corrupt.getMessage() + ")");
            } catch (IOException moveFailure) {
                LOGGER.warning("Player data file was unreadable and could not be preserved: "
                        + moveFailure);
            }
            return Optional.empty();
        }
    }

    private Path fileOf(UUID uuid) {
        return directory.resolve(uuid + ".zpd");
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
                throw new IOException("VarInt too large in player data file");
            }
        }
    }
}
