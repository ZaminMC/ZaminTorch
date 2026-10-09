package net.minecraft.entity.projectile;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.mob.passive.animal.ChickenEntity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.world.HitResult;
import net.minecraft.world.World;

public class EggEntity extends ThrownEntity {
    public EggEntity(World world) {
        super(world);
    }

    public EggEntity(World world, LivingEntity livingEntity) {
        super(world, livingEntity);
    }

    public EggEntity(World world, double d, double e, double f) {
        super(world, d, e, f);
    }

    @Override
    protected void onCollision(HitResult result) {
        if (result.entity != null) {
            result.entity.takeDamage(DamageSource.thrown(this, this.getThrower()), 0.0F);
        }

        if (!this.world.isClient && this.random.nextInt(8) == 0) {
            int i = 1;
            if (this.random.nextInt(32) == 0) {
                i = 4;
            }

            for (int j = 0; j < i; j++) {
                ChickenEntity chickenentity = new ChickenEntity(this.world);
                chickenentity.setBreedingAge(-24000);
                chickenentity.setPositionAndAngles(this.x, this.y, this.z, this.yaw, 0.0F);
                this.world.addEntity(chickenentity);
            }
        }

        double d0 = 0.08;

        for (int k = 0; k < 8; k++) {
            this.world
                .addParticle(
                    ParticleType.ITEM_CRACK,
                    this.x,
                    this.y,
                    this.z,
                    (this.random.nextFloat() - 0.5) * 0.08,
                    (this.random.nextFloat() - 0.5) * 0.08,
                    (this.random.nextFloat() - 0.5) * 0.08,
                    Item.getId(Items.EGG)
                );
        }

        if (!this.world.isClient) {
            this.remove();
        }
    }
}
