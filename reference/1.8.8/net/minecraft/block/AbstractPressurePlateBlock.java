package net.minecraft.block;

import java.util.Random;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

public abstract class AbstractPressurePlateBlock extends Block {
    protected AbstractPressurePlateBlock(Material material) {
        this(material, material.getColor());
    }

    protected AbstractPressurePlateBlock(Material material, MapColor mapColor) {
        super(material, mapColor);
        this.setCreativeModeTab(CreativeModeTab.REDSTONE);
        this.setTicksRandomly(true);
    }

    @Override
    public void updateShape(WorldView world, BlockPos pos) {
        this.updateShape(world.getBlockState(pos));
    }

    protected void updateShape(BlockState state) {
        boolean flag = this.getOutputSignal(state) > 0;
        float f = 0.0625F;
        if (flag) {
            this.setShape(0.0625F, 0.0F, 0.0625F, 0.9375F, 0.03125F, 0.9375F);
        } else {
            this.setShape(0.0625F, 0.0F, 0.0625F, 0.9375F, 0.0625F, 0.9375F);
        }
    }

    @Override
    public int getTickRate(World world) {
        return 20;
    }

    @Override
    public Box getCollisionShape(World world, BlockPos pos, BlockState state) {
        return null;
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
    public boolean canWalkThrough(WorldView world, BlockPos pos) {
        return true;
    }

    @Override
    public boolean canRespawnIn() {
        return true;
    }

    @Override
    public boolean canBePlaced(World world, BlockPos pos) {
        return this.canSitOnTop(world, pos.down());
    }

    @Override
    public void neighborChanged(World world, BlockPos pos, BlockState state, Block neighborBlock) {
        if (!this.canSitOnTop(world, pos.down())) {
            this.dropItems(world, pos, state, 0);
            world.removeBlock(pos);
        }
    }

    private boolean canSitOnTop(World world, BlockPos pos) {
        return World.hasSolidTop(world, pos) || world.getBlockState(pos).getBlock() instanceof FenceBlock;
    }

    @Override
    public void randomTick(World world, BlockPos pos, BlockState state, Random random) {
    }

    @Override
    public void tick(World world, BlockPos pos, BlockState state, Random random) {
        if (!world.isClient) {
            int i = this.getOutputSignal(state);
            if (i > 0) {
                this.updateOutputState(world, pos, state, i);
            }
        }
    }

    @Override
    public void onEntityCollision(World world, BlockPos pos, BlockState state, Entity entity) {
        if (!world.isClient) {
            int i = this.getOutputSignal(state);
            if (i == 0) {
                this.updateOutputState(world, pos, state, i);
            }
        }
    }

    protected void updateOutputState(World world, BlockPos pos, BlockState state, int signal) {
        int i = this.calculateOutputSignal(world, pos);
        boolean flag = signal > 0;
        boolean flag1 = i > 0;
        if (signal != i) {
            state = this.setOutputSignal(state, i);
            world.setBlockState(pos, state, 2);
            this.updateNeighbors(world, pos);
            world.notifyRegionChanged(pos, pos);
        }

        if (!flag1 && flag) {
            world.playSound(pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5, "random.click", 0.3F, 0.5F);
        } else if (flag1 && !flag) {
            world.playSound(pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5, "random.click", 0.3F, 0.6F);
        }

        if (flag1) {
            world.scheduleTick(pos, this, this.getTickRate(world));
        }
    }

    /**
     * Returns the bounds within which entities can activate this pressure plate.
     */
    protected Box getActivationBounds(BlockPos pos) {
        float f = 0.125F;
        return new Box(pos.getX() + 0.125F, pos.getY(), pos.getZ() + 0.125F, pos.getX() + 1 - 0.125F, pos.getY() + 0.25, pos.getZ() + 1 - 0.125F);
    }

    @Override
    public void onRemoved(World world, BlockPos pos, BlockState state) {
        if (this.getOutputSignal(state) > 0) {
            this.updateNeighbors(world, pos);
        }

        super.onRemoved(world, pos, state);
    }

    protected void updateNeighbors(World world, BlockPos pos) {
        world.updateNeighbors(pos, this);
        world.updateNeighbors(pos.down(), this);
    }

    @Override
    public int getSignal(WorldView world, BlockPos pos, BlockState state, Direction dir) {
        return this.getOutputSignal(state);
    }

    @Override
    public int getDirectSignal(WorldView world, BlockPos pos, BlockState state, Direction dir) {
        return dir == Direction.UP ? this.getOutputSignal(state) : 0;
    }

    @Override
    public boolean isSignalSource() {
        return true;
    }

    @Override
    public void resetShape() {
        float f = 0.5F;
        float f1 = 0.125F;
        float f2 = 0.5F;
        this.setShape(0.0F, 0.375F, 0.0F, 1.0F, 0.625F, 1.0F);
    }

    @Override
    public int getPistonMoveBehavior() {
        return 1;
    }

    protected abstract int calculateOutputSignal(World world, BlockPos pos);

    /**
     * Returns the output signal encoded in the given state.
     */
    protected abstract int getOutputSignal(BlockState state);

    /**
     * Returns the state that encodes the given output signal.
     */
    protected abstract BlockState setOutputSignal(BlockState state, int signal);
}
