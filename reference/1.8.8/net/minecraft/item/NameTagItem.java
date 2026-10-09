package net.minecraft.item;

import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.mob.MobEntity;
import net.minecraft.entity.living.player.PlayerEntity;

public class NameTagItem extends Item {
    public NameTagItem() {
        this.setCreativeModeTab(CreativeModeTab.TOOLS);
    }

    @Override
    public boolean interact(ItemStack stack, PlayerEntity player, LivingEntity entity) {
        if (!stack.hasCustomHoverName()) {
            return false;
        } else if (entity instanceof MobEntity) {
            MobEntity mobentity = (MobEntity)entity;
            mobentity.setCustomName(stack.getHoverName());
            mobentity.setPersistent();
            stack.size--;
            return true;
        } else {
            return super.interact(stack, player, entity);
        }
    }
}
