package net.minecraft.client.render.entity.layer;

import com.google.common.collect.Maps;
import java.util.Map;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.model.Model;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.Identifier;

public abstract class AbstractArmorLayer<T extends Model> implements EntityRenderLayer<LivingEntity> {
    protected static final Identifier ENCHANTMENT_GLINT_TEXTURE = new Identifier("textures/misc/enchanted_item_glint.png");
    protected T innerModel;
    protected T outerModel;
    private final LivingEntityRenderer<?> parent;
    private float alpha = 1.0F;
    private float red = 1.0F;
    private float green = 1.0F;
    private float blue = 1.0F;
    private boolean hasColor;
    private static final Map<String, Identifier> TEXTURE_CACHE = Maps.newHashMap();

    public AbstractArmorLayer(LivingEntityRenderer<?> parent) {
        this.parent = parent;
        this.hideAll();
    }

    @Override
    public void render(
        LivingEntity entity, float walkAnimationProgress, float walkAnimationSpeed, float tickDelta, float bob, float yaw, float pitch, float scale
    ) {
        this.renderArmor(entity, walkAnimationProgress, walkAnimationSpeed, tickDelta, bob, yaw, pitch, scale, 4);
        this.renderArmor(entity, walkAnimationProgress, walkAnimationSpeed, tickDelta, bob, yaw, pitch, scale, 3);
        this.renderArmor(entity, walkAnimationProgress, walkAnimationSpeed, tickDelta, bob, yaw, pitch, scale, 2);
        this.renderArmor(entity, walkAnimationProgress, walkAnimationSpeed, tickDelta, bob, yaw, pitch, scale, 1);
    }

    @Override
    public boolean colorsWhenDamaged() {
        return false;
    }

    private void renderArmor(
        LivingEntity entity,
        float walkAnimationProgress,
        float walkAnimationSpeed,
        float tickDelta,
        float bob,
        float yaw,
        float pitch,
        float scale,
        int equipmentSlot
    ) {
        ItemStack itemstack = this.getArmor(entity, equipmentSlot);
        if (itemstack != null && itemstack.getItem() instanceof ArmorItem) {
            ArmorItem armoritem = (ArmorItem)itemstack.getItem();
            T t = this.getModel(equipmentSlot);
            t.copyPropertiesFrom(this.parent.getModel());
            t.prepare(entity, walkAnimationProgress, walkAnimationSpeed, tickDelta);
            this.setVisible(t, equipmentSlot);
            boolean flag = this.usesInnerModel(equipmentSlot);
            this.parent.bindTexture(this.getArmorTexture(armoritem, flag));
            switch (armoritem.getTier()) {
                case CLOTH:
                    int i = armoritem.getColor(itemstack);
                    float f = (i >> 16 & 0xFF) / 255.0F;
                    float f1 = (i >> 8 & 0xFF) / 255.0F;
                    float f2 = (i & 0xFF) / 255.0F;
                    GlStateManager.color4f(this.red * f, this.green * f1, this.blue * f2, this.alpha);
                    t.render(entity, walkAnimationProgress, walkAnimationSpeed, bob, yaw, pitch, scale);
                    this.parent.bindTexture(this.getArmorTexture(armoritem, flag, "overlay"));
                case CHAIN:
                case IRON:
                case GOLD:
                case DIAMOND:
                    GlStateManager.color4f(this.red, this.green, this.blue, this.alpha);
                    t.render(entity, walkAnimationProgress, walkAnimationSpeed, bob, yaw, pitch, scale);
                default:
                    if (!this.hasColor && itemstack.hasEnchantments()) {
                        this.renderEnchantmentGlint(entity, t, walkAnimationProgress, walkAnimationSpeed, tickDelta, bob, yaw, pitch, scale);
                    }
            }
        }
    }

    public ItemStack getArmor(LivingEntity entity, int equipmentSlot) {
        return entity.getArmor(equipmentSlot - 1);
    }

    public T getModel(int equipmentSlot) {
        return this.usesInnerModel(equipmentSlot) ? this.innerModel : this.outerModel;
    }

    private boolean usesInnerModel(int equipmentSlot) {
        return equipmentSlot == 2;
    }

    private void renderEnchantmentGlint(
        LivingEntity entity, T model, float walkAnimationProgress, float walkAnimationSpeed, float tickDelta, float bob, float yaw, float pitch, float scale
    ) {
        float f = entity.ticks + tickDelta;
        this.parent.bindTexture(ENCHANTMENT_GLINT_TEXTURE);
        GlStateManager.enableBlend();
        GlStateManager.depthFunc(514);
        GlStateManager.depthMask(false);
        float f1 = 0.5F;
        GlStateManager.color4f(f1, f1, f1, 1.0F);

        for (int i = 0; i < 2; i++) {
            GlStateManager.disableLighting();
            GlStateManager.blendFunc(768, 1);
            float f2 = 0.76F;
            GlStateManager.color4f(0.5F * f2, 0.25F * f2, 0.8F * f2, 1.0F);
            GlStateManager.matrixMode(5890);
            GlStateManager.loadIdentity();
            float f3 = 0.33333334F;
            GlStateManager.scalef(f3, f3, f3);
            GlStateManager.rotatef(30.0F - i * 60.0F, 0.0F, 0.0F, 1.0F);
            GlStateManager.translatef(0.0F, f * (0.001F + i * 0.003F) * 20.0F, 0.0F);
            GlStateManager.matrixMode(5888);
            model.render(entity, walkAnimationProgress, walkAnimationSpeed, bob, yaw, pitch, scale);
        }

        GlStateManager.matrixMode(5890);
        GlStateManager.loadIdentity();
        GlStateManager.matrixMode(5888);
        GlStateManager.enableLighting();
        GlStateManager.depthMask(true);
        GlStateManager.depthFunc(515);
        GlStateManager.disableBlend();
    }

    private Identifier getArmorTexture(ArmorItem armor, boolean innerModel) {
        return this.getArmorTexture(armor, innerModel, null);
    }

    private Identifier getArmorTexture(ArmorItem armor, boolean innerModel, String type) {
        String s = String.format(
            "textures/models/armor/%s_layer_%d%s.png", armor.getTier().getKey(), innerModel ? 2 : 1, type == null ? "" : String.format("_%s", type)
        );
        Identifier identifier = TEXTURE_CACHE.get(s);
        if (identifier == null) {
            identifier = new Identifier(s);
            TEXTURE_CACHE.put(s, identifier);
        }

        return identifier;
    }

    protected abstract void hideAll();

    protected abstract void setVisible(T model, int equipmentSlot);
}
