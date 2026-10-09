package net.minecraft.world.biome;

import net.minecraft.block.Blocks;
import net.minecraft.entity.living.mob.monster.EndermanEntity;
import net.minecraft.world.gen.feature.decorator.EndIslandDecorator;

public class TheEndBiome extends Biome {
    public TheEndBiome(int i) {
        super(i);
        this.monsterEntries.clear();
        this.passiveEntries.clear();
        this.waterEntries.clear();
        this.ambientEntries.clear();
        this.monsterEntries.add(new Biome.SpawnEntry(EndermanEntity.class, 10, 4, 4));
        this.surfaceBlock = Blocks.DIRT.defaultState();
        this.subsurfaceBlock = Blocks.DIRT.defaultState();
        this.decorator = new EndIslandDecorator();
    }

    @Override
    public int getSkyColor(float temperature) {
        return 0;
    }
}
