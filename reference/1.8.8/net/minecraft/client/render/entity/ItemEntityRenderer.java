package net.minecraft.client.render.entity;

import java.util.Random;
import net.minecraft.client.render.model.block.ModelTransformations;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.client.resource.model.BakedModel;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.Identifier;
import net.minecraft.util.math.MathHelper;

public class ItemEntityRenderer extends EntityRenderer<ItemEntity> {
    private final ItemRenderer itemRenderer;
    private Random random = new Random();

    public ItemEntityRenderer(EntityRenderDispatcher dispatcher, ItemRenderer itemRenderer) {
        super(dispatcher);
        this.itemRenderer = itemRenderer;
        this.shadowSize = 0.15F;
        this.shadowDarkness = 0.75F;
    }

    private int applyItemBobbing(ItemEntity entity, double dx, double dy, double dz, float tickDelta, BakedModel model) {
        ItemStack itemstack = entity.getItem();
        Item item = itemstack.getItem();
        if (item == null) {
            return 0;
        }

        boolean flag = model.isGui3d();
        int i = this.scaledStackSize(itemstack);
        float f = 0.25F;
        float f1 = MathHelper.sin((entity.getAge() + tickDelta) / 10.0F + entity.bobOffset) * 0.1F + 0.1F;
        float f2 = model.getTransformations().get(ModelTransformations.Type.GROUND).scale.y;
        GlStateManager.translatef((float)dx, (float)dy + f1 + 0.25F * f2, (float)dz);
        if (flag || this.dispatcher.options != null) {
            float f3 = ((entity.getAge() + tickDelta) / 20.0F + entity.bobOffset) * (180.0F / (float)Math.PI);
            GlStateManager.rotatef(f3, 0.0F, 1.0F, 0.0F);
        }

        if (!flag) {
            float f6 = -0.0F * (i - 1) * 0.5F;
            float f4 = -0.0F * (i - 1) * 0.5F;
            float f5 = -0.046875F * (i - 1) * 0.5F;
            GlStateManager.translatef(f6, f4, f5);
        }

        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        return i;
    }

    private int scaledStackSize(ItemStack item) {
        int i = 1;
        if (item.size > 48) {
            i = 5;
        } else if (item.size > 32) {
            i = 4;
        } else if (item.size > 16) {
            i = 3;
        } else if (item.size > 1) {
            i = 2;
        }

        return i;
    }

    public void render(ItemEntity itemEntity, double d, double e, double f, float g, float h) {
        ItemStack itemstack = itemEntity.getItem();
        this.random.setSeed(187L);
        boolean flag = false;
        if (this.bindTexture(itemEntity)) {
            this.dispatcher.textureManager.get(this.getTextureLocation(itemEntity)).pushFilter(false, false);
            flag = true;
        }

        GlStateManager.enableRescaleNormal();
        GlStateManager.alphaFunc(516, 0.1F);
        GlStateManager.enableBlend();
        GlStateManager.blendFuncSeparate(770, 771, 1, 0);
        GlStateManager.pushMatrix();
        BakedModel bakedmodel = this.itemRenderer.getModelShaper().getModel(itemstack);
        int i = this.applyItemBobbing(itemEntity, d, e, f, h, bakedmodel);

        for (int j = 0; j < i; j++) {
            if (bakedmodel.isGui3d()) {
                GlStateManager.pushMatrix();
                if (j > 0) {
                    float fx = (this.random.nextFloat() * 2.0F - 1.0F) * 0.15F;
                    float f1 = (this.random.nextFloat() * 2.0F - 1.0F) * 0.15F;
                    float f2 = (this.random.nextFloat() * 2.0F - 1.0F) * 0.15F;
                    GlStateManager.translatef(fx, f1, f2);
                }

                GlStateManager.scalef(0.5F, 0.5F, 0.5F);
                bakedmodel.getTransformations().apply(ModelTransformations.Type.GROUND);
                this.itemRenderer.renderItem(itemstack, bakedmodel);
                GlStateManager.popMatrix();
            } else {
                GlStateManager.pushMatrix();
                bakedmodel.getTransformations().apply(ModelTransformations.Type.GROUND);
                this.itemRenderer.renderItem(itemstack, bakedmodel);
                GlStateManager.popMatrix();
                float f3 = bakedmodel.getTransformations().ground.scale.x;
                float f4 = bakedmodel.getTransformations().ground.scale.y;
                float f5 = bakedmodel.getTransformations().ground.scale.z;
                GlStateManager.translatef(0.0F * f3, 0.0F * f4, 0.046875F * f5);
            }
        }

        GlStateManager.popMatrix();
        GlStateManager.disableRescaleNormal();
        GlStateManager.disableBlend();
        this.bindTexture(itemEntity);
        if (flag) {
            this.dispatcher.textureManager.get(this.getTextureLocation(itemEntity)).popFilter();
        }

        super.render(itemEntity, d, e, f, g, h);
    }

    protected Identifier getTextureLocation(ItemEntity itemEntity) {
        return TextureAtlas.BLOCKS_LOCATION;
    }
}
