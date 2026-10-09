package net.minecraft.world;

import net.minecraft.entity.Entity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;

public interface WorldEventListener {
    void notifyBlockChanged(BlockPos pos);

    void notifyLightChanged(BlockPos pos);

    void notifyRegionChanged(int minX, int minY, int minZ, int maxX, int maxY, int maxZ);

    void playSound(String sound, double x, double y, double z, float volume, float pitch);

    void playSound(PlayerEntity source, String sound, double x, double y, double z, float volume, float pitch);

    void addParticle(int type, boolean ignoreDistance, double x, double y, double z, double velocityX, double velocityY, double velocityZ, int... parameters);

    void notifyEntityAdded(Entity entity);

    void notifyEntityRemoved(Entity entity);

    void playRecordMusic(String record, BlockPos pos);

    void doGlobalEvent(int type, BlockPos pos, int data);

    void doEvent(PlayerEntity source, int type, BlockPos pos, int data);

    void updateBlockMiningProgress(int id, BlockPos pos, int progress);
}
