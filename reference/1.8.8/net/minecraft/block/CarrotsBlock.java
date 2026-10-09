package net.minecraft.block;

import net.minecraft.item.Item;
import net.minecraft.item.Items;

public class CarrotsBlock extends WheatBlock {
    @Override
    protected Item getSeedItem() {
        return Items.CARROT;
    }

    @Override
    protected Item getPlantItem() {
        return Items.CARROT;
    }
}
