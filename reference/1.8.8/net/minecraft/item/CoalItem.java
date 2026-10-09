package net.minecraft.item;

import java.util.List;

public class CoalItem extends Item {
    public CoalItem() {
        this.setHasCustomData(true);
        this.setMaxDamage(0);
        this.setCreativeModeTab(CreativeModeTab.MATERIALS);
    }

    @Override
    public String getTranslationKey(ItemStack stack) {
        return stack.getMetadata() == 1 ? "item.charcoal" : "item.coal";
    }

    @Override
    public void addToCreativeMenu(Item item, CreativeModeTab tab, List<ItemStack> inventory) {
        inventory.add(new ItemStack(item, 1, 0));
        inventory.add(new ItemStack(item, 1, 1));
    }
}
