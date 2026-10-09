package net.minecraft.item;

import com.google.common.collect.Sets;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.material.Material;

public class AxeItem extends ToolItem {
    /**
     * The items that are broken more efficiently with an axe
     */
    private static final Set<Block> EFFECTIVE_BLOCKS = Sets.newHashSet(
        Blocks.PLANKS, Blocks.BOOKSHELF, Blocks.LOG, Blocks.LOG2, Blocks.CHEST, Blocks.PUMPKIN, Blocks.LIT_PUMPKIN, Blocks.MELON_BLOCK, Blocks.LADDER
    );

    protected AxeItem(Item.Tier tier) {
        super(3.0F, tier, EFFECTIVE_BLOCKS);
    }

    @Override
    public float getMiningSpeed(ItemStack stack, Block block) {
        return block.getMaterial() != Material.WOOD && block.getMaterial() != Material.PLANT && block.getMaterial() != Material.REPLACEABLE_PLANT
            ? super.getMiningSpeed(stack, block)
            : this.miningSpeed;
    }
}
