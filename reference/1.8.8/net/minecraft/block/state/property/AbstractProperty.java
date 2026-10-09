package net.minecraft.block.state.property;

import com.google.common.base.Objects;

public abstract class AbstractProperty<T extends Comparable<T>> implements Property<T> {
    private final Class<T> type;
    private final String name;

    protected AbstractProperty(String name, Class<T> type) {
        this.type = type;
        this.name = name;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public Class<T> getType() {
        return this.type;
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this).add("name", this.name).add("clazz", this.type).add("values", this.values()).toString();
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        } else if (object != null && this.getClass() == object.getClass()) {
            AbstractProperty abstractproperty = (AbstractProperty)object;
            return this.type.equals(abstractproperty.type) && this.name.equals(abstractproperty.name);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return 31 * this.type.hashCode() + this.name.hashCode();
    }
}
