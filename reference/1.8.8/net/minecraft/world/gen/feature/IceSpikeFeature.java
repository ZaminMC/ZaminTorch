package net.minecraft.world.gen.feature;

import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.material.Material;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public class IceSpikeFeature extends Feature {
    @Override
    public boolean place(World world, Random random, BlockPos pos) {
        while (world.isAir(pos) && pos.getY() > 2) {
            pos = pos.down();
        }

        if (world.getBlockState(pos).getBlock() != Blocks.SNOW) {
            return false;
        }

        pos = pos.up(random.nextInt(4));
        int i = random.nextInt(4) + 7;
        int j = i / 4 + random.nextInt(2);
        if (j > 1 && random.nextInt(60) == 0) {
            pos = pos.up(10 + random.nextInt(30));
        }

        for (int k = 0; k < i; k++) {
            float f = (1.0F - (float)k / i) * j;
            int l = MathHelper.ceil(f);

            for (int i1 = -l; i1 <= l; i1++) {
                float f1 = MathHelper.abs(i1) - 0.25F;

                for (int j1 = -l; j1 <= l; j1++) {
                    float f2 = MathHelper.abs(j1) - 0.25F;
                    if ((i1 == 0 && j1 == 0 || !(f1 * f1 + f2 * f2 > f * f)) && (i1 != -l && i1 != l && j1 != -l && j1 != l || !(random.nextFloat() > 0.75F))) {
                        Block block = world.getBlockState(pos.add(i1, k, j1)).getBlock();
                        if (block.getMaterial() == Material.AIR || block == Blocks.DIRT || block == Blocks.SNOW || block == Blocks.ICE) {
                            this.setBlockState(world, pos.add(i1, k, j1), Blocks.PACKED_ICE.defaultState());
                        }

                        if (k != 0 && l > 1) {
                            block = world.getBlockState(pos.add(i1, -k, j1)).getBlock();
                            if (block.getMaterial() == Material.AIR || block == Blocks.DIRT || block == Blocks.SNOW || block == Blocks.ICE) {
                                this.setBlockState(world, pos.add(i1, -k, j1), Blocks.PACKED_ICE.defaultState());
                            }
                        }
                    }
                }
            }
        }

        int k1 = j - 1;
        if (k1 < 0) {
            k1 = 0;
        } else if (k1 > 1) {
            k1 = 1;
        }

        for (int l1 = -k1; l1 <= k1; l1++) {
            for (int i2 = -k1; i2 <= k1; i2++) {
                BlockPos blockpos = pos.add(l1, -1, i2);
                int j2 = 50;
                if (Math.abs(l1) == 1 && Math.abs(i2) == 1) {
                    j2 = random.nextInt(5);
                }

                while (blockpos.getY() > 50) {
                    Block block1 = world.getBlockState(blockpos).getBlock();
                    if (block1.getMaterial() != Material.AIR
                        && block1 != Blocks.DIRT
                        && block1 != Blocks.SNOW
                        && block1 != Blocks.ICE
                        && block1 != Blocks.PACKED_ICE) {
                        break;
                    }

                    this.setBlockState(world, blockpos, Blocks.PACKED_ICE.defaultState());
                    blockpos = blockpos.down();
                    if (--j2 <= 0) {
                        blockpos = blockpos.down(random.nextInt(5) + 1);
                        j2 = random.nextInt(5);
                    }
                }
            }
        }

        return true;
    }
}
