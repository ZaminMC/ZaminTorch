package net.minecraft.item;

import java.util.List;
import net.minecraft.entity.living.effect.StatusEffect;
import net.minecraft.entity.living.effect.StatusEffectInstance;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.world.World;

public class GoldenAppleItem extends FoodItem {
    public GoldenAppleItem(int i, float f, boolean bl) {
        super(i, f, bl);
        this.setHasCustomData(true);
    }

    @Override
    public boolean hasEnchantmentGlint(ItemStack stack) {
        return stack.getMetadata() > 0;
    }

    @Override
    public Rarity getRarity(ItemStack stack) {
        return stack.getMetadata() == 0 ? Rarity.RARE : Rarity.EPIC;
    }

    @Override
    protected void addEatEffects(ItemStack stack, World world, PlayerEntity player) {
        if (!world.isClient) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffect.ABSORPTION.id, 2400, 0));
        }

        if (stack.getMetadata() > 0) {
            if (!world.isClient) {
                player.addStatusEffect(new StatusEffectInstance(StatusEffect.REGENERATION.id, 600, 4));
                player.addStatusEffect(new StatusEffectInstance(StatusEffect.RESISTANCE.id, 6000, 0));
                player.addStatusEffect(new StatusEffectInstance(StatusEffect.FIRE_RESISTANCE.id, 6000, 0));
            }
        } else {
            super.addEatEffects(stack, world, player);
        }
    }

    @Override
    public void addToCreativeMenu(Item item, CreativeModeTab tab, List<ItemStack> inventory) {
        inventory.add(new ItemStack(item, 1, 0));
        inventory.add(new ItemStack(item, 1, 1));
    }
}
