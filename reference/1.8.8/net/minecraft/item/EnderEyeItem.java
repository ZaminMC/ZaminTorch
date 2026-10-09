package net.minecraft.item;

import net.minecraft.block.Blocks;
import net.minecraft.block.EndPortalFrameBlock;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.EnderEyeEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.stat.Stats;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.HitResult;
import net.minecraft.world.World;

public class EnderEyeItem extends Item {
    public EnderEyeItem() {
        this.setCreativeModeTab(CreativeModeTab.MISC);
    }

    @Override
    public boolean useOn(ItemStack stack, PlayerEntity player, World world, BlockPos pos, Direction face, float faceX, float faceY, float faceZ) {
        BlockState blockstate = world.getBlockState(pos);
        if (!player.canUseItemOn(pos.offset(face), face, stack) || blockstate.getBlock() != Blocks.END_PORTAL_FRAME || blockstate.get(EndPortalFrameBlock.EYE)) {
            return false;
        }

        if (world.isClient) {
            return true;
        }

        world.setBlockState(pos, blockstate.set(EndPortalFrameBlock.EYE, true), 2);
        world.updateNeighborComparators(pos, Blocks.END_PORTAL_FRAME);
        stack.size--;

        for (int i = 0; i < 16; i++) {
            double d0 = pos.getX() + (5.0F + random.nextFloat() * 6.0F) / 16.0F;
            double d1 = pos.getY() + 0.8125F;
            double d2 = pos.getZ() + (5.0F + random.nextFloat() * 6.0F) / 16.0F;
            double d3 = 0.0;
            double d4 = 0.0;
            double d5 = 0.0;
            world.addParticle(ParticleType.SMOKE_NORMAL, d0, d1, d2, d3, d4, d5);
        }

        Direction direction = blockstate.get(EndPortalFrameBlock.FACING);
        int l = 0;
        int j = 0;
        boolean flag1 = false;
        boolean flag = true;
        Direction direction1 = direction.clockwiseY();

        for (int k = -2; k <= 2; k++) {
            BlockPos blockpos1 = pos.offset(direction1, k);
            BlockState blockstate1 = world.getBlockState(blockpos1);
            if (blockstate1.getBlock() == Blocks.END_PORTAL_FRAME) {
                if (!blockstate1.get(EndPortalFrameBlock.EYE)) {
                    flag = false;
                    break;
                }

                j = k;
                if (!flag1) {
                    l = k;
                    flag1 = true;
                }
            }
        }

        if (flag && j == l + 2) {
            BlockPos blockpos = pos.offset(direction, 4);

            for (int i1 = l; i1 <= j; i1++) {
                BlockPos blockpos2 = blockpos.offset(direction1, i1);
                BlockState blockstate3 = world.getBlockState(blockpos2);
                if (blockstate3.getBlock() != Blocks.END_PORTAL_FRAME || !blockstate3.get(EndPortalFrameBlock.EYE)) {
                    flag = false;
                    break;
                }
            }

            for (int j1 = l - 1; j1 <= j + 1; j1 += 4) {
                blockpos = pos.offset(direction1, j1);

                for (int l1 = 1; l1 <= 3; l1++) {
                    BlockPos blockpos3 = blockpos.offset(direction, l1);
                    BlockState blockstate2 = world.getBlockState(blockpos3);
                    if (blockstate2.getBlock() != Blocks.END_PORTAL_FRAME || !blockstate2.get(EndPortalFrameBlock.EYE)) {
                        flag = false;
                        break;
                    }
                }
            }

            if (flag) {
                for (int k1 = l; k1 <= j; k1++) {
                    blockpos = pos.offset(direction1, k1);

                    for (int i2 = 1; i2 <= 3; i2++) {
                        BlockPos blockpos4 = blockpos.offset(direction, i2);
                        world.setBlockState(blockpos4, Blocks.END_PORTAL.defaultState(), 2);
                    }
                }
            }
        }

        return true;
    }

    @Override
    public ItemStack startUsing(ItemStack stack, World world, PlayerEntity player) {
        HitResult hitresult = this.getUseTarget(world, player, false);
        if (hitresult != null && hitresult.type == HitResult.Type.BLOCK && world.getBlockState(hitresult.getPos()).getBlock() == Blocks.END_PORTAL_FRAME) {
            return stack;
        }

        if (!world.isClient) {
            BlockPos blockpos = world.findNearestStructure("Stronghold", new BlockPos(player));
            if (blockpos != null) {
                EnderEyeEntity endereyeentity = new EnderEyeEntity(world, player.x, player.y, player.z);
                endereyeentity.setTarget(blockpos);
                world.addEntity(endereyeentity);
                world.playSound((Entity)player, "random.bow", 0.5F, 0.4F / (random.nextFloat() * 0.4F + 0.8F));
                world.doEvent(null, 1002, new BlockPos(player), 0);
                if (!player.abilities.creativeMode) {
                    stack.size--;
                }

                player.incrementStat(Stats.ITEMS_USED[Item.getId(this)]);
            }
        }

        return stack;
    }
}
