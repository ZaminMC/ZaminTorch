package net.minecraft.block.state;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.block.state.property.Property;
import net.minecraft.util.IterableBuilder;
import net.minecraft.util.MapBuilder;

public class StateDefinition {
    private static final Joiner PROPERTY_JOINER = Joiner.on(", ");
    private static final Function<Property, String> PROPERTY_TO_STRING = new Function<Property, String>() {
        public String apply(Property property) {
            return property == null ? "<NULL>" : property.getName();
        }
    };
    private final Block block;
    private final ImmutableList<Property> properties;
    private final ImmutableList<BlockState> states;

    public StateDefinition(Block block, Property... properties) {
        this.block = block;
        Arrays.sort(properties, new Comparator<Property>() {
            public int compare(Property property, Property property2) {
                return property.getName().compareTo(property2.getName());
            }
        });
        this.properties = ImmutableList.copyOf(properties);
        Map<Map<Property, Comparable>, StateDefinition.BlockStateImpl> map = Maps.newLinkedHashMap();
        List<StateDefinition.BlockStateImpl> list = Lists.newArrayList();

        for (List<Comparable> list1 : IterableBuilder.iterableIterableToListIterable(this.collectValues())) {
            Map<Property, Comparable> map1 = MapBuilder.linkedHashMap(this.properties, list1);
            StateDefinition.BlockStateImpl statedefinition$blockstateimpl = new StateDefinition.BlockStateImpl(block, ImmutableMap.copyOf(map1));
            map.put(map1, statedefinition$blockstateimpl);
            list.add(statedefinition$blockstateimpl);
        }

        for (StateDefinition.BlockStateImpl statedefinition$blockstateimpl1 : list) {
            statedefinition$blockstateimpl1.findNeighbors(map);
        }

        this.states = ImmutableList.copyOf(list);
    }

    public ImmutableList<BlockState> all() {
        return this.states;
    }

    private List<Iterable<Comparable>> collectValues() {
        List<Iterable<Comparable>> list = Lists.newArrayList();

        for (int i = 0; i < this.properties.size(); i++) {
            list.add(this.properties.get(i).values());
        }

        return list;
    }

    public BlockState any() {
        return this.states.get(0);
    }

    public Block getBlock() {
        return this.block;
    }

    public Collection<Property> properties() {
        return this.properties;
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
            .add("block", Block.REGISTRY.getKey(this.block))
            .add("properties", Iterables.transform(this.properties, PROPERTY_TO_STRING))
            .toString();
    }

    static class BlockStateImpl extends AbstractBlockState {
        private final Block block;
        private final ImmutableMap<Property, Comparable> values;
        private ImmutableTable<Property, Comparable, BlockState> neighbors;

        private BlockStateImpl(Block block, ImmutableMap<Property, Comparable> values) {
            this.block = block;
            this.values = values;
        }

        @Override
        public Collection<Property> properties() {
            return Collections.unmodifiableCollection(this.values.keySet());
        }

        @Override
        public <T extends Comparable<T>> T get(Property<T> property) {
            if (!this.values.containsKey(property)) {
                throw new IllegalArgumentException("Cannot get property " + property + " as it does not exist in " + this.block.stateDefinition());
            } else {
                return property.getType().cast(this.values.get(property));
            }
        }

        @Override
        public <T extends Comparable<T>, V extends T> BlockState set(Property<T> property, V value) {
            if (!this.values.containsKey(property)) {
                throw new IllegalArgumentException("Cannot set property " + property + " as it does not exist in " + this.block.stateDefinition());
            } else if (!property.values().contains(value)) {
                throw new IllegalArgumentException(
                    "Cannot set property " + property + " to " + value + " on block " + Block.REGISTRY.getKey(this.block) + ", it is not an allowed value"
                );
            } else {
                return this.values.get(property) == value ? this : (BlockState)this.neighbors.get(property, value);
            }
        }

        @Override
        public ImmutableMap<Property, Comparable> values() {
            return this.values;
        }

        @Override
        public Block getBlock() {
            return this.block;
        }

        @Override
        public boolean equals(Object object) {
            return this == object;
        }

        @Override
        public int hashCode() {
            return this.values.hashCode();
        }

        public void findNeighbors(Map<Map<Property, Comparable>, StateDefinition.BlockStateImpl> statesByValues) {
            if (this.neighbors != null) {
                throw new IllegalStateException();
            }

            Table<Property, Comparable, BlockState> table = HashBasedTable.create();

            for (Property<? extends Comparable> property : this.values.keySet()) {
                for (Comparable comparable : property.values()) {
                    if (comparable != this.values.get(property)) {
                        table.put(property, comparable, statesByValues.get(this.getNeighborValues(property, comparable)));
                    }
                }
            }

            this.neighbors = ImmutableTable.copyOf(table);
        }

        private Map<Property, Comparable> getNeighborValues(Property property, Comparable value) {
            Map<Property, Comparable> map = Maps.newHashMap(this.values);
            map.put(property, value);
            return map;
        }
    }
}
