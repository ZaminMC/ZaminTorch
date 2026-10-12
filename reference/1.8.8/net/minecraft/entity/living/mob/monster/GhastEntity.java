package net.minecraft.entity.living.mob.monster;

import java.util.Random;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.MobEntityPlayerTargetGoal;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.attribute.EntityAttributes;
import net.minecraft.entity.living.mob.FlyingMobEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.stat.achievement.Achievements;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.World;

public class GhastEntity extends FlyingMobEntity implements Monster {
    private int fireballStrength = 1;

    public GhastEntity(World world) {
        super(world);
        this.setSize(4.0F, 4.0F);
        this.immuneToFire = true;
        this.xpDrop = 5;
        this.movementControl = new GhastEntity.MovementControl(this);
        this.goalSelector.addGoal(5, new GhastEntity.MoveAroundGoal(this));
        this.goalSelector.addGoal(7, new GhastEntity.LookAroundGoal(this));
        this.goalSelector.addGoal(7, new GhastEntity.ShootFireballGoal(this));
        this.targetSelector.addGoal(1, new MobEntityPlayerTargetGoal(this));
    }

    public boolean isCharging() {
        return this.syncedData.getByte(16) != 0;
    }

    public void setCharging(boolean charging) {
        this.syncedData.update(16, Byte.valueOf((byte)(charging ? 1 : 0)));
    }

    public int getFireballStrength() {
        return this.fireballStrength;
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.world.isClient && this.world.getDifficulty() == Difficulty.PEACEFUL) {
            this.remove();
        }
    }

    @Override
    public boolean takeDamage(DamageSource source, float amount) {
        if (this.isInvulnerable(source)) {
            return false;
        } else if ("fireball".equals(source.getName()) && source.getAttacker() instanceof PlayerEntity) {
            super.takeDamage(source, 1000.0F);
            ((PlayerEntity)source.getAttacker()).incrementStat(Achievements.KILL_GHAST_WITH_FIREBALL);
            return true;
        } else {
            return super.takeDamage(source, amount);
        }
    }

    @Override
    protected void registerSyncedData() {
        super.registerSyncedData();
        this.syncedData.register(16, (byte)0);
    }

    @Override
    protected void initAttributes() {
        super.initAttributes();
        this.getAttribute(EntityAttributes.MAX_HEALTH).setBase(10.0);
        this.getAttribute(EntityAttributes.FOLLOW_RANGE).setBase(100.0);
    }

    @Override
    protected String getAmbientSound() {
        return "mob.ghast.moan";
    }

    @Override
    protected String getHurtSound() {
        return "mob.ghast.scream";
    }

    @Override
    protected String getDeathSound() {
        return "mob.ghast.death";
    }

    @Override
    protected Item getDropItem() {
        return Items.GUNPOWDER;
    }

    @Override
    protected void dropItems(boolean loot, int lootingMultiplier) {
        int i = this.random.nextInt(2) + this.random.nextInt(1 + lootingMultiplier);

        for (int j = 0; j < i; j++) {
            this.dropItem(Items.GHAST_TEAR, 1);
        }

        i = this.random.nextInt(3) + this.random.nextInt(1 + lootingMultiplier);

        for (int k = 0; k < i; k++) {
            this.dropItem(Items.GUNPOWDER, 1);
        }
    }

    @Override
    protected float getSoundVolume() {
        return 10.0F;
    }

    @Override
    public boolean canSpawn() {
        return this.random.nextInt(20) == 0 && super.canSpawn() && this.world.getDifficulty() != Difficulty.PEACEFUL;
    }

    @Override
    public int getLimitPerChunk() {
        return 1;
    }

    @Override
    public void writeCustomNbt(NbtCompound nbt) {
        super.writeCustomNbt(nbt);
        nbt.putInt("ExplosionPower", this.fireballStrength);
    }

    @Override
    public void readCustomNbt(NbtCompound nbt) {
        super.readCustomNbt(nbt);
        if (nbt.contains("ExplosionPower", 99)) {
            this.fireballStrength = nbt.getInt("ExplosionPower");
        }
    }

    @Override
    public float getEyeHeight() {
        return 2.6F;
    }

    static class LookAroundGoal extends Goal {
        private GhastEntity ghast;

        public LookAroundGoal(GhastEntity ghast) {
            this.ghast = ghast;
            this.setControls(2);
        }

        @Override
        public boolean canStart() {
            return true;
        }

        @Override
        public void tick() {
            if (this.ghast.getAttackTarget() == null) {
                this.ghast.bodyYaw = this.ghast.yaw = -((float)MathHelper.fastAtan2(this.ghast.velocityX, this.ghast.velocityZ)) * 180.0F / (float) Math.PI;
            } else {
                LivingEntity livingentity = this.ghast.getAttackTarget();
                double d0 = 64.0;
                if (livingentity.squaredDistanceTo(this.ghast) < d0 * d0) {
                    double d1 = livingentity.x - this.ghast.x;
                    double d2 = livingentity.z - this.ghast.z;
                    this.ghast.bodyYaw = this.ghast.yaw = -((float)MathHelper.fastAtan2(d1, d2)) * 180.0F / (float) Math.PI;
                }
            }
        }
    }

    static class MoveAroundGoal extends Goal {
        private GhastEntity ghast;

        public MoveAroundGoal(GhastEntity ghast) {
            this.ghast = ghast;
            this.setControls(1);
        }

        @Override
        public boolean canStart() {
            net.minecraft.entity.ai.control.MovementControl movementcontrol = this.ghast.getMovementControl();
            if (!movementcontrol.isMoving()) {
                return true;
            }

            double d0 = movementcontrol.getX() - this.ghast.x;
            double d1 = movementcontrol.getY() - this.ghast.y;
            double d2 = movementcontrol.getZ() - this.ghast.z;
            double d3 = d0 * d0 + d1 * d1 + d2 * d2;
            return d3 < 1.0 || d3 > 3600.0;
        }

        @Override
        public boolean shouldContinue() {
            return false;
        }

        @Override
        public void start() {
            Random random = this.ghast.getRandom();
            double d0 = this.ghast.x + (random.nextFloat() * 2.0F - 1.0F) * 16.0F;
            double d1 = this.ghast.y + (random.nextFloat() * 2.0F - 1.0F) * 16.0F;
            double d2 = this.ghast.z + (random.nextFloat() * 2.0F - 1.0F) * 16.0F;
            this.ghast.getMovementControl().update(d0, d1, d2, 1.0);
        }
    }

    static class MovementControl extends net.minecraft.entity.ai.control.MovementControl {
        private GhastEntity ghast;
        private int cooldown;

        public MovementControl(GhastEntity ghast) {
            super(ghast);
            this.ghast = ghast;
        }

        @Override
        public void tick() {
            if (this.moving) {
                double d0 = this.x - this.ghast.x;
                double d1 = this.y - this.ghast.y;
                double d2 = this.z - this.ghast.z;
                double d3 = d0 * d0 + d1 * d1 + d2 * d2;
                if (this.cooldown-- <= 0) {
                    this.cooldown = this.cooldown + this.ghast.getRandom().nextInt(5) + 2;
                    d3 = MathHelper.sqrt(d3);
                    if (this.hasLineOfSight(this.x, this.y, this.z, d3)) {
                        this.ghast.velocityX += d0 / d3 * 0.1;
                        this.ghast.velocityY += d1 / d3 * 0.1;
                        this.ghast.velocityZ += d2 / d3 * 0.1;
                    } else {
                        this.moving = false;
                    }
                }
            }
        }

        private boolean hasLineOfSight(double targetX, double targetY, double targetZ, double range) {
            double d0 = (targetX - this.ghast.x) / range;
            double d1 = (targetY - this.ghast.y) / range;
            double d2 = (targetZ - this.ghast.z) / range;
            Box box = this.ghast.getShape();

            for (int i = 1; i < range; i++) {
                box = box.moved(d0, d1, d2);
                if (!this.ghast.world.getCollisions(this.ghast, box).isEmpty()) {
                    return false;
                }
            }

            return true;
        }
    }

    static class ShootFireballGoal extends Goal {
        private GhastEntity ghast;
        public int chargeTicks;

        public ShootFireballGoal(GhastEntity ghast) {
            this.ghast = ghast;
        }

        @Override
        public boolean canStart() {
            return this.ghast.getAttackTarget() != null;
        }

        @Override
        public void start() {
            this.chargeTicks = 0;
        }

        @Override
        public void stop() {
            this.ghast.setCharging(false);
        }

        @Override
        public void tick() {
            LivingEntity livingentity = this.ghast.getAttackTarget();
            double d0 = 64.0;
            if (livingentity.squaredDistanceTo(this.ghast) < d0 * d0 && this.ghast.canSee(livingentity)) {
                World world = this.ghast.world;
                this.chargeTicks++;
                if (this.chargeTicks == 10) {
                    world.doEvent(null, 1007, new BlockPos(this.ghast), 0);
                }

                if (this.chargeTicks == 20) {
                    double d1 = 4.0;
                    Vec3d vec3d = this.ghast.getRotationVec(1.0F);
                    double d2 = livingentity.x - (this.ghast.x + vec3d.x * d1);
                    double d3 = livingentity.getShape().minY + livingentity.height / 2.0F - (0.5 + this.ghast.y + this.ghast.height / 2.0F);
                    double d4 = livingentity.z - (this.ghast.z + vec3d.z * d1);
                    world.doEvent(null, 1008, new BlockPos(this.ghast), 0);
                    FireballEntity fireballentity = new FireballEntity(world, this.ghast, d2, d3, d4);
                    fireballentity.explosionPower = this.ghast.getFireballStrength();
                    fireballentity.x = this.ghast.x + vec3d.x * d1;
                    fireballentity.y = this.ghast.y + this.ghast.height / 2.0F + 0.5;
                    fireballentity.z = this.ghast.z + vec3d.z * d1;
                    world.addEntity(fireballentity);
                    this.chargeTicks = -40;
                }
            } else if (this.chargeTicks > 0) {
                this.chargeTicks--;
            }

            this.ghast.setCharging(this.chargeTicks > 10);
        }
    }
}
