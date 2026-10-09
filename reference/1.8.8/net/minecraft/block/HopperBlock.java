package net.minecraft.block;

import com.google.common.base.Predicate;
import java.util.List;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.BooleanProperty;
import net.minecraft.block.state.property.DirectionProperty;
import net.minecraft.client.render.block.BlockLayer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.inventory.InventoryUtils;
import net.minecraft.inventory.menu.InventoryMenu;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.item.ItemStack;
import net.minecraft.stat.Stats;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

public class HopperBlock extends BlockWithBlockEntity {
    public static final DirectionProperty FACING = DirectionProperty.of("facing", new Predicate<Direction>() {
        public boolean apply(Direction direction) {
            return direction != Direction.UP;
        }
    });
    public static final BooleanProperty ENABLED = BooleanProperty.of("enabled");

    public HopperBlock() {
        super(Material.IRON, MapColor.STONE);
        this.setDefaultState(this.stateDefinition.any().set(FACING, Direction.DOWN).set(ENABLED, true));
        this.setCreativeModeTab(CreativeModeTab.REDSTONE);
        this.setShape(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    public void updateShape(WorldView world, BlockPos pos) {
        this.setShape(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    public void addCollisions(World world, BlockPos pos, BlockState state, Box shape, List<Box> collisions, Entity entity) {
        this.setShape(0.0F, 0.0F, 0.0F, 1.0F, 0.625F, 1.0F);
        super.addCollisions(world, pos, state, shape, collisions, entity);
        float f = 0.125F;
        this.setShape(0.0F, 0.0F, 0.0F, f, 1.0F, 1.0F);
        super.addCollisions(world, pos, state, shape, collisions, entity);
        this.setShape(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, f);
        super.addCollisions(world, pos, state, shape, collisions, entity);
        this.setShape(1.0F - f, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
        super.addCollisions(world, pos, state, shape, collisions, entity);
        this.setShape(0.0F, 0.0F, 1.0F - f, 1.0F, 1.0F, 1.0F);
        super.addCollisions(world, pos, state, shape, collisions, entity);
        this.setShape(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    public BlockState getPlacementState(World world, BlockPos pos, Direction dir, float dx, float dy, float dz, int metadata, LivingEntity entity) {
        Direction direction = dir.getOpposite();
        if (direction == Direction.UP) {
            direction = Direction.DOWN;
        }

        return this.defaultState().set(FACING, direction).set(ENABLED, true);
    }

    @Override
    public BlockEntity createBlockEntity(World world, int metadata) {
        return new HopperBlockEntity();
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity entity, ItemStack item) {
        super.onPlaced(world, pos, state, entity, item);
        if (item.hasCustomHoverName()) {
            BlockEntity blockentity = world.getBlockEntity(pos);
            if (blockentity instanceof HopperBlockEntity) {
                ((HopperBlockEntity)blockentity).setCustomName(item.getHoverName());
            }
        }
    }

    @Override
    public void onAdded(World world, BlockPos pos, BlockState state) {
        this.updateEnabled(world, pos, state);
    }

    @Override
    public boolean use(World world, BlockPos pos, BlockState state, PlayerEntity player, Direction face, float faceX, float faceY, float faceZ) {
        if (world.isClient) {
            return true;
        }

        BlockEntity blockentity = world.getBlockEntity(pos);
        if (blockentity instanceof HopperBlockEntity) {
            player.openChestMenu((HopperBlockEntity)blockentity);
            player.incrementStat(Stats.HOPPERS_INSPECTED);
        }

        return true;
    }

    @Override
    public void neighborChanged(World world, BlockPos pos, BlockState state, Block neighborBlock) {
        this.updateEnabled(world, pos, state);
    }

    private void updateEnabled(World world, BlockPos pos, BlockState state) {
        boolean flag = !world.hasNeighborSignal(pos);
        if (flag != state.get(ENABLED)) {
            world.setBlockState(pos, state.set(ENABLED, flag), 4);
        }
    }

    @Override
    public void onRemoved(World world, BlockPos pos, BlockState state) {
        BlockEntity blockentity = world.getBlockEntity(pos);
        if (blockentity instanceof HopperBlockEntity) {
            InventoryUtils.dropItems(world, pos, (HopperBlockEntity)blockentity);
            world.updateNeighborComparators(pos, this);
        }

        super.onRemoved(world, pos, state);
    }

    @Override
    public int getRenderType() {
        return 3;
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
    public boolean shouldRenderFace(WorldView world, BlockPos pos, Direction face) {
        return true;
    }

    public static Direction getFacing(int metadata) {
        return Direction.byId(metadata & 7);
    }

    public static boolean isEnabled(int metadata) {
        return (metadata & 8) != 8;
    }

    @Override
    public boolean isAnalogSignalSource() {
        return true;
    }

    @Override
    public int getAnalogSignal(World world, BlockPos pos) {
        return InventoryMenu.getAnalogSignal(world.getBlockEntity(pos));
    }

    @Override
    public BlockLayer getRenderLayer() {
        return BlockLayer.CUTOUT_MIPPED;
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        return this.defaultState().set(FACING, getFacing(metadata)).set(ENABLED, isEnabled(metadata));
    }

    @Override
    public int getMetadataFromState(BlockState state) {
        int i = 0;
        i |= state.get(FACING).getId();
        if (!state.get(ENABLED)) {
            i |= 8;
        }

        return i;
    }

    @Override
    protected StateDefinition createStateDefinition() {
        return new StateDefinition(this, FACING, ENABLED);
    }
}
