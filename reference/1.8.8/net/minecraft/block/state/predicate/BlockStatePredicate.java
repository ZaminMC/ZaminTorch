package net.minecraft.block.state.predicate;

import com.google.common.base.Predicate;
import com.google.common.collect.Maps;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.block.Block;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.Property;

public class BlockStatePredicate implements Predicate<BlockState> {
    private final StateDefinition stateDefinition;
    private final Map<Property, Predicate> properties = Maps.newHashMap();

    private BlockStatePredicate(StateDefinition stateDefinition) {
        this.stateDefinition = stateDefinition;
    }

    public static BlockStatePredicate of(Block block) {
        return new BlockStatePredicate(block.stateDefinition());
    }

    public boolean apply(BlockState blockState) {
        if (blockState != null && blockState.getBlock().equals(this.stateDefinition.getBlock())) {
            for (Entry<Property, Predicate> entry : this.properties.entrySet()) {
                Object object = blockState.get(entry.getKey());
                if (!entry.getValue().apply(object)) {
                    return false;
                }
            }

            return true;
        } else {
            return false;
        }
    }

    public <V extends Comparable<V>> BlockStatePredicate with(Property<V> property, Predicate<? extends V> predicate) {
        if (!this.stateDefinition.properties().contains(property)) {
            throw new IllegalArgumentException(this.stateDefinition + " cannot support property " + property);
        }

        this.properties.put(property, predicate);
        return this;
    }
}
