package net.minecraft.item;

import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.world.World;

public class StewItem extends FoodItem {
    public StewItem(int hungerPoints) {
        super(hungerPoints, false);
        this.setMaxStackSize(1);
    }

    @Override
    public ItemStack finishUsing(ItemStack stack, World world, PlayerEntity player) {
        super.finishUsing(stack, world, player);
        return new ItemStack(Items.BOWL);
    }
}
