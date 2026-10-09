package net.minecraft.block;

import java.util.List;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.EnumProperty;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StringSerializable;

public class PlanksBlock extends Block {
    public static final EnumProperty<PlanksBlock.Variant> VARIANT = EnumProperty.of("variant", PlanksBlock.Variant.class);

    public PlanksBlock() {
        super(Material.WOOD);
        this.setDefaultState(this.stateDefinition.any().set(VARIANT, PlanksBlock.Variant.OAK));
        this.setCreativeModeTab(CreativeModeTab.BUILDING_BLOCKS);
    }

    @Override
    public int getDropItemMetadata(BlockState state) {
        return state.get(VARIANT).getId();
    }

    @Override
    public void addToCreativeMenu(Item item, CreativeModeTab tab, List<ItemStack> inventory) {
        for (PlanksBlock.Variant planksblock$variant : PlanksBlock.Variant.values()) {
            inventory.add(new ItemStack(item, 1, planksblock$variant.getId()));
        }
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        return this.defaultState().set(VARIANT, PlanksBlock.Variant.byId(metadata));
    }

    @Override
    public MapColor getMapColor(BlockState state) {
        return state.get(VARIANT).getColor();
    }

    @Override
    public int getMetadataFromState(BlockState state) {
        return state.get(VARIANT).getId();
    }

    @Override
    protected StateDefinition createStateDefinition() {
        return new StateDefinition(this, VARIANT);
    }

    public enum Variant implements StringSerializable {
        OAK(0, "oak", MapColor.WOOD),
        SPRUCE(1, "spruce", MapColor.SPRUCE),
        BIRCH(2, "birch", MapColor.SAND),
        JUNGLE(3, "jungle", MapColor.DIRT),
        ACACIA(4, "acacia", MapColor.ORANGE),
        DARK_OAK(5, "dark_oak", "big_oak", MapColor.BROWN);

        private static final PlanksBlock.Variant[] BY_ID = new PlanksBlock.Variant[values().length];
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

        public MapColor getColor() {
            return this.color;
        }

        @Override
        public String toString() {
            return this.key;
        }

        public static PlanksBlock.Variant byId(int id) {
            if (id < 0 || id >= BY_ID.length) {
                id = 0;
            }

            return BY_ID[id];
        }

        @Override
        public String serializeToString() {
            return this.key;
        }

        public String getName() {
            return this.name;
        }

        static {
            for (PlanksBlock.Variant planksblock$variant : values()) {
                BY_ID[planksblock$variant.getId()] = planksblock$variant;
            }
        }
    }
}
