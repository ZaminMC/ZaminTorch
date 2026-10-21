package net.zaminmc.torch.server.village;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * File-backed village state ("ZVI" format, version 1) — the village
 * census's own persistence (the reference's SavedVillageData over the
 * engine's data-store conventions): the villages with their whole
 * state (the population/golem censuses, the radius, the center + the
 * combined door sum, the mating window, the door rows, the per-player
 * reputations) survive a restart, the same rows the reference's
 * {@code villages.dat} carries.
 *
 * <p>Layout: magic 'Z','V','I',1 — ticks(int) — villages(varint count;
 * each: PopSize(int) Radius(int) Golems(int) Stable(int) Tick(int)
 * MTick(int) CX/CY/CZ(int x3) ACX/ACY/ACZ(int x3) — doors(varint count;
 * each: X(int) Y(int) Z(int) IDX(int) IDZ(int) TS(int)) — players(varint
 * count; each: name(string) S(int))).</p>
 *
 * <p>Writes are atomic (temp file then atomic move), corrupt files load
 * as absent and are preserved beside the storage path instead of being
 * destroyed, and save failures are loud. Save points are the engine's
 * {@code saveAllNow} (shutdown, console save) — the capture on the tick
 * lane, the serialization on the compute pool, the file half on the
 * I/O lane.</p>
 */
public final class VillageDataStore {

    private static final Logger LOGGER = Logger.getLogger(VillageDataStore.class.getName());
    private static final int MAGIC_0 = 'Z';
    private static final int MAGIC_1 = 'V';
    private static final int MAGIC_2 = 'I';
    private static final int FORMAT_VERSION = 1;

    private final Path file;

    public VillageDataStore(Path file) {
        this.file = Objects.requireNonNull(file, "file");
    }

    /**
     * Saves the village rows. Callers snapshot the state on the tick
     * thread before calling; the synchronous form composes the two
     * halves — the pure serialization and the file publication.
     */
    public void save(VillageSystem.Snapshot snapshot) {
        writeSerialized(serialize(snapshot));
    }

    /**
     * The pure serialization (the compute pool's own half): the immutable
     * snapshot IN, the ZVI bytes OUT — no world read, no I/O.
     */
    public byte[] serialize(VillageSystem.Snapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        int ticks = snapshot.ticks();
        List<VillageSystem.VillageRow> rows = snapshot.rows();
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(sink)) {
            out.writeByte(MAGIC_0);
            out.writeByte(MAGIC_1);
            out.writeByte(MAGIC_2);
            out.writeByte(FORMAT_VERSION);
            out.writeInt(ticks);
            writeVarInt(out, rows.size());
            for (VillageSystem.VillageRow row : rows) {
                out.writeInt(row.popSize());
                out.writeInt(row.radius());
                out.writeInt(row.golems());
                out.writeInt(row.stable());
                out.writeInt(row.tick());
                out.writeInt(row.matingTick());
                out.writeInt(row.cx());
                out.writeInt(row.cy());
                out.writeInt(row.cz());
                out.writeInt(row.acx());
                out.writeInt(row.acy());
                out.writeInt(row.acz());
                writeVarInt(out, row.doors().size());
                for (VillageSystem.VillageRow.DoorRow door : row.doors()) {
                    out.writeInt(door.x());
                    out.writeInt(door.y());
                    out.writeInt(door.z());
                    out.writeInt(door.idx());
                    out.writeInt(door.idz());
                    out.writeInt(door.ts());
                }
                writeVarInt(out, row.players().size());
                for (Map.Entry<String, Integer> player : row.players()) {
                    writeString(out, player.getKey());
                    out.writeInt(player.getValue());
                }
            }
        } catch (IOException e) {
            // The sink is an in-memory buffer; an IOException here is a
            // defect, not a persistence failure — still loud.
            throw new java.io.UncheckedIOException("Village state serialize failed", e);
        }
        return sink.toByteArray();
    }

    /**
     * Publishes already-serialized ZVI bytes: the temp-file write + the
     * atomic move (the non-atomic fallback preserved).
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
            LOGGER.severe("Village state save failed for " + file + ": " + e);
        }
    }

    /**
     * The loaded state: the tick + the rows; an absent or corrupt file
     * loads empty (corrupt files are preserved, not destroyed).
     */
    public Loaded load() {
        if (!Files.exists(file)) {
            return new Loaded(0, new ArrayList<>());
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                Files.newInputStream(file)))) {
            if (in.readByte() != MAGIC_0 || in.readByte() != MAGIC_1
                    || in.readByte() != MAGIC_2) {
                LOGGER.warning("Village state " + file + " is not ZVI; loading empty");
                return new Loaded(0, new ArrayList<>());
            }
            int version = in.readByte() & 0xFF;
            if (version != FORMAT_VERSION) {
                LOGGER.warning("Village state " + file + " version " + version
                        + " unsupported; loading empty");
                return new Loaded(0, new ArrayList<>());
            }
            int ticks = in.readInt();
            int count = readVarInt(in);
            if (count < 0 || count > 10_000) {
                LOGGER.warning("Village state " + file + " implausible count; loading empty");
                return new Loaded(0, new ArrayList<>());
            }
            List<VillageSystem.VillageRow> rows = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                int popSize = in.readInt();
                int radius = in.readInt();
                int golems = in.readInt();
                int stable = in.readInt();
                int tick = in.readInt();
                int matingTick = in.readInt();
                int cx = in.readInt();
                int cy = in.readInt();
                int cz = in.readInt();
                int acx = in.readInt();
                int acy = in.readInt();
                int acz = in.readInt();
                int doorCount = readVarInt(in);
                if (doorCount < 0 || doorCount > 10_000) {
                    throw new IOException("Implausible door count: " + doorCount);
                }
                List<VillageSystem.VillageRow.DoorRow> doors = new ArrayList<>(doorCount);
                for (int d = 0; d < doorCount; d++) {
                    doors.add(new VillageSystem.VillageRow.DoorRow(
                            in.readInt(), in.readInt(), in.readInt(),
                            in.readInt(), in.readInt(), in.readInt()));
                }
                int playerCount = readVarInt(in);
                if (playerCount < 0 || playerCount > 10_000) {
                    throw new IOException("Implausible player count: " + playerCount);
                }
                List<Map.Entry<String, Integer>> players = new ArrayList<>(playerCount);
                for (int p = 0; p < playerCount; p++) {
                    String name = readString(in);
                    players.add(Map.entry(name, in.readInt()));
                }
                rows.add(new VillageSystem.VillageRow(popSize, radius, golems, stable,
                        tick, matingTick, cx, cy, cz, acx, acy, acz, doors, players));
            }
            return new Loaded(ticks, rows);
        } catch (IOException | RuntimeException corrupt) {
            LOGGER.warning("Village state " + file + " corrupt; loading empty (" + corrupt + ")");
            return new Loaded(0, new ArrayList<>());
        }
    }

    /** The load's answer: the saved clock + the rows. */
    public record Loaded(int ticks, List<VillageSystem.VillageRow> rows) {
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
                out.writeByte(value);
                return;
            }
            out.writeByte((value & 0x7F) | 0x80);
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
                throw new IOException("VarInt too long");
            }
        }
    }
}
