package net.minecraft.entity.living.mob.monster;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.MobEntityActiveTargetGoal;
import net.minecraft.entity.ai.goal.MobEntityPlayerTargetGoal;
import net.minecraft.entity.ai.pathing.GroundPathNavigation;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.attribute.EntityAttributes;
import net.minecraft.entity.living.mob.IronGolemEntity;
import net.minecraft.entity.living.mob.MobEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Difficulty;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.gen.WorldGeneratorType;

public class SlimeEntity extends MobEntity implements Monster {
    public float targetStretch;
    public float stretch;
    public float lastStretch;
    private boolean wasOnGround;

    public SlimeEntity(World world) {
        super(world);
        this.movementControl = new SlimeEntity.MovementControl(this);
        this.goalSelector.addGoal(1, new SlimeEntity.SwimAroundGoal(this));
        this.goalSelector.addGoal(2, new SlimeEntity.AttackGoal(this));
        this.goalSelector.addGoal(3, new SlimeEntity.RandomDirectionGoal(this));
        this.goalSelector.addGoal(5, new SlimeEntity.JumpAroundGoal(this));
        this.targetSelector.addGoal(1, new MobEntityPlayerTargetGoal(this));
        this.targetSelector.addGoal(3, new MobEntityActiveTargetGoal(this, IronGolemEntity.class));
    }

    @Override
    protected void registerSyncedData() {
        super.registerSyncedData();
        this.syncedData.register(16, (byte)1);
    }

    protected void setSize(int size) {
        this.syncedData.update(16, (byte)size);
        this.setSize(0.51000005F * size, 0.51000005F * size);
        this.setPosition(this.x, this.y, this.z);
        this.getAttribute(EntityAttributes.MAX_HEALTH).setBase(size * size);
        this.getAttribute(EntityAttributes.MOVEMENT_SPEED).setBase(0.2F + 0.1F * size);
        this.setHealth(this.getMaxHealth());
        this.xpDrop = size;
    }

    public int getSize() {
        return this.syncedData.getByte(16);
    }

    @Override
    public void writeCustomNbt(NbtCompound nbt) {
        super.writeCustomNbt(nbt);
        nbt.putInt("Size", this.getSize() - 1);
        nbt.putBoolean("wasOnGround", this.wasOnGround);
    }

    @Override
    public void readCustomNbt(NbtCompound nbt) {
        super.readCustomNbt(nbt);
        int i = nbt.getInt("Size");
        if (i < 0) {
            i = 0;
        }

        this.setSize(i + 1);
        this.wasOnGround = nbt.getBoolean("wasOnGround");
    }

    protected ParticleType getParticleType() {
        return ParticleType.SLIME;
    }

    protected String getSoundName() {
        return "mob.slime." + (this.getSize() > 1 ? "big" : "small");
    }

    @Override
    public void tick() {
        if (!this.world.isClient && this.world.getDifficulty() == Difficulty.PEACEFUL && this.getSize() > 0) {
            this.removed = true;
        }

        this.stretch = this.stretch + (this.targetStretch - this.stretch) * 0.5F;
        this.lastStretch = this.stretch;
        super.tick();
        if (this.onGround && !this.wasOnGround) {
            int i = this.getSize();

            for (int j = 0; j < i * 8; j++) {
                float f = this.random.nextFloat() * (float) Math.PI * 2.0F;
                float f1 = this.random.nextFloat() * 0.5F + 0.5F;
                float f2 = MathHelper.sin(f) * i * 0.5F * f1;
                float f3 = MathHelper.cos(f) * i * 0.5F * f1;
                World world = this.world;
                ParticleType particletype = this.getParticleType();
                double d0 = this.x + f2;
                double d1 = this.z + f3;
                world.addParticle(particletype, d0, this.getShape().minY, d1, 0.0, 0.0, 0.0);
            }

            if (this.makesLandSound()) {
                this.playSound(this.getSoundName(), this.getSoundVolume(), ((this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F) / 0.8F);
            }

            this.targetStretch = -0.5F;
        } else if (!this.onGround && this.wasOnGround) {
            this.targetStretch = 1.0F;
        }

        this.wasOnGround = this.onGround;
        this.updateStretch();
    }

    protected void updateStretch() {
        this.targetStretch *= 0.6F;
    }

    protected int getTicksUntilNextJump() {
        return this.random.nextInt(20) + 10;
    }

    protected SlimeEntity getInstance() {
        return new SlimeEntity(this.world);
    }

    @Override
    public void onDataValueChanged(int id) {
        if (id == 16) {
            int i = this.getSize();
            this.setSize(0.51000005F * i, 0.51000005F * i);
            this.yaw = this.headYaw;
            this.bodyYaw = this.headYaw;
            if (this.isInWater() && this.random.nextInt(20) == 0) {
                this.doSplashEffect();
            }
        }

        super.onDataValueChanged(id);
    }

    @Override
    public void remove() {
        int i = this.getSize();
        if (!this.world.isClient && i > 1 && this.getHealth() <= 0.0F) {
            int j = 2 + this.random.nextInt(3);

            for (int k = 0; k < j; k++) {
                float f = (k % 2 - 0.5F) * i / 4.0F;
                float f1 = (k / 2 - 0.5F) * i / 4.0F;
                SlimeEntity slimeentity = this.getInstance();
                if (this.hasCustomName()) {
                    slimeentity.setCustomName(this.getCustomName());
                }

                if (this.isPersistent()) {
                    slimeentity.setPersistent();
                }

                slimeentity.setSize(i / 2);
                slimeentity.setPositionAndAngles(this.x + f, this.y + 0.5, this.z + f1, this.random.nextFloat() * 360.0F, 0.0F);
                this.world.addEntity(slimeentity);
            }
        }

        super.remove();
    }

    @Override
    public void push(Entity entity) {
        super.push(entity);
        if (entity instanceof IronGolemEntity && this.isBig()) {
            this.damageTargetEntity((LivingEntity)entity);
        }
    }

    @Override
    public void onPlayerCollision(PlayerEntity player) {
        if (this.isBig()) {
            this.damageTargetEntity(player);
        }
    }

    protected void damageTargetEntity(LivingEntity entity) {
        int i = this.getSize();
        if (this.canSee(entity) && this.squaredDistanceTo(entity) < 0.6 * i * (0.6 * i) && entity.takeDamage(DamageSource.mob(this), this.getDamageAmount())) {
            this.playSound("mob.attack", 1.0F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
            this.damageEntity(this, entity);
        }
    }

    @Override
    public float getEyeHeight() {
        return 0.625F * this.height;
    }

    protected boolean isBig() {
        return this.getSize() > 1;
    }

    protected int getDamageAmount() {
        return this.getSize();
    }

    @Override
    protected String getHurtSound() {
        return "mob.slime." + (this.getSize() > 1 ? "big" : "small");
    }

    @Override
    protected String getDeathSound() {
        return "mob.slime." + (this.getSize() > 1 ? "big" : "small");
    }

    @Override
    protected Item getDropItem() {
        return this.getSize() == 1 ? Items.SLIME_BALL : null;
    }

    @Override
    public boolean canSpawn() {
        BlockPos blockpos = new BlockPos(MathHelper.floor(this.x), 0, MathHelper.floor(this.z));
        WorldChunk worldchunk = this.world.getChunk(blockpos);
        if (this.world.getData().getGeneratorType() == WorldGeneratorType.FLAT && this.random.nextInt(4) != 1) {
            return false;
        }

        if (this.world.getDifficulty() != Difficulty.PEACEFUL) {
            Biome biome = this.world.getBiome(blockpos);
            if (biome == Biome.SWAMPLAND
                && this.y > 50.0
                && this.y < 70.0
                && this.random.nextFloat() < 0.5F
                && this.random.nextFloat() < this.world.getMoonSize()
                && this.world.getRawBrightness(new BlockPos(this)) <= this.random.nextInt(8)) {
                return super.canSpawn();
            }

            if (this.random.nextInt(10) == 0 && worldchunk.getRandomForSlime(987234911L).nextInt(10) == 0 && this.y < 40.0) {
                return super.canSpawn();
            }
        }

        return false;
    }

    @Override
    protected float getSoundVolume() {
        return 0.4F * this.getSize();
    }

    @Override
    public int getLookPitchSpeed() {
        return 0;
    }

    protected boolean makesJumpSound() {
        return this.getSize() > 0;
    }

    protected boolean makesLandSound() {
        return this.getSize() > 2;
    }

    @Override
    protected void jump() {
        this.velocityY = 0.42F;
        this.velocityDirty = true;
    }

    @Override
    public EntityData initialize(LocalDifficulty localDifficulty, EntityData data) {
        int i = this.random.nextInt(3);
        if (i < 2 && this.random.nextFloat() < 0.5F * localDifficulty.getMultiplier()) {
            i++;
        }

        int j = 1 << i;
        this.setSize(j);
        return super.initialize(localDifficulty, data);
    }

    static class AttackGoal extends Goal {
        private SlimeEntity slime;
        private int stopTimer;

        public AttackGoal(SlimeEntity entity) {
            this.slime = entity;
            this.setControls(2);
        }

        @Override
        public boolean canStart() {
            LivingEntity livingentity = this.slime.getAttackTarget();
            return livingentity != null
                && livingentity.isAlive()
                && (!(livingentity instanceof PlayerEntity) || !((PlayerEntity)livingentity).abilities.invulnerable);
        }

        @Override
        public void start() {
            this.stopTimer = 300;
            super.start();
        }

        @Override
        public boolean shouldContinue() {
            LivingEntity livingentity = this.slime.getAttackTarget();
            return livingentity != null
                && livingentity.isAlive()
                && (!(livingentity instanceof PlayerEntity) || !((PlayerEntity)livingentity).abilities.invulnerable)
                && --this.stopTimer > 0;
        }

        @Override
        public void tick() {
            this.slime.lookAt(this.slime.getAttackTarget(), 10.0F, 10.0F);
            ((SlimeEntity.MovementControl)this.slime.getMovementControl()).setDirection(this.slime.yaw, this.slime.isBig());
        }
    }

    static class JumpAroundGoal extends Goal {
        private SlimeEntity slime;

        public JumpAroundGoal(SlimeEntity slime) {
            this.slime = slime;
            this.setControls(5);
        }

        @Override
        public boolean canStart() {
            return true;
        }

        @Override
        public void tick() {
            ((SlimeEntity.MovementControl)this.slime.getMovementControl()).setSpeed(1.0);
        }
    }

    static class MovementControl extends net.minecraft.entity.ai.control.MovementControl {
        private float yaw;
        private int jumpCooldownTicks;
        private SlimeEntity entity;
        private boolean aggressive;

        public MovementControl(SlimeEntity entity) {
            super(entity);
            this.entity = entity;
        }

        public void setDirection(float yaw, boolean aggressive) {
            this.yaw = yaw;
            this.aggressive = aggressive;
        }

        public void setSpeed(double speed) {
            this.speed = speed;
            this.moving = true;
        }

        @Override
        public void tick() {
            this.mob.yaw = this.clampAndWrapAngle(this.mob.yaw, this.yaw, 30.0F);
            this.mob.headYaw = this.mob.yaw;
            this.mob.bodyYaw = this.mob.yaw;
            if (!this.moving) {
                this.mob.setForwardsSpeed(0.0F);
            } else {
                this.moving = false;
                if (this.mob.onGround) {
                    this.mob.setSpeed((float)(this.speed * this.mob.getAttribute(EntityAttributes.MOVEMENT_SPEED).get()));
                    if (this.jumpCooldownTicks-- <= 0) {
                        this.jumpCooldownTicks = this.entity.getTicksUntilNextJump();
                        if (this.aggressive) {
                            this.jumpCooldownTicks /= 3;
                        }

                        this.entity.getJumpControl().setActive();
                        if (this.entity.makesJumpSound()) {
                            this.entity
                                .playSound(
                                    this.entity.getSoundName(),
                                    this.entity.getSoundVolume(),
                                    ((this.entity.getRandom().nextFloat() - this.entity.getRandom().nextFloat()) * 0.2F + 1.0F) * 0.8F
                                );
                        }
                    } else {
                        this.entity.sidewaysSpeed = this.entity.forwardSpeed = 0.0F;
                        this.mob.setSpeed(0.0F);
                    }
                } else {
                    this.mob.setSpeed((float)(this.speed * this.mob.getAttribute(EntityAttributes.MOVEMENT_SPEED).get()));
                }
            }
        }
    }

    static class RandomDirectionGoal extends Goal {
        private SlimeEntity entity;
        private float direction;
        private int cooldown;

        public RandomDirectionGoal(SlimeEntity entity) {
            this.entity = entity;
            this.setControls(2);
        }

        @Override
        public boolean canStart() {
            return this.entity.getAttackTarget() == null && (this.entity.onGround || this.entity.isInWater() || this.entity.isInLava());
        }

        @Override
        public void tick() {
            if (--this.cooldown <= 0) {
                this.cooldown = 40 + this.entity.getRandom().nextInt(60);
                this.direction = this.entity.getRandom().nextInt(360);
            }

            ((SlimeEntity.MovementControl)this.entity.getMovementControl()).setDirection(this.direction, false);
        }
    }

    static class SwimAroundGoal extends Goal {
        private SlimeEntity slime;

        public SwimAroundGoal(SlimeEntity slime) {
            this.slime = slime;
            this.setControls(5);
            ((GroundPathNavigation)slime.getNavigation()).setCanFloat(true);
        }

        @Override
        public boolean canStart() {
            return this.slime.isInWater() || this.slime.isInLava();
        }

        @Override
        public void tick() {
            if (this.slime.getRandom().nextFloat() < 0.8F) {
                this.slime.getJumpControl().setActive();
            }

            ((SlimeEntity.MovementControl)this.slime.getMovementControl()).setSpeed(1.2);
        }
    }
}
