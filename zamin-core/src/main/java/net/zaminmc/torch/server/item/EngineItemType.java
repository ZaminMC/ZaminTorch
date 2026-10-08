package net.zaminmc.torch.server.item;

import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.item.ItemType;

import java.util.Objects;

/** Engine implementation of the public item identity. */
public final class EngineItemType implements ItemType {

    private final Identifier identifier;
    private final String displayName;
    private final int maxStackSize;
    private final int maxDurability;

    public EngineItemType(Identifier identifier, String displayName, int maxStackSize) {
        this(identifier, displayName, maxStackSize, 0);
    }

    public EngineItemType(Identifier identifier, String displayName, int maxStackSize,
                          int maxDurability) {
        this.identifier = Objects.requireNonNull(identifier, "identifier");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        if (maxStackSize < 1 || maxStackSize > 99) {
            throw new IllegalArgumentException("maxStackSize out of range: " + maxStackSize);
        }
        if (maxDurability < 0) {
            throw new IllegalArgumentException("maxDurability must not be negative: " + maxDurability);
        }
        this.maxStackSize = maxStackSize;
        this.maxDurability = maxDurability;
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
    public int maxStackSize() {
        return maxStackSize;
    }

    @Override
    public int maxDurability() {
        return maxDurability;
    }

    @Override
    public boolean equals(Object obj) {
        // Identity equality by identifier: two definitions of minecraft:dirt must not coexist.
        return obj instanceof EngineItemType other && identifier.equals(other.identifier);
    }

    @Override
    public int hashCode() {
        return identifier.hashCode();
    }

    @Override
    public String toString() {
        return "ItemType[" + identifier + "]";
    }
}
