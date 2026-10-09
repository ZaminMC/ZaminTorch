package net.minecraft.entity.living.attribute;

public interface EntityAttribute {
    String getName();

    double clamp(double value);

    double getDefault();

    boolean isTrackable();

    EntityAttribute getParent();
}
