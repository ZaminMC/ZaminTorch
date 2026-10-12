package net.minecraft.world.dimension;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.FixedBiomeSource;
import net.minecraft.world.chunk.ChunkSource;
import net.minecraft.world.gen.chunk.TheEndChunkGenerator;

public class TheEndDimension extends Dimension {
    @Override
    public void initBiomeSource() {
        this.biomeSource = new FixedBiomeSource(Biome.THE_END, 0.0F);
        this.id = 1;
        this.noSky = true;
    }

    @Override
    public ChunkSource createChunkGenerator() {
        return new TheEndChunkGenerator(this.world, this.world.getSeed());
    }

    @Override
    public float getTimeOfDay(long time, float tickDelta) {
        return 0.0F;
    }

    @Override
    public float[] getSunriseColor(float timeOfDay, float tickDelta) {
        return null;
    }

    @Override
    public Vec3d getFogColor(float timeOfDay, float tickDelta) {
        int i = 10518688;
        float f = MathHelper.cos(timeOfDay * (float) Math.PI * 2.0F) * 2.0F + 0.5F;
        f = MathHelper.clamp(f, 0.0F, 1.0F);
        float f1 = (i >> 16 & 0xFF) / 255.0F;
        float f2 = (i >> 8 & 0xFF) / 255.0F;
        float f3 = (i & 0xFF) / 255.0F;
        f1 *= f * 0.0F + 0.15F;
        f2 *= f * 0.0F + 0.15F;
        f3 *= f * 0.0F + 0.15F;
        return new Vec3d(f1, f2, f3);
    }

    @Override
    public boolean hasGround() {
        return false;
    }

    @Override
    public boolean hasSpawnPoint() {
        return false;
    }

    @Override
    public boolean isNatural() {
        return false;
    }

    @Override
    public float getCloudHeight() {
        return 8.0F;
    }

    @Override
    public boolean isValidSpawnPoint(int x, int z) {
        return this.world.getSurfaceBlock(new BlockPos(x, 0, z)).getMaterial().blocksMovement();
    }

    @Override
    public BlockPos getForcedSpawnPoint() {
        return new BlockPos(100, 50, 0);
    }

    @Override
    public int getMinSpawnY() {
        return 50;
    }

    @Override
    public boolean isFogThick(int x, int z) {
        return true;
    }

    @Override
    public String getName() {
        return "The End";
    }

    @Override
    public String getDataSuffix() {
        return "_end";
    }
}
