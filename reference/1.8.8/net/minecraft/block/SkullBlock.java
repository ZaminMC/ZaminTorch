package net.minecraft.block;

import com.google.common.base.Predicate;
import java.util.Random;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SkullBlockEntity;
import net.minecraft.block.material.Material;
import net.minecraft.block.pattern.BlockPattern;
import net.minecraft.block.pattern.BlockPatternBuilder;
import net.minecraft.block.pattern.BlockPointer;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.predicate.BlockStatePredicate;
import net.minecraft.block.state.property.BooleanProperty;
import net.minecraft.block.state.property.DirectionProperty;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.mob.monster.boss.WitherEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.locale.I18n;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.stat.achievement.Achievements;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Difficulty;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

public class SkullBlock extends BlockWithBlockEntity {
    public static final DirectionProperty FACING = DirectionProperty.of("facing");
    public static final BooleanProperty NODROP = BooleanProperty.of("nodrop");
    private static final Predicate<BlockPointer> WITHER_SKULL_PREDICATE = new Predicate<BlockPointer>() {
        public boolean apply(BlockPointer blockPointer) {
            return blockPointer.getState() != null
                && blockPointer.getState().getBlock() == Blocks.SKULL
                && blockPointer.getBlockEntity() instanceof SkullBlockEntity
                && ((SkullBlockEntity)blockPointer.getBlockEntity()).getType() == 1;
        }
    };
    private BlockPattern basePattern;
    private BlockPattern pattern;

    protected SkullBlock() {
        super(Material.DECORATION);
        this.setDefaultState(this.stateDefinition.any().set(FACING, Direction.NORTH).set(NODROP, false));
        this.setShape(0.25F, 0.0F, 0.25F, 0.75F, 0.5F, 0.75F);
    }

    @Override
    public String getName() {
        return I18n.translate("tile.skull.skeleton.name");
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
    public void updateShape(WorldView world, BlockPos pos) {
        switch ((Direction)world.getBlockState(pos).get(FACING)) {
            case UP:
            default:
                this.setShape(0.25F, 0.0F, 0.25F, 0.75F, 0.5F, 0.75F);
                break;
            case NORTH:
                this.setShape(0.25F, 0.25F, 0.5F, 0.75F, 0.75F, 1.0F);
                break;
            case SOUTH:
                this.setShape(0.25F, 0.25F, 0.0F, 0.75F, 0.75F, 0.5F);
                break;
            case WEST:
                this.setShape(0.5F, 0.25F, 0.25F, 1.0F, 0.75F, 0.75F);
                break;
            case EAST:
                this.setShape(0.0F, 0.25F, 0.25F, 0.5F, 0.75F, 0.75F);
        }
    }

    @Override
    public Box getCollisionShape(World world, BlockPos pos, BlockState state) {
        this.updateShape(world, pos);
        return super.getCollisionShape(world, pos, state);
    }

    @Override
    public BlockState getPlacementState(World world, BlockPos pos, Direction dir, float dx, float dy, float dz, int metadata, LivingEntity entity) {
        return this.defaultState().set(FACING, entity.getHorizontalFacing()).set(NODROP, false);
    }

    @Override
    public BlockEntity createBlockEntity(World world, int metadata) {
        return new SkullBlockEntity();
    }

    @Override
    public Item getPickItem(World world, BlockPos pos) {
        return Items.SKULL;
    }

    @Override
    public int getPickItemMetadata(World world, BlockPos pos) {
        BlockEntity blockentity = world.getBlockEntity(pos);
        return blockentity instanceof SkullBlockEntity ? ((SkullBlockEntity)blockentity).getType() : super.getPickItemMetadata(world, pos);
    }

    @Override
    public void dropItems(World world, BlockPos pos, BlockState state, float luck, int fortuneLevel) {
    }

    @Override
    public void beforeMinedByPlayer(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (player.abilities.creativeMode) {
            state = state.set(NODROP, true);
            world.setBlockState(pos, state, 4);
        }

        super.beforeMinedByPlayer(world, pos, state, player);
    }

    @Override
    public void onRemoved(World world, BlockPos pos, BlockState state) {
        if (!world.isClient) {
            if (!state.get(NODROP)) {
                BlockEntity blockentity = world.getBlockEntity(pos);
                if (blockentity instanceof SkullBlockEntity) {
                    SkullBlockEntity skullblockentity = (SkullBlockEntity)blockentity;
                    ItemStack itemstack = new ItemStack(Items.SKULL, 1, this.getPickItemMetadata(world, pos));
                    if (skullblockentity.getType() == 3 && skullblockentity.getProfile() != null) {
                        itemstack.setNbt(new NbtCompound());
                        NbtCompound nbtcompound = new NbtCompound();
                        NbtUtils.writeProfile(nbtcompound, skullblockentity.getProfile());
                        itemstack.getNbt().put("SkullOwner", nbtcompound);
                    }

                    dropItem(world, pos, itemstack);
                }
            }

            super.onRemoved(world, pos, state);
        }
    }

    @Override
    public Item getDropItem(BlockState state, Random random, int fortuneLevel) {
        return Items.SKULL;
    }

    public boolean canSpawn(World world, BlockPos pos, ItemStack item) {
        return item.getMetadata() == 1
            && pos.getY() >= 2
            && world.getDifficulty() != Difficulty.PEACEFUL
            && !world.isClient
            && this.getBasePattern().find(world, pos) != null;
    }

    public void trySpawn(World world, BlockPos pos, SkullBlockEntity skull) {
        if (skull.getType() == 1 && pos.getY() >= 2 && world.getDifficulty() != Difficulty.PEACEFUL && !world.isClient) {
            BlockPattern blockpattern = this.getPattern();
            BlockPattern.Match blockpattern$match = blockpattern.find(world, pos);
            if (blockpattern$match != null) {
                for (int i = 0; i < 3; i++) {
                    BlockPointer blockpointer = blockpattern$match.getBlock(i, 0, 0);
                    world.setBlockState(blockpointer.getPos(), blockpointer.getState().set(NODROP, true), 2);
                }

                for (int j = 0; j < blockpattern.getHeight(); j++) {
                    for (int k = 0; k < blockpattern.getWidth(); k++) {
                        BlockPointer blockpointer1 = blockpattern$match.getBlock(j, k, 0);
                        world.setBlockState(blockpointer1.getPos(), Blocks.AIR.defaultState(), 2);
                    }
                }

                BlockPos blockpos = blockpattern$match.getBlock(1, 0, 0).getPos();
                WitherEntity witherentity = new WitherEntity(world);
                BlockPos blockpos1 = blockpattern$match.getBlock(1, 2, 0).getPos();
                witherentity.setPositionAndAngles(
                    blockpos1.getX() + 0.5,
                    blockpos1.getY() + 0.55,
                    blockpos1.getZ() + 0.5,
                    blockpattern$match.getForward().getAxis() == Direction.Axis.X ? 0.0F : 90.0F,
                    0.0F
                );
                witherentity.bodyYaw = blockpattern$match.getForward().getAxis() == Direction.Axis.X ? 0.0F : 90.0F;
                witherentity.onSummoned();

                for (PlayerEntity playerentity : world.getEntitiesOfType(PlayerEntity.class, witherentity.getShape().grown(50.0, 50.0, 50.0))) {
                    playerentity.incrementStat(Achievements.SUMMON_WITHER);
                }

                world.addEntity(witherentity);

                for (int l = 0; l < 120; l++) {
                    world.addParticle(
                        ParticleType.SNOWBALL,
                        blockpos.getX() + world.random.nextDouble(),
                        blockpos.getY() - 2 + world.random.nextDouble() * 3.9,
                        blockpos.getZ() + world.random.nextDouble(),
                        0.0,
                        0.0,
                        0.0
                    );
                }

                for (int i1 = 0; i1 < blockpattern.getHeight(); i1++) {
                    for (int j1 = 0; j1 < blockpattern.getWidth(); j1++) {
                        BlockPointer blockpointer2 = blockpattern$match.getBlock(i1, j1, 0);
                        world.onBlockChanged(blockpointer2.getPos(), Blocks.AIR);
                    }
                }
            }
        }
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        return this.defaultState().set(FACING, Direction.byId(metadata & 7)).set(NODROP, (metadata & 8) > 0);
    }

    @Override
    public int getMetadataFromState(BlockState state) {
        int i = 0;
        i |= state.get(FACING).getId();
        if (state.get(NODROP)) {
            i |= 8;
        }

        return i;
    }

    @Override
    protected StateDefinition createStateDefinition() {
        return new StateDefinition(this, FACING, NODROP);
    }

    protected BlockPattern getBasePattern() {
        if (this.basePattern == null) {
            this.basePattern = BlockPatternBuilder.start()
                .aisle("   ", "###", "~#~")
                .with('#', BlockPointer.hasState(BlockStatePredicate.of(Blocks.SOUL_SAND)))
                .with('~', BlockPointer.hasState(BlockStatePredicate.of(Blocks.AIR)))
                .build();
        }

        return this.basePattern;
    }

    protected BlockPattern getPattern() {
        if (this.pattern == null) {
            this.pattern = BlockPatternBuilder.start()
                .aisle("^^^", "###", "~#~")
                .with('#', BlockPointer.hasState(BlockStatePredicate.of(Blocks.SOUL_SAND)))
                .with('^', WITHER_SKULL_PREDICATE)
                .with('~', BlockPointer.hasState(BlockStatePredicate.of(Blocks.AIR)))
                .build();
        }

        return this.pattern;
    }
}
