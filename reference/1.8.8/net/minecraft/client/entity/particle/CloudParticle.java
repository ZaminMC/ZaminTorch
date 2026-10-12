package net.minecraft.client.entity.particle;

import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public class CloudParticle extends Particle {
    float baseScale;

    protected CloudParticle(World world, double d, double e, double f, double g, double h, double i) {
        super(world, d, e, f, 0.0, 0.0, 0.0);
        float fx = 2.5F;
        this.velocityX *= 0.1F;
        this.velocityY *= 0.1F;
        this.velocityZ *= 0.1F;
        this.velocityX += g;
        this.velocityY += h;
        this.velocityZ += i;
        this.red = this.green = this.blue = 1.0F - (float)(Math.random() * 0.3F);
        this.size *= 0.75F;
        this.size *= fx;
        this.baseScale = this.size;
        this.lifetime = (int)(8.0 / (Math.random() * 0.8 + 0.3));
        this.lifetime = (int)(this.lifetime * fx);
        this.noClip = false;
    }

    @Override
    public void render(BufferBuilder bufferBuilder, Entity camera, float tickDelta, float dx, float dy, float dz, float forwards, float sideways) {
        float f = (this.age + tickDelta) / this.lifetime * 32.0F;
        f = MathHelper.clamp(f, 0.0F, 1.0F);
        this.size = this.baseScale * f;
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
        this.velocityX *= 0.96F;
        this.velocityY *= 0.96F;
        this.velocityZ *= 0.96F;
        PlayerEntity playerentity = this.world.getNearestPlayer(this, 2.0);
        if (playerentity != null && this.y > playerentity.getShape().minY) {
            this.y = this.y + (playerentity.getShape().minY - this.y) * 0.2;
            this.velocityY = this.velocityY + (playerentity.velocityY - this.velocityY) * 0.2;
            this.setPosition(this.x, this.y, this.z);
        }

        if (this.onGround) {
            this.velocityX *= 0.7F;
            this.velocityZ *= 0.7F;
        }
    }

    public static class Factory implements ParticleFactory {
        @Override
        public Particle create(int type, World world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, int... parameters) {
            return new CloudParticle(world, x, y, z, velocityX, velocityY, velocityZ);
        }
    }
}
