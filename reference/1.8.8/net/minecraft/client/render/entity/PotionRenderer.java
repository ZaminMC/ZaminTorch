package net.minecraft.client.render.entity;

import net.minecraft.entity.projectile.PotionEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class PotionRenderer extends ItemSpriteRenderer<PotionEntity> {
    public PotionRenderer(EntityRenderDispatcher dispatcher, ItemRenderer itemRenderer) {
        super(dispatcher, Items.POTION, itemRenderer);
    }

    public ItemStack asItem(PotionEntity potionEntity) {
        return new ItemStack(this.item, 1, potionEntity.getStatusEffect());
    }
}
