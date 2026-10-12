package net.minecraft.item;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.DirtBlock;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class HoeItem extends Item {
    protected Item.Tier tier;

    public HoeItem(Item.Tier tier) {
        this.tier = tier;
        this.maxStackSize = 1;
        this.setMaxDamage(tier.getDurability());
        this.setCreativeModeTab(CreativeModeTab.TOOLS);
    }

    @Override
    public boolean useOn(ItemStack stack, PlayerEntity player, World world, BlockPos pos, Direction face, float faceX, float faceY, float faceZ) {
        if (!player.canUseItemOn(pos.offset(face), face, stack)) {
            return false;
        }

        BlockState blockstate = world.getBlockState(pos);
        Block block = blockstate.getBlock();
        if (face != Direction.DOWN && world.getBlockState(pos.up()).getBlock().getMaterial() == Material.AIR) {
            if (block == Blocks.GRASS) {
                return this.tillBlock(stack, player, world, pos, Blocks.FARMLAND.defaultState());
            }

            if (block == Blocks.DIRT) {
                switch ((DirtBlock.Variant)blockstate.get(DirtBlock.VARIANT)) {
                    case DIRT:
                        return this.tillBlock(stack, player, world, pos, Blocks.FARMLAND.defaultState());
                    case COARSE_DIRT:
                        return this.tillBlock(stack, player, world, pos, Blocks.DIRT.defaultState().set(DirtBlock.VARIANT, DirtBlock.Variant.DIRT));
                }
            }
        }

        return false;
    }

    protected boolean tillBlock(ItemStack stack, PlayerEntity player, World world, BlockPos pos, BlockState tilledState) {
        world.playSound(
            pos.getX() + 0.5F,
            pos.getY() + 0.5F,
            pos.getZ() + 0.5F,
            tilledState.getBlock().sounds.getStepping(),
            (tilledState.getBlock().sounds.getVolume() + 1.0F) / 2.0F,
            tilledState.getBlock().sounds.getPitch() * 0.8F
        );
        if (world.isClient) {
            return true;
        }

        world.setBlockState(pos, tilledState);
        stack.takeDamageAndBreak(1, player);
        return true;
    }

    @Override
    public boolean isHandheld() {
        return true;
    }

    public String getTierName() {
        return this.tier.toString();
    }
}
