package net.minecraft.item;

import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public class BedItem extends Item {
    public BedItem() {
        this.setCreativeModeTab(CreativeModeTab.DECORATIONS);
    }

    @Override
    public boolean useOn(ItemStack stack, PlayerEntity player, World world, BlockPos pos, Direction face, float faceX, float faceY, float faceZ) {
        if (world.isClient) {
            return true;
        }

        if (face != Direction.UP) {
            return false;
        }

        BlockState blockstate = world.getBlockState(pos);
        Block block = blockstate.getBlock();
        boolean flag = block.canBeReplaced(world, pos);
        if (!flag) {
            pos = pos.up();
        }

        int i = MathHelper.floor(player.yaw * 4.0F / 360.0F + 0.5) & 3;
        Direction direction = Direction.byIdHorizontal(i);
        BlockPos blockpos = pos.offset(direction);
        if (player.canUseItemOn(pos, face, stack) && player.canUseItemOn(blockpos, face, stack)) {
            boolean flag1 = world.getBlockState(blockpos).getBlock().canBeReplaced(world, blockpos);
            boolean flag2 = flag || world.isAir(pos);
            boolean flag3 = flag1 || world.isAir(blockpos);
            if (flag2 && flag3 && World.hasSolidTop(world, pos.down()) && World.hasSolidTop(world, blockpos.down())) {
                BlockState blockstate1 = Blocks.BED
                    .defaultState()
                    .set(BedBlock.OCCUPIED, false)
                    .set(BedBlock.FACING, direction)
                    .set(BedBlock.PART, BedBlock.Part.FOOT);
                if (world.setBlockState(pos, blockstate1, 3)) {
                    BlockState blockstate2 = blockstate1.set(BedBlock.PART, BedBlock.Part.HEAD);
                    world.setBlockState(blockpos, blockstate2, 3);
                }

                stack.size--;
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }
}
