package net.minecraft.entity.projectile;

import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.mob.monster.EndermiteEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.server.entity.living.player.ServerPlayerEntity;
import net.minecraft.world.HitResult;
import net.minecraft.world.World;

public class EnderPearlEntity extends ThrownEntity {
    private LivingEntity owner;

    public EnderPearlEntity(World world) {
        super(world);
    }

    public EnderPearlEntity(World world, LivingEntity livingEntity) {
        super(world, livingEntity);
        this.owner = livingEntity;
    }

    public EnderPearlEntity(World world, double d, double e, double f) {
        super(world, d, e, f);
    }

    @Override
    protected void onCollision(HitResult result) {
        LivingEntity livingentity = this.getThrower();
        if (result.entity != null) {
            if (result.entity == this.owner) {
                return;
            }

            result.entity.takeDamage(DamageSource.thrown(this, livingentity), 0.0F);
        }

        for (int i = 0; i < 32; i++) {
            this.world
                .addParticle(
                    ParticleType.PORTAL, this.x, this.y + this.random.nextDouble() * 2.0, this.z, this.random.nextGaussian(), 0.0, this.random.nextGaussian()
                );
        }

        if (!this.world.isClient) {
            if (livingentity instanceof ServerPlayerEntity) {
                ServerPlayerEntity serverplayerentity = (ServerPlayerEntity)livingentity;
                if (serverplayerentity.networkHandler.getConnection().isConnected()
                    && serverplayerentity.world == this.world
                    && !serverplayerentity.isSleeping()) {
                    if (this.random.nextFloat() < 0.05F && this.world.getGameRules().getBoolean("doMobSpawning")) {
                        EndermiteEntity endermiteentity = new EndermiteEntity(this.world);
                        endermiteentity.setPlayerSpawned(true);
                        endermiteentity.setPositionAndAngles(livingentity.x, livingentity.y, livingentity.z, livingentity.yaw, livingentity.pitch);
                        this.world.addEntity(endermiteentity);
                    }

                    if (livingentity.isRiding()) {
                        livingentity.startRiding(null);
                    }

                    livingentity.teleport(this.x, this.y, this.z);
                    livingentity.fallDistance = 0.0F;
                    livingentity.takeDamage(DamageSource.FALL, 5.0F);
                }
            } else if (livingentity != null) {
                livingentity.teleport(this.x, this.y, this.z);
                livingentity.fallDistance = 0.0F;
            }

            this.remove();
        }
    }

    @Override
    public void tick() {
        LivingEntity livingentity = this.getThrower();
        if (livingentity != null && livingentity instanceof PlayerEntity && !livingentity.isAlive()) {
            this.remove();
        } else {
            super.tick();
        }
    }
}
