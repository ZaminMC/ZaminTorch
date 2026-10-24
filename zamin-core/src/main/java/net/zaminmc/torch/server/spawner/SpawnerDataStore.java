package net.zaminmc.torch.server.spawner;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.entity.MobType;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * File-backed spawner state ("ZSD" format, version 1), one file per
 * server — the note store's own durability rules.
 *
 * <p>Layout: magic 'Z','S','D',1 - spawners(varint count; each:
 * dimension(i32) x(i32) y(i32) z(i32) type(varint-length UTF, the
 * MobType's vanilla name) delay(i32) minSpawnDelay(i32)
 * maxSpawnDelay(i32) spawnCount(i32) maxNearbyEntities(i32)
 * requiredPlayerRange(i32) spawnRange(i32)).</p>
 *
 * <p>The persisted field set is the reference's own writeNbt law
 * (MobSpawner.writeNbt lines 236-252): a spawner whose type is unset
 * NEVER persists (the whole walk sits inside the EntityId-non-empty
 * gate), and a typed spawner carries the delay plus the six cadence
 * rows. The 1.8.8 generation paths never create SpawnPotentials (the
 * dungeon's own DungeonFeature line 128 sets a single type), so the
 * single-type row IS the vanilla 1.8.8 spawner's persisted shape.</p>
 *
 * <p>Writes are atomic (temp file then atomic move), corrupt files load
 * as absent and are preserved beside the storage path instead of being
 * destroyed, and save failures are loud. Save points are the engine's
 * {@code saveAllNow} (shutdown, console save).</p>
 */
public final class SpawnerDataStore {

    private static final Logger LOGGER = Logger.getLogger(SpawnerDataStore.class.getName());
    private static final int MAGIC_0 = 'Z';
    private static final int MAGIC_1 = 'S';
    private static final int MAGIC_2 = 'D';
    private static final int FORMAT_VERSION = 1;

    private final Path file;

    public SpawnerDataStore(Path file) {
        this.file = Objects.requireNonNull(file, "file");
    }

    /**
     * Saves the full spawner map. Callers snapshot state on the tick
     * thread before calling (the engine routes this through the ticker).
     */
    public void save(Map<BlockPosition, SpawnerManager.MobSpawnerBlockEntity> spawners) {
        writeSerialized(serialize(capture(spawners)));
    }

    /**
     * The immutable persistence projection: every typed spawner's row
     * captured on the owning lane, safe to cross into the compute pool —
     * the serializer never reads the live entities.
     */
    public record Snapshot(List<Row> rows) {

        /** One spawner's persisted state. */
        public record Row(int dimension, int x, int y, int z, String typeName,
                          int delay, int minSpawnDelay, int maxSpawnDelay, int spawnCount,
                          int maxNearbyEntities, int requiredPlayerRange, int spawnRange) {
        }
    }

    /**
     * The deep capture of the live spawner map — called ON the owning
     * tick lane. Untyped spawners never persist (the reference's own
     * EntityId gate); the caller's map is not retained.
     */
    public Snapshot capture(Map<BlockPosition, SpawnerManager.MobSpawnerBlockEntity> spawners) {
        List<Snapshot.Row> rows = new java.util.ArrayList<>(spawners.size());
        for (Map.Entry<BlockPosition, SpawnerManager.MobSpawnerBlockEntity> entry
                : spawners.entrySet()) {
            SpawnerManager.MobSpawnerBlockEntity spawner = entry.getValue();
            MobType type = spawner.type();
            if (type == null) {
                continue; // the reference's writeNbt skips the untyped row whole
            }
            BlockPosition position = entry.getKey();
            rows.add(new Snapshot.Row(spawner.dimension(), position.x(), position.y(),
                    position.z(), type.vanillaName(), spawner.delay(), spawner.minSpawnDelay(),
                    spawner.maxSpawnDelay(), spawner.spawnCount(), spawner.maxNearbyEntities(),
                    spawner.requiredPlayerRange(), spawner.spawnRange()));
        }
        return new Snapshot(List.copyOf(rows));
    }

    /**
     * The pure serialization: the immutable snapshot IN, the ZSD bytes
     * OUT — no world read, no I/O.
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
                out.writeInt(row.dimension());
                out.writeInt(row.x());
                out.writeInt(row.y());
                out.writeInt(row.z());
                writeString(out, row.typeName());
                out.writeInt(row.delay());
                out.writeInt(row.minSpawnDelay());
                out.writeInt(row.maxSpawnDelay());
                out.writeInt(row.spawnCount());
                out.writeInt(row.maxNearbyEntities());
                out.writeInt(row.requiredPlayerRange());
                out.writeInt(row.spawnRange());
            }
        } catch (IOException e) {
            // The sink is in-memory; an IOException here is a defect.
            throw new java.io.UncheckedIOException("Spawner serialize failed", e);
        }
        return sink.toByteArray();
    }

    /**
     * Publishes already-serialized ZSD bytes: the temp-file write + the
     * atomic move (the non-atomic fallback preserved). Safe from any
     * thread that holds the publication right.
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
                Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOGGER.severe("Spawner state save failed for " + file + ": " + e);
        }
    }

    /**
     * Loads the spawner map (typed rows only — an unknown type name logs
     * and skips its row, never corrupting the walk); an absent or corrupt
     * file loads empty (corrupt files are preserved, not destroyed).
     */
    public Map<BlockPosition, SpawnerManager.MobSpawnerBlockEntity> load() {
        if (!Files.exists(file)) {
            return new LinkedHashMap<>();
        }
        Map<BlockPosition, SpawnerManager.MobSpawnerBlockEntity> loaded = new LinkedHashMap<>();
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                Files.newInputStream(file)))) {
            if (in.readByte() != MAGIC_0 || in.readByte() != MAGIC_1
                    || in.readByte() != MAGIC_2) {
                LOGGER.warning("Spawner state " + file + " is not ZSD; loading empty");
                return new LinkedHashMap<>();
            }
            int version = in.readByte() & 0xFF;
            if (version != FORMAT_VERSION) {
                LOGGER.warning("Spawner state " + file + " version " + version
                        + " unsupported; loading empty");
                return new LinkedHashMap<>();
            }
            int count = readVarInt(in);
            for (int i = 0; i < count; i++) {
                int dimension = in.readInt();
                BlockPosition position = new BlockPosition(in.readInt(), in.readInt(),
                        in.readInt());
                String typeName = readString(in);
                MobType type = MobType.byVanillaName(typeName);
                if (type == null) {
                    LOGGER.warning("Spawner state " + file + " row " + position
                            + " carries unknown type '" + typeName + "'; skipping");
                    continue;
                }
                SpawnerManager.MobSpawnerBlockEntity spawner =
                        new SpawnerManager.MobSpawnerBlockEntity();
                spawner.setDimension(dimension);
                spawner.setType(type);
                spawner.setDelay(in.readInt());
                spawner.setMinSpawnDelay(in.readInt());
                spawner.setMaxSpawnDelay(in.readInt());
                spawner.setSpawnCount(in.readInt());
                spawner.setMaxNearbyEntities(in.readInt());
                spawner.setRequiredPlayerRange(in.readInt());
                spawner.setSpawnRange(in.readInt());
                loaded.put(position, spawner);
            }
            return loaded;
        } catch (IOException e) {
            LOGGER.warning("Spawner state " + file + " corrupt; loading empty (" + e + ")");
            return new LinkedHashMap<>();
        }
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = readVarInt(in);
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
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
