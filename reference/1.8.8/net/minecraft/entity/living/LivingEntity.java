package net.minecraft.entity.living;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Maps;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockState;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityFilter;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.MobType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTracker;
import net.minecraft.entity.living.attribute.AbstractEntityAttributeContainer;
import net.minecraft.entity.living.attribute.AttributeModifier;
import net.minecraft.entity.living.attribute.EntityAttribute;
import net.minecraft.entity.living.attribute.EntityAttributeContainer;
import net.minecraft.entity.living.attribute.EntityAttributeInstance;
import net.minecraft.entity.living.attribute.EntityAttributes;
import net.minecraft.entity.living.effect.PotionHelper;
import net.minecraft.entity.living.effect.StatusEffect;
import net.minecraft.entity.living.effect.StatusEffectInstance;
import net.minecraft.entity.living.mob.passive.animal.tameable.WolfEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtShort;
import net.minecraft.network.packet.s2c.play.EntityAnimationS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityEquipmentS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityPickupS2CPacket;
import net.minecraft.scoreboard.team.AbstractTeam;
import net.minecraft.server.EntityMap;
import net.minecraft.server.entity.living.player.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public abstract class LivingEntity extends Entity {
    private static final UUID SPRINTING_SPEED_MODIFIER_ID = UUID.fromString("662A6B8D-DA3E-4C1C-8813-96EA6097278D");
    private static final AttributeModifier SPRINTING_SPEED_MODIFIER = new AttributeModifier(SPRINTING_SPEED_MODIFIER_ID, "Sprinting speed boost", 0.3F, 2)
        .setSerialized(false);
    private AbstractEntityAttributeContainer attributes;
    private final DamageTracker damageTracker = new DamageTracker(this);
    private final Map<Integer, StatusEffectInstance> statusEffects = Maps.newHashMap();
    private final ItemStack[] itemsInHands = new ItemStack[5];
    public boolean armSwinging;
    public int armSwingingTicks;
    public int arrowDespawnTimer;
    public int damagedTimer;
    public int damagedTime;
    public float damagedSwingDir;
    public int deathTicks;
    public float lastAttackAnimationProgress;
    public float attackAnimationProgress;
    public float lastWalkAnimationSpeed;
    public float walkAnimationSpeed;
    public float walkAnimationProgress;
    /**
     * The number of ticks after taking damage this mob is invulnerable to more damage.
     */
    public int invulnerableTicks = 20;
    public float lastTilt;
    public float tilt;
    public float f_6462608;
    public float f_8068498;
    public float bodyYaw;
    public float lastBodyYaw;
    public float headYaw;
    public float lastHeadYaw;
    public float flyingSpeed = 0.02F;
    protected PlayerEntity attackingPlayer;
    protected int playerHitTimer;
    protected boolean dead;
    /**
     * A timer that keeps track of how long this mob has been far away from any players.
     * After a long enough time far from any players, this mob could randomly despawn.
     */
    protected int farFromPlayerTicks;
    protected float lastWalkProgress;
    protected float walkProgress;
    protected float totalWalkDistance;
    protected float lastTotalWalkDistance;
    protected float rotationOffset;
    protected int score;
    protected float lastDamageTaken;
    protected boolean jumping;
    public float sidewaysSpeed;
    public float forwardSpeed;
    protected float rotationSpeed;
    protected int lerpSteps;
    protected double lerpX;
    protected double lerpY;
    protected double lerpZ;
    protected double lerpYaw;
    protected double lerpPitch;
    private boolean effectsChanged = true;
    private LivingEntity attacker;
    private int lastAttackedTime;
    /**
     * The last mob this entity attacked.
     */
    private LivingEntity lastAttackedMob;
    private int lastAttackTime;
    private float speed;
    private int jumpingCooldown;
    private float absorption;

    @Override
    public void discard() {
        this.takeDamage(DamageSource.OUT_OF_WORLD, Float.MAX_VALUE);
    }

    public LivingEntity(World world) {
        super(world);
        this.initAttributes();
        this.setHealth(this.getMaxHealth());
        this.blocksBuilding = true;
        this.f_8068498 = (float)((Math.random() + 1.0) * 0.01F);
        this.setPosition(this.x, this.y, this.z);
        this.f_6462608 = (float)Math.random() * 12398.0F;
        this.yaw = (float)(Math.random() * (float) Math.PI * 2.0);
        this.headYaw = this.yaw;
        this.stepHeight = 0.6F;
    }

    @Override
    protected void registerSyncedData() {
        this.syncedData.register(7, 0);
        this.syncedData.register(8, (byte)0);
        this.syncedData.register(9, (byte)0);
        this.syncedData.register(6, 1.0F);
    }

    protected void initAttributes() {
        this.getAttributes().register(EntityAttributes.MAX_HEALTH);
        this.getAttributes().register(EntityAttributes.KNOCKBACK_RESISTANCE);
        this.getAttributes().register(EntityAttributes.MOVEMENT_SPEED);
    }

    @Override
    protected void checkFallDamage(double dy, boolean landed, Block block, BlockPos pos) {
        if (!this.isInWater()) {
            this.checkWaterCollisions();
        }

        if (!this.world.isClient && this.fallDistance > 3.0F && landed) {
            BlockState blockstate = this.world.getBlockState(pos);
            Block blockx = blockstate.getBlock();
            float f = MathHelper.ceil(this.fallDistance - 3.0F);
            if (blockx.getMaterial() != Material.AIR) {
                double d0 = Math.min(0.2F + f / 15.0F, 10.0F);
                if (d0 > 2.5) {
                    d0 = 2.5;
                }

                int i = (int)(150.0 * d0);
                ((ServerWorld)this.world).addParticle(ParticleType.BLOCK_DUST, this.x, this.y, this.z, i, 0.0, 0.0, 0.0, 0.15F, Block.serialize(blockstate));
            }
        }

        super.checkFallDamage(dy, landed, block, pos);
    }

    public boolean canBreatheUnderwater() {
        return false;
    }

    @Override
    public void baseTick() {
        this.lastAttackAnimationProgress = this.attackAnimationProgress;
        super.baseTick();
        this.world.profiler.push("livingEntityBaseTick");
        boolean flag = this instanceof PlayerEntity;
        if (this.isAlive()) {
            if (this.isInWall()) {
                this.takeDamage(DamageSource.IN_WALL, 1.0F);
            } else if (flag && !this.world.getWorldBorder().contains(this.getShape())) {
                double d0 = this.world.getWorldBorder().getDistanceFrom(this) + this.world.getWorldBorder().getSafeZone();
                if (d0 < 0.0) {
                    this.takeDamage(DamageSource.IN_WALL, Math.max(1, MathHelper.floor(-d0 * this.world.getWorldBorder().getDamagePerBlock())));
                }
            }
        }

        if (this.isImmuneToFire() || this.world.isClient) {
            this.extinguish();
        }

        boolean flag1 = flag && ((PlayerEntity)this).abilities.invulnerable;
        if (this.isAlive()) {
            if (this.isSubmergedIn(Material.WATER)) {
                if (!this.canBreatheUnderwater() && !this.hasStatusEffect(StatusEffect.WATER_BREATHING.id) && !flag1) {
                    this.setBreath(this.updateBreathUnderwater(this.getBreath()));
                    if (this.getBreath() == -20) {
                        this.setBreath(0);

                        for (int i = 0; i < 8; i++) {
                            float f = this.random.nextFloat() - this.random.nextFloat();
                            float f1 = this.random.nextFloat() - this.random.nextFloat();
                            float f2 = this.random.nextFloat() - this.random.nextFloat();
                            this.world
                                .addParticle(ParticleType.WATER_BUBBLE, this.x + f, this.y + f1, this.z + f2, this.velocityX, this.velocityY, this.velocityZ);
                        }

                        this.takeDamage(DamageSource.DROWN, 2.0F);
                    }
                }

                if (!this.world.isClient && this.isRiding() && this.vehicle instanceof LivingEntity) {
                    this.startRiding(null);
                }
            } else {
                this.setBreath(300);
            }
        }

        if (this.isAlive() && this.isInWaterOrRain()) {
            this.extinguish();
        }

        this.lastTilt = this.tilt;
        if (this.damagedTimer > 0) {
            this.damagedTimer--;
        }

        if (this.invulnerableTimer > 0 && !(this instanceof ServerPlayerEntity)) {
            this.invulnerableTimer--;
        }

        if (this.getHealth() <= 0.0F) {
            this.tickPostDeath();
        }

        if (this.playerHitTimer > 0) {
            this.playerHitTimer--;
        } else {
            this.attackingPlayer = null;
        }

        if (this.lastAttackedMob != null && !this.lastAttackedMob.isAlive()) {
            this.lastAttackedMob = null;
        }

        if (this.attacker != null) {
            if (!this.attacker.isAlive()) {
                this.setAttacker(null);
            } else if (this.ticks - this.lastAttackedTime > 100) {
                this.setAttacker(null);
            }
        }

        this.tickStatusEffects();
        this.lastTotalWalkDistance = this.totalWalkDistance;
        this.lastBodyYaw = this.bodyYaw;
        this.lastHeadYaw = this.headYaw;
        this.lastYaw = this.yaw;
        this.lastPitch = this.pitch;
        this.world.profiler.pop();
    }

    public boolean isBaby() {
        return false;
    }

    /**
     * This method is called 20 times from {@link net.minecraft.entity.living.LivingEntity#baseTick baseTick}
     * after the entity has died (when health is 0), as the death animation lasts for 20 ticks.
     * In the 20th tick/call it marks the entity as removed.
     */
    protected void tickPostDeath() {
        this.deathTicks++;
        if (this.deathTicks == 20) {
            if (!this.world.isClient
                && (this.playerHitTimer > 0 || this.shouldDropXp())
                && this.isGrownUp()
                && this.world.getGameRules().getBoolean("doMobLoot")) {
                int i = this.getXpDrop(this.attackingPlayer);

                while (i > 0) {
                    int j = ExperienceOrbEntity.roundSize(i);
                    i -= j;
                    this.world.addEntity(new ExperienceOrbEntity(this.world, this.x, this.y, this.z, j));
                }
            }

            this.remove();

            for (int k = 0; k < 20; k++) {
                double d2 = this.random.nextGaussian() * 0.02;
                double d0 = this.random.nextGaussian() * 0.02;
                double d1 = this.random.nextGaussian() * 0.02;
                this.world
                    .addParticle(
                        ParticleType.EXPLOSION_NORMAL,
                        this.x + this.random.nextFloat() * this.width * 2.0F - this.width,
                        this.y + this.random.nextFloat() * this.height,
                        this.z + this.random.nextFloat() * this.width * 2.0F - this.width,
                        d2,
                        d0,
                        d1
                    );
            }
        }
    }

    protected boolean isGrownUp() {
        return !this.isBaby();
    }

    protected int updateBreathUnderwater(int breath) {
        int i = EnchantmentHelper.getRespirationLevel(this);
        return i > 0 && this.random.nextInt(i + 1) > 0 ? breath : breath - 1;
    }

    protected int getXpDrop(PlayerEntity playerEntity) {
        return 0;
    }

    protected boolean shouldDropXp() {
        return false;
    }

    public Random getRandom() {
        return this.random;
    }

    public LivingEntity getAttacker() {
        return this.attacker;
    }

    public int getLastAttackedTime() {
        return this.lastAttackedTime;
    }

    public void setAttacker(LivingEntity attacker) {
        this.attacker = attacker;
        this.lastAttackedTime = this.ticks;
    }

    public LivingEntity getLastAttackedMob() {
        return this.lastAttackedMob;
    }

    public int getLastAttackTime() {
        return this.lastAttackTime;
    }

    public void setLastAttackedMob(Entity entity) {
        if (entity instanceof LivingEntity) {
            this.lastAttackedMob = (LivingEntity)entity;
        } else {
            this.lastAttackedMob = null;
        }

        this.lastAttackTime = this.ticks;
    }

    public int getDespawnTimer() {
        return this.farFromPlayerTicks;
    }

    @Override
    public void writeCustomNbt(NbtCompound nbt) {
        nbt.putFloat("HealF", this.getHealth());
        nbt.putShort("Health", (short)Math.ceil(this.getHealth()));
        nbt.putShort("HurtTime", (short)this.damagedTimer);
        nbt.putInt("HurtByTimestamp", this.lastAttackedTime);
        nbt.putShort("DeathTime", (short)this.deathTicks);
        nbt.putFloat("AbsorptionAmount", this.getAbsorption());

        for (ItemStack itemstack : this.getEquipment()) {
            if (itemstack != null) {
                this.attributes.removeModifiers(itemstack.getAttributeModifiers());
            }
        }

        nbt.put("Attributes", EntityAttributes.toNbt(this.getAttributes()));

        for (ItemStack itemstack1 : this.getEquipment()) {
            if (itemstack1 != null) {
                this.attributes.addModifiers(itemstack1.getAttributeModifiers());
            }
        }

        if (!this.statusEffects.isEmpty()) {
            NbtList nbtlist = new NbtList();

            for (StatusEffectInstance statuseffectinstance : this.statusEffects.values()) {
                nbtlist.addElement(statuseffectinstance.toNbt(new NbtCompound()));
            }

            nbt.put("ActiveEffects", nbtlist);
        }
    }

    @Override
    public void readCustomNbt(NbtCompound nbt) {
        this.setAbsorption(nbt.getFloat("AbsorptionAmount"));
        if (nbt.contains("Attributes", 9) && this.world != null && !this.world.isClient) {
            EntityAttributes.readNbt(this.getAttributes(), nbt.getList("Attributes", 10));
        }

        if (nbt.contains("ActiveEffects", 9)) {
            NbtList nbtlist = nbt.getList("ActiveEffects", 10);

            for (int i = 0; i < nbtlist.size(); i++) {
                NbtCompound nbtcompound = nbtlist.getCompound(i);
                StatusEffectInstance statuseffectinstance = StatusEffectInstance.fromNbt(nbtcompound);
                if (statuseffectinstance != null) {
                    this.statusEffects.put(statuseffectinstance.getId(), statuseffectinstance);
                }
            }
        }

        if (nbt.contains("HealF", 99)) {
            this.setHealth(nbt.getFloat("HealF"));
        } else {
            NbtElement nbtelement = nbt.get("Health");
            if (nbtelement == null) {
                this.setHealth(this.getMaxHealth());
            } else if (nbtelement.getType() == 5) {
                this.setHealth(((NbtFloat)nbtelement).getFloat());
            } else if (nbtelement.getType() == 2) {
                this.setHealth(((NbtShort)nbtelement).getShort());
            }
        }

        this.damagedTimer = nbt.getShort("HurtTime");
        this.deathTicks = nbt.getShort("DeathTime");
        this.lastAttackedTime = nbt.getInt("HurtByTimestamp");
    }

    protected void tickStatusEffects() {
        Iterator<Integer> iterator = this.statusEffects.keySet().iterator();

        while (iterator.hasNext()) {
            Integer integer = iterator.next();
            StatusEffectInstance statuseffectinstance = this.statusEffects.get(integer);
            if (!statuseffectinstance.tick(this)) {
                if (!this.world.isClient) {
                    iterator.remove();
                    this.onStatusEffectRemoved(statuseffectinstance);
                }
            } else if (statuseffectinstance.getDuration() % 600 == 0) {
                this.onStatusEffectUpgraded(statuseffectinstance, false);
            }
        }

        if (this.effectsChanged) {
            if (!this.world.isClient) {
                this.updateVisibility();
            }

            this.effectsChanged = false;
        }

        int i = this.syncedData.getInt(7);
        boolean flag1 = this.syncedData.getByte(8) > 0;
        if (i > 0) {
            boolean flag = false;
            if (!this.isInvisible()) {
                flag = this.random.nextBoolean();
            } else {
                flag = this.random.nextInt(15) == 0;
            }

            if (flag1) {
                flag &= this.random.nextInt(5) == 0;
            }

            if (flag && i > 0) {
                double d0 = (i >> 16 & 0xFF) / 255.0;
                double d1 = (i >> 8 & 0xFF) / 255.0;
                double d2 = (i >> 0 & 0xFF) / 255.0;
                this.world
                    .addParticle(
                        flag1 ? ParticleType.SPELL_MOB_AMBIENT : ParticleType.SPELL_MOB,
                        this.x + (this.random.nextDouble() - 0.5) * this.width,
                        this.y + this.random.nextDouble() * this.height,
                        this.z + (this.random.nextDouble() - 0.5) * this.width,
                        d0,
                        d1,
                        d2
                    );
            }
        }
    }

    protected void updateVisibility() {
        if (this.statusEffects.isEmpty()) {
            this.clearEffectParticles();
            this.setInvisible(false);
        } else {
            int i = PotionHelper.getColor(this.statusEffects.values());
            this.syncedData.update(8, Byte.valueOf((byte)(PotionHelper.isAllAmbient(this.statusEffects.values()) ? 1 : 0)));
            this.syncedData.update(7, i);
            this.setInvisible(this.hasStatusEffect(StatusEffect.INVISIBILITY.id));
        }
    }

    protected void clearEffectParticles() {
        this.syncedData.update(8, (byte)0);
        this.syncedData.update(7, 0);
    }

    public void clearStatusEffects() {
        Iterator<Integer> iterator = this.statusEffects.keySet().iterator();

        while (iterator.hasNext()) {
            Integer integer = iterator.next();
            StatusEffectInstance statuseffectinstance = this.statusEffects.get(integer);
            if (!this.world.isClient) {
                iterator.remove();
                this.onStatusEffectRemoved(statuseffectinstance);
            }
        }
    }

    public Collection<StatusEffectInstance> getStatusEffects() {
        return this.statusEffects.values();
    }

    public boolean hasStatusEffect(int id) {
        return this.statusEffects.containsKey(id);
    }

    public boolean hasStatusEffect(StatusEffect effect) {
        return this.statusEffects.containsKey(effect.id);
    }

    public StatusEffectInstance getEffectInstance(StatusEffect effect) {
        return this.statusEffects.get(effect.id);
    }

    public void addStatusEffect(StatusEffectInstance instance) {
        if (this.canHaveStatusEffect(instance)) {
            if (this.statusEffects.containsKey(instance.getId())) {
                this.statusEffects.get(instance.getId()).combine(instance);
                this.onStatusEffectUpgraded(this.statusEffects.get(instance.getId()), true);
            } else {
                this.statusEffects.put(instance.getId(), instance);
                this.onStatusEffectApplied(instance);
            }
        }
    }

    public boolean canHaveStatusEffect(StatusEffectInstance effect) {
        if (this.getMobType() == MobType.UNDEAD) {
            int i = effect.getId();
            if (i == StatusEffect.REGENERATION.id || i == StatusEffect.POISON.id) {
                return false;
            }
        }

        return true;
    }

    public boolean isAffectedBySmite() {
        return this.getMobType() == MobType.UNDEAD;
    }

    public void removeEffect(int id) {
        this.statusEffects.remove(id);
    }

    public void removeStatusEffect(int id) {
        StatusEffectInstance statuseffectinstance = this.statusEffects.remove(id);
        if (statuseffectinstance != null) {
            this.onStatusEffectRemoved(statuseffectinstance);
        }
    }

    protected void onStatusEffectApplied(StatusEffectInstance instance) {
        this.effectsChanged = true;
        if (!this.world.isClient) {
            StatusEffect.BY_ID[instance.getId()].addModifiers(this, this.getAttributes(), instance.getAmplifier());
        }
    }

    protected void onStatusEffectUpgraded(StatusEffectInstance instance, boolean timerRanOut) {
        this.effectsChanged = true;
        if (timerRanOut && !this.world.isClient) {
            StatusEffect.BY_ID[instance.getId()].removeModifiers(this, this.getAttributes(), instance.getAmplifier());
            StatusEffect.BY_ID[instance.getId()].addModifiers(this, this.getAttributes(), instance.getAmplifier());
        }
    }

    protected void onStatusEffectRemoved(StatusEffectInstance effect) {
        this.effectsChanged = true;
        if (!this.world.isClient) {
            StatusEffect.BY_ID[effect.getId()].removeModifiers(this, this.getAttributes(), effect.getAmplifier());
        }
    }

    public void heal(float amount) {
        float f = this.getHealth();
        if (f > 0.0F) {
            this.setHealth(f + amount);
        }
    }

    public final float getHealth() {
        return this.syncedData.getFloat(6);
    }

    public void setHealth(float amount) {
        this.syncedData.update(6, MathHelper.clamp(amount, 0.0F, this.getMaxHealth()));
    }

    @Override
    public boolean takeDamage(DamageSource source, float amount) {
        if (this.isInvulnerable(source)) {
            return false;
        }

        if (this.world.isClient) {
            return false;
        }

        this.farFromPlayerTicks = 0;
        if (this.getHealth() <= 0.0F) {
            return false;
        }

        if (source.isFire() && this.hasStatusEffect(StatusEffect.FIRE_RESISTANCE)) {
            return false;
        }

        if ((source == DamageSource.ANVIL || source == DamageSource.FALLING_BLOCK) && this.getEquipment(4) != null) {
            this.getEquipment(4).takeDamageAndBreak((int)(amount * 4.0F + this.random.nextFloat() * amount * 2.0F), this);
            amount *= 0.75F;
        }

        this.walkAnimationSpeed = 1.5F;
        boolean flag = true;
        if (this.invulnerableTimer > this.invulnerableTicks / 2.0F) {
            if (amount <= this.lastDamageTaken) {
                return false;
            }

            this.applyDamage(source, amount - this.lastDamageTaken);
            this.lastDamageTaken = amount;
            flag = false;
        } else {
            this.lastDamageTaken = amount;
            this.invulnerableTimer = this.invulnerableTicks;
            this.applyDamage(source, amount);
            this.damagedTimer = this.damagedTime = 10;
        }

        this.damagedSwingDir = 0.0F;
        Entity entity = source.getAttacker();
        if (entity != null) {
            if (entity instanceof LivingEntity) {
                this.setAttacker((LivingEntity)entity);
            }

            if (entity instanceof PlayerEntity) {
                this.playerHitTimer = 100;
                this.attackingPlayer = (PlayerEntity)entity;
            } else if (entity instanceof WolfEntity) {
                WolfEntity wolfentity = (WolfEntity)entity;
                if (wolfentity.isTamed()) {
                    this.playerHitTimer = 100;
                    this.attackingPlayer = null;
                }
            }
        }

        if (flag) {
            this.world.doEntityEvent(this, (byte)2);
            if (source != DamageSource.DROWN) {
                this.markDamaged();
            }

            if (entity != null) {
                double d1 = entity.x - this.x;

                double d0;
                for (d0 = entity.z - this.z; d1 * d1 + d0 * d0 < 1.0E-4; d0 = (Math.random() - Math.random()) * 0.01) {
                    d1 = (Math.random() - Math.random()) * 0.01;
                }

                this.damagedSwingDir = (float)(MathHelper.fastAtan2(d0, d1) * 180.0 / (float) Math.PI - this.yaw);
                this.applyKnockback(entity, amount, d1, d0);
            } else {
                this.damagedSwingDir = (int)(Math.random() * 2.0) * 180;
            }
        }

        if (this.getHealth() <= 0.0F) {
            String s = this.getDeathSound();
            if (flag && s != null) {
                this.playSound(s, this.getSoundVolume(), this.getSoundPitch());
            }

            this.die(source);
        } else {
            String s1 = this.getHurtSound();
            if (flag && s1 != null) {
                this.playSound(s1, this.getSoundVolume(), this.getSoundPitch());
            }
        }

        return true;
    }

    public void onBrokenItem(ItemStack item) {
        this.playSound("random.break", 0.8F, 0.8F + this.world.random.nextFloat() * 0.4F);

        for (int i = 0; i < 5; i++) {
            Vec3d vec3d = new Vec3d((this.random.nextFloat() - 0.5) * 0.1, Math.random() * 0.1 + 0.1, 0.0);
            vec3d = vec3d.rotateX(-this.pitch * (float) Math.PI / 180.0F);
            vec3d = vec3d.rotateY(-this.yaw * (float) Math.PI / 180.0F);
            double d0 = -this.random.nextFloat() * 0.6 - 0.3;
            Vec3d vec3d1 = new Vec3d((this.random.nextFloat() - 0.5) * 0.3, d0, 0.6);
            vec3d1 = vec3d1.rotateX(-this.pitch * (float) Math.PI / 180.0F);
            vec3d1 = vec3d1.rotateY(-this.yaw * (float) Math.PI / 180.0F);
            vec3d1 = vec3d1.add(this.x, this.y + this.getEyeHeight(), this.z);
            this.world.addParticle(ParticleType.ITEM_CRACK, vec3d1.x, vec3d1.y, vec3d1.z, vec3d.x, vec3d.y + 0.05, vec3d.z, Item.getId(item.getItem()));
        }
    }

    public void die(DamageSource source) {
        Entity entity = source.getAttacker();
        LivingEntity livingentity = this.getLastAttacker();
        if (this.score >= 0 && livingentity != null) {
            livingentity.takeKillScore(this, this.score);
        }

        if (entity != null) {
            entity.onKill(this);
        }

        this.dead = true;
        this.getDamageTracker().resetStatus();
        if (!this.world.isClient) {
            int i = 0;
            if (entity instanceof PlayerEntity) {
                i = EnchantmentHelper.getLootingLevel((LivingEntity)entity);
            }

            if (this.isGrownUp() && this.world.getGameRules().getBoolean("doMobLoot")) {
                this.dropItems(this.playerHitTimer > 0, i);
                this.dropEquipment(this.playerHitTimer > 0, i);
                if (this.playerHitTimer > 0 && this.random.nextFloat() < 0.025F + i * 0.01F) {
                    this.dropRareItem();
                }
            }
        }

        this.world.doEntityEvent(this, (byte)3);
    }

    protected void dropEquipment(boolean hitByPlayer, int lootingLevel) {
    }

    public void applyKnockback(Entity entity, float amount, double velocityX, double velocityZ) {
        if (!(this.random.nextDouble() < this.getAttribute(EntityAttributes.KNOCKBACK_RESISTANCE).get())) {
            this.velocityDirty = true;
            float f = MathHelper.sqrt(velocityX * velocityX + velocityZ * velocityZ);
            float f1 = 0.4F;
            this.velocityX /= 2.0;
            this.velocityY /= 2.0;
            this.velocityZ /= 2.0;
            this.velocityX -= velocityX / f * f1;
            this.velocityY += f1;
            this.velocityZ -= velocityZ / f * f1;
            if (this.velocityY > 0.4F) {
                this.velocityY = 0.4F;
            }
        }
    }

    protected String getHurtSound() {
        return "game.neutral.hurt";
    }

    protected String getDeathSound() {
        return "game.neutral.die";
    }

    protected void dropRareItem() {
    }

    protected void dropItems(boolean loot, int lootingMultiplier) {
    }

    public boolean isClimbing() {
        int i = MathHelper.floor(this.x);
        int j = MathHelper.floor(this.getShape().minY);
        int k = MathHelper.floor(this.z);
        Block block = this.world.getBlockState(new BlockPos(i, j, k)).getBlock();
        return (block == Blocks.LADDER || block == Blocks.VINE) && (!(this instanceof PlayerEntity) || !((PlayerEntity)this).isSpectator());
    }

    @Override
    public boolean isAlive() {
        return !this.removed && this.getHealth() > 0.0F;
    }

    @Override
    public void takeFallDamage(float distance, float damageMultiplier) {
        super.takeFallDamage(distance, damageMultiplier);
        StatusEffectInstance statuseffectinstance = this.getEffectInstance(StatusEffect.JUMP_BOOST);
        float f = statuseffectinstance != null ? statuseffectinstance.getAmplifier() + 1 : 0.0F;
        int i = MathHelper.ceil((distance - 3.0F - f) * damageMultiplier);
        if (i > 0) {
            this.playSound(this.getFallSound(i), 1.0F, 1.0F);
            this.takeDamage(DamageSource.FALL, i);
            int j = MathHelper.floor(this.x);
            int k = MathHelper.floor(this.y - 0.2F);
            int l = MathHelper.floor(this.z);
            Block block = this.world.getBlockState(new BlockPos(j, k, l)).getBlock();
            if (block.getMaterial() != Material.AIR) {
                Block.Sounds block$sounds = block.sounds;
                this.playSound(block$sounds.getStepping(), block$sounds.getVolume() * 0.5F, block$sounds.getPitch() * 0.75F);
            }
        }
    }

    protected String getFallSound(int distance) {
        return distance > 4 ? "game.neutral.hurt.fall.big" : "game.neutral.hurt.fall.small";
    }

    @Override
    public void animateDamage() {
        this.damagedTimer = this.damagedTime = 10;
        this.damagedSwingDir = 0.0F;
    }

    public int getArmorProtection() {
        int i = 0;

        for (ItemStack itemstack : this.getEquipment()) {
            if (itemstack != null && itemstack.getItem() instanceof ArmorItem) {
                int j = ((ArmorItem)itemstack.getItem()).protection;
                i += j;
            }
        }

        return i;
    }

    protected void damageArmor(float damage) {
    }

    protected float getDamageAfterArmor(DamageSource source, float damage) {
        if (!source.bypassesArmor()) {
            int i = 25 - this.getArmorProtection();
            float f = damage * i;
            this.damageArmor(damage);
            damage = f / 25.0F;
        }

        return damage;
    }

    protected float getDamageAfterEffectsAndEnchantments(DamageSource source, float damage) {
        if (source.isUnblockable()) {
            return damage;
        }

        if (this.hasStatusEffect(StatusEffect.RESISTANCE) && source != DamageSource.OUT_OF_WORLD) {
            int i = (this.getEffectInstance(StatusEffect.RESISTANCE).getAmplifier() + 1) * 5;
            int j = 25 - i;
            float f = damage * j;
            damage = f / 25.0F;
        }

        if (damage <= 0.0F) {
            return 0.0F;
        }

        int k = EnchantmentHelper.modifyProtection(this.getEquipment(), source);
        if (k > 20) {
            k = 20;
        }

        if (k > 0 && k <= 20) {
            int l = 25 - k;
            float f1 = damage * l;
            damage = f1 / 25.0F;
        }

        return damage;
    }

    protected void applyDamage(DamageSource source, float damage) {
        if (!this.isInvulnerable(source)) {
            damage = this.getDamageAfterArmor(source, damage);
            damage = this.getDamageAfterEffectsAndEnchantments(source, damage);
            float f = damage;
            damage = Math.max(damage - this.getAbsorption(), 0.0F);
            this.setAbsorption(this.getAbsorption() - (f - damage));
            if (damage != 0.0F) {
                float f1 = this.getHealth();
                this.setHealth(f1 - damage);
                this.getDamageTracker().recordDamage(source, f1, damage);
                this.setAbsorption(this.getAbsorption() - damage);
            }
        }
    }

    public DamageTracker getDamageTracker() {
        return this.damageTracker;
    }

    public LivingEntity getLastAttacker() {
        if (this.damageTracker.getInitialAttacker() != null) {
            return this.damageTracker.getInitialAttacker();
        } else if (this.attackingPlayer != null) {
            return this.attackingPlayer;
        } else {
            return this.attacker != null ? this.attacker : null;
        }
    }

    public final float getMaxHealth() {
        return (float)this.getAttribute(EntityAttributes.MAX_HEALTH).get();
    }

    public final int getStuckArrows() {
        return this.syncedData.getByte(9);
    }

    public final void setStuckArrows(int arrows) {
        this.syncedData.update(9, (byte)arrows);
    }

    private int getArmSwingDuration() {
        if (this.hasStatusEffect(StatusEffect.HASTE)) {
            return 6 - (1 + this.getEffectInstance(StatusEffect.HASTE).getAmplifier()) * 1;
        } else {
            return this.hasStatusEffect(StatusEffect.MINING_FATIGUE) ? 6 + (1 + this.getEffectInstance(StatusEffect.MINING_FATIGUE).getAmplifier()) * 2 : 6;
        }
    }

    public void swingArm() {
        if (!this.armSwinging || this.armSwingingTicks >= this.getArmSwingDuration() / 2 || this.armSwingingTicks < 0) {
            this.armSwingingTicks = -1;
            this.armSwinging = true;
            if (this.world instanceof ServerWorld) {
                ((ServerWorld)this.world).getEntityMap().sendPacket(this, new EntityAnimationS2CPacket(this, 0));
            }
        }
    }

    @Override
    public void doEvent(byte event) {
        if (event == 2) {
            this.walkAnimationSpeed = 1.5F;
            this.invulnerableTimer = this.invulnerableTicks;
            this.damagedTimer = this.damagedTime = 10;
            this.damagedSwingDir = 0.0F;
            String s = this.getHurtSound();
            if (s != null) {
                this.playSound(this.getHurtSound(), this.getSoundVolume(), (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
            }

            this.takeDamage(DamageSource.GENERIC, 0.0F);
        } else if (event == 3) {
            String s1 = this.getDeathSound();
            if (s1 != null) {
                this.playSound(this.getDeathSound(), this.getSoundVolume(), (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
            }

            this.setHealth(0.0F);
            this.die(DamageSource.GENERIC);
        } else {
            super.doEvent(event);
        }
    }

    @Override
    protected void voidTick() {
        this.takeDamage(DamageSource.OUT_OF_WORLD, 4.0F);
    }

    protected void updateArmSwing() {
        int i = this.getArmSwingDuration();
        if (this.armSwinging) {
            this.armSwingingTicks++;
            if (this.armSwingingTicks >= i) {
                this.armSwingingTicks = 0;
                this.armSwinging = false;
            }
        } else {
            this.armSwingingTicks = 0;
        }

        this.attackAnimationProgress = (float)this.armSwingingTicks / i;
    }

    public EntityAttributeInstance getAttribute(EntityAttribute attribute) {
        return this.getAttributes().get(attribute);
    }

    public AbstractEntityAttributeContainer getAttributes() {
        if (this.attributes == null) {
            this.attributes = new EntityAttributeContainer();
        }

        return this.attributes;
    }

    public MobType getMobType() {
        return MobType.UNDEFINED;
    }

    public abstract ItemStack getDisplayItemInHand();

    public abstract ItemStack getEquipment(int slot);

    public abstract ItemStack getArmor(int slot);

    @Override
    public abstract void setEquipment(int slot, ItemStack item);

    @Override
    public void setSprinting(boolean sprinting) {
        super.setSprinting(sprinting);
        EntityAttributeInstance entityattributeinstance = this.getAttribute(EntityAttributes.MOVEMENT_SPEED);
        if (entityattributeinstance.getModifier(SPRINTING_SPEED_MODIFIER_ID) != null) {
            entityattributeinstance.removeModifier(SPRINTING_SPEED_MODIFIER);
        }

        if (sprinting) {
            entityattributeinstance.addModifier(SPRINTING_SPEED_MODIFIER);
        }
    }

    @Override
    public abstract ItemStack[] getEquipment();

    protected float getSoundVolume() {
        return 1.0F;
    }

    protected float getSoundPitch() {
        return this.isBaby()
            ? (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.5F
            : (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F;
    }

    protected boolean isDead() {
        return this.getHealth() <= 0.0F;
    }

    public void dismountRider(Entity entity) {
        double d0 = entity.x;
        double d1 = entity.getShape().minY + entity.height;
        double d2 = entity.z;
        int i = 1;

        for (int j = -i; j <= i; j++) {
            for (int k = -i; k < i; k++) {
                if (j != 0 || k != 0) {
                    int l = (int)(this.x + j);
                    int i1 = (int)(this.z + k);
                    Box box = this.getShape().moved(j, 1.0, k);
                    if (this.world.getBlockCollisions(box).isEmpty()) {
                        if (World.hasSolidTop(this.world, new BlockPos(l, (int)this.y, i1))) {
                            this.teleport(this.x + j, this.y + 1.0, this.z + k);
                            return;
                        }

                        if (World.hasSolidTop(this.world, new BlockPos(l, (int)this.y - 1, i1))
                            || this.world.getBlockState(new BlockPos(l, (int)this.y - 1, i1)).getBlock().getMaterial() == Material.WATER) {
                            d0 = this.x + j;
                            d1 = this.y + 1.0;
                            d2 = this.z + k;
                        }
                    }
                }
            }
        }

        this.teleport(d0, d1, d2);
    }

    @Override
    public boolean shouldShowNameTag() {
        return false;
    }

    protected float getJumpStrength() {
        return 0.42F;
    }

    protected void jump() {
        this.velocityY = this.getJumpStrength();
        if (this.hasStatusEffect(StatusEffect.JUMP_BOOST)) {
            this.velocityY = this.velocityY + (this.getEffectInstance(StatusEffect.JUMP_BOOST).getAmplifier() + 1) * 0.1F;
        }

        if (this.isSprinting()) {
            float f = this.yaw * (float) (Math.PI / 180.0);
            this.velocityX = this.velocityX - MathHelper.sin(f) * 0.2F;
            this.velocityZ = this.velocityZ + MathHelper.cos(f) * 0.2F;
        }

        this.velocityDirty = true;
    }

    protected void jumpInWater() {
        this.velocityY += 0.04F;
    }

    protected void jumpInLava() {
        this.velocityY += 0.04F;
    }

    public void moveRelative(float sideways, float forwards) {
        if (this.isLocallyControlled()) {
            if (!this.isInWater() || this instanceof PlayerEntity && ((PlayerEntity)this).abilities.flying) {
                if (!this.isInLava() || this instanceof PlayerEntity && ((PlayerEntity)this).abilities.flying) {
                    float f4 = 0.91F;
                    if (this.onGround) {
                        f4 = this.world
                                .getBlockState(new BlockPos(MathHelper.floor(this.x), MathHelper.floor(this.getShape().minY) - 1, MathHelper.floor(this.z)))
                                .getBlock()
                                .slipperiness
                            * 0.91F;
                    }

                    float f = 0.16277136F / (f4 * f4 * f4);
                    float f5;
                    if (this.onGround) {
                        f5 = this.getSpeed() * f;
                    } else {
                        f5 = this.flyingSpeed;
                    }

                    this.updateVelocity(sideways, forwards, f5);
                    f4 = 0.91F;
                    if (this.onGround) {
                        f4 = this.world
                                .getBlockState(new BlockPos(MathHelper.floor(this.x), MathHelper.floor(this.getShape().minY) - 1, MathHelper.floor(this.z)))
                                .getBlock()
                                .slipperiness
                            * 0.91F;
                    }

                    if (this.isClimbing()) {
                        float f6 = 0.15F;
                        this.velocityX = MathHelper.clamp(this.velocityX, -f6, f6);
                        this.velocityZ = MathHelper.clamp(this.velocityZ, -f6, f6);
                        this.fallDistance = 0.0F;
                        if (this.velocityY < -0.15) {
                            this.velocityY = -0.15;
                        }

                        boolean flag = this.isSneaking() && this instanceof PlayerEntity;
                        if (flag && this.velocityY < 0.0) {
                            this.velocityY = 0.0;
                        }
                    }

                    this.move(this.velocityX, this.velocityY, this.velocityZ);
                    if (this.collidingHorizontally && this.isClimbing()) {
                        this.velocityY = 0.2;
                    }

                    if (this.world.isClient
                        && (
                            !this.world.isChunkLoaded(new BlockPos((int)this.x, 0, (int)this.z))
                                || !this.world.getChunk(new BlockPos((int)this.x, 0, (int)this.z)).isLoaded()
                        )) {
                        if (this.y > 0.0) {
                            this.velocityY = -0.1;
                        } else {
                            this.velocityY = 0.0;
                        }
                    } else {
                        this.velocityY -= 0.08;
                    }

                    this.velocityY *= 0.98F;
                    this.velocityX *= f4;
                    this.velocityZ *= f4;
                } else {
                    double d1 = this.y;
                    this.updateVelocity(sideways, forwards, 0.02F);
                    this.move(this.velocityX, this.velocityY, this.velocityZ);
                    this.velocityX *= 0.5;
                    this.velocityY *= 0.5;
                    this.velocityZ *= 0.5;
                    this.velocityY -= 0.02;
                    if (this.collidingHorizontally && this.canMove(this.velocityX, this.velocityY + 0.6F - this.y + d1, this.velocityZ)) {
                        this.velocityY = 0.3F;
                    }
                }
            } else {
                double d0 = this.y;
                float f1 = 0.8F;
                float f2 = 0.02F;
                float f3 = EnchantmentHelper.getDepthStriderLevel(this);
                if (f3 > 3.0F) {
                    f3 = 3.0F;
                }

                if (!this.onGround) {
                    f3 *= 0.5F;
                }

                if (f3 > 0.0F) {
                    f1 += (0.54600006F - f1) * f3 / 3.0F;
                    f2 += (this.getSpeed() * 1.0F - f2) * f3 / 3.0F;
                }

                this.updateVelocity(sideways, forwards, f2);
                this.move(this.velocityX, this.velocityY, this.velocityZ);
                this.velocityX *= f1;
                this.velocityY *= 0.8F;
                this.velocityZ *= f1;
                this.velocityY -= 0.02;
                if (this.collidingHorizontally && this.canMove(this.velocityX, this.velocityY + 0.6F - this.y + d0, this.velocityZ)) {
                    this.velocityY = 0.3F;
                }
            }
        }

        this.lastWalkAnimationSpeed = this.walkAnimationSpeed;
        double d2 = this.x - this.lastX;
        double d3 = this.z - this.lastZ;
        float f7 = MathHelper.sqrt(d2 * d2 + d3 * d3) * 4.0F;
        if (f7 > 1.0F) {
            f7 = 1.0F;
        }

        this.walkAnimationSpeed = this.walkAnimationSpeed + (f7 - this.walkAnimationSpeed) * 0.4F;
        this.walkAnimationProgress = this.walkAnimationProgress + this.walkAnimationSpeed;
    }

    public float getSpeed() {
        return this.speed;
    }

    public void setSpeed(float speed) {
        this.speed = speed;
    }

    public boolean tryDamage(Entity target) {
        this.setLastAttackedMob(target);
        return false;
    }

    public boolean isSleeping() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.world.isClient) {
            int i = this.getStuckArrows();
            if (i > 0) {
                if (this.arrowDespawnTimer <= 0) {
                    this.arrowDespawnTimer = 20 * (30 - i);
                }

                this.arrowDespawnTimer--;
                if (this.arrowDespawnTimer <= 0) {
                    this.setStuckArrows(i - 1);
                }
            }

            for (int j = 0; j < 5; j++) {
                ItemStack itemstack = this.itemsInHands[j];
                ItemStack itemstack1 = this.getEquipment(j);
                if (!ItemStack.matches(itemstack1, itemstack)) {
                    ((ServerWorld)this.world).getEntityMap().sendPacket(this, new EntityEquipmentS2CPacket(this.getNetworkId(), j, itemstack1));
                    if (itemstack != null) {
                        this.attributes.removeModifiers(itemstack.getAttributeModifiers());
                    }

                    if (itemstack1 != null) {
                        this.attributes.addModifiers(itemstack1.getAttributeModifiers());
                    }

                    this.itemsInHands[j] = itemstack1 == null ? null : itemstack1.copy();
                }
            }

            if (this.ticks % 20 == 0) {
                this.getDamageTracker().resetStatus();
            }
        }

        this.mobTick();
        double d0 = this.x - this.lastX;
        double d1 = this.z - this.lastZ;
        float f = (float)(d0 * d0 + d1 * d1);
        float f1 = this.bodyYaw;
        float f2 = 0.0F;
        this.lastWalkProgress = this.walkProgress;
        float f3 = 0.0F;
        if (f > 0.0025000002F) {
            f3 = 1.0F;
            f2 = (float)Math.sqrt(f) * 3.0F;
            f1 = (float)MathHelper.fastAtan2(d1, d0) * 180.0F / (float) Math.PI - 90.0F;
        }

        if (this.attackAnimationProgress > 0.0F) {
            f1 = this.yaw;
        }

        if (!this.onGround) {
            f3 = 0.0F;
        }

        this.walkProgress = this.walkProgress + (f3 - this.walkProgress) * 0.3F;
        this.world.profiler.push("headTurn");
        f2 = this.bodyMovement(f1, f2);
        this.world.profiler.pop();
        this.world.profiler.push("rangeChecks");

        while (this.yaw - this.lastYaw < -180.0F) {
            this.lastYaw -= 360.0F;
        }

        while (this.yaw - this.lastYaw >= 180.0F) {
            this.lastYaw += 360.0F;
        }

        while (this.bodyYaw - this.lastBodyYaw < -180.0F) {
            this.lastBodyYaw -= 360.0F;
        }

        while (this.bodyYaw - this.lastBodyYaw >= 180.0F) {
            this.lastBodyYaw += 360.0F;
        }

        while (this.pitch - this.lastPitch < -180.0F) {
            this.lastPitch -= 360.0F;
        }

        while (this.pitch - this.lastPitch >= 180.0F) {
            this.lastPitch += 360.0F;
        }

        while (this.headYaw - this.lastHeadYaw < -180.0F) {
            this.lastHeadYaw -= 360.0F;
        }

        while (this.headYaw - this.lastHeadYaw >= 180.0F) {
            this.lastHeadYaw += 360.0F;
        }

        this.world.profiler.pop();
        this.totalWalkDistance += f2;
    }

    protected float bodyMovement(float yaw, float movement) {
        float f = MathHelper.wrapDegrees(yaw - this.bodyYaw);
        this.bodyYaw += f * 0.3F;
        float f1 = MathHelper.wrapDegrees(this.yaw - this.bodyYaw);
        boolean flag = f1 < -90.0F || f1 >= 90.0F;
        if (f1 < -75.0F) {
            f1 = -75.0F;
        }

        if (f1 >= 75.0F) {
            f1 = 75.0F;
        }

        this.bodyYaw = this.yaw - f1;
        if (f1 * f1 > 2500.0F) {
            this.bodyYaw += f1 * 0.2F;
        }

        if (flag) {
            movement *= -1.0F;
        }

        return movement;
    }

    public void mobTick() {
        if (this.jumpingCooldown > 0) {
            this.jumpingCooldown--;
        }

        if (this.lerpSteps > 0) {
            double d0 = this.x + (this.lerpX - this.x) / this.lerpSteps;
            double d1 = this.y + (this.lerpY - this.y) / this.lerpSteps;
            double d2 = this.z + (this.lerpZ - this.z) / this.lerpSteps;
            double d3 = MathHelper.wrapDegrees(this.lerpYaw - this.yaw);
            this.yaw = (float)(this.yaw + d3 / this.lerpSteps);
            this.pitch = (float)(this.pitch + (this.lerpPitch - this.pitch) / this.lerpSteps);
            this.lerpSteps--;
            this.setPosition(d0, d1, d2);
            this.setRotation(this.yaw, this.pitch);
        } else if (!this.isLocallyControlled()) {
            this.velocityX *= 0.98;
            this.velocityY *= 0.98;
            this.velocityZ *= 0.98;
        }

        if (Math.abs(this.velocityX) < 0.005) {
            this.velocityX = 0.0;
        }

        if (Math.abs(this.velocityY) < 0.005) {
            this.velocityY = 0.0;
        }

        if (Math.abs(this.velocityZ) < 0.005) {
            this.velocityZ = 0.0;
        }

        this.world.profiler.push("ai");
        if (this.isDead()) {
            this.jumping = false;
            this.sidewaysSpeed = 0.0F;
            this.forwardSpeed = 0.0F;
            this.rotationSpeed = 0.0F;
        } else if (this.isLocallyControlled()) {
            this.world.profiler.push("newAi");
            this.serverTickAi();
            this.world.profiler.pop();
        }

        this.world.profiler.pop();
        this.world.profiler.push("jump");
        if (this.jumping) {
            if (this.isInWater()) {
                this.jumpInWater();
            } else if (this.isInLava()) {
                this.jumpInLava();
            } else if (this.onGround && this.jumpingCooldown == 0) {
                this.jump();
                this.jumpingCooldown = 10;
            }
        } else {
            this.jumpingCooldown = 0;
        }

        this.world.profiler.pop();
        this.world.profiler.push("travel");
        this.sidewaysSpeed *= 0.98F;
        this.forwardSpeed *= 0.98F;
        this.rotationSpeed *= 0.9F;
        this.moveRelative(this.sidewaysSpeed, this.forwardSpeed);
        this.world.profiler.pop();
        this.world.profiler.push("push");
        if (!this.world.isClient) {
            this.pushAwayCollidingEntities();
        }

        this.world.profiler.pop();
    }

    protected void serverTickAi() {
    }

    protected void pushAwayCollidingEntities() {
        List<Entity> list = this.world
            .getEntities(this, this.getShape().grown(0.2F, 0.0, 0.2F), Predicates.and(EntityFilter.NOT_SPECTATOR, new Predicate<Entity>() {
                public boolean apply(Entity entity) {
                    return entity.isPushable();
                }
            }));
        if (!list.isEmpty()) {
            for (int i = 0; i < list.size(); i++) {
                Entity entity = list.get(i);
                this.pushAway(entity);
            }
        }
    }

    protected void pushAway(Entity entity) {
        entity.push(this);
    }

    @Override
    public void startRiding(Entity entity) {
        if (this.vehicle != null && entity == null) {
            if (!this.world.isClient) {
                this.dismountRider(this.vehicle);
            }

            if (this.vehicle != null) {
                this.vehicle.rider = null;
            }

            this.vehicle = null;
        } else {
            super.startRiding(entity);
        }
    }

    @Override
    public void rideTick() {
        super.rideTick();
        this.lastWalkProgress = this.walkProgress;
        this.walkProgress = 0.0F;
        this.fallDistance = 0.0F;
    }

    @Override
    public void lerpPositionAndAngles(double x, double y, double z, float yaw, float pitch, int steps, boolean teleport) {
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpYaw = yaw;
        this.lerpPitch = pitch;
        this.lerpSteps = steps;
    }

    public void setJumping(boolean jumping) {
        this.jumping = jumping;
    }

    public void sendPickup(Entity entity, int count) {
        if (!entity.removed && !this.world.isClient) {
            EntityMap entitymap = ((ServerWorld)this.world).getEntityMap();
            if (entity instanceof ItemEntity) {
                entitymap.sendPacket(entity, new EntityPickupS2CPacket(entity.getNetworkId(), this.getNetworkId()));
            }

            if (entity instanceof ArrowEntity) {
                entitymap.sendPacket(entity, new EntityPickupS2CPacket(entity.getNetworkId(), this.getNetworkId()));
            }

            if (entity instanceof ExperienceOrbEntity) {
                entitymap.sendPacket(entity, new EntityPickupS2CPacket(entity.getNetworkId(), this.getNetworkId()));
            }
        }
    }

    public boolean canSee(Entity entity) {
        return this.world.rayTrace(new Vec3d(this.x, this.y + this.getEyeHeight(), this.z), new Vec3d(entity.x, entity.y + entity.getEyeHeight(), entity.z))
            == null;
    }

    @Override
    public Vec3d getLookVector() {
        return this.getRotationVec(1.0F);
    }

    @Override
    public Vec3d getRotationVec(float tickDelta) {
        if (tickDelta == 1.0F) {
            return this.getRotationVector(this.pitch, this.headYaw);
        }

        float f = this.lastPitch + (this.pitch - this.lastPitch) * tickDelta;
        float f1 = this.lastHeadYaw + (this.headYaw - this.lastHeadYaw) * tickDelta;
        return this.getRotationVector(f, f1);
    }

    public float getAttackAnimationProgress(float tickDelta) {
        float f = this.attackAnimationProgress - this.lastAttackAnimationProgress;
        if (f < 0.0F) {
            f++;
        }

        return this.lastAttackAnimationProgress + f * tickDelta;
    }

    /**
     * @return Whether the entity is "locally controlled".
     * A locally controlled entity will tick AI and movement.
     * A non-locally controlled entity does not have collision and will not move.
     * No entities on the client are locally controlled, except for the local player entity.
     */
    public boolean isLocallyControlled() {
        return !this.world.isClient;
    }

    @Override
    public boolean hasCollision() {
        return !this.removed;
    }

    @Override
    public boolean isPushable() {
        return !this.removed;
    }

    @Override
    protected void markDamaged() {
        this.damaged = this.random.nextDouble() >= this.getAttribute(EntityAttributes.KNOCKBACK_RESISTANCE).get();
    }

    @Override
    public float getHeadYaw() {
        return this.headYaw;
    }

    @Override
    public void setHeadYaw(float headYaw) {
        this.headYaw = headYaw;
    }

    @Override
    public void setBodyYaw(float yaw) {
        this.bodyYaw = yaw;
    }

    public float getAbsorption() {
        return this.absorption;
    }

    public void setAbsorption(float absorption) {
        if (absorption < 0.0F) {
            absorption = 0.0F;
        }

        this.absorption = absorption;
    }

    public AbstractTeam getScoreboardTeam() {
        return this.world.getScoreboard().getTeamOfMember(this.getUuid().toString());
    }

    public boolean isInSameTeam(LivingEntity entity) {
        return this.isInTeam(entity.getScoreboardTeam());
    }

    public boolean isInTeam(AbstractTeam team) {
        return this.getScoreboardTeam() != null && this.getScoreboardTeam().isAlliedTo(team);
    }

    public void enterCombat() {
    }

    public void endCombat() {
    }

    protected void markEffects() {
        this.effectsChanged = true;
    }
}
