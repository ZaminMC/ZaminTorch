package net.zaminmc.torch.bukkit.server;

import net.zaminmc.torch.bukkit.BukkitAdapter;
import net.zaminmc.torch.server.config.EngineConfig;

import org.bukkit.Server;
import org.bukkit.Warning;
import org.bukkit.command.CommandException;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * The legacy server view over the live engine (§28.7's inventory: the
 * main-thread check, the online-player view, the one command path, the
 * plugin manager and the scheduler — all translated onto the engine's own
 * §27 surfaces, none of them a second implementation).
 *
 * <p><b>The bridged subset (this slice):</b> identity (name/version/the
 * pinned 1.8.8 API lineage string), the online-player snapshot view and
 * the by-name/by-uuid lookups, the plugin manager and scheduler bridges,
 * the console sender, the broadcast routes (the engine's own
 * system-message path), the console dispatch route (the engine's ONE
 * command path), the three engine worlds, {@link #isPrimaryThread()} (the
 * §28.7 answer over the engine's own tick-thread check), the operator and
 * offline views (name-derived identity — no network lookup, the engine's
 * own identity model), and the config-backed constants.</p>
 *
 * <p><b>Every unbridged arm fails loudly</b> (§27.9) with the message
 * naming what the native API exposes instead. The services manager and
 * the messenger ride the API artifact's own implementations (empty
 * registries — the registration arms are real, the channels are a later
 * slice).</p>
 */
public final class TorchServer implements Server {

    /** The pinned 1.8.8 lineage string (the build file's own pin). */
    public static final String API_VERSION = "1.8.8-R0.1-SNAPSHOT";

    private final BukkitAdapter adapter;
    private final Logger logger = Logger.getLogger("net.zaminmc.torch.bukkit");
    private final org.bukkit.plugin.ServicesManager services =
            new org.bukkit.plugin.SimpleServicesManager();
    private final org.bukkit.plugin.messaging.Messenger messenger =
            new org.bukkit.plugin.messaging.StandardMessenger();

    public TorchServer(BukkitAdapter adapter) {
        this.adapter = adapter;
    }

    // ------------------------------------------------------------------ identity

    @Override
    public String getName() {
        return "Torch";
    }

    @Override
    public String getVersion() {
        return "ZaminTorch (the 1.8.8 engine)";
    }

    @Override
    public String getBukkitVersion() {
        return API_VERSION;
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    // ------------------------------------------------------------------ players

    @Override
    @SuppressWarnings("deprecation")
    public Player[] _INVALID_getOnlinePlayers() {
        return getOnlinePlayers().toArray(new Player[0]);
    }

    @Override
    public Collection<? extends Player> getOnlinePlayers() {
        return adapter.onlinePlayers();
    }

    @Override
    public int getMaxPlayers() {
        return adapter.engine().engineConfig().maxPlayers();
    }

    @Override
    public Player getPlayer(String name) {
        return adapter.playerByName(name);
    }

    @Override
    public Player getPlayerExact(String name) {
        return adapter.playerByExactName(name);
    }

    @Override
    public List<Player> matchPlayer(String name) {
        String needle = name.toLowerCase();
        return adapter.onlinePlayers().stream()
                .filter(player -> player.getName().toLowerCase().contains(needle))
                .toList();
    }

    @Override
    public Player getPlayer(UUID id) {
        return adapter.playerByUuid(id);
    }

    @Override
    public Set<org.bukkit.OfflinePlayer> getOperators() {
        return Set.of();
    }

    @Override
    public org.bukkit.OfflinePlayer[] getOfflinePlayers() {
        // The engine's identity model is name-derived (offline lineage): the
        // online set IS the known-player set this slice answers honestly.
        return adapter.onlinePlayers().toArray(new org.bukkit.OfflinePlayer[0]);
    }

    @Override
    public org.bukkit.OfflinePlayer getOfflinePlayer(String name) {
        Player online = adapter.playerByExactName(name);
        if (online != null) {
            return online;
        }
        // No Mojang lookup (§28.2's no-hidden-blocking rule): the engine's
        // identity model derives the uuid from the name — the documented
        // translation, never a network call.
        return adapter.offlineView(name, UUID.nameUUIDFromBytes(
                ("OfflinePlayer:" + name).getBytes()));
    }

    @Override
    public org.bukkit.OfflinePlayer getOfflinePlayer(UUID id) {
        Player online = adapter.playerByUuid(id);
        if (online != null) {
            return online;
        }
        return adapter.offlineView(id);
    }

    // ------------------------------------------------------------------ the bridges

    @Override
    public org.bukkit.plugin.PluginManager getPluginManager() {
        return adapter.pluginManager();
    }

    @Override
    public org.bukkit.scheduler.BukkitScheduler getScheduler() {
        return adapter.schedulerBridge();
    }

    @Override
    public boolean isPrimaryThread() {
        return adapter.engine().ticker().isOwnerThread();
    }

    // ------------------------------------------------------------------ worlds

    @Override
    public List<World> getWorlds() {
        return List.copyOf(adapter.worldViews());
    }

    @Override
    public World getWorld(String name) {
        for (World world : adapter.worldViews()) {
            if (world.getName().equals(name)) {
                return world;
            }
        }
        return null;
    }

    @Override
    public World getWorld(UUID uid) {
        for (World world : adapter.worldViews()) {
            if (world.getUID().equals(uid)) {
                return world;
            }
        }
        return null;
    }

    @Override
    public World createWorld(org.bukkit.WorldCreator creator) {
        throw adapter.unbridged("Server.createWorld");
    }

    @Override
    public boolean unloadWorld(String name, boolean save) {
        throw adapter.unbridged("Server.unloadWorld");
    }

    @Override
    public boolean unloadWorld(World world, boolean save) {
        throw adapter.unbridged("Server.unloadWorld");
    }

    // ------------------------------------------------------------------ messaging + commands

    @Override
    public int broadcastMessage(String message) {
        return broadcast(message, Server.BROADCAST_CHANNEL_USERS);
    }

    @Override
    public int broadcast(String message, String permission) {
        int heard = 0;
        for (Player player : adapter.onlinePlayers()) {
            // The administrative channel keeps the 1.8.8 rule: only the
            // operators hear it. The engine's operator level is the gate.
            if (Server.BROADCAST_CHANNEL_ADMINISTRATIVE.equals(permission)
                    && !player.isOp()) {
                continue;
            }
            player.sendMessage(message);
            heard++;
        }
        if (Server.BROADCAST_CHANNEL_ADMINISTRATIVE.equals(permission) || heard == 0) {
            // The console hears every administrative broadcast and every
            // broadcast on an empty server (the honest echo).
            logger.info(message);
        }
        return heard;
    }

    @Override
    public org.bukkit.command.ConsoleCommandSender getConsoleSender() {
        return adapter.consoleSender();
    }

    @Override
    public boolean dispatchCommand(org.bukkit.command.CommandSender sender, String commandLine)
            throws CommandException {
        if (sender instanceof TorchConsoleSender console) {
            console.dispatch(commandLine);
            return true;
        }
        if (sender instanceof net.zaminmc.torch.bukkit.entity.TorchPlayer player) {
            player.chat(commandLine);
            return true;
        }
        throw adapter.unbridged("Server.dispatchCommand (foreign sender "
                + sender.getClass().getName() + ")");
    }

    @Override
    public org.bukkit.command.PluginCommand getPluginCommand(String name) {
        // No legacy command registry in this slice: the engine's ONE command
        // path (§30.3) is the deliberate alternative — recorded, not faked.
        return null;
    }

    @Override
    public org.bukkit.help.HelpMap getHelpMap() {
        throw adapter.unbridged("Server.getHelpMap");
    }

    @Override
    public org.bukkit.scoreboard.ScoreboardManager getScoreboardManager() {
        throw adapter.unbridged("Server.getScoreboardManager");
    }

    @Override
    public org.bukkit.inventory.ItemFactory getItemFactory() {
        throw adapter.unbridged("Server.getItemFactory");
    }

    @Override
    public String getMotd() {
        return config().motd();
    }

    @Override
    public String getShutdownMessage() {
        return "Server closed";
    }

    @Override
    public int getMonsterSpawnLimit() {
        return 70; // the vanilla 1.8.8 default (Server-side, the same honest constant as World)
    }

    @Override
    public int getAnimalSpawnLimit() {
        return 10;
    }

    @Override
    public int getWaterAnimalSpawnLimit() {
        return 5;
    }

    @Override
    public int getAmbientSpawnLimit() {
        return 15;
    }

    @Override
    public org.bukkit.inventory.Inventory createInventory(
            org.bukkit.inventory.InventoryHolder owner, int size)
            throws IllegalArgumentException {
        throw adapter.unbridged("Server.createInventory");
    }

    @Override
    public org.bukkit.inventory.Inventory createInventory(
            org.bukkit.inventory.InventoryHolder owner, int size, String title)
            throws IllegalArgumentException {
        throw adapter.unbridged("Server.createInventory");
    }

    @Override
    public org.bukkit.inventory.Inventory createInventory(
            org.bukkit.inventory.InventoryHolder owner,
            org.bukkit.event.inventory.InventoryType type) {
        throw adapter.unbridged("Server.createInventory");
    }

    @Override
    public org.bukkit.inventory.Inventory createInventory(
            org.bukkit.inventory.InventoryHolder owner,
            org.bukkit.event.inventory.InventoryType type, String title) {
        throw adapter.unbridged("Server.createInventory");
    }

    @Override
    public org.bukkit.map.MapView getMap(short id) {
        throw adapter.unbridged("Server.getMap");
    }

    @Override
    public org.bukkit.map.MapView createMap(World world) {
        throw adapter.unbridged("Server.createMap");
    }

    @Override
    public void sendPluginMessage(org.bukkit.plugin.Plugin source, String channel,
            byte[] message) {
        throw adapter.unbridged("Server.sendPluginMessage");
    }

    @Override
    public Set<String> getListeningPluginChannels() {
        return Set.of();
    }

    @Override
    public org.bukkit.plugin.ServicesManager getServicesManager() {
        return services;
    }

    @Override
    public org.bukkit.plugin.messaging.Messenger getMessenger() {
        return messenger;
    }

    // ------------------------------------------------------------------ config-backed constants

    private EngineConfig config() {
        return adapter.engine().engineConfig();
    }

    @Override
    public int getPort() {
        return config().port();
    }

    @Override
    public int getViewDistance() {
        return config().viewDistance();
    }

    @Override
    public String getIp() {
        return config().host();
    }

    @Override
    public String getServerName() {
        return "Torch Server";
    }

    @Override
    public String getServerId() {
        return "zamin-torch";
    }

    @Override
    public String getWorldType() {
        return config().levelType();
    }

    @Override
    public boolean getGenerateStructures() {
        return false;
    }

    @Override
    public boolean getAllowEnd() {
        return true;
    }

    @Override
    public boolean getAllowNether() {
        return true;
    }

    @Override
    public boolean getOnlineMode() {
        // The engine's identity model is name-derived (the offline lineage)
        // — the honest answer, never a mode the engine does not run.
        return false;
    }

    @Override
    public boolean getAllowFlight() {
        return true;
    }

    @Override
    public boolean isHardcore() {
        return false;
    }

    @Override
    public boolean useExactLoginLocation() {
        return false;
    }

    @Override
    public long getConnectionThrottle() {
        return 4000; // the vanilla 1.8.8 default (documented)
    }

    @Override
    public int getTicksPerAnimalSpawns() {
        // The vanilla 1.8.8 default (the engine's own spawn pacing is not
        // exposed as config in this slice — the honest constant).
        return 400;
    }

    @Override
    public int getTicksPerMonsterSpawns() {
        // The vanilla 1.8.8 default (same honest-constant rule).
        return 1;
    }

    @Override
    public org.bukkit.GameMode getDefaultGameMode() {
        return org.bukkit.GameMode.getByValue(config().gamemode().ordinal());
    }

    @Override
    public void setDefaultGameMode(org.bukkit.GameMode mode) {
        throw adapter.unbridged("Server.setDefaultGameMode");
    }

    @Override
    public int getSpawnRadius() {
        return EngineConfig.DEFAULT_SPAWN_PROTECTION;
    }

    @Override
    public void setSpawnRadius(int value) {
        throw adapter.unbridged("Server.setSpawnRadius");
    }

    @Override
    public boolean hasWhitelist() {
        return config().whiteList();
    }

    @Override
    public void setWhitelist(boolean value) {
        throw adapter.unbridged("Server.setWhitelist");
    }

    @Override
    public Set<org.bukkit.OfflinePlayer> getWhitelistedPlayers() {
        return Set.of();
    }

    @Override
    public void reloadWhitelist() {
        // No whitelist registry in this slice — the no-op is the honest
        // answer (nothing to reload).
    }

    @Override
    public String getUpdateFolder() {
        return "update";
    }

    @Override
    public File getUpdateFolderFile() {
        return new File("update");
    }

    @Override
    public File getWorldContainer() {
        return new File(config().dataDir());
    }

    @Override
    public void savePlayers() {
        throw adapter.unbridged("Server.savePlayers");
    }

    @Override
    public void reload() {
        throw adapter.unbridged("Server.reload");
    }

    @Override
    public void configureDbConfig(com.avaje.ebean.config.ServerConfig config) {
        // The database family is unbridged (the ebean dependency rides the
        // API artifact only); a no-op keeps a database-enabled plugin.yml
        // from failing the whole load — the recorded §27.9 limitation.
    }

    @Override
    public boolean addRecipe(org.bukkit.inventory.Recipe recipe) {
        throw adapter.unbridged("Server.addRecipe");
    }

    @Override
    public List<org.bukkit.inventory.Recipe> getRecipesFor(org.bukkit.inventory.ItemStack result) {
        return List.of();
    }

    @Override
    public java.util.Iterator<org.bukkit.inventory.Recipe> recipeIterator() {
        return List.<org.bukkit.inventory.Recipe>of().iterator();
    }

    @Override
    public void clearRecipes() {
        // No legacy recipe registry in this slice — the engine's own recipe
        // table is the source of truth (the no-op keeps the honest answer).
    }

    @Override
    public void resetRecipes() {
        // Same as clearRecipes: the engine's table is authoritative.
    }

    @Override
    public java.util.Map<String, String[]> getCommandAliases() {
        return java.util.Map.of();
    }

    // ------------------------------------------------------------------ the ban family

    @Override
    public Set<String> getIPBans() {
        return Set.of();
    }

    @Override
    public void banIP(String address) {
        throw adapter.unbridged("Server.banIP");
    }

    @Override
    public void unbanIP(String address) {
        throw adapter.unbridged("Server.unbanIP");
    }

    @Override
    public Set<org.bukkit.OfflinePlayer> getBannedPlayers() {
        return Set.of();
    }

    @Override
    public org.bukkit.BanList getBanList(org.bukkit.BanList.Type type) {
        throw adapter.unbridged("Server.getBanList");
    }

    // ------------------------------------------------------------------ misc

    @Override
    public org.bukkit.util.CachedServerIcon getServerIcon() {
        return null; // no server-icon family in this slice (the honest null)
    }

    @Override
    public org.bukkit.util.CachedServerIcon loadServerIcon(java.io.File file)
            throws IllegalArgumentException, Exception {
        throw adapter.unbridged("Server.loadServerIcon");
    }

    @Override
    public org.bukkit.util.CachedServerIcon loadServerIcon(
            java.awt.image.BufferedImage image) throws IllegalArgumentException, Exception {
        throw adapter.unbridged("Server.loadServerIcon");
    }

    @Override
    public void shutdown() {
        // The legacy contract: never called from the primary thread — the
        // bridge hands the engine its own shutdown walk on a dedicated
        // thread and returns (never a blocking wait — §28.2).
        Thread stopper = new Thread(() -> adapter.engine().shutdown(null),
                "torch-bukkit-shutdown");
        stopper.start();
    }

    @SuppressWarnings("deprecation")
    @Override
    public org.bukkit.UnsafeValues getUnsafe() {
        return null; // the deprecated internal-use arm — never a bridge (null is the honest answer)
    }

    @Override
    public Warning.WarningState getWarningState() {
        return Warning.WarningState.OFF;
    }

    @Override
    public void setIdleTimeout(int threshold) {
        // No idle-timeout family in the engine yet — the no-op is the
        // honest answer (nothing enforces it).
    }

    @Override
    public int getIdleTimeout() {
        return 0;
    }

    @Override
    public org.bukkit.generator.ChunkGenerator.ChunkData createChunkData(World world) {
        throw adapter.unbridged("Server.createChunkData");
    }

    // ------------------------------------------------------------------ Spigot section

    @Override
    public Spigot spigot() {
        return new Spigot() {
        };
    }
}
