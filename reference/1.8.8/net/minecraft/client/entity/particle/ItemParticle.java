package net.minecraft.client.entity.particle;

import net.minecraft.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.world.World;

public class ItemParticle extends Particle {
    protected ItemParticle(World world, double x, double y, double z, Item item) {
        this(world, x, y, z, item, 0);
    }

    protected ItemParticle(World world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, Item item, int metadata) {
        this(world, x, y, z, item, metadata);
        this.velocityX *= 0.1F;
        this.velocityY *= 0.1F;
        this.velocityZ *= 0.1F;
        this.velocityX += velocityX;
        this.velocityY += velocityY;
        this.velocityZ += velocityZ;
    }

    protected ItemParticle(World world, double x, double y, double z, Item item, int metadata) {
        super(world, x, y, z, 0.0, 0.0, 0.0);
        this.setTexture(Minecraft.getInstance().getItemRenderer().getModelShaper().getParticleIcon(item, metadata));
        this.red = this.green = this.blue = 1.0F;
        this.gravity = Blocks.SNOW.gravity;
        this.size /= 2.0F;
    }

    @Override
    public int getAtlasType() {
        return 1;
    }

    @Override
    public void render(BufferBuilder bufferBuilder, Entity camera, float tickDelta, float dx, float dy, float dz, float forwards, float sideways) {
        float f = (this.spriteRow + this.offsetU / 4.0F) / 16.0F;
        float f1 = f + 0.015609375F;
        float f2 = (this.spriteColumn + this.offsetV / 4.0F) / 16.0F;
        float f3 = f2 + 0.015609375F;
        float f4 = 0.1F * this.size;
        if (this.sprite != null) {
            f = this.sprite.getU(this.offsetU / 4.0F * 16.0F);
            f1 = this.sprite.getU((this.offsetU + 1.0F) / 4.0F * 16.0F);
            f2 = this.sprite.getV(this.offsetV / 4.0F * 16.0F);
            f3 = this.sprite.getV((this.offsetV + 1.0F) / 4.0F * 16.0F);
        }

        float f5 = (float)(this.lastX + (this.x - this.lastX) * tickDelta - lerpCameraX);
        float f6 = (float)(this.lastY + (this.y - this.lastY) * tickDelta - lerpCameraY);
        float f7 = (float)(this.lastZ + (this.z - this.lastZ) * tickDelta - lerpCameraZ);
        int i = this.getLightLevel(tickDelta);
        int j = i >> 16 & 65535;
        int k = i & 65535;
        bufferBuilder.vertex(f5 - dx * f4 - forwards * f4, f6 - dy * f4, f7 - dz * f4 - sideways * f4)
            .texture(f, f3)
            .color(this.red, this.green, this.blue, 1.0F)
            .texture(j, k)
            .nextVertex();
        bufferBuilder.vertex(f5 - dx * f4 + forwards * f4, f6 + dy * f4, f7 - dz * f4 + sideways * f4)
            .texture(f, f2)
            .color(this.red, this.green, this.blue, 1.0F)
            .texture(j, k)
            .nextVertex();
        bufferBuilder.vertex(f5 + dx * f4 + forwards * f4, f6 + dy * f4, f7 + dz * f4 + sideways * f4)
            .texture(f1, f2)
            .color(this.red, this.green, this.blue, 1.0F)
            .texture(j, k)
            .nextVertex();
        bufferBuilder.vertex(f5 + dx * f4 - forwards * f4, f6 - dy * f4, f7 + dz * f4 - sideways * f4)
            .texture(f1, f3)
            .color(this.red, this.green, this.blue, 1.0F)
            .texture(j, k)
            .nextVertex();
    }

    public static class Factory implements ParticleFactory {
        @Override
        public Particle create(int type, World world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, int... parameters) {
            int i = parameters.length > 1 ? parameters[1] : 0;
            return new ItemParticle(world, x, y, z, velocityX, velocityY, velocityZ, Item.byId(parameters[0]), i);
        }
    }

    public static class SlimeBallFactory implements ParticleFactory {
        @Override
        public Particle create(int type, World world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, int... parameters) {
            return new ItemParticle(world, x, y, z, Items.SLIME_BALL);
        }
    }

    public static class SnowballFactory implements ParticleFactory {
        @Override
        public Particle create(int type, World world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, int... parameters) {
            return new ItemParticle(world, x, y, z, Items.SNOWBALL);
        }
    }
}
