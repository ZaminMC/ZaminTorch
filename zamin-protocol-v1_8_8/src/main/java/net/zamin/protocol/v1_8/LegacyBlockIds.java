package net.zamin.protocol.v1_8;

import net.zamin.api.Identifier;

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
            Map.entry(Identifier.parse("minecraft:crafting_table"), 58),
            Map.entry(Identifier.parse("minecraft:torch"), 50),
            // items yielded by mining and crafting
            Map.entry(Identifier.parse("minecraft:coal"), 263),
            Map.entry(Identifier.parse("minecraft:diamond"), 264),
            Map.entry(Identifier.parse("minecraft:stick"), 280),
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
            Map.entry(Identifier.parse("minecraft:shears"), 359));

    private static final Map<Integer, Identifier> BY_LEGACY_ID = reverse();

    private static Map<Integer, Identifier> reverse() {
        Map<Integer, Identifier> reversed = new HashMap<>();
        BY_IDENTIFIER.forEach((identifier, legacyId) -> reversed.put(legacyId, identifier));
        return Map.copyOf(reversed);
    }

    /** @return the legacy numeric id, or empty when this adapter cannot represent the type. */
    static Optional<Integer> legacyId(Identifier identifier) {
        return Optional.ofNullable(BY_IDENTIFIER.get(identifier));
    }

    /** @return the canonical identifier for a legacy id, or empty if unknown. */
    static Optional<Identifier> identifierOf(int legacyId) {
        return Optional.ofNullable(BY_LEGACY_ID.get(legacyId));
    }
}
