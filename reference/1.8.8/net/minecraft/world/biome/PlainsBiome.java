package net.minecraft.world.biome;

import java.util.Random;
import net.minecraft.block.DoublePlantBlock;
import net.minecraft.block.FlowerBlock;
import net.minecraft.entity.living.mob.passive.animal.HorseBaseEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class PlainsBiome extends Biome {
    protected boolean mutated;

    protected PlainsBiome(int i) {
        super(i);
        this.setTemperatureAndDownfall(0.8F, 0.4F);
        this.setHeight(PLAINS_HEIGHT);
        this.passiveEntries.add(new Biome.SpawnEntry(HorseBaseEntity.class, 5, 2, 6));
        this.decorator.treeAttempts = -999;
        this.decorator.flowerAttempts = 4;
        this.decorator.grassAttempts = 10;
    }

    @Override
    public FlowerBlock.Type getRandomFlower(Random random, BlockPos pos) {
        double d0 = FOLIAGE_NOISE.getValue(pos.getX() / 200.0, pos.getZ() / 200.0);
        if (d0 < -0.8) {
            int j = random.nextInt(4);
            switch (j) {
                case 0:
                    return FlowerBlock.Type.ORANGE_TULIP;
                case 1:
                    return FlowerBlock.Type.RED_TULIP;
                case 2:
                    return FlowerBlock.Type.PINK_TULIP;
                case 3:
                default:
                    return FlowerBlock.Type.WHITE_TULIP;
            }
        } else if (random.nextInt(3) > 0) {
            int i = random.nextInt(3);
            if (i == 0) {
                return FlowerBlock.Type.POPPY;
            } else {
                return i == 1 ? FlowerBlock.Type.HOUSTONIA : FlowerBlock.Type.OXEY_DAISY;
            }
        } else {
            return FlowerBlock.Type.DANDELION;
        }
    }

    @Override
    public void decorate(World world, Random random, BlockPos pos) {
        double d0 = FOLIAGE_NOISE.getValue((pos.getX() + 8) / 200.0, (pos.getZ() + 8) / 200.0);
        if (d0 < -0.8) {
            this.decorator.flowerAttempts = 15;
            this.decorator.grassAttempts = 5;
        } else {
            this.decorator.flowerAttempts = 4;
            this.decorator.grassAttempts = 10;
            DOUBLE_PLANT.setVariant(DoublePlantBlock.Variant.GRASS);

            for (int i = 0; i < 7; i++) {
                int j = random.nextInt(16) + 8;
                int k = random.nextInt(16) + 8;
                int l = random.nextInt(world.getHeight(pos.add(j, 0, k)).getY() + 32);
                DOUBLE_PLANT.place(world, random, pos.add(j, l, k));
            }
        }

        if (this.mutated) {
            DOUBLE_PLANT.setVariant(DoublePlantBlock.Variant.SUNFLOWER);

            for (int i1 = 0; i1 < 10; i1++) {
                int j1 = random.nextInt(16) + 8;
                int k1 = random.nextInt(16) + 8;
                int l1 = random.nextInt(world.getHeight(pos.add(j1, 0, k1)).getY() + 32);
                DOUBLE_PLANT.place(world, random, pos.add(j1, l1, k1));
            }
        }

        super.decorate(world, random, pos);
    }

    @Override
    protected Biome mutate(int id) {
        PlainsBiome plainsbiome = new PlainsBiome(id);
        plainsbiome.setName("Sunflower Plains");
        plainsbiome.mutated = true;
        plainsbiome.setColor(9286496);
        plainsbiome.baseColor = 14273354;
        return plainsbiome;
    }
}
