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
            // The note block (Slice 27a, legacy 25 — the reference's own
            // `register(25, "noteblock", ...)`: the block item shares the id).
            Map.entry(Identifier.parse("minecraft:noteblock"), 25),
            // The jukebox (Slice 27b, legacy 84 — the reference's Block.java
            // line-1181 `register(84, "jukebox", ...)`: the plain block, its
            // HAS_RECORD variant (the metadata nibble's bit 0), and the
            // block item all share the id).
            Map.entry(Identifier.parse("minecraft:jukebox"), 84),
            Map.entry(Identifier.parse("minecraft:jukebox_record"), 84),
            // The ender chest's item form (Slice 29a — the block family's
            // four facing states register through the merge below at legacy
            // 130; the bare item shares the id, the reference's own block
            // item).
            Map.entry(Identifier.parse("minecraft:ender_chest"), 130),
            // The daylight detector's item form (Slice 27c — Item.java
            // line 490's register(Blocks.DAYLIGHT_DETECTOR): legacy 151;
            // the block family's thirty-two states register through the
            // merge below, the inverted form (178) has no item).
            Map.entry(Identifier.parse("minecraft:daylight_detector"), 151),
            // The twelve music discs (Slice 27b — the reference's Item.java
            // lines 735-746, ids 2256-2267 in the registration order; the
            // analog signal and the play-record event carry these ids).
            Map.entry(Identifier.parse("minecraft:record_13"), 2256),
            Map.entry(Identifier.parse("minecraft:record_cat"), 2257),
            Map.entry(Identifier.parse("minecraft:record_blocks"), 2258),
            Map.entry(Identifier.parse("minecraft:record_chirp"), 2259),
            Map.entry(Identifier.parse("minecraft:record_far"), 2260),
            Map.entry(Identifier.parse("minecraft:record_mall"), 2261),
            Map.entry(Identifier.parse("minecraft:record_mellohi"), 2262),
            Map.entry(Identifier.parse("minecraft:record_stal"), 2263),
            Map.entry(Identifier.parse("minecraft:record_strad"), 2264),
            Map.entry(Identifier.parse("minecraft:record_ward"), 2265),
            Map.entry(Identifier.parse("minecraft:record_11"), 2266),
            Map.entry(Identifier.parse("minecraft:record_wait"), 2267),
            // The dispenser family's item forms (Slice 11a): the block forms
            // share the ids 23/158 (the flattened state families register
            // their own entries below); the fire charge is item-only 385.
            // (Slice 32's wire repair: the dropper is legacy 158 — the
            // original 125 was the double wooden slab's id, a real client
            // would render slabs where the dropper should be.)
            Map.entry(Identifier.parse("minecraft:dispenser"), 23),
            Map.entry(Identifier.parse("minecraft:dropper"), 158),
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
            // The blaze's own rod (Slice 18a — the reference's Item.java id
            // 369: BlazeEntity.getDropItem's drop, brewed into stands only
            // after the powder's family).
            Map.entry(Identifier.parse("minecraft:blaze_rod"), 369),
            Map.entry(Identifier.parse("minecraft:magma_cream"), 378),
            Map.entry(Identifier.parse("minecraft:glistering_melon"), 382),
            Map.entry(Identifier.parse("minecraft:writable_book"), 386),
            Map.entry(Identifier.parse("minecraft:nether_star"), 399),
            Map.entry(Identifier.parse("minecraft:rabbit_foot"), 414),
            // The spawn egg (Slice 20a — Item.java line 683's registration,
            // id 383): the damage field carries the entity id (SpawnEggs'
            // own table).
            Map.entry(Identifier.parse("minecraft:spawn_egg"), 383),
            // The fish family (Slice 11i, the reference's Item.java lines
            // 635-636): the raw fish 349 and the cooked fish 350 — the four
            // variants ride the damage field (0 cod, 1 salmon, 2 clownfish,
            // 3 pufferfish).
            Map.entry(Identifier.parse("minecraft:fish"), 349),
            Map.entry(Identifier.parse("minecraft:cooked_fish"), 350),
            // The bowl pair (Slice 17b — the reference's Item.java: the
            // bowl 281, the mushroom stew 282).
            Map.entry(Identifier.parse("minecraft:bowl"), 281),
            Map.entry(Identifier.parse("minecraft:mushroom_stew"), 282),
            Map.entry(Identifier.parse("minecraft:wool"), 35),
            // Mob loot and fluid handling of the living-night slice
            // (community items.json ids: bone 352, string 287, gunpowder 289,
            // mutton 423, cooked mutton 424, buckets 325/326/327).
            Map.entry(Identifier.parse("minecraft:bone"), 352),
            Map.entry(Identifier.parse("minecraft:string"), 287),
            Map.entry(Identifier.parse("minecraft:gunpowder"), 289),
            Map.entry(Identifier.parse("minecraft:slime_ball"), 341),
            // The mineral vault family (Slice 32 — the reference's own
            // registrations): the ores (lapis 21, emerald 129), the storage
            // blocks (lapis 22, redstone 152, coal 173, quartz 155, brick
            // 45, clay 82, hay 170, slime 165, end stone 121, packed ice
            // 174 — the item ids ARE the block ids, Item.java's BlockItem
            // registrations), and the material items (the clay ball 337,
            // the brick 336, the nether brick 405 — Item.java lines 709+).
            Map.entry(Identifier.parse("minecraft:lapis_ore"), 21),
            Map.entry(Identifier.parse("minecraft:emerald_ore"), 129),
            Map.entry(Identifier.parse("minecraft:lapis_block"), 22),
            Map.entry(Identifier.parse("minecraft:redstone_block"), 152),
            Map.entry(Identifier.parse("minecraft:coal_block"), 173),
            Map.entry(Identifier.parse("minecraft:quartz_block"), 155),
            Map.entry(Identifier.parse("minecraft:brick_block"), 45),
            Map.entry(Identifier.parse("minecraft:clay"), 82),
            Map.entry(Identifier.parse("minecraft:hay_block"), 170),
            Map.entry(Identifier.parse("minecraft:slime_block"), 165),
            Map.entry(Identifier.parse("minecraft:end_stone"), 121),
            Map.entry(Identifier.parse("minecraft:packed_ice"), 174),
            Map.entry(Identifier.parse("minecraft:clay_ball"), 337),
            Map.entry(Identifier.parse("minecraft:brick"), 336),
            Map.entry(Identifier.parse("minecraft:nether_brick"), 405),
            // The redstone power family (Slice 33 — Block.java's own rows):
            // the lamp pair 123/124 (the item shares the DARK form's 123 —
            // the reference's getDropItem; the lit twin has no item), the
            // lit ore 74 (the plain ore's glowing state).
            Map.entry(Identifier.parse("minecraft:redstone_lamp"), 123),
            Map.entry(Identifier.parse("minecraft:lit_redstone_lamp"), 124),
            Map.entry(Identifier.parse("minecraft:lit_redstone_ore"), 74),
            // The skull family (Slice 60 — block 144, the six facing
            // states; the FACING nibble the reference's Direction ids —
            // the floor mount carries the base name at metadata 1). The
            // ITEM form 397 lives in ITEM_OVERRIDES (the sign's own
            // split — the damage field carries the variant, 1 = wither).
            Map.entry(Identifier.parse("minecraft:skull"), 144),
            Map.entry(Identifier.parse("minecraft:mutton"), 423),
            Map.entry(Identifier.parse("minecraft:cooked_mutton"), 424),
            Map.entry(Identifier.parse("minecraft:bucket"), 325),
            Map.entry(Identifier.parse("minecraft:water_bucket"), 326),
            Map.entry(Identifier.parse("minecraft:lava_bucket"), 327),
            // The milk bucket (Slice 46 — Item.java's own row 335).
            Map.entry(Identifier.parse("minecraft:milk_bucket"), 335),
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
            // The R-2 repair (the item registry's id-by-id diff vs the
            // 1.8.8 table — the food rows the engine's slices registered
            // but this table never carried): without a row each of these
            // serialized as writeSlot's unmappable-empty arm (-1) while
            // the engine dropped them, crafted them and fed them. Every
            // id is the reference's own registration: apple 260 (Item.java
            // line 539), the golden apple 322 (lines 602-604), carrot 391
            // / potato 392 / baked potato 393 (lines 691-693), the golden
            // carrot 396 (lines 697-699), the rabbit family 411/412/413/
            // 415 (lines 715-719).
            Map.entry(Identifier.parse("minecraft:apple"), 260),
            Map.entry(Identifier.parse("minecraft:golden_apple"), 322),
            Map.entry(Identifier.parse("minecraft:carrot"), 391),
            Map.entry(Identifier.parse("minecraft:potato"), 392),
            Map.entry(Identifier.parse("minecraft:baked_potato"), 393),
            // The mature potato crop's 1/50 bonus roll (Item.java line 694).
            Map.entry(Identifier.parse("minecraft:poisonous_potato"), 394),
            Map.entry(Identifier.parse("minecraft:golden_carrot"), 396),
            Map.entry(Identifier.parse("minecraft:rabbit"), 411),
            Map.entry(Identifier.parse("minecraft:cooked_rabbit"), 412),
            Map.entry(Identifier.parse("minecraft:rabbit_stew"), 413),
            Map.entry(Identifier.parse("minecraft:rabbit_hide"), 415),
            // mob loot (community items.json legacy ids)
            Map.entry(Identifier.parse("minecraft:porkchop"), 319),
            Map.entry(Identifier.parse("minecraft:cooked_porkchop"), 320),
            Map.entry(Identifier.parse("minecraft:chicken"), 365),   // dataset/legacy name: "chicken"
            Map.entry(Identifier.parse("minecraft:cooked_chicken"), 366),
            Map.entry(Identifier.parse("minecraft:feather"), 288),
            Map.entry(Identifier.parse("minecraft:leather"), 334),
            Map.entry(Identifier.parse("minecraft:rotten_flesh"), 367),
            Map.entry(Identifier.parse("minecraft:gold_nugget"), 371),
            Map.entry(Identifier.parse("minecraft:dye"), 351),
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
            // The VIII14 decorators slice: the fern (block 31, variant 2).
            Map.entry(Identifier.parse("minecraft:fern"), 31),
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
            // The melon/pumpkin seed pair (Slice 21a): the
            // mineshaft's chest-minecart loot rows. The R-2 id diff
            // repaired both rows — the "community items.json" source had
            // the pair at the iron/golden hoe's own ids (292/294),
            // colliding with those items on the wire; the reference's
            // registrations are Item.java line 647's pumpkin 361 and line
            // 648's melon 362.
            Map.entry(Identifier.parse("minecraft:melon_seeds"), 362),
            Map.entry(Identifier.parse("minecraft:pumpkin_seeds"), 361),
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
            Map.entry(Identifier.parse("minecraft:skull_down"), 144),
            Map.entry(Identifier.parse("minecraft:skull_north"), 144),
            Map.entry(Identifier.parse("minecraft:skull_south"), 144),
            Map.entry(Identifier.parse("minecraft:skull_west"), 144),
            Map.entry(Identifier.parse("minecraft:skull_east"), 144),
            // The command-block family (Slice 72 — Block.java line 1299's
            // register(137, "command_block")): the two TRIGGERED states.
            Map.entry(Identifier.parse("minecraft:command_block"), 137),
            Map.entry(Identifier.parse("minecraft:command_block_triggered"), 137),
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
            Map.entry(Identifier.parse("minecraft:oak_stairs_east_top"), 53),
            Map.entry(Identifier.parse("minecraft:oak_stairs_west_top"), 53),
            Map.entry(Identifier.parse("minecraft:oak_stairs_south_top"), 53),
            Map.entry(Identifier.parse("minecraft:oak_stairs_north_top"), 53),
            Map.entry(Identifier.parse("minecraft:cobblestone_stairs_east"), 67),
            Map.entry(Identifier.parse("minecraft:cobblestone_stairs_west"), 67),
            Map.entry(Identifier.parse("minecraft:cobblestone_stairs_south"), 67),
            Map.entry(Identifier.parse("minecraft:cobblestone_stairs_north"), 67),
            Map.entry(Identifier.parse("minecraft:cobblestone_stairs_east_top"), 67),
            Map.entry(Identifier.parse("minecraft:cobblestone_stairs_west_top"), 67),
            Map.entry(Identifier.parse("minecraft:cobblestone_stairs_south_top"), 67),
            Map.entry(Identifier.parse("minecraft:cobblestone_stairs_north_top"), 67),
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
            Map.entry(Identifier.parse("minecraft:sandstone_stairs_east_top"), 128),
            Map.entry(Identifier.parse("minecraft:sandstone_stairs_west_top"), 128),
            Map.entry(Identifier.parse("minecraft:sandstone_stairs_south_top"), 128),
            Map.entry(Identifier.parse("minecraft:sandstone_stairs_north_top"), 128),
            Map.entry(Identifier.parse("minecraft:glass_pane"), 102),
            Map.entry(Identifier.parse("minecraft:iron_bars"), 101),
            Map.entry(Identifier.parse("minecraft:double_stone_slab"), 43),
            // The 43-family variant doubles + the seamless skins (Slice
            // L5 — the VARIANT nibble 1/3/5 and the SEAMLESS bit 8).
            Map.entry(Identifier.parse("minecraft:double_sandstone_slab"), 43),
            Map.entry(Identifier.parse("minecraft:double_cobblestone_slab"), 43),
            Map.entry(Identifier.parse("minecraft:double_stone_brick_slab"), 43),
            Map.entry(Identifier.parse("minecraft:double_stone_slab_seamless"), 43),
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
            // The chest minecart item (Slice 21a, community items.json 342):
            // the wire's own item id; the entity rides Spawn Object's
            // objectData 1 instead (TrackedEntity's minecart type byte).
            Map.entry(Identifier.parse("minecraft:chest_minecart"), 342),
            // The mount slice (community items.json): saddle 329, horse
            // armors 417/418/419, lead 420, carrot on a stick 398, and the
            // villager currency (emerald 388).
            Map.entry(Identifier.parse("minecraft:saddle"), 329),
            Map.entry(Identifier.parse("minecraft:iron_horse_armor"), 417),
            Map.entry(Identifier.parse("minecraft:golden_horse_armor"), 418),
            Map.entry(Identifier.parse("minecraft:diamond_horse_armor"), 419),
            Map.entry(Identifier.parse("minecraft:lead"), 420),
            // The decoration family (Slice 28a/28b — Item.java's own
            // DecorationItem registrations): the item frame 389, the
            // painting 321.
            Map.entry(Identifier.parse("minecraft:item_frame"), 389),
            Map.entry(Identifier.parse("minecraft:painting"), 321),
            // The armor stand's item form (Slice 28c — Item.java line 720).
            Map.entry(Identifier.parse("minecraft:armor_stand"), 416),
            Map.entry(Identifier.parse("minecraft:carrot_on_a_stick"), 398),
            Map.entry(Identifier.parse("minecraft:emerald"), 388),
            // The villager's trade stock (Slice 21g — the reference's
            // Item.java registrations): fishing rod 346, clock 347, cake
            // 354, cookie 357, melon 360, ender eye 381, experience
            // bottle 384, written book 387, pumpkin pie 400, name tag 421.
            Map.entry(Identifier.parse("minecraft:fishing_rod"), 346),
            Map.entry(Identifier.parse("minecraft:clock"), 347),
            Map.entry(Identifier.parse("minecraft:cake"), 354),
            Map.entry(Identifier.parse("minecraft:cookie"), 357),
            Map.entry(Identifier.parse("minecraft:melon"), 360),
            Map.entry(Identifier.parse("minecraft:ender_eye"), 381),
            Map.entry(Identifier.parse("minecraft:experience_bottle"), 384),
            // The firework family (Slice 65 — Item.java lines 705-706:
            // the rocket 401, the star 402).
            Map.entry(Identifier.parse("minecraft:fireworks"), 401),
            Map.entry(Identifier.parse("minecraft:firework_charge"), 402),
            // The minecart variant items (Slice 66 — Item.java lines 627 +
            // 711-712: the furnace cart 343, the TNT cart 407, the hopper
            // cart 408; the spawner cart has no item form — Item.java never
            // registers one — and the command cart's 422 rides its slice).
            Map.entry(Identifier.parse("minecraft:furnace_minecart"), 343),
            Map.entry(Identifier.parse("minecraft:tnt_minecart"), 407),
            Map.entry(Identifier.parse("minecraft:hopper_minecart"), 408),
            Map.entry(Identifier.parse("minecraft:command_block_minecart"), 422),
            Map.entry(Identifier.parse("minecraft:written_book"), 387),
            Map.entry(Identifier.parse("minecraft:pumpkin_pie"), 400),
            Map.entry(Identifier.parse("minecraft:name_tag"), 421),
            // The stronghold set (Slice 12c — the reference's own legacy
            // ids): the stone-brick family over 98 (the mossy/cracked pair
            // in the metadata nibble), the silverfish stone-brick monster
            // egg 97/4, the smooth stone-brick slab 44/5, the spawner 52,
            // the iron door family over 71 (the oak pair's own shape:
            // the facing band + the metadata-8 upper), the end portal
            // frame's eight states over 120 (the facing band | the eye
            // bit), and the chest loot family's items — the ender pearl
            // 368, the empty map 395, the compass 345 (the R-2 id diff
            // repaired the row: the old 329 was the saddle's own id, a
            // collision the reference never has — Item.java line 629's
            // register(345, "compass")).
            Map.entry(Identifier.parse("minecraft:stone_bricks"), 98),
            Map.entry(Identifier.parse("minecraft:mossy_stone_bricks"), 98),
            Map.entry(Identifier.parse("minecraft:cracked_stone_bricks"), 98),
            Map.entry(Identifier.parse("minecraft:chiseled_stone_bricks"), 98),
            Map.entry(Identifier.parse("minecraft:infested_stone_bricks"), 97),
            Map.entry(Identifier.parse("minecraft:infested_stone"), 97),
            Map.entry(Identifier.parse("minecraft:infested_cobblestone"), 97),
            Map.entry(Identifier.parse("minecraft:infested_mossy_stone_bricks"), 97),
            Map.entry(Identifier.parse("minecraft:infested_cracked_stone_bricks"), 97),
            Map.entry(Identifier.parse("minecraft:infested_chiseled_stone_bricks"), 97),
            Map.entry(Identifier.parse("minecraft:stone_brick_slab"), 44),
            Map.entry(Identifier.parse("minecraft:stone_brick_slab_top"), 44),
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
            // The filled map (the map item family — Item.java line 644's
            // own id 358): the damage field carries the map id, so every
            // slot write resolves the variant client-side exactly like the
            // reference's hasCustomData flag.
            Map.entry(Identifier.parse("minecraft:filled_map"), 358),
            Map.entry(Identifier.parse("minecraft:compass"), 345),
            Map.entry(Identifier.parse("minecraft:iron_door"), 330),
            // The fortress set (Slice 12d): the nether-brick family — the
            // bricks 112 (repaired in Slice 32), the fence 113, the stairs
            // 114 (the ascending band in the metadata nibble), the wart
            // 115 (the age 0..3) and the block item trio 405/113/114.
            // The nether bricks' wire repair (Slice 32): the fortress block
            // is legacy 112 (Block.java's register(112, "nether_brick")).
            // The original Slice 12d row carried 40 — the red mushroom's
            // id — so real clients rendered mushrooms for fortress walls.
            // The fence 113 and stairs 114 keep their correct rows below.
            Map.entry(Identifier.parse("minecraft:nether_bricks"), 112),
            Map.entry(Identifier.parse("minecraft:nether_brick_fence"), 113),
            Map.entry(Identifier.parse("minecraft:nether_brick_stairs_east"), 114),
            Map.entry(Identifier.parse("minecraft:nether_brick_stairs_west"), 114),
            Map.entry(Identifier.parse("minecraft:nether_brick_stairs_south"), 114),
            Map.entry(Identifier.parse("minecraft:nether_brick_stairs_north"), 114),
            Map.entry(Identifier.parse("minecraft:nether_brick_stairs_east_top"), 114),
            Map.entry(Identifier.parse("minecraft:nether_brick_stairs_west_top"), 114),
            Map.entry(Identifier.parse("minecraft:nether_brick_stairs_south_top"), 114),
            Map.entry(Identifier.parse("minecraft:nether_brick_stairs_north_top"), 114),
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
            // The beacon pyramid's minerals + the beacon itself (Slice 29b):
            // diamond block 57, emerald block 133, the beacon 138 (the block
            // and its item share the id).
            Map.entry(Identifier.parse("minecraft:diamond_block"), 57),
            Map.entry(Identifier.parse("minecraft:emerald_block"), 133),
            Map.entry(Identifier.parse("minecraft:beacon"), 138),
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
            // The golem family's blocks (Slice 17d — community blocks.json
            // 1.8 ids): the snow pair, the iron block, the pumpkin pair.
            Map.entry(Identifier.parse("minecraft:snow"), 80),
            Map.entry(Identifier.parse("minecraft:snow_layer"), 78),
            // The ice (Slice 21c, community blocks.json): the freeze arm's write.
            Map.entry(Identifier.parse("minecraft:ice"), 79),
            Map.entry(Identifier.parse("minecraft:iron_block"), 42),
            Map.entry(Identifier.parse("minecraft:pumpkin"), 86),
            Map.entry(Identifier.parse("minecraft:lit_pumpkin"), 91)));

    /**
     * The redstone family's base ids (Slice 9a): the wire's sixteen power
     * variants share legacy 55 (the power rides the metadata nibble), the
     * lit torch pair 76, the unlit pair 75, the repeater pairs 93/94 (the
     * facing and delay ride the nibble — facingH | (delay-1) << 2).
     */
    private static Map<Identifier, Integer> withRedstoneIds(Map<Identifier, Integer> base) {
        Map<Identifier, Integer> merged = new HashMap<>(base);
        // The complete door families (Slice 34 — Block.java's own rows: the
        // five new woods spruce 193, birch 194, jungle 195, acacia 196,
        // dark_oak 197 over the oak pair's own state shape, every family's
        // full hinge × powered × open upper grid, and the iron door's open
        // band over 71).
        int[] doorIds = {64, 193, 194, 195, 196, 197, 71};
        String[] woods = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "iron"};
        String[] lowerSuffixes = {"", "_north", "_east", "_south",
                "_open", "_open_north", "_open_east", "_open_south"};
        for (int w = 0; w < woods.length; w++) {
            String wood = woods[w];
            boolean iron = wood.equals("iron");
            for (int s = 0; s < lowerSuffixes.length; s++) {
                String suffix = lowerSuffixes[s];
                if (iron && (suffix.isEmpty() || suffix.equals("_open"))) {
                    suffix += "_west"; // the iron door's shipped band names meta 0 "west"
                }
                merged.put(Identifier.parse("minecraft:" + wood + "_door" + suffix), doorIds[w]);
            }
            String[] upperSuffixes = {"_upper", "_upper_right", "_upper_powered",
                    "_upper_right_powered", "_upper_open", "_upper_right_open",
                    "_upper_powered_open", "_upper_right_powered_open"};
            for (String suffix : upperSuffixes) {
                merged.put(Identifier.parse("minecraft:" + wood + "_door" + suffix), doorIds[w]);
            }
        }
        // The fence-gate families (Slice 37 — Block.java's own rows): the
        // oak gate 107 and the five 1.7 woods' gates (spruce 183, birch
        // 184, jungle 185, dark_oak 186, acacia 187) — the sixteen
        // facing/open/powered states per family over the reference's own
        // metadata band (facing | open 4 | powered 8), the bare meta-0
        // form sharing the family item's identifier (Item.java's own
        // register(Blocks.FENCE_GATE) rows — the item keeps the block id).
        int[] gateIds = {107, 183, 184, 185, 186, 187};
        String[] gateWoods = {"oak", "spruce", "birch", "jungle", "dark_oak", "acacia"};
        String[] gateFacings = {"south", "west", "north", "east"};
        for (int g = 0; g < gateWoods.length; g++) {
            String wood = gateWoods[g];
            for (int facing = 0; facing < 4; facing++) {
                for (int open = 0; open <= 1; open++) {
                    for (int powered = 0; powered <= 1; powered++) {
                        int meta = facing | (open == 1 ? 4 : 0) | (powered == 1 ? 8 : 0);
                        if (meta == 0) {
                            merged.put(Identifier.parse("minecraft:" + wood + "_fence_gate"), gateIds[g]);
                            continue;
                        }
                        StringBuilder name = new StringBuilder("minecraft:").append(wood).append("_fence_gate");
                        if (powered == 1) {
                            name.append("_powered");
                        }
                        if (open == 1) {
                            name.append("_open");
                        }
                        if (facing != 0) {
                            name.append('_').append(gateFacings[facing]);
                        }
                        merged.put(Identifier.parse(name.toString()), gateIds[g]);
                    }
                }
            }
        }
        // The rail trio (Slice 38 — Block.java's own rows): the golden
        // rail 27, the detector rail 28, the activator rail 157 — the six
        // straight shapes over metadata 0-5 (Shape.byId) with the POWERED
        // bit 8, the bare north_south form sharing the family item's
        // identifier (Item.java's own register(Blocks.POWERED_RAIL) rows).
        {
            String[] railFamilies = {"golden_rail", "detector_rail", "activator_rail"};
            int[] railIds = {27, 28, 157};
            String[] railShapes = {"", "_east_west", "_ascending_east", "_ascending_west",
                    "_ascending_north", "_ascending_south"};
            for (int f = 0; f < railFamilies.length; f++) {
                for (int shape = 0; shape < 6; shape++) {
                    for (int powered = 0; powered <= 1; powered++) {
                        int meta = shape | (powered == 1 ? 8 : 0);
                        if (meta == 0) {
                            merged.put(Identifier.parse("minecraft:" + railFamilies[f]), railIds[f]);
                            continue; // the bare form rides the 0 default
                        }
                        StringBuilder name = new StringBuilder("minecraft:").append(railFamilies[f]);
                        if (powered == 1) {
                            name.append("_powered");
                        }
                        name.append(railShapes[shape]);
                        merged.put(Identifier.parse(name.toString()), railIds[f]);
                    }
                }
            }
        }
        // The trapdoor pair (Slice 39 — Block.java's own rows): the wood
        // trapdoor 96, the iron trapdoor 167 — the horizontal facing over
        // metadata 0-3 (getFacing: N=0 S=1 W=2 E=3) with the OPEN bit 4 and
        // the TOP-half bit 8, the bare form sharing the family item's
        // identifier (Item.java's own rows 434/503).
        {
            String[] trapdoorFamilies = {"trapdoor", "iron_trapdoor"};
            int[] trapdoorIds = {96, 167};
            String[] trapdoorFacings = {"", "_south", "_west", "_east"}; // north is bare
            for (int f = 0; f < trapdoorFamilies.length; f++) {
                for (int facing = 0; facing < 4; facing++) {
                    for (int open = 0; open <= 1; open++) {
                        for (int top = 0; top <= 1; top++) {
                            int meta = facing | (open == 1 ? 4 : 0) | (top == 1 ? 8 : 0);
                            if (meta == 0) {
                                merged.put(Identifier.parse("minecraft:" + trapdoorFamilies[f]),
                                        trapdoorIds[f]);
                                continue;
                            }
                            StringBuilder name = new StringBuilder("minecraft:")
                                    .append(trapdoorFamilies[f]);
                            if (open == 1) {
                                name.append("_open");
                            }
                            if (top == 1) {
                                name.append("_top");
                            }
                            name.append(trapdoorFacings[facing]);
                            merged.put(Identifier.parse(name.toString()), trapdoorIds[f]);
                        }
                    }
                }
            }
        }
        // The cocoa family (Slice 43 — Block.java's own row): the pod 127 —
        // the horizontal facing | AGE << 2 (getStateFromMetadata's band);
        // the bare form is the south/age-0 state.
        for (int facing = 0; facing < 4; facing++) {
            for (int age = 0; age <= 2; age++) {
                int meta = facing | (age << 2);
                if (meta == 0) {
                    merged.put(Identifier.parse("minecraft:cocoa"), 127);
                    continue;
                }
                StringBuilder name = new StringBuilder("minecraft:cocoa");
                if (age > 0) {
                    name.append("_age").append(age);
                }
                if (facing != 0) {
                    name.append('_').append(new String[]{"south", "west", "north", "east"}[facing]);
                }
                merged.put(Identifier.parse(name.toString()), 127);
            }
        }
        // The flower family (Slice 44 — Block.java's own row): the red
        // flower 38 — the eight new variant states carry the TYPE id whole
        // in the metadata nibble (FlowerBlock.getMetadataFromState); the
        // poppy (the TYPE 0) keeps its shipped plain rows, the family item
        // (Item.java's own BlockItem over the same id) joins them.
        {
            String[] flowerTypes = {"blue_orchid", "allium", "houstonia",
                    "red_tulip", "orange_tulip", "white_tulip", "pink_tulip",
                    "oxeye_daisy"};
            for (int type = 1; type <= flowerTypes.length; type++) {
                merged.put(Identifier.parse("minecraft:" + flowerTypes[type - 1]), 38);
            }
        }
        merged.put(Identifier.parse("minecraft:red_flower"), 38);
        // The double-plant family (Slice 45 — Block.java's own row): the
        // six variants' twelve states over 175 — the LOWER carries the
        // VARIANT id whole, the UPPER carries 8 | the default NORTH
        // horizontal (the reference's own write, meta 10). The family item
        // (Item.java's own BlockItem over the same id) joins them.
        {
            String[] dpVariants = {"sunflower", "lilac", "double_grass",
                    "double_fern", "rose", "peony"};
            for (String variant : dpVariants) {
                merged.put(Identifier.parse("minecraft:" + variant), 175);
                merged.put(Identifier.parse("minecraft:" + variant + "_upper"), 175);
            }
        }
        merged.put(Identifier.parse("minecraft:double_plant"), 175);
        // The cake family (Slice 46 — Block.java's own row): the block 92 —
        // the seven BITES states, the bare bites-0 form the plain name.
        merged.put(Identifier.parse("minecraft:cake"), 92);
        for (int bites = 1; bites <= 6; bites++) {
            merged.put(Identifier.parse("minecraft:cake_bite" + bites), 92);
        }
        // The fire AGE band (FireBlocks — Block.java's own fire row 51;
        // the age rides the metadata nibble through the type's
        // legacyMetadata, so the ids share the one row).
        for (int age = 1; age <= 15; age++) {
            merged.put(Identifier.parse("minecraft:fire_age" + age), 51);
        }
        // The wall family (Slice 47 — Block.java's own row): the wall 139 —
        // the two VARIANT states (the connections are the client's own
        // virtual reads, never on the wire).
        merged.put(Identifier.parse("minecraft:cobblestone_wall"), 139);
        merged.put(Identifier.parse("minecraft:mossy_cobblestone_wall"), 139);
        // The flower-pot family (Slice 48 — Block.java's own row): the pot
        // 140 — the empty form + the thirteen potted contents over the
        // LEGACY_DATA band; the pot item (Item.java's own row) joins them.
        merged.put(Identifier.parse("minecraft:flower_pot"), 140);
        merged.put(Identifier.parse("minecraft:potted_poppy"), 140);
        merged.put(Identifier.parse("minecraft:potted_dandelion"), 140);
        merged.put(Identifier.parse("minecraft:potted_oak_sapling"), 140);
        merged.put(Identifier.parse("minecraft:potted_spruce_sapling"), 140);
        merged.put(Identifier.parse("minecraft:potted_birch_sapling"), 140);
        merged.put(Identifier.parse("minecraft:potted_jungle_sapling"), 140);
        merged.put(Identifier.parse("minecraft:potted_red_mushroom"), 140);
        merged.put(Identifier.parse("minecraft:potted_brown_mushroom"), 140);
        merged.put(Identifier.parse("minecraft:potted_cactus"), 140);
        merged.put(Identifier.parse("minecraft:potted_dead_bush"), 140);
        merged.put(Identifier.parse("minecraft:potted_fern"), 140);
        merged.put(Identifier.parse("minecraft:potted_acacia_sapling"), 140);
        merged.put(Identifier.parse("minecraft:potted_dark_oak_sapling"), 140);
        // The trapped chest (Slice 49b — Block.java's own row 146): the
        // single meta-0 form (the facing nibble rides the metadata-variants
        // slice); the item (Item.java's own BlockItem) joins it.
        merged.put(Identifier.parse("minecraft:trapped_chest"), 146);
        // The weighted pressure-plate pair (Slice 49a — Block.java's own
        // rows): the light 147 and the heavy 148 — the sixteen POWER states
        // each, the POWER value in the nibble.
        for (int family = 0; family <= 1; family++) {
            int id = family == 0 ? 147 : 148;
            String key = family == 0 ? "light_weighted_pressure_plate"
                    : "heavy_weighted_pressure_plate";
            merged.put(Identifier.parse("minecraft:" + key), id);
            for (int power = 1; power <= 15; power++) {
                merged.put(Identifier.parse("minecraft:" + key + "_power" + power), id);
            }
        }
        // The red sandstone family (Slice 52 — 1.8's own four, Block.java
        // lines 1379-1390): the red sand (the sand 12's RED_SAND variant,
        // the id whole in the nibble), the three TYPE states over 179, the
        // four facing states over 180 (the Bukkit band), the double 181 and
        // the slab pair 182 (the HALF bit 8). The family item (Item.java's
        // own VariantBlockItem over 179), the stairs (180) and the slab
        // (182, the SlabItem) join them through the shared identifiers.
        merged.put(Identifier.parse("minecraft:red_sand"), 12);
        merged.put(Identifier.parse("minecraft:red_sandstone"), 179);
        merged.put(Identifier.parse("minecraft:chiseled_red_sandstone"), 179);
        merged.put(Identifier.parse("minecraft:smooth_red_sandstone"), 179);
        merged.put(Identifier.parse("minecraft:red_sandstone_stairs"), 180);
        for (String facing : new String[] {"east", "west", "south", "north"}) {
            merged.put(Identifier.parse("minecraft:red_sandstone_stairs_" + facing), 180);
            // The HALF bit's upside-down form (StairsBlock's TOP fold).
            merged.put(Identifier.parse("minecraft:red_sandstone_stairs_" + facing + "_top"), 180);
        }
        merged.put(Identifier.parse("minecraft:double_stone_slab2"), 181);
        merged.put(Identifier.parse("minecraft:double_stone_slab2_seamless"), 181);
        merged.put(Identifier.parse("minecraft:stone_slab2"), 182);
        merged.put(Identifier.parse("minecraft:stone_slab2_top"), 182);
        // The anvil family (Slice 53 — Block.java's own row 145): the twelve
        // states over the FACING horizontal band (S=0 W=1 N=2 E=3) with the
        // DAMAGE << 2 in the high pair; the bare name carries the south
        // form like the gate precedent. The item (Item.java's own AnvilItem
        // over 145) joins them through the shared identifier.
        for (String damage : new String[] {"anvil", "anvil_chipped", "anvil_damaged"}) {
            for (String facing : new String[] {"", "_west", "_north", "_east"}) {
                merged.put(Identifier.parse("minecraft:" + damage + facing), 145);
            }
        }
        // The end portal (Slice 54 — Block.java's own row 119): the single
        // meta-0 state (no item form — getPickItem null, the portal is
        // never held).
        merged.put(Identifier.parse("minecraft:end_portal"), 119);
        // The dragon egg (Slice 55 — Block.java's own row 122): the single
        // meta-0 state (no item form — getPickItem null).
        merged.put(Identifier.parse("minecraft:dragon_egg"), 122);
        // The barrier (Slice 56 — Block.java's own row 166): the single
        // state; the item (Item.java's register(Blocks.BARRIER)) shares the
        // identifier.
        merged.put(Identifier.parse("minecraft:barrier"), 166);
        // The double wooden slab family (Slice 58 — Block.java's own row
        // 125): the six planks variants, the variant id whole in the
        // nibble (the oak carries the wire's own double_wooden_slab name).
        merged.put(Identifier.parse("minecraft:double_wooden_slab"), 125);
        merged.put(Identifier.parse("minecraft:double_spruce_slab"), 125);
        merged.put(Identifier.parse("minecraft:double_birch_slab"), 125);
        merged.put(Identifier.parse("minecraft:double_jungle_slab"), 125);
        merged.put(Identifier.parse("minecraft:double_acacia_slab"), 125);
        merged.put(Identifier.parse("minecraft:double_dark_oak_slab"), 125);
        // The ground-cover pair (Slice 42 — Block.java's own rows): the
        // mycelium 110, the waterlily 111 — both single meta-0 states (the
        // snowy side and the boat pass-through are the client's own reads).
        merged.put(Identifier.parse("minecraft:mycelium"), 110);
        // The dirt variants (VIII14's surface slice — the reference's
        // DirtBlock.VARIANT rows: coarse dirt 3:1, podzol 3:2 — the
        // mega taiga's podzol band and the coarse-dirt switches).
        merged.put(Identifier.parse("minecraft:coarse_dirt"), 3);
        merged.put(Identifier.parse("minecraft:podzol"), 3);
        merged.put(Identifier.parse("minecraft:waterlily"), 111);
        // The melon family (Slice 41 — Block.java's own rows): the melon
        // block 103, the pumpkin stem 104 and the melon stem 105 — the
        // stems' eight AGE stages over the metadata nibble whole (the
        // engine's crop naming convention, the wheat/carrots/potatoes
        // precedent; the melon block's item shares the plain id).
        merged.put(Identifier.parse("minecraft:melon_block"), 103);
        for (int stage = 0; stage <= 7; stage++) {
            merged.put(Identifier.parse("minecraft:pumpkin_stem_stage" + stage), 104);
            merged.put(Identifier.parse("minecraft:melon_stem_stage" + stage), 105);
        }
        // The huge-mushroom pair (Slice 40 — Block.java's own rows): the
        // brown 99, the red 100 — the fourteen VARIANT ids (0-10, 14, 15;
        // the bare form carries the all_outside 14 default, the placement
        // state the reference's own getPlacementState answers).
        {
            String[] mushroomFamilies = {"brown_mushroom_block", "red_mushroom_block"};
            int[] mushroomIds = {99, 100};
            String[][] mushroomVariants = {
                    {"_all_inside", "0"}, {"_north_west", "1"}, {"_north", "2"},
                    {"_north_east", "3"}, {"_west", "4"}, {"_center", "5"},
                    {"_east", "6"}, {"_south_west", "7"}, {"_south", "8"},
                    {"_south_east", "9"}, {"_stem", "10"}, {"", "14"}, {"_all_stem", "15"}};
            for (int f = 0; f < mushroomFamilies.length; f++) {
                for (String[] variant : mushroomVariants) {
                    merged.put(Identifier.parse(
                            "minecraft:" + mushroomFamilies[f] + variant[0]), mushroomIds[f]);
                }
            }
        }
        // The wood-variant foundation (Slice 35 — Block.java's own rows):
        // planks 5, logs 17 + the Log2 pair 162, leaves 18 + the Leaves2
        // pair 161, saplings 6 — every family member's block id, its
        // metadata nibble (the & 3 band for the log/leaves domains), and
        // the six family items.
        String[] woodNames = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak"};
        String[] families = {"planks", "log", "leaves", "sapling"};
        for (int variant = 1; variant < woodNames.length; variant++) {
            for (String family : families) {
                int wireId = switch (family) {
                    case "planks" -> 5;
                    case "log" -> variant < 4 ? 17 : 162;
                    case "leaves" -> variant < 4 ? 18 : 161;
                    default -> 6;
                };
                Identifier block = Identifier.parse(
                        "minecraft:" + woodNames[variant] + "_" + family);
                merged.put(block, wireId);
            }
        }
        merged.put(Identifier.parse("minecraft:planks"), 5);
        merged.put(Identifier.parse("minecraft:log"), 17);
        merged.put(Identifier.parse("minecraft:log2"), 162);
        merged.put(Identifier.parse("minecraft:leaves"), 18);
        merged.put(Identifier.parse("minecraft:leaves2"), 161);
        merged.put(Identifier.parse("minecraft:sapling"), 6);
        // The six bare sapling BLOCK rows (the R-2 repair of the wire
        // table's own documented gap): familyBlocks() registers the bare
        // minecraft:<wood>_sapling types — the placement target of the
        // sapling item's damage domain — and ChunkSerializer18's §147
        // loud fail THREW on every one of them (the placed sapling killed
        // the chunk/block-change send). The stage bit and the variant
        // nibble ride the METADATA merge below (the ready band's own
        // rows; the bare forms' variant nibbles were already there).
        for (String wood : new String[]{"oak", "spruce", "birch", "jungle", "acacia", "dark_oak"}) {
            merged.put(Identifier.parse("minecraft:" + wood + "_sapling"), 6);
        }
        // The tree-growth family's derived bands (the VIII2/VIII3 slice —
        // the leaves' decay-bit states and the saplings' ready states ride
        // the family's own wire rows; the nibble lands in the METADATA map
        // below, the type's legacyMetadata the same law).
        for (int variant = 0; variant < woodNames.length; variant++) {
            int leafWire = variant < 4 ? 18 : 161;
            String wood = woodNames[variant];
            merged.put(Identifier.parse("minecraft:" + wood + "_leaves_check"), leafWire);
            merged.put(Identifier.parse("minecraft:" + wood + "_leaves_persistent"), leafWire);
            merged.put(Identifier.parse("minecraft:" + wood + "_leaves_persistent_check"), leafWire);
            merged.put(Identifier.parse("minecraft:" + wood + "_sapling_ready"), 6);
        }
        // The building-shape families (Slice 36 — Block.java's own rows):
        // the eight stair families (the four facing states, the ascending
        // band in the metadata nibble), the five new wood fences, the
        // wooden-slab pairs over 126 (the variant nibble, the top's bit 8),
        // and the stair/fence/wooden-slab items.
        String[] stairFamilies = {"spruce", "birch", "jungle", "acacia",
                "dark_oak", "brick", "stone_brick", "quartz"};
        int[] stairIds = {134, 135, 136, 163, 164, 108, 109, 156};
        String[] facingSuffixes = {"_east", "_west", "_south", "_north"};
        for (int f = 0; f < stairFamilies.length; f++) {
            for (int s = 0; s < facingSuffixes.length; s++) {
                merged.put(Identifier.parse(
                        "minecraft:" + stairFamilies[f] + "_stairs" + facingSuffixes[s]),
                        stairIds[f]);
                // The HALF bit's upside-down forms (StairsBlock's TOP fold).
                merged.put(Identifier.parse(
                        "minecraft:" + stairFamilies[f] + "_stairs" + facingSuffixes[s] + "_top"),
                        stairIds[f]);
            }
            merged.put(Identifier.parse("minecraft:" + stairFamilies[f] + "_stairs"), stairIds[f]);
        }
        String[] fenceWoods = {"spruce", "birch", "jungle", "dark_oak", "acacia"};
        int[] fenceIds = {188, 189, 190, 191, 192};
        for (int f = 0; f < fenceWoods.length; f++) {
            merged.put(Identifier.parse("minecraft:" + fenceWoods[f] + "_fence"), fenceIds[f]);
        }
        {
            String[] slabWoods = {"spruce", "birch", "jungle", "acacia", "dark_oak"};
            for (int variant = 1; variant < 6; variant++) {
                String wood = slabWoods[variant - 1];
                merged.put(Identifier.parse("minecraft:" + wood + "_slab"), 126);
                merged.put(Identifier.parse("minecraft:" + wood + "_slab_top"), 126);
            }
        }
        merged.put(Identifier.parse("minecraft:wooden_slab"), 126);
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
        // The ender chest family (Slice 29a — EnderChestBlock lines 114-127):
        // legacy 130, the FACING id riding the nibble whole (2/3/4/5 — the
        // damage-aware reverse resolves every state); the bare item form
        // shares the id (the block item, damage 0 — the plain reverse
        // resolves it).
        for (String facing : new String[]{"north", "south", "west", "east"}) {
            merged.put(Identifier.parse("minecraft:ender_chest_" + facing), 130);
            // The chest pair's facing states (Slice L2 — ChestBlock lines
            // 114-127: the same Direction-id nibble band over 54 and 146).
            merged.put(Identifier.parse("minecraft:chest_" + facing), 54);
            merged.put(Identifier.parse("minecraft:trapped_chest_" + facing), 146);
            // The furnace pair's facing states (Slice L3 — FurnaceBlock
            // lines 114-123: the same band over 61 and 62).
            merged.put(Identifier.parse("minecraft:furnace_" + facing), 61);
            merged.put(Identifier.parse("minecraft:furnace_lit_" + facing), 62);
        }
        merged.put(Identifier.parse("minecraft:ender_chest"), 130);
        // The daylight detector family (Slice 27c — Block.java lines
        // 1323/1377): the detector 151, the inverted 178 — the POWER 0-15
        // rides the WHOLE nibble (DaylightDetectorBlock lines 123-130:
        // getStateFromMetadata reads POWER from the metadata).
        for (int power = 0; power <= 15; power++) {
            merged.put(Identifier.parse("minecraft:daylight_detector_p" + power), 151);
            merged.put(Identifier.parse("minecraft:daylight_detector_inverted_p" + power), 178);
        }
        // The dispenser families (Slice 11a; the id repaired in Slice 32):
        // the dispenser 23, the dropper 158 — the facing and the triggered
        // bit ride the nibble (DispenserBlock lines 202-215).
        for (String facing : new String[]{"down", "up", "north", "south", "west", "east"}) {
            merged.put(Identifier.parse("minecraft:dispenser_" + facing), 23);
            merged.put(Identifier.parse("minecraft:dispenser_" + facing + "_triggered"), 23);
            merged.put(Identifier.parse("minecraft:dropper_" + facing), 158);
            merged.put(Identifier.parse("minecraft:dropper_" + facing + "_triggered"), 158);
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
        // The cauldron family: legacy 118 — the LEVEL property (0-3) rides
        // the nibble (CauldronBlock lines 198-205: the level IS the
        // metadata).
        merged.put(Identifier.parse("minecraft:cauldron"), 118);
        for (int level = 1; level <= 3; level++) {
            merged.put(Identifier.parse("minecraft:cauldron_level" + level), 118);
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
        // The dye-coloring world families (Slice 31 — the reference's Block
        // registrations): the fifteen non-white wool members over 35, the
        // stained glass family 95, the stained panes 160, the carpets 171,
        // the stained clay family's fourteen new members over 159 (the
        // temple pair registers in the base map), hardened clay 172 — the
        // color riding the nibble whole (the METADATA merge below). The
        // five family ITEM forms register with their block ids too (the
        // damage-domain items: the stained glass item 95, the pane 160, the
        // carpet 171, the stained clay 159, hardened clay 172 — the plain
        // wool item 35 already rides the base map).
        for (int color = 0; color < 16; color++) {
            if (color != 0) { // the white wool keeps the plain minecraft:wool entry
                merged.put(DyeFamilyIds.woolBlock(color), 35);
            }
            merged.put(DyeFamilyIds.stainedGlassBlock(color), 95);
            merged.put(DyeFamilyIds.stainedGlassPaneBlock(color), 160);
            merged.put(DyeFamilyIds.carpetBlock(color), 171);
            if (color != 1 && color != 4) { // the temple pair rides the base map
                merged.put(DyeFamilyIds.stainedClayBlock(color), 159);
            }
        }
        merged.put(Identifier.parse("minecraft:stained_glass"), 95);
        merged.put(Identifier.parse("minecraft:stained_glass_pane"), 160);
        merged.put(Identifier.parse("minecraft:carpet"), 171);
        merged.put(Identifier.parse("minecraft:hardened_clay"), 172);
        merged.put(Identifier.parse("minecraft:stained_hardened_clay"), 159);
        return Map.copyOf(merged);
    }

    /**
     * The dye family's id shapes, mirrored from the engine's
     * {@code DyeFamilyBlocks} (the protocol module cannot depend on core;
     * the two tables are pinned together by the Slice 31 tests).
     */
    private static final class DyeFamilyIds {
        private static final String[] COLORS = {
                "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink",
                "gray", "silver", "cyan", "purple", "blue", "brown", "green",
                "red", "black"};

        private DyeFamilyIds() {
        }

        static Identifier woolBlock(int color) {
            return Identifier.parse("minecraft:" + COLORS[color & 0xF] + "_wool");
        }

        static Identifier stainedGlassBlock(int color) {
            return Identifier.parse("minecraft:stained_glass_" + COLORS[color & 0xF]);
        }

        static Identifier stainedGlassPaneBlock(int color) {
            return Identifier.parse("minecraft:stained_glass_pane_" + COLORS[color & 0xF]);
        }

        static Identifier carpetBlock(int color) {
            return Identifier.parse("minecraft:carpet_" + COLORS[color & 0xF]);
        }

        static Identifier stainedClayBlock(int color) {
            return Identifier.parse("minecraft:stained_clay_" + COLORS[color & 0xF]);
        }

        static boolean isVariant(Identifier id) {
            String value = id.value();
            if (!id.namespace().equals("minecraft")) {
                return false;
            }
            // the wool members (the plain minecraft:wool is the ITEM)
            for (String color : COLORS) {
                if (value.equals(color + "_wool")) {
                    return true;
                }
            }
            return (value.startsWith("stained_glass_pane_") || value.startsWith("stained_glass_")
                    || value.startsWith("carpet_") || value.startsWith("stained_clay_"))
                    && hasColorSuffix(value);
        }

        private static boolean hasColorSuffix(String value) {
            int underscore = value.lastIndexOf('_');
            if (underscore < 0 || underscore + 1 >= value.length()) {
                return false;
            }
            String suffix = value.substring(underscore + 1);
            for (String color : COLORS) {
                if (suffix.equals(color)) {
                    return true;
                }
            }
            return false;
        }
    }

    /** @return whether the id is a dye family's flattened member (never an item). */
    static boolean isDyeFamilyVariant(Identifier id) {
        return DyeFamilyIds.isVariant(id);
    }

    /**
     * Item-form ids that diverge from the block id sharing the identifier
     * (the sign: block 63 on the chunk wire, item 323 in slots — the
     * historical split). Consulted by the slot/item wire paths only.
     * Declared before the reverse map: reverse() folds it in at init.
     */
    private static final Map<Identifier, Integer> ITEM_OVERRIDES = Map.ofEntries(
            Map.entry(Identifier.parse("minecraft:sign"), 323),
            // The skull item (Slice 60 — community id 397; the block keeps
            // 144 — the sign's own block/item split, the damage field
            // carrying the head type).
            Map.entry(Identifier.parse("minecraft:skull"), 397),
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
            // The hopper item (Slice 10b — REPAIRED BY THE R-2 ID DIFF):
            // the reference registers the hopper as Item.java line 493's
            // register(Blocks.HOPPER) — a BlockItem SHARING the block id
            // 154; the old 410 row was a misread "historical split" (the
            // comparator's 404 is real, the hopper's is not), and 410 is
            // the prismarine crystals' own id (line 714). The block family
            // keeps 154 in BY_IDENTIFIER through the facing merge; the
            // override wins the reverse map so identifierOf(154) answers
            // the item, deterministically over the facing variants.
            Map.entry(Identifier.parse("minecraft:hopper"), 154),
            // The tripwire hook item (Slice 10c): the block family keeps
            // legacy 131, the item form shares it.
            Map.entry(Identifier.parse("minecraft:tripwire_hook"), 131),
            // The brewing stand item (Slice 11e): the block family keeps
            // legacy 117, the item form is 379 (the same split as the
            // comparator's 404).
            Map.entry(Identifier.parse("minecraft:brewing_stand"), 379),
            // The cauldron item (Item.java line 676): legacy 380 — the
            // block family keeps 118, the item form diverges.
            Map.entry(Identifier.parse("minecraft:cauldron"), 380),
            // The R-2 repair (the block/item id splits the table missed —
            // every id from the reference's own registrations): the cake
            // item 354 (Item.java line 640's PlaceableItem over
            // Blocks.CAKE; the block keeps 92), the flower pot item 390
            // (line 690; the block keeps 140 — Slice 48's own ledger row
            // always claimed "the item at 390"), the five 1.7 doors
            // 427-431 (lines 730-734 — DoorItems over blocks 193-197,
            // the oak pair's own 64/324 split), and the bare item forms
            // over the state-suffixed redstone families (the lever 69
            // [line 411], the stone button 77 [line 416], the wooden
            // button 143 [line 485], the pistons 33/29 [lines 376/372] —
            // the identifiers EngineServer's placement arms hold; without
            // a row every one serialized as writeSlot's empty arm).
            Map.entry(Identifier.parse("minecraft:cake"), 354),
            Map.entry(Identifier.parse("minecraft:flower_pot"), 390),
            Map.entry(Identifier.parse("minecraft:spruce_door"), 427),
            Map.entry(Identifier.parse("minecraft:birch_door"), 428),
            Map.entry(Identifier.parse("minecraft:jungle_door"), 429),
            Map.entry(Identifier.parse("minecraft:acacia_door"), 430),
            Map.entry(Identifier.parse("minecraft:dark_oak_door"), 431),
            Map.entry(Identifier.parse("minecraft:lever"), 69),
            Map.entry(Identifier.parse("minecraft:stone_button"), 77),
            Map.entry(Identifier.parse("minecraft:wooden_button"), 143),
            Map.entry(Identifier.parse("minecraft:piston"), 33),
            Map.entry(Identifier.parse("minecraft:sticky_piston"), 29));

    /** The damage-aware reverse map is declared after {@link #METADATA}. */

    private static final Map<Integer, Identifier> BY_LEGACY_ID = reverse();

    private static Map<Integer, Identifier> reverse() {
        Map<Integer, Identifier> reversed = new HashMap<>();
        BY_IDENTIFIER.forEach((identifier, legacyId) -> {
            // The dye families' flattened members share their legacy id
            // with the family's single damage-domain ITEM (Slice 31): the
            // no-meta reverse must keep the ITEM canonical (the creative
            // slot's plain-id fallback resolves it; the members are only
            // ever reachable through the damage-aware map below).
            if (isDyeFamilyVariant(identifier)) {
                return;
            }
            reversed.put(legacyId, identifier);
        });
        // The item-form overrides (sign item 323) resolve on the creative
        // and slot paths too; item ids never collide with block ids here.
        ITEM_OVERRIDES.forEach((identifier, legacyId) -> reversed.put(legacyId, identifier));
        return Map.copyOf(reversed);
    }

    /**
     * @return the legacy numeric id, or empty when this adapter cannot
     * represent the type. Fluid states resolve programmatically over the
     * BLOCK form (the Wave VI band: the source block — the true source
     * AND the settled {@code still_*} levels — rides 9/11; every flowing
     * form — the shallow levels, the plain falling column, the
     * falling-with-above-depth band — rides 8/10; the level itself is the
     * metadata nibble via {@link #metadataOf}).
     */
    static Optional<Integer> legacyId(Identifier identifier) {
        Integer direct = BY_IDENTIFIER.get(identifier);
        if (direct != null) {
            return Optional.of(direct);
        }
        FluidBlocks.Kind fluid = FluidBlocks.kindOf(identifier);
        if (fluid == FluidBlocks.Kind.WATER) {
            return Optional.of(FluidBlocks.isSourceForm(identifier) ? 9 : 8);
        }
        if (fluid == FluidBlocks.Kind.LAVA) {
            return Optional.of(FluidBlocks.isSourceForm(identifier) ? 11 : 10);
        }
        return Optional.empty();
    }

    /**
     * The 1.8 data-value variants that ride the metadata nibble (block 31's
     * flora: 0 dead shrub, 1 grass, 2 fern — the engine's tall grass is the
     * real "grass" variant on the wire).
     */
    private static final Map<Identifier, Integer> METADATA = withDyeFamilyMetadata(Map.ofEntries(
            Map.entry(Identifier.parse("minecraft:tall_grass"), 1),
            // The VIII14 decorators slice: the fern (block 31's variant 2
            // — TallPlantBlock.Type.FERN's own nibble).
            Map.entry(Identifier.parse("minecraft:fern"), 2),
            Map.entry(Identifier.parse("minecraft:sign_west"), 4),
            Map.entry(Identifier.parse("minecraft:sign_north"), 8),
            Map.entry(Identifier.parse("minecraft:sign_east"), 12),
            // The wall signs: the metadata names the faced direction.
            Map.entry(Identifier.parse("minecraft:wall_sign"), 2),
            Map.entry(Identifier.parse("minecraft:wall_sign_south"), 3),
            Map.entry(Identifier.parse("minecraft:wall_sign_west"), 4),
            Map.entry(Identifier.parse("minecraft:wall_sign_east"), 5),
            Map.entry(Identifier.parse("minecraft:oak_door_north"), 1),
            // The jukebox's HAS_RECORD variant (Slice 27b — the reference's
            // getMetadataFromState: has_record ? 1 : 0 over legacy id 84).
            Map.entry(Identifier.parse("minecraft:jukebox_record"), 1),
            // The ender chest facings (Slice 29a — EnderChestBlock lines
            // 114-127: the metadata IS the FACING id, horizontal 2/3/4/5).
            Map.entry(Identifier.parse("minecraft:ender_chest_north"), 2),
            Map.entry(Identifier.parse("minecraft:ender_chest_south"), 3),
            Map.entry(Identifier.parse("minecraft:ender_chest_west"), 4),
            Map.entry(Identifier.parse("minecraft:ender_chest_east"), 5),
            // The chest pair's facing nibbles (Slice L2 — the same
            // Direction-id band the ender chest shipped).
            Map.entry(Identifier.parse("minecraft:chest_north"), 2),
            Map.entry(Identifier.parse("minecraft:chest_south"), 3),
            Map.entry(Identifier.parse("minecraft:chest_west"), 4),
            Map.entry(Identifier.parse("minecraft:chest_east"), 5),
            Map.entry(Identifier.parse("minecraft:trapped_chest_north"), 2),
            Map.entry(Identifier.parse("minecraft:trapped_chest_south"), 3),
            Map.entry(Identifier.parse("minecraft:trapped_chest_west"), 4),
            Map.entry(Identifier.parse("minecraft:trapped_chest_east"), 5),
            // The furnace pair's facing nibbles (Slice L3 — the same band
            // over 61 and 62, FurnaceBlock lines 114-123).
            Map.entry(Identifier.parse("minecraft:furnace_north"), 2),
            Map.entry(Identifier.parse("minecraft:furnace_south"), 3),
            Map.entry(Identifier.parse("minecraft:furnace_west"), 4),
            Map.entry(Identifier.parse("minecraft:furnace_east"), 5),
            Map.entry(Identifier.parse("minecraft:furnace_lit_north"), 2),
            Map.entry(Identifier.parse("minecraft:furnace_lit_south"), 3),
            Map.entry(Identifier.parse("minecraft:furnace_lit_west"), 4),
            Map.entry(Identifier.parse("minecraft:furnace_lit_east"), 5),
            Map.entry(Identifier.parse("minecraft:oak_door_east"), 2),
            Map.entry(Identifier.parse("minecraft:oak_door_south"), 3),
            Map.entry(Identifier.parse("minecraft:oak_door_open"), 4),
            Map.entry(Identifier.parse("minecraft:oak_door_open_north"), 5),
            Map.entry(Identifier.parse("minecraft:oak_door_open_east"), 6),
            Map.entry(Identifier.parse("minecraft:oak_door_open_south"), 7),
            Map.entry(Identifier.parse("minecraft:oak_door_upper"), 8),
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
            // The stairs' HALF bit (StairsBlock.getMetadataFromState:
            // (HALF == TOP ? 4 : 0) | (5 - FACING.getId()) — the east
            // rides the 4 default, the rest +4).
            Map.entry(Identifier.parse("minecraft:oak_stairs_east_top"), 4),
            Map.entry(Identifier.parse("minecraft:oak_stairs_west_top"), 5),
            Map.entry(Identifier.parse("minecraft:oak_stairs_south_top"), 6),
            Map.entry(Identifier.parse("minecraft:oak_stairs_north_top"), 7),
            Map.entry(Identifier.parse("minecraft:cobblestone_stairs_west"), 1),
            Map.entry(Identifier.parse("minecraft:cobblestone_stairs_south"), 2),
            Map.entry(Identifier.parse("minecraft:cobblestone_stairs_north"), 3),
            Map.entry(Identifier.parse("minecraft:cobblestone_stairs_east_top"), 4),
            Map.entry(Identifier.parse("minecraft:cobblestone_stairs_west_top"), 5),
            Map.entry(Identifier.parse("minecraft:cobblestone_stairs_south_top"), 6),
            Map.entry(Identifier.parse("minecraft:cobblestone_stairs_north_top"), 7),
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
            // The infested family (Slice 18c — InfestedBlock.Variant's own
            // ids: 0 stone, 1 cobble, 2 stonebrick, 3 mossy, 4 cracked,
            // 5 chiseled; the pre-18c row's 4 carried the WRONG variant —
            // the stronghold's pick is STONEBRICK, the reference's own
            // getStateFromMetadata(2)).
            Map.entry(Identifier.parse("minecraft:infested_stone"), 0),
            Map.entry(Identifier.parse("minecraft:infested_cobblestone"), 1),
            Map.entry(Identifier.parse("minecraft:infested_stone_bricks"), 2),
            Map.entry(Identifier.parse("minecraft:infested_mossy_stone_bricks"), 3),
            Map.entry(Identifier.parse("minecraft:infested_cracked_stone_bricks"), 4),
            Map.entry(Identifier.parse("minecraft:infested_chiseled_stone_bricks"), 5),
            Map.entry(Identifier.parse("minecraft:stone_brick_slab"), 5),
            Map.entry(Identifier.parse("minecraft:stone_brick_slab_top"), 13),
            // The 43-family variant doubles' nibbles + the SEAMLESS bit
            // (Slice L5 — DoubleStoneSlabBlock's own fold).
            Map.entry(Identifier.parse("minecraft:double_sandstone_slab"), 1),
            Map.entry(Identifier.parse("minecraft:double_cobblestone_slab"), 3),
            Map.entry(Identifier.parse("minecraft:double_stone_brick_slab"), 5),
            Map.entry(Identifier.parse("minecraft:double_stone_slab_seamless"), 8),
            Map.entry(Identifier.parse("minecraft:double_stone_slab2_seamless"), 8),
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
            Map.entry(Identifier.parse("minecraft:nether_brick_stairs_east_top"), 4),
            Map.entry(Identifier.parse("minecraft:nether_brick_stairs_west_top"), 5),
            Map.entry(Identifier.parse("minecraft:nether_brick_stairs_south_top"), 6),
            Map.entry(Identifier.parse("minecraft:nether_brick_stairs_north_top"), 7),
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
            // The dirt variants' nibbles (VIII14's surface slice —
            // DirtBlock.VARIANT: coarse 1, podzol 2).
            Map.entry(Identifier.parse("minecraft:coarse_dirt"), 1),
            Map.entry(Identifier.parse("minecraft:podzol"), 2),
            Map.entry(Identifier.parse("minecraft:stained_clay_orange"), 1),
            Map.entry(Identifier.parse("minecraft:stained_clay_yellow"), 4),
            Map.entry(Identifier.parse("minecraft:vine"), 15)));

    /**
     * The dye-coloring families' nibble merge (Slice 31): every family
     * member's color rides the metadata nibble WHOLE (the DyeColor getId
     * domain — the wire's data value for all five families), including the
     * plain white wool's explicit 0 (the damage-aware reverse's
     * {@code 35|0} claim) and the temple pair's own 1/4 (their entries
     * live in the base map — this merge only adds what it lacks).
     */
    private static Map<Identifier, Integer> withDyeFamilyMetadata(Map<Identifier, Integer> base) {
        Map<Identifier, Integer> merged = new HashMap<>(base);
        // The complete door families' metadata nibbles (Slice 34 —
        // DoorBlock.getMetadataFromState 1:1): the lower's facing band
        // (clockwise horizontal id — the shipped oak rows above) with the
        // open bit 4, the upper's half bit 8 | hinge 1 | powered 2 with the
        // engine's open flag 4. The oak's manual-open upper is corrected
        // from the historical 9 (a right-hinge claim) to 12 — the exact
        // byte a vanilla client decodes for an unpowered open upper.
        int[] doorIds = {64, 193, 194, 195, 196, 197, 71};
        String[] woods = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "iron"};
        for (int w = 0; w < woods.length; w++) {
            String wood = woods[w];
            boolean iron = wood.equals("iron");
            // the lower band: meta 0 rides the id's own bare/west name
            merged.put(Identifier.parse("minecraft:" + wood + "_door_north"), 1);
            merged.put(Identifier.parse("minecraft:" + wood + "_door_east"), 2);
            merged.put(Identifier.parse("minecraft:" + wood + "_door_south"), 3);
            String openBare = wood + "_door_open" + (iron ? "_west" : "");
            merged.put(Identifier.parse("minecraft:" + openBare), 4);
            merged.put(Identifier.parse("minecraft:" + wood + "_door_open_north"), 5);
            merged.put(Identifier.parse("minecraft:" + wood + "_door_open_east"), 6);
            merged.put(Identifier.parse("minecraft:" + wood + "_door_open_south"), 7);
            // the upper grid: 8 | right 1 | powered 2 | open 4
            merged.put(Identifier.parse("minecraft:" + wood + "_door_upper"), 8);
            merged.put(Identifier.parse("minecraft:" + wood + "_door_upper_right"), 9);
            merged.put(Identifier.parse("minecraft:" + wood + "_door_upper_powered"), 10);
            merged.put(Identifier.parse("minecraft:" + wood + "_door_upper_right_powered"), 11);
            merged.put(Identifier.parse("minecraft:" + wood + "_door_upper_open"), 12);
            merged.put(Identifier.parse("minecraft:" + wood + "_door_upper_right_open"), 13);
            merged.put(Identifier.parse("minecraft:" + wood + "_door_upper_powered_open"), 14);
            merged.put(Identifier.parse("minecraft:" + wood + "_door_upper_right_powered_open"), 15);
        }
        // The wood-variant foundation's metadata nibbles (Slice 35 — the
        // reference's own encodings: planks/saplings carry the bare variant,
        // logs and leaves the & 3 band of their domain, the Log2/Leaves2
        // pair re-basing acacia/dark_oak to 0/1).
        {
            String[] woodNames = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak"};
            for (int variant = 1; variant < woodNames.length; variant++) {
                for (String family : new String[] {"planks", "log", "leaves", "sapling"}) {
                    int meta = (family.equals("log") || family.equals("leaves"))
                            ? variant & 3 : variant;
                    merged.put(Identifier.parse(
                            "minecraft:" + woodNames[variant] + "_" + family), meta);
                }
            }
            // The tree-growth bands' nibbles (the VIII2/VIII3 slice): the
            // leaf's variant band | persistent bit 4 | check bit 8
            // (LeavesBlock.getMetadataFromState lines 96-104), the sapling's
            // variant | stage bit 8 (SaplingBlock lines 197-204).
            for (int variant = 0; variant < woodNames.length; variant++) {
                String wood = woodNames[variant];
                int leafBase = variant & 3;
                merged.put(Identifier.parse("minecraft:" + wood + "_leaves_persistent"),
                        leafBase | 4);
                merged.put(Identifier.parse("minecraft:" + wood + "_leaves_check"),
                        leafBase | 8);
                merged.put(Identifier.parse("minecraft:" + wood + "_leaves_persistent_check"),
                        leafBase | 12);
                merged.put(Identifier.parse("minecraft:" + wood + "_sapling_ready"), variant | 8);
            }
        }
        // The building-shape families' metadata nibbles (Slice 36): the
        // stairs' ascending facing band 0-3 and the wooden slabs' variant
        // nibble with the top's bit 8.
        {
            String[] stairFamilies = {"spruce", "birch", "jungle", "acacia",
                    "dark_oak", "brick", "stone_brick", "quartz"};
            String[] facingSuffixes = {"_east", "_west", "_south", "_north"};
            for (String family : stairFamilies) {
                for (int s = 0; s < facingSuffixes.length; s++) {
                    merged.put(Identifier.parse(
                            "minecraft:" + family + "_stairs" + facingSuffixes[s]), s);
                    // The HALF bit's upside-down forms (+4).
                    merged.put(Identifier.parse(
                            "minecraft:" + family + "_stairs" + facingSuffixes[s] + "_top"),
                            s | 4);
                }
            }
            String[] slabWoods = {"spruce", "birch", "jungle", "acacia", "dark_oak"};
            for (int variant = 1; variant < 6; variant++) {
                String wood = slabWoods[variant - 1];
                merged.put(Identifier.parse("minecraft:" + wood + "_slab"), variant);
                merged.put(Identifier.parse("minecraft:" + wood + "_slab_top"), variant | 8);
            }
        }
        // The fence-gate families' metadata nibble (Slice 37 —
        // FenceGateBlock.getMetadataFromState lines 136-153: the horizontal
        // facing id S=0 W=1 N=2 E=3 | open 4 | powered 8; the bare meta-0
        // form needs no row — metadataOf's 0 default).
        {
            String[] gateWoods = {"oak", "spruce", "birch", "jungle", "dark_oak", "acacia"};
            String[] gateFacings = {"south", "west", "north", "east"};
            for (String wood : gateWoods) {
                for (int facing = 0; facing < 4; facing++) {
                    for (int open = 0; open <= 1; open++) {
                        for (int powered = 0; powered <= 1; powered++) {
                            int meta = facing | (open == 1 ? 4 : 0) | (powered == 1 ? 8 : 0);
                            if (meta == 0) {
                                continue; // the bare form rides the 0 default
                            }
                            StringBuilder name = new StringBuilder("minecraft:")
                                    .append(wood).append("_fence_gate");
                            if (powered == 1) {
                                name.append("_powered");
                            }
                            if (open == 1) {
                                name.append("_open");
                            }
                            if (facing != 0) {
                                name.append('_').append(gateFacings[facing]);
                            }
                            merged.put(Identifier.parse(name.toString()), meta);
                        }
                    }
                }
            }
        }
        // The rail trio's metadata nibble (Slice 38 —
        // PoweredRailBlock.getMetadataFromState lines 150-163: the shape id
        // 0-5 (Shape.byId: north_south, east_west, the four ascents) |
        // powered 8; the bare meta-0 form needs no row).
        {
            String[] railFamilies = {"golden_rail", "detector_rail", "activator_rail"};
            String[] railShapes = {"", "_east_west", "_ascending_east", "_ascending_west",
                    "_ascending_north", "_ascending_south"};
            for (String family : railFamilies) {
                for (int shape = 0; shape < 6; shape++) {
                    for (int powered = 0; powered <= 1; powered++) {
                        int meta = shape | (powered == 1 ? 8 : 0);
                        if (meta == 0) {
                            continue; // the bare form rides the 0 default
                        }
                        StringBuilder name = new StringBuilder("minecraft:").append(family);
                        if (powered == 1) {
                            name.append("_powered");
                        }
                        name.append(railShapes[shape]);
                        merged.put(Identifier.parse(name.toString()), meta);
                    }
                }
            }
        }
        // The trapdoor pair's metadata nibble (Slice 39 —
        // TrapdoorBlock.getMetadataFromState lines 206-218: the facing band
        // N=0 S=1 W=2 E=3 | open 4 | top 8; the bare meta-0 form needs no
        // row).
        {
            String[] trapdoorFamilies = {"trapdoor", "iron_trapdoor"};
            String[] trapdoorFacings = {"", "_south", "_west", "_east"};
            for (String family : trapdoorFamilies) {
                for (int facing = 0; facing < 4; facing++) {
                    for (int open = 0; open <= 1; open++) {
                        for (int top = 0; top <= 1; top++) {
                            int meta = facing | (open == 1 ? 4 : 0) | (top == 1 ? 8 : 0);
                            if (meta == 0) {
                                continue;
                            }
                            StringBuilder name = new StringBuilder("minecraft:").append(family);
                            if (open == 1) {
                                name.append("_open");
                            }
                            if (top == 1) {
                                name.append("_top");
                            }
                            name.append(trapdoorFacings[facing]);
                            merged.put(Identifier.parse(name.toString()), meta);
                        }
                    }
                }
            }
        }
        // The huge-mushroom pair's metadata nibble (Slice 40 —
        // MushroomBlock.getMetadataFromState: the VARIANT id whole; the
        // bare form carries the all_outside 14 — NOT the 0 default, the
        // all_inside's explicit row owns 0).
        {
            String[] mushroomFamilies = {"brown_mushroom_block", "red_mushroom_block"};
            String[][] mushroomVariants = {
                    {"_all_inside", "0"}, {"_north_west", "1"}, {"_north", "2"},
                    {"_north_east", "3"}, {"_west", "4"}, {"_center", "5"},
                    {"_east", "6"}, {"_south_west", "7"}, {"_south", "8"},
                    {"_south_east", "9"}, {"_stem", "10"}, {"", "14"}, {"_all_stem", "15"}};
            for (String family : mushroomFamilies) {
                for (String[] variant : mushroomVariants) {
                    if (variant[0].isEmpty()) {
                        continue; // the bare form's explicit row below
                    }
                    merged.put(Identifier.parse("minecraft:" + family + variant[0]),
                            Integer.parseInt(variant[1]));
                }
                merged.put(Identifier.parse("minecraft:" + family), 14);
            }
        }
        // The melon family's stem stages (Slice 41 —
        // StemBlock.getMetadataFromState: the AGE whole; stage 0 rides the
        // 0 default).
        for (int stage = 1; stage <= 7; stage++) {
            merged.put(Identifier.parse("minecraft:pumpkin_stem_stage" + stage), stage);
            merged.put(Identifier.parse("minecraft:melon_stem_stage" + stage), stage);
        }
        // The cocoa's metadata nibble (Slice 43 — getMetadataFromState:
        // facing | AGE << 2; the bare meta-0 form rides the 0 default).
        for (int facing = 0; facing < 4; facing++) {
            for (int age = 0; age <= 2; age++) {
                int meta = facing | (age << 2);
                if (meta == 0) {
                    continue;
                }
                StringBuilder name = new StringBuilder("minecraft:cocoa");
                if (age > 0) {
                    name.append("_age").append(age);
                }
                if (facing != 0) {
                    name.append('_').append(new String[]{"south", "west", "north", "east"}[facing]);
                }
                merged.put(Identifier.parse(name.toString()), meta);
            }
        }
        // The flower family's metadata nibble (Slice 44 —
        // FlowerBlock.getMetadataFromState: the TYPE id whole; the poppy's
        // own 0 row folds the plain (38, 0) reverse to the poppy so a held
        // family item's damage domain resolves every variant — the slab
        // families' damage-aware overload's own model).
        {
            String[] flowerTypes = {"blue_orchid", "allium", "houstonia",
                    "red_tulip", "orange_tulip", "white_tulip", "pink_tulip",
                    "oxeye_daisy"};
            for (int type = 1; type <= flowerTypes.length; type++) {
                merged.put(Identifier.parse("minecraft:" + flowerTypes[type - 1]), type);
            }
            merged.put(Identifier.parse("minecraft:poppy"), 0);
        }
        // The double-plant family's metadata nibble (Slice 45 —
        // DoublePlantBlock.getMetadataFromState line 242: the LOWER the
        // VARIANT id whole; the UPPER 8 | FACING's horizontal — the
        // default NORTH write, meta 10 for every placed upper).
        {
            String[] dpVariants = {"sunflower", "lilac", "double_grass",
                    "double_fern", "rose", "peony"};
            for (int variant = 0; variant < dpVariants.length; variant++) {
                merged.put(Identifier.parse("minecraft:" + dpVariants[variant]), variant);
                merged.put(Identifier.parse("minecraft:" + dpVariants[variant] + "_upper"), 10);
            }
        }
        // The cake's metadata nibble (Slice 46 —
        // CakeBlock.getMetadataFromState: the BITES id whole; the bare
        // bites-0 form rides the 0 default).
        for (int bites = 1; bites <= 6; bites++) {
            merged.put(Identifier.parse("minecraft:cake_bite" + bites), bites);
        }
        // The wall's metadata nibble (Slice 47 —
        // WallBlock.getMetadataFromState: the VARIANT id whole; the NORMAL
        // form's 0 rides the 0 default — the item's damage domain resolves
        // the mossy form through the fold's own (139, 1) row).
        merged.put(Identifier.parse("minecraft:mossy_cobblestone_wall"), 1);
        // The flower-pot's metadata nibble (Slice 48 —
        // FlowerPotBlock.getMetadataFromState: the LEGACY_DATA whole; the
        // empty form's 0 rides the 0 default).
        {
            String[] potContents = {"potted_poppy", "potted_dandelion",
                    "potted_oak_sapling", "potted_spruce_sapling",
                    "potted_birch_sapling", "potted_jungle_sapling",
                    "potted_red_mushroom", "potted_brown_mushroom",
                    "potted_cactus", "potted_dead_bush", "potted_fern",
                    "potted_acacia_sapling", "potted_dark_oak_sapling"};
            for (int content = 1; content <= potContents.length; content++) {
                merged.put(Identifier.parse("minecraft:" + potContents[content - 1]), content);
            }
        }
        // The weighted plates' metadata nibble (Slice 49a —
        // WeightedPressurePlateBlock.getMetadataFromState: the POWER value
        // whole; the unpowered form rides the 0 default).
        for (int family = 0; family <= 1; family++) {
            String key = family == 0 ? "light_weighted_pressure_plate"
                    : "heavy_weighted_pressure_plate";
            for (int power = 1; power <= 15; power++) {
                merged.put(Identifier.parse("minecraft:" + key + "_power" + power), power);
            }
        }
        // The red sandstone family's metadata nibbles (Slice 52 — the
        // reference's own bands): the red sand's variant id 1 (SandBlock.
        // Variant.RED_SAND), the TYPE ids over 179 (RedSandstoneBlock.
        // Type.byId's band: 1 chiseled, 2 smooth), the stairs' facing band
        // over 180 (the Bukkit E=0 W=1 S=2 N=3, the bare east the 0
        // default) and the slab's HALF bit 8 over 182.
        merged.put(Identifier.parse("minecraft:red_sand"), 1);
        merged.put(Identifier.parse("minecraft:chiseled_red_sandstone"), 1);
        merged.put(Identifier.parse("minecraft:smooth_red_sandstone"), 2);
        merged.put(Identifier.parse("minecraft:red_sandstone_stairs_west"), 1);
        merged.put(Identifier.parse("minecraft:red_sandstone_stairs_south"), 2);
        merged.put(Identifier.parse("minecraft:red_sandstone_stairs_north"), 3);
        merged.put(Identifier.parse("minecraft:red_sandstone_stairs_east_top"), 4);
        merged.put(Identifier.parse("minecraft:red_sandstone_stairs_west_top"), 5);
        merged.put(Identifier.parse("minecraft:red_sandstone_stairs_south_top"), 6);
        merged.put(Identifier.parse("minecraft:red_sandstone_stairs_north_top"), 7);
        // The sandstone stairs' own facing band over 128 — THE AUDIT'S
        // CATCH: the shipped village slice never registered the four
        // nibble rows, so every sandstone stair carried meta 0 (the east
        // form) on the wire; the band lands with the HALF bit's forms.
        merged.put(Identifier.parse("minecraft:sandstone_stairs_west"), 1);
        merged.put(Identifier.parse("minecraft:sandstone_stairs_south"), 2);
        merged.put(Identifier.parse("minecraft:sandstone_stairs_north"), 3);
        merged.put(Identifier.parse("minecraft:sandstone_stairs_east_top"), 4);
        merged.put(Identifier.parse("minecraft:sandstone_stairs_west_top"), 5);
        merged.put(Identifier.parse("minecraft:sandstone_stairs_south_top"), 6);
        merged.put(Identifier.parse("minecraft:sandstone_stairs_north_top"), 7);
        merged.put(Identifier.parse("minecraft:stone_slab2_top"), 8);
        // The anvil's metadata nibbles (Slice 53 — AnvilBlock.
        // getMetadataFromState: the FACING horizontal id whole in the low
        // pair, the DAMAGE << 2 in the high pair; the bare south forms ride
        // the 0/4/8 defaults).
        for (String[] row : new String[][] {
                {"anvil_west", "1"}, {"anvil_north", "2"}, {"anvil_east", "3"},
                {"anvil_chipped", "4"}, {"anvil_chipped_west", "5"},
                {"anvil_chipped_north", "6"}, {"anvil_chipped_east", "7"},
                {"anvil_damaged", "8"}, {"anvil_damaged_west", "9"},
                {"anvil_damaged_north", "10"}, {"anvil_damaged_east", "11"}}) {
            merged.put(Identifier.parse("minecraft:" + row[0]), Integer.parseInt(row[1]));
        }
        // The double wooden slabs' metadata nibbles (Slice 58 — the planks
        // variant id whole: 1 spruce .. 5 dark oak; the oak rides the 0
        // default).
        merged.put(Identifier.parse("minecraft:double_spruce_slab"), 1);
        merged.put(Identifier.parse("minecraft:double_birch_slab"), 2);
        merged.put(Identifier.parse("minecraft:double_jungle_slab"), 3);
        merged.put(Identifier.parse("minecraft:double_acacia_slab"), 4);
        merged.put(Identifier.parse("minecraft:double_dark_oak_slab"), 5);
        merged.put(Identifier.parse("minecraft:wool"), 0);
        for (int color = 0; color < 16; color++) {
            if (color != 0) {
                merged.put(DyeFamilyIds.woolBlock(color), color);
            }
            merged.put(DyeFamilyIds.stainedGlassBlock(color), color);
            merged.put(DyeFamilyIds.stainedGlassPaneBlock(color), color);
            merged.put(DyeFamilyIds.carpetBlock(color), color);
            if (color != 1 && color != 4) {
                merged.put(DyeFamilyIds.stainedClayBlock(color), color);
            }
        }
        return Map.copyOf(merged);
    }

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
        if (value.startsWith("daylight_detector_")) {
            // The POWER property IS the nibble (DaylightDetectorBlock lines
            // 123-130: POWER = metadata, metadata = POWER).
            return Integer.parseInt(value.substring(value.lastIndexOf('p') + 1));
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
        if (value.startsWith("cauldron_level")) {
            // The LEVEL property rides the nibble whole — CauldronBlock
            // lines 203-205 (getMetadataFromState = state.get(LEVEL)).
            return Integer.parseInt(value.substring("cauldron_level".length()));
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
        // The ender chest family folds its facings in (the nibble IS the
        // reference Direction id — 130|2 resolves the north state).
        BY_IDENTIFIER.forEach((identifier, legacy) -> {
            if (identifier.namespace().equals("minecraft")
                    && identifier.value().startsWith("ender_chest_")) {
                reversed.put((legacy << 4) | metadataOf(identifier), identifier);
            }
        });
        // The chest pair folds its facings in too (Slice L2 — 54|2 and
        // 146|2 resolve the north states; the bare meta-0 forms keep the
        // item's own rows).
        BY_IDENTIFIER.forEach((identifier, legacy) -> {
            if (identifier.namespace().equals("minecraft")
                    && (identifier.value().startsWith("chest_")
                    || identifier.value().startsWith("trapped_chest_"))) {
                reversed.put((legacy << 4) | metadataOf(identifier), identifier);
            }
        });
        // The stone-slab family's doubles fold their nibbles in (Slice
        // L5 — 43|1/3/5/8 and 181|8 resolve the variant doubles and the
        // seamless skins).
        BY_IDENTIFIER.forEach((identifier, legacy) -> {
            if (identifier.namespace().equals("minecraft")
                    && (identifier.value().startsWith("double_sandstone_slab")
                    || identifier.value().startsWith("double_cobblestone_slab")
                    || identifier.value().startsWith("double_stone_brick_slab")
                    || identifier.value().startsWith("double_stone_slab_seamless")
                    || identifier.value().startsWith("double_stone_slab2_seamless"))) {
                reversed.put((legacy << 4) | metadataOf(identifier), identifier);
            }
        });
        // The furnace pair folds its facings in too (Slice L3 — 61|2 and
        // 62|2 resolve the north states).
        BY_IDENTIFIER.forEach((identifier, legacy) -> {
            if (identifier.namespace().equals("minecraft")
                    && (identifier.value().startsWith("furnace_")
                    || identifier.value().startsWith("furnace_lit_"))) {
                reversed.put((legacy << 4) | metadataOf(identifier), identifier);
            }
        });
        // The daylight detector family folds its own nibble space in (the
        // POWER dial 0-15 — the reverse resolves every state).
        BY_IDENTIFIER.forEach((identifier, legacy) -> {
            if (identifier.namespace().equals("minecraft")
                    && (identifier.value().startsWith("daylight_detector_p")
                    || identifier.value().startsWith("daylight_detector_inverted_p"))) {
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
