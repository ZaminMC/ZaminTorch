package net.zamin.engine;

import net.zamin.api.Player;
import net.zamin.api.PlayerState;
import net.zamin.api.Position;
import net.zamin.api.Rotation;
import net.zamin.api.Server;
import net.zamin.api.ServerState;
import net.zamin.api.World;
import net.zamin.engine.config.EngineConfig;
import net.zamin.engine.entity.ItemEntity;
import net.zamin.engine.entity.ItemEntityManager;
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

    private final EngineConfig config;
    private final PlayerRegistry players = new PlayerRegistry();
    private final AtomicReference<ServerState> state = new AtomicReference<>(ServerState.NEW);
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);

    private FrozenBlockRegistry blockRegistry;
    private EngineWorld world;
    private EngineTicker ticker;
    private BlockInteractionService blockInteraction;
    private ChatService chatService;
    private WorldStorage worldStorage;
    private PlayerDataStore playerStore;
    private volatile ItemEntityManager itemEntities;
    private final java.util.List<WorldChangeListener> worldListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.List<ChatListener> chatListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.List<ItemEntityManager.Listener> itemListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.List<InventoryListener> inventoryListeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Entity ids for engine-global entities (items); player wire ids stay adapter-local. */
    private static final int ENTITY_ID_BASE = 100_000;

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
                pregenerateSpawnArea(world);
                ticker.attachWorld(world);
                // Item entities and drops: simulation-owned systems on the world owner.
                ItemEntityManager itemEntities = new ItemEntityManager(
                        (x, y, z) -> !world.getBlock(blockAt(x, y, z)).equals(world.airType()),
                        new java.util.Random(),
                        ENTITY_ID_BASE);
                this.itemEntities = itemEntities;
                itemEntities.addListener(new ItemEventDispatch());
                ticker.setTickHandler(() -> itemEntities.tick(players.all()));
                blockInteraction = new BlockInteractionService(world, ticker, this::publishBlockChange,
                        config.gamemode(), new DropService(), itemEntities,
                        type -> blockRegistry.lookup(type.identifier()),
                        this::publishInventoryChanged);
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

    /** An inventory content change that observers must re-sync to the client. */
    public interface InventoryListener {
        void onInventoryChanged(PlayerSession player);
    }

    /** Registers an internal inventory observer (e.g. the protocol adapter's sync). */
    public void addInventoryListener(InventoryListener listener) {
        inventoryListeners.add(listener);
    }

    private void publishInventoryChanged(PlayerSession player) {
        for (InventoryListener listener : inventoryListeners) {
            listener.onInventoryChanged(player);
        }
    }

    /**
     * Applies a validated held-slot change. The owning channel loop provides
     * ordering; the mutation itself is simulation-confined. Safe from any thread.
     */
    public void heldItemChange(PlayerSession session, int hotbarSlot) {
        Objects.requireNonNull(session, "session");
        ticker.submit(() -> {
            try {
                session.inventory().selectHotbarSlot(hotbarSlot);
            } catch (IllegalArgumentException invalidSlot) {
                LOGGER.fine(() -> "Rejected held-slot change " + hotbarSlot + " from "
                        + session.name());
            }
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
     * A window click in the player inventory (mode/button from the historical
     * wire): the semantic operation runs on the tick thread, the verdict goes
     * back through {@code result}, and the inventory re-syncs after every
     * click so the client never keeps predicted state. Safe from any thread.
     */
    @Override
    public void windowClick(PlayerSession session, int wireSlot, int button, int mode,
                            java.util.function.Consumer<Boolean> result) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(result, "result");
        ticker.submit(() -> windowClickOnTick(session, wireSlot, button, mode, result));
    }

    /** Wire slot -> engine slot mapping for the player inventory window; -1 when out of engine scope. */
    private static int engineSlotOf(int wireSlot) {
        if (wireSlot >= 9 && wireSlot <= 35) {
            return wireSlot;                 // main inventory
        }
        if (wireSlot >= 36 && wireSlot <= 44) {
            return wireSlot - 36;            // hotbar
        }
        return -1;                           // craft/armor slots or outside
    }

    private void windowClickOnTick(PlayerSession session, int wireSlot, int button, int mode,
                                   java.util.function.Consumer<Boolean> result) {
        var inventory = session.inventory();
        boolean accepted = false;
        try {
            switch (mode) {
                case 0 -> {
                    int engineSlot = engineSlotOf(wireSlot);
                    if (engineSlot >= 0) {
                        inventory.clickSlot(engineSlot, button);
                        accepted = true;
                    }
                }
                case 1 -> {
                    int engineSlot = engineSlotOf(wireSlot);
                    if (engineSlot >= 0) {
                        inventory.quickMove(engineSlot);
                        accepted = true;
                    }
                }
                case 2 -> {
                    int engineSlot = engineSlotOf(wireSlot);
                    if (engineSlot >= 0 && button >= 0 && button < 9) {
                        inventory.swapWithHotbar(engineSlot, button);
                        accepted = true;
                    }
                }
                case 3 -> {
                    // Middle-click clone is a creative-only gesture: rejected in survival.
                }
                case 4 -> {
                    int engineSlot = engineSlotOf(wireSlot);
                    if (engineSlot >= 0) {
                        net.zamin.api.ItemStack dropped = inventory.dropFromSlot(engineSlot, button != 0);
                        if (!dropped.isEmpty()) {
                            throwFromPlayer(session, dropped);
                        }
                        accepted = true;
                    }
                }
                case 5 -> {
                    // Drag painting: rejected per packet; the end-of-drag resync
                    // restores the authoritative state on the client.
                }
                default -> {
                    // Unknown mode: rejected.
                }
            }
            if (wireSlot == -999 && !inventory.cursor().isEmpty()) {
                // Clicking outside the window throws the carried stack.
                net.zamin.api.ItemStack carried = inventory.cursor();
                throwFromPlayer(session, carried);
                inventory.returnCursor();
                accepted = true;
            }
        } catch (IllegalArgumentException invalid) {
            accepted = false; // a broken click must not damage the session (§54)
        }
        result.accept(accepted);
        publishInventoryChanged(session);
    }

    /**
     * The player closed the inventory window: the carried cursor stack returns
     * to the inventory; a remainder is thrown into the world. Safe from any thread.
     */
    public void closeWindow(PlayerSession session) {
        Objects.requireNonNull(session, "session");
        ticker.submit(() -> {
            net.zamin.api.ItemStack leftover = session.inventory().returnCursor();
            if (!leftover.isEmpty()) {
                throwFromPlayer(session, leftover);
            }
            publishInventoryChanged(session);
        });
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
        commands.register(new CommandService.Command("give", "Give yourself an item: /give <name> [count]",
                this::giveCommand));
    }

    /**
     * /give &lt;name&gt; [count]: grants the item into the player's inventory
     * (fill order as pickup). The administrative item source until crafting and
     * inventory clicks exist; runs on the tick thread through chat dispatch.
     */
    private String giveCommand(PlayerSession sender, String[] args) {
        if (args.length < 1) {
            return "Usage: /give <item> [count]";
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
        net.zamin.api.ItemStack granted = net.zamin.api.ItemStack.of(type, count);
        net.zamin.api.ItemStack remainder = sender.inventory().pickUp(granted);
        publishInventoryChanged(sender);
        int given = count - remainder.count();
        return remainder.isEmpty()
                ? "Given " + given + " x " + type.displayName()
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
                    stack.count(), stack.damage()));
        }
        return new PlayerSnapshot(session.uuid(), session.name(), session.position(),
                session.rotation(), session.inventory().heldSlot(), filled);
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
                net.zamin.api.ItemStack leftover = session.inventory().returnCursor();
                if (!leftover.isEmpty()) {
                    throwFromPlayer(session, leftover);
                }
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
                net.zamin.api.ItemStack leftover = session.inventory().returnCursor();
                if (!leftover.isEmpty()) {
                    throwFromPlayer(session, leftover);
                }
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
