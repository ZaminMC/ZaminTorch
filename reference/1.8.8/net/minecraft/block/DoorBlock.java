package net.minecraft.block;

import java.util.Random;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.BooleanProperty;
import net.minecraft.block.state.property.DirectionProperty;
import net.minecraft.block.state.property.EnumProperty;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.locale.I18n;
import net.minecraft.util.StringSerializable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

public class DoorBlock extends Block {
    public static final DirectionProperty FACING = DirectionProperty.of("facing", Direction.Plane.HORIZONTAL);
    public static final BooleanProperty OPEN = BooleanProperty.of("open");
    public static final EnumProperty<DoorBlock.Hinge> HINGE = EnumProperty.of("hinge", DoorBlock.Hinge.class);
    public static final BooleanProperty POWERED = BooleanProperty.of("powered");
    public static final EnumProperty<DoorBlock.Half> HALF = EnumProperty.of("half", DoorBlock.Half.class);

    protected DoorBlock(Material material) {
        super(material);
        this.setDefaultState(
            this.stateDefinition
                .any()
                .set(FACING, Direction.NORTH)
                .set(OPEN, false)
                .set(HINGE, DoorBlock.Hinge.LEFT)
                .set(POWERED, false)
                .set(HALF, DoorBlock.Half.LOWER)
        );
    }

    @Override
    public String getName() {
        return I18n.translate((this.getTranslationKey() + ".name").replaceAll("tile", "item"));
    }

    @Override
    public boolean isSolidRender() {
        return false;
    }

    @Override
    public boolean canWalkThrough(WorldView world, BlockPos pos) {
        return isOpen(getCombinedMetadata(world, pos));
    }

    @Override
    public boolean isCube() {
        return false;
    }

    @Override
    public Box getOutlineShape(World world, BlockPos pos) {
        this.updateShape(world, pos);
        return super.getOutlineShape(world, pos);
    }

    @Override
    public Box getCollisionShape(World world, BlockPos pos, BlockState state) {
        this.updateShape(world, pos);
        return super.getCollisionShape(world, pos, state);
    }

    @Override
    public void updateShape(WorldView world, BlockPos pos) {
        this.updateShape(getCombinedMetadata(world, pos));
    }

    private void updateShape(int metadata) {
        float f = 0.1875F;
        this.setShape(0.0F, 0.0F, 0.0F, 1.0F, 2.0F, 1.0F);
        Direction direction = getFacing(metadata);
        boolean flag = isOpen(metadata);
        boolean flag1 = isRightHinge(metadata);
        if (flag) {
            if (direction == Direction.EAST) {
                if (!flag1) {
                    this.setShape(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, f);
                } else {
                    this.setShape(0.0F, 0.0F, 1.0F - f, 1.0F, 1.0F, 1.0F);
                }
            } else if (direction == Direction.SOUTH) {
                if (!flag1) {
                    this.setShape(1.0F - f, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
                } else {
                    this.setShape(0.0F, 0.0F, 0.0F, f, 1.0F, 1.0F);
                }
            } else if (direction == Direction.WEST) {
                if (!flag1) {
                    this.setShape(0.0F, 0.0F, 1.0F - f, 1.0F, 1.0F, 1.0F);
                } else {
                    this.setShape(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, f);
                }
            } else if (direction == Direction.NORTH) {
                if (!flag1) {
                    this.setShape(0.0F, 0.0F, 0.0F, f, 1.0F, 1.0F);
                } else {
                    this.setShape(1.0F - f, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
                }
            }
        } else if (direction == Direction.EAST) {
            this.setShape(0.0F, 0.0F, 0.0F, f, 1.0F, 1.0F);
        } else if (direction == Direction.SOUTH) {
            this.setShape(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, f);
        } else if (direction == Direction.WEST) {
            this.setShape(1.0F - f, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
        } else if (direction == Direction.NORTH) {
            this.setShape(0.0F, 0.0F, 1.0F - f, 1.0F, 1.0F, 1.0F);
        }
    }

    @Override
    public boolean use(World world, BlockPos pos, BlockState state, PlayerEntity player, Direction face, float faceX, float faceY, float faceZ) {
        if (this.material == Material.IRON) {
            return true;
        }

        BlockPos blockpos = state.get(HALF) == DoorBlock.Half.LOWER ? pos : pos.down();
        BlockState blockstate = pos.equals(blockpos) ? state : world.getBlockState(blockpos);
        if (blockstate.getBlock() != this) {
            return false;
        }

        state = blockstate.next(OPEN);
        world.setBlockState(blockpos, state, 2);
        world.notifyRegionChanged(blockpos, pos);
        world.doEvent(player, state.get(OPEN) ? 1003 : 1006, pos, 0);
        return true;
    }

    public void updateOpenState(World world, BlockPos pos, boolean open) {
        BlockState blockstate = world.getBlockState(pos);
        if (blockstate.getBlock() == this) {
            BlockPos blockpos = blockstate.get(HALF) == DoorBlock.Half.LOWER ? pos : pos.down();
            BlockState blockstate1 = pos == blockpos ? blockstate : world.getBlockState(blockpos);
            if (blockstate1.getBlock() == this && blockstate1.get(OPEN) != open) {
                world.setBlockState(blockpos, blockstate1.set(OPEN, open), 2);
                world.notifyRegionChanged(blockpos, pos);
                world.doEvent(null, open ? 1003 : 1006, pos, 0);
            }
        }
    }

    @Override
    public void neighborChanged(World world, BlockPos pos, BlockState state, Block neighborBlock) {
        if (state.get(HALF) == DoorBlock.Half.UPPER) {
            BlockPos blockpos = pos.down();
            BlockState blockstate = world.getBlockState(blockpos);
            if (blockstate.getBlock() != this) {
                world.removeBlock(pos);
            } else if (neighborBlock != this) {
                this.neighborChanged(world, blockpos, blockstate, neighborBlock);
            }
        } else {
            boolean flag1 = false;
            BlockPos blockpos1 = pos.up();
            BlockState blockstate1 = world.getBlockState(blockpos1);
            if (blockstate1.getBlock() != this) {
                world.removeBlock(pos);
                flag1 = true;
            }

            if (!World.hasSolidTop(world, pos.down())) {
                world.removeBlock(pos);
                flag1 = true;
                if (blockstate1.getBlock() == this) {
                    world.removeBlock(blockpos1);
                }
            }

            if (flag1) {
                if (!world.isClient) {
                    this.dropItems(world, pos, state, 0);
                }
            } else {
                boolean flag = world.hasNeighborSignal(pos) || world.hasNeighborSignal(blockpos1);
                if ((flag || neighborBlock.isSignalSource()) && neighborBlock != this && flag != blockstate1.get(POWERED)) {
                    world.setBlockState(blockpos1, blockstate1.set(POWERED, flag), 2);
                    if (flag != state.get(OPEN)) {
                        world.setBlockState(pos, state.set(OPEN, flag), 2);
                        world.notifyRegionChanged(pos, pos);
                        world.doEvent(null, flag ? 1003 : 1006, pos, 0);
                    }
                }
            }
        }
    }

    @Override
    public Item getDropItem(BlockState state, Random random, int fortuneLevel) {
        return state.get(HALF) == DoorBlock.Half.UPPER ? null : this.getItem();
    }

    @Override
    public HitResult rayTrace(World world, BlockPos pos, Vec3d start, Vec3d end) {
        this.updateShape(world, pos);
        return super.rayTrace(world, pos, start, end);
    }

    @Override
    public boolean canBePlaced(World world, BlockPos pos) {
        return pos.getY() < 255 && World.hasSolidTop(world, pos.down()) && super.canBePlaced(world, pos) && super.canBePlaced(world, pos.up());
    }

    @Override
    public int getPistonMoveBehavior() {
        return 1;
    }

    /**
     * Returns the combined metadata of both halves of the door. The information is laid
     * out as follows:
     * 
     * <p>  bit   property
     * <br> 1     facing
     * <br> 2
     * <br> 3     open
     * <br> 4     half (0 = lower, 1 = upper)
     * <br> 5     hinge (0 = left, 1 = right)
     * <br> 6     powered
     */
    public static int getCombinedMetadata(WorldView world, BlockPos pos) {
        BlockState blockstate = world.getBlockState(pos);
        int i = blockstate.getBlock().getMetadataFromState(blockstate);
        boolean flag = isUpperHalf(i);
        BlockState blockstate1 = world.getBlockState(pos.down());
        int j = blockstate1.getBlock().getMetadataFromState(blockstate1);
        int k = flag ? j : i;
        BlockState blockstate2 = world.getBlockState(pos.up());
        int l = blockstate2.getBlock().getMetadataFromState(blockstate2);
        int i1 = flag ? i : l;
        boolean flag1 = (i1 & 1) != 0;
        boolean flag2 = (i1 & 2) != 0;
        return getLowerHalfMetadata(k) | (flag ? 8 : 0) | (flag1 ? 16 : 0) | (flag2 ? 32 : 0);
    }

    @Override
    public Item getPickItem(World world, BlockPos pos) {
        return this.getItem();
    }

    private Item getItem() {
        if (this == Blocks.IRON_DOOR) {
            return Items.IRON_DOOR;
        } else if (this == Blocks.SPRUCE_DOOR) {
            return Items.SPRUCE_DOOR;
        } else if (this == Blocks.BIRCH_DOOR) {
            return Items.BIRCH_DOOR;
        } else if (this == Blocks.JUNGLE_DOOR) {
            return Items.JUNGLE_DOOR;
        } else if (this == Blocks.ACACIA_DOOR) {
            return Items.ACACIA_DOOR;
        } else {
            return this == Blocks.DARK_OAK_DOOR ? Items.DARK_OAK_DOOR : Items.WOODEN_DOOR;
        }
    }

    @Override
    public void beforeMinedByPlayer(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        BlockPos blockpos = pos.down();
        if (player.abilities.creativeMode && state.get(HALF) == DoorBlock.Half.UPPER && world.getBlockState(blockpos).getBlock() == this) {
            world.removeBlock(blockpos);
        }
    }

    @Override
    public BlockLayer getRenderLayer() {
        return BlockLayer.CUTOUT;
    }

    @Override
    public BlockState resolveVirtualProperties(BlockState state, WorldView world, BlockPos pos) {
        if (state.get(HALF) == DoorBlock.Half.LOWER) {
            BlockState blockstate = world.getBlockState(pos.up());
            if (blockstate.getBlock() == this) {
                state = state.set(HINGE, blockstate.get(HINGE)).set(POWERED, blockstate.get(POWERED));
            }
        } else {
            BlockState blockstate1 = world.getBlockState(pos.down());
            if (blockstate1.getBlock() == this) {
                state = state.set(FACING, blockstate1.get(FACING)).set(OPEN, blockstate1.get(OPEN));
            }
        }

        return state;
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        return (metadata & 8) > 0
            ? this.defaultState()
                .set(HALF, DoorBlock.Half.UPPER)
                .set(HINGE, (metadata & 1) > 0 ? DoorBlock.Hinge.RIGHT : DoorBlock.Hinge.LEFT)
                .set(POWERED, (metadata & 2) > 0)
            : this.defaultState()
                .set(HALF, DoorBlock.Half.LOWER)
                .set(FACING, Direction.byIdHorizontal(metadata & 3).counterClockwiseY())
                .set(OPEN, (metadata & 4) > 0);
    }

    @Override
    public int getMetadataFromState(BlockState state) {
        int i = 0;
        if (state.get(HALF) == DoorBlock.Half.UPPER) {
            i |= 8;
            if (state.get(HINGE) == DoorBlock.Hinge.RIGHT) {
                i |= 1;
            }

            if (state.get(POWERED)) {
                i |= 2;
            }
        } else {
            i |= state.get(FACING).clockwiseY().getIdHorizontal();
            if (state.get(OPEN)) {
                i |= 4;
            }
        }

        return i;
    }

    protected static int getLowerHalfMetadata(int metadata) {
        return metadata & 7;
    }

    public static boolean isOpen(WorldView world, BlockPos pos) {
        return isOpen(getCombinedMetadata(world, pos));
    }

    public static Direction getFacing(WorldView world, BlockPos pos) {
        return getFacing(getCombinedMetadata(world, pos));
    }

    public static Direction getFacing(int metadata) {
        return Direction.byIdHorizontal(metadata & 3).counterClockwiseY();
    }

    protected static boolean isOpen(int metadata) {
        return (metadata & 4) != 0;
    }

    protected static boolean isUpperHalf(int metadata) {
        return (metadata & 8) != 0;
    }

    protected static boolean isRightHinge(int metadata) {
        return (metadata & 16) != 0;
    }

    @Override
    protected StateDefinition createStateDefinition() {
        return new StateDefinition(this, HALF, FACING, OPEN, HINGE, POWERED);
    }

    public enum Half implements StringSerializable {
        UPPER,
        LOWER;

        @Override
        public String toString() {
            return this.serializeToString();
        }

        @Override
        public String serializeToString() {
            return this == UPPER ? "upper" : "lower";
        }
    }

    public enum Hinge implements StringSerializable {
        LEFT,
        RIGHT;

        @Override
        public String toString() {
            return this.serializeToString();
        }

        @Override
        public String serializeToString() {
            return this == LEFT ? "left" : "right";
        }
    }
}
