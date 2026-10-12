package net.minecraft.item;

import net.minecraft.block.Block;

public class AnvilItem extends VariantBlockItem {
    public AnvilItem(Block spriteProvider) {
        super(spriteProvider, spriteProvider, new String[]{"intact", "slightlyDamaged", "veryDamaged"});
    }

    @Override
    public int getBlockMetadata(int metadata) {
        return metadata << 2;
    }
}
