package net.minecraft.entity.living.attribute;

public abstract class BaseEntityAttribute implements EntityAttribute {
    private final EntityAttribute parent;
    private final String name;
    private final double defaultValue;
    private boolean trackable;

    protected BaseEntityAttribute(EntityAttribute parent, String name, double defaultValue) {
        this.parent = parent;
        this.name = name;
        this.defaultValue = defaultValue;
        if (name == null) {
            throw new IllegalArgumentException("Name cannot be null!");
        }
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public double getDefault() {
        return this.defaultValue;
    }

    @Override
    public boolean isTrackable() {
        return this.trackable;
    }

    public BaseEntityAttribute setTrackable(boolean trackable) {
        this.trackable = trackable;
        return this;
    }

    @Override
    public EntityAttribute getParent() {
        return this.parent;
    }

    @Override
    public int hashCode() {
        return this.name.hashCode();
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof EntityAttribute && this.name.equals(((EntityAttribute)object).getName());
    }
}
