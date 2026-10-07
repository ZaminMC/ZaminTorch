package net.zamin.engine.block;

import net.zamin.api.BlockType;
import net.zamin.api.Identifier;

import java.util.Objects;

/** Engine implementation of the public block type identity. */
public final class EngineBlockType implements BlockType {

    private final Identifier identifier;
    private final String displayName;

    public EngineBlockType(Identifier identifier, String displayName) {
        this.identifier = Objects.requireNonNull(identifier, "identifier");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
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
