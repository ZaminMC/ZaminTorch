package net.minecraft.entity.living.mob.monster.boss;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityFilter;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.MobType;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.ProjectileAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundGoal;
import net.minecraft.entity.ai.pathing.GroundPathNavigation;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.attribute.EntityAttributes;
import net.minecraft.entity.living.effect.StatusEffectInstance;
import net.minecraft.entity.living.mob.MobEntity;
import net.minecraft.entity.living.mob.monster.MonsterEntity;
import net.minecraft.entity.living.mob.monster.RangedAttackMob;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.WitherSkullEntity;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.stat.achievement.Achievements;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Difficulty;
import net.minecraft.world.World;

public class WitherEntity extends MonsterEntity implements Boss, RangedAttackMob {
    private float[] sideHeadPitches = new float[2];
    private float[] sideHeadYaws = new float[2];
    private float[] prevSideHeadPitches = new float[2];
    private float[] prevSideHeadYaws = new float[2];
    private int[] skullCooldowns = new int[2];
    private int[] chargedSkullCooldowns = new int[2];
    private int blockBreakingCooldown;
    private static final Predicate<Entity> UNDEAD_FILTER = new Predicate<Entity>() {
        public boolean apply(Entity entity) {
            return entity instanceof LivingEntity && ((LivingEntity)entity).getMobType() != MobType.UNDEAD;
        }
    };

    public WitherEntity(World world) {
        super(world);
        this.setHealth(this.getMaxHealth());
        this.setSize(0.9F, 3.5F);
        this.immuneToFire = true;
        ((GroundPathNavigation)this.getNavigation()).setCanFloat(true);
        this.goalSelector.addGoal(0, new SwimGoal(this));
        this.goalSelector.addGoal(2, new ProjectileAttackGoal(this, 1.0, 40, 20.0F));
        this.goalSelector.addGoal(5, new WanderAroundGoal(this, 1.0));
        this.goalSelector.addGoal(6, new LookAtEntityGoal(this, PlayerEntity.class, 8.0F));
        this.goalSelector.addGoal(7, new LookAroundGoal(this));
        this.targetSelector.addGoal(1, new RevengeGoal(this, false));
        this.targetSelector.addGoal(2, new ActiveTargetGoal<>(this, MobEntity.class, 0, false, false, UNDEAD_FILTER));
        this.xpDrop = 50;
    }

    @Override
    protected void registerSyncedData() {
        super.registerSyncedData();
        this.syncedData.register(17, new Integer(0));
        this.syncedData.register(18, new Integer(0));
        this.syncedData.register(19, new Integer(0));
        this.syncedData.register(20, new Integer(0));
    }

    @Override
    public void writeCustomNbt(NbtCompound nbt) {
        super.writeCustomNbt(nbt);
        nbt.putInt("Invul", this.getInvulnerabilityTimer());
    }

    @Override
    public void readCustomNbt(NbtCompound nbt) {
        super.readCustomNbt(nbt);
        this.setInvulnerabilityTimer(nbt.getInt("Invul"));
    }

    @Override
    protected String getAmbientSound() {
        return "mob.wither.idle";
    }

    @Override
    protected String getHurtSound() {
        return "mob.wither.hurt";
    }

    @Override
    protected String getDeathSound() {
        return "mob.wither.death";
    }

    @Override
    public void mobTick() {
        this.velocityY *= 0.6F;
        if (!this.world.isClient && this.getTrackedEntityId(0) > 0) {
            Entity entity = this.world.getEntity(this.getTrackedEntityId(0));
            if (entity != null) {
                if (this.y < entity.y || !this.isAtHalfHealth() && this.y < entity.y + 5.0) {
                    if (this.velocityY < 0.0) {
                        this.velocityY = 0.0;
                    }

                    this.velocityY = this.velocityY + (0.5 - this.velocityY) * 0.6F;
                }

                double d0 = entity.x - this.x;
                double d1 = entity.z - this.z;
                double d3 = d0 * d0 + d1 * d1;
                if (d3 > 9.0) {
                    double d5 = MathHelper.sqrt(d3);
                    this.velocityX = this.velocityX + (d0 / d5 * 0.5 - this.velocityX) * 0.6F;
                    this.velocityZ = this.velocityZ + (d1 / d5 * 0.5 - this.velocityZ) * 0.6F;
                }
            }
        }

        if (this.velocityX * this.velocityX + this.velocityZ * this.velocityZ > 0.05F) {
            this.yaw = (float)MathHelper.fastAtan2(this.velocityZ, this.velocityX) * (180.0F / (float)Math.PI) - 90.0F;
        }

        super.mobTick();

        for (int i = 0; i < 2; i++) {
            this.prevSideHeadYaws[i] = this.sideHeadYaws[i];
            this.prevSideHeadPitches[i] = this.sideHeadPitches[i];
        }

        for (int j = 0; j < 2; j++) {
            int k = this.getTrackedEntityId(j + 1);
            Entity entity1 = null;
            if (k > 0) {
                entity1 = this.world.getEntity(k);
            }

            if (entity1 != null) {
                double d11 = this.getHeadX(j + 1);
                double d12 = this.getHeadY(j + 1);
                double d13 = this.getHeadZ(j + 1);
                double d6 = entity1.x - d11;
                double d7 = entity1.y + entity1.getEyeHeight() - d12;
                double d8 = entity1.z - d13;
                double d9 = MathHelper.sqrt(d6 * d6 + d8 * d8);
                float f = (float)(MathHelper.fastAtan2(d8, d6) * 180.0 / (float) Math.PI) - 90.0F;
                float f1 = (float)(-(MathHelper.fastAtan2(d7, d9) * 180.0 / (float) Math.PI));
                this.sideHeadPitches[j] = this.getNextAngle(this.sideHeadPitches[j], f1, 40.0F);
                this.sideHeadYaws[j] = this.getNextAngle(this.sideHeadYaws[j], f, 10.0F);
            } else {
                this.sideHeadYaws[j] = this.getNextAngle(this.sideHeadYaws[j], this.bodyYaw, 10.0F);
            }
        }

        boolean flag = this.isAtHalfHealth();

        for (int l = 0; l < 3; l++) {
            double d10 = this.getHeadX(l);
            double d2 = this.getHeadY(l);
            double d4 = this.getHeadZ(l);
            this.world
                .addParticle(
                    ParticleType.SMOKE_NORMAL,
                    d10 + this.random.nextGaussian() * 0.3F,
                    d2 + this.random.nextGaussian() * 0.3F,
                    d4 + this.random.nextGaussian() * 0.3F,
                    0.0,
                    0.0,
                    0.0
                );
            if (flag && this.world.random.nextInt(4) == 0) {
                this.world
                    .addParticle(
                        ParticleType.SPELL_MOB,
                        d10 + this.random.nextGaussian() * 0.3F,
                        d2 + this.random.nextGaussian() * 0.3F,
                        d4 + this.random.nextGaussian() * 0.3F,
                        0.7F,
                        0.7F,
                        0.5
                    );
            }
        }

        if (this.getInvulnerabilityTimer() > 0) {
            for (int i1 = 0; i1 < 3; i1++) {
                this.world
                    .addParticle(
                        ParticleType.SPELL_MOB,
                        this.x + this.random.nextGaussian() * 1.0,
                        this.y + this.random.nextFloat() * 3.3F,
                        this.z + this.random.nextGaussian() * 1.0,
                        0.7F,
                        0.7F,
                        0.9F
                    );
            }
        }
    }

    @Override
    protected void mobAiTick() {
        if (this.getInvulnerabilityTimer() > 0) {
            int j1 = this.getInvulnerabilityTimer() - 1;
            if (j1 <= 0) {
                this.world.explode(this, this.x, this.y + this.getEyeHeight(), this.z, 7.0F, false, this.world.getGameRules().getBoolean("mobGriefing"));
                this.world.doGlobalEvent(1013, new BlockPos(this), 0);
            }

            this.setInvulnerabilityTimer(j1);
            if (this.ticks % 10 == 0) {
                this.heal(10.0F);
            }
        } else {
            super.mobAiTick();

            for (int i = 1; i < 3; i++) {
                if (this.ticks >= this.skullCooldowns[i - 1]) {
                    this.skullCooldowns[i - 1] = this.ticks + 10 + this.random.nextInt(10);
                    if ((this.world.getDifficulty() == Difficulty.NORMAL || this.world.getDifficulty() == Difficulty.HARD)
                        && this.chargedSkullCooldowns[i - 1]++ > 15) {
                        float f = 10.0F;
                        float f1 = 5.0F;
                        double d0 = MathHelper.nextDouble(this.random, this.x - f, this.x + f);
                        double d1 = MathHelper.nextDouble(this.random, this.y - f1, this.y + f1);
                        double d2 = MathHelper.nextDouble(this.random, this.z - f, this.z + f);
                        this.shootSkullAt(i + 1, d0, d1, d2, true);
                        this.chargedSkullCooldowns[i - 1] = 0;
                    }

                    int k1 = this.getTrackedEntityId(i);
                    if (k1 > 0) {
                        Entity entity = this.world.getEntity(k1);
                        if (entity == null || !entity.isAlive() || this.squaredDistanceTo(entity) > 900.0 || !this.canSee(entity)) {
                            this.setTrackedEntityId(i, 0);
                        } else if (entity instanceof PlayerEntity && ((PlayerEntity)entity).abilities.invulnerable) {
                            this.setTrackedEntityId(i, 0);
                        } else {
                            this.shootSkullAt(i + 1, (LivingEntity)entity);
                            this.skullCooldowns[i - 1] = this.ticks + 40 + this.random.nextInt(20);
                            this.chargedSkullCooldowns[i - 1] = 0;
                        }
                    } else {
                        List<LivingEntity> list = this.world
                            .getEntitiesOfType(
                                LivingEntity.class, this.getShape().grown(20.0, 8.0, 20.0), Predicates.and(UNDEAD_FILTER, EntityFilter.NOT_SPECTATOR)
                            );

                        for (int j2 = 0; j2 < 10 && !list.isEmpty(); j2++) {
                            LivingEntity livingentity = list.get(this.random.nextInt(list.size()));
                            if (livingentity != this && livingentity.isAlive() && this.canSee(livingentity)) {
                                if (livingentity instanceof PlayerEntity) {
                                    if (!((PlayerEntity)livingentity).abilities.invulnerable) {
                                        this.setTrackedEntityId(i, livingentity.getNetworkId());
                                    }
                                } else {
                                    this.setTrackedEntityId(i, livingentity.getNetworkId());
                                }
                                break;
                            }

                            list.remove(livingentity);
                        }
                    }
                }
            }

            if (this.getAttackTarget() != null) {
                this.setTrackedEntityId(0, this.getAttackTarget().getNetworkId());
            } else {
                this.setTrackedEntityId(0, 0);
            }

            if (this.blockBreakingCooldown > 0) {
                this.blockBreakingCooldown--;
                if (this.blockBreakingCooldown == 0 && this.world.getGameRules().getBoolean("mobGriefing")) {
                    int i1 = MathHelper.floor(this.y);
                    int l1 = MathHelper.floor(this.x);
                    int i2 = MathHelper.floor(this.z);
                    boolean flag = false;

                    for (int k2 = -1; k2 <= 1; k2++) {
                        for (int l2 = -1; l2 <= 1; l2++) {
                            for (int j = 0; j <= 3; j++) {
                                int i3 = l1 + k2;
                                int k = i1 + j;
                                int l = i2 + l2;
                                BlockPos blockpos = new BlockPos(i3, k, l);
                                Block block = this.world.getBlockState(blockpos).getBlock();
                                if (block.getMaterial() != Material.AIR && canDestroy(block)) {
                                    flag = this.world.breakBlock(blockpos, true) || flag;
                                }
                            }
                        }
                    }

                    if (flag) {
                        this.world.doEvent(null, 1012, new BlockPos(this), 0);
                    }
                }
            }

            if (this.ticks % 20 == 0) {
                this.heal(1.0F);
            }
        }
    }

    public static boolean canDestroy(Block block) {
        return block != Blocks.BEDROCK
            && block != Blocks.END_PORTAL
            && block != Blocks.END_PORTAL_FRAME
            && block != Blocks.COMMAND_BLOCK
            && block != Blocks.BARRIER;
    }

    public void onSummoned() {
        this.setInvulnerabilityTimer(220);
        this.setHealth(this.getMaxHealth() / 3.0F);
    }

    @Override
    public void onCobwebCollision() {
    }

    @Override
    public int getArmorProtection() {
        return 4;
    }

    private double getHeadX(int headIndex) {
        if (headIndex <= 0) {
            return this.x;
        }

        float f = (this.bodyYaw + 180 * (headIndex - 1)) / 180.0F * (float) Math.PI;
        float f1 = MathHelper.cos(f);
        return this.x + f1 * 1.3;
    }

    private double getHeadY(int headIndec) {
        return headIndec <= 0 ? this.y + 3.0 : this.y + 2.2;
    }

    private double getHeadZ(int headIndex) {
        if (headIndex <= 0) {
            return this.z;
        }

        float f = (this.bodyYaw + 180 * (headIndex - 1)) / 180.0F * (float) Math.PI;
        float f1 = MathHelper.sin(f);
        return this.z + f1 * 1.3;
    }

    private float getNextAngle(float prevAngle, float desiredAngle, float maxDelta) {
        float f = MathHelper.wrapDegrees(desiredAngle - prevAngle);
        if (f > maxDelta) {
            f = maxDelta;
        }

        if (f < -maxDelta) {
            f = -maxDelta;
        }

        return prevAngle + f;
    }

    private void shootSkullAt(int head, LivingEntity target) {
        this.shootSkullAt(head, target.x, target.y + target.getEyeHeight() * 0.5, target.z, head == 0 && this.random.nextFloat() < 0.001F);
    }

    private void shootSkullAt(int head, double targetX, double targetY, double targetZ, boolean charged) {
        this.world.doEvent(null, 1014, new BlockPos(this), 0);
        double d0 = this.getHeadX(head);
        double d1 = this.getHeadY(head);
        double d2 = this.getHeadZ(head);
        double d3 = targetX - d0;
        double d4 = targetY - d1;
        double d5 = targetZ - d2;
        WitherSkullEntity witherskullentity = new WitherSkullEntity(this.world, this, d3, d4, d5);
        if (charged) {
            witherskullentity.setCharged(true);
        }

        witherskullentity.y = d1;
        witherskullentity.x = d0;
        witherskullentity.z = d2;
        this.world.addEntity(witherskullentity);
    }

    @Override
    public void doRangedAttack(LivingEntity target, float range) {
        this.shootSkullAt(0, target);
    }

    @Override
    public boolean takeDamage(DamageSource source, float amount) {
        if (this.isInvulnerable(source)) {
            return false;
        }

        if (source == DamageSource.DROWN || source.getAttacker() instanceof WitherEntity) {
            return false;
        }

        if (this.getInvulnerabilityTimer() > 0 && source != DamageSource.OUT_OF_WORLD) {
            return false;
        }

        if (this.isAtHalfHealth()) {
            Entity entity = source.getSource();
            if (entity instanceof ArrowEntity) {
                return false;
            }
        }

        Entity entity1 = source.getAttacker();
        if (entity1 != null
            && !(entity1 instanceof PlayerEntity)
            && entity1 instanceof LivingEntity
            && ((LivingEntity)entity1).getMobType() == this.getMobType()) {
            return false;
        }

        if (this.blockBreakingCooldown <= 0) {
            this.blockBreakingCooldown = 20;
        }

        for (int i = 0; i < this.chargedSkullCooldowns.length; i++) {
            this.chargedSkullCooldowns[i] = this.chargedSkullCooldowns[i] + 3;
        }

        return super.takeDamage(source, amount);
    }

    @Override
    protected void dropItems(boolean loot, int lootingMultiplier) {
        ItemEntity itementity = this.dropItem(Items.NETHER_STAR, 1);
        if (itementity != null) {
            itementity.setNeverDespawn();
        }

        if (!this.world.isClient) {
            for (PlayerEntity playerentity : this.world.getEntitiesOfType(PlayerEntity.class, this.getShape().grown(50.0, 100.0, 50.0))) {
                playerentity.incrementStat(Achievements.KILL_WITHER);
            }
        }
    }

    @Override
    protected void checkDespawn() {
        this.farFromPlayerTicks = 0;
    }

    @Override
    public int getLightLevel(float tickDelta) {
        return 15728880;
    }

    @Override
    public void takeFallDamage(float distance, float damageMultiplier) {
    }

    @Override
    public void addStatusEffect(StatusEffectInstance instance) {
    }

    @Override
    protected void initAttributes() {
        super.initAttributes();
        this.getAttribute(EntityAttributes.MAX_HEALTH).setBase(300.0);
        this.getAttribute(EntityAttributes.MOVEMENT_SPEED).setBase(0.6F);
        this.getAttribute(EntityAttributes.FOLLOW_RANGE).setBase(40.0);
    }

    public float getHeadYaw(int id) {
        return this.sideHeadYaws[id];
    }

    public float getHeadPitch(int id) {
        return this.sideHeadPitches[id];
    }

    public int getInvulnerabilityTimer() {
        return this.syncedData.getInt(20);
    }

    public void setInvulnerabilityTimer(int ticks) {
        this.syncedData.update(20, ticks);
    }

    public int getTrackedEntityId(int headIndex) {
        return this.syncedData.getInt(17 + headIndex);
    }

    public void setTrackedEntityId(int headIndex, int id) {
        this.syncedData.update(17 + headIndex, id);
    }

    public boolean isAtHalfHealth() {
        return this.getHealth() <= this.getMaxHealth() / 2.0F;
    }

    @Override
    public MobType getMobType() {
        return MobType.UNDEAD;
    }

    @Override
    public void startRiding(Entity entity) {
        this.vehicle = null;
    }
}
