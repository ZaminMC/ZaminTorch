package net.minecraft.block;

import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class HayBlock extends AxisBlock {
    public HayBlock() {
        super(Material.GRASS, MapColor.YELLOW);
        this.setDefaultState(this.stateDefinition.any().set(AXIS, Direction.Axis.Y));
        this.setCreativeModeTab(CreativeModeTab.BUILDING_BLOCKS);
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        Direction.Axis direction$axis = Direction.Axis.Y;
        int i = metadata & 12;
        if (i == 4) {
            direction$axis = Direction.Axis.X;
        } else if (i == 8) {
            direction$axis = Direction.Axis.Z;
        }

        return this.defaultState().set(AXIS, direction$axis);
    }

    @Override
    public int getMetadataFromState(BlockState state) {
        int i = 0;
        Direction.Axis direction$axis = state.get(AXIS);
        if (direction$axis == Direction.Axis.X) {
            i |= 4;
        } else if (direction$axis == Direction.Axis.Z) {
            i |= 8;
        }

        return i;
    }

    @Override
    protected StateDefinition createStateDefinition() {
        return new StateDefinition(this, AXIS);
    }

    @Override
    protected ItemStack getSilkTouchDrop(BlockState state) {
        return new ItemStack(Item.byBlock(this), 1, 0);
    }

    @Override
    public BlockState getPlacementState(World world, BlockPos pos, Direction dir, float dx, float dy, float dz, int metadata, LivingEntity entity) {
        return super.getPlacementState(world, pos, dir, dx, dy, dz, metadata, entity).set(AXIS, dir.getAxis());
    }
}
