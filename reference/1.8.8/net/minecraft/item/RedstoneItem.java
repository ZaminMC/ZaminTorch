package net.minecraft.item;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class RedstoneItem extends Item {
    public RedstoneItem() {
        this.setCreativeModeTab(CreativeModeTab.REDSTONE);
    }

    @Override
    public boolean useOn(ItemStack stack, PlayerEntity player, World world, BlockPos pos, Direction face, float faceX, float faceY, float faceZ) {
        boolean flag = world.getBlockState(pos).getBlock().canBeReplaced(world, pos);
        BlockPos blockpos = flag ? pos : pos.offset(face);
        if (!player.canUseItemOn(blockpos, face, stack)) {
            return false;
        } else {
            Block block = world.getBlockState(blockpos).getBlock();
            if (!world.canPlace(block, blockpos, false, face, null, stack)) {
                return false;
            } else if (Blocks.REDSTONE_WIRE.canBePlaced(world, blockpos)) {
                stack.size--;
                world.setBlockState(blockpos, Blocks.REDSTONE_WIRE.defaultState());
                return true;
            } else {
                return false;
            }
        }
    }
}
