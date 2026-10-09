package net.minecraft.block;

import com.google.common.base.Predicate;
import java.util.Random;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.DirectionProperty;
import net.minecraft.block.state.property.IntegerProperty;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

public class StemBlock extends PlantBlock implements Fertilizable {
    public static final IntegerProperty AGE = IntegerProperty.of("age", 0, 7);
    public static final DirectionProperty FACING = DirectionProperty.of("facing", new Predicate<Direction>() {
        public boolean apply(Direction direction) {
            return direction != Direction.DOWN;
        }
    });
    private final Block plant;

    protected StemBlock(Block plant) {
        this.setDefaultState(this.stateDefinition.any().set(AGE, 0).set(FACING, Direction.UP));
        this.plant = plant;
        this.setTicksRandomly(true);
        float f = 0.125F;
        this.setShape(0.5F - f, 0.0F, 0.5F - f, 0.5F + f, 0.25F, 0.5F + f);
        this.setCreativeModeTab(null);
    }

    @Override
    public BlockState resolveVirtualProperties(BlockState state, WorldView world, BlockPos pos) {
        state = state.set(FACING, Direction.UP);

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (world.getBlockState(pos.offset(direction)).getBlock() == this.plant) {
                state = state.set(FACING, direction);
                break;
            }
        }

        return state;
    }

    @Override
    protected boolean canBePlacedOn(Block block) {
        return block == Blocks.FARMLAND;
    }

    @Override
    public void tick(World world, BlockPos pos, BlockState state, Random random) {
        super.tick(world, pos, state, random);
        if (world.getRawBrightness(pos.up()) >= 9) {
            float f = WheatBlock.getGrowthSpeed(this, world, pos);
            if (random.nextInt((int)(25.0F / f) + 1) == 0) {
                int i = state.get(AGE);
                if (i < 7) {
                    state = state.set(AGE, i + 1);
                    world.setBlockState(pos, state, 2);
                } else {
                    for (Direction direction : Direction.Plane.HORIZONTAL) {
                        if (world.getBlockState(pos.offset(direction)).getBlock() == this.plant) {
                            return;
                        }
                    }

                    pos = pos.offset(Direction.Plane.HORIZONTAL.pick(random));
                    Block block = world.getBlockState(pos.down()).getBlock();
                    if (world.getBlockState(pos).getBlock().material == Material.AIR
                        && (block == Blocks.FARMLAND || block == Blocks.DIRT || block == Blocks.GRASS)) {
                        world.setBlockState(pos, this.plant.defaultState());
                    }
                }
            }
        }
    }

    public void grow(World world, BlockPos pos, BlockState state) {
        int i = state.get(AGE) + MathHelper.nextInt(world.random, 2, 5);
        world.setBlockState(pos, state.set(AGE, Math.min(7, i)), 2);
    }

    @Override
    public int getColor(BlockState state) {
        if (state.getBlock() != this) {
            return super.getColor(state);
        }

        int i = state.get(AGE);
        int j = i * 32;
        int k = 255 - i * 8;
        int l = i * 4;
        return j << 16 | k << 8 | l;
    }

    @Override
    public int getColor(WorldView world, BlockPos pos, int tint) {
        return this.getColor(world.getBlockState(pos));
    }

    @Override
    public void resetShape() {
        float f = 0.125F;
        this.setShape(0.5F - f, 0.0F, 0.5F - f, 0.5F + f, 0.25F, 0.5F + f);
    }

    @Override
    public void updateShape(WorldView world, BlockPos pos) {
        this.maxY = (world.getBlockState(pos).get(AGE) * 2 + 2) / 16.0F;
        float f = 0.125F;
        this.setShape(0.5F - f, 0.0F, 0.5F - f, 0.5F + f, (float)this.maxY, 0.5F + f);
    }

    @Override
    public void dropItems(World world, BlockPos pos, BlockState state, float luck, int fortuneLevel) {
        super.dropItems(world, pos, state, luck, fortuneLevel);
        if (!world.isClient) {
            Item item = this.getSeedsItem();
            if (item != null) {
                int i = state.get(AGE);

                for (int j = 0; j < 3; j++) {
                    if (world.random.nextInt(15) <= i) {
                        dropItem(world, pos, new ItemStack(item));
                    }
                }
            }
        }
    }

    protected Item getSeedsItem() {
        if (this.plant == Blocks.PUMPKIN) {
            return Items.PUMPKIN_SEEDS;
        } else {
            return this.plant == Blocks.MELON_BLOCK ? Items.MELON_SEEDS : null;
        }
    }

    @Override
    public Item getDropItem(BlockState state, Random random, int fortuneLevel) {
        return null;
    }

    @Override
    public Item getPickItem(World world, BlockPos pos) {
        Item item = this.getSeedsItem();
        return item != null ? item : null;
    }

    @Override
    public boolean canGrow(World world, BlockPos pos, BlockState state, boolean isClient) {
        return state.get(AGE) != 7;
    }

    @Override
    public boolean canBeFertilized(World world, Random rand, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void grow(World world, Random rand, BlockPos pos, BlockState state) {
        this.grow(world, pos, state);
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
        return new StateDefinition(this, AGE, FACING);
    }
}
