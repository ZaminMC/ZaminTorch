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
            Map.entry("minecraft:glass", "dig.glass"));

    /** The dig family sound name for a block identifier; stone is the default. */
    public static Optional<String> digSound(Identifier block) {
        return Optional.ofNullable(DIG_FAMILY_BY_BLOCK.get(
                block.namespace() + ":" + block.value()));
    }
}
