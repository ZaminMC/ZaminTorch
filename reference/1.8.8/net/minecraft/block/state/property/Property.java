package net.minecraft.block.state.property;

import java.util.Collection;

public interface Property<T extends Comparable<T>> {
    String getName();

    Collection<T> values();

    Class<T> getType();

    String getName(T value);
}
