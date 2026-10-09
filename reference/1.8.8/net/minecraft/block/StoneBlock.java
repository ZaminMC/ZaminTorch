package net.minecraft.block;

import java.util.List;
import java.util.Random;
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

public class StoneBlock extends Block {
    public static final EnumProperty<StoneBlock.Variant> VARIANT = EnumProperty.of("variant", StoneBlock.Variant.class);

    public StoneBlock() {
        super(Material.STONE);
        this.setDefaultState(this.stateDefinition.any().set(VARIANT, StoneBlock.Variant.STONE));
        this.setCreativeModeTab(CreativeModeTab.BUILDING_BLOCKS);
    }

    @Override
    public String getName() {
        return I18n.translate(this.getTranslationKey() + "." + StoneBlock.Variant.STONE.getName() + ".name");
    }

    @Override
    public MapColor getMapColor(BlockState state) {
        return state.get(VARIANT).getColor();
    }

    @Override
    public Item getDropItem(BlockState state, Random random, int fortuneLevel) {
        return state.get(VARIANT) == StoneBlock.Variant.STONE ? Item.byBlock(Blocks.COBBLESTONE) : Item.byBlock(Blocks.STONE);
    }

    @Override
    public int getDropItemMetadata(BlockState state) {
        return state.get(VARIANT).getId();
    }

    @Override
    public void addToCreativeMenu(Item item, CreativeModeTab tab, List<ItemStack> inventory) {
        for (StoneBlock.Variant stoneblock$variant : StoneBlock.Variant.values()) {
            inventory.add(new ItemStack(item, 1, stoneblock$variant.getId()));
        }
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        return this.defaultState().set(VARIANT, StoneBlock.Variant.byId(metadata));
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
        STONE(0, MapColor.STONE, "stone"),
        GRANITE(1, MapColor.DIRT, "granite"),
        GRANITE_SMOOTH(2, MapColor.DIRT, "smooth_granite", "graniteSmooth"),
        DIORITE(3, MapColor.QUARTZ, "diorite"),
        DIORITE_SMOOTH(4, MapColor.QUARTZ, "smooth_diorite", "dioriteSmooth"),
        ANDESITE(5, MapColor.STONE, "andesite"),
        ANDESITE_SMOOTH(6, MapColor.STONE, "smooth_andesite", "andesiteSmooth");

        private static final StoneBlock.Variant[] BY_ID = new StoneBlock.Variant[values().length];
        private final int id;
        private final String key;
        private final String name;
        private final MapColor color;

        Variant(int id, MapColor color, String name) {
            this(id, color, name, name);
        }

        Variant(int id, MapColor color, String name, String key) {
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

        public static StoneBlock.Variant byId(int id) {
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
            for (StoneBlock.Variant stoneblock$variant : values()) {
                BY_ID[stoneblock$variant.getId()] = stoneblock$variant;
            }
        }
    }
}
