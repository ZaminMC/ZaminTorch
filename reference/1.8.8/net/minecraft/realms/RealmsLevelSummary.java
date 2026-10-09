package net.minecraft.realms;

import net.minecraft.world.storage.WorldSaveInfo;

public class RealmsLevelSummary implements Comparable<RealmsLevelSummary> {
    private WorldSaveInfo levelSummary;

    public RealmsLevelSummary(WorldSaveInfo worldSaveInfo) {
        this.levelSummary = worldSaveInfo;
    }

    public int getGameMode() {
        return this.levelSummary.getGameMode().getId();
    }

    public String getLevelId() {
        return this.levelSummary.getSaveName();
    }

    public boolean hasCheats() {
        return this.levelSummary.areCheatsEnabled();
    }

    public boolean isHardcore() {
        return this.levelSummary.isHardcore();
    }

    public boolean isRequiresConversion() {
        return this.levelSummary.isSameVersion();
    }

    public String getLevelName() {
        return this.levelSummary.getName();
    }

    public long getLastPlayed() {
        return this.levelSummary.getLastPlayed();
    }

    public int compareTo(WorldSaveInfo other) {
        return this.levelSummary.compareTo(other);
    }

    public long getSizeOnDisk() {
        return this.levelSummary.getSize();
    }

    public int compareTo(RealmsLevelSummary realmsLevelSummary) {
        if (this.levelSummary.getLastPlayed() < realmsLevelSummary.getLastPlayed()) {
            return 1;
        } else {
            return this.levelSummary.getLastPlayed() > realmsLevelSummary.getLastPlayed()
                ? -1
                : this.levelSummary.getSaveName().compareTo(realmsLevelSummary.getLevelId());
        }
    }
}
