package net.minecraft.world;

import net.minecraft.entity.living.player.PlayerAbilities;
import net.minecraft.world.gen.WorldGeneratorType;

public final class WorldSettings {
    private final long seed;
    private final WorldSettings.GameMode gameMode;
    private final boolean generateStructures;
    private final boolean hardcore;
    private final WorldGeneratorType generatorType;
    private boolean allowCommands;
    private boolean generateBonusChest;
    private String generatorOptions = "";

    public WorldSettings(long seed, WorldSettings.GameMode gameMode, boolean generateStructures, boolean hardcore, WorldGeneratorType generatorType) {
        this.seed = seed;
        this.gameMode = gameMode;
        this.generateStructures = generateStructures;
        this.hardcore = hardcore;
        this.generatorType = generatorType;
    }

    public WorldSettings(WorldData data) {
        this(data.getSeed(), data.getDefaultGamemode(), data.allowStructures(), data.isHardcore(), data.getGeneratorType());
    }

    public WorldSettings enableBonusChest() {
        this.generateBonusChest = true;
        return this;
    }

    public WorldSettings enableCommands() {
        this.allowCommands = true;
        return this;
    }

    public WorldSettings setGeneratorOptions(String generatorOptions) {
        this.generatorOptions = generatorOptions;
        return this;
    }

    public boolean generateBonusChest() {
        return this.generateBonusChest;
    }

    public long getSeed() {
        return this.seed;
    }

    public WorldSettings.GameMode getGameMode() {
        return this.gameMode;
    }

    public boolean isHardcore() {
        return this.hardcore;
    }

    public boolean allowStructures() {
        return this.generateStructures;
    }

    public WorldGeneratorType getGeneratorType() {
        return this.generatorType;
    }

    public boolean allowCommands() {
        return this.allowCommands;
    }

    public static WorldSettings.GameMode getGameModeById(int id) {
        return WorldSettings.GameMode.byId(id);
    }

    public String getGeneratorOptions() {
        return this.generatorOptions;
    }

    public enum GameMode {
        NOT_SET(-1, ""),
        SURVIVAL(0, "survival"),
        CREATIVE(1, "creative"),
        ADVENTURE(2, "adventure"),
        SPECTATOR(3, "spectator");

        int id;
        String key;

        GameMode(int id, String key) {
            this.id = id;
            this.key = key;
        }

        public int getId() {
            return this.id;
        }

        public String getKey() {
            return this.key;
        }

        public void apply(PlayerAbilities abilities) {
            if (this == CREATIVE) {
                abilities.canFly = true;
                abilities.creativeMode = true;
                abilities.invulnerable = true;
            } else if (this == SPECTATOR) {
                abilities.canFly = true;
                abilities.creativeMode = false;
                abilities.invulnerable = true;
                abilities.flying = true;
            } else {
                abilities.canFly = false;
                abilities.creativeMode = false;
                abilities.invulnerable = false;
                abilities.flying = false;
            }

            abilities.canModifyWorld = !this.restrictsWorldModification();
        }

        public boolean restrictsWorldModification() {
            return this == ADVENTURE || this == SPECTATOR;
        }

        public boolean isCreative() {
            return this == CREATIVE;
        }

        public boolean isSurvival() {
            return this == SURVIVAL || this == ADVENTURE;
        }

        public static WorldSettings.GameMode byId(int id) {
            for (WorldSettings.GameMode worldsettings$gamemode : values()) {
                if (worldsettings$gamemode.id == id) {
                    return worldsettings$gamemode;
                }
            }

            return SURVIVAL;
        }

        public static WorldSettings.GameMode byKey(String key) {
            for (WorldSettings.GameMode worldsettings$gamemode : values()) {
                if (worldsettings$gamemode.key.equals(key)) {
                    return worldsettings$gamemode;
                }
            }

            return SURVIVAL;
        }
    }
}
