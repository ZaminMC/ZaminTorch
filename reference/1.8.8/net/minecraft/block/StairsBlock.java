package net.minecraft.block;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.DirectionProperty;
import net.minecraft.block.state.property.EnumProperty;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.util.StringSerializable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.HitResult;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import net.minecraft.world.explosion.Explosion;

public class StairsBlock extends Block {
    public static final DirectionProperty FACING = DirectionProperty.of("facing", Direction.Plane.HORIZONTAL);
    public static final EnumProperty<StairsBlock.Half> HALF = EnumProperty.of("half", StairsBlock.Half.class);
    public static final EnumProperty<StairsBlock.Shape> SHAPE = EnumProperty.of("shape", StairsBlock.Shape.class);
    private static final int[][] ROTATIONS = new int[][]{{4, 5}, {5, 7}, {6, 7}, {4, 6}, {0, 1}, {1, 3}, {2, 3}, {0, 2}};
    private final Block baseBlock;
    private final BlockState baseState;
    private boolean isCorner;
    private int stairType;

    protected StairsBlock(BlockState baseState) {
        super(baseState.getBlock().material);
        this.setDefaultState(this.stateDefinition.any().set(FACING, Direction.NORTH).set(HALF, StairsBlock.Half.BOTTOM).set(SHAPE, StairsBlock.Shape.STRAIGHT));
        this.baseBlock = baseState.getBlock();
        this.baseState = baseState;
        this.setStrength(this.baseBlock.miningTime);
        this.setBlastResistance(this.baseBlock.blastResistance / 3.0F);
        this.setSounds(this.baseBlock.sounds);
        this.setOpacity(255);
        this.setCreativeModeTab(CreativeModeTab.BUILDING_BLOCKS);
    }

    @Override
    public void updateShape(WorldView world, BlockPos pos) {
        if (this.isCorner) {
            this.setShape(
                0.5F * (this.stairType % 2),
                0.5F * (this.stairType / 4 % 2),
                0.5F * (this.stairType / 2 % 2),
                0.5F + 0.5F * (this.stairType % 2),
                0.5F + 0.5F * (this.stairType / 4 % 2),
                0.5F + 0.5F * (this.stairType / 2 % 2)
            );
        } else {
            this.setShape(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    @Override
    public boolean isSolidRender() {
        return false;
    }

    @Override
    public boolean isCube() {
        return false;
    }

    public void setBaseShapeForCollisions(WorldView world, BlockPos pos) {
        if (world.getBlockState(pos).get(HALF) == StairsBlock.Half.TOP) {
            this.setShape(0.0F, 0.5F, 0.0F, 1.0F, 1.0F, 1.0F);
        } else {
            this.setShape(0.0F, 0.0F, 0.0F, 1.0F, 0.5F, 1.0F);
        }
    }

    public static boolean isStair(Block block) {
        return block instanceof StairsBlock;
    }

    public static boolean isStairSameHalfSameFacing(WorldView world, BlockPos pos, BlockState state) {
        BlockState blockstate = world.getBlockState(pos);
        Block block = blockstate.getBlock();
        return isStair(block) && blockstate.get(HALF) == state.get(HALF) && blockstate.get(FACING) == state.get(FACING);
    }

    public int getMetadataForOuterStair(WorldView world, BlockPos pos) {
        BlockState blockstate = world.getBlockState(pos);
        Direction direction = blockstate.get(FACING);
        StairsBlock.Half stairsblock$half = blockstate.get(HALF);
        boolean flag = stairsblock$half == StairsBlock.Half.TOP;
        if (direction == Direction.EAST) {
            BlockState blockstate1 = world.getBlockState(pos.east());
            Block block = blockstate1.getBlock();
            if (isStair(block) && stairsblock$half == blockstate1.get(HALF)) {
                Direction direction1 = blockstate1.get(FACING);
                if (direction1 == Direction.NORTH && !isStairSameHalfSameFacing(world, pos.south(), blockstate)) {
                    return flag ? 1 : 2;
                }

                if (direction1 == Direction.SOUTH && !isStairSameHalfSameFacing(world, pos.north(), blockstate)) {
                    return flag ? 2 : 1;
                }
            }
        } else if (direction == Direction.WEST) {
            BlockState blockstate2 = world.getBlockState(pos.west());
            Block block1 = blockstate2.getBlock();
            if (isStair(block1) && stairsblock$half == blockstate2.get(HALF)) {
                Direction direction2 = blockstate2.get(FACING);
                if (direction2 == Direction.NORTH && !isStairSameHalfSameFacing(world, pos.south(), blockstate)) {
                    return flag ? 2 : 1;
                }

                if (direction2 == Direction.SOUTH && !isStairSameHalfSameFacing(world, pos.north(), blockstate)) {
                    return flag ? 1 : 2;
                }
            }
        } else if (direction == Direction.SOUTH) {
            BlockState blockstate3 = world.getBlockState(pos.south());
            Block block2 = blockstate3.getBlock();
            if (isStair(block2) && stairsblock$half == blockstate3.get(HALF)) {
                Direction direction3 = blockstate3.get(FACING);
                if (direction3 == Direction.WEST && !isStairSameHalfSameFacing(world, pos.east(), blockstate)) {
                    return flag ? 2 : 1;
                }

                if (direction3 == Direction.EAST && !isStairSameHalfSameFacing(world, pos.west(), blockstate)) {
                    return flag ? 1 : 2;
                }
            }
        } else if (direction == Direction.NORTH) {
            BlockState blockstate4 = world.getBlockState(pos.north());
            Block block3 = blockstate4.getBlock();
            if (isStair(block3) && stairsblock$half == blockstate4.get(HALF)) {
                Direction direction4 = blockstate4.get(FACING);
                if (direction4 == Direction.WEST && !isStairSameHalfSameFacing(world, pos.east(), blockstate)) {
                    return flag ? 1 : 2;
                }

                if (direction4 == Direction.EAST && !isStairSameHalfSameFacing(world, pos.west(), blockstate)) {
                    return flag ? 2 : 1;
                }
            }
        }

        return 0;
    }

    public int getMetadataForInnerStair(WorldView world, BlockPos pos) {
        BlockState blockstate = world.getBlockState(pos);
        Direction direction = blockstate.get(FACING);
        StairsBlock.Half stairsblock$half = blockstate.get(HALF);
        boolean flag = stairsblock$half == StairsBlock.Half.TOP;
        if (direction == Direction.EAST) {
            BlockState blockstate1 = world.getBlockState(pos.west());
            Block block = blockstate1.getBlock();
            if (isStair(block) && stairsblock$half == blockstate1.get(HALF)) {
                Direction direction1 = blockstate1.get(FACING);
                if (direction1 == Direction.NORTH && !isStairSameHalfSameFacing(world, pos.north(), blockstate)) {
                    return flag ? 1 : 2;
                }

                if (direction1 == Direction.SOUTH && !isStairSameHalfSameFacing(world, pos.south(), blockstate)) {
                    return flag ? 2 : 1;
                }
            }
        } else if (direction == Direction.WEST) {
            BlockState blockstate2 = world.getBlockState(pos.east());
            Block block1 = blockstate2.getBlock();
            if (isStair(block1) && stairsblock$half == blockstate2.get(HALF)) {
                Direction direction2 = blockstate2.get(FACING);
                if (direction2 == Direction.NORTH && !isStairSameHalfSameFacing(world, pos.north(), blockstate)) {
                    return flag ? 2 : 1;
                }

                if (direction2 == Direction.SOUTH && !isStairSameHalfSameFacing(world, pos.south(), blockstate)) {
                    return flag ? 1 : 2;
                }
            }
        } else if (direction == Direction.SOUTH) {
            BlockState blockstate3 = world.getBlockState(pos.north());
            Block block2 = blockstate3.getBlock();
            if (isStair(block2) && stairsblock$half == blockstate3.get(HALF)) {
                Direction direction3 = blockstate3.get(FACING);
                if (direction3 == Direction.WEST && !isStairSameHalfSameFacing(world, pos.west(), blockstate)) {
                    return flag ? 2 : 1;
                }

                if (direction3 == Direction.EAST && !isStairSameHalfSameFacing(world, pos.east(), blockstate)) {
                    return flag ? 1 : 2;
                }
            }
        } else if (direction == Direction.NORTH) {
            BlockState blockstate4 = world.getBlockState(pos.south());
            Block block3 = blockstate4.getBlock();
            if (isStair(block3) && stairsblock$half == blockstate4.get(HALF)) {
                Direction direction4 = blockstate4.get(FACING);
                if (direction4 == Direction.WEST && !isStairSameHalfSameFacing(world, pos.west(), blockstate)) {
                    return flag ? 1 : 2;
                }

                if (direction4 == Direction.EAST && !isStairSameHalfSameFacing(world, pos.east(), blockstate)) {
                    return flag ? 2 : 1;
                }
            }
        }

        return 0;
    }

    public boolean isInnerStair(WorldView world, BlockPos pos) {
        BlockState blockstate = world.getBlockState(pos);
        Direction direction = blockstate.get(FACING);
        StairsBlock.Half stairsblock$half = blockstate.get(HALF);
        boolean flag = stairsblock$half == StairsBlock.Half.TOP;
        float f = 0.5F;
        float f1 = 1.0F;
        if (flag) {
            f = 0.0F;
            f1 = 0.5F;
        }

        float f2 = 0.0F;
        float f3 = 1.0F;
        float f4 = 0.0F;
        float f5 = 0.5F;
        boolean flag1 = true;
        if (direction == Direction.EAST) {
            f2 = 0.5F;
            f5 = 1.0F;
            BlockState blockstate1 = world.getBlockState(pos.east());
            Block block = blockstate1.getBlock();
            if (isStair(block) && stairsblock$half == blockstate1.get(HALF)) {
                Direction direction1 = blockstate1.get(FACING);
                if (direction1 == Direction.NORTH && !isStairSameHalfSameFacing(world, pos.south(), blockstate)) {
                    f5 = 0.5F;
                    flag1 = false;
                } else if (direction1 == Direction.SOUTH && !isStairSameHalfSameFacing(world, pos.north(), blockstate)) {
                    f4 = 0.5F;
                    flag1 = false;
                }
            }
        } else if (direction == Direction.WEST) {
            f3 = 0.5F;
            f5 = 1.0F;
            BlockState blockstate2 = world.getBlockState(pos.west());
            Block block1 = blockstate2.getBlock();
            if (isStair(block1) && stairsblock$half == blockstate2.get(HALF)) {
                Direction direction2 = blockstate2.get(FACING);
                if (direction2 == Direction.NORTH && !isStairSameHalfSameFacing(world, pos.south(), blockstate)) {
                    f5 = 0.5F;
                    flag1 = false;
                } else if (direction2 == Direction.SOUTH && !isStairSameHalfSameFacing(world, pos.north(), blockstate)) {
                    f4 = 0.5F;
                    flag1 = false;
                }
            }
        } else if (direction == Direction.SOUTH) {
            f4 = 0.5F;
            f5 = 1.0F;
            BlockState blockstate3 = world.getBlockState(pos.south());
            Block block2 = blockstate3.getBlock();
            if (isStair(block2) && stairsblock$half == blockstate3.get(HALF)) {
                Direction direction3 = blockstate3.get(FACING);
                if (direction3 == Direction.WEST && !isStairSameHalfSameFacing(world, pos.east(), blockstate)) {
                    f3 = 0.5F;
                    flag1 = false;
                } else if (direction3 == Direction.EAST && !isStairSameHalfSameFacing(world, pos.west(), blockstate)) {
                    f2 = 0.5F;
                    flag1 = false;
                }
            }
        } else if (direction == Direction.NORTH) {
            BlockState blockstate4 = world.getBlockState(pos.north());
            Block block3 = blockstate4.getBlock();
            if (isStair(block3) && stairsblock$half == blockstate4.get(HALF)) {
                Direction direction4 = blockstate4.get(FACING);
                if (direction4 == Direction.WEST && !isStairSameHalfSameFacing(world, pos.east(), blockstate)) {
                    f3 = 0.5F;
                    flag1 = false;
                } else if (direction4 == Direction.EAST && !isStairSameHalfSameFacing(world, pos.west(), blockstate)) {
                    f2 = 0.5F;
                    flag1 = false;
                }
            }
        }

        this.setShape(f2, f, f4, f3, f1, f5);
        return flag1;
    }

    public boolean setStepShapeForCollisions(WorldView world, BlockPos pos) {
        BlockState blockstate = world.getBlockState(pos);
        Direction direction = blockstate.get(FACING);
        StairsBlock.Half stairsblock$half = blockstate.get(HALF);
        boolean flag = stairsblock$half == StairsBlock.Half.TOP;
        float f = 0.5F;
        float f1 = 1.0F;
        if (flag) {
            f = 0.0F;
            f1 = 0.5F;
        }

        float f2 = 0.0F;
        float f3 = 0.5F;
        float f4 = 0.5F;
        float f5 = 1.0F;
        boolean flag1 = false;
        if (direction == Direction.EAST) {
            BlockState blockstate1 = world.getBlockState(pos.west());
            Block block = blockstate1.getBlock();
            if (isStair(block) && stairsblock$half == blockstate1.get(HALF)) {
                Direction direction1 = blockstate1.get(FACING);
                if (direction1 == Direction.NORTH && !isStairSameHalfSameFacing(world, pos.north(), blockstate)) {
                    f4 = 0.0F;
                    f5 = 0.5F;
                    flag1 = true;
                } else if (direction1 == Direction.SOUTH && !isStairSameHalfSameFacing(world, pos.south(), blockstate)) {
                    f4 = 0.5F;
                    f5 = 1.0F;
                    flag1 = true;
                }
            }
        } else if (direction == Direction.WEST) {
            BlockState blockstate2 = world.getBlockState(pos.east());
            Block block1 = blockstate2.getBlock();
            if (isStair(block1) && stairsblock$half == blockstate2.get(HALF)) {
                f2 = 0.5F;
                f3 = 1.0F;
                Direction direction2 = blockstate2.get(FACING);
                if (direction2 == Direction.NORTH && !isStairSameHalfSameFacing(world, pos.north(), blockstate)) {
                    f4 = 0.0F;
                    f5 = 0.5F;
                    flag1 = true;
                } else if (direction2 == Direction.SOUTH && !isStairSameHalfSameFacing(world, pos.south(), blockstate)) {
                    f4 = 0.5F;
                    f5 = 1.0F;
                    flag1 = true;
                }
            }
        } else if (direction == Direction.SOUTH) {
            BlockState blockstate3 = world.getBlockState(pos.north());
            Block block2 = blockstate3.getBlock();
            if (isStair(block2) && stairsblock$half == blockstate3.get(HALF)) {
                f4 = 0.0F;
                f5 = 0.5F;
                Direction direction3 = blockstate3.get(FACING);
                if (direction3 == Direction.WEST && !isStairSameHalfSameFacing(world, pos.west(), blockstate)) {
                    flag1 = true;
                } else if (direction3 == Direction.EAST && !isStairSameHalfSameFacing(world, pos.east(), blockstate)) {
                    f2 = 0.5F;
                    f3 = 1.0F;
                    flag1 = true;
                }
            }
        } else if (direction == Direction.NORTH) {
            BlockState blockstate4 = world.getBlockState(pos.south());
            Block block3 = blockstate4.getBlock();
            if (isStair(block3) && stairsblock$half == blockstate4.get(HALF)) {
                Direction direction4 = blockstate4.get(FACING);
                if (direction4 == Direction.WEST && !isStairSameHalfSameFacing(world, pos.west(), blockstate)) {
                    flag1 = true;
                } else if (direction4 == Direction.EAST && !isStairSameHalfSameFacing(world, pos.east(), blockstate)) {
                    f2 = 0.5F;
                    f3 = 1.0F;
                    flag1 = true;
                }
            }
        }

        if (flag1) {
            this.setShape(f2, f, f4, f3, f1, f5);
        }

        return flag1;
    }

    @Override
    public void addCollisions(World world, BlockPos pos, BlockState state, Box shape, List<Box> collisions, Entity entity) {
        this.setBaseShapeForCollisions(world, pos);
        super.addCollisions(world, pos, state, shape, collisions, entity);
        boolean flag = this.isInnerStair(world, pos);
        super.addCollisions(world, pos, state, shape, collisions, entity);
        if (flag && this.setStepShapeForCollisions(world, pos)) {
            super.addCollisions(world, pos, state, shape, collisions, entity);
        }

        this.setShape(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    public void randomDisplayTick(World world, BlockPos pos, BlockState state, Random random) {
        this.baseBlock.randomDisplayTick(world, pos, state, random);
    }

    @Override
    public void startMining(World world, BlockPos pos, PlayerEntity player) {
        this.baseBlock.startMining(world, pos, player);
    }

    @Override
    public void onBroken(World world, BlockPos pos, BlockState state) {
        this.baseBlock.onBroken(world, pos, state);
    }

    @Override
    public int getLightColor(WorldView world, BlockPos pos) {
        return this.baseBlock.getLightColor(world, pos);
    }

    @Override
    public float getBlastResistance(Entity entity) {
        return this.baseBlock.getBlastResistance(entity);
    }

    @Override
    public BlockLayer getRenderLayer() {
        return this.baseBlock.getRenderLayer();
    }

    @Override
    public int getTickRate(World world) {
        return this.baseBlock.getTickRate(world);
    }

    @Override
    public Box getOutlineShape(World world, BlockPos pos) {
        return this.baseBlock.getOutlineShape(world, pos);
    }

    @Override
    public Vec3d applyMaterialDrag(World world, BlockPos pos, Entity entity, Vec3d velocity) {
        return this.baseBlock.applyMaterialDrag(world, pos, entity, velocity);
    }

    @Override
    public boolean canRayTrace() {
        return this.baseBlock.canRayTrace();
    }

    @Override
    public boolean canRayTrace(BlockState state, boolean allowLiquids) {
        return this.baseBlock.canRayTrace(state, allowLiquids);
    }

    @Override
    public boolean canBePlaced(World world, BlockPos pos) {
        return this.baseBlock.canBePlaced(world, pos);
    }

    @Override
    public void onAdded(World world, BlockPos pos, BlockState state) {
        this.neighborChanged(world, pos, this.baseState, Blocks.AIR);
        this.baseBlock.onAdded(world, pos, this.baseState);
    }

    @Override
    public void onRemoved(World world, BlockPos pos, BlockState state) {
        this.baseBlock.onRemoved(world, pos, this.baseState);
    }

    @Override
    public void onSteppedOn(World world, BlockPos pos, Entity entity) {
        this.baseBlock.onSteppedOn(world, pos, entity);
    }

    @Override
    public void tick(World world, BlockPos pos, BlockState state, Random random) {
        this.baseBlock.tick(world, pos, state, random);
    }

    @Override
    public boolean use(World world, BlockPos pos, BlockState state, PlayerEntity player, Direction face, float faceX, float faceY, float faceZ) {
        return this.baseBlock.use(world, pos, this.baseState, player, Direction.DOWN, 0.0F, 0.0F, 0.0F);
    }

    @Override
    public void onExploded(World world, BlockPos pos, Explosion explosion) {
        this.baseBlock.onExploded(world, pos, explosion);
    }

    @Override
    public MapColor getMapColor(BlockState state) {
        return this.baseBlock.getMapColor(this.baseState);
    }

    @Override
    public BlockState getPlacementState(World world, BlockPos pos, Direction dir, float dx, float dy, float dz, int metadata, LivingEntity entity) {
        BlockState blockstate = super.getPlacementState(world, pos, dir, dx, dy, dz, metadata, entity);
        blockstate = blockstate.set(FACING, entity.getHorizontalFacing()).set(SHAPE, StairsBlock.Shape.STRAIGHT);
        return dir != Direction.DOWN && (dir == Direction.UP || !(dy > 0.5))
            ? blockstate.set(HALF, StairsBlock.Half.BOTTOM)
            : blockstate.set(HALF, StairsBlock.Half.TOP);
    }

    @Override
    public HitResult rayTrace(World world, BlockPos pos, Vec3d start, Vec3d end) {
        HitResult[] ahitresult = new HitResult[8];
        BlockState blockstate = world.getBlockState(pos);
        int i = blockstate.get(FACING).getIdHorizontal();
        boolean flag = blockstate.get(HALF) == StairsBlock.Half.TOP;
        int[] aint = ROTATIONS[i + (flag ? 4 : 0)];
        this.isCorner = true;

        for (int j = 0; j < 8; j++) {
            this.stairType = j;
            if (Arrays.binarySearch(aint, j) < 0) {
                ahitresult[j] = super.rayTrace(world, pos, start, end);
            }
        }

        for (int k : aint) {
            ahitresult[k] = null;
        }

        HitResult hitresult1 = null;
        double d1 = 0.0;

        for (HitResult hitresult : ahitresult) {
            if (hitresult != null) {
                double d0 = hitresult.facePos.squaredDistanceTo(end);
                if (d0 > d1) {
                    hitresult1 = hitresult;
                    d1 = d0;
                }
            }
        }

        return hitresult1;
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        BlockState blockstate = this.defaultState().set(HALF, (metadata & 4) > 0 ? StairsBlock.Half.TOP : StairsBlock.Half.BOTTOM);
        return blockstate.set(FACING, Direction.byId(5 - (metadata & 3)));
    }

    @Override
    public int getMetadataFromState(BlockState state) {
        int i = 0;
        if (state.get(HALF) == StairsBlock.Half.TOP) {
            i |= 4;
        }

        return i | 5 - state.get(FACING).getId();
    }

    @Override
    public BlockState resolveVirtualProperties(BlockState state, WorldView world, BlockPos pos) {
        if (this.isInnerStair(world, pos)) {
            switch (this.getMetadataForInnerStair(world, pos)) {
                case 0:
                    state = state.set(SHAPE, StairsBlock.Shape.STRAIGHT);
                    break;
                case 1:
                    state = state.set(SHAPE, StairsBlock.Shape.INNER_RIGHT);
                    break;
                case 2:
                    state = state.set(SHAPE, StairsBlock.Shape.INNER_LEFT);
            }
        } else {
            switch (this.getMetadataForOuterStair(world, pos)) {
                case 0:
                    state = state.set(SHAPE, StairsBlock.Shape.STRAIGHT);
                    break;
                case 1:
                    state = state.set(SHAPE, StairsBlock.Shape.OUTER_RIGHT);
                    break;
                case 2:
                    state = state.set(SHAPE, StairsBlock.Shape.OUTER_LEFT);
            }
        }

        return state;
    }

    @Override
    protected StateDefinition createStateDefinition() {
        return new StateDefinition(this, FACING, HALF, SHAPE);
    }

    public enum Half implements StringSerializable {
        TOP("top"),
        BOTTOM("bottom");

        private final String key;

        Half(String key) {
            this.key = key;
        }

        @Override
        public String toString() {
            return this.key;
        }

        @Override
        public String serializeToString() {
            return this.key;
        }
    }

    public enum Shape implements StringSerializable {
        STRAIGHT("straight"),
        INNER_LEFT("inner_left"),
        INNER_RIGHT("inner_right"),
        OUTER_LEFT("outer_left"),
        OUTER_RIGHT("outer_right");

        private final String key;

        Shape(String key) {
            this.key = key;
        }

        @Override
        public String toString() {
            return this.key;
        }

        @Override
        public String serializeToString() {
            return this.key;
        }
    }
}
