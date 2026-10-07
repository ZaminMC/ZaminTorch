package net.zamin.protocol.v1_8;

import net.zamin.api.Identifier;

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
            Identifier.parse("minecraft:bedrock"), 7);

    /** @return the legacy numeric id, or empty when this adapter cannot represent the type. */
    static Optional<Integer> legacyId(Identifier identifier) {
        return Optional.ofNullable(BY_IDENTIFIER.get(identifier));
    }
}
