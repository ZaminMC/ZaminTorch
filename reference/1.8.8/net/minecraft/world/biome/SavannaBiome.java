package net.minecraft.world.biome;

import java.util.Random;
import net.minecraft.block.Blocks;
import net.minecraft.block.DirtBlock;
import net.minecraft.block.DoublePlantBlock;
import net.minecraft.entity.living.mob.passive.animal.HorseBaseEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.BlockStateStorage;
import net.minecraft.world.gen.feature.AbstractTreeFeature;
import net.minecraft.world.gen.feature.AcaciaTreeFeature;

public class SavannaBiome extends Biome {
    private static final AcaciaTreeFeature ACACIA_TREE = new AcaciaTreeFeature(false);

    protected SavannaBiome(int i) {
        super(i);
        this.passiveEntries.add(new Biome.SpawnEntry(HorseBaseEntity.class, 1, 2, 6));
        this.decorator.treeAttempts = 1;
        this.decorator.flowerAttempts = 4;
        this.decorator.grassAttempts = 20;
    }

    @Override
    public AbstractTreeFeature pickTree(Random random) {
        return random.nextInt(5) > 0 ? ACACIA_TREE : this.tree;
    }

    @Override
    protected Biome mutate(int id) {
        Biome biome = new SavannaBiome.ShatteredSavannaBiome(id, this);
        biome.temperature = (this.temperature + 1.0F) * 0.5F;
        biome.baseHeight = this.baseHeight * 0.5F + 0.3F;
        biome.heightVariation = this.heightVariation * 0.5F + 1.2F;
        return biome;
    }

    @Override
    public void decorate(World world, Random random, BlockPos pos) {
        DOUBLE_PLANT.setVariant(DoublePlantBlock.Variant.GRASS);

        for (int i = 0; i < 7; i++) {
            int j = random.nextInt(16) + 8;
            int k = random.nextInt(16) + 8;
            int l = random.nextInt(world.getHeight(pos.add(j, 0, k)).getY() + 32);
            DOUBLE_PLANT.place(world, random, pos.add(j, l, k));
        }

        super.decorate(world, random, pos);
    }

    public static class ShatteredSavannaBiome extends MutatedBiome {
        public ShatteredSavannaBiome(int i, Biome biome) {
            super(i, biome);
            this.decorator.treeAttempts = 2;
            this.decorator.flowerAttempts = 2;
            this.decorator.grassAttempts = 5;
        }

        @Override
        public void prepareAndBuildSurfaces(World world, Random random, BlockStateStorage blocks, int x, int z, double depth) {
            this.surfaceBlock = Blocks.GRASS.defaultState();
            this.subsurfaceBlock = Blocks.DIRT.defaultState();
            if (depth > 1.75) {
                this.surfaceBlock = Blocks.STONE.defaultState();
                this.subsurfaceBlock = Blocks.STONE.defaultState();
            } else if (depth > -0.5) {
                this.surfaceBlock = Blocks.DIRT.defaultState().set(DirtBlock.VARIANT, DirtBlock.Variant.COARSE_DIRT);
            }

            this.buildSurfaces(world, random, blocks, x, z, depth);
        }

        @Override
        public void decorate(World world, Random random, BlockPos pos) {
            this.decorator.decorate(world, random, this, pos);
        }
    }
}
