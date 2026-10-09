package net.minecraft.block;

import java.util.Random;
import net.minecraft.block.material.Material;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.item.CreativeModeTab;

public class GlassBlock extends TransparentBlock {
    public GlassBlock(Material material, boolean sideVisible) {
        super(material, sideVisible);
        this.setCreativeModeTab(CreativeModeTab.BUILDING_BLOCKS);
    }

    @Override
    public int getBaseDropCount(Random random) {
        return 0;
    }

    @Override
    public BlockLayer getRenderLayer() {
        return BlockLayer.CUTOUT;
    }

    @Override
    public boolean isCube() {
        return false;
    }

    @Override
    protected boolean hasSilkTouchDrops() {
        return true;
    }
}
