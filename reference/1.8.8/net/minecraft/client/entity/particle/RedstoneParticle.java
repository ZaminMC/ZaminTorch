package net.minecraft.client.entity.particle;

import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public class RedstoneParticle extends Particle {
    float initialSize;

    protected RedstoneParticle(World world, double x, double y, double z, float r, float g, float b) {
        this(world, x, y, z, 1.0F, r, g, b);
    }

    protected RedstoneParticle(World world, double x, double y, double z, float scale, float r, float g, float b) {
        super(world, x, y, z, 0.0, 0.0, 0.0);
        this.velocityX *= 0.1F;
        this.velocityY *= 0.1F;
        this.velocityZ *= 0.1F;
        if (r == 0.0F) {
            r = 1.0F;
        }

        float f = (float)Math.random() * 0.4F + 0.6F;
        this.red = ((float)(Math.random() * 0.2F) + 0.8F) * r * f;
        this.green = ((float)(Math.random() * 0.2F) + 0.8F) * g * f;
        this.blue = ((float)(Math.random() * 0.2F) + 0.8F) * b * f;
        this.size *= 0.75F;
        this.size *= scale;
        this.initialSize = this.size;
        this.lifetime = (int)(8.0 / (Math.random() * 0.8 + 0.2));
        this.lifetime = (int)(this.lifetime * scale);
        this.noClip = false;
    }

    @Override
    public void render(BufferBuilder bufferBuilder, Entity camera, float tickDelta, float dx, float dy, float dz, float forwards, float sideways) {
        float f = (this.age + tickDelta) / this.lifetime * 32.0F;
        f = MathHelper.clamp(f, 0.0F, 1.0F);
        this.size = this.initialSize * f;
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

        this.setTextureCoordinates(7 - this.age * 8 / this.lifetime);
        this.move(this.velocityX, this.velocityY, this.velocityZ);
        if (this.y == this.lastY) {
            this.velocityX *= 1.1;
            this.velocityZ *= 1.1;
        }

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
            return new RedstoneParticle(world, x, y, z, (float)velocityX, (float)velocityY, (float)velocityZ);
        }
    }
}
