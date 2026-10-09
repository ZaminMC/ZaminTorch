package net.minecraft.item;

import com.google.common.base.Function;
import net.minecraft.block.Block;
import net.minecraft.block.DoublePlantBlock;
import net.minecraft.client.world.color.GrassColors;

public class BushItem extends VariantBlockItem {
    public BushItem(Block block, Block block2, Function<ItemStack, String> function) {
        super(block, block2, function);
    }

    @Override
    public int getDisplayColor(ItemStack stack, int stage) {
        DoublePlantBlock.Variant doubleplantblock$variant = DoublePlantBlock.Variant.byId(stack.getMetadata());
        return doubleplantblock$variant != DoublePlantBlock.Variant.GRASS && doubleplantblock$variant != DoublePlantBlock.Variant.FERN
            ? super.getDisplayColor(stack, stage)
            : GrassColors.getColor(0.5, 1.0);
    }
}
