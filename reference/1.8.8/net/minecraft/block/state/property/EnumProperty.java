package net.minecraft.block.state.property;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.Collection;
import java.util.Map;
import net.minecraft.util.StringSerializable;

public class EnumProperty<T extends Enum<T> & StringSerializable> extends AbstractProperty<T> {
    private final ImmutableSet<T> values;
    private final Map<String, T> valuesByName = Maps.newHashMap();

    protected EnumProperty(String name, Class<T> type, Collection<T> values) {
        super(name, type);
        this.values = ImmutableSet.copyOf(values);

        for (T t : values) {
            String s = t.serializeToString();
            if (this.valuesByName.containsKey(s)) {
                throw new IllegalArgumentException("Multiple values have the same name '" + s + "'");
            }

            this.valuesByName.put(s, t);
        }
    }

    @Override
    public Collection<T> values() {
        return this.values;
    }

    public String getName(T enum_) {
        return enum_.serializeToString();
    }

    public static <T extends Enum<T> & StringSerializable> EnumProperty<T> of(String name, Class<T> type) {
        return of(name, type, Predicates.alwaysTrue());
    }

    public static <T extends Enum<T> & StringSerializable> EnumProperty<T> of(String name, Class<T> type, Predicate<T> filter) {
        return of(name, type, Collections2.filter(Lists.newArrayList(type.getEnumConstants()), filter));
    }

    public static <T extends Enum<T> & StringSerializable> EnumProperty<T> of(String name, Class<T> type, T... values) {
        return of(name, type, Lists.newArrayList(values));
    }

    public static <T extends Enum<T> & StringSerializable> EnumProperty<T> of(String name, Class<T> type, Collection<T> values) {
        return new EnumProperty<>(name, type, values);
    }
}
