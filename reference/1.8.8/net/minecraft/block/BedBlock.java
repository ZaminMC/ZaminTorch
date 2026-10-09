package net.minecraft.block;

import java.util.Random;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.BooleanProperty;
import net.minecraft.block.state.property.EnumProperty;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.StringSerializable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import net.minecraft.world.biome.Biome;

public class BedBlock extends HorizontalFacingBlock {
    public static final EnumProperty<BedBlock.Part> PART = EnumProperty.of("part", BedBlock.Part.class);
    public static final BooleanProperty OCCUPIED = BooleanProperty.of("occupied");

    public BedBlock() {
        super(Material.WOOL);
        this.setDefaultState(this.stateDefinition.any().set(PART, BedBlock.Part.FOOT).set(OCCUPIED, false));
        this.setDefaultShape();
    }

    @Override
    public boolean use(World world, BlockPos pos, BlockState state, PlayerEntity player, Direction face, float faceX, float faceY, float faceZ) {
        if (world.isClient) {
            return true;
        }

        if (state.get(PART) != BedBlock.Part.HEAD) {
            pos = pos.offset(state.get(FACING));
            state = world.getBlockState(pos);
            if (state.getBlock() != this) {
                return true;
            }
        }

        if (world.dimension.hasSpawnPoint() && world.getBiome(pos) != Biome.HELL) {
            if (state.get(OCCUPIED)) {
                PlayerEntity playerentity = this.getOccupant(world, pos);
                if (playerentity != null) {
                    player.addMessage(new TranslatableText("tile.bed.occupied"));
                    return true;
                }

                state = state.set(OCCUPIED, false);
                world.setBlockState(pos, state, 4);
            }

            PlayerEntity.SleepAllowedStatus playerentity$sleepallowedstatus = player.trySleep(pos);
            if (playerentity$sleepallowedstatus == PlayerEntity.SleepAllowedStatus.OK) {
                state = state.set(OCCUPIED, true);
                world.setBlockState(pos, state, 4);
                return true;
            }

            if (playerentity$sleepallowedstatus == PlayerEntity.SleepAllowedStatus.NOT_POSSIBLE_NOW) {
                player.addMessage(new TranslatableText("tile.bed.noSleep"));
            } else if (playerentity$sleepallowedstatus == PlayerEntity.SleepAllowedStatus.NOT_SAFE) {
                player.addMessage(new TranslatableText("tile.bed.notSafe"));
            }

            return true;
        } else {
            world.removeBlock(pos);
            BlockPos blockpos = pos.offset(state.get(FACING).getOpposite());
            if (world.getBlockState(blockpos).getBlock() == this) {
                world.removeBlock(blockpos);
            }

            world.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 5.0F, true, true);
            return true;
        }
    }

    private PlayerEntity getOccupant(World world, BlockPos pos) {
        for (PlayerEntity playerentity : world.players) {
            if (playerentity.isSleeping() && playerentity.sleepingPos.equals(pos)) {
                return playerentity;
            }
        }

        return null;
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
    public void updateShape(WorldView world, BlockPos pos) {
        this.setDefaultShape();
    }

    @Override
    public void neighborChanged(World world, BlockPos pos, BlockState state, Block neighborBlock) {
        Direction direction = state.get(FACING);
        if (state.get(PART) == BedBlock.Part.HEAD) {
            if (world.getBlockState(pos.offset(direction.getOpposite())).getBlock() != this) {
                world.removeBlock(pos);
            }
        } else if (world.getBlockState(pos.offset(direction)).getBlock() != this) {
            world.removeBlock(pos);
            if (!world.isClient) {
                this.dropItems(world, pos, state, 0);
            }
        }
    }

    @Override
    public Item getDropItem(BlockState state, Random random, int fortuneLevel) {
        return state.get(PART) == BedBlock.Part.HEAD ? null : Items.BED;
    }

    private void setDefaultShape() {
        this.setShape(0.0F, 0.0F, 0.0F, 1.0F, 0.5625F, 1.0F);
    }

    public static BlockPos getSpawnPoint(World world, BlockPos pos, int skip) {
        Direction direction = world.getBlockState(pos).get(FACING);
        int i = pos.getX();
        int j = pos.getY();
        int k = pos.getZ();

        for (int l = 0; l <= 1; l++) {
            int i1 = i - direction.getOffsetX() * l - 1;
            int j1 = k - direction.getOffsetZ() * l - 1;
            int k1 = i1 + 2;
            int l1 = j1 + 2;

            for (int i2 = i1; i2 <= k1; i2++) {
                for (int j2 = j1; j2 <= l1; j2++) {
                    BlockPos blockpos = new BlockPos(i2, j, j2);
                    if (isValidSpawnPos(world, blockpos)) {
                        if (skip <= 0) {
                            return blockpos;
                        }

                        skip--;
                    }
                }
            }
        }

        return null;
    }

    protected static boolean isValidSpawnPos(World world, BlockPos pos) {
        return World.hasSolidTop(world, pos.down())
            && !world.getBlockState(pos).getBlock().getMaterial().isSolid()
            && !world.getBlockState(pos.up()).getBlock().getMaterial().isSolid();
    }

    @Override
    public void dropItems(World world, BlockPos pos, BlockState state, float luck, int fortuneLevel) {
        if (state.get(PART) == BedBlock.Part.FOOT) {
            super.dropItems(world, pos, state, luck, 0);
        }
    }

    @Override
    public int getPistonMoveBehavior() {
        return 1;
    }

    @Override
    public BlockLayer getRenderLayer() {
        return BlockLayer.CUTOUT;
    }

    @Override
    public Item getPickItem(World world, BlockPos pos) {
        return Items.BED;
    }

    @Override
    public void beforeMinedByPlayer(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (player.abilities.creativeMode && state.get(PART) == BedBlock.Part.HEAD) {
            BlockPos blockpos = pos.offset(state.get(FACING).getOpposite());
            if (world.getBlockState(blockpos).getBlock() == this) {
                world.removeBlock(blockpos);
            }
        }
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        Direction direction = Direction.byIdHorizontal(metadata);
        return (metadata & 8) > 0
            ? this.defaultState().set(PART, BedBlock.Part.HEAD).set(FACING, direction).set(OCCUPIED, (metadata & 4) > 0)
            : this.defaultState().set(PART, BedBlock.Part.FOOT).set(FACING, direction);
    }

    @Override
    public BlockState resolveVirtualProperties(BlockState state, WorldView world, BlockPos pos) {
        if (state.get(PART) == BedBlock.Part.FOOT) {
            BlockState blockstate = world.getBlockState(pos.offset(state.get(FACING)));
            if (blockstate.getBlock() == this) {
                state = state.set(OCCUPIED, blockstate.get(OCCUPIED));
            }
        }

        return state;
    }

    @Override
    public int getMetadataFromState(BlockState state) {
        int i = 0;
        i |= state.get(FACING).getIdHorizontal();
        if (state.get(PART) == BedBlock.Part.HEAD) {
            i |= 8;
            if (state.get(OCCUPIED)) {
                i |= 4;
            }
        }

        return i;
    }

    @Override
    protected StateDefinition createStateDefinition() {
        return new StateDefinition(this, FACING, PART, OCCUPIED);
    }

    public enum Part implements StringSerializable {
        HEAD("head"),
        FOOT("foot");

        private final String key;

        Part(String key) {
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
