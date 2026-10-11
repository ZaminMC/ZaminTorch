package net.zaminmc.torch.protocol.v1_8;

import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.server.block.FluidBlocks;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Legacy numeric block/item id translation for protocol 47. This table is
 * translation data at the protocol boundary only (§579): the engine uses
 * canonical identifiers; the wire of 1.8.8 uses numeric ids. Despite the
 * historical name it carries item ids too - slots, item entities and the
 * creative inventory all speak the same numeric space.
 */
final class LegacyBlockIds {

    private LegacyBlockIds() {
    }

    private static final Map<Identifier, Integer> BY_IDENTIFIER = withRedstoneIds(Map.ofEntries(
            // blocks
            Map.entry(Identifier.parse("minecraft:air"), 0),
            Map.entry(Identifier.parse("minecraft:stone"), 1),
            Map.entry(Identifier.parse("minecraft:grass_block"), 2),
            Map.entry(Identifier.parse("minecraft:dirt"), 3),
            Map.entry(Identifier.parse("minecraft:cobblestone"), 4),
            Map.entry(Identifier.parse("minecraft:oak_planks"), 5),   // dataset/legacy name: "planks"
            Map.entry(Identifier.parse("minecraft:bedrock"), 7),
            Map.entry(Identifier.parse("minecraft:coal_ore"), 16),
            Map.entry(Identifier.parse("minecraft:iron_ore"), 15),
            Map.entry(Identifier.parse("minecraft:diamond_ore"), 56),
            Map.entry(Identifier.parse("minecraft:oak_log"), 17),     // dataset/legacy name: "log"
            Map.entry(Identifier.parse("minecraft:oak_leaves"), 18),  // dataset blocks.json id
            Map.entry(Identifier.parse("minecraft:gold_ore"), 14),    // dataset blocks.json id
            Map.entry(Identifier.parse("minecraft:redstone_ore"), 73),// dataset blocks.json id
            Map.entry(Identifier.parse("minecraft:sand"), 12),        // dataset blocks.json id
            Map.entry(Identifier.parse("minecraft:gravel"), 13),      // dataset blocks.json id
            Map.entry(Identifier.parse("minecraft:glass"), 20),       // dataset blocks.json id
            Map.entry(Identifier.parse("minecraft:crafting_table"), 58),
            // The enchanting corner (legacy ids: bookshelf block-item 47,
            // the table 116, lapis the dye 351 whose damage 4 is the blue
            // the table's slot gate reads, the enchanted book 403).
            Map.entry(Identifier.parse("minecraft:bookshelf"), 47),
            Map.entry(Identifier.parse("minecraft:enchanting_table"), 116),
            Map.entry(Identifier.parse("minecraft:lapis"), 351),
            Map.entry(Identifier.parse("minecraft:enchanted_book"), 403),
            Map.entry(Identifier.parse("minecraft:torch"), 50),
            Map.entry(Identifier.parse("minecraft:fire"), 51),
            // The nether's materials (community blocks.json ids): netherrack
            // 87, soul sand 88, glowstone 89, quartz ore 153, the mushrooms
            // 39/40 (the block-item pair the flora drops ride).
            Map.entry(Identifier.parse("minecraft:netherrack"), 87),
            Map.entry(Identifier.parse("minecraft:soul_sand"), 88),
            Map.entry(Identifier.parse("minecraft:glowstone"), 89),
            Map.entry(Identifier.parse("minecraft:quartz_ore"), 153),
            Map.entry(Identifier.parse("minecraft:brown_mushroom"), 39),
            Map.entry(Identifier.parse("minecraft:red_mushroom"), 40),
            Map.entry(Identifier.parse("minecraft:nether_portal"), 90),
            Map.entry(Identifier.parse("minecraft:nether_portal_z"), 90),
            Map.entry(Identifier.parse("minecraft:furnace"), 61),
            Map.entry(Identifier.parse("minecraft:furnace_lit"), 62),
            Map.entry(Identifier.parse("minecraft:chest"), 54),
            // Fluid-contact products (the historical outcomes) and the sheep's
            // block-item wool (legacy 35).
            Map.entry(Identifier.parse("minecraft:obsidian"), 49),
            // The TNT block (Slice 9f, legacy 46): the block item shares the id.
            Map.entry(Identifier.parse("minecraft:tnt"), 46),
            // The dispenser family's item forms (Slice 11a): the block forms
            // share the ids 23/125 (the flattened state families register
            // their own entries below); the fire charge is item-only 385.
            Map.entry(Identifier.parse("minecraft:dispenser"), 23),
            Map.entry(Identifier.parse("minecraft:dropper"), 125),
            Map.entry(Identifier.parse("minecraft:fire_charge"), 385),
            // The potion family (Slice 11b, community items.json): the
            // potion 373 with the effect set riding the damage field, the
            // glass bottle 374.
            Map.entry(Identifier.parse("minecraft:potion"), 373),
            Map.entry(Identifier.parse("minecraft:glass_bottle"), 374),
            // The brewing family's items (Slice 11e, the reference's Item.java
            // registrations): the ingredients — ghast tear 370, nether
            // wart 372, spider eye 375, fermented spider eye 376, blaze
            // powder 377, magma cream 378, glistering melon 382 (the
            // reference's "speckled_melon"), rabbit foot 414. The stand's
            // ITEM form 379 lives in ITEM_OVERRIDES (the block keeps 117).
            Map.entry(Identifier.parse("minecraft:ghast_tear"), 370),
            Map.entry(Identifier.parse("minecraft:nether_wart"), 372),
            Map.entry(Identifier.parse("minecraft:spider_eye"), 375),
            Map.entry(Identifier.parse("minecraft:fermented_spider_eye"), 376),
            Map.entry(Identifier.parse("minecraft:blaze_powder"), 377),
            Map.entry(Identifier.parse("minecraft:magma_cream"), 378),
            Map.entry(Identifier.parse("minecraft:glistering_melon"), 382),
            Map.entry(Identifier.parse("minecraft:rabbit_foot"), 414),
            // The fish family (Slice 11i, the reference's Item.java lines
            // 635-636): the raw fish 349 and the cooked fish 350 — the four
            // variants ride the damage field (0 cod, 1 salmon, 2 clownfish,
            // 3 pufferfish).
            Map.entry(Identifier.parse("minecraft:fish"), 349),
            Map.entry(Identifier.parse("minecraft:cooked_fish"), 350),
            Map.entry(Identifier.parse("minecraft:wool"), 35),
            // Mob loot and fluid handling of the living-night slice
            // (community items.json ids: bone 352, string 287, gunpowder 289,
            // mutton 423, cooked mutton 424, buckets 325/326/327).
            Map.entry(Identifier.parse("minecraft:bone"), 352),
            Map.entry(Identifier.parse("minecraft:string"), 287),
            Map.entry(Identifier.parse("minecraft:gunpowder"), 289),
            Map.entry(Identifier.parse("minecraft:mutton"), 423),
            Map.entry(Identifier.parse("minecraft:cooked_mutton"), 424),
            Map.entry(Identifier.parse("minecraft:bucket"), 325),
            Map.entry(Identifier.parse("minecraft:water_bucket"), 326),
            Map.entry(Identifier.parse("minecraft:lava_bucket"), 327),
            // items yielded by mining and crafting
            Map.entry(Identifier.parse("minecraft:coal"), 263),
            Map.entry(Identifier.parse("minecraft:diamond"), 264),
            Map.entry(Identifier.parse("minecraft:stick"), 280),
            // smelted materials (furnace slice)
            Map.entry(Identifier.parse("minecraft:iron_ingot"), 265),
            Map.entry(Identifier.parse("minecraft:gold_ingot"), 266),
            Map.entry(Identifier.parse("minecraft:flint_and_steel"), 259),
            Map.entry(Identifier.parse("minecraft:flint"), 318),      // the 10% gravel roll
            // The nether's item yields (community items.json ids): glowstone
            // dust 348 (the cluster's 2-4 quantityDropped roll) and nether
            // quartz 406 (the ore's drop); the mushrooms ride their block ids.
            Map.entry(Identifier.parse("minecraft:glowstone_dust"), 348),
            Map.entry(Identifier.parse("minecraft:quartz"), 406),
            // world-completion items (community items.json ids): sugar 353,
            // paper 339, book 340; the cane and cactus block-items share the
            // block ids (81/83) with the cane's item form 323-style overridden
            // below (community items.json: sugar_cane item 338).
            Map.entry(Identifier.parse("minecraft:sugar"), 353),
            Map.entry(Identifier.parse("minecraft:paper"), 339),
            Map.entry(Identifier.parse("minecraft:book"), 340),
            // foods (community foods.json legacy ids)
            Map.entry(Identifier.parse("minecraft:beef"), 363),
            Map.entry(Identifier.parse("minecraft:cooked_beef"), 364),
            // mob loot (community items.json legacy ids)
            Map.entry(Identifier.parse("minecraft:porkchop"), 319),
            Map.entry(Identifier.parse("minecraft:cooked_porkchop"), 320),
            Map.entry(Identifier.parse("minecraft:chicken"), 365),   // dataset/legacy name: "chicken"
            Map.entry(Identifier.parse("minecraft:cooked_chicken"), 366),
            Map.entry(Identifier.parse("minecraft:feather"), 288),
            Map.entry(Identifier.parse("minecraft:leather"), 334),
            Map.entry(Identifier.parse("minecraft:rotten_flesh"), 367),
            // pickaxes (dataset-verified ids and durabilities)
            Map.entry(Identifier.parse("minecraft:wooden_pickaxe"), 270),
            Map.entry(Identifier.parse("minecraft:stone_pickaxe"), 274),
            Map.entry(Identifier.parse("minecraft:iron_pickaxe"), 257),
            Map.entry(Identifier.parse("minecraft:diamond_pickaxe"), 278),
            Map.entry(Identifier.parse("minecraft:golden_pickaxe"), 285),
            // axes
            Map.entry(Identifier.parse("minecraft:wooden_axe"), 271),
            Map.entry(Identifier.parse("minecraft:stone_axe"), 275),
            Map.entry(Identifier.parse("minecraft:iron_axe"), 258),
            Map.entry(Identifier.parse("minecraft:diamond_axe"), 279),
            Map.entry(Identifier.parse("minecraft:golden_axe"), 286),
            // shovels
            Map.entry(Identifier.parse("minecraft:wooden_shovel"), 269),
            Map.entry(Identifier.parse("minecraft:stone_shovel"), 273),
            Map.entry(Identifier.parse("minecraft:iron_shovel"), 256),
            Map.entry(Identifier.parse("minecraft:diamond_shovel"), 277),
            Map.entry(Identifier.parse("minecraft:golden_shovel"), 284),
            // swords
            Map.entry(Identifier.parse("minecraft:wooden_sword"), 268),
            Map.entry(Identifier.parse("minecraft:stone_sword"), 272),
            Map.entry(Identifier.parse("minecraft:iron_sword"), 267),
            Map.entry(Identifier.parse("minecraft:diamond_sword"), 276),
            Map.entry(Identifier.parse("minecraft:golden_sword"), 283),
            // shears
            Map.entry(Identifier.parse("minecraft:shears"), 359),
            // ranged combat (community items.json ids, dataset-verified)
            Map.entry(Identifier.parse("minecraft:bow"), 261),
            Map.entry(Identifier.parse("minecraft:arrow"), 262),
            Map.entry(Identifier.parse("minecraft:snowball"), 332),
            Map.entry(Identifier.parse("minecraft:egg"), 344),
            // armor (community items.json ids: the five historical tiers)
            Map.entry(Identifier.parse("minecraft:leather_helmet"), 298),
            Map.entry(Identifier.parse("minecraft:leather_chestplate"), 299),
            Map.entry(Identifier.parse("minecraft:leather_leggings"), 300),
            Map.entry(Identifier.parse("minecraft:leather_boots"), 301),
            Map.entry(Identifier.parse("minecraft:chainmail_helmet"), 302),
            Map.entry(Identifier.parse("minecraft:chainmail_chestplate"), 303),
            Map.entry(Identifier.parse("minecraft:chainmail_leggings"), 304),
            Map.entry(Identifier.parse("minecraft:chainmail_boots"), 305),
            Map.entry(Identifier.parse("minecraft:iron_helmet"), 306),
            Map.entry(Identifier.parse("minecraft:iron_chestplate"), 307),
            Map.entry(Identifier.parse("minecraft:iron_leggings"), 308),
            Map.entry(Identifier.parse("minecraft:iron_boots"), 309),
            Map.entry(Identifier.parse("minecraft:diamond_helmet"), 310),
            Map.entry(Identifier.parse("minecraft:diamond_chestplate"), 311),
            Map.entry(Identifier.parse("minecraft:diamond_leggings"), 312),
            Map.entry(Identifier.parse("minecraft:diamond_boots"), 313),
            Map.entry(Identifier.parse("minecraft:golden_helmet"), 314),
            Map.entry(Identifier.parse("minecraft:golden_chestplate"), 315),
            Map.entry(Identifier.parse("minecraft:golden_leggings"), 316),
            Map.entry(Identifier.parse("minecraft:golden_boots"), 317),
            // decorations (community blocks.json ids: 1.8 data-value flora)
            Map.entry(Identifier.parse("minecraft:tall_grass"), 31),   // metadata 1
            Map.entry(Identifier.parse("minecraft:dead_bush"), 32),
            Map.entry(Identifier.parse("minecraft:dandelion"), 37),
            Map.entry(Identifier.parse("minecraft:poppy"), 38),
            Map.entry(Identifier.parse("minecraft:sandstone"), 24),
            // The world-completion flora (community blocks.json: reed 83,
            // cactus 81 — the block-item forms share the block ids).
            Map.entry(Identifier.parse("minecraft:sugar_cane"), 83),
            Map.entry(Identifier.parse("minecraft:cactus"), 81),
            // farming items (community items.json)
            Map.entry(Identifier.parse("minecraft:wheat_seeds"), 295),
            Map.entry(Identifier.parse("minecraft:wheat"), 296),
            Map.entry(Identifier.parse("minecraft:bread"), 297),
            Map.entry(Identifier.parse("minecraft:bone_meal"), 351),
            Map.entry(Identifier.parse("minecraft:wooden_hoe"), 290),
            Map.entry(Identifier.parse("minecraft:stone_hoe"), 291),
            Map.entry(Identifier.parse("minecraft:iron_hoe"), 292),
            Map.entry(Identifier.parse("minecraft:diamond_hoe"), 293),
            Map.entry(Identifier.parse("minecraft:golden_hoe"), 294),
            // The farming slice (community blocks.json: farmland 60, wheat
            // crop 59 — the growth age rides the metadata nibble of the
            // per-stage block types).
            Map.entry(Identifier.parse("minecraft:farmland"), 60),
            Map.entry(Identifier.parse("minecraft:farmland_wet"), 60),
            Map.entry(Identifier.parse("minecraft:wheat_stage0"), 59),
            Map.entry(Identifier.parse("minecraft:wheat_stage1"), 59),
            Map.entry(Identifier.parse("minecraft:wheat_stage2"), 59),
            Map.entry(Identifier.parse("minecraft:wheat_stage3"), 59),
            Map.entry(Identifier.parse("minecraft:wheat_stage4"), 59),
            Map.entry(Identifier.parse("minecraft:wheat_stage5"), 59),
            Map.entry(Identifier.parse("minecraft:wheat_stage6"), 59),
            Map.entry(Identifier.parse("minecraft:wheat_stage7"), 59),
            // The standing signs (community blocks.json: block 63, the
            // rotation rides the metadata nibble via metadataOf).
            Map.entry(Identifier.parse("minecraft:sign"), 63),
            Map.entry(Identifier.parse("minecraft:sign_west"), 63),
            Map.entry(Identifier.parse("minecraft:sign_north"), 63),
            Map.entry(Identifier.parse("minecraft:sign_east"), 63),
            // The wall signs (block 68): the metadata 2-5 facings.
            Map.entry(Identifier.parse("minecraft:wall_sign"), 68),
            Map.entry(Identifier.parse("minecraft:wall_sign_south"), 68),
            Map.entry(Identifier.parse("minecraft:wall_sign_west"), 68),
            Map.entry(Identifier.parse("minecraft:wall_sign_east"), 68),
            // The building vocabulary slice (community blocks.json): oak
            // door 64 (facing+open in the metadata), ladder 65 (facing),
            // oak fence 85.
            Map.entry(Identifier.parse("minecraft:oak_door"), 64),
            Map.entry(Identifier.parse("minecraft:oak_door_north"), 64),
            Map.entry(Identifier.parse("minecraft:oak_door_east"), 64),
            Map.entry(Identifier.parse("minecraft:oak_door_south"), 64),
            Map.entry(Identifier.parse("minecraft:oak_door_open"), 64),
            Map.entry(Identifier.parse("minecraft:oak_door_open_north"), 64),
            Map.entry(Identifier.parse("minecraft:oak_door_open_east"), 64),
            Map.entry(Identifier.parse("minecraft:oak_door_open_south"), 64),
            Map.entry(Identifier.parse("minecraft:oak_door_upper"), 64),
            Map.entry(Identifier.parse("minecraft:oak_door_upper_open"), 64),
            Map.entry(Identifier.parse("minecraft:ladder"), 65),
            Map.entry(Identifier.parse("minecraft:ladder_south"), 65),
            Map.entry(Identifier.parse("minecraft:ladder_west"), 65),
            Map.entry(Identifier.parse("minecraft:ladder_east"), 65),
            Map.entry(Identifier.parse("minecraft:oak_fence"), 85),
            // The bed (block 26: facing on the foot, facing|8 on the head)
            Map.entry(Identifier.parse("minecraft:bed"), 26),
            Map.entry(Identifier.parse("minecraft:bed_west"), 26),
            Map.entry(Identifier.parse("minecraft:bed_north"), 26),
            Map.entry(Identifier.parse("minecraft:bed_east"), 26),
            Map.entry(Identifier.parse("minecraft:bed_head"), 26),
            Map.entry(Identifier.parse("minecraft:bed_head_west"), 26),
            Map.entry(Identifier.parse("minecraft:bed_head_north"), 26),
            Map.entry(Identifier.parse("minecraft:bed_head_east"), 26),
            // The collision-shape slice (community blocks.json): slabs 44
            // (stone family: 0=stone 1=sandstone 3=cobble; bit 8 = top) and
            // 126 (wooden: 0=oak); stairs 53 (oak) and 67 (cobble). The
            // metadata nibble disambiguates via the damage-aware overload.
            Map.entry(Identifier.parse("minecraft:stone_slab"), 44),
            Map.entry(Identifier.parse("minecraft:stone_slab_top"), 44),
            Map.entry(Identifier.parse("minecraft:sandstone_slab"), 44),
            Map.entry(Identifier.parse("minecraft:sandstone_slab_top"), 44),
            Map.entry(Identifier.parse("minecraft:cobblestone_slab"), 44),
            Map.entry(Identifier.parse("minecraft:cobblestone_slab_top"), 44),
            Map.entry(Identifier.parse("minecraft:oak_slab"), 126),
            Map.entry(Identifier.parse("minecraft:oak_slab_top"), 126),
            Map.entry(Identifier.parse("minecraft:oak_stairs_east"), 53),
            Map.entry(Identifier.parse("minecraft:oak_stairs_west"), 53),
            Map.entry(Identifier.parse("minecraft:oak_stairs_south"), 53),
            Map.entry(Identifier.parse("minecraft:oak_stairs_north"), 53),
            Map.entry(Identifier.parse("minecraft:cobblestone_stairs_east"), 67),
            Map.entry(Identifier.parse("minecraft:cobblestone_stairs_west"), 67),
            Map.entry(Identifier.parse("minecraft:cobblestone_stairs_south"), 67),
            Map.entry(Identifier.parse("minecraft:cobblestone_stairs_north"), 67),
            // The village slice (community blocks.json): the sandstone
            // stairs 128 (the desert swap's ascending band rides the
            // metadata nibble like the oak/cobble pair), the glass pane
            // 102 and the iron bars 101, the double stone slab 43, the
            // smooth sandstone skin (24 metadata 2), and the two farm
            // crops — carrots 141 and potatoes 142, the age 0..7 in the
            // metadata like the wheat.
            Map.entry(Identifier.parse("minecraft:sandstone_stairs_east"), 128),
            Map.entry(Identifier.parse("minecraft:sandstone_stairs_west"), 128),
            Map.entry(Identifier.parse("minecraft:sandstone_stairs_south"), 128),
            Map.entry(Identifier.parse("minecraft:sandstone_stairs_north"), 128),
            Map.entry(Identifier.parse("minecraft:glass_pane"), 102),
            Map.entry(Identifier.parse("minecraft:iron_bars"), 101),
            Map.entry(Identifier.parse("minecraft:double_stone_slab"), 43),
            Map.entry(Identifier.parse("minecraft:smooth_sandstone"), 24),
            Map.entry(Identifier.parse("minecraft:carrots_stage0"), 141),
            Map.entry(Identifier.parse("minecraft:carrots_stage1"), 141),
            Map.entry(Identifier.parse("minecraft:carrots_stage2"), 141),
            Map.entry(Identifier.parse("minecraft:carrots_stage3"), 141),
            Map.entry(Identifier.parse("minecraft:carrots_stage4"), 141),
            Map.entry(Identifier.parse("minecraft:carrots_stage5"), 141),
            Map.entry(Identifier.parse("minecraft:carrots_stage6"), 141),
            Map.entry(Identifier.parse("minecraft:carrots_stage7"), 141),
            Map.entry(Identifier.parse("minecraft:potatoes_stage0"), 142),
            Map.entry(Identifier.parse("minecraft:potatoes_stage1"), 142),
            Map.entry(Identifier.parse("minecraft:potatoes_stage2"), 142),
            Map.entry(Identifier.parse("minecraft:potatoes_stage3"), 142),
            Map.entry(Identifier.parse("minecraft:potatoes_stage4"), 142),
            Map.entry(Identifier.parse("minecraft:potatoes_stage5"), 142),
            Map.entry(Identifier.parse("minecraft:potatoes_stage6"), 142),
            Map.entry(Identifier.parse("minecraft:potatoes_stage7"), 142),
            // The vehicle slice (community blocks.json + items.json): the
            // rail block 66 (orientation in the metadata), the boat item
            // 333, the minecart item 328.
            Map.entry(Identifier.parse("minecraft:rail"), 66),
            // The cobweb (Slice 12a, community blocks.json): legacy 30.
            Map.entry(Identifier.parse("minecraft:cobweb"), 30),
            Map.entry(Identifier.parse("minecraft:rail_ew"), 66),
            Map.entry(Identifier.parse("minecraft:boat"), 333),
            Map.entry(Identifier.parse("minecraft:minecart"), 328),
            // The mount slice (community items.json): saddle 329, horse
            // armors 417/418/419, lead 420, carrot on a stick 398, and the
            // villager currency (emerald 388).
            Map.entry(Identifier.parse("minecraft:saddle"), 329),
            Map.entry(Identifier.parse("minecraft:iron_horse_armor"), 417),
            Map.entry(Identifier.parse("minecraft:golden_horse_armor"), 418),
            Map.entry(Identifier.parse("minecraft:diamond_horse_armor"), 419),
            Map.entry(Identifier.parse("minecraft:lead"), 420),
            Map.entry(Identifier.parse("minecraft:carrot_on_a_stick"), 398),
            Map.entry(Identifier.parse("minecraft:emerald"), 388),
            // The stronghold set (Slice 12c — the reference's own legacy
            // ids): the stone-brick family over 98 (the mossy/cracked pair
            // in the metadata nibble), the silverfish stone-brick monster
            // egg 97/4, the smooth stone-brick slab 44/5, the spawner 52,
            // the iron door family over 71 (the oak pair's own shape:
            // the facing band + the metadata-8 upper), the end portal
            // frame's eight states over 120 (the facing band | the eye
            // bit), and the chest loot family's items — the ender pearl
            // 368, the empty map 395, the compass 329 (the reference's
            // own "compass" registry name).
            Map.entry(Identifier.parse("minecraft:stone_bricks"), 98),
            Map.entry(Identifier.parse("minecraft:mossy_stone_bricks"), 98),
            Map.entry(Identifier.parse("minecraft:cracked_stone_bricks"), 98),
            Map.entry(Identifier.parse("minecraft:infested_stone_bricks"), 97),
            Map.entry(Identifier.parse("minecraft:stone_brick_slab"), 44),
            Map.entry(Identifier.parse("minecraft:mob_spawner"), 52),
            Map.entry(Identifier.parse("minecraft:iron_door_west"), 71),
            Map.entry(Identifier.parse("minecraft:iron_door_north"), 71),
            Map.entry(Identifier.parse("minecraft:iron_door_east"), 71),
            Map.entry(Identifier.parse("minecraft:iron_door_south"), 71),
            Map.entry(Identifier.parse("minecraft:iron_door_upper"), 71),
            Map.entry(Identifier.parse("minecraft:end_portal_frame_south"), 120),
            Map.entry(Identifier.parse("minecraft:end_portal_frame_west"), 120),
            Map.entry(Identifier.parse("minecraft:end_portal_frame_north"), 120),
            Map.entry(Identifier.parse("minecraft:end_portal_frame_east"), 120),
            Map.entry(Identifier.parse("minecraft:end_portal_frame_eye_south"), 120),
            Map.entry(Identifier.parse("minecraft:end_portal_frame_eye_west"), 120),
            Map.entry(Identifier.parse("minecraft:end_portal_frame_eye_north"), 120),
            Map.entry(Identifier.parse("minecraft:end_portal_frame_eye_east"), 120),
            Map.entry(Identifier.parse("minecraft:ender_pearl"), 368),
            Map.entry(Identifier.parse("minecraft:empty_map"), 395),
            Map.entry(Identifier.parse("minecraft:compass"), 329),
            Map.entry(Identifier.parse("minecraft:iron_door"), 330),
            // The fortress set (Slice 12d): the nether-brick family — the
            // bricks 40, the fence 113, the stairs 114 (the ascending
            // band in the metadata nibble), the wart 115 (the age 0..3)
            // and the block item trio 405/113/114.
            Map.entry(Identifier.parse("minecraft:nether_bricks"), 40),
            Map.entry(Identifier.parse("minecraft:nether_brick_fence"), 113),
            Map.entry(Identifier.parse("minecraft:nether_brick_stairs_east"), 114),
            Map.entry(Identifier.parse("minecraft:nether_brick_stairs_west"), 114),
            Map.entry(Identifier.parse("minecraft:nether_brick_stairs_south"), 114),
            Map.entry(Identifier.parse("minecraft:nether_brick_stairs_north"), 114),
            Map.entry(Identifier.parse("minecraft:nether_wart_stage0"), 115),
            Map.entry(Identifier.parse("minecraft:nether_wart_stage1"), 115),
            Map.entry(Identifier.parse("minecraft:nether_wart_stage2"), 115),
            Map.entry(Identifier.parse("minecraft:nether_wart_stage3"), 115),
            Map.entry(Identifier.parse("minecraft:nether_brick_stairs"), 114),
            // The monument set (Slice 12e): the prismarine trio over 168
            // (the variant in the metadata nibble), the sea lantern 169,
            // the sponge pair over 19, the gold block 41 — and the item
            // family (the prismarine 168, the crystals 410, the shard 409,
            // the lantern 169, the sponge 19, the gold block 41).
            Map.entry(Identifier.parse("minecraft:prismarine_rough"), 168),
            Map.entry(Identifier.parse("minecraft:prismarine_bricks"), 168),
            Map.entry(Identifier.parse("minecraft:prismarine_dark"), 168),
            Map.entry(Identifier.parse("minecraft:sea_lantern"), 169),
            Map.entry(Identifier.parse("minecraft:sponge"), 19),
            Map.entry(Identifier.parse("minecraft:wet_sponge"), 19),
            Map.entry(Identifier.parse("minecraft:gold_block"), 41),
            Map.entry(Identifier.parse("minecraft:prismarine"), 168),
            Map.entry(Identifier.parse("minecraft:prismarine_crystals"), 410),
            Map.entry(Identifier.parse("minecraft:prismarine_shard"), 409),
            // The temple set (Slice 12f): the chiseled sandstone 24/3, the
            // stained clay pair 159 (the variant nibble), the mossy
            // cobblestone 48, the vine 106 (metadata 15).
            Map.entry(Identifier.parse("minecraft:chiseled_sandstone"), 24),
            Map.entry(Identifier.parse("minecraft:stained_clay_orange"), 159),
            Map.entry(Identifier.parse("minecraft:stained_clay_yellow"), 159),
            Map.entry(Identifier.parse("minecraft:mossy_cobblestone"), 48),
            Map.entry(Identifier.parse("minecraft:vine"), 106),
            Map.entry(Identifier.parse("minecraft:chiseled_stone_bricks"), 98)));

    /**
     * The redstone family's base ids (Slice 9a): the wire's sixteen power
     * variants share legacy 55 (the power rides the metadata nibble), the
     * lit torch pair 76, the unlit pair 75, the repeater pairs 93/94 (the
     * facing and delay ride the nibble — facingH | (delay-1) << 2).
     */
    private static Map<Identifier, Integer> withRedstoneIds(Map<Identifier, Integer> base) {
        Map<Identifier, Integer> merged = new HashMap<>(base);
        for (int power = 0; power <= 15; power++) {
            merged.put(Identifier.parse("minecraft:redstone_wire" + (power == 0 ? "" : "_" + power)), 55);
        }
        for (String prefix : new String[]{"redstone_torch", "unlit_redstone_torch"}) {
            merged.put(Identifier.parse("minecraft:" + prefix),
                    prefix.startsWith("unlit") ? 75 : 76);
            for (String wall : new String[]{"east", "west", "south", "north"}) {
                merged.put(Identifier.parse("minecraft:" + prefix + "_" + wall),
                        prefix.startsWith("unlit") ? 75 : 76);
            }
        }
        for (String prefix : new String[]{"repeater", "powered_repeater"}) {
            for (String facing : new String[]{"south", "west", "north", "east"}) {
                for (int delay = 1; delay <= 4; delay++) {
                    merged.put(Identifier.parse("minecraft:" + prefix + "_" + facing + "_" + delay),
                            prefix.startsWith("powered") ? 94 : 93);
                }
            }
        }
        // The comparator (Slice 9d): the unpowered pair 149, the powered
        // pair 150 (the facing, mode and powered bits ride the nibble).
        for (String prefix : new String[]{"comparator", "powered_comparator"}) {
            for (String facing : new String[]{"south", "west", "north", "east"}) {
                for (String mode : new String[]{"compare", "subtract"}) {
                    merged.put(Identifier.parse("minecraft:" + prefix + "_" + facing + "_" + mode),
                            prefix.startsWith("powered") ? 150 : 149);
                }
            }
        }
        // The piston family (Slice 9e): the base 33 plain / 29 sticky, the
        // head 34, the moving carrier 36 — the facing/sticky/extended bits
        // ride the nibble.
        for (String prefix : new String[]{"piston", "sticky_piston"}) {
            for (String facing : new String[]{"down", "up", "north", "south", "west", "east"}) {
                merged.put(Identifier.parse("minecraft:" + prefix + "_" + facing),
                        prefix.startsWith("sticky") ? 29 : 33);
                merged.put(Identifier.parse("minecraft:" + prefix + "_" + facing + "_extended"),
                        prefix.startsWith("sticky") ? 29 : 33);
            }
        }
        for (String prefix : new String[]{"piston_head", "moving_piston"}) {
            for (String facing : new String[]{"down", "up", "north", "south", "west", "east"}) {
                merged.put(Identifier.parse("minecraft:" + prefix + "_" + facing),
                        prefix.startsWith("piston_head") ? 34 : 36);
                merged.put(Identifier.parse("minecraft:" + prefix + "_" + facing + "_sticky"),
                        prefix.startsWith("piston_head") ? 34 : 36);
            }
        }
        // The hopper family (Slice 10b): legacy 154, the facing and the
        // enabled bit riding the nibble (HopperBlock lines 176-190).
        for (String facing : new String[]{"down", "north", "south", "west", "east"}) {
            merged.put(Identifier.parse("minecraft:hopper_" + facing), 154);
            merged.put(Identifier.parse("minecraft:hopper_" + facing + "_disabled"), 154);
        }
        // The dispenser families (Slice 11a): the dispenser 23, the dropper
        // 125 — the facing and the triggered bit ride the nibble
        // (DispenserBlock lines 202-215).
        for (String facing : new String[]{"down", "up", "north", "south", "west", "east"}) {
            merged.put(Identifier.parse("minecraft:dispenser_" + facing), 23);
            merged.put(Identifier.parse("minecraft:dispenser_" + facing + "_triggered"), 23);
            merged.put(Identifier.parse("minecraft:dropper_" + facing), 125);
            merged.put(Identifier.parse("minecraft:dropper_" + facing + "_triggered"), 125);
        }
        // The brewing stand family (Slice 11e): legacy 117 — the three
        // HAS_BOTTLE bits ride the nibble (BrewingStandBlock lines 145-166:
        // bit i = has_bottle_i).
        for (int bits = 0; bits < 8; bits++) {
            StringBuilder name = new StringBuilder("minecraft:brewing_stand");
            for (int slot = 0; slot < 3; slot++) {
                if ((bits & (1 << slot)) != 0) {
                    name.append("_bottle").append(slot);
                }
            }
            merged.put(Identifier.parse(name.toString()), 117);
        }
        // The tripwire family (Slice 10c): the wire 132, the hook 131 — the
        // four stored bits ride the wire's nibble, the facing/attached/
        // powered the hook's.
        for (int bits = 0; bits < 16; bits++) {
            StringBuilder name = new StringBuilder("minecraft:tripwire");
            if ((bits & 1) != 0) {
                name.append("_powered");
            }
            if ((bits & 2) != 0) {
                name.append("_suspended");
            }
            if ((bits & 4) != 0) {
                name.append("_attached");
            }
            if ((bits & 8) != 0) {
                name.append("_disarmed");
            }
            merged.put(Identifier.parse(name.toString()), 132);
        }
        for (String facing : new String[]{"south", "west", "north", "east"}) {
            for (int attached = 0; attached <= 1; attached++) {
                for (int powered = 0; powered <= 1; powered++) {
                    merged.put(Identifier.parse("minecraft:tripwire_hook_" + facing
                            + (attached == 1 ? "_attached" : "")
                            + (powered == 1 ? "_powered" : "")), 131);
                }
            }
        }
        // The player-driven sources (Slice 9c): the lever 69, the buttons
        // 77 stone / 143 wood, the plates 70 stone / 72 wood.
        for (String key : new String[]{"down_x", "east", "west", "south", "north", "up_z", "up_x", "down_z"}) {
            merged.put(Identifier.parse("minecraft:lever_" + key), 69);
            merged.put(Identifier.parse("minecraft:lever_" + key + "_powered"), 69);
        }
        for (String facing : new String[]{"down", "up", "north", "south", "west", "east"}) {
            merged.put(Identifier.parse("minecraft:stone_button_" + facing), 77);
            merged.put(Identifier.parse("minecraft:stone_button_" + facing + "_powered"), 77);
            merged.put(Identifier.parse("minecraft:wooden_button_" + facing), 143);
            merged.put(Identifier.parse("minecraft:wooden_button_" + facing + "_powered"), 143);
        }
        merged.put(Identifier.parse("minecraft:stone_pressure_plate"), 70);
        merged.put(Identifier.parse("minecraft:stone_pressure_plate_powered"), 70);
        merged.put(Identifier.parse("minecraft:wooden_pressure_plate"), 72);
        merged.put(Identifier.parse("minecraft:wooden_pressure_plate_powered"), 72);
        return Map.copyOf(merged);
    }

    /**
     * Item-form ids that diverge from the block id sharing the identifier
     * (the sign: block 63 on the chunk wire, item 323 in slots — the
     * historical split). Consulted by the slot/item wire paths only.
     * Declared before the reverse map: reverse() folds it in at init.
     */
    private static final Map<Identifier, Integer> ITEM_OVERRIDES = Map.ofEntries(
            Map.entry(Identifier.parse("minecraft:sign"), 323),
            Map.entry(Identifier.parse("minecraft:oak_door"), 324),
            Map.entry(Identifier.parse("minecraft:ladder"), 65),
            Map.entry(Identifier.parse("minecraft:bed"), 355),
            Map.entry(Identifier.parse("minecraft:sugar_cane"), 338),
            // The stairs items (the block types carry facing suffixes; the
            // item keeps the bare historical identifier, item id = block id).
            Map.entry(Identifier.parse("minecraft:oak_stairs"), 53),
            Map.entry(Identifier.parse("minecraft:cobblestone_stairs"), 67),
            // The redstone slice: the dust is item 331, the repeater item
            // 356 (the block types carry the state suffixes); the comparator
            // item 404 (the same split); the redstone
            // torch item shares block id 76 through BY_IDENTIFIER.
            Map.entry(Identifier.parse("minecraft:redstone"), 331),
            Map.entry(Identifier.parse("minecraft:repeater"), 356),
            Map.entry(Identifier.parse("minecraft:comparator"), 404),
            // The hopper item (Slice 10b): the block family keeps legacy 154,
            // the item form is 410 (the historical split — the same shape as
            // the comparator's 404).
            Map.entry(Identifier.parse("minecraft:hopper"), 410),
            // The tripwire hook item (Slice 10c): the block family keeps
            // legacy 131, the item form shares it.
            Map.entry(Identifier.parse("minecraft:tripwire_hook"), 131),
            // The brewing stand item (Slice 11e): the block family keeps
            // legacy 117, the item form is 379 (the same split as the
            // hopper's 410).
            Map.entry(Identifier.parse("minecraft:brewing_stand"), 379));

    /** The damage-aware reverse map is declared after {@link #METADATA}. */

    private static final Map<Integer, Identifier> BY_LEGACY_ID = reverse();

    private static Map<Integer, Identifier> reverse() {
        Map<Integer, Identifier> reversed = new HashMap<>();
        BY_IDENTIFIER.forEach((identifier, legacyId) -> reversed.put(legacyId, identifier));
        // The item-form overrides (sign item 323) resolve on the creative
        // and slot paths too; item ids never collide with block ids here.
        ITEM_OVERRIDES.forEach((identifier, legacyId) -> reversed.put(legacyId, identifier));
        return Map.copyOf(reversed);
    }

    /**
     * @return the legacy numeric id, or empty when this adapter cannot
     * represent the type. Fluid states resolve programmatically (still 9/11,
     * flowing 8/10 — the wire family shares one id, the level rides the
     * metadata nibble via {@link #metadataOf}).
     */
    static Optional<Integer> legacyId(Identifier identifier) {
        Integer direct = BY_IDENTIFIER.get(identifier);
        if (direct != null) {
            return Optional.of(direct);
        }
        FluidBlocks.Kind fluid = FluidBlocks.kindOf(identifier);
        if (fluid == FluidBlocks.Kind.WATER) {
            return Optional.of(FluidBlocks.isSource(identifier) ? 9 : 8);
        }
        if (fluid == FluidBlocks.Kind.LAVA) {
            return Optional.of(FluidBlocks.isSource(identifier) ? 11 : 10);
        }
        return Optional.empty();
    }

    /**
     * The 1.8 data-value variants that ride the metadata nibble (block 31's
     * flora: 0 dead shrub, 1 grass, 2 fern — the engine's tall grass is the
     * real "grass" variant on the wire).
     */
    private static final Map<Identifier, Integer> METADATA = Map.ofEntries(
            Map.entry(Identifier.parse("minecraft:tall_grass"), 1),
            Map.entry(Identifier.parse("minecraft:sign_west"), 4),
            Map.entry(Identifier.parse("minecraft:sign_north"), 8),
            Map.entry(Identifier.parse("minecraft:sign_east"), 12),
            // The wall signs: the metadata names the faced direction.
            Map.entry(Identifier.parse("minecraft:wall_sign"), 2),
            Map.entry(Identifier.parse("minecraft:wall_sign_south"), 3),
            Map.entry(Identifier.parse("minecraft:wall_sign_west"), 4),
            Map.entry(Identifier.parse("minecraft:wall_sign_east"), 5),
            Map.entry(Identifier.parse("minecraft:oak_door_north"), 1),
            Map.entry(Identifier.parse("minecraft:oak_door_east"), 2),
            Map.entry(Identifier.parse("minecraft:oak_door_south"), 3),
            Map.entry(Identifier.parse("minecraft:oak_door_open"), 4),
            Map.entry(Identifier.parse("minecraft:oak_door_open_north"), 5),
            Map.entry(Identifier.parse("minecraft:oak_door_open_east"), 6),
            Map.entry(Identifier.parse("minecraft:oak_door_open_south"), 7),
            Map.entry(Identifier.parse("minecraft:oak_door_upper"), 8),
            Map.entry(Identifier.parse("minecraft:oak_door_upper_open"), 9),
            Map.entry(Identifier.parse("minecraft:ladder_south"), 3),
            Map.entry(Identifier.parse("minecraft:ladder_west"), 4),
            Map.entry(Identifier.parse("minecraft:ladder_east"), 5),
            Map.entry(Identifier.parse("minecraft:bed_west"), 1),
            Map.entry(Identifier.parse("minecraft:bed_north"), 2),
            Map.entry(Identifier.parse("minecraft:bed_east"), 3),
            Map.entry(Identifier.parse("minecraft:bed_head"), 8),
            Map.entry(Identifier.parse("minecraft:bed_head_west"), 9),
            Map.entry(Identifier.parse("minecraft:bed_head_north"), 10),
            Map.entry(Identifier.parse("minecraft:bed_head_east"), 11),
            // The collision-shape slice: slab halves (bit 8) and materials,
            // the stairs' ascending band (E=0 W=1 S=2 N=3, Bukkit Stairs).
            Map.entry(Identifier.parse("minecraft:stone_slab_top"), 8),
            Map.entry(Identifier.parse("minecraft:sandstone_slab"), 1),
            Map.entry(Identifier.parse("minecraft:sandstone_slab_top"), 9),
            Map.entry(Identifier.parse("minecraft:cobblestone_slab"), 3),
            Map.entry(Identifier.parse("minecraft:cobblestone_slab_top"), 11),
            Map.entry(Identifier.parse("minecraft:oak_slab_top"), 8),
            Map.entry(Identifier.parse("minecraft:oak_stairs_west"), 1),
            Map.entry(Identifier.parse("minecraft:oak_stairs_south"), 2),
            Map.entry(Identifier.parse("minecraft:oak_stairs_north"), 3),
            Map.entry(Identifier.parse("minecraft:cobblestone_stairs_west"), 1),
            Map.entry(Identifier.parse("minecraft:cobblestone_stairs_south"), 2),
            Map.entry(Identifier.parse("minecraft:cobblestone_stairs_north"), 3),
            // The vehicle slice: the rail's flat orientation (0 = NS default).
            Map.entry(Identifier.parse("minecraft:rail_ew"), 1),
            // The nether portal's axis (the reference PortalBlock.getMetadata:
            // 1 the X plane, 2 the Z plane).
            Map.entry(Identifier.parse("minecraft:nether_portal"), 1),
            Map.entry(Identifier.parse("minecraft:nether_portal_z"), 2),
            // The stronghold set (Slice 12c): the stone-brick family's
            // metadata band over 98 (the reference's StonebrickBlock: 1
            // mossy, 2 cracked) with the infested egg's own variant 4
            // over 97, the smooth stone-brick slab 5 over 44 (the
            // reference's StoneSlabBlock.Variant.SMOOTHBRICK), the iron
            // door's facings (the reference's Direction band) + the
            // metadata-8 upper, and the end portal frame's eight states
            // (the facing band | the eye bit — EndPortalFrameBlock lines
            // 71-85).
            Map.entry(Identifier.parse("minecraft:mossy_stone_bricks"), 1),
            Map.entry(Identifier.parse("minecraft:cracked_stone_bricks"), 2),
            Map.entry(Identifier.parse("minecraft:chiseled_stone_bricks"), 3),
            Map.entry(Identifier.parse("minecraft:infested_stone_bricks"), 4),
            Map.entry(Identifier.parse("minecraft:stone_brick_slab"), 5),
            Map.entry(Identifier.parse("minecraft:iron_door_north"), 1),
            Map.entry(Identifier.parse("minecraft:iron_door_east"), 2),
            Map.entry(Identifier.parse("minecraft:iron_door_south"), 3),
            Map.entry(Identifier.parse("minecraft:iron_door_upper"), 8),
            Map.entry(Identifier.parse("minecraft:end_portal_frame_south"), 0),
            Map.entry(Identifier.parse("minecraft:end_portal_frame_west"), 1),
            Map.entry(Identifier.parse("minecraft:end_portal_frame_north"), 2),
            Map.entry(Identifier.parse("minecraft:end_portal_frame_east"), 3),
            Map.entry(Identifier.parse("minecraft:end_portal_frame_eye_south"), 4),
            Map.entry(Identifier.parse("minecraft:end_portal_frame_eye_west"), 5),
            Map.entry(Identifier.parse("minecraft:end_portal_frame_eye_north"), 6),
            Map.entry(Identifier.parse("minecraft:end_portal_frame_eye_east"), 7),
            // The fortress set (Slice 12d): the nether-brick stairs'
            // ascending band + the wart's age band (the reference's own
            // getStateFromMetadata nibbles).
            Map.entry(Identifier.parse("minecraft:nether_brick_stairs_west"), 1),
            Map.entry(Identifier.parse("minecraft:nether_brick_stairs_south"), 2),
            Map.entry(Identifier.parse("minecraft:nether_brick_stairs_north"), 3),
            Map.entry(Identifier.parse("minecraft:nether_wart_stage1"), 1),
            Map.entry(Identifier.parse("minecraft:nether_wart_stage2"), 2),
            Map.entry(Identifier.parse("minecraft:nether_wart_stage3"), 3),
            // The monument set (Slice 12e): the prismarine variant band
            // (the reference's PrismarineBlock) + the wet sponge bit.
            Map.entry(Identifier.parse("minecraft:prismarine_bricks"), 1),
            Map.entry(Identifier.parse("minecraft:prismarine_dark"), 2),
            Map.entry(Identifier.parse("minecraft:wet_sponge"), 1),
            // The temple set (Slice 12f): the sandstone variant band + the
            // clay variant band + the vine's attached state.
            Map.entry(Identifier.parse("minecraft:chiseled_sandstone"), 3),
            Map.entry(Identifier.parse("minecraft:stained_clay_orange"), 1),
            Map.entry(Identifier.parse("minecraft:stained_clay_yellow"), 4),
            Map.entry(Identifier.parse("minecraft:vine"), 15));

    /**
     * @return the wire metadata nibble of a block type: the 1.8 data-value
     * variants, the flow level for fluids (0 source, 1..7 flowing, 8 the
     * falling column — the historical encoding) and 0 for everything else.
     */
    static int metadataOf(Identifier identifier) {
        Integer variant = METADATA.get(identifier);
        if (variant != null) {
            return variant;
        }
        FluidBlocks.Kind fluid = FluidBlocks.kindOf(identifier);
        if (fluid != null) {
            return FluidBlocks.levelOf(identifier);
        }
        return redstoneMetadataOf(identifier);
    }

    /**
     * The redstone family's metadata nibble: the wire's power (0..15), the
     * torch's facing (1 standing, 2..5 the walls — the reference's
     * BlockTorch metadata order east/west/south/north), the repeater's
     * facingH | (delay-1) << 2 (the reference's RepeaterBlock lines
     * 114-123).
     */
    private static int redstoneMetadataOf(Identifier identifier) {
        String value = identifier.value();
        if (!identifier.namespace().equals("minecraft")) {
            return 0;
        }
        if (value.startsWith("redstone_wire")) {
            if (value.equals("redstone_wire")) {
                return 0; // the power-0 wire carries metadata 0
            }
            int underscore = value.lastIndexOf('_');
            return Integer.parseInt(value.substring(underscore + 1));
        }
        if (value.startsWith("redstone_torch") || value.startsWith("unlit_redstone_torch")) {
            String suffix = value.contains("_east") ? "east"
                    : value.contains("_west") ? "west"
                    : value.contains("_south") ? "south"
                    : value.contains("_north") ? "north" : "";
            return switch (suffix) {
                case "east" -> 2;
                case "west" -> 3;
                case "south" -> 4;
                case "north" -> 5;
                default -> 1; // standing
            };
        }
        if (value.startsWith("repeater_") || value.startsWith("powered_repeater_")) {
            int facingH = value.contains("south") ? 0 : value.contains("west") ? 1
                    : value.contains("north") ? 2 : 3;
            int delay = Integer.parseInt(value.substring(value.lastIndexOf('_') + 1));
            return facingH | ((delay - 1) << 2);
        }
        if (value.startsWith("comparator_") || value.startsWith("powered_comparator_")) {
            // facingH | (subtract ? 4 : 0) | (powered ? 8 : 0) — ComparatorBlock
            // lines 230-250 (getStateFromMetadata / getMetadataFromState).
            int facingH = value.contains("south") ? 0 : value.contains("west") ? 1
                    : value.contains("north") ? 2 : 3;
            boolean powered = value.startsWith("powered_comparator");
            boolean subtract = value.endsWith("_subtract");
            return facingH | (subtract ? 4 : 0) | (powered ? 8 : 0);
        }
        if (value.startsWith("piston_head_") || value.startsWith("moving_piston_")) {
            // facing | (sticky ? 8 : 0) — PistonHeadBlock lines 181-194 +
            // MovingBlock lines 149-158. Checked BEFORE the base arm: the
            // head's identifier also starts with "piston_".
            boolean sticky = value.endsWith("_sticky");
            String rest = sticky ? value.substring(0, value.length() - "_sticky".length()) : value;
            String facing = rest.substring(rest.lastIndexOf('_') + 1);
            return directionNibble(facing) | (sticky ? 8 : 0);
        }
        if (value.startsWith("sticky_piston_") || (value.startsWith("piston_")
                && !value.startsWith("piston_head_"))) {
            // facing | (extended ? 8 : 0) — PistonBaseBlock lines 357-370.
            boolean extended = value.endsWith("_extended");
            String rest = extended ? value.substring(0, value.length() - "_extended".length()) : value;
            String facing = rest.substring(rest.lastIndexOf('_') + 1);
            return directionNibble(facing) | (extended ? 8 : 0);
        }
        if (value.startsWith("hopper_")) {
            // facing | (disabled ? 8 : 0) — HopperBlock lines 176-190
            // (getFacing reads metadata & 7, isEnabled reads (metadata & 8) != 8).
            boolean disabled = value.endsWith("_disabled");
            String rest = disabled
                    ? value.substring(0, value.length() - "_disabled".length()) : value;
            String facing = rest.substring(rest.lastIndexOf('_') + 1);
            return directionNibble(facing) | (disabled ? 8 : 0);
        }
        if (value.startsWith("dispenser_") || value.startsWith("dropper_")) {
            // facing | (triggered ? 8 : 0) — DispenserBlock lines 202-215
            // (getDirection reads id & 7, TRIGGERED reads (metadata & 8) > 0).
            boolean triggered = value.endsWith("_triggered");
            String rest = triggered
                    ? value.substring(0, value.length() - "_triggered".length()) : value;
            String facing = rest.substring(rest.lastIndexOf('_') + 1);
            return directionNibble(facing) | (triggered ? 8 : 0);
        }
        if (value.startsWith("brewing_stand") && value.length() > "brewing_stand".length()) {
            // has_bottle_i << i — BrewingStandBlock lines 145-153 (bit i =
            // has_bottle_i; the bare "brewing_stand" carries no suffix).
            int bits = 0;
            if (value.contains("_bottle0")) {
                bits |= 1;
            }
            if (value.contains("_bottle1")) {
                bits |= 2;
            }
            if (value.contains("_bottle2")) {
                bits |= 4;
            }
            return bits;
        }
        if (value.startsWith("tripwire_hook_")) {
            // facingH | (attached ? 4 : 0) | (powered ? 8 : 0) —
            // TripwireHookBlock lines 271-283.
            boolean powered = value.endsWith("_powered");
            boolean attached = value.contains("_attached");
            String rest = value.substring("tripwire_hook_".length());
            int cut = rest.indexOf('_');
            String facing = cut < 0 ? rest : rest.substring(0, cut);
            int facingH = switch (facing) {
                case "south" -> 0;
                case "west" -> 1;
                case "north" -> 2;
                default -> 3; // east
            };
            return facingH | (attached ? 4 : 0) | (powered ? 8 : 0);
        }
        if (value.startsWith("tripwire")) {
            // powered | suspended<<1 | attached<<2 | disarmed<<3 —
            // TripwireBlock lines 226-249.
            int bits = 0;
            if (value.contains("_powered")) {
                bits |= 1;
            }
            if (value.contains("_suspended")) {
                bits |= 2;
            }
            if (value.contains("_attached")) {
                bits |= 4;
            }
            if (value.endsWith("_disarmed")) {
                bits |= 8;
            }
            return bits;
        }
        if (value.startsWith("lever_")) {
            // The facing id | the powered bit (LeverBlock lines 258-263).
            boolean powered = value.endsWith("_powered");
            String rest = powered ? value.substring(0, value.length() - "_powered".length()) : value;
            int id = switch (rest) {
                case "lever_down_x" -> 0;
                case "lever_east" -> 1;
                case "lever_west" -> 2;
                case "lever_south" -> 3;
                case "lever_north" -> 4;
                case "lever_up_z" -> 5;
                case "lever_up_x" -> 6;
                case "lever_down_z" -> 7;
                default -> 0;
            };
            return id | (powered ? 8 : 0);
        }
        if (value.startsWith("stone_button_") || value.startsWith("wooden_button_")) {
            // The FACING id | the powered bit (ButtonBlock's metadata).
            boolean powered = value.endsWith("_powered");
            String rest = powered ? value.substring(0, value.length() - "_powered".length()) : value;
            String facing = rest.substring(rest.lastIndexOf('_') + 1);
            int id = switch (facing) {
                case "down" -> 0;
                case "up" -> 1;
                case "north" -> 2;
                case "south" -> 3;
                case "west" -> 4;
                case "east" -> 5;
                default -> 0;
            };
            return id | (powered ? 8 : 0);
        }
        if (value.equals("stone_pressure_plate") || value.equals("stone_pressure_plate_powered")
                || value.equals("wooden_pressure_plate") || value.equals("wooden_pressure_plate_powered")) {
            // The powered bit (the plate's metadata is 0/1).
            return value.endsWith("_powered") ? 1 : 0;
        }
        return 0;
    }

    /** The damage-aware reverse: (legacy id << 4) | metadata -> identifier. */
    private static final Map<Integer, Identifier> BY_LEGACY_ID_AND_META = reverseWithMetadata();

    private static Map<Integer, Identifier> reverseWithMetadata() {
        Map<Integer, Identifier> reversed = new HashMap<>();
        METADATA.forEach((identifier, meta) -> {
            Integer legacy = BY_IDENTIFIER.get(identifier);
            if (legacy != null) {
                reversed.put((legacy << 4) | (meta & 0xF), identifier);
            }
        });
        // The redstone family folds its own nibble space in (the wire's
        // power levels, the torch facings, the repeater states) so the
        // damage-aware reverse resolves creative placements and slot reads.
        BY_IDENTIFIER.forEach((identifier, legacy) -> {
            if (isRedstoneFamily(identifier)) {
                reversed.put((legacy << 4) | redstoneMetadataOf(identifier), identifier);
            }
        });
        // The hopper family folds its own nibble space in too (the facing
        // and the enabled bit) so the damage-aware reverse resolves the
        // family's ten states.
        BY_IDENTIFIER.forEach((identifier, legacy) -> {
            if (identifier.namespace().equals("minecraft")
                    && identifier.value().startsWith("hopper_")) {
                reversed.put((legacy << 4) | metadataOf(identifier), identifier);
            }
        });
        // The dispenser families fold theirs in (the facing and the
        // triggered bit — twelve states each).
        BY_IDENTIFIER.forEach((identifier, legacy) -> {
            String value = identifier.value();
            if (identifier.namespace().equals("minecraft")
                    && (value.startsWith("dispenser_") || value.startsWith("dropper_"))) {
                reversed.put((legacy << 4) | metadataOf(identifier), identifier);
            }
        });
        // The tripwire families fold their nibble spaces in (the wire's
        // four bits, the hook's facing/attached/powered).
        BY_IDENTIFIER.forEach((identifier, legacy) -> {
            String value = identifier.value();
            if (identifier.namespace().equals("minecraft")
                    && (value.equals("tripwire") || (value.startsWith("tripwire_")
                    && !value.startsWith("tripwire_hook")))) {
                reversed.put((legacy << 4) | metadataOf(identifier), identifier);
            }
        });
        BY_IDENTIFIER.forEach((identifier, legacy) -> {
            if (identifier.namespace().equals("minecraft")
                    && identifier.value().startsWith("tripwire_hook_")) {
                reversed.put((legacy << 4) | metadataOf(identifier), identifier);
            }
        });
        return Map.copyOf(reversed);
    }

    /** @return whether the identifier belongs to the redstone family (Slice 9a). */
    private static boolean isRedstoneFamily(Identifier identifier) {
        String value = identifier.value();
        return identifier.namespace().equals("minecraft")
                && (value.startsWith("redstone_wire")
                || value.startsWith("redstone_torch")
                || value.startsWith("unlit_redstone_torch")
                || value.startsWith("repeater_")
                || value.startsWith("powered_repeater_")
                || value.startsWith("comparator_")
                || value.startsWith("powered_comparator_")
                || value.startsWith("piston_")
                || value.startsWith("sticky_piston_")
                || value.startsWith("piston_head_")
                || value.startsWith("moving_piston_"));
    }

    /** The reference direction id of a direction key (down up north south west east). */
    private static int directionNibble(String facing) {
        return switch (facing) {
            case "down" -> 0;
            case "up" -> 1;
            case "north" -> 2;
            case "south" -> 3;
            case "west" -> 4;
            case "east" -> 5;
            default -> 1;
        };
    }

    /** @return the canonical identifier for a legacy id, or empty if unknown. */
    static Optional<Identifier> identifierOf(int legacyId) {
        return Optional.ofNullable(BY_LEGACY_ID.get(legacyId));
    }

    /**
     * The metadata-aware resolution (the collision-shape slice): the slab
     * family shares legacy ids (44's stone/sandstone/cobble, the top bit),
     * so the slot's damage field disambiguates wherever the metadata table
     * has an entry for the pair.
     */
    static Optional<Identifier> identifierOf(int legacyId, int damage) {
        Identifier keyed = BY_LEGACY_ID_AND_META.get((legacyId << 4) | (damage & 0xF));
        if (keyed != null) {
            return Optional.of(keyed);
        }
        return identifierOf(legacyId);
    }

    /**
     * @return the legacy id for an ITEM slot (the item overrides first — the
     * sign is 323 in slots, never its block id — then the shared table).
     */
    static Optional<Integer> legacyItemId(Identifier identifier) {
        Integer override = ITEM_OVERRIDES.get(identifier);
        if (override != null) {
            return Optional.of(override);
        }
        return legacyId(identifier);
    }
}
