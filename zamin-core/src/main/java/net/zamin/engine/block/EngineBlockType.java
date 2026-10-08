package net.zamin.engine.block;

import net.zamin.api.BlockType;
import net.zamin.api.Identifier;

import java.util.Objects;

/**
 * Engine implementation of the public block type identity.
 *
 * <p>Types are immutable and identified by identifier. The optional legacy
 * metadata field exists for kinds whose 1.8 wire form is
 * {@code (legacy id << 4) | metadata} with a meaningful metadata nibble —
 * fluids: still water is legacy 9 metadata 0, flowing water is legacy 8 with
 * the flow level 1..7 in the nibble (8 = the falling column), lava likewise
 * (11 still / 10 flowing). Ordinary blocks keep metadata 0. The metadata
 * never leaves the boundary: it is translation data the version adapter
 * reads, exactly like the legacy id itself (§579).</p>
 */
public final class EngineBlockType implements BlockType {

    private final Identifier identifier;
    private final String displayName;
    private final int legacyMetadata;

    public EngineBlockType(Identifier identifier, String displayName) {
        this(identifier, displayName, 0);
    }

    public EngineBlockType(Identifier identifier, String displayName, int legacyMetadata) {
        this.identifier = Objects.requireNonNull(identifier, "identifier");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.legacyMetadata = legacyMetadata;
    }

    /** @return the 1.8 wire metadata nibble for this kind (0 for plain blocks). */
    public int legacyMetadata() {
        return legacyMetadata;
    }

    @Override
    public Identifier identifier() {
        return identifier;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public boolean equals(Object obj) {
        // Identity equality by identifier: two definitions of minecraft:stone must not coexist.
        return obj instanceof EngineBlockType other && identifier.equals(other.identifier);
    }

    @Override
    public int hashCode() {
        return identifier.hashCode();
    }

    @Override
    public String toString() {
        return "BlockType[" + identifier + "]";
    }
}
