package net.minecraft.client.render.block.entity;

import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.block.spawner.MobSpawner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.Entity;

public class MobSpawnerRenderer extends BlockEntityRenderer<MobSpawnerBlockEntity> {
    public void render(MobSpawnerBlockEntity mobSpawnerBlockEntity, double d, double e, double f, float g, int i) {
        GlStateManager.pushMatrix();
        GlStateManager.translatef((float)d + 0.5F, (float)e, (float)f + 0.5F);
        renderDisplayEntity(mobSpawnerBlockEntity.getSpawner(), d, e, f, g);
        GlStateManager.popMatrix();
    }

    public static void renderDisplayEntity(MobSpawner spawner, double dx, double dy, double dz, float tickDelta) {
        Entity entity = spawner.getDisplayEntity(spawner.getWorld());
        if (entity != null) {
            float f = 0.4375F;
            GlStateManager.translatef(0.0F, 0.4F, 0.0F);
            GlStateManager.rotatef(
                (float)(spawner.getLastRotation() + (spawner.getRotation() - spawner.getLastRotation()) * tickDelta) * 10.0F, 0.0F, 1.0F, 0.0F
            );
            GlStateManager.rotatef(-30.0F, 1.0F, 0.0F, 0.0F);
            GlStateManager.translatef(0.0F, -0.4F, 0.0F);
            GlStateManager.scalef(f, f, f);
            entity.setPositionAndAngles(dx, dy, dz, 0.0F, 0.0F);
            Minecraft.getInstance().getEntityRenderDispatcher().render(entity, 0.0, 0.0, 0.0, 0.0F, tickDelta);
        }
    }
}
