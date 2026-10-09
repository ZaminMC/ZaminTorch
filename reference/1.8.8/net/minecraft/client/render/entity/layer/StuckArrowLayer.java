package net.minecraft.client.render.entity.layer;

import java.util.Random;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.model.Box;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.platform.Lighting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.util.math.MathHelper;

public class StuckArrowLayer implements EntityRenderLayer<LivingEntity> {
    private final LivingEntityRenderer parent;

    public StuckArrowLayer(LivingEntityRenderer parent) {
        this.parent = parent;
    }

    @Override
    public void render(
        LivingEntity entity, float walkAnimationProgress, float walkAnimationSpeed, float tickDelta, float bob, float yaw, float pitch, float scale
    ) {
        int i = entity.getStuckArrows();
        if (i > 0) {
            Entity entityx = new ArrowEntity(entity.world, entity.x, entity.y, entity.z);
            Random random = new Random(entity.getNetworkId());
            Lighting.turnOff();

            for (int j = 0; j < i; j++) {
                GlStateManager.pushMatrix();
                ModelPart modelpart = this.parent.getModel().pickPart(random);
                Box box = modelpart.boxes.get(random.nextInt(modelpart.boxes.size()));
                modelpart.transform(0.0625F);
                float f = random.nextFloat();
                float f1 = random.nextFloat();
                float f2 = random.nextFloat();
                float f3 = (box.minX + (box.maxX - box.minX) * f) / 16.0F;
                float f4 = (box.minY + (box.maxY - box.minY) * f1) / 16.0F;
                float f5 = (box.minZ + (box.maxZ - box.minZ) * f2) / 16.0F;
                GlStateManager.translatef(f3, f4, f5);
                f = f * 2.0F - 1.0F;
                f1 = f1 * 2.0F - 1.0F;
                f2 = f2 * 2.0F - 1.0F;
                f *= -1.0F;
                f1 *= -1.0F;
                f2 *= -1.0F;
                float f6 = MathHelper.sqrt(f * f + f2 * f2);
                entityx.lastYaw = entityx.yaw = (float)(Math.atan2(f, f2) * 180.0 / (float) Math.PI);
                entityx.lastPitch = entityx.pitch = (float)(Math.atan2(f1, f6) * 180.0 / (float) Math.PI);
                double d0 = 0.0;
                double d1 = 0.0;
                double d2 = 0.0;
                this.parent.getDispatcher().render(entityx, d0, d1, d2, 0.0F, tickDelta);
                GlStateManager.popMatrix();
            }

            Lighting.turnOn();
        }
    }

    @Override
    public boolean colorsWhenDamaged() {
        return false;
    }
}
