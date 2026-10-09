package net.minecraft.block;

import java.util.List;
import java.util.Random;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.EnumProperty;
import net.minecraft.block.state.property.Property;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public abstract class WoodenSlabBlock extends SlabBlock {
    public static final EnumProperty<PlanksBlock.Variant> VARIANT = EnumProperty.of("variant", PlanksBlock.Variant.class);

    public WoodenSlabBlock() {
        super(Material.WOOD);
        BlockState blockstate = this.stateDefinition.any();
        if (!this.isDouble()) {
            blockstate = blockstate.set(HALF, SlabBlock.Half.BOTTOM);
        }

        this.setDefaultState(blockstate.set(VARIANT, PlanksBlock.Variant.OAK));
        this.setCreativeModeTab(CreativeModeTab.BUILDING_BLOCKS);
    }

    @Override
    public MapColor getMapColor(BlockState state) {
        return state.get(VARIANT).getColor();
    }

    @Override
    public Item getDropItem(BlockState state, Random random, int fortuneLevel) {
        return Item.byBlock(Blocks.WOODEN_SLAB);
    }

    @Override
    public Item getPickItem(World world, BlockPos pos) {
        return Item.byBlock(Blocks.WOODEN_SLAB);
    }

    @Override
    public String getName(int variant) {
        return super.getTranslationKey() + "." + PlanksBlock.Variant.byId(variant).getName();
    }

    @Override
    public Property<?> getVariantProperty() {
        return VARIANT;
    }

    @Override
    public Object getVariant(ItemStack item) {
        return PlanksBlock.Variant.byId(item.getMetadata() & 7);
    }

    @Override
    public void addToCreativeMenu(Item item, CreativeModeTab tab, List<ItemStack> inventory) {
        if (item != Item.byBlock(Blocks.DOUBLE_WOODEN_SLAB)) {
            for (PlanksBlock.Variant planksblock$variant : PlanksBlock.Variant.values()) {
                inventory.add(new ItemStack(item, 1, planksblock$variant.getId()));
            }
        }
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        BlockState blockstate = this.defaultState().set(VARIANT, PlanksBlock.Variant.byId(metadata & 7));
        if (!this.isDouble()) {
            blockstate = blockstate.set(HALF, (metadata & 8) == 0 ? SlabBlock.Half.BOTTOM : SlabBlock.Half.TOP);
        }

        return blockstate;
    }

    @Override
    public int getMetadataFromState(BlockState state) {
        int i = 0;
        i |= state.get(VARIANT).getId();
        if (!this.isDouble() && state.get(HALF) == SlabBlock.Half.TOP) {
            i |= 8;
        }

        return i;
    }

    @Override
    protected StateDefinition createStateDefinition() {
        return this.isDouble() ? new StateDefinition(this, VARIANT) : new StateDefinition(this, HALF, VARIANT);
    }

    @Override
    public int getDropItemMetadata(BlockState state) {
        return state.get(VARIANT).getId();
    }
}
