package net.minecraft.client.resource.model;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.block.BlockModelShaper;
import net.minecraft.client.render.model.block.BlockElement;
import net.minecraft.client.render.model.block.BlockElementFace;
import net.minecraft.client.render.model.block.BlockModel;
import net.minecraft.client.render.model.block.BlockModelDefinition;
import net.minecraft.client.render.model.block.FaceBakery;
import net.minecraft.client.render.model.block.ItemModelGenerator;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.client.render.texture.TextureAtlasSprite;
import net.minecraft.client.resource.ModelIdentifier;
import net.minecraft.client.resource.Resource;
import net.minecraft.client.resource.manager.ResourceManager;
import net.minecraft.client.texture.SpriteSource;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.resource.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.registry.MappedRegistry;
import net.minecraft.util.registry.Registry;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ModelBakery {
    private static final Set<Identifier> UNREFERENCED_TEXTURES = Sets.newHashSet(
        new Identifier("blocks/water_flow"),
        new Identifier("blocks/water_still"),
        new Identifier("blocks/lava_flow"),
        new Identifier("blocks/lava_still"),
        new Identifier("blocks/destroy_stage_0"),
        new Identifier("blocks/destroy_stage_1"),
        new Identifier("blocks/destroy_stage_2"),
        new Identifier("blocks/destroy_stage_3"),
        new Identifier("blocks/destroy_stage_4"),
        new Identifier("blocks/destroy_stage_5"),
        new Identifier("blocks/destroy_stage_6"),
        new Identifier("blocks/destroy_stage_7"),
        new Identifier("blocks/destroy_stage_8"),
        new Identifier("blocks/destroy_stage_9"),
        new Identifier("items/empty_armor_slot_helmet"),
        new Identifier("items/empty_armor_slot_chestplate"),
        new Identifier("items/empty_armor_slot_leggings"),
        new Identifier("items/empty_armor_slot_boots")
    );
    private static final Logger LOGGER = LogManager.getLogger();
    protected static final ModelIdentifier MISSING = new ModelIdentifier("builtin/missing", "missing");
    private static final Map<String, String> BUILT_IN = Maps.newHashMap();
    private static final Joiner PARENT_CHAIN_JOINER = Joiner.on(" -> ");
    private final ResourceManager resourceManager;
    private final Map<Identifier, TextureAtlasSprite> sprites = Maps.newHashMap();
    private final Map<Identifier, BlockModel> blockModels = Maps.newLinkedHashMap();
    private final Map<ModelIdentifier, BlockModelDefinition.MultiVariant> variants = Maps.newLinkedHashMap();
    private final TextureAtlas blockAtlas;
    private final BlockModelShaper modelShaper;
    private final FaceBakery faceBakery = new FaceBakery();
    private final ItemModelGenerator itemModelGenerator = new ItemModelGenerator();
    private MappedRegistry<ModelIdentifier, BakedModel> bakedRegistry = new MappedRegistry<>();
    private static final BlockModel GENERATION_MARKER = BlockModel.fromJson(
        "{\"elements\":[{  \"from\": [0, 0, 0],   \"to\": [16, 16, 16],   \"faces\": {       \"down\": {\"uv\": [0, 0, 16, 16], \"texture\":\"\"}   }}]}"
    );
    private static final BlockModel COMPASS_GENERATION_MARKER = BlockModel.fromJson(
        "{\"elements\":[{  \"from\": [0, 0, 0],   \"to\": [16, 16, 16],   \"faces\": {       \"down\": {\"uv\": [0, 0, 16, 16], \"texture\":\"\"}   }}]}"
    );
    private static final BlockModel CLASS_GENERATION_MARKER = BlockModel.fromJson(
        "{\"elements\":[{  \"from\": [0, 0, 0],   \"to\": [16, 16, 16],   \"faces\": {       \"down\": {\"uv\": [0, 0, 16, 16], \"texture\":\"\"}   }}]}"
    );
    private static final BlockModel BLOCK_ENTITY_MARKER = BlockModel.fromJson(
        "{\"elements\":[{  \"from\": [0, 0, 0],   \"to\": [16, 16, 16],   \"faces\": {       \"down\": {\"uv\": [0, 0, 16, 16], \"texture\":\"\"}   }}]}"
    );
    private Map<String, Identifier> itemModels = Maps.newLinkedHashMap();
    private final Map<Identifier, BlockModelDefinition> modelDefinitions = Maps.newHashMap();
    private Map<Item, List<String>> itemVariants = Maps.newIdentityHashMap();

    public ModelBakery(ResourceManager resourceManager, TextureAtlas blockAtlas, BlockModelShaper modelShaper) {
        this.resourceManager = resourceManager;
        this.blockAtlas = blockAtlas;
        this.modelShaper = modelShaper;
    }

    public Registry<ModelIdentifier, BakedModel> getBakedModels() {
        this.loadBuiltIn();
        this.finalizeBlockModels();
        this.loadSprites();
        this.generateItemModels();
        this.bakeModels();
        return this.bakedRegistry;
    }

    private void loadBuiltIn() {
        this.registerVariants(this.modelShaper.getModels().provide().values());
        this.variants
            .put(
                MISSING,
                new BlockModelDefinition.MultiVariant(
                    MISSING.getVariant(),
                    Lists.newArrayList(new BlockModelDefinition.Variant(new Identifier(MISSING.getPath()), ModelRotation.X0_Y0, false, 1))
                )
            );
        Identifier identifier = new Identifier("item_frame");
        BlockModelDefinition blockmodeldefinition = this.loadDefinition(identifier);
        this.registerVariant(blockmodeldefinition, new ModelIdentifier(identifier, "normal"));
        this.registerVariant(blockmodeldefinition, new ModelIdentifier(identifier, "map"));
        this.loadBlockModels();
        this.loadItemModels();
    }

    private void registerVariants(Collection<ModelIdentifier> locations) {
        for (ModelIdentifier modelidentifier : locations) {
            try {
                BlockModelDefinition blockmodeldefinition = this.loadDefinition(modelidentifier);

                try {
                    this.registerVariant(blockmodeldefinition, modelidentifier);
                } catch (Exception exception) {
                    LOGGER.warn("Unable to load variant: " + modelidentifier.getVariant() + " from " + modelidentifier);
                }
            } catch (Exception exception1) {
                LOGGER.warn("Unable to load definition " + modelidentifier, exception1);
            }
        }
    }

    private void registerVariant(BlockModelDefinition definition, ModelIdentifier location) {
        this.variants.put(location, definition.getVariant(location.getVariant()));
    }

    private BlockModelDefinition loadDefinition(Identifier location) {
        Identifier identifier = this.getBlockStatesJsonLocation(location);
        BlockModelDefinition blockmodeldefinition = this.modelDefinitions.get(identifier);
        if (blockmodeldefinition == null) {
            List<BlockModelDefinition> list = Lists.newArrayList();

            try {
                for (Resource resource : this.resourceManager.getResources(identifier)) {
                    InputStream inputstream = null;

                    try {
                        inputstream = resource.asStream();
                        BlockModelDefinition blockmodeldefinition1 = BlockModelDefinition.fromJson(new InputStreamReader(inputstream, Charsets.UTF_8));
                        list.add(blockmodeldefinition1);
                    } catch (Exception exception) {
                        throw new RuntimeException(
                            "Encountered an exception when loading model definition of '"
                                + location
                                + "' from: '"
                                + resource.getLocation()
                                + "' in resourcepack: '"
                                + resource.getSourceName()
                                + "'",
                            exception
                        );
                    } finally {
                        IOUtils.closeQuietly(inputstream);
                    }
                }
            } catch (IOException ioexception) {
                throw new RuntimeException("Encountered an exception when loading model definition of model " + identifier.toString(), ioexception);
            }

            blockmodeldefinition = new BlockModelDefinition(list);
            this.modelDefinitions.put(identifier, blockmodeldefinition);
        }

        return blockmodeldefinition;
    }

    private Identifier getBlockStatesJsonLocation(Identifier location) {
        return new Identifier(location.getNamespace(), "blockstates/" + location.getPath() + ".json");
    }

    private void loadBlockModels() {
        for (ModelIdentifier modelidentifier : this.variants.keySet()) {
            for (BlockModelDefinition.Variant blockmodeldefinition$variant : this.variants.get(modelidentifier).getVariants()) {
                Identifier identifier = blockmodeldefinition$variant.getLocation();
                if (this.blockModels.get(identifier) == null) {
                    try {
                        BlockModel blockmodel = this.loadBlockModel(identifier);
                        this.blockModels.put(identifier, blockmodel);
                    } catch (Exception exception) {
                        LOGGER.warn("Unable to load block model: '" + identifier + "' for variant: '" + modelidentifier + "'", exception);
                    }
                }
            }
        }
    }

    private BlockModel loadBlockModel(Identifier location) throws IOException {
        String s = location.getPath();
        if ("builtin/generated".equals(s)) {
            return GENERATION_MARKER;
        }

        if ("builtin/compass".equals(s)) {
            return COMPASS_GENERATION_MARKER;
        }

        if ("builtin/clock".equals(s)) {
            return CLASS_GENERATION_MARKER;
        }

        if ("builtin/entity".equals(s)) {
            return BLOCK_ENTITY_MARKER;
        }

        Reader reader;
        if (s.startsWith("builtin/")) {
            String s1 = s.substring("builtin/".length());
            String s2 = BUILT_IN.get(s1);
            if (s2 == null) {
                throw new FileNotFoundException(location.toString());
            }

            reader = new StringReader(s2);
        } else {
            Resource resource = this.resourceManager.getResource(this.getModelsJsonLocation(location));
            reader = new InputStreamReader(resource.asStream(), Charsets.UTF_8);
        }

        try {
            BlockModel blockmodel = BlockModel.fromJson(reader);
            blockmodel.name = location.toString();
            return blockmodel;
        } finally {
            reader.close();
        }
    }

    private Identifier getModelsJsonLocation(Identifier location) {
        return new Identifier(location.getNamespace(), "models/" + location.getPath() + ".json");
    }

    private void loadItemModels() {
        this.registerItemVariants();

        for (Item item : Item.REGISTRY) {
            for (String s : this.getItemVariants(item)) {
                Identifier identifier = this.getItemModelLocation(s);
                this.itemModels.put(s, identifier);
                if (this.blockModels.get(identifier) == null) {
                    try {
                        BlockModel blockmodel = this.loadBlockModel(identifier);
                        this.blockModels.put(identifier, blockmodel);
                    } catch (Exception exception) {
                        LOGGER.warn("Unable to load item model: '" + identifier + "' for item: '" + Item.REGISTRY.getKey(item) + "'", exception);
                    }
                }
            }
        }
    }

    private void registerItemVariants() {
        this.itemVariants
            .put(
                Item.byBlock(Blocks.STONE),
                Lists.newArrayList("stone", "granite", "granite_smooth", "diorite", "diorite_smooth", "andesite", "andesite_smooth")
            );
        this.itemVariants.put(Item.byBlock(Blocks.DIRT), Lists.newArrayList("dirt", "coarse_dirt", "podzol"));
        this.itemVariants
            .put(
                Item.byBlock(Blocks.PLANKS),
                Lists.newArrayList("oak_planks", "spruce_planks", "birch_planks", "jungle_planks", "acacia_planks", "dark_oak_planks")
            );
        this.itemVariants
            .put(
                Item.byBlock(Blocks.SAPLING),
                Lists.newArrayList("oak_sapling", "spruce_sapling", "birch_sapling", "jungle_sapling", "acacia_sapling", "dark_oak_sapling")
            );
        this.itemVariants.put(Item.byBlock(Blocks.SAND), Lists.newArrayList("sand", "red_sand"));
        this.itemVariants.put(Item.byBlock(Blocks.LOG), Lists.newArrayList("oak_log", "spruce_log", "birch_log", "jungle_log"));
        this.itemVariants.put(Item.byBlock(Blocks.LEAVES), Lists.newArrayList("oak_leaves", "spruce_leaves", "birch_leaves", "jungle_leaves"));
        this.itemVariants.put(Item.byBlock(Blocks.SPONGE), Lists.newArrayList("sponge", "sponge_wet"));
        this.itemVariants.put(Item.byBlock(Blocks.SANDSTONE), Lists.newArrayList("sandstone", "chiseled_sandstone", "smooth_sandstone"));
        this.itemVariants.put(Item.byBlock(Blocks.RED_SANDSTONE), Lists.newArrayList("red_sandstone", "chiseled_red_sandstone", "smooth_red_sandstone"));
        this.itemVariants.put(Item.byBlock(Blocks.TALLGRASS), Lists.newArrayList("dead_bush", "tall_grass", "fern"));
        this.itemVariants.put(Item.byBlock(Blocks.DEADBUSH), Lists.newArrayList("dead_bush"));
        this.itemVariants
            .put(
                Item.byBlock(Blocks.WOOL),
                Lists.newArrayList(
                    "black_wool",
                    "red_wool",
                    "green_wool",
                    "brown_wool",
                    "blue_wool",
                    "purple_wool",
                    "cyan_wool",
                    "silver_wool",
                    "gray_wool",
                    "pink_wool",
                    "lime_wool",
                    "yellow_wool",
                    "light_blue_wool",
                    "magenta_wool",
                    "orange_wool",
                    "white_wool"
                )
            );
        this.itemVariants.put(Item.byBlock(Blocks.YELLOW_FLOWER), Lists.newArrayList("dandelion"));
        this.itemVariants
            .put(
                Item.byBlock(Blocks.RED_FLOWER),
                Lists.newArrayList("poppy", "blue_orchid", "allium", "houstonia", "red_tulip", "orange_tulip", "white_tulip", "pink_tulip", "oxeye_daisy")
            );
        this.itemVariants
            .put(
                Item.byBlock(Blocks.STONE_SLAB),
                Lists.newArrayList("stone_slab", "sandstone_slab", "cobblestone_slab", "brick_slab", "stone_brick_slab", "nether_brick_slab", "quartz_slab")
            );
        this.itemVariants.put(Item.byBlock(Blocks.RED_SANDSTONE_SLAB), Lists.newArrayList("red_sandstone_slab"));
        this.itemVariants
            .put(
                Item.byBlock(Blocks.STAINED_GLASS),
                Lists.newArrayList(
                    "black_stained_glass",
                    "red_stained_glass",
                    "green_stained_glass",
                    "brown_stained_glass",
                    "blue_stained_glass",
                    "purple_stained_glass",
                    "cyan_stained_glass",
                    "silver_stained_glass",
                    "gray_stained_glass",
                    "pink_stained_glass",
                    "lime_stained_glass",
                    "yellow_stained_glass",
                    "light_blue_stained_glass",
                    "magenta_stained_glass",
                    "orange_stained_glass",
                    "white_stained_glass"
                )
            );
        this.itemVariants
            .put(
                Item.byBlock(Blocks.MONSTER_EGG),
                Lists.newArrayList(
                    "stone_monster_egg",
                    "cobblestone_monster_egg",
                    "stone_brick_monster_egg",
                    "mossy_brick_monster_egg",
                    "cracked_brick_monster_egg",
                    "chiseled_brick_monster_egg"
                )
            );
        this.itemVariants
            .put(Item.byBlock(Blocks.STONE_BRICKS), Lists.newArrayList("stonebrick", "mossy_stonebrick", "cracked_stonebrick", "chiseled_stonebrick"));
        this.itemVariants
            .put(Item.byBlock(Blocks.WOODEN_SLAB), Lists.newArrayList("oak_slab", "spruce_slab", "birch_slab", "jungle_slab", "acacia_slab", "dark_oak_slab"));
        this.itemVariants.put(Item.byBlock(Blocks.COBBLESTONE_WALL), Lists.newArrayList("cobblestone_wall", "mossy_cobblestone_wall"));
        this.itemVariants.put(Item.byBlock(Blocks.ANVIL), Lists.newArrayList("anvil_intact", "anvil_slightly_damaged", "anvil_very_damaged"));
        this.itemVariants.put(Item.byBlock(Blocks.QUARTZ_BLOCK), Lists.newArrayList("quartz_block", "chiseled_quartz_block", "quartz_column"));
        this.itemVariants
            .put(
                Item.byBlock(Blocks.STAINED_HARDENED_CLAY),
                Lists.newArrayList(
                    "black_stained_hardened_clay",
                    "red_stained_hardened_clay",
                    "green_stained_hardened_clay",
                    "brown_stained_hardened_clay",
                    "blue_stained_hardened_clay",
                    "purple_stained_hardened_clay",
                    "cyan_stained_hardened_clay",
                    "silver_stained_hardened_clay",
                    "gray_stained_hardened_clay",
                    "pink_stained_hardened_clay",
                    "lime_stained_hardened_clay",
                    "yellow_stained_hardened_clay",
                    "light_blue_stained_hardened_clay",
                    "magenta_stained_hardened_clay",
                    "orange_stained_hardened_clay",
                    "white_stained_hardened_clay"
                )
            );
        this.itemVariants
            .put(
                Item.byBlock(Blocks.STAINED_GLASS_PANE),
                Lists.newArrayList(
                    "black_stained_glass_pane",
                    "red_stained_glass_pane",
                    "green_stained_glass_pane",
                    "brown_stained_glass_pane",
                    "blue_stained_glass_pane",
                    "purple_stained_glass_pane",
                    "cyan_stained_glass_pane",
                    "silver_stained_glass_pane",
                    "gray_stained_glass_pane",
                    "pink_stained_glass_pane",
                    "lime_stained_glass_pane",
                    "yellow_stained_glass_pane",
                    "light_blue_stained_glass_pane",
                    "magenta_stained_glass_pane",
                    "orange_stained_glass_pane",
                    "white_stained_glass_pane"
                )
            );
        this.itemVariants.put(Item.byBlock(Blocks.LEAVES2), Lists.newArrayList("acacia_leaves", "dark_oak_leaves"));
        this.itemVariants.put(Item.byBlock(Blocks.LOG2), Lists.newArrayList("acacia_log", "dark_oak_log"));
        this.itemVariants.put(Item.byBlock(Blocks.PRISMARINE), Lists.newArrayList("prismarine", "prismarine_bricks", "dark_prismarine"));
        this.itemVariants
            .put(
                Item.byBlock(Blocks.CARPET),
                Lists.newArrayList(
                    "black_carpet",
                    "red_carpet",
                    "green_carpet",
                    "brown_carpet",
                    "blue_carpet",
                    "purple_carpet",
                    "cyan_carpet",
                    "silver_carpet",
                    "gray_carpet",
                    "pink_carpet",
                    "lime_carpet",
                    "yellow_carpet",
                    "light_blue_carpet",
                    "magenta_carpet",
                    "orange_carpet",
                    "white_carpet"
                )
            );
        this.itemVariants
            .put(Item.byBlock(Blocks.DOUBLE_PLANT), Lists.newArrayList("sunflower", "syringa", "double_grass", "double_fern", "double_rose", "paeonia"));
        this.itemVariants.put(Items.BOW, Lists.newArrayList("bow", "bow_pulling_0", "bow_pulling_1", "bow_pulling_2"));
        this.itemVariants.put(Items.COAL, Lists.newArrayList("coal", "charcoal"));
        this.itemVariants.put(Items.FISHING_ROD, Lists.newArrayList("fishing_rod", "fishing_rod_cast"));
        this.itemVariants.put(Items.FISH, Lists.newArrayList("cod", "salmon", "clownfish", "pufferfish"));
        this.itemVariants.put(Items.COOKED_FISH, Lists.newArrayList("cooked_cod", "cooked_salmon"));
        this.itemVariants
            .put(
                Items.DYE,
                Lists.newArrayList(
                    "dye_black",
                    "dye_red",
                    "dye_green",
                    "dye_brown",
                    "dye_blue",
                    "dye_purple",
                    "dye_cyan",
                    "dye_silver",
                    "dye_gray",
                    "dye_pink",
                    "dye_lime",
                    "dye_yellow",
                    "dye_light_blue",
                    "dye_magenta",
                    "dye_orange",
                    "dye_white"
                )
            );
        this.itemVariants.put(Items.POTION, Lists.newArrayList("bottle_drinkable", "bottle_splash"));
        this.itemVariants.put(Items.SKULL, Lists.newArrayList("skull_skeleton", "skull_wither", "skull_zombie", "skull_char", "skull_creeper"));
        this.itemVariants.put(Item.byBlock(Blocks.FENCE_GATE), Lists.newArrayList("oak_fence_gate"));
        this.itemVariants.put(Item.byBlock(Blocks.FENCE), Lists.newArrayList("oak_fence"));
        this.itemVariants.put(Items.WOODEN_DOOR, Lists.newArrayList("oak_door"));
    }

    private List<String> getItemVariants(Item item) {
        List<String> list = this.itemVariants.get(item);
        if (list == null) {
            list = Collections.singletonList(Item.REGISTRY.getKey(item).toString());
        }

        return list;
    }

    private Identifier getItemModelLocation(String path) {
        Identifier identifier = new Identifier(path);
        return new Identifier(identifier.getNamespace(), "item/" + identifier.getPath());
    }

    private void bakeModels() {
        for (ModelIdentifier modelidentifier : this.variants.keySet()) {
            WeightedBakedModel.Builder weightedbakedmodel$builder = new WeightedBakedModel.Builder();
            int i = 0;

            for (BlockModelDefinition.Variant blockmodeldefinition$variant : this.variants.get(modelidentifier).getVariants()) {
                BlockModel blockmodel = this.blockModels.get(blockmodeldefinition$variant.getLocation());
                if (blockmodel != null && blockmodel.isComplete()) {
                    i++;
                    weightedbakedmodel$builder.add(
                        this.bake(blockmodel, blockmodeldefinition$variant.getRotation(), blockmodeldefinition$variant.isUvLocked()),
                        blockmodeldefinition$variant.getWeight()
                    );
                } else {
                    LOGGER.warn("Missing model for: " + modelidentifier);
                }
            }

            if (i == 0) {
                LOGGER.warn("No weighted models for: " + modelidentifier);
            } else if (i == 1) {
                this.bakedRegistry.put(modelidentifier, weightedbakedmodel$builder.first());
            } else {
                this.bakedRegistry.put(modelidentifier, weightedbakedmodel$builder.build());
            }
        }

        for (Entry<String, Identifier> entry : this.itemModels.entrySet()) {
            Identifier identifier = entry.getValue();
            ModelIdentifier modelidentifier1 = new ModelIdentifier(entry.getKey(), "inventory");
            BlockModel blockmodel1 = this.blockModels.get(identifier);
            if (blockmodel1 == null || !blockmodel1.isComplete()) {
                LOGGER.warn("Missing model for: " + identifier);
            } else if (this.isBlockEntity(blockmodel1)) {
                this.bakedRegistry.put(modelidentifier1, new BuiltInModel(blockmodel1.getTransformations()));
            } else {
                this.bakedRegistry.put(modelidentifier1, this.bake(blockmodel1, ModelRotation.X0_Y0, false));
            }
        }
    }

    private Set<Identifier> getBlockTextures() {
        Set<Identifier> set = Sets.newHashSet();
        List<ModelIdentifier> list = Lists.newArrayList(this.variants.keySet());
        Collections.sort(list, new Comparator<ModelIdentifier>() {
            public int compare(ModelIdentifier modelIdentifier, ModelIdentifier modelIdentifier2) {
                return modelIdentifier.toString().compareTo(modelIdentifier2.toString());
            }
        });

        for (ModelIdentifier modelidentifier : list) {
            BlockModelDefinition.MultiVariant blockmodeldefinition$multivariant = this.variants.get(modelidentifier);

            for (BlockModelDefinition.Variant blockmodeldefinition$variant : blockmodeldefinition$multivariant.getVariants()) {
                BlockModel blockmodel = this.blockModels.get(blockmodeldefinition$variant.getLocation());
                if (blockmodel == null) {
                    LOGGER.warn("Missing model for: " + modelidentifier);
                } else {
                    set.addAll(this.getTextures(blockmodel));
                }
            }
        }

        set.addAll(UNREFERENCED_TEXTURES);
        return set;
    }

    private BakedModel bake(BlockModel model, ModelRotation rotation, boolean uvLock) {
        TextureAtlasSprite textureatlassprite = this.sprites.get(new Identifier(model.getTexture("particle")));
        BasicBakedModel.Builder basicbakedmodel$builder = new BasicBakedModel.Builder(model).particleIcon(textureatlassprite);

        for (BlockElement blockelement : model.getElements()) {
            for (Direction direction : blockelement.faces.keySet()) {
                BlockElementFace blockelementface = blockelement.faces.get(direction);
                TextureAtlasSprite textureatlassprite1 = this.sprites.get(new Identifier(model.getTexture(blockelementface.texture)));
                if (blockelementface.cullFace == null) {
                    basicbakedmodel$builder.unculledFace(this.bakeFace(blockelement, blockelementface, textureatlassprite1, direction, rotation, uvLock));
                } else {
                    basicbakedmodel$builder.culledFace(
                        rotation.clockwise(blockelementface.cullFace),
                        this.bakeFace(blockelement, blockelementface, textureatlassprite1, direction, rotation, uvLock)
                    );
                }
            }
        }

        return basicbakedmodel$builder.build();
    }

    private BakedQuad bakeFace(
        BlockElement element, BlockElementFace elementFace, TextureAtlasSprite blockSprite, Direction face, ModelRotation rotation, boolean uvLock
    ) {
        return this.faceBakery.bakeQuad(element.from, element.to, elementFace, blockSprite, face, rotation, element.rotation, uvLock, element.shade);
    }

    private void finalizeBlockModels() {
        this.loadBlockModelDependencies();

        for (BlockModel blockmodel : this.blockModels.values()) {
            blockmodel.findParent(this.blockModels);
        }

        BlockModel.checkHierarchy(this.blockModels);
    }

    private void loadBlockModelDependencies() {
        Deque<Identifier> deque = Queues.newArrayDeque();
        Set<Identifier> set = Sets.newHashSet();

        for (Identifier identifier : this.blockModels.keySet()) {
            set.add(identifier);
            Identifier identifier1 = this.blockModels.get(identifier).getParentLocation();
            if (identifier1 != null) {
                deque.add(identifier1);
            }
        }

        while (!deque.isEmpty()) {
            Identifier identifier2 = deque.pop();

            try {
                if (this.blockModels.get(identifier2) != null) {
                    continue;
                }

                BlockModel blockmodel = this.loadBlockModel(identifier2);
                this.blockModels.put(identifier2, blockmodel);
                Identifier identifier3 = blockmodel.getParentLocation();
                if (identifier3 != null && !set.contains(identifier3)) {
                    deque.add(identifier3);
                }
            } catch (Exception exception) {
                LOGGER.warn(
                    "In parent chain: "
                        + PARENT_CHAIN_JOINER.join(this.getBlockModelDependencies(identifier2))
                        + "; unable to load model: '"
                        + identifier2
                        + "'",
                    exception
                );
            }

            set.add(identifier2);
        }
    }

    private List<Identifier> getBlockModelDependencies(Identifier location) {
        List<Identifier> list = Lists.newArrayList(location);
        Identifier identifier = location;

        while ((identifier = this.findParentBlockModel(identifier)) != null) {
            list.add(0, identifier);
        }

        return list;
    }

    private Identifier findParentBlockModel(Identifier location) {
        for (Entry<Identifier, BlockModel> entry : this.blockModels.entrySet()) {
            BlockModel blockmodel = entry.getValue();
            if (blockmodel != null && location.equals(blockmodel.getParentLocation())) {
                return entry.getKey();
            }
        }

        return null;
    }

    private Set<Identifier> getTextures(BlockModel model) {
        Set<Identifier> set = Sets.newHashSet();

        for (BlockElement blockelement : model.getElements()) {
            for (BlockElementFace blockelementface : blockelement.faces.values()) {
                Identifier identifier = new Identifier(model.getTexture(blockelementface.texture));
                set.add(identifier);
            }
        }

        set.add(new Identifier(model.getTexture("particle")));
        return set;
    }

    private void loadSprites() {
        final Set<Identifier> set = this.getBlockTextures();
        set.addAll(this.getItemTextures());
        set.remove(TextureAtlas.MISSING_TEXTURE_ID);
        SpriteSource spritesource = new SpriteSource() {
            @Override
            public void registerSprites(TextureAtlas atlas) {
                for (Identifier identifier : set) {
                    TextureAtlasSprite textureatlassprite = atlas.registerSprite(identifier);
                    ModelBakery.this.sprites.put(identifier, textureatlassprite);
                }
            }
        };
        this.blockAtlas.load(this.resourceManager, spritesource);
        this.sprites.put(new Identifier("missingno"), this.blockAtlas.getMissingSprite());
    }

    private Set<Identifier> getItemTextures() {
        Set<Identifier> set = Sets.newHashSet();

        for (Identifier identifier : this.itemModels.values()) {
            BlockModel blockmodel = this.blockModels.get(identifier);
            if (blockmodel != null) {
                set.add(new Identifier(blockmodel.getTexture("particle")));
                if (this.isGeneration(blockmodel)) {
                    for (String s : ItemModelGenerator.LAYERS) {
                        Identifier identifier2 = new Identifier(blockmodel.getTexture(s));
                        if (blockmodel.getRoot() == COMPASS_GENERATION_MARKER && !TextureAtlas.MISSING_TEXTURE_ID.equals(identifier2)) {
                            TextureAtlasSprite.setCompassPath(identifier2.toString());
                        } else if (blockmodel.getRoot() == CLASS_GENERATION_MARKER && !TextureAtlas.MISSING_TEXTURE_ID.equals(identifier2)) {
                            TextureAtlasSprite.setClockPath(identifier2.toString());
                        }

                        set.add(identifier2);
                    }
                } else if (!this.isBlockEntity(blockmodel)) {
                    for (BlockElement blockelement : blockmodel.getElements()) {
                        for (BlockElementFace blockelementface : blockelement.faces.values()) {
                            Identifier identifier1 = new Identifier(blockmodel.getTexture(blockelementface.texture));
                            set.add(identifier1);
                        }
                    }
                }
            }
        }

        return set;
    }

    private boolean isGeneration(BlockModel model) {
        if (model == null) {
            return false;
        }

        BlockModel blockmodel = model.getRoot();
        return blockmodel == GENERATION_MARKER || blockmodel == COMPASS_GENERATION_MARKER || blockmodel == CLASS_GENERATION_MARKER;
    }

    private boolean isBlockEntity(BlockModel model) {
        if (model == null) {
            return false;
        }

        BlockModel blockmodel = model.getRoot();
        return blockmodel == BLOCK_ENTITY_MARKER;
    }

    private void generateItemModels() {
        for (Identifier identifier : this.itemModels.values()) {
            BlockModel blockmodel = this.blockModels.get(identifier);
            if (this.isGeneration(blockmodel)) {
                BlockModel blockmodel1 = this.generateItemModels(blockmodel);
                if (blockmodel1 != null) {
                    blockmodel1.name = identifier.toString();
                }

                this.blockModels.put(identifier, blockmodel1);
            } else if (this.isBlockEntity(blockmodel)) {
                this.blockModels.put(identifier, blockmodel);
            }
        }

        for (TextureAtlasSprite textureatlassprite : this.sprites.values()) {
            if (!textureatlassprite.isAnimated()) {
                textureatlassprite.clearFrames();
            }
        }
    }

    private BlockModel generateItemModels(BlockModel model) {
        return this.itemModelGenerator.generate(this.blockAtlas, model);
    }

    static {
        BUILT_IN.put(
            "missing",
            "{ \"textures\": {   \"particle\": \"missingno\",   \"missingno\": \"missingno\"}, \"elements\": [ {     \"from\": [ 0, 0, 0 ],     \"to\": [ 16, 16, 16 ],     \"faces\": {         \"down\":  { \"uv\": [ 0, 0, 16, 16 ], \"cullface\": \"down\", \"texture\": \"#missingno\" },         \"up\":    { \"uv\": [ 0, 0, 16, 16 ], \"cullface\": \"up\", \"texture\": \"#missingno\" },         \"north\": { \"uv\": [ 0, 0, 16, 16 ], \"cullface\": \"north\", \"texture\": \"#missingno\" },         \"south\": { \"uv\": [ 0, 0, 16, 16 ], \"cullface\": \"south\", \"texture\": \"#missingno\" },         \"west\":  { \"uv\": [ 0, 0, 16, 16 ], \"cullface\": \"west\", \"texture\": \"#missingno\" },         \"east\":  { \"uv\": [ 0, 0, 16, 16 ], \"cullface\": \"east\", \"texture\": \"#missingno\" }    }}]}"
        );
        GENERATION_MARKER.name = "generation marker";
        COMPASS_GENERATION_MARKER.name = "compass generation marker";
        CLASS_GENERATION_MARKER.name = "class generation marker";
        BLOCK_ENTITY_MARKER.name = "block entity marker";
    }
}
