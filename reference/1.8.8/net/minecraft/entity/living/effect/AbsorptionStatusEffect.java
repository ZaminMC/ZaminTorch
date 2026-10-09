package net.minecraft.entity.living.effect;

import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.attribute.AbstractEntityAttributeContainer;
import net.minecraft.resource.Identifier;

public class AbsorptionStatusEffect extends StatusEffect {
    protected AbsorptionStatusEffect(int i, Identifier identifier, boolean bl, int j) {
        super(i, identifier, bl, j);
    }

    @Override
    public void removeModifiers(LivingEntity entity, AbstractEntityAttributeContainer container, int amplifier) {
        entity.setAbsorption(entity.getAbsorption() - 4 * (amplifier + 1));
        super.removeModifiers(entity, container, amplifier);
    }

    @Override
    public void addModifiers(LivingEntity entity, AbstractEntityAttributeContainer container, int amplifier) {
        entity.setAbsorption(entity.getAbsorption() + 4 * (amplifier + 1));
        super.addModifiers(entity, container, amplifier);
    }
}
