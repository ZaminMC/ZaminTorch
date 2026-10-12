package net.minecraft.block;

import java.util.Random;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.item.ItemStack;
import net.minecraft.stat.Stats;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;
import net.minecraft.world.World;

public class IceBlock extends TransparentBlock {
    public IceBlock() {
        super(Material.ICE, false);
        this.slipperiness = 0.98F;
        this.setTicksRandomly(true);
        this.setCreativeModeTab(CreativeModeTab.BUILDING_BLOCKS);
    }

    @Override
    public BlockLayer getRenderLayer() {
        return BlockLayer.TRANSLUCENT;
    }

    @Override
    public void afterMinedByPlayer(World world, PlayerEntity player, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        player.incrementStat(Stats.BLOCKS_MINED[Block.getId(this)]);
        player.addFatigue(0.025F);
        if (this.hasSilkTouchDrops() && EnchantmentHelper.hasSilkTouch(player)) {
            ItemStack itemstack = this.getSilkTouchDrop(state);
            if (itemstack != null) {
                dropItem(world, pos, itemstack);
            }
        } else {
            if (world.dimension.yeetsWater()) {
                world.removeBlock(pos);
                return;
            }

            int i = EnchantmentHelper.getFortuneLevel(player);
            this.dropItems(world, pos, state, i);
            Material material = world.getBlockState(pos.down()).getBlock().getMaterial();
            if (material.blocksMovement() || material.isLiquid()) {
                world.setBlockState(pos, Blocks.FLOWING_WATER.defaultState());
            }
        }
    }

    @Override
    public int getBaseDropCount(Random random) {
        return 0;
    }

    @Override
    public void tick(World world, BlockPos pos, BlockState state, Random random) {
        if (world.getLight(LightType.BLOCK, pos) > 11 - this.getOpacity()) {
            if (world.dimension.yeetsWater()) {
                world.removeBlock(pos);
            } else {
                this.dropItems(world, pos, world.getBlockState(pos), 0);
                world.setBlockState(pos, Blocks.WATER.defaultState());
            }
        }
    }

    @Override
    public int getPistonMoveBehavior() {
        return 0;
    }
}
