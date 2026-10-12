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

public class StonebrickBlock extends Block {
    public static final EnumProperty<StonebrickBlock.Variant> VARIANT = EnumProperty.of("variant", StonebrickBlock.Variant.class);
    public static final int DEFAULT_ID = StonebrickBlock.Variant.DEFAULT.getId();
    public static final int MOSSY_ID = StonebrickBlock.Variant.MOSSY.getId();
    public static final int CRACKED_ID = StonebrickBlock.Variant.CRACKED.getId();
    public static final int CHISELED_ID = StonebrickBlock.Variant.CHISELED.getId();

    public StonebrickBlock() {
        super(Material.STONE);
        this.setDefaultState(this.stateDefinition.any().set(VARIANT, StonebrickBlock.Variant.DEFAULT));
        this.setCreativeModeTab(CreativeModeTab.BUILDING_BLOCKS);
    }

    @Override
    public int getDropItemMetadata(BlockState state) {
        return state.get(VARIANT).getId();
    }

    @Override
    public void addToCreativeMenu(Item item, CreativeModeTab tab, List<ItemStack> inventory) {
        for (StonebrickBlock.Variant stonebrickblock$variant : StonebrickBlock.Variant.values()) {
            inventory.add(new ItemStack(item, 1, stonebrickblock$variant.getId()));
        }
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        return this.defaultState().set(VARIANT, StonebrickBlock.Variant.byId(metadata));
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
        DEFAULT(0, "stonebrick", "default"),
        MOSSY(1, "mossy_stonebrick", "mossy"),
        CRACKED(2, "cracked_stonebrick", "cracked"),
        CHISELED(3, "chiseled_stonebrick", "chiseled");

        private static final StonebrickBlock.Variant[] BY_ID = new StonebrickBlock.Variant[values().length];
        private final int id;
        private final String key;
        private final String name;

        Variant(int id, String key, String name) {
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

        public static StonebrickBlock.Variant byId(int id) {
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
            for (StonebrickBlock.Variant stonebrickblock$variant : values()) {
                BY_ID[stonebrickblock$variant.getId()] = stonebrickblock$variant;
            }
        }
    }
}
