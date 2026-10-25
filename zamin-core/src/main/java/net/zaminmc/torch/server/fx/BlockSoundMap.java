package net.zaminmc.torch.server.fx;

import net.zaminmc.torch.util.Identifier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The block-sound family table, extracted 1:1 from the reference's
 * {@code Block.init()} ({@code reference/1.8.8/net/minecraft/block/Block.java}
 * lines 991-1477): every registered block carries one of the twelve named
 * {@code Block.Sounds} families — set either by its {@code setSounds()} row
 * in the registration, by its class constructor, or by the base
 * {@code DEFAULT_SOUNDS} ("stone") when neither sets one. The families
 * answer the three names the reference derives ({@code Block.Sounds} lines
 * 1508-1539): the breaking dig name, the placing name, and the stepping
 * name — each family's volume/pitch riding with it.
 *
 * <p>The engine is identifier-keyed and flattens what the reference modeled
 * as block state (the facing chests, the stair halves, the fluid levels)
 * into separate identifiers. The table carries the base rows exactly and
 * resolves the flattened variants through ordered prefix rules — a state
 * variant resolves to its base block's family, the same answer the
 * reference's block-identity-keyed {@code sounds} field gives. Anything
 * unlisted folds to the stone family, the reference's own
 * {@code DEFAULT_SOUNDS} law.</p>
 */
public final class BlockSoundMap {

    private BlockSoundMap() {
    }

    /**
     * The reference's {@code Block.Sounds} families (Block.java lines
     * 40-88): the sound key, the volume, and the pitch. The three name
     * overrides the anonymous subclasses carry — glass's placing reads
     * {@code step.stone}, the ladder breaks like wood, the anvil breaks
     * stone and lands with {@code random.anvil_land}, the slime's whole
     * dig family rides the mob sounds — live in the accessors below,
     * exactly the shape of the reference's overridden getters.
     */
    public enum SoundFamily {
        STONE("stone", 1.0f, 1.0f),
        WOOD("wood", 1.0f, 1.0f),
        GRAVEL("gravel", 1.0f, 1.0f),
        GRASS("grass", 1.0f, 1.0f),
        METAL("stone", 1.0f, 1.5f),
        GLASS("stone", 1.0f, 1.0f),
        CLOTH("cloth", 1.0f, 1.0f),
        SAND("sand", 1.0f, 1.0f),
        SNOW("snow", 1.0f, 1.0f),
        LADDER("ladder", 1.0f, 1.0f),
        ANVIL("anvil", 0.3f, 1.0f),
        SLIME("slime", 1.0f, 1.0f);

        final String key;
        final float volume;
        final float pitch;

        SoundFamily(String key, float volume, float pitch) {
            this.key = key;
            this.volume = volume;
            this.pitch = pitch;
        }

        /** The family's volume term ({@code Sounds.getVolume}). */
        public float volume() {
            return volume;
        }

        /** The family's pitch term ({@code Sounds.getPitch}). */
        public float pitch() {
            return pitch;
        }

        /** {@code Sounds.getBreaking} — the dig name, four overrides 1:1. */
        public String breaking() {
            return switch (this) {
                case GLASS -> "dig.glass";
                case LADDER -> "dig.wood";
                case ANVIL -> "dig.stone";
                case SLIME -> "mob.slime.big";
                default -> "dig." + key;
            };
        }

        /** {@code Sounds.getPlacing} — the base is the breaking name. */
        public String placing() {
            return switch (this) {
                case GLASS -> "step.stone";
                case ANVIL -> "random.anvil_land";
                case SLIME -> "mob.slime.big";
                default -> breaking();
            };
        }

        /** {@code Sounds.getStepping} — "step." + key, the slime override. */
        public String stepping() {
            return this == SLIME ? "mob.slime.small" : "step." + key;
        }
    }

    // ------------------------------------------------------------------
    // The base rows: every Block.init() registration that carries a
    // setSounds() call (or a constructor's) mapped to the engine's
    // identifier for the same block. The rows the reference leaves on
    // DEFAULT_SOUNDS (noteblock, cobweb, the infested eggs, the redstone
    // dust, the tripwire pair, the enchanting table, the brewing stand,
    // the cauldron, the end portal, the command block, the beacon, the
    // flower pot, the barrier, the piston extension) fold to the stone
    // default and need no row — the fallback IS their family.
    // ------------------------------------------------------------------
    private static final Map<String, SoundFamily> FAMILIES = buildFamilies();

    private static Map<String, SoundFamily> buildFamilies() {
        Map<String, SoundFamily> m = new LinkedHashMap<>();
        // stone family
        row(m, "stone", SoundFamily.STONE);
        row(m, "cobblestone", SoundFamily.STONE);
        row(m, "bedrock", SoundFamily.STONE);
        row(m, "gold_ore", SoundFamily.STONE);
        row(m, "iron_ore", SoundFamily.STONE);
        row(m, "coal_ore", SoundFamily.STONE);
        row(m, "lapis_ore", SoundFamily.STONE);
        row(m, "lapis_block", SoundFamily.STONE);
        row(m, "dispenser", SoundFamily.STONE);
        row(m, "sandstone", SoundFamily.STONE);
        row(m, "chiseled_sandstone", SoundFamily.STONE);
        row(m, "smooth_sandstone", SoundFamily.STONE);
        row(m, "double_stone_slab", SoundFamily.STONE);
        row(m, "double_stone_slab_seamless", SoundFamily.STONE);
        row(m, "stone_slab", SoundFamily.STONE);
        row(m, "stone_slab_top", SoundFamily.STONE);
        row(m, "stone_slab2", SoundFamily.STONE);
        row(m, "stone_slab2_top", SoundFamily.STONE);
        row(m, "double_stone_slab2", SoundFamily.STONE);
        row(m, "double_stone_slab2_seamless", SoundFamily.STONE);
        row(m, "cobblestone_slab", SoundFamily.STONE);
        row(m, "cobblestone_slab_top", SoundFamily.STONE);
        row(m, "double_cobblestone_slab", SoundFamily.STONE);
        row(m, "sandstone_slab", SoundFamily.STONE);
        row(m, "sandstone_slab_top", SoundFamily.STONE);
        row(m, "double_sandstone_slab", SoundFamily.STONE);
        row(m, "stone_brick_slab", SoundFamily.STONE);
        row(m, "stone_brick_slab_top", SoundFamily.STONE);
        row(m, "double_stone_brick_slab", SoundFamily.STONE);
        row(m, "brick_block", SoundFamily.STONE);
        row(m, "mossy_cobblestone", SoundFamily.STONE);
        row(m, "obsidian", SoundFamily.STONE);
        row(m, "diamond_ore", SoundFamily.STONE);
        row(m, "furnace", SoundFamily.STONE);
        row(m, "furnace_lit", SoundFamily.STONE);
        row(m, "stone_pressure_plate", SoundFamily.STONE);
        row(m, "redstone_ore", SoundFamily.STONE);
        row(m, "lit_redstone_ore", SoundFamily.STONE);
        row(m, "stone_button", SoundFamily.STONE);
        row(m, "jukebox", SoundFamily.STONE);
        row(m, "jukebox_record", SoundFamily.STONE);
        row(m, "stone_bricks", SoundFamily.STONE);
        row(m, "mossy_stone_bricks", SoundFamily.STONE);
        row(m, "cracked_stone_bricks", SoundFamily.STONE);
        row(m, "chiseled_stone_bricks", SoundFamily.STONE);
        row(m, "netherrack", SoundFamily.STONE);
        row(m, "nether_bricks", SoundFamily.STONE);
        row(m, "nether_brick_fence", SoundFamily.STONE);
        row(m, "emerald_ore", SoundFamily.STONE);
        row(m, "ender_chest", SoundFamily.STONE);
        row(m, "skull", SoundFamily.STONE);
        row(m, "dropper", SoundFamily.STONE);
        row(m, "quartz_ore", SoundFamily.STONE);
        row(m, "quartz_block", SoundFamily.STONE);
        row(m, "stained_hardened_clay", SoundFamily.STONE);
        row(m, "hardened_clay", SoundFamily.STONE);
        row(m, "coal_block", SoundFamily.STONE);
        row(m, "end_stone", SoundFamily.STONE);
        row(m, "dragon_egg", SoundFamily.STONE);
        row(m, "prismarine_rough", SoundFamily.STONE);
        row(m, "prismarine_bricks", SoundFamily.STONE);
        row(m, "prismarine_dark", SoundFamily.STONE);
        row(m, "red_sandstone", SoundFamily.STONE);
        row(m, "stone_stairs", SoundFamily.STONE);
        row(m, "stone_brick_stairs", SoundFamily.STONE);
        row(m, "brick_stairs", SoundFamily.STONE);
        row(m, "sandstone_stairs", SoundFamily.STONE);
        row(m, "nether_brick_stairs", SoundFamily.STONE);
        row(m, "quartz_stairs", SoundFamily.STONE);
        row(m, "red_sandstone_stairs", SoundFamily.STONE);
        row(m, "cobblestone_stairs", SoundFamily.STONE);
        // wood family
        row(m, "oak_planks", SoundFamily.WOOD);
        row(m, "spruce_planks", SoundFamily.WOOD);
        row(m, "birch_planks", SoundFamily.WOOD);
        row(m, "jungle_planks", SoundFamily.WOOD);
        row(m, "acacia_planks", SoundFamily.WOOD);
        row(m, "dark_oak_planks", SoundFamily.WOOD);
        row(m, "oak_log", SoundFamily.WOOD);
        row(m, "spruce_log", SoundFamily.WOOD);
        row(m, "birch_log", SoundFamily.WOOD);
        row(m, "jungle_log", SoundFamily.WOOD);
        row(m, "acacia_log", SoundFamily.WOOD);
        row(m, "dark_oak_log", SoundFamily.WOOD);
        row(m, "bed", SoundFamily.WOOD);
        row(m, "bed_head", SoundFamily.WOOD);
        row(m, "bookshelf", SoundFamily.WOOD);
        row(m, "torch", SoundFamily.WOOD);
        row(m, "oak_stairs", SoundFamily.WOOD);
        row(m, "chest", SoundFamily.WOOD);
        row(m, "trapped_chest", SoundFamily.WOOD);
        row(m, "crafting_table", SoundFamily.WOOD);
        row(m, "sign", SoundFamily.WOOD);
        row(m, "wall_sign", SoundFamily.WOOD);
        row(m, "oak_door", SoundFamily.WOOD);
        row(m, "lever", SoundFamily.WOOD);
        row(m, "wooden_pressure_plate", SoundFamily.WOOD);
        row(m, "unlit_redstone_torch", SoundFamily.WOOD);
        row(m, "redstone_torch", SoundFamily.WOOD);
        row(m, "trapdoor", SoundFamily.WOOD);
        row(m, "brown_mushroom_block", SoundFamily.WOOD);
        row(m, "red_mushroom_block", SoundFamily.WOOD);
        row(m, "melon_block", SoundFamily.WOOD);
        row(m, "pumpkin", SoundFamily.WOOD);
        row(m, "lit_pumpkin", SoundFamily.WOOD);
        row(m, "fence_gate", SoundFamily.WOOD);
        row(m, "pumpkin_stem", SoundFamily.WOOD);
        row(m, "melon_stem", SoundFamily.WOOD);
        row(m, "oak_fence", SoundFamily.WOOD);
        row(m, "double_wooden_slab", SoundFamily.WOOD);
        row(m, "wooden_slab", SoundFamily.WOOD);
        row(m, "cocoa", SoundFamily.WOOD);
        row(m, "spruce_stairs", SoundFamily.WOOD);
        row(m, "birch_stairs", SoundFamily.WOOD);
        row(m, "jungle_stairs", SoundFamily.WOOD);
        row(m, "acacia_stairs", SoundFamily.WOOD);
        row(m, "dark_oak_stairs", SoundFamily.WOOD);
        row(m, "wooden_button", SoundFamily.WOOD);
        row(m, "light_weighted_pressure_plate", SoundFamily.WOOD);
        row(m, "heavy_weighted_pressure_plate", SoundFamily.WOOD);
        row(m, "repeater", SoundFamily.WOOD);
        row(m, "comparator", SoundFamily.WOOD);
        row(m, "daylight_detector", SoundFamily.WOOD);
        row(m, "oak_slab", SoundFamily.WOOD);
        row(m, "oak_slab_top", SoundFamily.WOOD);
        row(m, "banner", SoundFamily.WOOD);
        row(m, "oak_fence_gate", SoundFamily.WOOD);
        // gravel family
        row(m, "dirt", SoundFamily.GRAVEL);
        row(m, "coarse_dirt", SoundFamily.GRAVEL);
        row(m, "podzol", SoundFamily.GRAVEL);
        row(m, "gravel", SoundFamily.GRAVEL);
        row(m, "farmland", SoundFamily.GRAVEL);
        row(m, "farmland_wet", SoundFamily.GRAVEL);
        row(m, "clay", SoundFamily.GRAVEL);
        // grass family
        row(m, "grass_block", SoundFamily.GRASS);
        row(m, "sapling", SoundFamily.GRASS);
        row(m, "tall_grass", SoundFamily.GRASS);
        row(m, "fern", SoundFamily.GRASS);
        row(m, "dead_bush", SoundFamily.GRASS);
        row(m, "sponge", SoundFamily.GRASS);
        row(m, "wet_sponge", SoundFamily.GRASS);
        row(m, "yellow_flower", SoundFamily.GRASS);
        row(m, "red_flower", SoundFamily.GRASS);
        row(m, "dandelion", SoundFamily.GRASS);
        row(m, "poppy", SoundFamily.GRASS);
        row(m, "brown_mushroom", SoundFamily.GRASS);
        row(m, "red_mushroom", SoundFamily.GRASS);
        row(m, "tnt", SoundFamily.GRASS);
        row(m, "wheat", SoundFamily.GRASS);
        row(m, "reeds", SoundFamily.GRASS);
        row(m, "sugar_cane", SoundFamily.GRASS);
        row(m, "vine", SoundFamily.GRASS);
        row(m, "mycelium", SoundFamily.GRASS);
        row(m, "waterlily", SoundFamily.GRASS);
        row(m, "leaves", SoundFamily.GRASS);
        row(m, "leaves2", SoundFamily.GRASS);
        row(m, "oak_leaves", SoundFamily.GRASS);
        row(m, "spruce_leaves", SoundFamily.GRASS);
        row(m, "birch_leaves", SoundFamily.GRASS);
        row(m, "jungle_leaves", SoundFamily.GRASS);
        row(m, "acacia_leaves", SoundFamily.GRASS);
        row(m, "dark_oak_leaves", SoundFamily.GRASS);
        row(m, "double_plant", SoundFamily.GRASS);
        row(m, "hay_block", SoundFamily.GRASS);
        row(m, "carrots_stage0", SoundFamily.GRASS);
        row(m, "carrots_stage1", SoundFamily.GRASS);
        row(m, "carrots_stage2", SoundFamily.GRASS);
        row(m, "carrots_stage3", SoundFamily.GRASS);
        row(m, "carrots_stage4", SoundFamily.GRASS);
        row(m, "carrots_stage5", SoundFamily.GRASS);
        row(m, "carrots_stage6", SoundFamily.GRASS);
        row(m, "carrots_stage7", SoundFamily.GRASS);
        row(m, "potatoes_stage0", SoundFamily.GRASS);
        row(m, "potatoes_stage1", SoundFamily.GRASS);
        row(m, "potatoes_stage2", SoundFamily.GRASS);
        row(m, "potatoes_stage3", SoundFamily.GRASS);
        row(m, "potatoes_stage4", SoundFamily.GRASS);
        row(m, "potatoes_stage5", SoundFamily.GRASS);
        row(m, "potatoes_stage6", SoundFamily.GRASS);
        row(m, "potatoes_stage7", SoundFamily.GRASS);
        row(m, "wheat_stage0", SoundFamily.GRASS);
        row(m, "wheat_stage1", SoundFamily.GRASS);
        row(m, "wheat_stage2", SoundFamily.GRASS);
        row(m, "wheat_stage3", SoundFamily.GRASS);
        row(m, "wheat_stage4", SoundFamily.GRASS);
        row(m, "wheat_stage5", SoundFamily.GRASS);
        row(m, "wheat_stage6", SoundFamily.GRASS);
        row(m, "wheat_stage7", SoundFamily.GRASS);
        // metal family (the stone key at pitch 1.5)
        row(m, "golden_rail", SoundFamily.METAL);
        row(m, "detector_rail", SoundFamily.METAL);
        row(m, "gold_block", SoundFamily.METAL);
        row(m, "iron_block", SoundFamily.METAL);
        row(m, "mob_spawner", SoundFamily.METAL);
        row(m, "iron_door", SoundFamily.METAL);
        row(m, "rail", SoundFamily.METAL);
        row(m, "iron_bars", SoundFamily.METAL);
        row(m, "diamond_block", SoundFamily.METAL);
        row(m, "emerald_block", SoundFamily.METAL);
        row(m, "redstone_block", SoundFamily.METAL);
        row(m, "hopper", SoundFamily.METAL);
        row(m, "activator_rail", SoundFamily.METAL);
        row(m, "iron_trapdoor", SoundFamily.METAL);
        // glass family (the dig.glass break, the step.stone place)
        row(m, "glass", SoundFamily.GLASS);
        row(m, "ice", SoundFamily.GLASS);
        row(m, "glowstone", SoundFamily.GLASS);
        row(m, "nether_portal", SoundFamily.GLASS);
        row(m, "stained_glass", SoundFamily.GLASS);
        row(m, "glass_pane", SoundFamily.GLASS);
        row(m, "stained_glass_pane", SoundFamily.GLASS);
        row(m, "redstone_lamp", SoundFamily.GLASS);
        row(m, "lit_redstone_lamp", SoundFamily.GLASS);
        row(m, "sea_lantern", SoundFamily.GLASS);
        row(m, "packed_ice", SoundFamily.GLASS);
        row(m, "end_portal_frame", SoundFamily.GLASS);
        // cloth family
        row(m, "wool", SoundFamily.CLOTH);
        row(m, "fire", SoundFamily.CLOTH);
        row(m, "cactus", SoundFamily.CLOTH);
        row(m, "cake", SoundFamily.CLOTH);
        row(m, "carpet", SoundFamily.CLOTH);
        // sand family
        row(m, "sand", SoundFamily.SAND);
        row(m, "red_sand", SoundFamily.SAND);
        row(m, "soul_sand", SoundFamily.SAND);
        // snow family
        row(m, "snow_layer", SoundFamily.SNOW);
        row(m, "snow", SoundFamily.SNOW);
        // ladder family (the dig.wood break)
        row(m, "ladder", SoundFamily.LADDER);
        // anvil family (volume 0.3, the dig.stone break, the land place)
        row(m, "anvil", SoundFamily.ANVIL);
        // slime family (the mob-sound trio)
        row(m, "slime_block", SoundFamily.SLIME);
        return m;
    }

    private static void row(Map<String, SoundFamily> m, String name, SoundFamily family) {
        m.put("minecraft:" + name, family);
    }

    // ------------------------------------------------------------------
    // The flattened-state prefix rules: the engine's state variants
    // resolve to their base block's family. Ordered longest-first within
    // each group; the resolver tries the longest matching prefix.
    // ------------------------------------------------------------------
    private record Rule(String prefix, SoundFamily family) {
    }

    private static final List<Rule> PREFIX_RULES = List.of(
            // The six woods' state families (fence/gate/slab/stairs/door).
            new Rule("minecraft:oak_fence_gate", SoundFamily.WOOD),
            new Rule("minecraft:spruce_fence_gate", SoundFamily.WOOD),
            new Rule("minecraft:birch_fence_gate", SoundFamily.WOOD),
            new Rule("minecraft:jungle_fence_gate", SoundFamily.WOOD),
            new Rule("minecraft:acacia_fence_gate", SoundFamily.WOOD),
            new Rule("minecraft:dark_oak_fence_gate", SoundFamily.WOOD),
            new Rule("minecraft:oak_fence", SoundFamily.WOOD),
            new Rule("minecraft:spruce_fence", SoundFamily.WOOD),
            new Rule("minecraft:birch_fence", SoundFamily.WOOD),
            new Rule("minecraft:jungle_fence", SoundFamily.WOOD),
            new Rule("minecraft:acacia_fence", SoundFamily.WOOD),
            new Rule("minecraft:dark_oak_fence", SoundFamily.WOOD),
            new Rule("minecraft:oak_stairs", SoundFamily.WOOD),
            new Rule("minecraft:spruce_stairs", SoundFamily.WOOD),
            new Rule("minecraft:birch_stairs", SoundFamily.WOOD),
            new Rule("minecraft:jungle_stairs", SoundFamily.WOOD),
            new Rule("minecraft:acacia_stairs", SoundFamily.WOOD),
            new Rule("minecraft:dark_oak_stairs", SoundFamily.WOOD),
            // The six woods' leaves (the persistent/persistent-check
            // state flattening) and saplings (the ready states).
            new Rule("minecraft:oak_leaves", SoundFamily.GRASS),
            new Rule("minecraft:spruce_leaves", SoundFamily.GRASS),
            new Rule("minecraft:birch_leaves", SoundFamily.GRASS),
            new Rule("minecraft:jungle_leaves", SoundFamily.GRASS),
            new Rule("minecraft:acacia_leaves", SoundFamily.GRASS),
            new Rule("minecraft:dark_oak_leaves", SoundFamily.GRASS),
            new Rule("minecraft:oak_sapling", SoundFamily.GRASS),
            new Rule("minecraft:spruce_sapling", SoundFamily.GRASS),
            new Rule("minecraft:birch_sapling", SoundFamily.GRASS),
            new Rule("minecraft:jungle_sapling", SoundFamily.GRASS),
            new Rule("minecraft:acacia_sapling", SoundFamily.GRASS),
            new Rule("minecraft:dark_oak_sapling", SoundFamily.GRASS),
            new Rule("minecraft:oak_slab", SoundFamily.WOOD),
            new Rule("minecraft:spruce_slab", SoundFamily.WOOD),
            new Rule("minecraft:birch_slab", SoundFamily.WOOD),
            new Rule("minecraft:jungle_slab", SoundFamily.WOOD),
            new Rule("minecraft:acacia_slab", SoundFamily.WOOD),
            new Rule("minecraft:dark_oak_slab", SoundFamily.WOOD),
            new Rule("minecraft:oak_door", SoundFamily.WOOD),
            new Rule("minecraft:spruce_door", SoundFamily.WOOD),
            new Rule("minecraft:birch_door", SoundFamily.WOOD),
            new Rule("minecraft:jungle_door", SoundFamily.WOOD),
            new Rule("minecraft:acacia_door", SoundFamily.WOOD),
            new Rule("minecraft:dark_oak_door", SoundFamily.WOOD),
            // The masonry stairs' facing/half variants (the base blocks'
            // own families — StairsBlock copies its base block's sounds).
            new Rule("minecraft:stone_stairs", SoundFamily.STONE),
            new Rule("minecraft:cobblestone_stairs", SoundFamily.STONE),
            new Rule("minecraft:brick_stairs", SoundFamily.STONE),
            new Rule("minecraft:stone_brick_stairs", SoundFamily.STONE),
            new Rule("minecraft:sandstone_stairs", SoundFamily.STONE),
            new Rule("minecraft:nether_brick_stairs", SoundFamily.STONE),
            new Rule("minecraft:quartz_stairs", SoundFamily.STONE),
            new Rule("minecraft:red_sandstone_stairs", SoundFamily.STONE),
            // The facing/variant furniture: chests, furnaces, signs,
            // ladders, skulls, doors' upper/open/powered halves, the
            // anvil damage/facing forms, the end-portal frames, the
            // infested eggs (DEFAULT stone — no row needed, but the
            // prefix keeps them off the fence consult), the stained
            // families, the pressure plates' powered forms.
            new Rule("minecraft:chest", SoundFamily.WOOD),
            new Rule("minecraft:trapped_chest", SoundFamily.WOOD),
            new Rule("minecraft:ender_chest", SoundFamily.STONE),
            new Rule("minecraft:furnace", SoundFamily.STONE),
            new Rule("minecraft:furnace_lit", SoundFamily.STONE),
            new Rule("minecraft:sign", SoundFamily.WOOD),
            new Rule("minecraft:wall_sign", SoundFamily.WOOD),
            new Rule("minecraft:ladder", SoundFamily.LADDER),
            new Rule("minecraft:skull", SoundFamily.STONE),
            new Rule("minecraft:iron_door", SoundFamily.METAL),
            new Rule("minecraft:trapdoor", SoundFamily.WOOD),
            new Rule("minecraft:iron_trapdoor", SoundFamily.METAL),
            new Rule("minecraft:anvil", SoundFamily.ANVIL),
            new Rule("minecraft:end_portal_frame", SoundFamily.GLASS),
            new Rule("minecraft:stained_glass", SoundFamily.GLASS),
            new Rule("minecraft:stained_glass_pane", SoundFamily.GLASS),
            new Rule("minecraft:stained_clay", SoundFamily.STONE),
            new Rule("minecraft:stained_hardened_clay", SoundFamily.STONE),
            new Rule("minecraft:wooden_pressure_plate", SoundFamily.WOOD),
            new Rule("minecraft:stone_pressure_plate", SoundFamily.STONE),
            new Rule("minecraft:wooden_button", SoundFamily.WOOD),
            new Rule("minecraft:stone_button", SoundFamily.STONE),
            new Rule("minecraft:lever", SoundFamily.WOOD),
            new Rule("minecraft:redstone_wire", SoundFamily.STONE),
            new Rule("minecraft:repeater", SoundFamily.WOOD),
            new Rule("minecraft:comparator", SoundFamily.WOOD),
            new Rule("minecraft:powered_comparator", SoundFamily.WOOD),
            new Rule("minecraft:redstone_torch", SoundFamily.WOOD),
            new Rule("minecraft:unlit_redstone_torch", SoundFamily.WOOD),
            new Rule("minecraft:rail", SoundFamily.METAL),
            new Rule("minecraft:golden_rail", SoundFamily.METAL),
            new Rule("minecraft:detector_rail", SoundFamily.METAL),
            new Rule("minecraft:activator_rail", SoundFamily.METAL),
            new Rule("minecraft:piston", SoundFamily.STONE),
            new Rule("minecraft:sticky_piston", SoundFamily.STONE),
            new Rule("minecraft:piston_head", SoundFamily.STONE),
            new Rule("minecraft:moving_piston", SoundFamily.STONE),
            new Rule("minecraft:dispenser", SoundFamily.STONE),
            new Rule("minecraft:dropper", SoundFamily.STONE),
            new Rule("minecraft:hopper", SoundFamily.METAL),
            new Rule("minecraft:daylight_detector", SoundFamily.WOOD),
            new Rule("minecraft:cauldron", SoundFamily.STONE),
            new Rule("minecraft:brewing_stand", SoundFamily.STONE),
            new Rule("minecraft:cobblestone_wall", SoundFamily.STONE),
            new Rule("minecraft:mossy_cobblestone_wall", SoundFamily.STONE),
            new Rule("minecraft:bed", SoundFamily.WOOD),
            new Rule("minecraft:bed_head", SoundFamily.WOOD),
            new Rule("minecraft:banner", SoundFamily.WOOD),
            new Rule("minecraft:wall_banner", SoundFamily.WOOD),
            new Rule("minecraft:sapling", SoundFamily.GRASS),
            new Rule("minecraft:leaves", SoundFamily.GRASS),
            new Rule("minecraft:cake", SoundFamily.CLOTH),
            new Rule("minecraft:fire", SoundFamily.CLOTH),
            new Rule("minecraft:flowing_water", SoundFamily.STONE),
            new Rule("minecraft:flowing_lava", SoundFamily.STONE),
            new Rule("minecraft:still_water", SoundFamily.STONE),
            new Rule("minecraft:still_lava", SoundFamily.STONE),
            new Rule("minecraft:falling_water", SoundFamily.STONE),
            new Rule("minecraft:falling_lava", SoundFamily.STONE),
            new Rule("minecraft:water", SoundFamily.STONE),
            new Rule("minecraft:lava", SoundFamily.STONE),
            new Rule("minecraft:nether_wart", SoundFamily.STONE),
            new Rule("minecraft:cocoa", SoundFamily.WOOD),
            new Rule("minecraft:pumpkin_stem", SoundFamily.WOOD),
            new Rule("minecraft:melon_stem", SoundFamily.WOOD),
            new Rule("minecraft:prismarine", SoundFamily.STONE),
            new Rule("minecraft:snow_layer", SoundFamily.SNOW));

    /**
     * The block's sound family (the reference's block-identity-keyed
     * {@code Block.sounds} field). The minecraft namespace always answers
     * — an unlisted block folds to the stone family, the reference's own
     * {@code DEFAULT_SOUNDS} law — while other namespaces answer empty
     * (no historical family).
     */
    public static Optional<SoundFamily> familyOf(Identifier block) {
        if (!block.namespace().equals("minecraft")) {
            return Optional.empty();
        }
        SoundFamily exact = FAMILIES.get(block.namespace() + ":" + block.value());
        if (exact != null) {
            return Optional.of(exact);
        }
        String id = block.namespace() + ":" + block.value();
        Rule best = null;
        for (Rule rule : PREFIX_RULES) {
            if (id.startsWith(rule.prefix())
                    && (best == null || rule.prefix().length() > best.prefix().length())) {
                best = rule;
            }
        }
        return Optional.of(best != null ? best.family() : SoundFamily.STONE);
    }

    /**
     * The dig (break) family sound name for a block identifier; the
     * historical default folds to stone. Empty only outside the
     * minecraft namespace.
     */
    public static Optional<String> digSound(Identifier block) {
        return familyOf(block).map(SoundFamily::breaking);
    }
}
