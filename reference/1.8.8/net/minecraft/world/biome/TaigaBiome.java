package net.minecraft.world.biome;

import java.util.Random;
import net.minecraft.block.Blocks;
import net.minecraft.block.DirtBlock;
import net.minecraft.block.DoublePlantBlock;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.entity.living.mob.passive.animal.tameable.WolfEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.BlockStateStorage;
import net.minecraft.world.gen.feature.AbstractTreeFeature;
import net.minecraft.world.gen.feature.BlockVeinFeature;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.GiantSpruceTreeFeature;
import net.minecraft.world.gen.feature.PineTreeFeature;
import net.minecraft.world.gen.feature.SpruceTreeFeature;
import net.minecraft.world.gen.feature.TallPlantFeature;

public class TaigaBiome extends Biome {
    private static final PineTreeFeature PINE_TREE = new PineTreeFeature();
    private static final SpruceTreeFeature SPRUCE_TREE = new SpruceTreeFeature(false);
    private static final GiantSpruceTreeFeature GIANT_PINE_TREE = new GiantSpruceTreeFeature(false, false);
    private static final GiantSpruceTreeFeature GIANT_SPRUCE_TREE = new GiantSpruceTreeFeature(false, true);
    private static final BlockVeinFeature MOSSY_BOULDER = new BlockVeinFeature(Blocks.MOSSY_COBBLESTONE, 0);
    /**
     * The variant of forest. The possible values are:
     * <br> 0: normal
     * <br> 1: mega
     * <br> 2: mega spruce
     */
    private int variant;

    public TaigaBiome(int id, int variant) {
        super(id);
        this.variant = variant;
        this.passiveEntries.add(new Biome.SpawnEntry(WolfEntity.class, 8, 4, 4));
        this.decorator.treeAttempts = 10;
        if (variant != 1 && variant != 2) {
            this.decorator.grassAttempts = 1;
            this.decorator.mushroomAttempts = 1;
        } else {
            this.decorator.grassAttempts = 7;
            this.decorator.deadBushAttempts = 1;
            this.decorator.mushroomAttempts = 3;
        }
    }

    @Override
    public AbstractTreeFeature pickTree(Random random) {
        if ((this.variant == 1 || this.variant == 2) && random.nextInt(3) == 0) {
            return this.variant != 2 && random.nextInt(13) != 0 ? GIANT_PINE_TREE : GIANT_SPRUCE_TREE;
        } else {
            return random.nextInt(3) == 0 ? PINE_TREE : SPRUCE_TREE;
        }
    }

    @Override
    public Feature pickPlant(Random random) {
        return random.nextInt(5) > 0 ? new TallPlantFeature(TallPlantBlock.Type.FERN) : new TallPlantFeature(TallPlantBlock.Type.GRASS);
    }

    @Override
    public void decorate(World world, Random random, BlockPos pos) {
        if (this.variant == 1 || this.variant == 2) {
            int i = random.nextInt(3);

            for (int j = 0; j < i; j++) {
                int k = random.nextInt(16) + 8;
                int l = random.nextInt(16) + 8;
                BlockPos blockpos = world.getHeight(pos.add(k, 0, l));
                MOSSY_BOULDER.place(world, random, blockpos);
            }
        }

        DOUBLE_PLANT.setVariant(DoublePlantBlock.Variant.FERN);

        for (int i1 = 0; i1 < 7; i1++) {
            int j1 = random.nextInt(16) + 8;
            int k1 = random.nextInt(16) + 8;
            int l1 = random.nextInt(world.getHeight(pos.add(j1, 0, k1)).getY() + 32);
            DOUBLE_PLANT.place(world, random, pos.add(j1, l1, k1));
        }

        super.decorate(world, random, pos);
    }

    @Override
    public void prepareAndBuildSurfaces(World world, Random random, BlockStateStorage blocks, int x, int z, double depth) {
        if (this.variant == 1 || this.variant == 2) {
            this.surfaceBlock = Blocks.GRASS.defaultState();
            this.subsurfaceBlock = Blocks.DIRT.defaultState();
            if (depth > 1.75) {
                this.surfaceBlock = Blocks.DIRT.defaultState().set(DirtBlock.VARIANT, DirtBlock.Variant.COARSE_DIRT);
            } else if (depth > -0.95) {
                this.surfaceBlock = Blocks.DIRT.defaultState().set(DirtBlock.VARIANT, DirtBlock.Variant.PODZOL);
            }
        }

        this.buildSurfaces(world, random, blocks, x, z, depth);
    }

    @Override
    protected Biome mutate(int id) {
        return this.id == Biome.MEGA_TAIGA.id
            ? new TaigaBiome(id, 2)
                .setColor(5858897, true)
                .setName("Mega Spruce Taiga")
                .setMutatedColor(5159473)
                .setTemperatureAndDownfall(0.25F, 0.8F)
                .setHeight(new Biome.Height(this.baseHeight, this.heightVariation))
            : super.mutate(id);
    }
}
