package net.minecraft.server.world;

import net.minecraft.entity.Entity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.BlockMiningProgressS2CPacket;
import net.minecraft.network.packet.s2c.play.SoundEventS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldEventS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.entity.living.player.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldEventListener;

public class ServerWorldEventListener implements WorldEventListener {
    private MinecraftServer server;
    private ServerWorld world;

    public ServerWorldEventListener(MinecraftServer server, ServerWorld world) {
        this.server = server;
        this.world = world;
    }

    @Override
    public void addParticle(
        int type, boolean ignoreDistance, double x, double y, double z, double velocityX, double velocityY, double velocityZ, int... parameters
    ) {
    }

    @Override
    public void notifyEntityAdded(Entity entity) {
        this.world.getEntityMap().onEntityAdded(entity);
    }

    @Override
    public void notifyEntityRemoved(Entity entity) {
        this.world.getEntityMap().onEntityRemoved(entity);
        this.world.getScoreboard().onEntityRemoved(entity);
    }

    @Override
    public void playSound(String sound, double x, double y, double z, float volume, float pitch) {
        this.server
            .getPlayerManager()
            .sendPacket(x, y, z, volume > 1.0F ? 16.0F * volume : 16.0, this.world.dimension.getId(), new SoundEventS2CPacket(sound, x, y, z, volume, pitch));
    }

    @Override
    public void playSound(PlayerEntity source, String sound, double x, double y, double z, float volume, float pitch) {
        this.server
            .getPlayerManager()
            .sendPacket(
                source, x, y, z, volume > 1.0F ? 16.0F * volume : 16.0, this.world.dimension.getId(), new SoundEventS2CPacket(sound, x, y, z, volume, pitch)
            );
    }

    @Override
    public void notifyRegionChanged(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    }

    @Override
    public void notifyBlockChanged(BlockPos pos) {
        this.world.getChunkMap().onBlockChanged(pos);
    }

    @Override
    public void notifyLightChanged(BlockPos pos) {
    }

    @Override
    public void playRecordMusic(String record, BlockPos pos) {
    }

    @Override
    public void doEvent(PlayerEntity source, int type, BlockPos pos, int data) {
        this.server
            .getPlayerManager()
            .sendPacket(source, pos.getX(), pos.getY(), pos.getZ(), 64.0, this.world.dimension.getId(), new WorldEventS2CPacket(type, pos, data, false));
    }

    @Override
    public void doGlobalEvent(int type, BlockPos pos, int data) {
        this.server.getPlayerManager().sendPacket(new WorldEventS2CPacket(type, pos, data, true));
    }

    @Override
    public void updateBlockMiningProgress(int id, BlockPos pos, int progress) {
        for (ServerPlayerEntity serverplayerentity : this.server.getPlayerManager().getAll()) {
            if (serverplayerentity != null && serverplayerentity.world == this.world && serverplayerentity.getNetworkId() != id) {
                double d0 = pos.getX() - serverplayerentity.x;
                double d1 = pos.getY() - serverplayerentity.y;
                double d2 = pos.getZ() - serverplayerentity.z;
                if (d0 * d0 + d1 * d1 + d2 * d2 < 1024.0) {
                    serverplayerentity.networkHandler.sendPacket(new BlockMiningProgressS2CPacket(id, pos, progress));
                }
            }
        }
    }
}
