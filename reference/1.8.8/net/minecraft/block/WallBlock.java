package net.minecraft.block;

import java.util.List;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.BooleanProperty;
import net.minecraft.block.state.property.EnumProperty;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.locale.I18n;
import net.minecraft.util.StringSerializable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

public class WallBlock extends Block {
    public static final BooleanProperty UP = BooleanProperty.of("up");
    public static final BooleanProperty NORTH = BooleanProperty.of("north");
    public static final BooleanProperty EAST = BooleanProperty.of("east");
    public static final BooleanProperty SOUTH = BooleanProperty.of("south");
    public static final BooleanProperty WEST = BooleanProperty.of("west");
    public static final EnumProperty<WallBlock.Variant> VARIANT = EnumProperty.of("variant", WallBlock.Variant.class);

    public WallBlock(Block block) {
        super(block.material);
        this.setDefaultState(
            this.stateDefinition
                .any()
                .set(UP, false)
                .set(NORTH, false)
                .set(EAST, false)
                .set(SOUTH, false)
                .set(WEST, false)
                .set(VARIANT, WallBlock.Variant.NORMAL)
        );
        this.setStrength(block.miningTime);
        this.setBlastResistance(block.blastResistance / 3.0F);
        this.setSounds(block.sounds);
        this.setCreativeModeTab(CreativeModeTab.BUILDING_BLOCKS);
    }

    @Override
    public String getName() {
        return I18n.translate(this.getTranslationKey() + "." + WallBlock.Variant.NORMAL.getName() + ".name");
    }

    @Override
    public boolean isCube() {
        return false;
    }

    @Override
    public boolean canWalkThrough(WorldView world, BlockPos pos) {
        return false;
    }

    @Override
    public boolean isSolidRender() {
        return false;
    }

    @Override
    public void updateShape(WorldView world, BlockPos pos) {
        boolean flag = this.shouldConnectTo(world, pos.north());
        boolean flag1 = this.shouldConnectTo(world, pos.south());
        boolean flag2 = this.shouldConnectTo(world, pos.west());
        boolean flag3 = this.shouldConnectTo(world, pos.east());
        float f = 0.25F;
        float f1 = 0.75F;
        float f2 = 0.25F;
        float f3 = 0.75F;
        float f4 = 1.0F;
        if (flag) {
            f2 = 0.0F;
        }

        if (flag1) {
            f3 = 1.0F;
        }

        if (flag2) {
            f = 0.0F;
        }

        if (flag3) {
            f1 = 1.0F;
        }

        if (flag && flag1 && !flag2 && !flag3) {
            f4 = 0.8125F;
            f = 0.3125F;
            f1 = 0.6875F;
        } else if (!flag && !flag1 && flag2 && flag3) {
            f4 = 0.8125F;
            f2 = 0.3125F;
            f3 = 0.6875F;
        }

        this.setShape(f, 0.0F, f2, f1, f4, f3);
    }

    @Override
    public Box getCollisionShape(World world, BlockPos pos, BlockState state) {
        this.updateShape(world, pos);
        this.maxY = 1.5;
        return super.getCollisionShape(world, pos, state);
    }

    public boolean shouldConnectTo(WorldView world, BlockPos pos) {
        Block block = world.getBlockState(pos).getBlock();
        return block != Blocks.BARRIER
            && (block == this || block instanceof FenceGateBlock || block.material.isSolidBlocking() && block.isCube() && block.material != Material.PUMPKIN);
    }

    @Override
    public void addToCreativeMenu(Item item, CreativeModeTab tab, List<ItemStack> inventory) {
        for (WallBlock.Variant wallblock$variant : WallBlock.Variant.values()) {
            inventory.add(new ItemStack(item, 1, wallblock$variant.getId()));
        }
    }

    @Override
    public int getDropItemMetadata(BlockState state) {
        return state.get(VARIANT).getId();
    }

    @Override
    public boolean shouldRenderFace(WorldView world, BlockPos pos, Direction face) {
        return face != Direction.DOWN || super.shouldRenderFace(world, pos, face);
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        return this.defaultState().set(VARIANT, WallBlock.Variant.byId(metadata));
    }

    @Override
    public int getMetadataFromState(BlockState state) {
        return state.get(VARIANT).getId();
    }

    @Override
    public BlockState resolveVirtualProperties(BlockState state, WorldView world, BlockPos pos) {
        return state.set(UP, !world.isAir(pos.up()))
            .set(NORTH, this.shouldConnectTo(world, pos.north()))
            .set(EAST, this.shouldConnectTo(world, pos.east()))
            .set(SOUTH, this.shouldConnectTo(world, pos.south()))
            .set(WEST, this.shouldConnectTo(world, pos.west()));
    }

    @Override
    protected StateDefinition createStateDefinition() {
        return new StateDefinition(this, UP, NORTH, EAST, WEST, SOUTH, VARIANT);
    }

    public enum Variant implements StringSerializable {
        NORMAL(0, "cobblestone", "normal"),
        MOSSY(1, "mossy_cobblestone", "mossy");

        private static final WallBlock.Variant[] BY_ID = new WallBlock.Variant[values().length];
        private final int id;
        private final String key;
        private String name;

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

        public static WallBlock.Variant byId(int id) {
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
            for (WallBlock.Variant wallblock$variant : values()) {
                BY_ID[wallblock$variant.getId()] = wallblock$variant;
            }
        }
    }
}
