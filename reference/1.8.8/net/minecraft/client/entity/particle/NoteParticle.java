package net.minecraft.client.entity.particle;

import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public class NoteParticle extends Particle {
    float initialSize;

    protected NoteParticle(World world, double d, double e, double f, double g, double h, double i) {
        this(world, d, e, f, g, h, i, 2.0F);
    }

    protected NoteParticle(World world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, float scaleFactor) {
        super(world, x, y, z, 0.0, 0.0, 0.0);
        this.velocityX *= 0.01F;
        this.velocityY *= 0.01F;
        this.velocityZ *= 0.01F;
        this.velocityY += 0.2;
        this.red = MathHelper.sin(((float)velocityX + 0.0F) * (float) Math.PI * 2.0F) * 0.65F + 0.35F;
        this.green = MathHelper.sin(((float)velocityX + 0.33333334F) * (float) Math.PI * 2.0F) * 0.65F + 0.35F;
        this.blue = MathHelper.sin(((float)velocityX + 0.6666667F) * (float) Math.PI * 2.0F) * 0.65F + 0.35F;
        this.size *= 0.75F;
        this.size *= scaleFactor;
        this.initialSize = this.size;
        this.lifetime = 6;
        this.noClip = false;
        this.setTextureCoordinates(64);
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

        this.move(this.velocityX, this.velocityY, this.velocityZ);
        if (this.y == this.lastY) {
            this.velocityX *= 1.1;
            this.velocityZ *= 1.1;
        }

        this.velocityX *= 0.66F;
        this.velocityY *= 0.66F;
        this.velocityZ *= 0.66F;
        if (this.onGround) {
            this.velocityX *= 0.7F;
            this.velocityZ *= 0.7F;
        }
    }

    public static class Factory implements ParticleFactory {
        @Override
        public Particle create(int type, World world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, int... parameters) {
            return new NoteParticle(world, x, y, z, velocityX, velocityY, velocityZ);
        }
    }
}
