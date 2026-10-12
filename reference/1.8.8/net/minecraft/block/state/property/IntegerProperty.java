package net.minecraft.block.state.property;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Set;

public class IntegerProperty extends AbstractProperty<Integer> {
    private final ImmutableSet<Integer> values;

    protected IntegerProperty(String name, int min, int max) {
        super(name, Integer.class);
        if (min < 0) {
            throw new IllegalArgumentException("Min value of " + name + " must be 0 or greater");
        }

        if (max <= min) {
            throw new IllegalArgumentException("Max value of " + name + " must be greater than min (" + min + ")");
        }

        Set<Integer> set = Sets.newHashSet();

        for (int i = min; i <= max; i++) {
            set.add(i);
        }

        this.values = ImmutableSet.copyOf(set);
    }

    @Override
    public Collection<Integer> values() {
        return this.values;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (object == null || this.getClass() != object.getClass()) {
            return false;
        }

        if (!super.equals(object)) {
            return false;
        }

        IntegerProperty integerproperty = (IntegerProperty)object;
        return this.values.equals(integerproperty.values);
    }

    @Override
    public int hashCode() {
        int i = super.hashCode();
        return 31 * i + this.values.hashCode();
    }

    public static IntegerProperty of(String name, int min, int max) {
        return new IntegerProperty(name, min, max);
    }

    public String getName(Integer integer) {
        return integer.toString();
    }
}
