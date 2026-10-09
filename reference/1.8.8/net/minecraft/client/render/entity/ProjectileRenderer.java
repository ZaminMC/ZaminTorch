package net.minecraft.client.render.entity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.client.render.texture.TextureAtlasSprite;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.Items;
import net.minecraft.resource.Identifier;

public class ProjectileRenderer extends EntityRenderer<ProjectileEntity> {
    private float speed;

    public ProjectileRenderer(EntityRenderDispatcher dispatcher, float speed) {
        super(dispatcher);
        this.speed = speed;
    }

    public void render(ProjectileEntity projectileEntity, double d, double e, double f, float g, float h) {
        GlStateManager.pushMatrix();
        this.bindTexture(projectileEntity);
        GlStateManager.translatef((float)d, (float)e, (float)f);
        GlStateManager.enableRescaleNormal();
        GlStateManager.scalef(this.speed, this.speed, this.speed);
        TextureAtlasSprite textureatlassprite = Minecraft.getInstance().getItemRenderer().getModelShaper().getParticleIcon(Items.FIRE_CHARGE);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuffer();
        float fx = textureatlassprite.getUMin();
        float f1 = textureatlassprite.getUMax();
        float f2 = textureatlassprite.getVMin();
        float f3 = textureatlassprite.getVMax();
        float f4 = 1.0F;
        float f5 = 0.5F;
        float f6 = 0.25F;
        GlStateManager.rotatef(180.0F - this.dispatcher.cameraYaw, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotatef(-this.dispatcher.cameraPitch, 1.0F, 0.0F, 0.0F);
        bufferbuilder.begin(7, DefaultVertexFormat.POSITION_TEX_NORMAL);
        bufferbuilder.vertex(-0.5, -0.25, 0.0).texture(fx, f3).normal(0.0F, 1.0F, 0.0F).nextVertex();
        bufferbuilder.vertex(0.5, -0.25, 0.0).texture(f1, f3).normal(0.0F, 1.0F, 0.0F).nextVertex();
        bufferbuilder.vertex(0.5, 0.75, 0.0).texture(f1, f2).normal(0.0F, 1.0F, 0.0F).nextVertex();
        bufferbuilder.vertex(-0.5, 0.75, 0.0).texture(fx, f2).normal(0.0F, 1.0F, 0.0F).nextVertex();
        tesselator.end();
        GlStateManager.disableRescaleNormal();
        GlStateManager.popMatrix();
        super.render(projectileEntity, d, e, f, g, h);
    }

    protected Identifier getTextureLocation(ProjectileEntity projectileEntity) {
        return TextureAtlas.BLOCKS_LOCATION;
    }
}
