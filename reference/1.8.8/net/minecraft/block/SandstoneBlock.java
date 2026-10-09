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

public class SandstoneBlock extends Block {
    public static final EnumProperty<SandstoneBlock.Type> TYPE = EnumProperty.of("type", SandstoneBlock.Type.class);

    public SandstoneBlock() {
        super(Material.STONE);
        this.setDefaultState(this.stateDefinition.any().set(TYPE, SandstoneBlock.Type.DEFAULT));
        this.setCreativeModeTab(CreativeModeTab.BUILDING_BLOCKS);
    }

    @Override
    public int getDropItemMetadata(BlockState state) {
        return state.get(TYPE).getId();
    }

    @Override
    public void addToCreativeMenu(Item item, CreativeModeTab tab, List<ItemStack> inventory) {
        for (SandstoneBlock.Type sandstoneblock$type : SandstoneBlock.Type.values()) {
            inventory.add(new ItemStack(item, 1, sandstoneblock$type.getId()));
        }
    }

    @Override
    public MapColor getMapColor(BlockState state) {
        return MapColor.SAND;
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        return this.defaultState().set(TYPE, SandstoneBlock.Type.byId(metadata));
    }

    @Override
    public int getMetadataFromState(BlockState state) {
        return state.get(TYPE).getId();
    }

    @Override
    protected StateDefinition createStateDefinition() {
        return new StateDefinition(this, TYPE);
    }

    public enum Type implements StringSerializable {
        DEFAULT(0, "sandstone", "default"),
        CHISELED(1, "chiseled_sandstone", "chiseled"),
        SMOOTH(2, "smooth_sandstone", "smooth");

        private static final SandstoneBlock.Type[] BY_ID = new SandstoneBlock.Type[values().length];
        private final int id;
        private final String key;
        private final String name;

        Type(int id, String key, String name) {
            this.id = id;
            this.key = key;
            this.name = name;
        }

        public int getId() {
            return this.id;
        }

        @Override
        public String toString() {
            return this.key;
        }

        public static SandstoneBlock.Type byId(int id) {
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
            for (SandstoneBlock.Type sandstoneblock$type : values()) {
                BY_ID[sandstoneblock$type.getId()] = sandstoneblock$type;
            }
        }
    }
}
