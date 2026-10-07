package net.zamin.engine.world;

import net.zamin.api.BlockRegistry;
import net.zamin.api.BlockType;
import net.zamin.api.BlockPosition;
import net.zamin.api.ChunkPosition;
import net.zamin.api.Identifier;
import net.zamin.api.Position;
import net.zamin.api.World;

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
        return chunk.setBlock(position.localX(), position.y(), position.localZ(), type);
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

    public int loadedChunkCount() {
        return chunks.size();
    }

    /** Advances simulation time. Called only by the owning tick context. */
    public void tickTime() {
        requireOwnership("tickTime");
        totalTicks++;
        timeOfDay = (timeOfDay + 1) % 24_000;
    }

    private void requireOwnership(String operation) {
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException(
                    "World '" + name + "' is owned by the simulation context; " + operation
                            + " was called from " + Thread.currentThread().getName());
        }
    }
}
