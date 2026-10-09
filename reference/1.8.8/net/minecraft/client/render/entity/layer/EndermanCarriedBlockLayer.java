package net.minecraft.client.render.entity.layer;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.block.BlockRenderDispatcher;
import net.minecraft.client.render.entity.EndermanRenderer;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.entity.living.mob.monster.EndermanEntity;

public class EndermanCarriedBlockLayer implements EntityRenderLayer<EndermanEntity> {
    private final EndermanRenderer parent;

    public EndermanCarriedBlockLayer(EndermanRenderer parent) {
        this.parent = parent;
    }

    public void render(EndermanEntity endermanEntity, float f, float g, float h, float i, float j, float k, float l) {
        BlockState blockstate = endermanEntity.getCarriedBlock();
        if (blockstate.getBlock().getMaterial() != Material.AIR) {
            BlockRenderDispatcher blockrenderdispatcher = Minecraft.getInstance().getBlockRenderDispatcher();
            GlStateManager.enableRescaleNormal();
            GlStateManager.pushMatrix();
            GlStateManager.translatef(0.0F, 0.6875F, -0.75F);
            GlStateManager.rotatef(20.0F, 1.0F, 0.0F, 0.0F);
            GlStateManager.rotatef(45.0F, 0.0F, 1.0F, 0.0F);
            GlStateManager.translatef(0.25F, 0.1875F, 0.25F);
            float fx = 0.5F;
            GlStateManager.scalef(-fx, -fx, fx);
            int ix = endermanEntity.getLightLevel(h);
            int jx = ix % 65536;
            int kx = ix / 65536;
            GLX.multiTexCoord2f(GLX.GL_TEXTURE1, jx / 1.0F, kx / 1.0F);
            GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            this.parent.bindTexture(TextureAtlas.BLOCKS_LOCATION);
            blockrenderdispatcher.renderAsItem(blockstate, 1.0F);
            GlStateManager.popMatrix();
            GlStateManager.disableRescaleNormal();
        }
    }

    @Override
    public boolean colorsWhenDamaged() {
        return false;
    }
}
