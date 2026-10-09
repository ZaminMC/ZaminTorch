package net.minecraft.item;

import net.minecraft.block.Blocks;
import net.minecraft.block.StandingSignBlock;
import net.minecraft.block.WallSignBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public class SignItem extends Item {
    public SignItem() {
        this.maxStackSize = 16;
        this.setCreativeModeTab(CreativeModeTab.DECORATIONS);
    }

    @Override
    public boolean useOn(ItemStack stack, PlayerEntity player, World world, BlockPos pos, Direction face, float faceX, float faceY, float faceZ) {
        if (face == Direction.DOWN) {
            return false;
        }

        if (!world.getBlockState(pos).getBlock().getMaterial().isSolid()) {
            return false;
        }

        pos = pos.offset(face);
        if (!player.canUseItemOn(pos, face, stack)) {
            return false;
        }

        if (!Blocks.STANDING_SIGN.canBePlaced(world, pos)) {
            return false;
        }

        if (world.isClient) {
            return true;
        }

        if (face == Direction.UP) {
            int i = MathHelper.floor((player.yaw + 180.0F) * 16.0F / 360.0F + 0.5) & 15;
            world.setBlockState(pos, Blocks.STANDING_SIGN.defaultState().set(StandingSignBlock.ROTATION, i), 3);
        } else {
            world.setBlockState(pos, Blocks.WALL_SIGN.defaultState().set(WallSignBlock.FACING, face), 3);
        }

        stack.size--;
        BlockEntity blockentity = world.getBlockEntity(pos);
        if (blockentity instanceof SignBlockEntity && !BlockItem.setBlockNbt(world, player, pos, stack)) {
            player.openSignEditor((SignBlockEntity)blockentity);
        }

        return true;
    }
}
