package net.minecraft.client.render.entity;

import net.minecraft.block.Block;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.block.BlockRenderDispatcher;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.client.resource.model.BakedModel;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.resource.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class FallingBlockRenderer extends EntityRenderer<FallingBlockEntity> {
    public FallingBlockRenderer(EntityRenderDispatcher entityRenderDispatcher) {
        super(entityRenderDispatcher);
        this.shadowSize = 0.5F;
    }

    public void render(FallingBlockEntity fallingBlockEntity, double d, double e, double f, float g, float h) {
        if (fallingBlockEntity.getBlock() != null) {
            this.bindTexture(TextureAtlas.BLOCKS_LOCATION);
            BlockState blockstate = fallingBlockEntity.getBlock();
            Block block = blockstate.getBlock();
            BlockPos blockpos = new BlockPos(fallingBlockEntity);
            World world = fallingBlockEntity.getWorld();
            if (blockstate != world.getBlockState(blockpos) && block.getRenderType() != -1) {
                if (block.getRenderType() == 3) {
                    GlStateManager.pushMatrix();
                    GlStateManager.translatef((float)d, (float)e, (float)f);
                    GlStateManager.disableLighting();
                    Tesselator tesselator = Tesselator.getInstance();
                    BufferBuilder bufferbuilder = tesselator.getBuffer();
                    bufferbuilder.begin(7, DefaultVertexFormat.BLOCK);
                    int i = blockpos.getX();
                    int j = blockpos.getY();
                    int k = blockpos.getZ();
                    bufferbuilder.offset(-i - 0.5F, -j, -k - 0.5F);
                    BlockRenderDispatcher blockrenderdispatcher = Minecraft.getInstance().getBlockRenderDispatcher();
                    BakedModel bakedmodel = blockrenderdispatcher.getModel(blockstate, world, null);
                    blockrenderdispatcher.getModelRenderer().render(world, bakedmodel, blockstate, blockpos, bufferbuilder, false);
                    bufferbuilder.offset(0.0, 0.0, 0.0);
                    tesselator.end();
                    GlStateManager.enableLighting();
                    GlStateManager.popMatrix();
                    super.render(fallingBlockEntity, d, e, f, g, h);
                }
            }
        }
    }

    protected Identifier getTextureLocation(FallingBlockEntity fallingBlockEntity) {
        return TextureAtlas.BLOCKS_LOCATION;
    }
}
