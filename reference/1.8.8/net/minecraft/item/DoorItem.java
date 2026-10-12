package net.minecraft.item;

import net.minecraft.block.Block;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class DoorItem extends Item {
    private Block block;

    public DoorItem(Block block) {
        this.block = block;
        this.setCreativeModeTab(CreativeModeTab.REDSTONE);
    }

    @Override
    public boolean useOn(ItemStack stack, PlayerEntity player, World world, BlockPos pos, Direction face, float faceX, float faceY, float faceZ) {
        if (face != Direction.UP) {
            return false;
        }

        BlockState blockstate = world.getBlockState(pos);
        Block block = blockstate.getBlock();
        if (!block.canBeReplaced(world, pos)) {
            pos = pos.offset(face);
        }

        if (!player.canUseItemOn(pos, face, stack)) {
            return false;
        }

        if (!this.block.canBePlaced(world, pos)) {
            return false;
        }

        place(world, pos, Direction.byRotation(player.yaw), this.block);
        stack.size--;
        return true;
    }

    public static void place(World world, BlockPos pos, Direction facing, Block block) {
        BlockPos blockpos = pos.offset(facing.clockwiseY());
        BlockPos blockpos1 = pos.offset(facing.counterClockwiseY());
        int i = (world.getBlockState(blockpos1).getBlock().isSolid() ? 1 : 0) + (world.getBlockState(blockpos1.up()).getBlock().isSolid() ? 1 : 0);
        int j = (world.getBlockState(blockpos).getBlock().isSolid() ? 1 : 0) + (world.getBlockState(blockpos.up()).getBlock().isSolid() ? 1 : 0);
        boolean flag = world.getBlockState(blockpos1).getBlock() == block || world.getBlockState(blockpos1.up()).getBlock() == block;
        boolean flag1 = world.getBlockState(blockpos).getBlock() == block || world.getBlockState(blockpos.up()).getBlock() == block;
        boolean flag2 = false;
        if (flag && !flag1 || j > i) {
            flag2 = true;
        }

        BlockPos blockpos2 = pos.up();
        BlockState blockstate = block.defaultState().set(DoorBlock.FACING, facing).set(DoorBlock.HINGE, flag2 ? DoorBlock.Hinge.RIGHT : DoorBlock.Hinge.LEFT);
        world.setBlockState(pos, blockstate.set(DoorBlock.HALF, DoorBlock.Half.LOWER), 2);
        world.setBlockState(blockpos2, blockstate.set(DoorBlock.HALF, DoorBlock.Half.UPPER), 2);
        world.updateNeighbors(pos, block);
        world.updateNeighbors(blockpos2, block);
    }
}
