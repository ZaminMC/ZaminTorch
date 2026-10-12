package net.minecraft.entity.living.effect;

import com.google.common.collect.Maps;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.attribute.AbstractEntityAttributeContainer;
import net.minecraft.entity.living.attribute.AttributeModifier;
import net.minecraft.entity.living.attribute.EntityAttribute;
import net.minecraft.entity.living.attribute.EntityAttributeInstance;
import net.minecraft.entity.living.attribute.EntityAttributes;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.resource.Identifier;
import net.minecraft.text.StringUtils;

public class StatusEffect {
    public static final StatusEffect[] BY_ID = new StatusEffect[32];
    private static final Map<Identifier, StatusEffect> REGISTRY = Maps.newHashMap();
    public static final StatusEffect UNKNOWN = null;
    public static final StatusEffect SPEED = new StatusEffect(1, new Identifier("speed"), false, 8171462)
        .setKey("potion.moveSpeed")
        .setIcon(0, 0)
        .addModifier(EntityAttributes.MOVEMENT_SPEED, "91AEAA56-376B-4498-935B-2F7F68070635", 0.2F, 2);
    public static final StatusEffect SLOWNESS = new StatusEffect(2, new Identifier("slowness"), true, 5926017)
        .setKey("potion.moveSlowdown")
        .setIcon(1, 0)
        .addModifier(EntityAttributes.MOVEMENT_SPEED, "7107DE5E-7CE8-4030-940E-514C1F160890", -0.15F, 2);
    public static final StatusEffect HASTE = new StatusEffect(3, new Identifier("haste"), false, 14270531)
        .setKey("potion.digSpeed")
        .setIcon(2, 0)
        .setDurationMultiplier(1.5);
    public static final StatusEffect MINING_FATIGUE = new StatusEffect(4, new Identifier("mining_fatigue"), true, 4866583)
        .setKey("potion.digSlowDown")
        .setIcon(3, 0);
    public static final StatusEffect STRENGTH = new CombatStatusEffect(5, new Identifier("strength"), false, 9643043)
        .setKey("potion.damageBoost")
        .setIcon(4, 0)
        .addModifier(EntityAttributes.ATTACK_DAMAGE, "648D7064-6A60-4F59-8ABE-C2C23A6DD7A9", 2.5, 2);
    public static final StatusEffect INSTANT_HEALTH = new InstantStatusEffect(6, new Identifier("instant_health"), false, 16262179).setKey("potion.heal");
    public static final StatusEffect INSTANT_DAMAGE = new InstantStatusEffect(7, new Identifier("instant_damage"), true, 4393481).setKey("potion.harm");
    public static final StatusEffect JUMP_BOOST = new StatusEffect(8, new Identifier("jump_boost"), false, 2293580).setKey("potion.jump").setIcon(2, 1);
    public static final StatusEffect NAUSEA = new StatusEffect(9, new Identifier("nausea"), true, 5578058)
        .setKey("potion.confusion")
        .setIcon(3, 1)
        .setDurationMultiplier(0.25);
    public static final StatusEffect REGENERATION = new StatusEffect(10, new Identifier("regeneration"), false, 13458603)
        .setKey("potion.regeneration")
        .setIcon(7, 0)
        .setDurationMultiplier(0.25);
    public static final StatusEffect RESISTANCE = new StatusEffect(11, new Identifier("resistance"), false, 10044730).setKey("potion.resistance").setIcon(6, 1);
    public static final StatusEffect FIRE_RESISTANCE = new StatusEffect(12, new Identifier("fire_resistance"), false, 14981690)
        .setKey("potion.fireResistance")
        .setIcon(7, 1);
    public static final StatusEffect WATER_BREATHING = new StatusEffect(13, new Identifier("water_breathing"), false, 3035801)
        .setKey("potion.waterBreathing")
        .setIcon(0, 2);
    public static final StatusEffect INVISIBILITY = new StatusEffect(14, new Identifier("invisibility"), false, 8356754)
        .setKey("potion.invisibility")
        .setIcon(0, 1);
    public static final StatusEffect BLINDNESS = new StatusEffect(15, new Identifier("blindness"), true, 2039587)
        .setKey("potion.blindness")
        .setIcon(5, 1)
        .setDurationMultiplier(0.25);
    public static final StatusEffect NIGHTVISION = new StatusEffect(16, new Identifier("night_vision"), false, 2039713)
        .setKey("potion.nightVision")
        .setIcon(4, 1);
    public static final StatusEffect HUNGER = new StatusEffect(17, new Identifier("hunger"), true, 5797459).setKey("potion.hunger").setIcon(1, 1);
    public static final StatusEffect WEAKNESS = new CombatStatusEffect(18, new Identifier("weakness"), true, 4738376)
        .setKey("potion.weakness")
        .setIcon(5, 0)
        .addModifier(EntityAttributes.ATTACK_DAMAGE, "22653B89-116E-49DC-9B6B-9971489B5BE5", 2.0, 0);
    public static final StatusEffect POISON = new StatusEffect(19, new Identifier("poison"), true, 5149489)
        .setKey("potion.poison")
        .setIcon(6, 0)
        .setDurationMultiplier(0.25);
    public static final StatusEffect WITHER = new StatusEffect(20, new Identifier("wither"), true, 3484199)
        .setKey("potion.wither")
        .setIcon(1, 2)
        .setDurationMultiplier(0.25);
    public static final StatusEffect HEALTH_BOOST = new HealthBoostStatusEffect(21, new Identifier("health_boost"), false, 16284963)
        .setKey("potion.healthBoost")
        .setIcon(2, 2)
        .addModifier(EntityAttributes.MAX_HEALTH, "5D6F0BA2-1186-46AC-B896-C61C5CEE99CC", 4.0, 0);
    public static final StatusEffect ABSORPTION = new AbsorptionStatusEffect(22, new Identifier("absorption"), false, 2445989)
        .setKey("potion.absorption")
        .setIcon(2, 2);
    public static final StatusEffect SATURATION = new InstantStatusEffect(23, new Identifier("saturation"), false, 16262179).setKey("potion.saturation");
    public static final StatusEffect f_7316943 = null;
    public static final StatusEffect f_1102586 = null;
    public static final StatusEffect f_2839167 = null;
    public static final StatusEffect f_0817405 = null;
    public static final StatusEffect f_6765913 = null;
    public static final StatusEffect f_7424866 = null;
    public static final StatusEffect f_4501076 = null;
    public static final StatusEffect f_4557420 = null;
    public final int id;
    private final Map<EntityAttribute, AttributeModifier> modifiers = Maps.newHashMap();
    private final boolean harmful;
    private final int potionColor;
    private String key = "";
    private int iconIndex = -1;
    private double durationMultiplier;
    private boolean usable;

    protected StatusEffect(int id, Identifier key, boolean harmful, int potionColor) {
        this.id = id;
        BY_ID[id] = this;
        REGISTRY.put(key, this);
        this.harmful = harmful;
        if (harmful) {
            this.durationMultiplier = 0.5;
        } else {
            this.durationMultiplier = 1.0;
        }

        this.potionColor = potionColor;
    }

    public static StatusEffect get(String key) {
        return REGISTRY.get(new Identifier(key));
    }

    public static Set<Identifier> getKeys() {
        return REGISTRY.keySet();
    }

    protected StatusEffect setIcon(int column, int row) {
        this.iconIndex = column + row * 8;
        return this;
    }

    public int getId() {
        return this.id;
    }

    public void apply(LivingEntity entity, int amplifier) {
        if (this.id == REGENERATION.id) {
            if (entity.getHealth() < entity.getMaxHealth()) {
                entity.heal(1.0F);
            }
        } else if (this.id == POISON.id) {
            if (entity.getHealth() > 1.0F) {
                entity.takeDamage(DamageSource.MAGIC, 1.0F);
            }
        } else if (this.id == WITHER.id) {
            entity.takeDamage(DamageSource.WITHER, 1.0F);
        } else if (this.id == HUNGER.id && entity instanceof PlayerEntity) {
            ((PlayerEntity)entity).addFatigue(0.025F * (amplifier + 1));
        } else if (this.id == SATURATION.id && entity instanceof PlayerEntity) {
            if (!entity.world.isClient) {
                ((PlayerEntity)entity).getHungerManager().add(amplifier + 1, 1.0F);
            }
        } else if ((this.id != INSTANT_HEALTH.id || entity.isAffectedBySmite()) && (this.id != INSTANT_DAMAGE.id || !entity.isAffectedBySmite())) {
            if (this.id == INSTANT_DAMAGE.id && !entity.isAffectedBySmite() || this.id == INSTANT_HEALTH.id && entity.isAffectedBySmite()) {
                entity.takeDamage(DamageSource.MAGIC, 6 << amplifier);
            }
        } else {
            entity.heal(Math.max(4 << amplifier, 0));
        }
    }

    public void affectHealth(Entity potion, Entity thrower, LivingEntity target, int amplifier, double proximity) {
        if ((this.id != INSTANT_HEALTH.id || target.isAffectedBySmite()) && (this.id != INSTANT_DAMAGE.id || !target.isAffectedBySmite())) {
            if (this.id == INSTANT_DAMAGE.id && !target.isAffectedBySmite() || this.id == INSTANT_HEALTH.id && target.isAffectedBySmite()) {
                int j = (int)(proximity * (6 << amplifier) + 0.5);
                if (potion == null) {
                    target.takeDamage(DamageSource.MAGIC, j);
                } else {
                    target.takeDamage(DamageSource.magic(potion, thrower), j);
                }
            }
        } else {
            int i = (int)(proximity * (4 << amplifier) + 0.5);
            target.heal(i);
        }
    }

    public boolean isInstant() {
        return false;
    }

    /**
     * Returns whether this status effect should apply its effect with the given duration left over.
     */
    public boolean shouldApply(int duration, int amplifier) {
        if (this.id == REGENERATION.id) {
            int k = 50 >> amplifier;
            return k <= 0 || duration % k == 0;
        } else if (this.id == POISON.id) {
            int j = 25 >> amplifier;
            return j <= 0 || duration % j == 0;
        } else if (this.id == WITHER.id) {
            int i = 40 >> amplifier;
            return i <= 0 || duration % i == 0;
        } else {
            return this.id == HUNGER.id;
        }
    }

    public StatusEffect setKey(String key) {
        this.key = key;
        return this;
    }

    public String getTranslationKey() {
        return this.key;
    }

    public boolean hasIcon() {
        return this.iconIndex >= 0;
    }

    public int getIconIndex() {
        return this.iconIndex;
    }

    public boolean isHarmful() {
        return this.harmful;
    }

    public static String getDurationString(StatusEffectInstance instance) {
        if (instance.isPermanent()) {
            return "**:**";
        }

        int i = instance.getDuration();
        return StringUtils.getDurationString(i);
    }

    protected StatusEffect setDurationMultiplier(double durationMultiplier) {
        this.durationMultiplier = durationMultiplier;
        return this;
    }

    public double getDurationMultiplier() {
        return this.durationMultiplier;
    }

    public boolean isUsable() {
        return this.usable;
    }

    public int getPotionColor() {
        return this.potionColor;
    }

    public StatusEffect addModifier(EntityAttribute attribute, String id, double value, int operation) {
        AttributeModifier attributemodifier = new AttributeModifier(UUID.fromString(id), this.getTranslationKey(), value, operation);
        this.modifiers.put(attribute, attributemodifier);
        return this;
    }

    public Map<EntityAttribute, AttributeModifier> getModifiers() {
        return this.modifiers;
    }

    public void removeModifiers(LivingEntity entity, AbstractEntityAttributeContainer container, int amplifier) {
        for (Entry<EntityAttribute, AttributeModifier> entry : this.modifiers.entrySet()) {
            EntityAttributeInstance entityattributeinstance = container.get(entry.getKey());
            if (entityattributeinstance != null) {
                entityattributeinstance.removeModifier(entry.getValue());
            }
        }
    }

    public void addModifiers(LivingEntity entity, AbstractEntityAttributeContainer container, int amplifier) {
        for (Entry<EntityAttribute, AttributeModifier> entry : this.modifiers.entrySet()) {
            EntityAttributeInstance entityattributeinstance = container.get(entry.getKey());
            if (entityattributeinstance != null) {
                AttributeModifier attributemodifier = entry.getValue();
                entityattributeinstance.removeModifier(attributemodifier);
                entityattributeinstance.addModifier(
                    new AttributeModifier(
                        attributemodifier.getId(),
                        this.getTranslationKey() + " " + amplifier,
                        this.getModifier(amplifier, attributemodifier),
                        attributemodifier.getOperation()
                    )
                );
            }
        }
    }

    public double getModifier(int amplifier, AttributeModifier modifier) {
        return modifier.get() * (amplifier + 1);
    }
}
