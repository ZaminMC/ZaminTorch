package net.minecraft.block;

import java.util.Random;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

public class SignBlock extends BlockWithBlockEntity {
    protected SignBlock() {
        super(Material.WOOD);
        float f = 0.25F;
        float f1 = 1.0F;
        this.setShape(0.5F - f, 0.0F, 0.5F - f, 0.5F + f, f1, 0.5F + f);
    }

    @Override
    public Box getCollisionShape(World world, BlockPos pos, BlockState state) {
        return null;
    }

    @Override
    public Box getOutlineShape(World world, BlockPos pos) {
        this.updateShape(world, pos);
        return super.getOutlineShape(world, pos);
    }

    @Override
    public boolean isCube() {
        return false;
    }

    @Override
    public boolean canWalkThrough(WorldView world, BlockPos pos) {
        return true;
    }

    @Override
    public boolean isSolidRender() {
        return false;
    }

    @Override
    public boolean canRespawnIn() {
        return true;
    }

    @Override
    public BlockEntity createBlockEntity(World world, int metadata) {
        return new SignBlockEntity();
    }

    @Override
    public Item getDropItem(BlockState state, Random random, int fortuneLevel) {
        return Items.SIGN;
    }

    @Override
    public Item getPickItem(World world, BlockPos pos) {
        return Items.SIGN;
    }

    @Override
    public boolean use(World world, BlockPos pos, BlockState state, PlayerEntity player, Direction face, float faceX, float faceY, float faceZ) {
        if (world.isClient) {
            return true;
        }

        BlockEntity blockentity = world.getBlockEntity(pos);
        return blockentity instanceof SignBlockEntity && ((SignBlockEntity)blockentity).onUse(player);
    }

    @Override
    public boolean canBePlaced(World world, BlockPos pos) {
        return !this.isNeighboringCactus(world, pos) && super.canBePlaced(world, pos);
    }
}
