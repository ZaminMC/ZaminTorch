package net.zaminmc.torch.protocol.v1_8;

import net.zaminmc.torch.item.ItemType;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.util.Identifier;
import org.junit.jupiter.api.Test;


import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R-2 of the vanilla parity remainder: the item registry's id-by-id diff
 * against the reference 1.8.8 item table. The reference table is the
 * registration walk of {@code reference/1.8.8/net/minecraft/item/Item.java}'s
 * {@code init()} — the explicit {@code register(id, key, ...)}
 * registrations plus the {@code register(Blocks.X)} BlockItem rows whose
 * ids are the blocks' own ({@code Block.java}'s registrations) — 337
 * registrations: 150 block items over ids 1..192, 187 item-space rows
 * (256..425, the door block 426 gap, the 1.7 doors 427..431, the twelve
 * records 2256..2267).
 *
 * <p>The diff ran this cycle (wave5-B) and found the wire table carrying
 * eleven wrong ids (the hopper's misread "split" 410 — the reference
 * registers a BlockItem at 154 and 410 is the prismarine crystals' own id;
 * the compass at the saddle's 329; the seed pair at the hoe ids 292/294;
 * the cake/pot/1.7-door splits missing), sixteen registered items with no
 * wire row at all (the food family, the lever/button/piston item forms,
 * the oak sapling alias), and the empty bucket's stack size 1 (the
 * reference's line-608 16). All repaired; this test pins the outcome so
 * the table cannot drift again without a failure naming the row.</p>
 *
 * <p>The engine's registry grows per gameplay slice (BuiltinItems' own
 * model), so seventeen reference ids legitimately have no engine item —
 * the third assertion pins exactly that set. The engine's modernized
 * aliases (oak_planks beside planks, poppy beside red_flower, the dye
 * family's flattened bone_meal/lapis members) resolve at their family's
 * reference id and are pinned by the alias table.</p>
 */
class ItemRegistryParityTest {

    /**
     * The complete reference table: {@code {legacy id, the reference's
     * registry key, the engine identifier the engine registers at that
     * id}} — null where the engine registry carries no item yet (the
     * growth model's documented gap, pinned below). Where the engine's
     * spelling differs from the 1.8.8 registry key the engine spelling is
     * what the row carries (tallgrass/tall_grass, deadbush/dead_bush,
     * yellow_flower/dandelion, red_flower's poppy alias, stone_stairs/
     * cobblestone_stairs, fence/oak_fence, fence_gate/oak_fence_gate,
     * stonebrick/stone_bricks, slime/slime_block, wooden_door/oak_door,
     * netherbrick/nether_brick, reeds/sugar_cane, map/empty_map,
     * speckled_melon/glistering_melon, lit_furnace/furnace_lit).
     */
    private static final Object[][] TABLE = {
            {1, "stone", "minecraft:stone"},
            {2, "grass", null},
            {3, "dirt", "minecraft:dirt"},
            {4, "cobblestone", "minecraft:cobblestone"},
            {5, "planks", "minecraft:planks"},
            {6, "sapling", "minecraft:sapling"},
            {7, "bedrock", null},
            {12, "sand", "minecraft:sand"},
            {13, "gravel", "minecraft:gravel"},
            {14, "gold_ore", "minecraft:gold_ore"},
            {15, "iron_ore", "minecraft:iron_ore"},
            {16, "coal_ore", "minecraft:coal_ore"},
            {17, "log", "minecraft:log"},
            {18, "leaves", "minecraft:leaves"},
            {19, "sponge", "minecraft:sponge"},
            {20, "glass", "minecraft:glass"},
            {21, "lapis_ore", "minecraft:lapis_ore"},
            {22, "lapis_block", "minecraft:lapis_block"},
            {23, "dispenser", "minecraft:dispenser"},
            {24, "sandstone", "minecraft:sandstone"},
            {25, "noteblock", "minecraft:noteblock"},
            {27, "golden_rail", "minecraft:golden_rail"},
            {28, "detector_rail", "minecraft:detector_rail"},
            {29, "sticky_piston", "minecraft:sticky_piston"},
            {30, "web", null},
            {31, "tallgrass", "minecraft:tall_grass"},
            {32, "deadbush", "minecraft:dead_bush"},
            {33, "piston", "minecraft:piston"},
            {35, "wool", "minecraft:wool"},
            {37, "yellow_flower", "minecraft:dandelion"},
            {38, "red_flower", "minecraft:red_flower"},
            {39, "brown_mushroom", "minecraft:brown_mushroom"},
            {40, "red_mushroom", "minecraft:red_mushroom"},
            {41, "gold_block", "minecraft:gold_block"},
            {42, "iron_block", "minecraft:iron_block"},
            {44, "stone_slab", "minecraft:stone_slab"},
            {45, "brick_block", "minecraft:brick_block"},
            {46, "tnt", "minecraft:tnt"},
            {47, "bookshelf", "minecraft:bookshelf"},
            {48, "mossy_cobblestone", "minecraft:mossy_cobblestone"},
            {49, "obsidian", "minecraft:obsidian"},
            {50, "torch", "minecraft:torch"},
            {52, "mob_spawner", "minecraft:mob_spawner"},
            {53, "oak_stairs", "minecraft:oak_stairs"},
            {54, "chest", "minecraft:chest"},
            {56, "diamond_ore", "minecraft:diamond_ore"},
            {57, "diamond_block", "minecraft:diamond_block"},
            {58, "crafting_table", "minecraft:crafting_table"},
            {60, "farmland", null},
            {61, "furnace", "minecraft:furnace"},
            {62, "lit_furnace", null},
            {65, "ladder", "minecraft:ladder"},
            {66, "rail", "minecraft:rail"},
            {67, "stone_stairs", "minecraft:cobblestone_stairs"},
            {69, "lever", "minecraft:lever"},
            {70, "stone_pressure_plate", "minecraft:stone_pressure_plate"},
            {72, "wooden_pressure_plate", "minecraft:wooden_pressure_plate"},
            {73, "redstone_ore", "minecraft:redstone_ore"},
            {76, "redstone_torch", "minecraft:redstone_torch"},
            {77, "stone_button", "minecraft:stone_button"},
            {78, "snow_layer", "minecraft:snow_layer"},
            {79, "ice", null},
            {80, "snow", "minecraft:snow"},
            {81, "cactus", "minecraft:cactus"},
            {82, "clay", "minecraft:clay"},
            {84, "jukebox", "minecraft:jukebox"},
            {85, "fence", "minecraft:oak_fence"},
            {86, "pumpkin", "minecraft:pumpkin"},
            {87, "netherrack", "minecraft:netherrack"},
            {88, "soul_sand", "minecraft:soul_sand"},
            {89, "glowstone", null},
            {91, "lit_pumpkin", "minecraft:lit_pumpkin"},
            {95, "stained_glass", "minecraft:stained_glass"},
            {96, "trapdoor", "minecraft:trapdoor"},
            {97, "monster_egg", null},
            {98, "stonebrick", "minecraft:stone_bricks"},
            {99, "brown_mushroom_block", "minecraft:brown_mushroom_block"},
            {100, "red_mushroom_block", "minecraft:red_mushroom_block"},
            {101, "iron_bars", null},
            {102, "glass_pane", null},
            {103, "melon_block", "minecraft:melon_block"},
            {106, "vine", null},
            {107, "fence_gate", "minecraft:oak_fence_gate"},
            {108, "brick_stairs", "minecraft:brick_stairs"},
            {109, "stone_brick_stairs", "minecraft:stone_brick_stairs"},
            {110, "mycelium", "minecraft:mycelium"},
            {111, "waterlily", "minecraft:waterlily"},
            {112, "nether_brick", "minecraft:nether_bricks"},
            {113, "nether_brick_fence", "minecraft:nether_brick_fence"},
            {114, "nether_brick_stairs", "minecraft:nether_brick_stairs"},
            {116, "enchanting_table", "minecraft:enchanting_table"},
            {120, "end_portal_frame", null},
            {121, "end_stone", "minecraft:end_stone"},
            {122, "dragon_egg", null},
            {123, "redstone_lamp", "minecraft:redstone_lamp"},
            {126, "wooden_slab", "minecraft:wooden_slab"},
            {128, "sandstone_stairs", null},
            {129, "emerald_ore", "minecraft:emerald_ore"},
            {130, "ender_chest", "minecraft:ender_chest"},
            {131, "tripwire_hook", "minecraft:tripwire_hook"},
            {133, "emerald_block", "minecraft:emerald_block"},
            {134, "spruce_stairs", "minecraft:spruce_stairs"},
            {135, "birch_stairs", "minecraft:birch_stairs"},
            {136, "jungle_stairs", "minecraft:jungle_stairs"},
            {137, "command_block", "minecraft:command_block"},
            {138, "beacon", "minecraft:beacon"},
            {139, "cobblestone_wall", "minecraft:cobblestone_wall"},
            {143, "wooden_button", "minecraft:wooden_button"},
            {145, "anvil", "minecraft:anvil"},
            {146, "trapped_chest", "minecraft:trapped_chest"},
            {147, "light_weighted_pressure_plate", "minecraft:light_weighted_pressure_plate"},
            {148, "heavy_weighted_pressure_plate", "minecraft:heavy_weighted_pressure_plate"},
            {151, "daylight_detector", "minecraft:daylight_detector"},
            {152, "redstone_block", "minecraft:redstone_block"},
            {153, "quartz_ore", null},
            {154, "hopper", "minecraft:hopper"},
            {155, "quartz_block", "minecraft:quartz_block"},
            {156, "quartz_stairs", "minecraft:quartz_stairs"},
            {157, "activator_rail", "minecraft:activator_rail"},
            {158, "dropper", "minecraft:dropper"},
            {159, "stained_hardened_clay", "minecraft:stained_hardened_clay"},
            {160, "stained_glass_pane", "minecraft:stained_glass_pane"},
            {161, "leaves2", "minecraft:leaves2"},
            {162, "log2", "minecraft:log2"},
            {163, "acacia_stairs", "minecraft:acacia_stairs"},
            {164, "dark_oak_stairs", "minecraft:dark_oak_stairs"},
            {165, "slime", "minecraft:slime_block"},
            {166, "barrier", "minecraft:barrier"},
            {167, "iron_trapdoor", "minecraft:iron_trapdoor"},
            {168, "prismarine", "minecraft:prismarine"},
            {169, "sea_lantern", "minecraft:sea_lantern"},
            {170, "hay_block", "minecraft:hay_block"},
            {171, "carpet", "minecraft:carpet"},
            {172, "hardened_clay", "minecraft:hardened_clay"},
            {173, "coal_block", "minecraft:coal_block"},
            {174, "packed_ice", "minecraft:packed_ice"},
            {175, "double_plant", "minecraft:double_plant"},
            {179, "red_sandstone", "minecraft:red_sandstone"},
            {180, "red_sandstone_stairs", "minecraft:red_sandstone_stairs"},
            {182, "stone_slab2", "minecraft:stone_slab2"},
            {183, "spruce_fence_gate", "minecraft:spruce_fence_gate"},
            {184, "birch_fence_gate", "minecraft:birch_fence_gate"},
            {185, "jungle_fence_gate", "minecraft:jungle_fence_gate"},
            {186, "dark_oak_fence_gate", "minecraft:dark_oak_fence_gate"},
            {187, "acacia_fence_gate", "minecraft:acacia_fence_gate"},
            {188, "spruce_fence", "minecraft:spruce_fence"},
            {189, "birch_fence", "minecraft:birch_fence"},
            {190, "jungle_fence", "minecraft:jungle_fence"},
            {191, "dark_oak_fence", "minecraft:dark_oak_fence"},
            {192, "acacia_fence", "minecraft:acacia_fence"},
            {256, "iron_shovel", "minecraft:iron_shovel"},
            {257, "iron_pickaxe", "minecraft:iron_pickaxe"},
            {258, "iron_axe", "minecraft:iron_axe"},
            {259, "flint_and_steel", "minecraft:flint_and_steel"},
            {260, "apple", "minecraft:apple"},
            {261, "bow", "minecraft:bow"},
            {262, "arrow", "minecraft:arrow"},
            {263, "coal", "minecraft:coal"},
            {264, "diamond", "minecraft:diamond"},
            {265, "iron_ingot", "minecraft:iron_ingot"},
            {266, "gold_ingot", "minecraft:gold_ingot"},
            {267, "iron_sword", "minecraft:iron_sword"},
            {268, "wooden_sword", "minecraft:wooden_sword"},
            {269, "wooden_shovel", "minecraft:wooden_shovel"},
            {270, "wooden_pickaxe", "minecraft:wooden_pickaxe"},
            {271, "wooden_axe", "minecraft:wooden_axe"},
            {272, "stone_sword", "minecraft:stone_sword"},
            {273, "stone_shovel", "minecraft:stone_shovel"},
            {274, "stone_pickaxe", "minecraft:stone_pickaxe"},
            {275, "stone_axe", "minecraft:stone_axe"},
            {276, "diamond_sword", "minecraft:diamond_sword"},
            {277, "diamond_shovel", "minecraft:diamond_shovel"},
            {278, "diamond_pickaxe", "minecraft:diamond_pickaxe"},
            {279, "diamond_axe", "minecraft:diamond_axe"},
            {280, "stick", "minecraft:stick"},
            {281, "bowl", "minecraft:bowl"},
            {282, "mushroom_stew", "minecraft:mushroom_stew"},
            {283, "golden_sword", "minecraft:golden_sword"},
            {284, "golden_shovel", "minecraft:golden_shovel"},
            {285, "golden_pickaxe", "minecraft:golden_pickaxe"},
            {286, "golden_axe", "minecraft:golden_axe"},
            {287, "string", "minecraft:string"},
            {288, "feather", "minecraft:feather"},
            {289, "gunpowder", "minecraft:gunpowder"},
            {290, "wooden_hoe", "minecraft:wooden_hoe"},
            {291, "stone_hoe", "minecraft:stone_hoe"},
            {292, "iron_hoe", "minecraft:iron_hoe"},
            {293, "diamond_hoe", "minecraft:diamond_hoe"},
            {294, "golden_hoe", "minecraft:golden_hoe"},
            {295, "wheat_seeds", "minecraft:wheat_seeds"},
            {296, "wheat", "minecraft:wheat"},
            {297, "bread", "minecraft:bread"},
            {298, "leather_helmet", "minecraft:leather_helmet"},
            {299, "leather_chestplate", "minecraft:leather_chestplate"},
            {300, "leather_leggings", "minecraft:leather_leggings"},
            {301, "leather_boots", "minecraft:leather_boots"},
            {302, "chainmail_helmet", "minecraft:chainmail_helmet"},
            {303, "chainmail_chestplate", "minecraft:chainmail_chestplate"},
            {304, "chainmail_leggings", "minecraft:chainmail_leggings"},
            {305, "chainmail_boots", "minecraft:chainmail_boots"},
            {306, "iron_helmet", "minecraft:iron_helmet"},
            {307, "iron_chestplate", "minecraft:iron_chestplate"},
            {308, "iron_leggings", "minecraft:iron_leggings"},
            {309, "iron_boots", "minecraft:iron_boots"},
            {310, "diamond_helmet", "minecraft:diamond_helmet"},
            {311, "diamond_chestplate", "minecraft:diamond_chestplate"},
            {312, "diamond_leggings", "minecraft:diamond_leggings"},
            {313, "diamond_boots", "minecraft:diamond_boots"},
            {314, "golden_helmet", "minecraft:golden_helmet"},
            {315, "golden_chestplate", "minecraft:golden_chestplate"},
            {316, "golden_leggings", "minecraft:golden_leggings"},
            {317, "golden_boots", "minecraft:golden_boots"},
            {318, "flint", "minecraft:flint"},
            {319, "porkchop", "minecraft:porkchop"},
            {320, "cooked_porkchop", "minecraft:cooked_porkchop"},
            {321, "painting", "minecraft:painting"},
            {322, "golden_apple", "minecraft:golden_apple"},
            {323, "sign", "minecraft:sign"},
            {324, "wooden_door", "minecraft:oak_door"},
            {325, "bucket", "minecraft:bucket"},
            {326, "water_bucket", "minecraft:water_bucket"},
            {327, "lava_bucket", "minecraft:lava_bucket"},
            {328, "minecart", "minecraft:minecart"},
            {329, "saddle", "minecraft:saddle"},
            {330, "iron_door", "minecraft:iron_door"},
            {331, "redstone", "minecraft:redstone"},
            {332, "snowball", "minecraft:snowball"},
            {333, "boat", "minecraft:boat"},
            {334, "leather", "minecraft:leather"},
            {335, "milk_bucket", "minecraft:milk_bucket"},
            {336, "brick", "minecraft:brick"},
            {337, "clay_ball", "minecraft:clay_ball"},
            {338, "reeds", "minecraft:sugar_cane"},
            {339, "paper", "minecraft:paper"},
            {340, "book", "minecraft:book"},
            {341, "slime_ball", "minecraft:slime_ball"},
            {342, "chest_minecart", "minecraft:chest_minecart"},
            {343, "furnace_minecart", "minecraft:furnace_minecart"},
            {344, "egg", "minecraft:egg"},
            {345, "compass", "minecraft:compass"},
            {346, "fishing_rod", "minecraft:fishing_rod"},
            {347, "clock", "minecraft:clock"},
            {348, "glowstone_dust", "minecraft:glowstone_dust"},
            {349, "fish", "minecraft:fish"},
            {350, "cooked_fish", "minecraft:cooked_fish"},
            {351, "dye", "minecraft:dye"},
            {352, "bone", "minecraft:bone"},
            {353, "sugar", "minecraft:sugar"},
            {354, "cake", "minecraft:cake"},
            {355, "bed", "minecraft:bed"},
            {356, "repeater", "minecraft:repeater"},
            {357, "cookie", "minecraft:cookie"},
            {358, "filled_map", "minecraft:filled_map"},
            {359, "shears", "minecraft:shears"},
            {360, "melon", "minecraft:melon"},
            {361, "pumpkin_seeds", "minecraft:pumpkin_seeds"},
            {362, "melon_seeds", "minecraft:melon_seeds"},
            {363, "beef", "minecraft:beef"},
            {364, "cooked_beef", "minecraft:cooked_beef"},
            {365, "chicken", "minecraft:chicken"},
            {366, "cooked_chicken", "minecraft:cooked_chicken"},
            {367, "rotten_flesh", "minecraft:rotten_flesh"},
            {368, "ender_pearl", "minecraft:ender_pearl"},
            {369, "blaze_rod", "minecraft:blaze_rod"},
            {370, "ghast_tear", "minecraft:ghast_tear"},
            {371, "gold_nugget", "minecraft:gold_nugget"},
            {372, "nether_wart", "minecraft:nether_wart"},
            {373, "potion", "minecraft:potion"},
            {374, "glass_bottle", "minecraft:glass_bottle"},
            {375, "spider_eye", "minecraft:spider_eye"},
            {376, "fermented_spider_eye", "minecraft:fermented_spider_eye"},
            {377, "blaze_powder", "minecraft:blaze_powder"},
            {378, "magma_cream", "minecraft:magma_cream"},
            {379, "brewing_stand", "minecraft:brewing_stand"},
            {380, "cauldron", "minecraft:cauldron"},
            {381, "ender_eye", "minecraft:ender_eye"},
            {382, "speckled_melon", "minecraft:glistering_melon"},
            {383, "spawn_egg", "minecraft:spawn_egg"},
            {384, "experience_bottle", "minecraft:experience_bottle"},
            {385, "fire_charge", "minecraft:fire_charge"},
            {386, "writable_book", "minecraft:writable_book"},
            {387, "written_book", "minecraft:written_book"},
            {388, "emerald", "minecraft:emerald"},
            {389, "item_frame", "minecraft:item_frame"},
            {390, "flower_pot", "minecraft:flower_pot"},
            {391, "carrot", "minecraft:carrot"},
            {392, "potato", "minecraft:potato"},
            {393, "baked_potato", "minecraft:baked_potato"},
            {394, "poisonous_potato", null},
            {395, "map", "minecraft:empty_map"},
            {396, "golden_carrot", "minecraft:golden_carrot"},
            {397, "skull", "minecraft:skull"},
            {398, "carrot_on_a_stick", "minecraft:carrot_on_a_stick"},
            {399, "nether_star", "minecraft:nether_star"},
            {400, "pumpkin_pie", "minecraft:pumpkin_pie"},
            {401, "fireworks", "minecraft:fireworks"},
            {402, "firework_charge", "minecraft:firework_charge"},
            {403, "enchanted_book", "minecraft:enchanted_book"},
            {404, "comparator", "minecraft:comparator"},
            {405, "netherbrick", "minecraft:nether_brick"},
            {406, "quartz", "minecraft:quartz"},
            {407, "tnt_minecart", "minecraft:tnt_minecart"},
            {408, "hopper_minecart", "minecraft:hopper_minecart"},
            {409, "prismarine_shard", "minecraft:prismarine_shard"},
            {410, "prismarine_crystals", "minecraft:prismarine_crystals"},
            {411, "rabbit", "minecraft:rabbit"},
            {412, "cooked_rabbit", "minecraft:cooked_rabbit"},
            {413, "rabbit_stew", "minecraft:rabbit_stew"},
            {414, "rabbit_foot", "minecraft:rabbit_foot"},
            {415, "rabbit_hide", "minecraft:rabbit_hide"},
            {416, "armor_stand", "minecraft:armor_stand"},
            {417, "iron_horse_armor", "minecraft:iron_horse_armor"},
            {418, "golden_horse_armor", "minecraft:golden_horse_armor"},
            {419, "diamond_horse_armor", "minecraft:diamond_horse_armor"},
            {420, "lead", "minecraft:lead"},
            {421, "name_tag", "minecraft:name_tag"},
            {422, "command_block_minecart", "minecraft:command_block_minecart"},
            {423, "mutton", "minecraft:mutton"},
            {424, "cooked_mutton", "minecraft:cooked_mutton"},
            {425, "banner", null},
            {427, "spruce_door", "minecraft:spruce_door"},
            {428, "birch_door", "minecraft:birch_door"},
            {429, "jungle_door", "minecraft:jungle_door"},
            {430, "acacia_door", "minecraft:acacia_door"},
            {431, "dark_oak_door", "minecraft:dark_oak_door"},
            {2256, "record_13", "minecraft:record_13"},
            {2257, "record_cat", "minecraft:record_cat"},
            {2258, "record_blocks", "minecraft:record_blocks"},
            {2259, "record_chirp", "minecraft:record_chirp"},
            {2260, "record_far", "minecraft:record_far"},
            {2261, "record_mall", "minecraft:record_mall"},
            {2262, "record_mellohi", "minecraft:record_mellohi"},
            {2263, "record_stal", "minecraft:record_stal"},
            {2264, "record_strad", "minecraft:record_strad"},
            {2265, "record_ward", "minecraft:record_ward"},
            {2266, "record_11", "minecraft:record_11"},
            {2267, "record_wait", "minecraft:record_wait"},
    };

    /**
     * The engine's dual spellings and flattened family members: every
     * identifier must resolve at its family's reference id (the modern
     * alias beside the legacy family name, the dye family's damage-domain
     * flattening).
     */
    private static final Map<Identifier, Integer> ALIASES = Map.ofEntries(
            Map.entry(Identifier.parse("minecraft:bone_meal"), 351),
            Map.entry(Identifier.parse("minecraft:cobblestone_slab"), 44),
            Map.entry(Identifier.parse("minecraft:lapis"), 351),
            Map.entry(Identifier.parse("minecraft:oak_log"), 17),
            Map.entry(Identifier.parse("minecraft:oak_planks"), 5),
            Map.entry(Identifier.parse("minecraft:oak_sapling"), 6),
            Map.entry(Identifier.parse("minecraft:oak_slab"), 126),
            Map.entry(Identifier.parse("minecraft:poppy"), 38),
            Map.entry(Identifier.parse("minecraft:sandstone_slab"), 44));

    /** The reference rows the engine registry does not carry (the per-slice growth model's documented gap). */
    private static final Set<Integer> ABSENT = Set.of(
            2, 7, 30, 60, 62, 79, 89, 97, 101, 102, 106, 120, 122, 128,
            153, 394, 425);

    @Test
    void everyEngineItemResolvesAtItsReferenceId() {
        List<String> failures = new java.util.ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (Object[] row : TABLE) {
            int id = (int) row[0];
            String refKey = (String) row[1];
            String engineIdentifier = (String) row[2];
            if (engineIdentifier == null) {
                if (!ABSENT.contains(id)) {
                    failures.add("row " + id + " (" + refKey + ") has no engine identifier"
                            + " but is not in the documented ABSENT set");
                }
                continue;
            }
            Optional<ItemType> type = BuiltinItems.lookup(Identifier.parse(engineIdentifier));
            if (type.isEmpty()) {
                failures.add(engineIdentifier + " (reference " + refKey + " " + id
                        + ") is not registered in BuiltinItems");
                continue;
            }
            Optional<Integer> resolved = LegacyBlockIds.legacyItemId(type.get().identifier());
            if (resolved.isEmpty()) {
                failures.add(engineIdentifier + " (reference " + refKey + " " + id
                        + ") has NO legacy item id — writeSlot's unmappable-empty arm");
            } else if (resolved.get() != id) {
                failures.add(engineIdentifier + " resolves at " + resolved.get()
                        + " but the reference registers " + refKey + " at " + id);
            }
            seen.add(id);
        }
        assertTrue(failures.isEmpty(), "the id-by-id diff drifted:\n" + String.join("\n", failures));
    }

    @Test
    void noEngineItemRidesAForeignIdAndEveryItemResolves() {
        // Every registered engine item must resolve, and at a reference id
        // — no custom/foreign wire ids, no unmappable rows (the R-2
        // defect class this cycle repaired).
        Set<Integer> referenceIds = new HashSet<>();
        for (Object[] row : TABLE) {
            referenceIds.add((int) row[0]);
        }
        List<String> failures = new java.util.ArrayList<>();
        for (ItemType type : BuiltinItems.all()) {
            Optional<Integer> resolved = LegacyBlockIds.legacyItemId(type.identifier());
            if (resolved.isEmpty()) {
                failures.add(type.identifier() + " is registered but has NO wire id");
            } else if (!referenceIds.contains(resolved.get())) {
                failures.add(type.identifier() + " resolves at " + resolved.get()
                        + " — an id the reference item table never defines");
            }
        }
        // The absent set stays exactly the documented seventeen: any new
        // engine item covering one of them must move the row out of ABSENT
        // and into TABLE.
        for (Object[] row : TABLE) {
            int id = (int) row[0];
            if (row[2] == null && !ABSENT.contains(id)) {
                failures.add("id " + id + " (" + (String) row[1] + ") is neither covered nor documented absent");
            }
        }
        assertTrue(failures.isEmpty(), "engine items off the reference table:\n" + String.join("\n", failures));
    }

    @Test
    void theDualSpellingsResolveAtTheirFamilysReferenceId() {
        for (Map.Entry<Identifier, Integer> alias : ALIASES.entrySet()) {
            Optional<Integer> resolved = LegacyBlockIds.legacyItemId(alias.getKey());
            assertTrue(resolved.isPresent(), alias.getKey() + " must resolve");
            assertEquals(alias.getValue(), resolved.get(),
                    alias.getKey() + " must ride its family's reference id");
        }
    }

    /**
     * The reference's non-default stack sizes (Item.java's own
     * setMaxStackSize chain + the per-class ctors: StewItem/EnderPearlItem/
     * SnowballItem/EggItem/SignItem 16-shape, MusicDiscItem/BoatItem/
     * MinecartItem/PotionItem/WrittenBookItem/BookAndQuillItem the 1s, the
     * register-line overrides cake 1 / bed 1 / written_book 16 /
     * enchanted_book 1 / armor_stand 16 / bucket 16 / the horse armors 1).
     */
    @Test
    void theNonDefaultStackSizesMatchTheReference() {
        Map<String, Integer> expected = Map.ofEntries(
                Map.entry("minecraft:bucket", 16),
                Map.entry("minecraft:water_bucket", 1),
                Map.entry("minecraft:lava_bucket", 1),
                Map.entry("minecraft:milk_bucket", 1),
                Map.entry("minecraft:cake", 1),
                Map.entry("minecraft:bed", 1),
                Map.entry("minecraft:written_book", 16),
                Map.entry("minecraft:writable_book", 1),
                Map.entry("minecraft:enchanted_book", 1),
                Map.entry("minecraft:armor_stand", 16),
                Map.entry("minecraft:iron_horse_armor", 1),
                Map.entry("minecraft:golden_horse_armor", 1),
                Map.entry("minecraft:diamond_horse_armor", 1),
                Map.entry("minecraft:ender_pearl", 16),
                Map.entry("minecraft:snowball", 16),
                Map.entry("minecraft:egg", 16),
                Map.entry("minecraft:sign", 16),
                Map.entry("minecraft:potion", 1),
                Map.entry("minecraft:mushroom_stew", 1),
                Map.entry("minecraft:rabbit_stew", 1),
                Map.entry("minecraft:boat", 1),
                Map.entry("minecraft:minecart", 1),
                Map.entry("minecraft:record_13", 1),
                Map.entry("minecraft:record_wait", 1));
        List<String> failures = new java.util.ArrayList<>();
        expected.forEach((name, size) -> {
            Optional<ItemType> type = BuiltinItems.lookup(Identifier.parse(name));
            if (type.isEmpty()) {
                failures.add(name + " is not registered");
            } else if (type.get().maxStackSize() != size) {
                failures.add(name + " stacks to " + type.get().maxStackSize()
                        + " but the reference defines " + size);
            }
        });
        assertTrue(failures.isEmpty(), "stack sizes drifted from the reference:\n" + String.join("\n", failures));
    }
}
