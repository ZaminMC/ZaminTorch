package net.minecraft.entity.living.mob.monster;

import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.MobType;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.PounceAtTargetGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundGoal;
import net.minecraft.entity.ai.pathing.PathNavigation;
import net.minecraft.entity.ai.pathing.WallClimberPathNavigation;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.attribute.EntityAttributes;
import net.minecraft.entity.living.effect.StatusEffect;
import net.minecraft.entity.living.effect.StatusEffectInstance;
import net.minecraft.entity.living.mob.IronGolemEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.World;

public class SpiderEntity extends MonsterEntity {
    public SpiderEntity(World world) {
        super(world);
        this.setSize(1.4F, 0.9F);
        this.goalSelector.addGoal(1, new SwimGoal(this));
        this.goalSelector.addGoal(3, new PounceAtTargetGoal(this, 0.4F));
        this.goalSelector.addGoal(4, new SpiderEntity.SpiderAttackGoal(this, PlayerEntity.class));
        this.goalSelector.addGoal(4, new SpiderEntity.SpiderAttackGoal(this, IronGolemEntity.class));
        this.goalSelector.addGoal(5, new WanderAroundGoal(this, 0.8));
        this.goalSelector.addGoal(6, new LookAtEntityGoal(this, PlayerEntity.class, 8.0F));
        this.goalSelector.addGoal(6, new LookAroundGoal(this));
        this.targetSelector.addGoal(1, new RevengeGoal(this, false));
        this.targetSelector.addGoal(2, new SpiderEntity.TargetGoal<>(this, PlayerEntity.class));
        this.targetSelector.addGoal(3, new SpiderEntity.TargetGoal<>(this, IronGolemEntity.class));
    }

    @Override
    public double getMountHeight() {
        return this.height * 0.5F;
    }

    @Override
    protected PathNavigation createNavigation(World world) {
        return new WallClimberPathNavigation(this, world);
    }

    @Override
    protected void registerSyncedData() {
        super.registerSyncedData();
        this.syncedData.register(16, new Byte((byte)0));
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.world.isClient) {
            this.setClimbingWall(this.collidingHorizontally);
        }
    }

    @Override
    protected void initAttributes() {
        super.initAttributes();
        this.getAttribute(EntityAttributes.MAX_HEALTH).setBase(16.0);
        this.getAttribute(EntityAttributes.MOVEMENT_SPEED).setBase(0.3F);
    }

    @Override
    protected String getAmbientSound() {
        return "mob.spider.say";
    }

    @Override
    protected String getHurtSound() {
        return "mob.spider.say";
    }

    @Override
    protected String getDeathSound() {
        return "mob.spider.death";
    }

    @Override
    protected void playStepSound(BlockPos pos, Block block) {
        this.playSound("mob.spider.step", 0.15F, 1.0F);
    }

    @Override
    protected Item getDropItem() {
        return Items.STRING;
    }

    @Override
    protected void dropItems(boolean loot, int lootingMultiplier) {
        super.dropItems(loot, lootingMultiplier);
        if (loot && (this.random.nextInt(3) == 0 || this.random.nextInt(1 + lootingMultiplier) > 0)) {
            this.dropItem(Items.SPIDER_EYE, 1);
        }
    }

    @Override
    public boolean isClimbing() {
        return this.isClimbingWall();
    }

    @Override
    public void onCobwebCollision() {
    }

    @Override
    public MobType getMobType() {
        return MobType.ARTHROPOD;
    }

    @Override
    public boolean canHaveStatusEffect(StatusEffectInstance effect) {
        return effect.getId() != StatusEffect.POISON.id && super.canHaveStatusEffect(effect);
    }

    public boolean isClimbingWall() {
        return (this.syncedData.getByte(16) & 1) != 0;
    }

    public void setClimbingWall(boolean climbing) {
        byte b0 = this.syncedData.getByte(16);
        if (climbing) {
            b0 = (byte)(b0 | 1);
        } else {
            b0 = (byte)(b0 & -2);
        }

        this.syncedData.update(16, b0);
    }

    @Override
    public EntityData initialize(LocalDifficulty localDifficulty, EntityData data) {
        data = super.initialize(localDifficulty, data);
        if (this.world.random.nextInt(100) == 0) {
            SkeletonEntity skeletonentity = new SkeletonEntity(this.world);
            skeletonentity.setPositionAndAngles(this.x, this.y, this.z, this.yaw, 0.0F);
            skeletonentity.initialize(localDifficulty, null);
            this.world.addEntity(skeletonentity);
            skeletonentity.startRiding(this);
        }

        if (data == null) {
            data = new SpiderEntity.Data();
            if (this.world.getDifficulty() == Difficulty.HARD && this.world.random.nextFloat() < 0.1F * localDifficulty.getMultiplier()) {
                ((SpiderEntity.Data)data).setEffect(this.world.random);
            }
        }

        if (data instanceof SpiderEntity.Data) {
            int i = ((SpiderEntity.Data)data).effect;
            if (i > 0 && StatusEffect.BY_ID[i] != null) {
                this.addStatusEffect(new StatusEffectInstance(i, Integer.MAX_VALUE));
            }
        }

        return data;
    }

    @Override
    public float getEyeHeight() {
        return 0.65F;
    }

    public static class Data implements EntityData {
        public int effect;

        public void setEffect(Random random) {
            int i = random.nextInt(5);
            if (i <= 1) {
                this.effect = StatusEffect.SPEED.id;
            } else if (i <= 2) {
                this.effect = StatusEffect.STRENGTH.id;
            } else if (i <= 3) {
                this.effect = StatusEffect.REGENERATION.id;
            } else if (i <= 4) {
                this.effect = StatusEffect.INVISIBILITY.id;
            }
        }
    }

    static class SpiderAttackGoal extends MeleeAttackGoal {
        public SpiderAttackGoal(SpiderEntity spider, Class<? extends Entity> targetType) {
            super(spider, targetType, 1.0, true);
        }

        @Override
        public boolean shouldContinue() {
            float f = this.entity.getBrightness(1.0F);
            if (f >= 0.5F && this.entity.getRandom().nextInt(100) == 0) {
                this.entity.setAttackTarget(null);
                return false;
            } else {
                return super.shouldContinue();
            }
        }

        @Override
        protected double getReach(LivingEntity target) {
            return 4.0F + target.width;
        }
    }

    static class TargetGoal<T extends LivingEntity> extends ActiveTargetGoal {
        public TargetGoal(SpiderEntity spider, Class<T> targetType) {
            super(spider, targetType, true);
        }

        @Override
        public boolean canStart() {
            float f = this.mob.getBrightness(1.0F);
            return !(f >= 0.5F) && super.canStart();
        }
    }
}
