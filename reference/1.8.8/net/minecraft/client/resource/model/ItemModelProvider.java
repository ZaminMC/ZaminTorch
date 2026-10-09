package net.minecraft.client.resource.model;

import net.minecraft.client.resource.ModelIdentifier;
import net.minecraft.item.ItemStack;

public interface ItemModelProvider {
    ModelIdentifier provide(ItemStack item);
}
