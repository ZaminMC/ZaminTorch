package net.minecraft.client.render.entity;

import net.minecraft.block.Blocks;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.render.block.entity.MobSpawnerRenderer;
import net.minecraft.entity.vehicle.SpawnerMinecartEntity;

public class SpawnerMinecartRenderer extends MinecartRenderer<SpawnerMinecartEntity> {
    public SpawnerMinecartRenderer(EntityRenderDispatcher entityRenderDispatcher) {
        super(entityRenderDispatcher);
    }

    protected void renderBlockInMinecart(SpawnerMinecartEntity spawnerMinecartEntity, float f, BlockState blockState) {
        super.renderBlockInMinecart(spawnerMinecartEntity, f, blockState);
        if (blockState.getBlock() == Blocks.MOB_SPAWNER) {
            MobSpawnerRenderer.renderDisplayEntity(
                spawnerMinecartEntity.getSpawner(), spawnerMinecartEntity.x, spawnerMinecartEntity.y, spawnerMinecartEntity.z, f
            );
        }
    }
}
