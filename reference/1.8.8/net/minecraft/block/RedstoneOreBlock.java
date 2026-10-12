package net.minecraft.block;

import java.util.Random;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class RedstoneOreBlock extends Block {
    private final boolean lit;

    public RedstoneOreBlock(boolean lit) {
        super(Material.STONE);
        if (lit) {
            this.setTicksRandomly(true);
        }

        this.lit = lit;
    }

    @Override
    public int getTickRate(World world) {
        return 30;
    }

    @Override
    public void startMining(World world, BlockPos pos, PlayerEntity player) {
        this.interact(world, pos);
        super.startMining(world, pos, player);
    }

    @Override
    public void onSteppedOn(World world, BlockPos pos, Entity entity) {
        this.interact(world, pos);
        super.onSteppedOn(world, pos, entity);
    }

    @Override
    public boolean use(World world, BlockPos pos, BlockState state, PlayerEntity player, Direction face, float faceX, float faceY, float faceZ) {
        this.interact(world, pos);
        return super.use(world, pos, state, player, face, faceX, faceY, faceZ);
    }

    private void interact(World world, BlockPos pos) {
        this.addParticles(world, pos);
        if (this == Blocks.REDSTONE_ORE) {
            world.setBlockState(pos, Blocks.LIT_REDSTONE_ORE.defaultState());
        }
    }

    @Override
    public void tick(World world, BlockPos pos, BlockState state, Random random) {
        if (this == Blocks.LIT_REDSTONE_ORE) {
            world.setBlockState(pos, Blocks.REDSTONE_ORE.defaultState());
        }
    }

    @Override
    public Item getDropItem(BlockState state, Random random, int fortuneLevel) {
        return Items.REDSTONE;
    }

    @Override
    public int getDropCount(int fortuneLevel, Random random) {
        return this.getBaseDropCount(random) + random.nextInt(fortuneLevel + 1);
    }

    @Override
    public int getBaseDropCount(Random random) {
        return 4 + random.nextInt(2);
    }

    @Override
    public void dropItems(World world, BlockPos pos, BlockState state, float luck, int fortuneLevel) {
        super.dropItems(world, pos, state, luck, fortuneLevel);
        if (this.getDropItem(state, world.random, fortuneLevel) != Item.byBlock(this)) {
            int i = 1 + world.random.nextInt(5);
            this.dropXp(world, pos, i);
        }
    }

    @Override
    public void randomDisplayTick(World world, BlockPos pos, BlockState state, Random random) {
        if (this.lit) {
            this.addParticles(world, pos);
        }
    }

    private void addParticles(World world, BlockPos pos) {
        Random random = world.random;
        double d0 = 0.0625;

        for (int i = 0; i < 6; i++) {
            double d1 = pos.getX() + random.nextFloat();
            double d2 = pos.getY() + random.nextFloat();
            double d3 = pos.getZ() + random.nextFloat();
            if (i == 0 && !world.getBlockState(pos.up()).getBlock().isSolidRender()) {
                d2 = pos.getY() + d0 + 1.0;
            }

            if (i == 1 && !world.getBlockState(pos.down()).getBlock().isSolidRender()) {
                d2 = pos.getY() - d0;
            }

            if (i == 2 && !world.getBlockState(pos.south()).getBlock().isSolidRender()) {
                d3 = pos.getZ() + d0 + 1.0;
            }

            if (i == 3 && !world.getBlockState(pos.north()).getBlock().isSolidRender()) {
                d3 = pos.getZ() - d0;
            }

            if (i == 4 && !world.getBlockState(pos.east()).getBlock().isSolidRender()) {
                d1 = pos.getX() + d0 + 1.0;
            }

            if (i == 5 && !world.getBlockState(pos.west()).getBlock().isSolidRender()) {
                d1 = pos.getX() - d0;
            }

            if (d1 < pos.getX() || d1 > pos.getX() + 1 || d2 < 0.0 || d2 > pos.getY() + 1 || d3 < pos.getZ() || d3 > pos.getZ() + 1) {
                world.addParticle(ParticleType.REDSTONE, d1, d2, d3, 0.0, 0.0, 0.0);
            }
        }
    }

    @Override
    protected ItemStack getSilkTouchDrop(BlockState state) {
        return new ItemStack(Blocks.REDSTONE_ORE);
    }
}
