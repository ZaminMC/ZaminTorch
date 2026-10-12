package net.minecraft.block;

import java.util.Random;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.BooleanProperty;
import net.minecraft.block.state.property.IntegerProperty;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.locale.I18n;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

public class RepeaterBlock extends DiodeBlock {
    public static final BooleanProperty LOCKED = BooleanProperty.of("locked");
    public static final IntegerProperty DELAY = IntegerProperty.of("delay", 1, 4);

    protected RepeaterBlock(boolean bl) {
        super(bl);
        this.setDefaultState(this.stateDefinition.any().set(FACING, Direction.NORTH).set(DELAY, 1).set(LOCKED, false));
    }

    @Override
    public String getName() {
        return I18n.translate("item.diode.name");
    }

    @Override
    public BlockState resolveVirtualProperties(BlockState state, WorldView world, BlockPos pos) {
        return state.set(LOCKED, this.isLocked(world, pos, state));
    }

    @Override
    public boolean use(World world, BlockPos pos, BlockState state, PlayerEntity player, Direction face, float faceX, float faceY, float faceZ) {
        if (!player.abilities.canModifyWorld) {
            return false;
        }

        world.setBlockState(pos, state.next(DELAY), 3);
        return true;
    }

    @Override
    protected int getDelay(BlockState state) {
        return state.get(DELAY) * 2;
    }

    @Override
    protected BlockState setUnpowered(BlockState state) {
        Integer integer = state.get(DELAY);
        Boolean obool = state.get(LOCKED);
        Direction direction = state.get(FACING);
        return Blocks.POWERED_REPEATER.defaultState().set(FACING, direction).set(DELAY, integer).set(LOCKED, obool);
    }

    @Override
    protected BlockState setPowered(BlockState state) {
        Integer integer = state.get(DELAY);
        Boolean obool = state.get(LOCKED);
        Direction direction = state.get(FACING);
        return Blocks.REPEATER.defaultState().set(FACING, direction).set(DELAY, integer).set(LOCKED, obool);
    }

    @Override
    public Item getDropItem(BlockState state, Random random, int fortuneLevel) {
        return Items.REPEATER;
    }

    @Override
    public Item getPickItem(World world, BlockPos pos) {
        return Items.REPEATER;
    }

    @Override
    public boolean isLocked(WorldView world, BlockPos pos, BlockState state) {
        return this.getInputSignalFromSides(world, pos, state) > 0;
    }

    @Override
    protected boolean isValidSideInput(Block block) {
        return isDiode(block);
    }

    @Override
    public void randomDisplayTick(World world, BlockPos pos, BlockState state, Random random) {
        if (this.powered) {
            Direction direction = state.get(FACING);
            double d0 = pos.getX() + 0.5F + (random.nextFloat() - 0.5F) * 0.2;
            double d1 = pos.getY() + 0.4F + (random.nextFloat() - 0.5F) * 0.2;
            double d2 = pos.getZ() + 0.5F + (random.nextFloat() - 0.5F) * 0.2;
            float f = -5.0F;
            if (random.nextBoolean()) {
                f = state.get(DELAY) * 2 - 1;
            }

            f /= 16.0F;
            double d3 = f * direction.getOffsetX();
            double d4 = f * direction.getOffsetZ();
            world.addParticle(ParticleType.REDSTONE, d0 + d3, d1, d2 + d4, 0.0, 0.0, 0.0);
        }
    }

    @Override
    public void onRemoved(World world, BlockPos pos, BlockState state) {
        super.onRemoved(world, pos, state);
        this.updateNeighbors(world, pos, state);
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        return this.defaultState().set(FACING, Direction.byIdHorizontal(metadata)).set(LOCKED, false).set(DELAY, 1 + (metadata >> 2));
    }

    @Override
    public int getMetadataFromState(BlockState state) {
        int i = 0;
        i |= state.get(FACING).getIdHorizontal();
        return i | state.get(DELAY) - 1 << 2;
    }

    @Override
    protected StateDefinition createStateDefinition() {
        return new StateDefinition(this, FACING, DELAY, LOCKED);
    }
}
