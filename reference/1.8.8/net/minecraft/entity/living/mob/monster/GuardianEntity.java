package net.minecraft.entity.living.mob.monster;

import com.google.common.base.Predicate;
import net.minecraft.block.Blocks;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.FishingBobberEntity;
import net.minecraft.entity.ai.control.LookControl;
import net.minecraft.entity.ai.control.MovementControl;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.WanderAroundGoal;
import net.minecraft.entity.ai.goal.WanderThroughVillageGoal;
import net.minecraft.entity.ai.pathing.PathNavigation;
import net.minecraft.entity.ai.pathing.WaterPathNavigation;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.attribute.EntityAttributes;
import net.minecraft.entity.living.effect.StatusEffect;
import net.minecraft.entity.living.effect.StatusEffectInstance;
import net.minecraft.entity.living.mob.water.SquidEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.item.FishItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.GameEventS2CPacket;
import net.minecraft.server.entity.living.player.ServerPlayerEntity;
import net.minecraft.util.WeightedPicker;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.World;

public class GuardianEntity extends MonsterEntity {
    private float tailAngle;
    private float prevTailAngle;
    private float spikesExtensionRate;
    private float spikesExtension;
    private float prevSpikesExtension;
    private LivingEntity cachedBeamTarget;
    private int beamTicks;
    private boolean flopping;
    private WanderAroundGoal wanderGoal;

    public GuardianEntity(World world) {
        super(world);
        this.xpDrop = 10;
        this.setSize(0.85F, 0.85F);
        this.goalSelector.addGoal(4, new GuardianEntity.GuardianAttackEntityGoal(this));
        WanderThroughVillageGoal wanderthroughvillagegoal;
        this.goalSelector.addGoal(5, wanderthroughvillagegoal = new WanderThroughVillageGoal(this, 1.0));
        this.goalSelector.addGoal(7, this.wanderGoal = new WanderAroundGoal(this, 1.0, 80));
        this.goalSelector.addGoal(8, new LookAtEntityGoal(this, PlayerEntity.class, 8.0F));
        this.goalSelector.addGoal(8, new LookAtEntityGoal(this, GuardianEntity.class, 12.0F, 0.01F));
        this.goalSelector.addGoal(9, new LookAroundGoal(this));
        this.wanderGoal.setControls(3);
        wanderthroughvillagegoal.setControls(3);
        this.targetSelector.addGoal(1, new ActiveTargetGoal<>(this, LivingEntity.class, 10, true, false, new GuardianEntity.GuardianTargetValidator(this)));
        this.movementControl = new GuardianEntity.GuardianMovementControl(this);
        this.prevTailAngle = this.tailAngle = this.random.nextFloat();
    }

    @Override
    protected void initAttributes() {
        super.initAttributes();
        this.getAttribute(EntityAttributes.ATTACK_DAMAGE).setBase(6.0);
        this.getAttribute(EntityAttributes.MOVEMENT_SPEED).setBase(0.5);
        this.getAttribute(EntityAttributes.FOLLOW_RANGE).setBase(16.0);
        this.getAttribute(EntityAttributes.MAX_HEALTH).setBase(30.0);
    }

    @Override
    public void readCustomNbt(NbtCompound nbt) {
        super.readCustomNbt(nbt);
        this.setElder(nbt.getBoolean("Elder"));
    }

    @Override
    public void writeCustomNbt(NbtCompound nbt) {
        super.writeCustomNbt(nbt);
        nbt.putBoolean("Elder", this.isElder());
    }

    @Override
    protected PathNavigation createNavigation(World world) {
        return new WaterPathNavigation(this, world);
    }

    @Override
    protected void registerSyncedData() {
        super.registerSyncedData();
        this.syncedData.register(16, 0);
        this.syncedData.register(17, 0);
    }

    /**
     * The DataTracker is storing all the tracked boolean values in an integer, with each bit of the integer corresponding
     * to a boolean value. Then this method compares the bits of the integer with the flag integer that is sent to the method.
     * The flag value will always be a power of 2,as its only purpose
     * is to point out which bit of the integer is reserved for the tracked value
     */
    private boolean checkDataFlag(int value) {
        return (this.syncedData.getInt(16) & value) != 0;
    }

    private void setDataFlagValue(int flag, boolean value) {
        int i = this.syncedData.getInt(16);
        if (value) {
            this.syncedData.update(16, i | flag);
        } else {
            this.syncedData.update(16, i & ~flag);
        }
    }

    public boolean isMoving() {
        return this.checkDataFlag(2);
    }

    private void setMoving(boolean isMoving) {
        this.setDataFlagValue(2, isMoving);
    }

    public int getAttackChargeTime() {
        return this.isElder() ? 60 : 80;
    }

    public boolean isElder() {
        return this.checkDataFlag(4);
    }

    public void setElder(boolean isElder) {
        this.setDataFlagValue(4, isElder);
        if (isElder) {
            this.setSize(1.9975F, 1.9975F);
            this.getAttribute(EntityAttributes.MOVEMENT_SPEED).setBase(0.3F);
            this.getAttribute(EntityAttributes.ATTACK_DAMAGE).setBase(8.0);
            this.getAttribute(EntityAttributes.MAX_HEALTH).setBase(80.0);
            this.setPersistent();
            this.wanderGoal.setInterval(400);
        }
    }

    public void setGhost() {
        this.setElder(true);
        this.prevSpikesExtension = this.spikesExtension = 1.0F;
    }

    private void setBeamTag(int beam) {
        this.syncedData.update(17, beam);
    }

    public boolean hasBeamTarget() {
        return this.syncedData.getInt(17) != 0;
    }

    public LivingEntity getLaserTarget() {
        if (!this.hasBeamTarget()) {
            return null;
        }

        if (this.world.isClient) {
            if (this.cachedBeamTarget != null) {
                return this.cachedBeamTarget;
            } else {
                Entity entity = this.world.getEntity(this.syncedData.getInt(17));
                if (entity instanceof LivingEntity) {
                    this.cachedBeamTarget = (LivingEntity)entity;
                    return this.cachedBeamTarget;
                } else {
                    return null;
                }
            }
        } else {
            return this.getAttackTarget();
        }
    }

    @Override
    public void onDataValueChanged(int id) {
        super.onDataValueChanged(id);
        if (id == 16) {
            if (this.isElder() && this.width < 1.0F) {
                this.setSize(1.9975F, 1.9975F);
            }
        } else if (id == 17) {
            this.beamTicks = 0;
            this.cachedBeamTarget = null;
        }
    }

    @Override
    public int getAmbientSoundInterval() {
        return 160;
    }

    @Override
    protected String getAmbientSound() {
        if (!this.isInWater()) {
            return "mob.guardian.land.idle";
        } else {
            return this.isElder() ? "mob.guardian.elder.idle" : "mob.guardian.idle";
        }
    }

    @Override
    protected String getHurtSound() {
        if (!this.isInWater()) {
            return "mob.guardian.land.hit";
        } else {
            return this.isElder() ? "mob.guardian.elder.hit" : "mob.guardian.hit";
        }
    }

    @Override
    protected String getDeathSound() {
        if (!this.isInWater()) {
            return "mob.guardian.land.death";
        } else {
            return this.isElder() ? "mob.guardian.elder.death" : "mob.guardian.death";
        }
    }

    @Override
    protected boolean makesSteps() {
        return false;
    }

    @Override
    public float getEyeHeight() {
        return this.height * 0.5F;
    }

    @Override
    public float getPathfindingFavor(BlockPos pos) {
        return this.world.getBlockState(pos).getBlock().getMaterial() == Material.WATER
            ? 10.0F + this.world.getBrightness(pos) - 0.5F
            : super.getPathfindingFavor(pos);
    }

    @Override
    public void mobTick() {
        if (this.world.isClient) {
            this.prevTailAngle = this.tailAngle;
            if (!this.isInWater()) {
                this.spikesExtensionRate = 2.0F;
                if (this.velocityY > 0.0 && this.flopping && !this.isSilent()) {
                    this.world.playSound(this.x, this.y, this.z, "mob.guardian.flop", 1.0F, 1.0F, false);
                }

                this.flopping = this.velocityY < 0.0 && this.world.isSolidBlockingCube(new BlockPos(this).down(), false);
            } else if (this.isMoving()) {
                if (this.spikesExtensionRate < 0.5F) {
                    this.spikesExtensionRate = 4.0F;
                } else {
                    this.spikesExtensionRate = this.spikesExtensionRate + (0.5F - this.spikesExtensionRate) * 0.1F;
                }
            } else {
                this.spikesExtensionRate = this.spikesExtensionRate + (0.125F - this.spikesExtensionRate) * 0.2F;
            }

            this.tailAngle = this.tailAngle + this.spikesExtensionRate;
            this.prevSpikesExtension = this.spikesExtension;
            if (!this.isInWater()) {
                this.spikesExtension = this.random.nextFloat();
            } else if (this.isMoving()) {
                this.spikesExtension = this.spikesExtension + (0.0F - this.spikesExtension) * 0.25F;
            } else {
                this.spikesExtension = this.spikesExtension + (1.0F - this.spikesExtension) * 0.06F;
            }

            if (this.isMoving() && this.isInWater()) {
                Vec3d vec3d = this.getRotationVec(0.0F);

                for (int i = 0; i < 2; i++) {
                    this.world
                        .addParticle(
                            ParticleType.WATER_BUBBLE,
                            this.x + (this.random.nextDouble() - 0.5) * this.width - vec3d.x * 1.5,
                            this.y + this.random.nextDouble() * this.height - vec3d.y * 1.5,
                            this.z + (this.random.nextDouble() - 0.5) * this.width - vec3d.z * 1.5,
                            0.0,
                            0.0,
                            0.0
                        );
                }
            }

            if (this.hasBeamTarget()) {
                if (this.beamTicks < this.getAttackChargeTime()) {
                    this.beamTicks++;
                }

                LivingEntity livingentity = this.getLaserTarget();
                if (livingentity != null) {
                    this.getLookControl().setLookatValues(livingentity, 90.0F, 90.0F);
                    this.getLookControl().tick();
                    double d5 = this.getBeamProgress(0.0F);
                    double d0 = livingentity.x - this.x;
                    double d1 = livingentity.y + livingentity.height * 0.5F - (this.y + this.getEyeHeight());
                    double d2 = livingentity.z - this.z;
                    double d3 = Math.sqrt(d0 * d0 + d1 * d1 + d2 * d2);
                    d0 /= d3;
                    d1 /= d3;
                    d2 /= d3;
                    double d4 = this.random.nextDouble();

                    while (d4 < d3) {
                        d4 += 1.8 - d5 + this.random.nextDouble() * (1.7 - d5);
                        this.world
                            .addParticle(ParticleType.WATER_BUBBLE, this.x + d0 * d4, this.y + d1 * d4 + this.getEyeHeight(), this.z + d2 * d4, 0.0, 0.0, 0.0);
                    }
                }
            }
        }

        if (this.inWater) {
            this.setBreath(300);
        } else if (this.onGround) {
            this.velocityY += 0.5;
            this.velocityX = this.velocityX + (this.random.nextFloat() * 2.0F - 1.0F) * 0.4F;
            this.velocityZ = this.velocityZ + (this.random.nextFloat() * 2.0F - 1.0F) * 0.4F;
            this.yaw = this.random.nextFloat() * 360.0F;
            this.onGround = false;
            this.velocityDirty = true;
        }

        if (this.hasBeamTarget()) {
            this.yaw = this.headYaw;
        }

        super.mobTick();
    }

    public float getTailAngle(float tickDelta) {
        return this.prevTailAngle + (this.tailAngle - this.prevTailAngle) * tickDelta;
    }

    public float getSpikesExtension(float tickDelta) {
        return this.prevSpikesExtension + (this.spikesExtension - this.prevSpikesExtension) * tickDelta;
    }

    public float getBeamProgress(float tickDelta) {
        return (this.beamTicks + tickDelta) / this.getAttackChargeTime();
    }

    @Override
    protected void mobAiTick() {
        super.mobAiTick();
        if (this.isElder()) {
            int i = 1200;
            int j = 1200;
            int k = 6000;
            int l = 2;
            if ((this.ticks + this.getNetworkId()) % 1200 == 0) {
                StatusEffect statuseffect = StatusEffect.MINING_FATIGUE;

                for (ServerPlayerEntity serverplayerentity : this.world.getPlayers(ServerPlayerEntity.class, new Predicate<ServerPlayerEntity>() {
                    public boolean apply(ServerPlayerEntity serverPlayerEntity) {
                        return GuardianEntity.this.squaredDistanceTo(serverPlayerEntity) < 2500.0 && serverPlayerEntity.interactionManager.isSurvival();
                    }
                })) {
                    if (!serverplayerentity.hasStatusEffect(statuseffect)
                        || serverplayerentity.getEffectInstance(statuseffect).getAmplifier() < 2
                        || serverplayerentity.getEffectInstance(statuseffect).getDuration() < 1200) {
                        serverplayerentity.networkHandler.sendPacket(new GameEventS2CPacket(10, 0.0F));
                        serverplayerentity.addStatusEffect(new StatusEffectInstance(statuseffect.id, 6000, 2));
                    }
                }
            }

            if (!this.inVillage()) {
                this.setVillagePosAndRadius(new BlockPos(this), 16);
            }
        }
    }

    @Override
    protected void dropItems(boolean loot, int lootingMultiplier) {
        int i = this.random.nextInt(3) + this.random.nextInt(lootingMultiplier + 1);
        if (i > 0) {
            this.dropItem(new ItemStack(Items.PRISMARINE_SHARD, i, 0), 1.0F);
        }

        if (this.random.nextInt(3 + lootingMultiplier) > 1) {
            this.dropItem(new ItemStack(Items.FISH, 1, FishItem.Type.COD.getId()), 1.0F);
        } else if (this.random.nextInt(3 + lootingMultiplier) > 1) {
            this.dropItem(new ItemStack(Items.PRISMARINE_CRYSTALS, 1, 0), 1.0F);
        }

        if (loot && this.isElder()) {
            this.dropItem(new ItemStack(Blocks.SPONGE, 1, 1), 1.0F);
        }
    }

    @Override
    protected void dropRareItem() {
        ItemStack itemstack = WeightedPicker.pick(this.random, FishingBobberEntity.getFishLoot()).getItem(this.random);
        this.dropItem(itemstack, 1.0F);
    }

    @Override
    protected boolean canSpawnAtLightLevel() {
        return true;
    }

    @Override
    public boolean isUnobstructed() {
        return this.world.isUnobstructed(this.getShape(), this) && this.world.getCollisions(this, this.getShape()).isEmpty();
    }

    @Override
    public boolean canSpawn() {
        return (this.random.nextInt(20) == 0 || !this.world.hasSkyAccessIgnoreLiquids(new BlockPos(this))) && super.canSpawn();
    }

    @Override
    public boolean takeDamage(DamageSource source, float amount) {
        if (!this.isMoving() && !source.getMagic() && source.getSource() instanceof LivingEntity) {
            LivingEntity livingentity = (LivingEntity)source.getSource();
            if (!source.isExplosive()) {
                livingentity.takeDamage(DamageSource.thorns(this), 2.0F);
                livingentity.playSound("damage.thorns", 0.5F, 1.0F);
            }
        }

        this.wanderGoal.updateGoal();
        return super.takeDamage(source, amount);
    }

    @Override
    public int getLookPitchSpeed() {
        return 180;
    }

    @Override
    public void moveRelative(float sideways, float forwards) {
        if (this.isLocallyControlled()) {
            if (this.isInWater()) {
                this.updateVelocity(sideways, forwards, 0.1F);
                this.move(this.velocityX, this.velocityY, this.velocityZ);
                this.velocityX *= 0.9F;
                this.velocityY *= 0.9F;
                this.velocityZ *= 0.9F;
                if (!this.isMoving() && this.getAttackTarget() == null) {
                    this.velocityY -= 0.005;
                }
            } else {
                super.moveRelative(sideways, forwards);
            }
        } else {
            super.moveRelative(sideways, forwards);
        }
    }

    static class GuardianAttackEntityGoal extends Goal {
        private GuardianEntity guardianEntity;
        private int attackChargeTicks;

        public GuardianAttackEntityGoal(GuardianEntity guardianEntity) {
            this.guardianEntity = guardianEntity;
            this.setControls(3);
        }

        @Override
        public boolean canStart() {
            LivingEntity livingentity = this.guardianEntity.getAttackTarget();
            return livingentity != null && livingentity.isAlive();
        }

        @Override
        public boolean shouldContinue() {
            return super.shouldContinue()
                && (this.guardianEntity.isElder() || this.guardianEntity.squaredDistanceTo(this.guardianEntity.getAttackTarget()) > 9.0);
        }

        @Override
        public void start() {
            this.attackChargeTicks = -10;
            this.guardianEntity.getNavigation().stop();
            this.guardianEntity.getLookControl().setLookatValues(this.guardianEntity.getAttackTarget(), 90.0F, 90.0F);
            this.guardianEntity.velocityDirty = true;
        }

        @Override
        public void stop() {
            this.guardianEntity.setBeamTag(0);
            this.guardianEntity.setAttackTarget(null);
            this.guardianEntity.wanderGoal.updateGoal();
        }

        @Override
        public void tick() {
            LivingEntity livingentity = this.guardianEntity.getAttackTarget();
            this.guardianEntity.getNavigation().stop();
            this.guardianEntity.getLookControl().setLookatValues(livingentity, 90.0F, 90.0F);
            if (!this.guardianEntity.canSee(livingentity)) {
                this.guardianEntity.setAttackTarget(null);
            } else {
                this.attackChargeTicks++;
                if (this.attackChargeTicks == 0) {
                    this.guardianEntity.setBeamTag(this.guardianEntity.getAttackTarget().getNetworkId());
                    this.guardianEntity.world.doEntityEvent(this.guardianEntity, (byte)21);
                } else if (this.attackChargeTicks >= this.guardianEntity.getAttackChargeTime()) {
                    float f = 1.0F;
                    if (this.guardianEntity.world.getDifficulty() == Difficulty.HARD) {
                        f += 2.0F;
                    }

                    if (this.guardianEntity.isElder()) {
                        f += 2.0F;
                    }

                    livingentity.takeDamage(DamageSource.magic(this.guardianEntity, this.guardianEntity), f);
                    livingentity.takeDamage(
                        DamageSource.mob(this.guardianEntity), (float)this.guardianEntity.getAttribute(EntityAttributes.ATTACK_DAMAGE).get()
                    );
                    this.guardianEntity.setAttackTarget(null);
                } else if (this.attackChargeTicks >= 60 && this.attackChargeTicks % 20 == 0) {
                }

                super.tick();
            }
        }
    }

    static class GuardianMovementControl extends MovementControl {
        private GuardianEntity guardianEntity;

        public GuardianMovementControl(GuardianEntity guardianEntity) {
            super(guardianEntity);
            this.guardianEntity = guardianEntity;
        }

        @Override
        public void tick() {
            if (this.moving && !this.guardianEntity.getNavigation().isDone()) {
                double d0 = this.x - this.guardianEntity.x;
                double d1 = this.y - this.guardianEntity.y;
                double d2 = this.z - this.guardianEntity.z;
                double d3 = d0 * d0 + d1 * d1 + d2 * d2;
                d3 = MathHelper.sqrt(d3);
                d1 /= d3;
                float f = (float)(MathHelper.fastAtan2(d2, d0) * 180.0 / (float) Math.PI) - 90.0F;
                this.guardianEntity.yaw = this.clampAndWrapAngle(this.guardianEntity.yaw, f, 30.0F);
                this.guardianEntity.bodyYaw = this.guardianEntity.yaw;
                float f1 = (float)(this.speed * this.guardianEntity.getAttribute(EntityAttributes.MOVEMENT_SPEED).get());
                this.guardianEntity.setSpeed(this.guardianEntity.getSpeed() + (f1 - this.guardianEntity.getSpeed()) * 0.125F);
                double d4 = Math.sin((this.guardianEntity.ticks + this.guardianEntity.getNetworkId()) * 0.5) * 0.05;
                double d5 = Math.cos(this.guardianEntity.yaw * (float) Math.PI / 180.0F);
                double d6 = Math.sin(this.guardianEntity.yaw * (float) Math.PI / 180.0F);
                this.guardianEntity.velocityX += d4 * d5;
                this.guardianEntity.velocityZ += d4 * d6;
                d4 = Math.sin((this.guardianEntity.ticks + this.guardianEntity.getNetworkId()) * 0.75) * 0.05;
                this.guardianEntity.velocityY += d4 * (d6 + d5) * 0.25;
                this.guardianEntity.velocityY = this.guardianEntity.velocityY + this.guardianEntity.getSpeed() * d1 * 0.1;
                LookControl lookcontrol = this.guardianEntity.getLookControl();
                double d7 = this.guardianEntity.x + d0 / d3 * 2.0;
                double d8 = this.guardianEntity.getEyeHeight() + this.guardianEntity.y + d1 / d3 * 1.0;
                double d9 = this.guardianEntity.z + d2 / d3 * 2.0;
                double d10 = lookcontrol.getLookX();
                double d11 = lookcontrol.getLookY();
                double d12 = lookcontrol.getLookZ();
                if (!lookcontrol.isLookingAtSpecificPosition()) {
                    d10 = d7;
                    d11 = d8;
                    d12 = d9;
                }

                this.guardianEntity.getLookControl().lookAt(d10 + (d7 - d10) * 0.125, d11 + (d8 - d11) * 0.125, d12 + (d9 - d12) * 0.125, 10.0F, 40.0F);
                this.guardianEntity.setMoving(true);
            } else {
                this.guardianEntity.setSpeed(0.0F);
                this.guardianEntity.setMoving(false);
            }
        }
    }

    static class GuardianTargetValidator implements Predicate<LivingEntity> {
        private GuardianEntity guardianEntity;

        public GuardianTargetValidator(GuardianEntity guardianEntity) {
            this.guardianEntity = guardianEntity;
        }

        public boolean apply(LivingEntity livingEntity) {
            return (livingEntity instanceof PlayerEntity || livingEntity instanceof SquidEntity) && livingEntity.squaredDistanceTo(this.guardianEntity) > 9.0;
        }
    }
}
