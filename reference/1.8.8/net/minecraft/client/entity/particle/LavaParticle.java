package net.minecraft.client.entity.particle;

import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public class LavaParticle extends Particle {
    private float initialSize;

    protected LavaParticle(World world, double x, double y, double z) {
        super(world, x, y, z, 0.0, 0.0, 0.0);
        this.velocityX *= 0.8F;
        this.velocityY *= 0.8F;
        this.velocityZ *= 0.8F;
        this.velocityY = this.random.nextFloat() * 0.4F + 0.05F;
        this.red = this.green = this.blue = 1.0F;
        this.size = this.size * (this.random.nextFloat() * 2.0F + 0.2F);
        this.initialSize = this.size;
        this.lifetime = (int)(16.0 / (Math.random() * 0.8 + 0.2));
        this.noClip = false;
        this.setTextureCoordinates(49);
    }

    @Override
    public int getLightLevel(float tickDelta) {
        float f = (this.age + tickDelta) / this.lifetime;
        f = MathHelper.clamp(f, 0.0F, 1.0F);
        int i = super.getLightLevel(tickDelta);
        int j = 240;
        int k = i >> 16 & 0xFF;
        return j | k << 16;
    }

    @Override
    public float getBrightness(float tickDelta) {
        return 1.0F;
    }

    @Override
    public void render(BufferBuilder bufferBuilder, Entity camera, float tickDelta, float dx, float dy, float dz, float forwards, float sideways) {
        float f = (this.age + tickDelta) / this.lifetime;
        this.size = this.initialSize * (1.0F - f * f);
        super.render(bufferBuilder, camera, tickDelta, dx, dy, dz, forwards, sideways);
    }

    @Override
    public void tick() {
        this.lastX = this.x;
        this.lastY = this.y;
        this.lastZ = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
        }

        float f = (float)this.age / this.lifetime;
        if (this.random.nextFloat() > f) {
            this.world.addParticle(ParticleType.SMOKE_NORMAL, this.x, this.y, this.z, this.velocityX, this.velocityY, this.velocityZ);
        }

        this.velocityY -= 0.03;
        this.move(this.velocityX, this.velocityY, this.velocityZ);
        this.velocityX *= 0.999F;
        this.velocityY *= 0.999F;
        this.velocityZ *= 0.999F;
        if (this.onGround) {
            this.velocityX *= 0.7F;
            this.velocityZ *= 0.7F;
        }
    }

    public static class Factory implements ParticleFactory {
        @Override
        public Particle create(int type, World world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, int... parameters) {
            return new LavaParticle(world, x, y, z);
        }
    }
}
