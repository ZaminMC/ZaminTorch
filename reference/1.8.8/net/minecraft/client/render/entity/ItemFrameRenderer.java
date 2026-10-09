package net.minecraft.client.render.entity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.block.BlockRenderDispatcher;
import net.minecraft.client.render.model.block.ModelTransformations;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.platform.Lighting;
import net.minecraft.client.render.texture.CompassSprite;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.client.render.texture.TextureAtlasSprite;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.client.resource.ModelIdentifier;
import net.minecraft.client.resource.model.BakedModel;
import net.minecraft.client.resource.model.ModelManager;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.SkullItem;
import net.minecraft.resource.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.map.SavedMapData;
import org.lwjgl.opengl.GL11;

public class ItemFrameRenderer extends EntityRenderer<ItemFrameEntity> {
    private static final Identifier MAP_BACKGROUND_LOCATION = new Identifier("textures/map/map_background.png");
    private final Minecraft minecraft = Minecraft.getInstance();
    private final ModelIdentifier NORMAL_ITEM_FRAME_LOCATION = new ModelIdentifier("item_frame", "normal");
    private final ModelIdentifier MAP_ITEM_FRAME_LOCATION = new ModelIdentifier("item_frame", "map");
    private ItemRenderer itemRenderer;

    public ItemFrameRenderer(EntityRenderDispatcher dispatcher, ItemRenderer itemRenderer) {
        super(dispatcher);
        this.itemRenderer = itemRenderer;
    }

    public void render(ItemFrameEntity itemFrameEntity, double d, double e, double f, float g, float h) {
        GlStateManager.pushMatrix();
        BlockPos blockpos = itemFrameEntity.getBlockPos();
        double d0 = blockpos.getX() - itemFrameEntity.x + d;
        double d1 = blockpos.getY() - itemFrameEntity.y + e;
        double d2 = blockpos.getZ() - itemFrameEntity.z + f;
        GlStateManager.translated(d0 + 0.5, d1 + 0.5, d2 + 0.5);
        GlStateManager.rotatef(180.0F - itemFrameEntity.yaw, 0.0F, 1.0F, 0.0F);
        this.dispatcher.textureManager.bind(TextureAtlas.BLOCKS_LOCATION);
        BlockRenderDispatcher blockrenderdispatcher = this.minecraft.getBlockRenderDispatcher();
        ModelManager modelmanager = blockrenderdispatcher.getModelShaper().getManager();
        BakedModel bakedmodel;
        if (itemFrameEntity.getDisplayItem() != null && itemFrameEntity.getDisplayItem().getItem() == Items.FILLED_MAP) {
            bakedmodel = modelmanager.getModel(this.MAP_ITEM_FRAME_LOCATION);
        } else {
            bakedmodel = modelmanager.getModel(this.NORMAL_ITEM_FRAME_LOCATION);
        }

        GlStateManager.pushMatrix();
        GlStateManager.translatef(-0.5F, -0.5F, -0.5F);
        blockrenderdispatcher.getModelRenderer().render(bakedmodel, 1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
        GlStateManager.translatef(0.0F, 0.0F, 0.4375F);
        this.renderDisplayItem(itemFrameEntity);
        GlStateManager.popMatrix();
        this.renderNameTag(itemFrameEntity, d + itemFrameEntity.dir.getOffsetX() * 0.3F, e - 0.25, f + itemFrameEntity.dir.getOffsetZ() * 0.3F);
    }

    protected Identifier getTextureLocation(ItemFrameEntity itemFrameEntity) {
        return null;
    }

    private void renderDisplayItem(ItemFrameEntity itemFrame) {
        ItemStack itemstack = itemFrame.getDisplayItem();
        if (itemstack != null) {
            ItemEntity itementity = new ItemEntity(itemFrame.world, 0.0, 0.0, 0.0, itemstack);
            Item item = itementity.getItem().getItem();
            itementity.getItem().size = 1;
            itementity.bobOffset = 0.0F;
            GlStateManager.pushMatrix();
            GlStateManager.disableLighting();
            int i = itemFrame.rotation();
            if (item == Items.FILLED_MAP) {
                i = i % 4 * 2;
            }

            GlStateManager.rotatef(i * 360.0F / 8.0F, 0.0F, 0.0F, 1.0F);
            if (item == Items.FILLED_MAP) {
                this.dispatcher.textureManager.bind(MAP_BACKGROUND_LOCATION);
                GlStateManager.rotatef(180.0F, 0.0F, 0.0F, 1.0F);
                float f = 0.0078125F;
                GlStateManager.scalef(f, f, f);
                GlStateManager.translatef(-64.0F, -64.0F, 0.0F);
                SavedMapData savedmapdata = Items.FILLED_MAP.getSavedMapData(itementity.getItem(), itemFrame.world);
                GlStateManager.translatef(0.0F, 0.0F, -1.0F);
                if (savedmapdata != null) {
                    this.minecraft.gameRenderer.getMapRenderer().draw(savedmapdata, true);
                }
            } else {
                TextureAtlasSprite textureatlassprite = null;
                if (item == Items.COMPASS) {
                    textureatlassprite = this.minecraft.getBlocksAtlas().getSprite(CompassSprite.name);
                    this.minecraft.getTextureManager().bind(TextureAtlas.BLOCKS_LOCATION);
                    if (textureatlassprite instanceof CompassSprite) {
                        CompassSprite compasssprite = (CompassSprite)textureatlassprite;
                        double d0 = compasssprite.angle;
                        double d1 = compasssprite.angleDelta;
                        compasssprite.angle = 0.0;
                        compasssprite.angleDelta = 0.0;
                        compasssprite.tick(
                            itemFrame.world, itemFrame.x, itemFrame.z, MathHelper.wrapDegrees(180 + itemFrame.dir.getIdHorizontal() * 90), false, true
                        );
                        compasssprite.angle = d0;
                        compasssprite.angleDelta = d1;
                    } else {
                        textureatlassprite = null;
                    }
                }

                GlStateManager.scalef(0.5F, 0.5F, 0.5F);
                if (!this.itemRenderer.isGui3d(itementity.getItem()) || item instanceof SkullItem) {
                    GlStateManager.rotatef(180.0F, 0.0F, 1.0F, 0.0F);
                }

                GlStateManager.pushLightingAttributes();
                Lighting.turnOn();
                this.itemRenderer.renderItemInHand(itementity.getItem(), ModelTransformations.Type.FIXED);
                Lighting.turnOff();
                GlStateManager.popAttributes();
                if (textureatlassprite != null && textureatlassprite.getFrameCount() > 0) {
                    textureatlassprite.tick();
                }
            }

            GlStateManager.enableLighting();
            GlStateManager.popMatrix();
        }
    }

    protected void renderNameTag(ItemFrameEntity itemFrame, double x, double y, double z) {
        if (Minecraft.isDisplayGui()
            && itemFrame.getDisplayItem() != null
            && itemFrame.getDisplayItem().hasCustomHoverName()
            && this.dispatcher.targetEntity == itemFrame) {
            float f = 1.6F;
            float f1 = 0.016666668F * f;
            double d0 = itemFrame.squaredDistanceTo(this.dispatcher.camera);
            float f2 = itemFrame.isSneaking() ? 32.0F : 64.0F;
            if (d0 < f2 * f2) {
                String s = itemFrame.getDisplayItem().getHoverName();
                if (itemFrame.isSneaking()) {
                    TextRenderer textrenderer = this.getTextRenderer();
                    GlStateManager.pushMatrix();
                    GlStateManager.translatef((float)x + 0.0F, (float)y + itemFrame.height + 0.5F, (float)z);
                    GL11.glNormal3f(0.0F, 1.0F, 0.0F);
                    GlStateManager.rotatef(-this.dispatcher.cameraYaw, 0.0F, 1.0F, 0.0F);
                    GlStateManager.rotatef(this.dispatcher.cameraPitch, 1.0F, 0.0F, 0.0F);
                    GlStateManager.scalef(-f1, -f1, f1);
                    GlStateManager.disableLighting();
                    GlStateManager.translatef(0.0F, 0.25F / f1, 0.0F);
                    GlStateManager.depthMask(false);
                    GlStateManager.enableBlend();
                    GlStateManager.blendFunc(770, 771);
                    Tesselator tesselator = Tesselator.getInstance();
                    BufferBuilder bufferbuilder = tesselator.getBuffer();
                    int i = textrenderer.getWidth(s) / 2;
                    GlStateManager.disableTexture();
                    bufferbuilder.begin(7, DefaultVertexFormat.POSITION_COLOR);
                    bufferbuilder.vertex(-i - 1, -1.0, 0.0).color(0.0F, 0.0F, 0.0F, 0.25F).nextVertex();
                    bufferbuilder.vertex(-i - 1, 8.0, 0.0).color(0.0F, 0.0F, 0.0F, 0.25F).nextVertex();
                    bufferbuilder.vertex(i + 1, 8.0, 0.0).color(0.0F, 0.0F, 0.0F, 0.25F).nextVertex();
                    bufferbuilder.vertex(i + 1, -1.0, 0.0).color(0.0F, 0.0F, 0.0F, 0.25F).nextVertex();
                    tesselator.end();
                    GlStateManager.enableTexture();
                    GlStateManager.depthMask(true);
                    textrenderer.draw(s, -textrenderer.getWidth(s) / 2, 0, 553648127);
                    GlStateManager.enableLighting();
                    GlStateManager.disableBlend();
                    GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
                    GlStateManager.popMatrix();
                } else {
                    this.renderNameTag(itemFrame, s, x, y, z, 64);
                }
            }
        }
    }
}
