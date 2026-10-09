package net.minecraft.block;

import java.util.List;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.EnumProperty;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StringSerializable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class QuartzBlock extends Block {
    public static final EnumProperty<QuartzBlock.Variant> VARIANT = EnumProperty.of("variant", QuartzBlock.Variant.class);

    public QuartzBlock() {
        super(Material.STONE);
        this.setDefaultState(this.stateDefinition.any().set(VARIANT, QuartzBlock.Variant.DEFAULT));
        this.setCreativeModeTab(CreativeModeTab.BUILDING_BLOCKS);
    }

    @Override
    public BlockState getPlacementState(World world, BlockPos pos, Direction dir, float dx, float dy, float dz, int metadata, LivingEntity entity) {
        if (metadata == QuartzBlock.Variant.LINES_Y.getId()) {
            switch (dir.getAxis()) {
                case Z:
                    return this.defaultState().set(VARIANT, QuartzBlock.Variant.LINES_Z);
                case X:
                    return this.defaultState().set(VARIANT, QuartzBlock.Variant.LINES_X);
                case Y:
                default:
                    return this.defaultState().set(VARIANT, QuartzBlock.Variant.LINES_Y);
            }
        } else {
            return metadata == QuartzBlock.Variant.CHISELED.getId()
                ? this.defaultState().set(VARIANT, QuartzBlock.Variant.CHISELED)
                : this.defaultState().set(VARIANT, QuartzBlock.Variant.DEFAULT);
        }
    }

    @Override
    public int getDropItemMetadata(BlockState state) {
        QuartzBlock.Variant quartzblock$variant = state.get(VARIANT);
        return quartzblock$variant != QuartzBlock.Variant.LINES_X && quartzblock$variant != QuartzBlock.Variant.LINES_Z
            ? quartzblock$variant.getId()
            : QuartzBlock.Variant.LINES_Y.getId();
    }

    @Override
    protected ItemStack getSilkTouchDrop(BlockState state) {
        QuartzBlock.Variant quartzblock$variant = state.get(VARIANT);
        return quartzblock$variant != QuartzBlock.Variant.LINES_X && quartzblock$variant != QuartzBlock.Variant.LINES_Z
            ? super.getSilkTouchDrop(state)
            : new ItemStack(Item.byBlock(this), 1, QuartzBlock.Variant.LINES_Y.getId());
    }

    @Override
    public void addToCreativeMenu(Item item, CreativeModeTab tab, List<ItemStack> inventory) {
        inventory.add(new ItemStack(item, 1, QuartzBlock.Variant.DEFAULT.getId()));
        inventory.add(new ItemStack(item, 1, QuartzBlock.Variant.CHISELED.getId()));
        inventory.add(new ItemStack(item, 1, QuartzBlock.Variant.LINES_Y.getId()));
    }

    @Override
    public MapColor getMapColor(BlockState state) {
        return MapColor.QUARTZ;
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        return this.defaultState().set(VARIANT, QuartzBlock.Variant.byId(metadata));
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
        DEFAULT(0, "default", "default"),
        CHISELED(1, "chiseled", "chiseled"),
        LINES_Y(2, "lines_y", "lines"),
        LINES_X(3, "lines_x", "lines"),
        LINES_Z(4, "lines_z", "lines");

        private static final QuartzBlock.Variant[] BY_ID = new QuartzBlock.Variant[values().length];
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
            return this.name;
        }

        public static QuartzBlock.Variant byId(int id) {
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
            for (QuartzBlock.Variant quartzblock$variant : values()) {
                BY_ID[quartzblock$variant.getId()] = quartzblock$variant;
            }
        }
    }
}
