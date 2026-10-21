package net.zaminmc.torch.server.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import net.zaminmc.torch.server.world.Biome.Family;
import net.zaminmc.torch.server.world.Biome.TemperatureCategory;
import net.zaminmc.torch.server.world.noise.PerlinSimplexNoise;
import org.junit.jupiter.api.Test;

/**
 * The biome registration table's contracts (VIII14's first slice — the
 * reference's Biome.java registration whole): the 61-row census (40 base
 * ids 0-39 + 21 mutated ids 129-167) with every registration datum — the
 * display names byte-exact (the all-biomes progress keys), the
 * temperature/downfall pairs, the snowy and rain-READ flags, the surface
 * pairs as 1.8.8 wire ids, the height pairs, the water fog row — plus the
 * mutation law's four custom shapes, the 36-row explorable census, the
 * fixed-seed altitude shave, and the lookup maps.
 */
class BiomeRegistrationTest {

    // --------------------------------------------------------- the row census

    /**
     * Every one of the 61 rows carries the reference's own registration
     * datum (the ctor chains + the static block's mutations): name,
     * temperature, downfall, the snow flag, the rain READ, the water fog
     * color, the height pair, the surface pair, the type family.
     */
    @Test
    void theWholeTableCarriesTheReferenceRegistration() {
        // The base rows (the registration order, lines 70-234).
        pin(0, "Ocean", 0.5f, 0.5f, false, true, 16777215,
                -1.0f, 0.1f, 2, 3, Family.OCEAN);
        pin(1, "Plains", 0.8f, 0.4f, false, true, 16777215,
                0.125f, 0.05f, 2, 3, Family.PLAINS);
        pin(2, "Desert", 2.0f, 0.0f, false, false, 16777215,
                0.125f, 0.05f, 12, 12, Family.DESERT);
        pin(3, "Extreme Hills", 0.2f, 0.3f, false, true, 16777215,
                1.0f, 0.5f, 2, 3, Family.EXTREME_HILLS);
        pin(4, "Forest", 0.7f, 0.8f, false, true, 16777215,
                0.1f, 0.2f, 2, 3, Family.FOREST);
        pin(5, "Taiga", 0.25f, 0.8f, false, true, 16777215,
                0.2f, 0.2f, 2, 3, Family.TAIGA);
        pin(6, "Swampland", 0.8f, 0.9f, false, true, 14745518,
                -0.2f, 0.1f, 2, 3, Family.SWAMP);
        pin(7, "River", 0.5f, 0.5f, false, true, 16777215,
                -0.5f, 0.0f, 2, 3, Family.RIVER);
        pin(8, "Hell", 2.0f, 0.0f, false, false, 16777215,
                0.1f, 0.2f, 2, 3, Family.HELL);
        pin(9, "The End", 0.5f, 0.5f, false, false, 16777215,
                0.1f, 0.2f, 3, 3, Family.THE_END);
        pin(10, "FrozenOcean", 0.0f, 0.5f, true, false, 16777215,
                -1.0f, 0.1f, 2, 3, Family.OCEAN);
        pin(11, "FrozenRiver", 0.0f, 0.5f, true, false, 16777215,
                -0.5f, 0.0f, 2, 3, Family.RIVER);
        pin(12, "Ice Plains", 0.0f, 0.5f, true, false, 16777215,
                0.125f, 0.05f, 2, 3, Family.ICE);
        pin(13, "Ice Mountains", 0.0f, 0.5f, true, false, 16777215,
                0.45f, 0.3f, 2, 3, Family.ICE);
        pin(14, "MushroomIsland", 0.9f, 1.0f, false, true, 16777215,
                0.2f, 0.3f, 110, 3, Family.MUSHROOM);
        pin(15, "MushroomIslandShore", 0.9f, 1.0f, false, true, 16777215,
                0.0f, 0.025f, 110, 3, Family.MUSHROOM);
        pin(16, "Beach", 0.8f, 0.4f, false, true, 16777215,
                0.0f, 0.025f, 12, 12, Family.BEACH);
        pin(17, "DesertHills", 2.0f, 0.0f, false, false, 16777215,
                0.45f, 0.3f, 12, 12, Family.DESERT);
        pin(18, "ForestHills", 0.7f, 0.8f, false, true, 16777215,
                0.45f, 0.3f, 2, 3, Family.FOREST);
        pin(19, "TaigaHills", 0.25f, 0.8f, false, true, 16777215,
                0.45f, 0.3f, 2, 3, Family.TAIGA);
        pin(20, "Extreme Hills Edge", 0.2f, 0.3f, false, true, 16777215,
                0.8f, 0.3f, 2, 3, Family.EXTREME_HILLS);
        pin(21, "Jungle", 0.95f, 0.9f, false, true, 16777215,
                0.1f, 0.2f, 2, 3, Family.JUNGLE);
        pin(22, "JungleHills", 0.95f, 0.9f, false, true, 16777215,
                0.45f, 0.3f, 2, 3, Family.JUNGLE);
        pin(23, "JungleEdge", 0.95f, 0.8f, false, true, 16777215,
                0.1f, 0.2f, 2, 3, Family.JUNGLE);
        pin(24, "Deep Ocean", 0.5f, 0.5f, false, true, 16777215,
                -1.8f, 0.1f, 2, 3, Family.OCEAN);
        pin(25, "Stone Beach", 0.2f, 0.3f, false, true, 16777215,
                0.1f, 0.8f, 1, 1, Family.STONE_BEACH);
        pin(26, "Cold Beach", 0.05f, 0.3f, true, false, 16777215,
                0.0f, 0.025f, 12, 12, Family.BEACH);
        pin(27, "Birch Forest", 0.6f, 0.6f, false, true, 16777215,
                0.1f, 0.2f, 2, 3, Family.FOREST);
        pin(28, "Birch Forest Hills", 0.6f, 0.6f, false, true, 16777215,
                0.45f, 0.3f, 2, 3, Family.FOREST);
        pin(29, "Roofed Forest", 0.7f, 0.8f, false, true, 16777215,
                0.1f, 0.2f, 2, 3, Family.FOREST);
        pin(30, "Cold Taiga", -0.5f, 0.4f, true, false, 16777215,
                0.2f, 0.2f, 2, 3, Family.TAIGA);
        pin(31, "Cold Taiga Hills", -0.5f, 0.4f, true, false, 16777215,
                0.45f, 0.3f, 2, 3, Family.TAIGA);
        pin(32, "Mega Taiga", 0.3f, 0.8f, false, true, 16777215,
                0.2f, 0.2f, 2, 3, Family.TAIGA);
        pin(33, "Mega Taiga Hills", 0.3f, 0.8f, false, true, 16777215,
                0.45f, 0.3f, 2, 3, Family.TAIGA);
        pin(34, "Extreme Hills+", 0.2f, 0.3f, false, true, 16777215,
                1.0f, 0.5f, 2, 3, Family.EXTREME_HILLS);
        pin(35, "Savanna", 1.2f, 0.0f, false, false, 16777215,
                0.125f, 0.05f, 2, 3, Family.SAVANNA);
        pin(36, "Savanna Plateau", 1.0f, 0.0f, false, false, 16777215,
                1.5f, 0.025f, 2, 3, Family.SAVANNA);
        pin(37, "Mesa", 2.0f, 0.0f, false, false, 16777215,
                0.1f, 0.2f, 179, 159, Family.MESA);
        pin(38, "Mesa Plateau F", 2.0f, 0.0f, false, false, 16777215,
                1.5f, 0.025f, 179, 159, Family.MESA);
        pin(39, "Mesa Plateau", 2.0f, 0.0f, false, false, 16777215,
                1.5f, 0.025f, 179, 159, Family.MESA);
        // The mutated rows (the static block's 21 registrations).
        pin(129, "Sunflower Plains", 0.8f, 0.4f, false, true, 16777215,
                0.125f, 0.05f, 2, 3, Family.PLAINS);
        pin(130, "Desert M", 2.0f, 0.0f, false, false, 16777215,
                0.225f, 0.25f, 12, 12, Family.DESERT);
        pin(131, "Extreme Hills M", 0.2f, 0.3f, false, true, 16777215,
                1.0f, 0.5f, 2, 3, Family.EXTREME_HILLS);
        pin(132, "Flower Forest", 0.7f, 0.8f, false, true, 16777215,
                0.1f, 0.4f, 2, 3, Family.FOREST);
        pin(133, "Taiga M", 0.25f, 0.8f, false, true, 16777215,
                0.3f, 0.4f, 2, 3, Family.TAIGA);
        pin(134, "Swampland M", 0.8f, 0.9f, false, true, 14745518,
                -0.1f, 0.3f, 2, 3, Family.SWAMP);
        pin(140, "Ice Plains Spikes", 0.0f, 0.5f, true, false, 16777215,
                0.425f, 0.45f, 80, 3, Family.ICE);
        pin(149, "Jungle M", 0.95f, 0.9f, false, true, 16777215,
                0.2f, 0.4f, 2, 3, Family.JUNGLE);
        pin(151, "JungleEdge M", 0.95f, 0.8f, false, true, 16777215,
                0.2f, 0.4f, 2, 3, Family.JUNGLE);
        pin(155, "Birch Forest M", 0.6f, 0.6f, false, true, 16777215,
                0.2f, 0.4f, 2, 3, Family.FOREST);
        pin(156, "Birch Forest Hills M", 0.6f, 0.6f, false, true, 16777215,
                0.55f, 0.5f, 2, 3, Family.FOREST);
        pin(157, "Roofed Forest M", 0.7f, 0.8f, false, true, 16777215,
                0.2f, 0.4f, 2, 3, Family.FOREST);
        pin(158, "Cold Taiga M", -0.5f, 0.4f, true, false, 16777215,
                0.55f, 0.5f, 2, 3, Family.TAIGA);
        pin(160, "Mega Spruce Taiga", 0.25f, 0.8f, false, true, 16777215,
                0.2f, 0.2f, 2, 3, Family.TAIGA);
        pin(161, "Redwood Taiga Hills M", 0.25f, 0.8f, false, true, 16777215,
                0.2f, 0.2f, 2, 3, Family.TAIGA);
        pin(162, "Extreme Hills+ M", 0.2f, 0.3f, false, true, 16777215,
                1.0f, 0.5f, 2, 3, Family.EXTREME_HILLS);
        pin(163, "Savanna M", 1.1f, 0.0f, false, false, 16777215,
                0.3625f, 1.225f, 2, 3, Family.SAVANNA);
        pin(164, "Savanna Plateau M", 1.0f, 0.0f, false, false, 16777215,
                1.05f, 1.2125f, 2, 3, Family.SAVANNA);
        pin(165, "Mesa (Bryce)", 2.0f, 0.0f, false, false, 16777215,
                0.1f, 0.2f, 179, 159, Family.MESA);
        pin(166, "Mesa Plateau F M", 2.0f, 0.0f, false, false, 16777215,
                0.45f, 0.3f, 179, 159, Family.MESA);
        pin(167, "Mesa Plateau M", 2.0f, 0.0f, false, false, 16777215,
                0.45f, 0.3f, 179, 159, Family.MESA);
        // The census itself: 61 rows, the reference's own count.
        assertEquals(61, Biome.values().length, "the reference's 61 rows");
        assertEquals(61, Biome.all().length, "the whole-table census");
        int base = 0;
        int mutated = 0;
        for (Biome biome : Biome.values()) {
            if (biome.mutatedFrom() == null) {
                base++;
            } else {
                mutated++;
            }
        }
        assertEquals(40, base, "40 base rows");
        assertEquals(21, mutated, "21 mutated rows");
    }

    // -------------------------------------------------------- the mutation law

    /**
     * The static block's 21 mutations: every row names its base, the
     * standard copies carry the +0.1/+0.2 height bump, and the four
     * custom shapes (the sunflower plains' fresh ctor, the ice spikes'
     * overwritten heights, the mega spruce's variant-2 band, the savanna
     * pair's shattered laws) answer their own registration rows.
     */
    @Test
    void theMutatedRowsCarryTheMutationLaw() {
        // The base naming (every mutated row resolves its original).
        assertEquals(Biome.PLAINS, Biome.SUNFLOWER_PLAINS.mutatedFrom());
        assertEquals(Biome.DESERT, Biome.DESERT_M.mutatedFrom());
        assertEquals(Biome.EXTREME_HILLS, Biome.EXTREME_HILLS_M.mutatedFrom());
        assertEquals(Biome.FOREST, Biome.FLOWER_FOREST.mutatedFrom());
        assertEquals(Biome.TAIGA, Biome.TAIGA_M.mutatedFrom());
        assertEquals(Biome.SWAMPLAND, Biome.SWAMPLAND_M.mutatedFrom());
        assertEquals(Biome.ICE_PLAINS, Biome.ICE_PLAINS_SPIKES.mutatedFrom());
        assertEquals(Biome.JUNGLE, Biome.JUNGLE_M.mutatedFrom());
        assertEquals(Biome.JUNGLE_EDGE, Biome.JUNGLE_EDGE_M.mutatedFrom());
        assertEquals(Biome.BIRCH_FOREST, Biome.BIRCH_FOREST_M.mutatedFrom());
        assertEquals(Biome.BIRCH_FOREST_HILLS,
                Biome.BIRCH_FOREST_HILLS_M.mutatedFrom());
        assertEquals(Biome.ROOFED_FOREST, Biome.ROOFED_FOREST_M.mutatedFrom());
        assertEquals(Biome.COLD_TAIGA, Biome.COLD_TAIGA_M.mutatedFrom());
        assertEquals(Biome.MEGA_TAIGA, Biome.MEGA_SPRUCE_TAIGA.mutatedFrom());
        assertEquals(Biome.MEGA_TAIGA,
                Biome.REDWOOD_TAIGA_HILLS_M.mutatedFrom());
        assertEquals(Biome.EXTREME_HILLS_PLUS,
                Biome.EXTREME_HILLS_PLUS_M.mutatedFrom());
        assertEquals(Biome.SAVANNA, Biome.SAVANNA_M.mutatedFrom());
        assertEquals(Biome.SAVANNA_PLATEAU,
                Biome.SAVANNA_PLATEAU_M.mutatedFrom());
        assertEquals(Biome.MESA, Biome.MESA_BRYCE.mutatedFrom());
        assertEquals(Biome.MESA_PLATEAU_F,
                Biome.MESA_PLATEAU_F_M.mutatedFrom());
        assertEquals(Biome.MESA_PLATEAU, Biome.MESA_PLATEAU_M.mutatedFrom());
        // The standard bump (MutatedBiome lines 36-37): +0.1 base, +0.2
        // variation over the original.
        assertEquals(Biome.DESERT.baseHeight() + 0.1f,
                Biome.DESERT_M.baseHeight(), 1.0e-6f);
        assertEquals(Biome.DESERT.heightVariation() + 0.2f,
                Biome.DESERT_M.heightVariation(), 1.0e-6f);
        assertEquals(Biome.BIRCH_FOREST_HILLS.baseHeight() + 0.1f,
                Biome.BIRCH_FOREST_HILLS_M.baseHeight(), 1.0e-6f);
        assertEquals(Biome.BIRCH_FOREST_HILLS.heightVariation() + 0.2f,
                Biome.BIRCH_FOREST_HILLS_M.heightVariation(), 1.0e-6f);
        // The fresh-ctor sunflower plains: NO bump (PlainsBiome's own
        // mutate — the registration pair whole).
        assertEquals(Biome.PLAINS.baseHeight(),
                Biome.SUNFLOWER_PLAINS.baseHeight(), 1.0e-6f);
        assertEquals(Biome.PLAINS.heightVariation(),
                Biome.SUNFLOWER_PLAINS.heightVariation(), 1.0e-6f);
        // The ice spikes' overwritten heights (the +0.3/+0.4 pair that
        // replaces the setHeight call's own +0.1/+0.1 — the reference's
        // dead-write quirk).
        assertEquals(Biome.ICE_PLAINS.baseHeight() + 0.3f,
                Biome.ICE_PLAINS_SPIKES.baseHeight(), 1.0e-6f);
        assertEquals(Biome.ICE_PLAINS.heightVariation() + 0.4f,
                Biome.ICE_PLAINS_SPIKES.heightVariation(), 1.0e-6f);
        assertEquals(80, Biome.ICE_PLAINS_SPIKES.surfaceLegacyId(),
                "the spikes' snow surface (IceBiome's spikes=true ctor)");
        // The mega spruce: the fresh variant-2 band (0.25 against the
        // base's 0.3), the base's own heights (no bump).
        assertEquals(0.25f, Biome.MEGA_SPRUCE_TAIGA.temperature());
        assertEquals(Biome.MEGA_TAIGA.baseHeight(),
                Biome.MEGA_SPRUCE_TAIGA.baseHeight(), 1.0e-6f);
        // The shattered savanna pair: the (t + 1) * 0.5 temperature law
        // and the half-and-offset height law.
        assertEquals((Biome.SAVANNA.temperature() + 1.0f) * 0.5f,
                Biome.SAVANNA_M.temperature(), 1.0e-6f);
        assertEquals(Biome.SAVANNA.baseHeight() * 0.5f + 0.3f,
                Biome.SAVANNA_M.baseHeight(), 1.0e-6f);
        assertEquals(Biome.SAVANNA.heightVariation() * 0.5f + 1.2f,
                Biome.SAVANNA_M.heightVariation(), 1.0e-6f);
        assertEquals((Biome.SAVANNA_PLATEAU.temperature() + 1.0f) * 0.5f,
                Biome.SAVANNA_PLATEAU_M.temperature(), 1.0e-6f);
        // The extreme hills pair: the copied height (no bump — the
        // private mutate's setHeight(original) law).
        assertEquals(Biome.EXTREME_HILLS.baseHeight(),
                Biome.EXTREME_HILLS_M.baseHeight(), 1.0e-6f);
        assertEquals(Biome.EXTREME_HILLS_PLUS.heightVariation(),
                Biome.EXTREME_HILLS_PLUS_M.heightVariation(), 1.0e-6f);
        // The standard copies carry the whole registration pair.
        assertEquals(Biome.COLD_TAIGA.snowy(), Biome.COLD_TAIGA_M.snowy());
        assertEquals(Biome.SWAMPLAND.waterFogColor(),
                Biome.SWAMPLAND_M.waterFogColor());
        assertEquals(Biome.MESA.surfaceLegacyId(), Biome.MESA_BRYCE.surfaceLegacyId());
        assertEquals(Biome.MESA.subsurfaceLegacyId(),
                Biome.MESA_BRYCE.subsurfaceLegacyId());
    }

    // ------------------------------------------------------ the explorable band

    /**
     * The all-biomes achievement's target set (the reference's EXPLORABLE:
     * every id below 128 minus HELL, THE_END, FROZEN_OCEAN and
     * EXTREME_HILLS_EDGE): exactly 36 rows, no mutated id ever joining.
     */
    @Test
    void theExplorableBandCarriesTheReferenceCensus() {
        Biome[] band = Biome.explorable();
        assertEquals(36, band.length, "the reference's 36-row census");
        for (Biome biome : band) {
            assertTrue(biome.legacyId() < 128,
                    "no mutated id joins the band: " + biome.displayName());
        }
        for (Biome removed : new Biome[]{
                Biome.HELL, Biome.THE_END, Biome.FROZEN_OCEAN,
                Biome.EXTREME_HILLS_EDGE}) {
            boolean present = false;
            for (Biome biome : band) {
                present |= biome == removed;
            }
            assertFalse(present,
                    "the reference's own remove: " + removed.displayName());
        }
        // The grown rows the engine's band never carried (the shading
        // subset's six against the census's 36 — the new thirty all
        // answer their display names).
        for (String grown : new String[]{"Taiga", "Swampland", "River",
                "FrozenRiver", "Ice Plains", "MushroomIsland", "Beach",
                "Stone Beach", "Birch Forest", "Roofed Forest", "Cold Taiga",
                "Mega Taiga", "Savanna", "Mesa", "Extreme Hills+"}) {
            boolean present = false;
            for (Biome biome : band) {
                present |= biome.displayName().equals(grown);
            }
            assertTrue(present, "the grown row joins the census: " + grown);
        }
    }

    // ---------------------------------------------------------- the read laws

    /**
     * The rain READ (isRainy lines 393-395): {@code !isCold() && rainy} —
     * the frozen family's field stays true but the read refuses (the
     * class's own law), and the disableRain family refuses everywhere.
     */
    @Test
    void theRainReadRefusesTheFrozenAndDesertFamilies() {
        // The frozen family: snowy, and the read never rains.
        for (Biome frozen : new Biome[]{Biome.FROZEN_OCEAN,
                Biome.FROZEN_RIVER, Biome.ICE_PLAINS, Biome.ICE_MOUNTAINS,
                Biome.COLD_BEACH, Biome.COLD_TAIGA, Biome.COLD_TAIGA_HILLS,
                Biome.ICE_PLAINS_SPIKES, Biome.COLD_TAIGA_M}) {
            assertTrue(frozen.snowy(), "the frozen marker: " + frozen);
            assertFalse(frozen.rainy(),
                    "the frozen read refuses the rain: " + frozen);
        }
        // The disableRain family.
        for (Biome dry : new Biome[]{Biome.DESERT, Biome.DESERT_HILLS,
                Biome.HELL, Biome.THE_END, Biome.SAVANNA,
                Biome.SAVANNA_PLATEAU, Biome.MESA, Biome.MESA_PLATEAU_F,
                Biome.MESA_PLATEAU, Biome.DESERT_M, Biome.SAVANNA_M,
                Biome.SAVANNA_PLATEAU_M, Biome.MESA_BRYCE, Biome.MESA_PLATEAU_F_M,
                Biome.MESA_PLATEAU_M}) {
            assertFalse(dry.snowy(), "the dry family is never snowy: " + dry);
            assertFalse(dry.rainy(), "the disableRain refusal: " + dry);
        }
        // The temperate majority.
        for (Biome temperate : new Biome[]{Biome.OCEAN, Biome.PLAINS,
                Biome.FOREST, Biome.TAIGA, Biome.SWAMPLAND, Biome.RIVER,
                Biome.JUNGLE, Biome.DEEP_OCEAN, Biome.BIRCH_FOREST,
                Biome.MEGA_TAIGA}) {
            assertTrue(temperate.rainy(), "the temperate read: " + temperate);
        }
        // The humid consult (isHumid: downfall > 0.85).
        assertTrue(Biome.SWAMPLAND.humid());
        assertTrue(Biome.JUNGLE.humid());
        assertTrue(Biome.MUSHROOM_ISLAND.humid());
        assertFalse(Biome.PLAINS.humid());
        assertFalse(Biome.DESERT.humid());
    }

    /**
     * The altitude shave (getTemperature lines 413-420): at or below y 64
     * the registration value stands; above it the fixed-seed noise walks
     * the value down the reference's own formula — the same 1234-seeded
     * field every world shares.
     */
    @Test
    void theAltitudeShaveWalksTheFixedSeedNoise() {
        // At and below 64: the registration value exact.
        for (Biome biome : new Biome[]{Biome.PLAINS, Biome.DESERT,
                Biome.COLD_TAIGA, Biome.SAVANNA_M}) {
            assertEquals(biome.temperature(), biome.temperatureAt(0, 64, 0),
                    0.0f, "y 64 is the registration value: "
                            + biome.displayName());
            assertEquals(biome.temperature(), biome.temperatureAt(37, 0, -11),
                    0.0f, "y 0 is the registration value: "
                            + biome.displayName());
        }
        // Above 64: the reference's own formula over the 1234-seeded
        // field (an independent instance of the same seed answers the
        // same walk — the fixed-seed determinism IS the law).
        PerlinSimplexNoise noise = new PerlinSimplexNoise(new Random(1234L), 1);
        for (int[] pos : new int[][]{{0, 65, 0}, {100, 80, -100},
                {-512, 129, 512}, {33, 71, 7}}) {
            float f = (float) (noise.getValue(pos[0] * 1.0 / 8.0,
                    pos[2] * 1.0 / 8.0) * 4.0);
            float expected = Biome.PLAINS.temperature()
                    - (f + pos[1] - 64.0f) * 0.05f / 30.0f;
            assertEquals(expected, Biome.PLAINS.temperatureAt(
                    pos[0], pos[1], pos[2]), 1.0e-6f,
                    "the shave formula at " + java.util.Arrays.toString(pos));
        }
        // Determinism: the same column asks twice, answers once.
        assertEquals(Biome.PLAINS.temperatureAt(64, 100, 64),
                Biome.PLAINS.temperatureAt(64, 100, 64), 0.0f);
        // The monotone band: every walk above 64 cools against the
        // registration value (the (y - 64) term dominates the noise's
        // bounded ±4 contribution — at the 10-block band the term alone
        // is 0.5 * 0.05 / 30... the walk cools at every height the noise
        // cannot overcome).
        assertTrue(Biome.DESERT.temperatureAt(0, 200, 0)
                < Biome.DESERT.temperature(),
                "the altitude cools the desert at y 200");
        assertTrue(Biome.PLAINS.temperatureAt(0, 300, 0)
                < Biome.PLAINS.temperatureAt(0, 100, 0),
                "higher cools further");
    }

    // ------------------------------------------------------------- the lookups

    /**
     * The lookup maps (BY_ID/BY_NAME): every id resolves, the gaps answer
     * null (or the fallback), the out-of-band ids answer OCEAN (the
     * reference's warn-and-default shape), the names resolve both ways.
     */
    @Test
    void theLookupsAnswerTheReferenceMaps() {
        // The id round trip.
        for (Biome biome : Biome.values()) {
            assertEquals(biome, Biome.byLegacyId(biome.legacyId()),
                    "the id round trip: " + biome.displayName());
        }
        // The gaps (the unregistered bands).
        assertNull(Biome.byLegacyId(40));
        assertNull(Biome.byLegacyId(127));
        assertNull(Biome.byLegacyId(128));
        assertNull(Biome.byLegacyId(135));
        assertNull(Biome.byLegacyId(168));
        assertNull(Biome.byLegacyId(255));
        assertEquals(Biome.PLAINS, Biome.byLegacyId(40, Biome.PLAINS),
                "the fallback answers a gap");
        // The out-of-band default (the reference's own OCEAN default).
        assertEquals(Biome.OCEAN, Biome.byLegacyId(256));
        assertEquals(Biome.OCEAN, Biome.byLegacyId(-1));
        // The name round trip.
        for (Biome biome : Biome.values()) {
            assertEquals(biome, Biome.byDisplayName(biome.displayName()),
                    "the name round trip: " + biome.displayName());
        }
        assertNull(Biome.byDisplayName("Nether"),
                "the reference's own spelling: Hell, never Nether");
        assertNull(Biome.byDisplayName("plains"),
                "the map keys on the display name, not the API name");
    }

    /**
     * The type-family consult (is(Biome) lines 524-526): the same-class
     * families answer each other — the forest trio (ForestBiome covers
     * the forest, birch and roofed rows whole), the mutated forms through
     * their originals, and no cross-family consult ever holds.
     */
    @Test
    void theTypeFamilyConsultCarriesTheReferenceLaw() {
        assertTrue(Biome.FOREST.is(Biome.FOREST));
        assertTrue(Biome.FOREST.is(Biome.FLOWER_FOREST),
                "the mutated forest resolves through its original");
        assertTrue(Biome.FOREST.is(Biome.BIRCH_FOREST),
                "ForestBiome covers the birch row — the reference's own law");
        assertTrue(Biome.BIRCH_FOREST.is(Biome.ROOFED_FOREST),
                "the roofed row rides the same ForestBiome family");
        assertTrue(Biome.SUNFLOWER_PLAINS.is(Biome.PLAINS),
                "the fresh-ctor mutation is still a PlainsBiome");
        assertTrue(Biome.MEGA_SPRUCE_TAIGA.is(Biome.MEGA_TAIGA),
                "the variant-2 taiga rides the TaigaBiome family");
        assertTrue(Biome.EXTREME_HILLS.is(Biome.EXTREME_HILLS_EDGE));
        assertTrue(Biome.SAVANNA_M.is(Biome.SAVANNA));
        assertFalse(Biome.PLAINS.is(Biome.FOREST), "no cross-family consult");
        assertTrue(Biome.PLAINS.is(Biome.SUNFLOWER_PLAINS),
                "plains family covers its mutation");
        assertFalse(Biome.DESERT.is(Biome.BEACH),
                "the sand surfaces do not join the families");
        assertFalse(Biome.FOREST.is(null), "the null refusal");
    }

    /**
     * The temperature categories (getTemperatureCategory lines 528-534):
     * the ocean family's own OCEAN override, then the COLD/MEDIUM/WARM
     * bands — and every mutated row's delegate agrees with its own
     * registration temperature's band (the two laws never disagree on
     * the reference's own rows).
     */
    @Test
    void theTemperatureCategoriesFollowTheReferenceBands() {
        assertEquals(TemperatureCategory.OCEAN, Biome.OCEAN.temperatureCategory());
        assertEquals(TemperatureCategory.OCEAN,
                Biome.FROZEN_OCEAN.temperatureCategory());
        assertEquals(TemperatureCategory.OCEAN,
                Biome.DEEP_OCEAN.temperatureCategory());
        assertEquals(TemperatureCategory.COLD, Biome.COLD_TAIGA.temperatureCategory());
        assertEquals(TemperatureCategory.COLD, Biome.ICE_PLAINS.temperatureCategory());
        assertEquals(TemperatureCategory.COLD, Biome.COLD_BEACH.temperatureCategory());
        assertEquals(TemperatureCategory.MEDIUM, Biome.PLAINS.temperatureCategory());
        assertEquals(TemperatureCategory.MEDIUM, Biome.TAIGA.temperatureCategory());
        assertEquals(TemperatureCategory.MEDIUM,
                Biome.MEGA_SPRUCE_TAIGA.temperatureCategory());
        assertEquals(TemperatureCategory.WARM, Biome.DESERT.temperatureCategory());
        assertEquals(TemperatureCategory.WARM, Biome.SAVANNA_M.temperatureCategory());
        assertEquals(TemperatureCategory.WARM, Biome.MESA.temperatureCategory());
        // The delegate agreement: every row's own band equals its
        // original's band (the mutation temperatures never cross).
        for (Biome biome : Biome.values()) {
            if (biome.mutatedFrom() != null) {
                assertEquals(biome.mutatedFrom().temperatureCategory(),
                        biome.temperatureCategory(),
                        "the delegate agreement: " + biome.displayName());
            }
        }
    }

    /** The row pin: every registration datum against the two lookups. */
    private void pin(int id, String displayName, float temperature,
            float downfall, boolean snowy, boolean rainy, int waterFogColor,
            float baseHeight, float heightVariation, int surfaceLegacyId,
            int subsurfaceLegacyId, Family family) {
        Biome biome = Biome.byLegacyId(id);
        assertEquals(id, biome.legacyId(), "the wire id");
        assertEquals(displayName, biome.displayName(), "the display name");
        assertEquals(Biome.byDisplayName(displayName), biome,
                "the name map resolves the row");
        assertEquals(temperature, biome.temperature(), 0.0f,
                displayName + "'s temperature");
        assertEquals(downfall, biome.downfall(), 0.0f,
                displayName + "'s downfall");
        assertEquals(snowy, biome.snowy(), displayName + "'s snow flag");
        assertEquals(rainy, biome.rainy(), displayName + "'s rain read");
        assertEquals(waterFogColor, biome.waterFogColor(),
                displayName + "'s water fog");
        assertEquals(baseHeight, biome.baseHeight(), 1.0e-6f,
                displayName + "'s base height");
        assertEquals(heightVariation, biome.heightVariation(), 1.0e-6f,
                displayName + "'s height variation");
        assertEquals(surfaceLegacyId, biome.surfaceLegacyId(),
                displayName + "'s surface id");
        assertEquals(subsurfaceLegacyId, biome.subsurfaceLegacyId(),
                displayName + "'s subsurface id");
        assertEquals(family, biome.family(), displayName + "'s family");
    }
}
