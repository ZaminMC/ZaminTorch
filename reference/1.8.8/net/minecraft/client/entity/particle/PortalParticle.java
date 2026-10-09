package net.minecraft.client.entity.particle;

import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;

public class PortalParticle extends Particle {
    private float initialSize;
    private double initialX;
    private double initialY;
    private double initialZ;

    protected PortalParticle(World world, double d, double e, double f, double g, double h, double i) {
        super(world, d, e, f, g, h, i);
        this.velocityX = g;
        this.velocityY = h;
        this.velocityZ = i;
        this.initialX = this.x = d;
        this.initialY = this.y = e;
        this.initialZ = this.z = f;
        float fx = this.random.nextFloat() * 0.6F + 0.4F;
        this.initialSize = this.size = this.random.nextFloat() * 0.2F + 0.5F;
        this.red = this.green = this.blue = 1.0F * fx;
        this.green *= 0.3F;
        this.red *= 0.9F;
        this.lifetime = (int)(Math.random() * 10.0) + 40;
        this.noClip = true;
        this.setTextureCoordinates((int)(Math.random() * 8.0));
    }

    @Override
    public void render(BufferBuilder bufferBuilder, Entity camera, float tickDelta, float dx, float dy, float dz, float forwards, float sideways) {
        float f = (this.age + tickDelta) / this.lifetime;
        f = 1.0F - f;
        f *= f;
        f = 1.0F - f;
        this.size = this.initialSize * f;
        super.render(bufferBuilder, camera, tickDelta, dx, dy, dz, forwards, sideways);
    }

    @Override
    public int getLightLevel(float tickDelta) {
        int i = super.getLightLevel(tickDelta);
        float f = (float)this.age / this.lifetime;
        f *= f;
        f *= f;
        int j = i & 0xFF;
        int k = i >> 16 & 0xFF;
        k += (int)(f * 15.0F * 16.0F);
        if (k > 240) {
            k = 240;
        }

        return j | k << 16;
    }

    @Override
    public float getBrightness(float tickDelta) {
        float f = super.getBrightness(tickDelta);
        float f1 = (float)this.age / this.lifetime;
        f1 = f1 * f1 * f1 * f1;
        return f * (1.0F - f1) + f1;
    }

    @Override
    public void tick() {
        this.lastX = this.x;
        this.lastY = this.y;
        this.lastZ = this.z;
        float f = (float)this.age / this.lifetime;
        float f1 = f;
        f = -f + f * f * 2.0F;
        f = 1.0F - f;
        this.x = this.initialX + this.velocityX * f;
        this.y = this.initialY + this.velocityY * f + (1.0F - f1);
        this.z = this.initialZ + this.velocityZ * f;
        if (this.age++ >= this.lifetime) {
            this.remove();
        }
    }

    public static class Factory implements ParticleFactory {
        @Override
        public Particle create(int type, World world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, int... parameters) {
            return new PortalParticle(world, x, y, z, velocityX, velocityY, velocityZ);
        }
    }
}
