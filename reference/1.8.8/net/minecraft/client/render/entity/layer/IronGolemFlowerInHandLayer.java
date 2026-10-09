package net.minecraft.client.render.entity.layer;

import net.minecraft.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.block.BlockRenderDispatcher;
import net.minecraft.client.render.entity.IronGolemRenderer;
import net.minecraft.client.render.model.entity.IronGolemModel;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.entity.living.mob.IronGolemEntity;

public class IronGolemFlowerInHandLayer implements EntityRenderLayer<IronGolemEntity> {
    private final IronGolemRenderer parent;

    public IronGolemFlowerInHandLayer(IronGolemRenderer parent) {
        this.parent = parent;
    }

    public void render(IronGolemEntity ironGolemEntity, float f, float g, float h, float i, float j, float k, float l) {
        if (ironGolemEntity.getLookingAtVillagerTicks() != 0) {
            BlockRenderDispatcher blockrenderdispatcher = Minecraft.getInstance().getBlockRenderDispatcher();
            GlStateManager.enableRescaleNormal();
            GlStateManager.pushMatrix();
            GlStateManager.rotatef(5.0F + 180.0F * ((IronGolemModel)this.parent.getModel()).rightArm.rotationX / (float) Math.PI, 1.0F, 0.0F, 0.0F);
            GlStateManager.rotatef(90.0F, 1.0F, 0.0F, 0.0F);
            GlStateManager.translatef(-0.9375F, -0.625F, -0.9375F);
            float fx = 0.5F;
            GlStateManager.scalef(fx, -fx, fx);
            int ix = ironGolemEntity.getLightLevel(h);
            int jx = ix % 65536;
            int kx = ix / 65536;
            GLX.multiTexCoord2f(GLX.GL_TEXTURE1, jx / 1.0F, kx / 1.0F);
            GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            this.parent.bindTexture(TextureAtlas.BLOCKS_LOCATION);
            blockrenderdispatcher.renderAsItem(Blocks.RED_FLOWER.defaultState(), 1.0F);
            GlStateManager.popMatrix();
            GlStateManager.disableRescaleNormal();
        }
    }

    @Override
    public boolean colorsWhenDamaged() {
        return false;
    }
}
