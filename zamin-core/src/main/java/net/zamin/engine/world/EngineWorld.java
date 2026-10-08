package net.zamin.engine.world;

import net.zamin.api.BlockRegistry;
import net.zamin.api.BlockType;
import net.zamin.api.BlockPosition;
import net.zamin.api.ChunkPosition;
import net.zamin.api.Identifier;
import net.zamin.api.Position;
import net.zamin.api.World;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The authoritative state of one world.
 *
 * <p>Ownership model: world state is owned by the engine simulation context. The
 * owning thread is recorded at construction and enforced on mutation — coarse but
 * explicit for Slice #1; execution domains replace the single owner when gameplay
 * parallelism is introduced.</p>
 *
 * <p>Persistence: not implemented (deliberate, documented in the engineering plan).
 * TODO(persistence-slice): route all mutation through a write-behind storage layer
 * before the first block-mutating gameplay lands.</p>
 */
public final class EngineWorld implements World {

    private final String name;
    private final BlockRegistry registry;
    private final BlockType air;
    private final FlatWorldGenerator generator;
    private final Thread owner;
    private final Map<Long, EngineChunk> chunks = new ConcurrentHashMap<>();
    // Persisted player-caused changes, owned by the simulation thread. Keyed by
    // chunk, then by local block index (y<<8 | z<<4 | x).
    private final Map<Long, Map<Integer, BlockType>> deltas = new ConcurrentHashMap<>();

    private volatile Position spawnPosition;
    private volatile long timeOfDay;
    private volatile long totalTicks;

    public EngineWorld(String name, BlockRegistry registry, FlatWorldGenerator generator, Thread owner) {
        this.name = Objects.requireNonNull(name, "name");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.generator = Objects.requireNonNull(generator, "generator");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.air = registry.require(Identifier.parse("minecraft:air"));
        this.spawnPosition = new Position(0.5, generator.groundLevel() + 1.0, 0.5);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public long timeOfDay() {
        return timeOfDay;
    }

    @Override
    public long totalTicks() {
        return totalTicks;
    }

    @Override
    public Position spawnPosition() {
        return spawnPosition;
    }

    public void setSpawnPosition(Position position) {
        Objects.requireNonNull(position, "position");
        this.spawnPosition = position;
    }

    @Override
    public BlockType getBlock(BlockPosition position) {
        Objects.requireNonNull(position, "position");
        ChunkPosition chunkPosition = position.chunkPosition();
        EngineChunk chunk = chunks.get(chunkPosition.packed());
        if (chunk == null) {
            return air;
        }
        return chunk.getBlock(position.localX(), position.y(), position.localZ());
    }

    @Override
    public boolean setBlock(BlockPosition position, BlockType type) {
        Objects.requireNonNull(type, "type");
        requireOwnership("setBlock");
        EngineChunk chunk = getOrGenerate(position.chunkPosition());
        boolean changed = chunk.setBlock(position.localX(), position.y(), position.localZ(), type);
        if (changed) {
            // Deltas are the persistence projection of runtime changes (slice 2).
            recordDelta(position.chunkPosition(), localIndex(position.localX(), position.y(), position.localZ()), type);
        }
        return changed;
    }

    private void recordDelta(ChunkPosition chunkPosition, int localIndex, BlockType type) {
        deltas.computeIfAbsent(chunkPosition.packed(), key -> new HashMap<>())
                .put(localIndex, type);
    }

    private static int localIndex(int localX, int y, int localZ) {
        return ((y & 0xF) << 8) | (localZ << 4) | localX;
    }

    @Override
    public boolean isChunkLoaded(ChunkPosition position) {
        return chunks.containsKey(position.packed());
    }

    /**
     * Returns the chunk, generating it if needed. Generation happens on the caller's
     * thread; callers must hold world ownership. Atomic publication (§344): the
     * chunk becomes visible only fully generated.
     */
    public EngineChunk getOrGenerate(ChunkPosition position) {
        requireOwnership("getOrGenerate");
        long key = position.packed();
        EngineChunk existing = chunks.get(key);
        if (existing != null) {
            return existing;
        }
        EngineChunk chunk = new EngineChunk(position, air);
        generator.generate(chunk);
        // Persisted deltas override freshly generated terrain at the same positions.
        Map<Integer, BlockType> chunkDeltas = deltas.get(key);
        if (chunkDeltas != null) {
            for (Map.Entry<Integer, BlockType> entry : chunkDeltas.entrySet()) {
                int index = entry.getKey();
                chunk.setBlock(index & 0xF, (index >> 8) & 0xF, (index >> 4) & 0xF, entry.getValue());
            }
        }
        EngineChunk raced = chunks.putIfAbsent(key, chunk);
        return raced != null ? raced : chunk;
    }

    /** Read-only peek without generation, safe from any thread. */
    public EngineChunk peek(ChunkPosition position) {
        return chunks.get(position.packed());
    }

    /** The canonical air identity of this world (single source for emptiness checks). */
    public BlockType airType() {
        return air;
    }

    /** Applies a loaded delta snapshot. Owner-thread only, before chunks generate. */
    public void applyDeltas(WorldDeltaSnapshot snapshot) {
        requireOwnership("applyDeltas");
        for (Map.Entry<Long, Map<Integer, BlockType>> entry : snapshot.deltas().entrySet()) {
            deltas.computeIfAbsent(entry.getKey(), key -> new HashMap<>())
                    .putAll(entry.getValue());
        }
        this.totalTicks = snapshot.totalTicks();
        this.timeOfDay = snapshot.timeOfDay();
    }

    /** Consistent delta view for persistence. Owner-thread only. */
    public WorldDeltaSnapshot snapshotDeltas() {
        requireOwnership("snapshotDeltas");
        Map<Long, Map<Integer, BlockType>> copy = new HashMap<>();
        deltas.forEach((chunkKey, entries) -> copy.put(chunkKey, Map.copyOf(entries)));
        return new WorldDeltaSnapshot(totalTicks, timeOfDay, Map.copyOf(copy));
    }

    public int deltaCount() {
        return deltas.values().stream().mapToInt(Map::size).sum();
    }

    public int loadedChunkCount() {
        return chunks.size();
    }

    /** Advances simulation time. Called only by the owning tick context. */
    public void tickTime() {
        requireOwnership("tickTime");
        totalTicks++;
        timeOfDay = (timeOfDay + 1) % DAY_LENGTH_TICKS;
    }

    /** Historical day length in ticks (the world's own cycle). */
    public static final long DAY_LENGTH_TICKS = 24_000;

    /**
     * Moves the day clock (the /time command). Runs on the owning tick context
     * (the chat dispatch routes through the engine's tick queue); the periodic
     * cycle sync and hostile spawning read the same field.
     */
    public void setTimeOfDay(long timeOfDay) {
        requireOwnership("setTimeOfDay");
        this.timeOfDay = timeOfDay % DAY_LENGTH_TICKS;
    }

    private void requireOwnership(String operation) {
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException(
                    "World '" + name + "' is owned by the simulation context; " + operation
                            + " was called from " + Thread.currentThread().getName());
        }
    }
}
