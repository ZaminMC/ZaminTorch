package net.minecraft.server.dedicated;

import com.google.common.collect.Lists;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.Proxy;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.network.encryption.EncryptionUtils;
import net.minecraft.server.Console;
import net.minecraft.server.Eula;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.UserConverter;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.server.dedicated.gui.DedicatedServerGui;
import net.minecraft.server.rcon.QueryResponseHandler;
import net.minecraft.server.rcon.RconServer;
import net.minecraft.snooper.Snooper;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Difficulty;
import net.minecraft.world.World;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.gen.WorldGeneratorType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DedicatedServer extends MinecraftServer implements DedicatedServerAccess {
    private static final Logger LOGGER = LogManager.getLogger();
    private final List<PendingCommand> pendingCommands = Collections.synchronizedList(Lists.newArrayList());
    private QueryResponseHandler queryResponseHandler;
    private RconServer rconServer;
    private ServerProperties properties;
    private Eula eula;
    private boolean generateStructures;
    private WorldSettings.GameMode defaultGameMode;
    private boolean hasGui;

    public DedicatedServer(File gameDir) {
        super(gameDir, Proxy.NO_PROXY, USER_CACHE_FILE);
        new Thread("Server Infinisleeper") {
            {
                this.setDaemon(true);
                this.start();
            }

            @Override
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(2147483647L);
                    } catch (InterruptedException interruptedexception) {
                    }
                }
            }
        };
    }

    @Override
    protected boolean init() throws IOException {
        Thread thread = new Thread("Server console handler") {
            @Override
            public void run() {
                BufferedReader bufferedreader = new BufferedReader(new InputStreamReader(System.in));

                String s4;
                try {
                    while (!DedicatedServer.this.hasStopped() && DedicatedServer.this.isRunning() && (s4 = bufferedreader.readLine()) != null) {
                        DedicatedServer.this.queueCommand(s4, DedicatedServer.this);
                    }
                } catch (IOException ioexception1) {
                    DedicatedServer.LOGGER.error("Exception handling console input", ioexception1);
                }
            }
        };
        thread.setDaemon(true);
        thread.start();
        LOGGER.info("Starting minecraft server version 1.8.8");
        if (Runtime.getRuntime().maxMemory() / 1024L / 1024L < 512L) {
            LOGGER.warn("To start the server with more ram, launch it as \"java -Xmx1024M -Xms1024M -jar minecraft_server.jar\"");
        }

        LOGGER.info("Loading properties");
        this.properties = new ServerProperties(new File("server.properties"));
        this.eula = new Eula(new File("eula.txt"));
        if (!this.eula.isAccepted()) {
            LOGGER.info("You need to agree to the EULA in order to run the server. Go to eula.txt for more info.");
            this.eula.write();
            return false;
        }

        if (this.isSingleplayer()) {
            this.setServerIp("127.0.0.1");
        } else {
            this.setOnlineMode(this.properties.getBoolean("online-mode", true));
            this.setServerIp(this.properties.getString("server-ip", ""));
        }

        this.setSpawnAnimals(this.properties.getBoolean("spawn-animals", true));
        this.setSpawnNpcs(this.properties.getBoolean("spawn-npcs", true));
        this.setPvpEnabled(this.properties.getBoolean("pvp", true));
        this.setFlightEnabled(this.properties.getBoolean("allow-flight", false));
        this.setResourcePack(this.properties.getString("resource-pack", ""), this.properties.getString("resource-pack-hash", ""));
        this.setMotd(this.properties.getString("motd", "A Minecraft Server"));
        this.setForceGameMode(this.properties.getBoolean("force-gamemode", false));
        this.setPlayerIdleTimeout(this.properties.getInt("player-idle-timeout", 0));
        if (this.properties.getInt("difficulty", 1) < 0) {
            this.properties.set("difficulty", 0);
        } else if (this.properties.getInt("difficulty", 1) > 3) {
            this.properties.set("difficulty", 3);
        }

        this.generateStructures = this.properties.getBoolean("generate-structures", true);
        int i = this.properties.getInt("gamemode", WorldSettings.GameMode.SURVIVAL.getId());
        this.defaultGameMode = WorldSettings.getGameModeById(i);
        LOGGER.info("Default game type: " + this.defaultGameMode);
        InetAddress inetaddress = null;
        if (this.getServerIp().length() > 0) {
            inetaddress = InetAddress.getByName(this.getServerIp());
        }

        if (this.getServerPort() < 0) {
            this.setServerPort(this.properties.getInt("server-port", 25565));
        }

        LOGGER.info("Generating keypair");
        this.setKeyPair(EncryptionUtils.generateKeyPair());
        LOGGER.info("Starting Minecraft server on " + (this.getServerIp().length() == 0 ? "*" : this.getServerIp()) + ":" + this.getServerPort());

        try {
            this.getConnection().bind(inetaddress, this.getServerPort());
        } catch (IOException ioexception) {
            LOGGER.warn("**** FAILED TO BIND TO PORT!");
            LOGGER.warn("The exception was: {}", new Object[]{ioexception.toString()});
            LOGGER.warn("Perhaps a server is already running on that port?");
            return false;
        }

        if (!this.isOnlineMode()) {
            LOGGER.warn("**** SERVER IS RUNNING IN OFFLINE/INSECURE MODE!");
            LOGGER.warn("The server will make no attempt to authenticate usernames. Beware.");
            LOGGER.warn(
                "While this makes the game possible to play without internet access, it also opens up the ability for hackers to connect with any username they choose."
            );
            LOGGER.warn("To change this, set \"online-mode\" to \"true\" in the server.properties file.");
        }

        if (this.convertUsers()) {
            this.getGameProfileCache().save();
        }

        if (!UserConverter.areUsersConverted(this.properties)) {
            return false;
        }

        this.setPlayerManager(new DedicatedPlayerManager(this));
        long j = System.nanoTime();
        if (this.getWorldSaveName() == null) {
            this.setWorldSaveName(this.properties.getString("level-name", "world"));
        }

        String s = this.properties.getString("level-seed", "");
        String s1 = this.properties.getString("level-type", "DEFAULT");
        String s2 = this.properties.getString("generator-settings", "");
        long k = new Random().nextLong();
        if (s.length() > 0) {
            try {
                long l = Long.parseLong(s);
                if (l != 0L) {
                    k = l;
                }
            } catch (NumberFormatException numberformatexception) {
                k = s.hashCode();
            }
        }

        WorldGeneratorType worldgeneratortype = WorldGeneratorType.byKey(s1);
        if (worldgeneratortype == null) {
            worldgeneratortype = WorldGeneratorType.DEFAULT;
        }

        this.shouldAnnouncePlayerAchievements();
        this.areCommandBlocksEnabled();
        this.getOpPermissionLevel();
        this.isSnooperEnabled();
        this.getNetworkCompressionThreshold();
        this.setWorldHeight(this.properties.getInt("max-build-height", 256));
        this.setWorldHeight((this.getWorldHeight() + 8) / 16 * 16);
        this.setWorldHeight(MathHelper.clamp(this.getWorldHeight(), 64, 256));
        this.properties.set("max-build-height", this.getWorldHeight());
        LOGGER.info("Preparing level \"" + this.getWorldSaveName() + "\"");
        this.loadWorld(this.getWorldSaveName(), this.getWorldSaveName(), k, worldgeneratortype, s2);
        long i1 = System.nanoTime() - j;
        String s3 = String.format("%.3fs", i1 / 1.0E9);
        LOGGER.info("Done (" + s3 + ")! For help, type \"help\" or \"?\"");
        if (this.properties.getBoolean("enable-query", false)) {
            LOGGER.info("Starting GS4 status listener");
            this.queryResponseHandler = new QueryResponseHandler(this);
            this.queryResponseHandler.start();
        }

        if (this.properties.getBoolean("enable-rcon", false)) {
            LOGGER.info("Starting remote control listener");
            this.rconServer = new RconServer(this);
            this.rconServer.start();
        }

        if (this.getMaxTickTime() > 0L) {
            Thread thread1 = new Thread(new ServerWatchdog(this));
            thread1.setName("Server Watchdog");
            thread1.setDaemon(true);
            thread1.start();
        }

        return true;
    }

    @Override
    public void setDefaultGameMode(WorldSettings.GameMode gamemode) {
        super.setDefaultGameMode(gamemode);
        this.defaultGameMode = gamemode;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return this.generateStructures;
    }

    @Override
    public WorldSettings.GameMode getDefaultGameMode() {
        return this.defaultGameMode;
    }

    @Override
    public Difficulty getDefaultDifficulty() {
        return Difficulty.byId(this.properties.getInt("difficulty", Difficulty.NORMAL.getId()));
    }

    @Override
    public boolean isHardcore() {
        return this.properties.getBoolean("hardcore", false);
    }

    @Override
    protected void onServerCrashed(CrashReport report) {
    }

    @Override
    public CrashReport populateCrashReport(CrashReport report) {
        report = super.populateCrashReport(report);
        report.getSystemDetails().add("Is Modded", new Callable<String>() {
            public String call() throws Exception {
                String s = DedicatedServer.this.getServerModName();
                return !s.equals("vanilla") ? "Definitely; Server brand changed to '" + s + "'" : "Unknown (can't tell)";
            }
        });
        report.getSystemDetails().add("Type", new Callable<String>() {
            public String call() throws Exception {
                return "Dedicated Server (map_server.txt)";
            }
        });
        return report;
    }

    @Override
    protected void exit() {
        System.exit(0);
    }

    @Override
    protected void tickWorlds() {
        super.tickWorlds();
        this.runPendingCommands();
    }

    @Override
    public boolean isNetherAllowed() {
        return this.properties.getBoolean("allow-nether", true);
    }

    @Override
    public boolean isMonsterSpawningEnabled() {
        return this.properties.getBoolean("spawn-monsters", true);
    }

    @Override
    public void populateSnooper(Snooper snooper) {
        snooper.putDynamic("whitelist_enabled", this.getPlayerManager().isWhitelistEnforced());
        snooper.putDynamic("whitelist_count", this.getPlayerManager().getWhitelistNames().length);
        super.populateSnooper(snooper);
    }

    @Override
    public boolean isSnooperEnabled() {
        return this.properties.getBoolean("snooper-enabled", true);
    }

    public void queueCommand(String command, CommandSource source) {
        this.pendingCommands.add(new PendingCommand(command, source));
    }

    public void runPendingCommands() {
        while (!this.pendingCommands.isEmpty()) {
            PendingCommand pendingcommand = this.pendingCommands.remove(0);
            this.getCommandHandler().run(pendingcommand.source, pendingcommand.command);
        }
    }

    @Override
    public boolean isDedicated() {
        return true;
    }

    @Override
    public boolean useNativeTransport() {
        return this.properties.getBoolean("use-native-transport", true);
    }

    public DedicatedPlayerManager getPlayerManager() {
        return (DedicatedPlayerManager)super.getPlayerManager();
    }

    @Override
    public int getIntProperty(String key, int defaultValue) {
        return this.properties.getInt(key, defaultValue);
    }

    @Override
    public String getStringProperty(String key, String defaultValue) {
        return this.properties.getString(key, defaultValue);
    }

    public boolean getBooleanProperty(String key, boolean defaultValue) {
        return this.properties.getBoolean(key, defaultValue);
    }

    @Override
    public void setProperty(String key, Object value) {
        this.properties.set(key, value);
    }

    @Override
    public void saveProperties() {
        this.properties.save();
    }

    @Override
    public String getPropertiesFilePath() {
        File file1 = this.properties.getFile();
        return file1 != null ? file1.getAbsolutePath() : "No settings file";
    }

    public void createGui() {
        DedicatedServerGui.create(this);
        this.hasGui = true;
    }

    @Override
    public boolean hasGui() {
        return this.hasGui;
    }

    @Override
    public String publish(WorldSettings.GameMode defaultGameMode, boolean allowCommands) {
        return "";
    }

    @Override
    public boolean areCommandBlocksEnabled() {
        return this.properties.getBoolean("enable-command-block", false);
    }

    @Override
    public int getSpawnProtectionRadius() {
        return this.properties.getInt("spawn-protection", super.getSpawnProtectionRadius());
    }

    @Override
    public boolean isSpawnProtected(World world, BlockPos pos, PlayerEntity player) {
        if (world.dimension.getId() != 0) {
            return false;
        }

        if (this.getPlayerManager().getOps().isEmpty()) {
            return false;
        }

        if (this.getPlayerManager().isOp(player.getGameProfile())) {
            return false;
        }

        if (this.getSpawnProtectionRadius() <= 0) {
            return false;
        }

        BlockPos blockpos = world.getSpawnPoint();
        int i = MathHelper.abs(pos.getX() - blockpos.getX());
        int j = MathHelper.abs(pos.getZ() - blockpos.getZ());
        int k = Math.max(i, j);
        return k <= this.getSpawnProtectionRadius();
    }

    @Override
    public int getOpPermissionLevel() {
        return this.properties.getInt("op-permission-level", 4);
    }

    @Override
    public void setPlayerIdleTimeout(int playerIdleTimeout) {
        super.setPlayerIdleTimeout(playerIdleTimeout);
        this.properties.set("player-idle-timeout", playerIdleTimeout);
        this.saveProperties();
    }

    @Override
    public boolean broadcastRconToOps() {
        return this.properties.getBoolean("broadcast-rcon-to-ops", true);
    }

    @Override
    public boolean broacastConsoleToOps() {
        return this.properties.getBoolean("broadcast-console-to-ops", true);
    }

    @Override
    public boolean shouldAnnouncePlayerAchievements() {
        return this.properties.getBoolean("announce-player-achievements", true);
    }

    @Override
    public int getMaxWorldSize() {
        int i = this.properties.getInt("max-world-size", super.getMaxWorldSize());
        if (i < 1) {
            i = 1;
        } else if (i > super.getMaxWorldSize()) {
            i = super.getMaxWorldSize();
        }

        return i;
    }

    @Override
    public int getNetworkCompressionThreshold() {
        return this.properties.getInt("network-compression-threshold", super.getNetworkCompressionThreshold());
    }

    protected boolean convertUsers() {
        boolean flag = false;

        for (int i = 0; !flag && i <= 2; i++) {
            if (i > 0) {
                LOGGER.warn("Encountered a problem while converting the user banlist, retrying in a few seconds");
                this.sleep();
            }

            flag = UserConverter.convertPlayerBans(this);
        }

        boolean flag1 = false;

        for (int j = 0; !flag1 && j <= 2; j++) {
            if (j > 0) {
                LOGGER.warn("Encountered a problem while converting the ip banlist, retrying in a few seconds");
                this.sleep();
            }

            flag1 = UserConverter.convertIpBans(this);
        }

        boolean flag2 = false;

        for (int k = 0; !flag2 && k <= 2; k++) {
            if (k > 0) {
                LOGGER.warn("Encountered a problem while converting the op list, retrying in a few seconds");
                this.sleep();
            }

            flag2 = UserConverter.convertOps(this);
        }

        boolean flag3 = false;

        for (int l = 0; !flag3 && l <= 2; l++) {
            if (l > 0) {
                LOGGER.warn("Encountered a problem while converting the whitelist, retrying in a few seconds");
                this.sleep();
            }

            flag3 = UserConverter.convertWhitelist(this);
        }

        boolean flag4 = false;

        for (int i1 = 0; !flag4 && i1 <= 2; i1++) {
            if (i1 > 0) {
                LOGGER.warn("Encountered a problem while converting the player save files, retrying in a few seconds");
                this.sleep();
            }

            flag4 = UserConverter.convertPlayers(this, this.properties);
        }

        return flag || flag1 || flag2 || flag3 || flag4;
    }

    private void sleep() {
        try {
            Thread.sleep(5000L);
        } catch (InterruptedException interruptedexception) {
        }
    }

    public long getMaxTickTime() {
        return this.properties.getOrDefault("max-tick-time", TimeUnit.MINUTES.toMillis(1L));
    }

    @Override
    public String getPlugins() {
        return "";
    }

    @Override
    public String runRconCommand(String command) {
        Console.getInstance().destroy();
        this.commandHandler.run(Console.getInstance(), command);
        return Console.getInstance().getTextAsString();
    }
}
