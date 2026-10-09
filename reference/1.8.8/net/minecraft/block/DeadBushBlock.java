package net.minecraft.block;

import java.util.Random;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.stat.Stats;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class DeadBushBlock extends PlantBlock {
    protected DeadBushBlock() {
        super(Material.REPLACEABLE_PLANT);
        float f = 0.4F;
        this.setShape(0.5F - f, 0.0F, 0.5F - f, 0.5F + f, 0.8F, 0.5F + f);
    }

    @Override
    public MapColor getMapColor(BlockState state) {
        return MapColor.WOOD;
    }

    @Override
    protected boolean canBePlacedOn(Block block) {
        return block == Blocks.SAND || block == Blocks.HARDENED_CLAY || block == Blocks.STAINED_HARDENED_CLAY || block == Blocks.DIRT;
    }

    @Override
    public boolean canBeReplaced(World world, BlockPos pos) {
        return true;
    }

    @Override
    public Item getDropItem(BlockState state, Random random, int fortuneLevel) {
        return null;
    }

    @Override
    public void afterMinedByPlayer(World world, PlayerEntity player, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        if (!world.isClient && player.getItemInHand() != null && player.getItemInHand().getItem() == Items.SHEARS) {
            player.incrementStat(Stats.BLOCKS_MINED[Block.getId(this)]);
            dropItem(world, pos, new ItemStack(Blocks.DEADBUSH, 1, 0));
        } else {
            super.afterMinedByPlayer(world, player, pos, state, blockEntity);
        }
    }
}
