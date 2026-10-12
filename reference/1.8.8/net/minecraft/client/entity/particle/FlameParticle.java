package net.minecraft.client.entity.particle;

import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public class FlameParticle extends Particle {
    private float initialSize;

    protected FlameParticle(World world, double d, double e, double f, double g, double h, double i) {
        super(world, d, e, f, g, h, i);
        this.velocityX = this.velocityX * 0.01F + g;
        this.velocityY = this.velocityY * 0.01F + h;
        this.velocityZ = this.velocityZ * 0.01F + i;
        this.x = this.x + (this.random.nextFloat() - this.random.nextFloat()) * 0.05F;
        this.y = this.y + (this.random.nextFloat() - this.random.nextFloat()) * 0.05F;
        this.z = this.z + (this.random.nextFloat() - this.random.nextFloat()) * 0.05F;
        this.initialSize = this.size;
        this.red = this.green = this.blue = 1.0F;
        this.lifetime = (int)(8.0 / (Math.random() * 0.8 + 0.2)) + 4;
        this.noClip = true;
        this.setTextureCoordinates(48);
    }

    @Override
    public void render(BufferBuilder bufferBuilder, Entity camera, float tickDelta, float dx, float dy, float dz, float forwards, float sideways) {
        float f = (this.age + tickDelta) / this.lifetime;
        this.size = this.initialSize * (1.0F - f * f * 0.5F);
        super.render(bufferBuilder, camera, tickDelta, dx, dy, dz, forwards, sideways);
    }

    @Override
    public int getLightLevel(float tickDelta) {
        float f = (this.age + tickDelta) / this.lifetime;
        f = MathHelper.clamp(f, 0.0F, 1.0F);
        int i = super.getLightLevel(tickDelta);
        int j = i & 0xFF;
        int k = i >> 16 & 0xFF;
        j += (int)(f * 15.0F * 16.0F);
        if (j > 240) {
            j = 240;
        }

        return j | k << 16;
    }

    @Override
    public float getBrightness(float tickDelta) {
        float f = (this.age + tickDelta) / this.lifetime;
        f = MathHelper.clamp(f, 0.0F, 1.0F);
        float f1 = super.getBrightness(tickDelta);
        return f1 * f + (1.0F - f);
    }

    @Override
    public void tick() {
        this.lastX = this.x;
        this.lastY = this.y;
        this.lastZ = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
        }

        this.move(this.velocityX, this.velocityY, this.velocityZ);
        this.velocityX *= 0.96F;
        this.velocityY *= 0.96F;
        this.velocityZ *= 0.96F;
        if (this.onGround) {
            this.velocityX *= 0.7F;
            this.velocityZ *= 0.7F;
        }
    }

    public static class Factory implements ParticleFactory {
        @Override
        public Particle create(int type, World world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, int... parameters) {
            return new FlameParticle(world, x, y, z, velocityX, velocityY, velocityZ);
        }
    }
}
