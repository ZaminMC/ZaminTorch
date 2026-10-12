package net.minecraft.block;

import java.util.Random;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;
import net.minecraft.world.World;

public class SnowBlock extends Block {
    protected SnowBlock() {
        super(Material.SNOW);
        this.setTicksRandomly(true);
        this.setCreativeModeTab(CreativeModeTab.BUILDING_BLOCKS);
    }

    @Override
    public Item getDropItem(BlockState state, Random random, int fortuneLevel) {
        return Items.SNOWBALL;
    }

    @Override
    public int getBaseDropCount(Random random) {
        return 4;
    }

    @Override
    public void tick(World world, BlockPos pos, BlockState state, Random random) {
        if (world.getLight(LightType.BLOCK, pos) > 11) {
            this.dropItems(world, pos, world.getBlockState(pos), 0);
            world.removeBlock(pos);
        }
    }
}
