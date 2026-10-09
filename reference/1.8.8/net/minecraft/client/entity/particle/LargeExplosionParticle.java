package net.minecraft.client.entity.particle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.platform.Lighting;
import net.minecraft.client.render.texture.TextureManager;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.client.render.vertex.VertexFormat;
import net.minecraft.entity.Entity;
import net.minecraft.resource.Identifier;
import net.minecraft.world.World;

public class LargeExplosionParticle extends Particle {
    private static final Identifier EXPLOSION_LOCATION = new Identifier("textures/entity/explosion.png");
    private static final VertexFormat VERTEX_FORMAT = new VertexFormat()
        .addElement(DefaultVertexFormat.POSITION_ELEMENT)
        .addElement(DefaultVertexFormat.UV0_ELEMENT)
        .addElement(DefaultVertexFormat.COLOR_ELEMENT)
        .addElement(DefaultVertexFormat.UV1_ELEMENT)
        .addElement(DefaultVertexFormat.NORMAL_ELEMENT)
        .addElement(DefaultVertexFormat.PADDING_ELEMENT);
    private int age;
    private int maxAge;
    private TextureManager textureManager;
    private float scaleFactor;

    protected LargeExplosionParticle(
        TextureManager textureManager, World world, double x, double y, double z, double velocityX, double velocityY, double velocityZ
    ) {
        super(world, x, y, z, 0.0, 0.0, 0.0);
        this.textureManager = textureManager;
        this.maxAge = 6 + this.random.nextInt(4);
        this.red = this.green = this.blue = this.random.nextFloat() * 0.6F + 0.4F;
        this.scaleFactor = 1.0F - (float)velocityX * 0.5F;
    }

    @Override
    public void render(BufferBuilder bufferBuilder, Entity camera, float tickDelta, float dx, float dy, float dz, float forwards, float sideways) {
        int i = (int)((this.age + tickDelta) * 15.0F / this.maxAge);
        if (i <= 15) {
            this.textureManager.bind(EXPLOSION_LOCATION);
            float f = i % 4 / 4.0F;
            float f1 = f + 0.24975F;
            float f2 = i / 4 / 4.0F;
            float f3 = f2 + 0.24975F;
            float f4 = 2.0F * this.scaleFactor;
            float f5 = (float)(this.lastX + (this.x - this.lastX) * tickDelta - lerpCameraX);
            float f6 = (float)(this.lastY + (this.y - this.lastY) * tickDelta - lerpCameraY);
            float f7 = (float)(this.lastZ + (this.z - this.lastZ) * tickDelta - lerpCameraZ);
            GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.disableLighting();
            Lighting.turnOff();
            bufferBuilder.begin(7, VERTEX_FORMAT);
            bufferBuilder.vertex(f5 - dx * f4 - forwards * f4, f6 - dy * f4, f7 - dz * f4 - sideways * f4)
                .texture(f1, f3)
                .color(this.red, this.green, this.blue, 1.0F)
                .texture(0, 240)
                .normal(0.0F, 1.0F, 0.0F)
                .nextVertex();
            bufferBuilder.vertex(f5 - dx * f4 + forwards * f4, f6 + dy * f4, f7 - dz * f4 + sideways * f4)
                .texture(f1, f2)
                .color(this.red, this.green, this.blue, 1.0F)
                .texture(0, 240)
                .normal(0.0F, 1.0F, 0.0F)
                .nextVertex();
            bufferBuilder.vertex(f5 + dx * f4 + forwards * f4, f6 + dy * f4, f7 + dz * f4 + sideways * f4)
                .texture(f, f2)
                .color(this.red, this.green, this.blue, 1.0F)
                .texture(0, 240)
                .normal(0.0F, 1.0F, 0.0F)
                .nextVertex();
            bufferBuilder.vertex(f5 + dx * f4 - forwards * f4, f6 - dy * f4, f7 + dz * f4 - sideways * f4)
                .texture(f, f3)
                .color(this.red, this.green, this.blue, 1.0F)
                .texture(0, 240)
                .normal(0.0F, 1.0F, 0.0F)
                .nextVertex();
            Tesselator.getInstance().end();
            GlStateManager.enableLighting();
        }
    }

    @Override
    public int getLightLevel(float tickDelta) {
        return 61680;
    }

    @Override
    public void tick() {
        this.lastX = this.x;
        this.lastY = this.y;
        this.lastZ = this.z;
        this.age++;
        if (this.age == this.maxAge) {
            this.remove();
        }
    }

    @Override
    public int getAtlasType() {
        return 3;
    }

    public static class Factory implements ParticleFactory {
        @Override
        public Particle create(int type, World world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, int... parameters) {
            return new LargeExplosionParticle(Minecraft.getInstance().getTextureManager(), world, x, y, z, velocityX, velocityY, velocityZ);
        }
    }
}
