package net.minecraft.block;

import java.util.List;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.EnumProperty;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StringSerializable;

public class RedSandstoneBlock extends Block {
    public static final EnumProperty<RedSandstoneBlock.Type> TYPE = EnumProperty.of("type", RedSandstoneBlock.Type.class);

    public RedSandstoneBlock() {
        super(Material.STONE, SandBlock.Variant.RED_SAND.getColor());
        this.setDefaultState(this.stateDefinition.any().set(TYPE, RedSandstoneBlock.Type.DEFAULT));
        this.setCreativeModeTab(CreativeModeTab.BUILDING_BLOCKS);
    }

    @Override
    public int getDropItemMetadata(BlockState state) {
        return state.get(TYPE).getId();
    }

    @Override
    public void addToCreativeMenu(Item item, CreativeModeTab tab, List<ItemStack> inventory) {
        for (RedSandstoneBlock.Type redsandstoneblock$type : RedSandstoneBlock.Type.values()) {
            inventory.add(new ItemStack(item, 1, redsandstoneblock$type.getId()));
        }
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        return this.defaultState().set(TYPE, RedSandstoneBlock.Type.byId(metadata));
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
        DEFAULT(0, "red_sandstone", "default"),
        CHISELED(1, "chiseled_red_sandstone", "chiseled"),
        SMOOTH(2, "smooth_red_sandstone", "smooth");

        private static final RedSandstoneBlock.Type[] BY_ID = new RedSandstoneBlock.Type[values().length];
        private final int id;
        private final String name;
        private final String key;

        Type(int id, String name, String key) {
            this.id = id;
            this.name = name;
            this.key = key;
        }

        public int getId() {
            return this.id;
        }

        @Override
        public String toString() {
            return this.name;
        }

        public static RedSandstoneBlock.Type byId(int id) {
            if (id < 0 || id >= BY_ID.length) {
                id = 0;
            }

            return BY_ID[id];
        }

        @Override
        public String serializeToString() {
            return this.name;
        }

        public String getKey() {
            return this.key;
        }

        static {
            for (RedSandstoneBlock.Type redsandstoneblock$type : values()) {
                BY_ID[redsandstoneblock$type.getId()] = redsandstoneblock$type;
            }
        }
    }
}
