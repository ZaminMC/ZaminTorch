package net.minecraft.item;

import net.minecraft.entity.living.mob.passive.animal.PigEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.stat.Stats;
import net.minecraft.world.World;

public class CarrotOnAStickItem extends Item {
    public CarrotOnAStickItem() {
        this.setCreativeModeTab(CreativeModeTab.TRANSPORTATION);
        this.setMaxStackSize(1);
        this.setMaxDamage(25);
    }

    @Override
    public boolean isHandheld() {
        return true;
    }

    @Override
    public boolean shouldRotate() {
        return true;
    }

    @Override
    public ItemStack startUsing(ItemStack stack, World world, PlayerEntity player) {
        if (player.isRiding() && player.vehicle instanceof PigEntity) {
            PigEntity pigentity = (PigEntity)player.vehicle;
            if (pigentity.getPlayerControlGoal().canStartMoving() && stack.getMaxDamage() - stack.getMetadata() >= 7) {
                pigentity.getPlayerControlGoal().startMoving();
                stack.takeDamageAndBreak(7, player);
                if (stack.size == 0) {
                    ItemStack itemstack = new ItemStack(Items.FISHING_ROD);
                    itemstack.setNbt(stack.getNbt());
                    return itemstack;
                }
            }
        }

        player.incrementStat(Stats.ITEMS_USED[Item.getId(this)]);
        return stack;
    }
}
