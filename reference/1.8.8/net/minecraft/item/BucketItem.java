package net.minecraft.item;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.LiquidBlock;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.stat.Stats;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.HitResult;
import net.minecraft.world.World;

public class BucketItem extends Item {
    private Block liquid;

    public BucketItem(Block liquid) {
        this.maxStackSize = 1;
        this.liquid = liquid;
        this.setCreativeModeTab(CreativeModeTab.MISC);
    }

    @Override
    public ItemStack startUsing(ItemStack stack, World world, PlayerEntity player) {
        boolean flag = this.liquid == Blocks.AIR;
        HitResult hitresult = this.getUseTarget(world, player, flag);
        if (hitresult == null) {
            return stack;
        }

        if (hitresult.type == HitResult.Type.BLOCK) {
            BlockPos blockpos = hitresult.getPos();
            if (!world.canModify(player, blockpos)) {
                return stack;
            }

            if (flag) {
                if (!player.canUseItemOn(blockpos.offset(hitresult.face), hitresult.face, stack)) {
                    return stack;
                }

                BlockState blockstate = world.getBlockState(blockpos);
                Material material = blockstate.getBlock().getMaterial();
                if (material == Material.WATER && blockstate.get(LiquidBlock.LEVEL) == 0) {
                    world.removeBlock(blockpos);
                    player.incrementStat(Stats.ITEMS_USED[Item.getId(this)]);
                    return this.fill(stack, player, Items.WATER_BUCKET);
                }

                if (material == Material.LAVA && blockstate.get(LiquidBlock.LEVEL) == 0) {
                    world.removeBlock(blockpos);
                    player.incrementStat(Stats.ITEMS_USED[Item.getId(this)]);
                    return this.fill(stack, player, Items.LAVA_BUCKET);
                }
            } else {
                if (this.liquid == Blocks.AIR) {
                    return new ItemStack(Items.BUCKET);
                }

                BlockPos blockpos1 = blockpos.offset(hitresult.face);
                if (!player.canUseItemOn(blockpos1, hitresult.face, stack)) {
                    return stack;
                }

                if (this.place(world, blockpos1) && !player.abilities.creativeMode) {
                    player.incrementStat(Stats.ITEMS_USED[Item.getId(this)]);
                    return new ItemStack(Items.BUCKET);
                }
            }
        }

        return stack;
    }

    private ItemStack fill(ItemStack stack, PlayerEntity player, Item item) {
        if (player.abilities.creativeMode) {
            return stack;
        }

        if (--stack.size <= 0) {
            return new ItemStack(item);
        }

        if (!player.inventory.addItem(new ItemStack(item))) {
            player.dropItem(new ItemStack(item, 1, 0), false);
        }

        return stack;
    }

    public boolean place(World world, BlockPos pos) {
        if (this.liquid == Blocks.AIR) {
            return false;
        }

        Material material = world.getBlockState(pos).getBlock().getMaterial();
        boolean flag = !material.isSolid();
        if (!world.isAir(pos) && !flag) {
            return false;
        }

        if (world.dimension.yeetsWater() && this.liquid == Blocks.FLOWING_WATER) {
            int i = pos.getX();
            int j = pos.getY();
            int k = pos.getZ();
            world.playSound(i + 0.5F, j + 0.5F, k + 0.5F, "random.fizz", 0.5F, 2.6F + (world.random.nextFloat() - world.random.nextFloat()) * 0.8F);

            for (int l = 0; l < 8; l++) {
                world.addParticle(ParticleType.SMOKE_LARGE, i + Math.random(), j + Math.random(), k + Math.random(), 0.0, 0.0, 0.0);
            }
        } else {
            if (!world.isClient && flag && !material.isLiquid()) {
                world.breakBlock(pos, true);
            }

            world.setBlockState(pos, this.liquid.defaultState(), 3);
        }

        return true;
    }
}
