package net.minecraft.inventory;

import net.minecraft.text.Text;

public class AnimalInventory extends SimpleInventory {
    public AnimalInventory(String name, int size) {
        super(name, false, size);
    }

    public AnimalInventory(Text text, int i) {
        super(text, i);
    }
}
