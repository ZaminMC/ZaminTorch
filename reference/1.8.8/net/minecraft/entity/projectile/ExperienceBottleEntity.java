package net.minecraft.entity.projectile;

import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.HitResult;
import net.minecraft.world.World;

public class ExperienceBottleEntity extends ThrownEntity {
    public ExperienceBottleEntity(World world) {
        super(world);
    }

    public ExperienceBottleEntity(World world, LivingEntity owner) {
        super(world, owner);
    }

    public ExperienceBottleEntity(World world, double x, double y, double z) {
        super(world, x, y, z);
    }

    @Override
    protected float getGravity() {
        return 0.07F;
    }

    @Override
    protected float getPower() {
        return 0.7F;
    }

    @Override
    protected float getStartPitchOffset() {
        return -20.0F;
    }

    @Override
    protected void onCollision(HitResult result) {
        if (!this.world.isClient) {
            this.world.doEvent(2002, new BlockPos(this), 0);
            int i = 3 + this.world.random.nextInt(5) + this.world.random.nextInt(5);

            while (i > 0) {
                int j = ExperienceOrbEntity.roundSize(i);
                i -= j;
                this.world.addEntity(new ExperienceOrbEntity(this.world, this.x, this.y, this.z, j));
            }

            this.remove();
        }
    }
}
