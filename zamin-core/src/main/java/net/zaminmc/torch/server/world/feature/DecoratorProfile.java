package net.zaminmc.torch.server.world.feature;

import java.util.Set;

/**
 * One biome row's decorator settings (the VIII14 decorators slice — the
 * reference's per-class ctor outcomes, the {@code FeatureDecorator} field
 * writes the biome subclasses perform, 1:1): the twelve placement counts
 * and the pick/flavor laws the walk consults. Immutable per row, carried
 * on the registration table's own rows like the spawn tables before them.
 *
 * <p>THE MUTATION LAW ({@code MutatedBiome} lines 13-43): the standard
 * mutated rows run the walk over their ORIGINAL's counts (the reference's
 * {@code this.original.decorator.decorate} consults the original's
 * decorator object — the mutated row's own fresh default fields sit
 * unused) but the per-biome decorate OVERRIDES never run on them (the
 * walk alone, no flavor passes) — the mutated profile carries the
 * original's counts + pick laws (the reference delegates pickTree/
 * pickPlant/getRandomFlower through the original) with NO flavor flags.
 * The special fresh-ctor mutations (the sunflower plains, the flower
 * forest, the mega spruce pair, the mesa Bryce, the ice spikes, the
 * shattered savanna pair) carry their own ctor outcomes whole.</p>
 *
 * <p>THE COUNTS' MEANINGS ({@code FeatureDecorator} lines 53-64): the
 * class defaults are trees 0, flowers 2, grass 1, deadBush 0, mushrooms
 * 0, reeds 0, cacti 0, gravelPatches 1, sandPatches 3, clayPatches 1,
 * hugeMushrooms 0, lilyPads 0; the negative sentinel (-999 / -100) keeps
 * the stage's loop from ever running.</p>
 */
public record DecoratorProfile(
        int treeAttempts,
        int flowerAttempts,
        int grassAttempts,
        int deadBushAttempts,
        int mushroomAttempts,
        int sugarcaneAttempts,
        int cactusAttempts,
        int gravelPatchAttempts,
        int sandPatchAttempts,
        int clayPatchAttempts,
        int hugeMushroomAttempts,
        int lilyPadAttempts,
        TreePick treePick,
        PlantPick plantPick,
        FlowerPick flowerPick,
        Set<Flavor> flavor) {

    /** The tree-selection law ({@code Biome.pickTree} + the class overrides). */
    public enum TreePick {
        /** The base law: the large oak at 1-in-10, else the oak. */
        DEFAULT,
        /** ForestBiome variant 0: the oak at 4-in-5, else the birch. */
        FOREST_OAK,
        /** ForestBiome variant 1 (the flower forest): the base law. */
        FOREST_FLOWER,
        /** ForestBiome variant 2: the birch always. */
        FOREST_BIRCH,
        /** ForestBiome variant 3: the dark oak at 3-in-4, else the oak/birch law. */
        FOREST_ROOFED,
        /** The birch mutation: the tall birch at 1-in-2, else the birch. */
        BIRCH_TALL,
        /** TaigaBiome variant 0: the pine at 1-in-3, else the spruce. */
        TAIGA,
        /** TaigaBiome variant 1 (mega): the giant pine/spruce pair at 1-in-3. */
        TAIGA_MEGA,
        /** TaigaBiome variant 2 (mega spruce): the giant spruce at 1-in-3. */
        TAIGA_MEGA_SPRUCE,
        /** JungleBiome edge=false: the ladder bush/giant/small over the large oak. */
        JUNGLE,
        /** JungleBiome edge=true: the bush/small pair over the large oak. */
        JUNGLE_EDGE,
        /** SavannaBiome: the acacia at 4-in-5, else the oak. */
        SAVANNA,
        /** MesaBiome: the oak always. */
        MESA_OAK,
        /** IceBiome: the spruce always. */
        ICE_SPRUCE,
        /** ExtremeHillsBiome: the spruce at 2-in-3, else the base law. */
        HILLS,
        /** SwampBiome: the swamp tree always. */
        SWAMP
    }

    /** The ground-cover plant law ({@code Biome.pickPlant} + the overrides). */
    public enum PlantPick {
        /** The base law: the tall grass always. */
        DEFAULT,
        /** TaigaBiome: the fern at 4-in-5, else the tall grass. */
        TAIGA_FERN,
        /** JungleBiome: the fern at 1-in-4, else the tall grass. */
        JUNGLE_FERN
    }

    /** The flower-pick law ({@code Biome.getRandomFlower} + the overrides). */
    public enum FlowerPick {
        /** The base law: the dandelion at 2-in-3, else the poppy. */
        DEFAULT,
        /** PlainsBiome: the tulip band + the poppy/houstonia/oxeye ladder. */
        PLAINS_BAND,
        /** ForestBiome variant 1: the nine-flower noise walk (the orchid swapped). */
        FOREST_FLOWER_BAND,
        /** SwampBiome: the blue orchid always. */
        SWAMP_ORCHID
    }

    /**
     * The per-class decorate passes ({@code Biome.decorate} overrides —
     * the passes that run beside the walk itself; never on the standard
     * mutated rows).
     */
    public enum Flavor {
        /** ForestBiome.decorate's double-plant roll (the forest family's own). */
        FOREST_DOUBLE_PLANTS,
        /** The flower forest's +2 on the double-plant roll. */
        FLOWER_FOREST_DOUBLE_BONUS,
        /** ForestBiome variant 3's 4x4 roofed grid pass. */
        ROOFED_GRID,
        /** TaigaBiome.decorate's seven fern placements. */
        TAIGA_FERNS,
        /** The mega variants' mossy boulder roll. */
        MEGA_BOULDERS,
        /** SavannaBiome.decorate's seven double-grass placements. */
        SAVANNA_DOUBLE_PLANTS,
        /** PlainsBiome.decorate's band consult + the seven double grass. */
        PLAINS_PASS,
        /** The sunflower plains' ten sunflower placements. */
        SUNFLOWER_PASS,
        /** JungleBiome.decorate's melon + vine tail (AFTER the walk). */
        JUNGLE_TAIL,
        /** DesertBiome.decorate's 1-in-1000 well (AFTER the walk). */
        DESERT_WELL,
        /** IceBiome spikes variant's three spikes + two patches. */
        ICE_SPIKES,
        /** ExtremeHillsBiome.decorate's emerald + monster-egg veins (AFTER). */
        HILLS_VEINS
    }

    /** The class-default counts ({@code FeatureDecorator} lines 53-64). */
    public static DecoratorProfile defaults() {
        return new DecoratorProfile(0, 2, 1, 0, 0, 0, 0,
                1, 3, 1, 0, 0,
                TreePick.DEFAULT, PlantPick.DEFAULT, FlowerPick.DEFAULT, Set.of());
    }

    /**
     * The whole shape (the ctor-field writes the biome subclasses
     * perform, in the reference's own order).
     */
    public static DecoratorProfile of(int treeAttempts, int flowerAttempts,
            int grassAttempts, int deadBushAttempts, int mushroomAttempts,
            int sugarcaneAttempts, int cactusAttempts,
            int gravelPatchAttempts, int sandPatchAttempts, int clayPatchAttempts,
            int hugeMushroomAttempts, int lilyPadAttempts,
            TreePick treePick, PlantPick plantPick, FlowerPick flowerPick,
            Flavor... flavor) {
        return new DecoratorProfile(treeAttempts, flowerAttempts, grassAttempts,
                deadBushAttempts, mushroomAttempts, sugarcaneAttempts,
                cactusAttempts, gravelPatchAttempts, sandPatchAttempts,
                clayPatchAttempts, hugeMushroomAttempts, lilyPadAttempts,
                treePick, plantPick, flowerPick,
                flavor.length == 0 ? Set.of() : Set.of(flavor));
    }
}
