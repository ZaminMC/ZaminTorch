package net.minecraft.entity.living.mob.monster;

import java.util.List;
import java.util.UUID;
import net.minecraft.block.material.Material;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.ProjectileAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundGoal;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.attribute.AttributeModifier;
import net.minecraft.entity.living.attribute.EntityAttributeInstance;
import net.minecraft.entity.living.attribute.EntityAttributes;
import net.minecraft.entity.living.effect.StatusEffect;
import net.minecraft.entity.living.effect.StatusEffectInstance;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.entity.projectile.PotionEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public class WitchEntity extends MonsterEntity implements RangedAttackMob {
    private static final UUID DRINKING_SPEED_PENALTY_UUID = UUID.fromString("5CD17E52-A79A-43D3-A529-90FDE04B181E");
    private static final AttributeModifier DRINKING_SPEED_PENALTY = new AttributeModifier(DRINKING_SPEED_PENALTY_UUID, "Drinking speed penalty", -0.25, 0)
        .setSerialized(false);
    private static final Item[] LOOT = new Item[]{
        Items.GLOWSTONE_DUST, Items.SUGAR, Items.REDSTONE, Items.SPIDER_EYE, Items.GLASS_BOTTLE, Items.GUNPOWDER, Items.STICK, Items.STICK
    };
    private int drinkTimeLeft;

    public WitchEntity(World world) {
        super(world);
        this.setSize(0.6F, 1.95F);
        this.goalSelector.addGoal(1, new SwimGoal(this));
        this.goalSelector.addGoal(2, new ProjectileAttackGoal(this, 1.0, 60, 10.0F));
        this.goalSelector.addGoal(2, new WanderAroundGoal(this, 1.0));
        this.goalSelector.addGoal(3, new LookAtEntityGoal(this, PlayerEntity.class, 8.0F));
        this.goalSelector.addGoal(3, new LookAroundGoal(this));
        this.targetSelector.addGoal(1, new RevengeGoal(this, false));
        this.targetSelector.addGoal(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
    }

    @Override
    protected void registerSyncedData() {
        super.registerSyncedData();
        this.getSyncedData().register(21, (byte)0);
    }

    @Override
    protected String getAmbientSound() {
        return null;
    }

    @Override
    protected String getHurtSound() {
        return null;
    }

    @Override
    protected String getDeathSound() {
        return null;
    }

    public void setAgressive(boolean agressive) {
        this.getSyncedData().update(21, Byte.valueOf((byte)(agressive ? 1 : 0)));
    }

    public boolean isAgressive() {
        return this.getSyncedData().getByte(21) == 1;
    }

    @Override
    protected void initAttributes() {
        super.initAttributes();
        this.getAttribute(EntityAttributes.MAX_HEALTH).setBase(26.0);
        this.getAttribute(EntityAttributes.MOVEMENT_SPEED).setBase(0.25);
    }

    @Override
    public void mobTick() {
        if (!this.world.isClient) {
            if (this.isAgressive()) {
                if (this.drinkTimeLeft-- <= 0) {
                    this.setAgressive(false);
                    ItemStack itemstack = this.getDisplayItemInHand();
                    this.setEquipment(0, null);
                    if (itemstack != null && itemstack.getItem() == Items.POTION) {
                        List<StatusEffectInstance> list = Items.POTION.getPotionEffects(itemstack);
                        if (list != null) {
                            for (StatusEffectInstance statuseffectinstance : list) {
                                this.addStatusEffect(new StatusEffectInstance(statuseffectinstance));
                            }
                        }
                    }

                    this.getAttribute(EntityAttributes.MOVEMENT_SPEED).removeModifier(DRINKING_SPEED_PENALTY);
                }
            } else {
                int i = -1;
                if (this.random.nextFloat() < 0.15F && this.isSubmergedIn(Material.WATER) && !this.hasStatusEffect(StatusEffect.WATER_BREATHING)) {
                    i = 8237;
                } else if (this.random.nextFloat() < 0.15F && this.isOnFire() && !this.hasStatusEffect(StatusEffect.FIRE_RESISTANCE)) {
                    i = 16307;
                } else if (this.random.nextFloat() < 0.05F && this.getHealth() < this.getMaxHealth()) {
                    i = 16341;
                } else if (this.random.nextFloat() < 0.25F
                    && this.getAttackTarget() != null
                    && !this.hasStatusEffect(StatusEffect.SPEED)
                    && this.getAttackTarget().squaredDistanceTo(this) > 121.0) {
                    i = 16274;
                } else if (this.random.nextFloat() < 0.25F
                    && this.getAttackTarget() != null
                    && !this.hasStatusEffect(StatusEffect.SPEED)
                    && this.getAttackTarget().squaredDistanceTo(this) > 121.0) {
                    i = 16274;
                }

                if (i > -1) {
                    this.setEquipment(0, new ItemStack(Items.POTION, 1, i));
                    this.drinkTimeLeft = this.getDisplayItemInHand().getUseDuration();
                    this.setAgressive(true);
                    EntityAttributeInstance entityattributeinstance = this.getAttribute(EntityAttributes.MOVEMENT_SPEED);
                    entityattributeinstance.removeModifier(DRINKING_SPEED_PENALTY);
                    entityattributeinstance.addModifier(DRINKING_SPEED_PENALTY);
                }
            }

            if (this.random.nextFloat() < 7.5E-4F) {
                this.world.doEntityEvent(this, (byte)15);
            }
        }

        super.mobTick();
    }

    @Override
    public void doEvent(byte event) {
        if (event == 15) {
            for (int i = 0; i < this.random.nextInt(35) + 10; i++) {
                this.world
                    .addParticle(
                        ParticleType.SPELL_WITCH,
                        this.x + this.random.nextGaussian() * 0.13F,
                        this.getShape().maxY + 0.5 + this.random.nextGaussian() * 0.13F,
                        this.z + this.random.nextGaussian() * 0.13F,
                        0.0,
                        0.0,
                        0.0
                    );
            }
        } else {
            super.doEvent(event);
        }
    }

    @Override
    protected float getDamageAfterEffectsAndEnchantments(DamageSource source, float damage) {
        damage = super.getDamageAfterEffectsAndEnchantments(source, damage);
        if (source.getAttacker() == this) {
            damage = 0.0F;
        }

        if (source.getMagic()) {
            damage = (float)(damage * 0.15);
        }

        return damage;
    }

    @Override
    protected void dropItems(boolean loot, int lootingMultiplier) {
        int i = this.random.nextInt(3) + 1;

        for (int j = 0; j < i; j++) {
            int k = this.random.nextInt(3);
            Item item = LOOT[this.random.nextInt(LOOT.length)];
            if (lootingMultiplier > 0) {
                k += this.random.nextInt(lootingMultiplier + 1);
            }

            for (int l = 0; l < k; l++) {
                this.dropItem(item, 1);
            }
        }
    }

    @Override
    public void doRangedAttack(LivingEntity target, float range) {
        if (!this.isAgressive()) {
            PotionEntity potionentity = new PotionEntity(this.world, this, 32732);
            double d0 = target.y + target.getEyeHeight() - 1.1F;
            potionentity.pitch -= -20.0F;
            double d1 = target.x + target.velocityX - this.x;
            double d2 = d0 - this.y;
            double d3 = target.z + target.velocityZ - this.z;
            float f = MathHelper.sqrt(d1 * d1 + d3 * d3);
            if (f >= 8.0F && !target.hasStatusEffect(StatusEffect.SLOWNESS)) {
                potionentity.setPotionValue(32698);
            } else if (target.getHealth() >= 8.0F && !target.hasStatusEffect(StatusEffect.POISON)) {
                potionentity.setPotionValue(32660);
            } else if (f <= 3.0F && !target.hasStatusEffect(StatusEffect.WEAKNESS) && this.random.nextFloat() < 0.25F) {
                potionentity.setPotionValue(32696);
            }

            potionentity.dispense(d1, d2 + f * 0.2F, d3, 0.75F, 8.0F);
            this.world.addEntity(potionentity);
        }
    }

    @Override
    public float getEyeHeight() {
        return 1.62F;
    }
}
