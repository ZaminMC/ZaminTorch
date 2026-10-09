package net.minecraft.block;

import java.util.Random;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class LiquidSourceBlock extends LiquidBlock {
    protected LiquidSourceBlock(Material material) {
        super(material);
        this.setTicksRandomly(false);
        if (material == Material.LAVA) {
            this.setTicksRandomly(true);
        }
    }

    @Override
    public void neighborChanged(World world, BlockPos pos, BlockState state, Block neighborBlock) {
        if (!this.checkSpreadCollisions(world, pos, state)) {
            this.convertToFlowing(world, pos, state);
        }
    }

    private void convertToFlowing(World world, BlockPos pos, BlockState state) {
        FlowingLiquidBlock flowingliquidblock = getFlowing(this.material);
        world.setBlockState(pos, flowingliquidblock.defaultState().set(LEVEL, state.get(LEVEL)), 2);
        world.scheduleTick(pos, flowingliquidblock, this.getTickRate(world));
    }

    @Override
    public void tick(World world, BlockPos pos, BlockState state, Random random) {
        if (this.material == Material.LAVA) {
            if (world.getGameRules().getBoolean("doFireTick")) {
                int i = random.nextInt(3);
                if (i > 0) {
                    BlockPos blockpos = pos;

                    for (int j = 0; j < i; j++) {
                        blockpos = blockpos.add(random.nextInt(3) - 1, 1, random.nextInt(3) - 1);
                        Block block = world.getBlockState(blockpos).getBlock();
                        if (block.material == Material.AIR) {
                            if (this.hasFlammableNeighbor(world, blockpos)) {
                                world.setBlockState(blockpos, Blocks.FIRE.defaultState());
                                return;
                            }
                        } else if (block.material.blocksMovement()) {
                            return;
                        }
                    }
                } else {
                    for (int k = 0; k < 3; k++) {
                        BlockPos blockpos1 = pos.add(random.nextInt(3) - 1, 0, random.nextInt(3) - 1);
                        if (world.isAir(blockpos1.up()) && this.isFlammable(world, blockpos1)) {
                            world.setBlockState(blockpos1.up(), Blocks.FIRE.defaultState());
                        }
                    }
                }
            }
        }
    }

    protected boolean hasFlammableNeighbor(World world, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (this.isFlammable(world, pos.offset(direction))) {
                return true;
            }
        }

        return false;
    }

    private boolean isFlammable(World world, BlockPos pos) {
        return world.getBlockState(pos).getBlock().getMaterial().isFlammable();
    }
}
