package net.zaminmc.torch.server.map;

import net.zaminmc.torch.server.block.BlockRegistryBuilder;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.FluidBlocks;
import net.zaminmc.torch.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE MAP PALETTE (the map item family — the reference's
 * {@code block/material/MapColor.java} and every block's own
 * {@code getMapColor(state)} consult): the thirty-six colors' ids and RGB
 * values 1:1, the whole registry covered (every registered block resolves a
 * table row), and the per-family pins over the reference's own assignments
 * — the material colors, the named overrides, the per-variant bands, and
 * the vanilla quirks (the stairs' base-block consult, gravel's STONE,
 * glowstone's SAND, TNT's LAVA, the jukebox's DIRT).
 */
class MapColorsTest {

    /**
     * The whole registry is covered: every registered block (the built-ins
     * + the fluids — the composition the map scan reads through the
     * engine's world) has a table row. An unknown block would render as
     * nothing on a map (the reference's answer for unregistered blocks),
     * which is never the right answer for real terrain.
     */
    @Test
    void everyRegisteredBlockCarriesAColorRow() {
        BlockRegistryBuilder.FrozenBlockRegistry registry = fullRegistry();
        List<String> unknown = new ArrayList<>();
        for (net.zaminmc.torch.block.BlockType type : registry.all()) {
            if (!MapColors.known(type.identifier().value())) {
                unknown.add(type.identifier().toString());
            }
        }
        assertTrue(unknown.isEmpty(), "Blocks without a color row: " + unknown);
    }

    /** The thirty-six ids (the BY_ID band 0..35, no gaps) + RGB pins. */
    @Test
    void theColorIdsMatchTheReferenceBand() throws Exception {
        assertEquals(0, MapColors.AIR.id());
        assertEquals(12, MapColors.WATER.id());
        assertEquals(13, MapColors.WOOD.id());
        assertEquals(35, MapColors.NETHER.id());
        // The whole named band 0..35 exists (MapColor's own registration
        // order — one color per id, no gaps before 36).
        for (int id = 0; id <= 35; id++) {
            assertTrue(namedColorOf(id) != null, "color id " + id + " must exist");
        }
        // A few RGB pins (MapColor.java's own constants).
        assertEquals(8368696, MapColors.GRASS.rgb());
        assertEquals(4210943, MapColors.WATER.rgb());
        assertEquals(16711680, MapColors.LAVA.rgb());
        assertEquals(7368816, MapColors.STONE.rgb());
        assertEquals(8476209, MapColors.SPRUCE.rgb());
        // The client's shade walk (MapColor.getColor lines 54-76): the
        // WHITE color's four bands.
        assertEquals(0xFFB4B4B4, MapColors.WHITE.shaded(0));
        assertEquals(0xFFDCDCDC, MapColors.WHITE.shaded(1));
        assertEquals(0xFFFFFFFF, MapColors.WHITE.shaded(2));
        assertEquals(0xFF878787, MapColors.WHITE.shaded(3));
    }

    private static MapColors.Color namedColorOf(int id) {
        try {
            for (var field : MapColors.class.getFields()) {
                Object value = field.get(null);
                if (value instanceof MapColors.Color color && color.id() == id) {
                    return color;
                }
            }
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
        return null;
    }

    /**
     * The named overrides (the reference's own getMapColor arms): gravel's
     * STONE, glowstone's SAND, obsidian's BLACK, netherrack's NETHER,
     * soul sand's BROWN, mycelium's PURPLE, the quartz block's QUARTZ,
     * the sea lantern's QUARTZ, hardened clay's ORANGE, the jukebox's
     * DIRT, TNT's LAVA, the melon's LIME, the hay bale's YELLOW, the
     * pumpkins' ORANGE, the dragon egg's BLACK, the end stone's SAND.
     */
    @Test
    void theNamedOverridesMatchTheReference() {
        assertEquals(MapColors.STONE, color("gravel"));
        assertEquals(MapColors.SAND, color("glowstone"));
        assertEquals(MapColors.BLACK, color("obsidian"));
        assertEquals(MapColors.NETHER, color("netherrack"));
        assertEquals(MapColors.BROWN, color("soul_sand"));
        assertEquals(MapColors.PURPLE, color("mycelium"));
        assertEquals(MapColors.QUARTZ, color("quartz_block"));
        assertEquals(MapColors.QUARTZ, color("sea_lantern"));
        assertEquals(MapColors.ORANGE, color("hardened_clay"));
        assertEquals(MapColors.DIRT, color("jukebox"));
        assertEquals(MapColors.LAVA, color("tnt"));
        assertEquals(MapColors.LIME, color("melon_block"));
        assertEquals(MapColors.YELLOW, color("hay_block"));
        assertEquals(MapColors.ORANGE, color("pumpkin"));
        assertEquals(MapColors.ORANGE, color("lit_pumpkin"));
        assertEquals(MapColors.BLACK, color("dragon_egg"));
        assertEquals(MapColors.SAND, color("end_stone"));
        // The end portal's black + the frames' green.
        assertEquals(MapColors.BLACK, color("end_portal"));
        assertEquals(MapColors.GREEN, color("end_portal_frame_east"));
        assertEquals(MapColors.GREEN, color("end_portal_frame_eye_west"));
        // The infested eggs carry the CLAY material.
        assertEquals(MapColors.CLAY, color("infested_stone"));
    }

    /**
     * The per-variant bands: the stone family's ores (STONE, the quartz
     * ore's NETHER), the dirt trio (DIRT/DIRT/SPRUCE), the sand pair, the
     * planks' per-wood colors, the dye families' per-dye colors, and the
     * mushroom blocks' stem/inside overrides.
     */
    @Test
    void theVariantBandsMatchTheReference() {
        assertEquals(MapColors.STONE, color("diamond_ore"));
        assertEquals(MapColors.NETHER, color("quartz_ore"));
        assertEquals(MapColors.DIRT, color("dirt"));
        assertEquals(MapColors.DIRT, color("coarse_dirt"));
        assertEquals(MapColors.SPRUCE, color("podzol"));
        assertEquals(MapColors.SAND, color("sand"));
        assertEquals(MapColors.ORANGE, color("red_sand"));
        // The planks (PlanksBlock.Variant's own colors).
        assertEquals(MapColors.WOOD, color("oak_planks"));
        assertEquals(MapColors.SPRUCE, color("spruce_planks"));
        assertEquals(MapColors.SAND, color("birch_planks"));
        assertEquals(MapColors.DIRT, color("jungle_planks"));
        assertEquals(MapColors.ORANGE, color("acacia_planks"));
        assertEquals(MapColors.BROWN, color("dark_oak_planks"));
        // The logs' axis-Y arm (the engine's upright form).
        assertEquals(MapColors.SAND, color("birch_log"));
        assertEquals(MapColors.BROWN, color("dark_oak_log"));
        // The dye families (a spot pin of the 16-dye band).
        assertEquals(MapColors.RED, color("red_wool"));
        assertEquals(MapColors.BLACK, color("black_wool"));
        assertEquals(MapColors.WHITE, color("wool"));
        assertEquals(MapColors.CYAN, color("carpet_cyan"));
        assertEquals(MapColors.PURPLE, color("stained_glass_purple"));
        assertEquals(MapColors.LIGHT_BLUE, color("stained_clay_light_blue"));
        // The mushroom blocks' overrides.
        assertEquals(MapColors.WEB, color("brown_mushroom_block_all_stem"));
        assertEquals(MapColors.SAND, color("red_mushroom_block_stem"));
        assertEquals(MapColors.DIRT, color("brown_mushroom_block"));
        assertEquals(MapColors.RED, color("red_mushroom_block_center"));
        // The prismarine pair.
        assertEquals(MapColors.CYAN, color("prismarine_rough"));
        assertEquals(MapColors.DIAMOND, color("prismarine_bricks"));
    }

    /**
     * The stairs law (StairsBlock.getMapColor line 488 — the BASE BLOCK's
     * own consult): the brick stairs read the brick block's RED, the
     * quartz/sandstone/nether-brick stairs read their full blocks'
     * QUARTZ/SAND/NETHER, the wood stairs read their wood's plank color,
     * and the red sandstone stairs read the RED_SAND ctor's ORANGE.
     */
    @Test
    void theStairsCarryTheBaseBlocksOwnColors() {
        assertEquals(MapColors.RED, color("brick_stairs_east"));
        assertEquals(MapColors.QUARTZ, color("quartz_stairs_north_top"));
        assertEquals(MapColors.SAND, color("sandstone_stairs_south"));
        assertEquals(MapColors.NETHER, color("nether_brick_stairs_west"));
        assertEquals(MapColors.ORANGE, color("red_sandstone_stairs_east"));
        assertEquals(MapColors.SPRUCE, color("spruce_stairs_east"));
        assertEquals(MapColors.BROWN, color("dark_oak_stairs_west_top"));
    }

    /**
     * The transparent band: everything the reference renders as nothing on
     * the map (AIR color) — the glass family, the torch/repeater/
     * comparator/wire band, the levers/buttons, the ladder, the barriers,
     * fire, the portals.
     */
    @Test
    void theTransparentBandReadsAir() {
        for (String transparent : new String[] {"glass", "glass_pane",
                "torch", "redstone_torch", "redstone_wire", "redstone_wire_7",
                "lever_east", "stone_button_down", "ladder", "barrier",
                "nether_portal", "redstone_lamp", "lit_redstone_lamp",
                "tripwire", "flower_pot", "potted_dandelion", "skull"}) {
            assertEquals(MapColors.AIR, color(transparent),
                    transparent + " reads AIR");
        }
    }

    private static MapColors.Color color(String block) {
        return MapColors.of(lookup(block));
    }

    private static net.zaminmc.torch.block.BlockType lookup(String block) {
        return fullRegistry().lookup(Identifier.parse("minecraft:" + block))
                .orElseThrow(() -> new AssertionError("no block " + block));
    }

    /** The engine's own boot composition (EngineServer's registry chain). */
    private static BlockRegistryBuilder.FrozenBlockRegistry fullRegistry() {
        return net.zaminmc.torch.server.cauldron.CauldronBlocks.registerAll(
                net.zaminmc.torch.server.chest.EnderChestBlocks.registerAll(
                net.zaminmc.torch.server.beacon.BeaconSystem.registerBlocks(
                net.zaminmc.torch.server.brewing.BrewingStandBlocks.registerAll(
                net.zaminmc.torch.server.dispenser.DispenserSystem.registerBlocks(
                net.zaminmc.torch.server.hopper.HopperSystem.registerBlocks(
                net.zaminmc.torch.server.piston.PistonSystem.registerBlocks(
                net.zaminmc.torch.server.redstone.TripwireBlocks.registerAll(
                net.zaminmc.torch.server.redstone.RedstoneBlocks.registerAll(
                net.zaminmc.torch.server.daylight.DaylightDetectorBlocks.registerAll(
                        FluidBlocks.registerAll(
                                BuiltinBlocks.registerAll(new BlockRegistryBuilder()))))))))))))
                .freeze();
    }

    /** The unknown-block consult answers AIR (nothing renders). */
    @Test
    void theUnknownConsultAnswersAir() {
        assertFalse(MapColors.known("definitely_not_a_block"));
        assertEquals(MapColors.AIR, MapColors.of(null));
    }
}
