package net.minecraft.client.render.entity;

import java.util.List;
import java.util.concurrent.Callable;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.DirtBlock;
import net.minecraft.block.DoublePlantBlock;
import net.minecraft.block.FlowerBlock;
import net.minecraft.block.InfestedBlock;
import net.minecraft.block.MushroomBlock;
import net.minecraft.block.PlanksBlock;
import net.minecraft.block.PrismarineBlock;
import net.minecraft.block.QuartzBlock;
import net.minecraft.block.RedSandstoneBlock;
import net.minecraft.block.RedSandstoneSlab;
import net.minecraft.block.SandBlock;
import net.minecraft.block.SandstoneBlock;
import net.minecraft.block.StoneBlock;
import net.minecraft.block.StoneSlabBlock;
import net.minecraft.block.StonebrickBlock;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.item.ItemModelShaper;
import net.minecraft.client.render.model.block.ModelTransformation;
import net.minecraft.client.render.model.block.ModelTransformations;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.client.render.texture.TextureManager;
import net.minecraft.client.render.texture.TextureUtil;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.client.resource.ModelIdentifier;
import net.minecraft.client.resource.manager.ResourceManager;
import net.minecraft.client.resource.manager.ResourceReloadListener;
import net.minecraft.client.resource.model.BakedModel;
import net.minecraft.client.resource.model.BakedQuad;
import net.minecraft.client.resource.model.ItemModelProvider;
import net.minecraft.client.resource.model.ModelManager;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.DyeColor;
import net.minecraft.item.FishItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.PotionItem;
import net.minecraft.resource.Identifier;
import net.minecraft.text.Formatting;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportCategory;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;

public class ItemRenderer implements ResourceReloadListener {
    private static final Identifier ENCHANTMENT_GLINT_LOCATION = new Identifier("textures/misc/enchanted_item_glint.png");
    private boolean useCustomDisplayColor = true;
    public float zOffset;
    private final ItemModelShaper modelShaper;
    private final TextureManager textureManager;

    public ItemRenderer(TextureManager textureManager, ModelManager manager) {
        this.textureManager = textureManager;
        this.modelShaper = new ItemModelShaper(manager);
        this.registerGuiModels();
    }

    public void setUseCustomDisplayColor(boolean useCustomDisplayColor) {
        this.useCustomDisplayColor = useCustomDisplayColor;
    }

    public ItemModelShaper getModelShaper() {
        return this.modelShaper;
    }

    protected void registerModel(Item item, int metadata, String key) {
        this.modelShaper.register(item, metadata, new ModelIdentifier(key, "inventory"));
    }

    protected void registerModel(Block block, int metadata, String key) {
        this.registerModel(Item.byBlock(block), metadata, key);
    }

    private void registerModel(Block block, String key) {
        this.registerModel(block, 0, key);
    }

    private void registerModel(Item item, String key) {
        this.registerModel(item, 0, key);
    }

    private void render(BakedModel model, ItemStack item) {
        this.render(model, -1, item);
    }

    private void render(BakedModel model, int color) {
        this.render(model, color, null);
    }

    private void render(BakedModel model, int color, ItemStack item) {
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuffer();
        bufferbuilder.begin(7, DefaultVertexFormat.BLOCK_NORMALS);

        for (Direction direction : Direction.values()) {
            this.renderQuads(bufferbuilder, model.getQuads(direction), color, item);
        }

        this.renderQuads(bufferbuilder, model.getQuads(), color, item);
        tesselator.end();
    }

    public void renderItem(ItemStack item, BakedModel model) {
        if (item != null) {
            GlStateManager.pushMatrix();
            GlStateManager.scalef(0.5F, 0.5F, 0.5F);
            if (model.isCustomRenderer()) {
                GlStateManager.rotatef(180.0F, 0.0F, 1.0F, 0.0F);
                GlStateManager.translatef(-0.5F, -0.5F, -0.5F);
                GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
                GlStateManager.enableRescaleNormal();
                BlockEntityItemRenderer.INSTANCE.render(item);
            } else {
                GlStateManager.translatef(-0.5F, -0.5F, -0.5F);
                this.render(model, item);
                if (item.hasEnchantmentGlint()) {
                    this.renderEnchantmentGlint(model);
                }
            }

            GlStateManager.popMatrix();
        }
    }

    private void renderEnchantmentGlint(BakedModel model) {
        GlStateManager.depthMask(false);
        GlStateManager.depthFunc(514);
        GlStateManager.disableLighting();
        GlStateManager.blendFunc(768, 1);
        this.textureManager.bind(ENCHANTMENT_GLINT_LOCATION);
        GlStateManager.matrixMode(5890);
        GlStateManager.pushMatrix();
        GlStateManager.scalef(8.0F, 8.0F, 8.0F);
        float f = (float)(Minecraft.getTime() % 3000L) / 3000.0F / 8.0F;
        GlStateManager.translatef(f, 0.0F, 0.0F);
        GlStateManager.rotatef(-50.0F, 0.0F, 0.0F, 1.0F);
        this.render(model, -8372020);
        GlStateManager.popMatrix();
        GlStateManager.pushMatrix();
        GlStateManager.scalef(8.0F, 8.0F, 8.0F);
        float f1 = (float)(Minecraft.getTime() % 4873L) / 4873.0F / 8.0F;
        GlStateManager.translatef(-f1, 0.0F, 0.0F);
        GlStateManager.rotatef(10.0F, 0.0F, 0.0F, 1.0F);
        this.render(model, -8372020);
        GlStateManager.popMatrix();
        GlStateManager.matrixMode(5888);
        GlStateManager.blendFunc(770, 771);
        GlStateManager.enableLighting();
        GlStateManager.depthFunc(515);
        GlStateManager.depthMask(true);
        this.textureManager.bind(TextureAtlas.BLOCKS_LOCATION);
    }

    private void applyNormal(BufferBuilder bufferBuilder, BakedQuad quad) {
        Vec3i vec3i = quad.getFace().getNormal();
        bufferBuilder.postNormal(vec3i.getX(), vec3i.getY(), vec3i.getZ());
    }

    private void renderQuad(BufferBuilder bufferBuilder, BakedQuad quad, int color) {
        bufferBuilder.vertices(quad.getVertices());
        bufferBuilder.setQuadColor(color);
        this.applyNormal(bufferBuilder, quad);
    }

    private void renderQuads(BufferBuilder bufferBuilder, List<BakedQuad> quads, int color, ItemStack item) {
        boolean flag = color == -1 && item != null;
        int i = 0;

        for (int j = quads.size(); i < j; i++) {
            BakedQuad bakedquad = quads.get(i);
            int k = color;
            if (flag && bakedquad.hasTint()) {
                k = item.getItem().getDisplayColor(item, bakedquad.getTintIndex());
                if (GameRenderer.anaglyphEnabled) {
                    k = TextureUtil.getAnaglyphColor(k);
                }

                k |= -16777216;
            }

            this.renderQuad(bufferBuilder, bakedquad, k);
        }
    }

    public boolean isGui3d(ItemStack item) {
        BakedModel bakedmodel = this.modelShaper.getModel(item);
        return bakedmodel != null && bakedmodel.isGui3d();
    }

    private void prepareInHand(ItemStack item) {
        BakedModel bakedmodel = this.modelShaper.getModel(item);
        Item itemx = item.getItem();
        if (itemx != null) {
            boolean flag = bakedmodel.isGui3d();
            if (!flag) {
                GlStateManager.scalef(2.0F, 2.0F, 2.0F);
            }

            GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    public void renderItemInHand(ItemStack item, ModelTransformations.Type transform) {
        if (item != null) {
            BakedModel bakedmodel = this.modelShaper.getModel(item);
            this.renderItemInHand(item, bakedmodel, transform);
        }
    }

    public void renderItemInHand(ItemStack item, LivingEntity entity, ModelTransformations.Type transform) {
        if (item != null && entity != null) {
            BakedModel bakedmodel = this.modelShaper.getModel(item);
            if (entity instanceof PlayerEntity) {
                PlayerEntity playerentity = (PlayerEntity)entity;
                Item itemx = item.getItem();
                ModelIdentifier modelidentifier = null;
                if (itemx == Items.FISHING_ROD && playerentity.fishingBobber != null) {
                    modelidentifier = new ModelIdentifier("fishing_rod_cast", "inventory");
                } else if (itemx == Items.BOW && playerentity.getItemInUse() != null) {
                    int i = item.getUseDuration() - playerentity.getItemUseTimer();
                    if (i >= 18) {
                        modelidentifier = new ModelIdentifier("bow_pulling_2", "inventory");
                    } else if (i > 13) {
                        modelidentifier = new ModelIdentifier("bow_pulling_1", "inventory");
                    } else if (i > 0) {
                        modelidentifier = new ModelIdentifier("bow_pulling_0", "inventory");
                    }
                }

                if (modelidentifier != null) {
                    bakedmodel = this.modelShaper.getManager().getModel(modelidentifier);
                }
            }

            this.renderItemInHand(item, bakedmodel, transform);
        }
    }

    protected void renderItemInHand(ItemStack item, BakedModel model, ModelTransformations.Type transform) {
        this.textureManager.bind(TextureAtlas.BLOCKS_LOCATION);
        this.textureManager.get(TextureAtlas.BLOCKS_LOCATION).pushFilter(false, false);
        this.prepareInHand(item);
        GlStateManager.enableRescaleNormal();
        GlStateManager.alphaFunc(516, 0.1F);
        GlStateManager.enableBlend();
        GlStateManager.blendFuncSeparate(770, 771, 1, 0);
        GlStateManager.pushMatrix();
        ModelTransformations modeltransformations = model.getTransformations();
        modeltransformations.apply(transform);
        if (this.shouldCullFrontFace(modeltransformations.get(transform))) {
            GlStateManager.cullFace(1028);
        }

        this.renderItem(item, model);
        GlStateManager.cullFace(1029);
        GlStateManager.popMatrix();
        GlStateManager.disableRescaleNormal();
        GlStateManager.disableBlend();
        this.textureManager.bind(TextureAtlas.BLOCKS_LOCATION);
        this.textureManager.get(TextureAtlas.BLOCKS_LOCATION).popFilter();
    }

    private boolean shouldCullFrontFace(ModelTransformation transformation) {
        return transformation.scale.x < 0.0F ^ transformation.scale.y < 0.0F ^ transformation.scale.z < 0.0F;
    }

    public void renderGuiItemModel(ItemStack item, int x, int y) {
        BakedModel bakedmodel = this.modelShaper.getModel(item);
        GlStateManager.pushMatrix();
        this.textureManager.bind(TextureAtlas.BLOCKS_LOCATION);
        this.textureManager.get(TextureAtlas.BLOCKS_LOCATION).pushFilter(false, false);
        GlStateManager.enableRescaleNormal();
        GlStateManager.enableAlphaTest();
        GlStateManager.alphaFunc(516, 0.1F);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(770, 771);
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        this.prepareGuiItemRender(x, y, bakedmodel.isGui3d());
        bakedmodel.getTransformations().apply(ModelTransformations.Type.GUI);
        this.renderItem(item, bakedmodel);
        GlStateManager.disableAlphaTest();
        GlStateManager.disableRescaleNormal();
        GlStateManager.disableLighting();
        GlStateManager.popMatrix();
        this.textureManager.bind(TextureAtlas.BLOCKS_LOCATION);
        this.textureManager.get(TextureAtlas.BLOCKS_LOCATION).popFilter();
    }

    private void prepareGuiItemRender(int x, int y, boolean gui3d) {
        GlStateManager.translatef(x, y, 100.0F + this.zOffset);
        GlStateManager.translatef(8.0F, 8.0F, 0.0F);
        GlStateManager.scalef(1.0F, 1.0F, -1.0F);
        GlStateManager.scalef(0.5F, 0.5F, 0.5F);
        if (gui3d) {
            GlStateManager.scalef(40.0F, 40.0F, 40.0F);
            GlStateManager.rotatef(210.0F, 1.0F, 0.0F, 0.0F);
            GlStateManager.rotatef(-135.0F, 0.0F, 1.0F, 0.0F);
            GlStateManager.enableLighting();
        } else {
            GlStateManager.scalef(64.0F, 64.0F, 64.0F);
            GlStateManager.rotatef(180.0F, 1.0F, 0.0F, 0.0F);
            GlStateManager.disableLighting();
        }
    }

    public void renderGuiItem(ItemStack item, int x, int y) {
        if (item != null && item.getItem() != null) {
            this.zOffset += 50.0F;

            try {
                this.renderGuiItemModel(item, x, y);
            } catch (Throwable throwable) {
                CrashReport crashreport = CrashReport.of(throwable, "Rendering item");
                CrashReportCategory crashreportcategory = crashreport.addCategory("Item being rendered");
                crashreportcategory.add("Item Type", new Callable<String>() {
                    public String call() throws Exception {
                        return String.valueOf(item.getItem());
                    }
                });
                crashreportcategory.add("Item Aux", new Callable<String>() {
                    public String call() throws Exception {
                        return String.valueOf(item.getMetadata());
                    }
                });
                crashreportcategory.add("Item NBT", new Callable<String>() {
                    public String call() throws Exception {
                        return String.valueOf(item.getNbt());
                    }
                });
                crashreportcategory.add("Item Foil", new Callable<String>() {
                    public String call() throws Exception {
                        return String.valueOf(item.hasEnchantmentGlint());
                    }
                });
                throw new CrashException(crashreport);
            }

            this.zOffset -= 50.0F;
        }
    }

    /**
     * Render an item info overlay conaining info such as the number of items in a stack or
     * the damage for armour or tools.
     */
    public void renderGuiItemDecoration(TextRenderer textRenderer, ItemStack item, int x, int y) {
        this.renderGuiItemDecorations(textRenderer, item, x, y, null);
    }

    /**
     * Render an item info overlay conaining info such as the number of items in a stack or
     * the damage for armour or tools.
     */
    public void renderGuiItemDecorations(TextRenderer textRenderer, ItemStack item, int x, int y, String stackSizeText) {
        if (item != null) {
            if (item.size != 1 || stackSizeText != null) {
                String s = stackSizeText == null ? String.valueOf(item.size) : stackSizeText;
                if (stackSizeText == null && item.size < 1) {
                    s = Formatting.RED + String.valueOf(item.size);
                }

                GlStateManager.disableLighting();
                GlStateManager.disableDepthTest();
                GlStateManager.disableBlend();
                textRenderer.drawWithShadow(s, x + 19 - 2 - textRenderer.getWidth(s), y + 6 + 3, 16777215);
                GlStateManager.enableLighting();
                GlStateManager.enableDepthTest();
            }

            if (item.isDamaged()) {
                int j = (int)Math.round(13.0 - item.getDamage() * 13.0 / item.getMaxDamage());
                int i = (int)Math.round(255.0 - item.getDamage() * 255.0 / item.getMaxDamage());
                GlStateManager.disableLighting();
                GlStateManager.disableDepthTest();
                GlStateManager.disableTexture();
                GlStateManager.disableAlphaTest();
                GlStateManager.disableBlend();
                Tesselator tesselator = Tesselator.getInstance();
                BufferBuilder bufferbuilder = tesselator.getBuffer();
                this.fill(bufferbuilder, x + 2, y + 13, 13, 2, 0, 0, 0, 255);
                this.fill(bufferbuilder, x + 2, y + 13, 12, 1, (255 - i) / 4, 64, 0, 255);
                this.fill(bufferbuilder, x + 2, y + 13, j, 1, 255 - i, i, 0, 255);
                GlStateManager.enableBlend();
                GlStateManager.enableAlphaTest();
                GlStateManager.enableTexture();
                GlStateManager.enableLighting();
                GlStateManager.enableDepthTest();
            }
        }
    }

    private void fill(BufferBuilder bufferBuilder, int x1, int y1, int x2, int y2, int r, int g, int b, int a) {
        bufferBuilder.begin(7, DefaultVertexFormat.POSITION_COLOR);
        bufferBuilder.vertex(x1 + 0, y1 + 0, 0.0).color(r, g, b, a).nextVertex();
        bufferBuilder.vertex(x1 + 0, y1 + y2, 0.0).color(r, g, b, a).nextVertex();
        bufferBuilder.vertex(x1 + x2, y1 + y2, 0.0).color(r, g, b, a).nextVertex();
        bufferBuilder.vertex(x1 + x2, y1 + 0, 0.0).color(r, g, b, a).nextVertex();
        Tesselator.getInstance().end();
    }

    private void registerGuiModels() {
        this.registerModel(Blocks.ANVIL, "anvil_intact");
        this.registerModel(Blocks.ANVIL, 1, "anvil_slightly_damaged");
        this.registerModel(Blocks.ANVIL, 2, "anvil_very_damaged");
        this.registerModel(Blocks.CARPET, DyeColor.BLACK.getId(), "black_carpet");
        this.registerModel(Blocks.CARPET, DyeColor.BLUE.getId(), "blue_carpet");
        this.registerModel(Blocks.CARPET, DyeColor.BROWN.getId(), "brown_carpet");
        this.registerModel(Blocks.CARPET, DyeColor.CYAN.getId(), "cyan_carpet");
        this.registerModel(Blocks.CARPET, DyeColor.GRAY.getId(), "gray_carpet");
        this.registerModel(Blocks.CARPET, DyeColor.GREEN.getId(), "green_carpet");
        this.registerModel(Blocks.CARPET, DyeColor.LIGHT_BLUE.getId(), "light_blue_carpet");
        this.registerModel(Blocks.CARPET, DyeColor.LIME.getId(), "lime_carpet");
        this.registerModel(Blocks.CARPET, DyeColor.MAGENTA.getId(), "magenta_carpet");
        this.registerModel(Blocks.CARPET, DyeColor.ORANGE.getId(), "orange_carpet");
        this.registerModel(Blocks.CARPET, DyeColor.PINK.getId(), "pink_carpet");
        this.registerModel(Blocks.CARPET, DyeColor.PURPLE.getId(), "purple_carpet");
        this.registerModel(Blocks.CARPET, DyeColor.RED.getId(), "red_carpet");
        this.registerModel(Blocks.CARPET, DyeColor.SILVER.getId(), "silver_carpet");
        this.registerModel(Blocks.CARPET, DyeColor.WHITE.getId(), "white_carpet");
        this.registerModel(Blocks.CARPET, DyeColor.YELLOW.getId(), "yellow_carpet");
        this.registerModel(Blocks.COBBLESTONE_WALL, WallBlock.Variant.MOSSY.getId(), "mossy_cobblestone_wall");
        this.registerModel(Blocks.COBBLESTONE_WALL, WallBlock.Variant.NORMAL.getId(), "cobblestone_wall");
        this.registerModel(Blocks.DIRT, DirtBlock.Variant.COARSE_DIRT.getId(), "coarse_dirt");
        this.registerModel(Blocks.DIRT, DirtBlock.Variant.DIRT.getId(), "dirt");
        this.registerModel(Blocks.DIRT, DirtBlock.Variant.PODZOL.getId(), "podzol");
        this.registerModel(Blocks.DOUBLE_PLANT, DoublePlantBlock.Variant.FERN.getId(), "double_fern");
        this.registerModel(Blocks.DOUBLE_PLANT, DoublePlantBlock.Variant.GRASS.getId(), "double_grass");
        this.registerModel(Blocks.DOUBLE_PLANT, DoublePlantBlock.Variant.PAEONIA.getId(), "paeonia");
        this.registerModel(Blocks.DOUBLE_PLANT, DoublePlantBlock.Variant.ROSE.getId(), "double_rose");
        this.registerModel(Blocks.DOUBLE_PLANT, DoublePlantBlock.Variant.SUNFLOWER.getId(), "sunflower");
        this.registerModel(Blocks.DOUBLE_PLANT, DoublePlantBlock.Variant.SYRINGA.getId(), "syringa");
        this.registerModel(Blocks.LEAVES, PlanksBlock.Variant.BIRCH.getId(), "birch_leaves");
        this.registerModel(Blocks.LEAVES, PlanksBlock.Variant.JUNGLE.getId(), "jungle_leaves");
        this.registerModel(Blocks.LEAVES, PlanksBlock.Variant.OAK.getId(), "oak_leaves");
        this.registerModel(Blocks.LEAVES, PlanksBlock.Variant.SPRUCE.getId(), "spruce_leaves");
        this.registerModel(Blocks.LEAVES2, PlanksBlock.Variant.ACACIA.getId() - 4, "acacia_leaves");
        this.registerModel(Blocks.LEAVES2, PlanksBlock.Variant.DARK_OAK.getId() - 4, "dark_oak_leaves");
        this.registerModel(Blocks.LOG, PlanksBlock.Variant.BIRCH.getId(), "birch_log");
        this.registerModel(Blocks.LOG, PlanksBlock.Variant.JUNGLE.getId(), "jungle_log");
        this.registerModel(Blocks.LOG, PlanksBlock.Variant.OAK.getId(), "oak_log");
        this.registerModel(Blocks.LOG, PlanksBlock.Variant.SPRUCE.getId(), "spruce_log");
        this.registerModel(Blocks.LOG2, PlanksBlock.Variant.ACACIA.getId() - 4, "acacia_log");
        this.registerModel(Blocks.LOG2, PlanksBlock.Variant.DARK_OAK.getId() - 4, "dark_oak_log");
        this.registerModel(Blocks.MONSTER_EGG, InfestedBlock.Variant.CHISELED_STONEBRICK.getId(), "chiseled_brick_monster_egg");
        this.registerModel(Blocks.MONSTER_EGG, InfestedBlock.Variant.COBBLESTONE.getId(), "cobblestone_monster_egg");
        this.registerModel(Blocks.MONSTER_EGG, InfestedBlock.Variant.CRACKED_STONEBRICK.getId(), "cracked_brick_monster_egg");
        this.registerModel(Blocks.MONSTER_EGG, InfestedBlock.Variant.MOSSY_STONEBRICK.getId(), "mossy_brick_monster_egg");
        this.registerModel(Blocks.MONSTER_EGG, InfestedBlock.Variant.STONE.getId(), "stone_monster_egg");
        this.registerModel(Blocks.MONSTER_EGG, InfestedBlock.Variant.STONEBRICK.getId(), "stone_brick_monster_egg");
        this.registerModel(Blocks.PLANKS, PlanksBlock.Variant.ACACIA.getId(), "acacia_planks");
        this.registerModel(Blocks.PLANKS, PlanksBlock.Variant.BIRCH.getId(), "birch_planks");
        this.registerModel(Blocks.PLANKS, PlanksBlock.Variant.DARK_OAK.getId(), "dark_oak_planks");
        this.registerModel(Blocks.PLANKS, PlanksBlock.Variant.JUNGLE.getId(), "jungle_planks");
        this.registerModel(Blocks.PLANKS, PlanksBlock.Variant.OAK.getId(), "oak_planks");
        this.registerModel(Blocks.PLANKS, PlanksBlock.Variant.SPRUCE.getId(), "spruce_planks");
        this.registerModel(Blocks.PRISMARINE, PrismarineBlock.Variant.BRICKS.getId(), "prismarine_bricks");
        this.registerModel(Blocks.PRISMARINE, PrismarineBlock.Variant.DARK.getId(), "dark_prismarine");
        this.registerModel(Blocks.PRISMARINE, PrismarineBlock.Variant.ROUGH.getId(), "prismarine");
        this.registerModel(Blocks.QUARTZ_BLOCK, QuartzBlock.Variant.CHISELED.getId(), "chiseled_quartz_block");
        this.registerModel(Blocks.QUARTZ_BLOCK, QuartzBlock.Variant.DEFAULT.getId(), "quartz_block");
        this.registerModel(Blocks.QUARTZ_BLOCK, QuartzBlock.Variant.LINES_Y.getId(), "quartz_column");
        this.registerModel(Blocks.RED_FLOWER, FlowerBlock.Type.ALLIUM.getId(), "allium");
        this.registerModel(Blocks.RED_FLOWER, FlowerBlock.Type.BLUE_ORCHID.getId(), "blue_orchid");
        this.registerModel(Blocks.RED_FLOWER, FlowerBlock.Type.HOUSTONIA.getId(), "houstonia");
        this.registerModel(Blocks.RED_FLOWER, FlowerBlock.Type.ORANGE_TULIP.getId(), "orange_tulip");
        this.registerModel(Blocks.RED_FLOWER, FlowerBlock.Type.OXEY_DAISY.getId(), "oxeye_daisy");
        this.registerModel(Blocks.RED_FLOWER, FlowerBlock.Type.PINK_TULIP.getId(), "pink_tulip");
        this.registerModel(Blocks.RED_FLOWER, FlowerBlock.Type.POPPY.getId(), "poppy");
        this.registerModel(Blocks.RED_FLOWER, FlowerBlock.Type.RED_TULIP.getId(), "red_tulip");
        this.registerModel(Blocks.RED_FLOWER, FlowerBlock.Type.WHITE_TULIP.getId(), "white_tulip");
        this.registerModel(Blocks.SAND, SandBlock.Variant.RED_SAND.getId(), "red_sand");
        this.registerModel(Blocks.SAND, SandBlock.Variant.SAND.getId(), "sand");
        this.registerModel(Blocks.SANDSTONE, SandstoneBlock.Type.CHISELED.getId(), "chiseled_sandstone");
        this.registerModel(Blocks.SANDSTONE, SandstoneBlock.Type.DEFAULT.getId(), "sandstone");
        this.registerModel(Blocks.SANDSTONE, SandstoneBlock.Type.SMOOTH.getId(), "smooth_sandstone");
        this.registerModel(Blocks.RED_SANDSTONE, RedSandstoneBlock.Type.CHISELED.getId(), "chiseled_red_sandstone");
        this.registerModel(Blocks.RED_SANDSTONE, RedSandstoneBlock.Type.DEFAULT.getId(), "red_sandstone");
        this.registerModel(Blocks.RED_SANDSTONE, RedSandstoneBlock.Type.SMOOTH.getId(), "smooth_red_sandstone");
        this.registerModel(Blocks.SAPLING, PlanksBlock.Variant.ACACIA.getId(), "acacia_sapling");
        this.registerModel(Blocks.SAPLING, PlanksBlock.Variant.BIRCH.getId(), "birch_sapling");
        this.registerModel(Blocks.SAPLING, PlanksBlock.Variant.DARK_OAK.getId(), "dark_oak_sapling");
        this.registerModel(Blocks.SAPLING, PlanksBlock.Variant.JUNGLE.getId(), "jungle_sapling");
        this.registerModel(Blocks.SAPLING, PlanksBlock.Variant.OAK.getId(), "oak_sapling");
        this.registerModel(Blocks.SAPLING, PlanksBlock.Variant.SPRUCE.getId(), "spruce_sapling");
        this.registerModel(Blocks.SPONGE, 0, "sponge");
        this.registerModel(Blocks.SPONGE, 1, "sponge_wet");
        this.registerModel(Blocks.STAINED_GLASS, DyeColor.BLACK.getId(), "black_stained_glass");
        this.registerModel(Blocks.STAINED_GLASS, DyeColor.BLUE.getId(), "blue_stained_glass");
        this.registerModel(Blocks.STAINED_GLASS, DyeColor.BROWN.getId(), "brown_stained_glass");
        this.registerModel(Blocks.STAINED_GLASS, DyeColor.CYAN.getId(), "cyan_stained_glass");
        this.registerModel(Blocks.STAINED_GLASS, DyeColor.GRAY.getId(), "gray_stained_glass");
        this.registerModel(Blocks.STAINED_GLASS, DyeColor.GREEN.getId(), "green_stained_glass");
        this.registerModel(Blocks.STAINED_GLASS, DyeColor.LIGHT_BLUE.getId(), "light_blue_stained_glass");
        this.registerModel(Blocks.STAINED_GLASS, DyeColor.LIME.getId(), "lime_stained_glass");
        this.registerModel(Blocks.STAINED_GLASS, DyeColor.MAGENTA.getId(), "magenta_stained_glass");
        this.registerModel(Blocks.STAINED_GLASS, DyeColor.ORANGE.getId(), "orange_stained_glass");
        this.registerModel(Blocks.STAINED_GLASS, DyeColor.PINK.getId(), "pink_stained_glass");
        this.registerModel(Blocks.STAINED_GLASS, DyeColor.PURPLE.getId(), "purple_stained_glass");
        this.registerModel(Blocks.STAINED_GLASS, DyeColor.RED.getId(), "red_stained_glass");
        this.registerModel(Blocks.STAINED_GLASS, DyeColor.SILVER.getId(), "silver_stained_glass");
        this.registerModel(Blocks.STAINED_GLASS, DyeColor.WHITE.getId(), "white_stained_glass");
        this.registerModel(Blocks.STAINED_GLASS, DyeColor.YELLOW.getId(), "yellow_stained_glass");
        this.registerModel(Blocks.STAINED_GLASS_PANE, DyeColor.BLACK.getId(), "black_stained_glass_pane");
        this.registerModel(Blocks.STAINED_GLASS_PANE, DyeColor.BLUE.getId(), "blue_stained_glass_pane");
        this.registerModel(Blocks.STAINED_GLASS_PANE, DyeColor.BROWN.getId(), "brown_stained_glass_pane");
        this.registerModel(Blocks.STAINED_GLASS_PANE, DyeColor.CYAN.getId(), "cyan_stained_glass_pane");
        this.registerModel(Blocks.STAINED_GLASS_PANE, DyeColor.GRAY.getId(), "gray_stained_glass_pane");
        this.registerModel(Blocks.STAINED_GLASS_PANE, DyeColor.GREEN.getId(), "green_stained_glass_pane");
        this.registerModel(Blocks.STAINED_GLASS_PANE, DyeColor.LIGHT_BLUE.getId(), "light_blue_stained_glass_pane");
        this.registerModel(Blocks.STAINED_GLASS_PANE, DyeColor.LIME.getId(), "lime_stained_glass_pane");
        this.registerModel(Blocks.STAINED_GLASS_PANE, DyeColor.MAGENTA.getId(), "magenta_stained_glass_pane");
        this.registerModel(Blocks.STAINED_GLASS_PANE, DyeColor.ORANGE.getId(), "orange_stained_glass_pane");
        this.registerModel(Blocks.STAINED_GLASS_PANE, DyeColor.PINK.getId(), "pink_stained_glass_pane");
        this.registerModel(Blocks.STAINED_GLASS_PANE, DyeColor.PURPLE.getId(), "purple_stained_glass_pane");
        this.registerModel(Blocks.STAINED_GLASS_PANE, DyeColor.RED.getId(), "red_stained_glass_pane");
        this.registerModel(Blocks.STAINED_GLASS_PANE, DyeColor.SILVER.getId(), "silver_stained_glass_pane");
        this.registerModel(Blocks.STAINED_GLASS_PANE, DyeColor.WHITE.getId(), "white_stained_glass_pane");
        this.registerModel(Blocks.STAINED_GLASS_PANE, DyeColor.YELLOW.getId(), "yellow_stained_glass_pane");
        this.registerModel(Blocks.STAINED_HARDENED_CLAY, DyeColor.BLACK.getId(), "black_stained_hardened_clay");
        this.registerModel(Blocks.STAINED_HARDENED_CLAY, DyeColor.BLUE.getId(), "blue_stained_hardened_clay");
        this.registerModel(Blocks.STAINED_HARDENED_CLAY, DyeColor.BROWN.getId(), "brown_stained_hardened_clay");
        this.registerModel(Blocks.STAINED_HARDENED_CLAY, DyeColor.CYAN.getId(), "cyan_stained_hardened_clay");
        this.registerModel(Blocks.STAINED_HARDENED_CLAY, DyeColor.GRAY.getId(), "gray_stained_hardened_clay");
        this.registerModel(Blocks.STAINED_HARDENED_CLAY, DyeColor.GREEN.getId(), "green_stained_hardened_clay");
        this.registerModel(Blocks.STAINED_HARDENED_CLAY, DyeColor.LIGHT_BLUE.getId(), "light_blue_stained_hardened_clay");
        this.registerModel(Blocks.STAINED_HARDENED_CLAY, DyeColor.LIME.getId(), "lime_stained_hardened_clay");
        this.registerModel(Blocks.STAINED_HARDENED_CLAY, DyeColor.MAGENTA.getId(), "magenta_stained_hardened_clay");
        this.registerModel(Blocks.STAINED_HARDENED_CLAY, DyeColor.ORANGE.getId(), "orange_stained_hardened_clay");
        this.registerModel(Blocks.STAINED_HARDENED_CLAY, DyeColor.PINK.getId(), "pink_stained_hardened_clay");
        this.registerModel(Blocks.STAINED_HARDENED_CLAY, DyeColor.PURPLE.getId(), "purple_stained_hardened_clay");
        this.registerModel(Blocks.STAINED_HARDENED_CLAY, DyeColor.RED.getId(), "red_stained_hardened_clay");
        this.registerModel(Blocks.STAINED_HARDENED_CLAY, DyeColor.SILVER.getId(), "silver_stained_hardened_clay");
        this.registerModel(Blocks.STAINED_HARDENED_CLAY, DyeColor.WHITE.getId(), "white_stained_hardened_clay");
        this.registerModel(Blocks.STAINED_HARDENED_CLAY, DyeColor.YELLOW.getId(), "yellow_stained_hardened_clay");
        this.registerModel(Blocks.STONE, StoneBlock.Variant.ANDESITE.getId(), "andesite");
        this.registerModel(Blocks.STONE, StoneBlock.Variant.ANDESITE_SMOOTH.getId(), "andesite_smooth");
        this.registerModel(Blocks.STONE, StoneBlock.Variant.DIORITE.getId(), "diorite");
        this.registerModel(Blocks.STONE, StoneBlock.Variant.DIORITE_SMOOTH.getId(), "diorite_smooth");
        this.registerModel(Blocks.STONE, StoneBlock.Variant.GRANITE.getId(), "granite");
        this.registerModel(Blocks.STONE, StoneBlock.Variant.GRANITE_SMOOTH.getId(), "granite_smooth");
        this.registerModel(Blocks.STONE, StoneBlock.Variant.STONE.getId(), "stone");
        this.registerModel(Blocks.STONE_BRICKS, StonebrickBlock.Variant.CRACKED.getId(), "cracked_stonebrick");
        this.registerModel(Blocks.STONE_BRICKS, StonebrickBlock.Variant.DEFAULT.getId(), "stonebrick");
        this.registerModel(Blocks.STONE_BRICKS, StonebrickBlock.Variant.CHISELED.getId(), "chiseled_stonebrick");
        this.registerModel(Blocks.STONE_BRICKS, StonebrickBlock.Variant.MOSSY.getId(), "mossy_stonebrick");
        this.registerModel(Blocks.STONE_SLAB, StoneSlabBlock.Variant.BRICK.getId(), "brick_slab");
        this.registerModel(Blocks.STONE_SLAB, StoneSlabBlock.Variant.COBBLESTONE.getId(), "cobblestone_slab");
        this.registerModel(Blocks.STONE_SLAB, StoneSlabBlock.Variant.WOOD.getId(), "old_wood_slab");
        this.registerModel(Blocks.STONE_SLAB, StoneSlabBlock.Variant.NETHERBRICK.getId(), "nether_brick_slab");
        this.registerModel(Blocks.STONE_SLAB, StoneSlabBlock.Variant.QUARTZ.getId(), "quartz_slab");
        this.registerModel(Blocks.STONE_SLAB, StoneSlabBlock.Variant.SAND.getId(), "sandstone_slab");
        this.registerModel(Blocks.STONE_SLAB, StoneSlabBlock.Variant.SMOOTHBRICK.getId(), "stone_brick_slab");
        this.registerModel(Blocks.STONE_SLAB, StoneSlabBlock.Variant.STONE.getId(), "stone_slab");
        this.registerModel(Blocks.RED_SANDSTONE_SLAB, RedSandstoneSlab.Variant.RED_SANDSTONE.getId(), "red_sandstone_slab");
        this.registerModel(Blocks.TALLGRASS, TallPlantBlock.Type.DEAD_BUSH.getId(), "dead_bush");
        this.registerModel(Blocks.TALLGRASS, TallPlantBlock.Type.FERN.getId(), "fern");
        this.registerModel(Blocks.TALLGRASS, TallPlantBlock.Type.GRASS.getId(), "tall_grass");
        this.registerModel(Blocks.WOODEN_SLAB, PlanksBlock.Variant.ACACIA.getId(), "acacia_slab");
        this.registerModel(Blocks.WOODEN_SLAB, PlanksBlock.Variant.BIRCH.getId(), "birch_slab");
        this.registerModel(Blocks.WOODEN_SLAB, PlanksBlock.Variant.DARK_OAK.getId(), "dark_oak_slab");
        this.registerModel(Blocks.WOODEN_SLAB, PlanksBlock.Variant.JUNGLE.getId(), "jungle_slab");
        this.registerModel(Blocks.WOODEN_SLAB, PlanksBlock.Variant.OAK.getId(), "oak_slab");
        this.registerModel(Blocks.WOODEN_SLAB, PlanksBlock.Variant.SPRUCE.getId(), "spruce_slab");
        this.registerModel(Blocks.WOOL, DyeColor.BLACK.getId(), "black_wool");
        this.registerModel(Blocks.WOOL, DyeColor.BLUE.getId(), "blue_wool");
        this.registerModel(Blocks.WOOL, DyeColor.BROWN.getId(), "brown_wool");
        this.registerModel(Blocks.WOOL, DyeColor.CYAN.getId(), "cyan_wool");
        this.registerModel(Blocks.WOOL, DyeColor.GRAY.getId(), "gray_wool");
        this.registerModel(Blocks.WOOL, DyeColor.GREEN.getId(), "green_wool");
        this.registerModel(Blocks.WOOL, DyeColor.LIGHT_BLUE.getId(), "light_blue_wool");
        this.registerModel(Blocks.WOOL, DyeColor.LIME.getId(), "lime_wool");
        this.registerModel(Blocks.WOOL, DyeColor.MAGENTA.getId(), "magenta_wool");
        this.registerModel(Blocks.WOOL, DyeColor.ORANGE.getId(), "orange_wool");
        this.registerModel(Blocks.WOOL, DyeColor.PINK.getId(), "pink_wool");
        this.registerModel(Blocks.WOOL, DyeColor.PURPLE.getId(), "purple_wool");
        this.registerModel(Blocks.WOOL, DyeColor.RED.getId(), "red_wool");
        this.registerModel(Blocks.WOOL, DyeColor.SILVER.getId(), "silver_wool");
        this.registerModel(Blocks.WOOL, DyeColor.WHITE.getId(), "white_wool");
        this.registerModel(Blocks.WOOL, DyeColor.YELLOW.getId(), "yellow_wool");
        this.registerModel(Blocks.ACACIA_STAIRS, "acacia_stairs");
        this.registerModel(Blocks.ACTIVATOR_RAIL, "activator_rail");
        this.registerModel(Blocks.BEACON, "beacon");
        this.registerModel(Blocks.BEDROCK, "bedrock");
        this.registerModel(Blocks.BIRCH_STAIRS, "birch_stairs");
        this.registerModel(Blocks.BOOKSHELF, "bookshelf");
        this.registerModel(Blocks.BRICKS, "brick_block");
        this.registerModel(Blocks.BRICKS, "brick_block");
        this.registerModel(Blocks.BRICK_STAIRS, "brick_stairs");
        this.registerModel(Blocks.BROWN_MUSHROOM, "brown_mushroom");
        this.registerModel(Blocks.CACTUS, "cactus");
        this.registerModel(Blocks.CLAY, "clay");
        this.registerModel(Blocks.COAL_BLOCK, "coal_block");
        this.registerModel(Blocks.COAL_ORE, "coal_ore");
        this.registerModel(Blocks.COBBLESTONE, "cobblestone");
        this.registerModel(Blocks.CRAFTING_TABLE, "crafting_table");
        this.registerModel(Blocks.DARK_OAK_STAIRS, "dark_oak_stairs");
        this.registerModel(Blocks.DAYLIGHT_DETECTOR, "daylight_detector");
        this.registerModel(Blocks.DEADBUSH, "dead_bush");
        this.registerModel(Blocks.DETECTOR_RAIL, "detector_rail");
        this.registerModel(Blocks.DIAMOND_BLOCK, "diamond_block");
        this.registerModel(Blocks.DIAMOND_ORE, "diamond_ore");
        this.registerModel(Blocks.DISPENSER, "dispenser");
        this.registerModel(Blocks.DROPPER, "dropper");
        this.registerModel(Blocks.EMERALD_BLOCK, "emerald_block");
        this.registerModel(Blocks.EMERALD_ORE, "emerald_ore");
        this.registerModel(Blocks.ENCHANTING_TABLE, "enchanting_table");
        this.registerModel(Blocks.END_PORTAL_FRAME, "end_portal_frame");
        this.registerModel(Blocks.END_STONE, "end_stone");
        this.registerModel(Blocks.FENCE, "oak_fence");
        this.registerModel(Blocks.SPRUCE_FENCE, "spruce_fence");
        this.registerModel(Blocks.BIRCH_FENCE, "birch_fence");
        this.registerModel(Blocks.JUNGLE_FENCE, "jungle_fence");
        this.registerModel(Blocks.DARK_OAK_FENCE, "dark_oak_fence");
        this.registerModel(Blocks.ACACIA_FENCE, "acacia_fence");
        this.registerModel(Blocks.FENCE_GATE, "oak_fence_gate");
        this.registerModel(Blocks.SPRUCE_FENCE_GATE, "spruce_fence_gate");
        this.registerModel(Blocks.BIRCH_FENCE_GATE, "birch_fence_gate");
        this.registerModel(Blocks.JUNGLE_FENCE_GATE, "jungle_fence_gate");
        this.registerModel(Blocks.DARK_OAK_FENCE_GATE, "dark_oak_fence_gate");
        this.registerModel(Blocks.ACACIA_FENCE_GATE, "acacia_fence_gate");
        this.registerModel(Blocks.FURNACE, "furnace");
        this.registerModel(Blocks.GLASS, "glass");
        this.registerModel(Blocks.GLASS_PANE, "glass_pane");
        this.registerModel(Blocks.GLOWSTONE, "glowstone");
        this.registerModel(Blocks.POWERED_RAIL, "golden_rail");
        this.registerModel(Blocks.GOLD_BLOCK, "gold_block");
        this.registerModel(Blocks.GOLD_ORE, "gold_ore");
        this.registerModel(Blocks.GRASS, "grass");
        this.registerModel(Blocks.GRAVEL, "gravel");
        this.registerModel(Blocks.HARDENED_CLAY, "hardened_clay");
        this.registerModel(Blocks.HAY, "hay_block");
        this.registerModel(Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE, "heavy_weighted_pressure_plate");
        this.registerModel(Blocks.HOPPER, "hopper");
        this.registerModel(Blocks.ICE, "ice");
        this.registerModel(Blocks.IRON_BARS, "iron_bars");
        this.registerModel(Blocks.IRON_BLOCK, "iron_block");
        this.registerModel(Blocks.IRON_ORE, "iron_ore");
        this.registerModel(Blocks.IRON_TRAPDOOR, "iron_trapdoor");
        this.registerModel(Blocks.JUKEBOX, "jukebox");
        this.registerModel(Blocks.JUNGLE_STAIRS, "jungle_stairs");
        this.registerModel(Blocks.LADDER, "ladder");
        this.registerModel(Blocks.LAPIS_BLOCK, "lapis_block");
        this.registerModel(Blocks.LAPIS_ORE, "lapis_ore");
        this.registerModel(Blocks.LEVER, "lever");
        this.registerModel(Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE, "light_weighted_pressure_plate");
        this.registerModel(Blocks.LIT_PUMPKIN, "lit_pumpkin");
        this.registerModel(Blocks.MELON_BLOCK, "melon_block");
        this.registerModel(Blocks.MOSSY_COBBLESTONE, "mossy_cobblestone");
        this.registerModel(Blocks.MYCELIUM, "mycelium");
        this.registerModel(Blocks.NETHERRACK, "netherrack");
        this.registerModel(Blocks.NETHER_BRICKS, "nether_brick");
        this.registerModel(Blocks.NETHER_BRICK_FENCE, "nether_brick_fence");
        this.registerModel(Blocks.NETHER_BRICK_STAIRS, "nether_brick_stairs");
        this.registerModel(Blocks.NOTEBLOCK, "noteblock");
        this.registerModel(Blocks.OAK_STAIRS, "oak_stairs");
        this.registerModel(Blocks.OBSIDIAN, "obsidian");
        this.registerModel(Blocks.PACKED_ICE, "packed_ice");
        this.registerModel(Blocks.PISTON, "piston");
        this.registerModel(Blocks.PUMPKIN, "pumpkin");
        this.registerModel(Blocks.QUARTZ_ORE, "quartz_ore");
        this.registerModel(Blocks.QUARTZ_STAIRS, "quartz_stairs");
        this.registerModel(Blocks.RAIL, "rail");
        this.registerModel(Blocks.REDSTONE_BLOCK, "redstone_block");
        this.registerModel(Blocks.REDSTONE_LAMP, "redstone_lamp");
        this.registerModel(Blocks.REDSTONE_ORE, "redstone_ore");
        this.registerModel(Blocks.REDSTONE_TORCH, "redstone_torch");
        this.registerModel(Blocks.RED_MUSHROOM, "red_mushroom");
        this.registerModel(Blocks.SANDSTONE_STAIRS, "sandstone_stairs");
        this.registerModel(Blocks.RED_SANDSTONE_STAIRS, "red_sandstone_stairs");
        this.registerModel(Blocks.SEA_LANTERN, "sea_lantern");
        this.registerModel(Blocks.SLIME, "slime");
        this.registerModel(Blocks.SNOW, "snow");
        this.registerModel(Blocks.SNOW_LAYER, "snow_layer");
        this.registerModel(Blocks.SOUL_SAND, "soul_sand");
        this.registerModel(Blocks.SPRUCE_STAIRS, "spruce_stairs");
        this.registerModel(Blocks.STICKY_PISTON, "sticky_piston");
        this.registerModel(Blocks.STONE_BRICK_STAIRS, "stone_brick_stairs");
        this.registerModel(Blocks.STONE_BUTTON, "stone_button");
        this.registerModel(Blocks.STONE_PRESSURE_PLATE, "stone_pressure_plate");
        this.registerModel(Blocks.STONE_STAIRS, "stone_stairs");
        this.registerModel(Blocks.TNT, "tnt");
        this.registerModel(Blocks.TORCH, "torch");
        this.registerModel(Blocks.TRAPDOOR, "trapdoor");
        this.registerModel(Blocks.TRIPWIRE_HOOK, "tripwire_hook");
        this.registerModel(Blocks.VINE, "vine");
        this.registerModel(Blocks.LILY_PAD, "waterlily");
        this.registerModel(Blocks.WEB, "web");
        this.registerModel(Blocks.WOODEN_BUTTON, "wooden_button");
        this.registerModel(Blocks.WOODEN_PRESSURE_PLATE, "wooden_pressure_plate");
        this.registerModel(Blocks.YELLOW_FLOWER, FlowerBlock.Type.DANDELION.getId(), "dandelion");
        this.registerModel(Blocks.CHEST, "chest");
        this.registerModel(Blocks.TRAPPED_CHEST, "trapped_chest");
        this.registerModel(Blocks.ENDER_CHEST, "ender_chest");
        this.registerModel(Items.IRON_SHOVEL, "iron_shovel");
        this.registerModel(Items.IRON_PICKAXE, "iron_pickaxe");
        this.registerModel(Items.IRON_AXE, "iron_axe");
        this.registerModel(Items.FLINT_AND_STEEL, "flint_and_steel");
        this.registerModel(Items.APPLE, "apple");
        this.registerModel(Items.BOW, 0, "bow");
        this.registerModel(Items.BOW, 1, "bow_pulling_0");
        this.registerModel(Items.BOW, 2, "bow_pulling_1");
        this.registerModel(Items.BOW, 3, "bow_pulling_2");
        this.registerModel(Items.ARROW, "arrow");
        this.registerModel(Items.COAL, 0, "coal");
        this.registerModel(Items.COAL, 1, "charcoal");
        this.registerModel(Items.DIAMOND, "diamond");
        this.registerModel(Items.IRON_INGOT, "iron_ingot");
        this.registerModel(Items.GOLD_INGOT, "gold_ingot");
        this.registerModel(Items.IRON_SWORD, "iron_sword");
        this.registerModel(Items.WOODEN_SWORD, "wooden_sword");
        this.registerModel(Items.WOODEN_SHOVEL, "wooden_shovel");
        this.registerModel(Items.WOODEN_PICKAXE, "wooden_pickaxe");
        this.registerModel(Items.WOODEN_AXE, "wooden_axe");
        this.registerModel(Items.STONE_SWORD, "stone_sword");
        this.registerModel(Items.STONE_SHOVEL, "stone_shovel");
        this.registerModel(Items.STONE_PICKAXE, "stone_pickaxe");
        this.registerModel(Items.STONE_AXE, "stone_axe");
        this.registerModel(Items.DIAMOND_SWORD, "diamond_sword");
        this.registerModel(Items.DIAMOND_SHOVEL, "diamond_shovel");
        this.registerModel(Items.DIAMOND_PICKAXE, "diamond_pickaxe");
        this.registerModel(Items.DIAMOND_AXE, "diamond_axe");
        this.registerModel(Items.STICK, "stick");
        this.registerModel(Items.BOWL, "bowl");
        this.registerModel(Items.MUSHROOM_STEW, "mushroom_stew");
        this.registerModel(Items.GOLDEN_SWORD, "golden_sword");
        this.registerModel(Items.GOLDEN_SHOVEL, "golden_shovel");
        this.registerModel(Items.GOLDEN_PICKAXE, "golden_pickaxe");
        this.registerModel(Items.GOLDEN_AXE, "golden_axe");
        this.registerModel(Items.STRING, "string");
        this.registerModel(Items.FEATHER, "feather");
        this.registerModel(Items.GUNPOWDER, "gunpowder");
        this.registerModel(Items.WOODEN_HOE, "wooden_hoe");
        this.registerModel(Items.STONE_HOE, "stone_hoe");
        this.registerModel(Items.IRON_HOE, "iron_hoe");
        this.registerModel(Items.DIAMOND_HOE, "diamond_hoe");
        this.registerModel(Items.GOLDEN_HOE, "golden_hoe");
        this.registerModel(Items.WHEAT_SEEDS, "wheat_seeds");
        this.registerModel(Items.WHEAT, "wheat");
        this.registerModel(Items.BREAD, "bread");
        this.registerModel(Items.LEATHER_HELMET, "leather_helmet");
        this.registerModel(Items.LEATHER_CHESTPLATE, "leather_chestplate");
        this.registerModel(Items.LEATHER_LEGGINGS, "leather_leggings");
        this.registerModel(Items.LEATHER_BOOTS, "leather_boots");
        this.registerModel(Items.CHAINMAIL_HELMET, "chainmail_helmet");
        this.registerModel(Items.CHAINMAIL_CHESTPLATE, "chainmail_chestplate");
        this.registerModel(Items.CHAINMAIL_LEGGINGS, "chainmail_leggings");
        this.registerModel(Items.CHAINMAIL_BOOTS, "chainmail_boots");
        this.registerModel(Items.IRON_HELMET, "iron_helmet");
        this.registerModel(Items.IRON_CHESTPLATE, "iron_chestplate");
        this.registerModel(Items.IRON_LEGGINGS, "iron_leggings");
        this.registerModel(Items.IRON_BOOTS, "iron_boots");
        this.registerModel(Items.DIAMOND_HELMET, "diamond_helmet");
        this.registerModel(Items.DIAMOND_CHESTPLATE, "diamond_chestplate");
        this.registerModel(Items.DIAMOND_LEGGINGS, "diamond_leggings");
        this.registerModel(Items.DIAMOND_BOOTS, "diamond_boots");
        this.registerModel(Items.GOLDEN_HELMET, "golden_helmet");
        this.registerModel(Items.GOLDEN_CHESTPLATE, "golden_chestplate");
        this.registerModel(Items.GOLDEN_LEGGINGS, "golden_leggings");
        this.registerModel(Items.GOLDEN_BOOTS, "golden_boots");
        this.registerModel(Items.FLINT, "flint");
        this.registerModel(Items.PORKCHOP, "porkchop");
        this.registerModel(Items.COOKED_PORKCHOP, "cooked_porkchop");
        this.registerModel(Items.PAINTING, "painting");
        this.registerModel(Items.GOLDEN_APPLE, "golden_apple");
        this.registerModel(Items.GOLDEN_APPLE, 1, "golden_apple");
        this.registerModel(Items.SIGN, "sign");
        this.registerModel(Items.WOODEN_DOOR, "oak_door");
        this.registerModel(Items.SPRUCE_DOOR, "spruce_door");
        this.registerModel(Items.BIRCH_DOOR, "birch_door");
        this.registerModel(Items.JUNGLE_DOOR, "jungle_door");
        this.registerModel(Items.ACACIA_DOOR, "acacia_door");
        this.registerModel(Items.DARK_OAK_DOOR, "dark_oak_door");
        this.registerModel(Items.BUCKET, "bucket");
        this.registerModel(Items.WATER_BUCKET, "water_bucket");
        this.registerModel(Items.LAVA_BUCKET, "lava_bucket");
        this.registerModel(Items.MINECART, "minecart");
        this.registerModel(Items.SADDLE, "saddle");
        this.registerModel(Items.IRON_DOOR, "iron_door");
        this.registerModel(Items.REDSTONE, "redstone");
        this.registerModel(Items.SNOWBALL, "snowball");
        this.registerModel(Items.BOAT, "boat");
        this.registerModel(Items.LEATHER, "leather");
        this.registerModel(Items.MILK_BUCKET, "milk_bucket");
        this.registerModel(Items.BRICK, "brick");
        this.registerModel(Items.CLAY_BALL, "clay_ball");
        this.registerModel(Items.REEDS, "reeds");
        this.registerModel(Items.PAPER, "paper");
        this.registerModel(Items.BOOK, "book");
        this.registerModel(Items.SLIME_BALL, "slime_ball");
        this.registerModel(Items.CHEST_MINECART, "chest_minecart");
        this.registerModel(Items.FURNACE_MINECART, "furnace_minecart");
        this.registerModel(Items.EGG, "egg");
        this.registerModel(Items.COMPASS, "compass");
        this.registerModel(Items.FISHING_ROD, "fishing_rod");
        this.registerModel(Items.FISHING_ROD, 1, "fishing_rod_cast");
        this.registerModel(Items.CLOCK, "clock");
        this.registerModel(Items.GLOWSTONE_DUST, "glowstone_dust");
        this.registerModel(Items.FISH, FishItem.Type.COD.getId(), "cod");
        this.registerModel(Items.FISH, FishItem.Type.SALMON.getId(), "salmon");
        this.registerModel(Items.FISH, FishItem.Type.CLOWNFISH.getId(), "clownfish");
        this.registerModel(Items.FISH, FishItem.Type.PUFFERFISH.getId(), "pufferfish");
        this.registerModel(Items.COOKED_FISH, FishItem.Type.COD.getId(), "cooked_cod");
        this.registerModel(Items.COOKED_FISH, FishItem.Type.SALMON.getId(), "cooked_salmon");
        this.registerModel(Items.DYE, DyeColor.BLACK.getMetadata(), "dye_black");
        this.registerModel(Items.DYE, DyeColor.RED.getMetadata(), "dye_red");
        this.registerModel(Items.DYE, DyeColor.GREEN.getMetadata(), "dye_green");
        this.registerModel(Items.DYE, DyeColor.BROWN.getMetadata(), "dye_brown");
        this.registerModel(Items.DYE, DyeColor.BLUE.getMetadata(), "dye_blue");
        this.registerModel(Items.DYE, DyeColor.PURPLE.getMetadata(), "dye_purple");
        this.registerModel(Items.DYE, DyeColor.CYAN.getMetadata(), "dye_cyan");
        this.registerModel(Items.DYE, DyeColor.SILVER.getMetadata(), "dye_silver");
        this.registerModel(Items.DYE, DyeColor.GRAY.getMetadata(), "dye_gray");
        this.registerModel(Items.DYE, DyeColor.PINK.getMetadata(), "dye_pink");
        this.registerModel(Items.DYE, DyeColor.LIME.getMetadata(), "dye_lime");
        this.registerModel(Items.DYE, DyeColor.YELLOW.getMetadata(), "dye_yellow");
        this.registerModel(Items.DYE, DyeColor.LIGHT_BLUE.getMetadata(), "dye_light_blue");
        this.registerModel(Items.DYE, DyeColor.MAGENTA.getMetadata(), "dye_magenta");
        this.registerModel(Items.DYE, DyeColor.ORANGE.getMetadata(), "dye_orange");
        this.registerModel(Items.DYE, DyeColor.WHITE.getMetadata(), "dye_white");
        this.registerModel(Items.BONE, "bone");
        this.registerModel(Items.SUGAR, "sugar");
        this.registerModel(Items.CAKE, "cake");
        this.registerModel(Items.BED, "bed");
        this.registerModel(Items.REPEATER, "repeater");
        this.registerModel(Items.COOKIE, "cookie");
        this.registerModel(Items.SHEARS, "shears");
        this.registerModel(Items.MELON, "melon");
        this.registerModel(Items.PUMPKIN_SEEDS, "pumpkin_seeds");
        this.registerModel(Items.MELON_SEEDS, "melon_seeds");
        this.registerModel(Items.BEEF, "beef");
        this.registerModel(Items.COOKED_BEEF, "cooked_beef");
        this.registerModel(Items.CHICKEN, "chicken");
        this.registerModel(Items.COOKED_CHICKEN, "cooked_chicken");
        this.registerModel(Items.RABBIT, "rabbit");
        this.registerModel(Items.COOKED_RABBIT, "cooked_rabbit");
        this.registerModel(Items.MUTTON, "mutton");
        this.registerModel(Items.COOKED_MUTTON, "cooked_mutton");
        this.registerModel(Items.RABBIT_FOOT, "rabbit_foot");
        this.registerModel(Items.RABBIT_HIDE, "rabbit_hide");
        this.registerModel(Items.RABBIT_STEW, "rabbit_stew");
        this.registerModel(Items.ROTTEN_FLESH, "rotten_flesh");
        this.registerModel(Items.ENDER_PEARL, "ender_pearl");
        this.registerModel(Items.BLAZE_ROD, "blaze_rod");
        this.registerModel(Items.GHAST_TEAR, "ghast_tear");
        this.registerModel(Items.GOLD_NUGGET, "gold_nugget");
        this.registerModel(Items.NETHER_WART, "nether_wart");
        this.modelShaper
            .register(
                Items.POTION,
                new ItemModelProvider() {
                    @Override
                    public ModelIdentifier provide(ItemStack item) {
                        return PotionItem.isSplashPotion(item.getMetadata())
                            ? new ModelIdentifier("bottle_splash", "inventory")
                            : new ModelIdentifier("bottle_drinkable", "inventory");
                    }
                }
            );
        this.registerModel(Items.GLASS_BOTTLE, "glass_bottle");
        this.registerModel(Items.SPIDER_EYE, "spider_eye");
        this.registerModel(Items.FERMENTED_SPIDER_EYE, "fermented_spider_eye");
        this.registerModel(Items.BLAZE_POWDER, "blaze_powder");
        this.registerModel(Items.MAGMA_CREAM, "magma_cream");
        this.registerModel(Items.BREWING_STAND, "brewing_stand");
        this.registerModel(Items.CAULDRON, "cauldron");
        this.registerModel(Items.ENDER_EYE, "ender_eye");
        this.registerModel(Items.SPECKLED_MELON, "speckled_melon");
        this.modelShaper.register(Items.SPAWN_EGG, new ItemModelProvider() {
            @Override
            public ModelIdentifier provide(ItemStack item) {
                return new ModelIdentifier("spawn_egg", "inventory");
            }
        });
        this.registerModel(Items.EXPERIENCE_BOTTLE, "experience_bottle");
        this.registerModel(Items.FIRE_CHARGE, "fire_charge");
        this.registerModel(Items.WRITABLE_BOOK, "writable_book");
        this.registerModel(Items.EMERALD, "emerald");
        this.registerModel(Items.ITEM_FRAME, "item_frame");
        this.registerModel(Items.FLOWER_POT, "flower_pot");
        this.registerModel(Items.CARROT, "carrot");
        this.registerModel(Items.POTATO, "potato");
        this.registerModel(Items.BAKED_POTATO, "baked_potato");
        this.registerModel(Items.POISONOUS_POTATO, "poisonous_potato");
        this.registerModel(Items.EMPTY_MAP, "map");
        this.registerModel(Items.GOLDEN_CARROT, "golden_carrot");
        this.registerModel(Items.SKULL, 0, "skull_skeleton");
        this.registerModel(Items.SKULL, 1, "skull_wither");
        this.registerModel(Items.SKULL, 2, "skull_zombie");
        this.registerModel(Items.SKULL, 3, "skull_char");
        this.registerModel(Items.SKULL, 4, "skull_creeper");
        this.registerModel(Items.CARROT_ON_A_STICK, "carrot_on_a_stick");
        this.registerModel(Items.NETHER_STAR, "nether_star");
        this.registerModel(Items.PUMPKIN_PIE, "pumpkin_pie");
        this.registerModel(Items.FIREWORKS_CHARGE, "firework_charge");
        this.registerModel(Items.COMPARATOR, "comparator");
        this.registerModel(Items.NETHERBRICK, "netherbrick");
        this.registerModel(Items.QUARTZ, "quartz");
        this.registerModel(Items.TNT_MINECART, "tnt_minecart");
        this.registerModel(Items.HOPPER_MINECART, "hopper_minecart");
        this.registerModel(Items.ARMOR_STAND, "armor_stand");
        this.registerModel(Items.IRON_HORSE_ARMOR, "iron_horse_armor");
        this.registerModel(Items.GOLDEN_HORSE_ARMOR, "golden_horse_armor");
        this.registerModel(Items.DIAMOND_HORSE_ARMOR, "diamond_horse_armor");
        this.registerModel(Items.LEAD, "lead");
        this.registerModel(Items.NAME_TAG, "name_tag");
        this.modelShaper.register(Items.BANNER, new ItemModelProvider() {
            @Override
            public ModelIdentifier provide(ItemStack item) {
                return new ModelIdentifier("banner", "inventory");
            }
        });
        this.registerModel(Items.RECORD_13, "record_13");
        this.registerModel(Items.RECORD_CAT, "record_cat");
        this.registerModel(Items.RECORD_BLOCKS, "record_blocks");
        this.registerModel(Items.RECORD_CHIRP, "record_chirp");
        this.registerModel(Items.RECORD_FAR, "record_far");
        this.registerModel(Items.RECORD_MALL, "record_mall");
        this.registerModel(Items.RECORD_MELLOHI, "record_mellohi");
        this.registerModel(Items.RECORD_STAL, "record_stal");
        this.registerModel(Items.RECORD_STRAD, "record_strad");
        this.registerModel(Items.RECORD_WARD, "record_ward");
        this.registerModel(Items.RECORD_11, "record_11");
        this.registerModel(Items.RECORD_WAIT, "record_wait");
        this.registerModel(Items.PRISMARINE_SHARD, "prismarine_shard");
        this.registerModel(Items.PRISMARINE_CRYSTALS, "prismarine_crystals");
        this.modelShaper.register(Items.ENCHANTED_BOOK, new ItemModelProvider() {
            @Override
            public ModelIdentifier provide(ItemStack item) {
                return new ModelIdentifier("enchanted_book", "inventory");
            }
        });
        this.modelShaper.register(Items.FILLED_MAP, new ItemModelProvider() {
            @Override
            public ModelIdentifier provide(ItemStack item) {
                return new ModelIdentifier("filled_map", "inventory");
            }
        });
        this.registerModel(Blocks.COMMAND_BLOCK, "command_block");
        this.registerModel(Items.FIREWORKS, "fireworks");
        this.registerModel(Items.COMMAND_BLOCK_MINECART, "command_block_minecart");
        this.registerModel(Blocks.BARRIER, "barrier");
        this.registerModel(Blocks.MOB_SPAWNER, "mob_spawner");
        this.registerModel(Items.WRITTEN_BOOK, "written_book");
        this.registerModel(Blocks.BROWN_MUSHROOM_BLOCK, MushroomBlock.Variant.ALL_INSIDE.getId(), "brown_mushroom_block");
        this.registerModel(Blocks.RED_MUSHROOM_BLOCK, MushroomBlock.Variant.ALL_INSIDE.getId(), "red_mushroom_block");
        this.registerModel(Blocks.DRAGON_EGG, "dragon_egg");
    }

    @Override
    public void reload(ResourceManager resourceManager) {
        this.modelShaper.rebuildCache();
    }
}
