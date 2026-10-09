package net.minecraft.entity.damage;

import net.minecraft.entity.living.LivingEntity;
import net.minecraft.text.Text;

public class DamageRecord {
    private final DamageSource source;
    private final int time;
    private final float damage;
    private final float health;
    private final String fallLocation;
    private final float fallDistance;

    public DamageRecord(DamageSource source, int time, float health, float damage, String fallLocation, float fallDistance) {
        this.source = source;
        this.time = time;
        this.damage = damage;
        this.health = health;
        this.fallLocation = fallLocation;
        this.fallDistance = fallDistance;
    }

    public DamageSource getSource() {
        return this.source;
    }

    public float getDamage() {
        return this.damage;
    }

    public boolean isCombat() {
        return this.source.getAttacker() instanceof LivingEntity;
    }

    public String getFallLocation() {
        return this.fallLocation;
    }

    public Text getAttackerName() {
        return this.getSource().getAttacker() == null ? null : this.getSource().getAttacker().getDisplayName();
    }

    public float getFallDistance() {
        return this.source == DamageSource.OUT_OF_WORLD ? Float.MAX_VALUE : this.fallDistance;
    }
}
