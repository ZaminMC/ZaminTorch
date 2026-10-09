package net.minecraft.entity;

import net.minecraft.entity.particle.ParticleType;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public class FireworksEntity extends Entity {
    private int age;
    private int maxAge;

    public FireworksEntity(World world) {
        super(world);
        this.setSize(0.25F, 0.25F);
    }

    @Override
    protected void registerSyncedData() {
        this.syncedData.add(8, 5);
    }

    @Override
    public boolean shouldRender(double squaredDistanceToCamera) {
        return squaredDistanceToCamera < 4096.0;
    }

    public FireworksEntity(World world, double x, double y, double z, ItemStack item) {
        super(world);
        this.age = 0;
        this.setSize(0.25F, 0.25F);
        this.setPosition(x, y, z);
        int i = 1;
        if (item != null && item.hasNbt()) {
            this.syncedData.update(8, item);
            NbtCompound nbtcompound = item.getNbt();
            NbtCompound nbtcompound1 = nbtcompound.getCompound("Fireworks");
            if (nbtcompound1 != null) {
                i += nbtcompound1.getByte("Flight");
            }
        }

        this.velocityX = this.random.nextGaussian() * 0.001;
        this.velocityZ = this.random.nextGaussian() * 0.001;
        this.velocityY = 0.05;
        this.maxAge = 10 * i + this.random.nextInt(6) + this.random.nextInt(7);
    }

    @Override
    public void lerpVelocity(double velocityX, double velocityY, double velocityZ) {
        this.velocityX = velocityX;
        this.velocityY = velocityY;
        this.velocityZ = velocityZ;
        if (this.lastPitch == 0.0F && this.lastYaw == 0.0F) {
            float f = MathHelper.sqrt(velocityX * velocityX + velocityZ * velocityZ);
            this.lastYaw = this.yaw = (float)(MathHelper.fastAtan2(velocityX, velocityZ) * 180.0 / (float) Math.PI);
            this.lastPitch = this.pitch = (float)(MathHelper.fastAtan2(velocityY, f) * 180.0 / (float) Math.PI);
        }
    }

    @Override
    public void tick() {
        this.prevX = this.x;
        this.prevY = this.y;
        this.prevZ = this.z;
        super.tick();
        this.velocityX *= 1.15;
        this.velocityZ *= 1.15;
        this.velocityY += 0.04;
        this.move(this.velocityX, this.velocityY, this.velocityZ);
        float f = MathHelper.sqrt(this.velocityX * this.velocityX + this.velocityZ * this.velocityZ);
        this.yaw = (float)(MathHelper.fastAtan2(this.velocityX, this.velocityZ) * 180.0 / (float) Math.PI);
        this.pitch = (float)(MathHelper.fastAtan2(this.velocityY, f) * 180.0 / (float) Math.PI);

        while (this.pitch - this.lastPitch < -180.0F) {
            this.lastPitch -= 360.0F;
        }

        while (this.pitch - this.lastPitch >= 180.0F) {
            this.lastPitch += 360.0F;
        }

        while (this.yaw - this.lastYaw < -180.0F) {
            this.lastYaw -= 360.0F;
        }

        while (this.yaw - this.lastYaw >= 180.0F) {
            this.lastYaw += 360.0F;
        }

        this.pitch = this.lastPitch + (this.pitch - this.lastPitch) * 0.2F;
        this.yaw = this.lastYaw + (this.yaw - this.lastYaw) * 0.2F;
        if (this.age == 0 && !this.isSilent()) {
            this.world.playSound(this, "fireworks.launch", 3.0F, 1.0F);
        }

        this.age++;
        if (this.world.isClient && this.age % 2 < 2) {
            this.world
                .addParticle(
                    ParticleType.FIREWORKS_SPARK,
                    this.x,
                    this.y - 0.3,
                    this.z,
                    this.random.nextGaussian() * 0.05,
                    -this.velocityY * 0.5,
                    this.random.nextGaussian() * 0.05
                );
        }

        if (!this.world.isClient && this.age > this.maxAge) {
            this.world.doEntityEvent(this, (byte)17);
            this.remove();
        }
    }

    @Override
    public void doEvent(byte event) {
        if (event == 17 && this.world.isClient) {
            ItemStack itemstack = this.syncedData.getItem(8);
            NbtCompound nbtcompound = null;
            if (itemstack != null && itemstack.hasNbt()) {
                nbtcompound = itemstack.getNbt().getCompound("Fireworks");
            }

            this.world.addFireworksParticle(this.x, this.y, this.z, this.velocityX, this.velocityY, this.velocityZ, nbtcompound);
        }

        super.doEvent(event);
    }

    @Override
    public void writeCustomNbt(NbtCompound nbt) {
        nbt.putInt("Life", this.age);
        nbt.putInt("LifeTime", this.maxAge);
        ItemStack itemstack = this.syncedData.getItem(8);
        if (itemstack != null) {
            NbtCompound nbtcompound = new NbtCompound();
            itemstack.writeNbt(nbtcompound);
            nbt.put("FireworksItem", nbtcompound);
        }
    }

    @Override
    public void readCustomNbt(NbtCompound nbt) {
        this.age = nbt.getInt("Life");
        this.maxAge = nbt.getInt("LifeTime");
        NbtCompound nbtcompound = nbt.getCompound("FireworksItem");
        if (nbtcompound != null) {
            ItemStack itemstack = ItemStack.fromNbt(nbtcompound);
            if (itemstack != null) {
                this.syncedData.update(8, itemstack);
            }
        }
    }

    @Override
    public float getBrightness(float tickDelta) {
        return super.getBrightness(tickDelta);
    }

    @Override
    public int getLightLevel(float tickDelta) {
        return super.getLightLevel(tickDelta);
    }

    @Override
    public boolean canBePunched() {
        return false;
    }
}
