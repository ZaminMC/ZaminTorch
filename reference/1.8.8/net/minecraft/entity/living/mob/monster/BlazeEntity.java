package net.minecraft.entity.living.mob.monster;

import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.WanderAroundGoal;
import net.minecraft.entity.ai.goal.WanderThroughVillageGoal;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.attribute.EntityAttributes;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.entity.projectile.SmallFireballEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public class BlazeEntity extends MonsterEntity {
    private float eyeOffset = 0.5F;
    private int fireActivation;

    public BlazeEntity(World world) {
        super(world);
        this.immuneToFire = true;
        this.xpDrop = 10;
        this.goalSelector.addGoal(4, new BlazeEntity.AttackGoal(this));
        this.goalSelector.addGoal(5, new WanderThroughVillageGoal(this, 1.0));
        this.goalSelector.addGoal(7, new WanderAroundGoal(this, 1.0));
        this.goalSelector.addGoal(8, new LookAtEntityGoal(this, PlayerEntity.class, 8.0F));
        this.goalSelector.addGoal(8, new LookAroundGoal(this));
        this.targetSelector.addGoal(1, new RevengeGoal(this, true));
        this.targetSelector.addGoal(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
    }

    @Override
    protected void initAttributes() {
        super.initAttributes();
        this.getAttribute(EntityAttributes.ATTACK_DAMAGE).setBase(6.0);
        this.getAttribute(EntityAttributes.MOVEMENT_SPEED).setBase(0.23F);
        this.getAttribute(EntityAttributes.FOLLOW_RANGE).setBase(48.0);
    }

    @Override
    protected void registerSyncedData() {
        super.registerSyncedData();
        this.syncedData.register(16, new Byte((byte)0));
    }

    @Override
    protected String getAmbientSound() {
        return "mob.blaze.breathe";
    }

    @Override
    protected String getHurtSound() {
        return "mob.blaze.hit";
    }

    @Override
    protected String getDeathSound() {
        return "mob.blaze.death";
    }

    @Override
    public int getLightLevel(float tickDelta) {
        return 15728880;
    }

    @Override
    public float getBrightness(float tickDelta) {
        return 1.0F;
    }

    @Override
    public void mobTick() {
        if (!this.onGround && this.velocityY < 0.0) {
            this.velocityY *= 0.6;
        }

        if (this.world.isClient) {
            if (this.random.nextInt(24) == 0 && !this.isSilent()) {
                this.world
                    .playSound(
                        this.x + 0.5, this.y + 0.5, this.z + 0.5, "fire.fire", 1.0F + this.random.nextFloat(), this.random.nextFloat() * 0.7F + 0.3F, false
                    );
            }

            for (int i = 0; i < 2; i++) {
                this.world
                    .addParticle(
                        ParticleType.SMOKE_LARGE,
                        this.x + (this.random.nextDouble() - 0.5) * this.width,
                        this.y + this.random.nextDouble() * this.height,
                        this.z + (this.random.nextDouble() - 0.5) * this.width,
                        0.0,
                        0.0,
                        0.0
                    );
            }
        }

        super.mobTick();
    }

    @Override
    protected void mobAiTick() {
        if (this.isInWaterOrRain()) {
            this.takeDamage(DamageSource.DROWN, 1.0F);
        }

        this.fireActivation--;
        if (this.fireActivation <= 0) {
            this.fireActivation = 100;
            this.eyeOffset = 0.5F + (float)this.random.nextGaussian() * 3.0F;
        }

        LivingEntity livingentity = this.getAttackTarget();
        if (livingentity != null && livingentity.y + livingentity.getEyeHeight() > this.y + this.getEyeHeight() + this.eyeOffset) {
            this.velocityY = this.velocityY + (0.3F - this.velocityY) * 0.3F;
            this.velocityDirty = true;
        }

        super.mobAiTick();
    }

    @Override
    public void takeFallDamage(float distance, float damageMultiplier) {
    }

    @Override
    protected Item getDropItem() {
        return Items.BLAZE_ROD;
    }

    @Override
    public boolean isOnFire() {
        return this.isFireActive();
    }

    @Override
    protected void dropItems(boolean loot, int lootingMultiplier) {
        if (loot) {
            int i = this.random.nextInt(2 + lootingMultiplier);

            for (int j = 0; j < i; j++) {
                this.dropItem(Items.BLAZE_ROD, 1);
            }
        }
    }

    public boolean isFireActive() {
        return (this.syncedData.getByte(16) & 1) != 0;
    }

    public void setFireActive(boolean active) {
        byte b0 = this.syncedData.getByte(16);
        if (active) {
            b0 = (byte)(b0 | 1);
        } else {
            b0 = (byte)(b0 & -2);
        }

        this.syncedData.update(16, b0);
    }

    @Override
    protected boolean canSpawnAtLightLevel() {
        return true;
    }

    static class AttackGoal extends Goal {
        private BlazeEntity blaze;
        private int step;
        private int ticks;

        public AttackGoal(BlazeEntity blaze) {
            this.blaze = blaze;
            this.setControls(3);
        }

        @Override
        public boolean canStart() {
            LivingEntity livingentity = this.blaze.getAttackTarget();
            return livingentity != null && livingentity.isAlive();
        }

        @Override
        public void start() {
            this.step = 0;
        }

        @Override
        public void stop() {
            this.blaze.setFireActive(false);
        }

        @Override
        public void tick() {
            this.ticks--;
            LivingEntity livingentity = this.blaze.getAttackTarget();
            double d0 = this.blaze.squaredDistanceTo(livingentity);
            if (d0 < 4.0) {
                if (this.ticks <= 0) {
                    this.ticks = 20;
                    this.blaze.tryDamage(livingentity);
                }

                this.blaze.getMovementControl().update(livingentity.x, livingentity.y, livingentity.z, 1.0);
            } else if (d0 < 256.0) {
                double d1 = livingentity.x - this.blaze.x;
                double d2 = livingentity.getShape().minY + livingentity.height / 2.0F - (this.blaze.y + this.blaze.height / 2.0F);
                double d3 = livingentity.z - this.blaze.z;
                if (this.ticks <= 0) {
                    this.step++;
                    if (this.step == 1) {
                        this.ticks = 60;
                        this.blaze.setFireActive(true);
                    } else if (this.step <= 4) {
                        this.ticks = 6;
                    } else {
                        this.ticks = 100;
                        this.step = 0;
                        this.blaze.setFireActive(false);
                    }

                    if (this.step > 1) {
                        float f = MathHelper.sqrt(MathHelper.sqrt(d0)) * 0.5F;
                        this.blaze.world.doEvent(null, 1009, new BlockPos((int)this.blaze.x, (int)this.blaze.y, (int)this.blaze.z), 0);

                        for (int i = 0; i < 1; i++) {
                            SmallFireballEntity smallfireballentity = new SmallFireballEntity(
                                this.blaze.world,
                                this.blaze,
                                d1 + this.blaze.getRandom().nextGaussian() * f,
                                d2,
                                d3 + this.blaze.getRandom().nextGaussian() * f
                            );
                            smallfireballentity.y = this.blaze.y + this.blaze.height / 2.0F + 0.5;
                            this.blaze.world.addEntity(smallfireballentity);
                        }
                    }
                }

                this.blaze.getLookControl().setLookatValues(livingentity, 10.0F, 10.0F);
            } else {
                this.blaze.getNavigation().stop();
                this.blaze.getMovementControl().update(livingentity.x, livingentity.y, livingentity.z, 1.0);
            }

            super.tick();
        }
    }
}
