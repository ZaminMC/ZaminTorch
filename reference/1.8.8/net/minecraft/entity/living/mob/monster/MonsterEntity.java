package net.minecraft.entity.living.mob.monster;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.attribute.EntityAttributes;
import net.minecraft.entity.living.mob.PathFinderMobEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Difficulty;
import net.minecraft.world.LightType;
import net.minecraft.world.World;

public abstract class MonsterEntity extends PathFinderMobEntity implements Monster {
    public MonsterEntity(World world) {
        super(world);
        this.xpDrop = 5;
    }

    @Override
    public void mobTick() {
        this.updateArmSwing();
        float f = this.getBrightness(1.0F);
        if (f > 0.5F) {
            this.farFromPlayerTicks += 2;
        }

        super.mobTick();
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.world.isClient && this.world.getDifficulty() == Difficulty.PEACEFUL) {
            this.remove();
        }
    }

    @Override
    protected String getSwimSound() {
        return "game.hostile.swim";
    }

    @Override
    protected String getSplashSound() {
        return "game.hostile.swim.splash";
    }

    @Override
    public boolean takeDamage(DamageSource source, float amount) {
        if (this.isInvulnerable(source)) {
            return false;
        } else if (super.takeDamage(source, amount)) {
            Entity entity = source.getAttacker();
            return this.rider != entity && this.vehicle != entity || true;
        } else {
            return false;
        }
    }

    @Override
    protected String getHurtSound() {
        return "game.hostile.hurt";
    }

    @Override
    protected String getDeathSound() {
        return "game.hostile.die";
    }

    @Override
    protected String getFallSound(int distance) {
        return distance > 4 ? "game.hostile.hurt.fall.big" : "game.hostile.hurt.fall.small";
    }

    @Override
    public boolean tryDamage(Entity target) {
        float f = (float)this.getAttribute(EntityAttributes.ATTACK_DAMAGE).get();
        int i = 0;
        if (target instanceof LivingEntity) {
            f += EnchantmentHelper.modifyDamage(this.getDisplayItemInHand(), ((LivingEntity)target).getMobType());
            i += EnchantmentHelper.getKnockbackLevel(this);
        }

        boolean flag = target.takeDamage(DamageSource.mob(this), f);
        if (flag) {
            if (i > 0) {
                target.addVelocity(
                    -MathHelper.sin(this.yaw * (float) Math.PI / 180.0F) * i * 0.5F, 0.1, MathHelper.cos(this.yaw * (float) Math.PI / 180.0F) * i * 0.5F
                );
                this.velocityX *= 0.6;
                this.velocityZ *= 0.6;
            }

            int j = EnchantmentHelper.getFireAspectLevel(this);
            if (j > 0) {
                target.setOnFireFor(j * 4);
            }

            this.damageEntity(this, target);
        }

        return flag;
    }

    @Override
    public float getPathfindingFavor(BlockPos pos) {
        return 0.5F - this.world.getBrightness(pos);
    }

    protected boolean canSpawnAtLightLevel() {
        BlockPos blockpos = new BlockPos(this.x, this.getShape().minY, this.z);
        if (this.world.getLight(LightType.SKY, blockpos) > this.random.nextInt(32)) {
            return false;
        }

        int i = this.world.getRawBrightness(blockpos);
        if (this.world.isThundering()) {
            int j = this.world.getAmbientDarkness();
            this.world.setAmbientDarkness(10);
            i = this.world.getRawBrightness(blockpos);
            this.world.setAmbientDarkness(j);
        }

        return i <= this.random.nextInt(8);
    }

    @Override
    public boolean canSpawn() {
        return this.world.getDifficulty() != Difficulty.PEACEFUL && this.canSpawnAtLightLevel() && super.canSpawn();
    }

    @Override
    protected void initAttributes() {
        super.initAttributes();
        this.getAttributes().register(EntityAttributes.ATTACK_DAMAGE);
    }

    @Override
    protected boolean isGrownUp() {
        return true;
    }
}
