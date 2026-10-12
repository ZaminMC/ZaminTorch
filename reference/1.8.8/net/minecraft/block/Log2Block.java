package net.minecraft.block;

import com.google.common.base.Predicate;
import java.util.List;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.EnumProperty;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public class Log2Block extends AbstractLogBlock {
    public static final EnumProperty<PlanksBlock.Variant> VARIANT = EnumProperty.of(
        "variant", PlanksBlock.Variant.class, new Predicate<PlanksBlock.Variant>() {
            public boolean apply(PlanksBlock.Variant variant) {
                return variant.getId() >= 4;
            }
        }
    );

    public Log2Block() {
        this.setDefaultState(this.stateDefinition.any().set(VARIANT, PlanksBlock.Variant.ACACIA).set(LOG_AXIS, AbstractLogBlock.LogAxis.Y));
    }

    @Override
    public MapColor getMapColor(BlockState state) {
        PlanksBlock.Variant planksblock$variant = state.get(VARIANT);
        switch ((AbstractLogBlock.LogAxis)state.get(LOG_AXIS)) {
            case X:
            case Z:
            case NONE:
            default:
                switch (planksblock$variant) {
                    case ACACIA:
                    default:
                        return MapColor.STONE;
                    case DARK_OAK:
                        return PlanksBlock.Variant.DARK_OAK.getColor();
                }
            case Y:
                return planksblock$variant.getColor();
        }
    }

    @Override
    public void addToCreativeMenu(Item item, CreativeModeTab tab, List<ItemStack> inventory) {
        inventory.add(new ItemStack(item, 1, PlanksBlock.Variant.ACACIA.getId() - 4));
        inventory.add(new ItemStack(item, 1, PlanksBlock.Variant.DARK_OAK.getId() - 4));
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        BlockState blockstate = this.defaultState().set(VARIANT, PlanksBlock.Variant.byId((metadata & 3) + 4));
        switch (metadata & 12) {
            case 0:
                blockstate = blockstate.set(LOG_AXIS, AbstractLogBlock.LogAxis.Y);
                break;
            case 4:
                blockstate = blockstate.set(LOG_AXIS, AbstractLogBlock.LogAxis.X);
                break;
            case 8:
                blockstate = blockstate.set(LOG_AXIS, AbstractLogBlock.LogAxis.Z);
                break;
            default:
                blockstate = blockstate.set(LOG_AXIS, AbstractLogBlock.LogAxis.NONE);
        }

        return blockstate;
    }

    @Override
    public int getMetadataFromState(BlockState state) {
        int i = 0;
        i |= state.get(VARIANT).getId() - 4;
        switch ((AbstractLogBlock.LogAxis)state.get(LOG_AXIS)) {
            case X:
                i |= 4;
                break;
            case Z:
                i |= 8;
                break;
            case NONE:
                i |= 12;
        }

        return i;
    }

    @Override
    protected StateDefinition createStateDefinition() {
        return new StateDefinition(this, VARIANT, LOG_AXIS);
    }

    @Override
    protected ItemStack getSilkTouchDrop(BlockState state) {
        return new ItemStack(Item.byBlock(this), 1, state.get(VARIANT).getId() - 4);
    }

    @Override
    public int getDropItemMetadata(BlockState state) {
        return state.get(VARIANT).getId() - 4;
    }
}
