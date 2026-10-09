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
            Map.entry(Identifier.parse("minecraft:torch"), 50),
            Map.entry(Identifier.parse("minecraft:furnace"), 61),
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
            Map.entry(Identifier.parse("minecraft:flint"), 318),      // the 10% gravel roll
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
            Map.entry(Identifier.parse("minecraft:wheat_stage7"), 59));

    private static final Map<Integer, Identifier> BY_LEGACY_ID = reverse();

    private static Map<Integer, Identifier> reverse() {
        Map<Integer, Identifier> reversed = new HashMap<>();
        BY_IDENTIFIER.forEach((identifier, legacyId) -> reversed.put(legacyId, identifier));
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
    private static final Map<Identifier, Integer> METADATA = Map.of(
            Identifier.parse("minecraft:tall_grass"), 1);

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

    /** @return the canonical identifier for a legacy id, or empty if unknown. */
    static Optional<Identifier> identifierOf(int legacyId) {
        return Optional.ofNullable(BY_LEGACY_ID.get(legacyId));
    }
}
