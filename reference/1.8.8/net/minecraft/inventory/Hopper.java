package net.minecraft.inventory;

import net.minecraft.world.World;

public interface Hopper extends Inventory {
    World getWorld();

    double getX();

    double getY();

    double getZ();
}
