package net.minecraft.server;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.base64.Base64;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.Proxy;
import java.security.KeyPair;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import javax.imageio.ImageIO;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.WorldTimeS2CPacket;
import net.minecraft.server.command.AbstractCommand;
import net.minecraft.server.command.handler.CommandHandler;
import net.minecraft.server.command.handler.CommandManager;
import net.minecraft.server.command.source.CommandResults;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.server.network.ConnectionListener;
import net.minecraft.server.world.DemoServerWorld;
import net.minecraft.server.world.ReadOnlyServerWorld;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.ServerWorldEventListener;
import net.minecraft.snooper.Snooper;
import net.minecraft.snooper.SnooperPopulator;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.BlockableEventLoop;
import net.minecraft.util.ProgressListener;
import net.minecraft.util.Tickable;
import net.minecraft.util.Utils;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.world.Difficulty;
import net.minecraft.world.World;
import net.minecraft.world.WorldData;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.gen.WorldGeneratorType;
import net.minecraft.world.storage.AnvilWorldStorageSource;
import net.minecraft.world.storage.WorldStorage;
import net.minecraft.world.storage.WorldStorageSource;
import net.minecraft.world.storage.exception.SessionLockException;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class MinecraftServer implements Runnable, CommandSource, BlockableEventLoop, SnooperPopulator {
    private static final Logger LOGGER = LogManager.getLogger();
    public static final File USER_CACHE_FILE = new File("usercache.json");
    private static MinecraftServer instance;
    private final WorldStorageSource worldStorageSource;
    private final Snooper snooper = new Snooper("server", this, getTimeMillis());
    private final File gameDirectory;
    private final List<Tickable> tickables = Lists.newArrayList();
    protected final CommandHandler commandHandler;
    public final Profiler profiler = new Profiler();
    private final ConnectionListener connection;
    private final ServerStatus status = new ServerStatus();
    private final Random random = new Random();
    private int serverPort = -1;
    public ServerWorld[] worlds;
    private PlayerManager playerManager;
    private boolean running = true;
    private boolean stopped;
    private int ticks;
    protected final Proxy proxy;
    public String progressType;
    public int progress;
    private boolean onlineMode;
    private boolean spawnAnimals;
    private boolean spawnNpcs;
    private boolean pvpEnabled;
    private boolean flightEnabled;
    private String motd;
    private int worldHeight;
    private int playerIdleTimeout = 0;
    public final long[] averageTickTimes = new long[100];
    public long[][] worldTickTimes;
    private KeyPair keyPair;
    private String username;
    private String worldSaveName;
    private String worldName;
    private boolean demo;
    private boolean bonusChestEnabled;
    private boolean stopping;
    private String resourcePackUrl = "";
    private String resourcePackHash = "";
    private boolean loading;
    private long lastWarnTime;
    private String loadingStage;
    private boolean profiling;
    private boolean forceGameMode;
    private final YggdrasilAuthenticationService authService;
    private final MinecraftSessionService sessionService;
    private long lastPlayerSampleUpdate = 0L;
    private final GameProfileRepository gameProfileRepository;
    private final GameProfileCache gameProfileCache;
    protected final Queue<FutureTask<?>> pendingEvents = Queues.newArrayDeque();
    private Thread thread;
    private long nextTickTime = getTimeMillis();

    public MinecraftServer(Proxy proxy, File userCacheFile) {
        this.proxy = proxy;
        instance = this;
        this.gameDirectory = null;
        this.connection = null;
        this.gameProfileCache = new GameProfileCache(this, userCacheFile);
        this.commandHandler = null;
        this.worldStorageSource = null;
        this.authService = new YggdrasilAuthenticationService(proxy, UUID.randomUUID().toString());
        this.sessionService = this.authService.createMinecraftSessionService();
        this.gameProfileRepository = this.authService.createProfileRepository();
    }

    public MinecraftServer(File gameDir, Proxy proxy, File userCacheFile) {
        this.proxy = proxy;
        instance = this;
        this.gameDirectory = gameDir;
        this.connection = new ConnectionListener(this);
        this.gameProfileCache = new GameProfileCache(this, userCacheFile);
        this.commandHandler = this.createCommandHandler();
        this.worldStorageSource = new AnvilWorldStorageSource(gameDir);
        this.authService = new YggdrasilAuthenticationService(proxy, UUID.randomUUID().toString());
        this.sessionService = this.authService.createMinecraftSessionService();
        this.gameProfileRepository = this.authService.createProfileRepository();
    }

    protected CommandManager createCommandHandler() {
        return new CommandManager();
    }

    protected abstract boolean init() throws IOException;

    protected void convertWorld(String saveName) {
        if (this.getWorldStorageSource().needsConversion(saveName)) {
            LOGGER.info("Converting map!");
            this.setLoadingStage("menu.convertingLevel");
            this.getWorldStorageSource().convert(saveName, new ProgressListener() {
                private long lastLogTime = MinecraftServer.getTimeMillis();

                @Override
                public void progressStartNoAbort(String title) {
                }

                @Override
                public void updateTitle(String title) {
                }

                @Override
                public void progressStagePercentage(int percentage) {
                    if (MinecraftServer.getTimeMillis() - this.lastLogTime >= 1000L) {
                        this.lastLogTime = MinecraftServer.getTimeMillis();
                        MinecraftServer.LOGGER.info("Converting... " + percentage + "%");
                    }
                }

                @Override
                public void setDone() {
                }

                @Override
                public void progressStage(String stage) {
                }
            });
        }
    }

    protected synchronized void setLoadingStage(String stage) {
        this.loadingStage = stage;
    }

    public synchronized String getLoadingStage() {
        return this.loadingStage;
    }

    protected void loadWorld(String saveName, String name, long seed, WorldGeneratorType generatorType, String generatorOptions) {
        this.convertWorld(saveName);
        this.setLoadingStage("menu.loadingLevel");
        this.worlds = new ServerWorld[3];
        this.worldTickTimes = new long[this.worlds.length][100];
        WorldStorage worldstorage = this.worldStorageSource.get(saveName, true);
        this.findResourcePack(this.getWorldSaveName(), worldstorage);
        WorldData worlddata = worldstorage.loadData();
        WorldSettings worldsettings;
        if (worlddata == null) {
            if (this.isDemo()) {
                worldsettings = DemoServerWorld.SETTINGS;
            } else {
                worldsettings = new WorldSettings(seed, this.getDefaultGameMode(), this.shouldGenerateStructures(), this.isHardcore(), generatorType);
                worldsettings.setGeneratorOptions(generatorOptions);
                if (this.bonusChestEnabled) {
                    worldsettings.enableBonusChest();
                }
            }

            worlddata = new WorldData(worldsettings, name);
        } else {
            worlddata.setName(name);
            worldsettings = new WorldSettings(worlddata);
        }

        for (int i = 0; i < this.worlds.length; i++) {
            int j = 0;
            if (i == 1) {
                j = -1;
            }

            if (i == 2) {
                j = 1;
            }

            if (i == 0) {
                if (this.isDemo()) {
                    this.worlds[i] = (ServerWorld)new DemoServerWorld(this, worldstorage, worlddata, j, this.profiler).load();
                } else {
                    this.worlds[i] = (ServerWorld)new ServerWorld(this, worldstorage, worlddata, j, this.profiler).load();
                }

                this.worlds[i].init(worldsettings);
            } else {
                this.worlds[i] = (ServerWorld)new ReadOnlyServerWorld(this, worldstorage, j, this.worlds[0], this.profiler).load();
            }

            this.worlds[i].addEventListener(new ServerWorldEventListener(this, this.worlds[i]));
            if (!this.isSingleplayer()) {
                this.worlds[i].getData().setDefaultGameMode(this.getDefaultGameMode());
            }
        }

        this.playerManager.setWorld(this.worlds);
        this.setDifficulty(this.getDefaultDifficulty());
        this.prepareWorlds();
    }

    protected void prepareWorlds() {
        int i = 16;
        int j = 4;
        int k = 192;
        int l = 625;
        int i1 = 0;
        this.setLoadingStage("menu.generatingTerrain");
        int j1 = 0;
        LOGGER.info("Preparing start region for level " + j1);
        ServerWorld serverworld = this.worlds[j1];
        BlockPos blockpos = serverworld.getSpawnPoint();
        long k1 = getTimeMillis();

        for (int l1 = -192; l1 <= 192 && this.isRunning(); l1 += 16) {
            for (int i2 = -192; i2 <= 192 && this.isRunning(); i2 += 16) {
                long j2 = getTimeMillis();
                if (j2 - k1 > 1000L) {
                    this.logProgress("Preparing spawn area", i1 * 100 / 625);
                    k1 = j2;
                }

                i1++;
                serverworld.chunkCache.loadChunk(blockpos.getX() + l1 >> 4, blockpos.getZ() + i2 >> 4);
            }
        }

        this.clearProgress();
    }

    protected void findResourcePack(String saveName, WorldStorage storage) {
        File file1 = new File(storage.getDirectory(), "resources.zip");
        if (file1.isFile()) {
            this.setResourcePack("level://" + saveName + "/" + file1.getName(), "");
        }
    }

    public abstract boolean shouldGenerateStructures();

    public abstract WorldSettings.GameMode getDefaultGameMode();

    public abstract Difficulty getDefaultDifficulty();

    public abstract boolean isHardcore();

    public abstract int getOpPermissionLevel();

    public abstract boolean broadcastRconToOps();

    public abstract boolean broacastConsoleToOps();

    protected void logProgress(String progressType, int progress) {
        this.progressType = progressType;
        this.progress = progress;
        LOGGER.info(progressType + ": " + progress + "%");
    }

    protected void clearProgress() {
        this.progressType = null;
        this.progress = 0;
    }

    protected void saveWorlds(boolean silent) {
        if (!this.stopping) {
            for (ServerWorld serverworld : this.worlds) {
                if (serverworld != null) {
                    if (!silent) {
                        LOGGER.info("Saving chunks for level '" + serverworld.getData().getName() + "'/" + serverworld.dimension.getName());
                    }

                    try {
                        serverworld.save(true, null);
                    } catch (SessionLockException sessionlockexception) {
                        LOGGER.warn(sessionlockexception.getMessage());
                    }
                }
            }
        }
    }

    public void shutdown() {
        if (!this.stopping) {
            LOGGER.info("Stopping server");
            if (this.getConnection() != null) {
                this.getConnection().close();
            }

            if (this.playerManager != null) {
                LOGGER.info("Saving players");
                this.playerManager.saveAll();
                this.playerManager.onServerClosed();
            }

            if (this.worlds != null) {
                LOGGER.info("Saving worlds");
                this.saveWorlds(false);

                for (int i = 0; i < this.worlds.length; i++) {
                    ServerWorld serverworld = this.worlds[i];
                    serverworld.forceSave();
                }
            }

            if (this.snooper.isInitialized()) {
                this.snooper.interrupt();
            }
        }
    }

    public boolean isRunning() {
        return this.running;
    }

    public void stop() {
        this.running = false;
    }

    protected void setInstance() {
        instance = this;
    }

    @Override
    public void run() {
        try {
            if (this.init()) {
                this.nextTickTime = getTimeMillis();
                long i = 0L;
                this.status.setDescription(new LiteralText(this.motd));
                this.status.setVersion(new ServerStatus.Version("1.8.8", 47));
                this.setStatus(this.status);

                while (this.running) {
                    long k = getTimeMillis();
                    long j = k - this.nextTickTime;
                    if (j > 2000L && this.nextTickTime - this.lastWarnTime >= 15000L) {
                        LOGGER.warn(
                            "Can't keep up! Did the system time change, or is the server overloaded? Running {}ms behind, skipping {} tick(s)", j, j / 50L
                        );
                        j = 2000L;
                        this.lastWarnTime = this.nextTickTime;
                    }

                    if (j < 0L) {
                        LOGGER.warn("Time ran backwards! Did the system time change?");
                        j = 0L;
                    }

                    i += j;
                    this.nextTickTime = k;
                    if (this.worlds[0].canSkipNight()) {
                        this.tick();
                        i = 0L;
                    } else {
                        while (i > 50L) {
                            i -= 50L;
                            this.tick();
                        }
                    }

                    Thread.sleep(Math.max(1L, 50L - i));
                    this.loading = true;
                }
            } else {
                this.onServerCrashed(null);
            }
        } catch (Throwable throwable1) {
            LOGGER.error("Encountered an unexpected exception", throwable1);
            CrashReport crashreport = null;
            if (throwable1 instanceof CrashException) {
                crashreport = this.populateCrashReport(((CrashException)throwable1).getReport());
            } else {
                crashreport = this.populateCrashReport(new CrashReport("Exception in server tick loop", throwable1));
            }

            File file1 = new File(
                new File(this.getRunDir(), "crash-reports"), "crash-" + new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new Date()) + "-server.txt"
            );
            if (crashreport.writeToFile(file1)) {
                LOGGER.error("This crash report has been saved to: " + file1.getAbsolutePath());
            } else {
                LOGGER.error("We were unable to save this crash report to disk.");
            }

            this.onServerCrashed(crashreport);
        } finally {
            try {
                this.stopped = true;
                this.shutdown();
            } catch (Throwable throwable) {
                LOGGER.error("Exception stopping the server", throwable);
            } finally {
                this.exit();
            }
        }
    }

    private void setStatus(ServerStatus status) {
        File file1 = this.getFile("server-icon.png");
        if (file1.isFile()) {
            ByteBuf bytebuf = Unpooled.buffer();

            try {
                BufferedImage bufferedimage = ImageIO.read(file1);
                Validate.validState(bufferedimage.getWidth() == 64, "Must be 64 pixels wide");
                Validate.validState(bufferedimage.getHeight() == 64, "Must be 64 pixels high");
                ImageIO.write(bufferedimage, "PNG", new ByteBufOutputStream(bytebuf));
                ByteBuf bytebuf1 = Base64.encode(bytebuf);
                status.setFavicon("data:image/png;base64," + bytebuf1.toString(Charsets.UTF_8));
            } catch (Exception exception) {
                LOGGER.error("Couldn't load server icon", exception);
            } finally {
                bytebuf.release();
            }
        }
    }

    public File getRunDir() {
        return new File(".");
    }

    protected void onServerCrashed(CrashReport report) {
    }

    protected void exit() {
    }

    public void tick() {
        long i = System.nanoTime();
        this.ticks++;
        if (this.profiling) {
            this.profiling = false;
            this.profiler.profiling = true;
            this.profiler.reset();
        }

        this.profiler.push("root");
        this.tickWorlds();
        if (i - this.lastPlayerSampleUpdate >= 5000000000L) {
            this.lastPlayerSampleUpdate = i;
            this.status.setPlayers(new ServerStatus.Players(this.getMaxPlayerCount(), this.getPlayerCount()));
            GameProfile[] agameprofile = new GameProfile[Math.min(this.getPlayerCount(), 12)];
            int j = MathHelper.nextInt(this.random, 0, this.getPlayerCount() - agameprofile.length);

            for (int k = 0; k < agameprofile.length; k++) {
                agameprofile[k] = this.playerManager.getAll().get(j + k).getGameProfile();
            }

            Collections.shuffle(Arrays.asList(agameprofile));
            this.status.getPlayers().set(agameprofile);
        }

        if (this.ticks % 900 == 0) {
            this.profiler.push("save");
            this.playerManager.saveAll();
            this.saveWorlds(true);
            this.profiler.pop();
        }

        this.profiler.push("tallying");
        this.averageTickTimes[this.ticks % 100] = System.nanoTime() - i;
        this.profiler.pop();
        this.profiler.push("snooper");
        if (!this.snooper.isInitialized() && this.ticks > 100) {
            this.snooper.init();
        }

        if (this.ticks % 6000 == 0) {
            this.snooper.populate();
        }

        this.profiler.pop();
        this.profiler.pop();
    }

    public void tickWorlds() {
        this.profiler.push("jobs");
        synchronized (this.pendingEvents) {
            while (!this.pendingEvents.isEmpty()) {
                Utils.run(this.pendingEvents.poll(), LOGGER);
            }
        }

        this.profiler.swap("levels");

        for (int j = 0; j < this.worlds.length; j++) {
            long i = System.nanoTime();
            if (j == 0 || this.isNetherAllowed()) {
                ServerWorld serverworld = this.worlds[j];
                this.profiler.push(serverworld.getData().getName());
                if (this.ticks % 20 == 0) {
                    this.profiler.push("timeSync");
                    this.playerManager
                        .sendPacket(
                            new WorldTimeS2CPacket(serverworld.getTime(), serverworld.getTimeOfDay(), serverworld.getGameRules().getBoolean("doDaylightCycle")),
                            serverworld.dimension.getId()
                        );
                    this.profiler.pop();
                }

                this.profiler.push("tick");

                try {
                    serverworld.tick();
                } catch (Throwable throwable1) {
                    CrashReport crashreport = CrashReport.of(throwable1, "Exception ticking world");
                    serverworld.populateCrashReport(crashreport);
                    throw new CrashException(crashreport);
                }

                try {
                    serverworld.tickEntities();
                } catch (Throwable throwable) {
                    CrashReport crashreport1 = CrashReport.of(throwable, "Exception ticking world entities");
                    serverworld.populateCrashReport(crashreport1);
                    throw new CrashException(crashreport1);
                }

                this.profiler.pop();
                this.profiler.push("tracker");
                serverworld.getEntityMap().tick();
                this.profiler.pop();
                this.profiler.pop();
            }

            this.worldTickTimes[j][this.ticks % 100] = System.nanoTime() - i;
        }

        this.profiler.swap("connection");
        this.getConnection().tick();
        this.profiler.swap("players");
        this.playerManager.tick();
        this.profiler.swap("tickables");

        for (int k = 0; k < this.tickables.size(); k++) {
            this.tickables.get(k).tick();
        }

        this.profiler.pop();
    }

    public boolean isNetherAllowed() {
        return true;
    }

    public void start() {
        this.thread = new Thread(this, "Server thread");
        this.thread.start();
    }

    public File getFile(String path) {
        return new File(this.getRunDir(), path);
    }

    public void warn(String message) {
        LOGGER.warn(message);
    }

    public ServerWorld getWorld(int dimension) {
        if (dimension == -1) {
            return this.worlds[1];
        } else {
            return dimension == 1 ? this.worlds[2] : this.worlds[0];
        }
    }

    public String getGameVersion() {
        return "1.8.8";
    }

    public int getPlayerCount() {
        return this.playerManager.getCount();
    }

    public int getMaxPlayerCount() {
        return this.playerManager.getMaxCount();
    }

    public String[] getPlayerNames() {
        return this.playerManager.getNames();
    }

    public GameProfile[] getGameProfiles() {
        return this.playerManager.getProfiles();
    }

    public String getServerModName() {
        return "vanilla";
    }

    public CrashReport populateCrashReport(CrashReport report) {
        report.getSystemDetails().add("Profiler Position", new Callable<String>() {
            public String call() throws Exception {
                return MinecraftServer.this.profiler.profiling ? MinecraftServer.this.profiler.getCurrentLocation() : "N/A (disabled)";
            }
        });
        if (this.playerManager != null) {
            report.getSystemDetails()
                .add(
                    "Player Count",
                    new Callable<String>() {
                        public String call() {
                            return MinecraftServer.this.playerManager.getCount()
                                + " / "
                                + MinecraftServer.this.playerManager.getMaxCount()
                                + "; "
                                + MinecraftServer.this.playerManager.getAll();
                        }
                    }
                );
        }

        return report;
    }

    public List<String> getCommandSuggestions(CommandSource source, String command, BlockPos pos) {
        List<String> list = Lists.newArrayList();
        if (command.startsWith("/")) {
            command = command.substring(1);
            boolean flag = !command.contains(" ");
            List<String> list1 = this.commandHandler.getSuggestions(source, command, pos);
            if (list1 != null) {
                for (String s2 : list1) {
                    if (flag) {
                        list.add("/" + s2);
                    } else {
                        list.add(s2);
                    }
                }
            }

            return list;
        } else {
            String[] astring = command.split(" ", -1);
            String s = astring[astring.length - 1];

            for (String s1 : this.playerManager.getNames()) {
                if (AbstractCommand.doesStringStartWith(s, s1)) {
                    list.add(s1);
                }
            }

            return list;
        }
    }

    public static MinecraftServer getInstance() {
        return instance;
    }

    public boolean hasGameDirectory() {
        return this.gameDirectory != null;
    }

    @Override
    public String getName() {
        return "Server";
    }

    @Override
    public void sendMessage(Text message) {
        LOGGER.info(message.getString());
    }

    @Override
    public boolean canUseCommand(int permissionLevel, String command) {
        return true;
    }

    public CommandHandler getCommandHandler() {
        return this.commandHandler;
    }

    public KeyPair getKeyPair() {
        return this.keyPair;
    }

    public String getUsername() {
        return this.username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public boolean isSingleplayer() {
        return this.username != null;
    }

    public String getWorldSaveName() {
        return this.worldSaveName;
    }

    public void setWorldSaveName(String worldSaveName) {
        this.worldSaveName = worldSaveName;
    }

    public void setWorldName(String worldName) {
        this.worldName = worldName;
    }

    public String getWorldName() {
        return this.worldName;
    }

    public void setKeyPair(KeyPair keyPair) {
        this.keyPair = keyPair;
    }

    public void setDifficulty(Difficulty difficulty) {
        for (int i = 0; i < this.worlds.length; i++) {
            World world = this.worlds[i];
            if (world != null) {
                if (world.getData().isHardcore()) {
                    world.getData().setDifficulty(Difficulty.HARD);
                    world.setAllowedMobSpawns(true, true);
                } else if (this.isSingleplayer()) {
                    world.getData().setDifficulty(difficulty);
                    world.setAllowedMobSpawns(world.getDifficulty() != Difficulty.PEACEFUL, true);
                } else {
                    world.getData().setDifficulty(difficulty);
                    world.setAllowedMobSpawns(this.isMonsterSpawningEnabled(), this.spawnAnimals);
                }
            }
        }
    }

    protected boolean isMonsterSpawningEnabled() {
        return true;
    }

    public boolean isDemo() {
        return this.demo;
    }

    public void setDemo(boolean demo) {
        this.demo = demo;
    }

    public void enableBonusChest(boolean bonusChestEnabled) {
        this.bonusChestEnabled = bonusChestEnabled;
    }

    public WorldStorageSource getWorldStorageSource() {
        return this.worldStorageSource;
    }

    public void deleteWorldAndStop() {
        this.stopping = true;
        this.getWorldStorageSource().flush();

        for (int i = 0; i < this.worlds.length; i++) {
            ServerWorld serverworld = this.worlds[i];
            if (serverworld != null) {
                serverworld.forceSave();
            }
        }

        this.getWorldStorageSource().delete(this.worlds[0].getStorage().getName());
        this.stop();
    }

    public String getResourcePackUrl() {
        return this.resourcePackUrl;
    }

    public String getResourcePackHash() {
        return this.resourcePackHash;
    }

    public void setResourcePack(String url, String hash) {
        this.resourcePackUrl = url;
        this.resourcePackHash = hash;
    }

    @Override
    public void populateSnooper(Snooper snooper) {
        snooper.putDynamic("whitelist_enabled", false);
        snooper.putDynamic("whitelist_count", 0);
        if (this.playerManager != null) {
            snooper.putDynamic("players_current", this.getPlayerCount());
            snooper.putDynamic("players_max", this.getMaxPlayerCount());
            snooper.putDynamic("players_seen", this.playerManager.getSavedIds().length);
        }

        snooper.putDynamic("uses_auth", this.onlineMode);
        snooper.putDynamic("gui_state", this.hasGui() ? "enabled" : "disabled");
        snooper.putDynamic("run_time", (getTimeMillis() - snooper.getInitTime()) / 60L * 1000L);
        snooper.putDynamic("avg_tick_ms", (int)(MathHelper.average(this.averageTickTimes) * 1.0E-6));
        int i = 0;
        if (this.worlds != null) {
            for (int j = 0; j < this.worlds.length; j++) {
                if (this.worlds[j] != null) {
                    ServerWorld serverworld = this.worlds[j];
                    WorldData worlddata = serverworld.getData();
                    snooper.putDynamic("world[" + i + "][dimension]", serverworld.dimension.getId());
                    snooper.putDynamic("world[" + i + "][mode]", worlddata.getDefaultGamemode());
                    snooper.putDynamic("world[" + i + "][difficulty]", serverworld.getDifficulty());
                    snooper.putDynamic("world[" + i + "][hardcore]", worlddata.isHardcore());
                    snooper.putDynamic("world[" + i + "][generator_name]", worlddata.getGeneratorType().getKey());
                    snooper.putDynamic("world[" + i + "][generator_version]", worlddata.getGeneratorType().getVersion());
                    snooper.putDynamic("world[" + i + "][height]", this.worldHeight);
                    snooper.putDynamic("world[" + i + "][chunks_loaded]", serverworld.getChunkSource().size());
                    i++;
                }
            }
        }

        snooper.putDynamic("worlds", i);
    }

    @Override
    public void initSnooper(Snooper snooper) {
        snooper.putFixed("singleplayer", this.isSingleplayer());
        snooper.putFixed("server_brand", this.getServerModName());
        snooper.putFixed("gui_supported", GraphicsEnvironment.isHeadless() ? "headless" : "supported");
        snooper.putFixed("dedicated", this.isDedicated());
    }

    @Override
    public boolean isSnooperEnabled() {
        return true;
    }

    public abstract boolean isDedicated();

    public boolean isOnlineMode() {
        return this.onlineMode;
    }

    public void setOnlineMode(boolean onlineMode) {
        this.onlineMode = onlineMode;
    }

    public boolean shouldSpawnAnimals() {
        return this.spawnAnimals;
    }

    public void setSpawnAnimals(boolean spawnAnimals) {
        this.spawnAnimals = spawnAnimals;
    }

    public boolean shouldSpawnNpcs() {
        return this.spawnNpcs;
    }

    public abstract boolean useNativeTransport();

    public void setSpawnNpcs(boolean spawnNpcs) {
        this.spawnNpcs = spawnNpcs;
    }

    public boolean isPvpEnabled() {
        return this.pvpEnabled;
    }

    public void setPvpEnabled(boolean pvpEnabled) {
        this.pvpEnabled = pvpEnabled;
    }

    public boolean isFlightEnabled() {
        return this.flightEnabled;
    }

    public void setFlightEnabled(boolean flightEnabled) {
        this.flightEnabled = flightEnabled;
    }

    public abstract boolean areCommandBlocksEnabled();

    public String getServerMotd() {
        return this.motd;
    }

    public void setMotd(String motd) {
        this.motd = motd;
    }

    public int getWorldHeight() {
        return this.worldHeight;
    }

    public void setWorldHeight(int worldHeight) {
        this.worldHeight = worldHeight;
    }

    public boolean hasStopped() {
        return this.stopped;
    }

    public PlayerManager getPlayerManager() {
        return this.playerManager;
    }

    public void setPlayerManager(PlayerManager playerManager) {
        this.playerManager = playerManager;
    }

    public void setDefaultGameMode(WorldSettings.GameMode gamemode) {
        for (int i = 0; i < this.worlds.length; i++) {
            getInstance().worlds[i].getData().setDefaultGameMode(gamemode);
        }
    }

    public ConnectionListener getConnection() {
        return this.connection;
    }

    public boolean isLoading() {
        return this.loading;
    }

    public boolean hasGui() {
        return false;
    }

    public abstract String publish(WorldSettings.GameMode defaultGameMode, boolean allowCommands);

    public int getTicks() {
        return this.ticks;
    }

    public void enableProfiling() {
        this.profiling = true;
    }

    public Snooper getSnooper() {
        return this.snooper;
    }

    @Override
    public BlockPos getCommandSourceBlockPos() {
        return BlockPos.ORIGIN;
    }

    @Override
    public Vec3d getCommandSourcePos() {
        return new Vec3d(0.0, 0.0, 0.0);
    }

    @Override
    public World getCommandSourceWorld() {
        return this.worlds[0];
    }

    @Override
    public Entity asEntity() {
        return null;
    }

    public int getSpawnProtectionRadius() {
        return 16;
    }

    public boolean isSpawnProtected(World world, BlockPos pos, PlayerEntity player) {
        return false;
    }

    public boolean shouldForceGameMode() {
        return this.forceGameMode;
    }

    public Proxy getProxy() {
        return this.proxy;
    }

    public static long getTimeMillis() {
        return System.currentTimeMillis();
    }

    public int getPlayerIdleTimeout() {
        return this.playerIdleTimeout;
    }

    public void setPlayerIdleTimeout(int playerIdleTimeout) {
        this.playerIdleTimeout = playerIdleTimeout;
    }

    @Override
    public Text getDisplayName() {
        return new LiteralText(this.getName());
    }

    public boolean shouldAnnouncePlayerAchievements() {
        return true;
    }

    public MinecraftSessionService getSessionService() {
        return this.sessionService;
    }

    public GameProfileRepository getGameProfileRepository() {
        return this.gameProfileRepository;
    }

    public GameProfileCache getGameProfileCache() {
        return this.gameProfileCache;
    }

    public ServerStatus getStatus() {
        return this.status;
    }

    public void forcePlayerSampleUpdate() {
        this.lastPlayerSampleUpdate = 0L;
    }

    public Entity getEntity(UUID uuid) {
        for (ServerWorld serverworld : this.worlds) {
            if (serverworld != null) {
                Entity entity = serverworld.getEntity(uuid);
                if (entity != null) {
                    return entity;
                }
            }
        }

        return null;
    }

    @Override
    public boolean sendCommandSuccessToOps() {
        return getInstance().worlds[0].getGameRules().getBoolean("sendCommandFeedback");
    }

    @Override
    public void addResult(CommandResults.Type type, int result) {
    }

    public int getMaxWorldSize() {
        return 29999984;
    }

    public <V> ListenableFuture<V> execute(Callable<V> task) {
        Validate.notNull(task);
        if (!this.isOnSameThread() && !this.hasStopped()) {
            ListenableFutureTask<V> listenablefuturetask = ListenableFutureTask.create(task);
            synchronized (this.pendingEvents) {
                this.pendingEvents.add(listenablefuturetask);
                return listenablefuturetask;
            }
        } else {
            try {
                return Futures.immediateFuture(task.call());
            } catch (Exception exception) {
                return Futures.immediateFailedCheckedFuture(exception);
            }
        }
    }

    @Override
    public ListenableFuture<Object> execute(Runnable task) {
        Validate.notNull(task);
        return this.execute(Executors.callable(task));
    }

    @Override
    public boolean isOnSameThread() {
        return Thread.currentThread() == this.thread;
    }

    public int getNetworkCompressionThreshold() {
        return 256;
    }
}
