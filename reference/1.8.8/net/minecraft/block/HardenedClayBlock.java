package net.minecraft.block;

import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.item.CreativeModeTab;

public class HardenedClayBlock extends Block {
    public HardenedClayBlock() {
        super(Material.STONE);
        this.setCreativeModeTab(CreativeModeTab.BUILDING_BLOCKS);
    }

    @Override
    public MapColor getMapColor(BlockState state) {
        return MapColor.ORANGE;
    }
}
