package net.zamin.protocol.v1_8;

import net.zamin.api.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Legacy numeric block id translation for protocol 47. This table is
 * translation data at the protocol boundary only (§579): the engine uses
 * canonical identifiers; the wire of 1.8.8 uses numeric ids.
 */
final class LegacyBlockIds {

    private LegacyBlockIds() {
    }

    private static final Map<Identifier, Integer> BY_IDENTIFIER = Map.of(
            Identifier.parse("minecraft:air"), 0,
            Identifier.parse("minecraft:stone"), 1,
            Identifier.parse("minecraft:grass_block"), 2,
            Identifier.parse("minecraft:dirt"), 3,
            Identifier.parse("minecraft:cobblestone"), 4,
            Identifier.parse("minecraft:oak_planks"), 5,   // dataset/legacy name: "planks"
            Identifier.parse("minecraft:oak_log"), 17,     // dataset/legacy name: "log"
            Identifier.parse("minecraft:bedrock"), 7);

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
