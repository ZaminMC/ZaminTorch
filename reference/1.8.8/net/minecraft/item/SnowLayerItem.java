package net.minecraft.item;

import net.minecraft.block.Block;
import net.minecraft.block.SnowLayerBlock;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class SnowLayerItem extends BlockItem {
    public SnowLayerItem(Block block) {
        super(block);
        this.setMaxDamage(0);
        this.setHasCustomData(true);
    }

    @Override
    public boolean useOn(ItemStack stack, PlayerEntity player, World world, BlockPos pos, Direction face, float faceX, float faceY, float faceZ) {
        if (stack.size == 0) {
            return false;
        }

        if (!player.canUseItemOn(pos, face, stack)) {
            return false;
        }

        BlockState blockstate = world.getBlockState(pos);
        Block block = blockstate.getBlock();
        BlockPos blockpos = pos;
        if ((face != Direction.UP || block != this.block) && !block.canBeReplaced(world, pos)) {
            blockpos = pos.offset(face);
            blockstate = world.getBlockState(blockpos);
            block = blockstate.getBlock();
        }

        if (block == this.block) {
            int i = blockstate.get(SnowLayerBlock.LAYERS);
            if (i <= 7) {
                BlockState blockstate1 = blockstate.set(SnowLayerBlock.LAYERS, i + 1);
                Box box = this.block.getCollisionShape(world, blockpos, blockstate1);
                if (box != null && world.isUnobstructed(box) && world.setBlockState(blockpos, blockstate1, 2)) {
                    world.playSound(
                        blockpos.getX() + 0.5F,
                        blockpos.getY() + 0.5F,
                        blockpos.getZ() + 0.5F,
                        this.block.sounds.getPlacing(),
                        (this.block.sounds.getVolume() + 1.0F) / 2.0F,
                        this.block.sounds.getPitch() * 0.8F
                    );
                    stack.size--;
                    return true;
                }
            }
        }

        return super.useOn(stack, player, world, blockpos, face, faceX, faceY, faceZ);
    }

    @Override
    public int getBlockMetadata(int metadata) {
        return metadata;
    }
}
