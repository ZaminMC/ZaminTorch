package net.minecraft.block;

import java.util.Random;
import net.minecraft.block.state.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.HugeMushroomFeature;

public class MushroomPlantBlock extends PlantBlock implements Fertilizable {
    protected MushroomPlantBlock() {
        float f = 0.2F;
        this.setShape(0.5F - f, 0.0F, 0.5F - f, 0.5F + f, f * 2.0F, 0.5F + f);
        this.setTicksRandomly(true);
    }

    @Override
    public void tick(World world, BlockPos pos, BlockState state, Random random) {
        if (random.nextInt(25) == 0) {
            int i = 5;
            int j = 4;

            for (BlockPos blockpos : BlockPos.iterateRegionMutable(pos.add(-4, -1, -4), pos.add(4, 1, 4))) {
                if (world.getBlockState(blockpos).getBlock() == this) {
                    if (--i <= 0) {
                        return;
                    }
                }
            }

            BlockPos blockpos1 = pos.add(random.nextInt(3) - 1, random.nextInt(2) - random.nextInt(2), random.nextInt(3) - 1);

            for (int k = 0; k < 4; k++) {
                if (world.isAir(blockpos1) && this.canSurvive(world, blockpos1, this.defaultState())) {
                    pos = blockpos1;
                }

                blockpos1 = pos.add(random.nextInt(3) - 1, random.nextInt(2) - random.nextInt(2), random.nextInt(3) - 1);
            }

            if (world.isAir(blockpos1) && this.canSurvive(world, blockpos1, this.defaultState())) {
                world.setBlockState(blockpos1, this.defaultState(), 2);
            }
        }
    }

    @Override
    public boolean canBePlaced(World world, BlockPos pos) {
        return super.canBePlaced(world, pos) && this.canSurvive(world, pos, this.defaultState());
    }

    @Override
    protected boolean canBePlacedOn(Block block) {
        return block.isOpaque();
    }

    @Override
    public boolean canSurvive(World world, BlockPos pos, BlockState state) {
        if (pos.getY() >= 0 && pos.getY() < 256) {
            BlockState blockstate = world.getBlockState(pos.down());
            return blockstate.getBlock() == Blocks.MYCELIUM
                || blockstate.getBlock() == Blocks.DIRT && blockstate.get(DirtBlock.VARIANT) == DirtBlock.Variant.PODZOL
                || world.getActualLight(pos) < 13 && this.canBePlacedOn(blockstate.getBlock());
        } else {
            return false;
        }
    }

    public boolean grow(World world, BlockPos pos, BlockState state, Random random) {
        world.removeBlock(pos);
        Feature feature = null;
        if (this == Blocks.BROWN_MUSHROOM) {
            feature = new HugeMushroomFeature(Blocks.BROWN_MUSHROOM_BLOCK);
        } else if (this == Blocks.RED_MUSHROOM) {
            feature = new HugeMushroomFeature(Blocks.RED_MUSHROOM_BLOCK);
        }

        if (feature != null && feature.place(world, random, pos)) {
            return true;
        }

        world.setBlockState(pos, state, 3);
        return false;
    }

    @Override
    public boolean canGrow(World world, BlockPos pos, BlockState state, boolean isClient) {
        return true;
    }

    @Override
    public boolean canBeFertilized(World world, Random rand, BlockPos pos, BlockState state) {
        return rand.nextFloat() < 0.4;
    }

    @Override
    public void grow(World world, Random rand, BlockPos pos, BlockState state) {
        this.grow(world, pos, state, rand);
    }
}
