package net.minecraft.world.biome;

import net.minecraft.block.Blocks;

public class StoneBeachBiome extends Biome {
    public StoneBeachBiome(int i) {
        super(i);
        this.passiveEntries.clear();
        this.surfaceBlock = Blocks.STONE.defaultState();
        this.subsurfaceBlock = Blocks.STONE.defaultState();
        this.decorator.treeAttempts = -999;
        this.decorator.deadBushAttempts = 0;
        this.decorator.sugarcaneAttempts = 0;
        this.decorator.cactusAttempts = 0;
    }
}
