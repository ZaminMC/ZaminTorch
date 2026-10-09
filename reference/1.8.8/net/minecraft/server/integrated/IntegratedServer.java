package net.minecraft.server.integrated;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;
import net.minecraft.client.ClientBrandRetriever;
import net.minecraft.client.Minecraft;
import net.minecraft.network.encryption.EncryptionUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.handler.CommandManager;
import net.minecraft.server.entity.living.player.ServerPlayerEntity;
import net.minecraft.server.world.DemoServerWorld;
import net.minecraft.server.world.ReadOnlyServerWorld;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.ServerWorldEventListener;
import net.minecraft.snooper.Snooper;
import net.minecraft.util.HttpUtil;
import net.minecraft.util.Utils;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.world.Difficulty;
import net.minecraft.world.WorldData;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.gen.WorldGeneratorType;
import net.minecraft.world.storage.WorldStorage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class IntegratedServer extends MinecraftServer {
    private static final Logger LOGGER = LogManager.getLogger();
    private final Minecraft minecraft;
    private final WorldSettings settings;
    private boolean paused;
    private boolean published;
    private LanServerPinger pinger;

    public IntegratedServer(Minecraft minecraft) {
        super(minecraft.getNetworkProxy(), new File(minecraft.gameDir, USER_CACHE_FILE.getName()));
        this.minecraft = minecraft;
        this.settings = null;
    }

    public IntegratedServer(Minecraft minecraft, String saveName, String name, WorldSettings settings) {
        super(new File(minecraft.gameDir, "saves"), minecraft.getNetworkProxy(), new File(minecraft.gameDir, USER_CACHE_FILE.getName()));
        this.setUsername(minecraft.getSession().getUsername());
        this.setWorldSaveName(saveName);
        this.setWorldName(name);
        this.setDemo(minecraft.isDemo());
        this.enableBonusChest(settings.generateBonusChest());
        this.setWorldHeight(256);
        this.setPlayerManager(new IntegratedPlayerManager(this));
        this.minecraft = minecraft;
        this.settings = this.isDemo() ? DemoServerWorld.SETTINGS : settings;
    }

    @Override
    protected CommandManager createCommandHandler() {
        return new IntegratedCommandManager();
    }

    @Override
    protected void loadWorld(String saveName, String name, long seed, WorldGeneratorType generatorType, String generatorOptions) {
        this.convertWorld(saveName);
        this.worlds = new ServerWorld[3];
        this.worldTickTimes = new long[this.worlds.length][100];
        WorldStorage worldstorage = this.getWorldStorageSource().get(saveName, true);
        this.findResourcePack(this.getWorldSaveName(), worldstorage);
        WorldData worlddata = worldstorage.loadData();
        if (worlddata == null) {
            worlddata = new WorldData(this.settings, name);
        } else {
            worlddata.setName(name);
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

                this.worlds[i].init(this.settings);
            } else {
                this.worlds[i] = (ServerWorld)new ReadOnlyServerWorld(this, worldstorage, j, this.worlds[0], this.profiler).load();
            }

            this.worlds[i].addEventListener(new ServerWorldEventListener(this, this.worlds[i]));
        }

        this.getPlayerManager().setWorld(this.worlds);
        if (this.worlds[0].getData().getDifficulty() == null) {
            this.setDifficulty(this.minecraft.options.difficulty);
        }

        this.prepareWorlds();
    }

    @Override
    protected boolean init() throws IOException {
        LOGGER.info("Starting integrated minecraft server version 1.8.8");
        this.setOnlineMode(true);
        this.setSpawnAnimals(true);
        this.setSpawnNpcs(true);
        this.setPvpEnabled(true);
        this.setFlightEnabled(true);
        LOGGER.info("Generating keypair");
        this.setKeyPair(EncryptionUtils.generateKeyPair());
        this.loadWorld(
            this.getWorldSaveName(), this.getWorldName(), this.settings.getSeed(), this.settings.getGeneratorType(), this.settings.getGeneratorOptions()
        );
        this.setMotd(this.getUsername() + " - " + this.worlds[0].getData().getName());
        return true;
    }

    @Override
    protected void tick() {
        boolean flag = this.paused;
        this.paused = Minecraft.getInstance().getNetworkHandler() != null && Minecraft.getInstance().isPaused();
        if (!flag && this.paused) {
            LOGGER.info("Saving and pausing game...");
            this.getPlayerManager().saveAll();
            this.saveWorlds(false);
        }

        if (this.paused) {
            synchronized (this.pendingEvents) {
                while (!this.pendingEvents.isEmpty()) {
                    Utils.run(this.pendingEvents.poll(), LOGGER);
                }
            }
        } else {
            super.tick();
            if (this.minecraft.options.viewDistance != this.getPlayerManager().getChunkViewDistance()) {
                LOGGER.info("Changing view distance to {}, from {}", this.minecraft.options.viewDistance, this.getPlayerManager().getChunkViewDistance());
                this.getPlayerManager().updateViewDistance(this.minecraft.options.viewDistance);
            }

            if (this.minecraft.world != null) {
                WorldData worlddata1 = this.worlds[0].getData();
                WorldData worlddata = this.minecraft.world.getData();
                if (!worlddata1.isDifficultyLocked() && worlddata.getDifficulty() != worlddata1.getDifficulty()) {
                    LOGGER.info("Changing difficulty to {}, from {}", worlddata.getDifficulty(), worlddata1.getDifficulty());
                    this.setDifficulty(worlddata.getDifficulty());
                } else if (worlddata.isDifficultyLocked() && !worlddata1.isDifficultyLocked()) {
                    LOGGER.info("Locking difficulty to {}", new Object[]{worlddata.getDifficulty()});

                    for (ServerWorld serverworld : this.worlds) {
                        if (serverworld != null) {
                            serverworld.getData().setDifficultyLocked(true);
                        }
                    }
                }
            }
        }
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }

    @Override
    public WorldSettings.GameMode getDefaultGameMode() {
        return this.settings.getGameMode();
    }

    @Override
    public Difficulty getDefaultDifficulty() {
        return this.minecraft.world.getData().getDifficulty();
    }

    @Override
    public boolean isHardcore() {
        return this.settings.isHardcore();
    }

    @Override
    public boolean broadcastRconToOps() {
        return true;
    }

    @Override
    public boolean broacastConsoleToOps() {
        return true;
    }

    @Override
    public File getRunDir() {
        return this.minecraft.gameDir;
    }

    @Override
    public boolean isDedicated() {
        return false;
    }

    @Override
    public boolean useNativeTransport() {
        return false;
    }

    @Override
    protected void onServerCrashed(CrashReport report) {
        this.minecraft.integratedServerCrashed(report);
    }

    @Override
    public CrashReport populateCrashReport(CrashReport report) {
        report = super.populateCrashReport(report);
        report.getSystemDetails().add("Type", new Callable<String>() {
            public String call() throws Exception {
                return "Integrated Server (map_client.txt)";
            }
        });
        report.getSystemDetails()
            .add(
                "Is Modded",
                new Callable<String>() {
                    public String call() throws Exception {
                        String s = ClientBrandRetriever.getClientModName();
                        if (!s.equals("vanilla")) {
                            return "Definitely; Client brand changed to '" + s + "'";
                        } else {
                            s = IntegratedServer.this.getServerModName();
                            if (!s.equals("vanilla")) {
                                return "Definitely; Server brand changed to '" + s + "'";
                            } else {
                                return Minecraft.class.getSigners() == null
                                    ? "Very likely; Jar signature invalidated"
                                    : "Probably not. Jar signature remains and both client + server brands are untouched.";
                            }
                        }
                    }
                }
            );
        return report;
    }

    @Override
    public void setDifficulty(Difficulty difficulty) {
        super.setDifficulty(difficulty);
        if (this.minecraft.world != null) {
            this.minecraft.world.getData().setDifficulty(difficulty);
        }
    }

    @Override
    public void populateSnooper(Snooper snooper) {
        super.populateSnooper(snooper);
        snooper.putDynamic("snooper_partner", this.minecraft.getSnooper().getToken());
    }

    @Override
    public boolean isSnooperEnabled() {
        return Minecraft.getInstance().isSnooperEnabled();
    }

    @Override
    public String publish(WorldSettings.GameMode defaultGameMode, boolean allowCommands) {
        try {
            int i = -1;

            try {
                i = HttpUtil.getLocalPort();
            } catch (IOException ioexception) {
            }

            if (i <= 0) {
                i = 25564;
            }

            this.getConnection().bind(null, i);
            LOGGER.info("Started on " + i);
            this.published = true;
            this.pinger = new LanServerPinger(this.getServerMotd(), i + "");
            this.pinger.start();
            this.getPlayerManager().setDefaultGameMode(defaultGameMode);
            this.getPlayerManager().setAllowCommands(allowCommands);
            return i + "";
        } catch (IOException ioexception1) {
            return null;
        }
    }

    @Override
    public void shutdown() {
        super.shutdown();
        if (this.pinger != null) {
            this.pinger.interrupt();
            this.pinger = null;
        }
    }

    @Override
    public void stop() {
        Futures.getUnchecked(this.execute(new Runnable() {
            @Override
            public void run() {
                for (ServerPlayerEntity serverplayerentity : Lists.newArrayList(IntegratedServer.this.getPlayerManager().getAll())) {
                    IntegratedServer.this.getPlayerManager().remove(serverplayerentity);
                }
            }
        }));
        super.stop();
        if (this.pinger != null) {
            this.pinger.interrupt();
            this.pinger = null;
        }
    }

    public void resetInstance() {
        this.setInstance();
    }

    public boolean isPublished() {
        return this.published;
    }

    @Override
    public void setDefaultGameMode(WorldSettings.GameMode gamemode) {
        this.getPlayerManager().setDefaultGameMode(gamemode);
    }

    @Override
    public boolean areCommandBlocksEnabled() {
        return true;
    }

    @Override
    public int getOpPermissionLevel() {
        return 4;
    }
}
