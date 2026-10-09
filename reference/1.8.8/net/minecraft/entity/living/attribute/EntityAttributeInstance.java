package net.minecraft.entity.living.attribute;

import java.util.Collection;
import java.util.UUID;

public interface EntityAttributeInstance {
    EntityAttribute getAttribute();

    double getBase();

    void setBase(double base);

    Collection<AttributeModifier> getModifiers(int operation);

    Collection<AttributeModifier> getModifiers();

    boolean hasModifier(AttributeModifier modifier);

    AttributeModifier getModifier(UUID id);

    void addModifier(AttributeModifier modifier);

    void removeModifier(AttributeModifier modifier);

    void clearModifiers();

    double get();
}
