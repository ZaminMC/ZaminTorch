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
public final class EngineWorld implements World,
        net.zaminmc.torch.server.block.TreeFeatures.TreeWorld {

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
    /**
     * The spatial ownership refinement (the permanent architecture's Phase 4
     * spatial activation): every block/chunk mutation resolves the owning
     * region's domain through this partition and asserts against it — the
     * region is the mutation authority for its chunks (design §4's state
     * map). Null keeps the ambient-only check. The assertion passes when the
     * caller holds EITHER the region domain (the parallel lane's writer) OR
     * the ambient simulation domain (the single tick lane's authority — the
     * "global region" credential of ADR-13); every other context fails with
     * the region-named violation.
     */
    private volatile net.zaminmc.torch.server.concurrent.RegionPartition partition;
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
    /**
     * The RAW day clock (VIII14 — the reference's unwrapped
     * WorldData.timeOfDay): the day count rides its high bits (the moon
     * phase's own {@code / 24000 % 8}), so unlike {@link #timeOfDay} it
     * never folds at the day length. Walks beside the wrapped clock under
     * the same doDaylightCycle gate; {@link #setTimeOfDay} writes it
     * verbatim (the /time arms' own surface).
     */
    private volatile long rawTimeOfDay;
    /**
     * The restored inhabited rows for chunks not yet published (VIII14 —
     * the ZWD v2 tail): {@link #installGenerated} applies a chunk's row
     * at publication, the same pending shape the block-delta overlay
     * carries. Owner-thread; rows persist idempotently (the published
     * chunk's live value is authoritative in the snapshot walk).
     */
    private final Map<Long, Long> pendingInhabited = new HashMap<>();

    /**
     * The public entity view (§27.5): the live population supplier, attached
     * at boot (the overworld carries the mob manager's view; the nether an
     * empty one until its own population lands). Simulation-context reads.
     */
    private volatile java.util.function.Supplier<
            java.util.Collection<? extends net.zaminmc.torch.entity.LivingEntity>> entityView;

    /**
     * The public dropped-item view (§27.3's entity family): the point-in-time
     * population supplier, attached at boot. Simulation-context reads.
     */
    private volatile java.util.function.Supplier<
            java.util.List<? extends net.zaminmc.torch.entity.DroppedItem>> itemView;

    /** The public projectile view (§27.3): attached at boot. */
    private volatile java.util.function.Supplier<
            java.util.List<? extends net.zaminmc.torch.entity.Projectile>> projectileView;

    /** The public falling-block view (§27.3): attached at boot. */
    private volatile java.util.function.Supplier<
            java.util.List<? extends net.zaminmc.torch.entity.FallingBlock>> fallingBlockView;

    /** The public vehicle view (§27.3): attached at boot. */
    private volatile java.util.function.Supplier<
            java.util.List<? extends net.zaminmc.torch.entity.Vehicle>> vehicleView;
    /** The public container view (the chest/furnace/hopper family; boot wiring). */
    private volatile net.zaminmc.torch.container.Containers containerView
            = net.zaminmc.torch.container.Containers.EMPTY;

    /** The public block-entity view (the spawner/sign family; boot wiring). */
    private volatile net.zaminmc.torch.blockentity.BlockEntities blockEntityView
            = net.zaminmc.torch.blockentity.BlockEntities.EMPTY;

    /** The public scheduled-update view (the neighbor/fuse queue; boot wiring). */
    private volatile net.zaminmc.torch.block.ScheduledUpdates scheduledUpdateView
            = net.zaminmc.torch.block.ScheduledUpdates.EMPTY;

    /** The public scheduled block-tick view (the reference's TileTicks queue; boot wiring). */
    private volatile net.zaminmc.torch.block.ScheduledBlockTicks scheduledBlockTickView
            = net.zaminmc.torch.block.ScheduledBlockTicks.EMPTY;

    /** The public redstone view (the power walks; boot wiring). */
    private volatile net.zaminmc.torch.block.RedstoneSignals redstoneView
            = net.zaminmc.torch.block.RedstoneSignals.EMPTY;

    /** The public growth-family view (the stage/age/survival consults; boot wiring). */
    private volatile net.zaminmc.torch.block.Growth growthView
            = net.zaminmc.torch.block.Growth.EMPTY;

    /** The public decoration-census view (the hanging bodies + the
     * stands' family rows; boot wiring — the README TODO's row 15). */
    private volatile net.zaminmc.torch.decoration.Decorations decorationsView
            = net.zaminmc.torch.decoration.Decorations.EMPTY;

    /** The public primed-TNT view (§27.3): attached at boot. */
    private volatile java.util.function.Supplier<
            java.util.List<? extends net.zaminmc.torch.entity.PrimedTnt>> primedTntView;

    /** The public experience-orb view (§27.3): attached at boot. */
    private volatile java.util.function.Supplier<
            java.util.List<? extends net.zaminmc.torch.entity.ExperienceOrb>> orbView;
    /**
     * The owner-context executor (§27.6): routes work onto the authoritative
     * simulation lane (the engine ticker between ticks). Attached at boot.
     */
    private volatile java.util.function.Consumer<Runnable> ownerExecutor;

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

    /** The raw day clock (the moon phase's + the local difficulty's own read). */
    public long rawTimeOfDay() {
        return rawTimeOfDay;
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

    /** The world's own registry (the block-identity lookups' surface). */
    public BlockRegistry blockRegistry() {
        return registry;
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
        requireOwnership("setBlock", position);
        EngineChunk chunk = getOrGenerate(position.chunkPosition());
        BlockType previous = chunk.getBlock(position.localX(), position.y(), position.localZ());
        boolean changed = chunk.setBlock(position.localX(), position.y(), position.localZ(), type);
        if (changed) {
            // Deltas are the persistence projection of runtime changes (slice 2).
            recordDelta(position.chunkPosition(), localIndex(position.localX(), position.y(), position.localZ()), type);
            fireReplaced(position, previous, type);
            fireChange(position, type);
            if (!previous.equals(type)) {
                fireRemoved(position, previous);
            }
        }
        return changed;
    }

    /** The combined pair fan-out (the public event's own shape, Slice 14a). */
    private void fireReplaced(BlockPosition position, BlockType oldType, BlockType newType) {
        for (WorldChangeListener listener : changeListeners) {
            try {
                listener.onBlockReplaced(this, position, oldType, newType);
            } catch (RuntimeException listenerFailure) {
                LOGGER.warning(() -> "World replace listener failed at " + position + ": "
                        + listenerFailure);
            }
        }
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

    /** The removal arm: the old type fanned out after the change (the onRemoved port surface). */
    private void fireRemoved(BlockPosition position, BlockType oldType) {
        for (WorldChangeListener listener : changeListeners) {
            try {
                listener.onBlockRemoved(this, position, oldType);
            } catch (RuntimeException listenerFailure) {
                LOGGER.warning(() -> "World removal listener failed at " + position + ": "
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
        requireOwnership("republish", position);
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
     * True when the chunk holding the position is loaded. Unloaded chunks
     * read as air through {@code getBlock}; state holders must treat that
     * as "frozen" rather than "gone" — the vanilla block-entity model: a
     * block entity in an unloaded chunk neither ticks nor discards; it
     * returns with the chunk (the restart-persistence rule: restored state
     * survives until its chunk loads and the block there is genuinely
     * gone).
     */
    public boolean isChunkLoadedAt(net.zaminmc.torch.block.BlockPosition position) {
        return isChunkLoaded(position.chunkPosition());
    }

    /** Attaches the public entity view (the boot wiring; §27.5). */
    public void attachEntityView(java.util.function.Supplier<
            java.util.Collection<? extends net.zaminmc.torch.entity.LivingEntity>> view) {
        this.entityView = java.util.Objects.requireNonNull(view, "view");
    }

    /** Attaches the public entity-family views (the boot wiring; §27.3's
     *  entity families — items, projectiles, falling blocks, vehicles,
     *  experience orbs). Each supplier's capture must be a stable
     *  point-in-time copy (the managers' {@code all()} contract). */
    public void attachEntityFamilyViews(
            java.util.function.Supplier<
                    java.util.List<? extends net.zaminmc.torch.entity.DroppedItem>> items,
            java.util.function.Supplier<
                    java.util.List<? extends net.zaminmc.torch.entity.Projectile>> projectiles,
            java.util.function.Supplier<
                    java.util.List<? extends net.zaminmc.torch.entity.FallingBlock>> fallingBlocks,
            java.util.function.Supplier<
                    java.util.List<? extends net.zaminmc.torch.entity.Vehicle>> vehicles,
            java.util.function.Supplier<
                    java.util.List<? extends net.zaminmc.torch.entity.ExperienceOrb>> orbs) {
        this.itemView = java.util.Objects.requireNonNull(items, "items");
        this.projectileView = java.util.Objects.requireNonNull(projectiles, "projectiles");
        this.fallingBlockView = java.util.Objects.requireNonNull(fallingBlocks, "fallingBlocks");
        this.vehicleView = java.util.Objects.requireNonNull(vehicles, "vehicles");
        this.orbView = java.util.Objects.requireNonNull(orbs, "orbs");
    }

    /** Attaches the public container view (the chest/furnace/hopper
     *  family's boot wiring; §27.3's block-entity reads). */
    public void attachContainerView(net.zaminmc.torch.container.Containers view) {
        this.containerView = java.util.Objects.requireNonNull(view, "view");
    }

    /** Attaches the public block-entity view (the spawner/sign family's
     *  boot wiring; §27.3's block-entity reads beyond the containers). */
    public void attachBlockEntityView(net.zaminmc.torch.blockentity.BlockEntities view) {
        this.blockEntityView = java.util.Objects.requireNonNull(view, "view");
    }

    /** Attaches the public scheduled-update view (the neighbor/fuse
     *  queue's boot wiring; §27.3's scheduled-block-tick family). */
    public void attachScheduledUpdateView(net.zaminmc.torch.block.ScheduledUpdates view) {
        this.scheduledUpdateView = java.util.Objects.requireNonNull(view, "view");
    }

    /** Attaches the public scheduled block-tick view (the reference's
     *  TileTicks queue's boot wiring; §27.3's family). */
    public void attachScheduledBlockTickView(net.zaminmc.torch.block.ScheduledBlockTicks view) {
        this.scheduledBlockTickView = java.util.Objects.requireNonNull(view, "view");
    }

    /** Attaches the public redstone view (the power walks' boot wiring;
     *  §27.3's redstone family). */
    public void attachRedstoneView(net.zaminmc.torch.block.RedstoneSignals view) {
        this.redstoneView = java.util.Objects.requireNonNull(view, "view");
    }

    /** Wires the public growth-family view (the boot path — §27.3). */
    public void attachGrowthView(net.zaminmc.torch.block.Growth view) {
        this.growthView = java.util.Objects.requireNonNull(view, "view");
    }

    /** Wires the public decoration-census view (the boot path — §27.3,
     *  the README TODO's row 15: the frames + the paintings + the
     *  stands' family rows, each enumeration a monitor snapshot). */
    public void attachDecorationsView(net.zaminmc.torch.decoration.Decorations view) {
        this.decorationsView = java.util.Objects.requireNonNull(view, "view");
    }

    /** Attaches the public primed-TNT view (the TNT family's boot
     *  wiring; §27.3). */
    public void attachPrimedTntView(java.util.function.Supplier<
            java.util.List<? extends net.zaminmc.torch.entity.PrimedTnt>> view) {
        this.primedTntView = java.util.Objects.requireNonNull(view, "view");
    }

    /** Attaches the owner-context executor (the boot wiring; §27.6). */
    public void attachOwnerExecutor(java.util.function.Consumer<Runnable> executor) {
        this.ownerExecutor = java.util.Objects.requireNonNull(executor, "executor");
    }

    /**
     * Asserts the caller holds this world's authoritative mutation
     * context — the public §27.6 surface the read-only views ride for
     * their mutation arms (the cancel/request verbs over tick-confined
     * engine structures).
     *
     * @throws IllegalStateException outside the world's allowed execution context
     */
    public void requireMutationContext(String operation) {
        requireOwnership(operation);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public java.util.Collection<net.zaminmc.torch.entity.LivingEntity> entities() {
        var view = entityView;
        if (view == null) {
            return java.util.List.of();
        }
        // The supplier's own capture already copied the population list
        // (the manager's all() contract) — a stable point-in-time view.
        return (java.util.Collection) view.get();
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public java.util.Collection<net.zaminmc.torch.entity.DroppedItem> itemEntities() {
        var view = itemView;
        if (view == null) {
            return java.util.List.of();
        }
        // The supplier's capture already copied the population (the
        // manager's all() contract) — the same point-in-time shape as
        // entities() above; the raw cast bridges the capture wildcard.
        return (java.util.Collection) view.get();
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public java.util.Collection<net.zaminmc.torch.entity.Projectile> projectiles() {
        var view = projectileView;
        if (view == null) {
            return java.util.List.of();
        }
        // The supplier's capture already copied the population (the
        // manager's all() contract) — the same point-in-time shape as
        // entities() above; the raw cast bridges the capture wildcard.
        return (java.util.Collection) view.get();
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public java.util.Collection<net.zaminmc.torch.entity.FallingBlock> fallingBlocks() {
        var view = fallingBlockView;
        if (view == null) {
            return java.util.List.of();
        }
        // The supplier's capture already copied the population (the
        // manager's all() contract) — the same point-in-time shape as
        // entities() above; the raw cast bridges the capture wildcard.
        return (java.util.Collection) view.get();
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public java.util.Collection<net.zaminmc.torch.entity.Vehicle> vehicles() {
        var view = vehicleView;
        if (view == null) {
            return java.util.List.of();
        }
        // The supplier's capture already copied the population (the
        // manager's all() contract) — the same point-in-time shape as
        // entities() above; the raw cast bridges the capture wildcard.
        return (java.util.Collection) view.get();
    }

    @Override
    public net.zaminmc.torch.container.Containers containers() {
        return containerView;
    }

    @Override
    public net.zaminmc.torch.blockentity.BlockEntities blockEntities() {
        return blockEntityView;
    }

    @Override
    public net.zaminmc.torch.block.ScheduledUpdates scheduledUpdates() {
        return scheduledUpdateView;
    }

    @Override
    public net.zaminmc.torch.block.ScheduledBlockTicks scheduledBlockTicks() {
        return scheduledBlockTickView;
    }

    @Override
    public net.zaminmc.torch.block.RedstoneSignals redstone() {
        return redstoneView;
    }

    @Override
    public net.zaminmc.torch.block.Growth growth() {
        return growthView;
    }

    @Override
    public net.zaminmc.torch.decoration.Decorations decorations() {
        return decorationsView;
    }

    @Override
    public java.util.Collection<net.zaminmc.torch.entity.PrimedTnt> primedTnt() {
        var view = primedTntView;
        if (view == null) {
            return java.util.List.of();
        }
        // The supplier's capture already copied the population list (the
        // system's primedBodies() contract) — a stable point-in-time view.
        return new java.util.ArrayList<>(view.get());
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public java.util.Collection<net.zaminmc.torch.entity.ExperienceOrb> experienceOrbs() {
        var view = orbView;
        if (view == null) {
            return java.util.List.of();
        }
        // The supplier's capture already copied the population (the
        // manager's all() contract) — the same point-in-time shape as
        // entities() above; the raw cast bridges the capture wildcard.
        return (java.util.Collection) view.get();
    }

    @Override
    public void execute(Runnable work) {
        java.util.Objects.requireNonNull(work, "work");
        var executor = ownerExecutor;
        if (executor == null) {
            throw new IllegalStateException(
                    "The world's owner executor is not wired (pre-boot execute)");
        }
        executor.accept(work);
    }

    /**
     * Returns the chunk, generating it if needed. Generation happens on the caller's
     * thread; callers must hold world ownership. Atomic publication (§344): the
     * chunk becomes visible only fully generated.
     */
    public EngineChunk getOrGenerate(ChunkPosition position) {
        requireOwnership("getOrGenerate", position);
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
        requireOwnership("installGenerated", position);
        Objects.requireNonNull(generated, "generated");
        long key = position.packed();
        EngineChunk existing = chunks.get(key);
        if (existing != null) {
            return existing; // the newer authoritative state wins
        }
        // Persisted deltas override freshly generated terrain at the same positions.
        applyPersistedDeltas(generated);
        // The restored inhabited row rides the same install (VIII14): a
        // chunk the previous life never published picks its accrued time
        // up at publication — idempotent, the same shape the overlay carries.
        Long restoredInhabited = pendingInhabited.get(key);
        if (restoredInhabited != null) {
            generated.setInhabitedTime(restoredInhabited);
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

    /**
     * Overlays any persisted deltas over a freshly generated (still-detached)
     * chunk — {@link #installGenerated}'s own first half, factored out for the
     * batched pregen: the wave driver applies the overlay BEFORE the light
     * region's capture, so a pool-computed initial result is derived from the
     * same delta-corrected materials the sequential walk's hook would see
     * (the write is idempotent — installGenerated re-applying the same rows
     * changes nothing).
     */
    public void applyPersistedDeltas(EngineChunk generated) {
        Map<Integer, BlockType> chunkDeltas = deltas.get(generated.position().packed());
        if (chunkDeltas != null) {
            for (Map.Entry<Integer, BlockType> entry : chunkDeltas.entrySet()) {
                int index = entry.getKey();
                generated.setBlock(index & 0xF, (index >> 8) & 0xFF, (index >> 4) & 0xF, entry.getValue());
            }
        }
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
    @Override
    public int blockLightAt(BlockPosition position) {
        EngineChunk chunk = peek(position.chunkPosition());
        return chunk == null ? 0 : chunk.blockLight(position.localX(), position.y(), position.localZ());
    }

    /** @return the skylight level at the position; 15 outside loaded chunks (open sky). */
    @Override
    public int skyLightAt(BlockPosition position) {
        EngineChunk chunk = peek(position.chunkPosition());
        return chunk == null ? 15 : chunk.skyLight(position.localX(), position.y(), position.localZ());
    }

    /**
     * @return the biome's stable name at the column (the API read over the
     * chunk's generation-derived biome row; plains outside loaded chunks —
     * the implicit default). The full 61-row registration table answers
     * every id the anvil import can carry (a "FrozenOcean" column names
     * itself; the old 7-row loop answered the implicit plains).
     */
    @Override
    public String biomeAt(BlockPosition position) {
        return biomeValueAt(position.x(), position.z())
                .name().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * @return the whole registration census as api rows (the VIII14
     * slice-1 exposure beside {@link #biomeAt} — 61 rows, id order; the
     * immutable world-independent table, any thread; every world answers
     * the same census).
     */
    @Override
    public java.util.List<net.zaminmc.torch.block.BiomeData> biomes() {
        return BiomeDataViews.all();
    }

    /**
     * @return the registration row's api view at the column (the VIII14
     * slice-1 exposure beside {@link #biomeAt}) — the SAME resolution
     * {@link #biomeAt} rides ({@link #biomeValueAt}: the chunk's own
     * stored row; the plains row outside loaded chunks, the implicit
     * default's own datums).
     */
    @Override
    public net.zaminmc.torch.block.BiomeData biomeDataAt(BlockPosition position) {
        return BiomeDataViews.of(biomeValueAt(position.x(), position.z()));
    }

    /**
     * @return the biome row at the column (the chunk's own stored row —
     * the reference's {@code world.getBiome(pos)} read the natural spawn
     * cycle's table consult rides): plains outside loaded chunks (the
     * engine's border gate — the reference would force the generation,
     * the engine's own named stand-in from the earlier slices).
     */
    public Biome biomeValueAt(int x, int z) {
        EngineChunk chunk = peek(new net.zaminmc.torch.block.ChunkPosition(
                Math.floorDiv(x, 16), Math.floorDiv(z, 16)));
        if (chunk == null) {
            return Biome.PLAINS;
        }
        Biome biome = Biome.byLegacyId(chunk.biome(x & 15, z & 15));
        return biome == null ? Biome.PLAINS : biome;
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
        // VIII14 — the raw day clock + the inhabited rows ride the same
        // restore: the clock takes the v2 row (a v1 file's migration reads
        // it as the wrapped value — day 0, the historical behavior), and
        // each inhabited row lands both in the pending map (chunks the
        // boot has not generated yet pick it up at install) and on any
        // chunk already published (the restore-before-pregen order keeps
        // this the empty case in practice; the idempotent write is the
        // same shape the delta overlay carries).
        this.rawTimeOfDay = snapshot.rawTimeOfDay();
        for (Map.Entry<Long, Long> row : snapshot.inhabitedTimes().entrySet()) {
            pendingInhabited.put(row.getKey(), row.getValue());
            EngineChunk live = chunks.get(row.getKey());
            if (live != null) {
                live.setInhabitedTime(row.getValue());
            }
        }
    }

    /** Consistent delta view for persistence. Owner-thread only. */
    public WorldDeltaSnapshot snapshotDeltas() {
        requireOwnership("snapshotDeltas");
        Map<Long, Map<Integer, BlockType>> copy = new HashMap<>();
        deltas.forEach((chunkKey, entries) -> copy.put(chunkKey, Map.copyOf(entries)));
        // The inhabited rows: the pending map's never-loaded rows survive
        // (the round trip must not lose them) with every published chunk's
        // live value overlaying its own stale pending row — the chunk is
        // the authoritative holder once installed.
        Map<Long, Long> inhabitedRows = new HashMap<>(pendingInhabited);
        for (Map.Entry<Long, EngineChunk> entry : chunks.entrySet()) {
            long value = entry.getValue().inhabitedTime();
            if (value > 0L) {
                inhabitedRows.put(entry.getKey(), value);
            }
        }
        return new WorldDeltaSnapshot(totalTicks, timeOfDay, rawTimeOfDay,
                Map.copyOf(inhabitedRows), Map.copyOf(copy));
    }

    public int deltaCount() {
        return deltas.values().stream().mapToInt(Map::size).sum();
    }

    public int loadedChunkCount() {
        return chunks.size();
    }

    /**
     * Advances simulation time. Called only by the owning tick context.
     * The total clock always walks; the day clock advances only under
     * the {@code doDaylightCycle} gate (Slice 24a — ServerWorld.tick
     * lines 190-192: {@code setTime(time + 1)} unconditionally,
     * {@code setTimeOfDay(timeOfDay + 1)} only when the rule holds).
     */
    public void tickTime(boolean advanceDaylight) {
        requireOwnership("tickTime");
        totalTicks++;
        if (advanceDaylight) {
            timeOfDay = (timeOfDay + 1) % DAY_LENGTH_TICKS;
            // VIII14 — the RAW day clock walks beside the wrapped one: the
            // reference's WorldData.timeOfDay never wraps (the moon phase
            // and the local difficulty's own time term read the DAY COUNT
            // in its high bits; the wrapped clock the sky cycle reads loses
            // it). doDaylightCycle=false freezes both day clocks together —
            // the reference's own pair.
            rawTimeOfDay++;
        }
    }

    /** Historical day length in ticks (the world's own cycle). */
    public static final long DAY_LENGTH_TICKS = 24_000;

    /**
     * The moon phase sizes (Dimension.MOON_PHASE_TO_SIZE 1:1): phase 0 the
     * full moon, phase 4 the new moon, the waning/waxing pair around them.
     */
    public static final float[] MOON_PHASE_TO_SIZE =
            {1.0F, 0.75F, 0.5F, 0.25F, 0.0F, 0.25F, 0.5F, 0.75F};

    /**
     * The current moon phase 0..7 (Dimension.getMoonPhase 1:1 — the raw day
     * clock's own {@code (time / 24000 % 8 + 8) % 8} walk, the +8 band the
     * negative-clock guard). Phase 0 = full moon.
     */
    public int moonPhase() {
        return (int) (rawTimeOfDay / DAY_LENGTH_TICKS % 8L + 8L) % 8;
    }

    /**
     * The moon's size 1.0..0.0 at the raw day clock (World.getMoonSize 1:1 —
     * the local difficulty's own moon term reads it; the client derives the
     * same phase from the 0x03 day-time field's high bits).
     */
    public float moonSize() {
        return MOON_PHASE_TO_SIZE[moonPhase()];
    }

    /**
     * Moves the day clock (the /time command). Runs on the owning tick context
     * (the chat dispatch routes through the engine's tick queue); the periodic
     * cycle sync and hostile spawning read the same field.
     */
    @Override
    public void setTimeOfDay(long timeOfDay) {
        requireOwnership("setTimeOfDay");
        // The clock wraps on the day length (floorMod keeps the /time add
        // arm's negative sums walking forward, never to a negative clock).
        this.timeOfDay = Math.floorMod(timeOfDay, DAY_LENGTH_TICKS);
        // VIII14 — the RAW clock takes the value verbatim (the reference's
        // own /time write: WorldData.timeOfDay never folds — a /time set
        // past a day boundary moves the moon, the wrapped clock above keeps
        // the sky cycle's own within-day read).
        this.rawTimeOfDay = timeOfDay;
    }

    /**
     * The /time add arm (the reference's own: TimeCommand's addTo arm adds
     * to the RAW clock — {@code data.setTimeOfDay(data.getTimeOfDay() +
     * value)} — the wrapped sky clock derives). Runs on the owning tick
     * context like the set arm.
     */
    public void addTimeOfDay(long delta) {
        requireOwnership("addTimeOfDay");
        this.timeOfDay = Math.floorMod(this.timeOfDay + delta, DAY_LENGTH_TICKS);
        this.rawTimeOfDay += delta;
    }

    /**
     * The inhabited-time credit walk (VIII14 — the reference's ChunkMap
     * batches collapsed to the per-tick credit, the named adaptation):
     * every watched chunk — a chunk inside at least one player's view
     * square — accrues exactly one tick. The reference's
     * {@code updateInhabitedTime} credits {@code world.getTime() -
     * holder.lastUpdateTime} per holder in ~8000-tick batches and at the
     * last watcher's departure: the same union-of-watched-spans law, the
     * per-tick credit one batch fresher (a fresh chunk's first credit
     * never lags 8000 ticks). The caller deduplicates the squares (two
     * watchers share one credit — the holder's span, never per-player).
     * Owner-thread (the tick lane).
     *
     * @param watchedKeys the packed chunk positions inside this tick's
     *                    union of player view squares (the same square the
     *                    tracker sends — the caller's own walk)
     */
    public void accrueInhabitedTime(java.util.Set<Long> watchedKeys) {
        requireOwnership("accrueInhabitedTime");
        for (Long key : watchedKeys) {
            EngineChunk watched = chunks.get(key);
            if (watched != null) {
                watched.setInhabitedTime(watched.inhabitedTime() + 1L);
            }
        }
    }

    /**
     * The local-difficulty consult (the reference's World
     * .getLocalDifficulty 1:1): the chunk's inhabited row + the moon size
     * read ONLY when the chunk is loaded — an unloaded position answers
     * 0/0.0 for both (the reference's own gate), while the raw day clock
     * and the base difficulty always carry (the world-global reads). The
     * spawn dice's multiplier consult rides it.
     *
     * @param at           the position (the mob's own)
     * @param difficultyId the world's difficulty id (0 peaceful .. 3 hard —
     *                     the engine's one shared table)
     */
    public LocalDifficulty localDifficulty(net.zaminmc.torch.block.BlockPosition at,
                                           int difficultyId) {
        EngineChunk chunk = chunks.get(at.chunkPosition().packed());
        if (chunk == null) {
            return new LocalDifficulty(difficultyId, rawTimeOfDay, 0L, 0.0F);
        }
        return new LocalDifficulty(difficultyId, rawTimeOfDay,
                chunk.inhabitedTime(), moonSize());
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

    /**
     * The generator's seed (the lane stream derivation's root — the
     * RandomTickPicks contract reads it once per tick walk).
     */
    public long generatorSeed() {
        return generator.seed();
    }

    /**
     * Attaches the spatial ownership partition (the Phase 4 spatial
     * activation). Called once at boot, before the loop starts; the
     * partition's region domains become the per-position mutation authority
     * alongside the ambient domain (the single tick lane holds the ambient
     * credential — the writes still all pass there, but every foreign
     * context now fails against the position's own region instead of the
     * whole-world domain).
     */
    public void attachRegionPartition(
            net.zaminmc.torch.server.concurrent.RegionPartition regionPartition) {
        this.partition = Objects.requireNonNull(regionPartition, "regionPartition");
    }

    /** @return the attached spatial partition, or null when the world is one domain. */
    public net.zaminmc.torch.server.concurrent.RegionPartition regionPartition() {
        return partition;
    }

    /**
     * The region domain owning the position's chunk (null when no partition
     * is attached). Pure resolution — creates the domain on first touch,
     * identical on every call.
     */
    public net.zaminmc.torch.server.concurrent.OwnershipDomain regionDomainFor(BlockPosition position) {
        net.zaminmc.torch.server.concurrent.RegionPartition attached = partition;
        if (attached == null) {
            return null;
        }
        ChunkPosition chunk = position.chunkPosition();
        return attached.domainFor(chunk.x(), chunk.z());
    }

    /**
     * The position-aware mutation assertion (the Phase 4 spatial activation):
     * resolves the owning region's domain and passes when the caller holds
     * it, or — on the single tick lane — holds the ambient simulation domain
     * (the world authority). Every other context fails through the region's
     * own enforcement (STRICT throws the diagnosable violation naming the
     * region; DIAGNOSTIC logs once and proceeds). Without a partition the
     * ambient check stands exactly as before.
     */
    private void requireOwnership(String operation, BlockPosition position) {
        net.zaminmc.torch.server.concurrent.RegionPartition attached = partition;
        if (attached == null) {
            requireOwnership(operation);
            return;
        }
        ChunkPosition chunk = position.chunkPosition();
        requireOwnershipInRegion(operation, attached.domainFor(chunk.x(), chunk.z()));
    }

    /** The chunk-box variant: a chunk belongs to exactly one region. */
    private void requireOwnership(String operation, ChunkPosition chunk) {
        net.zaminmc.torch.server.concurrent.RegionPartition attached = partition;
        if (attached == null) {
            requireOwnership(operation);
            return;
        }
        requireOwnershipInRegion(operation, attached.domainFor(chunk.x(), chunk.z()));
    }

    private void requireOwnershipInRegion(String operation,
                                          net.zaminmc.torch.server.concurrent.OwnershipDomain region) {
        if (region.inContext()) {
            return; // the region's own writer (the parallel lane's credential)
        }
        net.zaminmc.torch.server.concurrent.OwnershipDomain ambient = domain;
        if (ambient != null) {
            if (ambient.inContext()) {
                return; // the single tick lane's authority (ADR-13's global credential)
            }
            // An ambient domain exists but this context is not it: the
            // region's enforcement decides (STRICT throws the violation
            // naming THIS region — the diagnosable failure; DIAGNOSTIC logs
            // once per operation and proceeds).
            region.checkInContext(operation);
            return;
        }
        // No ambient domain attached (the boot-lane construction window and
        // the unit fixtures): the historical owner-thread check stands —
        // the partition refines the assertion surface, never who may write
        // the un-domain-ed world.
        requireOwnership(operation);
    }

    /**
     * The boundary scope (the design §10.3's "larger exclusive ownership
     * scope" — ADR-9): runs the body holding EVERY region domain the box
     * (inclusive block-coordinate corners) touches, acquired in ascending
     * packed-region-id order and released in reverse — one global
     * acquisition order means concurrent scopes can never deadlock AB-BA,
     * and a region held by a scope is exclusive (another writer's enter
     * fails until the scope releases). Mechanics whose operations span a
     * region seam and need same-tick atomicity (the piston's move writes,
     * the explosion's blast walk) execute inside this scope so their
     * foreign-region writes are legal in BOTH execution lanes.
     *
     * <p>Without a partition (or with the one-region default) the body runs
     * directly — the scope is a no-op on the unsplit world.</p>
     */
    public void executeInRegions(BlockPosition cornerA, BlockPosition cornerB, Runnable body) {
        Objects.requireNonNull(body, "body");
        net.zaminmc.torch.server.concurrent.RegionPartition attached = partition;
        if (attached == null) {
            body.run();
            return;
        }
        ChunkPosition from = cornerA.chunkPosition();
        ChunkPosition to = cornerB.chunkPosition();
        java.util.List<net.zaminmc.torch.server.concurrent.OwnershipDomain> entered =
                attached.domainsBetween(from.x(), from.z(), to.x(), to.z());
        java.util.List<net.zaminmc.torch.server.concurrent.OwnershipDomain> acquired =
                new java.util.ArrayList<>(entered.size());
        try {
            for (net.zaminmc.torch.server.concurrent.OwnershipDomain region : entered) {
                region.enter();
                acquired.add(region);
            }
            body.run();
        } finally {
            for (int i = acquired.size() - 1; i >= 0; i--) {
                acquired.get(i).exit();
            }
        }
    }

    /**
     * The per-region boundary walk (the §7 protocol's target-side apply):
     * enters every live region domain in the deterministic id order, drains
     * the router's inbound intents for that region at its own boundary, and
     * releases — an intent's action validates and applies in the target
     * region's context, admission order, exactly-once. Called at the tick
     * head (before the gameplay systems) so applied intents are visible to
     * the same tick's authoritative work.
     *
     * @return how many intents were applied across all regions
     */
    public int drainRegionIntents(
            net.zaminmc.torch.server.concurrent.CrossOwnerRouter router) {
        net.zaminmc.torch.server.concurrent.RegionPartition attached = partition;
        if (attached == null) {
            return 0;
        }
        int applied = 0;
        // domains() walks in the stable name order (deterministic on every
        // JVM — design invariant 9); the walk runs single-threaded at the
        // tick head, so the deterministic order is all the guarantee needed.
        for (net.zaminmc.torch.server.concurrent.OwnershipDomain region : attached.domains()) {
            try {
                region.enter();
            } catch (net.zaminmc.torch.server.concurrent.OwnershipViolationException held) {
                // A region held by another writer is skipped this tick —
                // its intents stay queued (ordered delivery, never
                // dropped), and one held region never stalls the other
                // regions' drains or the tick itself.
                continue;
            }
            try {
                applied += router.drainIntents(region);
            } finally {
                region.exit();
            }
        }
        return applied;
    }
}
