package net.minecraft.entity.living.mob.monster.boss;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.world.World;

public interface EnderDragon {
    World getWorld();

    boolean takeDamage(EnderDragonPart part, DamageSource source, float amount);
}
