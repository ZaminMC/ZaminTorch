package net.minecraft.block;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

public class RedstoneTorchBlock extends TorchBlock {
    private static Map<World, List<RedstoneTorchBlock.ToggleEntry>> RECENT_TOGGLES = Maps.newHashMap();
    private final boolean lit;

    private boolean shouldBurnOut(World world, BlockPos pos, boolean logToggle) {
        if (!RECENT_TOGGLES.containsKey(world)) {
            RECENT_TOGGLES.put(world, Lists.newArrayList());
        }

        List<RedstoneTorchBlock.ToggleEntry> list = RECENT_TOGGLES.get(world);
        if (logToggle) {
            list.add(new RedstoneTorchBlock.ToggleEntry(pos, world.getTime()));
        }

        int i = 0;

        for (int j = 0; j < list.size(); j++) {
            RedstoneTorchBlock.ToggleEntry redstonetorchblock$toggleentry = list.get(j);
            if (redstonetorchblock$toggleentry.pos.equals(pos)) {
                if (++i >= 8) {
                    return true;
                }
            }
        }

        return false;
    }

    protected RedstoneTorchBlock(boolean lit) {
        this.lit = lit;
        this.setTicksRandomly(true);
        this.setCreativeModeTab(null);
    }

    @Override
    public int getTickRate(World world) {
        return 2;
    }

    @Override
    public void onAdded(World world, BlockPos pos, BlockState state) {
        if (this.lit) {
            for (Direction direction : Direction.values()) {
                world.updateNeighbors(pos.offset(direction), this);
            }
        }
    }

    @Override
    public void onRemoved(World world, BlockPos pos, BlockState state) {
        if (this.lit) {
            for (Direction direction : Direction.values()) {
                world.updateNeighbors(pos.offset(direction), this);
            }
        }
    }

    @Override
    public int getSignal(WorldView world, BlockPos pos, BlockState state, Direction dir) {
        return this.lit && state.get(FACING) != dir ? 15 : 0;
    }

    private boolean hasNeighborSignal(World world, BlockPos pos, BlockState state) {
        Direction direction = state.get(FACING).getOpposite();
        return world.hasSignal(pos.offset(direction), direction);
    }

    @Override
    public void randomTick(World world, BlockPos pos, BlockState state, Random random) {
    }

    @Override
    public void tick(World world, BlockPos pos, BlockState state, Random random) {
        boolean flag = this.hasNeighborSignal(world, pos, state);
        List<RedstoneTorchBlock.ToggleEntry> list = RECENT_TOGGLES.get(world);

        while (list != null && !list.isEmpty() && world.getTime() - list.get(0).time > 60L) {
            list.remove(0);
        }

        if (this.lit) {
            if (flag) {
                world.setBlockState(pos, Blocks.UNLIT_REDSTONE_TORCH.defaultState().set(FACING, state.get(FACING)), 3);
                if (this.shouldBurnOut(world, pos, true)) {
                    world.playSound(
                        pos.getX() + 0.5F,
                        pos.getY() + 0.5F,
                        pos.getZ() + 0.5F,
                        "random.fizz",
                        0.5F,
                        2.6F + (world.random.nextFloat() - world.random.nextFloat()) * 0.8F
                    );

                    for (int i = 0; i < 5; i++) {
                        double d0 = pos.getX() + random.nextDouble() * 0.6 + 0.2;
                        double d1 = pos.getY() + random.nextDouble() * 0.6 + 0.2;
                        double d2 = pos.getZ() + random.nextDouble() * 0.6 + 0.2;
                        world.addParticle(ParticleType.SMOKE_NORMAL, d0, d1, d2, 0.0, 0.0, 0.0);
                    }

                    world.scheduleTick(pos, world.getBlockState(pos).getBlock(), 160);
                }
            }
        } else if (!flag && !this.shouldBurnOut(world, pos, false)) {
            world.setBlockState(pos, Blocks.REDSTONE_TORCH.defaultState().set(FACING, state.get(FACING)), 3);
        }
    }

    @Override
    public void neighborChanged(World world, BlockPos pos, BlockState state, Block neighborBlock) {
        if (!this.tryBreak(world, pos, state)) {
            if (this.lit == this.hasNeighborSignal(world, pos, state)) {
                world.scheduleTick(pos, this, this.getTickRate(world));
            }
        }
    }

    @Override
    public int getDirectSignal(WorldView world, BlockPos pos, BlockState state, Direction dir) {
        return dir == Direction.DOWN ? this.getSignal(world, pos, state, dir) : 0;
    }

    @Override
    public Item getDropItem(BlockState state, Random random, int fortuneLevel) {
        return Item.byBlock(Blocks.REDSTONE_TORCH);
    }

    @Override
    public boolean isSignalSource() {
        return true;
    }

    @Override
    public void randomDisplayTick(World world, BlockPos pos, BlockState state, Random random) {
        if (this.lit) {
            double d0 = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.2;
            double d1 = pos.getY() + 0.7 + (random.nextDouble() - 0.5) * 0.2;
            double d2 = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.2;
            Direction direction = state.get(FACING);
            if (direction.getAxis().isHorizontal()) {
                Direction direction1 = direction.getOpposite();
                double d3 = 0.27;
                d0 += 0.27 * direction1.getOffsetX();
                d1 += 0.22;
                d2 += 0.27 * direction1.getOffsetZ();
            }

            world.addParticle(ParticleType.REDSTONE, d0, d1, d2, 0.0, 0.0, 0.0);
        }
    }

    @Override
    public Item getPickItem(World world, BlockPos pos) {
        return Item.byBlock(Blocks.REDSTONE_TORCH);
    }

    @Override
    public boolean is(Block block) {
        return block == Blocks.UNLIT_REDSTONE_TORCH || block == Blocks.REDSTONE_TORCH;
    }

    static class ToggleEntry {
        BlockPos pos;
        long time;

        public ToggleEntry(BlockPos pos, long time) {
            this.pos = pos;
            this.time = time;
        }
    }
}
