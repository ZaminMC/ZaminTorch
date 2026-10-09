package net.minecraft.world.biome;

import net.minecraft.block.Blocks;
import net.minecraft.entity.living.mob.passive.animal.MooshroomEntity;

public class MushroomBiome extends Biome {
    public MushroomBiome(int i) {
        super(i);
        this.decorator.treeAttempts = -100;
        this.decorator.flowerAttempts = -100;
        this.decorator.grassAttempts = -100;
        this.decorator.mushroomAttempts = 1;
        this.decorator.hugeMushroomAttempts = 1;
        this.surfaceBlock = Blocks.MYCELIUM.defaultState();
        this.monsterEntries.clear();
        this.passiveEntries.clear();
        this.waterEntries.clear();
        this.passiveEntries.add(new Biome.SpawnEntry(MooshroomEntity.class, 8, 4, 8));
    }
}
