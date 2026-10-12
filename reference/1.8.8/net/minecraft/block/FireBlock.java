package net.minecraft.block;

import com.google.common.collect.Maps;
import java.util.Map;
import java.util.Random;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.BooleanProperty;
import net.minecraft.block.state.property.IntegerProperty;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import net.minecraft.world.dimension.TheEndDimension;

public class FireBlock extends Block {
    public static final IntegerProperty AGE = IntegerProperty.of("age", 0, 15);
    public static final BooleanProperty FLIP = BooleanProperty.of("flip");
    public static final BooleanProperty ALT = BooleanProperty.of("alt");
    public static final BooleanProperty NORTH = BooleanProperty.of("north");
    public static final BooleanProperty EAST = BooleanProperty.of("east");
    public static final BooleanProperty SOUTH = BooleanProperty.of("south");
    public static final BooleanProperty WEST = BooleanProperty.of("west");
    public static final IntegerProperty UPPER = IntegerProperty.of("upper", 0, 2);
    private final Map<Block, Integer> flammability = Maps.newIdentityHashMap();
    private final Map<Block, Integer> burnChance = Maps.newIdentityHashMap();

    @Override
    public BlockState resolveVirtualProperties(BlockState state, WorldView world, BlockPos pos) {
        int i = pos.getX();
        int j = pos.getY();
        int k = pos.getZ();
        if (!World.hasSolidTop(world, pos.down()) && !Blocks.FIRE.isFlammable(world, pos.down())) {
            boolean flag = (i + j + k & 1) == 1;
            boolean flag1 = (i / 2 + j / 2 + k / 2 & 1) == 1;
            int l = 0;
            if (this.isFlammable(world, pos.up())) {
                l = flag ? 1 : 2;
            }

            return state.set(NORTH, this.isFlammable(world, pos.north()))
                .set(EAST, this.isFlammable(world, pos.east()))
                .set(SOUTH, this.isFlammable(world, pos.south()))
                .set(WEST, this.isFlammable(world, pos.west()))
                .set(UPPER, l)
                .set(FLIP, flag1)
                .set(ALT, flag);
        } else {
            return this.defaultState();
        }
    }

    protected FireBlock() {
        super(Material.FIRE);
        this.setDefaultState(
            this.stateDefinition
                .any()
                .set(AGE, 0)
                .set(FLIP, false)
                .set(ALT, false)
                .set(NORTH, false)
                .set(EAST, false)
                .set(SOUTH, false)
                .set(WEST, false)
                .set(UPPER, 0)
        );
        this.setTicksRandomly(true);
    }

    public static void init() {
        Blocks.FIRE.setFlammable(Blocks.PLANKS, 5, 20);
        Blocks.FIRE.setFlammable(Blocks.DOUBLE_WOODEN_SLAB, 5, 20);
        Blocks.FIRE.setFlammable(Blocks.WOODEN_SLAB, 5, 20);
        Blocks.FIRE.setFlammable(Blocks.FENCE_GATE, 5, 20);
        Blocks.FIRE.setFlammable(Blocks.SPRUCE_FENCE_GATE, 5, 20);
        Blocks.FIRE.setFlammable(Blocks.BIRCH_FENCE_GATE, 5, 20);
        Blocks.FIRE.setFlammable(Blocks.JUNGLE_FENCE_GATE, 5, 20);
        Blocks.FIRE.setFlammable(Blocks.DARK_OAK_FENCE_GATE, 5, 20);
        Blocks.FIRE.setFlammable(Blocks.ACACIA_FENCE_GATE, 5, 20);
        Blocks.FIRE.setFlammable(Blocks.FENCE, 5, 20);
        Blocks.FIRE.setFlammable(Blocks.SPRUCE_FENCE, 5, 20);
        Blocks.FIRE.setFlammable(Blocks.BIRCH_FENCE, 5, 20);
        Blocks.FIRE.setFlammable(Blocks.JUNGLE_FENCE, 5, 20);
        Blocks.FIRE.setFlammable(Blocks.DARK_OAK_FENCE, 5, 20);
        Blocks.FIRE.setFlammable(Blocks.ACACIA_FENCE, 5, 20);
        Blocks.FIRE.setFlammable(Blocks.OAK_STAIRS, 5, 20);
        Blocks.FIRE.setFlammable(Blocks.BIRCH_STAIRS, 5, 20);
        Blocks.FIRE.setFlammable(Blocks.SPRUCE_STAIRS, 5, 20);
        Blocks.FIRE.setFlammable(Blocks.JUNGLE_STAIRS, 5, 20);
        Blocks.FIRE.setFlammable(Blocks.LOG, 5, 5);
        Blocks.FIRE.setFlammable(Blocks.LOG2, 5, 5);
        Blocks.FIRE.setFlammable(Blocks.LEAVES, 30, 60);
        Blocks.FIRE.setFlammable(Blocks.LEAVES2, 30, 60);
        Blocks.FIRE.setFlammable(Blocks.BOOKSHELF, 30, 20);
        Blocks.FIRE.setFlammable(Blocks.TNT, 15, 100);
        Blocks.FIRE.setFlammable(Blocks.TALLGRASS, 60, 100);
        Blocks.FIRE.setFlammable(Blocks.DOUBLE_PLANT, 60, 100);
        Blocks.FIRE.setFlammable(Blocks.YELLOW_FLOWER, 60, 100);
        Blocks.FIRE.setFlammable(Blocks.RED_FLOWER, 60, 100);
        Blocks.FIRE.setFlammable(Blocks.DEADBUSH, 60, 100);
        Blocks.FIRE.setFlammable(Blocks.WOOL, 30, 60);
        Blocks.FIRE.setFlammable(Blocks.VINE, 15, 100);
        Blocks.FIRE.setFlammable(Blocks.COAL_BLOCK, 5, 5);
        Blocks.FIRE.setFlammable(Blocks.HAY, 60, 20);
        Blocks.FIRE.setFlammable(Blocks.CARPET, 60, 20);
    }

    public void setFlammable(Block block, int flammability, int burnChance) {
        this.flammability.put(block, flammability);
        this.burnChance.put(block, burnChance);
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
    public int getBaseDropCount(Random random) {
        return 0;
    }

    @Override
    public int getTickRate(World world) {
        return 30;
    }

    @Override
    public void tick(World world, BlockPos pos, BlockState state, Random random) {
        if (world.getGameRules().getBoolean("doFireTick")) {
            if (!this.canBePlaced(world, pos)) {
                world.removeBlock(pos);
            }

            Block block = world.getBlockState(pos.down()).getBlock();
            boolean flag = block == Blocks.NETHERRACK;
            if (world.dimension instanceof TheEndDimension && block == Blocks.BEDROCK) {
                flag = true;
            }

            if (!flag && world.isRaining() && this.shouldExtinguish(world, pos)) {
                world.removeBlock(pos);
            } else {
                int i = state.get(AGE);
                if (i < 15) {
                    state = state.set(AGE, i + random.nextInt(3) / 2);
                    world.setBlockState(pos, state, 4);
                }

                world.scheduleTick(pos, this, this.getTickRate(world) + random.nextInt(10));
                if (!flag) {
                    if (!this.hasFlammableNeighbor(world, pos)) {
                        if (!World.hasSolidTop(world, pos.down()) || i > 3) {
                            world.removeBlock(pos);
                        }

                        return;
                    }

                    if (!this.isFlammable(world, pos.down()) && i == 15 && random.nextInt(4) == 0) {
                        world.removeBlock(pos);
                        return;
                    }
                }

                boolean flag1 = world.isHumid(pos);
                int j = 0;
                if (flag1) {
                    j = -50;
                }

                this.burnNeighbor(world, pos.east(), 300 + j, random, i);
                this.burnNeighbor(world, pos.west(), 300 + j, random, i);
                this.burnNeighbor(world, pos.down(), 250 + j, random, i);
                this.burnNeighbor(world, pos.up(), 250 + j, random, i);
                this.burnNeighbor(world, pos.north(), 300 + j, random, i);
                this.burnNeighbor(world, pos.south(), 300 + j, random, i);

                for (int k = -1; k <= 1; k++) {
                    for (int l = -1; l <= 1; l++) {
                        for (int i1 = -1; i1 <= 4; i1++) {
                            if (k != 0 || i1 != 0 || l != 0) {
                                int j1 = 100;
                                if (i1 > 1) {
                                    j1 += (i1 - 1) * 100;
                                }

                                BlockPos blockpos = pos.add(k, i1, l);
                                int k1 = this.getSpreadChance(world, blockpos);
                                if (k1 > 0) {
                                    int l1 = (k1 + 40 + world.getDifficulty().getId() * 7) / (i + 30);
                                    if (flag1) {
                                        l1 /= 2;
                                    }

                                    if (l1 > 0 && random.nextInt(j1) <= l1 && (!world.isRaining() || !this.shouldExtinguish(world, blockpos))) {
                                        int i2 = i + random.nextInt(5) / 4;
                                        if (i2 > 15) {
                                            i2 = 15;
                                        }

                                        world.setBlockState(blockpos, state.set(AGE, i2), 3);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    protected boolean shouldExtinguish(World world, BlockPos pos) {
        return world.isRaining(pos)
            || world.isRaining(pos.west())
            || world.isRaining(pos.east())
            || world.isRaining(pos.north())
            || world.isRaining(pos.south());
    }

    @Override
    public boolean acceptsImmediateTicks() {
        return false;
    }

    private int getBurnChance(Block block) {
        Integer integer = this.burnChance.get(block);
        return integer == null ? 0 : integer;
    }

    private int getFlammability(Block block) {
        Integer integer = this.flammability.get(block);
        return integer == null ? 0 : integer;
    }

    private void burnNeighbor(World world, BlockPos pos, int chance, Random random, int metadata) {
        int i = this.getBurnChance(world.getBlockState(pos).getBlock());
        if (random.nextInt(chance) < i) {
            BlockState blockstate = world.getBlockState(pos);
            if (random.nextInt(metadata + 10) < 5 && !world.isRaining(pos)) {
                int j = metadata + random.nextInt(5) / 4;
                if (j > 15) {
                    j = 15;
                }

                world.setBlockState(pos, this.defaultState().set(AGE, j), 3);
            } else {
                world.removeBlock(pos);
            }

            if (blockstate.getBlock() == Blocks.TNT) {
                Blocks.TNT.onBroken(world, pos, blockstate.set(TntBlock.EXPLODE, true));
            }
        }
    }

    private boolean hasFlammableNeighbor(World world, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (this.isFlammable(world, pos.offset(direction))) {
                return true;
            }
        }

        return false;
    }

    private int getSpreadChance(World world, BlockPos pos) {
        if (!world.isAir(pos)) {
            return 0;
        }

        int i = 0;

        for (Direction direction : Direction.values()) {
            i = Math.max(this.getFlammability(world.getBlockState(pos.offset(direction)).getBlock()), i);
        }

        return i;
    }

    @Override
    public boolean canRayTrace() {
        return false;
    }

    public boolean isFlammable(WorldView world, BlockPos pos) {
        return this.getFlammability(world.getBlockState(pos).getBlock()) > 0;
    }

    @Override
    public boolean canBePlaced(World world, BlockPos pos) {
        return World.hasSolidTop(world, pos.down()) || this.hasFlammableNeighbor(world, pos);
    }

    @Override
    public void neighborChanged(World world, BlockPos pos, BlockState state, Block neighborBlock) {
        if (!World.hasSolidTop(world, pos.down()) && !this.hasFlammableNeighbor(world, pos)) {
            world.removeBlock(pos);
        }
    }

    @Override
    public void onAdded(World world, BlockPos pos, BlockState state) {
        if (world.dimension.getId() > 0 || !Blocks.NETHER_PORTAL.create(world, pos)) {
            if (!World.hasSolidTop(world, pos.down()) && !this.hasFlammableNeighbor(world, pos)) {
                world.removeBlock(pos);
            } else {
                world.scheduleTick(pos, this, this.getTickRate(world) + world.random.nextInt(10));
            }
        }
    }

    @Override
    public void randomDisplayTick(World world, BlockPos pos, BlockState state, Random random) {
        if (random.nextInt(24) == 0) {
            world.playSound(
                pos.getX() + 0.5F, pos.getY() + 0.5F, pos.getZ() + 0.5F, "fire.fire", 1.0F + random.nextFloat(), random.nextFloat() * 0.7F + 0.3F, false
            );
        }

        if (!World.hasSolidTop(world, pos.down()) && !Blocks.FIRE.isFlammable(world, pos.down())) {
            if (Blocks.FIRE.isFlammable(world, pos.west())) {
                for (int j = 0; j < 2; j++) {
                    double d3 = pos.getX() + random.nextDouble() * 0.1F;
                    double d8 = pos.getY() + random.nextDouble();
                    double d13 = pos.getZ() + random.nextDouble();
                    world.addParticle(ParticleType.SMOKE_LARGE, d3, d8, d13, 0.0, 0.0, 0.0);
                }
            }

            if (Blocks.FIRE.isFlammable(world, pos.east())) {
                for (int k = 0; k < 2; k++) {
                    double d4 = pos.getX() + 1 - random.nextDouble() * 0.1F;
                    double d9 = pos.getY() + random.nextDouble();
                    double d14 = pos.getZ() + random.nextDouble();
                    world.addParticle(ParticleType.SMOKE_LARGE, d4, d9, d14, 0.0, 0.0, 0.0);
                }
            }

            if (Blocks.FIRE.isFlammable(world, pos.north())) {
                for (int l = 0; l < 2; l++) {
                    double d5 = pos.getX() + random.nextDouble();
                    double d10 = pos.getY() + random.nextDouble();
                    double d15 = pos.getZ() + random.nextDouble() * 0.1F;
                    world.addParticle(ParticleType.SMOKE_LARGE, d5, d10, d15, 0.0, 0.0, 0.0);
                }
            }

            if (Blocks.FIRE.isFlammable(world, pos.south())) {
                for (int i1 = 0; i1 < 2; i1++) {
                    double d6 = pos.getX() + random.nextDouble();
                    double d11 = pos.getY() + random.nextDouble();
                    double d16 = pos.getZ() + 1 - random.nextDouble() * 0.1F;
                    world.addParticle(ParticleType.SMOKE_LARGE, d6, d11, d16, 0.0, 0.0, 0.0);
                }
            }

            if (Blocks.FIRE.isFlammable(world, pos.up())) {
                for (int j1 = 0; j1 < 2; j1++) {
                    double d7 = pos.getX() + random.nextDouble();
                    double d12 = pos.getY() + 1 - random.nextDouble() * 0.1F;
                    double d17 = pos.getZ() + random.nextDouble();
                    world.addParticle(ParticleType.SMOKE_LARGE, d7, d12, d17, 0.0, 0.0, 0.0);
                }
            }
        } else {
            for (int i = 0; i < 3; i++) {
                double d0 = pos.getX() + random.nextDouble();
                double d1 = pos.getY() + random.nextDouble() * 0.5 + 0.5;
                double d2 = pos.getZ() + random.nextDouble();
                world.addParticle(ParticleType.SMOKE_LARGE, d0, d1, d2, 0.0, 0.0, 0.0);
            }
        }
    }

    @Override
    public MapColor getMapColor(BlockState state) {
        return MapColor.LAVA;
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
        return new StateDefinition(this, AGE, NORTH, EAST, SOUTH, WEST, UPPER, FLIP, ALT);
    }
}
