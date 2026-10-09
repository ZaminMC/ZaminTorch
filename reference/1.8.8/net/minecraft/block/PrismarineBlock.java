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
import net.minecraft.locale.I18n;
import net.minecraft.util.StringSerializable;

public class PrismarineBlock extends Block {
    public static final EnumProperty<PrismarineBlock.Variant> VARIANT = EnumProperty.of("variant", PrismarineBlock.Variant.class);
    public static final int ROUGH_VARIANT = PrismarineBlock.Variant.ROUGH.getId();
    public static final int BRICKS_VARIANT = PrismarineBlock.Variant.BRICKS.getId();
    public static final int DARK_VARIANT = PrismarineBlock.Variant.DARK.getId();

    public PrismarineBlock() {
        super(Material.STONE);
        this.setDefaultState(this.stateDefinition.any().set(VARIANT, PrismarineBlock.Variant.ROUGH));
        this.setCreativeModeTab(CreativeModeTab.BUILDING_BLOCKS);
    }

    @Override
    public String getName() {
        return I18n.translate(this.getTranslationKey() + "." + PrismarineBlock.Variant.ROUGH.getName() + ".name");
    }

    @Override
    public MapColor getMapColor(BlockState state) {
        return state.get(VARIANT) == PrismarineBlock.Variant.ROUGH ? MapColor.CYAN : MapColor.DIAMOND;
    }

    @Override
    public int getDropItemMetadata(BlockState state) {
        return state.get(VARIANT).getId();
    }

    @Override
    public int getMetadataFromState(BlockState state) {
        return state.get(VARIANT).getId();
    }

    @Override
    protected StateDefinition createStateDefinition() {
        return new StateDefinition(this, VARIANT);
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        return this.defaultState().set(VARIANT, PrismarineBlock.Variant.byId(metadata));
    }

    @Override
    public void addToCreativeMenu(Item item, CreativeModeTab tab, List<ItemStack> inventory) {
        inventory.add(new ItemStack(item, 1, ROUGH_VARIANT));
        inventory.add(new ItemStack(item, 1, BRICKS_VARIANT));
        inventory.add(new ItemStack(item, 1, DARK_VARIANT));
    }

    public enum Variant implements StringSerializable {
        ROUGH(0, "prismarine", "rough"),
        BRICKS(1, "prismarine_bricks", "bricks"),
        DARK(2, "dark_prismarine", "dark");

        private static final PrismarineBlock.Variant[] BY_ID = new PrismarineBlock.Variant[values().length];
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

        public static PrismarineBlock.Variant byId(int id) {
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
            for (PrismarineBlock.Variant prismarineblock$variant : values()) {
                BY_ID[prismarineblock$variant.getId()] = prismarineblock$variant;
            }
        }
    }
}
