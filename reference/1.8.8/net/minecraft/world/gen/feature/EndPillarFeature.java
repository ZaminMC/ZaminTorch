package net.minecraft.world.gen.feature;

import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EnderCrystalEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class EndPillarFeature extends Feature {
    private Block block;

    public EndPillarFeature(Block block) {
        this.block = block;
    }

    @Override
    public boolean place(World world, Random random, BlockPos pos) {
        if (world.isAir(pos) && world.getBlockState(pos.down()).getBlock() == this.block) {
            int i = random.nextInt(32) + 6;
            int j = random.nextInt(4) + 1;
            BlockPos.Mutable blockpos$mutable = new BlockPos.Mutable();

            for (int k = pos.getX() - j; k <= pos.getX() + j; k++) {
                for (int l = pos.getZ() - j; l <= pos.getZ() + j; l++) {
                    int i1 = k - pos.getX();
                    int j1 = l - pos.getZ();
                    if (i1 * i1 + j1 * j1 <= j * j + 1 && world.getBlockState(blockpos$mutable.set(k, pos.getY() - 1, l)).getBlock() != this.block) {
                        return false;
                    }
                }
            }

            for (int l1 = pos.getY(); l1 < pos.getY() + i && l1 < 256; l1++) {
                for (int i2 = pos.getX() - j; i2 <= pos.getX() + j; i2++) {
                    for (int j2 = pos.getZ() - j; j2 <= pos.getZ() + j; j2++) {
                        int k2 = i2 - pos.getX();
                        int k1 = j2 - pos.getZ();
                        if (k2 * k2 + k1 * k1 <= j * j + 1) {
                            world.setBlockState(new BlockPos(i2, l1, j2), Blocks.OBSIDIAN.defaultState(), 2);
                        }
                    }
                }
            }

            Entity entity = new EnderCrystalEntity(world);
            entity.setPositionAndAngles(pos.getX() + 0.5F, pos.getY() + i, pos.getZ() + 0.5F, random.nextFloat() * 360.0F, 0.0F);
            world.addEntity(entity);
            world.setBlockState(pos.up(i), Blocks.BEDROCK.defaultState(), 2);
            return true;
        } else {
            return false;
        }
    }
}
