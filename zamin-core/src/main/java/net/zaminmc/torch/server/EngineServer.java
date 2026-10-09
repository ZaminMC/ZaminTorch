package net.zaminmc.torch.server;

import net.zaminmc.torch.util.Identifier;

import net.zaminmc.torch.entity.Player;
import net.zaminmc.torch.entity.PlayerState;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.item.ItemType;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.util.Rotation;
import net.zaminmc.torch.Server;
import net.zaminmc.torch.ServerState;
import net.zaminmc.torch.World;
import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.block.BlockUpdateSystem;
import net.zaminmc.torch.server.block.RandomTickSystem;
import net.zaminmc.torch.server.chest.ChestBlockEntity;
import net.zaminmc.torch.server.chest.ChestDataStore;
import net.zaminmc.torch.server.chest.ChestManager;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.crafting.CraftingService;
import net.zaminmc.torch.server.entity.FallingBlockEntityManager;
import net.zaminmc.torch.server.entity.FallingBlockEntity;
import net.zaminmc.torch.server.entity.ItemEntity;
import net.zaminmc.torch.server.entity.ItemEntityManager;
import net.zaminmc.torch.server.entity.MobDataStore;
import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.entity.MobManager;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.furnace.FurnaceBlockEntity;
import net.zaminmc.torch.server.furnace.FurnaceDataStore;
import net.zaminmc.torch.server.furnace.FurnaceManager;
import net.zaminmc.torch.server.fx.BlockSoundMap;
import net.zaminmc.torch.server.fx.FxManager;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.item.Foods;
import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.server.chat.CommandService;
import net.zaminmc.torch.server.chat.CommandSender;
import net.zaminmc.torch.server.chat.ConsoleSender;
import net.zaminmc.torch.server.interaction.DropService;
import net.zaminmc.torch.server.ops.BanStore;
import net.zaminmc.torch.server.ops.OpStore;
import net.zaminmc.torch.server.ops.WhitelistStore;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.MovementGuard;
import net.zaminmc.torch.server.player.PlayerDataStore;
import net.zaminmc.torch.server.player.PlayerRegistry;
import net.zaminmc.torch.server.player.PlayerInventory;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.player.PlayerSnapshot;
import net.zaminmc.torch.server.entity.projectile.ProjectileEntity;
import net.zaminmc.torch.server.entity.projectile.ProjectileManager;
import net.zaminmc.torch.server.world.EngineChunk;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.server.world.DeltaWorldStorage;
import net.zaminmc.torch.server.world.NormalWorldGenerator;
import net.zaminmc.torch.server.world.WorldGenerator;
import net.zaminmc.torch.server.world.FlatWorldGenerator;
import net.zaminmc.torch.server.world.WorldStorage;
import net.zaminmc.torch.server.world.WorldDeltaSnapshot;
import net.zaminmc.torch.server.block.BlockRegistryBuilder;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.ExplosionService;
import net.zaminmc.torch.server.block.FluidBlocks;
import net.zaminmc.torch.server.block.FluidSystem;
import net.zaminmc.torch.server.block.WorldSolidity;
import net.zaminmc.torch.server.chat.ChatListener;
import net.zaminmc.torch.server.chat.ChatService;
import net.zaminmc.torch.server.chat.CommandService;
import net.zaminmc.torch.server.block.BlockRegistryBuilder.FrozenBlockRegistry;
import net.zaminmc.torch.server.interaction.BlockInteractionService;
import net.zaminmc.torch.server.world.WorldChangeListener;

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
    /** Player-window armor row (wire order: head, chest, legs, feet). */
    private static final int WIRE_SLOT_ARMOR_FIRST = 5;
    private static final int WIRE_SLOT_ARMOR_LAST = 8;
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
    private FluidSystem fluidSystem;
    private ExplosionService explosionService;
    private RandomTickSystem randomTicks;
    private net.zaminmc.torch.server.world.light.LightEngine lightEngine;
    private MobDataStore mobStore;
    /** The operator registry (ops.json); loaded at boot, rewritten on /op and /deop. */
    private OpStore opStore = OpStore.load(java.nio.file.Path.of("ops.json"));
    /** The ban registry (banned-players.json) and the whitelist roster. */
    private BanStore banStore = BanStore.load(java.nio.file.Path.of("banned-players.json"));
    private WhitelistStore whitelistStore =
            WhitelistStore.load(java.nio.file.Path.of("whitelist.json"));
    /** Runtime whitelist enforcement (server.properties boot value; /whitelist on|off). */
    private volatile boolean whitelistEnforced;
    /** The weather state (the historical always-clear default; /weather drives it). */
    private volatile boolean raining;
    /** Weather countdown in ticks; -1 = holds until the next /weather. */
    private volatile long weatherTicks = -1;
    private final java.util.List<ChatListener> chatListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.List<ItemEntityManager.Listener> itemListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.List<MobManager.Listener> mobListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.List<MobSwingObserver> swingObservers = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.List<ExplosionListener> explosionListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
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
    /** Projectiles (arrows, shards) get the next band. */
    private static final int PROJECTILE_ID_BASE = FALLING_ID_BASE + 1_000_000;
    /** Player engine-global ids live above the projectiles (engine-side identity). */
    /** The band the adapter recognizes as player ids (public: wire translation). */
    public static final int PLAYER_ID_BASE = PROJECTILE_ID_BASE + 1_000_000;

    /** The game-feedback bus (sounds, particles); created at boot, read-only after. */
    private final FxManager fxManager = new FxManager();
    /** The engine's gameplay rolls (tick-thread confined; drops, spawns, floods). */
    private final java.util.Random gameplayRandom = new java.util.Random();
    /** The airborne projectiles; constructed at boot after the world exists. */
    private volatile ProjectileManager projectileManager;

    /** The FX pitch/jitter source (cosmetic rolls only, never gameplay rules). */
    private final java.util.Random fxRandom = new java.util.Random();
    /** The next engine-global player entity id (the projectile thrower band). */
    private int nextPlayerEntityId = PLAYER_ID_BASE;

    public EngineServer(EngineConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    // ------------------------------------------------------------------ lifecycle

    public EngineConfig config() {
        return config;
    }

    /** The game-feedback bus (sounds, particles) the adapter subscribes to. */
    public FxManager fx() {
        return fxManager;
    }

    /** The airborne projectiles (engine-side simulation state). */
    public ProjectileManager projectiles() {
        return projectileManager;
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
            blockRegistry = FluidBlocks.registerAll(
                    BuiltinBlocks.registerAll(new BlockRegistryBuilder())).freeze();
        }

        state.set(ServerState.STARTING);
        CountDownLatch worldReady = new CountDownLatch(1);
        AtomicReference<Throwable> bootFailure = new AtomicReference<>();

        Thread boot = new Thread(() -> {
            try {
                ticker = new EngineTicker(config.tickRateHz());
                // The Paper stores: ops.json, banned-players.json, whitelist.json
                // at the server root (Paper layout); whitelist enforcement reads
                // the server.properties flag until /whitelist on|off overrides.
                opStore = OpStore.load(java.nio.file.Path.of(config.dataDir(), "ops.json"));
                banStore = BanStore.load(java.nio.file.Path.of(config.dataDir(), "banned-players.json"));
                whitelistStore = WhitelistStore.load(java.nio.file.Path.of(config.dataDir(), "whitelist.json"));
                whitelistEnforced = config.whiteList();
                // Player persistence: one ZPD file per identity under world/playerdata.
                playerStore = new PlayerDataStore(
                        config.playerDataDir());
                // Ticker thread constructs the world so it is the owner from the start.
                Thread owner = Thread.currentThread();
                // level-type decides the terrain: flat keeps the slice fixture,
                // normal runs the full world (seas, caves, ores, forests).
                WorldGenerator generator = config.levelType().equals("flat")
                        ? new FlatWorldGenerator(blockRegistry, 4)
                        : new NormalWorldGenerator(blockRegistry, config.worldName());
                world = new EngineWorld(config.worldName(), blockRegistry, generator, owner);
                // The persisted spawn anchor (/setspawn): world/data/spawn.json
                // rides above the generator's deterministic spawn when present.
                loadSpawnAnchor().ifPresent(world::setSpawnPosition);
                // Persistence: load saved deltas before any chunk generates so the
                // spawn area is already the survived world (§407 restart proof).
                worldStorage = new DeltaWorldStorage(
                        config.worldDataDir().resolve("zamin-delta.bin"),
                        identifier -> blockRegistry.require(identifier));
                worldStorage.load().ifPresent(world::applyDeltas);
                // Light (§475/§476): derived world state, recomputed on every
                // committed change and on chunk generation. Registered FIRST
                // (before spawn pregeneration) so every generated chunk — boot
                // area included — publishes already lit (§344: publication
                // stays atomic), and as the first change listener so its
                // relight queue batches whole cascades per tick.
                lightEngine = new net.zaminmc.torch.server.world.light.LightEngine(world);
                world.addChangeListener(lightEngine);
                world.addChunkLoadListener(lightEngine);
                pregenerateSpawnArea(world);
                ticker.attachWorld(world);
                // Item entities and drops: simulation-owned systems on the world owner.
                ItemEntityManager itemEntities = new ItemEntityManager(
                        this::solidAt,
                        new java.util.Random(),
                        ENTITY_ID_BASE);
                this.itemEntities = itemEntities;
                itemEntities.addListener(new ItemEventDispatch());
                // Furnace block entities: world-state simulation, ZFD persistence.
                furnaceManager = new FurnaceManager();
                furnaceStore = new FurnaceDataStore(config.worldDataDir().resolve("furnaces.bin"));
                furnaceManager.restoreAll(furnaceStore.load());
                // Chest block entities: world-state containers, ZCD persistence.
                chestManager = new ChestManager();
                chestStore = new ChestDataStore(config.worldDataDir().resolve("chests.bin"));
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
                mobStore = new MobDataStore(config.worldDataDir().resolve("mobs.bin"));
                boolean mobsRestored = mobs.restoreAll(mobStore.load());
                if (!mobsRestored) {
                    mobs.populateInitial(world.spawnPosition());
                }
                // Falling blocks (§470): the block→entity→block transition for
                // gravity blocks; occupied landings drop as items.
                FallingBlockEntityManager falling = new FallingBlockEntityManager(
                        this::solidAt,
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
                // Fluids (§472 pattern): the scheduled pour/dry/contact system,
                // waking on every committed change like the neighbor rules do.
                fluidSystem = new FluidSystem(new FluidWorld(), new FluidSink(itemEntities));
                world.addChangeListener(fluidSystem);
                // Explosions: the ray-fan destructor (the creeper's demolition).
                explosionService = new ExplosionService(new BlastWorld(), new java.util.Random());
                // Random ticks (§471 pattern): grass growth and decay.
                randomTicks = new RandomTickSystem(world, new java.util.Random());
                // Projectiles (arrows, shards): the tick-thread physics system,
                // with damage semantics staying here in the combat callbacks.
                ProjectileManager projectiles = new ProjectileManager(
                        this::solidAt,
                        new ProjectileHitResolver(),
                        new ProjectileCombatSink(),
                        fxManager,
                        new java.util.Random(),
                        PROJECTILE_ID_BASE);
                this.projectileManager = projectiles;
                projectiles.addListener(new ProjectileEventDispatch());
                ticker.setTickHandler(() -> {
                    blockUpdateSystem.tick(); // §466: scheduled updates (falls start here)
                    fluidSystem.tick();       // §472 pattern: pours, streams, contact
                    falling.tick();           // §470: falling physics + landings
                    projectileManager.tick(); // ranged combat physics
                    furnaceManager.tick(world, itemEntities);
                    chestManager.tick(world);
                    itemEntities.tick(players.all());
                    mobs.tick(players.all(), world.timeOfDay());
                    randomTicks.tick(players.all()); // §471: grass growth/decay
                    tickFurnaceViewers();
                    tickPlayerBodies();
                    tickWeather();            // /weather's countdown (the auto-clear)
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
                        session -> session.gamemode(), new DropService(new java.util.Random()), itemEntities,
                        type -> blockRegistry.lookup(type.identifier()),
                        this::publishInventoryChanged);
                // A survival-broken furnace spills its slots, and a survival-
                // broken chest spills its 27, into the world first. The same
                // hook emits the break/place feedback through the FX bus.
                blockInteraction.setBlockBrokenListener(position -> {
                    furnaceManager.onBlockBroken(position, itemEntities);
                    chestManager.onBlockBroken(position, itemEntities);
                });
                blockInteraction.setCommitFeedbackListener(commit -> {
                    if (commit.now().equals(world.airType())) {
                        // A break: the shatter burst plus the dig family sound.
                        Position center = new Position(commit.position().x() + 0.5,
                                commit.position().y() + 0.5, commit.position().z() + 0.5);
                        fxManager.blockShatter(center, commit.previous());
                        fxManager.sound(center,
                                BlockSoundMap.digSound(commit.previous().identifier())
                                        .orElse("dig.stone"), 1.0f,
                                0.75f + fxRandom.nextFloat() * 0.2f);
                    } else if (!commit.previous().equals(commit.now())) {
                        // A place (or replace): the dig family sound at the softer
                        // historical place volume.
                        Position center = new Position(commit.position().x() + 0.5,
                                commit.position().y() + 0.5, commit.position().z() + 0.5);
                        fxManager.sound(center,
                                BlockSoundMap.digSound(commit.now().identifier())
                                        .orElse("dig.stone"), 0.8f,
                                0.8f + fxRandom.nextFloat() * 0.2f);
                    }
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
                engineWorld.getOrGenerate(new net.zaminmc.torch.block.ChunkPosition(center.x() + dx, center.z() + dz));
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
        void onFurnaceViewTick(PlayerSession viewer, net.zaminmc.torch.block.BlockPosition position,
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
        void onRespawned(PlayerSession player, net.zaminmc.torch.util.Position spawn);

        /** The player took a melee hit: the hurt flash rides the entity status. */
        void onPlayerHurt(PlayerSession player);

        /** The player was knocked back: the victim's client simulates the impulse. */
        void onKnockback(PlayerSession player, double vx, double vy, double vz);

        /**
         * The player's posture (sneak/sprint) changed: observers need the
         * living-flags metadata re-broadcast (crouch 0x02, sprint 0x10).
         * Default no-op so older listeners stay source-compatible.
         */
        default void onPostureChanged(PlayerSession player) {
        }
    }

    /** Registers an internal survival observer (e.g. the protocol adapter's sync). */
    public void addSurvivalListener(SurvivalListener listener) {
        survivalListeners.add(listener);
    }

    private void publishPostureChanged(PlayerSession player) {
        for (SurvivalListener listener : survivalListeners) {
            listener.onPostureChanged(player);
        }
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
        publishRespawned(player, world.spawnPosition());
    }

    /** The re-anchor event with an explicit destination (teleports, respawns). */
    private void publishRespawned(PlayerSession player, Position destination) {
        for (SurvivalListener listener : survivalListeners) {
            listener.onRespawned(player, destination);
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
            session.tickGrace();
            // The vanilla void: below the kill plane the out-of-world damage
            // lands every tick (the historical outOfWorld rate) until death —
            // and it pierces creative invulnerability, the historical rule.
            if (session.position().y() < MobEntity.VOID_KILL_Y) {
                damageOnTick(session, MobEntity.VOID_DAMAGE_PER_TICK, true);
            }
            tickEating(session);
            tickBowCharge(session);
            tickFoodEconomy(session);
            tickLanding(session);
            tickBreath(session);
            // The sprint exhaustion approximation: the historical rule charges
            // per meter; without a server-side mover, a flat per-tick rate of
            // ~0.6 exhaustion/second tracks the sprinting feel closely enough.
            if (session.sprinting() && session.gamemode() == GameMode.SURVIVAL) {
                session.addExhaustion(0.03f);
            }
        }
    }

    /** The bow's draw clock: charge advances while the use gesture holds. */
    private void tickBowCharge(PlayerSession session) {
        if (session.bowCharging()) {
            session.advanceBowCharge();
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
        int ticks = session.advanceEating();
        if (ticks < Foods.EAT_TICKS) {
            // The historical eating feedback: crumbs + the eating sound every
            // four ticks, from the mouth height.
            if (ticks % 4 == 1) {
                Position mouth = mouthPosition(session);
                fxManager.itemShatter(mouth, held);
                fxManager.sound(mouth, "random.eat", 0.5f,
                        0.8f + fxRandom.nextFloat() * 0.2f);
            }
            return;
        }
        session.cancelEating();
        var nutrition = Foods.nutritionOf(held.type()).orElseThrow();
        if (session.food() >= net.zaminmc.torch.server.player.PlayerSession.MAX_FOOD) {
            return; // already full (the start check cannot fully guard the window)
        }
        session.inventory().consumeHeld(1);
        int newFood = Math.min(net.zaminmc.torch.server.player.PlayerSession.MAX_FOOD,
                session.food() + nutrition.foodPoints());
        float newSaturation = Math.min(newFood,
                session.saturation() + nutrition.saturation());
        session.setBody(session.health(), newFood, newSaturation);
        fxManager.sound(mouthPosition(session), "random.burp", 0.5f,
                fxRandom.nextFloat() * 0.1f + 0.9f);
        publishInventoryChanged(session);
        publishBodyChanged(session);
    }

    /** The eye height (1.62) — where eat sounds and throw gestures originate. */
    private static Position mouthPosition(PlayerSession session) {
        Position p = session.position();
        return new Position(p.x(), p.y() + 1.62, p.z());
    }

    /** The historical 1.8 FoodStats loop on easy difficulty. */
    private void tickFoodEconomy(PlayerSession session) {
        if (session.gamemode() != GameMode.SURVIVAL
                && session.gamemode() != GameMode.ADVENTURE) {
            return; // creative and spectator bodies carry no hunger
        }
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
            // A body that lands in fluid takes no fall damage (the water break).
            var feet = session.position().toBlockPosition();
            var at = world.getBlock(feet);
            if (FluidBlocks.kindOf(at.identifier()) == null) {
                damageOnTick(session, (float) Math.ceil(distance - PlayerSession.SAFE_FALL_DISTANCE));
            }
        }
    }

    /**
     * The underwater breath clock: air drains while the eye sits in fluid;
     * after the historical 15 seconds the body drowns at 2 damage per second.
     * Creative and spectator bodies hold no breath.
     */
    private void tickBreath(PlayerSession session) {
        if (session.gamemode() == GameMode.CREATIVE
                || session.gamemode() == GameMode.SPECTATOR) {
            return;
        }
        Position eye = mouthPosition(session);
        var at = world.getBlock(new BlockPosition(
                (int) Math.floor(eye.x()), (int) Math.floor(eye.y()), (int) Math.floor(eye.z())));
        boolean underwater = FluidBlocks.kindOf(at.identifier()) != null;
        if (session.advanceBreath(underwater)) {
            damageOnTick(session, 2.0f);
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
            float damage = net.zaminmc.torch.server.item.Tools.attackDamageOf(held.type());
            // Knockback direction: attacker -> mob (the historical feel).
            double kbYaw = Math.toDegrees(Math.atan2(-dx, dz));
            mobManager.hurt(mob, damage, kbYaw);
            attacker.addExhaustion(ATTACK_EXHAUSTION);
            // Tool durability: the historical wear on a living-entity hit.
            if (net.zaminmc.torch.server.item.Tools.specOf(held.type()).isPresent()) {
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
            float damage = net.zaminmc.torch.server.item.Tools.attackDamageOf(attacker.inventory().held().type());
            if (victim.hurtInvulnerable()) {
                if (damage <= victim.lastHurtDamage()) {
                    return; // absorbed by the hurt window
                }
                damage -= victim.lastHurtDamage(); // the historical out-damage rule
            }
            final float rawDamage = damage;
            damage = applyArmor(victim, damage); // the armor envelope (the 1.8 formula)
            final float applied = damage;
            victim.beginHurtInvulnerability(rawDamage);
            victim.hurt(damage);
            attacker.addExhaustion(ATTACK_EXHAUSTION);
            if (net.zaminmc.torch.server.item.Tools.specOf(attacker.inventory().held().type()).isPresent()) {
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

    /**
     * The 1.8 armor envelope: a physical hit passes through the equipped
     * pieces (the {@code Armor.reduce} formula), the pieces take their wear
     * and any change re-syncs the client. Environmental damage (fall, drown,
     * void, starvation) never calls here — armor does not absorb it, the
     * historical rule. Tick-thread context.
     */
    private float applyArmor(PlayerSession victim, float damage) {
        int points = victim.inventory().totalArmorPoints();
        float reduced = net.zaminmc.torch.server.item.Armor.reduce(points, damage);
        if (points > 0 && reduced < damage && victim.inventory().wearArmor()) {
            publishInventoryChanged(victim); // worn or broken pieces re-sync
        }
        return reduced;
    }

    private void publishKnockback(PlayerSession victim, double vx, double vy, double vz) {
        // The knockback flight opens the guard's grace window: the client's
        // next proposals ride the launch burst legitimately.
        victim.setGraceTicks(10);
        for (SurvivalListener listener : survivalListeners) {
            listener.onKnockback(victim, vx, vy, vz);
        }
    }

    private void damageOnTick(PlayerSession session, float amount) {
        damageOnTick(session, amount, false);
    }

    /**
     * The semantic damage entry, with the mode guard: creative and spectator
     * bodies are invulnerable (the historical rule) — the only bypass is the
     * void, which consumes even creative bodies past the kill plane.
     */
    private void damageOnTick(PlayerSession session, float amount, boolean bypassProtection) {
        if (session.dead() || session.state() != PlayerState.PLAYING) {
            return;
        }
        if (!bypassProtection && (session.gamemode() == GameMode.CREATIVE
                || session.gamemode() == GameMode.SPECTATOR)) {
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
        // The armor row scatters with the inventory (the historical death drop).
        for (int armorSlot = 0; armorSlot < PlayerInventory.ARMOR_SLOTS; armorSlot++) {
            ItemStack piece = session.inventory().armorAt(armorSlot);
            if (!piece.isEmpty()) {
                spawnDeathDrop(session, piece);
            }
            session.inventory().setArmor(armorSlot, ItemStack.EMPTY);
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
                new net.zaminmc.torch.util.Position(origin.x(), origin.y() + 1.0, origin.z()), stack);
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

    // -------------------------------------------------------------- using + respawn

    /** Full-draw charge in ticks: the historical 1 second to maximum power. */
    static final int BOW_FULL_CHARGE_TICKS = 20;
    /** The minimum draw before an arrow flies (the historical flick guard). */
    static final int BOW_MIN_CHARGE_TICKS = 3;
    /** Full-draw arrow launch speed (the historical 3.0 blocks/tick). */
    static final double BOW_MAX_SPEED = 3.0;
    /** Shard (snowball/egg) launch speed (the historical 1.5). */
    static final double SHARD_SPEED = 1.5;

    /**
     * A right-click use in the air: the held item decides the semantics —
     * the bow begins its draw (ammunition checked in survival), the snowball
     * and egg throw on the press, food starts the 32-tick eat timer when the
     * player is hungry; everything else is a no-op this slice. Safe from any
     * thread.
     */
    @Override
    public void useItem(PlayerSession session) {
        Objects.requireNonNull(session, "session");
        ticker.submit(() -> {
            if (session.dead() || session.state() != PlayerState.PLAYING) {
                return;
            }
            ItemStack held = session.inventory().held();
            if (held.isEmpty()) {
                return;
            }
            // Ranged: the bow starts its draw (the release gesture fires).
            if (held.type().equals(BuiltinItems.BOW)) {
                if (session.bowCharging()) {
                    return; // already drawn
                }
                boolean creative = session.gamemode() == GameMode.CREATIVE;
                if (!creative && session.inventory().countOf(BuiltinItems.ARROW) == 0) {
                    return; // no ammunition: the historical refusal
                }
                session.beginBowCharge();
                return;
            }
            // Shards: the snowball and egg throw on the press (historical timing).
            if (held.type().equals(BuiltinItems.SNOWBALL)
                    || held.type().equals(BuiltinItems.EGG)) {
                throwShard(session, held.type());
                return;
            }
            // Food: the eat timer.
            if (Foods.nutritionOf(held.type()).isEmpty()) {
                return;
            }
            if (session.food() >= net.zaminmc.torch.server.player.PlayerSession.MAX_FOOD) {
                return; // not hungry: the historical refusal
            }
            session.beginEating();
        });
    }

    /**
     * The client released a use (dig status 5): an unfinished eat cancels; a
     * finished one was already applied by the tick timer; a drawn bow fires.
     * Safe from any thread.
     */
    @Override
    public void releaseUsingItem(PlayerSession session) {
        Objects.requireNonNull(session, "session");
        ticker.submit(() -> {
            if (session.eating()) {
                if (session.eatingTicks() < Foods.EAT_TICKS) {
                    session.cancelEating(); // released early: the historical cancel
                }
                return;
            }
            if (session.bowCharging()) {
                releaseBow(session);
            }
        });
    }

    /**
     * The bow's release: power scales with the draw (clamped at one second),
     * the arrow launches from the eyes, one unit of ammunition and one unit of
     * durability leave in survival. Tick-thread context.
     */
    private void releaseBow(PlayerSession session) {
        int charge = session.bowChargeTicks();
        session.cancelBowCharge();
        if (charge < BOW_MIN_CHARGE_TICKS) {
            return; // the flick: no shot, no wear
        }
        float power = Math.min(1.0f, (float) charge / BOW_FULL_CHARGE_TICKS);
        boolean creative = session.gamemode() == GameMode.CREATIVE;
        if (!creative && !session.inventory().consumeOne(BuiltinItems.ARROW)) {
            return; // the ammunition vanished mid-draw
        }
        launchProjectile(session, ProjectileEntity.Kind.ARROW, power * BOW_MAX_SPEED);
        if (!creative && session.inventory().damageHeld(1)) {
            // The bow's snap: worn out, the client's held slot re-syncs.
            publishInventoryChanged(session);
        }
        publishInventoryChanged(session);
    }

    /**
     * A shard throw: the projectile launches at the historical speed, one
     * unit leaves the hand in survival, the launch whoosh rides the wire.
     * Tick-thread context.
     */
    private void throwShard(PlayerSession session, net.zaminmc.torch.item.ItemType shard) {
        boolean creative = session.gamemode() == GameMode.CREATIVE;
        if (!creative) {
            if (session.inventory().held().type().equals(shard)) {
                session.inventory().consumeHeld(1);
            } else {
                return; // the hand changed between press and tick
            }
        }
        launchProjectile(session, shard.equals(BuiltinItems.EGG)
                ? ProjectileEntity.Kind.EGG : ProjectileEntity.Kind.SNOWBALL, SHARD_SPEED);
        publishInventoryChanged(session);
    }

    /**
     * The shared launch: spawn origin at the eyes, look-vector velocity, the
     * historical bow-whoosh sound. Tick-thread context.
     */
    private void launchProjectile(PlayerSession session, ProjectileEntity.Kind kind,
                                  double speed) {
        if (projectileManager == null) {
            return; // pre-boot guard (tests construct partial engines)
        }
        Position eye = mouthPosition(session);
        ProjectileEntity projectile = projectileManager.launch(kind,
                session.engineEntityId(), eye,
                session.rotation().yaw(), session.rotation().pitch(), speed);
        fxManager.sound(eye, "random.bow", 1.0f,
                (float) (1.0 / (fxRandom.nextFloat() * 0.4 + 1.2) + speed * 0.1));
        LOGGER.fine(() -> session.name() + " launched " + projectile.kind()
                + " (entity " + projectile.entityId() + ")");
    }

    /**
     * The Entity Action stream (0x0B): the posture gestures the client's own
     * physics already animates — the server tracks them for the observers'
     * flags metadata, the sprint food cost and future movement validation.
     * Actions: 0 start-sneak, 1 stop-sneak, 2 start-sprint, 3 stop-sprint
     * (4 jump-with-horse and 5 leave-bed are posture-neutral here). Safe from
     * any thread.
     */
    public void entityAction(PlayerSession session, int action) {
        Objects.requireNonNull(session, "session");
        ticker.submit(() -> {
            boolean before = session.sneaking() || session.sprinting();
            switch (action) {
                case 0 -> session.setSneaking(true);
                case 1 -> session.setSneaking(false);
                case 2 -> {
                    // The historical sprint gate: hunger below 7 refuses sprint.
                    if (session.food() > 6) {
                        session.setSprinting(true);
                    }
                }
                case 3 -> session.setSprinting(false);
                default -> {
                    return;
                }
            }
            // The flags metadata is idempotent; observers re-sync regardless of
            // whether the posture actually flipped.
            publishPostureChanged(session);
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
            // The respawn re-anchor rides the guard's grace window: the client
            // teleports to spawn and its first proposals are position bursts.
            session.setGraceTicks(100);
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
            net.zaminmc.torch.item.ItemStack dropped = session.inventory().dropHeld(entireStack);
            if (dropped.isEmpty()) {
                return;
            }
            throwFromPlayer(session, dropped);
            publishInventoryChanged(session);
        });
    }

    /** Historical throw: look direction, ~0.3 speed, small upward bias. */
    private void throwFromPlayer(PlayerSession session, net.zaminmc.torch.item.ItemStack dropped) {
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
                new net.zaminmc.torch.util.Position(
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
                    net.zaminmc.torch.item.ItemStack carried = inventory.cursor();
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
        int armorSlot = armorIndexForWireSlot(wireSlot);
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
                } else if (armorSlot >= 0) {
                    // Armor slots: kind-gated semantics (armor never stacks,
                    // the historical rule).
                    return clickArmorSlot(session, armorSlot);
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
                } else if (armorSlot >= 0) {
                    return inventory.quickMoveFromArmor(armorSlot);
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
                // Number-key swaps; armor slots swap against the hotbar too
                // (the historical ContainerPlayer mapping).
                if (armorSlot >= 0 && button >= 0 && button < 9) {
                    return inventory.swapArmorWithHotbar(armorSlot, button);
                }
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
                if (armorSlot >= 0) {
                    // Drop-click from an armor slot: one unit or the whole piece.
                    net.zaminmc.torch.item.ItemStack equipped = inventory.armorAt(armorSlot);
                    if (equipped.isEmpty()) {
                        return false;
                    }
                    net.zaminmc.torch.item.ItemStack dropped = button != 0
                            ? equipped
                            : equipped.split(1);
                    inventory.setArmor(armorSlot, button != 0
                            ? net.zaminmc.torch.item.ItemStack.EMPTY
                            : equipped);
                    throwFromPlayer(session, dropped);
                    return true;
                }
                if (wireSlot >= WIRE_SLOT_CRAFT_FIRST && wireSlot <= WIRE_SLOT_CRAFT_LAST) {
                    net.zaminmc.torch.item.ItemStack dropped = grid.dropFromCell(
                            wireSlot - WIRE_SLOT_CRAFT_FIRST, button != 0);
                    if (!dropped.isEmpty()) {
                        throwFromPlayer(session, dropped);
                    }
                    return true;
                }
                int engineSlot = engineSlotOf(wireSlot);
                if (engineSlot >= 0) {
                    net.zaminmc.torch.item.ItemStack dropped = inventory.dropFromSlot(engineSlot, button != 0);
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

    /** The armor index of a player-window wire slot (5-8), or -1. */
    private static int armorIndexForWireSlot(int wireSlot) {
        if (wireSlot >= WIRE_SLOT_ARMOR_FIRST && wireSlot <= WIRE_SLOT_ARMOR_LAST) {
            return wireSlot - WIRE_SLOT_ARMOR_FIRST;
        }
        return -1;
    }

    /**
     * The armor slots' left/right click: an empty cursor takes the piece, a
     * cursor piece of the matching kind swaps in, anything else is refused.
     * Tick-thread context.
     */
    private boolean clickArmorSlot(PlayerSession session, int armorSlot) {
        var inventory = session.inventory();
        net.zaminmc.torch.item.ItemStack equipped = inventory.armorAt(armorSlot);
        net.zaminmc.torch.item.ItemStack cursor = inventory.cursor();
        if (cursor.isEmpty()) {
            if (equipped.isEmpty()) {
                return false; // empty on empty: a no-op click
            }
            inventory.setArmor(armorSlot, net.zaminmc.torch.item.ItemStack.EMPTY);
            inventory.cursorBox().set(equipped);
            return true;
        }
        if (inventory.setArmor(armorSlot, cursor)) {
            inventory.cursorBox().set(equipped);
            return true;
        }
        return false; // wrong kind: the client reverts its prediction
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
                if (engineSlot >= net.zaminmc.torch.server.player.PlayerInventory.HOTBAR_SLOTS
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
                    net.zaminmc.torch.item.ItemStack dropped = grid.dropFromCell(
                            wireSlot - TABLE_WIRE_SLOT_GRID_FIRST, button != 0);
                    if (!dropped.isEmpty()) {
                        throwFromPlayer(session, dropped);
                    }
                    return true;
                }
                int engineSlot = tableEngineSlotOf(wireSlot);
                if (engineSlot >= 0) {
                    net.zaminmc.torch.item.ItemStack dropped = inventory.dropFromSlot(engineSlot, button != 0);
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
                if (engineSlot >= net.zaminmc.torch.server.player.PlayerInventory.HOTBAR_SLOTS
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
                    net.zaminmc.torch.item.ItemStack dropped =
                            furnaceManager.dropFromSlot(furnace, wireSlot, button != 0);
                    if (!dropped.isEmpty()) {
                        throwFromPlayer(session, dropped);
                    }
                    return true;
                }
                int engineSlot = furnaceEngineSlotOf(wireSlot);
                if (engineSlot >= 0) {
                    net.zaminmc.torch.item.ItemStack dropped =
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
                    net.zaminmc.torch.item.ItemStack peek = inventory.snapshot().get(engineSlot);
                    if (peek.isEmpty()) {
                        return false; // empty slot shift-click: nothing moves
                    }
                    net.zaminmc.torch.item.ItemStack moving = inventory.dropFromSlot(engineSlot, true);
                    net.zaminmc.torch.item.ItemStack remainder = chest.quickMoveIn(moving);
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
                if (engineSlot >= net.zaminmc.torch.server.player.PlayerInventory.HOTBAR_SLOTS
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
                    net.zaminmc.torch.item.ItemStack dropped = chest.dropFromSlot(wireSlot, button != 0);
                    if (!dropped.isEmpty()) {
                        throwFromPlayer(session, dropped);
                    }
                    return true;
                }
                int engineSlot = chestEngineSlotOf(wireSlot);
                if (engineSlot >= 0) {
                    net.zaminmc.torch.item.ItemStack dropped =
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
    private boolean takeCraftingResult(PlayerSession session, net.zaminmc.torch.server.player.CraftingGrid grid,
                                       net.zaminmc.torch.server.player.PlayerInventory inventory) {
        net.zaminmc.torch.item.ItemStack result = craftingResultOf(grid);
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
    private net.zaminmc.torch.item.ItemStack craftingResultOf(net.zaminmc.torch.server.player.CraftingGrid grid) {
        var snapshot = grid.snapshotArray();
        return (grid.cols() == 3
                        ? crafting.resultOf3x3(snapshot)
                        : crafting.resultOf(snapshot))
                .orElse(net.zaminmc.torch.item.ItemStack.EMPTY);
    }

    /**
     * Craft-all (shift-click on the result): repeats the single craft into the
     * inventory until the grid no longer matches or the inventory cannot
     * absorb the next result (the historical stop). A thrown remainder ends
     * the chain so a full inventory never spins the loop.
     */
    private void craftAllIntoInventory(PlayerSession session, net.zaminmc.torch.server.player.CraftingGrid grid,
                                       net.zaminmc.torch.server.player.PlayerInventory inventory) {
        for (int craft = 0; craft < CRAFT_ALL_LIMIT; craft++) {
            net.zaminmc.torch.item.ItemStack result = craftingResultOf(grid);
            if (result.isEmpty()) {
                return;
            }
            grid.consumeOne();
            net.zaminmc.torch.item.ItemStack remainder = inventory.pickUp(result);
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
    public net.zaminmc.torch.item.ItemStack craftingResult(PlayerSession session) {
        Objects.requireNonNull(session, "session");
        return crafting.resultOf(session.crafting().snapshotArray())
                .orElse(net.zaminmc.torch.item.ItemStack.EMPTY);
    }

    /**
     * The crafting-table preview for wire sync (container wire slot 0), empty
     * while no container is open. Same thread discipline as
     * {@link #craftingResult}.
     */
    public net.zaminmc.torch.item.ItemStack containerResult(PlayerSession session) {
        Objects.requireNonNull(session, "session");
        if (session.openContainerWindowId() < 0) {
            return net.zaminmc.torch.item.ItemStack.EMPTY;
        }
        return crafting.resultOf3x3(session.tableCrafting().snapshotArray())
                .orElse(net.zaminmc.torch.item.ItemStack.EMPTY);
    }

    /**
     * A right-click use on a block (§215 family): the engine decides on the
     * simulation context whether the target block opens a container window
     * (the crafting table) or the use degrades to a placement proposal. Safe
     * from any thread.
     */
    @Override
    public void useItemOnBlock(PlayerSession session, net.zaminmc.torch.block.BlockPosition clicked, int face,
                               java.util.Optional<net.zaminmc.torch.block.BlockType> creativeHeld,
                               java.util.function.IntConsumer onTableOpened) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(clicked, "clicked");
        Objects.requireNonNull(creativeHeld, "creativeHeld");
        Objects.requireNonNull(onTableOpened, "onTableOpened");
        ticker.submit(() -> useItemOnBlockOnTick(session, clicked, face, creativeHeld, onTableOpened));
    }

    private void useItemOnBlockOnTick(PlayerSession session, net.zaminmc.torch.block.BlockPosition clicked,
                                      int face, java.util.Optional<net.zaminmc.torch.block.BlockType> creativeHeld,
                                      java.util.function.IntConsumer onTableOpened) {
        net.zaminmc.torch.block.BlockType current = world.getBlock(clicked);
        if (current.identifier().equals(net.zaminmc.torch.server.block.BuiltinBlocks.FURNACE.identifier())) {
            openFurnaceOnTick(session, clicked, onTableOpened);
            return;
        }
        if (current.identifier().equals(net.zaminmc.torch.server.block.BuiltinBlocks.CHEST.identifier())) {
            openChestOnTick(session, clicked, onTableOpened);
            return;
        }
        if (current.identifier().equals(net.zaminmc.torch.server.block.BuiltinBlocks.CRAFTING_TABLE.identifier())) {
            openCraftingTableOnTick(session, onTableOpened);
            return;
        }
        if (useBucketOnTick(session, clicked, face)) {
            return; // the bucket did its work; no placement proposal follows
        }
        blockInteraction.placeFromUseOnTick(session, clicked, face, creativeHeld);
    }

    /**
     * The bucket flow: an empty bucket scoops a fluid source out of the
     * aimed cell; a filled bucket pours its source against the clicked face.
     * Both swap the held stack the historical way (one bucket out, one in).
     * Returns whether the use was consumed. Tick-thread context.
     */
    private boolean useBucketOnTick(PlayerSession session, BlockPosition clicked, int face) {
        net.zaminmc.torch.item.ItemType heldType = session.inventory().held().type();
        String held = heldType.identifier().toString();

        if (held.equals("minecraft:bucket")) {
            // Scoop: the aimed cell itself must be a source (the client's
            // fluid ray targets the fluid block directly).
            BlockType at = world.getBlock(clicked);
            FluidBlocks.Kind kind = FluidBlocks.kindOf(at.identifier());
            if (kind == null || !FluidBlocks.isSource(at.identifier())) {
                return false; // not a source: the historical no-op
            }
            ItemType full = kind == FluidBlocks.Kind.WATER
                    ? net.zaminmc.torch.server.item.BuiltinItems.WATER_BUCKET
                    : net.zaminmc.torch.server.item.BuiltinItems.LAVA_BUCKET;
            session.inventory().consumeHeld(1);
            throwOverflow(session, java.util.List.of(
                    session.inventory().pickUp(ItemStack.of(full, 1))));
            world.setBlock(clicked, world.airType()); // the commit wakes the fluid neighbors
            fxManager.sound(new Position(clicked.x() + 0.5, clicked.y() + 0.5, clicked.z() + 0.5),
                    "random.splash", 0.4f, 1.0f);
            return true;
        }

        if (held.equals("minecraft:water_bucket") || held.equals("minecraft:lava_bucket")) {
            // Pour: the source lands against the clicked face; the bucket
            // empties into the hand the historical way.
            BlockPosition target = offsetByFace(clicked, face);
            if (target == null || !world.getBlock(target).equals(world.airType())) {
                return false; // no open cell: the pour stays in the bucket
            }
            FluidBlocks.Kind kind = held.endsWith("water_bucket")
                    ? FluidBlocks.Kind.WATER : FluidBlocks.Kind.LAVA;
            world.setBlock(target, FluidBlocks.sourceOf(kind));
            session.inventory().consumeHeld(1);
            throwOverflow(session, java.util.List.of(session.inventory().pickUp(
                    ItemStack.of(net.zaminmc.torch.server.item.BuiltinItems.BUCKET, 1))));
            fxManager.sound(new Position(target.x() + 0.5, target.y() + 0.5, target.z() + 0.5),
                    "random.splash", 0.4f, kind == FluidBlocks.Kind.WATER ? 1.0f : 0.6f);
            return true;
        }
        return false;
    }

    /** The 1.8 face-to-offset table (0=-Y, 1=+Y, 2=-Z, 3=+Z, 4=-X, 5=+X). */
    private static BlockPosition offsetByFace(BlockPosition clicked, int face) {
        return switch (face) {
            case 0 -> clicked.offset(0, -1, 0);
            case 1 -> clicked.offset(0, 1, 0);
            case 2 -> clicked.offset(0, 0, -1);
            case 3 -> clicked.offset(0, 0, 1);
            case 4 -> clicked.offset(-1, 0, 0);
            case 5 -> clicked.offset(1, 0, 0);
            default -> null;
        };
    }

    /**
     * A player right-clicked a mob (Use Entity 0x02, mouse 0/2): the shear
     * path — shears in hand on a wooly kind take its coat. Safe from any
     * thread; the application runs on the tick thread.
     */
    public void interactEntity(PlayerSession player, int targetEntityId) {
        Objects.requireNonNull(player, "player");
        ticker.submit(() -> {
            if (player.state() != PlayerState.PLAYING || player.dead() || mobManager == null) {
                return;
            }
            MobEntity mob = mobManager.byId(targetEntityId);
            if (mob == null || mob.dead() || mob.type() != MobType.SHEEP) {
                return;
            }
            ItemStack held = player.inventory().held();
            if (!held.type().identifier().toString().equals("minecraft:shears")) {
                return; // bare hands do not shear (the historical tool gate)
            }
            double dx = mob.position().x() - player.position().x();
            double dy = mob.position().y() - player.position().y();
            double dz = mob.position().z() - player.position().z();
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            if (horizontal > MELEE_REACH + mob.type().width * 0.5 || dy < -2.0 || dy > 4.0) {
                return; // out of reach: the server-side refusal
            }
            if (mobManager.shear(mob) > 0) {
                player.inventory().damageHeld(1); // the shears wear one use
                publishInventoryChanged(player);
            }
        });
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
    private void openFurnaceOnTick(PlayerSession session, net.zaminmc.torch.block.BlockPosition position,
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
    private void openChestOnTick(PlayerSession session, net.zaminmc.torch.block.BlockPosition position,
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
    private void throwOverflow(PlayerSession session, java.util.List<net.zaminmc.torch.item.ItemStack> overflow) {
        for (net.zaminmc.torch.item.ItemStack stack : overflow) {
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
        net.zaminmc.torch.item.ItemStack leftover = session.inventory().returnCursor();
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

    /** Observers of the projectile system (the protocol adapter's wire sync). */
    public interface ProjectileListener {
        void onProjectileSpawned(ProjectileEntity projectile);

        void onProjectileMoved(ProjectileEntity projectile);

        void onProjectileLanded(ProjectileEntity projectile);

        void onProjectileRemoved(ProjectileEntity projectile, String reason);
    }

    private final java.util.List<ProjectileListener> projectileListeners =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Registers an internal projectile observer (the protocol adapter). */
    public void addProjectileListener(ProjectileListener listener) {
        projectileListeners.add(listener);
    }

    /** Fan-out from the projectile system to the observer list (§448 pattern). */
    private final class ProjectileEventDispatch implements ProjectileManager.Listener {
        @Override
        public void onProjectileSpawned(ProjectileEntity projectile) {
            for (ProjectileListener listener : projectileListeners) {
                listener.onProjectileSpawned(projectile);
            }
        }

        @Override
        public void onProjectileMoved(ProjectileEntity projectile) {
            for (ProjectileListener listener : projectileListeners) {
                listener.onProjectileMoved(projectile);
            }
        }

        @Override
        public void onProjectileLanded(ProjectileEntity projectile) {
            for (ProjectileListener listener : projectileListeners) {
                listener.onProjectileLanded(projectile);
            }
        }

        @Override
        public void onProjectileRemoved(ProjectileEntity projectile, String reason) {
            for (ProjectileListener listener : projectileListeners) {
                listener.onProjectileRemoved(projectile, reason);
            }
        }
    }

    /**
     * Resolves what a projectile is inside of: the same boxes the melee
     * checks use (mob type width/height; the 0.6 × 1.8 player box). Tick-thread.
     */
    private final class ProjectileHitResolver implements ProjectileManager.HitResolver {
        @Override
        public MobEntity mobAt(Position point) {
            return mobManager.mobAt(point);
        }

        @Override
        public PlayerSession playerAt(Position point) {
            for (PlayerSession player : players.all()) {
                if (player.state() != PlayerState.PLAYING || player.dead()) {
                    continue;
                }
                Position p = player.position();
                double dx = point.x() - p.x();
                double dy = point.y() - p.y();
                double dz = point.z() - p.z();
                if (Math.abs(dx) <= 0.3 && Math.abs(dz) <= 0.3 && dy >= -0.2 && dy <= 1.8) {
                    return player;
                }
            }
            return null;
        }
    }

    /**
     * The projectile combat rules: arrows wound (speed-scaled), shards bruise
     * (knockback without damage — the historical snowball). Both ride the
     * same hurt-window, status and knockback packets as the melee paths.
     */
    private final class ProjectileCombatSink implements ProjectileManager.CombatSink {
        @Override
        public void mobHit(MobEntity mob, float damage, double kbYaw) {
            mobManager.hurt(mob, damage, kbYaw);
        }

        @Override
        public void playerHit(PlayerSession victim, float damage, double kbYaw) {
            projectileHitPlayerOnTick(victim, damage, kbYaw);
        }

        @Override
        public void chickenHatch(Position position) {
            mobManager.spawnAt(MobType.CHICKEN, position);
            fxManager.sound(position, "mob.chicken.say", 0.5f, 1.0f);
            LOGGER.fine(() -> "An egg hatched a chick at " + position);
        }
    }

    /**
     * The zero-reach projectile hit on a player: the projectile already
     * traveled, so no reach check — but the hurt window, knockback and death
     * path stay identical to melee PvP. Tick-thread context.
     */
    private void projectileHitPlayerOnTick(PlayerSession victim, float damage, double kbYaw) {
        if (!config.pvp() || victim.dead() || victim.state() != PlayerState.PLAYING) {
            return;
        }
        // The armor envelope eats its share of a physical hit before the
        // invulnerability bookkeeping (the historical order).
        damage = applyArmor(victim, damage);
        if (victim.hurtInvulnerable() && damage <= victim.lastHurtDamage()) {
            return; // absorbed by the hurt window (a bruise out-damages nothing)
        }
        float applied = victim.hurtInvulnerable() ? Math.max(0.0f, damage - victim.lastHurtDamage())
                : damage;
        victim.beginHurtInvulnerability(Math.max(damage, 0.01f));
        if (applied > 0) {
            victim.hurt(applied);
        }
        // Knockback along the impact velocity (the historical thrown-entity bruise).
        double vx = -Math.sin(kbYaw) * 0.4;
        double vz = Math.cos(kbYaw) * 0.4;
        publishPlayerHurt(victim);
        publishKnockback(victim, vx, 0.35, vz);
        if (victim.health() <= 0) {
            dieOnTick(victim);
        } else {
            publishBodyChanged(victim);
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
            // The same-thread survival damage path; the victim's armor eats
            // its share first (the 1.8 envelope).
            damageOnTick(target, applyArmor(target, damage));
        }

        @Override
        public void onMobSound(MobEntity mob, String soundName) {
            for (MobManager.Listener listener : mobListeners) {
                listener.onMobSound(mob, soundName);
            }
        }

        @Override
        public void onMobRangedAttack(MobEntity mob, Position aimPoint) {
            // The skeleton loosed: launch the arrow through the projectile
            // system (the same physics and hit resolution player arrows ride).
            Position origin = mob.eyePosition();
            double dx = aimPoint.x() - origin.x();
            double dy = (aimPoint.y() + 1.0) - origin.y(); // aim at the torso
            double dz = aimPoint.z() - origin.z();
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
            projectileManager.launch(ProjectileEntity.Kind.ARROW, mob.entityId(),
                    origin, yaw, pitch, MobEntity.SKELETON_ARROW_SPEED);
            fxManager.sound(mob.position(), "random.bow", 1.0f, 1.0f);
            publishMobSwing(mob); // the arm swing rides the animation packet
        }

        @Override
        public void onMobFuseChanged(MobEntity mob, boolean priming) {
            for (MobManager.Listener listener : mobListeners) {
                listener.onMobFuseChanged(mob, priming);
            }
        }

        @Override
        public void onMobSheared(MobEntity mob, int woolCount) {
            for (MobManager.Listener listener : mobListeners) {
                listener.onMobSheared(mob, woolCount);
            }
        }

        @Override
        public void onMobCoatRegrown(MobEntity mob) {
            for (MobManager.Listener listener : mobListeners) {
                listener.onMobCoatRegrown(mob);
            }
        }

        @Override
        public void onMobExploded(MobEntity mob) {
            explodeOnTick(mob); // the blast is the engine's: blocks, damage, wire
        }
    }

    /** Broadcasts the arm-swing animation of one entity (the ranged shot). */
    private void publishMobSwing(MobEntity mob) {
        for (MobSwingObserver observer : swingObservers) {
            observer.onMobSwing(mob);
        }
    }

    /** The wire-side animation observer (the adapter registers one). */
    public interface MobSwingObserver {
        void onMobSwing(MobEntity mob);
    }

    /** Registers an internal swing-animation observer (the protocol adapter). */
    public void addMobSwingObserver(MobSwingObserver observer) {
        swingObservers.add(Objects.requireNonNull(observer, "observer"));
    }

    /**
     * The world queries a mob's mind and physics need, answered from the
     * world the tick thread owns. Called only on the simulation context.
     */
    private final class MobWorldQuery implements MobEntity.WorldQuery {
        @Override
        public boolean isSolid(double x, double y, double z) {
            return solidAt(x, y, z);
        }

        @Override
        public boolean inFluid(double x, double y, double z) {
            return fluidAt(x, y, z);
        }

        @Override
        public boolean clearLine(Position from, Position to) {
            // March the segment at half-block steps; one solid sample blocks
            // the shot (the cheap shooter's ray, exact enough for 1.8 eyes).
            double dx = to.x() - from.x();
            double dy = to.y() - from.y();
            double dz = to.z() - from.z();
            double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            int steps = (int) Math.ceil(length * 2.0);
            for (int i = 1; i < steps; i++) {
                double t = i / (double) steps;
                if (isSolid(from.x() + dx * t, from.y() + dy * t, from.z() + dz * t)) {
                    return false;
                }
            }
            return true;
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

    /** The world adapter the fluid system reads and commits through. */
    private final class FluidWorld implements FluidSystem.World {
        @Override
        public BlockType getBlock(BlockPosition position) {
            return world.getBlock(position);
        }

        @Override
        public void setBlock(BlockPosition position, BlockType type) {
            world.setBlock(position, type);
        }

        @Override
        public BlockType airType() {
            return world.airType();
        }

        @Override
        public long totalTicks() {
            return world.totalTicks();
        }
    }

    /** The fluid feedback: torch wash-out drops and the fizz sound. */
    private final class FluidSink implements FluidSystem.Sink {
        private final ItemEntityManager items;

        FluidSink(ItemEntityManager items) {
            this.items = items;
        }

        @Override
        public void popItem(Position at, ItemStack stack) {
            items.spawnDropAtBlock(at, stack, ItemEntity.PICKUP_DELAY_DROP_TICKS);
        }

        @Override
        public void sound(Position at, String name, float volume, float pitch) {
            fxManager.sound(at, name, volume, pitch);
        }
    }

    /** The world adapter the explosion service commits through. */
    private final class BlastWorld implements ExplosionService.World {
        @Override
        public BlockType getBlock(BlockPosition position) {
            return world.getBlock(position);
        }

        @Override
        public boolean setBlock(BlockPosition position, BlockType type) {
            return world.setBlock(position, type);
        }
    }

    /** The wire-side explosion observer (the adapter registers one). */
    public interface ExplosionListener {
        void onExplosion(ExplosionEvent event);
    }

    /** One detonation for the wire: the center, the wire radius, the removed
     * block offsets (relative i8 triples for the packet) and each hit player's
     * own knockback vector (the historical per-observer motion). */
    public record ExplosionEvent(double x, double y, double z, float radius,
                                 java.util.List<int[]> blockOffsets,
                                 java.util.Map<UUID, double[]> playerMotion) {
    }

    /** Registers an internal explosion observer (the protocol adapter). */
    public void addExplosionListener(ExplosionListener listener) {
        explosionListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /** Historical creeper power (the explosion radius in blocks). */
    static final float CREEPER_POWER = 3.0f;
    /** The blast's injury radius: double the power (the historical falloff span). */
    static final double BLAST_INJURY_RADIUS = CREEPER_POWER * 2.0;
    /** Point-blank explosion damage on easy difficulty (the historical ~24). */
    static final float BLAST_MAX_DAMAGE = 24.0f;

    /**
     * A primed creeper went off: destroy the blast sphere, roll the block
     * drops, wound players and mobs with distance falloff, and hand the
     * result to the wire (the Explosion packet carries each player's own
     * knockback — the client applies it, the historical 1.8 explosion path).
     * Tick-thread context.
     */
    private void explodeOnTick(MobEntity creeper) {
        Position center = creeper.position();
        double cx = center.x();
        double cy = center.y() + 0.5;
        double cz = center.z();
        BlockPosition blockCenter = center.toBlockPosition();

        // The destruction (each commit wakes the fluid and neighbor systems).
        java.util.List<ExplosionService.Destroyed> destroyed =
                explosionService.detonate(blockCenter, CREEPER_POWER);
        // The historical drop roll: one block in `power` survives as loot.
        java.util.List<int[]> blockOffsets = new java.util.ArrayList<>();
        for (ExplosionService.Destroyed removed : destroyed) {
            BlockPosition b = removed.position();
            blockOffsets.add(new int[]{
                    b.x() - blockCenter.x(), b.y() - blockCenter.y(), b.z() - blockCenter.z()});
            if (gameplayRandom.nextInt(Math.max(1, (int) CREEPER_POWER)) == 0) {
                net.zaminmc.torch.item.ItemType item = net.zaminmc.torch.server.item.BuiltinItems.lookup(
                        removed.type().identifier()).orElse(null);
                if (item != null) {
                    itemEntities.spawnDropAtBlock(new Position(b.x() + 0.5, b.y() + 0.5, b.z() + 0.5),
                            ItemStack.of(item, 1), ItemEntity.PICKUP_DELAY_DROP_TICKS);
                }
            }
        }

        // Player wounds: distance falloff to double the power, then the
        // historical hurt window and the per-observer knockback vector.
        java.util.Map<UUID, double[]> motion = new java.util.HashMap<>();
        for (PlayerSession player : players.all()) {
            if (player.state() != PlayerState.PLAYING) {
                continue;
            }
            Position p = player.position();
            double dx = p.x() - cx;
            double dy = (p.y() + 0.9) - cy;
            double dz = p.z() - cz;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist > BLAST_INJURY_RADIUS) {
                continue;
            }
            float damage = (float) ((1 - dist / BLAST_INJURY_RADIUS) * BLAST_MAX_DAMAGE);
            if (damage <= 0) {
                continue;
            }
            damage = applyArmor(player, damage); // armor eats its share of the blast
            double scale = (1 - dist / BLAST_INJURY_RADIUS) * 1.6;
            double mx = dist < 0.001 ? 0 : dx / dist * scale;
            double mz = dist < 0.001 ? 0 : dz / dist * scale;
            if (!player.hurtInvulnerable() || damage > player.lastHurtDamage()) {
                float applied = player.hurtInvulnerable()
                        ? Math.max(0.0f, damage - player.lastHurtDamage()) : damage;
                player.beginHurtInvulnerability(damage);
                if (applied > 0) {
                    player.hurt(applied);
                }
                publishPlayerHurt(player);
                publishKnockback(player, mx, 0.4, mz);
                if (player.health() <= 0) {
                    dieOnTick(player);
                } else {
                    publishBodyChanged(player);
                }
            }
            motion.put(player.uuid(), new double[]{mx, 0.4, mz});
        }

        // Mob wounds: the same falloff, through the manager's hurt path.
        for (MobEntity mob : mobManager.all()) {
            Position p = mob.position();
            double dx = p.x() - cx;
            double dy = (p.y() + mob.type().height * 0.5) - cy;
            double dz = p.z() - cz;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist > BLAST_INJURY_RADIUS) {
                continue;
            }
            float damage = (float) ((1 - dist / BLAST_INJURY_RADIUS) * BLAST_MAX_DAMAGE);
            if (damage <= 0) {
                continue;
            }
            mobManager.hurt(mob, damage,
                    Math.toDegrees(Math.atan2(-dx, dz))); // knockback away from the blast
        }

        // The wire: one Explosion packet per observer, motion included.
        ExplosionEvent event = new ExplosionEvent(cx, cy, cz, CREEPER_POWER,
                java.util.List.copyOf(blockOffsets), java.util.Map.copyOf(motion));
        for (ExplosionListener listener : explosionListeners) {
            listener.onExplosion(event);
        }
        LOGGER.fine(() -> "Explosion at " + blockCenter + " removed " + destroyed.size() + " blocks");
    }

    /**
     * Topmost solid block y of a column for population rolls (-1 when the
     * chunk is absent). All callers run on the simulation context, so a
     * missing chunk can be generated safely.
     */
    private int surfaceY(int x, int z) {
        EngineChunk chunk = world.peek(new net.zaminmc.torch.block.ChunkPosition(x >> 4, z >> 4));
        if (chunk == null) {
            chunk = world.getOrGenerate(new net.zaminmc.torch.block.ChunkPosition(x >> 4, z >> 4));
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
                        if (sender.opLevel() >= command.requiredLevel()) {
                            text.append(" /").append(command.name())
                                    .append(" (").append(command.description()).append(")");
                        }
                    }
                    return text.toString();
                }));
        commands.register(new CommandService.Command("ping", "Check server responsiveness",
                (sender, args) -> "pong"));
        commands.register(new CommandService.Command("give",
                "Give yourself an item: /give <name> [count] [metadata]",
                playerCommand(this::giveCommand)));
        commands.register(new CommandService.Command("time",
                "Query or set the day: /time query | /time set <day|noon|night|midnight|ticks>",
                this::timeCommand));
        commands.register(new CommandService.Command("rename",
                "Rename the held item: /rename <name...> (no name clears it)",
                0, playerCommand(this::renameCommand)));
        commands.register(new CommandService.Command("spawnmob",
                "Spawn mobs near you: /spawnmob <pig|cow|chicken|zombie|creeper|skeleton|sheep|spider> [count]",
                playerCommand(this::spawnMobCommand)));
        // The Paper operator set.
        commands.register(new CommandService.Command("gamemode",
                "Change a game mode: /gamemode <survival|creative|adventure|spectator> [player]",
                2, this::gamemodeCommand));
        commands.register(new CommandService.Command("op", "Grant operator: /op <player>",
                3, this::opCommand));
        commands.register(new CommandService.Command("deop", "Revoke operator: /deop <player>",
                3, this::deopCommand));
        commands.register(new CommandService.Command("list", "List online players",
                (sender, args) -> "There are " + players.all().size() + " of a max of "
                        + config.maxPlayers() + " players online: "
                        + players.all().stream().map(PlayerSession::name)
                                .sorted().reduce((a, b) -> a + ", " + b).orElse("(none)")));
        commands.register(new CommandService.Command("kick", "Kick a player: /kick <player> [reason]",
                2, this::kickCommand));
        commands.register(new CommandService.Command("say", "Broadcast a message: /say <message...>",
                2, this::sayCommand));
        commands.register(new CommandService.Command("save", "Save all worlds",
                4, (sender, args) -> {
                    saveAllNow();
                    return "Saved the game";
                }));
        commands.register(new CommandService.Command("stop", "Stop the server",
                4, (sender, args) -> {
                    LOGGER.info("Stop requested by " + sender.name());
                    new Thread(() -> shutdown(null), "zamin-command-stop").start();
                    return "Stopping the server";
                }));
        // Community-style single-letter aliases of /gamemode.
        commands.register(new CommandService.Command("gms", "Shortcut: /gamemode survival",
                2, (sender, args) -> gamemodeCommand(sender, prepend(args, "survival"))));
        commands.register(new CommandService.Command("gmc", "Shortcut: /gamemode creative",
                2, (sender, args) -> gamemodeCommand(sender, prepend(args, "creative"))));
        commands.register(new CommandService.Command("gma", "Shortcut: /gamemode adventure",
                2, (sender, args) -> gamemodeCommand(sender, prepend(args, "adventure"))));
        commands.register(new CommandService.Command("gmsp", "Shortcut: /gamemode spectator",
                2, (sender, args) -> gamemodeCommand(sender, prepend(args, "spectator"))));
        // The Paper movement / survival operator set.
        commands.register(new CommandService.Command("tp",
                "Teleport: /tp <x> <y> <z> | /tp <player> | /tp <from> <to>",
                2, this::tpCommand));
        commands.register(new CommandService.Command("kill",
                "Kill a player: /kill [player]",
                2, this::killCommand));
        commands.register(new CommandService.Command("heal",
                "Restore health and hunger: /heal [player]",
                2, this::healCommand));
        commands.register(new CommandService.Command("feed",
                "Restore hunger: /feed [player]",
                2, this::feedCommand));
        commands.register(new CommandService.Command("spawn",
                "Teleport to the world spawn point",
                0, playerCommand(this::spawnTpCommand)));
        commands.register(new CommandService.Command("setspawn",
                "Move the world spawn to where you stand",
                2, playerCommand(this::setSpawnCommand)));
        commands.register(new CommandService.Command("fly",
                "Toggle personal flight: /fly [on|off]",
                2, playerCommand(this::flyCommand)));
        commands.register(new CommandService.Command("clear",
                "Empty a player's inventory: /clear [player]",
                2, this::clearCommand));
        commands.register(new CommandService.Command("ban",
                "Ban a player: /ban <player> [reason...]",
                3, this::banCommand));
        commands.register(new CommandService.Command("pardon",
                "Lift a ban: /pardon <player>",
                3, this::pardonCommand));
        commands.register(new CommandService.Command("banlist",
                "List the banned players",
                3, this::banlistCommand));
        commands.register(new CommandService.Command("whitelist",
                "Whitelist control: /whitelist on|off|add|remove|list",
                3, this::whitelistCommand));
        commands.register(new CommandService.Command("weather",
                "Set the weather: /weather <clear|rain> [seconds]",
                2, this::weatherCommand));
    }

    private static String[] prepend(String[] args, String first) {
        String[] all = new String[args.length + 1];
        all[0] = first;
        System.arraycopy(args, 0, all, 1, args.length);
        return all;
    }

    // -------------------------------------------------------------- movement / body commands

    /**
     * /tp: the operator teleport set — coordinates, a named target, or a
     * from→to move. The re-anchor rides the respawn wire path (chunk tracker
     * reset + Position and Look) with a guard grace window.
     */
    private String tpCommand(CommandSender sender, String[] args) {
        if (args.length == 3) {
            PlayerSession self = sender.player();
            if (self == null) {
                return "Console must name a player: /tp <player> <x> <y> <z> is a later form.";
            }
            double x, y, z;
            try {
                x = Double.parseDouble(args[0]);
                y = Double.parseDouble(args[1]);
                z = Double.parseDouble(args[2]);
            } catch (NumberFormatException malformed) {
                return "Coordinates must be numbers: /tp <x> <y> <z>";
            }
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                    || y < net.zaminmc.torch.block.BlockPosition.MIN_Y
                    || y > net.zaminmc.torch.block.BlockPosition.MAX_Y) {
                return "Coordinates out of bounds";
            }
            teleportOnTick(self, new Position(x, y, z));
            return String.format(java.util.Locale.ROOT, "Teleported %s to %.1f %.1f %.1f",
                    self.name(), x, y, z);
        }
        if (args.length == 1) {
            PlayerSession self = sender.player();
            if (self == null) {
                return "Console must name two players: /tp <from> <to>";
            }
            PlayerSession target = players.byName(args[0]).orElse(null);
            if (target == null) {
                return "No online player named " + args[0];
            }
            teleportOnTick(self, target.position());
            return "Teleported you to " + target.name();
        }
        if (args.length == 2) {
            PlayerSession from = players.byName(args[0]).orElse(null);
            PlayerSession to = players.byName(args[1]).orElse(null);
            if (from == null || to == null) {
                return "No online player named " + (from == null ? args[0] : args[1]);
            }
            teleportOnTick(from, to.position());
            return "Teleported " + from.name() + " to " + to.name();
        }
        return "Usage: /tp <x> <y> <z> | /tp <player> | /tp <from> <to>";
    }

    /**
     * The engine-side teleport: re-anchors the session on the tick thread,
     * opens the guard's grace window and reuses the respawn wire path (the
     * chunk tracker re-anchors and Position and Look re-syncs the client).
     */
    private void teleportOnTick(PlayerSession session, Position to) {
        ticker.submit(() -> {
            if (session.state() != PlayerState.PLAYING || session.dead()) {
                return;
            }
            session.applyMovement(to, session.rotation(), false);
            session.setGraceTicks(100);
            session.consumeFallDistance(); // the teleport cancels fall debt
            publishRespawned(session, to);
            LOGGER.fine(() -> "Teleported " + session.name() + " to " + to);
        });
    }

    /** /kill [player]: the void-equivalent death (pierces protection, historical rule). */
    private String killCommand(CommandSender sender, String[] args) {
        PlayerSession target = resolveTarget(sender, args);
        if (target == null) {
            return "Usage: /kill [player]";
        }
        ticker.submit(() -> {
            if (target.state() == PlayerState.PLAYING && !target.dead()) {
                dieOnTick(target);
            }
        });
        return "Killed " + target.name();
    }

    /** /heal [player]: full hearts, full hunger, the historical saturation reset. */
    private String healCommand(CommandSender sender, String[] args) {
        PlayerSession target = resolveTarget(sender, args);
        if (target == null) {
            return "Usage: /heal [player]";
        }
        ticker.submit(() -> {
            if (target.state() != PlayerState.PLAYING || target.dead()) {
                return;
            }
            target.setBody(PlayerSession.MAX_HEALTH, PlayerSession.MAX_FOOD,
                    PlayerSession.DEFAULT_SATURATION);
            target.setExhaustion(0);
            target.resetFallDistance();
            publishBodyChanged(target);
        });
        return "Healed " + target.name();
    }

    /** /feed [player]: full hunger bar with the default saturation. */
    private String feedCommand(CommandSender sender, String[] args) {
        PlayerSession target = resolveTarget(sender, args);
        if (target == null) {
            return "Usage: /feed [player]";
        }
        ticker.submit(() -> {
            if (target.state() != PlayerState.PLAYING || target.dead()) {
                return;
            }
            target.setBody(target.health(), PlayerSession.MAX_FOOD,
                    PlayerSession.DEFAULT_SATURATION);
            target.setExhaustion(0);
            publishBodyChanged(target);
        });
        return "Fed " + target.name();
    }

    /** The command target resolver: an explicit name, or the sending player. */
    private PlayerSession resolveTarget(CommandSender sender, String[] args) {
        if (args.length >= 1) {
            PlayerSession named = players.byName(args[0]).orElse(null);
            if (named == null) {
                return null;
            }
            return named;
        }
        return sender.player();
    }

    /** /spawn: the player's trip back to the world spawn anchor. */
    private String spawnTpCommand(PlayerSession sender, String[] args) {
        Position spawn = world.spawnPosition();
        teleportOnTick(sender, spawn);
        return "Teleported you to the world spawn";
    }

    /** /setspawn: moves the world spawn anchor and persists world/data/spawn.json. */
    private String setSpawnCommand(PlayerSession sender, String[] args) {
        Position at = sender.position();
        world.setSpawnPosition(at);
        saveSpawnAnchor(at);
        LOGGER.info(() -> "World spawn moved to " + at + " by " + sender.name());
        return String.format(java.util.Locale.ROOT, "World spawn set to %.1f %.1f %.1f",
                at.x(), at.y(), at.z());
    }

    /** The persisted spawn anchor (world/data/spawn.json: x, y, z). */
    private java.util.Optional<Position> loadSpawnAnchor() {
        java.nio.file.Path file = config.worldDataDir().resolve("spawn.json");
        if (!java.nio.file.Files.exists(file)) {
            return java.util.Optional.empty();
        }
        try {
            String json = java.nio.file.Files.readString(file, java.nio.charset.StandardCharsets.UTF_8);
            double x = Double.parseDouble(fieldOf(json, "x"));
            double y = Double.parseDouble(fieldOf(json, "y"));
            double z = Double.parseDouble(fieldOf(json, "z"));
            return java.util.Optional.of(new Position(x, y, z));
        } catch (Exception e) {
            LOGGER.warning(() -> "Unreadable spawn.json ignored: " + e.getMessage());
            return java.util.Optional.empty();
        }
    }

    private void saveSpawnAnchor(Position at) {
        java.nio.file.Path file = config.worldDataDir().resolve("spawn.json");
        try {
            java.nio.file.Files.createDirectories(file.getParent());
            java.nio.file.Files.writeString(file,
                    String.format(java.util.Locale.ROOT,
                            "{\"x\": %.4f, \"y\": %.4f, \"z\": %.4f}%n",
                            at.x(), at.y(), at.z()),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            LOGGER.warning(() -> "spawn.json write failed: " + e.getMessage());
        }
    }

    private static String fieldOf(String json, String key) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "\"" + key + "\"\\s*:\\s*(-?[0-9.]+)").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    /** /fly [on|off]: toggles personal flight (an admin tool; creative flies anyway). */
    private String flyCommand(PlayerSession sender, String[] args) {
        boolean enable = args.length >= 1
                ? args[0].equalsIgnoreCase("on")
                : !sender.allowedToFly();
        sender.setAllowedToFly(enable);
        if (!enable) {
            sender.setFlying(false);
            sender.clearHoverTicks();
        }
        sender.link().updateAbilities(abilitiesFlagsFor(sender));
        return enable ? "Flight enabled" : "Flight disabled";
    }

    /** The protocol-47 abilities flags for a session (the /fly + mode map). */
    private static int abilitiesFlagsFor(PlayerSession session) {
        int flags = 0;
        if (session.gamemode() == GameMode.CREATIVE || session.gamemode() == GameMode.SPECTATOR) {
            flags |= 0x01 | 0x04 | 0x08; // invulnerable + may-fly + instant build
        }
        if (session.allowedToFly()) {
            flags |= 0x04; // may-fly for the /fly grant
        }
        if (session.flying() && session.allowedToFly()) {
            flags |= 0x02; // keep the announced flight state
        }
        return flags;
    }

    /** /clear [player]: empties the inventory (the historical op 2 cleanup). */
    private String clearCommand(CommandSender sender, String[] args) {
        PlayerSession target = resolveTarget(sender, args);
        if (target == null) {
            return "Usage: /clear [player]";
        }
        ticker.submit(() -> {
            target.inventory().clear();
            publishInventoryChanged(target);
        });
        return "Cleared " + target.name() + "'s inventory";
    }

    // -------------------------------------------------------------- ban / whitelist / weather

    /** /ban &lt;player&gt; [reason...]: bans and kicks (the historical op 3 gate). */
    private String banCommand(CommandSender sender, String[] args) {
        if (args.length < 1) {
            return "Usage: /ban <player> [reason...]";
        }
        String name = args[0];
        String reason = args.length >= 2
                ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length))
                : null;
        java.util.UUID uuid = players.byName(name)
                .map(PlayerSession::uuid)
                .orElseGet(() -> java.util.UUID.nameUUIDFromBytes(
                        ("OfflinePlayer:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        banStore.ban(uuid, name, sender.name(), reason);
        players.byName(name).ifPresent(online ->
                online.link().kick("You are banned from this server.\nReason: "
                        + (reason == null ? "Banned by an operator" : reason)));
        LOGGER.info(() -> "Banned " + name + " (by " + sender.name() + ")");
        return "Banned " + name;
    }

    /** /pardon &lt;player&gt;: lifts the ban by name. */
    private String pardonCommand(CommandSender sender, String[] args) {
        if (args.length < 1) {
            return "Usage: /pardon <player>";
        }
        return banStore.pardon(args[0])
                ? "Unbanned " + args[0]
                : "No ban found for " + args[0];
    }

    /** /banlist: the entries of banned-players.json (the historical output). */
    private String banlistCommand(CommandSender sender, String[] args) {
        var entries = banStore.entries();
        if (entries.isEmpty()) {
            return "There are no bans";
        }
        return "There are " + entries.size() + " ban(s): "
                + entries.stream()
                        .map(e -> e.name() + (e.permanent() ? "" : " (until " + e.expires() + ")"))
                        .sorted()
                        .reduce((a, b) -> a + ", " + b).orElse("(none)");
    }

    /** /whitelist on|off|add|remove|list: the roster + the runtime enforcement flag. */
    private String whitelistCommand(CommandSender sender, String[] args) {
        if (args.length < 1) {
            return "Usage: /whitelist on|off|add|remove|list";
        }
        return switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
            case "on" -> {
                whitelistEnforced = true;
                yield "Whitelist is now enforced";
            }
            case "off" -> {
                whitelistEnforced = false;
                yield "Whitelist is now off";
            }
            case "add" -> {
                if (args.length < 2) {
                    yield "Usage: /whitelist add <player>";
                }
                java.util.UUID uuid = players.byName(args[1])
                        .map(PlayerSession::uuid)
                        .orElseGet(() -> java.util.UUID.nameUUIDFromBytes(
                                ("OfflinePlayer:" + args[1]).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                whitelistStore.add(uuid, args[1]);
                yield "Added " + args[1] + " to the whitelist";
            }
            case "remove" -> {
                if (args.length < 2) {
                    yield "Usage: /whitelist remove <player>";
                }
                yield whitelistStore.remove(args[1])
                        ? "Removed " + args[1] + " from the whitelist"
                        : "No whitelist entry for " + args[1];
            }
            case "list" -> {
                var entries = whitelistStore.entries();
                yield entries.isEmpty() ? "The whitelist is empty"
                        : "There are " + entries.size() + " whitelisted player(s): "
                                + entries.stream().map(e -> e.name()).sorted()
                                        .reduce((a, b) -> a + ", " + b).orElse("(none)");
            }
            default -> "Usage: /whitelist on|off|add|remove|list";
        };
    }

    /** /weather &lt;clear|rain&gt; [seconds]: the Change Game State broadcast. */
    private String weatherCommand(CommandSender sender, String[] args) {
        if (args.length < 1 || !(args[0].equalsIgnoreCase("clear")
                || args[0].equalsIgnoreCase("rain"))) {
            return "Usage: /weather <clear|rain> [seconds]";
        }
        boolean target = args[0].equalsIgnoreCase("rain");
        long durationTicks = -1;
        if (args.length >= 2) {
            try {
                durationTicks = Long.parseLong(args[1]) * 20L;
            } catch (NumberFormatException malformed) {
                return "Duration must be seconds: /weather <clear|rain> [seconds]";
            }
        }
        setWeather(target, durationTicks);
        return target ? "Set the weather to rain" : "Set the weather to clear";
    }

    /**
     * Weather commit: the state flips, the broadcast fans out through every
     * link (Change Game State 1/2), and a duration schedules the auto-clear.
     * Safe from any thread.
     */
    public void setWeather(boolean rain, long durationTicks) {
        this.raining = rain;
        this.weatherTicks = durationTicks;
        for (PlayerSession player : players.all()) {
            player.link().updateWeather(rain);
        }
        LOGGER.info(() -> "Weather set to " + (rain ? "rain" : "clear"));
    }

    /** @return whether rain is falling (the join's initial state push). */
    public boolean raining() {
        return raining;
    }

    /** The weather countdown (the tick handler drives the auto-clear). */
    private void tickWeather() {
        if (weatherTicks > 0 && !raining) {
            return;
        }
        long left = weatherTicks;
        if (left > 0) {
            left--;
            weatherTicks = left;
            if (left == 0) {
                setWeather(false, -1);
            }
        }
    }

    /** Adapter for gameplay commands that only an in-world player may use. */
    private static java.util.function.BiFunction<CommandSender, String[], String> playerCommand(
            java.util.function.BiFunction<PlayerSession, String[], String> executor) {
        return (sender, args) -> {
            PlayerSession player = sender.player();
            if (player == null) {
                return "This command must be run by a player.";
            }
            return executor.apply(player, args);
        };
    }

    /**
     * /gamemode &lt;mode&gt; [player]: switches the per-player game mode.
     * The target learns through Change Game State 3 + Player Abilities; the
     * server-side interaction rules follow the session's mode from now on.
     */
    private String gamemodeCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            return "Usage: /gamemode <survival|creative|adventure|spectator> [player]";
        }
        GameMode mode;
        try {
            mode = GameMode.parse(args[0]);
        } catch (IllegalArgumentException unknown) {
            return "Unknown game mode: " + args[0];
        }
        PlayerSession target;
        if (args.length >= 2) {
            target = players.byName(args[1]).orElse(null);
            if (target == null) {
                return "No online player named " + args[1];
            }
        } else {
            if (sender.player() == null) {
                return "Console must name a player: /gamemode <mode> <player>";
            }
            target = sender.player();
        }
        setGamemode(target, mode);
        return "Set " + target.name() + "'s game mode to "
                + mode.name().toLowerCase(java.util.Locale.ROOT);
    }

    /** /op &lt;player&gt;: grants the operator level and persists ops.json. */
    private String opCommand(CommandSender sender, String[] args) {
        if (args.length < 1) {
            return "Usage: /op <player>";
        }
        PlayerSession target = players.byName(args[0]).orElse(null);
        if (target == null) {
            return "No online player named " + args[0];
        }
        opStore.op(target.uuid(), target.name(), 4);
        target.setOpLevel(4);
        systemMessage(target, "You are now op!");
        LOGGER.info("Made " + target.name() + " a server operator (by " + sender.name() + ")");
        return "Made " + target.name() + " a server operator";
    }

    /** /deop &lt;player&gt;: revokes the operator level and persists ops.json. */
    private String deopCommand(CommandSender sender, String[] args) {
        if (args.length < 1) {
            return "Usage: /deop <player>";
        }
        PlayerSession target = players.byName(args[0]).orElse(null);
        if (target == null) {
            // Offline revoke: ops.json may still carry the name.
            return opStore.deop(args[0]) ? "Revoked operator status of " + args[0]
                    : "No online player named " + args[0];
        }
        boolean removed = opStore.deop(target.name());
        target.setOpLevel(0);
        systemMessage(target, "You are no longer op!");
        LOGGER.info("Revoked operator status of " + target.name() + " (by " + sender.name() + ")");
        return removed ? "Revoked operator status of " + target.name()
                : target.name() + " was not an operator";
    }

    /** /kick &lt;player&gt; [reason...]: the disconnect with the operator's words. */
    private String kickCommand(CommandSender sender, String[] args) {
        if (args.length < 1) {
            return "Usage: /kick <player> [reason...]";
        }
        PlayerSession target = players.byName(args[0]).orElse(null);
        if (target == null) {
            return "No online player named " + args[0];
        }
        String reason = args.length >= 2
                ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length))
                : "Kicked by an operator.";
        target.link().kick(reason);
        return "Kicked " + target.name() + ": " + reason;
    }

    /** /say &lt;message...&gt;: the historical broadcast with the [Server] tag. */
    private String sayCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            return "Usage: /say <message...>";
        }
        String line = "[" + sender.name() + "] " + String.join(" ", args);
        for (PlayerSession player : players.all()) {
            systemMessage(player, line);
        }
        LOGGER.info(line);
        return null; // the broadcast itself is the feedback
    }

    /**
     * Pushes a system line to one player (command feedback, /say, op notes).
     * Safe from any thread; delivery rides the tick thread like chat.
     */
    public void systemMessage(PlayerSession recipient, String content) {
        ticker.submit(() -> {
            for (ChatListener listener : chatListeners) {
                listener.onSystemMessage(recipient, content);
            }
        });
    }

    /**
     * Switches a player's game mode (the /gamemode path): the session state
     * flips on the tick thread, then Change Game State 3 + Player Abilities
     * tell the client, and the body display re-syncs.
     */
    public void setGamemode(PlayerSession session, GameMode mode) {
        java.util.Objects.requireNonNull(session, "session");
        java.util.Objects.requireNonNull(mode, "mode");
        ticker.submit(() -> setGamemodeOnTick(session, mode));
    }

    private void setGamemodeOnTick(PlayerSession session, GameMode mode) {
        if (session.state() != PlayerState.PLAYING) {
            return;
        }
        session.setGamemode(mode);
        session.link().updateGamemode(mode.legacyId());
        // The protocol-47 abilities map: 0x01 invulnerable, 0x02 flying,
        // 0x04 may-fly, 0x08 instant build — the historical grant. Flight
        // rights follow the mode (the movement guard reads the same flag).
        int flags = 0;
        boolean mayFly = mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR;
        if (mayFly) {
            flags |= 0x01 | 0x04 | 0x08;
        }
        session.setAllowedToFly(mayFly);
        if (!mayFly) {
            session.setFlying(false); // leaving flight: the client re-announces
            session.clearHoverTicks();
        } else {
            session.setGraceTicks(40); // the mode-switch re-anchor burst
        }
        session.link().updateAbilities(flags);
        publishBodyChanged(session);
        LOGGER.info("Game mode of " + session.name() + " set to "
                + mode.name().toLowerCase(java.util.Locale.ROOT));
    }

    /**
     * Set Creative Slot (0x10): the creative inventory's authoritative write.
     * Creative sessions only; the client-claimed stack lands in the mapped
     * engine slot (window 0's historical ContainerPlayer mapping). Safe from
     * any thread; the write runs on the tick thread.
     */
    public void setCreativeSlot(PlayerSession session, int wireSlot,
                                net.zaminmc.torch.item.ItemStack stack) {
        java.util.Objects.requireNonNull(session, "session");
        java.util.Objects.requireNonNull(stack, "stack");
        ticker.submit(() -> setCreativeSlotOnTick(session, wireSlot, stack));
    }

    private void setCreativeSlotOnTick(PlayerSession session, int wireSlot,
                                       net.zaminmc.torch.item.ItemStack stack) {
        if (session.state() != PlayerState.PLAYING
                || session.gamemode() != GameMode.CREATIVE) {
            return; // the vanilla guard against faked creative packets
        }
        if (wireSlot >= WIRE_SLOT_CRAFT_FIRST && wireSlot <= WIRE_SLOT_CRAFT_LAST) {
            session.crafting().setCell(wireSlot - WIRE_SLOT_CRAFT_FIRST, stack);
        } else if (wireSlot >= WIRE_SLOT_CRAFT_LAST + 1 && wireSlot <= WIRE_SLOT_CRAFT_LAST + 4) {
            return; // armor cells ride the window clicks, not the creative menu
        } else {
            int engineSlot = engineSlotOf(wireSlot);
            if (engineSlot < 0) {
                return; // result slot and unknown cells: not a creative write
            }
            session.inventory().setSlot(engineSlot, stack);
        }
        publishInventoryChanged(session);
    }

    /**
     * The console command line routed through the same dispatcher the chat
     * slash commands use (ConsoleSender, full operator level). Safe from any
     * thread; the dispatch runs on the tick thread like player commands.
     */
    public void consoleCommand(String raw) {
        ticker.submit(() -> chatService.dispatchCommand(new ConsoleSender(), raw));
    }

    /**
     * /time query, or /time set &lt;day|noon|night|midnight|ticks&gt;: moves the
     * world's day clock (the same value Time Update 0x03 carries). Runs on the
     * tick thread through chat dispatch; every client hears the new time
     * through the time listeners.
     */
    private String timeCommand(CommandSender sender, String[] args) {
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
        if (name.length() > net.zaminmc.torch.item.ItemStack.MAX_NAME_LENGTH) {
            return "Name too long (max " + net.zaminmc.torch.item.ItemStack.MAX_NAME_LENGTH + ")";
        }
        sender.inventory().renameHeld(name);
        publishInventoryChanged(sender);
        return name.isEmpty() ? "Name cleared" : "Renamed to " + name;
    }

    private String spawnMobCommand(PlayerSession sender, String[] args) {
        if (args.length < 1) {
            return "Usage: /spawnmob <pig|cow|chicken|sheep|zombie|skeleton|creeper|spider> [count]";
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
        net.zaminmc.torch.item.ItemType type = net.zaminmc.torch.server.item.BuiltinItems.lookup(
                net.zaminmc.torch.util.Identifier.parse(name)).orElse(null);
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
            if (metadata < 0 || metadata > net.zaminmc.torch.item.ItemStack.MAX_DAMAGE) {
                return "Metadata must be 0.." + net.zaminmc.torch.item.ItemStack.MAX_DAMAGE;
            }
        }
        net.zaminmc.torch.item.ItemStack granted;
        try {
            granted = net.zaminmc.torch.item.ItemStack.of(type, count).withDamage(metadata);
        } catch (IllegalArgumentException invalidVariant) {
            return "Cannot grant that metadata: " + invalidVariant.getMessage();
        }
        net.zaminmc.torch.item.ItemStack remainder = sender.inventory().pickUp(granted);
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
        // The ban gate (the historical banned-players.json check, offline-uuid
        // keyed): a banned name stays banned across relogins.
        BanStore.Entry ban = banStore.banOf(offlineUuid, username);
        if (ban != null) {
            return new EngineBridge.Rejected("You are banned from this server.\nReason: "
                    + ban.reason());
        }
        // The whitelist gate: only when enforcement is on (server.properties
        // boot flag, or /whitelist on at runtime).
        if (whitelistEnforced && !whitelistStore.contains(offlineUuid, username)) {
            return new EngineBridge.Rejected("You are not whitelisted on this server!");
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
            // The engine-global identity (the projectile thrower band) rides in
            // from here; the wire-local ids remain the adapter's business.
            session.assignEngineEntityId(nextPlayerEntityId++);
            players.register(session);
            session.authenticate();
            // The personal game mode survives restarts (ZPD v4); a fresh player
            // inherits the server default from server.properties.
            session.setGamemode(saved.map(PlayerSnapshot::gamemodeId)
                    .filter(id -> id >= 0)
                    .map(GameMode::byLegacyId)
                    .filter(java.util.Objects::nonNull)
                    .orElse(config.gamemode()));
            session.setOpLevel(opStore.level(offlineUuid));
            Position spawn = saved.map(PlayerSnapshot::position)
                    .orElseGet(world::spawnPosition);
            session.beginJoin(world, spawn);
            // The join burst (spawn chunks, position re-anchor) rides grace.
            session.setGraceTicks(100);
            // Flight rights follow the mode: creative and spectator may fly.
            session.setAllowedToFly(session.gamemode() == GameMode.CREATIVE
                    || session.gamemode() == GameMode.SPECTATOR);
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
        java.util.List<net.zaminmc.torch.item.ItemStack> restored = new java.util.ArrayList<>(
                java.util.Collections.nCopies(net.zaminmc.torch.server.player.PlayerInventory.TOTAL_SLOTS,
                        net.zaminmc.torch.item.ItemStack.EMPTY));
        java.util.List<net.zaminmc.torch.item.ItemStack> armorRestored = new java.util.ArrayList<>(
                java.util.Collections.nCopies(net.zaminmc.torch.server.player.PlayerInventory.ARMOR_SLOTS,
                        net.zaminmc.torch.item.ItemStack.EMPTY));
        for (PlayerSnapshot.SlotStack saved : snapshot.slots()) {
            Optional<net.zaminmc.torch.item.ItemType> type =
                    net.zaminmc.torch.server.item.BuiltinItems.lookup(saved.item());
            if (type.isEmpty()) {
                LOGGER.warning(() -> "Saved item no longer registered, dropped: " + saved.item());
                continue;
            }
            try {
                net.zaminmc.torch.item.ItemStack stack = net.zaminmc.torch.item.ItemStack.of(type.get(), saved.count())
                        .withDamage(saved.damage());
                if (saved.slot() >= net.zaminmc.torch.server.player.PlayerInventory.ARMOR_BASE) {
                    // ZPD v5 namespace: slots 36-39 are the armor row (head, chest, legs, feet).
                    armorRestored.set(saved.slot()
                            - net.zaminmc.torch.server.player.PlayerInventory.ARMOR_BASE, stack);
                } else {
                    restored.set(saved.slot(), stack);
                }
            } catch (IllegalArgumentException invalid) {
                LOGGER.warning(() -> "Saved slot dropped (invalid values): " + saved + " - "
                        + invalid.getMessage());
            }
        }
        try {
            session.inventory().restore(restored, snapshot.heldSlot());
            session.inventory().restoreArmor(armorRestored);
        } catch (IllegalArgumentException invalid) {
            LOGGER.warning("Inventory restore rejected for " + session.name() + ": "
                    + invalid.getMessage());
        }
        session.setBody(snapshot.health(), snapshot.food(), snapshot.saturation());
    }

    /** The persistable view of a live session (position is volatile-read, inventory snapshotted). */
    private PlayerSnapshot snapshotOf(PlayerSession session) {
        java.util.List<PlayerSnapshot.SlotStack> filled = new java.util.ArrayList<>();
        java.util.List<net.zaminmc.torch.item.ItemStack> slots = session.inventory().snapshot();
        for (int i = 0; i < slots.size(); i++) {
            net.zaminmc.torch.item.ItemStack stack = slots.get(i);
            if (stack.isEmpty()) {
                continue;
            }
            filled.add(new PlayerSnapshot.SlotStack(i, stack.type().identifier(),
                    stack.count(), stack.damage(), stack.displayName()));
        }
        // ZPD v5: the armor row rides the slot namespace at 36-39 (head, chest, legs, feet).
        java.util.List<net.zaminmc.torch.item.ItemStack> armor = session.inventory().armorSnapshot();
        for (int i = 0; i < armor.size(); i++) {
            net.zaminmc.torch.item.ItemStack piece = armor.get(i);
            if (piece.isEmpty()) {
                continue;
            }
            filled.add(new PlayerSnapshot.SlotStack(
                    net.zaminmc.torch.server.player.PlayerInventory.ARMOR_BASE + i,
                    piece.type().identifier(), piece.count(), piece.damage(), piece.displayName()));
        }
        return new PlayerSnapshot(session.uuid(), session.name(), session.position(),
                session.rotation(), session.inventory().heldSlot(), filled,
                session.health(), session.food(), session.saturation(),
                session.gamemode().legacyId());
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
        // The join learns the sky state (a rainy world never looks wrong).
        session.link().updateWeather(raining);
        LOGGER.info(() -> "Player in play state: " + session.name());
    }

    @Override
    public void movementProposal(PlayerSession session, Position position, Rotation rotation, boolean onGround) {
        // The anti-cheat baseline (the mango-adopted shape): proposals outside
        // the historical physics envelope are dropped and the client is
        // snapped back to the authoritative position — the historical
        // "moved wrongly" behavior. The grace window (teleports, knockback,
        // join bursts) suspends the speed caps for honest bursts.
        Position from = session.position();
        if (!MovementGuard.permits(session, from, position, onGround,
                fluidAt(from.x(), from.y(), from.z()))) {
            session.link().resyncPosition();
            LOGGER.fine(() -> "Movement rejected for " + session.name()
                    + " (" + from + " -> " + position + ")");
            return;
        }
        // Per-channel ordering (transport guarantee) makes this safe.
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
    public EngineChunk peekChunk(net.zaminmc.torch.block.ChunkPosition position) {
        return world.peek(position);
    }

    /** Floor-to-block-position helper for double-space queries. */
    private static net.zaminmc.torch.block.BlockPosition blockAt(double x, double y, double z) {
        return new net.zaminmc.torch.block.BlockPosition(
                (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }

    /**
     * Bounds-safe solidity for entity physics: the void below the world and
     * the sky above it hold no blocks (the 1.8 rule — bodies fall through
     * below y=0 until the kill plane instead of crashing the column lookup).
     */
    private boolean solidAt(double x, double y, double z) {
        if (y < net.zaminmc.torch.block.BlockPosition.MIN_Y
                || y > net.zaminmc.torch.block.BlockPosition.MAX_Y) {
            return false;
        }
        return WorldSolidity.isSolid(world.getBlock(blockAt(x, y, z)));
    }

    /** Bounds-safe fluid lookup: nothing outside the world column is fluid. */
    private boolean fluidAt(double x, double y, double z) {
        if (y < net.zaminmc.torch.block.BlockPosition.MIN_Y
                || y > net.zaminmc.torch.block.BlockPosition.MAX_Y) {
            return false;
        }
        return FluidBlocks.kindOf(world.getBlock(blockAt(x, y, z)).identifier()) != null;
    }

    /**
     * Ensures a chunk is loaded without violating world ownership: generation is
     * scheduled onto the world's owner thread and the callback runs there.
     * Safe to call from any thread. If the chunk already exists, the callback
     * runs immediately on the caller's thread.
     */
    public void requestChunkLoad(net.zaminmc.torch.block.ChunkPosition position,
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
