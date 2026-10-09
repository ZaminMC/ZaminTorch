package net.minecraft.block;

import com.google.common.base.Predicate;
import java.util.List;
import java.util.Random;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityProvider;
import net.minecraft.block.entity.ComparatorBlockEntity;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.BooleanProperty;
import net.minecraft.block.state.property.EnumProperty;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.locale.I18n;
import net.minecraft.util.StringSerializable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

public class ComparatorBlock extends DiodeBlock implements BlockEntityProvider {
    public static final BooleanProperty POWERED = BooleanProperty.of("powered");
    public static final EnumProperty<ComparatorBlock.Mode> MODE = EnumProperty.of("mode", ComparatorBlock.Mode.class);

    public ComparatorBlock(boolean bl) {
        super(bl);
        this.setDefaultState(this.stateDefinition.any().set(FACING, Direction.NORTH).set(POWERED, false).set(MODE, ComparatorBlock.Mode.COMPARE));
        this.hasBlockEntity = true;
    }

    @Override
    public String getName() {
        return I18n.translate("item.comparator.name");
    }

    @Override
    public Item getDropItem(BlockState state, Random random, int fortuneLevel) {
        return Items.COMPARATOR;
    }

    @Override
    public Item getPickItem(World world, BlockPos pos) {
        return Items.COMPARATOR;
    }

    @Override
    protected int getDelay(BlockState state) {
        return 2;
    }

    @Override
    protected BlockState setUnpowered(BlockState state) {
        Boolean obool = state.get(POWERED);
        ComparatorBlock.Mode comparatorblock$mode = state.get(MODE);
        Direction direction = state.get(FACING);
        return Blocks.POWERED_COMPARATOR.defaultState().set(FACING, direction).set(POWERED, obool).set(MODE, comparatorblock$mode);
    }

    @Override
    protected BlockState setPowered(BlockState state) {
        Boolean obool = state.get(POWERED);
        ComparatorBlock.Mode comparatorblock$mode = state.get(MODE);
        Direction direction = state.get(FACING);
        return Blocks.COMPARATOR.defaultState().set(FACING, direction).set(POWERED, obool).set(MODE, comparatorblock$mode);
    }

    @Override
    protected boolean isPowered(BlockState state) {
        return this.powered || state.get(POWERED);
    }

    @Override
    protected int getOutputSignal(WorldView world, BlockPos pos, BlockState state) {
        BlockEntity blockentity = world.getBlockEntity(pos);
        return blockentity instanceof ComparatorBlockEntity ? ((ComparatorBlockEntity)blockentity).getOutputSignal() : 0;
    }

    private int calculateOutputSignal(World world, BlockPos pos, BlockState state) {
        return state.get(MODE) == ComparatorBlock.Mode.SUBTRACT
            ? Math.max(this.getInputSignal(world, pos, state) - this.getInputSignalFromSides(world, pos, state), 0)
            : this.getInputSignal(world, pos, state);
    }

    @Override
    protected boolean shouldBePowered(World world, BlockPos pos, BlockState state) {
        int i = this.getInputSignal(world, pos, state);
        if (i >= 15) {
            return true;
        }

        if (i == 0) {
            return false;
        }

        int j = this.getInputSignalFromSides(world, pos, state);
        return j == 0 || i >= j;
    }

    @Override
    protected int getInputSignal(World world, BlockPos pos, BlockState state) {
        int i = super.getInputSignal(world, pos, state);
        Direction direction = state.get(FACING);
        BlockPos blockpos = pos.offset(direction);
        Block block = world.getBlockState(blockpos).getBlock();
        if (block.isAnalogSignalSource()) {
            i = block.getAnalogSignal(world, blockpos);
        } else if (i < 15 && block.isSolid()) {
            blockpos = blockpos.offset(direction);
            block = world.getBlockState(blockpos).getBlock();
            if (block.isAnalogSignalSource()) {
                i = block.getAnalogSignal(world, blockpos);
            } else if (block.getMaterial() == Material.AIR) {
                ItemFrameEntity itemframeentity = this.getItemFrame(world, direction, blockpos);
                if (itemframeentity != null) {
                    i = itemframeentity.getAnalogSignal();
                }
            }
        }

        return i;
    }

    private ItemFrameEntity getItemFrame(World world, Direction facing, BlockPos pos) {
        List<ItemFrameEntity> list = world.getEntitiesOfType(
            ItemFrameEntity.class, new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1), new Predicate<Entity>() {
                public boolean apply(Entity entity) {
                    return entity != null && entity.getHorizontalFacing() == facing;
                }
            }
        );
        return list.size() == 1 ? list.get(0) : null;
    }

    @Override
    public boolean use(World world, BlockPos pos, BlockState state, PlayerEntity player, Direction face, float faceX, float faceY, float faceZ) {
        if (!player.abilities.canModifyWorld) {
            return false;
        }

        state = state.next(MODE);
        world.playSound(
            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, "random.click", 0.3F, state.get(MODE) == ComparatorBlock.Mode.SUBTRACT ? 0.55F : 0.5F
        );
        world.setBlockState(pos, state, 2);
        this.updateOutputState(world, pos, state);
        return true;
    }

    @Override
    protected void checkOutputState(World world, BlockPos pos, BlockState state) {
        if (!world.willTickThisTick(pos, this)) {
            int i = this.calculateOutputSignal(world, pos, state);
            BlockEntity blockentity = world.getBlockEntity(pos);
            int j = blockentity instanceof ComparatorBlockEntity ? ((ComparatorBlockEntity)blockentity).getOutputSignal() : 0;
            if (i != j || this.isPowered(state) != this.shouldBePowered(world, pos, state)) {
                if (this.shouldPrioritize(world, pos, state)) {
                    world.scheduleTick(pos, this, 2, -1);
                } else {
                    world.scheduleTick(pos, this, 2, 0);
                }
            }
        }
    }

    private void updateOutputState(World world, BlockPos pos, BlockState state) {
        int i = this.calculateOutputSignal(world, pos, state);
        BlockEntity blockentity = world.getBlockEntity(pos);
        int j = 0;
        if (blockentity instanceof ComparatorBlockEntity) {
            ComparatorBlockEntity comparatorblockentity = (ComparatorBlockEntity)blockentity;
            j = comparatorblockentity.getOutputSignal();
            comparatorblockentity.setOutputSignal(i);
        }

        if (j != i || state.get(MODE) == ComparatorBlock.Mode.COMPARE) {
            boolean flag1 = this.shouldBePowered(world, pos, state);
            boolean flag = this.isPowered(state);
            if (flag && !flag1) {
                world.setBlockState(pos, state.set(POWERED, false), 2);
            } else if (!flag && flag1) {
                world.setBlockState(pos, state.set(POWERED, true), 2);
            }

            this.updateNeighbors(world, pos, state);
        }
    }

    @Override
    public void tick(World world, BlockPos pos, BlockState state, Random random) {
        if (this.powered) {
            world.setBlockState(pos, this.setPowered(state).set(POWERED, true), 4);
        }

        this.updateOutputState(world, pos, state);
    }

    @Override
    public void onAdded(World world, BlockPos pos, BlockState state) {
        super.onAdded(world, pos, state);
        world.setBlockEntity(pos, this.createBlockEntity(world, 0));
    }

    @Override
    public void onRemoved(World world, BlockPos pos, BlockState state) {
        super.onRemoved(world, pos, state);
        world.removeBlockEntity(pos);
        this.updateNeighbors(world, pos, state);
    }

    @Override
    public boolean doEvent(World world, BlockPos pos, BlockState state, int type, int data) {
        super.doEvent(world, pos, state, type, data);
        BlockEntity blockentity = world.getBlockEntity(pos);
        return blockentity != null && blockentity.doEvent(type, data);
    }

    @Override
    public BlockEntity createBlockEntity(World world, int metadata) {
        return new ComparatorBlockEntity();
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        return this.defaultState()
            .set(FACING, Direction.byIdHorizontal(metadata))
            .set(POWERED, (metadata & 8) > 0)
            .set(MODE, (metadata & 4) > 0 ? ComparatorBlock.Mode.SUBTRACT : ComparatorBlock.Mode.COMPARE);
    }

    @Override
    public int getMetadataFromState(BlockState state) {
        int i = 0;
        i |= state.get(FACING).getIdHorizontal();
        if (state.get(POWERED)) {
            i |= 8;
        }

        if (state.get(MODE) == ComparatorBlock.Mode.SUBTRACT) {
            i |= 4;
        }

        return i;
    }

    @Override
    protected StateDefinition createStateDefinition() {
        return new StateDefinition(this, FACING, MODE, POWERED);
    }

    @Override
    public BlockState getPlacementState(World world, BlockPos pos, Direction dir, float dx, float dy, float dz, int metadata, LivingEntity entity) {
        return this.defaultState().set(FACING, entity.getHorizontalFacing().getOpposite()).set(POWERED, false).set(MODE, ComparatorBlock.Mode.COMPARE);
    }

    public enum Mode implements StringSerializable {
        COMPARE("compare"),
        SUBTRACT("subtract");

        private final String key;

        Mode(String key) {
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
