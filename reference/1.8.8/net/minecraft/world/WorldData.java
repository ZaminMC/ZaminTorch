package net.minecraft.world;

import java.util.concurrent.Callable;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.crash.CrashReportCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.WorldGeneratorType;

public class WorldData {
    public static final Difficulty DEFAULT_DIFFICULTY = Difficulty.NORMAL;
    private long seed;
    private WorldGeneratorType generatorType = WorldGeneratorType.DEFAULT;
    private String generatorOptions = "";
    private int spawnX;
    private int spawnY;
    private int spawnZ;
    private long time;
    private long timeOfDay;
    private long lastPlayed;
    private long sizeOnDisk;
    private NbtCompound playerData;
    private int dimension;
    private String name;
    private int version;
    private int clearWeatherTime;
    private boolean raining;
    private int rainTime;
    private boolean thundering;
    private int thunderTime;
    private WorldSettings.GameMode defaultGameMode;
    private boolean allowStructures;
    private boolean hardcore;
    private boolean allowCommands;
    private boolean initialized;
    private Difficulty difficulty;
    private boolean difficultyLocked;
    private double borderCenterX = 0.0;
    private double borderCenterZ = 0.0;
    private double borderSize = 6.0E7;
    private long borderSizeLerpTime = 0L;
    private double borderSizeLerpTarget = 0.0;
    private double borderSafeZone = 5.0;
    private double borderDamagePerBlock = 0.2;
    private int borderWarningBlocks = 5;
    private int borderWarningTime = 15;
    private GameRules gameRules = new GameRules();

    protected WorldData() {
    }

    public WorldData(NbtCompound nbt) {
        this.seed = nbt.getLong("RandomSeed");
        if (nbt.contains("generatorName", 8)) {
            String s = nbt.getString("generatorName");
            this.generatorType = WorldGeneratorType.byKey(s);
            if (this.generatorType == null) {
                this.generatorType = WorldGeneratorType.DEFAULT;
            } else if (this.generatorType.isVersioned()) {
                int i = 0;
                if (nbt.contains("generatorVersion", 99)) {
                    i = nbt.getInt("generatorVersion");
                }

                this.generatorType = this.generatorType.getTypeForVersion(i);
            }

            if (nbt.contains("generatorOptions", 8)) {
                this.generatorOptions = nbt.getString("generatorOptions");
            }
        }

        this.defaultGameMode = WorldSettings.GameMode.byId(nbt.getInt("GameType"));
        if (nbt.contains("MapFeatures", 99)) {
            this.allowStructures = nbt.getBoolean("MapFeatures");
        } else {
            this.allowStructures = true;
        }

        this.spawnX = nbt.getInt("SpawnX");
        this.spawnY = nbt.getInt("SpawnY");
        this.spawnZ = nbt.getInt("SpawnZ");
        this.time = nbt.getLong("Time");
        if (nbt.contains("DayTime", 99)) {
            this.timeOfDay = nbt.getLong("DayTime");
        } else {
            this.timeOfDay = this.time;
        }

        this.lastPlayed = nbt.getLong("LastPlayed");
        this.sizeOnDisk = nbt.getLong("SizeOnDisk");
        this.name = nbt.getString("LevelName");
        this.version = nbt.getInt("version");
        this.clearWeatherTime = nbt.getInt("clearWeatherTime");
        this.rainTime = nbt.getInt("rainTime");
        this.raining = nbt.getBoolean("raining");
        this.thunderTime = nbt.getInt("thunderTime");
        this.thundering = nbt.getBoolean("thundering");
        this.hardcore = nbt.getBoolean("hardcore");
        if (nbt.contains("initialized", 99)) {
            this.initialized = nbt.getBoolean("initialized");
        } else {
            this.initialized = true;
        }

        if (nbt.contains("allowCommands", 99)) {
            this.allowCommands = nbt.getBoolean("allowCommands");
        } else {
            this.allowCommands = this.defaultGameMode == WorldSettings.GameMode.CREATIVE;
        }

        if (nbt.contains("Player", 10)) {
            this.playerData = nbt.getCompound("Player");
            this.dimension = this.playerData.getInt("Dimension");
        }

        if (nbt.contains("GameRules", 10)) {
            this.gameRules.readNbt(nbt.getCompound("GameRules"));
        }

        if (nbt.contains("Difficulty", 99)) {
            this.difficulty = Difficulty.byId(nbt.getByte("Difficulty"));
        }

        if (nbt.contains("DifficultyLocked", 1)) {
            this.difficultyLocked = nbt.getBoolean("DifficultyLocked");
        }

        if (nbt.contains("BorderCenterX", 99)) {
            this.borderCenterX = nbt.getDouble("BorderCenterX");
        }

        if (nbt.contains("BorderCenterZ", 99)) {
            this.borderCenterZ = nbt.getDouble("BorderCenterZ");
        }

        if (nbt.contains("BorderSize", 99)) {
            this.borderSize = nbt.getDouble("BorderSize");
        }

        if (nbt.contains("BorderSizeLerpTime", 99)) {
            this.borderSizeLerpTime = nbt.getLong("BorderSizeLerpTime");
        }

        if (nbt.contains("BorderSizeLerpTarget", 99)) {
            this.borderSizeLerpTarget = nbt.getDouble("BorderSizeLerpTarget");
        }

        if (nbt.contains("BorderSafeZone", 99)) {
            this.borderSafeZone = nbt.getDouble("BorderSafeZone");
        }

        if (nbt.contains("BorderDamagePerBlock", 99)) {
            this.borderDamagePerBlock = nbt.getDouble("BorderDamagePerBlock");
        }

        if (nbt.contains("BorderWarningBlocks", 99)) {
            this.borderWarningBlocks = nbt.getInt("BorderWarningBlocks");
        }

        if (nbt.contains("BorderWarningTime", 99)) {
            this.borderWarningTime = nbt.getInt("BorderWarningTime");
        }
    }

    public WorldData(WorldSettings settings, String name) {
        this.setSettings(settings);
        this.name = name;
        this.difficulty = DEFAULT_DIFFICULTY;
        this.initialized = false;
    }

    public void setSettings(WorldSettings settings) {
        this.seed = settings.getSeed();
        this.defaultGameMode = settings.getGameMode();
        this.allowStructures = settings.allowStructures();
        this.hardcore = settings.isHardcore();
        this.generatorType = settings.getGeneratorType();
        this.generatorOptions = settings.getGeneratorOptions();
        this.allowCommands = settings.allowCommands();
    }

    public WorldData(WorldData data) {
        this.seed = data.seed;
        this.generatorType = data.generatorType;
        this.generatorOptions = data.generatorOptions;
        this.defaultGameMode = data.defaultGameMode;
        this.allowStructures = data.allowStructures;
        this.spawnX = data.spawnX;
        this.spawnY = data.spawnY;
        this.spawnZ = data.spawnZ;
        this.time = data.time;
        this.timeOfDay = data.timeOfDay;
        this.lastPlayed = data.lastPlayed;
        this.sizeOnDisk = data.sizeOnDisk;
        this.playerData = data.playerData;
        this.dimension = data.dimension;
        this.name = data.name;
        this.version = data.version;
        this.rainTime = data.rainTime;
        this.raining = data.raining;
        this.thunderTime = data.thunderTime;
        this.thundering = data.thundering;
        this.hardcore = data.hardcore;
        this.allowCommands = data.allowCommands;
        this.initialized = data.initialized;
        this.gameRules = data.gameRules;
        this.difficulty = data.difficulty;
        this.difficultyLocked = data.difficultyLocked;
        this.borderCenterX = data.borderCenterX;
        this.borderCenterZ = data.borderCenterZ;
        this.borderSize = data.borderSize;
        this.borderSizeLerpTime = data.borderSizeLerpTime;
        this.borderSizeLerpTarget = data.borderSizeLerpTarget;
        this.borderSafeZone = data.borderSafeZone;
        this.borderDamagePerBlock = data.borderDamagePerBlock;
        this.borderWarningTime = data.borderWarningTime;
        this.borderWarningBlocks = data.borderWarningBlocks;
    }

    public NbtCompound toNbt() {
        NbtCompound nbtcompound = new NbtCompound();
        this.writeNbt(nbtcompound, this.playerData);
        return nbtcompound;
    }

    public NbtCompound toNbt(NbtCompound playerData) {
        NbtCompound nbtcompound = new NbtCompound();
        this.writeNbt(nbtcompound, playerData);
        return nbtcompound;
    }

    private void writeNbt(NbtCompound nbt, NbtCompound playerData) {
        nbt.putLong("RandomSeed", this.seed);
        nbt.putString("generatorName", this.generatorType.getKey());
        nbt.putInt("generatorVersion", this.generatorType.getVersion());
        nbt.putString("generatorOptions", this.generatorOptions);
        nbt.putInt("GameType", this.defaultGameMode.getId());
        nbt.putBoolean("MapFeatures", this.allowStructures);
        nbt.putInt("SpawnX", this.spawnX);
        nbt.putInt("SpawnY", this.spawnY);
        nbt.putInt("SpawnZ", this.spawnZ);
        nbt.putLong("Time", this.time);
        nbt.putLong("DayTime", this.timeOfDay);
        nbt.putLong("SizeOnDisk", this.sizeOnDisk);
        nbt.putLong("LastPlayed", MinecraftServer.getTimeMillis());
        nbt.putString("LevelName", this.name);
        nbt.putInt("version", this.version);
        nbt.putInt("clearWeatherTime", this.clearWeatherTime);
        nbt.putInt("rainTime", this.rainTime);
        nbt.putBoolean("raining", this.raining);
        nbt.putInt("thunderTime", this.thunderTime);
        nbt.putBoolean("thundering", this.thundering);
        nbt.putBoolean("hardcore", this.hardcore);
        nbt.putBoolean("allowCommands", this.allowCommands);
        nbt.putBoolean("initialized", this.initialized);
        nbt.putDouble("BorderCenterX", this.borderCenterX);
        nbt.putDouble("BorderCenterZ", this.borderCenterZ);
        nbt.putDouble("BorderSize", this.borderSize);
        nbt.putLong("BorderSizeLerpTime", this.borderSizeLerpTime);
        nbt.putDouble("BorderSafeZone", this.borderSafeZone);
        nbt.putDouble("BorderDamagePerBlock", this.borderDamagePerBlock);
        nbt.putDouble("BorderSizeLerpTarget", this.borderSizeLerpTarget);
        nbt.putDouble("BorderWarningBlocks", this.borderWarningBlocks);
        nbt.putDouble("BorderWarningTime", this.borderWarningTime);
        if (this.difficulty != null) {
            nbt.putByte("Difficulty", (byte)this.difficulty.getId());
        }

        nbt.putBoolean("DifficultyLocked", this.difficultyLocked);
        nbt.put("GameRules", this.gameRules.toNbt());
        if (playerData != null) {
            nbt.put("Player", playerData);
        }
    }

    public long getSeed() {
        return this.seed;
    }

    public int getSpawnX() {
        return this.spawnX;
    }

    public int getSpawnY() {
        return this.spawnY;
    }

    public int getSpawnZ() {
        return this.spawnZ;
    }

    public long getTime() {
        return this.time;
    }

    public long getTimeOfDay() {
        return this.timeOfDay;
    }

    public long getSizeOnDisk() {
        return this.sizeOnDisk;
    }

    public NbtCompound getPlayerData() {
        return this.playerData;
    }

    public void setSpawnX(int x) {
        this.spawnX = x;
    }

    public void setSpawnY(int y) {
        this.spawnY = y;
    }

    public void setSpawnZ(int z) {
        this.spawnZ = z;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public void setTimeOfDay(long time) {
        this.timeOfDay = time;
    }

    public void setSpawnPoint(BlockPos pos) {
        this.spawnX = pos.getX();
        this.spawnY = pos.getY();
        this.spawnZ = pos.getZ();
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getVersion() {
        return this.version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public long getLastPlayed() {
        return this.lastPlayed;
    }

    public int getClearWeatherTime() {
        return this.clearWeatherTime;
    }

    public void setClearWeatherTime(int time) {
        this.clearWeatherTime = time;
    }

    public boolean isThundering() {
        return this.thundering;
    }

    public void setThundering(boolean thundering) {
        this.thundering = thundering;
    }

    public int getThunderTime() {
        return this.thunderTime;
    }

    public void setThunderTime(int thunderTime) {
        this.thunderTime = thunderTime;
    }

    public boolean isRaining() {
        return this.raining;
    }

    public void setRaining(boolean raining) {
        this.raining = raining;
    }

    public int getRainTime() {
        return this.rainTime;
    }

    public void setRainTime(int rainTime) {
        this.rainTime = rainTime;
    }

    public WorldSettings.GameMode getDefaultGamemode() {
        return this.defaultGameMode;
    }

    public boolean allowStructures() {
        return this.allowStructures;
    }

    public void setAllowStructures(boolean allowStructures) {
        this.allowStructures = allowStructures;
    }

    public void setDefaultGameMode(WorldSettings.GameMode gameMode) {
        this.defaultGameMode = gameMode;
    }

    public boolean isHardcore() {
        return this.hardcore;
    }

    public void setHardcore(boolean hardcore) {
        this.hardcore = hardcore;
    }

    public WorldGeneratorType getGeneratorType() {
        return this.generatorType;
    }

    public void setGeneratorType(WorldGeneratorType type) {
        this.generatorType = type;
    }

    public String getGeneratorOptions() {
        return this.generatorOptions;
    }

    public boolean allowCommands() {
        return this.allowCommands;
    }

    public void setAllowCommands(boolean allowCommands) {
        this.allowCommands = allowCommands;
    }

    public boolean isInitialized() {
        return this.initialized;
    }

    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }

    public GameRules getGameRules() {
        return this.gameRules;
    }

    public double getBorderCenterX() {
        return this.borderCenterX;
    }

    public double getBorderCenterZ() {
        return this.borderCenterZ;
    }

    public double getBorderSize() {
        return this.borderSize;
    }

    public void setBorderSize(double size) {
        this.borderSize = size;
    }

    public long getBorderSizeLerpTime() {
        return this.borderSizeLerpTime;
    }

    public void setBorderSizeLerpTime(long time) {
        this.borderSizeLerpTime = time;
    }

    public double getBorderSizeLerpTarget() {
        return this.borderSizeLerpTarget;
    }

    public void setBorderSizeLerpTarget(double target) {
        this.borderSizeLerpTarget = target;
    }

    public void setBorderCenterZ(double z) {
        this.borderCenterZ = z;
    }

    public void setBorderCenterX(double x) {
        this.borderCenterX = x;
    }

    public double getBorderSafeZone() {
        return this.borderSafeZone;
    }

    public void setBorderSafeZone(double zone) {
        this.borderSafeZone = zone;
    }

    public double getBorderDamagePerBlock() {
        return this.borderDamagePerBlock;
    }

    public void setBorderDamagePerBlock(double damage) {
        this.borderDamagePerBlock = damage;
    }

    public int getBorderWarningBlocks() {
        return this.borderWarningBlocks;
    }

    public int getBorderWarningTime() {
        return this.borderWarningTime;
    }

    public void setBorderWarningBlocks(int blocks) {
        this.borderWarningBlocks = blocks;
    }

    public void setBorderWarningTime(int time) {
        this.borderWarningTime = time;
    }

    public Difficulty getDifficulty() {
        return this.difficulty;
    }

    public void setDifficulty(Difficulty difficulty) {
        this.difficulty = difficulty;
    }

    public boolean isDifficultyLocked() {
        return this.difficultyLocked;
    }

    public void setDifficultyLocked(boolean locked) {
        this.difficultyLocked = locked;
    }

    public void populateCrashReport(CrashReportCategory category) {
        category.add("Level seed", new Callable<String>() {
            public String call() throws Exception {
                return String.valueOf(WorldData.this.getSeed());
            }
        });
        category.add(
            "Level generator",
            new Callable<String>() {
                public String call() throws Exception {
                    return String.format(
                        "ID %02d - %s, ver %d. Features enabled: %b",
                        WorldData.this.generatorType.getId(),
                        WorldData.this.generatorType.getKey(),
                        WorldData.this.generatorType.getVersion(),
                        WorldData.this.allowStructures
                    );
                }
            }
        );
        category.add("Level generator options", new Callable<String>() {
            public String call() throws Exception {
                return WorldData.this.generatorOptions;
            }
        });
        category.add("Level spawn location", new Callable<String>() {
            public String call() throws Exception {
                return CrashReportCategory.formatPosition(WorldData.this.spawnX, WorldData.this.spawnY, WorldData.this.spawnZ);
            }
        });
        category.add("Level time", new Callable<String>() {
            public String call() throws Exception {
                return String.format("%d game time, %d day time", WorldData.this.time, WorldData.this.timeOfDay);
            }
        });
        category.add("Level dimension", new Callable<String>() {
            public String call() throws Exception {
                return String.valueOf(WorldData.this.dimension);
            }
        });
        category.add("Level storage version", new Callable<String>() {
            public String call() throws Exception {
                String s = "Unknown?";

                try {
                    switch (WorldData.this.version) {
                        case 19132:
                            s = "McRegion";
                            break;
                        case 19133:
                            s = "Anvil";
                    }
                } catch (Throwable throwable) {
                }

                return String.format("0x%05X - %s", WorldData.this.version, s);
            }
        });
        category.add(
            "Level weather",
            new Callable<String>() {
                public String call() throws Exception {
                    return String.format(
                        "Rain time: %d (now: %b), thunder time: %d (now: %b)",
                        WorldData.this.rainTime,
                        WorldData.this.raining,
                        WorldData.this.thunderTime,
                        WorldData.this.thundering
                    );
                }
            }
        );
        category.add(
            "Level game mode",
            new Callable<String>() {
                public String call() throws Exception {
                    return String.format(
                        "Game mode: %s (ID %d). Hardcore: %b. Cheats: %b",
                        WorldData.this.defaultGameMode.getKey(),
                        WorldData.this.defaultGameMode.getId(),
                        WorldData.this.hardcore,
                        WorldData.this.allowCommands
                    );
                }
            }
        );
    }
}
