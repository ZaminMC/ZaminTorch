package net.minecraft.world.biome;

import java.util.Random;
import net.minecraft.block.AbstractLeavesBlock;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.LogBlock;
import net.minecraft.block.PlanksBlock;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.living.mob.passive.animal.ChickenEntity;
import net.minecraft.entity.living.mob.passive.animal.tameable.OcelotEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.AbstractTreeFeature;
import net.minecraft.world.gen.feature.BushFeature;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.GiantJungleTreeFeature;
import net.minecraft.world.gen.feature.MelonPatchFeature;
import net.minecraft.world.gen.feature.TallPlantFeature;
import net.minecraft.world.gen.feature.TreeFeature;
import net.minecraft.world.gen.feature.VineFeature;

public class JungleBiome extends Biome {
    private boolean edge;
    private static final BlockState LOG = Blocks.LOG.defaultState().set(LogBlock.VARIANT, PlanksBlock.Variant.JUNGLE);
    private static final BlockState JUNGLE_LEAVES = Blocks.LEAVES
        .defaultState()
        .set(LeavesBlock.VARIANT, PlanksBlock.Variant.JUNGLE)
        .set(AbstractLeavesBlock.CHECK_DECAY, false);
    private static final BlockState OAK_LEAVES = Blocks.LEAVES
        .defaultState()
        .set(LeavesBlock.VARIANT, PlanksBlock.Variant.OAK)
        .set(AbstractLeavesBlock.CHECK_DECAY, false);

    public JungleBiome(int id, boolean edge) {
        super(id);
        this.edge = edge;
        if (edge) {
            this.decorator.treeAttempts = 2;
        } else {
            this.decorator.treeAttempts = 50;
        }

        this.decorator.grassAttempts = 25;
        this.decorator.flowerAttempts = 4;
        if (!edge) {
            this.monsterEntries.add(new Biome.SpawnEntry(OcelotEntity.class, 2, 1, 1));
        }

        this.passiveEntries.add(new Biome.SpawnEntry(ChickenEntity.class, 10, 4, 4));
    }

    @Override
    public AbstractTreeFeature pickTree(Random random) {
        if (random.nextInt(10) == 0) {
            return this.largeTree;
        } else if (random.nextInt(2) == 0) {
            return new BushFeature(LOG, OAK_LEAVES);
        } else {
            return !this.edge && random.nextInt(3) == 0
                ? new GiantJungleTreeFeature(false, 10, 20, LOG, JUNGLE_LEAVES)
                : new TreeFeature(false, 4 + random.nextInt(7), LOG, JUNGLE_LEAVES, true);
        }
    }

    @Override
    public Feature pickPlant(Random random) {
        return random.nextInt(4) == 0 ? new TallPlantFeature(TallPlantBlock.Type.FERN) : new TallPlantFeature(TallPlantBlock.Type.GRASS);
    }

    @Override
    public void decorate(World world, Random random, BlockPos pos) {
        super.decorate(world, random, pos);
        int i = random.nextInt(16) + 8;
        int j = random.nextInt(16) + 8;
        int k = random.nextInt(world.getHeight(pos.add(i, 0, j)).getY() * 2);
        new MelonPatchFeature().place(world, random, pos.add(i, k, j));
        VineFeature vinefeature = new VineFeature();

        for (int j1 = 0; j1 < 50; j1++) {
            k = random.nextInt(16) + 8;
            int l = 128;
            int i1 = random.nextInt(16) + 8;
            vinefeature.place(world, random, pos.add(k, 128, i1));
        }
    }
}
