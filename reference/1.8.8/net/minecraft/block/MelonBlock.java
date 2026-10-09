package net.minecraft.block;

import java.util.Random;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

public class MelonBlock extends Block {
    protected MelonBlock() {
        super(Material.PUMPKIN, MapColor.LIME);
        this.setCreativeModeTab(CreativeModeTab.BUILDING_BLOCKS);
    }

    @Override
    public Item getDropItem(BlockState state, Random random, int fortuneLevel) {
        return Items.MELON;
    }

    @Override
    public int getBaseDropCount(Random random) {
        return 3 + random.nextInt(5);
    }

    @Override
    public int getDropCount(int fortuneLevel, Random random) {
        return Math.min(9, this.getBaseDropCount(random) + random.nextInt(1 + fortuneLevel));
    }
}
