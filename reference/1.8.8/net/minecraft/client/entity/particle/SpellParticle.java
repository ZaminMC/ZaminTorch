package net.minecraft.client.entity.particle;

import java.util.Random;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public class SpellParticle extends Particle {
    private static final Random RANDOM = new Random();
    private int miscTexOffset = 128;

    protected SpellParticle(World world, double d, double e, double f, double g, double h, double i) {
        super(world, d, e, f, 0.5 - RANDOM.nextDouble(), h, 0.5 - RANDOM.nextDouble());
        this.velocityY *= 0.2F;
        if (g == 0.0 && i == 0.0) {
            this.velocityX *= 0.1F;
            this.velocityZ *= 0.1F;
        }

        this.size *= 0.75F;
        this.lifetime = (int)(8.0 / (Math.random() * 0.8 + 0.2));
        this.noClip = false;
    }

    @Override
    public void render(BufferBuilder bufferBuilder, Entity camera, float tickDelta, float dx, float dy, float dz, float forwards, float sideways) {
        float f = (this.age + tickDelta) / this.lifetime * 32.0F;
        f = MathHelper.clamp(f, 0.0F, 1.0F);
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

        this.setTextureCoordinates(this.miscTexOffset + (7 - this.age * 8 / this.lifetime));
        this.velocityY += 0.004;
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

    public void setMiscTexOffset(int offset) {
        this.miscTexOffset = offset;
    }

    public static class Factory implements ParticleFactory {
        @Override
        public Particle create(int type, World world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, int... parameters) {
            return new SpellParticle(world, x, y, z, velocityX, velocityY, velocityZ);
        }
    }

    public static class InstantFactory implements ParticleFactory {
        @Override
        public Particle create(int type, World world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, int... parameters) {
            Particle particle = new SpellParticle(world, x, y, z, velocityX, velocityY, velocityZ);
            ((SpellParticle)particle).setMiscTexOffset(144);
            return particle;
        }
    }

    public static class MobAmbientFactory implements ParticleFactory {
        @Override
        public Particle create(int type, World world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, int... parameters) {
            Particle particle = new SpellParticle(world, x, y, z, velocityX, velocityY, velocityZ);
            particle.setAlpha(0.15F);
            particle.setColor((float)velocityX, (float)velocityY, (float)velocityZ);
            return particle;
        }
    }

    public static class MobFactory implements ParticleFactory {
        @Override
        public Particle create(int type, World world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, int... parameters) {
            Particle particle = new SpellParticle(world, x, y, z, velocityX, velocityY, velocityZ);
            particle.setColor((float)velocityX, (float)velocityY, (float)velocityZ);
            return particle;
        }
    }

    public static class WitchFactory implements ParticleFactory {
        @Override
        public Particle create(int type, World world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, int... parameters) {
            Particle particle = new SpellParticle(world, x, y, z, velocityX, velocityY, velocityZ);
            ((SpellParticle)particle).setMiscTexOffset(144);
            float f = world.random.nextFloat() * 0.5F + 0.35F;
            particle.setColor(1.0F * f, 0.0F * f, 1.0F * f);
            return particle;
        }
    }
}
