package net.minecraft.item;

public class BookItem extends Item {
    @Override
    public boolean isEnchantable(ItemStack stack) {
        return stack.size == 1;
    }

    @Override
    public int getEnchantability() {
        return 1;
    }
}
