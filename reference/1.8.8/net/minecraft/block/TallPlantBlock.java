package net.minecraft.block;

import java.util.List;
import java.util.Random;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.EnumProperty;
import net.minecraft.client.world.color.GrassColors;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.stat.Stats;
import net.minecraft.util.StringSerializable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

public class TallPlantBlock extends PlantBlock implements Fertilizable {
    public static final EnumProperty<TallPlantBlock.Type> TYPE = EnumProperty.of("type", TallPlantBlock.Type.class);

    protected TallPlantBlock() {
        super(Material.REPLACEABLE_PLANT);
        this.setDefaultState(this.stateDefinition.any().set(TYPE, TallPlantBlock.Type.DEAD_BUSH));
        float f = 0.4F;
        this.setShape(0.5F - f, 0.0F, 0.5F - f, 0.5F + f, 0.8F, 0.5F + f);
    }

    @Override
    public int getColor() {
        return GrassColors.getColor(0.5, 1.0);
    }

    @Override
    public boolean canSurvive(World world, BlockPos pos, BlockState state) {
        return this.canBePlacedOn(world.getBlockState(pos.down()).getBlock());
    }

    @Override
    public boolean canBeReplaced(World world, BlockPos pos) {
        return true;
    }

    @Override
    public int getColor(BlockState state) {
        if (state.getBlock() != this) {
            return super.getColor(state);
        }

        TallPlantBlock.Type tallplantblock$type = state.get(TYPE);
        return tallplantblock$type == TallPlantBlock.Type.DEAD_BUSH ? 16777215 : GrassColors.getColor(0.5, 1.0);
    }

    @Override
    public int getColor(WorldView world, BlockPos pos, int tint) {
        return world.getBiome(pos).getGrassColor(pos);
    }

    @Override
    public Item getDropItem(BlockState state, Random random, int fortuneLevel) {
        return random.nextInt(8) == 0 ? Items.WHEAT_SEEDS : null;
    }

    @Override
    public int getDropCount(int fortuneLevel, Random random) {
        return 1 + random.nextInt(fortuneLevel * 2 + 1);
    }

    @Override
    public void afterMinedByPlayer(World world, PlayerEntity player, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        if (!world.isClient && player.getItemInHand() != null && player.getItemInHand().getItem() == Items.SHEARS) {
            player.incrementStat(Stats.BLOCKS_MINED[Block.getId(this)]);
            dropItem(world, pos, new ItemStack(Blocks.TALLGRASS, 1, state.get(TYPE).getId()));
        } else {
            super.afterMinedByPlayer(world, player, pos, state, blockEntity);
        }
    }

    @Override
    public int getPickItemMetadata(World world, BlockPos pos) {
        BlockState blockstate = world.getBlockState(pos);
        return blockstate.getBlock().getMetadataFromState(blockstate);
    }

    @Override
    public void addToCreativeMenu(Item item, CreativeModeTab tab, List<ItemStack> inventory) {
        for (int i = 1; i < 3; i++) {
            inventory.add(new ItemStack(item, 1, i));
        }
    }

    @Override
    public boolean canGrow(World world, BlockPos pos, BlockState state, boolean isClient) {
        return state.get(TYPE) != TallPlantBlock.Type.DEAD_BUSH;
    }

    @Override
    public boolean canBeFertilized(World world, Random rand, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void grow(World world, Random rand, BlockPos pos, BlockState state) {
        DoublePlantBlock.Variant doubleplantblock$variant = DoublePlantBlock.Variant.GRASS;
        if (state.get(TYPE) == TallPlantBlock.Type.FERN) {
            doubleplantblock$variant = DoublePlantBlock.Variant.FERN;
        }

        if (Blocks.DOUBLE_PLANT.canBePlaced(world, pos)) {
            Blocks.DOUBLE_PLANT.place(world, pos, doubleplantblock$variant, 2);
        }
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        return this.defaultState().set(TYPE, TallPlantBlock.Type.byId(metadata));
    }

    @Override
    public int getMetadataFromState(BlockState state) {
        return state.get(TYPE).getId();
    }

    @Override
    protected StateDefinition createStateDefinition() {
        return new StateDefinition(this, TYPE);
    }

    @Override
    public Block.OffsetType getOffsetType() {
        return Block.OffsetType.XYZ;
    }

    public enum Type implements StringSerializable {
        DEAD_BUSH(0, "dead_bush"),
        GRASS(1, "tall_grass"),
        FERN(2, "fern");

        private static final TallPlantBlock.Type[] BY_ID = new TallPlantBlock.Type[values().length];
        private final int id;
        private final String key;

        Type(int id, String key) {
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

        public static TallPlantBlock.Type byId(int id) {
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
            for (TallPlantBlock.Type tallplantblock$type : values()) {
                BY_ID[tallplantblock$type.getId()] = tallplantblock$type;
            }
        }
    }
}
