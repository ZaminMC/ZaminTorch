package net.minecraft.client.world.color;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;
import net.minecraft.world.biome.Biome;

public class BiomeColors {
    private static final BiomeColors.ColorProvider GRASS = new BiomeColors.ColorProvider() {
        @Override
        public int getColor(Biome biome, BlockPos pos) {
            return biome.getGrassColor(pos);
        }
    };
    private static final BiomeColors.ColorProvider FOLIAGE = new BiomeColors.ColorProvider() {
        @Override
        public int getColor(Biome biome, BlockPos pos) {
            return biome.getFoliageColor(pos);
        }
    };
    private static final BiomeColors.ColorProvider WATER_FOG = new BiomeColors.ColorProvider() {
        @Override
        public int getColor(Biome biome, BlockPos pos) {
            return biome.waterFogColor;
        }
    };

    private static int getColor(WorldView world, BlockPos pos, BiomeColors.ColorProvider provider) {
        int i = 0;
        int j = 0;
        int k = 0;

        for (BlockPos.Mutable blockpos$mutable : BlockPos.iterateRegionMutable(pos.add(-1, 0, -1), pos.add(1, 0, 1))) {
            int l = provider.getColor(world.getBiome(blockpos$mutable), blockpos$mutable);
            i += (l & 0xFF0000) >> 16;
            j += (l & 0xFF00) >> 8;
            k += l & 0xFF;
        }

        return (i / 9 & 0xFF) << 16 | (j / 9 & 0xFF) << 8 | k / 9 & 0xFF;
    }

    public static int getGrassColor(WorldView world, BlockPos pos) {
        return getColor(world, pos, GRASS);
    }

    public static int getFoliageColor(WorldView world, BlockPos pos) {
        return getColor(world, pos, FOLIAGE);
    }

    public static int getWaterFogColor(WorldView world, BlockPos pos) {
        return getColor(world, pos, WATER_FOG);
    }

    interface ColorProvider {
        int getColor(Biome biome, BlockPos pos);
    }
}
