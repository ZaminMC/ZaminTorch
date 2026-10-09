package net.minecraft.client.render.entity;

import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.resource.Identifier;

public class DefaultRenderer extends EntityRenderer<Entity> {
    public DefaultRenderer(EntityRenderDispatcher entityRenderDispatcher) {
        super(entityRenderDispatcher);
    }

    @Override
    public void render(Entity entity, double dx, double dy, double dz, float yaw, float tickDelta) {
        GlStateManager.pushMatrix();
        renderShape(entity.getShape(), dx - entity.prevX, dy - entity.prevY, dz - entity.prevZ);
        GlStateManager.popMatrix();
        super.render(entity, dx, dy, dz, yaw, tickDelta);
    }

    @Override
    protected Identifier getTextureLocation(Entity entity) {
        return null;
    }
}
