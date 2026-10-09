package net.minecraft.item;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.SnowLayerBlock;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class PlaceableItem extends Item {
    private Block block;

    public PlaceableItem(Block block) {
        this.block = block;
    }

    @Override
    public boolean useOn(ItemStack stack, PlayerEntity player, World world, BlockPos pos, Direction face, float faceX, float faceY, float faceZ) {
        BlockState blockstate = world.getBlockState(pos);
        Block block = blockstate.getBlock();
        if (block == Blocks.SNOW_LAYER && blockstate.get(SnowLayerBlock.LAYERS) < 1) {
            face = Direction.UP;
        } else if (!block.canBeReplaced(world, pos)) {
            pos = pos.offset(face);
        }

        if (!player.canUseItemOn(pos, face, stack)) {
            return false;
        }

        if (stack.size == 0) {
            return false;
        }

        if (world.canPlace(this.block, pos, false, face, null, stack)) {
            BlockState blockstate1 = this.block.getPlacementState(world, pos, face, faceX, faceY, faceZ, 0, player);
            if (world.setBlockState(pos, blockstate1, 3)) {
                blockstate1 = world.getBlockState(pos);
                if (blockstate1.getBlock() == this.block) {
                    BlockItem.setBlockNbt(world, player, pos, stack);
                    blockstate1.getBlock().onPlaced(world, pos, blockstate1, player, stack);
                }

                world.playSound(
                    pos.getX() + 0.5F,
                    pos.getY() + 0.5F,
                    pos.getZ() + 0.5F,
                    this.block.sounds.getPlacing(),
                    (this.block.sounds.getVolume() + 1.0F) / 2.0F,
                    this.block.sounds.getPitch() * 0.8F
                );
                stack.size--;
                return true;
            }
        }

        return false;
    }
}
