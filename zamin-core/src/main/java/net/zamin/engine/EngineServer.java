package net.zamin.engine;

import net.zamin.api.Player;
import net.zamin.api.PlayerState;
import net.zamin.api.Position;
import net.zamin.api.ItemStack;
import net.zamin.api.Rotation;
import net.zamin.api.Server;
import net.zamin.api.ServerState;
import net.zamin.api.World;
import net.zamin.api.ChunkPosition;
import net.zamin.api.BlockPosition;
import net.zamin.engine.block.BlockUpdateSystem;
import net.zamin.engine.block.RandomTickSystem;
import net.zamin.engine.chest.ChestBlockEntity;
import net.zamin.engine.chest.ChestDataStore;
import net.zamin.engine.chest.ChestManager;
import net.zamin.engine.config.EngineConfig;
import net.zamin.engine.crafting.CraftingService;
import net.zamin.engine.entity.FallingBlockEntityManager;
import net.zamin.engine.entity.FallingBlockEntity;
import net.zamin.engine.entity.ItemEntity;
import net.zamin.engine.entity.ItemEntityManager;
import net.zamin.engine.entity.MobDataStore;
import net.zamin.engine.entity.MobEntity;
import net.zamin.engine.entity.MobManager;
import net.zamin.engine.entity.MobType;
import net.zamin.engine.furnace.FurnaceBlockEntity;
import net.zamin.engine.furnace.FurnaceDataStore;
import net.zamin.engine.furnace.FurnaceManager;
import net.zamin.engine.item.Foods;
import net.zamin.engine.interaction.DropService;
import net.zamin.engine.net.ClientLink;
import net.zamin.engine.net.EngineBridge;
import net.zamin.engine.player.PlayerDataStore;
import net.zamin.engine.player.PlayerRegistry;
import net.zamin.engine.player.PlayerSession;
import net.zamin.engine.player.PlayerSnapshot;
import net.zamin.engine.world.EngineChunk;
import net.zamin.engine.world.EngineWorld;
import net.zamin.engine.world.DeltaWorldStorage;
import net.zamin.engine.world.FlatWorldGenerator;
import net.zamin.engine.world.WorldStorage;
import net.zamin.engine.world.WorldDeltaSnapshot;
import net.zamin.engine.block.BlockRegistryBuilder;
import net.zamin.engine.block.BuiltinBlocks;
import net.zamin.engine.chat.ChatListener;
import net.zamin.engine.chat.ChatService;
import net.zamin.engine.chat.CommandService;
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

    /** Player-window wire slots for the crafting area (window 0, protocol 47). */
    private static final int WIRE_SLOT_RESULT = 0;
    private static final int WIRE_SLOT_CRAFT_FIRST = 1;
    private static final int WIRE_SLOT_CRAFT_LAST = 4;
    /** Crafting-table container window wire slots (protocol 47, 10-slot GUI). */
    private static final int TABLE_WIRE_SLOT_RESULT = 0;
    private static final int TABLE_WIRE_SLOT_GRID_FIRST = 1;
    private static final int TABLE_WIRE_SLOT_GRID_LAST = 9;
    private static final int TABLE_WIRE_SLOT_HOTBAR_FIRST = 37;
    private static final int TABLE_WIRE_SLOT_HOTBAR_LAST = 45;
    /** Furnace container window wire slots (protocol 47, community-verified GUI). */
    private static final int FURNACE_WIRE_SLOT_LAST = 2; // 0 input, 1 fuel, 2 output
    private static final int FURNACE_WIRE_SLOT_MAIN_FIRST = 3;
    private static final int FURNACE_WIRE_SLOT_MAIN_LAST = 29;
    private static final int FURNACE_WIRE_SLOT_HOTBAR_FIRST = 30;
    private static final int FURNACE_WIRE_SLOT_HOTBAR_LAST = 38;
    /** Chest container window wire slots (protocol 47, community-verified GUI). */
    private static final int CHEST_WIRE_SLOT_LAST = 26; // 3x9 chest slots
    private static final int CHEST_WIRE_SLOT_MAIN_FIRST = 27;
    private static final int CHEST_WIRE_SLOT_MAIN_LAST = 53;
    private static final int CHEST_WIRE_SLOT_HOTBAR_FIRST = 54;
    private static final int CHEST_WIRE_SLOT_HOTBAR_LAST = 62;
    /** Craft-all guard: even a 3x3 grid cannot chain more crafts than this. */
    private static final int CRAFT_ALL_LIMIT = 64;
    /** Wire window ids: 0 = player inventory; containers get ids from this counter (u8). */
    private static final int WIRE_WINDOW_PLAYER = 0;
    private static final int FIRST_CONTAINER_WINDOW_ID = 1;
    private static final int LAST_CONTAINER_WINDOW_ID = 255;

    /** Tick-thread confined; ids are handed out on the simulation context only. */
    private int nextContainerWindowId = FIRST_CONTAINER_WINDOW_ID;

    private final EngineConfig config;
    private final PlayerRegistry players = new PlayerRegistry();
    private final AtomicReference<ServerState> state = new AtomicReference<>(ServerState.NEW);
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);

    private FrozenBlockRegistry blockRegistry;
    private EngineWorld world;
    private EngineTicker ticker;
    private BlockInteractionService blockInteraction;
    private ChatService chatService;
    private final CraftingService crafting = CraftingService.builtin();
    private WorldStorage worldStorage;
    private PlayerDataStore playerStore;
    private FurnaceManager furnaceManager;
    private FurnaceDataStore furnaceStore;
    private ChestManager chestManager;
    private ChestDataStore chestStore;
    private volatile ItemEntityManager itemEntities;
    private volatile MobManager mobManager;
    private volatile FallingBlockEntityManager fallingEntities;
    private BlockUpdateSystem blockUpdateSystem;
    private RandomTickSystem randomTicks;
    private net.zamin.engine.world.light.LightEngine lightEngine;
    private MobDataStore mobStore;
    private final java.util.List<ChatListener> chatListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.List<ItemEntityManager.Listener> itemListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.List<MobManager.Listener> mobListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.List<FallingBlockEntityManager.Listener> fallingListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.List<InventoryListener> inventoryListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.List<FurnaceViewListener> furnaceViewListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.List<SurvivalListener> survivalListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.List<TimeListener> timeListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.List<RelightListener> relightListeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Entity ids for engine-global entities (items); player wire ids stay adapter-local. */
    private static final int ENTITY_ID_BASE = 100_000;
    /** Mob ids live in a disjoint band above the item ids (one id space, no collision). */
    private static final int MOB_ID_BASE = ENTITY_ID_BASE + 1_000_000;
    /** Falling blocks get their own band above the mobs. */
    private static final int FALLING_ID_BASE = MOB_ID_BASE + 1_000_000;

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
                // Player persistence: one ZPD file per identity under <dataDir>/players.
                playerStore = new PlayerDataStore(
                        java.nio.file.Path.of(config.dataDir(), "players"));
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
                // Light (§475/§476): derived world state, recomputed on every
                // committed change and on chunk generation. Registered FIRST
                // (before spawn pregeneration) so every generated chunk — boot
                // area included — publishes already lit (§344: publication
                // stays atomic), and as the first change listener so its
                // relight queue batches whole cascades per tick.
                lightEngine = new net.zamin.engine.world.light.LightEngine(world);
                world.addChangeListener(lightEngine);
                world.addChunkLoadListener(lightEngine);
                pregenerateSpawnArea(world);
                ticker.attachWorld(world);
                // Item entities and drops: simulation-owned systems on the world owner.
                ItemEntityManager itemEntities = new ItemEntityManager(
                        (x, y, z) -> !world.getBlock(blockAt(x, y, z)).equals(world.airType()),
                        new java.util.Random(),
                        ENTITY_ID_BASE);
                this.itemEntities = itemEntities;
                itemEntities.addListener(new ItemEventDispatch());
                // Furnace block entities: world-state simulation, ZFD persistence.
                furnaceManager = new FurnaceManager();
                furnaceStore = new FurnaceDataStore(java.nio.file.Path.of(
                        config.dataDir(), "worlds", config.worldName(), "furnaces.bin"));
                furnaceManager.restoreAll(furnaceStore.load());
                // Chest block entities: world-state containers, ZCD persistence.
                chestManager = new ChestManager();
                chestStore = new ChestDataStore(java.nio.file.Path.of(
                        config.dataDir(), "worlds", config.worldName(), "chests.bin"));
                chestManager.restoreAll(chestStore.load());
                // Living mobs: simulation-owned population, loot flows into items.
                MobManager mobs = new MobManager(
                        new MobWorldQuery(),
                        new java.util.Random(),
                        (position, stack) -> itemEntities.spawnDropAtBlock(position, stack,
                                ItemEntity.PICKUP_DELAY_DROP_TICKS),
                        MOB_ID_BASE,
                        (x, z) -> surfaceY(x, z));
                this.mobManager = mobs;
                mobs.addListener(new MobEventDispatch());
                // Mob persistence (ZMD v1): the population survives restarts; the
                // boot packs roll only for a fresh world (no restored mobs) — the
                // maintainer keeps a restored world topped up while players play.
                mobStore = new MobDataStore(java.nio.file.Path.of(
                        config.dataDir(), "worlds", config.worldName(), "mobs.bin"));
                boolean mobsRestored = mobs.restoreAll(mobStore.load());
                if (!mobsRestored) {
                    mobs.populateInitial(world.spawnPosition());
                }
                // Falling blocks (§470): the block→entity→block transition for
                // gravity blocks; occupied landings drop as items.
                FallingBlockEntityManager falling = new FallingBlockEntityManager(
                        (x, y, z) -> !world.getBlock(blockAt(x, y, z)).equals(world.airType()),
                        world,
                        (position, stack) -> itemEntities.spawnDropAtBlock(
                                new Position(position.x(), position.y(), position.z()), stack,
                                ItemEntity.PICKUP_DELAY_DROP_TICKS),
                        new java.util.Random(),
                        FALLING_ID_BASE);
                this.fallingEntities = falling;
                falling.addListener(new FallingEventDispatch());
                // Scheduled block updates (§466): neighbor notifications drive the
                // gravity/torch/grass rules. Registered as a world listener before
                // the adapter, so engine-side rules observe every commit first.
                blockUpdateSystem = new BlockUpdateSystem(world, itemEntities, falling);
                // The world itself dispatches every committed change (§208): the
                // neighbor-update system observes player- AND engine-driven changes.
                world.addChangeListener(blockUpdateSystem);
                // Random ticks (§471 pattern): grass growth and decay.
                randomTicks = new RandomTickSystem(world, new java.util.Random());
                ticker.setTickHandler(() -> {
                    blockUpdateSystem.tick(); // §466: scheduled updates (falls start here)
                    falling.tick();           // §470: falling physics + landings
                    furnaceManager.tick(world, itemEntities);
                    chestManager.tick(world);
                    itemEntities.tick(players.all());
                    mobs.tick(players.all(), world.timeOfDay());
                    randomTicks.tick(players.all()); // §471: grass growth/decay
                    tickFurnaceViewers();
                    tickPlayerBodies();
                    // Relight transport (§475): protocol 47 has no light-only
                    // packet, so every chunk column the light touched this tick
                    // re-sends once, deduplicated across the whole cascade.
                    lightEngine.flushRelight(this::publishChunkRelit);
                    if (world.totalTicks() % 100 == 0) {
                        publishTimeChanged(); // smooth day cycle on every client
                    }
                });
                blockInteraction = new BlockInteractionService(world, ticker,
                        change -> world.republish(change.position()), // the resync path (§441)
                        config.gamemode(), new DropService(new java.util.Random()), itemEntities,
                        type -> blockRegistry.lookup(type.identifier()),
                        this::publishInventoryChanged);
                // A survival-broken furnace spills its slots, and a survival-
                // broken chest spills its 27, into the world first.
                blockInteraction.setBlockBrokenListener(position -> {
                    furnaceManager.onBlockBroken(position, itemEntities);
                    chestManager.onBlockBroken(position, itemEntities);
                });
                CommandService commands = new CommandService();
                registerBuiltinCommands(commands);
                chatService = new ChatService(ticker, commands, this::publishChat);
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

        // 2. engine-side player cleanup (each leaving player's state is persisted
        //    so a restart continues their survival exactly where it stopped)
        for (PlayerSession session : players.all()) {
            try {
                session.markDisconnecting();
                persistPlayerNow(session);
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

    /** The semantic chat entry point: validation, commands, audience. */
    public ChatService chatService() {
        return chatService;
    }

    /** The simulation-owned item entity manager (present once the world is up). */
    public ItemEntityManager itemEntities() {
        return itemEntities;
    }

    /** Registers an internal item-entity observer (e.g. the protocol adapter's sync). */
    public void addItemListener(ItemEntityManager.Listener listener) {
        itemListeners.add(listener);
    }

    /** The simulation-owned mob manager (present once the world is up). */
    public MobManager mobs() {
        return mobManager;
    }

    /** Registers an internal mob observer (e.g. the protocol adapter's sync). */
    public void addMobListener(MobManager.Listener listener) {
        mobListeners.add(listener);
    }

    /** The simulation-owned falling-block system (present once the world is up). */
    public FallingBlockEntityManager fallingEntities() {
        return fallingEntities;
    }

    /** Registers an internal falling-block observer (e.g. the protocol adapter's sync). */
    public void addFallingListener(FallingBlockEntityManager.Listener listener) {
        fallingListeners.add(listener);
    }

    /** The scheduled block-update system (exposed for behavioral tests). */
    public BlockUpdateSystem blockUpdates() {
        return blockUpdateSystem;
    }

    /** A world-time change (the /time command, the periodic cycle sync). */
    public interface TimeListener {
        void onTimeChanged(long totalTicks, long timeOfDay);
    }

    /**
     * A chunk column's light changed this tick (§475): the wire transport is a
     * chunk re-send (protocol 47 has no light-only packet), deduplicated per
     * tick by the light engine's queue. Positions are chunk columns.
     */
    public interface RelightListener {
        void onChunkRelit(ChunkPosition position);
    }

    /** Registers an internal time observer (e.g. the protocol adapter's sync). */
    public void addTimeListener(TimeListener listener) {
        timeListeners.add(listener);
    }

    /** Registers the chunk-relight observer (the protocol adapter's resend path). */
    public void addRelightListener(RelightListener listener) {
        relightListeners.add(listener);
    }

    private void publishChunkRelit(ChunkPosition position) {
        for (RelightListener listener : relightListeners) {
            listener.onChunkRelit(position);
        }
    }

    private void publishTimeChanged() {
        for (TimeListener listener : timeListeners) {
            listener.onTimeChanged(world.totalTicks(), world.timeOfDay());
        }
    }

    /** An inventory content change that observers must re-sync to the client. */
    public interface InventoryListener {
        void onInventoryChanged(PlayerSession player);
    }

    /** Registers an internal inventory observer (e.g. the protocol adapter's sync). */
    public void addInventoryListener(InventoryListener listener) {
        inventoryListeners.add(listener);
    }

    /** A per-tick furnace-window view: the adapter diffs serials and syncs the wire. */
    public interface FurnaceViewListener {
        void onFurnaceViewTick(PlayerSession viewer, net.zamin.api.BlockPosition position,
                               FurnaceBlockEntity furnace);
    }

    /** Registers an internal furnace-view observer (e.g. the protocol adapter's sync). */
    public void addFurnaceViewListener(FurnaceViewListener listener) {
        furnaceViewListeners.add(listener);
    }

    /** Fans per-tick furnace-window views out to the open viewer(s). Tick-thread context. */
    private void tickFurnaceViewers() {
        for (PlayerSession session : players.all()) {
            if (session.openContainerKind() != PlayerSession.ContainerKind.FURNACE
                    || session.openContainerWindowId() < 0) {
                continue;
            }
            var position = session.openContainerPosition();
            FurnaceBlockEntity furnace = position == null ? null : furnaceManager.peek(position);
            if (furnace == null) {
                continue;
            }
            for (FurnaceViewListener listener : furnaceViewListeners) {
                listener.onFurnaceViewTick(session, position, furnace);
            }
        }
    }

    /** The simulation-owned furnace block entities (present once the world is up). */
    public FurnaceManager furnaces() {
        return furnaceManager;
    }

    /** The simulation-owned chest block entities (present once the world is up). */
    public ChestManager chests() {
        return chestManager;
    }

    /** Observers of the survival body (health sync, death, respawn anchors). */
    public interface SurvivalListener {
        /** A body value changed (health, food, saturation) — re-sync the client. */
        void onBodyChanged(PlayerSession player);

        /** The player died: the adapter tells the client (combat event). */
        void onDied(PlayerSession player);

        /** The player respawned at spawn: the adapter re-anchors the wire. */
        void onRespawned(PlayerSession player, net.zamin.api.Position spawn);

        /** The player took a melee hit: the hurt flash rides the entity status. */
        void onPlayerHurt(PlayerSession player);

        /** The player was knocked back: the victim's client simulates the impulse. */
        void onKnockback(PlayerSession player, double vx, double vy, double vz);
    }

    /** Registers an internal survival observer (e.g. the protocol adapter's sync). */
    public void addSurvivalListener(SurvivalListener listener) {
        survivalListeners.add(listener);
    }

    private void publishBodyChanged(PlayerSession player) {
        for (SurvivalListener listener : survivalListeners) {
            listener.onBodyChanged(player);
        }
    }

    private void publishDied(PlayerSession player) {
        for (SurvivalListener listener : survivalListeners) {
            listener.onDied(player);
        }
    }

    private void publishRespawned(PlayerSession player) {
        for (SurvivalListener listener : survivalListeners) {
            listener.onRespawned(player, world.spawnPosition());
        }
    }

    // ------------------------------------------------------------------ survival body

    /**
     * The per-tick survival body simulation for every playing player: the
     * server-side eat timer, the historical food economy (exhaustion points
     * drain saturation then hunger; regen at food &gt;= 18 costs exhaustion;
     * starvation on easy floors at 10 hearts) and landing fall damage.
     * Tick-thread context.
     */
    private void tickPlayerBodies() {
        for (PlayerSession session : players.all()) {
            if (session.state() != PlayerState.PLAYING || session.dead()) {
                continue;
            }
            session.tickHurtInvulnerability();
            tickEating(session);
            tickFoodEconomy(session);
            tickLanding(session);
        }
    }

    /** The 32-tick server-side eat timer: completion consumes and nourishes. */
    private void tickEating(PlayerSession session) {
        if (!session.eating()) {
            return;
        }
        var held = session.inventory().held();
        if (held.isEmpty() || Foods.nutritionOf(held.type()).isEmpty()) {
            session.cancelEating(); // the held item changed mid-eat
            return;
        }
        if (session.advanceEating() < Foods.EAT_TICKS) {
            return;
        }
        session.cancelEating();
        var nutrition = Foods.nutritionOf(held.type()).orElseThrow();
        if (session.food() >= net.zamin.engine.player.PlayerSession.MAX_FOOD) {
            return; // already full (the start check cannot fully guard the window)
        }
        session.inventory().consumeHeld(1);
        int newFood = Math.min(net.zamin.engine.player.PlayerSession.MAX_FOOD,
                session.food() + nutrition.foodPoints());
        float newSaturation = Math.min(newFood,
                session.saturation() + nutrition.saturation());
        session.setBody(session.health(), newFood, newSaturation);
        publishInventoryChanged(session);
        publishBodyChanged(session);
    }

    /** The historical 1.8 FoodStats loop on easy difficulty. */
    private void tickFoodEconomy(PlayerSession session) {
        if (session.exhaustion() >= PlayerSession.EXHAUSTION_COST) {
            session.setExhaustion(session.exhaustion() - PlayerSession.EXHAUSTION_COST);
            if (session.saturation() > 0) {
                session.setBody(session.health(), session.food(),
                        Math.max(0.0f, session.saturation() - 1));
            } else if (session.food() > 0) {
                session.setBody(session.health(), session.food() - 1, session.saturation());
            }
            publishBodyChanged(session);
        }
        if (session.food() >= 18 && session.health() < PlayerSession.MAX_HEALTH) {
            session.advanceBodyTimer();
            if (session.bodyTimer() >= PlayerSession.BODY_TIMER_PERIOD) {
                session.resetBodyTimer();
                session.setBody(Math.min(PlayerSession.MAX_HEALTH, session.health() + 1),
                        session.food(), session.saturation());
                session.addExhaustion(3.0f); // the historical regen cost
                publishBodyChanged(session);
            }
        } else if (session.food() == 0) {
            session.advanceBodyTimer();
            if (session.bodyTimer() >= PlayerSession.BODY_TIMER_PERIOD) {
                session.resetBodyTimer();
                if (session.health() > PlayerSession.STARVATION_FLOOR) {
                    damageOnTick(session, 1.0f); // easy difficulty: cannot kill
                }
            }
        } else {
            session.resetBodyTimer();
        }
    }

    /** Landing damage: falls beyond three blocks hurt ceil(distance - 3). */
    private void tickLanding(PlayerSession session) {
        if (!session.onGround()) {
            return;
        }
        float distance = session.consumeFallDistance();
        if (distance > PlayerSession.SAFE_FALL_DISTANCE) {
            damageOnTick(session, (float) Math.ceil(distance - PlayerSession.SAFE_FALL_DISTANCE));
        }
    }

    /**
     * The semantic damage entry (fall, starvation, mob melee; player melee is
     * {@link #attackEntity}). Safe from any thread: the application runs on the
     * tick thread.
     */
    public void damage(PlayerSession session, float amount) {
        Objects.requireNonNull(session, "session");
        ticker.submit(() -> damageOnTick(session, amount));
    }

    /** Historical 1.8 melee reach from the eyes (the survival attack range). */
    static final double MELEE_REACH = 3.5;
    /** Historical attack exhaustion (one swing). */
    private static final float ATTACK_EXHAUSTION = 0.3f;

    /**
     * A player attacked an entity (Use Entity 0x02, mouse=1): validates reach,
     * computes damage from the held item (historical values), applies
     * knockback along the attacker's look, and charges attack exhaustion.
     * Safe from any thread; the application runs on the tick thread.
     */
    public void attackEntity(PlayerSession attacker, int targetEntityId) {
        Objects.requireNonNull(attacker, "attacker");
        ticker.submit(() -> {
            if (attacker.state() != PlayerState.PLAYING || attacker.dead()
                    || mobManager == null) {
                return;
            }
            MobEntity mob = mobManager.byId(targetEntityId);
            if (mob == null || mob.dead()) {
                return; // already gone: nothing to hit
            }
            Position eye = attacker.position();
            Position target = mob.position();
            double dx = target.x() - eye.x();
            double dy = target.y() - eye.y();
            double dz = target.z() - eye.z();
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            if (horizontal > MELEE_REACH + mob.type().width * 0.5
                    || dy < -2.0 || dy > 4.0) {
                return; // out of reach: the server-side refusal
            }
            ItemStack held = attacker.inventory().held();
            float damage = net.zamin.engine.item.Tools.attackDamageOf(held.type());
            // Knockback direction: attacker -> mob (the historical feel).
            double kbYaw = Math.toDegrees(Math.atan2(-dx, dz));
            mobManager.hurt(mob, damage, kbYaw);
            attacker.addExhaustion(ATTACK_EXHAUSTION);
            // Tool durability: the historical wear on a living-entity hit.
            if (net.zamin.engine.item.Tools.specOf(held.type()).isPresent()) {
                attacker.inventory().damageHeld(1);
                publishInventoryChanged(attacker);
            }
        });
    }

    /**
     * A player attacked another player (Use Entity 0x02, mouse=1, resolved
     * through the attacker's observer id space): the same melee verdicts as
     * the mob path — reach, held-item damage, exhaustion, tool wear — plus
     * the historical hurt invulnerability window (a weaker hit inside the
     * window is absorbed; a stronger one out-damages it) and the knockback
     * velocity the victim's own client simulates. The victim's client gets
     * the hurt status, the health re-sync and the velocity; every client
     * that can see the victim gets the hurt flash. Safe from any thread;
     * the application runs on the tick thread.
     */
    public void attackPlayer(PlayerSession attacker, PlayerSession victim) {
        Objects.requireNonNull(attacker, "attacker");
        Objects.requireNonNull(victim, "victim");
        ticker.submit(() -> {
            if (!config.pvp() || attacker == victim
                    || attacker.state() != PlayerState.PLAYING || attacker.dead()
                    || victim.state() != PlayerState.PLAYING || victim.dead()) {
                return;
            }
            Position eye = attacker.position();
            Position target = victim.position();
            double dx = target.x() - eye.x();
            double dy = target.y() - eye.y();
            double dz = target.z() - eye.z();
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            // The victim's bounding box adds 0.3 to the reach like a mob's width.
            if (horizontal > MELEE_REACH + 0.3 || dy < -2.0 || dy > 4.0) {
                return; // out of reach: the server-side refusal
            }
            float damage = net.zamin.engine.item.Tools.attackDamageOf(attacker.inventory().held().type());
            if (victim.hurtInvulnerable()) {
                if (damage <= victim.lastHurtDamage()) {
                    return; // absorbed by the hurt window
                }
                damage -= victim.lastHurtDamage(); // the historical out-damage rule
            }
            final float applied = damage;
            victim.beginHurtInvulnerability(damage);
            victim.hurt(damage);
            attacker.addExhaustion(ATTACK_EXHAUSTION);
            if (net.zamin.engine.item.Tools.specOf(attacker.inventory().held().type()).isPresent()) {
                attacker.inventory().damageHeld(1);
                publishInventoryChanged(attacker);
            }
            // Knockback (the historical feel: 0.4 horizontal along the swing,
            // 0.4 up). The victim's client owns its own physics, so this rides
            // the wire as a velocity set; observers see the movement packets.
            double kbYaw = Math.atan2(-dx, dz);
            double vx = -Math.sin(kbYaw) * 0.4;
            double vz = Math.cos(kbYaw) * 0.4;
            publishPlayerHurt(victim);
            publishKnockback(victim, vx, 0.4, vz);
            if (victim.health() <= 0) {
                dieOnTick(victim);
            } else {
                publishBodyChanged(victim);
            }
            LOGGER.fine(() -> attacker.name() + " hit " + victim.name()
                    + " for " + applied + " (health " + victim.health() + ")");
        });
    }

    private void publishPlayerHurt(PlayerSession victim) {
        for (SurvivalListener listener : survivalListeners) {
            listener.onPlayerHurt(victim);
        }
    }

    private void publishKnockback(PlayerSession victim, double vx, double vy, double vz) {
        for (SurvivalListener listener : survivalListeners) {
            listener.onKnockback(victim, vx, vy, vz);
        }
    }

    private void damageOnTick(PlayerSession session, float amount) {
        if (session.dead() || session.state() != PlayerState.PLAYING) {
            return;
        }
        session.hurt(amount);
        if (session.health() <= 0) {
            dieOnTick(session);
        } else {
            publishBodyChanged(session);
        }
    }

    /**
     * Death: carried window state returns, the whole inventory (and cursor)
     * scatters at the body with the historical pop, the body marks dead. The
     * client learns through the survival listener (combat event + health 0).
     * Tick-thread context.
     */
    private void dieOnTick(PlayerSession session) {
        session.markDead();
        returnWindowCarriedItems(session, true);
        var slots = session.inventory().snapshot();
        for (int slot = 0; slot < slots.size(); slot++) {
            ItemStack dropped = session.inventory().dropFromSlot(slot, true);
            spawnDeathDrop(session, dropped);
        }
        spawnDeathDrop(session, session.inventory().takeCursor());
        publishInventoryChanged(session);
        publishBodyChanged(session);
        publishDied(session);
        LOGGER.info(() -> "Player died: " + session.name());
    }

    /** Death drops scatter around the body with a small random pop. */
    private void spawnDeathDrop(PlayerSession session, ItemStack stack) {
        if (stack.isEmpty() || itemEntities == null) {
            return;
        }
        var origin = session.position();
        ItemEntity entity = itemEntities.spawnThrown(
                new net.zamin.api.Position(origin.x(), origin.y() + 1.0, origin.z()), stack);
        entity.setVelocity((Math.random() - 0.5) * 0.2, 0.2, (Math.random() - 0.5) * 0.2);
    }

    private void publishInventoryChanged(PlayerSession player) {
        for (InventoryListener listener : inventoryListeners) {
            listener.onInventoryChanged(player);
        }
    }

    /**
     * Applies a validated held-slot change. The owning channel loop provides
     * ordering; the mutation itself is simulation-confined. Safe from any thread.
     * Switching slots cancels an in-progress eat (the historical rule).
     */
    public void heldItemChange(PlayerSession session, int hotbarSlot) {
        Objects.requireNonNull(session, "session");
        ticker.submit(() -> {
            try {
                session.inventory().selectHotbarSlot(hotbarSlot);
                session.cancelEating();
            } catch (IllegalArgumentException invalidSlot) {
                LOGGER.fine(() -> "Rejected held-slot change " + hotbarSlot + " from "
                        + session.name());
            }
        });
    }

    // ------------------------------------------------------------------ eating + respawn

    /**
     * A right-click use in the air: the held item decides the semantics —
     * food starts the 32-tick eat timer when the player is hungry; everything
     * else is a no-op this slice. Safe from any thread.
     */
    @Override
    public void useItem(PlayerSession session) {
        Objects.requireNonNull(session, "session");
        ticker.submit(() -> {
            if (session.dead() || session.state() != PlayerState.PLAYING) {
                return;
            }
            ItemStack held = session.inventory().held();
            if (held.isEmpty() || Foods.nutritionOf(held.type()).isEmpty()) {
                return; // nothing edible held: nothing to use yet
            }
            if (session.food() >= net.zamin.engine.player.PlayerSession.MAX_FOOD) {
                return; // not hungry: the historical refusal
            }
            session.beginEating();
        });
    }

    /**
     * The client released a use (dig status 5): an unfinished eat cancels; a
     * finished one was already applied by the tick timer. Safe from any thread.
     */
    @Override
    public void releaseUsingItem(PlayerSession session) {
        Objects.requireNonNull(session, "session");
        ticker.submit(() -> {
            if (session.eating() && session.eatingTicks() < Foods.EAT_TICKS) {
                session.cancelEating(); // released early: the historical cancel
            }
        });
    }

    /**
     * The client asked to respawn after dying: the body resets (full health,
     * food, saturation) and the adapter re-anchors the wire at world spawn.
     * Safe from any thread.
     */
    @Override
    public void performRespawn(PlayerSession session) {
        Objects.requireNonNull(session, "session");
        ticker.submit(() -> {
            if (!session.dead()) {
                return; // respawn requests only answer deaths
            }
            session.resetBody();
            session.cancelEating();
            publishInventoryChanged(session);
            publishBodyChanged(session);
            publishRespawned(session);
            LOGGER.info(() -> "Player respawned: " + session.name());
        });
    }

    /**
     * Semantic drop from the held slot (historical Q / Ctrl+Q): removes the
     * units, spawns a thrown item entity in the look direction. Safe from any thread.
     */
    public void dropHeld(PlayerSession session, boolean entireStack) {
        Objects.requireNonNull(session, "session");
        ticker.submit(() -> {
            net.zamin.api.ItemStack dropped = session.inventory().dropHeld(entireStack);
            if (dropped.isEmpty()) {
                return;
            }
            throwFromPlayer(session, dropped);
            publishInventoryChanged(session);
        });
    }

    /** Historical throw: look direction, ~0.3 speed, small upward bias. */
    private void throwFromPlayer(PlayerSession session, net.zamin.api.ItemStack dropped) {
        double yaw = Math.toRadians(session.rotation().yaw());
        double pitch = Math.toRadians(session.rotation().pitch());
        double dx = -Math.sin(yaw) * Math.cos(pitch);
        double dy = -Math.sin(pitch);
        double dz = Math.cos(yaw) * Math.cos(pitch);
        double speed = 0.3;
        var itemEntitiesManager = itemEntities;
        if (itemEntitiesManager == null) {
            return;
        }
        var eye = session.position();
        ItemEntity entity = itemEntitiesManager.spawnThrown(
                new net.zamin.api.Position(
                        eye.x() + dx * 0.4, eye.y() + 1.62 + dy * 0.4, eye.z() + dz * 0.4),
                dropped);
        entity.setVelocity(dx * speed, dy * speed + 0.1, dz * speed);
    }

    /**
     * A window click in an open window (window 0 = player inventory, or the
     * session's open container): the semantic operation runs on the tick
     * thread, the verdict goes back through {@code result}, and the affected
     * window re-syncs after every click so the client never keeps predicted
     * state. Safe from any thread.
     */
    @Override
    public void windowClick(PlayerSession session, int windowId, int wireSlot, int button,
                            int mode, java.util.function.Consumer<Boolean> result) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(result, "result");
        ticker.submit(() -> windowClickOnTick(session, windowId, wireSlot, button, mode, result));
    }

    private void windowClickOnTick(PlayerSession session, int windowId, int wireSlot, int button,
                                   int mode, java.util.function.Consumer<Boolean> result) {
        var inventory = session.inventory();
        boolean accepted = false;
        try {
            if (wireSlot == -999) {
                // Clicking outside the window throws the carried stack.
                if (!inventory.cursor().isEmpty()) {
                    net.zamin.api.ItemStack carried = inventory.cursor();
                    throwFromPlayer(session, carried);
                    inventory.returnCursor();
                    accepted = true;
                }
            } else if (windowId == WIRE_WINDOW_PLAYER) {
                accepted = clickPlayerWindowOnTick(session, wireSlot, button, mode);
            } else if (windowId == session.openContainerWindowId()) {
                accepted = switch (session.openContainerKind()) {
                    case CRAFTING_TABLE -> clickContainerWindowOnTick(session, wireSlot, button, mode);
                    case FURNACE -> clickFurnaceWindowOnTick(session, wireSlot, button, mode);
                    case CHEST -> clickChestWindowOnTick(session, wireSlot, button, mode);
                    default -> false;
                };
            }
            // Unknown window ids: rejected per packet, the resync restores truth.
        } catch (IllegalArgumentException invalid) {
            accepted = false; // a broken click must not damage the session (§54)
        }
        try {
            result.accept(accepted);
        } catch (RuntimeException brokenCallback) {
            // The adapter's response path must never starve the tick queue or
            // corrupt session state (§54): the confirm/cursor sends are the
            // adapter's concern, the authoritative resync below still runs.
            LOGGER.log(java.util.logging.Level.WARNING,
                    "Window click callback failed for " + session.name(), brokenCallback);
        }
        publishInventoryChanged(session);
    }

    /** Click routing for the player inventory window (window 0). Tick-thread context. */
    private boolean clickPlayerWindowOnTick(PlayerSession session, int wireSlot, int button,
                                            int mode) {
        var inventory = session.inventory();
        var grid = session.crafting();
        switch (mode) {
            case 0 -> {
                if (wireSlot == WIRE_SLOT_RESULT) {
                    // Historical result-slot behavior: both buttons craft once
                    // into the cursor; a refused take (mismatched or
                    // overflowing cursor) reverts the client's prediction.
                    return takeCraftingResult(session, grid, inventory);
                } else if (wireSlot >= WIRE_SLOT_CRAFT_FIRST && wireSlot <= WIRE_SLOT_CRAFT_LAST) {
                    grid.clickCell(wireSlot - WIRE_SLOT_CRAFT_FIRST, button, inventory);
                    return true;
                } else {
                    int engineSlot = engineSlotOf(wireSlot);
                    if (engineSlot >= 0) {
                        inventory.clickSlot(engineSlot, button);
                        return true;
                    }
                }
                return false;
            }
            case 1 -> {
                if (wireSlot == WIRE_SLOT_RESULT) {
                    craftAllIntoInventory(session, grid, inventory);
                    return true;
                } else if (wireSlot >= WIRE_SLOT_CRAFT_FIRST && wireSlot <= WIRE_SLOT_CRAFT_LAST) {
                    grid.quickMoveTo(wireSlot - WIRE_SLOT_CRAFT_FIRST, inventory);
                    return true;
                } else {
                    int engineSlot = engineSlotOf(wireSlot);
                    if (engineSlot >= 0) {
                        inventory.quickMove(engineSlot);
                        return true;
                    }
                }
                return false;
            }
            case 2 -> {
                // Number-key swaps on the crafting area are a later gesture;
                // rejected per packet, the resync restores the truth.
                int engineSlot = engineSlotOf(wireSlot);
                if (engineSlot >= 0 && button >= 0 && button < 9) {
                    inventory.swapWithHotbar(engineSlot, button);
                    return true;
                }
                return false;
            }
            case 3 -> {
                // Middle-click clone is a creative-only gesture: rejected in survival.
                return false;
            }
            case 4 -> {
                if (wireSlot >= WIRE_SLOT_CRAFT_FIRST && wireSlot <= WIRE_SLOT_CRAFT_LAST) {
                    net.zamin.api.ItemStack dropped = grid.dropFromCell(
                            wireSlot - WIRE_SLOT_CRAFT_FIRST, button != 0);
                    if (!dropped.isEmpty()) {
                        throwFromPlayer(session, dropped);
                    }
                    return true;
                }
                int engineSlot = engineSlotOf(wireSlot);
                if (engineSlot >= 0) {
                    net.zamin.api.ItemStack dropped = inventory.dropFromSlot(engineSlot, button != 0);
                    if (!dropped.isEmpty()) {
                        throwFromPlayer(session, dropped);
                    }
                    return true;
                }
                return false;
            }
            case 5 -> {
                // Drag painting: rejected per packet; the end-of-drag resync
                // restores the authoritative state on the client.
                return false;
            }
            default -> {
                return false; // unknown mode: rejected
            }
        }
    }

    /**
     * Click routing for the open crafting-table container window (protocol 47
     * 10-slot GUI: 0 result, 1-9 grid, 10-36 main inventory, 37-45 hotbar).
     * Tick-thread context.
     */
    private boolean clickContainerWindowOnTick(PlayerSession session, int wireSlot, int button,
                                               int mode) {
        var inventory = session.inventory();
        var grid = session.tableCrafting();
        switch (mode) {
            case 0 -> {
                if (wireSlot == TABLE_WIRE_SLOT_RESULT) {
                    return takeCraftingResult(session, grid, inventory);
                } else if (wireSlot >= TABLE_WIRE_SLOT_GRID_FIRST
                        && wireSlot <= TABLE_WIRE_SLOT_GRID_LAST) {
                    grid.clickCell(wireSlot - TABLE_WIRE_SLOT_GRID_FIRST, button, inventory);
                    return true;
                }
                int engineSlot = tableEngineSlotOf(wireSlot);
                if (engineSlot >= 0) {
                    inventory.clickSlot(engineSlot, button);
                    return true;
                }
                return false;
            }
            case 1 -> {
                if (wireSlot == TABLE_WIRE_SLOT_RESULT) {
                    craftAllIntoInventory(session, grid, inventory);
                    return true;
                } else if (wireSlot >= TABLE_WIRE_SLOT_GRID_FIRST
                        && wireSlot <= TABLE_WIRE_SLOT_GRID_LAST) {
                    grid.quickMoveTo(wireSlot - TABLE_WIRE_SLOT_GRID_FIRST, inventory);
                    return true;
                }
                int engineSlot = tableEngineSlotOf(wireSlot);
                if (engineSlot >= 0) {
                    inventory.quickMove(engineSlot);
                    return true;
                }
                return false;
            }
            case 2 -> {
                // Number keys exchange main inventory and hotbar only; grid and
                // result slots are rejected per packet.
                int engineSlot = tableEngineSlotOf(wireSlot);
                if (engineSlot >= net.zamin.engine.player.PlayerInventory.HOTBAR_SLOTS
                        && button >= 0 && button < 9) {
                    inventory.swapWithHotbar(engineSlot, button);
                    return true;
                }
                return false;
            }
            case 3 -> {
                return false; // middle-click clone: rejected in survival
            }
            case 4 -> {
                if (wireSlot >= TABLE_WIRE_SLOT_GRID_FIRST
                        && wireSlot <= TABLE_WIRE_SLOT_GRID_LAST) {
                    net.zamin.api.ItemStack dropped = grid.dropFromCell(
                            wireSlot - TABLE_WIRE_SLOT_GRID_FIRST, button != 0);
                    if (!dropped.isEmpty()) {
                        throwFromPlayer(session, dropped);
                    }
                    return true;
                }
                int engineSlot = tableEngineSlotOf(wireSlot);
                if (engineSlot >= 0) {
                    net.zamin.api.ItemStack dropped = inventory.dropFromSlot(engineSlot, button != 0);
                    if (!dropped.isEmpty()) {
                        throwFromPlayer(session, dropped);
                    }
                    return true;
                }
                return false;
            }
            default -> {
                return false; // drag painting (5) / unknown mode: rejected
            }
        }
    }

    /**
     * Wire slot -&gt; engine slot mapping for the player inventory window; -1 when out of engine scope.
     */
    private static int engineSlotOf(int wireSlot) {
        if (wireSlot >= 9 && wireSlot <= 35) {
            return wireSlot;                 // main inventory
        }
        if (wireSlot >= 36 && wireSlot <= 44) {
            return wireSlot - 36;            // hotbar
        }
        return -1;                           // craft/armor slots or outside
    }

    /**
     * Wire slot -&gt; engine slot inside a furnace container window (3-29 maps to
     * main inventory engine slots 9-35; 30-38 to hotbar 0-8); -1 for the
     * furnace's own three slots.
     */
    private static int furnaceEngineSlotOf(int wireSlot) {
        if (wireSlot >= FURNACE_WIRE_SLOT_MAIN_FIRST && wireSlot <= FURNACE_WIRE_SLOT_MAIN_LAST) {
            return wireSlot + 6;             // main inventory (engine 9-35)
        }
        if (wireSlot >= FURNACE_WIRE_SLOT_HOTBAR_FIRST && wireSlot <= FURNACE_WIRE_SLOT_HOTBAR_LAST) {
            return wireSlot - FURNACE_WIRE_SLOT_HOTBAR_FIRST; // hotbar (engine 0-8)
        }
        return -1;
    }

    /**
     * Click routing for the open furnace container window (protocol 47
     * community-verified GUI: 0 input, 1 fuel, 2 output, 3-29 main inventory,
     * 30-38 hotbar). Tick-thread context.
     */
    private boolean clickFurnaceWindowOnTick(PlayerSession session, int wireSlot, int button,
                                             int mode) {
        var inventory = session.inventory();
        var position = session.openContainerPosition();
        var furnace = position == null ? null : furnaceManager.peek(position);
        if (furnace == null) {
            return false; // stale window (state discarded): rejected, resync restores
        }
        switch (mode) {
            case 0 -> {
                if (wireSlot >= 0 && wireSlot <= FURNACE_WIRE_SLOT_LAST) {
                    furnaceManager.clickSlot(furnace, wireSlot, button, inventory);
                    return true;
                }
                int engineSlot = furnaceEngineSlotOf(wireSlot);
                if (engineSlot >= 0) {
                    inventory.clickSlot(engineSlot, button);
                    return true;
                }
                return false;
            }
            case 1 -> {
                if (wireSlot >= 0 && wireSlot <= FURNACE_WIRE_SLOT_LAST) {
                    return furnaceManager.quickMove(furnace, wireSlot, true, -1, inventory);
                }
                int engineSlot = furnaceEngineSlotOf(wireSlot);
                if (engineSlot >= 0) {
                    return furnaceManager.quickMove(furnace, -1, false, engineSlot, inventory);
                }
                return false;
            }
            case 2 -> {
                // Number keys exchange main inventory and hotbar only; furnace
                // slots are rejected per packet (the resync restores truth).
                int engineSlot = furnaceEngineSlotOf(wireSlot);
                if (engineSlot >= net.zamin.engine.player.PlayerInventory.HOTBAR_SLOTS
                        && button >= 0 && button < 9) {
                    inventory.swapWithHotbar(engineSlot, button);
                    return true;
                }
                return false;
            }
            case 3 -> {
                return false; // middle-click clone: rejected in survival
            }
            case 4 -> {
                if (wireSlot >= 0 && wireSlot <= FURNACE_WIRE_SLOT_LAST) {
                    net.zamin.api.ItemStack dropped =
                            furnaceManager.dropFromSlot(furnace, wireSlot, button != 0);
                    if (!dropped.isEmpty()) {
                        throwFromPlayer(session, dropped);
                    }
                    return true;
                }
                int engineSlot = furnaceEngineSlotOf(wireSlot);
                if (engineSlot >= 0) {
                    net.zamin.api.ItemStack dropped =
                            inventory.dropFromSlot(engineSlot, button != 0);
                    if (!dropped.isEmpty()) {
                        throwFromPlayer(session, dropped);
                    }
                    return true;
                }
                return false;
            }
            default -> {
                return false; // drag painting (5) / unknown mode: rejected
            }
        }
    }

    /**
     * Wire slot -&gt; engine slot inside a chest container window (27-53 maps
     * to main inventory engine slots 9-35; 54-62 to hotbar 0-8); -1 for the
     * chest's own 27 slots.
     */
    private static int chestEngineSlotOf(int wireSlot) {
        if (wireSlot >= CHEST_WIRE_SLOT_MAIN_FIRST && wireSlot <= CHEST_WIRE_SLOT_MAIN_LAST) {
            return wireSlot - 18;            // main inventory (engine 9-35)
        }
        if (wireSlot >= CHEST_WIRE_SLOT_HOTBAR_FIRST && wireSlot <= CHEST_WIRE_SLOT_HOTBAR_LAST) {
            return wireSlot - CHEST_WIRE_SLOT_HOTBAR_FIRST; // hotbar (engine 0-8)
        }
        return -1;
    }

    /**
     * Click routing for the open chest container window (protocol 47
     * community-verified GUI: 0-26 chest, 27-53 main inventory, 54-62 hotbar).
     * Tick-thread context.
     */
    private boolean clickChestWindowOnTick(PlayerSession session, int wireSlot, int button,
                                           int mode) {
        var inventory = session.inventory();
        var position = session.openContainerPosition();
        var chest = position == null ? null : chestManager.peek(position);
        if (chest == null) {
            return false; // stale window (state discarded): rejected, resync restores
        }
        switch (mode) {
            case 0 -> {
                if (wireSlot >= 0 && wireSlot <= CHEST_WIRE_SLOT_LAST) {
                    chest.clickSlot(wireSlot, button, inventory);
                    return true;
                }
                int engineSlot = chestEngineSlotOf(wireSlot);
                if (engineSlot >= 0) {
                    inventory.clickSlot(engineSlot, button);
                    return true;
                }
                return false;
            }
            case 1 -> {
                if (wireSlot >= 0 && wireSlot <= CHEST_WIRE_SLOT_LAST) {
                    chest.quickMoveToInventory(wireSlot, inventory);
                    return true;
                }
                int engineSlot = chestEngineSlotOf(wireSlot);
                if (engineSlot >= 0) {
                    // Any stack moves in: a chest has no slot filters (the
                    // historical TileEntityChest). Remainder returns to the slot.
                    net.zamin.api.ItemStack peek = inventory.snapshot().get(engineSlot);
                    if (peek.isEmpty()) {
                        return false; // empty slot shift-click: nothing moves
                    }
                    net.zamin.api.ItemStack moving = inventory.dropFromSlot(engineSlot, true);
                    net.zamin.api.ItemStack remainder = chest.quickMoveIn(moving);
                    if (!remainder.isEmpty()) {
                        inventory.pickUp(remainder); // chest full: put the rest back
                    }
                    return true;
                }
                return false;
            }
            case 2 -> {
                // Number keys exchange main inventory and hotbar only; chest
                // slots are rejected per packet (the resync restores truth).
                int engineSlot = chestEngineSlotOf(wireSlot);
                if (engineSlot >= net.zamin.engine.player.PlayerInventory.HOTBAR_SLOTS
                        && button >= 0 && button < 9) {
                    inventory.swapWithHotbar(engineSlot, button);
                    return true;
                }
                return false;
            }
            case 3 -> {
                return false; // middle-click clone: rejected in survival
            }
            case 4 -> {
                if (wireSlot >= 0 && wireSlot <= CHEST_WIRE_SLOT_LAST) {
                    net.zamin.api.ItemStack dropped = chest.dropFromSlot(wireSlot, button != 0);
                    if (!dropped.isEmpty()) {
                        throwFromPlayer(session, dropped);
                    }
                    return true;
                }
                int engineSlot = chestEngineSlotOf(wireSlot);
                if (engineSlot >= 0) {
                    net.zamin.api.ItemStack dropped =
                            inventory.dropFromSlot(engineSlot, button != 0);
                    if (!dropped.isEmpty()) {
                        throwFromPlayer(session, dropped);
                    }
                    return true;
                }
                return false;
            }
            default -> {
                return false; // drag painting (5) / unknown mode: rejected
            }
        }
    }

    /**
     * Wire slot -> engine inventory slot inside a container window (10-36 maps
     * to main inventory engine slots 9-35; 37-45 to hotbar 0-8); -1 for the
     * container's own result/grid slots.
     */
    private static int tableEngineSlotOf(int wireSlot) {
        if (wireSlot >= TABLE_WIRE_SLOT_GRID_LAST + 1 && wireSlot <= TABLE_WIRE_SLOT_GRID_LAST + 27) {
            return wireSlot - 1;             // main inventory (engine 9-35)
        }
        if (wireSlot >= TABLE_WIRE_SLOT_HOTBAR_FIRST && wireSlot <= TABLE_WIRE_SLOT_HOTBAR_LAST) {
            return wireSlot - TABLE_WIRE_SLOT_HOTBAR_FIRST; // hotbar (engine 0-8)
        }
        return -1;
    }

    /**
     * Result-slot pickup (mode 0 on wire slot 0): the previewed result moves
     * onto the cursor and one unit leaves every non-empty grid cell — atomic,
     * historical. A refused cursor take rejects the click unchanged.
     */
    private boolean takeCraftingResult(PlayerSession session, net.zamin.engine.player.CraftingGrid grid,
                                       net.zamin.engine.player.PlayerInventory inventory) {
        net.zamin.api.ItemStack result = craftingResultOf(grid);
        if (result.isEmpty()) {
            return true; // nothing crafted: an accepted no-op, the resync realigns
        }
        if (!inventory.takeResultToCursor(result)) {
            return false;
        }
        grid.consumeOne();
        return true;
    }

    /** The preview over a grid of either shape (2x2 player window, 3x3 table). */
    private net.zamin.api.ItemStack craftingResultOf(net.zamin.engine.player.CraftingGrid grid) {
        var snapshot = grid.snapshotArray();
        return (grid.cols() == 3
                        ? crafting.resultOf3x3(snapshot)
                        : crafting.resultOf(snapshot))
                .orElse(net.zamin.api.ItemStack.EMPTY);
    }

    /**
     * Craft-all (shift-click on the result): repeats the single craft into the
     * inventory until the grid no longer matches or the inventory cannot
     * absorb the next result (the historical stop). A thrown remainder ends
     * the chain so a full inventory never spins the loop.
     */
    private void craftAllIntoInventory(PlayerSession session, net.zamin.engine.player.CraftingGrid grid,
                                       net.zamin.engine.player.PlayerInventory inventory) {
        for (int craft = 0; craft < CRAFT_ALL_LIMIT; craft++) {
            net.zamin.api.ItemStack result = craftingResultOf(grid);
            if (result.isEmpty()) {
                return;
            }
            grid.consumeOne();
            net.zamin.api.ItemStack remainder = inventory.pickUp(result);
            if (!remainder.isEmpty()) {
                throwFromPlayer(session, remainder);
                return;
            }
        }
    }

    /**
     * The crafting preview for wire sync (window slot 0). Call on the tick
     * thread or before a session's first grid mutation (both call sites of the
     * adapter qualify); the read is a pure match over a snapshot.
     */
    public net.zamin.api.ItemStack craftingResult(PlayerSession session) {
        Objects.requireNonNull(session, "session");
        return crafting.resultOf(session.crafting().snapshotArray())
                .orElse(net.zamin.api.ItemStack.EMPTY);
    }

    /**
     * The crafting-table preview for wire sync (container wire slot 0), empty
     * while no container is open. Same thread discipline as
     * {@link #craftingResult}.
     */
    public net.zamin.api.ItemStack containerResult(PlayerSession session) {
        Objects.requireNonNull(session, "session");
        if (session.openContainerWindowId() < 0) {
            return net.zamin.api.ItemStack.EMPTY;
        }
        return crafting.resultOf3x3(session.tableCrafting().snapshotArray())
                .orElse(net.zamin.api.ItemStack.EMPTY);
    }

    /**
     * A right-click use on a block (§215 family): the engine decides on the
     * simulation context whether the target block opens a container window
     * (the crafting table) or the use degrades to a placement proposal. Safe
     * from any thread.
     */
    @Override
    public void useItemOnBlock(PlayerSession session, net.zamin.api.BlockPosition clicked, int face,
                               java.util.Optional<net.zamin.api.BlockType> creativeHeld,
                               java.util.function.IntConsumer onTableOpened) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(clicked, "clicked");
        Objects.requireNonNull(creativeHeld, "creativeHeld");
        Objects.requireNonNull(onTableOpened, "onTableOpened");
        ticker.submit(() -> useItemOnBlockOnTick(session, clicked, face, creativeHeld, onTableOpened));
    }

    private void useItemOnBlockOnTick(PlayerSession session, net.zamin.api.BlockPosition clicked,
                                      int face, java.util.Optional<net.zamin.api.BlockType> creativeHeld,
                                      java.util.function.IntConsumer onTableOpened) {
        net.zamin.api.BlockType current = world.getBlock(clicked);
        if (current.identifier().equals(net.zamin.engine.block.BuiltinBlocks.FURNACE.identifier())) {
            openFurnaceOnTick(session, clicked, onTableOpened);
            return;
        }
        if (current.identifier().equals(net.zamin.engine.block.BuiltinBlocks.CHEST.identifier())) {
            openChestOnTick(session, clicked, onTableOpened);
            return;
        }
        if (current.identifier().equals(net.zamin.engine.block.BuiltinBlocks.CRAFTING_TABLE.identifier())) {
            openCraftingTableOnTick(session, onTableOpened);
            return;
        }
        blockInteraction.placeFromUseOnTick(session, clicked, face, creativeHeld);
    }

    /**
     * Opens the crafting-table container: assigns the wire window id, returns
     * any carried window state (the historical container change closes the
     * previous window; the one cursor carries over — one mouse), and reports
     * the id so the adapter can send Open Window followed by the authoritative
     * window contents. Tick-thread context.
     */
    private void openCraftingTableOnTick(PlayerSession session,
                                         java.util.function.IntConsumer onTableOpened) {
        closeOpenContainerOnTick(session);
        // Opening a container closes the player inventory window (historical):
        // the 2x2 grid returns to the inventory, nothing is lost.
        throwOverflow(session, session.crafting().returnAllTo(session.inventory()));

        int windowId = nextContainerWindowId;
        nextContainerWindowId = nextContainerWindowId >= LAST_CONTAINER_WINDOW_ID
                ? FIRST_CONTAINER_WINDOW_ID : nextContainerWindowId + 1;
        session.openContainerWindow(windowId, PlayerSession.ContainerKind.CRAFTING_TABLE, null);
        // The adapter must send Open Window before any slot data: the client
        // ignores Window Items for a window id it does not know yet.
        onTableOpened.accept(windowId);
    }

    /**
     * Opens the furnace container at the clicked block: assigns the wire
     * window id, lazily creates the block-entity state, closes any carried
     * window state first, and reports the id for the adapter's Open Window +
     * slot sync. The furnace's slots live in the world — closing the window
     * later leaves them inside (the historical container behavior).
     * Tick-thread context.
     */
    private void openFurnaceOnTick(PlayerSession session, net.zamin.api.BlockPosition position,
                                   java.util.function.IntConsumer onTableOpened) {
        closeOpenContainerOnTick(session);
        throwOverflow(session, session.crafting().returnAllTo(session.inventory()));

        FurnaceBlockEntity furnace = furnaceManager.getOrCreate(position);
        int windowId = nextContainerWindowId;
        nextContainerWindowId = nextContainerWindowId >= LAST_CONTAINER_WINDOW_ID
                ? FIRST_CONTAINER_WINDOW_ID : nextContainerWindowId + 1;
        session.openContainerWindow(windowId, PlayerSession.ContainerKind.FURNACE, position);
        onTableOpened.accept(windowId);
    }

    /**
     * Opens the chest container at the clicked block: assigns the wire window
     * id, lazily creates the block-entity state, closes any carried window
     * state first, and reports the id for the adapter's Open Window + slot
     * sync. The chest's 27 slots live in the world — closing the window later
     * leaves them inside (the historical container behavior).
     * Tick-thread context.
     */
    private void openChestOnTick(PlayerSession session, net.zamin.api.BlockPosition position,
                                 java.util.function.IntConsumer onTableOpened) {
        closeOpenContainerOnTick(session);
        throwOverflow(session, session.crafting().returnAllTo(session.inventory()));

        chestManager.getOrCreate(position);
        int windowId = nextContainerWindowId;
        nextContainerWindowId = nextContainerWindowId >= LAST_CONTAINER_WINDOW_ID
                ? FIRST_CONTAINER_WINDOW_ID : nextContainerWindowId + 1;
        session.openContainerWindow(windowId, PlayerSession.ContainerKind.CHEST, position);
        onTableOpened.accept(windowId);
    }

    /**
     * A stale open container (re-open without a close) releases what it
     * carries: the crafting table's 3x3 grid returns to the inventory; a
     * furnace keeps its slots in the world. Tick-thread context.
     */
    private void closeOpenContainerOnTick(PlayerSession session) {
        if (session.openContainerWindowId() < 0) {
            return;
        }
        if (session.openContainerKind() == PlayerSession.ContainerKind.CRAFTING_TABLE) {
            throwOverflow(session, session.tableCrafting().returnAllTo(session.inventory()));
        }
        session.closeContainerWindow();
    }

    /** Throws each overflow stack into the world at the player (nothing is lost). */
    private void throwOverflow(PlayerSession session, java.util.List<net.zamin.api.ItemStack> overflow) {
        for (net.zamin.api.ItemStack stack : overflow) {
            throwFromPlayer(session, stack);
        }
    }

    /**
     * The client closed a window: carried window state returns to the
     * inventory; remainders are thrown into the world so nothing is lost.
     * Safe from any thread.
     */
    @Override
    public void closeWindow(PlayerSession session, int windowId) {
        Objects.requireNonNull(session, "session");
        ticker.submit(() -> {
            if (windowId == WIRE_WINDOW_PLAYER) {
                returnWindowCarriedItems(session, false);
            } else if (windowId == session.openContainerWindowId()) {
                returnWindowCarriedItems(session, true);
                session.closeContainerWindow();
            }
            publishInventoryChanged(session);
        });
    }

    /**
     * Cursor + crafting grids back into the inventory; overflow is thrown.
     * With {@code containerToo} the open container releases its carried state
     * as well: the crafting table's 3x3 grid returns to the inventory, while a
     * furnace's three slots stay inside the furnace (historical container
     * behavior) and persist with the world. Tick-thread context.
     */
    private void returnWindowCarriedItems(PlayerSession session, boolean containerToo) {
        net.zamin.api.ItemStack leftover = session.inventory().returnCursor();
        if (!leftover.isEmpty()) {
            throwFromPlayer(session, leftover);
        }
        throwOverflow(session, session.crafting().returnAllTo(session.inventory()));
        if (containerToo && session.openContainerWindowId() >= 0) {
            if (session.openContainerKind() == PlayerSession.ContainerKind.CRAFTING_TABLE) {
                throwOverflow(session, session.tableCrafting().returnAllTo(session.inventory()));
            }
            session.closeContainerWindow();
        }
    }

    /** Fans item-entity events out to registered observers (tick-thread context). */
    private final class ItemEventDispatch implements ItemEntityManager.Listener {
        @Override
        public void onItemSpawned(ItemEntity entity) {
            for (ItemEntityManager.Listener listener : itemListeners) {
                listener.onItemSpawned(entity);
            }
        }

        @Override
        public void onItemMoved(ItemEntity entity) {
            for (ItemEntityManager.Listener listener : itemListeners) {
                listener.onItemMoved(entity);
            }
        }

        @Override
        public void onItemCollected(ItemEntity entity, PlayerSession collector, int collectedCount) {
            for (ItemEntityManager.Listener listener : itemListeners) {
                listener.onItemCollected(entity, collector, collectedCount);
            }
            publishInventoryChanged(collector); // pickup changed the inventory (§433 sync)
        }

        @Override
        public void onItemStackChanged(ItemEntity entity) {
            for (ItemEntityManager.Listener listener : itemListeners) {
                listener.onItemStackChanged(entity);
            }
        }

        @Override
        public void onItemRemoved(ItemEntity entity, String reason) {
            for (ItemEntityManager.Listener listener : itemListeners) {
                listener.onItemRemoved(entity, reason);
            }
        }
    }

    /**
     * Fan-out from the simulation-owned falling-block system to the observer
     * list — the same pattern the item and mob systems use (§448): the engine
     * decides what happened, the listeners decide how it reaches clients. The
     * landing's block change itself rides the world listeners.
     */
    private final class FallingEventDispatch implements FallingBlockEntityManager.Listener {
        @Override
        public void onFallingSpawned(FallingBlockEntity entity) {
            for (FallingBlockEntityManager.Listener listener : fallingListeners) {
                listener.onFallingSpawned(entity);
            }
        }

        @Override
        public void onFallingMoved(FallingBlockEntity entity) {
            for (FallingBlockEntityManager.Listener listener : fallingListeners) {
                listener.onFallingMoved(entity);
            }
        }

        @Override
        public void onFallingEnded(FallingBlockEntity entity, boolean becameBlock) {
            for (FallingBlockEntityManager.Listener listener : fallingListeners) {
                listener.onFallingEnded(entity, becameBlock);
            }
        }
    }

    /**
     * Fan-out from the simulation-owned mob system to the observer list —
     * the same pattern the item-entity system uses (§448): the engine decides
     * what happened, the listeners decide how it reaches clients. Zombie
     * melee lands through the survival damage path (same thread, one owner).
     */
    private final class MobEventDispatch implements MobManager.Listener {
        @Override
        public void onMobSpawned(MobEntity mob) {
            for (MobManager.Listener listener : mobListeners) {
                listener.onMobSpawned(mob);
            }
        }

        @Override
        public void onMobMoved(MobEntity mob) {
            for (MobManager.Listener listener : mobListeners) {
                listener.onMobMoved(mob);
            }
        }

        @Override
        public void onMobHurt(MobEntity mob) {
            for (MobManager.Listener listener : mobListeners) {
                listener.onMobHurt(mob);
            }
        }

        @Override
        public void onMobDied(MobEntity mob) {
            for (MobManager.Listener listener : mobListeners) {
                listener.onMobDied(mob);
            }
        }

        @Override
        public void onMobRemoved(MobEntity mob, String reason) {
            for (MobManager.Listener listener : mobListeners) {
                listener.onMobRemoved(mob, reason);
            }
        }

        @Override
        public void onMobAttackedPlayer(MobEntity mob, PlayerSession target, float damage) {
            for (MobManager.Listener listener : mobListeners) {
                listener.onMobAttackedPlayer(mob, target, damage);
            }
            damageOnTick(target, damage); // the same-thread survival damage path
        }

        @Override
        public void onMobSound(MobEntity mob, String soundName) {
            for (MobManager.Listener listener : mobListeners) {
                listener.onMobSound(mob, soundName);
            }
        }
    }

    /**
     * The world queries a mob's AI and physics need, answered from the world
     * the tick thread owns. Called only on the simulation context.
     */
    private final class MobWorldQuery implements MobEntity.WorldQuery {
        @Override
        public boolean isSolid(double x, double y, double z) {
            return !world.getBlock(blockAt(x, y, z)).equals(world.airType());
        }

        @Override
        public Position nearestPlayer(double x, double y, double z, double range) {
            PlayerSession best = null;
            double bestDistance = range * range;
            for (PlayerSession player : players.all()) {
                if (player.state() != PlayerState.PLAYING || player.dead()) {
                    continue;
                }
                Position p = player.position();
                double dx = p.x() - x;
                double dy = p.y() - y;
                double dz = p.z() - z;
                double distance = dx * dx + dy * dy + dz * dz;
                if (distance <= bestDistance) {
                    bestDistance = distance;
                    best = player;
                }
            }
            return best == null ? null : best.position();
        }
    }

    /**
     * Topmost solid block y of a column for population rolls (-1 when the
     * chunk is absent). All callers run on the simulation context, so a
     * missing chunk can be generated safely.
     */
    private int surfaceY(int x, int z) {
        EngineChunk chunk = world.peek(new net.zamin.api.ChunkPosition(x >> 4, z >> 4));
        if (chunk == null) {
            chunk = world.getOrGenerate(new net.zamin.api.ChunkPosition(x >> 4, z >> 4));
        }
        for (int y = EngineChunk.SECTION_COUNT * 16 - 1; y >= 0; y--) {
            if (!chunk.getBlock(x & 15, y, z & 15).equals(world.airType())) {
                return y;
            }
        }
        return -1;
    }

    /** Registers an internal chat delivery observer (e.g. the protocol adapter). */
    public void addChatListener(ChatListener listener) {
        chatListeners.add(listener);
    }

    private void registerBuiltinCommands(CommandService commands) {
        commands.register(new CommandService.Command("help", "List commands",
                (sender, args) -> {
                    StringBuilder text = new StringBuilder("Commands:");
                    for (CommandService.Command command : commands.all()) {
                        text.append(" /").append(command.name()).append(" (").append(command.description()).append(")");
                    }
                    return text.toString();
                }));
        commands.register(new CommandService.Command("ping", "Check server responsiveness",
                (sender, args) -> "pong"));
        commands.register(new CommandService.Command("give", "Give yourself an item: /give <name> [count] [metadata]",
                this::giveCommand));
        commands.register(new CommandService.Command("time", "Query or set the day: /time query | /time set <day|noon|night|midnight|ticks>",
                this::timeCommand));
        commands.register(new CommandService.Command("rename",
                "Rename the held item: /rename <name...> (no name clears it)", this::renameCommand));
        commands.register(new CommandService.Command("spawnmob", "Spawn mobs near you: /spawnmob <pig|cow|chicken|zombie> [count]",
                this::spawnMobCommand));
    }

    /**
     * /time query, or /time set &lt;day|noon|night|midnight|ticks&gt;: moves the
     * world's day clock (the same value Time Update 0x03 carries). Runs on the
     * tick thread through chat dispatch; every client hears the new time
     * through the time listeners.
     */
    private String timeCommand(PlayerSession sender, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("query")) {
            return "Time: " + world.timeOfDay() + " / " + MobManager.DAY_LENGTH
                    + " (total " + world.totalTicks() + ")";
        }
        if (args.length < 2 || !args[0].equalsIgnoreCase("set")) {
            return "Usage: /time query | /time set <day|noon|night|midnight|ticks>";
        }
        long timeOfDay;
        switch (args[1].toLowerCase(java.util.Locale.ROOT)) {
            case "day" -> timeOfDay = 1_000;
            case "noon" -> timeOfDay = 6_000;
            case "night" -> timeOfDay = MobManager.NIGHT_START;
            case "midnight" -> timeOfDay = 18_000;
            default -> {
                try {
                    timeOfDay = Long.parseLong(args[1]);
                } catch (NumberFormatException e) {
                    return "Not a time: " + args[1];
                }
            }
        }
        if (timeOfDay < 0 || timeOfDay >= MobManager.DAY_LENGTH) {
            return "Time must be 0.." + (MobManager.DAY_LENGTH - 1);
        }
        world.setTimeOfDay(timeOfDay);
        publishTimeChanged();
        return "Time set to " + timeOfDay;
    }

    /**
     * /spawnmob &lt;pig|cow|chicken|zombie&gt; [count]: spawns a small group of
     * the kind around the sender on the surface — the controlled entry point
     * for real-client validation. Runs on the tick thread through chat dispatch.
     */
    /**
     * /rename &lt;name...&gt;: renames the held stack (the historical
     * tag.display.Name — the anvil rename without the anvil). Joins arguments
     * with spaces; no arguments clears the name. The wire carries it as the
     * slot encoding's NBT compound, so every container, drop and pickup
     * preserves it. Runs on the tick thread through chat dispatch.
     */
    private String renameCommand(PlayerSession sender, String[] args) {
        if (sender.inventory().held().isEmpty()) {
            return "Hold an item to rename it";
        }
        String name = args.length == 0 ? "" : String.join(" ", args);
        if (name.length() > net.zamin.api.ItemStack.MAX_NAME_LENGTH) {
            return "Name too long (max " + net.zamin.api.ItemStack.MAX_NAME_LENGTH + ")";
        }
        sender.inventory().renameHeld(name);
        publishInventoryChanged(sender);
        return name.isEmpty() ? "Name cleared" : "Renamed to " + name;
    }

    private String spawnMobCommand(PlayerSession sender, String[] args) {
        if (args.length < 1) {
            return "Usage: /spawnmob <pig|cow|chicken|zombie> [count]";
        }
        MobType type = MobType.byName(args[0]);
        if (type == null) {
            return "Unknown mob: " + args[0];
        }
        int count = 1;
        if (args.length >= 2) {
            try {
                count = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                return "Not a count: " + args[1];
            }
            if (count < 1 || count > 10) {
                return "Count must be 1..10";
            }
        }
        int spawned = mobManager.spawnGroup(type, sender.position(), count).size();
        return "Spawned " + spawned + " x " + type.name().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * /give &lt;name&gt; [count] [metadata]: grants the item into the player's
     * inventory (fill order as pickup). The optional metadata is the
     * historical damage field's variant role — charcoal is {@code /give coal 1
     * 1} (community items.json variant, the client names it from the value).
     * Runs on the tick thread through chat dispatch.
     */
    private String giveCommand(PlayerSession sender, String[] args) {
        if (args.length < 1) {
            return "Usage: /give <item> [count] [metadata]";
        }
        String rawName = args[0];
        String name = rawName.contains(":") ? rawName : "minecraft:" + rawName;
        net.zamin.api.ItemType type = net.zamin.engine.item.BuiltinItems.lookup(
                net.zamin.api.Identifier.parse(name)).orElse(null);
        if (type == null) {
            return "Unknown item: " + rawName;
        }
        int count = 1;
        if (args.length >= 2) {
            try {
                count = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                return "Not a count: " + args[1];
            }
            if (count < 1 || count > type.maxStackSize()) {
                return "Count must be 1.." + type.maxStackSize() + " for " + type.displayName();
            }
        }
        int metadata = 0;
        if (args.length >= 3) {
            try {
                metadata = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                return "Not a metadata value: " + args[2];
            }
            if (metadata < 0 || metadata > net.zamin.api.ItemStack.MAX_DAMAGE) {
                return "Metadata must be 0.." + net.zamin.api.ItemStack.MAX_DAMAGE;
            }
        }
        net.zamin.api.ItemStack granted;
        try {
            granted = net.zamin.api.ItemStack.of(type, count).withDamage(metadata);
        } catch (IllegalArgumentException invalidVariant) {
            return "Cannot grant that metadata: " + invalidVariant.getMessage();
        }
        net.zamin.api.ItemStack remainder = sender.inventory().pickUp(granted);
        publishInventoryChanged(sender);
        int given = count - remainder.count();
        String variantName = metadata != 0 ? " (metadata " + metadata + ")" : "";
        return remainder.isEmpty()
                ? "Given " + given + " x " + type.displayName() + variantName
                : "Inventory full: gave " + given + " of " + count;
    }

    private void publishChat(ChatService.ChatEvent event) {
        for (ChatListener listener : chatListeners) {
            if (event instanceof ChatService.PublicChat publicChat) {
                listener.onChatMessage(publicChat.sender(), publicChat.content());
            } else if (event instanceof ChatService.SystemToPlayer system) {
                listener.onSystemMessage(system.recipient(), system.content());
            }
        }
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
                // Mid-fall blocks land now: blocks persist, entities do not.
                if (fallingEntities != null) {
                    fallingEntities.finishAllFalls();
                }
                worldStorage.save(world.snapshotDeltas());
                if (furnaceStore != null && furnaceManager != null) {
                    furnaceStore.save(furnaceManager.snapshot());
                }
                if (chestStore != null && chestManager != null) {
                    chestStore.save(chestManager.snapshot());
                }
                if (mobStore != null && mobManager != null) {
                    mobStore.save(mobManager.snapshot());
                }
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
        if (world == null) {
            throw new IllegalStateException("World listener registration requires the booted world");
        }
        world.addChangeListener(listener);
    }

    // World-change fan-out lives on the world itself (§208): every committed
    // mutation fires the listeners once, whether a player or a rule drove it.

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
            // Returning players re-enter at their saved spot (§407 spirit: restart
            // survival). The small per-player file read happens on the joining
            // channel's event loop - one seek-and-read, no gameplay lock held.
            Optional<PlayerSnapshot> saved = playerStore == null
                    ? Optional.empty() : playerStore.load(offlineUuid);
            PlayerSession session = new PlayerSession(offlineUuid, username, link);
            players.register(session);
            session.authenticate();
            Position spawn = saved.map(PlayerSnapshot::position)
                    .orElseGet(world::spawnPosition);
            session.beginJoin(world, spawn);
            saved.ifPresent(snapshot -> restorePlayer(session, snapshot));
            LOGGER.info(() -> "Player joined: " + username + " (" + offlineUuid + ")"
                    + saved.map(s -> " [restored]").orElse(""));
            return new EngineBridge.Accepted(session);
        }
    }

    /**
     * Applies a saved snapshot to a joining session: look, inventory, held slot.
     * Position already came through beginJoin. Saved items the current registry
     * cannot resolve are dropped with a warning - the world moved on, the rest
     * of the survival state stays intact.
     */
    private void restorePlayer(PlayerSession session, PlayerSnapshot snapshot) {
        session.applyMovement(session.position(), snapshot.rotation(), true);
        java.util.List<net.zamin.api.ItemStack> restored = new java.util.ArrayList<>(
                java.util.Collections.nCopies(net.zamin.engine.player.PlayerInventory.TOTAL_SLOTS,
                        net.zamin.api.ItemStack.EMPTY));
        for (PlayerSnapshot.SlotStack saved : snapshot.slots()) {
            Optional<net.zamin.api.ItemType> type =
                    net.zamin.engine.item.BuiltinItems.lookup(saved.item());
            if (type.isEmpty()) {
                LOGGER.warning(() -> "Saved item no longer registered, dropped: " + saved.item());
                continue;
            }
            try {
                restored.set(saved.slot(), net.zamin.api.ItemStack.of(type.get(), saved.count())
                        .withDamage(saved.damage()));
            } catch (IllegalArgumentException invalid) {
                LOGGER.warning(() -> "Saved slot dropped (invalid values): " + saved + " - "
                        + invalid.getMessage());
            }
        }
        try {
            session.inventory().restore(restored, snapshot.heldSlot());
        } catch (IllegalArgumentException invalid) {
            LOGGER.warning("Inventory restore rejected for " + session.name() + ": "
                    + invalid.getMessage());
        }
        session.setBody(snapshot.health(), snapshot.food(), snapshot.saturation());
    }

    /** The persistable view of a live session (position is volatile-read, inventory snapshotted). */
    private PlayerSnapshot snapshotOf(PlayerSession session) {
        java.util.List<PlayerSnapshot.SlotStack> filled = new java.util.ArrayList<>();
        java.util.List<net.zamin.api.ItemStack> slots = session.inventory().snapshot();
        for (int i = 0; i < slots.size(); i++) {
            net.zamin.api.ItemStack stack = slots.get(i);
            if (stack.isEmpty()) {
                continue;
            }
            filled.add(new PlayerSnapshot.SlotStack(i, stack.type().identifier(),
                    stack.count(), stack.damage(), stack.displayName()));
        }
        return new PlayerSnapshot(session.uuid(), session.name(), session.position(),
                session.rotation(), session.inventory().heldSlot(), filled,
                session.health(), session.food(), session.saturation());
    }

    /**
     * Persists one player's state. Runs on the tick thread when called through
     * {@link #clientDisconnected} (ordered after any pending inventory work);
     * direct calls from shutdown are safe because the tick loop no longer
     * mutates player state at that point.
     */
    private void persistPlayer(PlayerSession session) {
        if (playerStore == null) {
            return;
        }
        try {
            playerStore.save(snapshotOf(session));
        } catch (RuntimeException e) {
            LOGGER.log(java.util.logging.Level.WARNING,
                    "Player save failed for " + session.name(), e);
        }
    }

    /**
     * Shutdown variant: submits the cursor-return + persist onto the tick thread
     * and waits, so the tick loop's ordering guarantees hold and the save is
     * durable before the loop stops.
     */
    private void persistPlayerNow(PlayerSession session) {
        if (playerStore == null || ticker == null) {
            return;
        }
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        ticker.submit(() -> {
            try {
                returnWindowCarriedItems(session, true);
                persistPlayer(session);
            } finally {
                done.countDown();
            }
        });
        try {
            if (!done.await(2, java.util.concurrent.TimeUnit.SECONDS)) {
                LOGGER.warning("Player save did not complete within 2s for " + session.name());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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
        // Persist on the tick thread so the save is ordered after any queued
        // inventory work for this player (pickup, wear, give). A carried cursor
        // stack goes back into the inventory first; a remainder is thrown so
        // nothing is lost (historical behavior for leaving with a held stack).
        if (ticker != null) {
            ticker.submit(() -> {
                returnWindowCarriedItems(session, true);
                persistPlayer(session);
            });
        }
        LOGGER.info(() -> "Player disconnected: " + session.name() + " (" + reason + ")");
    }

    /** Checks whether the chunk containing the position is available read-only. */
    public EngineChunk peekChunk(net.zamin.api.ChunkPosition position) {
        return world.peek(position);
    }

    /** Floor-to-block-position helper for double-space queries. */
    private static net.zamin.api.BlockPosition blockAt(double x, double y, double z) {
        return new net.zamin.api.BlockPosition(
                (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
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
