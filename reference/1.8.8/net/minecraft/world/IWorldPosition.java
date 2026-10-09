package net.minecraft.world;

import net.minecraft.util.math.IPosition;

public interface IWorldPosition extends IPosition {
    World getWorld();
}
