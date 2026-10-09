package net.minecraft.client.render.block.entity;

import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.client.render.model.block.entity.ChestModel;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.resource.Identifier;

public class EnderChestRenderer extends BlockEntityRenderer<EnderChestBlockEntity> {
    private static final Identifier ENDER_CHEST_LOCATION = new Identifier("textures/entity/chest/ender.png");
    private ChestModel model = new ChestModel();

    public void render(EnderChestBlockEntity enderChestBlockEntity, double d, double e, double f, float g, int i) {
        int ix = 0;
        if (enderChestBlockEntity.hasWorld()) {
            ix = enderChestBlockEntity.getBlockMetadata();
        }

        if (i >= 0) {
            this.bindTexture(MINING_PROGRESS_LOCATIONS[i]);
            GlStateManager.matrixMode(5890);
            GlStateManager.pushMatrix();
            GlStateManager.scalef(4.0F, 4.0F, 1.0F);
            GlStateManager.translatef(0.0625F, 0.0625F, 0.0625F);
            GlStateManager.matrixMode(5888);
        } else {
            this.bindTexture(ENDER_CHEST_LOCATION);
        }

        GlStateManager.pushMatrix();
        GlStateManager.enableRescaleNormal();
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.translatef((float)d, (float)e + 1.0F, (float)f + 1.0F);
        GlStateManager.scalef(1.0F, -1.0F, -1.0F);
        GlStateManager.translatef(0.5F, 0.5F, 0.5F);
        int j = 0;
        if (ix == 2) {
            j = 180;
        }

        if (ix == 3) {
            j = 0;
        }

        if (ix == 4) {
            j = 90;
        }

        if (ix == 5) {
            j = -90;
        }

        GlStateManager.rotatef(j, 0.0F, 1.0F, 0.0F);
        GlStateManager.translatef(-0.5F, -0.5F, -0.5F);
        float fx = enderChestBlockEntity.lastAnimationProgress + (enderChestBlockEntity.animationProgress - enderChestBlockEntity.lastAnimationProgress) * g;
        fx = 1.0F - fx;
        fx = 1.0F - fx * fx * fx;
        this.model.lid.rotationX = -(fx * (float) Math.PI / 2.0F);
        this.model.renderParts();
        GlStateManager.disableRescaleNormal();
        GlStateManager.popMatrix();
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        if (i >= 0) {
            GlStateManager.matrixMode(5890);
            GlStateManager.popMatrix();
            GlStateManager.matrixMode(5888);
        }
    }
}
