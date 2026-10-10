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

    private static final Map<Identifier, Integer> BY_IDENTIFIER = Map.ofEntries(
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
            // The vehicle slice (community blocks.json + items.json): the
            // rail block 66 (orientation in the metadata), the boat item
            // 333, the minecart item 328.
            Map.entry(Identifier.parse("minecraft:rail"), 66),
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
            Map.entry(Identifier.parse("minecraft:emerald"), 388));

    /**
     * Item-form ids that diverge from the block id sharing the identifier
     * (the sign: block 63 on the chunk wire, item 323 in slots — the
     * historical split). Consulted by the slot/item wire paths only.
     * Declared before the reverse map: reverse() folds it in at init.
     */
    private static final Map<Identifier, Integer> ITEM_OVERRIDES = Map.of(
            Identifier.parse("minecraft:sign"), 323,
            Identifier.parse("minecraft:oak_door"), 324,
            Identifier.parse("minecraft:ladder"), 65,
            Identifier.parse("minecraft:bed"), 355,
            Identifier.parse("minecraft:sugar_cane"), 338,
            // The stairs items (the block types carry facing suffixes; the
            // item keeps the bare historical identifier, item id = block id).
            Identifier.parse("minecraft:oak_stairs"), 53,
            Identifier.parse("minecraft:cobblestone_stairs"), 67);

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
            Map.entry(Identifier.parse("minecraft:nether_portal_z"), 2));

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
        return fluid == null ? 0 : FluidBlocks.levelOf(identifier);
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
        return Map.copyOf(reversed);
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
