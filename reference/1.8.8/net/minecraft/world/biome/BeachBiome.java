package net.minecraft.world.biome;

import net.minecraft.block.Blocks;

public class BeachBiome extends Biome {
    public BeachBiome(int i) {
        super(i);
        this.passiveEntries.clear();
        this.surfaceBlock = Blocks.SAND.defaultState();
        this.subsurfaceBlock = Blocks.SAND.defaultState();
        this.decorator.treeAttempts = -999;
        this.decorator.deadBushAttempts = 0;
        this.decorator.sugarcaneAttempts = 0;
        this.decorator.cactusAttempts = 0;
    }
}
