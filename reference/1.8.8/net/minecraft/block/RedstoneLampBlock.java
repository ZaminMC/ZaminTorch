package net.minecraft.block;

import java.util.Random;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class RedstoneLampBlock extends Block {
    private final boolean lit;

    public RedstoneLampBlock(boolean lit) {
        super(Material.REDSTONE_LAMP);
        this.lit = lit;
        if (lit) {
            this.setLight(1.0F);
        }
    }

    @Override
    public void onAdded(World world, BlockPos pos, BlockState state) {
        if (!world.isClient) {
            if (this.lit && !world.hasNeighborSignal(pos)) {
                world.setBlockState(pos, Blocks.REDSTONE_LAMP.defaultState(), 2);
            } else if (!this.lit && world.hasNeighborSignal(pos)) {
                world.setBlockState(pos, Blocks.LIT_REDSTONE_LAMP.defaultState(), 2);
            }
        }
    }

    @Override
    public void neighborChanged(World world, BlockPos pos, BlockState state, Block neighborBlock) {
        if (!world.isClient) {
            if (this.lit && !world.hasNeighborSignal(pos)) {
                world.scheduleTick(pos, this, 4);
            } else if (!this.lit && world.hasNeighborSignal(pos)) {
                world.setBlockState(pos, Blocks.LIT_REDSTONE_LAMP.defaultState(), 2);
            }
        }
    }

    @Override
    public void tick(World world, BlockPos pos, BlockState state, Random random) {
        if (!world.isClient) {
            if (this.lit && !world.hasNeighborSignal(pos)) {
                world.setBlockState(pos, Blocks.REDSTONE_LAMP.defaultState(), 2);
            }
        }
    }

    @Override
    public Item getDropItem(BlockState state, Random random, int fortuneLevel) {
        return Item.byBlock(Blocks.REDSTONE_LAMP);
    }

    @Override
    public Item getPickItem(World world, BlockPos pos) {
        return Item.byBlock(Blocks.REDSTONE_LAMP);
    }

    @Override
    protected ItemStack getSilkTouchDrop(BlockState state) {
        return new ItemStack(Blocks.REDSTONE_LAMP);
    }
}
