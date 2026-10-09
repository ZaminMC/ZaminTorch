package net.minecraft.block;

import java.util.Random;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class FallingBlock extends Block {
    public static boolean fallImmediately;

    public FallingBlock() {
        super(Material.SAND);
        this.setCreativeModeTab(CreativeModeTab.BUILDING_BLOCKS);
    }

    public FallingBlock(Material material) {
        super(material);
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
        if (!world.isClient) {
            this.tryFall(world, pos);
        }
    }

    private void tryFall(World world, BlockPos pos) {
        if (canFallThrough(world, pos.down()) && pos.getY() >= 0) {
            int i = 32;
            if (fallImmediately || !world.isAreaLoaded(pos.add(-i, -i, -i), pos.add(i, i, i))) {
                world.removeBlock(pos);
                BlockPos blockpos = pos.down();

                while (canFallThrough(world, blockpos) && blockpos.getY() > 0) {
                    blockpos = blockpos.down();
                }

                if (blockpos.getY() > 0) {
                    world.setBlockState(blockpos.up(), this.defaultState());
                }
            } else if (!world.isClient) {
                FallingBlockEntity fallingblockentity = new FallingBlockEntity(world, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, world.getBlockState(pos));
                this.beforeStartFalling(fallingblockentity);
                world.addEntity(fallingblockentity);
            }
        }
    }

    protected void beforeStartFalling(FallingBlockEntity fallingBlockEntity) {
    }

    @Override
    public int getTickRate(World world) {
        return 2;
    }

    public static boolean canFallThrough(World world, BlockPos pos) {
        Block block = world.getBlockState(pos).getBlock();
        Material material = block.material;
        return block == Blocks.FIRE || material == Material.AIR || material == Material.WATER || material == Material.LAVA;
    }

    public void onTickFallingBlockEntity(World world, BlockPos pos) {
    }
}
