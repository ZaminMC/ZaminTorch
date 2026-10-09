package net.minecraft.client.render.entity.layer;

import net.minecraft.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.entity.SnowGolemRenderer;
import net.minecraft.client.render.model.block.ModelTransformations;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.living.mob.SnowGolemEntity;
import net.minecraft.item.ItemStack;

public class SnowGolemHeadLayer implements EntityRenderLayer<SnowGolemEntity> {
    private final SnowGolemRenderer parent;

    public SnowGolemHeadLayer(SnowGolemRenderer parent) {
        this.parent = parent;
    }

    public void render(SnowGolemEntity snowGolemEntity, float f, float g, float h, float i, float j, float k, float l) {
        if (!snowGolemEntity.isInvisible()) {
            GlStateManager.pushMatrix();
            this.parent.getModel().head.transform(0.0625F);
            float fx = 0.625F;
            GlStateManager.translatef(0.0F, -0.34375F, 0.0F);
            GlStateManager.rotatef(180.0F, 0.0F, 1.0F, 0.0F);
            GlStateManager.scalef(fx, -fx, -fx);
            Minecraft.getInstance().getItemInHandRenderer().render(snowGolemEntity, new ItemStack(Blocks.PUMPKIN, 1), ModelTransformations.Type.HEAD);
            GlStateManager.popMatrix();
        }
    }

    @Override
    public boolean colorsWhenDamaged() {
        return true;
    }
}
