package net.minecraft.block;

import java.util.Random;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.BooleanProperty;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.client.world.color.FoliageColors;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.stat.Stats;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

public class VineBlock extends Block {
    public static final BooleanProperty UP = BooleanProperty.of("up");
    public static final BooleanProperty NORTH = BooleanProperty.of("north");
    public static final BooleanProperty EAST = BooleanProperty.of("east");
    public static final BooleanProperty SOUTH = BooleanProperty.of("south");
    public static final BooleanProperty WEST = BooleanProperty.of("west");
    public static final BooleanProperty[] PROPERTIES = new BooleanProperty[]{UP, NORTH, SOUTH, WEST, EAST};

    public VineBlock() {
        super(Material.REPLACEABLE_PLANT);
        this.setDefaultState(this.stateDefinition.any().set(UP, false).set(NORTH, false).set(EAST, false).set(SOUTH, false).set(WEST, false));
        this.setTicksRandomly(true);
        this.setCreativeModeTab(CreativeModeTab.DECORATIONS);
    }

    @Override
    public BlockState resolveVirtualProperties(BlockState state, WorldView world, BlockPos pos) {
        return state.set(UP, world.getBlockState(pos.up()).getBlock().blocksAmbientLight());
    }

    @Override
    public void resetShape() {
        this.setShape(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
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
    public boolean canBeReplaced(World world, BlockPos pos) {
        return true;
    }

    @Override
    public void updateShape(WorldView world, BlockPos pos) {
        float f = 0.0625F;
        float f1 = 1.0F;
        float f2 = 1.0F;
        float f3 = 1.0F;
        float f4 = 0.0F;
        float f5 = 0.0F;
        float f6 = 0.0F;
        boolean flag = false;
        if (world.getBlockState(pos).get(WEST)) {
            f4 = Math.max(f4, 0.0625F);
            f1 = 0.0F;
            f2 = 0.0F;
            f5 = 1.0F;
            f3 = 0.0F;
            f6 = 1.0F;
            flag = true;
        }

        if (world.getBlockState(pos).get(EAST)) {
            f1 = Math.min(f1, 0.9375F);
            f4 = 1.0F;
            f2 = 0.0F;
            f5 = 1.0F;
            f3 = 0.0F;
            f6 = 1.0F;
            flag = true;
        }

        if (world.getBlockState(pos).get(NORTH)) {
            f6 = Math.max(f6, 0.0625F);
            f3 = 0.0F;
            f1 = 0.0F;
            f4 = 1.0F;
            f2 = 0.0F;
            f5 = 1.0F;
            flag = true;
        }

        if (world.getBlockState(pos).get(SOUTH)) {
            f3 = Math.min(f3, 0.9375F);
            f6 = 1.0F;
            f1 = 0.0F;
            f4 = 1.0F;
            f2 = 0.0F;
            f5 = 1.0F;
            flag = true;
        }

        if (!flag && this.canGrowOn(world.getBlockState(pos.up()).getBlock())) {
            f2 = Math.min(f2, 0.9375F);
            f5 = 1.0F;
            f1 = 0.0F;
            f4 = 1.0F;
            f3 = 0.0F;
            f6 = 1.0F;
        }

        this.setShape(f1, f2, f3, f4, f5, f6);
    }

    @Override
    public Box getCollisionShape(World world, BlockPos pos, BlockState state) {
        return null;
    }

    @Override
    public boolean canBePlaced(World world, BlockPos pos, Direction face) {
        switch (face) {
            case UP:
                return this.canGrowOn(world.getBlockState(pos.up()).getBlock());
            case NORTH:
            case SOUTH:
            case EAST:
            case WEST:
                return this.canGrowOn(world.getBlockState(pos.offset(face.getOpposite())).getBlock());
            default:
                return false;
        }
    }

    private boolean canGrowOn(Block block) {
        return block.isCube() && block.material.blocksMovement();
    }

    private boolean hasConnections(World world, BlockPos pos, BlockState state) {
        BlockState blockstate = state;

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BooleanProperty booleanproperty = getPropertyForDirection(direction);
            if (state.get(booleanproperty) && !this.canGrowOn(world.getBlockState(pos.offset(direction)).getBlock())) {
                BlockState blockstate1 = world.getBlockState(pos.up());
                if (blockstate1.getBlock() != this || !blockstate1.get(booleanproperty)) {
                    state = state.set(booleanproperty, false);
                }
            }
        }

        if (getConnectionCount(state) == 0) {
            return false;
        }

        if (blockstate != state) {
            world.setBlockState(pos, state, 2);
        }

        return true;
    }

    @Override
    public int getColor() {
        return FoliageColors.getDefaultColor();
    }

    @Override
    public int getColor(BlockState state) {
        return FoliageColors.getDefaultColor();
    }

    @Override
    public int getColor(WorldView world, BlockPos pos, int tint) {
        return world.getBiome(pos).getFoliageColor(pos);
    }

    @Override
    public void neighborChanged(World world, BlockPos pos, BlockState state, Block neighborBlock) {
        if (!world.isClient && !this.hasConnections(world, pos, state)) {
            this.dropItems(world, pos, state, 0);
            world.removeBlock(pos);
        }
    }

    @Override
    public void tick(World world, BlockPos pos, BlockState state, Random random) {
        if (!world.isClient) {
            if (world.random.nextInt(4) == 0) {
                int i = 4;
                int j = 5;
                boolean flag = false;

                label189:
                for (int k = -i; k <= i; k++) {
                    for (int l = -i; l <= i; l++) {
                        for (int i1 = -1; i1 <= 1; i1++) {
                            if (world.getBlockState(pos.add(k, i1, l)).getBlock() == this) {
                                if (--j <= 0) {
                                    flag = true;
                                    break label189;
                                }
                            }
                        }
                    }
                }

                Direction direction2 = Direction.pick(random);
                BlockPos blockpos2 = pos.up();
                if (direction2 == Direction.UP && pos.getY() < 255 && world.isAir(blockpos2)) {
                    if (!flag) {
                        BlockState blockstate = state;

                        for (Direction direction3 : Direction.Plane.HORIZONTAL) {
                            if (random.nextBoolean() || !this.canGrowOn(world.getBlockState(blockpos2.offset(direction3)).getBlock())) {
                                blockstate = blockstate.set(getPropertyForDirection(direction3), false);
                            }
                        }

                        if (blockstate.get(NORTH) || blockstate.get(EAST) || blockstate.get(SOUTH) || blockstate.get(WEST)) {
                            world.setBlockState(blockpos2, blockstate, 2);
                        }
                    }
                } else if (!direction2.getAxis().isHorizontal() || state.get(getPropertyForDirection(direction2))) {
                    if (pos.getY() > 1) {
                        BlockPos blockpos4 = pos.down();
                        BlockState blockstate1 = world.getBlockState(blockpos4);
                        Block block1 = blockstate1.getBlock();
                        if (block1.material == Material.AIR) {
                            BlockState blockstate2 = state;

                            for (Direction direction4 : Direction.Plane.HORIZONTAL) {
                                if (random.nextBoolean()) {
                                    blockstate2 = blockstate2.set(getPropertyForDirection(direction4), false);
                                }
                            }

                            if (blockstate2.get(NORTH) || blockstate2.get(EAST) || blockstate2.get(SOUTH) || blockstate2.get(WEST)) {
                                world.setBlockState(blockpos4, blockstate2, 2);
                            }
                        } else if (block1 == this) {
                            BlockState blockstate3 = blockstate1;

                            for (Direction direction5 : Direction.Plane.HORIZONTAL) {
                                BooleanProperty booleanproperty = getPropertyForDirection(direction5);
                                if (random.nextBoolean() && state.get(booleanproperty)) {
                                    blockstate3 = blockstate3.set(booleanproperty, true);
                                }
                            }

                            if (blockstate3.get(NORTH) || blockstate3.get(EAST) || blockstate3.get(SOUTH) || blockstate3.get(WEST)) {
                                world.setBlockState(blockpos4, blockstate3, 2);
                            }
                        }
                    }
                } else if (!flag) {
                    BlockPos blockpos3 = pos.offset(direction2);
                    Block block = world.getBlockState(blockpos3).getBlock();
                    if (block.material == Material.AIR) {
                        Direction direction = direction2.clockwiseY();
                        Direction direction1 = direction2.counterClockwiseY();
                        boolean flag1 = state.get(getPropertyForDirection(direction));
                        boolean flag2 = state.get(getPropertyForDirection(direction1));
                        BlockPos blockpos = blockpos3.offset(direction);
                        BlockPos blockpos1 = blockpos3.offset(direction1);
                        if (flag1 && this.canGrowOn(world.getBlockState(blockpos).getBlock())) {
                            world.setBlockState(blockpos3, this.defaultState().set(getPropertyForDirection(direction), true), 2);
                        } else if (flag2 && this.canGrowOn(world.getBlockState(blockpos1).getBlock())) {
                            world.setBlockState(blockpos3, this.defaultState().set(getPropertyForDirection(direction1), true), 2);
                        } else if (flag1 && world.isAir(blockpos) && this.canGrowOn(world.getBlockState(pos.offset(direction)).getBlock())) {
                            world.setBlockState(blockpos, this.defaultState().set(getPropertyForDirection(direction2.getOpposite()), true), 2);
                        } else if (flag2 && world.isAir(blockpos1) && this.canGrowOn(world.getBlockState(pos.offset(direction1)).getBlock())) {
                            world.setBlockState(blockpos1, this.defaultState().set(getPropertyForDirection(direction2.getOpposite()), true), 2);
                        } else if (this.canGrowOn(world.getBlockState(blockpos3.up()).getBlock())) {
                            world.setBlockState(blockpos3, this.defaultState(), 2);
                        }
                    } else if (block.material.isSolidBlocking() && block.isCube()) {
                        world.setBlockState(pos, state.set(getPropertyForDirection(direction2), true), 2);
                    }
                }
            }
        }
    }

    @Override
    public BlockState getPlacementState(World world, BlockPos pos, Direction dir, float dx, float dy, float dz, int metadata, LivingEntity entity) {
        BlockState blockstate = this.defaultState().set(UP, false).set(NORTH, false).set(EAST, false).set(SOUTH, false).set(WEST, false);
        return dir.getAxis().isHorizontal() ? blockstate.set(getPropertyForDirection(dir.getOpposite()), true) : blockstate;
    }

    @Override
    public Item getDropItem(BlockState state, Random random, int fortuneLevel) {
        return null;
    }

    @Override
    public int getBaseDropCount(Random random) {
        return 0;
    }

    @Override
    public void afterMinedByPlayer(World world, PlayerEntity player, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        if (!world.isClient && player.getItemInHand() != null && player.getItemInHand().getItem() == Items.SHEARS) {
            player.incrementStat(Stats.BLOCKS_MINED[Block.getId(this)]);
            dropItem(world, pos, new ItemStack(Blocks.VINE, 1, 0));
        } else {
            super.afterMinedByPlayer(world, player, pos, state, blockEntity);
        }
    }

    @Override
    public BlockLayer getRenderLayer() {
        return BlockLayer.CUTOUT;
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        return this.defaultState().set(SOUTH, (metadata & 1) > 0).set(WEST, (metadata & 2) > 0).set(NORTH, (metadata & 4) > 0).set(EAST, (metadata & 8) > 0);
    }

    @Override
    public int getMetadataFromState(BlockState state) {
        int i = 0;
        if (state.get(SOUTH)) {
            i |= 1;
        }

        if (state.get(WEST)) {
            i |= 2;
        }

        if (state.get(NORTH)) {
            i |= 4;
        }

        if (state.get(EAST)) {
            i |= 8;
        }

        return i;
    }

    @Override
    protected StateDefinition createStateDefinition() {
        return new StateDefinition(this, UP, NORTH, EAST, SOUTH, WEST);
    }

    public static BooleanProperty getPropertyForDirection(Direction dir) {
        switch (dir) {
            case UP:
                return UP;
            case NORTH:
                return NORTH;
            case SOUTH:
                return SOUTH;
            case EAST:
                return EAST;
            case WEST:
                return WEST;
            default:
                throw new IllegalArgumentException(dir + " is an invalid choice");
        }
    }

    public static int getConnectionCount(BlockState state) {
        int i = 0;

        for (BooleanProperty booleanproperty : PROPERTIES) {
            if (state.get(booleanproperty)) {
                i++;
            }
        }

        return i;
    }
}
