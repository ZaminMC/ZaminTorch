package net.minecraft.client.render.block;

import net.minecraft.block.LiquidBlock;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.client.render.texture.TextureAtlasSprite;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.WorldView;

public class LiquidRenderer {
    private TextureAtlasSprite[] lavaSprites = new TextureAtlasSprite[2];
    private TextureAtlasSprite[] waterSprites = new TextureAtlasSprite[2];

    public LiquidRenderer() {
        this.reload();
    }

    protected void reload() {
        TextureAtlas textureatlas = Minecraft.getInstance().getBlocksAtlas();
        this.lavaSprites[0] = textureatlas.getSprite("minecraft:blocks/lava_still");
        this.lavaSprites[1] = textureatlas.getSprite("minecraft:blocks/lava_flow");
        this.waterSprites[0] = textureatlas.getSprite("minecraft:blocks/water_still");
        this.waterSprites[1] = textureatlas.getSprite("minecraft:blocks/water_flow");
    }

    public boolean render(WorldView world, BlockState state, BlockPos pos, BufferBuilder bufferBuilder) {
        LiquidBlock liquidblock = (LiquidBlock)state.getBlock();
        liquidblock.updateShape(world, pos);
        TextureAtlasSprite[] atextureatlassprite = liquidblock.getMaterial() == Material.LAVA ? this.lavaSprites : this.waterSprites;
        int i = liquidblock.getColor(world, pos);
        float f = (i >> 16 & 0xFF) / 255.0F;
        float f1 = (i >> 8 & 0xFF) / 255.0F;
        float f2 = (i & 0xFF) / 255.0F;
        boolean flag = liquidblock.shouldRenderFace(world, pos.up(), Direction.UP);
        boolean flag1 = liquidblock.shouldRenderFace(world, pos.down(), Direction.DOWN);
        boolean[] aboolean = new boolean[]{
            liquidblock.shouldRenderFace(world, pos.north(), Direction.NORTH),
            liquidblock.shouldRenderFace(world, pos.south(), Direction.SOUTH),
            liquidblock.shouldRenderFace(world, pos.west(), Direction.WEST),
            liquidblock.shouldRenderFace(world, pos.east(), Direction.EAST)
        };
        if (!flag && !flag1 && !aboolean[0] && !aboolean[1] && !aboolean[2] && !aboolean[3]) {
            return false;
        }

        boolean flag2 = false;
        float f3 = 0.5F;
        float f4 = 1.0F;
        float f5 = 0.8F;
        float f6 = 0.6F;
        Material material = liquidblock.getMaterial();
        float f7 = this.getLiquidHeight(world, pos, material);
        float f8 = this.getLiquidHeight(world, pos.south(), material);
        float f9 = this.getLiquidHeight(world, pos.east().south(), material);
        float f10 = this.getLiquidHeight(world, pos.east(), material);
        double d0 = pos.getX();
        double d1 = pos.getY();
        double d2 = pos.getZ();
        float f11 = 0.001F;
        if (flag) {
            flag2 = true;
            TextureAtlasSprite textureatlassprite = atextureatlassprite[0];
            float f12 = (float)LiquidBlock.getFlowAngle(world, pos, material);
            if (f12 > -999.0F) {
                textureatlassprite = atextureatlassprite[1];
            }

            f7 -= f11;
            f8 -= f11;
            f9 -= f11;
            f10 -= f11;
            float f13;
            float f14;
            float f15;
            float f16;
            float f17;
            float f18;
            float f19;
            float f20;
            if (f12 < -999.0F) {
                f13 = textureatlassprite.getU(0.0);
                f17 = textureatlassprite.getV(0.0);
                f14 = f13;
                f18 = textureatlassprite.getV(16.0);
                f15 = textureatlassprite.getU(16.0);
                f19 = f18;
                f16 = f15;
                f20 = f17;
            } else {
                float f21 = MathHelper.sin(f12) * 0.25F;
                float f22 = MathHelper.cos(f12) * 0.25F;
                float f23 = 8.0F;
                f13 = textureatlassprite.getU(8.0F + (-f22 - f21) * 16.0F);
                f17 = textureatlassprite.getV(8.0F + (-f22 + f21) * 16.0F);
                f14 = textureatlassprite.getU(8.0F + (-f22 + f21) * 16.0F);
                f18 = textureatlassprite.getV(8.0F + (f22 + f21) * 16.0F);
                f15 = textureatlassprite.getU(8.0F + (f22 + f21) * 16.0F);
                f19 = textureatlassprite.getV(8.0F + (f22 - f21) * 16.0F);
                f16 = textureatlassprite.getU(8.0F + (f22 - f21) * 16.0F);
                f20 = textureatlassprite.getV(8.0F + (-f22 - f21) * 16.0F);
            }

            int k2 = liquidblock.getLightColor(world, pos);
            int l2 = k2 >> 16 & 65535;
            int i3 = k2 & 65535;
            float f24 = f4 * f;
            float f25 = f4 * f1;
            float f26 = f4 * f2;
            bufferBuilder.vertex(d0 + 0.0, d1 + f7, d2 + 0.0).color(f24, f25, f26, 1.0F).texture(f13, f17).texture(l2, i3).nextVertex();
            bufferBuilder.vertex(d0 + 0.0, d1 + f8, d2 + 1.0).color(f24, f25, f26, 1.0F).texture(f14, f18).texture(l2, i3).nextVertex();
            bufferBuilder.vertex(d0 + 1.0, d1 + f9, d2 + 1.0).color(f24, f25, f26, 1.0F).texture(f15, f19).texture(l2, i3).nextVertex();
            bufferBuilder.vertex(d0 + 1.0, d1 + f10, d2 + 0.0).color(f24, f25, f26, 1.0F).texture(f16, f20).texture(l2, i3).nextVertex();
            if (liquidblock.isNeighboringGap(world, pos.up())) {
                bufferBuilder.vertex(d0 + 0.0, d1 + f7, d2 + 0.0).color(f24, f25, f26, 1.0F).texture(f13, f17).texture(l2, i3).nextVertex();
                bufferBuilder.vertex(d0 + 1.0, d1 + f10, d2 + 0.0).color(f24, f25, f26, 1.0F).texture(f16, f20).texture(l2, i3).nextVertex();
                bufferBuilder.vertex(d0 + 1.0, d1 + f9, d2 + 1.0).color(f24, f25, f26, 1.0F).texture(f15, f19).texture(l2, i3).nextVertex();
                bufferBuilder.vertex(d0 + 0.0, d1 + f8, d2 + 1.0).color(f24, f25, f26, 1.0F).texture(f14, f18).texture(l2, i3).nextVertex();
            }
        }

        if (flag1) {
            float f35 = atextureatlassprite[0].getUMin();
            float f36 = atextureatlassprite[0].getUMax();
            float f37 = atextureatlassprite[0].getVMin();
            float f38 = atextureatlassprite[0].getVMax();
            int l1 = liquidblock.getLightColor(world, pos.down());
            int i2 = l1 >> 16 & 65535;
            int j2 = l1 & 65535;
            bufferBuilder.vertex(d0, d1, d2 + 1.0).color(f3, f3, f3, 1.0F).texture(f35, f38).texture(i2, j2).nextVertex();
            bufferBuilder.vertex(d0, d1, d2).color(f3, f3, f3, 1.0F).texture(f35, f37).texture(i2, j2).nextVertex();
            bufferBuilder.vertex(d0 + 1.0, d1, d2).color(f3, f3, f3, 1.0F).texture(f36, f37).texture(i2, j2).nextVertex();
            bufferBuilder.vertex(d0 + 1.0, d1, d2 + 1.0).color(f3, f3, f3, 1.0F).texture(f36, f38).texture(i2, j2).nextVertex();
            flag2 = true;
        }

        for (int i1 = 0; i1 < 4; i1++) {
            int j1 = 0;
            int k1 = 0;
            if (i1 == 0) {
                k1--;
            }

            if (i1 == 1) {
                k1++;
            }

            if (i1 == 2) {
                j1--;
            }

            if (i1 == 3) {
                j1++;
            }

            BlockPos blockpos = pos.add(j1, 0, k1);
            TextureAtlasSprite textureatlassprite1 = atextureatlassprite[1];
            if (aboolean[i1]) {
                float f39;
                float f40;
                double d3;
                double d4;
                double d5;
                double d6;
                if (i1 == 0) {
                    f39 = f7;
                    f40 = f10;
                    d3 = d0;
                    d5 = d0 + 1.0;
                    d4 = d2 + f11;
                    d6 = d2 + f11;
                } else if (i1 == 1) {
                    f39 = f9;
                    f40 = f8;
                    d3 = d0 + 1.0;
                    d5 = d0;
                    d4 = d2 + 1.0 - f11;
                    d6 = d2 + 1.0 - f11;
                } else if (i1 == 2) {
                    f39 = f8;
                    f40 = f7;
                    d3 = d0 + f11;
                    d5 = d0 + f11;
                    d4 = d2 + 1.0;
                    d6 = d2;
                } else {
                    f39 = f10;
                    f40 = f9;
                    d3 = d0 + 1.0 - f11;
                    d5 = d0 + 1.0 - f11;
                    d4 = d2;
                    d6 = d2 + 1.0;
                }

                flag2 = true;
                float f41 = textureatlassprite1.getU(0.0);
                float f27 = textureatlassprite1.getU(8.0);
                float f28 = textureatlassprite1.getV((1.0F - f39) * 16.0F * 0.5F);
                float f29 = textureatlassprite1.getV((1.0F - f40) * 16.0F * 0.5F);
                float f30 = textureatlassprite1.getV(8.0);
                int j = liquidblock.getLightColor(world, blockpos);
                int k = j >> 16 & 65535;
                int l = j & 65535;
                float f31 = i1 < 2 ? f5 : f6;
                float f32 = f4 * f31 * f;
                float f33 = f4 * f31 * f1;
                float f34 = f4 * f31 * f2;
                bufferBuilder.vertex(d3, d1 + f39, d4).color(f32, f33, f34, 1.0F).texture(f41, f28).texture(k, l).nextVertex();
                bufferBuilder.vertex(d5, d1 + f40, d6).color(f32, f33, f34, 1.0F).texture(f27, f29).texture(k, l).nextVertex();
                bufferBuilder.vertex(d5, d1 + 0.0, d6).color(f32, f33, f34, 1.0F).texture(f27, f30).texture(k, l).nextVertex();
                bufferBuilder.vertex(d3, d1 + 0.0, d4).color(f32, f33, f34, 1.0F).texture(f41, f30).texture(k, l).nextVertex();
                bufferBuilder.vertex(d3, d1 + 0.0, d4).color(f32, f33, f34, 1.0F).texture(f41, f30).texture(k, l).nextVertex();
                bufferBuilder.vertex(d5, d1 + 0.0, d6).color(f32, f33, f34, 1.0F).texture(f27, f30).texture(k, l).nextVertex();
                bufferBuilder.vertex(d5, d1 + f40, d6).color(f32, f33, f34, 1.0F).texture(f27, f29).texture(k, l).nextVertex();
                bufferBuilder.vertex(d3, d1 + f39, d4).color(f32, f33, f34, 1.0F).texture(f41, f28).texture(k, l).nextVertex();
            }
        }

        return flag2;
    }

    private float getLiquidHeight(WorldView world, BlockPos pos, Material material) {
        int i = 0;
        float f = 0.0F;

        for (int j = 0; j < 4; j++) {
            BlockPos blockpos = pos.add(-(j & 1), 0, -(j >> 1 & 1));
            if (world.getBlockState(blockpos.up()).getBlock().getMaterial() == material) {
                return 1.0F;
            }

            BlockState blockstate = world.getBlockState(blockpos);
            Material materialx = blockstate.getBlock().getMaterial();
            if (materialx == material) {
                int k = blockstate.get(LiquidBlock.LEVEL);
                if (k >= 8 || k == 0) {
                    f += LiquidBlock.getHeightLoss(k) * 10.0F;
                    i += 10;
                }

                f += LiquidBlock.getHeightLoss(k);
                i++;
            } else if (!materialx.isSolid()) {
                f++;
                i++;
            }
        }

        return 1.0F - f / i;
    }
}
