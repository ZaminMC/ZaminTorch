package net.minecraft.client.render.block;

import com.google.common.collect.Maps;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.block.AbstractLeavesBlock;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.CactusBlock;
import net.minecraft.block.ColoredBlock;
import net.minecraft.block.CommandBlock;
import net.minecraft.block.DirtBlock;
import net.minecraft.block.DispenserBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.DoublePlantBlock;
import net.minecraft.block.DropperBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.FireBlock;
import net.minecraft.block.FlowerPotBlock;
import net.minecraft.block.HopperBlock;
import net.minecraft.block.InfestedBlock;
import net.minecraft.block.JukeboxBlock;
import net.minecraft.block.Leaves2Block;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.Log2Block;
import net.minecraft.block.LogBlock;
import net.minecraft.block.PlanksBlock;
import net.minecraft.block.PrismarineBlock;
import net.minecraft.block.QuartzBlock;
import net.minecraft.block.RedSandstoneBlock;
import net.minecraft.block.RedSandstoneSlab;
import net.minecraft.block.RedstoneWireBlock;
import net.minecraft.block.SandBlock;
import net.minecraft.block.SandstoneBlock;
import net.minecraft.block.SaplingBlock;
import net.minecraft.block.StemBlock;
import net.minecraft.block.StoneBlock;
import net.minecraft.block.StoneSlabBlock;
import net.minecraft.block.StonebrickBlock;
import net.minecraft.block.SugarCaneBlock;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.block.TntBlock;
import net.minecraft.block.TripwireBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.property.Property;
import net.minecraft.client.render.texture.TextureAtlasSprite;
import net.minecraft.client.resource.ModelIdentifier;
import net.minecraft.client.resource.model.AbstractBlockModelProvider;
import net.minecraft.client.resource.model.BakedModel;
import net.minecraft.client.resource.model.BlockModelProvider;
import net.minecraft.client.resource.model.BlockModels;
import net.minecraft.client.resource.model.ModelManager;
import net.minecraft.client.resource.model.VariantBlockModelProvider;
import net.minecraft.util.math.Direction;

public class BlockModelShaper {
    private final Map<BlockState, BakedModel> modelCache = Maps.newIdentityHashMap();
    private final BlockModels models = new BlockModels();
    private final ModelManager manager;

    public BlockModelShaper(ModelManager manager) {
        this.manager = manager;
        this.init();
    }

    public BlockModels getModels() {
        return this.models;
    }

    public TextureAtlasSprite getParticleIcon(BlockState state) {
        Block block = state.getBlock();
        BakedModel bakedmodel = this.getModel(state);
        if (bakedmodel == null || bakedmodel == this.manager.getMissingModel()) {
            if (block == Blocks.WALL_SIGN
                || block == Blocks.STANDING_SIGN
                || block == Blocks.CHEST
                || block == Blocks.TRAPPED_CHEST
                || block == Blocks.STANDING_BANNER
                || block == Blocks.WALL_BANNER) {
                return this.manager.getBlocksAtlas().getSprite("minecraft:blocks/planks_oak");
            }

            if (block == Blocks.ENDER_CHEST) {
                return this.manager.getBlocksAtlas().getSprite("minecraft:blocks/obsidian");
            }

            if (block == Blocks.FLOWING_LAVA || block == Blocks.LAVA) {
                return this.manager.getBlocksAtlas().getSprite("minecraft:blocks/lava_still");
            }

            if (block == Blocks.FLOWING_WATER || block == Blocks.WATER) {
                return this.manager.getBlocksAtlas().getSprite("minecraft:blocks/water_still");
            }

            if (block == Blocks.SKULL) {
                return this.manager.getBlocksAtlas().getSprite("minecraft:blocks/soul_sand");
            }

            if (block == Blocks.BARRIER) {
                return this.manager.getBlocksAtlas().getSprite("minecraft:items/barrier");
            }
        }

        if (bakedmodel == null) {
            bakedmodel = this.manager.getMissingModel();
        }

        return bakedmodel.getParticleIcon();
    }

    public BakedModel getModel(BlockState state) {
        BakedModel bakedmodel = this.modelCache.get(state);
        if (bakedmodel == null) {
            bakedmodel = this.manager.getMissingModel();
        }

        return bakedmodel;
    }

    public ModelManager getManager() {
        return this.manager;
    }

    public void rebuildCache() {
        this.modelCache.clear();

        for (Entry<BlockState, ModelIdentifier> entry : this.models.provide().entrySet()) {
            this.modelCache.put(entry.getKey(), this.manager.getModel(entry.getValue()));
        }
    }

    public void register(Block block, BlockModelProvider provider) {
        this.models.register(block, provider);
    }

    public void register(Block... blocks) {
        this.models.register(blocks);
    }

    private void init() {
        this.register(
            Blocks.AIR,
            Blocks.FLOWING_WATER,
            Blocks.WATER,
            Blocks.FLOWING_LAVA,
            Blocks.LAVA,
            Blocks.MOVING_BLOCK,
            Blocks.CHEST,
            Blocks.ENDER_CHEST,
            Blocks.TRAPPED_CHEST,
            Blocks.STANDING_SIGN,
            Blocks.SKULL,
            Blocks.END_PORTAL,
            Blocks.BARRIER,
            Blocks.WALL_SIGN,
            Blocks.WALL_BANNER,
            Blocks.STANDING_BANNER
        );
        this.register(Blocks.STONE, new VariantBlockModelProvider.Builder().setProperty(StoneBlock.VARIANT).build());
        this.register(Blocks.PRISMARINE, new VariantBlockModelProvider.Builder().setProperty(PrismarineBlock.VARIANT).build());
        this.register(
            Blocks.LEAVES,
            new VariantBlockModelProvider.Builder()
                .setProperty(LeavesBlock.VARIANT)
                .setVariant("_leaves")
                .setUnusedProperties(AbstractLeavesBlock.CHECK_DECAY, AbstractLeavesBlock.DECAYABLE)
                .build()
        );
        this.register(
            Blocks.LEAVES2,
            new VariantBlockModelProvider.Builder()
                .setProperty(Leaves2Block.VARIANT)
                .setVariant("_leaves")
                .setUnusedProperties(AbstractLeavesBlock.CHECK_DECAY, AbstractLeavesBlock.DECAYABLE)
                .build()
        );
        this.register(Blocks.CACTUS, new VariantBlockModelProvider.Builder().setUnusedProperties(CactusBlock.AGE).build());
        this.register(Blocks.REEDS, new VariantBlockModelProvider.Builder().setUnusedProperties(SugarCaneBlock.AGE).build());
        this.register(Blocks.JUKEBOX, new VariantBlockModelProvider.Builder().setUnusedProperties(JukeboxBlock.HAS_RECORD).build());
        this.register(Blocks.COMMAND_BLOCK, new VariantBlockModelProvider.Builder().setUnusedProperties(CommandBlock.TRIGGERED).build());
        this.register(Blocks.COBBLESTONE_WALL, new VariantBlockModelProvider.Builder().setProperty(WallBlock.VARIANT).setVariant("_wall").build());
        this.register(
            Blocks.DOUBLE_PLANT,
            new VariantBlockModelProvider.Builder().setProperty(DoublePlantBlock.VARIANT).setUnusedProperties(DoublePlantBlock.FACING).build()
        );
        this.register(Blocks.FENCE_GATE, new VariantBlockModelProvider.Builder().setUnusedProperties(FenceGateBlock.POWERED).build());
        this.register(Blocks.SPRUCE_FENCE_GATE, new VariantBlockModelProvider.Builder().setUnusedProperties(FenceGateBlock.POWERED).build());
        this.register(Blocks.BIRCH_FENCE_GATE, new VariantBlockModelProvider.Builder().setUnusedProperties(FenceGateBlock.POWERED).build());
        this.register(Blocks.JUNGLE_FENCE_GATE, new VariantBlockModelProvider.Builder().setUnusedProperties(FenceGateBlock.POWERED).build());
        this.register(Blocks.DARK_OAK_FENCE_GATE, new VariantBlockModelProvider.Builder().setUnusedProperties(FenceGateBlock.POWERED).build());
        this.register(Blocks.ACACIA_FENCE_GATE, new VariantBlockModelProvider.Builder().setUnusedProperties(FenceGateBlock.POWERED).build());
        this.register(Blocks.TRIPWIRE, new VariantBlockModelProvider.Builder().setUnusedProperties(TripwireBlock.DISARMED, TripwireBlock.POWERED).build());
        this.register(Blocks.DOUBLE_WOODEN_SLAB, new VariantBlockModelProvider.Builder().setProperty(PlanksBlock.VARIANT).setVariant("_double_slab").build());
        this.register(Blocks.WOODEN_SLAB, new VariantBlockModelProvider.Builder().setProperty(PlanksBlock.VARIANT).setVariant("_slab").build());
        this.register(Blocks.TNT, new VariantBlockModelProvider.Builder().setUnusedProperties(TntBlock.EXPLODE).build());
        this.register(Blocks.FIRE, new VariantBlockModelProvider.Builder().setUnusedProperties(FireBlock.AGE).build());
        this.register(Blocks.REDSTONE_WIRE, new VariantBlockModelProvider.Builder().setUnusedProperties(RedstoneWireBlock.POWER).build());
        this.register(Blocks.WOODEN_DOOR, new VariantBlockModelProvider.Builder().setUnusedProperties(DoorBlock.POWERED).build());
        this.register(Blocks.SPRUCE_DOOR, new VariantBlockModelProvider.Builder().setUnusedProperties(DoorBlock.POWERED).build());
        this.register(Blocks.BIRCH_DOOR, new VariantBlockModelProvider.Builder().setUnusedProperties(DoorBlock.POWERED).build());
        this.register(Blocks.JUNGLE_DOOR, new VariantBlockModelProvider.Builder().setUnusedProperties(DoorBlock.POWERED).build());
        this.register(Blocks.ACACIA_DOOR, new VariantBlockModelProvider.Builder().setUnusedProperties(DoorBlock.POWERED).build());
        this.register(Blocks.DARK_OAK_DOOR, new VariantBlockModelProvider.Builder().setUnusedProperties(DoorBlock.POWERED).build());
        this.register(Blocks.IRON_DOOR, new VariantBlockModelProvider.Builder().setUnusedProperties(DoorBlock.POWERED).build());
        this.register(Blocks.WOOL, new VariantBlockModelProvider.Builder().setProperty(ColoredBlock.COLOR).setVariant("_wool").build());
        this.register(Blocks.CARPET, new VariantBlockModelProvider.Builder().setProperty(ColoredBlock.COLOR).setVariant("_carpet").build());
        this.register(
            Blocks.STAINED_HARDENED_CLAY, new VariantBlockModelProvider.Builder().setProperty(ColoredBlock.COLOR).setVariant("_stained_hardened_clay").build()
        );
        this.register(
            Blocks.STAINED_GLASS_PANE, new VariantBlockModelProvider.Builder().setProperty(ColoredBlock.COLOR).setVariant("_stained_glass_pane").build()
        );
        this.register(Blocks.STAINED_GLASS, new VariantBlockModelProvider.Builder().setProperty(ColoredBlock.COLOR).setVariant("_stained_glass").build());
        this.register(Blocks.SANDSTONE, new VariantBlockModelProvider.Builder().setProperty(SandstoneBlock.TYPE).build());
        this.register(Blocks.RED_SANDSTONE, new VariantBlockModelProvider.Builder().setProperty(RedSandstoneBlock.TYPE).build());
        this.register(Blocks.TALLGRASS, new VariantBlockModelProvider.Builder().setProperty(TallPlantBlock.TYPE).build());
        this.register(Blocks.BED, new VariantBlockModelProvider.Builder().setUnusedProperties(BedBlock.OCCUPIED).build());
        this.register(Blocks.YELLOW_FLOWER, new VariantBlockModelProvider.Builder().setProperty(Blocks.YELLOW_FLOWER.getTypeProperty()).build());
        this.register(Blocks.RED_FLOWER, new VariantBlockModelProvider.Builder().setProperty(Blocks.RED_FLOWER.getTypeProperty()).build());
        this.register(Blocks.STONE_SLAB, new VariantBlockModelProvider.Builder().setProperty(StoneSlabBlock.VARIANT).setVariant("_slab").build());
        this.register(Blocks.RED_SANDSTONE_SLAB, new VariantBlockModelProvider.Builder().setProperty(RedSandstoneSlab.VARIANT).setVariant("_slab").build());
        this.register(Blocks.MONSTER_EGG, new VariantBlockModelProvider.Builder().setProperty(InfestedBlock.VARIANT).setVariant("_monster_egg").build());
        this.register(Blocks.STONE_BRICKS, new VariantBlockModelProvider.Builder().setProperty(StonebrickBlock.VARIANT).build());
        this.register(Blocks.DISPENSER, new VariantBlockModelProvider.Builder().setUnusedProperties(DispenserBlock.TRIGGERED).build());
        this.register(Blocks.DROPPER, new VariantBlockModelProvider.Builder().setUnusedProperties(DropperBlock.TRIGGERED).build());
        this.register(Blocks.LOG, new VariantBlockModelProvider.Builder().setProperty(LogBlock.VARIANT).setVariant("_log").build());
        this.register(Blocks.LOG2, new VariantBlockModelProvider.Builder().setProperty(Log2Block.VARIANT).setVariant("_log").build());
        this.register(Blocks.PLANKS, new VariantBlockModelProvider.Builder().setProperty(PlanksBlock.VARIANT).setVariant("_planks").build());
        this.register(Blocks.SAPLING, new VariantBlockModelProvider.Builder().setProperty(SaplingBlock.TYPE).setVariant("_sapling").build());
        this.register(Blocks.SAND, new VariantBlockModelProvider.Builder().setProperty(SandBlock.VARIANT).build());
        this.register(Blocks.HOPPER, new VariantBlockModelProvider.Builder().setUnusedProperties(HopperBlock.ENABLED).build());
        this.register(Blocks.FLOWER_POT, new VariantBlockModelProvider.Builder().setUnusedProperties(FlowerPotBlock.LEGACY_DATA).build());
        this.register(Blocks.QUARTZ_BLOCK, new AbstractBlockModelProvider() {
            @Override
            protected ModelIdentifier provide(BlockState state) {
                QuartzBlock.Variant quartzblock$variant = state.get(QuartzBlock.VARIANT);
                switch (quartzblock$variant) {
                    case DEFAULT:
                    default:
                        return new ModelIdentifier("quartz_block", "normal");
                    case CHISELED:
                        return new ModelIdentifier("chiseled_quartz_block", "normal");
                    case LINES_Y:
                        return new ModelIdentifier("quartz_column", "axis=y");
                    case LINES_X:
                        return new ModelIdentifier("quartz_column", "axis=x");
                    case LINES_Z:
                        return new ModelIdentifier("quartz_column", "axis=z");
                }
            }
        });
        this.register(Blocks.DEADBUSH, new AbstractBlockModelProvider() {
            @Override
            protected ModelIdentifier provide(BlockState state) {
                return new ModelIdentifier("dead_bush", "normal");
            }
        });
        this.register(Blocks.PUMPKIN_STEM, new AbstractBlockModelProvider() {
            @Override
            protected ModelIdentifier provide(BlockState state) {
                Map<Property, Comparable> map = Maps.newLinkedHashMap(state.values());
                if (state.get(StemBlock.FACING) != Direction.UP) {
                    map.remove(StemBlock.AGE);
                }

                return new ModelIdentifier(Block.REGISTRY.getKey(state.getBlock()), this.propertiesAsString(map));
            }
        });
        this.register(Blocks.MELON_STEM, new AbstractBlockModelProvider() {
            @Override
            protected ModelIdentifier provide(BlockState state) {
                Map<Property, Comparable> map = Maps.newLinkedHashMap(state.values());
                if (state.get(StemBlock.FACING) != Direction.UP) {
                    map.remove(StemBlock.AGE);
                }

                return new ModelIdentifier(Block.REGISTRY.getKey(state.getBlock()), this.propertiesAsString(map));
            }
        });
        this.register(Blocks.DIRT, new AbstractBlockModelProvider() {
            @Override
            protected ModelIdentifier provide(BlockState state) {
                Map<Property, Comparable> map = Maps.newLinkedHashMap(state.values());
                String s = DirtBlock.VARIANT.getName(map.remove(DirtBlock.VARIANT));
                if (DirtBlock.Variant.PODZOL != state.get(DirtBlock.VARIANT)) {
                    map.remove(DirtBlock.SNOWY);
                }

                return new ModelIdentifier(s, this.propertiesAsString(map));
            }
        });
        this.register(Blocks.DOUBLE_STONE_SLAB, new AbstractBlockModelProvider() {
            @Override
            protected ModelIdentifier provide(BlockState state) {
                Map<Property, Comparable> map = Maps.newLinkedHashMap(state.values());
                String s = StoneSlabBlock.VARIANT.getName(map.remove(StoneSlabBlock.VARIANT));
                map.remove(StoneSlabBlock.SEAMLESS);
                String s1 = state.get(StoneSlabBlock.SEAMLESS) ? "all" : "normal";
                return new ModelIdentifier(s + "_double_slab", s1);
            }
        });
        this.register(Blocks.DOUBLE_RED_SANDSTONE_SLAB, new AbstractBlockModelProvider() {
            @Override
            protected ModelIdentifier provide(BlockState state) {
                Map<Property, Comparable> map = Maps.newLinkedHashMap(state.values());
                String s = RedSandstoneSlab.VARIANT.getName(map.remove(RedSandstoneSlab.VARIANT));
                map.remove(StoneSlabBlock.SEAMLESS);
                String s1 = state.get(RedSandstoneSlab.SEAMLESS) ? "all" : "normal";
                return new ModelIdentifier(s + "_double_slab", s1);
            }
        });
    }
}
