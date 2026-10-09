package net.minecraft.block;

import java.util.List;
import java.util.Random;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.EnumProperty;
import net.minecraft.client.world.color.BiomeColors;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.stat.Stats;
import net.minecraft.util.StringSerializable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

public class DoublePlantBlock extends PlantBlock implements Fertilizable {
    public static final EnumProperty<DoublePlantBlock.Variant> VARIANT = EnumProperty.of("variant", DoublePlantBlock.Variant.class);
    public static final EnumProperty<DoublePlantBlock.Half> HALF = EnumProperty.of("half", DoublePlantBlock.Half.class);
    public static final EnumProperty<Direction> FACING = HorizontalFacingBlock.FACING;

    public DoublePlantBlock() {
        super(Material.REPLACEABLE_PLANT);
        this.setDefaultState(
            this.stateDefinition.any().set(VARIANT, DoublePlantBlock.Variant.SUNFLOWER).set(HALF, DoublePlantBlock.Half.LOWER).set(FACING, Direction.NORTH)
        );
        this.setStrength(0.0F);
        this.setSounds(GRASS_SOUNDS);
        this.setKey("doublePlant");
    }

    @Override
    public void updateShape(WorldView world, BlockPos pos) {
        this.setShape(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
    }

    public DoublePlantBlock.Variant getVariant(WorldView world, BlockPos pos) {
        BlockState blockstate = world.getBlockState(pos);
        if (blockstate.getBlock() == this) {
            blockstate = this.resolveVirtualProperties(blockstate, world, pos);
            return blockstate.get(VARIANT);
        } else {
            return DoublePlantBlock.Variant.FERN;
        }
    }

    @Override
    public boolean canBePlaced(World world, BlockPos pos) {
        return super.canBePlaced(world, pos) && world.isAir(pos.up());
    }

    @Override
    public boolean canBeReplaced(World world, BlockPos pos) {
        BlockState blockstate = world.getBlockState(pos);
        if (blockstate.getBlock() != this) {
            return true;
        }

        DoublePlantBlock.Variant doubleplantblock$variant = this.resolveVirtualProperties(blockstate, world, pos).get(VARIANT);
        return doubleplantblock$variant == DoublePlantBlock.Variant.FERN || doubleplantblock$variant == DoublePlantBlock.Variant.GRASS;
    }

    @Override
    protected void canSurviveOrBreak(World world, BlockPos pos, BlockState state) {
        if (!this.canSurvive(world, pos, state)) {
            boolean flag = state.get(HALF) == DoublePlantBlock.Half.UPPER;
            BlockPos blockpos = flag ? pos : pos.up();
            BlockPos blockpos1 = flag ? pos.down() : pos;
            Block block = flag ? this : world.getBlockState(blockpos).getBlock();
            Block block1 = flag ? world.getBlockState(blockpos1).getBlock() : this;
            if (block == this) {
                world.setBlockState(blockpos, Blocks.AIR.defaultState(), 2);
            }

            if (block1 == this) {
                world.setBlockState(blockpos1, Blocks.AIR.defaultState(), 3);
                if (!flag) {
                    this.dropItems(world, blockpos1, state, 0);
                }
            }
        }
    }

    @Override
    public boolean canSurvive(World world, BlockPos pos, BlockState state) {
        if (state.get(HALF) == DoublePlantBlock.Half.UPPER) {
            return world.getBlockState(pos.down()).getBlock() == this;
        }

        BlockState blockstate = world.getBlockState(pos.up());
        return blockstate.getBlock() == this && super.canSurvive(world, pos, blockstate);
    }

    @Override
    public Item getDropItem(BlockState state, Random random, int fortuneLevel) {
        if (state.get(HALF) == DoublePlantBlock.Half.UPPER) {
            return null;
        } else {
            DoublePlantBlock.Variant doubleplantblock$variant = state.get(VARIANT);
            if (doubleplantblock$variant == DoublePlantBlock.Variant.FERN) {
                return null;
            } else if (doubleplantblock$variant == DoublePlantBlock.Variant.GRASS) {
                return random.nextInt(8) == 0 ? Items.WHEAT_SEEDS : null;
            } else {
                return Item.byBlock(this);
            }
        }
    }

    @Override
    public int getDropItemMetadata(BlockState state) {
        return state.get(HALF) != DoublePlantBlock.Half.UPPER && state.get(VARIANT) != DoublePlantBlock.Variant.GRASS ? state.get(VARIANT).getId() : 0;
    }

    @Override
    public int getColor(WorldView world, BlockPos pos, int tint) {
        DoublePlantBlock.Variant doubleplantblock$variant = this.getVariant(world, pos);
        return doubleplantblock$variant != DoublePlantBlock.Variant.GRASS && doubleplantblock$variant != DoublePlantBlock.Variant.FERN
            ? 16777215
            : BiomeColors.getGrassColor(world, pos);
    }

    public void place(World world, BlockPos pos, DoublePlantBlock.Variant variant, int flags) {
        world.setBlockState(pos, this.defaultState().set(HALF, DoublePlantBlock.Half.LOWER).set(VARIANT, variant), flags);
        world.setBlockState(pos.up(), this.defaultState().set(HALF, DoublePlantBlock.Half.UPPER), flags);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity entity, ItemStack item) {
        world.setBlockState(pos.up(), this.defaultState().set(HALF, DoublePlantBlock.Half.UPPER), 2);
    }

    @Override
    public void afterMinedByPlayer(World world, PlayerEntity player, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        if (world.isClient
            || player.getItemInHand() == null
            || player.getItemInHand().getItem() != Items.SHEARS
            || state.get(HALF) != DoublePlantBlock.Half.LOWER
            || !this.onMinedByPlayer(world, pos, state, player)) {
            super.afterMinedByPlayer(world, player, pos, state, blockEntity);
        }
    }

    @Override
    public void beforeMinedByPlayer(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (state.get(HALF) == DoublePlantBlock.Half.UPPER) {
            if (world.getBlockState(pos.down()).getBlock() == this) {
                if (!player.abilities.creativeMode) {
                    BlockState blockstate = world.getBlockState(pos.down());
                    DoublePlantBlock.Variant doubleplantblock$variant = blockstate.get(VARIANT);
                    if (doubleplantblock$variant != DoublePlantBlock.Variant.FERN && doubleplantblock$variant != DoublePlantBlock.Variant.GRASS) {
                        world.breakBlock(pos.down(), true);
                    } else if (!world.isClient) {
                        if (player.getItemInHand() != null && player.getItemInHand().getItem() == Items.SHEARS) {
                            this.onMinedByPlayer(world, pos, blockstate, player);
                            world.removeBlock(pos.down());
                        } else {
                            world.breakBlock(pos.down(), true);
                        }
                    } else {
                        world.removeBlock(pos.down());
                    }
                } else {
                    world.removeBlock(pos.down());
                }
            }
        } else if (player.abilities.creativeMode && world.getBlockState(pos.up()).getBlock() == this) {
            world.setBlockState(pos.up(), Blocks.AIR.defaultState(), 2);
        }

        super.beforeMinedByPlayer(world, pos, state, player);
    }

    private boolean onMinedByPlayer(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        DoublePlantBlock.Variant doubleplantblock$variant = state.get(VARIANT);
        if (doubleplantblock$variant != DoublePlantBlock.Variant.FERN && doubleplantblock$variant != DoublePlantBlock.Variant.GRASS) {
            return false;
        }

        player.incrementStat(Stats.BLOCKS_MINED[Block.getId(this)]);
        int i = (doubleplantblock$variant == DoublePlantBlock.Variant.GRASS ? TallPlantBlock.Type.GRASS : TallPlantBlock.Type.FERN).getId();
        dropItem(world, pos, new ItemStack(Blocks.TALLGRASS, 2, i));
        return true;
    }

    @Override
    public void addToCreativeMenu(Item item, CreativeModeTab tab, List<ItemStack> inventory) {
        for (DoublePlantBlock.Variant doubleplantblock$variant : DoublePlantBlock.Variant.values()) {
            inventory.add(new ItemStack(item, 1, doubleplantblock$variant.getId()));
        }
    }

    @Override
    public int getPickItemMetadata(World world, BlockPos pos) {
        return this.getVariant(world, pos).getId();
    }

    @Override
    public boolean canGrow(World world, BlockPos pos, BlockState state, boolean isClient) {
        DoublePlantBlock.Variant doubleplantblock$variant = this.getVariant(world, pos);
        return doubleplantblock$variant != DoublePlantBlock.Variant.GRASS && doubleplantblock$variant != DoublePlantBlock.Variant.FERN;
    }

    @Override
    public boolean canBeFertilized(World world, Random rand, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void grow(World world, Random rand, BlockPos pos, BlockState state) {
        dropItem(world, pos, new ItemStack(this, 1, this.getVariant(world, pos).getId()));
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        return (metadata & 8) > 0
            ? this.defaultState().set(HALF, DoublePlantBlock.Half.UPPER)
            : this.defaultState().set(HALF, DoublePlantBlock.Half.LOWER).set(VARIANT, DoublePlantBlock.Variant.byId(metadata & 7));
    }

    @Override
    public BlockState resolveVirtualProperties(BlockState state, WorldView world, BlockPos pos) {
        if (state.get(HALF) == DoublePlantBlock.Half.UPPER) {
            BlockState blockstate = world.getBlockState(pos.down());
            if (blockstate.getBlock() == this) {
                state = state.set(VARIANT, blockstate.get(VARIANT));
            }
        }

        return state;
    }

    @Override
    public int getMetadataFromState(BlockState state) {
        return state.get(HALF) == DoublePlantBlock.Half.UPPER ? 8 | state.get(FACING).getIdHorizontal() : state.get(VARIANT).getId();
    }

    @Override
    protected StateDefinition createStateDefinition() {
        return new StateDefinition(this, HALF, VARIANT, FACING);
    }

    @Override
    public Block.OffsetType getOffsetType() {
        return Block.OffsetType.XZ;
    }

    public enum Half implements StringSerializable {
        UPPER,
        LOWER;

        @Override
        public String toString() {
            return this.serializeToString();
        }

        @Override
        public String serializeToString() {
            return this == UPPER ? "upper" : "lower";
        }
    }

    public enum Variant implements StringSerializable {
        SUNFLOWER(0, "sunflower"),
        SYRINGA(1, "syringa"),
        GRASS(2, "double_grass", "grass"),
        FERN(3, "double_fern", "fern"),
        ROSE(4, "double_rose", "rose"),
        PAEONIA(5, "paeonia");

        private static final DoublePlantBlock.Variant[] BY_ID = new DoublePlantBlock.Variant[values().length];
        private final int id;
        private final String key;
        private final String name;

        Variant(int id, String key) {
            this(id, key, key);
        }

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

        public static DoublePlantBlock.Variant byId(int id) {
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
            for (DoublePlantBlock.Variant doubleplantblock$variant : values()) {
                BY_ID[doubleplantblock$variant.getId()] = doubleplantblock$variant;
            }
        }
    }
}
