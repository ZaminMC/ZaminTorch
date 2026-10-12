package net.minecraft.block;

import java.util.List;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.EnumProperty;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StringSerializable;

public class SandBlock extends FallingBlock {
    public static final EnumProperty<SandBlock.Variant> VARIANT = EnumProperty.of("variant", SandBlock.Variant.class);

    public SandBlock() {
        this.setDefaultState(this.stateDefinition.any().set(VARIANT, SandBlock.Variant.SAND));
    }

    @Override
    public int getDropItemMetadata(BlockState state) {
        return state.get(VARIANT).getId();
    }

    @Override
    public void addToCreativeMenu(Item item, CreativeModeTab tab, List<ItemStack> inventory) {
        for (SandBlock.Variant sandblock$variant : SandBlock.Variant.values()) {
            inventory.add(new ItemStack(item, 1, sandblock$variant.getId()));
        }
    }

    @Override
    public MapColor getMapColor(BlockState state) {
        return state.get(VARIANT).getColor();
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        return this.defaultState().set(VARIANT, SandBlock.Variant.byId(metadata));
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
        SAND(0, "sand", "default", MapColor.SAND),
        RED_SAND(1, "red_sand", "red", MapColor.ORANGE);

        private static final SandBlock.Variant[] BY_ID = new SandBlock.Variant[values().length];
        private final int id;
        private final String key;
        private final MapColor color;
        private final String name;

        Variant(int id, String key, String name, MapColor color) {
            this.id = id;
            this.key = key;
            this.color = color;
            this.name = name;
        }

        public int getId() {
            return this.id;
        }

        @Override
        public String toString() {
            return this.key;
        }

        public MapColor getColor() {
            return this.color;
        }

        public static SandBlock.Variant byId(int id) {
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
            for (SandBlock.Variant sandblock$variant : values()) {
                BY_ID[sandblock$variant.getId()] = sandblock$variant;
            }
        }
    }
}
