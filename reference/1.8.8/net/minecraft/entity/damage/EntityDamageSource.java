package net.minecraft.entity.damage;

import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.locale.I18n;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;

public class EntityDamageSource extends DamageSource {
    protected Entity source;
    private boolean thorns = false;

    public EntityDamageSource(String name, Entity source) {
        super(name);
        this.source = source;
    }

    public EntityDamageSource setThorns() {
        this.thorns = true;
        return this;
    }

    public boolean hasThorns() {
        return this.thorns;
    }

    @Override
    public Entity getAttacker() {
        return this.source;
    }

    @Override
    public Text getDeathMessage(LivingEntity entity) {
        ItemStack itemstack = this.source instanceof LivingEntity ? ((LivingEntity)this.source).getDisplayItemInHand() : null;
        String s = "death.attack." + this.name;
        String s1 = s + ".item";
        return itemstack != null && itemstack.hasCustomHoverName() && I18n.hasTranslation(s1)
            ? new TranslatableText(s1, entity.getDisplayName(), this.source.getDisplayName(), itemstack.getDisplayName())
            : new TranslatableText(s, entity.getDisplayName(), this.source.getDisplayName());
    }

    @Override
    public boolean isScaledWithDifficulty() {
        return this.source != null && this.source instanceof LivingEntity && !(this.source instanceof PlayerEntity);
    }
}
