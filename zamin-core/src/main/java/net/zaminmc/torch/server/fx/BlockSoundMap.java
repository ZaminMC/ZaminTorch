package net.zaminmc.torch.server.fx;

import net.zaminmc.torch.util.Identifier;

import java.util.Map;
import java.util.Optional;

/**
 * The block-sound translation data: which historical 1.8 dig-sound family a
 * block's break/place feedback uses. The engine stays identifier-keyed; this
 * map is the version-bound table the 1.8 feedback path reads.
 *
 * <p>Family coverage follows the historical material classes: stone, wood,
 * grass, gravel, sand, glass, cloth, snow. Blocks absent from the map fall
 * back to stone (the historical default feel).</p>
 */
public final class BlockSoundMap {

    private BlockSoundMap() {
    }

    private static final Map<String, String> DIG_FAMILY_BY_BLOCK = Map.ofEntries(
            // stony family
            Map.entry("minecraft:stone", "dig.stone"),
            Map.entry("minecraft:cobblestone", "dig.stone"),
            Map.entry("minecraft:coal_ore", "dig.stone"),
            Map.entry("minecraft:iron_ore", "dig.stone"),
            Map.entry("minecraft:diamond_ore", "dig.stone"),
            Map.entry("minecraft:furnace", "dig.stone"),
            // wooden family
            Map.entry("minecraft:oak_planks", "dig.wood"),
            Map.entry("minecraft:oak_log", "dig.wood"),
            Map.entry("minecraft:crafting_table", "dig.wood"),
            Map.entry("minecraft:chest", "dig.wood"),
            Map.entry("minecraft:torch", "dig.wood"),
            Map.entry("minecraft:oak_fence", "dig.wood"),
            // the collision-shape slice's wooden and stone families
            Map.entry("minecraft:oak_slab", "dig.wood"),
            Map.entry("minecraft:oak_slab_top", "dig.wood"),
            Map.entry("minecraft:oak_stairs_east", "dig.wood"),
            Map.entry("minecraft:oak_stairs_west", "dig.wood"),
            Map.entry("minecraft:oak_stairs_south", "dig.wood"),
            Map.entry("minecraft:oak_stairs_north", "dig.wood"),
            Map.entry("minecraft:stone_slab", "dig.stone"),
            Map.entry("minecraft:stone_slab_top", "dig.stone"),
            Map.entry("minecraft:cobblestone_slab", "dig.stone"),
            Map.entry("minecraft:cobblestone_slab_top", "dig.stone"),
            Map.entry("minecraft:sandstone_slab", "dig.stone"),
            Map.entry("minecraft:sandstone_slab_top", "dig.stone"),
            Map.entry("minecraft:cobblestone_stairs_east", "dig.stone"),
            Map.entry("minecraft:cobblestone_stairs_west", "dig.stone"),
            Map.entry("minecraft:cobblestone_stairs_south", "dig.stone"),
            Map.entry("minecraft:cobblestone_stairs_north", "dig.stone"),
            Map.entry("minecraft:rail", "dig.stone"),
            Map.entry("minecraft:rail_ew", "dig.stone"),
            // The village slice: the stone pieces dig like their families,
            // the panes shatter like glass, the crops dig like the wheat.
            Map.entry("minecraft:glass_pane", "dig.glass"),
            Map.entry("minecraft:iron_bars", "dig.stone"),
            Map.entry("minecraft:double_stone_slab", "dig.stone"),
            Map.entry("minecraft:smooth_sandstone", "dig.stone"),
            Map.entry("minecraft:sandstone_stairs_east", "dig.stone"),
            Map.entry("minecraft:sandstone_stairs_west", "dig.stone"),
            Map.entry("minecraft:sandstone_stairs_south", "dig.stone"),
            Map.entry("minecraft:sandstone_stairs_north", "dig.stone"),
            Map.entry("minecraft:carrots_stage0", "dig.grass"),
            Map.entry("minecraft:carrots_stage1", "dig.grass"),
            Map.entry("minecraft:carrots_stage2", "dig.grass"),
            Map.entry("minecraft:carrots_stage3", "dig.grass"),
            Map.entry("minecraft:carrots_stage4", "dig.grass"),
            Map.entry("minecraft:carrots_stage5", "dig.grass"),
            Map.entry("minecraft:carrots_stage6", "dig.grass"),
            Map.entry("minecraft:carrots_stage7", "dig.grass"),
            Map.entry("minecraft:potatoes_stage0", "dig.grass"),
            Map.entry("minecraft:potatoes_stage1", "dig.grass"),
            Map.entry("minecraft:potatoes_stage2", "dig.grass"),
            Map.entry("minecraft:potatoes_stage3", "dig.grass"),
            Map.entry("minecraft:potatoes_stage4", "dig.grass"),
            Map.entry("minecraft:potatoes_stage5", "dig.grass"),
            Map.entry("minecraft:potatoes_stage6", "dig.grass"),
            Map.entry("minecraft:potatoes_stage7", "dig.grass"),
            Map.entry("minecraft:sugar_cane", "dig.grass"),
            Map.entry("minecraft:cactus", "dig.wool"),
            Map.entry("minecraft:furnace_lit", "dig.stone"),
            // cloth family (the wool block-item's placeable form)
            Map.entry("minecraft:wool", "dig.wool"),
            // soft families
            Map.entry("minecraft:grass_block", "dig.grass"),
            Map.entry("minecraft:dirt", "dig.grass"),
            Map.entry("minecraft:sand", "dig.sand"),
            Map.entry("minecraft:gravel", "dig.gravel"),
            // The red sand (Slice 52 — the SandBlock family's own feel).
            Map.entry("minecraft:red_sand", "dig.sand"),
            Map.entry("minecraft:glass", "dig.glass"));

    /** The dig family sound name for a block identifier; stone is the default. */
    public static Optional<String> digSound(Identifier block) {
        return Optional.ofNullable(DIG_FAMILY_BY_BLOCK.get(
                block.namespace() + ":" + block.value()));
    }
}
