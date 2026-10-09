package net.minecraft.client.render.entity;

import net.minecraft.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.block.BlockRenderDispatcher;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.entity.PrimedTntEntity;
import net.minecraft.resource.Identifier;
import net.minecraft.util.math.MathHelper;

public class PrimedTntRenderer extends EntityRenderer<PrimedTntEntity> {
    public PrimedTntRenderer(EntityRenderDispatcher entityRenderDispatcher) {
        super(entityRenderDispatcher);
        this.shadowSize = 0.5F;
    }

    public void render(PrimedTntEntity primedTntEntity, double d, double e, double f, float g, float h) {
        BlockRenderDispatcher blockrenderdispatcher = Minecraft.getInstance().getBlockRenderDispatcher();
        GlStateManager.pushMatrix();
        GlStateManager.translatef((float)d, (float)e + 0.5F, (float)f);
        if (primedTntEntity.fuseTimer - h + 1.0F < 10.0F) {
            float fx = 1.0F - (primedTntEntity.fuseTimer - h + 1.0F) / 10.0F;
            fx = MathHelper.clamp(fx, 0.0F, 1.0F);
            fx *= fx;
            fx *= fx;
            float f1 = 1.0F + fx * 0.3F;
            GlStateManager.scalef(f1, f1, f1);
        }

        float f2 = (1.0F - (primedTntEntity.fuseTimer - h + 1.0F) / 100.0F) * 0.8F;
        this.bindTexture(primedTntEntity);
        GlStateManager.translatef(-0.5F, -0.5F, 0.5F);
        blockrenderdispatcher.renderAsItem(Blocks.TNT.defaultState(), primedTntEntity.getBrightness(h));
        GlStateManager.translatef(0.0F, 0.0F, 1.0F);
        if (primedTntEntity.fuseTimer / 5 % 2 == 0) {
            GlStateManager.disableTexture();
            GlStateManager.disableLighting();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(770, 772);
            GlStateManager.color4f(1.0F, 1.0F, 1.0F, f2);
            GlStateManager.polygonOffset(-3.0F, -3.0F);
            GlStateManager.enablePolygonOffset();
            blockrenderdispatcher.renderAsItem(Blocks.TNT.defaultState(), 1.0F);
            GlStateManager.polygonOffset(0.0F, 0.0F);
            GlStateManager.disablePolygonOffset();
            GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.disableBlend();
            GlStateManager.enableLighting();
            GlStateManager.enableTexture();
        }

        GlStateManager.popMatrix();
        super.render(primedTntEntity, d, e, f, g, h);
    }

    protected Identifier getTextureLocation(PrimedTntEntity primedTntEntity) {
        return TextureAtlas.BLOCKS_LOCATION;
    }
}
