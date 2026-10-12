package net.minecraft.client.entity.particle;

import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.world.World;

public class EmitterParticle extends Particle {
    private Entity target;
    private int age;
    private int lifetime;
    private ParticleType type;

    public EmitterParticle(World world, Entity target, ParticleType type) {
        super(world, target.x, target.getShape().minY + target.height / 2.0F, target.z, target.velocityX, target.velocityY, target.velocityZ);
        this.target = target;
        this.lifetime = 3;
        this.type = type;
        this.tick();
    }

    @Override
    public void render(BufferBuilder bufferBuilder, Entity camera, float tickDelta, float dx, float dy, float dz, float forwards, float sideways) {
    }

    @Override
    public void tick() {
        for (int i = 0; i < 16; i++) {
            double d0 = this.random.nextFloat() * 2.0F - 1.0F;
            double d1 = this.random.nextFloat() * 2.0F - 1.0F;
            double d2 = this.random.nextFloat() * 2.0F - 1.0F;
            if (!(d0 * d0 + d1 * d1 + d2 * d2 > 1.0)) {
                double d3 = this.target.x + d0 * this.target.width / 4.0;
                double d4 = this.target.getShape().minY + this.target.height / 2.0F + d1 * this.target.height / 4.0;
                double d5 = this.target.z + d2 * this.target.width / 4.0;
                this.world.addParticle(this.type, false, d3, d4, d5, d0, d1 + 0.2, d2);
            }
        }

        this.age++;
        if (this.age >= this.lifetime) {
            this.remove();
        }
    }

    @Override
    public int getAtlasType() {
        return 3;
    }
}
