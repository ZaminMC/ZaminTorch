package net.minecraft.client.entity.particle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.TextureManager;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.entity.Entity;
import net.minecraft.resource.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class FootstepParticle extends Particle {
    private static final Identifier FOOTPRINT_LOCATION = new Identifier("textures/particle/footprint.png");
    private int age;
    private int maxAge;
    private TextureManager textureManager;

    protected FootstepParticle(TextureManager textureManager, World world, double x, double y, double z) {
        super(world, x, y, z, 0.0, 0.0, 0.0);
        this.textureManager = textureManager;
        this.velocityX = this.velocityY = this.velocityZ = 0.0;
        this.maxAge = 200;
    }

    @Override
    public void render(BufferBuilder bufferBuilder, Entity camera, float tickDelta, float dx, float dy, float dz, float forwards, float sideways) {
        float f = (this.age + tickDelta) / this.maxAge;
        f *= f;
        float f1 = 2.0F - f * 2.0F;
        if (f1 > 1.0F) {
            f1 = 1.0F;
        }

        f1 *= 0.2F;
        GlStateManager.disableLighting();
        float f2 = 0.125F;
        float f3 = (float)(this.x - lerpCameraX);
        float f4 = (float)(this.y - lerpCameraY);
        float f5 = (float)(this.z - lerpCameraZ);
        float f6 = this.world.getBrightness(new BlockPos(this));
        this.textureManager.bind(FOOTPRINT_LOCATION);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(770, 771);
        bufferBuilder.begin(7, DefaultVertexFormat.POSITION_TEX_COLOR);
        bufferBuilder.vertex(f3 - 0.125F, f4, f5 + 0.125F).texture(0.0, 1.0).color(f6, f6, f6, f1).nextVertex();
        bufferBuilder.vertex(f3 + 0.125F, f4, f5 + 0.125F).texture(1.0, 1.0).color(f6, f6, f6, f1).nextVertex();
        bufferBuilder.vertex(f3 + 0.125F, f4, f5 - 0.125F).texture(1.0, 0.0).color(f6, f6, f6, f1).nextVertex();
        bufferBuilder.vertex(f3 - 0.125F, f4, f5 - 0.125F).texture(0.0, 0.0).color(f6, f6, f6, f1).nextVertex();
        Tesselator.getInstance().end();
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
    }

    @Override
    public void tick() {
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
            return new FootstepParticle(Minecraft.getInstance().getTextureManager(), world, x, y, z);
        }
    }
}
