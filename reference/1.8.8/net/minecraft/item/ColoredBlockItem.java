package net.minecraft.item;

import net.minecraft.block.Block;

public class ColoredBlockItem extends BlockItem {
    public ColoredBlockItem(Block block) {
        super(block);
        this.setMaxDamage(0);
        this.setHasCustomData(true);
    }

    @Override
    public int getBlockMetadata(int metadata) {
        return metadata;
    }

    @Override
    public String getTranslationKey(ItemStack stack) {
        return super.getTranslationKey() + "." + DyeColor.byId(stack.getMetadata()).getName();
    }
}
