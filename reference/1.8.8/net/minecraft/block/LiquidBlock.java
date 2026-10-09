package net.minecraft.block;

import java.util.Random;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.IntegerProperty;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.client.world.color.BiomeColors;
import net.minecraft.entity.Entity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.item.Item;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

public abstract class LiquidBlock extends Block {
    public static final IntegerProperty LEVEL = IntegerProperty.of("level", 0, 15);

    protected LiquidBlock(Material material) {
        super(material);
        this.setDefaultState(this.stateDefinition.any().set(LEVEL, 0));
        this.setShape(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
        this.setTicksRandomly(true);
    }

    @Override
    public boolean canWalkThrough(WorldView world, BlockPos pos) {
        return this.material != Material.LAVA;
    }

    @Override
    public int getColor(WorldView world, BlockPos pos, int tint) {
        return this.material == Material.WATER ? BiomeColors.getWaterFogColor(world, pos) : 16777215;
    }

    /**
     * @return the height loss of a liquid with the given state compared to a full block.
     */
    public static float getHeightLoss(int state) {
        if (state >= 8) {
            state = 0;
        }

        return (state + 1) / 9.0F;
    }

    /**
     * Returns the depth and falling state of the liquid at the given position, encoded in a 4 bit number,
     * or {@code -1} if the given position is not occupied by this liquid.
     * <p>
     * The 3 least significant bits encode the depth of this liquid - that is to say, how shallow it lies within this block.
     * A value of {@code 0} denotes a source block, while the values {@code 1} to {@code 7} denote increasingly shallow depth values.
     * <br>
     * If this liquid is falling, the depth applies not to this liquid, but to the liquid above it.
     * If the liquid above is also falling, the depth is always 0.
     * <p>
     * The 4th least significant bit encodes whether the liquid is falling (i.e. there is liquid flowing in from above).
     */
    protected int getLiquidState(WorldView world, BlockPos pos) {
        return world.getBlockState(pos).getBlock().getMaterial() == this.material ? world.getBlockState(pos).get(LEVEL) : -1;
    }

    /**
     * @return the depth of the liquid at the given position - that is to say, how shallow it lies within this block -
     * or {@code -1} if the given position is not occupied by this liquid.
     */
    protected int getLiquidDepth(WorldView world, BlockPos pos) {
        int i = this.getLiquidState(world, pos);
        return i >= 8 ? 0 : i;
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
    public boolean canRayTrace(BlockState state, boolean allowLiquids) {
        return allowLiquids && state.get(LEVEL) == 0;
    }

    @Override
    public boolean isFaceSolid(WorldView world, BlockPos pos, Direction face) {
        Material material = world.getBlockState(pos).getBlock().getMaterial();
        return material != this.material && (face == Direction.UP || material != Material.ICE && super.isFaceSolid(world, pos, face));
    }

    @Override
    public boolean shouldRenderFace(WorldView world, BlockPos pos, Direction face) {
        return world.getBlockState(pos).getBlock().getMaterial() != this.material && (face == Direction.UP || super.shouldRenderFace(world, pos, face));
    }

    public boolean isNeighboringGap(WorldView world, BlockPos pos) {
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                BlockState blockstate = world.getBlockState(pos.add(i, 0, j));
                Block block = blockstate.getBlock();
                Material material = block.getMaterial();
                if (material != this.material && !block.isOpaque()) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public Box getCollisionShape(World world, BlockPos pos, BlockState state) {
        return null;
    }

    @Override
    public int getRenderType() {
        return 1;
    }

    @Override
    public Item getDropItem(BlockState state, Random random, int fortuneLevel) {
        return null;
    }

    @Override
    public int getBaseDropCount(Random random) {
        return 0;
    }

    protected Vec3d getFlow(WorldView world, BlockPos pos) {
        Vec3d vec3d = new Vec3d(0.0, 0.0, 0.0);
        int i = this.getLiquidDepth(world, pos);

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos blockpos = pos.offset(direction);
            int j = this.getLiquidDepth(world, blockpos);
            if (j < 0) {
                if (!world.getBlockState(blockpos).getBlock().getMaterial().blocksMovement()) {
                    j = this.getLiquidDepth(world, blockpos.down());
                    if (j >= 0) {
                        int k = j - (i - 8);
                        vec3d = vec3d.add((blockpos.getX() - pos.getX()) * k, (blockpos.getY() - pos.getY()) * k, (blockpos.getZ() - pos.getZ()) * k);
                    }
                }
            } else if (j >= 0) {
                int l = j - i;
                vec3d = vec3d.add((blockpos.getX() - pos.getX()) * l, (blockpos.getY() - pos.getY()) * l, (blockpos.getZ() - pos.getZ()) * l);
            }
        }

        if (world.getBlockState(pos).get(LEVEL) >= 8) {
            for (Direction direction1 : Direction.Plane.HORIZONTAL) {
                BlockPos blockpos1 = pos.offset(direction1);
                if (this.isFaceSolid(world, blockpos1, direction1) || this.isFaceSolid(world, blockpos1.up(), direction1)) {
                    vec3d = vec3d.normalize().add(0.0, -6.0, 0.0);
                    break;
                }
            }
        }

        return vec3d.normalize();
    }

    @Override
    public Vec3d applyMaterialDrag(World world, BlockPos pos, Entity entity, Vec3d velocity) {
        return velocity.add(this.getFlow(world, pos));
    }

    @Override
    public int getTickRate(World world) {
        if (this.material == Material.WATER) {
            return 5;
        } else if (this.material == Material.LAVA) {
            return world.dimension.hasNoSky() ? 10 : 30;
        } else {
            return 0;
        }
    }

    @Override
    public int getLightColor(WorldView world, BlockPos pos) {
        int i = world.getLightColor(pos, 0);
        int j = world.getLightColor(pos.up(), 0);
        int k = i & 0xFF;
        int l = j & 0xFF;
        int i1 = i >> 16 & 0xFF;
        int j1 = j >> 16 & 0xFF;
        return (k > l ? k : l) | (i1 > j1 ? i1 : j1) << 16;
    }

    @Override
    public BlockLayer getRenderLayer() {
        return this.material == Material.WATER ? BlockLayer.TRANSLUCENT : BlockLayer.SOLID;
    }

    @Override
    public void randomDisplayTick(World world, BlockPos pos, BlockState state, Random random) {
        double d0 = pos.getX();
        double d1 = pos.getY();
        double d2 = pos.getZ();
        if (this.material == Material.WATER) {
            int i = state.get(LEVEL);
            if (i > 0 && i < 8) {
                if (random.nextInt(64) == 0) {
                    world.playSound(d0 + 0.5, d1 + 0.5, d2 + 0.5, "liquid.water", random.nextFloat() * 0.25F + 0.75F, random.nextFloat() * 1.0F + 0.5F, false);
                }
            } else if (random.nextInt(10) == 0) {
                world.addParticle(ParticleType.SUSPENDED, d0 + random.nextFloat(), d1 + random.nextFloat(), d2 + random.nextFloat(), 0.0, 0.0, 0.0);
            }
        }

        if (this.material == Material.LAVA
            && world.getBlockState(pos.up()).getBlock().getMaterial() == Material.AIR
            && !world.getBlockState(pos.up()).getBlock().isSolidRender()) {
            if (random.nextInt(100) == 0) {
                double d8 = d0 + random.nextFloat();
                double d4 = d1 + this.maxY;
                double d6 = d2 + random.nextFloat();
                world.addParticle(ParticleType.LAVA, d8, d4, d6, 0.0, 0.0, 0.0);
                world.playSound(d8, d4, d6, "liquid.lavapop", 0.2F + random.nextFloat() * 0.2F, 0.9F + random.nextFloat() * 0.15F, false);
            }

            if (random.nextInt(200) == 0) {
                world.playSound(d0, d1, d2, "liquid.lava", 0.2F + random.nextFloat() * 0.2F, 0.9F + random.nextFloat() * 0.15F, false);
            }
        }

        if (random.nextInt(10) == 0 && World.hasSolidTop(world, pos.down())) {
            Material material = world.getBlockState(pos.down(2)).getBlock().getMaterial();
            if (!material.blocksMovement() && !material.isLiquid()) {
                double d3 = d0 + random.nextFloat();
                double d5 = d1 - 1.05;
                double d7 = d2 + random.nextFloat();
                if (this.material == Material.WATER) {
                    world.addParticle(ParticleType.DRIP_WATER, d3, d5, d7, 0.0, 0.0, 0.0);
                } else {
                    world.addParticle(ParticleType.DRIP_LAVA, d3, d5, d7, 0.0, 0.0, 0.0);
                }
            }
        }
    }

    public static double getFlowAngle(WorldView world, BlockPos pos, Material material) {
        Vec3d vec3d = getFlowing(material).getFlow(world, pos);
        return vec3d.x == 0.0 && vec3d.z == 0.0 ? -1000.0 : MathHelper.fastAtan2(vec3d.z, vec3d.x) - (Math.PI / 2);
    }

    @Override
    public void onAdded(World world, BlockPos pos, BlockState state) {
        this.checkSpreadCollisions(world, pos, state);
    }

    @Override
    public void neighborChanged(World world, BlockPos pos, BlockState state, Block neighborBlock) {
        this.checkSpreadCollisions(world, pos, state);
    }

    public boolean checkSpreadCollisions(World world, BlockPos pos, BlockState state) {
        if (this.material == Material.LAVA) {
            boolean flag = false;

            for (Direction direction : Direction.values()) {
                if (direction != Direction.DOWN && world.getBlockState(pos.offset(direction)).getBlock().getMaterial() == Material.WATER) {
                    flag = true;
                    break;
                }
            }

            if (flag) {
                Integer integer = state.get(LEVEL);
                if (integer == 0) {
                    world.setBlockState(pos, Blocks.OBSIDIAN.defaultState());
                    this.fizz(world, pos);
                    return true;
                }

                if (integer <= 4) {
                    world.setBlockState(pos, Blocks.COBBLESTONE.defaultState());
                    this.fizz(world, pos);
                    return true;
                }
            }
        }

        return false;
    }

    protected void fizz(World world, BlockPos pos) {
        double d0 = pos.getX();
        double d1 = pos.getY();
        double d2 = pos.getZ();
        world.playSound(d0 + 0.5, d1 + 0.5, d2 + 0.5, "random.fizz", 0.5F, 2.6F + (world.random.nextFloat() - world.random.nextFloat()) * 0.8F);

        for (int i = 0; i < 8; i++) {
            world.addParticle(ParticleType.SMOKE_LARGE, d0 + Math.random(), d1 + 1.2, d2 + Math.random(), 0.0, 0.0, 0.0);
        }
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        return this.defaultState().set(LEVEL, metadata);
    }

    @Override
    public int getMetadataFromState(BlockState state) {
        return state.get(LEVEL);
    }

    @Override
    protected StateDefinition createStateDefinition() {
        return new StateDefinition(this, LEVEL);
    }

    public static FlowingLiquidBlock getFlowing(Material material) {
        if (material == Material.WATER) {
            return Blocks.FLOWING_WATER;
        } else if (material == Material.LAVA) {
            return Blocks.FLOWING_LAVA;
        } else {
            throw new IllegalArgumentException("Invalid material");
        }
    }

    public static LiquidSourceBlock getSource(Material material) {
        if (material == Material.WATER) {
            return Blocks.WATER;
        } else if (material == Material.LAVA) {
            return Blocks.LAVA;
        } else {
            throw new IllegalArgumentException("Invalid material");
        }
    }
}
