package net.minecraft.block;

import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.item.CreativeModeTab;

public class NetherBrickBlock extends Block {
    public NetherBrickBlock() {
        super(Material.STONE);
        this.setCreativeModeTab(CreativeModeTab.BUILDING_BLOCKS);
    }

    @Override
    public MapColor getMapColor(BlockState state) {
        return MapColor.NETHER;
    }
}
