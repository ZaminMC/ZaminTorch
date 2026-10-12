package net.minecraft.block;

import java.util.Random;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.CommandBlockBlockEntity;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.BooleanProperty;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.source.CommandExecutor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class CommandBlock extends BlockWithBlockEntity {
    public static final BooleanProperty TRIGGERED = BooleanProperty.of("triggered");

    public CommandBlock() {
        super(Material.IRON, MapColor.ORANGE);
        this.setDefaultState(this.stateDefinition.any().set(TRIGGERED, false));
    }

    @Override
    public BlockEntity createBlockEntity(World world, int metadata) {
        return new CommandBlockBlockEntity();
    }

    @Override
    public void neighborChanged(World world, BlockPos pos, BlockState state, Block neighborBlock) {
        if (!world.isClient) {
            boolean flag = world.hasNeighborSignal(pos);
            boolean flag1 = state.get(TRIGGERED);
            if (flag && !flag1) {
                world.setBlockState(pos, state.set(TRIGGERED, true), 4);
                world.scheduleTick(pos, this, this.getTickRate(world));
            } else if (!flag && flag1) {
                world.setBlockState(pos, state.set(TRIGGERED, false), 4);
            }
        }
    }

    @Override
    public void tick(World world, BlockPos pos, BlockState state, Random random) {
        BlockEntity blockentity = world.getBlockEntity(pos);
        if (blockentity instanceof CommandBlockBlockEntity) {
            ((CommandBlockBlockEntity)blockentity).getCommandExecutor().run(world);
            world.updateNeighborComparators(pos, this);
        }
    }

    @Override
    public int getTickRate(World world) {
        return 1;
    }

    @Override
    public boolean use(World world, BlockPos pos, BlockState state, PlayerEntity player, Direction face, float faceX, float faceY, float faceZ) {
        BlockEntity blockentity = world.getBlockEntity(pos);
        return blockentity instanceof CommandBlockBlockEntity && ((CommandBlockBlockEntity)blockentity).getCommandExecutor().openScreen(player);
    }

    @Override
    public boolean isAnalogSignalSource() {
        return true;
    }

    @Override
    public int getAnalogSignal(World world, BlockPos pos) {
        BlockEntity blockentity = world.getBlockEntity(pos);
        return blockentity instanceof CommandBlockBlockEntity ? ((CommandBlockBlockEntity)blockentity).getCommandExecutor().getSuccessCount() : 0;
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity entity, ItemStack item) {
        BlockEntity blockentity = world.getBlockEntity(pos);
        if (blockentity instanceof CommandBlockBlockEntity) {
            CommandExecutor commandexecutor = ((CommandBlockBlockEntity)blockentity).getCommandExecutor();
            if (item.hasCustomHoverName()) {
                commandexecutor.setName(item.getHoverName());
            }

            if (!world.isClient) {
                commandexecutor.setTrackOutput(world.getGameRules().getBoolean("sendCommandFeedback"));
            }
        }
    }

    @Override
    public int getBaseDropCount(Random random) {
        return 0;
    }

    @Override
    public int getRenderType() {
        return 3;
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        return this.defaultState().set(TRIGGERED, (metadata & 1) > 0);
    }

    @Override
    public int getMetadataFromState(BlockState state) {
        int i = 0;
        if (state.get(TRIGGERED)) {
            i |= 1;
        }

        return i;
    }

    @Override
    protected StateDefinition createStateDefinition() {
        return new StateDefinition(this, TRIGGERED);
    }

    @Override
    public BlockState getPlacementState(World world, BlockPos pos, Direction dir, float dx, float dy, float dz, int metadata, LivingEntity entity) {
        return this.defaultState().set(TRIGGERED, false);
    }
}
