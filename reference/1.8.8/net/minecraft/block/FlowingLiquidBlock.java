package net.minecraft.block;

import java.util.EnumSet;
import java.util.Random;
import java.util.Set;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class FlowingLiquidBlock extends LiquidBlock {
    /**
     * The number of adjacent source liquids.
     */
    int adjacentSources;

    protected FlowingLiquidBlock(Material material) {
        super(material);
    }

    private void convertToSource(World world, BlockPos pos, BlockState state) {
        world.setBlockState(pos, getSource(this.material).defaultState().set(LEVEL, state.get(LEVEL)), 2);
    }

    @Override
    public void tick(World world, BlockPos pos, BlockState state, Random random) {
        int i = state.get(LEVEL);
        int j = 1;
        if (this.material == Material.LAVA && !world.dimension.yeetsWater()) {
            j = 2;
        }

        int k = this.getTickRate(world);
        if (i > 0) {
            int l = -100;
            this.adjacentSources = 0;

            for (Direction direction : Direction.Plane.HORIZONTAL) {
                l = this.getLowestDepth(world, pos.offset(direction), l);
            }

            int i1 = l + j;
            if (i1 >= 8 || l < 0) {
                i1 = -1;
            }

            if (this.getLiquidState(world, pos.up()) >= 0) {
                int j1 = this.getLiquidState(world, pos.up());
                if (j1 >= 8) {
                    i1 = j1;
                } else {
                    i1 = j1 + 8;
                }
            }

            if (this.adjacentSources >= 2 && this.material == Material.WATER) {
                BlockState blockstate1 = world.getBlockState(pos.down());
                if (blockstate1.getBlock().getMaterial().isSolid()) {
                    i1 = 0;
                } else if (blockstate1.getBlock().getMaterial() == this.material && blockstate1.get(LEVEL) == 0) {
                    i1 = 0;
                }
            }

            if (this.material == Material.LAVA && i < 8 && i1 < 8 && i1 > i && random.nextInt(4) != 0) {
                k *= 4;
            }

            if (i1 == i) {
                this.convertToSource(world, pos, state);
            } else {
                i = i1;
                if (i < 0) {
                    world.removeBlock(pos);
                } else {
                    state = state.set(LEVEL, i);
                    world.setBlockState(pos, state, 2);
                    world.scheduleTick(pos, this, k);
                    world.updateNeighbors(pos, this);
                }
            }
        } else {
            this.convertToSource(world, pos, state);
        }

        BlockState blockstate = world.getBlockState(pos.down());
        if (this.canSpreadTo(world, pos.down(), blockstate)) {
            if (this.material == Material.LAVA && world.getBlockState(pos.down()).getBlock().getMaterial() == Material.WATER) {
                world.setBlockState(pos.down(), Blocks.STONE.defaultState());
                this.fizz(world, pos.down());
                return;
            }

            if (i >= 8) {
                this.spreadTo(world, pos.down(), blockstate, i);
            } else {
                this.spreadTo(world, pos.down(), blockstate, i + 8);
            }
        } else if (i >= 0 && (i == 0 || this.isLiquidBlocking(world, pos.down(), blockstate))) {
            Set<Direction> set = this.getSpread(world, pos);
            int k1 = i + j;
            if (i >= 8) {
                k1 = 1;
            }

            if (k1 >= 8) {
                return;
            }

            for (Direction direction1 : set) {
                this.spreadTo(world, pos.offset(direction1), world.getBlockState(pos.offset(direction1)), k1);
            }
        }
    }

    private void spreadTo(World world, BlockPos pos, BlockState state, int depth) {
        if (this.canSpreadTo(world, pos, state)) {
            if (state.getBlock() != Blocks.AIR) {
                if (this.material == Material.LAVA) {
                    this.fizz(world, pos);
                } else {
                    state.getBlock().dropItems(world, pos, state, 0);
                }
            }

            world.setBlockState(pos, this.defaultState().set(LEVEL, depth), 3);
        }
    }

    private int getDistanceToGap(World world, BlockPos pos, int distance, Direction fromDir) {
        int i = 1000;

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (direction != fromDir) {
                BlockPos blockpos = pos.offset(direction);
                BlockState blockstate = world.getBlockState(blockpos);
                if (!this.isLiquidBlocking(world, blockpos, blockstate) && (blockstate.getBlock().getMaterial() != this.material || blockstate.get(LEVEL) > 0)) {
                    if (!this.isLiquidBlocking(world, blockpos.down(), blockstate)) {
                        return distance;
                    }

                    if (distance < 4) {
                        int j = this.getDistanceToGap(world, blockpos, distance + 1, direction.getOpposite());
                        if (j < i) {
                            i = j;
                        }
                    }
                }
            }
        }

        return i;
    }

    private Set<Direction> getSpread(World world, BlockPos pos) {
        int i = 1000;
        Set<Direction> set = EnumSet.noneOf(Direction.class);

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos blockpos = pos.offset(direction);
            BlockState blockstate = world.getBlockState(blockpos);
            if (!this.isLiquidBlocking(world, blockpos, blockstate) && (blockstate.getBlock().getMaterial() != this.material || blockstate.get(LEVEL) > 0)) {
                int j;
                if (this.isLiquidBlocking(world, blockpos.down(), world.getBlockState(blockpos.down()))) {
                    j = this.getDistanceToGap(world, blockpos, 1, direction.getOpposite());
                } else {
                    j = 0;
                }

                if (j < i) {
                    set.clear();
                }

                if (j <= i) {
                    set.add(direction);
                    i = j;
                }
            }
        }

        return set;
    }

    private boolean isLiquidBlocking(World world, BlockPos pos, BlockState state) {
        Block block = world.getBlockState(pos).getBlock();
        return block instanceof DoorBlock
            || block == Blocks.STANDING_SIGN
            || block == Blocks.LADDER
            || block == Blocks.REEDS
            || block.material == Material.PORTAL
            || block.material.blocksMovement();
    }

    protected int getLowestDepth(World world, BlockPos pos, int depth) {
        int i = this.getLiquidState(world, pos);
        if (i < 0) {
            return depth;
        }

        if (i == 0) {
            this.adjacentSources++;
        }

        if (i >= 8) {
            i = 0;
        }

        return depth >= 0 && i >= depth ? depth : i;
    }

    private boolean canSpreadTo(World world, BlockPos pos, BlockState state) {
        Material material = state.getBlock().getMaterial();
        return material != this.material && material != Material.LAVA && !this.isLiquidBlocking(world, pos, state);
    }

    @Override
    public void onAdded(World world, BlockPos pos, BlockState state) {
        if (!this.checkSpreadCollisions(world, pos, state)) {
            world.scheduleTick(pos, this, this.getTickRate(world));
        }
    }
}
