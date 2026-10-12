package net.minecraft.block;

import java.util.Random;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.IntegerProperty;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public class WheatBlock extends PlantBlock implements Fertilizable {
    public static final IntegerProperty AGE = IntegerProperty.of("age", 0, 7);

    protected WheatBlock() {
        this.setDefaultState(this.stateDefinition.any().set(AGE, 0));
        this.setTicksRandomly(true);
        float f = 0.5F;
        this.setShape(0.5F - f, 0.0F, 0.5F - f, 0.5F + f, 0.25F, 0.5F + f);
        this.setCreativeModeTab(null);
        this.setStrength(0.0F);
        this.setSounds(GRASS_SOUNDS);
        this.disableStats();
    }

    @Override
    protected boolean canBePlacedOn(Block block) {
        return block == Blocks.FARMLAND;
    }

    @Override
    public void tick(World world, BlockPos pos, BlockState state, Random random) {
        super.tick(world, pos, state, random);
        if (world.getRawBrightness(pos.up()) >= 9) {
            int i = state.get(AGE);
            if (i < 7) {
                float f = getGrowthSpeed(this, world, pos);
                if (random.nextInt((int)(25.0F / f) + 1) == 0) {
                    world.setBlockState(pos, state.set(AGE, i + 1), 2);
                }
            }
        }
    }

    public void growFully(World world, BlockPos pos, BlockState state) {
        int i = state.get(AGE) + MathHelper.nextInt(world.random, 2, 5);
        if (i > 7) {
            i = 7;
        }

        world.setBlockState(pos, state.set(AGE, i), 2);
    }

    protected static float getGrowthSpeed(Block block, World world, BlockPos pos) {
        float f = 1.0F;
        BlockPos blockpos = pos.down();

        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                float f1 = 0.0F;
                BlockState blockstate = world.getBlockState(blockpos.add(i, 0, j));
                if (blockstate.getBlock() == Blocks.FARMLAND) {
                    f1 = 1.0F;
                    if (blockstate.get(FarmlandBlock.MOISTURE) > 0) {
                        f1 = 3.0F;
                    }
                }

                if (i != 0 || j != 0) {
                    f1 /= 4.0F;
                }

                f += f1;
            }
        }

        BlockPos blockpos1 = pos.north();
        BlockPos blockpos2 = pos.south();
        BlockPos blockpos3 = pos.west();
        BlockPos blockpos4 = pos.east();
        boolean flag = block == world.getBlockState(blockpos3).getBlock() || block == world.getBlockState(blockpos4).getBlock();
        boolean flag1 = block == world.getBlockState(blockpos1).getBlock() || block == world.getBlockState(blockpos2).getBlock();
        if (flag && flag1) {
            f /= 2.0F;
        } else {
            boolean flag2 = block == world.getBlockState(blockpos3.north()).getBlock()
                || block == world.getBlockState(blockpos4.north()).getBlock()
                || block == world.getBlockState(blockpos4.south()).getBlock()
                || block == world.getBlockState(blockpos3.south()).getBlock();
            if (flag2) {
                f /= 2.0F;
            }
        }

        return f;
    }

    @Override
    public boolean canSurvive(World world, BlockPos pos, BlockState state) {
        return (world.getActualLight(pos) >= 8 || world.hasSkyAccess(pos)) && this.canBePlacedOn(world.getBlockState(pos.down()).getBlock());
    }

    protected Item getSeedItem() {
        return Items.WHEAT_SEEDS;
    }

    protected Item getPlantItem() {
        return Items.WHEAT;
    }

    @Override
    public void dropItems(World world, BlockPos pos, BlockState state, float luck, int fortuneLevel) {
        super.dropItems(world, pos, state, luck, 0);
        if (!world.isClient) {
            int i = state.get(AGE);
            if (i >= 7) {
                int j = 3 + fortuneLevel;

                for (int k = 0; k < j; k++) {
                    if (world.random.nextInt(15) <= i) {
                        dropItem(world, pos, new ItemStack(this.getSeedItem(), 1, 0));
                    }
                }
            }
        }
    }

    @Override
    public Item getDropItem(BlockState state, Random random, int fortuneLevel) {
        return state.get(AGE) == 7 ? this.getPlantItem() : this.getSeedItem();
    }

    @Override
    public Item getPickItem(World world, BlockPos pos) {
        return this.getSeedItem();
    }

    @Override
    public boolean canGrow(World world, BlockPos pos, BlockState state, boolean isClient) {
        return state.get(AGE) < 7;
    }

    @Override
    public boolean canBeFertilized(World world, Random rand, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void grow(World world, Random rand, BlockPos pos, BlockState state) {
        this.growFully(world, pos, state);
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
