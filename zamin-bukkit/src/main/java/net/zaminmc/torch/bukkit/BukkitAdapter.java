package net.zaminmc.torch.bukkit;

import net.zaminmc.torch.World;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.bukkit.block.MaterialMap;
import net.zaminmc.torch.bukkit.block.TorchBlock;
import net.zaminmc.torch.bukkit.block.TorchBlockState;
import net.zaminmc.torch.bukkit.command.TorchCommandBridge;
import net.zaminmc.torch.bukkit.entity.TorchMob;
import net.zaminmc.torch.bukkit.entity.TorchOfflinePlayer;
import net.zaminmc.torch.bukkit.entity.TorchPlayer;
import net.zaminmc.torch.bukkit.event.TorchEventBridge;
import net.zaminmc.torch.bukkit.plugin.BukkitPluginBridge;
import net.zaminmc.torch.bukkit.plugin.TorchPluginManager;
import net.zaminmc.torch.bukkit.scheduler.TorchSchedulerBridge;
import net.zaminmc.torch.bukkit.server.TorchConsoleSender;
import net.zaminmc.torch.bukkit.server.TorchServer;
import net.zaminmc.torch.bukkit.world.TorchWorld;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Identifier;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * The §28.7 Bukkit/Spigot/Paper compatibility adapter's composition root —
 * the first slice: plugin lifecycle + event bridge + scheduler bridge over
 * the engine's own §27 surfaces (§27.2's boundary: the adapter translates
 * legacy contracts into Torch operations; it must never become Torch's
 * internal architecture or weaken the native API — §28.10's final rule:
 * compatibility constraints belong to the adapter, never the engine).
 *
 * <p><b>The attach walk:</b> {@link Bukkit#setServer} (the legacy singleton
 * — the adapter IS the server from the plugins' view), the player/world
 * object adapters, the plugin manager + lifecycle bridge, the scheduler
 * bridge, the event bridge's first group, and the legacy discovery wave
 * over {@code <dataDir>/plugins} (every {@code *.jar} with a
 * {@code plugin.yml}). Jars carrying the native {@code
 * torch-plugin.properties} descriptor stay the native host's — the two
 * waves pick up disjoint jar forms from one directory.</p>
 *
 * <p><b>The §27.6 routing headroom (the adapter's own law):</b> every
 * mutation through a legacy handle routes — a call already on the
 * simulation owner's context applies directly (the event handlers'
 * context), and a call from anywhere else travels as a cross-owner intent
 * to the position's owner, applied at a tick boundary (§28.2's
 * nonblocking default). A reference is never authority (§28.3).</p>
 */
public final class BukkitAdapter {

    private static final Logger LOGGER = Logger.getLogger(BukkitAdapter.class.getName());

    private final EngineServer engine;
    private final MaterialMap materials;
    private final TorchServer server;
    private final TorchConsoleSender console;
    private final TorchPluginManager pluginManager;
    private final BukkitPluginBridge pluginBridge;
    private final TorchCommandBridge commandBridge;
    private final TorchSchedulerBridge schedulerBridge;
    private final TorchEventBridge eventBridge;

    // The object adapters (§27.5 handles: resolve to the current state).
    private final Map<PlayerSession, TorchPlayer> players = new ConcurrentHashMap<>();
    private final Map<World, TorchWorld> worlds = new ConcurrentHashMap<>();
    private final Map<BlockPosition, TorchBlock> blocks = new ConcurrentHashMap<>();
    // The entity-view family's own caches (slice 3 — the live mob views;
    // the pending spawn views are per-event snapshots and never cached).
    private final Map<MobEntity, TorchMob> mobViews = new ConcurrentHashMap<>();
    private volatile boolean attached;

    public BukkitAdapter(EngineServer engine) {
        this.engine = engine;
        this.materials = new MaterialMap(engine.blocks());
        this.server = new TorchServer(this);
        this.console = new TorchConsoleSender(this);
        this.pluginBridge = new BukkitPluginBridge(this);
        this.pluginManager = new TorchPluginManager(this);
        this.commandBridge = new TorchCommandBridge(this);
        this.schedulerBridge = new TorchSchedulerBridge(this);
        this.eventBridge = new TorchEventBridge(this);
    }

    /**
     * The one-time attach: the legacy singleton, the bridges, the first
     * event group, and the discovery wave over {@code <dataDir>/plugins}.
     *
     * @return how many Bukkit plugins the wave bridged onto the engine host
     */
    public int attach() {
        if (attached) {
            throw new IllegalStateException("the adapter is already attached");
        }
        attached = true;
        Bukkit.setServer(server); // the legacy singleton — this call logs the banner
        eventBridge.attach();
        // The discovery wave: the engine's own data dir's plugins/ directory
        // (the same directory the native wave scans — the two waves pick up
        // disjoint jar forms: plugin.yml here, torch-plugin.properties there;
        // the native wave's per-jar skip lines for bukkit jars are its own
        // documented noise, §28.5's per-jar isolation law).
        int bridged = pluginBridge.loadAll(
                java.nio.file.Path.of(engine.engineConfig().dataDir(), "plugins"),
                new org.bukkit.plugin.java.JavaPluginLoader(server)).size();
        LOGGER.info("The Bukkit adapter's first slice is attached: " + bridged
                + " plugin(s) bridged onto the engine's native plugin host");
        return bridged;
    }

    // ------------------------------------------------------------------ the §27.6 routing (the adapter's own headroom)

    /**
     * The §27.6 route for one position-scoped mutation: on the owner's own
     * context it applies directly; from anywhere else it travels as a
     * nonblocking cross-owner intent to the position's owner (§28.2 — the
     * submitter never waits, the owner validates and applies at a tick
     * boundary).
     */
    public void mutatePosition(World world, BlockPosition position, String operation,
            Runnable action) {
        if (engine.ticker().isOwnerThread()) {
            action.run(); // the event handlers' context — legal by §27.6
            return;
        }
        engine.crossOwner().submit(world, position, operation, action);
    }

    /**
     * The §27.6 route for one world-scoped mutation (the routing target is
     * the world's own spawn position — the world's one owner).
     */
    public void mutateWorld(World world, Runnable action) {
        BlockPosition spawn = world.spawnPosition().toBlockPosition();
        mutatePosition(world, spawn, "bukkit-world-mutation", action);
    }

    // ------------------------------------------------------------------ the object adapters

    public TorchPlayer player(PlayerSession session) {
        return players.computeIfAbsent(session, fresh -> new TorchPlayer(this, session));
    }

    public TorchPlayer playerByName(String name) {
        for (Player online : onlinePlayers()) {
            if (online.getName().equalsIgnoreCase(name)) {
                return (TorchPlayer) online;
            }
        }
        return null;
    }

    public TorchPlayer playerByExactName(String name) {
        for (Player online : onlinePlayers()) {
            if (online.getName().equals(name)) {
                return (TorchPlayer) online;
            }
        }
        return null;
    }

    public TorchPlayer playerByUuid(UUID uuid) {
        for (PlayerSession session : sessions()) {
            if (session.uuid().equals(uuid)) {
                return player(session);
            }
        }
        return null;
    }

    /** The online snapshot view (a copy — the legacy collection contract). */
    public List<Player> onlinePlayers() {
        List<Player> view = new ArrayList<>();
        for (PlayerSession session : sessions()) {
            view.add(player(session));
        }
        return view;
    }

    /** The players currently in one engine world (the session's own dimension). */
    public List<Player> playersIn(World world) {
        List<Player> in = new ArrayList<>();
        for (PlayerSession session : sessions()) {
            if (session.world() == world) {
                in.add(player(session));
            }
        }
        return in;
    }

    public boolean isOnline(PlayerSession session) {
        for (PlayerSession live : sessions()) {
            if (live == session) {
                return true;
            }
        }
        return false;
    }

    private List<PlayerSession> sessions() {
        List<PlayerSession> live = new ArrayList<>();
        for (net.zaminmc.torch.entity.Player apiPlayer : engine.players()) {
            if (apiPlayer instanceof PlayerSession session) {
                live.add(session);
            }
        }
        return live;
    }

    public TorchOfflinePlayer offlineView(String name, UUID uuid) {
        return new TorchOfflinePlayer(this, name, uuid);
    }

    public TorchOfflinePlayer offlineView(UUID uuid) {
        return new TorchOfflinePlayer(this, "uuid:" + uuid, uuid);
    }

    // ------------------------------------------------------------------ the entity views (the slice-3 object adapters)

    /**
     * The identity-stable live view over one engine mob (§27.5: one
     * view per body — the player views' own caching law). The damage
     * and death bridges and the thrower resolutions ride it.
     */
    public TorchMob mobView(MobEntity mob) {
        return mobViews.computeIfAbsent(mob, fresh -> new TorchMob(this, fresh));
    }

    /**
     * The engine-id resolution over the adapter's own entity views (the
     * projectile family's thrower row): an online session answers the
     * player view, a live mob answers the mob view, anything else
     * answers null — never an invented body.
     */
    public org.bukkit.entity.Entity entityByEngineId(int entityId) {
        if (entityId < 0) {
            return null;
        }
        for (PlayerSession session : sessions()) {
            if (session.engineEntityId() == entityId) {
                return player(session);
            }
        }
        MobEntity mob = engine.mobs().byId(entityId);
        return mob != null ? mobView(mob) : null;
    }

    /**
     * The {@link org.bukkit.projectiles.ProjectileSource} resolution for
     * the pending projectile views' shooter row: the same id walk as
     * {@link #entityByEngineId} narrowed to the sources (players and
     * living mobs — both views implement the interface).
     */
    public org.bukkit.projectiles.ProjectileSource projectileSourceByEngineId(int entityId) {
        org.bukkit.entity.Entity resolved = entityByEngineId(entityId);
        return resolved instanceof org.bukkit.projectiles.ProjectileSource source
                ? source : null;
    }

    public TorchWorld worldFor(PlayerSession session) {
        World engineWorld = session.world();
        if (engineWorld == null) {
            engineWorld = engine.world(); // the pre-spawn window (documented)
        }
        return worldView(engineWorld);
    }

    public TorchWorld worldView(World engineWorld) {
        return worlds.computeIfAbsent(engineWorld, fresh -> new TorchWorld(this, fresh));
    }

    /**
     * The engine-world lookup by name — the name the native events carry
     * ({@code worldName()}) resolved against the adapter's own world
     * views, or null when no world of that name is loaded (the caller's
     * own loud-skip law: a foreign world is not the adapter's to bridge).
     */
    public TorchWorld worldByName(String name) {
        for (TorchWorld view : worldViews()) {
            if (view.getName().equals(name)) {
                return view;
            }
        }
        return null;
    }

    public List<TorchWorld> worldViews() {
        List<TorchWorld> views = new ArrayList<>();
        views.add(worldView(engine.world()));
        if (engine.netherWorld() != null) {
            views.add(worldView(engine.netherWorld()));
        }
        if (engine.endWorld() != null) {
            views.add(worldView(engine.endWorld()));
        }
        return views;
    }

    public TorchBlock blockAt(TorchWorld world, int x, int y, int z) {
        return blocks.computeIfAbsent(new BlockPosition(x, y, z),
                fresh -> new TorchBlock(this, world, x, y, z));
    }

    public TorchBlock blockAtPosition(World engineWorld, BlockPosition position) {
        TorchWorld world = worldView(engineWorld);
        return blockAt(world, position.x(), position.y(), position.z());
    }

    public TorchBlockState blockState(TorchBlock block) {
        return new TorchBlockState(block);
    }

    public BlockType airBlock() {
        return engine.blocks().byKey("air")
                .orElseThrow(() -> new IllegalStateException("no air block in the registry"));
    }

    // ------------------------------------------------------------------ the engine surfaces the bridges ride

    public EngineServer engine() {
        return engine;
    }

    public Logger logger() {
        return LOGGER;
    }

    public MaterialMap materialMap() {
        return materials;
    }

    public TorchServer bukkitServer() {
        return server;
    }

    public TorchConsoleSender consoleSender() {
        return console;
    }

    public TorchPluginManager pluginManager() {
        return pluginManager;
    }

    public BukkitPluginBridge pluginBridge() {
        return pluginBridge;
    }

    public TorchCommandBridge commandBridge() {
        return commandBridge;
    }

    public TorchSchedulerBridge schedulerBridge() {
        return schedulerBridge;
    }

    /** The engine's own system-message path (the one route — §30.3's law). */
    public void systemMessage(PlayerSession session, String content) {
        engine.systemMessage(session, content);
    }

    /** The engine's own op-grant surface (the operator write). */
    public void opGrant(PlayerSession session, int level) {
        session.setOpLevel(level);
    }

    // ------------------------------------------------------------------ the unbridged refusal (§27.9's honest gaps)

    /**
     * The loud refusal every unbridged arm throws: the §27.9 honest-gap
     * rule — a recorded limitation with a deliberate message, never a
     * silent wrong answer, and never a hidden wait.
     */
    public UnsupportedOperationException unbridged(String what) {
        return new UnsupportedOperationException(what
                + " is not bridged in the Torch adapter's first slice — the native API"
                + " exposes the engine capability through its own owner-aware contracts"
                + " (§28.10: compatibility limits stay in the adapter)");
    }
}
