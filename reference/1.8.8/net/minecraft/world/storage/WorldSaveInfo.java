package net.minecraft.world.storage;

import net.minecraft.world.WorldSettings;

public class WorldSaveInfo implements Comparable<WorldSaveInfo> {
    private final String saveName;
    private final String name;
    private final long lastPlayed;
    private final long size;
    private final boolean sameVersion;
    private final WorldSettings.GameMode gameMode;
    private final boolean hardcore;
    private final boolean cheats;

    public WorldSaveInfo(
        String fileName, String worldName, long lastPlayed, long size, WorldSettings.GameMode gameMode, boolean sameVersion, boolean hardcore, boolean cheats
    ) {
        this.saveName = fileName;
        this.name = worldName;
        this.lastPlayed = lastPlayed;
        this.size = size;
        this.gameMode = gameMode;
        this.sameVersion = sameVersion;
        this.hardcore = hardcore;
        this.cheats = cheats;
    }

    public String getSaveName() {
        return this.saveName;
    }

    public String getName() {
        return this.name;
    }

    public long getSize() {
        return this.size;
    }

    public boolean isSameVersion() {
        return this.sameVersion;
    }

    public long getLastPlayed() {
        return this.lastPlayed;
    }

    public int compareTo(WorldSaveInfo worldSaveInfo) {
        if (this.lastPlayed < worldSaveInfo.lastPlayed) {
            return 1;
        } else {
            return this.lastPlayed > worldSaveInfo.lastPlayed ? -1 : this.saveName.compareTo(worldSaveInfo.saveName);
        }
    }

    public WorldSettings.GameMode getGameMode() {
        return this.gameMode;
    }

    public boolean isHardcore() {
        return this.hardcore;
    }

    public boolean areCheatsEnabled() {
        return this.cheats;
    }
}
