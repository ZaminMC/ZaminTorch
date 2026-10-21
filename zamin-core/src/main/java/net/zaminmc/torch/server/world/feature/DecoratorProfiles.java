package net.zaminmc.torch.server.world.feature;

import static net.zaminmc.torch.server.world.feature.DecoratorProfile.Flavor;
import static net.zaminmc.torch.server.world.feature.DecoratorProfile.FlowerPick;
import static net.zaminmc.torch.server.world.feature.DecoratorProfile.PlantPick;
import static net.zaminmc.torch.server.world.feature.DecoratorProfile.TreePick;

/**
 * The 61 registration rows' decorator profiles (the VIII14 decorators
 * slice — the reference's biome-subclass ctor outcomes, 1:1): the twelve
 * counts each per-class ctor writes plus the pick laws and the decorate
 * passes its own class carries. A holder class so the enum constants can
 * reference the profiles at construction (the SpawnTables precedent).
 *
 * <p>Every row's derivation is named at its use in {@code Biome.java};
 * the table below mirrors the reference's classes (the counts:
 * ForestBiome lines 33-52, TaigaBiome 39-49, JungleBiome 31-45,
 * PlainsBiome 19-23, DesertBiome 12-21, SwampBiome 11-25,
 * ExtremeHillsBiome 23-29, SavannaBiome 19-23 + the shattered 68-73,
 * MesaBiome 31-47, MushroomBiome 11-17, BeachBiome/StoneBeachBiome 8-17,
 * IceBiome 13-18; the mutated rows through MutatedBiome's original-
 * decorator consult — the counts copied, the flavor passes never run).
 */
public final class DecoratorProfiles {

    private DecoratorProfiles() {
    }

    /** The class defaults (Biome's own FeatureDecorator fields). */
    public static final DecoratorProfile DEFAULT = DecoratorProfile.defaults();

    /** The plains (trees -999, flowers 4, grass 10 + the band pass). */
    public static final DecoratorProfile PLAINS = DecoratorProfile.of(
            -999, 4, 10, 0, 0, 0, 0, 1, 3, 1, 0, 0,
            TreePick.DEFAULT, PlantPick.DEFAULT, FlowerPick.PLAINS_BAND,
            Flavor.PLAINS_PASS);

    /** The sunflower plains (the fresh-ctor mutation: + the sunflower pass). */
    public static final DecoratorProfile SUNFLOWER_PLAINS = DecoratorProfile.of(
            -999, 4, 10, 0, 0, 0, 0, 1, 3, 1, 0, 0,
            TreePick.DEFAULT, PlantPick.DEFAULT, FlowerPick.PLAINS_BAND,
            Flavor.PLAINS_PASS, Flavor.SUNFLOWER_PASS);

    /** The desert pair (the dead bushes, the reeds, the cacti, the well). */
    public static final DecoratorProfile DESERT = DecoratorProfile.of(
            -999, 2, 1, 2, 0, 50, 10, 1, 3, 1, 0, 0,
            TreePick.DEFAULT, PlantPick.DEFAULT, FlowerPick.DEFAULT,
            Flavor.DESERT_WELL);

    /** The desert's standard mutation (the counts without the well pass). */
    public static final DecoratorProfile DESERT_M = DecoratorProfile.of(
            -999, 2, 1, 2, 0, 50, 10, 1, 3, 1, 0, 0,
            TreePick.DEFAULT, PlantPick.DEFAULT, FlowerPick.DEFAULT);

    /** The extreme hills (the spruce-biased pick, the emerald + egg veins). */
    public static final DecoratorProfile HILLS = DecoratorProfile.of(
            0, 2, 1, 0, 0, 0, 0, 1, 3, 1, 0, 0,
            TreePick.HILLS, PlantPick.DEFAULT, FlowerPick.DEFAULT,
            Flavor.HILLS_VEINS);

    /** The more-trees hills forms (the edge, the plus — trees 3). */
    public static final DecoratorProfile HILLS_TREED = DecoratorProfile.of(
            3, 2, 1, 0, 0, 0, 0, 1, 3, 1, 0, 0,
            TreePick.HILLS, PlantPick.DEFAULT, FlowerPick.DEFAULT,
            Flavor.HILLS_VEINS);

    /** The forest (trees 10, grass 2, the double plants, the oak-birch law). */
    public static final DecoratorProfile FOREST = DecoratorProfile.of(
            10, 2, 2, 0, 0, 0, 0, 1, 3, 1, 0, 0,
            TreePick.FOREST_OAK, PlantPick.DEFAULT, FlowerPick.DEFAULT,
            Flavor.FOREST_DOUBLE_PLANTS);

    /** The birch forest (the birch always + the double plants). */
    public static final DecoratorProfile BIRCH_FOREST = DecoratorProfile.of(
            10, 2, 2, 0, 0, 0, 0, 1, 3, 1, 0, 0,
            TreePick.FOREST_BIRCH, PlantPick.DEFAULT, FlowerPick.DEFAULT,
            Flavor.FOREST_DOUBLE_PLANTS);

    /** The roofed forest (the grid pass, trees -999). */
    public static final DecoratorProfile ROOFED_FOREST = DecoratorProfile.of(
            -999, 2, 2, 0, 0, 0, 0, 1, 3, 1, 0, 0,
            TreePick.FOREST_ROOFED, PlantPick.DEFAULT, FlowerPick.DEFAULT,
            Flavor.ROOFED_GRID, Flavor.FOREST_DOUBLE_PLANTS);

    /** The roofed forest's standard mutation (the original's whole decorate). */
    public static final DecoratorProfile ROOFED_FOREST_M = ROOFED_FOREST;

    /** The flower forest (the fresh variant-1 ctor: the hundred flowers). */
    public static final DecoratorProfile FLOWER_FOREST = DecoratorProfile.of(
            6, 100, 1, 0, 0, 0, 0, 1, 3, 1, 0, 0,
            TreePick.FOREST_FLOWER, PlantPick.DEFAULT,
            FlowerPick.FOREST_FLOWER_BAND,
            Flavor.FOREST_DOUBLE_PLANTS, Flavor.FLOWER_FOREST_DOUBLE_BONUS);

    /** The taiga (trees 10, grass 1, mushrooms 1, the ferns). */
    public static final DecoratorProfile TAIGA = DecoratorProfile.of(
            10, 2, 1, 0, 1, 0, 0, 1, 3, 1, 0, 0,
            TreePick.TAIGA, PlantPick.TAIGA_FERN, FlowerPick.DEFAULT,
            Flavor.TAIGA_FERNS);

    /** The taiga's standard mutation (the counts, the delegated picks, no pass). */
    public static final DecoratorProfile TAIGA_M = DecoratorProfile.of(
            10, 2, 1, 0, 1, 0, 0, 1, 3, 1, 0, 0,
            TreePick.TAIGA, PlantPick.TAIGA_FERN, FlowerPick.DEFAULT);

    /** The mega taiga (grass 7, deadBush 1, mushrooms 3, the boulders). */
    public static final DecoratorProfile MEGA_TAIGA = DecoratorProfile.of(
            10, 2, 7, 1, 3, 0, 0, 1, 3, 1, 0, 0,
            TreePick.TAIGA_MEGA, PlantPick.TAIGA_FERN, FlowerPick.DEFAULT,
            Flavor.TAIGA_FERNS, Flavor.MEGA_BOULDERS);

    /** The mega spruce taiga pair (the fresh variant-2 ctor shape). */
    public static final DecoratorProfile MEGA_SPRUCE_TAIGA = DecoratorProfile.of(
            10, 2, 7, 1, 3, 0, 0, 1, 3, 1, 0, 0,
            TreePick.TAIGA_MEGA_SPRUCE, PlantPick.TAIGA_FERN, FlowerPick.DEFAULT,
            Flavor.TAIGA_FERNS, Flavor.MEGA_BOULDERS);

    /** The swamp (the swamp tree, the orchid, the lilies, the reeds). */
    public static final DecoratorProfile SWAMP = DecoratorProfile.of(
            2, 1, 5, 1, 8, 10, 0, 0, 0, 1, 0, 4,
            TreePick.SWAMP, PlantPick.DEFAULT, FlowerPick.SWAMP_ORCHID);

    /** The mushroom isle pair (trees/flowers/grass -100, the huge mushrooms). */
    public static final DecoratorProfile MUSHROOM = DecoratorProfile.of(
            -100, -100, -100, 0, 1, 0, 0, 1, 3, 1, 1, 0,
            TreePick.DEFAULT, PlantPick.DEFAULT, FlowerPick.DEFAULT);

    /** The shores (the beach, the cold beach, the stone beach: everything off). */
    public static final DecoratorProfile BEACH = DecoratorProfile.of(
            -999, 2, 1, 0, 0, 0, 0, 1, 3, 1, 0, 0,
            TreePick.DEFAULT, PlantPick.DEFAULT, FlowerPick.DEFAULT);

    /** The jungle (trees 50, grass 25, flowers 4, the melon + vine tail). */
    public static final DecoratorProfile JUNGLE = DecoratorProfile.of(
            50, 4, 25, 0, 0, 0, 0, 1, 3, 1, 0, 0,
            TreePick.JUNGLE, PlantPick.JUNGLE_FERN, FlowerPick.DEFAULT,
            Flavor.JUNGLE_TAIL);

    /** The jungle edge (trees 2, the rest of the jungle's own shape). */
    public static final DecoratorProfile JUNGLE_EDGE = DecoratorProfile.of(
            2, 4, 25, 0, 0, 0, 0, 1, 3, 1, 0, 0,
            TreePick.JUNGLE_EDGE, PlantPick.JUNGLE_FERN, FlowerPick.DEFAULT,
            Flavor.JUNGLE_TAIL);

    /** The jungle pair's standard mutations (the counts, no tail). */
    public static final DecoratorProfile JUNGLE_M = DecoratorProfile.of(
            50, 4, 25, 0, 0, 0, 0, 1, 3, 1, 0, 0,
            TreePick.JUNGLE, PlantPick.JUNGLE_FERN, FlowerPick.DEFAULT);

    /** The jungle edge's standard mutation. */
    public static final DecoratorProfile JUNGLE_EDGE_M = DecoratorProfile.of(
            2, 4, 25, 0, 0, 0, 0, 1, 3, 1, 0, 0,
            TreePick.JUNGLE_EDGE, PlantPick.JUNGLE_FERN, FlowerPick.DEFAULT);

    /** The savanna pair (the acacia, the double grass pass). */
    public static final DecoratorProfile SAVANNA = DecoratorProfile.of(
            1, 4, 20, 0, 0, 0, 0, 1, 3, 1, 0, 0,
            TreePick.SAVANNA, PlantPick.DEFAULT, FlowerPick.DEFAULT,
            Flavor.SAVANNA_DOUBLE_PLANTS);

    /** The shattered savanna pair (the fresh-ctor counts, the walk alone). */
    public static final DecoratorProfile SHATTERED_SAVANNA = DecoratorProfile.of(
            2, 2, 5, 0, 0, 0, 0, 1, 3, 1, 0, 0,
            TreePick.SAVANNA, PlantPick.DEFAULT, FlowerPick.DEFAULT);

    /** The mesa family (the dead bushes, the reeds, the cacti). */
    public static final DecoratorProfile MESA = DecoratorProfile.of(
            -999, 0, 1, 20, 0, 3, 5, 1, 3, 1, 0, 0,
            TreePick.MESA_OAK, PlantPick.DEFAULT, FlowerPick.DEFAULT);

    /** The mesa plateau F (the has-trees arm: trees 5). */
    public static final DecoratorProfile MESA_TREED = DecoratorProfile.of(
            5, 0, 1, 20, 0, 3, 5, 1, 3, 1, 0, 0,
            TreePick.MESA_OAK, PlantPick.DEFAULT, FlowerPick.DEFAULT);

    /** The ice plains pair (the spruce always, the spikes off). */
    public static final DecoratorProfile ICE = DecoratorProfile.of(
            0, 2, 1, 0, 0, 0, 0, 1, 3, 1, 0, 0,
            TreePick.ICE_SPRUCE, PlantPick.DEFAULT, FlowerPick.DEFAULT);

    /** The ice plains spikes (the fresh spikes ctor: the spikes + patches). */
    public static final DecoratorProfile ICE_SPIKES = DecoratorProfile.of(
            0, 2, 1, 0, 0, 0, 0, 1, 3, 1, 0, 0,
            TreePick.ICE_SPRUCE, PlantPick.DEFAULT, FlowerPick.DEFAULT,
            Flavor.ICE_SPIKES);
}
