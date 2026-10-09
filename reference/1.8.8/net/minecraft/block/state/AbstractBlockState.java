package net.minecraft.block.state;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map.Entry;
import net.minecraft.block.Block;
import net.minecraft.block.state.property.Property;

public abstract class AbstractBlockState implements BlockState {
    private static final Joiner ENTRY_JOINER = Joiner.on(',');
    private static final Function<Entry<Property, Comparable>, String> ENTRY_TO_STRING = new Function<Entry<Property, Comparable>, String>() {
        public String apply(Entry<Property, Comparable> entry) {
            if (entry == null) {
                return "<NULL>";
            }

            Property property = entry.getKey();
            return property.getName() + "=" + property.getName(entry.getValue());
        }
    };

    @Override
    public <T extends Comparable<T>> BlockState next(Property<T> property) {
        return this.set(property, findNext(property.values(), this.get(property)));
    }

    protected static <T> T findNext(Collection<T> values, T current) {
        Iterator<T> iterator = values.iterator();

        while (iterator.hasNext()) {
            if (iterator.next().equals(current)) {
                if (iterator.hasNext()) {
                    return iterator.next();
                }

                return values.iterator().next();
            }
        }

        return iterator.next();
    }

    @Override
    public String toString() {
        StringBuilder stringbuilder = new StringBuilder();
        stringbuilder.append(Block.REGISTRY.getKey(this.getBlock()));
        if (!this.values().isEmpty()) {
            stringbuilder.append("[");
            ENTRY_JOINER.appendTo(stringbuilder, Iterables.transform(this.values().entrySet(), ENTRY_TO_STRING));
            stringbuilder.append("]");
        }

        return stringbuilder.toString();
    }
}
