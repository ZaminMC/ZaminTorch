package net.zaminmc.torch.server.world;

import net.zaminmc.torch.block.BlockRegistry;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.World;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

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

    private static final Logger LOGGER = Logger.getLogger(EngineWorld.class.getName());

    private final String name;
    private final BlockRegistry registry;
    private final BlockType air;
    private final WorldGenerator generator;
    private final Thread owner;
    /**
     * The formal ownership domain (the permanent architecture's Phase 1
     * enforcement — {@code docs/CONCURRENCY_ARCHITECTURE.md} §15). Null keeps
     * the legacy thread check; when attached, {@link #requireOwnership}
     * delegates and failures carry the domain + operation context. The
     * engine's simulation loop binds the domain for its whole run, so the
     * live behavior is identical to the thread check.
     */
    private volatile net.zaminmc.torch.server.concurrent.OwnershipDomain domain;
    private final Map<Long, EngineChunk> chunks = new ConcurrentHashMap<>();
    // World change listeners fire on every committed mutation (owner thread).
    private final java.util.List<WorldChangeListener> changeListeners =
            new java.util.concurrent.CopyOnWriteArrayList<>();
    // Chunk-load listeners fire once per freshly generated chunk, before publication.
    private final java.util.List<ChunkLoadListener> chunkLoadListeners =
            new java.util.concurrent.CopyOnWriteArrayList<>();
    // Persisted player-caused changes, owned by the simulation thread. Keyed by
    // chunk, then by local block index (y<<8 | z<<4 | x).
    private final Map<Long, Map<Integer, BlockType>> deltas = new ConcurrentHashMap<>();

    private volatile Position spawnPosition;
    private volatile long timeOfDay;
    private volatile long totalTicks;

    public EngineWorld(String name, BlockRegistry registry, WorldGenerator generator, Thread owner) {
        this.name = Objects.requireNonNull(name, "name");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.generator = Objects.requireNonNull(generator, "generator");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.air = registry.require(Identifier.parse("minecraft:air"));
        this.spawnPosition = generator.spawnPosition();
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

    /**
     * Applies one block state. Owner-thread only. On an actual change the
     * world dispatches its own change event (§208: the world is the source of
     * truth — every committed change, player-driven or engine-driven, reaches
     * the neighbor-update system and the client syncs exactly once, in world
     * order). Listener failures are isolated per listener (§54): one broken
     * observer cannot corrupt the world mutation or starve the others.
     */
    @Override
    public boolean setBlock(BlockPosition position, BlockType type) {
        Objects.requireNonNull(type, "type");
        requireOwnership("setBlock");
        EngineChunk chunk = getOrGenerate(position.chunkPosition());
        boolean changed = chunk.setBlock(position.localX(), position.y(), position.localZ(), type);
        if (changed) {
            // Deltas are the persistence projection of runtime changes (slice 2).
            recordDelta(position.chunkPosition(), localIndex(position.localX(), position.y(), position.localZ()), type);
            fireChange(position, type);
        }
        return changed;
    }

    private void fireChange(BlockPosition position, BlockType type) {
        for (WorldChangeListener listener : changeListeners) {
            try {
                listener.onBlockChanged(this, position, type);
            } catch (RuntimeException listenerFailure) {
                LOGGER.warning(() -> "World change listener failed at " + position + ": "
                        + listenerFailure);
            }
        }
    }

    /**
     * Re-publishes a position's CURRENT state to the change listeners without
     * a world mutation — the rejected-prediction sync path (§441 spirit): the
     * authoritative state re-syncs so clients drop ghost blocks.
     */
    public void republish(BlockPosition position) {
        requireOwnership("republish");
        fireChange(position, getBlock(position));
    }

    private void recordDelta(ChunkPosition chunkPosition, int localIndex, BlockType type) {
        deltas.computeIfAbsent(chunkPosition.packed(), key -> new HashMap<>())
                .put(localIndex, type);
    }

    /**
     * The chunk-local delta index: {@code (y << 8) | (localZ << 4) | localX}
     * — the y takes its full byte (0..255, the chunk's real height), the
     * earlier 4-bit y mask truncated every edit at y ≥ 16 down into the
     * 0..15 band (the nether dimension's 128-tall world surfaced it: a
     * y=40 edit round-tripped as y=8 and collided with the y=8 resident).
     */
    private static int localIndex(int localX, int y, int localZ) {
        return (y << 8) | (localZ << 4) | localX;
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
        return installGenerated(position, chunk);
    }

    /**
     * The owner-side installation of a fully generated detached chunk (the
     * permanent architecture's §13 chunk contract: "an off-thread result must
     * not overwrite newer authoritative state simply because it finishes
     * later"): the persisted deltas override the generated terrain, the
     * chunk-load hooks run before publication (§344), and a chunk another
     * path already published wins — the detached result is discarded, never
     * installed over it. Owner-thread context (the compute pool's fresh
     * results reach this through the owner's drain).
     */
    public EngineChunk installGenerated(ChunkPosition position, EngineChunk generated) {
        requireOwnership("installGenerated");
        Objects.requireNonNull(generated, "generated");
        long key = position.packed();
        EngineChunk existing = chunks.get(key);
        if (existing != null) {
            return existing; // the newer authoritative state wins
        }
        // Persisted deltas override freshly generated terrain at the same positions.
        Map<Integer, BlockType> chunkDeltas = deltas.get(key);
        if (chunkDeltas != null) {
            for (Map.Entry<Integer, BlockType> entry : chunkDeltas.entrySet()) {
                int index = entry.getKey();
                generated.setBlock(index & 0xF, (index >> 8) & 0xFF, (index >> 4) & 0xF, entry.getValue());
            }
        }
        // Chunk-load hooks run before publication (§344): the chunk becomes
        // visible only fully generated AND fully lit. Listeners must not
        // re-enter generation (they peek neighbors, never create chunks).
        for (ChunkLoadListener listener : chunkLoadListeners) {
            listener.onChunkGenerated(this, generated);
        }
        EngineChunk raced = chunks.putIfAbsent(key, generated);
        return raced != null ? raced : generated;
    }

    /** The terrain generator (the detached chunk generation reads it off-owner). */
    public WorldGenerator generator() {
        return generator;
    }

    /** Read-only peek without generation, safe from any thread. */
    public EngineChunk peek(ChunkPosition position) {
        return chunks.get(position.packed());
    }

    /**
     * Snapshot view of all currently loaded chunks. Tick-thread context only
     * (the simulation's random-tick and update systems iterate it); the
     * concurrent map makes an uncoordinated read safe, the iteration order is
     * unspecified by design.
     */
    public java.util.Collection<EngineChunk> loadedChunks() {
        return java.util.Collections.unmodifiableCollection(chunks.values());
    }

    /** Registers an engine-internal change observer (update system, adapter sync). */
    public void addChangeListener(WorldChangeListener listener) {
        changeListeners.add(java.util.Objects.requireNonNull(listener, "listener"));
    }

    /**
     * Registers an engine-internal chunk-load observer (the light engine).
     * Fires on the generating thread, before the chunk is visible.
     */
    public void addChunkLoadListener(ChunkLoadListener listener) {
        chunkLoadListeners.add(java.util.Objects.requireNonNull(listener, "listener"));
    }

    /** The canonical air identity of this world (single source for emptiness checks). */
    public BlockType airType() {
        return air;
    }

    // ------------------------------------------------------------------ light reads (§475)

    /** @return the block light level at the position; 0 outside loaded chunks. */
    public int blockLightAt(BlockPosition position) {
        EngineChunk chunk = peek(position.chunkPosition());
        return chunk == null ? 0 : chunk.blockLight(position.localX(), position.y(), position.localZ());
    }

    /** @return the skylight level at the position; 15 outside loaded chunks (open sky). */
    public int skyLightAt(BlockPosition position) {
        EngineChunk chunk = peek(position.chunkPosition());
        return chunk == null ? 15 : chunk.skyLight(position.localX(), position.y(), position.localZ());
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
        // The clock wraps on the day length (floorMod keeps the /time add
        // arm's negative sums walking forward, never to a negative clock).
        this.timeOfDay = Math.floorMod(timeOfDay, DAY_LENGTH_TICKS);
    }

    private void requireOwnership(String operation) {
        net.zaminmc.torch.server.concurrent.OwnershipDomain attached = domain;
        if (attached != null) {
            // The domain path: the bound executor is the authoritative
            // context; STRICT throws the diagnosable violation (an
            // IllegalStateException subclass — the historical contract).
            attached.checkInContext(operation);
            return;
        }
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException(
                    "World '" + name + "' is owned by the simulation context; " + operation
                            + " was called from " + Thread.currentThread().getName());
        }
    }

    /**
     * Attaches the formal ownership domain (Phase 1 wiring; the engine's
     * simulation loop binds it for its whole run). Once attached the legacy
     * owner-thread check is superseded — the domain's bound executor is the
     * authority, generation-tagged for the compute-result validation.
     */
    public void attachDomain(net.zaminmc.torch.server.concurrent.OwnershipDomain ownershipDomain) {
        this.domain = Objects.requireNonNull(ownershipDomain, "ownershipDomain");
    }

    /** @return the attached ownership domain, or null when the legacy check is active. */
    public net.zaminmc.torch.server.concurrent.OwnershipDomain domain() {
        return domain;
    }
}
