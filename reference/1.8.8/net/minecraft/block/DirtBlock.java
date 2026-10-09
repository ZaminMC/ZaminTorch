package net.minecraft.block;

import java.util.List;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.BooleanProperty;
import net.minecraft.block.state.property.EnumProperty;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StringSerializable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

public class DirtBlock extends Block {
    public static final EnumProperty<DirtBlock.Variant> VARIANT = EnumProperty.of("variant", DirtBlock.Variant.class);
    public static final BooleanProperty SNOWY = BooleanProperty.of("snowy");

    protected DirtBlock() {
        super(Material.DIRT);
        this.setDefaultState(this.stateDefinition.any().set(VARIANT, DirtBlock.Variant.DIRT).set(SNOWY, false));
        this.setCreativeModeTab(CreativeModeTab.BUILDING_BLOCKS);
    }

    @Override
    public MapColor getMapColor(BlockState state) {
        return state.get(VARIANT).getColor();
    }

    @Override
    public BlockState resolveVirtualProperties(BlockState state, WorldView world, BlockPos pos) {
        if (state.get(VARIANT) == DirtBlock.Variant.PODZOL) {
            Block block = world.getBlockState(pos.up()).getBlock();
            state = state.set(SNOWY, block == Blocks.SNOW || block == Blocks.SNOW_LAYER);
        }

        return state;
    }

    @Override
    public void addToCreativeMenu(Item item, CreativeModeTab tab, List<ItemStack> inventory) {
        inventory.add(new ItemStack(this, 1, DirtBlock.Variant.DIRT.getId()));
        inventory.add(new ItemStack(this, 1, DirtBlock.Variant.COARSE_DIRT.getId()));
        inventory.add(new ItemStack(this, 1, DirtBlock.Variant.PODZOL.getId()));
    }

    @Override
    public int getPickItemMetadata(World world, BlockPos pos) {
        BlockState blockstate = world.getBlockState(pos);
        return blockstate.getBlock() != this ? 0 : blockstate.get(VARIANT).getId();
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        return this.defaultState().set(VARIANT, DirtBlock.Variant.byId(metadata));
    }

    @Override
    public int getMetadataFromState(BlockState state) {
        return state.get(VARIANT).getId();
    }

    @Override
    protected StateDefinition createStateDefinition() {
        return new StateDefinition(this, VARIANT, SNOWY);
    }

    @Override
    public int getDropItemMetadata(BlockState state) {
        DirtBlock.Variant dirtblock$variant = state.get(VARIANT);
        if (dirtblock$variant == DirtBlock.Variant.PODZOL) {
            dirtblock$variant = DirtBlock.Variant.DIRT;
        }

        return dirtblock$variant.getId();
    }

    public enum Variant implements StringSerializable {
        DIRT(0, "dirt", "default", MapColor.DIRT),
        COARSE_DIRT(1, "coarse_dirt", "coarse", MapColor.DIRT),
        PODZOL(2, "podzol", MapColor.SPRUCE);

        private static final DirtBlock.Variant[] BY_ID = new DirtBlock.Variant[values().length];
        private final int id;
        private final String key;
        private final String name;
        private final MapColor color;

        Variant(int id, String name, MapColor color) {
            this(id, name, name, color);
        }

        Variant(int id, String name, String key, MapColor color) {
            this.id = id;
            this.key = name;
            this.name = key;
            this.color = color;
        }

        public int getId() {
            return this.id;
        }

        public String getName() {
            return this.name;
        }

        public MapColor getColor() {
            return this.color;
        }

        @Override
        public String toString() {
            return this.key;
        }

        public static DirtBlock.Variant byId(int id) {
            if (id < 0 || id >= BY_ID.length) {
                id = 0;
            }

            return BY_ID[id];
        }

        @Override
        public String serializeToString() {
            return this.key;
        }

        static {
            for (DirtBlock.Variant dirtblock$variant : values()) {
                BY_ID[dirtblock$variant.getId()] = dirtblock$variant;
            }
        }
    }
}
