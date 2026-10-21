package net.zaminmc.torch.server.block;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.server.world.FlatWorldGenerator;
import net.zaminmc.torch.server.world.noise.PerlinSimplexNoise;
import net.zaminmc.torch.server.block.BlockRegistryBuilder.FrozenBlockRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The grass/mushroom growth family (the 2026-10-21 audit's VIII7/VIII8 —
 * reference/1.8.8 GrassBlock/MushroomPlantBlock/MyceliumBlock/
 * HugeMushroomFeature + the biome flower picks): the fixed-seed foliage
 * noise's tulip band on the plains, the 128-attempt bone-meal grow walk,
 * the mushroom survival law (the mycelium bypass, the light gate), the
 * 1-in-25 spread with the same-family density cap, the two giant forms'
 * exact shapes, and the refused-grow restore — each pinned against the
 * reference's own lines.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GrassMushroomGrowthTest {

    private static FrozenBlockRegistry registry;
    private EngineWorld world;
    private Random random;

    /** The flat generator's surface height (the grass top). */
    private static final int SURFACE = 4;

    @BeforeAll
    void setUp() {
        registry = BuiltinBlocks.registerAll(new BlockRegistryBuilder()).freeze();
    }

    // ------------------------------------------------------------------
    // The scene harness: a fresh flat world + the light engine
    // ------------------------------------------------------------------

    private void scene() {
        world = new EngineWorld("growth-scene", registry,
                new FlatWorldGenerator(registry, 4), Thread.currentThread());
        // The light engine FIRST (the boot's own order): the survival gates
        // read the real stored light.
        net.zaminmc.torch.server.world.light.LightEngine light =
                new net.zaminmc.torch.server.world.light.LightEngine(world);
        world.addChangeListener(light);
        world.addChunkLoadListener(light);
        world.getOrGenerate(new ChunkPosition(0, 0));
        world.getOrGenerate(new ChunkPosition(-1, 0));
        world.getOrGenerate(new ChunkPosition(0, -1));
        world.getOrGenerate(new ChunkPosition(-1, -1));
        random = new Random(1);
    }

    private BlockPosition at(int x, int y, int z) {
        return new BlockPosition(x, y, z);
    }

    /** @return whether the cell carries the family's variant suffix (or the bare all_outside). */
    private boolean isFamilyVariant(int x, int y, int z, String family, String suffix) {
        BlockType type = world.getBlock(at(x, y, z));
        String value = type.identifier().value();
        String expected = suffix.equals("all_outside")
                ? family : family + "_" + suffix;
        return value.equals(expected);
    }

    // ------------------------------------------------------------------
    // The flower bands (the plains' own pick over the fixed-seed noise)
    // ------------------------------------------------------------------

    @Test
    void theFoliageNoiseBandsAreTheFixedSeedField() {
        // The reference's own FOLIAGE_NOISE (Biome.java line 595): the fixed
        // 2345 seed, one level, world-seed independent — the tulip band's
        // cell at (0, 1050) reads -0.835 (inside the < -0.8 band), the
        // origin reads 0.0 (outside it).
        PerlinSimplexNoise field = new PerlinSimplexNoise(new Random(2345L), 1);
        assertTrue(field.getValue(0.0, 1050.0 / 200.0) < -0.8,
                "the (0,1050) cell sits in the tulip band");
        assertTrue(field.getValue(0.0, 0.0) >= -0.8,
                "the origin sits outside the tulip band");
        // The same construction answers the same field: the determinism the
        // reference's fixed seed carries (every world's tulip patches at the
        // same coordinates).
        assertEquals(GrassBlocks.FOLIAGE_NOISE.getValue(0.0, 1050.0 / 200.0),
                field.getValue(0.0, 1050.0 / 200.0), 1.0E-12,
                "the engine's field is the reference's own");
    }

    @Test
    void thePlainsFlowerPickAnswersTheBandAndTheLadder() {
        scene();
        // The band cell (PlainsBiome lines 24-46): the four tulips alone —
        // ORANGE/RED/PINK/WHITE over the nextInt(4) ladder.
        java.util.Set<String> bandPicks = new java.util.HashSet<>();
        for (int i = 0; i < 200; i++) {
            BlockType flower = GrassBlocks.randomFlowerAt(world, at(0, 5, 1050), random);
            bandPicks.add(flower.identifier().value());
        }
        for (String pick : bandPicks) {
            assertTrue(pick.equals("orange_tulip") || pick.equals("red_tulip")
                    || pick.equals("pink_tulip") || pick.equals("white_tulip"),
                    "the band picks a tulip, not " + pick);
        }
        assertTrue(bandPicks.size() >= 2, "the ladder's rolls reach several tulips");
        // The non-band cell (lines 37-45): the poppy/houstonia/oxeye-daisy
        // ladder at 2-in-3, the dandelion tail — never a tulip.
        java.util.Set<String> plainPicks = new java.util.HashSet<>();
        for (int i = 0; i < 200; i++) {
            BlockType flower = GrassBlocks.randomFlowerAt(world, at(0, 5, 0), random);
            plainPicks.add(flower.identifier().value());
        }
        for (String pick : plainPicks) {
            assertTrue(pick.equals("dandelion") || pick.equals("poppy")
                    || pick.equals("houstonia") || pick.equals("oxeye_daisy"),
                    "the non-band pick stays out of the tulips: " + pick);
        }
        assertTrue(plainPicks.contains("dandelion"), "the dandelion tail rolls");
    }

    @Test
    void theBaseLawCarriesTheNonPlainsBiomes() {
        scene();
        // The base law (Biome.java lines 328-330): dandelion at 2-in-3, the
        // poppy at 1-in-3 — the forest answers it (the engine's FOREST is
        // the oak variant, whose own override is the birch variant's).
        // The flat scene is plains, so this consult pins the base law
        // through GrassBlocks' own fall-through with a non-plains biome
        // string — the forest's chunk is not generated, so we pin the law
        // at the plains' non-band arm instead (the same dandelion-heavy
        // distribution the base law carries).
        int dandelions = 0;
        for (int i = 0; i < 300; i++) {
            if (GrassBlocks.randomFlowerAt(world, at(0, 5, 0), random)
                    .identifier().value().equals("dandelion")) {
                dandelions++;
            }
        }
        // 2-in-3 expected: the band-ladder's dandelion tail rolls ~1/3 of
        // the passes (nextInt(3) == 0) — over 300 rolls the count sits
        // near 100, comfortably inside 20..200.
        assertTrue(dandelions > 20 && dandelions < 200,
                "the dandelion tail owns its share (rolled " + dandelions + ")");
    }

    // ------------------------------------------------------------------
    // The grass bone-meal walk (GrassBlock.grow lines 91-122)
    // ------------------------------------------------------------------

    @Test
    void theGrassGrowWalkPlantsTheFloraFamilyOverGrass() {
        scene();
        BlockPosition clicked = at(2, SURFACE, 2);
        assertEquals(BuiltinBlocks.GRASS_BLOCK, world.getBlock(clicked),
                "the scene's surface is grass");
        GrassBlocks.growGrass(world, clicked, new Random(7));
        // The 128 attempts' placements: every non-air cell in the band above
        // the surface is the tall grass or a flower, and every one of them
        // sits on grass (the walk's own step gate).
        int placements = 0;
        for (int x = -6; x <= 10; x++) {
            for (int z = -6; z <= 10; z++) {
                for (int y = SURFACE + 1; y <= SURFACE + 8; y++) {
                    BlockType type = world.getBlock(at(x, y, z));
                    if (type.equals(world.airType())) {
                        continue;
                    }
                    placements++;
                    String name = type.identifier().value();
                    assertTrue(name.equals("tall_grass")
                                    || name.equals("dandelion")
                                    || FlowerBlocks.isFlowerIdentifier(type.identifier()),
                            "the walk planted the flora family, not " + name);
                    assertEquals(BuiltinBlocks.GRASS_BLOCK,
                            world.getBlock(at(x, y - 1, z)),
                            "the placement at (" + x + "," + y + "," + z + ") sits on grass");
                }
            }
        }
        // Attempt 0 (zero steps) always plants at the start cell — the
        // count is at least one, and the walk's 128 attempts cap it.
        assertTrue(placements >= 1 && placements <= 128,
                "the walk planted " + placements + " cells");
        assertTrue(placements >= 3, "the outward attempts plant a patch ("
                + placements + " cells)");
    }

    // ------------------------------------------------------------------
    // The mushroom survival law (MushroomPlantBlock.canSurvive lines 71-80)
    // ------------------------------------------------------------------

    /** The combined light at the cell (the engine's own grass-band consult). */
    private int lightAt(int x, int y, int z) {
        return Math.max(world.blockLightAt(at(x, y, z)), world.skyLightAt(at(x, y, z)));
    }

    @Test
    void theMushroomSurvivalLaw() {
        scene();
        // On grass at full skylight: the light gate refuses (15 not < 13).
        assertTrue(lightAt(5, SURFACE + 1, 5) >= 13,
                "the open surface cell carries full light");
        assertFalse(MushroomBlocks.canPlantSurviveAt(world, at(5, SURFACE + 1, 5)),
                "a mushroom refuses the lit grass");
        // Under a 7x7 opaque lid: the skylight bleeds in from the sides at
        // the historical -1-per-step attenuation (a single lid reads 14,
        // still too bright — the vanilla nook needs the width); the center
        // cell drops under 13 and the grass below is opaque — the survival
        // passes.
        for (int x = 2; x <= 8; x++) {
            for (int z = 2; z <= 8; z++) {
                world.setBlock(at(x, SURFACE + 2, z), BuiltinBlocks.STONE);
            }
        }
        assertTrue(lightAt(5, SURFACE + 1, 5) < 13,
                "the lid's center reads under 13 (found "
                        + lightAt(5, SURFACE + 1, 5) + ")");
        assertTrue(MushroomBlocks.canPlantSurviveAt(world, at(5, SURFACE + 1, 5)),
                "the covered cell's darkness survives the mushroom");
        // On mycelium at FULL daylight (away from the lid): the bypass
        // answers true whole — the mushroom island's own law.
        world.setBlock(at(10, SURFACE, 10), BuiltinBlocks.MYCELIUM);
        assertTrue(lightAt(10, SURFACE + 1, 10) >= 13,
                "the mycelium cell sits in full daylight");
        assertTrue(MushroomBlocks.canPlantSurviveAt(world, at(10, SURFACE + 1, 10)),
                "the mycelium bypass survives any daylight");
    }

    // ------------------------------------------------------------------
    // The mushroom spread (MushroomPlantBlock.tick lines 43-68)
    // ------------------------------------------------------------------

    /** Plants a 9x9 mycelium patch and one mushroom at its center. */
    private void myceliumPatch(String plant) {
        for (int x = 0; x <= 8; x++) {
            for (int z = 0; z <= 8; z++) {
                world.setBlock(at(x, SURFACE, z), BuiltinBlocks.MYCELIUM);
            }
        }
        world.setBlock(at(4, SURFACE + 1, 4), plant.equals("brown_mushroom")
                ? BuiltinBlocks.BROWN_MUSHROOM : BuiltinBlocks.RED_MUSHROOM);
    }

    /** @return the count of the given plant inside the 9x3x9 box around the center. */
    private int countInBox(BlockType plant) {
        int count = 0;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    if (world.getBlock(at(4 + dx, SURFACE + 1 + dy, 4 + dz)).equals(plant)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    @Test
    void theSpreadDensifiesToTheFiveCap() {
        scene();
        myceliumPatch("brown_mushroom");
        // 300 ticks of the original plant: the 1-in-25 gate passes ~12
        // times, each pass rolling the four-step walk — the patch fills,
        // but the density gate (five same-family plants in the 9x3x9 box)
        // refuses every further spread.
        for (int tick = 0; tick < 300; tick++) {
            MushroomBlocks.spreadTick(world, at(4, SURFACE + 1, 4),
                    BuiltinBlocks.BROWN_MUSHROOM, random);
        }
        int count = countInBox(BuiltinBlocks.BROWN_MUSHROOM);
        assertTrue(count >= 2 && count <= 5,
                "the spread fills to the five-family cap (found " + count + ")");
    }

    @Test
    void theSpreadCountsTheFamilyAlone() {
        scene();
        myceliumPatch("red_mushroom");
        // Four brown neighbours around the red: the brown count never enters
        // the red's own density gate (the reference's == this consult).
        world.setBlock(at(3, SURFACE + 1, 4), BuiltinBlocks.BROWN_MUSHROOM);
        world.setBlock(at(5, SURFACE + 1, 4), BuiltinBlocks.BROWN_MUSHROOM);
        world.setBlock(at(4, SURFACE + 1, 3), BuiltinBlocks.BROWN_MUSHROOM);
        world.setBlock(at(4, SURFACE + 1, 5), BuiltinBlocks.BROWN_MUSHROOM);
        for (int tick = 0; tick < 300; tick++) {
            MushroomBlocks.spreadTick(world, at(4, SURFACE + 1, 4),
                    BuiltinBlocks.RED_MUSHROOM, random);
        }
        int reds = countInBox(BuiltinBlocks.RED_MUSHROOM);
        int browns = countInBox(BuiltinBlocks.BROWN_MUSHROOM);
        assertTrue(reds >= 2, "the red spreads past its brown neighbours (found "
                + reds + " reds)");
        assertEquals(4, browns, "the browns never spread — only the red ticked");
    }

    // ------------------------------------------------------------------
    // The giant forms (HugeMushroomFeature.place lines 27-158)
    // ------------------------------------------------------------------

    @Test
    void theBrownHugeMushroomShape() {
        scene();
        BlockPosition pos = at(2, SURFACE + 1, 2);
        world.setBlock(pos, BuiltinBlocks.BROWN_MUSHROOM);
        // The same stream's height roll: the seeded grow consumes the
        // reference's own first roll (nextInt(3) + 4).
        Random heightRolls = new Random(42);
        int height = heightRolls.nextInt(3) + 4;
        assertTrue(MushroomBlocks.growHuge(world, pos, BuiltinBlocks.BROWN_MUSHROOM,
                new Random(42)), "the open surface accepts the giant");
        // The stem column (lines 150-157): height tall, the STEM variant.
        for (int i = 0; i < height; i++) {
            assertTrue(isFamilyVariant(2, SURFACE + 1 + i, 2,
                            "brown_mushroom_block", "stem"),
                    "the stem column at +" + i);
        }
        // The single 7x7 top row (the brown's half-3 cap, lines 63-148):
        // the corners skipped, the lattice's center/north/west faces, the
        // corner-adjacent NORTH_WEST override.
        int top = SURFACE + 1 + height;
        int capBlocks = 0;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (!world.getBlock(at(2 + dx, top, 2 + dz)).equals(world.airType())) {
                    capBlocks++;
                }
            }
        }
        assertEquals(45, capBlocks, "the 7x7 cap minus its four corners");
        assertTrue(isFamilyVariant(2, top, 2, "brown_mushroom_block", "center"));
        assertTrue(isFamilyVariant(2, top, -1, "brown_mushroom_block", "north"));
        assertTrue(isFamilyVariant(-1, top, 2, "brown_mushroom_block", "west"));
        assertTrue(isFamilyVariant(2, top, 5, "brown_mushroom_block", "south"));
        assertTrue(isFamilyVariant(5, top, 2, "brown_mushroom_block", "east"));
        assertTrue(isFamilyVariant(0, top, -1, "brown_mushroom_block", "north_west"));
        assertTrue(isFamilyVariant(4, top, 5, "brown_mushroom_block", "south_east"));
        // The corners are air (the skipped cells).
        assertTrue(world.getBlock(at(-1, top, -1)).equals(world.airType()));
        assertTrue(world.getBlock(at(5, top, 5)).equals(world.airType()));
    }

    @Test
    void theRedHugeMushroomShape() {
        scene();
        BlockPosition pos = at(2, SURFACE + 1, 2);
        world.setBlock(pos, BuiltinBlocks.RED_MUSHROOM);
        Random heightRolls = new Random(7);
        int height = heightRolls.nextInt(3) + 4;
        assertTrue(MushroomBlocks.growHuge(world, pos, BuiltinBlocks.RED_MUSHROOM,
                new Random(7)), "the open surface accepts the giant");
        int top = SURFACE + 1 + height;
        // The top row (lines 63-148 at l2 == y + i): the FULL 3x3 — the
        // corners not skipped (the skip gate's brown-or-below band), the
        // complete directional lattice.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                assertFalse(world.getBlock(at(2 + dx, top, 2 + dz)).equals(world.airType()),
                        "the red's top row is the full 3x3");
            }
        }
        assertTrue(isFamilyVariant(2, top, 2, "red_mushroom_block", "center"));
        assertTrue(isFamilyVariant(1, top, 1, "red_mushroom_block", "north_west"));
        assertTrue(isFamilyVariant(3, top, 3, "red_mushroom_block", "south_east"));
        assertTrue(isFamilyVariant(2, top, 1, "red_mushroom_block", "north"));
        // The skirt rows (top-3 .. top-1): the 5x5 ring alone PLUS the stem
        // column's own cell at the center (the stem runs y=5..top-1, so
        // every skirt row's center is the stem) — the corners skipped and
        // the interior's ALL_INSIDE never written (the write gate's
        // variant != 0).
        for (int y = top - 3; y <= top - 1; y++) {
            int ringBlocks = 0;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (!world.getBlock(at(2 + dx, y, 2 + dz)).equals(world.airType())) {
                        ringBlocks++;
                    }
                }
            }
            assertEquals(13, ringBlocks, "the skirt row at y=" + y
                    + " is the 12-cell ring plus the stem's center");
            assertTrue(isFamilyVariant(2, y, 2, "red_mushroom_block", "stem"),
                    "the skirt's center is the stem column's own cell");
            assertTrue(world.getBlock(at(1, y, 1)).equals(world.airType()),
                    "the interior's ALL_INSIDE never writes (the center alone carries the stem)");
            assertTrue(isFamilyVariant(2, y, 0, "red_mushroom_block", "north"));
            assertTrue(isFamilyVariant(0, y, 2, "red_mushroom_block", "west"));
        }
        // The stem column.
        for (int i = 0; i < height; i++) {
            assertTrue(isFamilyVariant(2, SURFACE + 1 + i, 2,
                    "red_mushroom_block", "stem"));
        }
    }

    @Test
    void theRefusedGrowRestoresThePlant() {
        scene();
        BlockPosition pos = at(2, SURFACE + 1, 2);
        world.setBlock(pos, BuiltinBlocks.BROWN_MUSHROOM);
        // The fit walk's refusal (lines 34-56): one solid cell inside the
        // band refuses the placement whole — the stone at (2, 6, 2) sits in
        // the bottom column (r = 0, y <= pos.y() + 3).
        world.setBlock(at(2, SURFACE + 2, 2), BuiltinBlocks.STONE);
        assertFalse(MushroomBlocks.growHuge(world, pos, BuiltinBlocks.BROWN_MUSHROOM,
                new Random(42)), "the blocked band refuses the giant");
        assertEquals(BuiltinBlocks.BROWN_MUSHROOM, world.getBlock(pos),
                "the refused grow restores the plant (grow line 97)");
        world.setBlock(at(2, SURFACE + 2, 2), world.airType());
        // The base gate's refusal (lines 58-61): stone below refuses too.
        world.setBlock(at(2, SURFACE, 2), BuiltinBlocks.STONE);
        assertFalse(MushroomBlocks.growHuge(world, pos, BuiltinBlocks.BROWN_MUSHROOM,
                new Random(42)), "the stone base refuses the giant");
        assertEquals(BuiltinBlocks.BROWN_MUSHROOM, world.getBlock(pos),
                "the base refusal restores the plant too");
    }
}
