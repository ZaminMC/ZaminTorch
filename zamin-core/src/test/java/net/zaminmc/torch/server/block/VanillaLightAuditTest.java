package net.zaminmc.torch.server.block;

import net.zaminmc.torch.util.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The vanilla 1:1 light audit's pin — every family the audit corrected or
 * verified against the reference's own registrations
 * ({@code reference/1.8.8/net/minecraft/block/Block.java}'s register block,
 * the sole authority): every {@code setLight(f)} chain is an emission
 * {@code (int)(15 * f)} truncated (Block.java line 254), every
 * {@code setOpacity(n)} a filter, and a block with neither answers the
 * constructor default {@code isSolidRender() ? 255 : 0} (line 216).
 *
 * <p>Nine families were corrected (the table's own class javadoc carries the
 * full citations): snow_layer, the leaves, the brewing stand, the beacon, the
 * unlit furnace, the brown mushroom, the end portal frames, the falling water,
 * and the powered comparators. This test pins every one of them PLUS the
 * reference-verified rows the audit confirmed already correct — the full
 * emitter list of the registry (every setLight call in the register block and
 * the two class-internal ones), so a future table edit that drifts from
 * vanilla fails here with the reference line in hand.</p>
 */
class VanillaLightAuditTest {

    private static BlockLightTable.LightData light(String id) {
        return BlockLightTable.lookup(Identifier.parse(id)).orElseThrow();
    }

    private static void assertLight(String id, int emission, int filter, String because) {
        BlockLightTable.LightData data = light(id);
        assertEquals(emission, data.emission(), id + " emission — " + because);
        assertEquals(filter, data.filter(), id + " filter — " + because);
    }

    @Test
    void theNineCorrectedFamiliesCarryTheReferenceRows() {
        // (1) snow_layer: setOpacity(0) at Block.java line 1175 — the thin
        // cover passes skylight whole (the full snow block stays opaque).
        assertLight("minecraft:snow_layer", 0, 0, "setOpacity(0), line 1175");
        assertLight("minecraft:snow", 0, 15, "the solid cube keeps the default");

        // (2) the leaves: no setLight on either registration (lines 1027/1347)
        // — the dataset's emitLight 1 is a quirk — with
        // AbstractLeavesBlock's setOpacity(1) (its line 30).
        assertLight("minecraft:oak_leaves", 0, 1, "no setLight + setOpacity(1)");
        assertLight("minecraft:spruce_leaves", 0, 1, "leaves2 registered the same way");
        assertLight("minecraft:acacia_leaves", 0, 1, "leaves2 registered the same way");

        // (3) the brewing stand: setLight(0.125F) = 1 at line 1241 — an
        // EMISSION (the old row misread the call as a filter) — with
        // isSolidRender false giving opacity 0; every bottle-bit state.
        assertLight("minecraft:brewing_stand", 1, 0, "setLight(0.125F) = 1, line 1241");
        assertLight("minecraft:brewing_stand_bottle0", 1, 0, "the same block's own states");
        assertLight("minecraft:brewing_stand_bottle1_bottle2", 1, 0, "the same block's own states");
        assertLight("minecraft:brewing_stand_bottle0_bottle1_bottle2", 1, 0, "the same block's own states");

        // (4) the beacon: setLight(1.0F) = 15 at line 1300 — the beacon
        // GLOWS — with the glass shape's opacity 0.
        assertLight("minecraft:beacon", 15, 0, "setLight(1.0F) = 15, line 1300");

        // (5) the unlit furnace family: FurnaceBlock(false) overrides nothing
        // (line 1126) — solid render, filter 15 — self-consistent with the
        // lit rows' 13/15 (setLight(0.875F), line 1128).
        assertLight("minecraft:furnace", 0, 15, "solid-render default, line 1126");
        assertLight("minecraft:furnace_north", 0, 15, "the facing states of the same block");
        assertLight("minecraft:furnace_east", 0, 15, "the facing states of the same block");
        assertLight("minecraft:furnace_lit", 13, 15, "setLight(0.875F) = 13, line 1128");
        assertLight("minecraft:furnace_lit_west", 13, 15, "the lit facing states");

        // (6) the mushrooms are TWO registrations (lines 1058-1061): only the
        // BROWN carries setLight(0.125F) = 1 — the audit's first cut wrongly
        // gave both the glow; the reference's block4 has no setLight.
        assertLight("minecraft:brown_mushroom", 1, 0, "setLight(0.125F) = 1, line 1058");
        assertLight("minecraft:red_mushroom", 0, 0, "no setLight on block4, line 1061");

        // (7) the end portal frames: setLight(0.125F) = 1 (line 1249 — the
        // eye-socket's glow) with EndPortalFrameBlock's isSolidRender false
        // (its line 29) giving opacity 0 — the 13/16-high frame passes light.
        assertLight("minecraft:end_portal_frame_south", 1, 0, "setLight(0.125F) + isSolidRender false");
        assertLight("minecraft:end_portal_frame_eye_north", 1, 0, "the eye states glow the same");

        // (8) the falling water: setOpacity(3) on BOTH water forms (lines
        // 1017-1018) — the dataset's filterLight 2 retired.
        assertLight("minecraft:falling_water", 0, 3, "setOpacity(3), line 1017");
        assertLight("minecraft:water", 0, 3, "setOpacity(3), line 1018");
        assertLight("minecraft:flowing_water_4", 0, 3, "the levels pay the source's 3");

        // (9) the powered comparators: setLight(0.625F) = 9 (line 1321) — the
        // powered pair glows — while the unpowered registration (line 1319)
        // carries no setLight.
        assertLight("minecraft:powered_comparator_east_compare", 9, 0, "setLight(0.625F) = 9, line 1321");
        assertLight("minecraft:powered_comparator_north_subtract", 9, 0, "the whole powered family");
        assertLight("minecraft:comparator_east_compare", 0, 0, "no setLight, line 1319");
        assertLight("minecraft:comparator_west_subtract", 0, 0, "the unpowered family stays dark");
    }

    @Test
    void everyRegistryEmitterCarriesTheReferenceEmission() {
        // The complete setLight census of the register block plus the two
        // class-internal arms (EndPortalBlock line 22, RedstoneLampBlock
        // line 18) — every vanilla 1.8.8 light source, pinned.
        List<Map.Entry<String, Integer>> emitters = List.of(
                Map.entry("minecraft:torch", 14),                // setLight(0.9375F), line 1104
                Map.entry("minecraft:fire", 15),                 // setLight(1.0F), line 1105
                Map.entry("minecraft:lava", 15),                 // setLight(1.0F), line 1020
                Map.entry("minecraft:falling_lava", 15),         // setLight(1.0F), line 1019
                Map.entry("minecraft:flowing_lava_3", 15),       // the levels ride the source
                Map.entry("minecraft:glowstone", 15),            // setLight(1.0F), line 1191
                Map.entry("minecraft:nether_portal", 11),        // setLight(0.75F), line 1192
                Map.entry("minecraft:nether_portal_z", 11),      // the axis pair
                Map.entry("minecraft:lit_pumpkin", 15),          // setLight(1.0F), line 1193
                Map.entry("minecraft:sea_lantern", 15),          // setLight(1.0F), line 1357
                Map.entry("minecraft:redstone_torch", 7),        // setLight(0.5F), line 1172
                Map.entry("minecraft:redstone_torch_west", 7),   // the wall arms
                Map.entry("minecraft:unlit_redstone_torch", 0),  // no setLight, line 1168
                Map.entry("minecraft:lit_redstone_ore", 9),      // setLight(0.625F), line 1166
                Map.entry("minecraft:redstone_ore", 0),          // the unlit ore never glows
                Map.entry("minecraft:lit_redstone_lamp", 15),    // class-internal, RedstoneLampBlock line 18
                Map.entry("minecraft:redstone_lamp", 0),         // the unlit lamp
                Map.entry("minecraft:ender_chest_north", 7),     // setLight(0.5F), line 1282
                Map.entry("minecraft:ender_chest_east", 7),      // the facing states
                Map.entry("minecraft:dragon_egg", 1),            // setLight(0.125F), line 1266
                Map.entry("minecraft:end_portal", 15));          // class-internal, EndPortalBlock line 22
        for (Map.Entry<String, Integer> emitter : emitters) {
            assertEquals(emitter.getValue(), light(emitter.getKey()).emission(),
                    emitter.getKey() + "'s reference emission");
        }
    }

    @Test
    void everyRegistryOpacityOverrideCarriesTheReferenceFilter() {
        // The complete setOpacity census of the register block plus the
        // class-internal calls the audit walked (StairsBlock 255, SlabBlock
        // 255, FarmlandBlock 255, AnvilBlock 0, EnchantingTableBlock 0,
        // AbstractLeavesBlock 1) — the 255s fold to the engine's 15.
        List<Map.Entry<String, Integer>> overrides = List.of(
                Map.entry("minecraft:water", 3),              // setOpacity(3), line 1018
                Map.entry("minecraft:falling_water", 3),      // setOpacity(3), line 1017
                Map.entry("minecraft:flowing_water_7", 3),    // the levels pay the source's 3
                Map.entry("minecraft:ice", 3),                // setOpacity(3), line 1176
                Map.entry("minecraft:cobweb", 1),             // setOpacity(1), line 1049
                Map.entry("minecraft:snow_layer", 0),         // setOpacity(0), line 1175
                Map.entry("minecraft:carpet_white", 0),       // setOpacity(0), line 1361 (the color members ride it)
                Map.entry("minecraft:carpet_red", 0),         // the color members
                Map.entry("minecraft:anvil", 0),              // AnvilBlock setOpacity(0), its line 32
                Map.entry("minecraft:enchanting_table", 0),   // EnchantingTableBlock, its line 22
                Map.entry("minecraft:oak_stairs_east", 15),   // StairsBlock setOpacity(255), its line 44
                Map.entry("minecraft:oak_slab", 15),          // SlabBlock setOpacity(255), its line 30
                Map.entry("minecraft:farmland", 15),          // FarmlandBlock setOpacity(255), its line 26
                Map.entry("minecraft:farmland_wet", 15),      // the wet states
                Map.entry("minecraft:oak_leaves", 1));        // AbstractLeavesBlock setOpacity(1), its line 30
        for (Map.Entry<String, Integer> override : overrides) {
            assertEquals(override.getValue(), light(override.getKey()).filter(),
                    override.getKey() + "'s reference filter");
        }
    }

    @Test
    void theSolidRenderDefaultsFoldToTheEngineBand() {
        // The constructor default isSolidRender() ? 255 : 0 (Block.java line
        // 216): the engine folds 255 to its 15. A few census anchors —
        // blocks with NO light calls whose whole answer is the default.
        assertLight("minecraft:stone", 0, 15, "the solid-render default");
        assertLight("minecraft:chest", 0, 0, "the non-solid-render default");
        assertLight("minecraft:glass", 0, 0, "the non-solid-render default");
        assertLight("minecraft:mob_spawner", 0, 0, "the cutout cage's default");
        assertLight("minecraft:jukebox", 0, 15, "the solid-render default");
        assertLight("minecraft:command_block", 0, 15, "the solid-render default");
    }

    @Test
    void theTableCoversEveryRegisteredEmitterRow() {
        // The audit's completeness bar: every table row with a NONZERO
        // emission must be one of the reference's emitters (the census above
        // plus the nine corrected families) — no invented glow survives.
        List<String> referenceEmitters = List.of(
                "minecraft:torch", "minecraft:fire",
                "minecraft:fire_age1", "minecraft:fire_age2", "minecraft:fire_age3",
                "minecraft:fire_age4", "minecraft:fire_age5", "minecraft:fire_age6",
                "minecraft:fire_age7", "minecraft:fire_age8", "minecraft:fire_age9",
                "minecraft:fire_age10", "minecraft:fire_age11", "minecraft:fire_age12",
                "minecraft:fire_age13", "minecraft:fire_age14", "minecraft:fire_age15",
                "minecraft:lava", "minecraft:falling_lava",
                "minecraft:flowing_lava",
                "minecraft:flowing_lava_1", "minecraft:flowing_lava_2", "minecraft:flowing_lava_3",
                "minecraft:flowing_lava_4", "minecraft:flowing_lava_5", "minecraft:flowing_lava_6",
                "minecraft:flowing_lava_7",
                // The Wave VI band's settled and falling-with-level members
                // (the same two Block registrations — one glow family).
                "minecraft:still_lava_1", "minecraft:still_lava_2", "minecraft:still_lava_3",
                "minecraft:still_lava_4", "minecraft:still_lava_5", "minecraft:still_lava_6",
                "minecraft:still_lava_7", "minecraft:still_lava_8", "minecraft:still_lava_9",
                "minecraft:still_lava_10", "minecraft:still_lava_11", "minecraft:still_lava_12",
                "minecraft:still_lava_13", "minecraft:still_lava_14", "minecraft:still_lava_15",
                "minecraft:falling_lava_1", "minecraft:falling_lava_2", "minecraft:falling_lava_3",
                "minecraft:falling_lava_4", "minecraft:falling_lava_5", "minecraft:falling_lava_6",
                "minecraft:falling_lava_7",
                "minecraft:glowstone", "minecraft:nether_portal", "minecraft:nether_portal_z",
                "minecraft:lit_pumpkin", "minecraft:sea_lantern",
                "minecraft:redstone_torch", "minecraft:redstone_torch_east", "minecraft:redstone_torch_west",
                "minecraft:redstone_torch_south", "minecraft:redstone_torch_north",
                "minecraft:lit_redstone_ore", "minecraft:lit_redstone_lamp",
                "minecraft:ender_chest_north", "minecraft:ender_chest_south",
                "minecraft:ender_chest_west", "minecraft:ender_chest_east",
                "minecraft:dragon_egg", "minecraft:end_portal",
                "minecraft:furnace_lit", "minecraft:furnace_lit_north", "minecraft:furnace_lit_south",
                "minecraft:furnace_lit_west", "minecraft:furnace_lit_east",
                "minecraft:brewing_stand", "minecraft:brewing_stand_bottle0", "minecraft:brewing_stand_bottle1",
                "minecraft:brewing_stand_bottle2", "minecraft:brewing_stand_bottle0_bottle1",
                "minecraft:brewing_stand_bottle0_bottle2", "minecraft:brewing_stand_bottle1_bottle2",
                "minecraft:brewing_stand_bottle0_bottle1_bottle2",
                "minecraft:beacon", "minecraft:brown_mushroom",
                "minecraft:end_portal_frame_south", "minecraft:end_portal_frame_west",
                "minecraft:end_portal_frame_north", "minecraft:end_portal_frame_east",
                "minecraft:end_portal_frame_eye_south", "minecraft:end_portal_frame_eye_west",
                "minecraft:end_portal_frame_eye_north", "minecraft:end_portal_frame_eye_east",
                "minecraft:powered_comparator_east_compare", "minecraft:powered_comparator_east_subtract",
                "minecraft:powered_comparator_south_compare", "minecraft:powered_comparator_south_subtract",
                "minecraft:powered_comparator_west_compare", "minecraft:powered_comparator_west_subtract",
                "minecraft:powered_comparator_north_compare", "minecraft:powered_comparator_north_subtract");
        for (Map.Entry<Identifier, BlockLightTable.LightData> entry : BlockLightTable.entries()) {
            if (entry.getValue().emission() > 0) {
                assertTrue(referenceEmitters.contains(entry.getKey().toString()),
                        "an emission outside the reference's census: " + entry.getKey());
            }
        }
        // And the census itself is fully present (the lookup above already
        // threw on a gap; this keeps the count honest on both sides).
        for (String emitter : referenceEmitters) {
            assertTrue(light(emitter).emission() > 0, "the census row went dark: " + emitter);
        }
    }
}
