package net.minecraft.block;

import java.util.List;
import java.util.Random;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.BooleanProperty;
import net.minecraft.block.state.property.EnumProperty;
import net.minecraft.block.state.property.Property;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.locale.I18n;
import net.minecraft.util.StringSerializable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public abstract class RedSandstoneSlab extends SlabBlock {
    public static final BooleanProperty SEAMLESS = BooleanProperty.of("seamless");
    public static final EnumProperty<RedSandstoneSlab.Variant> VARIANT = EnumProperty.of("variant", RedSandstoneSlab.Variant.class);

    public RedSandstoneSlab() {
        super(Material.STONE);
        BlockState blockstate = this.stateDefinition.any();
        if (this.isDouble()) {
            blockstate = blockstate.set(SEAMLESS, false);
        } else {
            blockstate = blockstate.set(HALF, SlabBlock.Half.BOTTOM);
        }

        this.setDefaultState(blockstate.set(VARIANT, RedSandstoneSlab.Variant.RED_SANDSTONE));
        this.setCreativeModeTab(CreativeModeTab.BUILDING_BLOCKS);
    }

    @Override
    public String getName() {
        return I18n.translate(this.getTranslationKey() + ".red_sandstone.name");
    }

    @Override
    public Item getDropItem(BlockState state, Random random, int fortuneLevel) {
        return Item.byBlock(Blocks.RED_SANDSTONE_SLAB);
    }

    @Override
    public Item getPickItem(World world, BlockPos pos) {
        return Item.byBlock(Blocks.RED_SANDSTONE_SLAB);
    }

    @Override
    public String getName(int variant) {
        return super.getTranslationKey() + "." + RedSandstoneSlab.Variant.byId(variant).getKey();
    }

    @Override
    public Property<?> getVariantProperty() {
        return VARIANT;
    }

    @Override
    public Object getVariant(ItemStack item) {
        return RedSandstoneSlab.Variant.byId(item.getMetadata() & 7);
    }

    @Override
    public void addToCreativeMenu(Item item, CreativeModeTab tab, List<ItemStack> inventory) {
        if (item != Item.byBlock(Blocks.DOUBLE_RED_SANDSTONE_SLAB)) {
            for (RedSandstoneSlab.Variant redsandstoneslab$variant : RedSandstoneSlab.Variant.values()) {
                inventory.add(new ItemStack(item, 1, redsandstoneslab$variant.getId()));
            }
        }
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        BlockState blockstate = this.defaultState().set(VARIANT, RedSandstoneSlab.Variant.byId(metadata & 7));
        if (this.isDouble()) {
            blockstate = blockstate.set(SEAMLESS, (metadata & 8) != 0);
        } else {
            blockstate = blockstate.set(HALF, (metadata & 8) == 0 ? SlabBlock.Half.BOTTOM : SlabBlock.Half.TOP);
        }

        return blockstate;
    }

    @Override
    public int getMetadataFromState(BlockState state) {
        int i = 0;
        i |= state.get(VARIANT).getId();
        if (this.isDouble()) {
            if (state.get(SEAMLESS)) {
                i |= 8;
            }
        } else if (state.get(HALF) == SlabBlock.Half.TOP) {
            i |= 8;
        }

        return i;
    }

    @Override
    protected StateDefinition createStateDefinition() {
        return this.isDouble() ? new StateDefinition(this, SEAMLESS, VARIANT) : new StateDefinition(this, HALF, VARIANT);
    }

    @Override
    public MapColor getMapColor(BlockState state) {
        return state.get(VARIANT).getColor();
    }

    @Override
    public int getDropItemMetadata(BlockState state) {
        return state.get(VARIANT).getId();
    }

    public enum Variant implements StringSerializable {
        RED_SANDSTONE(0, "red_sandstone", SandBlock.Variant.RED_SAND.getColor());

        private static final RedSandstoneSlab.Variant[] BY_ID = new RedSandstoneSlab.Variant[values().length];
        private final int id;
        private final String name;
        private final MapColor color;

        Variant(int id, String name, MapColor color) {
            this.id = id;
            this.name = name;
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
            return this.name;
        }

        public static RedSandstoneSlab.Variant byId(int id) {
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
            return this.name;
        }

        static {
            for (RedSandstoneSlab.Variant redsandstoneslab$variant : values()) {
                BY_ID[redsandstoneslab$variant.getId()] = redsandstoneslab$variant;
            }
        }
    }
}
