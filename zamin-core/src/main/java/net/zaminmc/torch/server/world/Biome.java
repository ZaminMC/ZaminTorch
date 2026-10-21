package net.zaminmc.torch.server.world;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.world.noise.PerlinSimplexNoise;

/**
 * The engine's biome registration table — the reference's {@code Biome.java}
 * static registration whole (lines 70-234 + the static block's 21 mutations
 * + the class ctors): 40 base rows (ids 0-39) + 21 mutated rows (ids
 * 129-167) = 61, the reference's own census. The 1.8 numeric ids ride the
 * chunk packet's biome array, which is what the real client colors grass
 * and foliage from — and the byte-exact display names ride the all-biomes
 * achievement's progress set, identical to a vanilla stats file's rows.
 *
 * <p>Every row carries the registration datum the reference's ctor chain
 * sets: the temperature/downfall pair ({@code setTemperatureAndDownfall},
 * the class default 0.5/0.5), the {@code disableRain} field (the desert
 * family, hell, the end, the savanna pair, the mesa family), the
 * {@code setSnowy} flag (the frozen family), the surface/subsurface pair
 * ({@code surfaceBlock}/{@code subsurfaceBlock} as the 1.8.8 wire ids —
 * the class default grass/dirt, the desert/beach sand pair, the stone
 * beach, the mushroom island's mycelium, the end's dirt pair, the ice
 * spikes' snow, the mesa family's red sand over stained clay), the height
 * pair ({@code setHeight} — the base/variation bands the GenLayer layout
 * slice consults), and the swamp's own water fog color
 * ({@code waterFogColor} 14745518 against the class default 16777215).
 *
 * <p>THE SPAWN TABLES (the reference's four lists — the ctor's own
 * defaults at lines 279-292, the per-class clears and adds, the
 * mutated rows' copies): every row carries its four tables (the
 * monster/creature/water/ambient consults — the natural cycle's pick
 * AND the worldgen seeding's read). The five audit fixes this slice
 * lands against the reference's own mutation laws: 132 is the FRESH
 * ForestBiome(1) "Flower Forest" (not a standard copy — the forest's
 * own 0.1 base), 161 rides TaigaBiome's special MEGA_TAIGA path (the
 * 0.25 temperature, the original's 0.2/0.2 pair — not the standard
 * bump), 165 is the fresh "Mesa (Bryce)" (the class-default 0.1/0.2),
 * and 166/167 carry HILLS_HEIGHT's 0.45/0.3 (MesaBiome.mutate's
 * !flag arm).
 *
 * <p>THE RAIN READ LAW (the reference's {@code isRainy()} line 393-395):
 * {@code !isCold() && rainy} — a SNOWY biome never reports rain (the
 * frozen ocean, the frozen river, the ice plains pair, the cold beach,
 * the cold taiga pair all refuse the cauldron's rain fill), and the
 * disableRain family refuses everywhere. The field alone never answers:
 * the frozen family's field is true (the class default), the READ is
 * false — this slice's own catch, the first extraction draft read the
 * field.
 *
 * <p>THE MUTATION LAW (the static block lines 554-596 + {@code MutatedBiome}
 * lines 13-43): the standard form copies every registration row from the
 * original (name + " M", temperature, downfall, surface pair, flags, water
 * fog) and bumps the height pair (+0.1 base, +0.2 variation). Four
 * mutations carry their own shapes: PLAINS' "Sunflower Plains" (a fresh
 * PlainsBiome — no height bump, the registration pair whole), ICE_PLAINS'
 * "Ice Plains Spikes" (the snow surface, the +0.3/+0.4 height overwrite —
 * the reference's own dead {@code setHeight} quirk kept), MEGA_TAIGA's
 * "Mega Spruce Taiga" (a fresh variant-2 taiga — the 0.25 temperature
 * against the base's 0.3, no height bump), and the savanna pair's
 * shattered forms (the {@code (t + 1) * 0.5} temperature law and the
 * {@code base * 0.5 + 0.3} / {@code variation * 0.5 + 1.2} height law).
 * The special 161 (the static block's second MEGA_TAIGA call) is the
 * "Redwood Taiga Hills M" — the standard copy of MEGA_TAIGA itself.
 *
 * <p>THE ALTITUDE SHAVE ({@code getTemperature} lines 413-420): above y 64
 * the temperature walks down {@code temperature - (noise(x/8, z/8) * 4 +
 * y - 64) * 0.05 / 30} over the fixed-seed {@code TEMPERATURE_NOISE}
 * ({@code new PerlinSimplexNoise(new Random(1234L), 1)} — the FOLIAGE_NOISE
 * 2345 sibling, world-seed independent, identical across every world).
 * The precipitation gates ({@code canFreeze}/{@code canSnowFall}'s
 * {@code > 0.15F} refusal) read this shaved form.
 *
 * <p>THE EXPLORABLE BAND (the static block lines 577-593): every id
 * &lt; 128 minus HELL, THE_END, FROZEN_OCEAN and EXTREME_HILLS_EDGE — the
 * all-biomes achievement's 36-row target set (the mutated ids never join:
 * they are all {@code >= 128}). The engine's band grows from the 6-row
 * shading subset to the reference's whole census; the ZPD v11 progress
 * rows stay compatible (the set keys on the display name, the grown rows
 * simply join the target list).
 *
 * <p>The color rows ({@code biomeColor}/{@code baseColor}/{@code
 * mutatedColor}/{@code getSkyColor}) never ride the wire — the client's
 * own BiomeColors derives every tint from the biome id itself, so the
 * server table carries no color datum (the vanilla-grade answer). The
 * GenLayer layout (which biome generates where), the depth-dependent
 * surface builders (the mega taiga's podzol/coarse switch, the shattered
 * savanna's, the extreme hills', the mesa clay layers), the feature
 * decorators and the flower-color overrides ledger as the biome
 * family's named follow-up slices — this slice is the registration
 * foundation they all consult; the SPAWN TABLES LANDED (every row
 * carries its four lists — the natural cycle's pick and the worldgen
 * seeding's read both consult them now).
 */
public enum Biome {

    // ---------------------------------------------------------- the 40 base rows
    // (the reference's registration order, Biome.java lines 70-234)

    /** Id 0 (OceanBiome): the class-default pair, the ocean height band. */
    OCEAN(0, "Ocean", 0.5f, 0.5f, false, true, 16777215,
            -1.0f, 0.1f, 2, 3, Family.OCEAN, null, Spawns.NO_CREATURES),
    /** Id 1 (PlainsBiome): sparse oaks, grass and the flower rolls. */
    PLAINS(1, "Plains", 0.8f, 0.4f, false, true, 16777215,
            0.125f, 0.05f, 2, 3, Family.PLAINS, null, Spawns.PLAINS_LIKE),
    /** Id 2 (DesertBiome): sand skin over sand, the no-rain refusal. */
    DESERT(2, "Desert", 2.0f, 0.0f, false, false, 16777215,
            0.125f, 0.05f, 12, 12, Family.DESERT, null, Spawns.NO_CREATURES),
    /** Id 3 (ExtremeHillsBiome, variant 0): the stone-depth switch's base. */
    EXTREME_HILLS(3, "Extreme Hills", 0.2f, 0.3f, false, true, 16777215,
            1.0f, 0.5f, 2, 3, Family.EXTREME_HILLS, null, Spawns.BASE),
    /** Id 4 (ForestBiome, variant 0): dense oak forest, grass and flowers. */
    FOREST(4, "Forest", 0.7f, 0.8f, false, true, 16777215,
            0.1f, 0.2f, 2, 3, Family.FOREST, null, Spawns.FOREST_LIKE),
    /** Id 5 (TaigaBiome, variant 0): the spruce band. */
    TAIGA(5, "Taiga", 0.25f, 0.8f, false, true, 16777215,
            0.2f, 0.2f, 2, 3, Family.TAIGA, null, Spawns.TAIGA_LIKE),
    /** Id 6 (SwampBiome): the water fog row, the flat height band. */
    SWAMPLAND(6, "Swampland", 0.8f, 0.9f, false, true, 14745518,
            -0.2f, 0.1f, 2, 3, Family.SWAMP, null, Spawns.SWAMP),
    /** Id 7 (RiverBiome): the class-default pair, the river height band. */
    RIVER(7, "River", 0.5f, 0.5f, false, true, 16777215,
            -0.5f, 0.0f, 2, 3, Family.RIVER, null, Spawns.NO_CREATURES),
    /** Id 8 (HellBiome): the nether's FixedBiomeSource — every cell. */
    HELL(8, "Hell", 2.0f, 0.0f, false, false, 16777215,
            0.1f, 0.2f, 2, 3, Family.HELL, null, Spawns.HELL),
    /** Id 9 (TheEndBiome): the dirt pair, the end's own decorator. */
    THE_END(9, "The End", 0.5f, 0.5f, false, false, 16777215,
            0.1f, 0.2f, 3, 3, Family.THE_END, null, Spawns.THE_END),
    /** Id 10 (OceanBiome + setSnowy): the frozen read refuses the rain. */
    FROZEN_OCEAN(10, "FrozenOcean", 0.0f, 0.5f, true, true, 16777215,
            -1.0f, 0.1f, 2, 3, Family.OCEAN, null, Spawns.NO_CREATURES),
    /** Id 11 (RiverBiome + setSnowy): the frozen river column. */
    FROZEN_RIVER(11, "FrozenRiver", 0.0f, 0.5f, true, true, 16777215,
            -0.5f, 0.0f, 2, 3, Family.RIVER, null, Spawns.NO_CREATURES),
    /** Id 12 (IceBiome, spikes=false): GRASS, not snow — the ctor quirk. */
    ICE_PLAINS(12, "Ice Plains", 0.0f, 0.5f, true, true, 16777215,
            0.125f, 0.05f, 2, 3, Family.ICE, null, Spawns.NO_CREATURES),
    /** Id 13 (IceBiome, spikes=false): the hills height band. */
    ICE_MOUNTAINS(13, "Ice Mountains", 0.0f, 0.5f, true, true, 16777215,
            0.45f, 0.3f, 2, 3, Family.ICE, null, Spawns.NO_CREATURES),
    /** Id 14 (MushroomBiome): the mycelium surface, the mooshroom isle. */
    MUSHROOM_ISLAND(14, "MushroomIsland", 0.9f, 1.0f, false, true, 16777215,
            0.2f, 0.3f, 110, 3, Family.MUSHROOM, null, Spawns.MUSHROOM),
    /** Id 15 (MushroomBiome): the shore height band. */
    MUSHROOM_ISLAND_SHORE(15, "MushroomIslandShore", 0.9f, 1.0f, false, true,
            16777215, 0.0f, 0.025f, 110, 3, Family.MUSHROOM, null, Spawns.MUSHROOM),
    /** Id 16 (BeachBiome): the sand pair. */
    BEACH(16, "Beach", 0.8f, 0.4f, false, true, 16777215,
            0.0f, 0.025f, 12, 12, Family.BEACH, null, Spawns.NO_CREATURES),
    /** Id 17 (DesertBiome): the hills height band. */
    DESERT_HILLS(17, "DesertHills", 2.0f, 0.0f, false, false, 16777215,
            0.45f, 0.3f, 12, 12, Family.DESERT, null, Spawns.NO_CREATURES),
    /** Id 18 (ForestBiome, variant 0): the hills height band. */
    FOREST_HILLS(18, "ForestHills", 0.7f, 0.8f, false, true, 16777215,
            0.45f, 0.3f, 2, 3, Family.FOREST, null, Spawns.FOREST_LIKE),
    /** Id 19 (TaigaBiome, variant 0): the hills height band. */
    TAIGA_HILLS(19, "TaigaHills", 0.25f, 0.8f, false, true, 16777215,
            0.45f, 0.3f, 2, 3, Family.TAIGA, null, Spawns.TAIGA_LIKE),
    /** Id 20 (ExtremeHillsBiome, variant edge): the diminished heights. */
    EXTREME_HILLS_EDGE(20, "Extreme Hills Edge", 0.2f, 0.3f, false, true,
            16777215, 0.8f, 0.3f, 2, 3, Family.EXTREME_HILLS, null, Spawns.BASE),
    /** Id 21 (JungleBiome, edge=false): the hot-wet band. */
    JUNGLE(21, "Jungle", 0.95f, 0.9f, false, true, 16777215,
            0.1f, 0.2f, 2, 3, Family.JUNGLE, null, Spawns.JUNGLE),
    /** Id 22 (JungleBiome, edge=false): the hills height band. */
    JUNGLE_HILLS(22, "JungleHills", 0.95f, 0.9f, false, true, 16777215,
            0.45f, 0.3f, 2, 3, Family.JUNGLE, null, Spawns.JUNGLE),
    /** Id 23 (JungleBiome, edge=true): the drier edge band (0.8). */
    JUNGLE_EDGE(23, "JungleEdge", 0.95f, 0.8f, false, true, 16777215,
            0.1f, 0.2f, 2, 3, Family.JUNGLE, null, Spawns.JUNGLE_EDGE),
    /** Id 24 (OceanBiome): the deep water band (the monument gate). */
    DEEP_OCEAN(24, "Deep Ocean", 0.5f, 0.5f, false, true, 16777215,
            -1.8f, 0.1f, 2, 3, Family.OCEAN, null, Spawns.NO_CREATURES),
    /** Id 25 (StoneBeachBiome): the stone pair. */
    STONE_BEACH(25, "Stone Beach", 0.2f, 0.3f, false, true, 16777215,
            0.1f, 0.8f, 1, 1, Family.STONE_BEACH, null, Spawns.NO_CREATURES),
    /** Id 26 (BeachBiome + setSnowy): the cold sand shore. */
    COLD_BEACH(26, "Cold Beach", 0.05f, 0.3f, true, true, 16777215,
            0.0f, 0.025f, 12, 12, Family.BEACH, null, Spawns.NO_CREATURES),
    /** Id 27 (ForestBiome, variant 2): the birch pair (0.6/0.6). */
    BIRCH_FOREST(27, "Birch Forest", 0.6f, 0.6f, false, true, 16777215,
            0.1f, 0.2f, 2, 3, Family.FOREST, null, Spawns.BASE),
    /** Id 28 (ForestBiome, variant 2): the hills height band. */
    BIRCH_FOREST_HILLS(28, "Birch Forest Hills", 0.6f, 0.6f, false, true,
            16777215, 0.45f, 0.3f, 2, 3, Family.FOREST, null, Spawns.BASE),
    /** Id 29 (ForestBiome, variant 3): the dark oak band. */
    ROOFED_FOREST(29, "Roofed Forest", 0.7f, 0.8f, false, true, 16777215,
            0.1f, 0.2f, 2, 3, Family.FOREST, null, Spawns.BASE),
    /** Id 30 (TaigaBiome, variant 0 + setSnowy): the negative band. */
    COLD_TAIGA(30, "Cold Taiga", -0.5f, 0.4f, true, true, 16777215,
            0.2f, 0.2f, 2, 3, Family.TAIGA, null, Spawns.TAIGA_LIKE),
    /** Id 31 (TaigaBiome, variant 0 + setSnowy): the hills band. */
    COLD_TAIGA_HILLS(31, "Cold Taiga Hills", -0.5f, 0.4f, true, true,
            16777215, 0.45f, 0.3f, 2, 3, Family.TAIGA, null, Spawns.TAIGA_LIKE),
    /** Id 32 (TaigaBiome, variant 1): the mega band (0.3/0.8). */
    MEGA_TAIGA(32, "Mega Taiga", 0.3f, 0.8f, false, true, 16777215,
            0.2f, 0.2f, 2, 3, Family.TAIGA, null, Spawns.TAIGA_LIKE),
    /** Id 33 (TaigaBiome, variant 1): the hills height band. */
    MEGA_TAIGA_HILLS(33, "Mega Taiga Hills", 0.3f, 0.8f, false, true,
            16777215, 0.45f, 0.3f, 2, 3, Family.TAIGA, null, Spawns.TAIGA_LIKE),
    /** Id 34 (ExtremeHillsBiome, variant plus): the gravel/stone switch. */
    EXTREME_HILLS_PLUS(34, "Extreme Hills+", 0.2f, 0.3f, false, true,
            16777215, 1.0f, 0.5f, 2, 3, Family.EXTREME_HILLS, null, Spawns.BASE),
    /** Id 35 (SavannaBiome): the no-rain refusal, the acacia band. */
    SAVANNA(35, "Savanna", 1.2f, 0.0f, false, false, 16777215,
            0.125f, 0.05f, 2, 3, Family.SAVANNA, null, Spawns.SAVANNA_LIKE),
    /** Id 36 (SavannaBiome): the plateau height band. */
    SAVANNA_PLATEAU(36, "Savanna Plateau", 1.0f, 0.0f, false, false,
            16777215, 1.5f, 0.025f, 2, 3, Family.SAVANNA, null, Spawns.SAVANNA_LIKE),
    /** Id 37 (MesaBiome, hasTrees=false): the red sand over clay. */
    MESA(37, "Mesa", 2.0f, 0.0f, false, false, 16777215,
            0.1f, 0.2f, 179, 159, Family.MESA, null, Spawns.NO_CREATURES),
    /** Id 38 (MesaBiome, hasTrees=true): the plateau band, the trees. */
    MESA_PLATEAU_F(38, "Mesa Plateau F", 2.0f, 0.0f, false, false, 16777215,
            1.5f, 0.025f, 179, 159, Family.MESA, null, Spawns.NO_CREATURES),
    /** Id 39 (MesaBiome, hasTrees=false): the plateau band. */
    MESA_PLATEAU(39, "Mesa Plateau", 2.0f, 0.0f, false, false, 16777215,
            1.5f, 0.025f, 179, 159, Family.MESA, null, Spawns.NO_CREATURES),

    // ------------------------------------------------------- the mutated rows
    // (the static block's 21 registrations — see the class javadoc's
    // mutation law; every row names the base it mutates)

    /** Id 129 (PlainsBiome's own mutate): the fresh-ctor form — no bump. */
    SUNFLOWER_PLAINS(129, "Sunflower Plains", 0.8f, 0.4f, false, true,
            16777215, 0.125f, 0.05f, 2, 3, Family.PLAINS, PLAINS, Spawns.PLAINS_LIKE),
    /** Id 130 (the standard copy): the desert's whole pair + the bump. */
    DESERT_M(130, "Desert M", 2.0f, 0.0f, false, false, 16777215,
            0.225f, 0.25f, 12, 12, Family.DESERT, DESERT, Spawns.NO_CREATURES),
    /** Id 131 (ExtremeHillsBiome's own mutate): the copied height, no bump. */
    EXTREME_HILLS_M(131, "Extreme Hills M", 0.2f, 0.3f, false, true,
            16777215, 1.0f, 0.5f, 2, 3, Family.EXTREME_HILLS, EXTREME_HILLS, Spawns.BASE),
    /** Id 132 (ForestBiome's own mutate — the fresh variant-1 ctor):
     * "Flower Forest", the forest's own base + the variation bump. */
    FLOWER_FOREST(132, "Flower Forest", 0.7f, 0.8f, false, true,
            16777215, 0.1f, 0.4f, 2, 3, Family.FOREST, FOREST, Spawns.BASE),
    /** Id 133 (the standard copy): the taiga pair + the bump. */
    TAIGA_M(133, "Taiga M", 0.25f, 0.8f, false, true, 16777215,
            0.3f, 0.4f, 2, 3, Family.TAIGA, TAIGA, Spawns.TAIGA_LIKE),
    /** Id 134 (the standard copy): the water fog row rides along. */
    SWAMPLAND_M(134, "Swampland M", 0.8f, 0.9f, false, true, 14745518,
            -0.1f, 0.3f, 2, 3, Family.SWAMP, SWAMPLAND, Spawns.SWAMP),
    /** Id 140 (IceBiome's own mutate): snow surface, the +0.3/+0.4 quirk. */
    ICE_PLAINS_SPIKES(140, "Ice Plains Spikes", 0.0f, 0.5f, true, true,
            16777215, 0.425f, 0.45f, 80, 3, Family.ICE, ICE_PLAINS, Spawns.NO_CREATURES),
    /** Id 149 (the standard copy): the jungle pair + the bump. */
    JUNGLE_M(149, "Jungle M", 0.95f, 0.9f, false, true, 16777215,
            0.2f, 0.4f, 2, 3, Family.JUNGLE, JUNGLE, Spawns.JUNGLE),
    /** Id 151 (the standard copy): the edge's 0.8 downfall + the bump. */
    JUNGLE_EDGE_M(151, "JungleEdge M", 0.95f, 0.8f, false, true, 16777215,
            0.2f, 0.4f, 2, 3, Family.JUNGLE, JUNGLE_EDGE, Spawns.JUNGLE_EDGE),
    /** Id 155 (the standard copy): the birch pair + the bump. */
    BIRCH_FOREST_M(155, "Birch Forest M", 0.6f, 0.6f, false, true,
            16777215, 0.2f, 0.4f, 2, 3, Family.FOREST, BIRCH_FOREST, Spawns.BASE),
    /** Id 156 (the standard copy): the birch hills' bump over the hills. */
    BIRCH_FOREST_HILLS_M(156, "Birch Forest Hills M", 0.6f, 0.6f, false,
            true, 16777215, 0.55f, 0.5f, 2, 3, Family.FOREST,
            BIRCH_FOREST_HILLS, Spawns.BASE),
    /** Id 157 (the standard copy): the roofed pair + the bump. */
    ROOFED_FOREST_M(157, "Roofed Forest M", 0.7f, 0.8f, false, true,
            16777215, 0.2f, 0.4f, 2, 3, Family.FOREST, ROOFED_FOREST, Spawns.BASE),
    /** Id 158 (the standard copy): the snowy flag rides along. */
    COLD_TAIGA_M(158, "Cold Taiga M", -0.5f, 0.4f, true, true, 16777215,
            0.55f, 0.5f, 2, 3, Family.TAIGA, COLD_TAIGA, Spawns.TAIGA_LIKE),
    /** Id 160 (TaigaBiome's own mutate): variant 2, the 0.25 band, no bump. */
    MEGA_SPRUCE_TAIGA(160, "Mega Spruce Taiga", 0.25f, 0.8f, false, true,
            16777215, 0.2f, 0.2f, 2, 3, Family.TAIGA, MEGA_TAIGA, Spawns.TAIGA_LIKE),
    /** Id 161 (the special second call): the same fresh TaigaBiome(id, 2)
     * shape as 160 — MEGA_TAIGA's own pair, renamed. */
    REDWOOD_TAIGA_HILLS_M(161, "Redwood Taiga Hills M", 0.25f, 0.8f,
            false, true, 16777215, 0.2f, 0.2f, 2, 3, Family.TAIGA,
            MEGA_TAIGA, Spawns.TAIGA_LIKE),
    /** Id 162 (ExtremeHillsBiome's own mutate): the plus row's copy. */
    EXTREME_HILLS_PLUS_M(162, "Extreme Hills+ M", 0.2f, 0.3f, false, true,
            16777215, 1.0f, 0.5f, 2, 3, Family.EXTREME_HILLS,
            EXTREME_HILLS_PLUS, Spawns.BASE),
    /** Id 163 (SavannaBiome's own mutate): the shattered law — 1.1, the
     *  {@code base * 0.5 + 0.3} / {@code variation * 0.5 + 1.2} heights. */
    SAVANNA_M(163, "Savanna M", 1.1f, 0.0f, false, false, 16777215,
            0.3625f, 1.225f, 2, 3, Family.SAVANNA, SAVANNA, Spawns.SAVANNA_LIKE),
    /** Id 164 (SavannaBiome's own mutate): the plateau's shattered form. */
    SAVANNA_PLATEAU_M(164, "Savanna Plateau M", 1.0f, 0.0f, false, false,
            16777215, 1.05f, 1.2125f, 2, 3, Family.SAVANNA, SAVANNA_PLATEAU, Spawns.SAVANNA_LIKE),
    /** Id 165 (MesaBiome's own mutate, the flag arm): "Mesa (Bryce)" —
     * the fresh ctor's class-default heights, no bump. */
    MESA_BRYCE(165, "Mesa (Bryce)", 2.0f, 0.0f, false, false, 16777215,
            0.1f, 0.2f, 179, 159, Family.MESA, MESA, Spawns.NO_CREATURES),
    /** Id 166 (the standard copy): the plateau F's bump. */
    MESA_PLATEAU_F_M(166, "Mesa Plateau F M", 2.0f, 0.0f, false, false,
            16777215, 0.45f, 0.3f, 179, 159, Family.MESA, MESA_PLATEAU_F, Spawns.NO_CREATURES),
    /** Id 167 (the standard copy): the plateau's bump. */
    MESA_PLATEAU_M(167, "Mesa Plateau M", 2.0f, 0.0f, false, false,
            16777215, 0.45f, 0.3f, 179, 159, Family.MESA, MESA_PLATEAU, Spawns.NO_CREATURES);

    // ------------------------------------------------------------ the datum
    // (the reference ctor's own registration rows, the exact values)

    private final int legacyId;
    private final String displayName;
    private final float temperature;
    private final float downfall;
    private final boolean snowy;
    private final boolean rainField;
    private final int waterFogColor;
    private final float baseHeight;
    private final float heightVariation;
    private final int surfaceLegacyId;
    private final int subsurfaceLegacyId;
    private final Family family;
    private final Biome mutatedFrom;
    private final SpawnTables spawns;

    Biome(int legacyId, String displayName, float temperature, float downfall,
            boolean snowy, boolean rainField, int waterFogColor,
            float baseHeight, float heightVariation,
            int surfaceLegacyId, int subsurfaceLegacyId,
            Family family, Biome mutatedFrom, SpawnTables spawns) {
        this.legacyId = legacyId;
        this.displayName = displayName;
        this.temperature = temperature;
        this.downfall = downfall;
        this.snowy = snowy;
        this.rainField = rainField;
        this.waterFogColor = waterFogColor;
        this.baseHeight = baseHeight;
        this.heightVariation = heightVariation;
        this.surfaceLegacyId = surfaceLegacyId;
        this.subsurfaceLegacyId = subsurfaceLegacyId;
        this.family = family;
        this.mutatedFrom = mutatedFrom;
        this.spawns = spawns;
    }

    /** @return the 1.8 wire id (the chunk packet's biome array value). */
    public int legacyId() {
        return legacyId;
    }

    /**
     * @return the reference's display name ({@code setName}'s own string —
     * "FrozenOcean" without the space, "Extreme Hills+" with the plus,
     * "Sunflower Plains" not "Plains M"): the key the all-biomes progress
     * set carries, byte-identical to a vanilla stats file's rows.
     */
    public String displayName() {
        return displayName;
    }

    /**
     * @return the registration temperature (the precipitation gate's
     * band input; the shaved position form is {@link #temperatureAt}).
     */
    public float temperature() {
        return temperature;
    }

    /**
     * The position-shaved temperature (the reference's
     * {@code getTemperature(BlockPos)} lines 413-420 1:1): above y 64 the
     * fixed-seed {@code TEMPERATURE_NOISE} ({@code new
     * PerlinSimplexNoise(new Random(1234L), 1)} — the FOLIAGE_NOISE 2345
     * sibling, world-seed independent) walks the value down
     * {@code temperature - (noise(x/8, z/8) * 4 + y - 64) * 0.05 / 30};
     * at or below 64 the registration value stands. The
     * {@code canFreeze}/{@code canSnowFall} gates ({@code > 0.15F}) read
     * this form.
     */
    public float temperatureAt(int x, int y, int z) {
        if (y > 64) {
            float f = (float) (TEMPERATURE_NOISE.getValue(
                    x * 1.0 / 8.0, z * 1.0 / 8.0) * 4.0);
            return temperature - (f + y - 64.0f) * 0.05f / 30.0f;
        }
        return temperature;
    }

    /** @return the registration downfall ({@code getDownfall}). */
    public float downfall() {
        return downfall;
    }

    /**
     * @return the {@code setSnowy} flag ({@code isSnowy}/{@code isCold}):
     * the frozen family's marker — the frozen ocean and river, the ice
     * plains pair, the cold beach, the cold taiga pair, the ice spikes.
     */
    public boolean snowy() {
        return snowy;
    }

    /**
     * The rain READ (the reference's {@code isRainy()} lines 393-395 —
     * {@code !isCold() && this.rainy}): a snowy biome NEVER reports rain
     * (the frozen family's own field stays true; the read refuses), and
     * the {@code disableRain} family (the deserts, hell, the end, the
     * savanna pair, the mesa family) refuses everywhere. The cauldron's
     * rain-fill gate reads this form.
     */
    public boolean rainy() {
        return !snowy && rainField;
    }

    /**
     * @return the humid consult ({@code isHumid} line 397-399): a
     * downfall above 0.85 — the swamp, the jungle pair, the mushroom
     * isle pair.
     */
    public boolean humid() {
        return downfall > 0.85f;
    }

    /**
     * @return the water fog color ({@code waterFogColor} — the swamp's
     * own 14745518 against the class default 16777215; the mutated swamp
     * copies it).
     */
    public int waterFogColor() {
        return waterFogColor;
    }

    /** @return the registration base height ({@code setHeight}'s first). */
    public float baseHeight() {
        return baseHeight;
    }

    /** @return the registration height variation (the second member). */
    public float heightVariation() {
        return heightVariation;
    }

    /**
     * @return the surface block's 1.8.8 wire id ({@code surfaceBlock} at
     * registration — the class default grass 2; the depth-dependent
     * switches, the mega taiga's podzol band among them, ledger with the
     * surface-builder slice).
     */
    public int surfaceLegacyId() {
        return surfaceLegacyId;
    }

    /**
     * @return the subsurface block's 1.8.8 wire id ({@code
     * subsurfaceBlock} at registration — the class default dirt 3).
     */
    public int subsurfaceLegacyId() {
        return subsurfaceLegacyId;
    }

    /**
     * @return the type family (the reference's {@code getType()} class
     * identity — {@code ForestBiome} covers the forest, birch and roofed
     * rows whole; the mutated forms resolve through their original).
     */
    public Family family() {
        return family;
    }

    /**
     * @return the base row this row mutates (null on every base row) —
     * the mutation relationship the static block's 21 calls record.
     */
    public Biome mutatedFrom() {
        return mutatedFrom;
    }

    /**
     * @return the monster table (the reference's {@code
     * getSpawnEntries(MobCategory.MONSTER)} — the natural cycle's
     * hostile consult): the seven class defaults, the swamp's second
     * slime, the jungle's ocelot; EMPTY on the isle, hell's own three,
     * the end's lone enderman.
     */
    public List<SpawnEntry> monsterEntries() {
        return spawns.monsters();
    }

    /**
     * @return the creature table ({@code getSpawnEntries(CREATURE)} —
     * the natural cycle's rare-cadence consult AND the worldgen
     * populate seeding's own read): the five farm animals, the plains'
     * horse, the savanna's horse, the forest's wolf, the taiga's wolf,
     * the jungle's second chicken, the isle's mooshroom; EMPTY on the
     * shore/desert/ice/mesa/ocean/river/stone families.
     */
    public List<SpawnEntry> creatureEntries() {
        return spawns.creatures();
    }

    /**
     * @return the water table ({@code getSpawnEntries(WATER_CREATURE)}
     * — the squid's own row; EMPTY on the isle and hell).
     */
    public List<SpawnEntry> waterEntries() {
        return spawns.water();
    }

    /**
     * @return the ambient table ({@code getSpawnEntries(AMBIENT)} — the
     * bat's own row; EMPTY on hell and the end).
     */
    public List<SpawnEntry> ambientEntries() {
        return spawns.ambient();
    }

    /**
     * The type-family consult (the reference's {@code is(Biome)} lines
     * 524-526: this, or the same {@code getType()} class — the mutated
     * forms resolve through their original, so {@code FOREST.is(FOREST_M)}
     * and {@code FOREST.is(BIRCH_FOREST)} both hold, the reference's own
     * law).
     */
    public boolean is(Biome other) {
        return this == other || (other != null && family == other.family);
    }

    /**
     * @return the temperature category ({@code getTemperatureCategory}
     * lines 528-534 — the ocean family's own OCEAN override, then
     * {@code < 0.2} COLD, {@code < 1.0} MEDIUM, else WARM; every mutated
     * row delegates to its original, and the two laws agree on all 61
     * rows — the savanna's 1.1 and the mega spruce's 0.25 both sit
     * inside their originals' bands).
     */
    public TemperatureCategory temperatureCategory() {
        if (family == Family.OCEAN) {
            return TemperatureCategory.OCEAN;
        }
        if (temperature < 0.2f) {
            return TemperatureCategory.COLD;
        }
        return temperature < 1.0f ? TemperatureCategory.MEDIUM
                : TemperatureCategory.WARM;
    }


    // -------------------------------------------------------- the spawn
    // tables (the reference's four lists: the class ctor's defaults,
    // the per-class clears and adds, the mutated rows' copies)

    /**
     * One spawn-table row (the reference's {@code Biome.SpawnEntry},
     * lines 613-627): the mob kind, the weight (the {@code
     * WeightedPicker} band), and the group-size pair. The LIST ORDER is
     * the registration order — the weighted pick walks the entries in
     * sequence, so a duplicated row (the jungle's second chicken, the
     * swamp's second slime) carries its weight twice, exactly the
     * reference's own shape.
     */
    public record SpawnEntry(
            net.zaminmc.torch.server.entity.MobType type,
            int weight, int minGroup, int maxGroup) {
    }

    /**
     * The biome's four spawn tables (the reference's {@code
     * monsterEntries}/{@code passiveEntries}/{@code waterEntries}/
     * {@code ambientEntries} — the ctor's own defaults at lines
     * 279-292, the subclasses' clears and adds). Immutable: a mutated
     * row shares its original's instance (the reference's {@code
     * MutatedBiome} copies the four lists into fresh mutables — the
     * same content, one shared table here).
     */
    public static final class SpawnTables {

        private final List<SpawnEntry> monsters;
        private final List<SpawnEntry> creatures;
        private final List<SpawnEntry> water;
        private final List<SpawnEntry> ambient;

        private SpawnTables(List<SpawnEntry> monsters,
                List<SpawnEntry> creatures, List<SpawnEntry> water,
                List<SpawnEntry> ambient) {
            this.monsters = List.copyOf(monsters);
            this.creatures = List.copyOf(creatures);
            this.water = List.copyOf(water);
            this.ambient = List.copyOf(ambient);
        }

        /**
         * The class defaults (Biome's ctor lines 279-292): the five
         * farm animals, the seven common hostiles, the squid, the bat.
         */
        public static SpawnTables base() {
            return new SpawnTables(
                    List.of(
                            new SpawnEntry(MobType.SPIDER, 100, 4, 4),
                            new SpawnEntry(MobType.ZOMBIE, 100, 4, 4),
                            new SpawnEntry(MobType.SKELETON, 100, 4, 4),
                            new SpawnEntry(MobType.CREEPER, 100, 4, 4),
                            new SpawnEntry(MobType.SLIME, 100, 4, 4),
                            new SpawnEntry(MobType.ENDERMAN, 10, 1, 4),
                            new SpawnEntry(MobType.WITCH, 5, 1, 1)),
                    List.of(
                            new SpawnEntry(MobType.SHEEP, 12, 4, 4),
                            new SpawnEntry(MobType.RABBIT, 10, 3, 3),
                            new SpawnEntry(MobType.PIG, 10, 4, 4),
                            new SpawnEntry(MobType.CHICKEN, 10, 4, 4),
                            new SpawnEntry(MobType.COW, 8, 4, 4)),
                    List.of(new SpawnEntry(MobType.SQUID, 10, 4, 4)),
                    List.of(new SpawnEntry(MobType.BAT, 10, 8, 8)));
        }

        /** The whole custom shape (the all-clears: hell, the end). */
        public static SpawnTables of(List<SpawnEntry> monsters,
                List<SpawnEntry> creatures, List<SpawnEntry> water,
                List<SpawnEntry> ambient) {
            return new SpawnTables(monsters, creatures, water, ambient);
        }

        /** {@code passiveEntries.clear()} — the shore/desert/ice/mesa
         *  family's own empty creature table. */
        public SpawnTables noCreatures() {
            return new SpawnTables(monsters, List.of(), water, ambient);
        }

        /** {@code passiveEntries.add} — the append (the jungle's second
         *  chicken rides the base row's side). */
        public SpawnTables withCreature(MobType type, int weight,
                int minGroup, int maxGroup) {
            List<SpawnEntry> grown = new ArrayList<>(creatures);
            grown.add(new SpawnEntry(type, weight, minGroup, maxGroup));
            return new SpawnTables(monsters, grown, water, ambient);
        }

        /** {@code monsterEntries.add} — the append (the swamp's second
         *  slime, the jungle's ocelot). */
        public SpawnTables withMonster(MobType type, int weight,
                int minGroup, int maxGroup) {
            List<SpawnEntry> grown = new ArrayList<>(monsters);
            grown.add(new SpawnEntry(type, weight, minGroup, maxGroup));
            return new SpawnTables(grown, creatures, water, ambient);
        }

        /** @return the monster table ({@code getSpawnEntries(MONSTER)}). */
        public List<SpawnEntry> monsters() {
            return monsters;
        }

        /** @return the creature table ({@code getSpawnEntries(CREATURE)}). */
        public List<SpawnEntry> creatures() {
            return creatures;
        }

        /** @return the water table ({@code getSpawnEntries(WATER_CREATURE)}). */
        public List<SpawnEntry> water() {
            return water;
        }

        /** @return the ambient table ({@code getSpawnEntries(AMBIENT)}). */
        public List<SpawnEntry> ambient() {
            return ambient;
        }
    }

    /**
     * The twelve distinct tables the 61 rows carry (the reference's
     * class-ctor outcomes): a holder class so the enum constants can
     * reference them at construction (the nested class initializes
     * lazily on first touch — before the first constant).
     */
    static final class Spawns {
        /** The class defaults (Biome's ctor). */
        static final SpawnTables BASE = SpawnTables.base();
        /** The cleared creature table (OceanBiome/RiverBiome/BeachBiome/
         *  DesertBiome/IceBiome/MesaBiome/StoneBeachBiome's clears). */
        static final SpawnTables NO_CREATURES = BASE.noCreatures();
        /** PlainsBiome's own add (the horse at 5, groups 2-6). */
        static final SpawnTables PLAINS_LIKE =
                BASE.withCreature(MobType.HORSE, 5, 2, 6);
        /** SavannaBiome's own add (the horse at 1, groups 2-6). */
        static final SpawnTables SAVANNA_LIKE =
                BASE.withCreature(MobType.HORSE, 1, 2, 6);
        /** ForestBiome variant 0 (the wolf at 5 — the forest and its
         *  hills; the birch/roofed/flower variants carry none). */
        static final SpawnTables FOREST_LIKE =
                BASE.withCreature(MobType.WOLF, 5, 4, 4);
        /** TaigaBiome every variant (the wolf at 8 — the ten taiga
         *  rows whole, the mutated copies among them). */
        static final SpawnTables TAIGA_LIKE =
                BASE.withCreature(MobType.WOLF, 8, 4, 4);
        /** JungleBiome edge=false (the ocelot on the monster table at
         *  2/1-1 + the second chicken on the creature table). */
        static final SpawnTables JUNGLE = BASE
                .withMonster(MobType.OCELOT, 2, 1, 1)
                .withCreature(MobType.CHICKEN, 10, 4, 4);
        /** JungleBiome edge=true (the second chicken alone — no ocelot
         *  on the edge). */
        static final SpawnTables JUNGLE_EDGE =
                BASE.withCreature(MobType.CHICKEN, 10, 4, 4);
        /** SwampBiome (the second slime on the monster table, weight 1,
         *  alone). */
        static final SpawnTables SWAMP =
                BASE.withMonster(MobType.SLIME, 1, 1, 1);
        /** HellBiome (all four cleared; the three nether monsters). */
        static final SpawnTables HELL = SpawnTables.of(
                List.of(
                        new SpawnEntry(MobType.GHAST, 50, 4, 4),
                        new SpawnEntry(MobType.ZOMBIE_PIGMAN, 100, 4, 4),
                        new SpawnEntry(MobType.MAGMA_CUBE, 1, 4, 4)),
                List.of(), List.of(), List.of());
        /** TheEndBiome (all four cleared; the enderman alone). */
        static final SpawnTables THE_END = SpawnTables.of(
                List.of(new SpawnEntry(MobType.ENDERMAN, 10, 4, 4)),
                List.of(), List.of(), List.of());
        /** MushroomBiome (the monsters and the water cleared, the
         *  mooshroom's own creature row; the bat's ambient row stays). */
        static final SpawnTables MUSHROOM = SpawnTables.of(
                List.of(),
                List.of(new SpawnEntry(MobType.MOOSHROOM, 8, 4, 8)),
                List.of(),
                List.of(new SpawnEntry(MobType.BAT, 10, 8, 8)));
    }

    // ----------------------------------------------------------- the lookups
    // (the reference's own BY_ID/BY_NAME maps, the static block's census)

    /** The type families (the reference's per-class identities). */
    public enum Family {
        OCEAN, PLAINS, DESERT, EXTREME_HILLS, FOREST, TAIGA, SWAMP, RIVER,
        HELL, THE_END, ICE, MUSHROOM, BEACH, JUNGLE, STONE_BEACH, SAVANNA,
        MESA
    }

    /** The temperature categories (the reference's own enum shape). */
    public enum TemperatureCategory {
        OCEAN, COLD, MEDIUM, WARM
    }

    /**
     * The id lookup (the reference's {@code BY_ID} read): the row for a
     * 1.8 wire id, null on the gaps (the unregistered bands 40-127,
     * 135-139 and the rest — a vanilla client's biome array never
     * carries them).
     */
    public static Biome byLegacyId(int legacyId) {
        return byLegacyId(legacyId, null);
    }

    /**
     * The id lookup with a fallback (the reference's {@code byId(int,
     * Biome)} shape): the row, the fallback on a gap, and OCEAN on an
     * out-of-band id (the reference's own warn-and-default, the {@code
     * <=} off-by-one quirk tightened to the safe 0-255 band — the
     * reference's 256 would index out of bounds before the default ever
     * answered).
     */
    public static Biome byLegacyId(int legacyId, Biome fallback) {
        if (legacyId >= 0 && legacyId <= 255) {
            Biome row = BY_ID[legacyId];
            return row == null ? fallback : row;
        }
        return OCEAN;
    }

    /**
     * The name lookup (the reference's {@code BY_NAME} map): the row for
     * a display name, null on an unknown string. The duplicate-name
     * guard the static block pins at class init.
     */
    public static Biome byDisplayName(String name) {
        return BY_NAME.get(name);
    }

    /**
     * The all-biomes achievement's target band (the reference's
     * {@code EXPLORABLE} — the id&lt;128 census minus the four removes):
     * 36 overworld rows, HELL, THE_END, FROZEN_OCEAN and
     * EXTREME_HILLS_EDGE excluded by the reference's own removes, the
     * mutated ids never joining (all {@code >= 128}).
     */
    public static Biome[] explorable() {
        return EXPLORABLE.clone();
    }

    /**
     * @return the whole registration census in id order (61 rows — the
     * 40 base + the 21 mutated, the reference's own table).
     */
    public static Biome[] all() {
        return VALUES_CACHE.clone();
    }

    /** The id table (BY_ID's engine shape — 256 slots, the gaps null). */
    private static final Biome[] BY_ID = new Biome[256];
    /** The name table (BY_NAME's engine shape). */
    private static final Map<String, Biome> BY_NAME = new HashMap<>();
    /** The explorable census, resolved once at class init. */
    private static final Biome[] EXPLORABLE;
    /** The whole-table census in id order. */
    private static final Biome[] VALUES_CACHE;
    /**
     * The altitude shave's own field (the reference's line 594: the
     * fixed 1234 seed, one level — world-seed independent, identical
     * across every world; the FOLIAGE_NOISE 2345 sibling GrassBlocks
     * already carries).
     */
    private static final PerlinSimplexNoise TEMPERATURE_NOISE =
            new PerlinSimplexNoise(new Random(1234L), 1);

    static {
        VALUES_CACHE = values();
        for (Biome biome : VALUES_CACHE) {
            if (BY_ID[biome.legacyId] != null) {
                throw new IllegalStateException(
                        "Biome id " + biome.legacyId + " registered twice");
            }
            BY_ID[biome.legacyId] = biome;
            Biome prior = BY_NAME.put(biome.displayName, biome);
            if (prior != null) {
                throw new IllegalStateException("Biome \""
                        + biome.displayName + "\" is defined as both id "
                        + prior.legacyId + " and " + biome.legacyId);
            }
        }
        java.util.List<Biome> explorableRows = new java.util.ArrayList<>();
        for (Biome biome : VALUES_CACHE) {
            if (biome.legacyId < 128
                    && biome != HELL && biome != THE_END
                    && biome != FROZEN_OCEAN && biome != EXTREME_HILLS_EDGE) {
                explorableRows.add(biome);
            }
        }
        EXPLORABLE = explorableRows.toArray(new Biome[0]);
    }

    /**
     * The stable API name at the column (the lowercase enum name —
     * "plains", "ice_plains_spikes"; the display name's spaces never
     * ride the API's name surface).
     */
    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
