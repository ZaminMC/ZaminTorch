package net.minecraft.world.gen.feature.decorator;

import java.util.Random;
import net.minecraft.block.Blocks;
import net.minecraft.block.FlowerBlock;
import net.minecraft.block.StoneBlock;
import net.minecraft.block.material.Material;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.chunk.OverworldGeneratorOptions;
import net.minecraft.world.gen.feature.AbstractTreeFeature;
import net.minecraft.world.gen.feature.BlockPatchFeature;
import net.minecraft.world.gen.feature.CactusFeature;
import net.minecraft.world.gen.feature.ClayPatchFeature;
import net.minecraft.world.gen.feature.DeadBushFeature;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.FlowerFeature;
import net.minecraft.world.gen.feature.HugeMushroomFeature;
import net.minecraft.world.gen.feature.LilyPadFeature;
import net.minecraft.world.gen.feature.LiquidFallFeature;
import net.minecraft.world.gen.feature.PlantFeature;
import net.minecraft.world.gen.feature.PumpkinPatchFeature;
import net.minecraft.world.gen.feature.SugarcaneFeature;
import net.minecraft.world.gen.feature.VeinFeature;

public class FeatureDecorator {
    protected World world;
    protected Random random;
    protected BlockPos pos;
    protected OverworldGeneratorOptions options;
    protected Feature clayPatch = new ClayPatchFeature(4);
    protected Feature sandPatch = new BlockPatchFeature(Blocks.SAND, 7);
    protected Feature gravelPatch = new BlockPatchFeature(Blocks.GRAVEL, 6);
    protected Feature dirtVein;
    protected Feature gravelVein;
    protected Feature graniteVein;
    protected Feature dioriteVein;
    protected Feature andesiteVein;
    protected Feature coalOreVein;
    protected Feature ironOreVein;
    protected Feature goldOreVein;
    protected Feature redstoneOreVein;
    protected Feature diamondOreVein;
    protected Feature lapisOreVein;
    protected FlowerFeature flower = new FlowerFeature(Blocks.YELLOW_FLOWER, FlowerBlock.Type.DANDELION);
    protected Feature brownMushroom = new PlantFeature(Blocks.BROWN_MUSHROOM);
    protected Feature redMushroom = new PlantFeature(Blocks.RED_MUSHROOM);
    protected Feature hugeRedMushroom = new HugeMushroomFeature();
    protected Feature sugarcane = new SugarcaneFeature();
    protected Feature cactus = new CactusFeature();
    protected Feature lilyPad = new LilyPadFeature();
    protected int lilyPadAttempts;
    protected int treeAttempts;
    protected int flowerAttempts = 2;
    protected int grassAttempts = 1;
    protected int deadBushAttempts;
    protected int mushroomAttempts;
    protected int sugarcaneAttempts;
    protected int cactusAttempts;
    protected int gravelPatchAttempts = 1;
    protected int sandPatchAttempts = 3;
    protected int clayPatchAttempts = 1;
    protected int hugeMushroomAttempts;
    public boolean placeLakes = true;

    public void decorate(World world, Random random, Biome biome, BlockPos pos) {
        if (this.world != null) {
            throw new RuntimeException("Already decorating");
        }

        this.world = world;
        String s = world.getData().getGeneratorOptions();
        if (s != null) {
            this.options = OverworldGeneratorOptions.Builder.fromJson(s).build();
        } else {
            this.options = OverworldGeneratorOptions.Builder.fromJson("").build();
        }

        this.random = random;
        this.pos = pos;
        this.dirtVein = new VeinFeature(Blocks.DIRT.defaultState(), this.options.dirtSize);
        this.gravelVein = new VeinFeature(Blocks.GRAVEL.defaultState(), this.options.gravelSize);
        this.graniteVein = new VeinFeature(Blocks.STONE.defaultState().set(StoneBlock.VARIANT, StoneBlock.Variant.GRANITE), this.options.graniteSize);
        this.dioriteVein = new VeinFeature(Blocks.STONE.defaultState().set(StoneBlock.VARIANT, StoneBlock.Variant.DIORITE), this.options.dioriteSize);
        this.andesiteVein = new VeinFeature(Blocks.STONE.defaultState().set(StoneBlock.VARIANT, StoneBlock.Variant.ANDESITE), this.options.andesiteSize);
        this.coalOreVein = new VeinFeature(Blocks.COAL_ORE.defaultState(), this.options.coalSize);
        this.ironOreVein = new VeinFeature(Blocks.IRON_ORE.defaultState(), this.options.ironSize);
        this.goldOreVein = new VeinFeature(Blocks.GOLD_ORE.defaultState(), this.options.goldSize);
        this.redstoneOreVein = new VeinFeature(Blocks.REDSTONE_ORE.defaultState(), this.options.redstoneSize);
        this.diamondOreVein = new VeinFeature(Blocks.DIAMOND_ORE.defaultState(), this.options.diamondSize);
        this.lapisOreVein = new VeinFeature(Blocks.LAPIS_ORE.defaultState(), this.options.lapisSize);
        this.decorate(biome);
        this.world = null;
        this.random = null;
    }

    protected void decorate(Biome biome) {
        this.placeVeins();

        for (int i = 0; i < this.sandPatchAttempts; i++) {
            int j = this.random.nextInt(16) + 8;
            int k = this.random.nextInt(16) + 8;
            this.sandPatch.place(this.world, this.random, this.world.getSurfaceHeight(this.pos.add(j, 0, k)));
        }

        for (int i1 = 0; i1 < this.clayPatchAttempts; i1++) {
            int l1 = this.random.nextInt(16) + 8;
            int i6 = this.random.nextInt(16) + 8;
            this.clayPatch.place(this.world, this.random, this.world.getSurfaceHeight(this.pos.add(l1, 0, i6)));
        }

        for (int j1 = 0; j1 < this.gravelPatchAttempts; j1++) {
            int i2 = this.random.nextInt(16) + 8;
            int j6 = this.random.nextInt(16) + 8;
            this.gravelPatch.place(this.world, this.random, this.world.getSurfaceHeight(this.pos.add(i2, 0, j6)));
        }

        int k1 = this.treeAttempts;
        if (this.random.nextInt(10) == 0) {
            k1++;
        }

        for (int j2 = 0; j2 < k1; j2++) {
            int k6 = this.random.nextInt(16) + 8;
            int l = this.random.nextInt(16) + 8;
            AbstractTreeFeature abstracttreefeature = biome.pickTree(this.random);
            abstracttreefeature.prepare();
            BlockPos blockpos = this.world.getHeight(this.pos.add(k6, 0, l));
            if (abstracttreefeature.place(this.world, this.random, blockpos)) {
                abstracttreefeature.placeSoil(this.world, this.random, blockpos);
            }
        }

        for (int k2 = 0; k2 < this.hugeMushroomAttempts; k2++) {
            int l6 = this.random.nextInt(16) + 8;
            int k10 = this.random.nextInt(16) + 8;
            this.hugeRedMushroom.place(this.world, this.random, this.world.getHeight(this.pos.add(l6, 0, k10)));
        }

        for (int l2 = 0; l2 < this.flowerAttempts; l2++) {
            int i7 = this.random.nextInt(16) + 8;
            int l10 = this.random.nextInt(16) + 8;
            int j14 = this.world.getHeight(this.pos.add(i7, 0, l10)).getY() + 32;
            if (j14 > 0) {
                int k17 = this.random.nextInt(j14);
                BlockPos blockpos1 = this.pos.add(i7, k17, l10);
                FlowerBlock.Type flowerblock$type = biome.getRandomFlower(this.random, blockpos1);
                FlowerBlock flowerblock = flowerblock$type.getGroup().getBlock();
                if (flowerblock.getMaterial() != Material.AIR) {
                    this.flower.set(flowerblock, flowerblock$type);
                    this.flower.place(this.world, this.random, blockpos1);
                }
            }
        }

        for (int i3 = 0; i3 < this.grassAttempts; i3++) {
            int j7 = this.random.nextInt(16) + 8;
            int i11 = this.random.nextInt(16) + 8;
            int k14 = this.world.getHeight(this.pos.add(j7, 0, i11)).getY() * 2;
            if (k14 > 0) {
                int l17 = this.random.nextInt(k14);
                biome.pickPlant(this.random).place(this.world, this.random, this.pos.add(j7, l17, i11));
            }
        }

        for (int j3 = 0; j3 < this.deadBushAttempts; j3++) {
            int k7 = this.random.nextInt(16) + 8;
            int j11 = this.random.nextInt(16) + 8;
            int l14 = this.world.getHeight(this.pos.add(k7, 0, j11)).getY() * 2;
            if (l14 > 0) {
                int i18 = this.random.nextInt(l14);
                new DeadBushFeature().place(this.world, this.random, this.pos.add(k7, i18, j11));
            }
        }

        for (int k3 = 0; k3 < this.lilyPadAttempts; k3++) {
            int l7 = this.random.nextInt(16) + 8;
            int k11 = this.random.nextInt(16) + 8;
            int i15 = this.world.getHeight(this.pos.add(l7, 0, k11)).getY() * 2;
            if (i15 > 0) {
                int j18 = this.random.nextInt(i15);
                BlockPos blockpos4 = this.pos.add(l7, j18, k11);

                while (blockpos4.getY() > 0) {
                    BlockPos blockpos7 = blockpos4.down();
                    if (!this.world.isAir(blockpos7)) {
                        break;
                    }

                    blockpos4 = blockpos7;
                }

                this.lilyPad.place(this.world, this.random, blockpos4);
            }
        }

        for (int l3 = 0; l3 < this.mushroomAttempts; l3++) {
            if (this.random.nextInt(4) == 0) {
                int i8 = this.random.nextInt(16) + 8;
                int l11 = this.random.nextInt(16) + 8;
                BlockPos blockpos2 = this.world.getHeight(this.pos.add(i8, 0, l11));
                this.brownMushroom.place(this.world, this.random, blockpos2);
            }

            if (this.random.nextInt(8) == 0) {
                int j8 = this.random.nextInt(16) + 8;
                int i12 = this.random.nextInt(16) + 8;
                int j15 = this.world.getHeight(this.pos.add(j8, 0, i12)).getY() * 2;
                if (j15 > 0) {
                    int k18 = this.random.nextInt(j15);
                    BlockPos blockpos5 = this.pos.add(j8, k18, i12);
                    this.redMushroom.place(this.world, this.random, blockpos5);
                }
            }
        }

        if (this.random.nextInt(4) == 0) {
            int i4 = this.random.nextInt(16) + 8;
            int k8 = this.random.nextInt(16) + 8;
            int j12 = this.world.getHeight(this.pos.add(i4, 0, k8)).getY() * 2;
            if (j12 > 0) {
                int k15 = this.random.nextInt(j12);
                this.brownMushroom.place(this.world, this.random, this.pos.add(i4, k15, k8));
            }
        }

        if (this.random.nextInt(8) == 0) {
            int j4 = this.random.nextInt(16) + 8;
            int l8 = this.random.nextInt(16) + 8;
            int k12 = this.world.getHeight(this.pos.add(j4, 0, l8)).getY() * 2;
            if (k12 > 0) {
                int l15 = this.random.nextInt(k12);
                this.redMushroom.place(this.world, this.random, this.pos.add(j4, l15, l8));
            }
        }

        for (int k4 = 0; k4 < this.sugarcaneAttempts; k4++) {
            int i9 = this.random.nextInt(16) + 8;
            int l12 = this.random.nextInt(16) + 8;
            int i16 = this.world.getHeight(this.pos.add(i9, 0, l12)).getY() * 2;
            if (i16 > 0) {
                int l18 = this.random.nextInt(i16);
                this.sugarcane.place(this.world, this.random, this.pos.add(i9, l18, l12));
            }
        }

        for (int l4 = 0; l4 < 10; l4++) {
            int j9 = this.random.nextInt(16) + 8;
            int i13 = this.random.nextInt(16) + 8;
            int j16 = this.world.getHeight(this.pos.add(j9, 0, i13)).getY() * 2;
            if (j16 > 0) {
                int i19 = this.random.nextInt(j16);
                this.sugarcane.place(this.world, this.random, this.pos.add(j9, i19, i13));
            }
        }

        if (this.random.nextInt(32) == 0) {
            int i5 = this.random.nextInt(16) + 8;
            int k9 = this.random.nextInt(16) + 8;
            int j13 = this.world.getHeight(this.pos.add(i5, 0, k9)).getY() * 2;
            if (j13 > 0) {
                int k16 = this.random.nextInt(j13);
                new PumpkinPatchFeature().place(this.world, this.random, this.pos.add(i5, k16, k9));
            }
        }

        for (int j5 = 0; j5 < this.cactusAttempts; j5++) {
            int l9 = this.random.nextInt(16) + 8;
            int k13 = this.random.nextInt(16) + 8;
            int l16 = this.world.getHeight(this.pos.add(l9, 0, k13)).getY() * 2;
            if (l16 > 0) {
                int j19 = this.random.nextInt(l16);
                this.cactus.place(this.world, this.random, this.pos.add(l9, j19, k13));
            }
        }

        if (this.placeLakes) {
            for (int k5 = 0; k5 < 50; k5++) {
                int i10 = this.random.nextInt(16) + 8;
                int l13 = this.random.nextInt(16) + 8;
                int i17 = this.random.nextInt(248) + 8;
                if (i17 > 0) {
                    int k19 = this.random.nextInt(i17);
                    BlockPos blockpos6 = this.pos.add(i10, k19, l13);
                    new LiquidFallFeature(Blocks.FLOWING_WATER).place(this.world, this.random, blockpos6);
                }
            }

            for (int l5 = 0; l5 < 20; l5++) {
                int j10 = this.random.nextInt(16) + 8;
                int i14 = this.random.nextInt(16) + 8;
                int j17 = this.random.nextInt(this.random.nextInt(this.random.nextInt(240) + 8) + 8);
                BlockPos blockpos3 = this.pos.add(j10, j17, i14);
                new LiquidFallFeature(Blocks.FLOWING_LAVA).place(this.world, this.random, blockpos3);
            }
        }
    }

    /**
     * Place a block vein with a linear distribution.
     */
    protected void placeVeinLinear(int count, Feature vein, int minHeight, int maxHeight) {
        if (maxHeight < minHeight) {
            int i = minHeight;
            minHeight = maxHeight;
            maxHeight = i;
        } else if (maxHeight == minHeight) {
            if (minHeight < 255) {
                maxHeight++;
            } else {
                minHeight--;
            }
        }

        for (int j = 0; j < count; j++) {
            BlockPos blockpos = this.pos.add(this.random.nextInt(16), this.random.nextInt(maxHeight - minHeight) + minHeight, this.random.nextInt(16));
            vein.place(this.world, this.random, blockpos);
        }
    }

    /**
     * Place a block vein with a triangular distribution centered around {@code minHeight}.
     */
    protected void placeVeinTriangular(int count, Feature vein, int minHeight, int maxHeight) {
        for (int i = 0; i < count; i++) {
            BlockPos blockpos = this.pos
                .add(this.random.nextInt(16), this.random.nextInt(maxHeight) + this.random.nextInt(maxHeight) + minHeight - maxHeight, this.random.nextInt(16));
            vein.place(this.world, this.random, blockpos);
        }
    }

    protected void placeVeins() {
        this.placeVeinLinear(this.options.dirtCount, this.dirtVein, this.options.dirtMinHeight, this.options.dirtMaxHeight);
        this.placeVeinLinear(this.options.gravelCount, this.gravelVein, this.options.gravelMinHeight, this.options.gravelMaxHeight);
        this.placeVeinLinear(this.options.dioriteCount, this.dioriteVein, this.options.dioriteMinHeight, this.options.dioriteMaxHeight);
        this.placeVeinLinear(this.options.graniteCount, this.graniteVein, this.options.graniteMinHeight, this.options.graniteMaxHeight);
        this.placeVeinLinear(this.options.andesiteCount, this.andesiteVein, this.options.andesiteMinHeight, this.options.andesiteMaxHeight);
        this.placeVeinLinear(this.options.coalCount, this.coalOreVein, this.options.coalMinHeight, this.options.coalMaxHeight);
        this.placeVeinLinear(this.options.ironCount, this.ironOreVein, this.options.ironMinHeight, this.options.ironMaxHeight);
        this.placeVeinLinear(this.options.goldCount, this.goldOreVein, this.options.goldMinHeight, this.options.goldMaxHeight);
        this.placeVeinLinear(this.options.redstoneCount, this.redstoneOreVein, this.options.redstoneMinHeight, this.options.redstoneMaxHeight);
        this.placeVeinLinear(this.options.diamondCount, this.diamondOreVein, this.options.diamondMinHeight, this.options.diamondMaxHeight);
        this.placeVeinTriangular(this.options.lapisCount, this.lapisOreVein, this.options.lapisMinHeight, this.options.lapisMaxHeight);
    }
}
