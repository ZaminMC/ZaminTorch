package net.minecraft.item;

import net.minecraft.block.Block;

public class PlantBlockItem extends BlockItem {
    private final Block block;
    private String[] names;

    public PlantBlockItem(Block block, boolean stackable) {
        super(block);
        this.block = block;
        if (stackable) {
            this.setMaxDamage(0);
            this.setHasCustomData(true);
        }
    }

    @Override
    public int getDisplayColor(ItemStack stack, int stage) {
        return this.block.getColor(this.block.getStateFromMetadata(stack.getMetadata()));
    }

    @Override
    public int getBlockMetadata(int metadata) {
        return metadata;
    }

    public PlantBlockItem setNames(String[] names) {
        this.names = names;
        return this;
    }

    @Override
    public String getTranslationKey(ItemStack stack) {
        if (this.names == null) {
            return super.getTranslationKey(stack);
        }

        int i = stack.getMetadata();
        return i >= 0 && i < this.names.length ? super.getTranslationKey(stack) + "." + this.names[i] : super.getTranslationKey(stack);
    }
}
