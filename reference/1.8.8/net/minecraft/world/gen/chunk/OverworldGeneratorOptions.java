package net.minecraft.world.gen.chunk;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;
import net.minecraft.util.JsonUtils;
import net.minecraft.world.biome.Biome;

public class OverworldGeneratorOptions {
    public final float coordinateScale;
    public final float heightScale;
    public final float upperLimitScale;
    public final float lowerLimitScale;
    public final float depthNoiseScaleX;
    public final float depthNoiseScaleZ;
    public final float depthNoiseScaleExponent;
    public final float mainNoiseScaleX;
    public final float mainNoiseScaleY;
    public final float mainNoiseScaleZ;
    public final float baseSize;
    public final float stretchY;
    public final float biomeDepthWeight;
    public final float biomeDepthOffset;
    public final float biomeScaleWeight;
    public final float biomeScaleOffset;
    public final int seaLevel;
    public final boolean useCaves;
    public final boolean useDungeons;
    public final int dungeonChance;
    public final boolean useStrongholds;
    public final boolean useVillages;
    public final boolean useMineshafts;
    public final boolean useTemples;
    public final boolean useMonuments;
    public final boolean useRavines;
    public final boolean useWaterLakes;
    public final int waterLakeChance;
    public final boolean useLavaLakes;
    public final int lavaLakeChance;
    public final boolean useLavaOceans;
    public final int fixedBiome;
    public final int biomeSize;
    public final int riverSize;
    public final int dirtSize;
    public final int dirtCount;
    public final int dirtMinHeight;
    public final int dirtMaxHeight;
    public final int gravelSize;
    public final int gravelCount;
    public final int gravelMinHeight;
    public final int gravelMaxHeight;
    public final int graniteSize;
    public final int graniteCount;
    public final int graniteMinHeight;
    public final int graniteMaxHeight;
    public final int dioriteSize;
    public final int dioriteCount;
    public final int dioriteMinHeight;
    public final int dioriteMaxHeight;
    public final int andesiteSize;
    public final int andesiteCount;
    public final int andesiteMinHeight;
    public final int andesiteMaxHeight;
    public final int coalSize;
    public final int coalCount;
    public final int coalMinHeight;
    public final int coalMaxHeight;
    public final int ironSize;
    public final int ironCount;
    public final int ironMinHeight;
    public final int ironMaxHeight;
    public final int goldSize;
    public final int goldCount;
    public final int goldMinHeight;
    public final int goldMaxHeight;
    public final int redstoneSize;
    public final int redstoneCount;
    public final int redstoneMinHeight;
    public final int redstoneMaxHeight;
    public final int diamondSize;
    public final int diamondCount;
    public final int diamondMinHeight;
    public final int diamondMaxHeight;
    public final int lapisSize;
    public final int lapisCount;
    public final int lapisMinHeight;
    public final int lapisMaxHeight;

    private OverworldGeneratorOptions(OverworldGeneratorOptions.Builder builder) {
        this.coordinateScale = builder.coordinateScale;
        this.heightScale = builder.heightScale;
        this.upperLimitScale = builder.upperLimitScale;
        this.lowerLimitScale = builder.lowerLimitScale;
        this.depthNoiseScaleX = builder.depthNoisescaleX;
        this.depthNoiseScaleZ = builder.depthNoiseScaleZ;
        this.depthNoiseScaleExponent = builder.depthNoiseScaleExponent;
        this.mainNoiseScaleX = builder.mainNoiseScaleX;
        this.mainNoiseScaleY = builder.mainNoiseScaleY;
        this.mainNoiseScaleZ = builder.mainNoiseScaleZ;
        this.baseSize = builder.baseSize;
        this.stretchY = builder.stretchY;
        this.biomeDepthWeight = builder.biomeDepthWeight;
        this.biomeDepthOffset = builder.biomeDepthOffset;
        this.biomeScaleWeight = builder.biomeScaleWeight;
        this.biomeScaleOffset = builder.biomeScaleOffset;
        this.seaLevel = builder.seaLevel;
        this.useCaves = builder.useCaves;
        this.useDungeons = builder.useDungeons;
        this.dungeonChance = builder.dungeonChance;
        this.useStrongholds = builder.useStrongholds;
        this.useVillages = builder.useVillages;
        this.useMineshafts = builder.useMineshafts;
        this.useTemples = builder.useTemples;
        this.useMonuments = builder.useMonuments;
        this.useRavines = builder.useRavines;
        this.useWaterLakes = builder.useWaterLakes;
        this.waterLakeChance = builder.waterLakeChance;
        this.useLavaLakes = builder.useLavaLakes;
        this.lavaLakeChance = builder.lavaLakeChance;
        this.useLavaOceans = builder.useLavaOceans;
        this.fixedBiome = builder.fixedBiome;
        this.biomeSize = builder.biomeSize;
        this.riverSize = builder.riverSize;
        this.dirtSize = builder.dirtSize;
        this.dirtCount = builder.dirtCount;
        this.dirtMinHeight = builder.dirtMinHeight;
        this.dirtMaxHeight = builder.dirtMaxHeight;
        this.gravelSize = builder.gravelSize;
        this.gravelCount = builder.gravelCount;
        this.gravelMinHeight = builder.gravelMinHeight;
        this.gravelMaxHeight = builder.gravelMaxHeight;
        this.graniteSize = builder.graniteSize;
        this.graniteCount = builder.graniteCount;
        this.graniteMinHeight = builder.graniteMinHeight;
        this.graniteMaxHeight = builder.graniteMaxHeight;
        this.dioriteSize = builder.dioriteSize;
        this.dioriteCount = builder.dioriteCount;
        this.dioriteMinHeight = builder.dioriteMinHeight;
        this.dioriteMaxHeight = builder.dioriteMaxHeight;
        this.andesiteSize = builder.andesiteSize;
        this.andesiteCount = builder.andesiteCount;
        this.andesiteMinHeight = builder.andesiteMinHeight;
        this.andesiteMaxHeight = builder.andesiteMaxHeight;
        this.coalSize = builder.coalSize;
        this.coalCount = builder.coalCount;
        this.coalMinHeight = builder.coalMinHeight;
        this.coalMaxHeight = builder.coalMaxHeight;
        this.ironSize = builder.ironSize;
        this.ironCount = builder.ironCount;
        this.ironMinHeight = builder.ironMinHeight;
        this.ironMaxHeight = builder.ironMaxHeight;
        this.goldSize = builder.goldSize;
        this.goldCount = builder.goldCount;
        this.goldMinHeight = builder.goldMinHeight;
        this.goldMaxHeight = builder.goldMaxHeight;
        this.redstoneSize = builder.redstoneSize;
        this.redstoneCount = builder.redstoneCount;
        this.redstoneMinHeight = builder.redstoneMinHeight;
        this.redstoneMaxHeight = builder.redstoneMaxHeight;
        this.diamondSize = builder.diamondSize;
        this.diamondCount = builder.diamondCount;
        this.diamondMinHeight = builder.diamondMinHeight;
        this.diamondMaxHeight = builder.diamondMaxHeight;
        this.lapisSize = builder.lapisSize;
        this.lapisCount = builder.lapisCount;
        this.lapisMinHeight = builder.lapisMinHeight;
        this.lapisMaxHeight = builder.lapisMaxHeight;
    }

    public static class Builder {
        static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(OverworldGeneratorOptions.Builder.class, new OverworldGeneratorOptions.Serializer())
            .create();
        public float coordinateScale = 684.412F;
        public float heightScale = 684.412F;
        public float upperLimitScale = 512.0F;
        public float lowerLimitScale = 512.0F;
        public float depthNoisescaleX = 200.0F;
        public float depthNoiseScaleZ = 200.0F;
        public float depthNoiseScaleExponent = 0.5F;
        public float mainNoiseScaleX = 80.0F;
        public float mainNoiseScaleY = 160.0F;
        public float mainNoiseScaleZ = 80.0F;
        public float baseSize = 8.5F;
        public float stretchY = 12.0F;
        public float biomeDepthWeight = 1.0F;
        public float biomeDepthOffset = 0.0F;
        public float biomeScaleWeight = 1.0F;
        public float biomeScaleOffset = 0.0F;
        public int seaLevel = 63;
        public boolean useCaves = true;
        public boolean useDungeons = true;
        public int dungeonChance = 8;
        public boolean useStrongholds = true;
        public boolean useVillages = true;
        public boolean useMineshafts = true;
        public boolean useTemples = true;
        public boolean useMonuments = true;
        public boolean useRavines = true;
        public boolean useWaterLakes = true;
        public int waterLakeChance = 4;
        public boolean useLavaLakes = true;
        public int lavaLakeChance = 80;
        public boolean useLavaOceans = false;
        public int fixedBiome = -1;
        public int biomeSize = 4;
        public int riverSize = 4;
        public int dirtSize = 33;
        public int dirtCount = 10;
        public int dirtMinHeight = 0;
        public int dirtMaxHeight = 256;
        public int gravelSize = 33;
        public int gravelCount = 8;
        public int gravelMinHeight = 0;
        public int gravelMaxHeight = 256;
        public int graniteSize = 33;
        public int graniteCount = 10;
        public int graniteMinHeight = 0;
        public int graniteMaxHeight = 80;
        public int dioriteSize = 33;
        public int dioriteCount = 10;
        public int dioriteMinHeight = 0;
        public int dioriteMaxHeight = 80;
        public int andesiteSize = 33;
        public int andesiteCount = 10;
        public int andesiteMinHeight = 0;
        public int andesiteMaxHeight = 80;
        public int coalSize = 17;
        public int coalCount = 20;
        public int coalMinHeight = 0;
        public int coalMaxHeight = 128;
        public int ironSize = 9;
        public int ironCount = 20;
        public int ironMinHeight = 0;
        public int ironMaxHeight = 64;
        public int goldSize = 9;
        public int goldCount = 2;
        public int goldMinHeight = 0;
        public int goldMaxHeight = 32;
        public int redstoneSize = 8;
        public int redstoneCount = 8;
        public int redstoneMinHeight = 0;
        public int redstoneMaxHeight = 16;
        public int diamondSize = 8;
        public int diamondCount = 1;
        public int diamondMinHeight = 0;
        public int diamondMaxHeight = 16;
        public int lapisSize = 7;
        public int lapisCount = 1;
        public int lapisMinHeight = 16;
        public int lapisMaxHeight = 16;

        public static OverworldGeneratorOptions.Builder fromJson(String json) {
            if (json.length() == 0) {
                return new OverworldGeneratorOptions.Builder();
            }

            try {
                return GSON.fromJson(json, OverworldGeneratorOptions.Builder.class);
            } catch (Exception exception) {
                return new OverworldGeneratorOptions.Builder();
            }
        }

        @Override
        public String toString() {
            return GSON.toJson(this);
        }

        public Builder() {
            this.reset();
        }

        public void reset() {
            this.coordinateScale = 684.412F;
            this.heightScale = 684.412F;
            this.upperLimitScale = 512.0F;
            this.lowerLimitScale = 512.0F;
            this.depthNoisescaleX = 200.0F;
            this.depthNoiseScaleZ = 200.0F;
            this.depthNoiseScaleExponent = 0.5F;
            this.mainNoiseScaleX = 80.0F;
            this.mainNoiseScaleY = 160.0F;
            this.mainNoiseScaleZ = 80.0F;
            this.baseSize = 8.5F;
            this.stretchY = 12.0F;
            this.biomeDepthWeight = 1.0F;
            this.biomeDepthOffset = 0.0F;
            this.biomeScaleWeight = 1.0F;
            this.biomeScaleOffset = 0.0F;
            this.seaLevel = 63;
            this.useCaves = true;
            this.useDungeons = true;
            this.dungeonChance = 8;
            this.useStrongholds = true;
            this.useVillages = true;
            this.useMineshafts = true;
            this.useTemples = true;
            this.useMonuments = true;
            this.useRavines = true;
            this.useWaterLakes = true;
            this.waterLakeChance = 4;
            this.useLavaLakes = true;
            this.lavaLakeChance = 80;
            this.useLavaOceans = false;
            this.fixedBiome = -1;
            this.biomeSize = 4;
            this.riverSize = 4;
            this.dirtSize = 33;
            this.dirtCount = 10;
            this.dirtMinHeight = 0;
            this.dirtMaxHeight = 256;
            this.gravelSize = 33;
            this.gravelCount = 8;
            this.gravelMinHeight = 0;
            this.gravelMaxHeight = 256;
            this.graniteSize = 33;
            this.graniteCount = 10;
            this.graniteMinHeight = 0;
            this.graniteMaxHeight = 80;
            this.dioriteSize = 33;
            this.dioriteCount = 10;
            this.dioriteMinHeight = 0;
            this.dioriteMaxHeight = 80;
            this.andesiteSize = 33;
            this.andesiteCount = 10;
            this.andesiteMinHeight = 0;
            this.andesiteMaxHeight = 80;
            this.coalSize = 17;
            this.coalCount = 20;
            this.coalMinHeight = 0;
            this.coalMaxHeight = 128;
            this.ironSize = 9;
            this.ironCount = 20;
            this.ironMinHeight = 0;
            this.ironMaxHeight = 64;
            this.goldSize = 9;
            this.goldCount = 2;
            this.goldMinHeight = 0;
            this.goldMaxHeight = 32;
            this.redstoneSize = 8;
            this.redstoneCount = 8;
            this.redstoneMinHeight = 0;
            this.redstoneMaxHeight = 16;
            this.diamondSize = 8;
            this.diamondCount = 1;
            this.diamondMinHeight = 0;
            this.diamondMaxHeight = 16;
            this.lapisSize = 7;
            this.lapisCount = 1;
            this.lapisMinHeight = 16;
            this.lapisMaxHeight = 16;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            } else if (object != null && this.getClass() == object.getClass()) {
                OverworldGeneratorOptions.Builder overworldgeneratoroptions$builder = (OverworldGeneratorOptions.Builder)object;
                return this.andesiteCount == overworldgeneratoroptions$builder.andesiteCount
                    && this.andesiteMaxHeight == overworldgeneratoroptions$builder.andesiteMaxHeight
                    && this.andesiteMinHeight == overworldgeneratoroptions$builder.andesiteMinHeight
                    && this.andesiteSize == overworldgeneratoroptions$builder.andesiteSize
                    && Float.compare(overworldgeneratoroptions$builder.baseSize, this.baseSize) == 0
                    && Float.compare(overworldgeneratoroptions$builder.biomeDepthOffset, this.biomeDepthOffset) == 0
                    && Float.compare(overworldgeneratoroptions$builder.biomeDepthWeight, this.biomeDepthWeight) == 0
                    && Float.compare(overworldgeneratoroptions$builder.biomeScaleOffset, this.biomeScaleOffset) == 0
                    && Float.compare(overworldgeneratoroptions$builder.biomeScaleWeight, this.biomeScaleWeight) == 0
                    && this.biomeSize == overworldgeneratoroptions$builder.biomeSize
                    && this.coalCount == overworldgeneratoroptions$builder.coalCount
                    && this.coalMaxHeight == overworldgeneratoroptions$builder.coalMaxHeight
                    && this.coalMinHeight == overworldgeneratoroptions$builder.coalMinHeight
                    && this.coalSize == overworldgeneratoroptions$builder.coalSize
                    && Float.compare(overworldgeneratoroptions$builder.coordinateScale, this.coordinateScale) == 0
                    && Float.compare(overworldgeneratoroptions$builder.depthNoiseScaleExponent, this.depthNoiseScaleExponent) == 0
                    && Float.compare(overworldgeneratoroptions$builder.depthNoisescaleX, this.depthNoisescaleX) == 0
                    && Float.compare(overworldgeneratoroptions$builder.depthNoiseScaleZ, this.depthNoiseScaleZ) == 0
                    && this.diamondCount == overworldgeneratoroptions$builder.diamondCount
                    && this.diamondMaxHeight == overworldgeneratoroptions$builder.diamondMaxHeight
                    && this.diamondMinHeight == overworldgeneratoroptions$builder.diamondMinHeight
                    && this.diamondSize == overworldgeneratoroptions$builder.diamondSize
                    && this.dioriteCount == overworldgeneratoroptions$builder.dioriteCount
                    && this.dioriteMaxHeight == overworldgeneratoroptions$builder.dioriteMaxHeight
                    && this.dioriteMinHeight == overworldgeneratoroptions$builder.dioriteMinHeight
                    && this.dioriteSize == overworldgeneratoroptions$builder.dioriteSize
                    && this.dirtCount == overworldgeneratoroptions$builder.dirtCount
                    && this.dirtMaxHeight == overworldgeneratoroptions$builder.dirtMaxHeight
                    && this.dirtMinHeight == overworldgeneratoroptions$builder.dirtMinHeight
                    && this.dirtSize == overworldgeneratoroptions$builder.dirtSize
                    && this.dungeonChance == overworldgeneratoroptions$builder.dungeonChance
                    && this.fixedBiome == overworldgeneratoroptions$builder.fixedBiome
                    && this.goldCount == overworldgeneratoroptions$builder.goldCount
                    && this.goldMaxHeight == overworldgeneratoroptions$builder.goldMaxHeight
                    && this.goldMinHeight == overworldgeneratoroptions$builder.goldMinHeight
                    && this.goldSize == overworldgeneratoroptions$builder.goldSize
                    && this.graniteCount == overworldgeneratoroptions$builder.graniteCount
                    && this.graniteMaxHeight == overworldgeneratoroptions$builder.graniteMaxHeight
                    && this.graniteMinHeight == overworldgeneratoroptions$builder.graniteMinHeight
                    && this.graniteSize == overworldgeneratoroptions$builder.graniteSize
                    && this.gravelCount == overworldgeneratoroptions$builder.gravelCount
                    && this.gravelMaxHeight == overworldgeneratoroptions$builder.gravelMaxHeight
                    && this.gravelMinHeight == overworldgeneratoroptions$builder.gravelMinHeight
                    && this.gravelSize == overworldgeneratoroptions$builder.gravelSize
                    && Float.compare(overworldgeneratoroptions$builder.heightScale, this.heightScale) == 0
                    && this.ironCount == overworldgeneratoroptions$builder.ironCount
                    && this.ironMaxHeight == overworldgeneratoroptions$builder.ironMaxHeight
                    && this.ironMinHeight == overworldgeneratoroptions$builder.ironMinHeight
                    && this.ironSize == overworldgeneratoroptions$builder.ironSize
                    && this.lapisMinHeight == overworldgeneratoroptions$builder.lapisMinHeight
                    && this.lapisCount == overworldgeneratoroptions$builder.lapisCount
                    && this.lapisSize == overworldgeneratoroptions$builder.lapisSize
                    && this.lapisMaxHeight == overworldgeneratoroptions$builder.lapisMaxHeight
                    && this.lavaLakeChance == overworldgeneratoroptions$builder.lavaLakeChance
                    && Float.compare(overworldgeneratoroptions$builder.lowerLimitScale, this.lowerLimitScale) == 0
                    && Float.compare(overworldgeneratoroptions$builder.mainNoiseScaleX, this.mainNoiseScaleX) == 0
                    && Float.compare(overworldgeneratoroptions$builder.mainNoiseScaleY, this.mainNoiseScaleY) == 0
                    && Float.compare(overworldgeneratoroptions$builder.mainNoiseScaleZ, this.mainNoiseScaleZ) == 0
                    && this.redstoneCount == overworldgeneratoroptions$builder.redstoneCount
                    && this.redstoneMaxHeight == overworldgeneratoroptions$builder.redstoneMaxHeight
                    && this.redstoneMinHeight == overworldgeneratoroptions$builder.redstoneMinHeight
                    && this.redstoneSize == overworldgeneratoroptions$builder.redstoneSize
                    && this.riverSize == overworldgeneratoroptions$builder.riverSize
                    && this.seaLevel == overworldgeneratoroptions$builder.seaLevel
                    && Float.compare(overworldgeneratoroptions$builder.stretchY, this.stretchY) == 0
                    && Float.compare(overworldgeneratoroptions$builder.upperLimitScale, this.upperLimitScale) == 0
                    && this.useCaves == overworldgeneratoroptions$builder.useCaves
                    && this.useDungeons == overworldgeneratoroptions$builder.useDungeons
                    && this.useLavaLakes == overworldgeneratoroptions$builder.useLavaLakes
                    && this.useLavaOceans == overworldgeneratoroptions$builder.useLavaOceans
                    && this.useMineshafts == overworldgeneratoroptions$builder.useMineshafts
                    && this.useRavines == overworldgeneratoroptions$builder.useRavines
                    && this.useStrongholds == overworldgeneratoroptions$builder.useStrongholds
                    && this.useTemples == overworldgeneratoroptions$builder.useTemples
                    && this.useMonuments == overworldgeneratoroptions$builder.useMonuments
                    && this.useVillages == overworldgeneratoroptions$builder.useVillages
                    && this.useWaterLakes == overworldgeneratoroptions$builder.useWaterLakes
                    && this.waterLakeChance == overworldgeneratoroptions$builder.waterLakeChance;
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            int i = this.coordinateScale != 0.0F ? Float.floatToIntBits(this.coordinateScale) : 0;
            i = 31 * i + (this.heightScale != 0.0F ? Float.floatToIntBits(this.heightScale) : 0);
            i = 31 * i + (this.upperLimitScale != 0.0F ? Float.floatToIntBits(this.upperLimitScale) : 0);
            i = 31 * i + (this.lowerLimitScale != 0.0F ? Float.floatToIntBits(this.lowerLimitScale) : 0);
            i = 31 * i + (this.depthNoisescaleX != 0.0F ? Float.floatToIntBits(this.depthNoisescaleX) : 0);
            i = 31 * i + (this.depthNoiseScaleZ != 0.0F ? Float.floatToIntBits(this.depthNoiseScaleZ) : 0);
            i = 31 * i + (this.depthNoiseScaleExponent != 0.0F ? Float.floatToIntBits(this.depthNoiseScaleExponent) : 0);
            i = 31 * i + (this.mainNoiseScaleX != 0.0F ? Float.floatToIntBits(this.mainNoiseScaleX) : 0);
            i = 31 * i + (this.mainNoiseScaleY != 0.0F ? Float.floatToIntBits(this.mainNoiseScaleY) : 0);
            i = 31 * i + (this.mainNoiseScaleZ != 0.0F ? Float.floatToIntBits(this.mainNoiseScaleZ) : 0);
            i = 31 * i + (this.baseSize != 0.0F ? Float.floatToIntBits(this.baseSize) : 0);
            i = 31 * i + (this.stretchY != 0.0F ? Float.floatToIntBits(this.stretchY) : 0);
            i = 31 * i + (this.biomeDepthWeight != 0.0F ? Float.floatToIntBits(this.biomeDepthWeight) : 0);
            i = 31 * i + (this.biomeDepthOffset != 0.0F ? Float.floatToIntBits(this.biomeDepthOffset) : 0);
            i = 31 * i + (this.biomeScaleWeight != 0.0F ? Float.floatToIntBits(this.biomeScaleWeight) : 0);
            i = 31 * i + (this.biomeScaleOffset != 0.0F ? Float.floatToIntBits(this.biomeScaleOffset) : 0);
            i = 31 * i + this.seaLevel;
            i = 31 * i + (this.useCaves ? 1 : 0);
            i = 31 * i + (this.useDungeons ? 1 : 0);
            i = 31 * i + this.dungeonChance;
            i = 31 * i + (this.useStrongholds ? 1 : 0);
            i = 31 * i + (this.useVillages ? 1 : 0);
            i = 31 * i + (this.useMineshafts ? 1 : 0);
            i = 31 * i + (this.useTemples ? 1 : 0);
            i = 31 * i + (this.useMonuments ? 1 : 0);
            i = 31 * i + (this.useRavines ? 1 : 0);
            i = 31 * i + (this.useWaterLakes ? 1 : 0);
            i = 31 * i + this.waterLakeChance;
            i = 31 * i + (this.useLavaLakes ? 1 : 0);
            i = 31 * i + this.lavaLakeChance;
            i = 31 * i + (this.useLavaOceans ? 1 : 0);
            i = 31 * i + this.fixedBiome;
            i = 31 * i + this.biomeSize;
            i = 31 * i + this.riverSize;
            i = 31 * i + this.dirtSize;
            i = 31 * i + this.dirtCount;
            i = 31 * i + this.dirtMinHeight;
            i = 31 * i + this.dirtMaxHeight;
            i = 31 * i + this.gravelSize;
            i = 31 * i + this.gravelCount;
            i = 31 * i + this.gravelMinHeight;
            i = 31 * i + this.gravelMaxHeight;
            i = 31 * i + this.graniteSize;
            i = 31 * i + this.graniteCount;
            i = 31 * i + this.graniteMinHeight;
            i = 31 * i + this.graniteMaxHeight;
            i = 31 * i + this.dioriteSize;
            i = 31 * i + this.dioriteCount;
            i = 31 * i + this.dioriteMinHeight;
            i = 31 * i + this.dioriteMaxHeight;
            i = 31 * i + this.andesiteSize;
            i = 31 * i + this.andesiteCount;
            i = 31 * i + this.andesiteMinHeight;
            i = 31 * i + this.andesiteMaxHeight;
            i = 31 * i + this.coalSize;
            i = 31 * i + this.coalCount;
            i = 31 * i + this.coalMinHeight;
            i = 31 * i + this.coalMaxHeight;
            i = 31 * i + this.ironSize;
            i = 31 * i + this.ironCount;
            i = 31 * i + this.ironMinHeight;
            i = 31 * i + this.ironMaxHeight;
            i = 31 * i + this.goldSize;
            i = 31 * i + this.goldCount;
            i = 31 * i + this.goldMinHeight;
            i = 31 * i + this.goldMaxHeight;
            i = 31 * i + this.redstoneSize;
            i = 31 * i + this.redstoneCount;
            i = 31 * i + this.redstoneMinHeight;
            i = 31 * i + this.redstoneMaxHeight;
            i = 31 * i + this.diamondSize;
            i = 31 * i + this.diamondCount;
            i = 31 * i + this.diamondMinHeight;
            i = 31 * i + this.diamondMaxHeight;
            i = 31 * i + this.lapisSize;
            i = 31 * i + this.lapisCount;
            i = 31 * i + this.lapisMinHeight;
            return 31 * i + this.lapisMaxHeight;
        }

        public OverworldGeneratorOptions build() {
            return new OverworldGeneratorOptions(this);
        }
    }

    public static class Serializer implements JsonDeserializer<OverworldGeneratorOptions.Builder>, JsonSerializer<OverworldGeneratorOptions.Builder> {
        public OverworldGeneratorOptions.Builder deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
            JsonObject jsonobject = jsonElement.getAsJsonObject();
            OverworldGeneratorOptions.Builder overworldgeneratoroptions$builder = new OverworldGeneratorOptions.Builder();

            try {
                overworldgeneratoroptions$builder.coordinateScale = JsonUtils.getFloatOrDefault(
                    jsonobject, "coordinateScale", overworldgeneratoroptions$builder.coordinateScale
                );
                overworldgeneratoroptions$builder.heightScale = JsonUtils.getFloatOrDefault(
                    jsonobject, "heightScale", overworldgeneratoroptions$builder.heightScale
                );
                overworldgeneratoroptions$builder.lowerLimitScale = JsonUtils.getFloatOrDefault(
                    jsonobject, "lowerLimitScale", overworldgeneratoroptions$builder.lowerLimitScale
                );
                overworldgeneratoroptions$builder.upperLimitScale = JsonUtils.getFloatOrDefault(
                    jsonobject, "upperLimitScale", overworldgeneratoroptions$builder.upperLimitScale
                );
                overworldgeneratoroptions$builder.depthNoisescaleX = JsonUtils.getFloatOrDefault(
                    jsonobject, "depthNoiseScaleX", overworldgeneratoroptions$builder.depthNoisescaleX
                );
                overworldgeneratoroptions$builder.depthNoiseScaleZ = JsonUtils.getFloatOrDefault(
                    jsonobject, "depthNoiseScaleZ", overworldgeneratoroptions$builder.depthNoiseScaleZ
                );
                overworldgeneratoroptions$builder.depthNoiseScaleExponent = JsonUtils.getFloatOrDefault(
                    jsonobject, "depthNoiseScaleExponent", overworldgeneratoroptions$builder.depthNoiseScaleExponent
                );
                overworldgeneratoroptions$builder.mainNoiseScaleX = JsonUtils.getFloatOrDefault(
                    jsonobject, "mainNoiseScaleX", overworldgeneratoroptions$builder.mainNoiseScaleX
                );
                overworldgeneratoroptions$builder.mainNoiseScaleY = JsonUtils.getFloatOrDefault(
                    jsonobject, "mainNoiseScaleY", overworldgeneratoroptions$builder.mainNoiseScaleY
                );
                overworldgeneratoroptions$builder.mainNoiseScaleZ = JsonUtils.getFloatOrDefault(
                    jsonobject, "mainNoiseScaleZ", overworldgeneratoroptions$builder.mainNoiseScaleZ
                );
                overworldgeneratoroptions$builder.baseSize = JsonUtils.getFloatOrDefault(jsonobject, "baseSize", overworldgeneratoroptions$builder.baseSize);
                overworldgeneratoroptions$builder.stretchY = JsonUtils.getFloatOrDefault(jsonobject, "stretchY", overworldgeneratoroptions$builder.stretchY);
                overworldgeneratoroptions$builder.biomeDepthWeight = JsonUtils.getFloatOrDefault(
                    jsonobject, "biomeDepthWeight", overworldgeneratoroptions$builder.biomeDepthWeight
                );
                overworldgeneratoroptions$builder.biomeDepthOffset = JsonUtils.getFloatOrDefault(
                    jsonobject, "biomeDepthOffset", overworldgeneratoroptions$builder.biomeDepthOffset
                );
                overworldgeneratoroptions$builder.biomeScaleWeight = JsonUtils.getFloatOrDefault(
                    jsonobject, "biomeScaleWeight", overworldgeneratoroptions$builder.biomeScaleWeight
                );
                overworldgeneratoroptions$builder.biomeScaleOffset = JsonUtils.getFloatOrDefault(
                    jsonobject, "biomeScaleOffset", overworldgeneratoroptions$builder.biomeScaleOffset
                );
                overworldgeneratoroptions$builder.seaLevel = JsonUtils.getIntegerOrDefault(jsonobject, "seaLevel", overworldgeneratoroptions$builder.seaLevel);
                overworldgeneratoroptions$builder.useCaves = JsonUtils.getBooleanOrDefault(jsonobject, "useCaves", overworldgeneratoroptions$builder.useCaves);
                overworldgeneratoroptions$builder.useDungeons = JsonUtils.getBooleanOrDefault(
                    jsonobject, "useDungeons", overworldgeneratoroptions$builder.useDungeons
                );
                overworldgeneratoroptions$builder.dungeonChance = JsonUtils.getIntegerOrDefault(
                    jsonobject, "dungeonChance", overworldgeneratoroptions$builder.dungeonChance
                );
                overworldgeneratoroptions$builder.useStrongholds = JsonUtils.getBooleanOrDefault(
                    jsonobject, "useStrongholds", overworldgeneratoroptions$builder.useStrongholds
                );
                overworldgeneratoroptions$builder.useVillages = JsonUtils.getBooleanOrDefault(
                    jsonobject, "useVillages", overworldgeneratoroptions$builder.useVillages
                );
                overworldgeneratoroptions$builder.useMineshafts = JsonUtils.getBooleanOrDefault(
                    jsonobject, "useMineShafts", overworldgeneratoroptions$builder.useMineshafts
                );
                overworldgeneratoroptions$builder.useTemples = JsonUtils.getBooleanOrDefault(
                    jsonobject, "useTemples", overworldgeneratoroptions$builder.useTemples
                );
                overworldgeneratoroptions$builder.useMonuments = JsonUtils.getBooleanOrDefault(
                    jsonobject, "useMonuments", overworldgeneratoroptions$builder.useMonuments
                );
                overworldgeneratoroptions$builder.useRavines = JsonUtils.getBooleanOrDefault(
                    jsonobject, "useRavines", overworldgeneratoroptions$builder.useRavines
                );
                overworldgeneratoroptions$builder.useWaterLakes = JsonUtils.getBooleanOrDefault(
                    jsonobject, "useWaterLakes", overworldgeneratoroptions$builder.useWaterLakes
                );
                overworldgeneratoroptions$builder.waterLakeChance = JsonUtils.getIntegerOrDefault(
                    jsonobject, "waterLakeChance", overworldgeneratoroptions$builder.waterLakeChance
                );
                overworldgeneratoroptions$builder.useLavaLakes = JsonUtils.getBooleanOrDefault(
                    jsonobject, "useLavaLakes", overworldgeneratoroptions$builder.useLavaLakes
                );
                overworldgeneratoroptions$builder.lavaLakeChance = JsonUtils.getIntegerOrDefault(
                    jsonobject, "lavaLakeChance", overworldgeneratoroptions$builder.lavaLakeChance
                );
                overworldgeneratoroptions$builder.useLavaOceans = JsonUtils.getBooleanOrDefault(
                    jsonobject, "useLavaOceans", overworldgeneratoroptions$builder.useLavaOceans
                );
                overworldgeneratoroptions$builder.fixedBiome = JsonUtils.getIntegerOrDefault(
                    jsonobject, "fixedBiome", overworldgeneratoroptions$builder.fixedBiome
                );
                if (overworldgeneratoroptions$builder.fixedBiome >= 38 || overworldgeneratoroptions$builder.fixedBiome < -1) {
                    overworldgeneratoroptions$builder.fixedBiome = -1;
                } else if (overworldgeneratoroptions$builder.fixedBiome >= Biome.HELL.id) {
                    overworldgeneratoroptions$builder.fixedBiome += 2;
                }

                overworldgeneratoroptions$builder.biomeSize = JsonUtils.getIntegerOrDefault(
                    jsonobject, "biomeSize", overworldgeneratoroptions$builder.biomeSize
                );
                overworldgeneratoroptions$builder.riverSize = JsonUtils.getIntegerOrDefault(
                    jsonobject, "riverSize", overworldgeneratoroptions$builder.riverSize
                );
                overworldgeneratoroptions$builder.dirtSize = JsonUtils.getIntegerOrDefault(jsonobject, "dirtSize", overworldgeneratoroptions$builder.dirtSize);
                overworldgeneratoroptions$builder.dirtCount = JsonUtils.getIntegerOrDefault(
                    jsonobject, "dirtCount", overworldgeneratoroptions$builder.dirtCount
                );
                overworldgeneratoroptions$builder.dirtMinHeight = JsonUtils.getIntegerOrDefault(
                    jsonobject, "dirtMinHeight", overworldgeneratoroptions$builder.dirtMinHeight
                );
                overworldgeneratoroptions$builder.dirtMaxHeight = JsonUtils.getIntegerOrDefault(
                    jsonobject, "dirtMaxHeight", overworldgeneratoroptions$builder.dirtMaxHeight
                );
                overworldgeneratoroptions$builder.gravelSize = JsonUtils.getIntegerOrDefault(
                    jsonobject, "gravelSize", overworldgeneratoroptions$builder.gravelSize
                );
                overworldgeneratoroptions$builder.gravelCount = JsonUtils.getIntegerOrDefault(
                    jsonobject, "gravelCount", overworldgeneratoroptions$builder.gravelCount
                );
                overworldgeneratoroptions$builder.gravelMinHeight = JsonUtils.getIntegerOrDefault(
                    jsonobject, "gravelMinHeight", overworldgeneratoroptions$builder.gravelMinHeight
                );
                overworldgeneratoroptions$builder.gravelMaxHeight = JsonUtils.getIntegerOrDefault(
                    jsonobject, "gravelMaxHeight", overworldgeneratoroptions$builder.gravelMaxHeight
                );
                overworldgeneratoroptions$builder.graniteSize = JsonUtils.getIntegerOrDefault(
                    jsonobject, "graniteSize", overworldgeneratoroptions$builder.graniteSize
                );
                overworldgeneratoroptions$builder.graniteCount = JsonUtils.getIntegerOrDefault(
                    jsonobject, "graniteCount", overworldgeneratoroptions$builder.graniteCount
                );
                overworldgeneratoroptions$builder.graniteMinHeight = JsonUtils.getIntegerOrDefault(
                    jsonobject, "graniteMinHeight", overworldgeneratoroptions$builder.graniteMinHeight
                );
                overworldgeneratoroptions$builder.graniteMaxHeight = JsonUtils.getIntegerOrDefault(
                    jsonobject, "graniteMaxHeight", overworldgeneratoroptions$builder.graniteMaxHeight
                );
                overworldgeneratoroptions$builder.dioriteSize = JsonUtils.getIntegerOrDefault(
                    jsonobject, "dioriteSize", overworldgeneratoroptions$builder.dioriteSize
                );
                overworldgeneratoroptions$builder.dioriteCount = JsonUtils.getIntegerOrDefault(
                    jsonobject, "dioriteCount", overworldgeneratoroptions$builder.dioriteCount
                );
                overworldgeneratoroptions$builder.dioriteMinHeight = JsonUtils.getIntegerOrDefault(
                    jsonobject, "dioriteMinHeight", overworldgeneratoroptions$builder.dioriteMinHeight
                );
                overworldgeneratoroptions$builder.dioriteMaxHeight = JsonUtils.getIntegerOrDefault(
                    jsonobject, "dioriteMaxHeight", overworldgeneratoroptions$builder.dioriteMaxHeight
                );
                overworldgeneratoroptions$builder.andesiteSize = JsonUtils.getIntegerOrDefault(
                    jsonobject, "andesiteSize", overworldgeneratoroptions$builder.andesiteSize
                );
                overworldgeneratoroptions$builder.andesiteCount = JsonUtils.getIntegerOrDefault(
                    jsonobject, "andesiteCount", overworldgeneratoroptions$builder.andesiteCount
                );
                overworldgeneratoroptions$builder.andesiteMinHeight = JsonUtils.getIntegerOrDefault(
                    jsonobject, "andesiteMinHeight", overworldgeneratoroptions$builder.andesiteMinHeight
                );
                overworldgeneratoroptions$builder.andesiteMaxHeight = JsonUtils.getIntegerOrDefault(
                    jsonobject, "andesiteMaxHeight", overworldgeneratoroptions$builder.andesiteMaxHeight
                );
                overworldgeneratoroptions$builder.coalSize = JsonUtils.getIntegerOrDefault(jsonobject, "coalSize", overworldgeneratoroptions$builder.coalSize);
                overworldgeneratoroptions$builder.coalCount = JsonUtils.getIntegerOrDefault(
                    jsonobject, "coalCount", overworldgeneratoroptions$builder.coalCount
                );
                overworldgeneratoroptions$builder.coalMinHeight = JsonUtils.getIntegerOrDefault(
                    jsonobject, "coalMinHeight", overworldgeneratoroptions$builder.coalMinHeight
                );
                overworldgeneratoroptions$builder.coalMaxHeight = JsonUtils.getIntegerOrDefault(
                    jsonobject, "coalMaxHeight", overworldgeneratoroptions$builder.coalMaxHeight
                );
                overworldgeneratoroptions$builder.ironSize = JsonUtils.getIntegerOrDefault(jsonobject, "ironSize", overworldgeneratoroptions$builder.ironSize);
                overworldgeneratoroptions$builder.ironCount = JsonUtils.getIntegerOrDefault(
                    jsonobject, "ironCount", overworldgeneratoroptions$builder.ironCount
                );
                overworldgeneratoroptions$builder.ironMinHeight = JsonUtils.getIntegerOrDefault(
                    jsonobject, "ironMinHeight", overworldgeneratoroptions$builder.ironMinHeight
                );
                overworldgeneratoroptions$builder.ironMaxHeight = JsonUtils.getIntegerOrDefault(
                    jsonobject, "ironMaxHeight", overworldgeneratoroptions$builder.ironMaxHeight
                );
                overworldgeneratoroptions$builder.goldSize = JsonUtils.getIntegerOrDefault(jsonobject, "goldSize", overworldgeneratoroptions$builder.goldSize);
                overworldgeneratoroptions$builder.goldCount = JsonUtils.getIntegerOrDefault(
                    jsonobject, "goldCount", overworldgeneratoroptions$builder.goldCount
                );
                overworldgeneratoroptions$builder.goldMinHeight = JsonUtils.getIntegerOrDefault(
                    jsonobject, "goldMinHeight", overworldgeneratoroptions$builder.goldMinHeight
                );
                overworldgeneratoroptions$builder.goldMaxHeight = JsonUtils.getIntegerOrDefault(
                    jsonobject, "goldMaxHeight", overworldgeneratoroptions$builder.goldMaxHeight
                );
                overworldgeneratoroptions$builder.redstoneSize = JsonUtils.getIntegerOrDefault(
                    jsonobject, "redstoneSize", overworldgeneratoroptions$builder.redstoneSize
                );
                overworldgeneratoroptions$builder.redstoneCount = JsonUtils.getIntegerOrDefault(
                    jsonobject, "redstoneCount", overworldgeneratoroptions$builder.redstoneCount
                );
                overworldgeneratoroptions$builder.redstoneMinHeight = JsonUtils.getIntegerOrDefault(
                    jsonobject, "redstoneMinHeight", overworldgeneratoroptions$builder.redstoneMinHeight
                );
                overworldgeneratoroptions$builder.redstoneMaxHeight = JsonUtils.getIntegerOrDefault(
                    jsonobject, "redstoneMaxHeight", overworldgeneratoroptions$builder.redstoneMaxHeight
                );
                overworldgeneratoroptions$builder.diamondSize = JsonUtils.getIntegerOrDefault(
                    jsonobject, "diamondSize", overworldgeneratoroptions$builder.diamondSize
                );
                overworldgeneratoroptions$builder.diamondCount = JsonUtils.getIntegerOrDefault(
                    jsonobject, "diamondCount", overworldgeneratoroptions$builder.diamondCount
                );
                overworldgeneratoroptions$builder.diamondMinHeight = JsonUtils.getIntegerOrDefault(
                    jsonobject, "diamondMinHeight", overworldgeneratoroptions$builder.diamondMinHeight
                );
                overworldgeneratoroptions$builder.diamondMaxHeight = JsonUtils.getIntegerOrDefault(
                    jsonobject, "diamondMaxHeight", overworldgeneratoroptions$builder.diamondMaxHeight
                );
                overworldgeneratoroptions$builder.lapisSize = JsonUtils.getIntegerOrDefault(
                    jsonobject, "lapisSize", overworldgeneratoroptions$builder.lapisSize
                );
                overworldgeneratoroptions$builder.lapisCount = JsonUtils.getIntegerOrDefault(
                    jsonobject, "lapisCount", overworldgeneratoroptions$builder.lapisCount
                );
                overworldgeneratoroptions$builder.lapisMinHeight = JsonUtils.getIntegerOrDefault(
                    jsonobject, "lapisCenterHeight", overworldgeneratoroptions$builder.lapisMinHeight
                );
                overworldgeneratoroptions$builder.lapisMaxHeight = JsonUtils.getIntegerOrDefault(
                    jsonobject, "lapisSpread", overworldgeneratoroptions$builder.lapisMaxHeight
                );
            } catch (Exception exception) {
            }

            return overworldgeneratoroptions$builder;
        }

        public JsonElement serialize(OverworldGeneratorOptions.Builder builder, Type type, JsonSerializationContext jsonSerializationContext) {
            JsonObject jsonobject = new JsonObject();
            jsonobject.addProperty("coordinateScale", builder.coordinateScale);
            jsonobject.addProperty("heightScale", builder.heightScale);
            jsonobject.addProperty("lowerLimitScale", builder.lowerLimitScale);
            jsonobject.addProperty("upperLimitScale", builder.upperLimitScale);
            jsonobject.addProperty("depthNoiseScaleX", builder.depthNoisescaleX);
            jsonobject.addProperty("depthNoiseScaleZ", builder.depthNoiseScaleZ);
            jsonobject.addProperty("depthNoiseScaleExponent", builder.depthNoiseScaleExponent);
            jsonobject.addProperty("mainNoiseScaleX", builder.mainNoiseScaleX);
            jsonobject.addProperty("mainNoiseScaleY", builder.mainNoiseScaleY);
            jsonobject.addProperty("mainNoiseScaleZ", builder.mainNoiseScaleZ);
            jsonobject.addProperty("baseSize", builder.baseSize);
            jsonobject.addProperty("stretchY", builder.stretchY);
            jsonobject.addProperty("biomeDepthWeight", builder.biomeDepthWeight);
            jsonobject.addProperty("biomeDepthOffset", builder.biomeDepthOffset);
            jsonobject.addProperty("biomeScaleWeight", builder.biomeScaleWeight);
            jsonobject.addProperty("biomeScaleOffset", builder.biomeScaleOffset);
            jsonobject.addProperty("seaLevel", builder.seaLevel);
            jsonobject.addProperty("useCaves", builder.useCaves);
            jsonobject.addProperty("useDungeons", builder.useDungeons);
            jsonobject.addProperty("dungeonChance", builder.dungeonChance);
            jsonobject.addProperty("useStrongholds", builder.useStrongholds);
            jsonobject.addProperty("useVillages", builder.useVillages);
            jsonobject.addProperty("useMineShafts", builder.useMineshafts);
            jsonobject.addProperty("useTemples", builder.useTemples);
            jsonobject.addProperty("useMonuments", builder.useMonuments);
            jsonobject.addProperty("useRavines", builder.useRavines);
            jsonobject.addProperty("useWaterLakes", builder.useWaterLakes);
            jsonobject.addProperty("waterLakeChance", builder.waterLakeChance);
            jsonobject.addProperty("useLavaLakes", builder.useLavaLakes);
            jsonobject.addProperty("lavaLakeChance", builder.lavaLakeChance);
            jsonobject.addProperty("useLavaOceans", builder.useLavaOceans);
            jsonobject.addProperty("fixedBiome", builder.fixedBiome);
            jsonobject.addProperty("biomeSize", builder.biomeSize);
            jsonobject.addProperty("riverSize", builder.riverSize);
            jsonobject.addProperty("dirtSize", builder.dirtSize);
            jsonobject.addProperty("dirtCount", builder.dirtCount);
            jsonobject.addProperty("dirtMinHeight", builder.dirtMinHeight);
            jsonobject.addProperty("dirtMaxHeight", builder.dirtMaxHeight);
            jsonobject.addProperty("gravelSize", builder.gravelSize);
            jsonobject.addProperty("gravelCount", builder.gravelCount);
            jsonobject.addProperty("gravelMinHeight", builder.gravelMinHeight);
            jsonobject.addProperty("gravelMaxHeight", builder.gravelMaxHeight);
            jsonobject.addProperty("graniteSize", builder.graniteSize);
            jsonobject.addProperty("graniteCount", builder.graniteCount);
            jsonobject.addProperty("graniteMinHeight", builder.graniteMinHeight);
            jsonobject.addProperty("graniteMaxHeight", builder.graniteMaxHeight);
            jsonobject.addProperty("dioriteSize", builder.dioriteSize);
            jsonobject.addProperty("dioriteCount", builder.dioriteCount);
            jsonobject.addProperty("dioriteMinHeight", builder.dioriteMinHeight);
            jsonobject.addProperty("dioriteMaxHeight", builder.dioriteMaxHeight);
            jsonobject.addProperty("andesiteSize", builder.andesiteSize);
            jsonobject.addProperty("andesiteCount", builder.andesiteCount);
            jsonobject.addProperty("andesiteMinHeight", builder.andesiteMinHeight);
            jsonobject.addProperty("andesiteMaxHeight", builder.andesiteMaxHeight);
            jsonobject.addProperty("coalSize", builder.coalSize);
            jsonobject.addProperty("coalCount", builder.coalCount);
            jsonobject.addProperty("coalMinHeight", builder.coalMinHeight);
            jsonobject.addProperty("coalMaxHeight", builder.coalMaxHeight);
            jsonobject.addProperty("ironSize", builder.ironSize);
            jsonobject.addProperty("ironCount", builder.ironCount);
            jsonobject.addProperty("ironMinHeight", builder.ironMinHeight);
            jsonobject.addProperty("ironMaxHeight", builder.ironMaxHeight);
            jsonobject.addProperty("goldSize", builder.goldSize);
            jsonobject.addProperty("goldCount", builder.goldCount);
            jsonobject.addProperty("goldMinHeight", builder.goldMinHeight);
            jsonobject.addProperty("goldMaxHeight", builder.goldMaxHeight);
            jsonobject.addProperty("redstoneSize", builder.redstoneSize);
            jsonobject.addProperty("redstoneCount", builder.redstoneCount);
            jsonobject.addProperty("redstoneMinHeight", builder.redstoneMinHeight);
            jsonobject.addProperty("redstoneMaxHeight", builder.redstoneMaxHeight);
            jsonobject.addProperty("diamondSize", builder.diamondSize);
            jsonobject.addProperty("diamondCount", builder.diamondCount);
            jsonobject.addProperty("diamondMinHeight", builder.diamondMinHeight);
            jsonobject.addProperty("diamondMaxHeight", builder.diamondMaxHeight);
            jsonobject.addProperty("lapisSize", builder.lapisSize);
            jsonobject.addProperty("lapisCount", builder.lapisCount);
            jsonobject.addProperty("lapisCenterHeight", builder.lapisMinHeight);
            jsonobject.addProperty("lapisSpread", builder.lapisMaxHeight);
            return jsonobject;
        }
    }
}
