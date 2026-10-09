package net.minecraft.item;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.LiquidBlock;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.stat.Stats;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.HitResult;
import net.minecraft.world.World;

public class LilyPadItem extends PlantBlockItem {
    public LilyPadItem(Block block) {
        super(block, false);
    }

    @Override
    public ItemStack startUsing(ItemStack stack, World world, PlayerEntity player) {
        HitResult hitresult = this.getUseTarget(world, player, true);
        if (hitresult == null) {
            return stack;
        }

        if (hitresult.type == HitResult.Type.BLOCK) {
            BlockPos blockpos = hitresult.getPos();
            if (!world.canModify(player, blockpos)) {
                return stack;
            }

            if (!player.canUseItemOn(blockpos.offset(hitresult.face), hitresult.face, stack)) {
                return stack;
            }

            BlockPos blockpos1 = blockpos.up();
            BlockState blockstate = world.getBlockState(blockpos);
            if (blockstate.getBlock().getMaterial() == Material.WATER && blockstate.get(LiquidBlock.LEVEL) == 0 && world.isAir(blockpos1)) {
                world.setBlockState(blockpos1, Blocks.LILY_PAD.defaultState());
                if (!player.abilities.creativeMode) {
                    stack.size--;
                }

                player.incrementStat(Stats.ITEMS_USED[Item.getId(this)]);
            }
        }

        return stack;
    }

    @Override
    public int getDisplayColor(ItemStack stack, int stage) {
        return Blocks.LILY_PAD.getColor(Blocks.LILY_PAD.getStateFromMetadata(stack.getMetadata()));
    }
}
