package net.minecraft.client.entity.particle;

import net.minecraft.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.world.World;

public class BarrierParticle extends Particle {
    protected BarrierParticle(World world, double x, double y, double z, Item item) {
        super(world, x, y, z, 0.0, 0.0, 0.0);
        this.setTexture(Minecraft.getInstance().getItemRenderer().getModelShaper().getParticleIcon(item));
        this.red = this.green = this.blue = 1.0F;
        this.velocityX = this.velocityY = this.velocityZ = 0.0;
        this.gravity = 0.0F;
        this.lifetime = 80;
    }

    @Override
    public int getAtlasType() {
        return 1;
    }

    @Override
    public void render(BufferBuilder bufferBuilder, Entity camera, float tickDelta, float dx, float dy, float dz, float forwards, float sideways) {
        float f = this.sprite.getUMin();
        float f1 = this.sprite.getUMax();
        float f2 = this.sprite.getVMin();
        float f3 = this.sprite.getVMax();
        float f4 = 0.5F;
        float f5 = (float)(this.lastX + (this.x - this.lastX) * tickDelta - lerpCameraX);
        float f6 = (float)(this.lastY + (this.y - this.lastY) * tickDelta - lerpCameraY);
        float f7 = (float)(this.lastZ + (this.z - this.lastZ) * tickDelta - lerpCameraZ);
        int i = this.getLightLevel(tickDelta);
        int j = i >> 16 & 65535;
        int k = i & 65535;
        bufferBuilder.vertex(f5 - dx * 0.5F - forwards * 0.5F, f6 - dy * 0.5F, f7 - dz * 0.5F - sideways * 0.5F)
            .texture(f1, f3)
            .color(this.red, this.green, this.blue, 1.0F)
            .texture(j, k)
            .nextVertex();
        bufferBuilder.vertex(f5 - dx * 0.5F + forwards * 0.5F, f6 + dy * 0.5F, f7 - dz * 0.5F + sideways * 0.5F)
            .texture(f1, f2)
            .color(this.red, this.green, this.blue, 1.0F)
            .texture(j, k)
            .nextVertex();
        bufferBuilder.vertex(f5 + dx * 0.5F + forwards * 0.5F, f6 + dy * 0.5F, f7 + dz * 0.5F + sideways * 0.5F)
            .texture(f, f2)
            .color(this.red, this.green, this.blue, 1.0F)
            .texture(j, k)
            .nextVertex();
        bufferBuilder.vertex(f5 + dx * 0.5F - forwards * 0.5F, f6 - dy * 0.5F, f7 + dz * 0.5F - sideways * 0.5F)
            .texture(f, f3)
            .color(this.red, this.green, this.blue, 1.0F)
            .texture(j, k)
            .nextVertex();
    }

    public static class Factory implements ParticleFactory {
        @Override
        public Particle create(int type, World world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, int... parameters) {
            return new BarrierParticle(world, x, y, z, Item.byBlock(Blocks.BARRIER));
        }
    }
}
