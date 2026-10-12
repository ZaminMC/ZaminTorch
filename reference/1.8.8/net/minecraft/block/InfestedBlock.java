package net.minecraft.block;

import java.util.List;
import java.util.Random;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.StateDefinition;
import net.minecraft.block.state.property.EnumProperty;
import net.minecraft.entity.living.mob.monster.SilverfishEntity;
import net.minecraft.item.CreativeModeTab;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StringSerializable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class InfestedBlock extends Block {
    public static final EnumProperty<InfestedBlock.Variant> VARIANT = EnumProperty.of("variant", InfestedBlock.Variant.class);

    public InfestedBlock() {
        super(Material.CLAY);
        this.setDefaultState(this.stateDefinition.any().set(VARIANT, InfestedBlock.Variant.STONE));
        this.setStrength(0.0F);
        this.setCreativeModeTab(CreativeModeTab.DECORATIONS);
    }

    @Override
    public int getBaseDropCount(Random random) {
        return 0;
    }

    public static boolean canBeInfested(BlockState state) {
        Block block = state.getBlock();
        return state == Blocks.STONE.defaultState().set(StoneBlock.VARIANT, StoneBlock.Variant.STONE)
            || block == Blocks.COBBLESTONE
            || block == Blocks.STONE_BRICKS;
    }

    @Override
    protected ItemStack getSilkTouchDrop(BlockState state) {
        switch ((InfestedBlock.Variant)state.get(VARIANT)) {
            case COBBLESTONE:
                return new ItemStack(Blocks.COBBLESTONE);
            case STONEBRICK:
                return new ItemStack(Blocks.STONE_BRICKS);
            case MOSSY_STONEBRICK:
                return new ItemStack(Blocks.STONE_BRICKS, 1, StonebrickBlock.Variant.MOSSY.getId());
            case CRACKED_STONEBRICK:
                return new ItemStack(Blocks.STONE_BRICKS, 1, StonebrickBlock.Variant.CRACKED.getId());
            case CHISELED_STONEBRICK:
                return new ItemStack(Blocks.STONE_BRICKS, 1, StonebrickBlock.Variant.CHISELED.getId());
            default:
                return new ItemStack(Blocks.STONE);
        }
    }

    @Override
    public void dropItems(World world, BlockPos pos, BlockState state, float luck, int fortuneLevel) {
        if (!world.isClient && world.getGameRules().getBoolean("doTileDrops")) {
            SilverfishEntity silverfishentity = new SilverfishEntity(world);
            silverfishentity.setPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0F, 0.0F);
            world.addEntity(silverfishentity);
            silverfishentity.animateSpawn();
        }
    }

    @Override
    public int getPickItemMetadata(World world, BlockPos pos) {
        BlockState blockstate = world.getBlockState(pos);
        return blockstate.getBlock().getMetadataFromState(blockstate);
    }

    @Override
    public void addToCreativeMenu(Item item, CreativeModeTab tab, List<ItemStack> inventory) {
        for (InfestedBlock.Variant infestedblock$variant : InfestedBlock.Variant.values()) {
            inventory.add(new ItemStack(item, 1, infestedblock$variant.getId()));
        }
    }

    @Override
    public BlockState getStateFromMetadata(int metadata) {
        return this.defaultState().set(VARIANT, InfestedBlock.Variant.byId(metadata));
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
        STONE(0, "stone") {
            @Override
            public BlockState getHostState() {
                return Blocks.STONE.defaultState().set(StoneBlock.VARIANT, StoneBlock.Variant.STONE);
            }
        },
        COBBLESTONE(1, "cobblestone", "cobble") {
            @Override
            public BlockState getHostState() {
                return Blocks.COBBLESTONE.defaultState();
            }
        },
        STONEBRICK(2, "stone_brick", "brick") {
            @Override
            public BlockState getHostState() {
                return Blocks.STONE_BRICKS.defaultState().set(StonebrickBlock.VARIANT, StonebrickBlock.Variant.DEFAULT);
            }
        },
        MOSSY_STONEBRICK(3, "mossy_brick", "mossybrick") {
            @Override
            public BlockState getHostState() {
                return Blocks.STONE_BRICKS.defaultState().set(StonebrickBlock.VARIANT, StonebrickBlock.Variant.MOSSY);
            }
        },
        CRACKED_STONEBRICK(4, "cracked_brick", "crackedbrick") {
            @Override
            public BlockState getHostState() {
                return Blocks.STONE_BRICKS.defaultState().set(StonebrickBlock.VARIANT, StonebrickBlock.Variant.CRACKED);
            }
        },
        CHISELED_STONEBRICK(5, "chiseled_brick", "chiseledbrick") {
            @Override
            public BlockState getHostState() {
                return Blocks.STONE_BRICKS.defaultState().set(StonebrickBlock.VARIANT, StonebrickBlock.Variant.CHISELED);
            }
        };

        private static final InfestedBlock.Variant[] BY_ID = new InfestedBlock.Variant[values().length];
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

        public static InfestedBlock.Variant byId(int id) {
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

        public abstract BlockState getHostState();

        public static InfestedBlock.Variant byHostState(BlockState state) {
            for (InfestedBlock.Variant infestedblock$variant : values()) {
                if (state == infestedblock$variant.getHostState()) {
                    return infestedblock$variant;
                }
            }

            return STONE;
        }

        static {
            for (InfestedBlock.Variant infestedblock$variant : values()) {
                BY_ID[infestedblock$variant.getId()] = infestedblock$variant;
            }
        }
    }
}
