package net.minecraft.item;

import com.google.common.collect.Sets;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;

public class ShovelItem extends ToolItem {
    private static final Set<Block> EFFECTIVE_BLOCKS = Sets.newHashSet(
        Blocks.CLAY, Blocks.DIRT, Blocks.FARMLAND, Blocks.GRASS, Blocks.GRAVEL, Blocks.MYCELIUM, Blocks.SAND, Blocks.SNOW, Blocks.SNOW_LAYER, Blocks.SOUL_SAND
    );

    public ShovelItem(Item.Tier tier) {
        super(1.0F, tier, EFFECTIVE_BLOCKS);
    }

    @Override
    public boolean canMineBlock(Block block) {
        return block == Blocks.SNOW_LAYER || block == Blocks.SNOW;
    }
}
