package net.minecraft.item;

import net.minecraft.block.Block;

public class PistonBlockItem extends BlockItem {
    public PistonBlockItem(Block block) {
        super(block);
    }

    @Override
    public int getBlockMetadata(int metadata) {
        return 7;
    }
}
