package net.minecraft.block;

import java.util.Random;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.EnumProperty;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.util.StringSerializable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class MushroomBlock extends Block {
    public static final EnumProperty<MushroomBlock.Variant> VARIANT = EnumProperty.of("variant", MushroomBlock.Variant.class);
    private final Block plant;

    public MushroomBlock(Material material, MapColor mapColor, Block plant) {
        super(material, mapColor);
        this.setDefaultState(this.stateDefinition.any().set(VARIANT, MushroomBlock.Variant.ALL_OUTSIDE));
        this.plant = plant;
    }

    @Override
    public int getBaseDropCount(Random random) {
        return Math.max(0, random.nextInt(10) - 7);
    }

    @Override
    public MapColor getMapColor(BlockState state) {
        switch ((MushroomBlock.Variant)state.get(VARIANT)) {
            case ALL_STEM:
                return MapColor.WEB;
            case ALL_INSIDE:
                return MapColor.SAND;
            case STEM:
                return MapColor.SAND;
            default:
                return super.getMapColor(state);
        }
    }

    @Override
    public Item getDropItem(BlockState state, Random random, int fortuneLevel) {
        return Item.byBlock(this.plant);
    }

    @Override
    public Item getPickItem(World world, BlockPos pos) {
        return Item.byBlock(this.plant);
    }

    @Override
    public BlockState getPlacementState(World world, BlockPos pos, Direction dir, float dx, float dy, float dz, int metadata, LivingEntity entity) {
        return this.defaultState();
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        return this.defaultState().set(VARIANT, MushroomBlock.Variant.byId(metadata));
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
        NORTH_WEST(1, "north_west"),
        NORTH(2, "north"),
        NORTH_EAST(3, "north_east"),
        WEST(4, "west"),
        CENTER(5, "center"),
        EAST(6, "east"),
        SOUTH_WEST(7, "south_west"),
        SOUTH(8, "south"),
        SOUTH_EAST(9, "south_east"),
        STEM(10, "stem"),
        ALL_INSIDE(0, "all_inside"),
        ALL_OUTSIDE(14, "all_outside"),
        ALL_STEM(15, "all_stem");

        private static final MushroomBlock.Variant[] BY_ID = new MushroomBlock.Variant[16];
        private final int id;
        private final String key;

        Variant(int id, String key) {
            this.id = id;
            this.key = key;
        }

        public int getId() {
            return this.id;
        }

        @Override
        public String toString() {
            return this.key;
        }

        public static MushroomBlock.Variant byId(int id) {
            if (id < 0 || id >= BY_ID.length) {
                id = 0;
            }

            MushroomBlock.Variant mushroomblock$variant = BY_ID[id];
            return mushroomblock$variant == null ? BY_ID[0] : mushroomblock$variant;
        }

        @Override
        public String serializeToString() {
            return this.key;
        }

        static {
            for (MushroomBlock.Variant mushroomblock$variant : values()) {
                BY_ID[mushroomblock$variant.getId()] = mushroomblock$variant;
            }
        }
    }
}
