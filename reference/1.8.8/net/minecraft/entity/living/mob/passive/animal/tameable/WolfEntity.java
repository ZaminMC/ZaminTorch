package net.minecraft.entity.living.mob.passive.animal.tameable;

import com.google.common.base.Predicate;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.AnimalBreedGoal;
import net.minecraft.entity.ai.goal.AttackWithOwnerGoal;
import net.minecraft.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.OwnerHurtGoal;
import net.minecraft.entity.ai.goal.PounceAtTargetGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.UntamedActiveTargetGoal;
import net.minecraft.entity.ai.goal.WanderAroundGoal;
import net.minecraft.entity.ai.goal.WolfBegGoal;
import net.minecraft.entity.ai.pathing.GroundPathNavigation;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.attribute.EntityAttributes;
import net.minecraft.entity.living.mob.monster.CreeperEntity;
import net.minecraft.entity.living.mob.monster.GhastEntity;
import net.minecraft.entity.living.mob.monster.SkeletonEntity;
import net.minecraft.entity.living.mob.passive.PassiveEntity;
import net.minecraft.entity.living.mob.passive.animal.AnimalEntity;
import net.minecraft.entity.living.mob.passive.animal.HorseBaseEntity;
import net.minecraft.entity.living.mob.passive.animal.RabbitEntity;
import net.minecraft.entity.living.mob.passive.animal.SheepEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.DyeColor;
import net.minecraft.item.FoodItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public class WolfEntity extends TameableEntity {
    private float headRollAngle;
    private float lastHeadRollAngle;
    private boolean wet;
    private boolean shaking;
    private float shakeProgress;
    private float lastShakeProgress;

    public WolfEntity(World world) {
        super(world);
        this.setSize(0.6F, 0.8F);
        ((GroundPathNavigation)this.getNavigation()).setCanSwim(true);
        this.goalSelector.addGoal(1, new SwimGoal(this));
        this.goalSelector.addGoal(2, this.sitGoal);
        this.goalSelector.addGoal(3, new PounceAtTargetGoal(this, 0.4F));
        this.goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.0, true));
        this.goalSelector.addGoal(5, new FollowOwnerGoal(this, 1.0, 10.0F, 2.0F));
        this.goalSelector.addGoal(6, new AnimalBreedGoal(this, 1.0));
        this.goalSelector.addGoal(7, new WanderAroundGoal(this, 1.0));
        this.goalSelector.addGoal(8, new WolfBegGoal(this, 8.0F));
        this.goalSelector.addGoal(9, new LookAtEntityGoal(this, PlayerEntity.class, 8.0F));
        this.goalSelector.addGoal(9, new LookAroundGoal(this));
        this.targetSelector.addGoal(1, new AttackWithOwnerGoal(this));
        this.targetSelector.addGoal(2, new OwnerHurtGoal(this));
        this.targetSelector.addGoal(3, new RevengeGoal(this, true));
        this.targetSelector.addGoal(4, new UntamedActiveTargetGoal<>(this, AnimalEntity.class, false, new Predicate<Entity>() {
            public boolean apply(Entity entity) {
                return entity instanceof SheepEntity || entity instanceof RabbitEntity;
            }
        }));
        this.targetSelector.addGoal(5, new ActiveTargetGoal<>(this, SkeletonEntity.class, false));
        this.setTamed(false);
    }

    @Override
    protected void initAttributes() {
        super.initAttributes();
        this.getAttribute(EntityAttributes.MOVEMENT_SPEED).setBase(0.3F);
        if (this.isTamed()) {
            this.getAttribute(EntityAttributes.MAX_HEALTH).setBase(20.0);
        } else {
            this.getAttribute(EntityAttributes.MAX_HEALTH).setBase(8.0);
        }

        this.getAttributes().register(EntityAttributes.ATTACK_DAMAGE);
        this.getAttribute(EntityAttributes.ATTACK_DAMAGE).setBase(2.0);
    }

    @Override
    public void setAttackTarget(LivingEntity target) {
        super.setAttackTarget(target);
        if (target == null) {
            this.setAngry(false);
        } else if (!this.isTamed()) {
            this.setAngry(true);
        }
    }

    @Override
    protected void mobAiTick() {
        this.syncedData.update(18, this.getHealth());
    }

    @Override
    protected void registerSyncedData() {
        super.registerSyncedData();
        this.syncedData.register(18, new Float(this.getHealth()));
        this.syncedData.register(19, new Byte((byte)0));
        this.syncedData.register(20, new Byte((byte)DyeColor.RED.getId()));
    }

    @Override
    protected void playStepSound(BlockPos pos, Block block) {
        this.playSound("mob.wolf.step", 0.15F, 1.0F);
    }

    @Override
    public void writeCustomNbt(NbtCompound nbt) {
        super.writeCustomNbt(nbt);
        nbt.putBoolean("Angry", this.isAngry());
        nbt.putByte("CollarColor", (byte)this.getCollarColor().getMetadata());
    }

    @Override
    public void readCustomNbt(NbtCompound nbt) {
        super.readCustomNbt(nbt);
        this.setAngry(nbt.getBoolean("Angry"));
        if (nbt.contains("CollarColor", 99)) {
            this.setCollarColor(DyeColor.byMetadata(nbt.getByte("CollarColor")));
        }
    }

    @Override
    protected String getAmbientSound() {
        if (this.isAngry()) {
            return "mob.wolf.growl";
        } else if (this.random.nextInt(3) == 0) {
            return this.isTamed() && this.syncedData.getFloat(18) < 10.0F ? "mob.wolf.whine" : "mob.wolf.panting";
        } else {
            return "mob.wolf.bark";
        }
    }

    @Override
    protected String getHurtSound() {
        return "mob.wolf.hurt";
    }

    @Override
    protected String getDeathSound() {
        return "mob.wolf.death";
    }

    @Override
    protected float getSoundVolume() {
        return 0.4F;
    }

    @Override
    protected Item getDropItem() {
        return Item.byId(-1);
    }

    @Override
    public void mobTick() {
        super.mobTick();
        if (!this.world.isClient && this.wet && !this.shaking && !this.isNavigating() && this.onGround) {
            this.shaking = true;
            this.shakeProgress = 0.0F;
            this.lastShakeProgress = 0.0F;
            this.world.doEntityEvent(this, (byte)8);
        }

        if (!this.world.isClient && this.getAttackTarget() == null && this.isAngry()) {
            this.setAngry(false);
        }
    }

    @Override
    public void tick() {
        super.tick();
        this.lastHeadRollAngle = this.headRollAngle;
        if (this.isBegging()) {
            this.headRollAngle = this.headRollAngle + (1.0F - this.headRollAngle) * 0.4F;
        } else {
            this.headRollAngle = this.headRollAngle + (0.0F - this.headRollAngle) * 0.4F;
        }

        if (this.isInWaterOrRain()) {
            this.wet = true;
            this.shaking = false;
            this.shakeProgress = 0.0F;
            this.lastShakeProgress = 0.0F;
        } else if ((this.wet || this.shaking) && this.shaking) {
            if (this.shakeProgress == 0.0F) {
                this.playSound("mob.wolf.shake", this.getSoundVolume(), (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
            }

            this.lastShakeProgress = this.shakeProgress;
            this.shakeProgress += 0.05F;
            if (this.lastShakeProgress >= 2.0F) {
                this.wet = false;
                this.shaking = false;
                this.lastShakeProgress = 0.0F;
                this.shakeProgress = 0.0F;
            }

            if (this.shakeProgress > 0.4F) {
                float f = (float)this.getShape().minY;
                int i = (int)(MathHelper.sin((this.shakeProgress - 0.4F) * (float) Math.PI) * 7.0F);

                for (int j = 0; j < i; j++) {
                    float f1 = (this.random.nextFloat() * 2.0F - 1.0F) * this.width * 0.5F;
                    float f2 = (this.random.nextFloat() * 2.0F - 1.0F) * this.width * 0.5F;
                    this.world.addParticle(ParticleType.WATER_SPLASH, this.x + f1, f + 0.8F, this.z + f2, this.velocityX, this.velocityY, this.velocityZ);
                }
            }
        }
    }

    public boolean isWet() {
        return this.wet;
    }

    public float getShakeProgress(float tickDelta) {
        return 0.75F + (this.lastShakeProgress + (this.shakeProgress - this.lastShakeProgress) * tickDelta) / 2.0F * 0.25F;
    }

    public float getShakeAngle(float tickdelta, float offset) {
        float f = (this.lastShakeProgress + (this.shakeProgress - this.lastShakeProgress) * tickdelta + offset) / 1.8F;
        if (f < 0.0F) {
            f = 0.0F;
        } else if (f > 1.0F) {
            f = 1.0F;
        }

        return MathHelper.sin(f * (float) Math.PI) * MathHelper.sin(f * (float) Math.PI * 11.0F) * 0.15F * (float) Math.PI;
    }

    public float getHeadRollAngle(float tickDelta) {
        return (this.lastHeadRollAngle + (this.headRollAngle - this.lastHeadRollAngle) * tickDelta) * 0.15F * (float) Math.PI;
    }

    @Override
    public float getEyeHeight() {
        return this.height * 0.8F;
    }

    @Override
    public int getLookPitchSpeed() {
        return this.isSitting() ? 20 : super.getLookPitchSpeed();
    }

    @Override
    public boolean takeDamage(DamageSource source, float amount) {
        if (this.isInvulnerable(source)) {
            return false;
        }

        Entity entity = source.getAttacker();
        this.sitGoal.setEnabledWithOwner(false);
        if (entity != null && !(entity instanceof PlayerEntity) && !(entity instanceof ArrowEntity)) {
            amount = (amount + 1.0F) / 2.0F;
        }

        return super.takeDamage(source, amount);
    }

    @Override
    public boolean tryDamage(Entity target) {
        boolean flag = target.takeDamage(DamageSource.mob(this), (int)this.getAttribute(EntityAttributes.ATTACK_DAMAGE).get());
        if (flag) {
            this.damageEntity(this, target);
        }

        return flag;
    }

    @Override
    public void setTamed(boolean tamed) {
        super.setTamed(tamed);
        if (tamed) {
            this.getAttribute(EntityAttributes.MAX_HEALTH).setBase(20.0);
        } else {
            this.getAttribute(EntityAttributes.MAX_HEALTH).setBase(8.0);
        }

        this.getAttribute(EntityAttributes.ATTACK_DAMAGE).setBase(4.0);
    }

    @Override
    public boolean interactMob(PlayerEntity player) {
        ItemStack itemstack = player.inventory.getSelectedItem();
        if (this.isTamed()) {
            if (itemstack != null) {
                if (itemstack.getItem() instanceof FoodItem) {
                    FoodItem fooditem = (FoodItem)itemstack.getItem();
                    if (fooditem.canBeCooked() && this.syncedData.getFloat(18) < 20.0F) {
                        if (!player.abilities.creativeMode) {
                            itemstack.size--;
                        }

                        this.heal(fooditem.getHungerPoints(itemstack));
                        if (itemstack.size <= 0) {
                            player.inventory.setItem(player.inventory.selectedSlot, null);
                        }

                        return true;
                    }
                } else if (itemstack.getItem() == Items.DYE) {
                    DyeColor dyecolor = DyeColor.byMetadata(itemstack.getMetadata());
                    if (dyecolor != this.getCollarColor()) {
                        this.setCollarColor(dyecolor);
                        if (!player.abilities.creativeMode && --itemstack.size <= 0) {
                            player.inventory.setItem(player.inventory.selectedSlot, null);
                        }

                        return true;
                    }
                }
            }

            if (this.isOwner(player) && !this.world.isClient && !this.isBreedingItem(itemstack)) {
                this.sitGoal.setEnabledWithOwner(!this.isSitting());
                this.jumping = false;
                this.entityNavigation.stop();
                this.setAttackTarget(null);
            }
        } else if (itemstack != null && itemstack.getItem() == Items.BONE && !this.isAngry()) {
            if (!player.abilities.creativeMode) {
                itemstack.size--;
            }

            if (itemstack.size <= 0) {
                player.inventory.setItem(player.inventory.selectedSlot, null);
            }

            if (!this.world.isClient) {
                if (this.random.nextInt(3) == 0) {
                    this.setTamed(true);
                    this.entityNavigation.stop();
                    this.setAttackTarget(null);
                    this.sitGoal.setEnabledWithOwner(true);
                    this.setHealth(20.0F);
                    this.setOwnerName(player.getUuid().toString());
                    this.addTamingParticles(true);
                    this.world.doEntityEvent(this, (byte)7);
                } else {
                    this.addTamingParticles(false);
                    this.world.doEntityEvent(this, (byte)6);
                }
            }

            return true;
        }

        return super.interactMob(player);
    }

    @Override
    public void doEvent(byte event) {
        if (event == 8) {
            this.shaking = true;
            this.shakeProgress = 0.0F;
            this.lastShakeProgress = 0.0F;
        } else {
            super.doEvent(event);
        }
    }

    public float getTailBob() {
        if (this.isAngry()) {
            return 1.5393804F;
        } else {
            return this.isTamed() ? (0.55F - (20.0F - this.syncedData.getFloat(18)) * 0.02F) * (float) Math.PI : (float) (Math.PI / 5);
        }
    }

    @Override
    public boolean isBreedingItem(ItemStack item) {
        return item != null && item.getItem() instanceof FoodItem && ((FoodItem)item.getItem()).canBeCooked();
    }

    @Override
    public int getLimitPerChunk() {
        return 8;
    }

    public boolean isAngry() {
        return (this.syncedData.getByte(16) & 2) != 0;
    }

    public void setAngry(boolean angry) {
        byte b0 = this.syncedData.getByte(16);
        if (angry) {
            this.syncedData.update(16, (byte)(b0 | 2));
        } else {
            this.syncedData.update(16, (byte)(b0 & -3));
        }
    }

    public DyeColor getCollarColor() {
        return DyeColor.byMetadata(this.syncedData.getByte(20) & 15);
    }

    public void setCollarColor(DyeColor color) {
        this.syncedData.update(20, (byte)(color.getMetadata() & 15));
    }

    public WolfEntity makeChild(PassiveEntity passiveEntity) {
        WolfEntity wolfentity = new WolfEntity(this.world);
        String s = this.getOwnerName();
        if (s != null && s.trim().length() > 0) {
            wolfentity.setOwnerName(s);
            wolfentity.setTamed(true);
        }

        return wolfentity;
    }

    public void setBegging(boolean begging) {
        if (begging) {
            this.syncedData.update(19, (byte)1);
        } else {
            this.syncedData.update(19, (byte)0);
        }
    }

    @Override
    public boolean canBreedWith(AnimalEntity other) {
        if (other == this) {
            return false;
        }

        if (!this.isTamed()) {
            return false;
        }

        if (!(other instanceof WolfEntity)) {
            return false;
        }

        WolfEntity wolfentity = (WolfEntity)other;
        return wolfentity.isTamed() && !wolfentity.isSitting() && this.isInLove() && wolfentity.isInLove();
    }

    public boolean isBegging() {
        return this.syncedData.getByte(19) == 1;
    }

    @Override
    protected boolean canDespawn() {
        return !this.isTamed() && this.ticks > 2400;
    }

    @Override
    public boolean shouldAttack(LivingEntity attackedEntity, LivingEntity attacker) {
        if (!(attackedEntity instanceof CreeperEntity) && !(attackedEntity instanceof GhastEntity)) {
            if (attackedEntity instanceof WolfEntity) {
                WolfEntity wolfentity = (WolfEntity)attackedEntity;
                if (wolfentity.isTamed() && wolfentity.getOwner() == attacker) {
                    return false;
                }
            }

            return (
                    !(attackedEntity instanceof PlayerEntity)
                        || !(attacker instanceof PlayerEntity)
                        || ((PlayerEntity)attacker).canAttack((PlayerEntity)attackedEntity)
                )
                && (!(attackedEntity instanceof HorseBaseEntity) || !((HorseBaseEntity)attackedEntity).isTame());
        } else {
            return false;
        }
    }

    @Override
    public boolean isTameable() {
        return !this.isAngry() && super.isTameable();
    }
}
