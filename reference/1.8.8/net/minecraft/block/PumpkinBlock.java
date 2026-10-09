package net.minecraft.block;

import com.google.common.base.Predicate;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.pattern.BlockPattern;
import net.minecraft.block.pattern.BlockPatternBuilder;
import net.minecraft.block.pattern.BlockPointer;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.predicate.BlockStatePredicate;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.mob.IronGolemEntity;
import net.minecraft.entity.living.mob.SnowGolemEntity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class PumpkinBlock extends HorizontalFacingBlock {
    private BlockPattern snowGolemBodyPattern;
    private BlockPattern snowGolemPattern;
    private BlockPattern ironGolemBodyPattern;
    private BlockPattern ironGolemPattern;
    private static final Predicate<BlockState> IS_GOLEM_BLOCK = new Predicate<BlockState>() {
        public boolean apply(BlockState blockState) {
            return blockState != null && (blockState.getBlock() == Blocks.PUMPKIN || blockState.getBlock() == Blocks.LIT_PUMPKIN);
        }
    };

    protected PumpkinBlock() {
        super(Material.PUMPKIN, MapColor.ORANGE);
        this.setDefaultState(this.stateDefinition.any().set(FACING, Direction.NORTH));
        this.setTicksRandomly(true);
        this.setCreativeModeTab(CreativeModeTab.BUILDING_BLOCKS);
    }

    @Override
    public void onAdded(World world, BlockPos pos, BlockState state) {
        super.onAdded(world, pos, state);
        this.trySpawnGolem(world, pos);
    }

    public boolean canSpawnGolem(World world, BlockPos pos) {
        return this.getSnowGolemBodyPattern().find(world, pos) != null || this.getIronGolemBodyPattern().find(world, pos) != null;
    }

    private void trySpawnGolem(World world, BlockPos pos) {
        BlockPattern.Match blockpattern$match;
        if ((blockpattern$match = this.getSnowGolemPattern().find(world, pos)) != null) {
            for (int i = 0; i < this.getSnowGolemPattern().getWidth(); i++) {
                BlockPointer blockpointer = blockpattern$match.getBlock(0, i, 0);
                world.setBlockState(blockpointer.getPos(), Blocks.AIR.defaultState(), 2);
            }

            SnowGolemEntity snowgolementity = new SnowGolemEntity(world);
            BlockPos blockpos1 = blockpattern$match.getBlock(0, 2, 0).getPos();
            snowgolementity.setPositionAndAngles(blockpos1.getX() + 0.5, blockpos1.getY() + 0.05, blockpos1.getZ() + 0.5, 0.0F, 0.0F);
            world.addEntity(snowgolementity);

            for (int j = 0; j < 120; j++) {
                world.addParticle(
                    ParticleType.SNOW_SHOVEL,
                    blockpos1.getX() + world.random.nextDouble(),
                    blockpos1.getY() + world.random.nextDouble() * 2.5,
                    blockpos1.getZ() + world.random.nextDouble(),
                    0.0,
                    0.0,
                    0.0
                );
            }

            for (int i1 = 0; i1 < this.getSnowGolemPattern().getWidth(); i1++) {
                BlockPointer blockpointer1 = blockpattern$match.getBlock(0, i1, 0);
                world.onBlockChanged(blockpointer1.getPos(), Blocks.AIR);
            }
        } else if ((blockpattern$match = this.getIronGolemPattern().find(world, pos)) != null) {
            for (int k = 0; k < this.getIronGolemPattern().getHeight(); k++) {
                for (int l = 0; l < this.getIronGolemPattern().getWidth(); l++) {
                    world.setBlockState(blockpattern$match.getBlock(k, l, 0).getPos(), Blocks.AIR.defaultState(), 2);
                }
            }

            BlockPos blockpos = blockpattern$match.getBlock(1, 2, 0).getPos();
            IronGolemEntity irongolementity = new IronGolemEntity(world);
            irongolementity.setPlayerCreated(true);
            irongolementity.setPositionAndAngles(blockpos.getX() + 0.5, blockpos.getY() + 0.05, blockpos.getZ() + 0.5, 0.0F, 0.0F);
            world.addEntity(irongolementity);

            for (int j1 = 0; j1 < 120; j1++) {
                world.addParticle(
                    ParticleType.SNOWBALL,
                    blockpos.getX() + world.random.nextDouble(),
                    blockpos.getY() + world.random.nextDouble() * 3.9,
                    blockpos.getZ() + world.random.nextDouble(),
                    0.0,
                    0.0,
                    0.0
                );
            }

            for (int k1 = 0; k1 < this.getIronGolemPattern().getHeight(); k1++) {
                for (int l1 = 0; l1 < this.getIronGolemPattern().getWidth(); l1++) {
                    BlockPointer blockpointer2 = blockpattern$match.getBlock(k1, l1, 0);
                    world.onBlockChanged(blockpointer2.getPos(), Blocks.AIR);
                }
            }
        }
    }

    @Override
    public boolean canBePlaced(World world, BlockPos pos) {
        return world.getBlockState(pos).getBlock().material.isReplaceable() && World.hasSolidTop(world, pos.down());
    }

    @Override
    public BlockState getPlacementState(World world, BlockPos pos, Direction dir, float dx, float dy, float dz, int metadata, LivingEntity entity) {
        return this.defaultState().set(FACING, entity.getHorizontalFacing().getOpposite());
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        return this.defaultState().set(FACING, Direction.byIdHorizontal(metadata));
    }

    @Override
    public int getMetadataFromState(BlockState state) {
        return state.get(FACING).getIdHorizontal();
    }

    @Override
    protected StateDefinition createStateDefinition() {
        return new StateDefinition(this, FACING);
    }

    protected BlockPattern getSnowGolemBodyPattern() {
        if (this.snowGolemBodyPattern == null) {
            this.snowGolemBodyPattern = BlockPatternBuilder.start()
                .aisle(" ", "#", "#")
                .with('#', BlockPointer.hasState(BlockStatePredicate.of(Blocks.SNOW)))
                .build();
        }

        return this.snowGolemBodyPattern;
    }

    protected BlockPattern getSnowGolemPattern() {
        if (this.snowGolemPattern == null) {
            this.snowGolemPattern = BlockPatternBuilder.start()
                .aisle("^", "#", "#")
                .with('^', BlockPointer.hasState(IS_GOLEM_BLOCK))
                .with('#', BlockPointer.hasState(BlockStatePredicate.of(Blocks.SNOW)))
                .build();
        }

        return this.snowGolemPattern;
    }

    protected BlockPattern getIronGolemBodyPattern() {
        if (this.ironGolemBodyPattern == null) {
            this.ironGolemBodyPattern = BlockPatternBuilder.start()
                .aisle("~ ~", "###", "~#~")
                .with('#', BlockPointer.hasState(BlockStatePredicate.of(Blocks.IRON_BLOCK)))
                .with('~', BlockPointer.hasState(BlockStatePredicate.of(Blocks.AIR)))
                .build();
        }

        return this.ironGolemBodyPattern;
    }

    protected BlockPattern getIronGolemPattern() {
        if (this.ironGolemPattern == null) {
            this.ironGolemPattern = BlockPatternBuilder.start()
                .aisle("~^~", "###", "~#~")
                .with('^', BlockPointer.hasState(IS_GOLEM_BLOCK))
                .with('#', BlockPointer.hasState(BlockStatePredicate.of(Blocks.IRON_BLOCK)))
                .with('~', BlockPointer.hasState(BlockStatePredicate.of(Blocks.AIR)))
                .build();
        }

        return this.ironGolemPattern;
    }
}
