package net.minecraft.world.dimension;

import net.minecraft.util.math.Vec3d;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.FixedBiomeSource;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.chunk.ChunkSource;
import net.minecraft.world.gen.chunk.NetherChunkGenerator;

public class NetherDimension extends Dimension {
    @Override
    public void initBiomeSource() {
        this.biomeSource = new FixedBiomeSource(Biome.HELL, 0.0F);
        this.yeetsWater = true;
        this.noSky = true;
        this.id = -1;
    }

    @Override
    public Vec3d getFogColor(float timeOfDay, float tickDelta) {
        return new Vec3d(0.2F, 0.03F, 0.03F);
    }

    @Override
    protected void initBrightnessTable() {
        float f = 0.1F;

        for (int i = 0; i <= 15; i++) {
            float f1 = 1.0F - i / 15.0F;
            this.brightnessTable[i] = (1.0F - f1) / (f1 * 3.0F + 1.0F) * (1.0F - f) + f;
        }
    }

    @Override
    public ChunkSource createChunkGenerator() {
        return new NetherChunkGenerator(this.world, this.world.getData().allowStructures(), this.world.getSeed());
    }

    @Override
    public boolean isNatural() {
        return false;
    }

    @Override
    public boolean isValidSpawnPoint(int x, int z) {
        return false;
    }

    @Override
    public float getTimeOfDay(long time, float tickDelta) {
        return 0.5F;
    }

    @Override
    public boolean hasSpawnPoint() {
        return false;
    }

    @Override
    public boolean isFogThick(int x, int z) {
        return true;
    }

    @Override
    public String getName() {
        return "Nether";
    }

    @Override
    public String getDataSuffix() {
        return "_nether";
    }

    @Override
    public WorldBorder createWorldBorder() {
        return new WorldBorder() {
            @Override
            public double getCenterX() {
                return super.getCenterX() / 8.0;
            }

            @Override
            public double getCenterZ() {
                return super.getCenterZ() / 8.0;
            }
        };
    }
}
