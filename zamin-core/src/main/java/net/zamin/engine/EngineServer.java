package net.zamin.engine;

import net.zamin.api.Player;
import net.zamin.api.PlayerState;
import net.zamin.api.Position;
import net.zamin.api.Rotation;
import net.zamin.api.Server;
import net.zamin.api.ServerState;
import net.zamin.api.World;
import net.zamin.engine.config.EngineConfig;
import net.zamin.engine.net.ClientLink;
import net.zamin.engine.net.EngineBridge;
import net.zamin.engine.player.PlayerRegistry;
import net.zamin.engine.player.PlayerSession;
import net.zamin.engine.world.EngineChunk;
import net.zamin.engine.world.EngineWorld;
import net.zamin.engine.world.DeltaWorldStorage;
import net.zamin.engine.world.FlatWorldGenerator;
import net.zamin.engine.world.WorldStorage;
import net.zamin.engine.world.WorldDeltaSnapshot;
import net.zamin.engine.block.BlockRegistryBuilder;
import net.zamin.engine.block.BuiltinBlocks;
import net.zamin.engine.block.BlockRegistryBuilder.FrozenBlockRegistry;
import net.zamin.engine.interaction.BlockInteractionService;
import net.zamin.engine.world.WorldChangeListener;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * The engine server: owns the lifecycle state machine, the world, the player
 * registry and the bridge through which protocol adapters connect clients.
 *
 * <p>Ownership model (Slice #1): world state is owned by the tick thread;
 * identity-critical session operations (join/reject/disconnect) run on the
 * caller's network event loop and are guarded by the registry's atomicity plus
 * per-channel packet ordering guarantees of the transport. Player position is
 * per-player state written by the owning channel loop only, published volatile.
 * A general command queue arrives with cross-entity gameplay.</p>
 */
public final class EngineServer implements Server, EngineBridge {

    private static final Logger LOGGER = Logger.getLogger(EngineServer.class.getName());
    private static final Pattern VALID_NAME = Pattern.compile("^[A-Za-z0-9_]{1,16}$");
    private static final long BOOT_TIMEOUT_MS = 30_000;

    private final EngineConfig config;
    private final PlayerRegistry players = new PlayerRegistry();
    private final AtomicReference<ServerState> state = new AtomicReference<>(ServerState.NEW);
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);

    private FrozenBlockRegistry blockRegistry;
    private EngineWorld world;
    private EngineTicker ticker;
    private BlockInteractionService blockInteraction;
    private WorldStorage worldStorage;
    private final java.util.List<WorldChangeListener> worldListeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    public EngineServer(EngineConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    // ------------------------------------------------------------------ lifecycle

    public EngineConfig config() {
        return config;
    }

    /**
     * Boots the engine. The adapter is started by the launcher after this returns,
     * once the world is guaranteed to exist.
     */
    public void start() throws InterruptedException {
        if (!state.compareAndSet(ServerState.NEW, ServerState.INITIALIZING)) {
            throw new IllegalStateException("Server already started or starting (state " + state.get() + ")");
        }
        LOGGER.info(() -> "ZaminTorch engine starting (world=" + config.worldName()
                + ", port=" + config.port() + ", view-distance=" + config.viewDistance() + ")");

        // Registry freeze: built-ins registered during boot preparation must be frozen
        // before any world exists (registry lifecycle: create -> register -> freeze).
        if (blockRegistry == null) {
            blockRegistry = BuiltinBlocks.registerAll(new BlockRegistryBuilder()).freeze();
        }

        state.set(ServerState.STARTING);
        CountDownLatch worldReady = new CountDownLatch(1);
        AtomicReference<Throwable> bootFailure = new AtomicReference<>();

        Thread boot = new Thread(() -> {
            try {
                ticker = new EngineTicker(config.tickRateHz());
                // Ticker thread constructs the world so it is the owner from the start.
                Thread owner = Thread.currentThread();
                FlatWorldGenerator generator = new FlatWorldGenerator(blockRegistry, 4);
                world = new EngineWorld(config.worldName(), blockRegistry, generator, owner);
                // Persistence: load saved deltas before any chunk generates so the
                // spawn area is already the survived world (§407 restart proof).
                worldStorage = new DeltaWorldStorage(
                        java.nio.file.Path.of(config.dataDir(), "worlds", config.worldName(), "zamin-delta.bin"),
                        identifier -> blockRegistry.require(identifier));
                worldStorage.load().ifPresent(world::applyDeltas);
                pregenerateSpawnArea(world);
                ticker.attachWorld(world);
                blockInteraction = new BlockInteractionService(world, ticker, this::publishBlockChange);
                worldReady.countDown();
                ticker.runLoop(); // blocks until stop
            } catch (Throwable t) {
                bootFailure.set(t);
                worldReady.countDown();
            }
        }, "zamin-boot");
        boot.start();

        if (!worldReady.await(BOOT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            state.set(ServerState.FAILED);
            throw new IllegalStateException("World boot timed out");
        }
        if (bootFailure.get() != null) {
            state.set(ServerState.FAILED);
            throw new IllegalStateException("World boot failed", bootFailure.get());
        }
        state.set(ServerState.RUNNING);
        LOGGER.info(() -> "World '" + world.name() + "' ready (spawn "
                + world.spawnPosition() + ", chunks=" + world.loadedChunkCount() + ")");
    }

    private void pregenerateSpawnArea(EngineWorld engineWorld) {
        // Pregenerate the full initial view: the join sequence can then send every
        // chunk synchronously, so a client never waits past its position packet for
        // spawn-adjacent chunks. Larger play areas load on demand via requestChunkLoad.
        Position spawn = engineWorld.spawnPosition();
        var center = spawn.toBlockPosition().chunkPosition();
        int radius = config.viewDistance();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                engineWorld.getOrGenerate(new net.zamin.api.ChunkPosition(center.x() + dx, center.z() + dz));
            }
        }
    }

    @Override
    public void awaitShutdown() {
        try {
            shutdownLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Initiates graceful shutdown: stop intake, disconnect players, stop the
     * simulation, release resources. Idempotent; safe from any thread.
     */
    public void shutdown(Runnable adapterShutdown) {
        ServerState previous = state.getAndUpdate(s ->
                s == ServerState.RUNNING || s == ServerState.STARTING
                        ? ServerState.STOPPING
                        : (s == ServerState.STOPPING ? ServerState.STOPPING : s));
        if (previous != ServerState.RUNNING && previous != ServerState.STARTING
                && previous != ServerState.STOPPING) {
            return; // never ran, already stopped, or failed: nothing to stop
        }
        LOGGER.info("Server stopping");

        // 1. stop accepting work / disconnect clients (adapter first: it feeds us joins)
        if (adapterShutdown != null) {
            try {
                adapterShutdown.run();
            } catch (RuntimeException e) {
                LOGGER.warning("Protocol adapter shutdown problem: " + e);
            }
        }

        // 2. engine-side player cleanup
        for (PlayerSession session : players.all()) {
            try {
                session.markDisconnecting();
                players.unregister(session);
                session.link().kick("Server closed");
            } catch (RuntimeException e) {
                LOGGER.warning("Player cleanup problem for " + session.name() + ": " + e);
            }
        }

        // 3. flush world state while the tick thread is still alive (consistent snapshot)
        if (ticker != null && world != null) {
            try {
                saveAllNow();
            } catch (RuntimeException e) {
                LOGGER.log(java.util.logging.Level.SEVERE, "World save during shutdown failed", e);
            }
        }

        // 4. stop simulation
        if (ticker != null) {
            ticker.stop();
            try {
                ticker.awaitStop(5_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        state.set(ServerState.STOPPED);
        shutdownLatch.countDown();
        LOGGER.info("Server stopped");
    }

    // ------------------------------------------------------------------ Server API

    @Override
    public ServerState state() {
        return state.get();
    }

    @Override
    public Collection<World> worlds() {
        return world == null ? java.util.List.of() : java.util.List.of((World) world);
    }

    @Override
    public Collection<Player> players() {
        return players.publicView();
    }

    public EngineWorld world() {
        return world;
    }

    public FrozenBlockRegistry blockRegistry() {
        return blockRegistry;
    }

    public PlayerRegistry playerRegistry() {
        return players;
    }

    /** The semantic entry point for player-driven block changes. */
    public BlockInteractionService blockInteraction() {
        return blockInteraction;
    }

    /**
     * Persists world state on the owning tick thread (consistent snapshot) and
     * waits for durability. Safe from any thread; no-op if the world is absent.
     */
    public void saveAllNow() {
        if (world == null || ticker == null) {
            return;
        }
        java.util.concurrent.CountDownLatch saved = new java.util.concurrent.CountDownLatch(1);
        ticker.submit(() -> {
            try {
                worldStorage.save(world.snapshotDeltas());
            } finally {
                saved.countDown();
            }
        });
        try {
            if (!saved.await(10, java.util.concurrent.TimeUnit.SECONDS)) {
                LOGGER.warning("World save did not complete within 10s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Registers an internal world observer (e.g. the protocol adapter's sync). */
    public void addWorldListener(WorldChangeListener listener) {
        worldListeners.add(listener);
    }

    private void publishBlockChange(BlockInteractionService.BlockChange change) {
        for (WorldChangeListener listener : worldListeners) {
            listener.onBlockChanged(world, change.position(), change.newType());
        }
    }

    // ------------------------------------------------------------------ EngineBridge

    @Override
    public JoinResult joinRequest(ClientLink link, String username, UUID offlineUuid) {
        Objects.requireNonNull(link, "link");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(offlineUuid, "offlineUuid");

        if (state.get() != ServerState.RUNNING) {
            return new EngineBridge.Rejected("Server is not accepting players");
        }
        if (!VALID_NAME.matcher(username).matches()) {
            return new EngineBridge.Rejected("Invalid username");
        }
        synchronized (players) {
            if (players.size() >= config.maxPlayers()) {
                return new EngineBridge.Rejected("Server is full");
            }
            if (players.isNameTaken(username)) {
                return new EngineBridge.Rejected("You are already connected");
            }
            Optional<PlayerSession> existing = players.byUuid(offlineUuid);
            if (existing.isPresent()) {
                return new EngineBridge.Rejected("You are already connected");
            }
            PlayerSession session = new PlayerSession(offlineUuid, username, link);
            players.register(session);
            session.authenticate();
            session.beginJoin(world, world.spawnPosition());
            LOGGER.info(() -> "Player joined: " + username + " (" + offlineUuid + ")");
            return new EngineBridge.Accepted(session);
        }
    }

    @Override
    public void joinCompleted(PlayerSession session) {
        session.markPlaying();
        LOGGER.info(() -> "Player in play state: " + session.name());
    }

    @Override
    public void movementProposal(PlayerSession session, Position position, Rotation rotation, boolean onGround) {
        // Per-channel ordering (transport guarantee) makes this safe for slice 1.
        session.applyMovement(position, rotation, onGround);
    }

    @Override
    public void clientDisconnected(PlayerSession session, String reason) {
        if (session.state() == PlayerState.DISCONNECTED) {
            return;
        }
        session.markDisconnecting();
        session.markDisconnected();
        players.unregister(session);
        LOGGER.info(() -> "Player disconnected: " + session.name() + " (" + reason + ")");
    }

    /** Checks whether the chunk containing the position is available read-only. */
    public EngineChunk peekChunk(net.zamin.api.ChunkPosition position) {
        return world.peek(position);
    }

    /**
     * Ensures a chunk is loaded without violating world ownership: generation is
     * scheduled onto the world's owner thread and the callback runs there.
     * Safe to call from any thread. If the chunk already exists, the callback
     * runs immediately on the caller's thread.
     */
    public void requestChunkLoad(net.zamin.api.ChunkPosition position,
                                 java.util.function.Consumer<EngineChunk> onLoaded) {
        Objects.requireNonNull(onLoaded, "onLoaded");
        EngineChunk existing = world.peek(position);
        if (existing != null) {
            onLoaded.accept(existing);
            return;
        }
        ticker.submit(() -> {
            EngineChunk chunk = world.getOrGenerate(position);
            onLoaded.accept(chunk);
        });
    }
}
