package net.minecraft.client.render.entity.layer;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.entity.WitchRenderer;
import net.minecraft.client.render.model.block.ModelTransformations;
import net.minecraft.client.render.model.entity.WitchModel;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.living.mob.monster.WitchEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class WitchItemInHandLayer implements EntityRenderLayer<WitchEntity> {
    private final WitchRenderer parent;

    public WitchItemInHandLayer(WitchRenderer parent) {
        this.parent = parent;
    }

    public void render(WitchEntity witchEntity, float f, float g, float h, float i, float j, float k, float l) {
        ItemStack itemstack = witchEntity.getDisplayItemInHand();
        if (itemstack != null) {
            GlStateManager.color3f(1.0F, 1.0F, 1.0F);
            GlStateManager.pushMatrix();
            if (this.parent.getModel().isBaby) {
                GlStateManager.translatef(0.0F, 0.625F, 0.0F);
                GlStateManager.rotatef(-20.0F, -1.0F, 0.0F, 0.0F);
                float fx = 0.5F;
                GlStateManager.scalef(fx, fx, fx);
            }

            ((WitchModel)this.parent.getModel()).nose.transform(0.0625F);
            GlStateManager.translatef(-0.0625F, 0.53125F, 0.21875F);
            Item item = itemstack.getItem();
            Minecraft minecraft = Minecraft.getInstance();
            if (item instanceof BlockItem && minecraft.getBlockRenderDispatcher().isItem3d(Block.byItem(item), itemstack.getMetadata())) {
                GlStateManager.translatef(0.0F, 0.0625F, -0.25F);
                GlStateManager.rotatef(30.0F, 1.0F, 0.0F, 0.0F);
                GlStateManager.rotatef(-5.0F, 0.0F, 1.0F, 0.0F);
                float f4 = 0.375F;
                GlStateManager.scalef(f4, -f4, f4);
            } else if (item == Items.BOW) {
                GlStateManager.translatef(0.0F, 0.125F, -0.125F);
                GlStateManager.rotatef(-45.0F, 0.0F, 1.0F, 0.0F);
                float f1 = 0.625F;
                GlStateManager.scalef(f1, -f1, f1);
                GlStateManager.rotatef(-100.0F, 1.0F, 0.0F, 0.0F);
                GlStateManager.rotatef(-20.0F, 0.0F, 1.0F, 0.0F);
            } else if (item.isHandheld()) {
                if (item.shouldRotate()) {
                    GlStateManager.rotatef(180.0F, 0.0F, 0.0F, 1.0F);
                    GlStateManager.translatef(0.0F, -0.0625F, 0.0F);
                }

                this.parent.glTranslate();
                GlStateManager.translatef(0.0625F, -0.125F, 0.0F);
                float f2 = 0.625F;
                GlStateManager.scalef(f2, -f2, f2);
                GlStateManager.rotatef(0.0F, 1.0F, 0.0F, 0.0F);
                GlStateManager.rotatef(0.0F, 0.0F, 1.0F, 0.0F);
            } else {
                GlStateManager.translatef(0.1875F, 0.1875F, 0.0F);
                float f3 = 0.875F;
                GlStateManager.scalef(f3, f3, f3);
                GlStateManager.rotatef(-20.0F, 0.0F, 0.0F, 1.0F);
                GlStateManager.rotatef(-60.0F, 1.0F, 0.0F, 0.0F);
                GlStateManager.rotatef(-30.0F, 0.0F, 0.0F, 1.0F);
            }

            GlStateManager.rotatef(-15.0F, 1.0F, 0.0F, 0.0F);
            GlStateManager.rotatef(40.0F, 0.0F, 0.0F, 1.0F);
            minecraft.getItemInHandRenderer().render(witchEntity, itemstack, ModelTransformations.Type.THIRD_PERSON);
            GlStateManager.popMatrix();
        }
    }

    @Override
    public boolean colorsWhenDamaged() {
        return false;
    }
}
