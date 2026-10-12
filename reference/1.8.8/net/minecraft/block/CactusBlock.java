package net.minecraft.block;

import java.util.Random;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.IntegerProperty;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class CactusBlock extends Block {
    public static final IntegerProperty AGE = IntegerProperty.of("age", 0, 15);

    protected CactusBlock() {
        super(Material.CACTUS);
        this.setDefaultState(this.stateDefinition.any().set(AGE, 0));
        this.setTicksRandomly(true);
        this.setCreativeModeTab(CreativeModeTab.DECORATIONS);
    }

    @Override
    public void tick(World world, BlockPos pos, BlockState state, Random random) {
        BlockPos blockpos = pos.up();
        if (world.isAir(blockpos)) {
            int i = 1;

            while (world.getBlockState(pos.down(i)).getBlock() == this) {
                i++;
            }

            if (i < 3) {
                int j = state.get(AGE);
                if (j == 15) {
                    world.setBlockState(blockpos, this.defaultState());
                    BlockState blockstate = state.set(AGE, 0);
                    world.setBlockState(pos, blockstate, 4);
                    this.neighborChanged(world, blockpos, blockstate, this);
                } else {
                    world.setBlockState(pos, state.set(AGE, j + 1), 4);
                }
            }
        }
    }

    @Override
    public Box getCollisionShape(World world, BlockPos pos, BlockState state) {
        float f = 0.0625F;
        return new Box(pos.getX() + f, pos.getY(), pos.getZ() + f, pos.getX() + 1 - f, pos.getY() + 1 - f, pos.getZ() + 1 - f);
    }

    @Override
    public Box getOutlineShape(World world, BlockPos pos) {
        float f = 0.0625F;
        return new Box(pos.getX() + f, pos.getY(), pos.getZ() + f, pos.getX() + 1 - f, pos.getY() + 1, pos.getZ() + 1 - f);
    }

    @Override
    public boolean isCube() {
        return false;
    }

    @Override
    public boolean isSolidRender() {
        return false;
    }

    @Override
    public boolean canBePlaced(World world, BlockPos pos) {
        return super.canBePlaced(world, pos) && this.canSurvive(world, pos);
    }

    @Override
    public void neighborChanged(World world, BlockPos pos, BlockState state, Block neighborBlock) {
        if (!this.canSurvive(world, pos)) {
            world.breakBlock(pos, true);
        }
    }

    public boolean canSurvive(World world, BlockPos pos) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (world.getBlockState(pos.offset(direction)).getBlock().getMaterial().isSolid()) {
                return false;
            }
        }

        Block block = world.getBlockState(pos.down()).getBlock();
        return block == Blocks.CACTUS || block == Blocks.SAND;
    }

    @Override
    public void onEntityCollision(World world, BlockPos pos, BlockState state, Entity entity) {
        entity.takeDamage(DamageSource.CACTUS, 1.0F);
    }

    @Override
    public BlockLayer getRenderLayer() {
        return BlockLayer.CUTOUT;
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        return this.defaultState().set(AGE, metadata);
    }

    @Override
    public int getMetadataFromState(BlockState state) {
        return state.get(AGE);
    }

    @Override
    protected StateDefinition createStateDefinition() {
        return new StateDefinition(this, AGE);
    }
}
