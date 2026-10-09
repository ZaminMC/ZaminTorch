package net.minecraft.entity;

import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;

public class PrimedTntEntity extends Entity {
    public int fuseTimer;
    private LivingEntity igniter;

    public PrimedTntEntity(World world) {
        super(world);
        this.blocksBuilding = true;
        this.setSize(0.98F, 0.98F);
    }

    public PrimedTntEntity(World world, double x, double y, double z, LivingEntity igniter) {
        this(world);
        this.setPosition(x, y, z);
        float f = (float)(Math.random() * (float) Math.PI * 2.0);
        this.velocityX = -((float)Math.sin(f)) * 0.02F;
        this.velocityY = 0.2F;
        this.velocityZ = -((float)Math.cos(f)) * 0.02F;
        this.fuseTimer = 80;
        this.lastX = x;
        this.lastY = y;
        this.lastZ = z;
        this.igniter = igniter;
    }

    @Override
    protected void registerSyncedData() {
    }

    @Override
    protected boolean makesSteps() {
        return false;
    }

    @Override
    public boolean hasCollision() {
        return !this.removed;
    }

    @Override
    public void tick() {
        this.lastX = this.x;
        this.lastY = this.y;
        this.lastZ = this.z;
        this.velocityY -= 0.04F;
        this.move(this.velocityX, this.velocityY, this.velocityZ);
        this.velocityX *= 0.98F;
        this.velocityY *= 0.98F;
        this.velocityZ *= 0.98F;
        if (this.onGround) {
            this.velocityX *= 0.7F;
            this.velocityZ *= 0.7F;
            this.velocityY *= -0.5;
        }

        if (this.fuseTimer-- <= 0) {
            this.remove();
            if (!this.world.isClient) {
                this.explode();
            }
        } else {
            this.checkWaterCollisions();
            this.world.addParticle(ParticleType.SMOKE_NORMAL, this.x, this.y + 0.5, this.z, 0.0, 0.0, 0.0);
        }
    }

    private void explode() {
        float f = 4.0F;
        this.world.explode(this, this.x, this.y + this.height / 16.0F, this.z, f, true);
    }

    @Override
    protected void writeCustomNbt(NbtCompound nbt) {
        nbt.putByte("Fuse", (byte)this.fuseTimer);
    }

    @Override
    protected void readCustomNbt(NbtCompound nbt) {
        this.fuseTimer = nbt.getByte("Fuse");
    }

    public LivingEntity getIgniter() {
        return this.igniter;
    }

    @Override
    public float getEyeHeight() {
        return 0.0F;
    }
}
