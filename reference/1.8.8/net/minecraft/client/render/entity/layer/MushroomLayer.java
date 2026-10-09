package net.minecraft.client.render.entity.layer;

import net.minecraft.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.block.BlockRenderDispatcher;
import net.minecraft.client.render.entity.MooshroomRenderer;
import net.minecraft.client.render.model.entity.QuadrupedModel;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.entity.living.mob.passive.animal.MooshroomEntity;

public class MushroomLayer implements EntityRenderLayer<MooshroomEntity> {
    private final MooshroomRenderer parent;

    public MushroomLayer(MooshroomRenderer parent) {
        this.parent = parent;
    }

    public void render(MooshroomEntity mooshroomEntity, float f, float g, float h, float i, float j, float k, float l) {
        if (!mooshroomEntity.isBaby() && !mooshroomEntity.isInvisible()) {
            BlockRenderDispatcher blockrenderdispatcher = Minecraft.getInstance().getBlockRenderDispatcher();
            this.parent.bindTexture(TextureAtlas.BLOCKS_LOCATION);
            GlStateManager.enableCull();
            GlStateManager.cullFace(1028);
            GlStateManager.pushMatrix();
            GlStateManager.scalef(1.0F, -1.0F, 1.0F);
            GlStateManager.translatef(0.2F, 0.35F, 0.5F);
            GlStateManager.rotatef(42.0F, 0.0F, 1.0F, 0.0F);
            GlStateManager.pushMatrix();
            GlStateManager.translatef(-0.5F, -0.5F, 0.5F);
            blockrenderdispatcher.renderAsItem(Blocks.RED_MUSHROOM.defaultState(), 1.0F);
            GlStateManager.popMatrix();
            GlStateManager.pushMatrix();
            GlStateManager.translatef(0.1F, 0.0F, -0.6F);
            GlStateManager.rotatef(42.0F, 0.0F, 1.0F, 0.0F);
            GlStateManager.translatef(-0.5F, -0.5F, 0.5F);
            blockrenderdispatcher.renderAsItem(Blocks.RED_MUSHROOM.defaultState(), 1.0F);
            GlStateManager.popMatrix();
            GlStateManager.popMatrix();
            GlStateManager.pushMatrix();
            ((QuadrupedModel)this.parent.getModel()).head.transform(0.0625F);
            GlStateManager.scalef(1.0F, -1.0F, 1.0F);
            GlStateManager.translatef(0.0F, 0.7F, -0.2F);
            GlStateManager.rotatef(12.0F, 0.0F, 1.0F, 0.0F);
            GlStateManager.translatef(-0.5F, -0.5F, 0.5F);
            blockrenderdispatcher.renderAsItem(Blocks.RED_MUSHROOM.defaultState(), 1.0F);
            GlStateManager.popMatrix();
            GlStateManager.cullFace(1029);
            GlStateManager.disableCull();
        }
    }

    @Override
    public boolean colorsWhenDamaged() {
        return true;
    }
}
