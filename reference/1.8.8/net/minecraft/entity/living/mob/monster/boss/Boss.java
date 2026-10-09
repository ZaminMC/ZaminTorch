package net.minecraft.entity.living.mob.monster.boss;

import net.minecraft.text.Text;

public interface Boss {
    float getMaxHealth();

    float getHealth();

    Text getDisplayName();
}
