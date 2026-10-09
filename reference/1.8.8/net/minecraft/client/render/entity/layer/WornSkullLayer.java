package net.minecraft.client.render.entity.layer;

import com.mojang.authlib.GameProfile;
import net.minecraft.block.entity.SkullBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.block.entity.SkullRenderer;
import net.minecraft.client.render.model.ModelPart;
import net.minecraft.client.render.model.block.ModelTransformations;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.mob.monster.ZombieEntity;
import net.minecraft.entity.living.mob.passive.VillagerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.text.StringUtils;
import net.minecraft.util.math.Direction;

public class WornSkullLayer implements EntityRenderLayer<LivingEntity> {
    private final ModelPart model;

    public WornSkullLayer(ModelPart model) {
        this.model = model;
    }

    @Override
    public void render(
        LivingEntity entity, float walkAnimationProgress, float walkAnimationSpeed, float tickDelta, float bob, float yaw, float pitch, float scale
    ) {
        ItemStack itemstack = entity.getArmor(3);
        if (itemstack != null && itemstack.getItem() != null) {
            Item item = itemstack.getItem();
            Minecraft minecraft = Minecraft.getInstance();
            GlStateManager.pushMatrix();
            if (entity.isSneaking()) {
                GlStateManager.translatef(0.0F, 0.2F, 0.0F);
            }

            boolean flag = entity instanceof VillagerEntity || entity instanceof ZombieEntity && ((ZombieEntity)entity).isVillager();
            if (!flag && entity.isBaby()) {
                float f = 2.0F;
                float f1 = 1.4F;
                GlStateManager.scalef(f1 / f, f1 / f, f1 / f);
                GlStateManager.translatef(0.0F, 16.0F * scale, 0.0F);
            }

            this.model.transform(0.0625F);
            GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            if (item instanceof BlockItem) {
                float f2 = 0.625F;
                GlStateManager.translatef(0.0F, -0.25F, 0.0F);
                GlStateManager.rotatef(180.0F, 0.0F, 1.0F, 0.0F);
                GlStateManager.scalef(f2, -f2, -f2);
                if (flag) {
                    GlStateManager.translatef(0.0F, 0.1875F, 0.0F);
                }

                minecraft.getItemInHandRenderer().render(entity, itemstack, ModelTransformations.Type.HEAD);
            } else if (item == Items.SKULL) {
                float f3 = 1.1875F;
                GlStateManager.scalef(f3, -f3, -f3);
                if (flag) {
                    GlStateManager.translatef(0.0F, 0.0625F, 0.0F);
                }

                GameProfile gameprofile = null;
                if (itemstack.hasNbt()) {
                    NbtCompound nbtcompound = itemstack.getNbt();
                    if (nbtcompound.contains("SkullOwner", 10)) {
                        gameprofile = NbtUtils.readProfile(nbtcompound.getCompound("SkullOwner"));
                    } else if (nbtcompound.contains("SkullOwner", 8)) {
                        String s = nbtcompound.getString("SkullOwner");
                        if (!StringUtils.isStringEmpty(s)) {
                            gameprofile = SkullBlockEntity.updateProfile(new GameProfile(null, s));
                            nbtcompound.put("SkullOwner", NbtUtils.writeProfile(new NbtCompound(), gameprofile));
                        }
                    }
                }

                SkullRenderer.INSTANCE.render(-0.5F, 0.0F, -0.5F, Direction.UP, 180.0F, itemstack.getMetadata(), gameprofile, -1);
            }

            GlStateManager.popMatrix();
        }
    }

    @Override
    public boolean colorsWhenDamaged() {
        return true;
    }
}
