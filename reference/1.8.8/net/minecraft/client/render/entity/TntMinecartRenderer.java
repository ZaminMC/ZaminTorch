package net.minecraft.client.render.entity;

import net.minecraft.block.Blocks;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.block.BlockRenderDispatcher;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.vehicle.TntMinecartEntity;
import net.minecraft.util.math.MathHelper;

public class TntMinecartRenderer extends MinecartRenderer<TntMinecartEntity> {
    public TntMinecartRenderer(EntityRenderDispatcher entityRenderDispatcher) {
        super(entityRenderDispatcher);
    }

    protected void renderBlockInMinecart(TntMinecartEntity tntMinecartEntity, float f, BlockState blockState) {
        int i = tntMinecartEntity.getFuseTicks();
        if (i > -1 && i - f + 1.0F < 10.0F) {
            float fx = 1.0F - (i - f + 1.0F) / 10.0F;
            fx = MathHelper.clamp(fx, 0.0F, 1.0F);
            fx *= fx;
            fx *= fx;
            float f1 = 1.0F + fx * 0.3F;
            GlStateManager.scalef(f1, f1, f1);
        }

        super.renderBlockInMinecart(tntMinecartEntity, f, blockState);
        if (i > -1 && i / 5 % 2 == 0) {
            BlockRenderDispatcher blockrenderdispatcher = Minecraft.getInstance().getBlockRenderDispatcher();
            GlStateManager.disableTexture();
            GlStateManager.disableLighting();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(770, 772);
            GlStateManager.color4f(1.0F, 1.0F, 1.0F, (1.0F - (i - f + 1.0F) / 100.0F) * 0.8F);
            GlStateManager.pushMatrix();
            blockrenderdispatcher.renderAsItem(Blocks.TNT.defaultState(), 1.0F);
            GlStateManager.popMatrix();
            GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.disableBlend();
            GlStateManager.enableLighting();
            GlStateManager.enableTexture();
        }
    }
}
