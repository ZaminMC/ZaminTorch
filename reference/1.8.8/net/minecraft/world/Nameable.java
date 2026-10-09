package net.minecraft.world;

import net.minecraft.text.Text;

public interface Nameable {
    String getName();

    boolean hasCustomName();

    Text getDisplayName();
}
