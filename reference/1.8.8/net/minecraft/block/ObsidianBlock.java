package net.minecraft.block;

import java.util.Random;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.item.Item;

public class ObsidianBlock extends Block {
    public ObsidianBlock() {
        super(Material.STONE);
        this.setCreativeModeTab(CreativeModeTab.BUILDING_BLOCKS);
    }

    @Override
    public Item getDropItem(BlockState state, Random random, int fortuneLevel) {
        return Item.byBlock(Blocks.OBSIDIAN);
    }

    @Override
    public MapColor getMapColor(BlockState state) {
        return MapColor.BLACK;
    }
}
