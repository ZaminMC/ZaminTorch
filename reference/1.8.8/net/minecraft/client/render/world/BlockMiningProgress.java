package net.minecraft.client.render.world;

import net.minecraft.util.math.BlockPos;

public class BlockMiningProgress {
    private final int id;
    private final BlockPos pos;
    private int progress;
    private int lastUpdateTick;

    public BlockMiningProgress(int id, BlockPos pos) {
        this.id = id;
        this.pos = pos;
    }

    public BlockPos getPos() {
        return this.pos;
    }

    public void setProgress(int progress) {
        if (progress > 10) {
            progress = 10;
        }

        this.progress = progress;
    }

    public int getProgress() {
        return this.progress;
    }

    public void setLastUpdateTick(int lastUpdateTick) {
        this.lastUpdateTick = lastUpdateTick;
    }

    public int getLastUpdateTick() {
        return this.lastUpdateTick;
    }
}
