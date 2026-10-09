package net.minecraft.block;

import java.util.Random;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

public class DragonEggBlock extends Block {
    public DragonEggBlock() {
        super(Material.EGG, MapColor.BLACK);
        this.setShape(0.0625F, 0.0F, 0.0625F, 0.9375F, 1.0F, 0.9375F);
    }

    @Override
    public void onAdded(World world, BlockPos pos, BlockState state) {
        world.scheduleTick(pos, this, this.getTickRate(world));
    }

    @Override
    public void neighborChanged(World world, BlockPos pos, BlockState state, Block neighborBlock) {
        world.scheduleTick(pos, this, this.getTickRate(world));
    }

    @Override
    public void tick(World world, BlockPos pos, BlockState state, Random random) {
        this.tryFall(world, pos);
    }

    private void tryFall(World world, BlockPos pos) {
        if (FallingBlock.canFallThrough(world, pos.down()) && pos.getY() >= 0) {
            int i = 32;
            if (!FallingBlock.fallImmediately && world.isAreaLoaded(pos.add(-i, -i, -i), pos.add(i, i, i))) {
                world.addEntity(new FallingBlockEntity(world, pos.getX() + 0.5F, pos.getY(), pos.getZ() + 0.5F, this.defaultState()));
            } else {
                world.removeBlock(pos);
                BlockPos blockpos = pos;

                while (FallingBlock.canFallThrough(world, blockpos) && blockpos.getY() > 0) {
                    blockpos = blockpos.down();
                }

                if (blockpos.getY() > 0) {
                    world.setBlockState(blockpos, this.defaultState(), 2);
                }
            }
        }
    }

    @Override
    public boolean use(World world, BlockPos pos, BlockState state, PlayerEntity player, Direction face, float faceX, float faceY, float faceZ) {
        this.tryTeleport(world, pos);
        return true;
    }

    @Override
    public void startMining(World world, BlockPos pos, PlayerEntity player) {
        this.tryTeleport(world, pos);
    }

    private void tryTeleport(World world, BlockPos pos) {
        BlockState blockstate = world.getBlockState(pos);
        if (blockstate.getBlock() == this) {
            for (int i = 0; i < 1000; i++) {
                BlockPos blockpos = pos.add(
                    world.random.nextInt(16) - world.random.nextInt(16),
                    world.random.nextInt(8) - world.random.nextInt(8),
                    world.random.nextInt(16) - world.random.nextInt(16)
                );
                if (world.getBlockState(blockpos).getBlock().material == Material.AIR) {
                    if (world.isClient) {
                        for (int j = 0; j < 128; j++) {
                            double d0 = world.random.nextDouble();
                            float f = (world.random.nextFloat() - 0.5F) * 0.2F;
                            float f1 = (world.random.nextFloat() - 0.5F) * 0.2F;
                            float f2 = (world.random.nextFloat() - 0.5F) * 0.2F;
                            double d1 = blockpos.getX() + (pos.getX() - blockpos.getX()) * d0 + (world.random.nextDouble() - 0.5) * 1.0 + 0.5;
                            double d2 = blockpos.getY() + (pos.getY() - blockpos.getY()) * d0 + world.random.nextDouble() * 1.0 - 0.5;
                            double d3 = blockpos.getZ() + (pos.getZ() - blockpos.getZ()) * d0 + (world.random.nextDouble() - 0.5) * 1.0 + 0.5;
                            world.addParticle(ParticleType.PORTAL, d1, d2, d3, f, f1, f2);
                        }
                    } else {
                        world.setBlockState(blockpos, blockstate, 2);
                        world.removeBlock(pos);
                    }

                    return;
                }
            }
        }
    }

    @Override
    public int getTickRate(World world) {
        return 5;
    }

    @Override
    public boolean isSolidRender() {
        return false;
    }

    @Override
    public boolean isCube() {
        return false;
    }

    @Override
    public boolean shouldRenderFace(WorldView world, BlockPos pos, Direction face) {
        return true;
    }

    @Override
    public Item getPickItem(World world, BlockPos pos) {
        return null;
    }
}
