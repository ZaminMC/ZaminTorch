package net.minecraft.world.biome;

import java.util.Random;
import net.minecraft.world.World;
import net.minecraft.world.chunk.BlockStateStorage;

public class OceanBiome extends Biome {
    public OceanBiome(int i) {
        super(i);
        this.passiveEntries.clear();
    }

    @Override
    public Biome.TemperatureCategory getTemperatureCategory() {
        return Biome.TemperatureCategory.OCEAN;
    }

    @Override
    public void prepareAndBuildSurfaces(World world, Random random, BlockStateStorage blocks, int x, int z, double depth) {
        super.prepareAndBuildSurfaces(world, random, blocks, x, z, depth);
    }
}
