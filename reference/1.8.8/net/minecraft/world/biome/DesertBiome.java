package net.minecraft.world.biome;

import java.util.Random;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.DesertWellFeature;

public class DesertBiome extends Biome {
    public DesertBiome(int i) {
        super(i);
        this.passiveEntries.clear();
        this.surfaceBlock = Blocks.SAND.defaultState();
        this.subsurfaceBlock = Blocks.SAND.defaultState();
        this.decorator.treeAttempts = -999;
        this.decorator.deadBushAttempts = 2;
        this.decorator.sugarcaneAttempts = 50;
        this.decorator.cactusAttempts = 10;
        this.passiveEntries.clear();
    }

    @Override
    public void decorate(World world, Random random, BlockPos pos) {
        super.decorate(world, random, pos);
        if (random.nextInt(1000) == 0) {
            int i = random.nextInt(16) + 8;
            int j = random.nextInt(16) + 8;
            BlockPos blockpos = world.getHeight(pos.add(i, 0, j)).up();
            new DesertWellFeature().place(world, random, blockpos);
        }
    }
}
