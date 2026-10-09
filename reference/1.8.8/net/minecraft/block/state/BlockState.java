package net.minecraft.block.state;

import com.google.common.collect.ImmutableMap;
import java.util.Collection;
import net.minecraft.block.Block;
import net.minecraft.block.state.property.Property;

public interface BlockState {
    Collection<Property> properties();

    <T extends Comparable<T>> T get(Property<T> property);

    <T extends Comparable<T>, V extends T> BlockState set(Property<T> property, V value);

    <T extends Comparable<T>> BlockState next(Property<T> property);

    ImmutableMap<Property, Comparable> values();

    Block getBlock();
}
