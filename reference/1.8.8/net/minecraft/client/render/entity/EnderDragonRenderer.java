package net.minecraft.client.render.entity;

import net.minecraft.client.gui.BossBar;
import net.minecraft.client.render.entity.layer.EnderDragonDeathLayer;
import net.minecraft.client.render.entity.layer.EnderDragonEyesLayer;
import net.minecraft.client.render.model.entity.EnderDragonModel;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.platform.Lighting;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.entity.living.mob.monster.boss.EnderDragonEntity;
import net.minecraft.resource.Identifier;
import net.minecraft.util.math.MathHelper;

public class EnderDragonRenderer extends MobRenderer<EnderDragonEntity> {
    private static final Identifier ENDER_CRYSTAL_BEAM_LOCATION = new Identifier("textures/entity/endercrystal/endercrystal_beam.png");
    private static final Identifier DRAGON_EXPLODING_LOCATION = new Identifier("textures/entity/enderdragon/dragon_exploding.png");
    private static final Identifier DRAGON_LOCATION = new Identifier("textures/entity/enderdragon/dragon.png");
    protected EnderDragonModel model = (EnderDragonModel)this.model;

    public EnderDragonRenderer(EntityRenderDispatcher entityRenderDispatcher) {
        super(entityRenderDispatcher, new EnderDragonModel(0.0F), 0.5F);
        this.addLayer(new EnderDragonEyesLayer(this));
        this.addLayer(new EnderDragonDeathLayer());
    }

    protected void applyRotation(EnderDragonEntity enderDragonEntity, float f, float g, float h) {
        float fx = (float)enderDragonEntity.getSegmentProperties(7, h)[0];
        float f1 = (float)(enderDragonEntity.getSegmentProperties(5, h)[1] - enderDragonEntity.getSegmentProperties(10, h)[1]);
        GlStateManager.rotatef(-fx, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotatef(f1 * 10.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.translatef(0.0F, 0.0F, 1.0F);
        if (enderDragonEntity.deathTicks > 0) {
            float f2 = (enderDragonEntity.deathTicks + h - 1.0F) / 20.0F * 1.6F;
            f2 = MathHelper.sqrt(f2);
            if (f2 > 1.0F) {
                f2 = 1.0F;
            }

            GlStateManager.rotatef(f2 * this.getDeathYaw(enderDragonEntity), 0.0F, 0.0F, 1.0F);
        }
    }

    protected void renderModel(EnderDragonEntity enderDragonEntity, float f, float g, float h, float i, float j, float k) {
        if (enderDragonEntity.ticksSinceDeath > 0) {
            float fx = enderDragonEntity.ticksSinceDeath / 200.0F;
            GlStateManager.depthFunc(515);
            GlStateManager.enableAlphaTest();
            GlStateManager.alphaFunc(516, fx);
            this.bindTexture(DRAGON_EXPLODING_LOCATION);
            this.model.render(enderDragonEntity, f, g, h, i, j, k);
            GlStateManager.alphaFunc(516, 0.1F);
            GlStateManager.depthFunc(514);
        }

        this.bindTexture(enderDragonEntity);
        this.model.render(enderDragonEntity, f, g, h, i, j, k);
        if (enderDragonEntity.damagedTimer > 0) {
            GlStateManager.depthFunc(514);
            GlStateManager.disableTexture();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(770, 771);
            GlStateManager.color4f(1.0F, 0.0F, 0.0F, 0.5F);
            this.model.render(enderDragonEntity, f, g, h, i, j, k);
            GlStateManager.enableTexture();
            GlStateManager.disableBlend();
            GlStateManager.depthFunc(515);
        }
    }

    public void render(EnderDragonEntity enderDragonEntity, double d, double e, double f, float g, float h) {
        BossBar.update(enderDragonEntity, false);
        super.render(enderDragonEntity, d, e, f, g, h);
        if (enderDragonEntity.connectedCrystal != null) {
            this.renderCrystalBeam(enderDragonEntity, d, e, f, h);
        }
    }

    protected void renderCrystalBeam(EnderDragonEntity dragon, double dx, double dy, double dz, float tickDelta) {
        float f = dragon.connectedCrystal.renderTicks + tickDelta;
        float f1 = MathHelper.sin(f * 0.2F) / 2.0F + 0.5F;
        f1 = (f1 * f1 + f1) * 0.2F;
        float f2 = (float)(dragon.connectedCrystal.x - dragon.x - (dragon.lastX - dragon.x) * (1.0F - tickDelta));
        float f3 = (float)(f1 + dragon.connectedCrystal.y - 1.0 - dragon.y - (dragon.lastY - dragon.y) * (1.0F - tickDelta));
        float f4 = (float)(dragon.connectedCrystal.z - dragon.z - (dragon.lastZ - dragon.z) * (1.0F - tickDelta));
        float f5 = MathHelper.sqrt(f2 * f2 + f4 * f4);
        float f6 = MathHelper.sqrt(f2 * f2 + f3 * f3 + f4 * f4);
        GlStateManager.pushMatrix();
        GlStateManager.translatef((float)dx, (float)dy + 2.0F, (float)dz);
        GlStateManager.rotatef((float)(-Math.atan2(f4, f2)) * 180.0F / (float) Math.PI - 90.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotatef((float)(-Math.atan2(f5, f3)) * 180.0F / (float) Math.PI - 90.0F, 1.0F, 0.0F, 0.0F);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuffer();
        Lighting.turnOff();
        GlStateManager.disableCull();
        this.bindTexture(ENDER_CRYSTAL_BEAM_LOCATION);
        GlStateManager.shadeModel(7425);
        float f7 = 0.0F - (dragon.ticks + tickDelta) * 0.01F;
        float f8 = MathHelper.sqrt(f2 * f2 + f3 * f3 + f4 * f4) / 32.0F - (dragon.ticks + tickDelta) * 0.01F;
        bufferbuilder.begin(5, DefaultVertexFormat.POSITION_TEX_COLOR);
        int i = 8;

        for (int j = 0; j <= 8; j++) {
            float f9 = MathHelper.sin(j % 8 * (float) Math.PI * 2.0F / 8.0F) * 0.75F;
            float f10 = MathHelper.cos(j % 8 * (float) Math.PI * 2.0F / 8.0F) * 0.75F;
            float f11 = j % 8 * 1.0F / 8.0F;
            bufferbuilder.vertex(f9 * 0.2F, f10 * 0.2F, 0.0).texture(f11, f8).color(0, 0, 0, 255).nextVertex();
            bufferbuilder.vertex(f9, f10, f6).texture(f11, f7).color(255, 255, 255, 255).nextVertex();
        }

        tesselator.end();
        GlStateManager.enableCull();
        GlStateManager.shadeModel(7424);
        Lighting.turnOn();
        GlStateManager.popMatrix();
    }

    protected Identifier getTextureLocation(EnderDragonEntity enderDragonEntity) {
        return DRAGON_LOCATION;
    }
}
