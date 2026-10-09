package net.minecraft.world.biome;

import java.util.Random;
import net.minecraft.block.Blocks;
import net.minecraft.block.FlowerBlock;
import net.minecraft.block.material.Material;
import net.minecraft.entity.living.mob.monster.SlimeEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.BlockStateStorage;
import net.minecraft.world.gen.feature.AbstractTreeFeature;

public class SwampBiome extends Biome {
    protected SwampBiome(int i) {
        super(i);
        this.decorator.treeAttempts = 2;
        this.decorator.flowerAttempts = 1;
        this.decorator.deadBushAttempts = 1;
        this.decorator.mushroomAttempts = 8;
        this.decorator.sugarcaneAttempts = 10;
        this.decorator.clayPatchAttempts = 1;
        this.decorator.lilyPadAttempts = 4;
        this.decorator.sandPatchAttempts = 0;
        this.decorator.gravelPatchAttempts = 0;
        this.decorator.grassAttempts = 5;
        this.waterFogColor = 14745518;
        this.monsterEntries.add(new Biome.SpawnEntry(SlimeEntity.class, 1, 1, 1));
    }

    @Override
    public AbstractTreeFeature pickTree(Random random) {
        return this.swampTree;
    }

    @Override
    public int getGrassColor(BlockPos pos) {
        double d0 = FOLIAGE_NOISE.getValue(pos.getX() * 0.0225, pos.getZ() * 0.0225);
        return d0 < -0.1 ? 5011004 : 6975545;
    }

    @Override
    public int getFoliageColor(BlockPos pos) {
        return 6975545;
    }

    @Override
    public FlowerBlock.Type getRandomFlower(Random random, BlockPos pos) {
        return FlowerBlock.Type.BLUE_ORCHID;
    }

    @Override
    public void prepareAndBuildSurfaces(World world, Random random, BlockStateStorage blocks, int x, int z, double depth) {
        double d0 = FOLIAGE_NOISE.getValue(x * 0.25, z * 0.25);
        if (d0 > 0.0) {
            int i = x & 15;
            int j = z & 15;

            for (int k = 255; k >= 0; k--) {
                if (blocks.get(j, k, i).getBlock().getMaterial() != Material.AIR) {
                    if (k == 62 && blocks.get(j, k, i).getBlock() != Blocks.WATER) {
                        blocks.set(j, k, i, Blocks.WATER.defaultState());
                        if (d0 < 0.12) {
                            blocks.set(j, k + 1, i, Blocks.LILY_PAD.defaultState());
                        }
                    }
                    break;
                }
            }
        }

        this.buildSurfaces(world, random, blocks, x, z, depth);
    }
}
