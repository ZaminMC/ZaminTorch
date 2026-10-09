package net.minecraft.block;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.property.EnumProperty;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.util.StringSerializable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public abstract class AbstractLogBlock extends AxisBlock {
    public static final EnumProperty<AbstractLogBlock.LogAxis> LOG_AXIS = EnumProperty.of("axis", AbstractLogBlock.LogAxis.class);

    public AbstractLogBlock() {
        super(Material.WOOD);
        this.setCreativeModeTab(CreativeModeTab.BUILDING_BLOCKS);
        this.setStrength(2.0F);
        this.setSounds(WOOD_SOUNDS);
    }

    @Override
    public void onRemoved(World world, BlockPos pos, BlockState state) {
        int i = 4;
        int j = i + 1;
        if (world.isAreaLoaded(pos.add(-j, -j, -j), pos.add(j, j, j))) {
            for (BlockPos blockpos : BlockPos.iterateRegion(pos.add(-i, -i, -i), pos.add(i, i, i))) {
                BlockState blockstate = world.getBlockState(blockpos);
                if (blockstate.getBlock().getMaterial() == Material.LEAVES && !blockstate.get(AbstractLeavesBlock.CHECK_DECAY)) {
                    world.setBlockState(blockpos, blockstate.set(AbstractLeavesBlock.CHECK_DECAY, true), 4);
                }
            }
        }
    }

    @Override
    public BlockState getPlacementState(World world, BlockPos pos, Direction dir, float dx, float dy, float dz, int metadata, LivingEntity entity) {
        return super.getPlacementState(world, pos, dir, dx, dy, dz, metadata, entity).set(LOG_AXIS, AbstractLogBlock.LogAxis.byAxis(dir.getAxis()));
    }

    public enum LogAxis implements StringSerializable {
        X("x"),
        Y("y"),
        Z("z"),
        NONE("none");

        private final String key;

        LogAxis(String key) {
            this.key = key;
        }

        @Override
        public String toString() {
            return this.key;
        }

        public static AbstractLogBlock.LogAxis byAxis(Direction.Axis axis) {
            switch (axis) {
                case X:
                    return X;
                case Y:
                    return Y;
                case Z:
                    return Z;
                default:
                    return NONE;
            }
        }

        @Override
        public String serializeToString() {
            return this.key;
        }
    }
}
