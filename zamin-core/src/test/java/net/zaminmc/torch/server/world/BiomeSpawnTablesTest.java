package net.zaminmc.torch.server.world;

import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.world.Biome.SpawnEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The biome spawn tables (the spawn-tables slice — the reference's four
 * lists over the 61-row registration): the class ctor's own defaults
 * (Biome lines 279-292), the per-class clears and adds (every biome
 * subclass's own rows), and the mutated rows' copies (MutatedBiome's
 * four-list copy, the fresh-ctor mutations' own tables). Every row is
 * the reference's own datum — the weights, the group bands, the
 * duplicated entries (the jungle's second chicken, the swamp's second
 * slime) and the clears (the shore/desert/ice/mesa/ocean/river/stone
 * families' empty creature tables).
 */
class BiomeSpawnTablesTest {

    // -------------------------------------------------------- the row shape

    private static SpawnEntry row(MobType type, int weight, int min, int max) {
        return new SpawnEntry(type, weight, min, max);
    }

    private static void assertTable(String what, List<SpawnEntry> expected,
                                    List<SpawnEntry> actual) {
        assertEquals(expected, actual, what);
    }

    // ------------------------------------------------------------ the tests

    /**
     * The class defaults (Biome's ctor lines 279-292): the five farm
     * animals, the seven common hostiles, the squid, the bat — the
     * table every biome without an override carries (the extreme hills
     * family, the birch/roofed/flower forest variants).
     */
    @Test
    void theClassDefaultsRideTheUnmodifiedRows() {
        List<SpawnEntry> creatures = List.of(
                row(MobType.SHEEP, 12, 4, 4),
                row(MobType.RABBIT, 10, 3, 3),
                row(MobType.PIG, 10, 4, 4),
                row(MobType.CHICKEN, 10, 4, 4),
                row(MobType.COW, 8, 4, 4));
        List<SpawnEntry> monsters = List.of(
                row(MobType.SPIDER, 100, 4, 4),
                row(MobType.ZOMBIE, 100, 4, 4),
                row(MobType.SKELETON, 100, 4, 4),
                row(MobType.CREEPER, 100, 4, 4),
                row(MobType.SLIME, 100, 4, 4),
                row(MobType.ENDERMAN, 10, 1, 4),
                row(MobType.WITCH, 5, 1, 1));
        List<SpawnEntry> water = List.of(row(MobType.SQUID, 10, 4, 4));
        List<SpawnEntry> ambient = List.of(row(MobType.BAT, 10, 8, 8));
        for (Biome biome : new Biome[] {
                Biome.EXTREME_HILLS, Biome.EXTREME_HILLS_EDGE,
                Biome.EXTREME_HILLS_PLUS, Biome.BIRCH_FOREST,
                Biome.BIRCH_FOREST_HILLS, Biome.ROOFED_FOREST,
                Biome.FLOWER_FOREST, Biome.EXTREME_HILLS_M,
                Biome.EXTREME_HILLS_PLUS_M, Biome.BIRCH_FOREST_M,
                Biome.BIRCH_FOREST_HILLS_M, Biome.ROOFED_FOREST_M}) {
            assertTable(biome + " creatures", creatures, biome.creatureEntries());
            assertTable(biome + " monsters", monsters, biome.monsterEntries());
            assertTable(biome + " water", water, biome.waterEntries());
            assertTable(biome + " ambient", ambient, biome.ambientEntries());
        }
    }

    /**
     * The cleared creature tables (the shore/desert/ice/mesa/ocean/
     * river/stone families — BeachBiome line 8, DesertBiome line 12,
     * IceBiome line 24, MesaBiome line 33, OceanBiome line 10,
     * RiverBiome line 6, StoneBeachBiome line 8): no passive ever
     * spawns there — the natural cycle's rare cadence refuses, and the
     * worldgen seeding never runs. The monsters/water/ambient stay the
     * class defaults.
     */
    @Test
    void theClearedFamiliesCarryNoCreatures() {
        for (Biome biome : new Biome[] {
                Biome.OCEAN, Biome.DESERT, Biome.RIVER, Biome.FROZEN_OCEAN,
                Biome.FROZEN_RIVER, Biome.ICE_PLAINS, Biome.ICE_MOUNTAINS,
                Biome.BEACH, Biome.DESERT_HILLS, Biome.DEEP_OCEAN,
                Biome.STONE_BEACH, Biome.COLD_BEACH, Biome.MESA,
                Biome.MESA_PLATEAU_F, Biome.MESA_PLATEAU,
                Biome.DESERT_M, Biome.ICE_PLAINS_SPIKES, Biome.MESA_BRYCE,
                Biome.MESA_PLATEAU_F_M, Biome.MESA_PLATEAU_M}) {
            assertTrue(biome.creatureEntries().isEmpty(),
                    biome + " carries no creature rows (the cleared table)");
            assertEquals(7, biome.monsterEntries().size(),
                    biome + " keeps the default monster table");
        }
    }

    /**
     * The per-family adds: the plains' horse at 5 (PlainsBiome line 17),
     * the savanna's horse at 1 (SavannaBiome line 19), the forest's
     * wolf at 5 (ForestBiome line 48, variant 0 only — the birch/roofed
     * variants never), the taiga's wolf at 8 (TaigaBiome line 37, every
     * variant).
     */
    @Test
    void theFamilyAddsRideTheirOwnRows() {
        // The plains family (the fresh-ctor sunflower plains included).
        for (Biome biome : new Biome[] {Biome.PLAINS, Biome.SUNFLOWER_PLAINS}) {
            assertEquals(6, biome.creatureEntries().size(), biome + " rows");
            assertEquals(row(MobType.HORSE, 5, 2, 6),
                    biome.creatureEntries().get(5), biome + " horse row");
        }
        // The savanna family (the shattered pair copies its original).
        for (Biome biome : new Biome[] {
                Biome.SAVANNA, Biome.SAVANNA_PLATEAU,
                Biome.SAVANNA_M, Biome.SAVANNA_PLATEAU_M}) {
            assertEquals(6, biome.creatureEntries().size(), biome + " rows");
            assertEquals(row(MobType.HORSE, 1, 2, 6),
                    biome.creatureEntries().get(5), biome + " horse row");
        }
        // The forest family's variant 0 (the forest + its hills + the
        // standard-copy 133... no — the taiga M copies the TAIGA; the
        // forest's own standard copy was 132's fresh flower ctor).
        for (Biome biome : new Biome[] {Biome.FOREST, Biome.FOREST_HILLS}) {
            assertEquals(6, biome.creatureEntries().size(), biome + " rows");
            assertEquals(row(MobType.WOLF, 5, 4, 4),
                    biome.creatureEntries().get(5), biome + " wolf row");
        }
        // The taiga family whole (all six variants + the copies: 133,
        // 158, the fresh 160/161 pair) — the wolf at 8 on every row.
        for (Biome biome : new Biome[] {
                Biome.TAIGA, Biome.TAIGA_HILLS, Biome.COLD_TAIGA,
                Biome.COLD_TAIGA_HILLS, Biome.MEGA_TAIGA,
                Biome.MEGA_TAIGA_HILLS, Biome.TAIGA_M, Biome.COLD_TAIGA_M,
                Biome.MEGA_SPRUCE_TAIGA, Biome.REDWOOD_TAIGA_HILLS_M}) {
            assertEquals(6, biome.creatureEntries().size(), biome + " rows");
            assertEquals(row(MobType.WOLF, 8, 4, 4),
                    biome.creatureEntries().get(5), biome + " wolf row");
        }
    }

    /**
     * The jungle's own rows (JungleBiome lines 48-52): the ocelot on
     * the MONSTER table at weight 2, groups 1-1 (the natural cycle's
     * hostile walk spawns it — the engine's own seeding-side stand-in
     * retired with this slice), and the SECOND chicken on the creature
     * table (the reference's own duplicate — the two entries carry
     * their weight twice, the list's own shape). The edge form (23)
     * carries the chicken but never the ocelot.
     */
    @Test
    void theJungleCarriesTheOcelotAndTheSecondChicken() {
        for (Biome biome : new Biome[] {
                Biome.JUNGLE, Biome.JUNGLE_HILLS, Biome.JUNGLE_M}) {
            List<SpawnEntry> creatures = biome.creatureEntries();
            assertEquals(6, creatures.size(), biome + " creature rows");
            int chickens = 0;
            for (SpawnEntry entry : creatures) {
                if (entry.type() == MobType.CHICKEN) {
                    chickens++;
                    assertEquals(10, entry.weight(), "the chicken's weight");
                }
            }
            assertEquals(2, chickens, biome + " carries the chicken twice");
            assertEquals(row(MobType.OCELOT, 2, 1, 1),
                    biome.monsterEntries().get(7), biome + " ocelot row");
        }
        // The edge form: the second chicken, never the ocelot.
        for (Biome biome : new Biome[] {Biome.JUNGLE_EDGE, Biome.JUNGLE_EDGE_M}) {
            assertEquals(6, biome.creatureEntries().size(), biome + " rows");
            assertEquals(7, biome.monsterEntries().size(),
                    biome + " never carries the ocelot");
        }
    }

    /**
     * The swamp's second slime (SwampBiome line 27): the monster table
     * carries the base's weight-100 row AND the swamp's own weight-1
     * row — the duplicate the reference's own list shape carries.
     */
    @Test
    void theSwampCarriesTheSecondSlime() {
        for (Biome biome : new Biome[] {Biome.SWAMPLAND, Biome.SWAMPLAND_M}) {
            assertEquals(8, biome.monsterEntries().size(), biome + " rows");
            assertEquals(row(MobType.SLIME, 1, 1, 1),
                    biome.monsterEntries().get(7), biome + " second slime");
        }
    }

    /**
     * The three all-clear families: HellBiome (lines 10-16 — the three
     * nether monsters alone), TheEndBiome (lines 10-14 — the enderman
     * alone), MushroomBiome (lines 15-18 — the mooshroom's own creature
     * row, the monsters and water cleared, the bat's ambient row kept).
     */
    @Test
    void theSpecialDimensionsCarryTheirOwnTables() {
        assertTable("hell monsters", List.of(
                row(MobType.GHAST, 50, 4, 4),
                row(MobType.ZOMBIE_PIGMAN, 100, 4, 4),
                row(MobType.MAGMA_CUBE, 1, 4, 4)),
                Biome.HELL.monsterEntries());
        assertTrue(Biome.HELL.creatureEntries().isEmpty(), "hell creatures");
        assertTrue(Biome.HELL.waterEntries().isEmpty(), "hell water");
        assertTrue(Biome.HELL.ambientEntries().isEmpty(), "hell ambient");

        assertTable("the end monsters",
                List.of(row(MobType.ENDERMAN, 10, 4, 4)),
                Biome.THE_END.monsterEntries());
        assertTrue(Biome.THE_END.creatureEntries().isEmpty(), "end creatures");
        assertTrue(Biome.THE_END.waterEntries().isEmpty(), "end water");
        assertTrue(Biome.THE_END.ambientEntries().isEmpty(), "end ambient");

        assertTable("the isle creatures",
                List.of(row(MobType.MOOSHROOM, 8, 4, 8)),
                Biome.MUSHROOM_ISLAND.creatureEntries());
        assertTrue(Biome.MUSHROOM_ISLAND.monsterEntries().isEmpty(),
                "isle monsters");
        assertTrue(Biome.MUSHROOM_ISLAND.waterEntries().isEmpty(),
                "isle water");
        assertTable("the isle ambient",
                List.of(row(MobType.BAT, 10, 8, 8)),
                Biome.MUSHROOM_ISLAND.ambientEntries());
        assertTable("the isle shore creatures",
                List.of(row(MobType.MOOSHROOM, 8, 4, 8)),
                Biome.MUSHROOM_ISLAND_SHORE.creatureEntries());
    }

    /**
     * The mutated copies' law (MutatedBiome lines 28-31: the four lists
     * copied whole): every standard-copy row answers its original's
     * exact tables — the desert M's clear, the taiga M's wolf, the
     * swamp M's slime, the jungle M's pair, the cold taiga M's wolf.
     */
    @Test
    void theMutatedCopiesShareTheirOriginalsTables() {
        assertEquals(Biome.DESERT.creatureEntries(),
                Biome.DESERT_M.creatureEntries());
        assertEquals(Biome.TAIGA.creatureEntries(),
                Biome.TAIGA_M.creatureEntries());
        assertEquals(Biome.SWAMPLAND.monsterEntries(),
                Biome.SWAMPLAND_M.monsterEntries());
        assertEquals(Biome.JUNGLE.monsterEntries(),
                Biome.JUNGLE_M.monsterEntries());
        assertEquals(Biome.JUNGLE.creatureEntries(),
                Biome.JUNGLE_M.creatureEntries());
        assertEquals(Biome.JUNGLE_EDGE.creatureEntries(),
                Biome.JUNGLE_EDGE_M.creatureEntries());
        assertEquals(Biome.COLD_TAIGA.creatureEntries(),
                Biome.COLD_TAIGA_M.creatureEntries());
        assertEquals(Biome.MESA.creatureEntries(),
                Biome.MESA_BRYCE.creatureEntries());
    }

    /**
     * The whole-census sanity: every row's tables are non-null and
     * immutable-shape (the List.copyOf contract — the registration
     * never leaks a mutable list).
     */
    @Test
    void everyRowCarriesNonNullTables() {
        for (Biome biome : Biome.values()) {
            assertTrue(biome.monsterEntries() != null
                    && biome.creatureEntries() != null
                    && biome.waterEntries() != null
                    && biome.ambientEntries() != null,
                    biome + " carries all four tables");
        }
    }
}
