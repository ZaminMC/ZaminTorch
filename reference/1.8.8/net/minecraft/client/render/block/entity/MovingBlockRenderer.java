package net.minecraft.client.render.block.entity;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.PistonBaseBlock;
import net.minecraft.block.PistonHeadBlock;
import net.minecraft.block.entity.MovingBlockEntity;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.block.BlockRenderDispatcher;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.platform.Lighting;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class MovingBlockRenderer extends BlockEntityRenderer<MovingBlockEntity> {
    private final BlockRenderDispatcher blockRenderDispatcher = Minecraft.getInstance().getBlockRenderDispatcher();

    public void render(MovingBlockEntity movingBlockEntity, double d, double e, double f, float g, int i) {
        BlockPos blockpos = movingBlockEntity.getPos();
        BlockState blockstate = movingBlockEntity.getMovedState();
        Block block = blockstate.getBlock();
        if (block.getMaterial() != Material.AIR && !(movingBlockEntity.getProgress(g) >= 1.0F)) {
            Tesselator tesselator = Tesselator.getInstance();
            BufferBuilder bufferbuilder = tesselator.getBuffer();
            this.bindTexture(TextureAtlas.BLOCKS_LOCATION);
            Lighting.turnOff();
            GlStateManager.blendFunc(770, 771);
            GlStateManager.enableBlend();
            GlStateManager.disableCull();
            if (Minecraft.isAmbientOcclusionEnabled()) {
                GlStateManager.shadeModel(7425);
            } else {
                GlStateManager.shadeModel(7424);
            }

            bufferbuilder.begin(7, DefaultVertexFormat.BLOCK);
            bufferbuilder.offset(
                (float)d - blockpos.getX() + movingBlockEntity.getRenderOffsetX(g),
                (float)e - blockpos.getY() + movingBlockEntity.getRenderOffsetY(g),
                (float)f - blockpos.getZ() + movingBlockEntity.getRenderOffsetZ(g)
            );
            World world = this.getWorld();
            if (block == Blocks.PISTON_HEAD && movingBlockEntity.getProgress(g) < 0.5F) {
                blockstate = blockstate.set(PistonHeadBlock.SHORT, true);
                this.blockRenderDispatcher
                    .getModelRenderer()
                    .render(world, this.blockRenderDispatcher.getModel(blockstate, world, blockpos), blockstate, blockpos, bufferbuilder, true);
            } else if (movingBlockEntity.isSource() && !movingBlockEntity.isExtending()) {
                PistonHeadBlock.Type pistonheadblock$type = block == Blocks.STICKY_PISTON ? PistonHeadBlock.Type.STICKY : PistonHeadBlock.Type.DEFAULT;
                BlockState blockstate1 = Blocks.PISTON_HEAD
                    .defaultState()
                    .set(PistonHeadBlock.TYPE, pistonheadblock$type)
                    .set(PistonHeadBlock.FACING, blockstate.get(PistonBaseBlock.FACING));
                blockstate1 = blockstate1.set(PistonHeadBlock.SHORT, movingBlockEntity.getProgress(g) >= 0.5F);
                this.blockRenderDispatcher
                    .getModelRenderer()
                    .render(world, this.blockRenderDispatcher.getModel(blockstate1, world, blockpos), blockstate1, blockpos, bufferbuilder, true);
                bufferbuilder.offset((float)d - blockpos.getX(), (float)e - blockpos.getY(), (float)f - blockpos.getZ());
                blockstate.set(PistonBaseBlock.EXTENDED, true);
                this.blockRenderDispatcher
                    .getModelRenderer()
                    .render(world, this.blockRenderDispatcher.getModel(blockstate, world, blockpos), blockstate, blockpos, bufferbuilder, true);
            } else {
                this.blockRenderDispatcher
                    .getModelRenderer()
                    .render(world, this.blockRenderDispatcher.getModel(blockstate, world, blockpos), blockstate, blockpos, bufferbuilder, false);
            }

            bufferbuilder.offset(0.0, 0.0, 0.0);
            tesselator.end();
            Lighting.turnOn();
        }
    }
}
