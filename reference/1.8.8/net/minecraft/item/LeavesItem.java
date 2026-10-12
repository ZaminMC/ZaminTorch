package net.minecraft.item;

import net.minecraft.block.AbstractLeavesBlock;

public class LeavesItem extends BlockItem {
    private final AbstractLeavesBlock block;

    public LeavesItem(AbstractLeavesBlock block) {
        super(block);
        this.block = block;
        this.setMaxDamage(0);
        this.setHasCustomData(true);
    }

    @Override
    public int getBlockMetadata(int metadata) {
        return metadata | 4;
    }

    @Override
    public int getDisplayColor(ItemStack stack, int stage) {
        return this.block.getColor(this.block.getStateFromMetadata(stack.getMetadata()));
    }

    @Override
    public String getTranslationKey(ItemStack stack) {
        return super.getTranslationKey() + "." + this.block.getVariant(stack.getMetadata()).getName();
    }
}
