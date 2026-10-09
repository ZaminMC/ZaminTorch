package net.minecraft.item;

import com.google.common.base.Function;
import net.minecraft.block.Block;

public class VariantBlockItem extends BlockItem {
    protected final Block spriteProvider;
    protected final Function<ItemStack, String> nameProvider;

    public VariantBlockItem(Block block, Block spriteProvider, Function<ItemStack, String> nameProvider) {
        super(block);
        this.spriteProvider = spriteProvider;
        this.nameProvider = nameProvider;
        this.setMaxDamage(0);
        this.setHasCustomData(true);
    }

    public VariantBlockItem(Block block, Block spriteProvider, String[] variants) {
        this(block, spriteProvider, new Function<ItemStack, String>() {
            public String apply(ItemStack itemStack) {
                int i = itemStack.getMetadata();
                if (i < 0 || i >= variants.length) {
                    i = 0;
                }

                return variants[i];
            }
        });
    }

    @Override
    public int getBlockMetadata(int metadata) {
        return metadata;
    }

    @Override
    public String getTranslationKey(ItemStack stack) {
        return super.getTranslationKey() + "." + this.nameProvider.apply(stack);
    }
}
