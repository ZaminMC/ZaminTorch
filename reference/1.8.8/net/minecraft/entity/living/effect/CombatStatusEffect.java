package net.minecraft.entity.living.effect;

import net.minecraft.entity.living.attribute.AttributeModifier;
import net.minecraft.resource.Identifier;

public class CombatStatusEffect extends StatusEffect {
    protected CombatStatusEffect(int i, Identifier identifier, boolean bl, int j) {
        super(i, identifier, bl, j);
    }

    @Override
    public double getModifier(int amplifier, AttributeModifier modifier) {
        return this.id == StatusEffect.WEAKNESS.id ? -0.5F * (amplifier + 1) : 1.3 * (amplifier + 1);
    }
}
