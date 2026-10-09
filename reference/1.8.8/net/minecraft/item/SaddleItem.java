package net.minecraft.item;

import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.mob.passive.animal.PigEntity;
import net.minecraft.entity.living.player.PlayerEntity;

public class SaddleItem extends Item {
    public SaddleItem() {
        this.maxStackSize = 1;
        this.setCreativeModeTab(CreativeModeTab.TRANSPORTATION);
    }

    @Override
    public boolean interact(ItemStack stack, PlayerEntity player, LivingEntity entity) {
        if (entity instanceof PigEntity) {
            PigEntity pigentity = (PigEntity)entity;
            if (!pigentity.isSaddled() && !pigentity.isBaby()) {
                pigentity.setSaddled(true);
                pigentity.world.playSound(pigentity, "mob.horse.leather", 0.5F, 1.0F);
                stack.size--;
            }

            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean attack(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        this.interact(stack, null, target);
        return true;
    }
}
