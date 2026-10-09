package net.minecraft.client.render.entity.layer;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.model.block.ModelTransformations;
import net.minecraft.client.render.model.entity.HumanoidModel;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class ItemInHandLayer implements EntityRenderLayer<LivingEntity> {
    private final LivingEntityRenderer<?> parent;

    public ItemInHandLayer(LivingEntityRenderer<?> parent) {
        this.parent = parent;
    }

    @Override
    public void render(
        LivingEntity entity, float walkAnimationProgress, float walkAnimationSpeed, float tickDelta, float bob, float yaw, float pitch, float scale
    ) {
        ItemStack itemstack = entity.getDisplayItemInHand();
        if (itemstack != null) {
            GlStateManager.pushMatrix();
            if (this.parent.getModel().isBaby) {
                float f = 0.5F;
                GlStateManager.translatef(0.0F, 0.625F, 0.0F);
                GlStateManager.rotatef(-20.0F, -1.0F, 0.0F, 0.0F);
                GlStateManager.scalef(f, f, f);
            }

            ((HumanoidModel)this.parent.getModel()).translateRightArm(0.0625F);
            GlStateManager.translatef(-0.0625F, 0.4375F, 0.0625F);
            if (entity instanceof PlayerEntity && ((PlayerEntity)entity).fishingBobber != null) {
                itemstack = new ItemStack(Items.FISHING_ROD, 0);
            }

            Item item = itemstack.getItem();
            Minecraft minecraft = Minecraft.getInstance();
            if (item instanceof BlockItem && Block.byItem(item).getRenderType() == 2) {
                GlStateManager.translatef(0.0F, 0.1875F, -0.3125F);
                GlStateManager.rotatef(20.0F, 1.0F, 0.0F, 0.0F);
                GlStateManager.rotatef(45.0F, 0.0F, 1.0F, 0.0F);
                float f1 = 0.375F;
                GlStateManager.scalef(-f1, -f1, f1);
            }

            if (entity.isSneaking()) {
                GlStateManager.translatef(0.0F, 0.203125F, 0.0F);
            }

            minecraft.getItemInHandRenderer().render(entity, itemstack, ModelTransformations.Type.THIRD_PERSON);
            GlStateManager.popMatrix();
        }
    }

    @Override
    public boolean colorsWhenDamaged() {
        return false;
    }
}
