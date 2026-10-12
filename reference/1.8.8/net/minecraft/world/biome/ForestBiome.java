package net.minecraft.world.biome;

import java.util.Random;
import net.minecraft.block.DoublePlantBlock;
import net.minecraft.block.FlowerBlock;
import net.minecraft.entity.living.mob.passive.animal.tameable.WolfEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.AbstractTreeFeature;
import net.minecraft.world.gen.feature.BirchTreeFeature;
import net.minecraft.world.gen.feature.DarkOakTreeFeature;
import net.minecraft.world.gen.feature.HugeMushroomFeature;

public class ForestBiome extends Biome {
    /**
     * The variant of forest. The possible values are:
     * <br> 0: normal
     * <br> 1: flower
     * <br> 2: birch
     * <br> 3: roofed
     */
    private int variant;
    protected static final BirchTreeFeature TALL_BIRCH_TREE = new BirchTreeFeature(false, true);
    protected static final BirchTreeFeature BIRCH_TREE = new BirchTreeFeature(false, false);
    protected static final DarkOakTreeFeature DARK_OAK_TREE = new DarkOakTreeFeature(false);

    public ForestBiome(int id, int type) {
        super(id);
        this.variant = type;
        this.decorator.treeAttempts = 10;
        this.decorator.grassAttempts = 2;
        if (this.variant == 1) {
            this.decorator.treeAttempts = 6;
            this.decorator.flowerAttempts = 100;
            this.decorator.grassAttempts = 1;
        }

        this.setMutatedColor(5159473);
        this.setTemperatureAndDownfall(0.7F, 0.8F);
        if (this.variant == 2) {
            this.baseColor = 353825;
            this.biomeColor = 3175492;
            this.setTemperatureAndDownfall(0.6F, 0.6F);
        }

        if (this.variant == 0) {
            this.passiveEntries.add(new Biome.SpawnEntry(WolfEntity.class, 5, 4, 4));
        }

        if (this.variant == 3) {
            this.decorator.treeAttempts = -999;
        }
    }

    @Override
    protected Biome setColor(int color, boolean modifyBaseColor) {
        if (this.variant == 2) {
            this.baseColor = 353825;
            this.biomeColor = color;
            if (modifyBaseColor) {
                this.baseColor = (this.baseColor & 16711422) >> 1;
            }

            return this;
        } else {
            return super.setColor(color, modifyBaseColor);
        }
    }

    @Override
    public AbstractTreeFeature pickTree(Random random) {
        if (this.variant == 3 && random.nextInt(3) > 0) {
            return DARK_OAK_TREE;
        } else {
            return this.variant != 2 && random.nextInt(5) != 0 ? this.tree : BIRCH_TREE;
        }
    }

    @Override
    public FlowerBlock.Type getRandomFlower(Random random, BlockPos pos) {
        if (this.variant == 1) {
            double d0 = MathHelper.clamp((1.0 + FOLIAGE_NOISE.getValue(pos.getX() / 48.0, pos.getZ() / 48.0)) / 2.0, 0.0, 0.9999);
            FlowerBlock.Type flowerblock$type = FlowerBlock.Type.values()[(int)(d0 * FlowerBlock.Type.values().length)];
            return flowerblock$type == FlowerBlock.Type.BLUE_ORCHID ? FlowerBlock.Type.POPPY : flowerblock$type;
        } else {
            return super.getRandomFlower(random, pos);
        }
    }

    @Override
    public void decorate(World world, Random random, BlockPos pos) {
        if (this.variant == 3) {
            for (int i = 0; i < 4; i++) {
                for (int j = 0; j < 4; j++) {
                    int k = i * 4 + 1 + 8 + random.nextInt(3);
                    int l = j * 4 + 1 + 8 + random.nextInt(3);
                    BlockPos blockpos = world.getHeight(pos.add(k, 0, l));
                    if (random.nextInt(20) == 0) {
                        HugeMushroomFeature hugemushroomfeature = new HugeMushroomFeature();
                        hugemushroomfeature.place(world, random, blockpos);
                    } else {
                        AbstractTreeFeature abstracttreefeature = this.pickTree(random);
                        abstracttreefeature.prepare();
                        if (abstracttreefeature.place(world, random, blockpos)) {
                            abstracttreefeature.placeSoil(world, random, blockpos);
                        }
                    }
                }
            }
        }

        int j1 = random.nextInt(5) - 3;
        if (this.variant == 1) {
            j1 += 2;
        }

        for (int k1 = 0; k1 < j1; k1++) {
            int l1 = random.nextInt(3);
            if (l1 == 0) {
                DOUBLE_PLANT.setVariant(DoublePlantBlock.Variant.SYRINGA);
            } else if (l1 == 1) {
                DOUBLE_PLANT.setVariant(DoublePlantBlock.Variant.ROSE);
            } else if (l1 == 2) {
                DOUBLE_PLANT.setVariant(DoublePlantBlock.Variant.PAEONIA);
            }

            for (int i2 = 0; i2 < 5; i2++) {
                int j2 = random.nextInt(16) + 8;
                int k2 = random.nextInt(16) + 8;
                int i1 = random.nextInt(world.getHeight(pos.add(j2, 0, k2)).getY() + 32);
                if (DOUBLE_PLANT.place(world, random, new BlockPos(pos.getX() + j2, i1, pos.getZ() + k2))) {
                    break;
                }
            }
        }

        super.decorate(world, random, pos);
    }

    @Override
    public int getGrassColor(BlockPos pos) {
        int i = super.getGrassColor(pos);
        return this.variant == 3 ? (i & 16711422) + 2634762 >> 1 : i;
    }

    @Override
    protected Biome mutate(int id) {
        if (this.id == Biome.FOREST.id) {
            ForestBiome forestbiome = new ForestBiome(id, 1);
            forestbiome.setHeight(new Biome.Height(this.baseHeight, this.heightVariation + 0.2F));
            forestbiome.setName("Flower Forest");
            forestbiome.setColor(6976549, true);
            forestbiome.setMutatedColor(8233509);
            return forestbiome;
        } else {
            return this.id != Biome.BIRCH_FOREST.id && this.id != Biome.BIRCH_FOREST_HILLS.id ? new MutatedBiome(id, this) {
                @Override
                public void decorate(World world, Random random, BlockPos pos) {
                    this.original.decorate(world, random, pos);
                }
            } : new MutatedBiome(id, this) {
                @Override
                public AbstractTreeFeature pickTree(Random random) {
                    return random.nextBoolean() ? ForestBiome.TALL_BIRCH_TREE : ForestBiome.BIRCH_TREE;
                }
            };
        }
    }
}
