package net.minecraft.entity.projectile;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.mob.monster.BlazeEntity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.world.HitResult;
import net.minecraft.world.World;

public class SnowballEntity extends ThrownEntity {
    public SnowballEntity(World world) {
        super(world);
    }

    public SnowballEntity(World world, LivingEntity livingEntity) {
        super(world, livingEntity);
    }

    public SnowballEntity(World world, double d, double e, double f) {
        super(world, d, e, f);
    }

    @Override
    protected void onCollision(HitResult result) {
        if (result.entity != null) {
            int i = 0;
            if (result.entity instanceof BlazeEntity) {
                i = 3;
            }

            result.entity.takeDamage(DamageSource.thrown(this, this.getThrower()), i);
        }

        for (int j = 0; j < 8; j++) {
            this.world.addParticle(ParticleType.SNOWBALL, this.x, this.y, this.z, 0.0, 0.0, 0.0);
        }

        if (!this.world.isClient) {
            this.remove();
        }
    }
}
