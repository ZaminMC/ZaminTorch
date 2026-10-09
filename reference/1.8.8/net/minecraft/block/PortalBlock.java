package net.minecraft.block;

import com.google.common.cache.LoadingCache;
import java.util.Random;
import net.minecraft.block.material.Material;
import net.minecraft.block.pattern.BlockPattern;
import net.minecraft.block.pattern.BlockPointer;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.EnumProperty;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.item.Item;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

public class PortalBlock extends TransparentBlock {
    public static final EnumProperty<Direction.Axis> AXIS = EnumProperty.of("axis", Direction.Axis.class, Direction.Axis.X, Direction.Axis.Z);

    public PortalBlock() {
        super(Material.PORTAL, false);
        this.setDefaultState(this.stateDefinition.any().set(AXIS, Direction.Axis.X));
        this.setTicksRandomly(true);
    }

    @Override
    public void tick(World world, BlockPos pos, BlockState state, Random random) {
        super.tick(world, pos, state, random);
        if (world.dimension.isNatural() && world.getGameRules().getBoolean("doMobSpawning") && random.nextInt(2000) < world.getDifficulty().getId()) {
            int i = pos.getY();
            BlockPos blockpos = pos;

            while (!World.hasSolidTop(world, blockpos) && blockpos.getY() > 0) {
                blockpos = blockpos.down();
            }

            if (i > 0 && !world.getBlockState(blockpos.up()).getBlock().isSolid()) {
                Entity entity = SpawnEggItem.spawnEntity(world, 57, blockpos.getX() + 0.5, blockpos.getY() + 1.1, blockpos.getZ() + 0.5);
                if (entity != null) {
                    entity.portalCooldown = entity.getPortalCooldown();
                }
            }
        }
    }

    @Override
    public Box getCollisionShape(World world, BlockPos pos, BlockState state) {
        return null;
    }

    @Override
    public void updateShape(WorldView world, BlockPos pos) {
        Direction.Axis direction$axis = world.getBlockState(pos).get(AXIS);
        float f = 0.125F;
        float f1 = 0.125F;
        if (direction$axis == Direction.Axis.X) {
            f = 0.5F;
        }

        if (direction$axis == Direction.Axis.Z) {
            f1 = 0.5F;
        }

        this.setShape(0.5F - f, 0.0F, 0.5F - f1, 0.5F + f, 1.0F, 0.5F + f1);
    }

    public static int getMetadata(Direction.Axis axis) {
        if (axis == Direction.Axis.X) {
            return 1;
        } else {
            return axis == Direction.Axis.Z ? 2 : 0;
        }
    }

    @Override
    public boolean isCube() {
        return false;
    }

    public boolean create(World world, BlockPos pos) {
        PortalBlock.PortalBuilder portalblock$portalbuilder = new PortalBlock.PortalBuilder(world, pos, Direction.Axis.X);
        if (portalblock$portalbuilder.isValid() && portalblock$portalbuilder.foundPortalBlocks == 0) {
            portalblock$portalbuilder.build();
            return true;
        } else {
            PortalBlock.PortalBuilder portalblock$portalbuilder1 = new PortalBlock.PortalBuilder(world, pos, Direction.Axis.Z);
            if (portalblock$portalbuilder1.isValid() && portalblock$portalbuilder1.foundPortalBlocks == 0) {
                portalblock$portalbuilder1.build();
                return true;
            } else {
                return false;
            }
        }
    }

    @Override
    public void neighborChanged(World world, BlockPos pos, BlockState state, Block neighborBlock) {
        Direction.Axis direction$axis = state.get(AXIS);
        if (direction$axis == Direction.Axis.X) {
            PortalBlock.PortalBuilder portalblock$portalbuilder = new PortalBlock.PortalBuilder(world, pos, Direction.Axis.X);
            if (!portalblock$portalbuilder.isValid()
                || portalblock$portalbuilder.foundPortalBlocks < portalblock$portalbuilder.width * portalblock$portalbuilder.height) {
                world.setBlockState(pos, Blocks.AIR.defaultState());
            }
        } else if (direction$axis == Direction.Axis.Z) {
            PortalBlock.PortalBuilder portalblock$portalbuilder1 = new PortalBlock.PortalBuilder(world, pos, Direction.Axis.Z);
            if (!portalblock$portalbuilder1.isValid()
                || portalblock$portalbuilder1.foundPortalBlocks < portalblock$portalbuilder1.width * portalblock$portalbuilder1.height) {
                world.setBlockState(pos, Blocks.AIR.defaultState());
            }
        }
    }

    @Override
    public boolean shouldRenderFace(WorldView world, BlockPos pos, Direction face) {
        Direction.Axis direction$axis = null;
        BlockState blockstate = world.getBlockState(pos);
        if (world.getBlockState(pos).getBlock() == this) {
            direction$axis = blockstate.get(AXIS);
            if (direction$axis == null) {
                return false;
            }

            if (direction$axis == Direction.Axis.Z && face != Direction.EAST && face != Direction.WEST) {
                return false;
            }

            if (direction$axis == Direction.Axis.X && face != Direction.SOUTH && face != Direction.NORTH) {
                return false;
            }
        }

        boolean flag = world.getBlockState(pos.west()).getBlock() == this && world.getBlockState(pos.west(2)).getBlock() != this;
        boolean flag1 = world.getBlockState(pos.east()).getBlock() == this && world.getBlockState(pos.east(2)).getBlock() != this;
        boolean flag2 = world.getBlockState(pos.north()).getBlock() == this && world.getBlockState(pos.north(2)).getBlock() != this;
        boolean flag3 = world.getBlockState(pos.south()).getBlock() == this && world.getBlockState(pos.south(2)).getBlock() != this;
        boolean flag4 = flag || flag1 || direction$axis == Direction.Axis.X;
        boolean flag5 = flag2 || flag3 || direction$axis == Direction.Axis.Z;
        return flag4 && face == Direction.WEST || flag4 && face == Direction.EAST || flag5 && face == Direction.NORTH || flag5 && face == Direction.SOUTH;
    }

    @Override
    public int getBaseDropCount(Random random) {
        return 0;
    }

    @Override
    public BlockLayer getRenderLayer() {
        return BlockLayer.TRANSLUCENT;
    }

    @Override
    public void onEntityCollision(World world, BlockPos pos, BlockState state, Entity entity) {
        if (entity.vehicle == null && entity.rider == null) {
            entity.onPortalCollision(pos);
        }
    }

    @Override
    public void randomDisplayTick(World world, BlockPos pos, BlockState state, Random random) {
        if (random.nextInt(100) == 0) {
            world.playSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, "portal.portal", 0.5F, random.nextFloat() * 0.4F + 0.8F, false);
        }

        for (int i = 0; i < 4; i++) {
            double d0 = pos.getX() + random.nextFloat();
            double d1 = pos.getY() + random.nextFloat();
            double d2 = pos.getZ() + random.nextFloat();
            double d3 = (random.nextFloat() - 0.5) * 0.5;
            double d4 = (random.nextFloat() - 0.5) * 0.5;
            double d5 = (random.nextFloat() - 0.5) * 0.5;
            int j = random.nextInt(2) * 2 - 1;
            if (world.getBlockState(pos.west()).getBlock() != this && world.getBlockState(pos.east()).getBlock() != this) {
                d0 = pos.getX() + 0.5 + 0.25 * j;
                d3 = random.nextFloat() * 2.0F * j;
            } else {
                d2 = pos.getZ() + 0.5 + 0.25 * j;
                d5 = random.nextFloat() * 2.0F * j;
            }

            world.addParticle(ParticleType.PORTAL, d0, d1, d2, d3, d4, d5);
        }
    }

    @Override
    public Item getPickItem(World world, BlockPos pos) {
        return null;
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        return this.defaultState().set(AXIS, (metadata & 3) == 2 ? Direction.Axis.Z : Direction.Axis.X);
    }

    @Override
    public int getMetadataFromState(BlockState state) {
        return getMetadata(state.get(AXIS));
    }

    @Override
    protected StateDefinition createStateDefinition() {
        return new StateDefinition(this, AXIS);
    }

    public BlockPattern.Match findPortalShape(World world, BlockPos pos) {
        Direction.Axis direction$axis = Direction.Axis.Z;
        PortalBlock.PortalBuilder portalblock$portalbuilder = new PortalBlock.PortalBuilder(world, pos, Direction.Axis.X);
        LoadingCache<BlockPos, BlockPointer> loadingcache = BlockPattern.createBlockCache(world, true);
        if (!portalblock$portalbuilder.isValid()) {
            direction$axis = Direction.Axis.X;
            portalblock$portalbuilder = new PortalBlock.PortalBuilder(world, pos, Direction.Axis.Z);
        }

        if (!portalblock$portalbuilder.isValid()) {
            return new BlockPattern.Match(pos, Direction.NORTH, Direction.UP, loadingcache, 1, 1, 1);
        }

        int[] aint = new int[Direction.AxisDirection.values().length];
        Direction direction = portalblock$portalbuilder.right.counterClockwiseY();
        BlockPos blockpos = portalblock$portalbuilder.bottomLeft.up(portalblock$portalbuilder.getHeight() - 1);

        for (Direction.AxisDirection direction$axisdirection : Direction.AxisDirection.values()) {
            BlockPattern.Match blockpattern$match = new BlockPattern.Match(
                direction.getAxisDirection() == direction$axisdirection
                    ? blockpos
                    : blockpos.offset(portalblock$portalbuilder.right, portalblock$portalbuilder.getWidth() - 1),
                Direction.of(direction$axisdirection, direction$axis),
                Direction.UP,
                loadingcache,
                portalblock$portalbuilder.getWidth(),
                portalblock$portalbuilder.getHeight(),
                1
            );

            for (int i = 0; i < portalblock$portalbuilder.getWidth(); i++) {
                for (int j = 0; j < portalblock$portalbuilder.getHeight(); j++) {
                    BlockPointer blockpointer = blockpattern$match.getBlock(i, j, 1);
                    if (blockpointer.getState() != null && blockpointer.getState().getBlock().getMaterial() != Material.AIR) {
                        aint[direction$axisdirection.ordinal()]++;
                    }
                }
            }
        }

        Direction.AxisDirection direction$axisdirection1 = Direction.AxisDirection.POSITIVE;

        for (Direction.AxisDirection direction$axisdirection2 : Direction.AxisDirection.values()) {
            if (aint[direction$axisdirection2.ordinal()] < aint[direction$axisdirection1.ordinal()]) {
                direction$axisdirection1 = direction$axisdirection2;
            }
        }

        return new BlockPattern.Match(
            direction.getAxisDirection() == direction$axisdirection1
                ? blockpos
                : blockpos.offset(portalblock$portalbuilder.right, portalblock$portalbuilder.getWidth() - 1),
            Direction.of(direction$axisdirection1, direction$axis),
            Direction.UP,
            loadingcache,
            portalblock$portalbuilder.getWidth(),
            portalblock$portalbuilder.getHeight(),
            1
        );
    }

    public static class PortalBuilder {
        private final World world;
        private final Direction.Axis axis;
        private final Direction right;
        private final Direction left;
        private int foundPortalBlocks = 0;
        private BlockPos bottomLeft;
        private int height;
        private int width;

        public PortalBuilder(World world, BlockPos pos, Direction.Axis axis) {
            this.world = world;
            this.axis = axis;
            if (axis == Direction.Axis.X) {
                this.left = Direction.EAST;
                this.right = Direction.WEST;
            } else {
                this.left = Direction.NORTH;
                this.right = Direction.SOUTH;
            }

            BlockPos blockpos = pos;

            while (pos.getY() > blockpos.getY() - 21 && pos.getY() > 0 && this.canBeReplacedByPortal(world.getBlockState(pos.down()).getBlock())) {
                pos = pos.down();
            }

            int i = this.findWidth(pos, this.left) - 1;
            if (i >= 0) {
                this.bottomLeft = pos.offset(this.left, i);
                this.width = this.findWidth(this.bottomLeft, this.right);
                if (this.width < 2 || this.width > 21) {
                    this.bottomLeft = null;
                    this.width = 0;
                }
            }

            if (this.bottomLeft != null) {
                this.height = this.findHeight();
            }
        }

        protected int findWidth(BlockPos pos, Direction dir) {
            int i;
            for (i = 0; i < 22; i++) {
                BlockPos blockpos = pos.offset(dir, i);
                if (!this.canBeReplacedByPortal(this.world.getBlockState(blockpos).getBlock())
                    || this.world.getBlockState(blockpos.down()).getBlock() != Blocks.OBSIDIAN) {
                    break;
                }
            }

            Block block = this.world.getBlockState(pos.offset(dir, i)).getBlock();
            return block == Blocks.OBSIDIAN ? i : 0;
        }

        public int getHeight() {
            return this.height;
        }

        public int getWidth() {
            return this.width;
        }

        protected int findHeight() {
            label56:
            for (this.height = 0; this.height < 21; this.height++) {
                for (int i = 0; i < this.width; i++) {
                    BlockPos blockpos = this.bottomLeft.offset(this.right, i).up(this.height);
                    Block block = this.world.getBlockState(blockpos).getBlock();
                    if (!this.canBeReplacedByPortal(block)) {
                        break label56;
                    }

                    if (block == Blocks.NETHER_PORTAL) {
                        this.foundPortalBlocks++;
                    }

                    if (i == 0) {
                        block = this.world.getBlockState(blockpos.offset(this.left)).getBlock();
                        if (block != Blocks.OBSIDIAN) {
                            break label56;
                        }
                    } else if (i == this.width - 1) {
                        block = this.world.getBlockState(blockpos.offset(this.right)).getBlock();
                        if (block != Blocks.OBSIDIAN) {
                            break label56;
                        }
                    }
                }
            }

            for (int j = 0; j < this.width; j++) {
                if (this.world.getBlockState(this.bottomLeft.offset(this.right, j).up(this.height)).getBlock() != Blocks.OBSIDIAN) {
                    this.height = 0;
                    break;
                }
            }

            if (this.height <= 21 && this.height >= 3) {
                return this.height;
            }

            this.bottomLeft = null;
            this.width = 0;
            this.height = 0;
            return 0;
        }

        protected boolean canBeReplacedByPortal(Block block) {
            return block.material == Material.AIR || block == Blocks.FIRE || block == Blocks.NETHER_PORTAL;
        }

        public boolean isValid() {
            return this.bottomLeft != null && this.width >= 2 && this.width <= 21 && this.height >= 3 && this.height <= 21;
        }

        public void build() {
            for (int i = 0; i < this.width; i++) {
                BlockPos blockpos = this.bottomLeft.offset(this.right, i);

                for (int j = 0; j < this.height; j++) {
                    this.world.setBlockState(blockpos.up(j), Blocks.NETHER_PORTAL.defaultState().set(PortalBlock.AXIS, this.axis), 2);
                }
            }
        }
    }
}
