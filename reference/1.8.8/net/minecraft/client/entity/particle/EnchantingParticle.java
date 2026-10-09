package net.minecraft.client.entity.particle;

import net.minecraft.world.World;

public class EnchantingParticle extends Particle {
    private float baseScale;
    private double startX;
    private double startY;
    private double startZ;

    protected EnchantingParticle(World world, double d, double e, double f, double g, double h, double i) {
        super(world, d, e, f, g, h, i);
        this.velocityX = g;
        this.velocityY = h;
        this.velocityZ = i;
        this.startX = d;
        this.startY = e;
        this.startZ = f;
        this.x = this.lastX = d + g;
        this.y = this.lastY = e + h;
        this.z = this.lastZ = f + i;
        float fx = this.random.nextFloat() * 0.6F + 0.4F;
        this.baseScale = this.size = this.random.nextFloat() * 0.5F + 0.2F;
        this.red = this.green = this.blue = 1.0F * fx;
        this.green *= 0.9F;
        this.red *= 0.9F;
        this.lifetime = (int)(Math.random() * 10.0) + 30;
        this.noClip = true;
        this.setTextureCoordinates((int)(Math.random() * 26.0 + 1.0 + 224.0));
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
        f1 *= f1;
        f1 *= f1;
        return f * (1.0F - f1) + f1;
    }

    @Override
    public void tick() {
        this.lastX = this.x;
        this.lastY = this.y;
        this.lastZ = this.z;
        float f = (float)this.age / this.lifetime;
        f = 1.0F - f;
        float f1 = 1.0F - f;
        f1 *= f1;
        f1 *= f1;
        this.x = this.startX + this.velocityX * f;
        this.y = this.startY + this.velocityY * f - f1 * 1.2F;
        this.z = this.startZ + this.velocityZ * f;
        if (this.age++ >= this.lifetime) {
            this.remove();
        }
    }

    public static class Factory implements ParticleFactory {
        @Override
        public Particle create(int type, World world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, int... parameters) {
            return new EnchantingParticle(world, x, y, z, velocityX, velocityY, velocityZ);
        }
    }
}
