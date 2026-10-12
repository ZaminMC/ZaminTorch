package net.minecraft.world.dimension;

import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.biome.source.FixedBiomeSource;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.chunk.ChunkSource;
import net.minecraft.world.gen.WorldGeneratorType;
import net.minecraft.world.gen.chunk.DebugChunkGenerator;
import net.minecraft.world.gen.chunk.FlatChunkGenerator;
import net.minecraft.world.gen.chunk.FlatWorldGeneratorSettings;
import net.minecraft.world.gen.chunk.OverworldChunkGenerator;

public abstract class Dimension {
    public static final float[] MOON_PHASE_TO_SIZE = new float[]{1.0F, 0.75F, 0.5F, 0.25F, 0.0F, 0.25F, 0.5F, 0.75F};
    protected World world;
    private WorldGeneratorType generatorType;
    private String generatorOptions;
    protected BiomeSource biomeSource;
    protected boolean yeetsWater;
    protected boolean noSky;
    protected final float[] brightnessTable = new float[16];
    protected int id;
    private final float[] sunriseColor = new float[4];

    public final void init(World world) {
        this.world = world;
        this.generatorType = world.getData().getGeneratorType();
        this.generatorOptions = world.getData().getGeneratorOptions();
        this.initBiomeSource();
        this.initBrightnessTable();
    }

    protected void initBrightnessTable() {
        float f = 0.0F;

        for (int i = 0; i <= 15; i++) {
            float f1 = 1.0F - i / 15.0F;
            this.brightnessTable[i] = (1.0F - f1) / (f1 * 3.0F + 1.0F) * (1.0F - f) + f;
        }
    }

    protected void initBiomeSource() {
        WorldGeneratorType worldgeneratortype = this.world.getData().getGeneratorType();
        if (worldgeneratortype == WorldGeneratorType.FLAT) {
            FlatWorldGeneratorSettings flatworldgeneratorsettings = FlatWorldGeneratorSettings.of(this.world.getData().getGeneratorOptions());
            this.biomeSource = new FixedBiomeSource(Biome.byId(flatworldgeneratorsettings.getBiome(), Biome.DEFAULT), 0.5F);
        } else if (worldgeneratortype == WorldGeneratorType.DEBUG_ALL_BLOCK_STATES) {
            this.biomeSource = new FixedBiomeSource(Biome.PLAINS, 0.0F);
        } else {
            this.biomeSource = new BiomeSource(this.world);
        }
    }

    public ChunkSource createChunkGenerator() {
        if (this.generatorType == WorldGeneratorType.FLAT) {
            return new FlatChunkGenerator(this.world, this.world.getSeed(), this.world.getData().allowStructures(), this.generatorOptions);
        } else if (this.generatorType == WorldGeneratorType.DEBUG_ALL_BLOCK_STATES) {
            return new DebugChunkGenerator(this.world);
        } else {
            return this.generatorType == WorldGeneratorType.CUSTOMIZED
                ? new OverworldChunkGenerator(this.world, this.world.getSeed(), this.world.getData().allowStructures(), this.generatorOptions)
                : new OverworldChunkGenerator(this.world, this.world.getSeed(), this.world.getData().allowStructures(), this.generatorOptions);
        }
    }

    public boolean isValidSpawnPoint(int x, int z) {
        return this.world.getSurfaceBlock(new BlockPos(x, 0, z)) == Blocks.GRASS;
    }

    public float getTimeOfDay(long time, float tickDelta) {
        int i = (int)(time % 24000L);
        float f = (i + tickDelta) / 24000.0F - 0.25F;
        if (f < 0.0F) {
            f++;
        }

        if (f > 1.0F) {
            f--;
        }

        float f1 = f;
        f = 1.0F - (float)((Math.cos(f * Math.PI) + 1.0) / 2.0);
        return f1 + (f - f1) / 3.0F;
    }

    public int getMoonPhase(long time) {
        return (int)(time / 24000L % 8L + 8L) % 8;
    }

    public boolean isNatural() {
        return true;
    }

    public float[] getSunriseColor(float timeOfDay, float tickDelta) {
        float f = 0.4F;
        float f1 = MathHelper.cos(timeOfDay * (float) Math.PI * 2.0F) - 0.0F;
        float f2 = -0.0F;
        if (f1 >= f2 - f && f1 <= f2 + f) {
            float f3 = (f1 - f2) / f * 0.5F + 0.5F;
            float f4 = 1.0F - (1.0F - MathHelper.sin(f3 * (float) Math.PI)) * 0.99F;
            f4 *= f4;
            this.sunriseColor[0] = f3 * 0.3F + 0.7F;
            this.sunriseColor[1] = f3 * f3 * 0.7F + 0.2F;
            this.sunriseColor[2] = f3 * f3 * 0.0F + 0.2F;
            this.sunriseColor[3] = f4;
            return this.sunriseColor;
        } else {
            return null;
        }
    }

    public Vec3d getFogColor(float timeOfDay, float tickDelta) {
        float f = MathHelper.cos(timeOfDay * (float) Math.PI * 2.0F) * 2.0F + 0.5F;
        f = MathHelper.clamp(f, 0.0F, 1.0F);
        float f1 = 0.7529412F;
        float f2 = 0.84705883F;
        float f3 = 1.0F;
        f1 *= f * 0.94F + 0.06F;
        f2 *= f * 0.94F + 0.06F;
        f3 *= f * 0.91F + 0.09F;
        return new Vec3d(f1, f2, f3);
    }

    public boolean hasSpawnPoint() {
        return true;
    }

    public static Dimension fromId(int id) {
        if (id == -1) {
            return new NetherDimension();
        } else if (id == 0) {
            return new OverworldDimension();
        } else {
            return id == 1 ? new TheEndDimension() : null;
        }
    }

    public float getCloudHeight() {
        return 128.0F;
    }

    public boolean hasGround() {
        return true;
    }

    public BlockPos getForcedSpawnPoint() {
        return null;
    }

    public int getMinSpawnY() {
        return this.generatorType == WorldGeneratorType.FLAT ? 4 : this.world.getSeaLevel() + 1;
    }

    public double getFogSize() {
        return this.generatorType == WorldGeneratorType.FLAT ? 1.0 : 0.03125;
    }

    public boolean isFogThick(int x, int z) {
        return false;
    }

    public abstract String getName();

    public abstract String getDataSuffix();

    public BiomeSource getBiomeSource() {
        return this.biomeSource;
    }

    public boolean yeetsWater() {
        return this.yeetsWater;
    }

    public boolean hasNoSky() {
        return this.noSky;
    }

    public float[] getBrightnessTable() {
        return this.brightnessTable;
    }

    public int getId() {
        return this.id;
    }

    public WorldBorder createWorldBorder() {
        return new WorldBorder();
    }
}
