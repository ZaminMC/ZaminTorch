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
 * File-backed player data store ("ZPD" format, version 7), one file per player.
 *
 * <p>Layout: magic 'Z','P','D',6 - name(string) - x(double) y(double) z(double)
 * - yaw(float) pitch(float) - heldSlot(varint) - slots(varint count of non-empty
 * entries; each: slot(varint) + item identifier(string) + count(varint) +
 * damage(varint) + displayName(varint-length UTF-8, 0 = none)) - health(float)
 * food(varint) saturation(float). Version 1 files (pre-body) load with the
 * historical defaults (20 / 20 / 5); version 2 files (pre-name) load with no
 * custom names; version 4 files (pre-armor) load with empty armor. ZPD v5
 * carries the armor row inside the slot namespace at indexes 36-39
 * (head, chest, legs, feet) — the count-prefixed list is self-describing, so
 * older readers never see entries they cannot place. ZPD v6 appends the
 * experience total and v7 the bed spawn (absent-marker first), so
 * experience total (varlong) after the game mode; v5 files load with 0 XP;
 * v9 appends the statistics map (varint count of key(string) + value(varint)
 * pairs) after the ender tail; v8 files load with empty stats; v11 appends
 * the achievement-progress rows (varint count of stat key(string) + set
 * size(varint) + the strings) after the command-stat tail; v10 files load
 * with no progress.</p>
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
    private static final int FORMAT_VERSION = 11;

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
                writeVarLong(out, snapshot.totalXp());
                // ZPD v7: the bed spawn tail (absent marker first).
                if (snapshot.bedSpawn() == null) {
                    out.writeBoolean(false);
                } else {
                    out.writeBoolean(true);
                    out.writeDouble(snapshot.bedSpawn().x());
                    out.writeDouble(snapshot.bedSpawn().y());
                    out.writeDouble(snapshot.bedSpawn().z());
                }
                // ZPD v8: the ender chest tail (the reference's EnderItems —
                // the player's private 27-slot grid, slot 0-26).
                List<PlayerSnapshot.SlotStack> ender = snapshot.enderSlots();
                writeVarInt(out, ender.size());
                for (PlayerSnapshot.SlotStack stack : ender) {
                    writeVarInt(out, stack.slot());
                    writeString(out, stack.item().toString());
                    writeVarInt(out, stack.count());
                    writeVarInt(out, stack.damage());
                    writeOptionalString(out, stack.displayName());
                }
                // ZPD v9: the statistics tail (the stats slice — the
                // counter map over the EngineStats vocabulary).
                writeVarInt(out, snapshot.stats().size());
                for (java.util.Map.Entry<String, Integer> stat : snapshot.stats().entrySet()) {
                    writeString(out, stat.getKey());
                    writeVarInt(out, stat.getValue());
                }
                // ZPD v10: the command-stat tail (Slice 90 — the reference's
                // Entity.writeNbt: the player's .dat carries its CommandResults).
                writeVarInt(out, snapshot.commandStats().size());
                for (java.util.Map.Entry<String, String> pair : snapshot.commandStats().entrySet()) {
                    writeString(out, pair.getKey());
                    writeString(out, pair.getValue());
                }
                // ZPD v11: the achievement-progress tail (VIII6 — the
                // reference's ServerPlayerStats.serialize progress arm: the
                // all-biomes row's string set, one row per carrying stat).
                writeVarInt(out, snapshot.progress().size());
                for (java.util.Map.Entry<String, java.util.Set<String>> row
                        : snapshot.progress().entrySet()) {
                    writeString(out, row.getKey());
                    writeVarInt(out, row.getValue().size());
                    for (String value : row.getValue()) {
                        writeString(out, value);
                    }
                }
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
            // Version 6 appends the experience total; older saves load at 0.
            long totalXp = version >= 6 ? readVarLong(in) : 0;
            // Version 7 appends the bed spawn; older saves load without one.
            Position bedSpawn = null;
            if (version >= 7 && in.readBoolean()) {
                bedSpawn = new Position(in.readDouble(), in.readDouble(), in.readDouble());
            }
            // Version 8 appends the ender chest slots (the reference's
            // EnderItems); older saves load an empty grid.
            List<PlayerSnapshot.SlotStack> enderSlots = new ArrayList<>();
            if (version >= 8) {
                int enderCount = readVarInt(in);
                for (int i = 0; i < enderCount; i++) {
                    int slot = readVarInt(in);
                    Identifier item = Identifier.parse(readString(in));
                    int count = readVarInt(in);
                    int damage = readVarInt(in);
                    String displayName = readOptionalString(in);
                    if (slot >= 0 && slot <= 26) {
                        enderSlots.add(new PlayerSnapshot.SlotStack(
                                slot, item, count, damage, displayName));
                    }
                }
            }
            // Version 9 appends the statistics map; older saves load empty.
            java.util.Map<String, Integer> stats = new java.util.LinkedHashMap<>();
            if (version >= 9) {
                int statCount = readVarInt(in);
                for (int i = 0; i < statCount; i++) {
                    String key = readString(in);
                    stats.put(key, readVarInt(in));
                }
            }
            // Version 10 appends the command-stat pairs; older saves load empty.
            java.util.Map<String, String> commandStats = new java.util.LinkedHashMap<>();
            if (version >= 10) {
                int pairCount = readVarInt(in);
                if (pairCount < 0 || pairCount > 64) {
                    throw new IOException("Implausible command-stat pair count: " + pairCount);
                }
                for (int i = 0; i < pairCount; i++) {
                    commandStats.put(readString(in), readString(in));
                }
            }
            // Version 11 appends the achievement-progress rows (the
            // all-biomes set); older saves load none.
            java.util.Map<String, java.util.Set<String>> progress = new java.util.LinkedHashMap<>();
            if (version >= 11) {
                int rowCount = readVarInt(in);
                if (rowCount < 0 || rowCount > 64) {
                    throw new IOException("Implausible progress row count: " + rowCount);
                }
                for (int i = 0; i < rowCount; i++) {
                    String key = readString(in);
                    int size = readVarInt(in);
                    if (size < 0 || size > 1024) {
                        throw new IOException("Implausible progress set size: " + size);
                    }
                    java.util.Set<String> values = new java.util.LinkedHashSet<>();
                    for (int j = 0; j < size; j++) {
                        values.add(readString(in));
                    }
                    progress.put(key, values);
                }
            }
            return Optional.of(new PlayerSnapshot(uuid, name, position, rotation, heldSlot,
                    stacks, health, food, saturation, gamemodeId, totalXp, bedSpawn, enderSlots,
                    stats, commandStats, progress));
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

    private static void writeVarLong(DataOutputStream out, long value) throws IOException {
        while (true) {
            if ((value & ~0x7FL) == 0) {
                out.write((int) value);
                return;
            }
            out.write((int) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }
    }

    private static long readVarLong(DataInputStream in) throws IOException {
        long value = 0;
        int shift = 0;
        while (true) {
            long b = in.readUnsignedByte();
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return value;
            }
            shift += 7;
            if (shift > 63) {
                throw new IOException("VarLong too large in player data file");
            }
        }
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
