package net.minecraft.world.biome;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.minecraft.block.Blocks;
import net.minecraft.block.FlowerBlock;
import net.minecraft.block.SandBlock;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.world.color.FoliageColors;
import net.minecraft.client.world.color.GrassColors;
import net.minecraft.entity.living.mob.MobCategory;
import net.minecraft.entity.living.mob.MobEntity;
import net.minecraft.entity.living.mob.ambient.BatEntity;
import net.minecraft.entity.living.mob.monster.CreeperEntity;
import net.minecraft.entity.living.mob.monster.EndermanEntity;
import net.minecraft.entity.living.mob.monster.SkeletonEntity;
import net.minecraft.entity.living.mob.monster.SlimeEntity;
import net.minecraft.entity.living.mob.monster.SpiderEntity;
import net.minecraft.entity.living.mob.monster.WitchEntity;
import net.minecraft.entity.living.mob.monster.ZombieEntity;
import net.minecraft.entity.living.mob.passive.animal.ChickenEntity;
import net.minecraft.entity.living.mob.passive.animal.CowEntity;
import net.minecraft.entity.living.mob.passive.animal.PigEntity;
import net.minecraft.entity.living.mob.passive.animal.RabbitEntity;
import net.minecraft.entity.living.mob.passive.animal.SheepEntity;
import net.minecraft.entity.living.mob.water.SquidEntity;
import net.minecraft.util.WeightedPicker;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.chunk.BlockStateStorage;
import net.minecraft.world.gen.feature.AbstractTreeFeature;
import net.minecraft.world.gen.feature.DoublePlantFeature;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.LargeOakTreeFeature;
import net.minecraft.world.gen.feature.SwampTreeFeature;
import net.minecraft.world.gen.feature.TallPlantFeature;
import net.minecraft.world.gen.feature.TreeFeature;
import net.minecraft.world.gen.feature.decorator.FeatureDecorator;
import net.minecraft.world.gen.noise.PerlinSimplexNoise;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class Biome {
    private static final Logger LOGGER = LogManager.getLogger();
    protected static final Biome.Height DEFAULT_HEIGHT = new Biome.Height(0.1F, 0.2F);
    protected static final Biome.Height RIVER_HEIGHT = new Biome.Height(-0.5F, 0.0F);
    protected static final Biome.Height OCEAN_HEIGHT = new Biome.Height(-1.0F, 0.1F);
    protected static final Biome.Height DEEP_OCEAN_HEIGHT = new Biome.Height(-1.8F, 0.1F);
    protected static final Biome.Height PLAINS_HEIGHT = new Biome.Height(0.125F, 0.05F);
    protected static final Biome.Height TAIGA_HEIGHT = new Biome.Height(0.2F, 0.2F);
    protected static final Biome.Height HILLS_HEIGHT = new Biome.Height(0.45F, 0.3F);
    protected static final Biome.Height PLATEAU_HEIGHT = new Biome.Height(1.5F, 0.025F);
    protected static final Biome.Height EXTREME_HILLS_HEIGHT = new Biome.Height(1.0F, 0.5F);
    protected static final Biome.Height MUSHROOM_SHORE_HEIGHT = new Biome.Height(0.0F, 0.025F);
    protected static final Biome.Height STONE_BEACH_HEIGHT = new Biome.Height(0.1F, 0.8F);
    protected static final Biome.Height MUSHROOM_HEIGHT = new Biome.Height(0.2F, 0.3F);
    protected static final Biome.Height SWAMP_HEIGHT = new Biome.Height(-0.2F, 0.1F);
    private static final Biome[] BY_ID = new Biome[256];
    public static final Set<Biome> EXPLORABLE = Sets.newHashSet();
    public static final Map<String, Biome> BY_NAME = Maps.newHashMap();
    public static final Biome OCEAN = new OceanBiome(0).setColor(112).setName("Ocean").setHeight(OCEAN_HEIGHT);
    public static final Biome PLAINS = new PlainsBiome(1).setColor(9286496).setName("Plains");
    public static final Biome DESERT = new DesertBiome(2)
        .setColor(16421912)
        .setName("Desert")
        .disableRain()
        .setTemperatureAndDownfall(2.0F, 0.0F)
        .setHeight(PLAINS_HEIGHT);
    public static final Biome EXTREME_HILLS = new ExtremeHillsBiome(3, false)
        .setColor(6316128)
        .setName("Extreme Hills")
        .setHeight(EXTREME_HILLS_HEIGHT)
        .setTemperatureAndDownfall(0.2F, 0.3F);
    public static final Biome FOREST = new ForestBiome(4, 0).setColor(353825).setName("Forest");
    public static final Biome TAIGA = new TaigaBiome(5, 0)
        .setColor(747097)
        .setName("Taiga")
        .setMutatedColor(5159473)
        .setTemperatureAndDownfall(0.25F, 0.8F)
        .setHeight(TAIGA_HEIGHT);
    public static final Biome SWAMPLAND = new SwampBiome(6)
        .setColor(522674)
        .setName("Swampland")
        .setMutatedColor(9154376)
        .setHeight(SWAMP_HEIGHT)
        .setTemperatureAndDownfall(0.8F, 0.9F);
    public static final Biome RIVER = new RiverBiome(7).setColor(255).setName("River").setHeight(RIVER_HEIGHT);
    public static final Biome HELL = new HellBiome(8).setColor(16711680).setName("Hell").disableRain().setTemperatureAndDownfall(2.0F, 0.0F);
    public static final Biome THE_END = new TheEndBiome(9).setColor(8421631).setName("The End").disableRain();
    public static final Biome FROZEN_OCEAN = new OceanBiome(10)
        .setColor(9474208)
        .setName("FrozenOcean")
        .setSnowy()
        .setHeight(OCEAN_HEIGHT)
        .setTemperatureAndDownfall(0.0F, 0.5F);
    public static final Biome FROZEN_RIVER = new RiverBiome(11)
        .setColor(10526975)
        .setName("FrozenRiver")
        .setSnowy()
        .setHeight(RIVER_HEIGHT)
        .setTemperatureAndDownfall(0.0F, 0.5F);
    public static final Biome ICE_PLAINS = new IceBiome(12, false)
        .setColor(16777215)
        .setName("Ice Plains")
        .setSnowy()
        .setTemperatureAndDownfall(0.0F, 0.5F)
        .setHeight(PLAINS_HEIGHT);
    public static final Biome ICE_MOUNTAINS = new IceBiome(13, false)
        .setColor(10526880)
        .setName("Ice Mountains")
        .setSnowy()
        .setHeight(HILLS_HEIGHT)
        .setTemperatureAndDownfall(0.0F, 0.5F);
    public static final Biome MUSHROOM_ISLAND = new MushroomBiome(14)
        .setColor(16711935)
        .setName("MushroomIsland")
        .setTemperatureAndDownfall(0.9F, 1.0F)
        .setHeight(MUSHROOM_HEIGHT);
    public static final Biome MUSHROOM_ISLAND_SHORE = new MushroomBiome(15)
        .setColor(10486015)
        .setName("MushroomIslandShore")
        .setTemperatureAndDownfall(0.9F, 1.0F)
        .setHeight(MUSHROOM_SHORE_HEIGHT);
    public static final Biome BEACH = new BeachBiome(16)
        .setColor(16440917)
        .setName("Beach")
        .setTemperatureAndDownfall(0.8F, 0.4F)
        .setHeight(MUSHROOM_SHORE_HEIGHT);
    public static final Biome DESERT_HILLS = new DesertBiome(17)
        .setColor(13786898)
        .setName("DesertHills")
        .disableRain()
        .setTemperatureAndDownfall(2.0F, 0.0F)
        .setHeight(HILLS_HEIGHT);
    public static final Biome FOREST_HILLS = new ForestBiome(18, 0).setColor(2250012).setName("ForestHills").setHeight(HILLS_HEIGHT);
    public static final Biome TAIGA_HILLS = new TaigaBiome(19, 0)
        .setColor(1456435)
        .setName("TaigaHills")
        .setMutatedColor(5159473)
        .setTemperatureAndDownfall(0.25F, 0.8F)
        .setHeight(HILLS_HEIGHT);
    public static final Biome EXTREME_HILLS_EDGE = new ExtremeHillsBiome(20, true)
        .setColor(7501978)
        .setName("Extreme Hills Edge")
        .setHeight(EXTREME_HILLS_HEIGHT.diminish())
        .setTemperatureAndDownfall(0.2F, 0.3F);
    public static final Biome JUNGLE = new JungleBiome(21, false)
        .setColor(5470985)
        .setName("Jungle")
        .setMutatedColor(5470985)
        .setTemperatureAndDownfall(0.95F, 0.9F);
    public static final Biome JUNGLE_HILLS = new JungleBiome(22, false)
        .setColor(2900485)
        .setName("JungleHills")
        .setMutatedColor(5470985)
        .setTemperatureAndDownfall(0.95F, 0.9F)
        .setHeight(HILLS_HEIGHT);
    public static final Biome JUNGLE_EDGE = new JungleBiome(23, true)
        .setColor(6458135)
        .setName("JungleEdge")
        .setMutatedColor(5470985)
        .setTemperatureAndDownfall(0.95F, 0.8F);
    public static final Biome DEEP_OCEAN = new OceanBiome(24).setColor(48).setName("Deep Ocean").setHeight(DEEP_OCEAN_HEIGHT);
    public static final Biome STONE_BEACH = new StoneBeachBiome(25)
        .setColor(10658436)
        .setName("Stone Beach")
        .setTemperatureAndDownfall(0.2F, 0.3F)
        .setHeight(STONE_BEACH_HEIGHT);
    public static final Biome COLD_BEACH = new BeachBiome(26)
        .setColor(16445632)
        .setName("Cold Beach")
        .setTemperatureAndDownfall(0.05F, 0.3F)
        .setHeight(MUSHROOM_SHORE_HEIGHT)
        .setSnowy();
    public static final Biome BIRCH_FOREST = new ForestBiome(27, 2).setName("Birch Forest").setColor(3175492);
    public static final Biome BIRCH_FOREST_HILLS = new ForestBiome(28, 2).setName("Birch Forest Hills").setColor(2055986).setHeight(HILLS_HEIGHT);
    public static final Biome ROOFED_FOREST = new ForestBiome(29, 3).setColor(4215066).setName("Roofed Forest");
    public static final Biome COLD_TAIGA = new TaigaBiome(30, 0)
        .setColor(3233098)
        .setName("Cold Taiga")
        .setMutatedColor(5159473)
        .setSnowy()
        .setTemperatureAndDownfall(-0.5F, 0.4F)
        .setHeight(TAIGA_HEIGHT)
        .setBaseColor(16777215);
    public static final Biome COLD_TAIGA_HILLS = new TaigaBiome(31, 0)
        .setColor(2375478)
        .setName("Cold Taiga Hills")
        .setMutatedColor(5159473)
        .setSnowy()
        .setTemperatureAndDownfall(-0.5F, 0.4F)
        .setHeight(HILLS_HEIGHT)
        .setBaseColor(16777215);
    public static final Biome MEGA_TAIGA = new TaigaBiome(32, 1)
        .setColor(5858897)
        .setName("Mega Taiga")
        .setMutatedColor(5159473)
        .setTemperatureAndDownfall(0.3F, 0.8F)
        .setHeight(TAIGA_HEIGHT);
    public static final Biome MEGA_TAIGA_HILLS = new TaigaBiome(33, 1)
        .setColor(4542270)
        .setName("Mega Taiga Hills")
        .setMutatedColor(5159473)
        .setTemperatureAndDownfall(0.3F, 0.8F)
        .setHeight(HILLS_HEIGHT);
    public static final Biome EXTREME_HILLS_PLUS = new ExtremeHillsBiome(34, true)
        .setColor(5271632)
        .setName("Extreme Hills+")
        .setHeight(EXTREME_HILLS_HEIGHT)
        .setTemperatureAndDownfall(0.2F, 0.3F);
    public static final Biome SAVANNA = new SavannaBiome(35)
        .setColor(12431967)
        .setName("Savanna")
        .setTemperatureAndDownfall(1.2F, 0.0F)
        .disableRain()
        .setHeight(PLAINS_HEIGHT);
    public static final Biome SAVANNA_PLATEAU = new SavannaBiome(36)
        .setColor(10984804)
        .setName("Savanna Plateau")
        .setTemperatureAndDownfall(1.0F, 0.0F)
        .disableRain()
        .setHeight(PLATEAU_HEIGHT);
    public static final Biome MESA = new MesaBiome(37, false, false).setColor(14238997).setName("Mesa");
    public static final Biome MESA_PLATEAU_F = new MesaBiome(38, false, true).setColor(11573093).setName("Mesa Plateau F").setHeight(PLATEAU_HEIGHT);
    public static final Biome MESA_PLATEAU = new MesaBiome(39, false, false).setColor(13274213).setName("Mesa Plateau").setHeight(PLATEAU_HEIGHT);
    public static final Biome DEFAULT = OCEAN;
    protected static final PerlinSimplexNoise TEMPERATURE_NOISE;
    protected static final PerlinSimplexNoise FOLIAGE_NOISE;
    protected static final DoublePlantFeature DOUBLE_PLANT;
    public String name;
    public int biomeColor;
    public int baseColor;
    public BlockState surfaceBlock = Blocks.GRASS.defaultState();
    public BlockState subsurfaceBlock = Blocks.DIRT.defaultState();
    public int mutatedColor = 5169201;
    public float baseHeight;
    public float heightVariation;
    public float temperature;
    public float downfall;
    public int waterFogColor;
    public FeatureDecorator decorator;
    protected List<Biome.SpawnEntry> monsterEntries;
    protected List<Biome.SpawnEntry> passiveEntries;
    protected List<Biome.SpawnEntry> waterEntries;
    protected List<Biome.SpawnEntry> ambientEntries;
    protected boolean snowy;
    protected boolean rainy;
    public final int id;
    protected TreeFeature tree;
    protected LargeOakTreeFeature largeTree;
    protected SwampTreeFeature swampTree;

    protected Biome(int id) {
        this.baseHeight = DEFAULT_HEIGHT.baseHeight;
        this.heightVariation = DEFAULT_HEIGHT.heightModifier;
        this.temperature = 0.5F;
        this.downfall = 0.5F;
        this.waterFogColor = 16777215;
        this.monsterEntries = Lists.newArrayList();
        this.passiveEntries = Lists.newArrayList();
        this.waterEntries = Lists.newArrayList();
        this.ambientEntries = Lists.newArrayList();
        this.rainy = true;
        this.tree = new TreeFeature(false);
        this.largeTree = new LargeOakTreeFeature(false);
        this.swampTree = new SwampTreeFeature();
        this.id = id;
        BY_ID[id] = this;
        this.decorator = this.createDecorator();
        this.passiveEntries.add(new Biome.SpawnEntry(SheepEntity.class, 12, 4, 4));
        this.passiveEntries.add(new Biome.SpawnEntry(RabbitEntity.class, 10, 3, 3));
        this.passiveEntries.add(new Biome.SpawnEntry(PigEntity.class, 10, 4, 4));
        this.passiveEntries.add(new Biome.SpawnEntry(ChickenEntity.class, 10, 4, 4));
        this.passiveEntries.add(new Biome.SpawnEntry(CowEntity.class, 8, 4, 4));
        this.monsterEntries.add(new Biome.SpawnEntry(SpiderEntity.class, 100, 4, 4));
        this.monsterEntries.add(new Biome.SpawnEntry(ZombieEntity.class, 100, 4, 4));
        this.monsterEntries.add(new Biome.SpawnEntry(SkeletonEntity.class, 100, 4, 4));
        this.monsterEntries.add(new Biome.SpawnEntry(CreeperEntity.class, 100, 4, 4));
        this.monsterEntries.add(new Biome.SpawnEntry(SlimeEntity.class, 100, 4, 4));
        this.monsterEntries.add(new Biome.SpawnEntry(EndermanEntity.class, 10, 1, 4));
        this.monsterEntries.add(new Biome.SpawnEntry(WitchEntity.class, 5, 1, 1));
        this.waterEntries.add(new Biome.SpawnEntry(SquidEntity.class, 10, 4, 4));
        this.ambientEntries.add(new Biome.SpawnEntry(BatEntity.class, 10, 8, 8));
    }

    protected FeatureDecorator createDecorator() {
        return new FeatureDecorator();
    }

    protected Biome setTemperatureAndDownfall(float temperature, float downfall) {
        if (temperature > 0.1F && temperature < 0.2F) {
            throw new IllegalArgumentException("Please avoid temperatures in the range 0.1 - 0.2 because of snow");
        }

        this.temperature = temperature;
        this.downfall = downfall;
        return this;
    }

    protected final Biome setHeight(Biome.Height height) {
        this.baseHeight = height.baseHeight;
        this.heightVariation = height.heightModifier;
        return this;
    }

    protected Biome disableRain() {
        this.rainy = false;
        return this;
    }

    public AbstractTreeFeature pickTree(Random random) {
        return random.nextInt(10) == 0 ? this.largeTree : this.tree;
    }

    public Feature pickPlant(Random random) {
        return new TallPlantFeature(TallPlantBlock.Type.GRASS);
    }

    public FlowerBlock.Type getRandomFlower(Random random, BlockPos pos) {
        return random.nextInt(3) > 0 ? FlowerBlock.Type.DANDELION : FlowerBlock.Type.POPPY;
    }

    protected Biome setSnowy() {
        this.snowy = true;
        return this;
    }

    protected Biome setName(String name) {
        this.name = name;
        return this;
    }

    protected Biome setMutatedColor(int color) {
        this.mutatedColor = color;
        return this;
    }

    protected Biome setColor(int modifier) {
        this.setColor(modifier, false);
        return this;
    }

    protected Biome setBaseColor(int color) {
        this.baseColor = color;
        return this;
    }

    protected Biome setColor(int color, boolean modifyBaseColor) {
        this.biomeColor = color;
        if (modifyBaseColor) {
            this.baseColor = (color & 16711422) >> 1;
        } else {
            this.baseColor = color;
        }

        return this;
    }

    public int getSkyColor(float temperature) {
        temperature /= 3.0F;
        temperature = MathHelper.clamp(temperature, -1.0F, 1.0F);
        return MathHelper.toRgb(0.62222224F - temperature * 0.05F, 0.5F + temperature * 0.1F, 1.0F);
    }

    public List<Biome.SpawnEntry> getSpawnEntries(MobCategory category) {
        switch (category) {
            case MONSTER:
                return this.monsterEntries;
            case CREATURE:
                return this.passiveEntries;
            case WATER_CREATURE:
                return this.waterEntries;
            case AMBIENT:
                return this.ambientEntries;
            default:
                return Collections.emptyList();
        }
    }

    public boolean isSnowy() {
        return this.isCold();
    }

    public boolean isRainy() {
        return !this.isCold() && this.rainy;
    }

    public boolean isHumid() {
        return this.downfall > 0.85F;
    }

    public float getAnimalSpawnChance() {
        return 0.1F;
    }

    public final int getScaledDownfall() {
        return (int)(this.downfall * 65536.0F);
    }

    public final float getDownfall() {
        return this.downfall;
    }

    public final float getTemperature(BlockPos pos) {
        if (pos.getY() > 64) {
            float f = (float)(TEMPERATURE_NOISE.getValue(pos.getX() * 1.0 / 8.0, pos.getZ() * 1.0 / 8.0) * 4.0);
            return this.temperature - (f + pos.getY() - 64.0F) * 0.05F / 30.0F;
        } else {
            return this.temperature;
        }
    }

    public void decorate(World world, Random random, BlockPos pos) {
        this.decorator.decorate(world, random, this, pos);
    }

    public int getGrassColor(BlockPos pos) {
        double d0 = MathHelper.clamp(this.getTemperature(pos), 0.0F, 1.0F);
        double d1 = MathHelper.clamp(this.getDownfall(), 0.0F, 1.0F);
        return GrassColors.getColor(d0, d1);
    }

    public int getFoliageColor(BlockPos pos) {
        double d0 = MathHelper.clamp(this.getTemperature(pos), 0.0F, 1.0F);
        double d1 = MathHelper.clamp(this.getDownfall(), 0.0F, 1.0F);
        return FoliageColors.get(d0, d1);
    }

    public boolean isCold() {
        return this.snowy;
    }

    /**
     * Places the bedrock layer, biome-dependent surface and subsurface blocks, and liquid bodies.
     */
    public void prepareAndBuildSurfaces(World world, Random random, BlockStateStorage blocks, int x, int z, double depth) {
        this.buildSurfaces(world, random, blocks, x, z, depth);
    }

    /**
     * Places the bedrock layer, biome-dependent surface and subsurface blocks, and liquid bodies.
     */
    public final void buildSurfaces(World world, Random random, BlockStateStorage blocks, int x, int z, double depth) {
        int i = world.getSeaLevel();
        BlockState blockstate = this.surfaceBlock;
        BlockState blockstate1 = this.subsurfaceBlock;
        int j = -1;
        int k = (int)(depth / 3.0 + 3.0 + random.nextDouble() * 0.25);
        int l = x & 15;
        int i1 = z & 15;
        BlockPos.Mutable blockpos$mutable = new BlockPos.Mutable();

        for (int j1 = 255; j1 >= 0; j1--) {
            if (j1 <= random.nextInt(5)) {
                blocks.set(i1, j1, l, Blocks.BEDROCK.defaultState());
            } else {
                BlockState blockstate2 = blocks.get(i1, j1, l);
                if (blockstate2.getBlock().getMaterial() == Material.AIR) {
                    j = -1;
                } else if (blockstate2.getBlock() == Blocks.STONE) {
                    if (j == -1) {
                        if (k <= 0) {
                            blockstate = null;
                            blockstate1 = Blocks.STONE.defaultState();
                        } else if (j1 >= i - 4 && j1 <= i + 1) {
                            blockstate = this.surfaceBlock;
                            blockstate1 = this.subsurfaceBlock;
                        }

                        if (j1 < i && (blockstate == null || blockstate.getBlock().getMaterial() == Material.AIR)) {
                            if (this.getTemperature(blockpos$mutable.set(x, j1, z)) < 0.15F) {
                                blockstate = Blocks.ICE.defaultState();
                            } else {
                                blockstate = Blocks.WATER.defaultState();
                            }
                        }

                        j = k;
                        if (j1 >= i - 1) {
                            blocks.set(i1, j1, l, blockstate);
                        } else if (j1 < i - 7 - k) {
                            blockstate = null;
                            blockstate1 = Blocks.STONE.defaultState();
                            blocks.set(i1, j1, l, Blocks.GRAVEL.defaultState());
                        } else {
                            blocks.set(i1, j1, l, blockstate1);
                        }
                    } else if (j > 0) {
                        j--;
                        blocks.set(i1, j1, l, blockstate1);
                        if (j == 0 && blockstate1.getBlock() == Blocks.SAND) {
                            j = random.nextInt(4) + Math.max(0, j1 - 63);
                            blockstate1 = blockstate1.get(SandBlock.VARIANT) == SandBlock.Variant.RED_SAND
                                ? Blocks.RED_SANDSTONE.defaultState()
                                : Blocks.SANDSTONE.defaultState();
                        }
                    }
                }
            }
        }
    }

    protected Biome mutate() {
        return this.mutate(this.id + 128);
    }

    protected Biome mutate(int id) {
        return new MutatedBiome(id, this);
    }

    public Class<? extends Biome> getType() {
        return (Class<? extends Biome>)this.getClass();
    }

    public boolean is(Biome biome) {
        return biome == this || biome != null && this.getType() == biome.getType();
    }

    public Biome.TemperatureCategory getTemperatureCategory() {
        if (this.temperature < 0.2) {
            return Biome.TemperatureCategory.COLD;
        } else {
            return this.temperature < 1.0 ? Biome.TemperatureCategory.MEDIUM : Biome.TemperatureCategory.WARM;
        }
    }

    public static Biome[] getAll() {
        return BY_ID;
    }

    public static Biome byId(int id) {
        return byId(id, null);
    }

    public static Biome byId(int id, Biome defaultValue) {
        if (id >= 0 && id <= BY_ID.length) {
            Biome biome = BY_ID[id];
            return biome == null ? defaultValue : biome;
        } else {
            LOGGER.warn("Biome ID is out of bounds: " + id + ", defaulting to 0 (Ocean)");
            return OCEAN;
        }
    }

    static {
        PLAINS.mutate();
        DESERT.mutate();
        FOREST.mutate();
        TAIGA.mutate();
        SWAMPLAND.mutate();
        ICE_PLAINS.mutate();
        JUNGLE.mutate();
        JUNGLE_EDGE.mutate();
        COLD_TAIGA.mutate();
        SAVANNA.mutate();
        SAVANNA_PLATEAU.mutate();
        MESA.mutate();
        MESA_PLATEAU_F.mutate();
        MESA_PLATEAU.mutate();
        BIRCH_FOREST.mutate();
        BIRCH_FOREST_HILLS.mutate();
        ROOFED_FOREST.mutate();
        MEGA_TAIGA.mutate();
        EXTREME_HILLS.mutate();
        EXTREME_HILLS_PLUS.mutate();
        MEGA_TAIGA.mutate(MEGA_TAIGA_HILLS.id + 128).setName("Redwood Taiga Hills M");

        for (Biome biome : BY_ID) {
            if (biome != null) {
                if (BY_NAME.containsKey(biome.name)) {
                    throw new Error("Biome \"" + biome.name + "\" is defined as both ID " + BY_NAME.get(biome.name).id + " and " + biome.id);
                }

                BY_NAME.put(biome.name, biome);
                if (biome.id < 128) {
                    EXPLORABLE.add(biome);
                }
            }
        }

        EXPLORABLE.remove(HELL);
        EXPLORABLE.remove(THE_END);
        EXPLORABLE.remove(FROZEN_OCEAN);
        EXPLORABLE.remove(EXTREME_HILLS_EDGE);
        TEMPERATURE_NOISE = new PerlinSimplexNoise(new Random(1234L), 1);
        FOLIAGE_NOISE = new PerlinSimplexNoise(new Random(2345L), 1);
        DOUBLE_PLANT = new DoublePlantFeature();
    }

    public static class Height {
        public float baseHeight;
        public float heightModifier;

        public Height(float baseHeight, float heightModifier) {
            this.baseHeight = baseHeight;
            this.heightModifier = heightModifier;
        }

        public Biome.Height diminish() {
            return new Biome.Height(this.baseHeight * 0.8F, this.heightModifier * 0.6F);
        }
    }

    public static class SpawnEntry extends WeightedPicker.Entry {
        public Class<? extends MobEntity> type;
        public int minGroupSize;
        public int maxGroupSize;

        public SpawnEntry(Class<? extends MobEntity> type, int weight, int minGroupSize, int maxGroupSize) {
            super(weight);
            this.type = type;
            this.minGroupSize = minGroupSize;
            this.maxGroupSize = maxGroupSize;
        }

        @Override
        public String toString() {
            return this.type.getSimpleName() + "*(" + this.minGroupSize + "-" + this.maxGroupSize + "):" + this.weight;
        }
    }

    public enum TemperatureCategory {
        OCEAN,
        COLD,
        MEDIUM,
        WARM;
    }
}
