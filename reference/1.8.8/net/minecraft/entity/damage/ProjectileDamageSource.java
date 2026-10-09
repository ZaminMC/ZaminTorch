package net.minecraft.entity.damage;

import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.locale.I18n;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;

public class ProjectileDamageSource extends EntityDamageSource {
    private Entity shooter;

    public ProjectileDamageSource(String name, Entity projectile, Entity shooter) {
        super(name, projectile);
        this.shooter = shooter;
    }

    @Override
    public Entity getSource() {
        return this.source;
    }

    @Override
    public Entity getAttacker() {
        return this.shooter;
    }

    @Override
    public Text getDeathMessage(LivingEntity entity) {
        Text text = this.shooter == null ? this.source.getDisplayName() : this.shooter.getDisplayName();
        ItemStack itemstack = this.shooter instanceof LivingEntity ? ((LivingEntity)this.shooter).getDisplayItemInHand() : null;
        String s = "death.attack." + this.name;
        String s1 = s + ".item";
        return itemstack != null && itemstack.hasCustomHoverName() && I18n.hasTranslation(s1)
            ? new TranslatableText(s1, entity.getDisplayName(), text, itemstack.getDisplayName())
            : new TranslatableText(s, entity.getDisplayName(), text);
    }
}
