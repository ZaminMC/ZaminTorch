package net.minecraft.entity.global;

import net.minecraft.entity.Entity;
import net.minecraft.world.World;

public abstract class GlobalEntity extends Entity {
    public GlobalEntity(World world) {
        super(world);
    }
}
