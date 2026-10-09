package net.minecraft.client.entity.particle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;

public class EntityPickupParticle extends Particle {
    /**
     * The entity being picked up - this could be an item, arrow, or xp.
     */
    private Entity entity;
    /**
     * The collector that is picking up the entity.
     */
    private Entity collector;
    private int age;
    private int lifetime;
    private float offsetY;
    private EntityRenderDispatcher entityRenderDispatcher = Minecraft.getInstance().getEntityRenderDispatcher();

    public EntityPickupParticle(World world, Entity entity, Entity collector, float offsetY) {
        super(world, entity.x, entity.y, entity.z, entity.velocityX, entity.velocityY, entity.velocityZ);
        this.entity = entity;
        this.collector = collector;
        this.lifetime = 3;
        this.offsetY = offsetY;
    }

    @Override
    public void render(BufferBuilder bufferBuilder, Entity camera, float tickDelta, float dx, float dy, float dz, float forwards, float sideways) {
        float f = (this.age + tickDelta) / this.lifetime;
        f *= f;
        double d0 = this.entity.x;
        double d1 = this.entity.y;
        double d2 = this.entity.z;
        double d3 = this.collector.prevX + (this.collector.x - this.collector.prevX) * tickDelta;
        double d4 = this.collector.prevY + (this.collector.y - this.collector.prevY) * tickDelta + this.offsetY;
        double d5 = this.collector.prevZ + (this.collector.z - this.collector.prevZ) * tickDelta;
        double d6 = d0 + (d3 - d0) * f;
        double d7 = d1 + (d4 - d1) * f;
        double d8 = d2 + (d5 - d2) * f;
        int i = this.getLightLevel(tickDelta);
        int j = i % 65536;
        int k = i / 65536;
        GLX.multiTexCoord2f(GLX.GL_TEXTURE1, j / 1.0F, k / 1.0F);
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        d6 -= lerpCameraX;
        d7 -= lerpCameraY;
        d8 -= lerpCameraZ;
        this.entityRenderDispatcher.render(this.entity, (float)d6, (float)d7, (float)d8, this.entity.yaw, tickDelta);
    }

    @Override
    public void tick() {
        this.age++;
        if (this.age == this.lifetime) {
            this.remove();
        }
    }

    @Override
    public int getAtlasType() {
        return 3;
    }
}
