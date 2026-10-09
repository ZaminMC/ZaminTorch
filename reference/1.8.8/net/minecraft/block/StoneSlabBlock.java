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
import net.minecraft.util.StringSerializable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public abstract class StoneSlabBlock extends SlabBlock {
    public static final BooleanProperty SEAMLESS = BooleanProperty.of("seamless");
    public static final EnumProperty<StoneSlabBlock.Variant> VARIANT = EnumProperty.of("variant", StoneSlabBlock.Variant.class);

    public StoneSlabBlock() {
        super(Material.STONE);
        BlockState blockstate = this.stateDefinition.any();
        if (this.isDouble()) {
            blockstate = blockstate.set(SEAMLESS, false);
        } else {
            blockstate = blockstate.set(HALF, SlabBlock.Half.BOTTOM);
        }

        this.setDefaultState(blockstate.set(VARIANT, StoneSlabBlock.Variant.STONE));
        this.setCreativeModeTab(CreativeModeTab.BUILDING_BLOCKS);
    }

    @Override
    public Item getDropItem(BlockState state, Random random, int fortuneLevel) {
        return Item.byBlock(Blocks.STONE_SLAB);
    }

    @Override
    public Item getPickItem(World world, BlockPos pos) {
        return Item.byBlock(Blocks.STONE_SLAB);
    }

    @Override
    public String getName(int variant) {
        return super.getTranslationKey() + "." + StoneSlabBlock.Variant.byId(variant).getName();
    }

    @Override
    public Property<?> getVariantProperty() {
        return VARIANT;
    }

    @Override
    public Object getVariant(ItemStack item) {
        return StoneSlabBlock.Variant.byId(item.getMetadata() & 7);
    }

    @Override
    public void addToCreativeMenu(Item item, CreativeModeTab tab, List<ItemStack> inventory) {
        if (item != Item.byBlock(Blocks.DOUBLE_STONE_SLAB)) {
            for (StoneSlabBlock.Variant stoneslabblock$variant : StoneSlabBlock.Variant.values()) {
                if (stoneslabblock$variant != StoneSlabBlock.Variant.WOOD) {
                    inventory.add(new ItemStack(item, 1, stoneslabblock$variant.getId()));
                }
            }
        }
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        BlockState blockstate = this.defaultState().set(VARIANT, StoneSlabBlock.Variant.byId(metadata & 7));
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
    public int getDropItemMetadata(BlockState state) {
        return state.get(VARIANT).getId();
    }

    @Override
    public MapColor getMapColor(BlockState state) {
        return state.get(VARIANT).getColor();
    }

    public enum Variant implements StringSerializable {
        STONE(0, MapColor.STONE, "stone"),
        SAND(1, MapColor.SAND, "sandstone", "sand"),
        WOOD(2, MapColor.WOOD, "wood_old", "wood"),
        COBBLESTONE(3, MapColor.STONE, "cobblestone", "cobble"),
        BRICK(4, MapColor.RED, "brick"),
        SMOOTHBRICK(5, MapColor.STONE, "stone_brick", "smoothStoneBrick"),
        NETHERBRICK(6, MapColor.NETHER, "nether_brick", "netherBrick"),
        QUARTZ(7, MapColor.QUARTZ, "quartz");

        private static final StoneSlabBlock.Variant[] BY_ID = new StoneSlabBlock.Variant[values().length];
        private final int id;
        private final MapColor color;
        private final String key;
        private final String name;

        Variant(int id, MapColor color, String key) {
            this(id, color, key, key);
        }

        Variant(int id, MapColor color, String key, String name) {
            this.id = id;
            this.color = color;
            this.key = key;
            this.name = name;
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

        public static StoneSlabBlock.Variant byId(int id) {
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
            for (StoneSlabBlock.Variant stoneslabblock$variant : values()) {
                BY_ID[stoneslabblock$variant.getId()] = stoneslabblock$variant;
            }
        }
    }
}
