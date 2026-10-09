package net.minecraft.world.biome;

import java.util.Random;
import net.minecraft.block.Blocks;
import net.minecraft.block.InfestedBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.BlockStateStorage;
import net.minecraft.world.gen.feature.AbstractTreeFeature;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.SpruceTreeFeature;
import net.minecraft.world.gen.feature.VeinFeature;

public class ExtremeHillsBiome extends Biome {
    private Feature monsterEggVein = new VeinFeature(Blocks.MONSTER_EGG.defaultState().set(InfestedBlock.VARIANT, InfestedBlock.Variant.STONE), 9);
    private SpruceTreeFeature spruceTree = new SpruceTreeFeature(false);
    private int normalVariant = 0;
    private int moreTreesVariant = 1;
    private int mutatedVariant = 2;
    private int variant = this.normalVariant;

    protected ExtremeHillsBiome(int id, boolean moreTrees) {
        super(id);
        if (moreTrees) {
            this.decorator.treeAttempts = 3;
            this.variant = this.moreTreesVariant;
        }
    }

    @Override
    public AbstractTreeFeature pickTree(Random random) {
        return random.nextInt(3) > 0 ? this.spruceTree : super.pickTree(random);
    }

    @Override
    public void decorate(World world, Random random, BlockPos pos) {
        super.decorate(world, random, pos);
        int i = 3 + random.nextInt(6);

        for (int j = 0; j < i; j++) {
            int k = random.nextInt(16);
            int l = random.nextInt(28) + 4;
            int i1 = random.nextInt(16);
            BlockPos blockpos = pos.add(k, l, i1);
            if (world.getBlockState(blockpos).getBlock() == Blocks.STONE) {
                world.setBlockState(blockpos, Blocks.EMERALD_ORE.defaultState(), 2);
            }
        }

        for (int j1 = 0; j1 < 7; j1++) {
            int k1 = random.nextInt(16);
            int l1 = random.nextInt(64);
            int i2 = random.nextInt(16);
            this.monsterEggVein.place(world, random, pos.add(k1, l1, i2));
        }
    }

    @Override
    public void prepareAndBuildSurfaces(World world, Random random, BlockStateStorage blocks, int x, int z, double depth) {
        this.surfaceBlock = Blocks.GRASS.defaultState();
        this.subsurfaceBlock = Blocks.DIRT.defaultState();
        if ((depth < -1.0 || depth > 2.0) && this.variant == this.mutatedVariant) {
            this.surfaceBlock = Blocks.GRAVEL.defaultState();
            this.subsurfaceBlock = Blocks.GRAVEL.defaultState();
        } else if (depth > 1.0 && this.variant != this.moreTreesVariant) {
            this.surfaceBlock = Blocks.STONE.defaultState();
            this.subsurfaceBlock = Blocks.STONE.defaultState();
        }

        this.buildSurfaces(world, random, blocks, x, z, depth);
    }

    private ExtremeHillsBiome mutate(Biome original) {
        this.variant = this.mutatedVariant;
        this.setColor(original.biomeColor, true);
        this.setName(original.name + " M");
        this.setHeight(new Biome.Height(original.baseHeight, original.heightVariation));
        this.setTemperatureAndDownfall(original.temperature, original.downfall);
        return this;
    }

    @Override
    protected Biome mutate(int id) {
        return new ExtremeHillsBiome(id, false).mutate(this);
    }
}
