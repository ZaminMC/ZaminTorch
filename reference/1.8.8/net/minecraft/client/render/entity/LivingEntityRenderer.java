package net.minecraft.client.render.entity;

import com.google.common.collect.Lists;
import java.nio.Buffer;
import java.nio.FloatBuffer;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.entity.layer.EntityRenderLayer;
import net.minecraft.client.render.model.Model;
import net.minecraft.client.render.model.PlayerModelPart;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.platform.MemoryTracker;
import net.minecraft.client.render.texture.DynamicTexture;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.scoreboard.team.AbstractTeam;
import net.minecraft.scoreboard.team.Team;
import net.minecraft.text.Formatting;
import net.minecraft.util.math.MathHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;

public abstract class LivingEntityRenderer<T extends LivingEntity> extends EntityRenderer<T> {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final DynamicTexture WHITE_TEXTURE = new DynamicTexture(16, 16);
    protected Model model;
    protected FloatBuffer tintBuffer = MemoryTracker.createFloatBuffer(4);
    protected List<EntityRenderLayer<T>> layers = Lists.newArrayList();
    protected boolean solidRender = false;

    public LivingEntityRenderer(EntityRenderDispatcher dispatcher, Model model, float shadowSize) {
        super(dispatcher);
        this.model = model;
        this.shadowSize = shadowSize;
    }

    protected <V extends LivingEntity, U extends EntityRenderLayer<V>> boolean addLayer(U layer) {
        return this.layers.add(layer);
    }

    protected <V extends LivingEntity, U extends EntityRenderLayer<V>> boolean removeLayer(U layer) {
        return this.layers.remove(layer);
    }

    public Model getModel() {
        return this.model;
    }

    protected float getRotatedAngle(float prevAng, float ang, float tickDelta) {
        float f = ang - prevAng;

        while (f < -180.0F) {
            f += 360.0F;
        }

        while (f >= 180.0F) {
            f -= 360.0F;
        }

        return prevAng + tickDelta * f;
    }

    public void glTranslate() {
    }

    public void render(T livingEntity, double d, double e, double f, float g, float h) {
        GlStateManager.pushMatrix();
        GlStateManager.disableCull();
        this.model.attackAnimationProgress = this.getAttackAnimationProgress(livingEntity, h);
        this.model.riding = livingEntity.isRiding();
        this.model.isBaby = livingEntity.isBaby();

        try {
            float fx = this.getRotatedAngle(livingEntity.lastBodyYaw, livingEntity.bodyYaw, h);
            float f1 = this.getRotatedAngle(livingEntity.lastHeadYaw, livingEntity.headYaw, h);
            float f2 = f1 - fx;
            if (livingEntity.isRiding() && livingEntity.vehicle instanceof LivingEntity) {
                LivingEntity livingentity = (LivingEntity)livingEntity.vehicle;
                fx = this.getRotatedAngle(livingentity.lastBodyYaw, livingentity.bodyYaw, h);
                f2 = f1 - fx;
                float f3 = MathHelper.wrapDegrees(f2);
                if (f3 < -85.0F) {
                    f3 = -85.0F;
                }

                if (f3 >= 85.0F) {
                    f3 = 85.0F;
                }

                fx = f1 - f3;
                if (f3 * f3 > 2500.0F) {
                    fx += f3 * 0.2F;
                }
            }

            float f7 = livingEntity.lastPitch + (livingEntity.pitch - livingEntity.lastPitch) * h;
            this.applyTranslation(livingEntity, d, e, f);
            float f8 = this.getBob(livingEntity, h);
            this.applyRotation(livingEntity, f8, fx, h);
            GlStateManager.enableRescaleNormal();
            GlStateManager.scalef(-1.0F, -1.0F, 1.0F);
            this.applyScale(livingEntity, h);
            float f4 = 0.0625F;
            GlStateManager.translatef(0.0F, -1.5078125F, 0.0F);
            float f5 = livingEntity.lastWalkAnimationSpeed + (livingEntity.walkAnimationSpeed - livingEntity.lastWalkAnimationSpeed) * h;
            float f6 = livingEntity.walkAnimationProgress - livingEntity.walkAnimationSpeed * (1.0F - h);
            if (livingEntity.isBaby()) {
                f6 *= 3.0F;
            }

            if (f5 > 1.0F) {
                f5 = 1.0F;
            }

            GlStateManager.enableAlphaTest();
            this.model.prepare(livingEntity, f6, f5, h);
            this.model.setupAnimation(f6, f5, f8, f2, f7, 0.0625F, livingEntity);
            if (this.solidRender) {
                boolean flag1 = this.setupSolidState(livingEntity);
                this.renderModel(livingEntity, f6, f5, f8, f2, f7, 0.0625F);
                if (flag1) {
                    this.tearDownSolidState();
                }
            } else {
                boolean flag = this.setupOverlayColor(livingEntity, h);
                this.renderModel(livingEntity, f6, f5, f8, f2, f7, 0.0625F);
                if (flag) {
                    this.tearDownOverlayColor();
                }

                GlStateManager.depthMask(true);
                if (!(livingEntity instanceof PlayerEntity) || !((PlayerEntity)livingEntity).isSpectator()) {
                    this.renderLayers(livingEntity, f6, f5, h, f8, f2, f7, 0.0625F);
                }
            }

            GlStateManager.disableRescaleNormal();
        } catch (Exception exception) {
            LOGGER.error("Couldn't render entity", exception);
        }

        GlStateManager.activeTexture(GLX.GL_TEXTURE1);
        GlStateManager.enableTexture();
        GlStateManager.activeTexture(GLX.GL_TEXTURE0);
        GlStateManager.enableCull();
        GlStateManager.popMatrix();
        if (!this.solidRender) {
            super.render(livingEntity, d, e, f, g, h);
        }
    }

    protected boolean setupSolidState(T entity) {
        int i = 16777215;
        if (entity instanceof PlayerEntity) {
            Team team = (Team)entity.getScoreboardTeam();
            if (team != null) {
                String s = TextRenderer.isolateFormatting(team.getPrefix());
                if (s.length() >= 2) {
                    i = this.getTextRenderer().getColor(s.charAt(1));
                }
            }
        }

        float f1 = (i >> 16 & 0xFF) / 255.0F;
        float f2 = (i >> 8 & 0xFF) / 255.0F;
        float f = (i & 0xFF) / 255.0F;
        GlStateManager.disableLighting();
        GlStateManager.activeTexture(GLX.GL_TEXTURE0);
        GlStateManager.color4f(f1, f2, f, 1.0F);
        GlStateManager.disableTexture();
        GlStateManager.activeTexture(GLX.GL_TEXTURE1);
        GlStateManager.disableTexture();
        GlStateManager.activeTexture(GLX.GL_TEXTURE0);
        return true;
    }

    protected void tearDownSolidState() {
        GlStateManager.enableLighting();
        GlStateManager.activeTexture(GLX.GL_TEXTURE0);
        GlStateManager.enableTexture();
        GlStateManager.activeTexture(GLX.GL_TEXTURE1);
        GlStateManager.enableTexture();
        GlStateManager.activeTexture(GLX.GL_TEXTURE0);
    }

    protected void renderModel(T entity, float walkAnimationProgress, float walkAnimationSpeed, float bob, float yaw, float pitch, float scale) {
        boolean flag = !entity.isInvisible();
        boolean flag1 = !flag && !entity.isInvisibleTo(Minecraft.getInstance().player);
        if (flag || flag1) {
            if (!this.bindTexture(entity)) {
                return;
            }

            if (flag1) {
                GlStateManager.pushMatrix();
                GlStateManager.color4f(1.0F, 1.0F, 1.0F, 0.15F);
                GlStateManager.depthMask(false);
                GlStateManager.enableBlend();
                GlStateManager.blendFunc(770, 771);
                GlStateManager.alphaFunc(516, 0.003921569F);
            }

            this.model.render(entity, walkAnimationProgress, walkAnimationSpeed, bob, yaw, pitch, scale);
            if (flag1) {
                GlStateManager.disableBlend();
                GlStateManager.alphaFunc(516, 0.1F);
                GlStateManager.popMatrix();
                GlStateManager.depthMask(true);
            }
        }
    }

    protected boolean setupOverlayColor(T entity, float tickDelta) {
        return this.setupOverlayColor(entity, tickDelta, true);
    }

    protected boolean setupOverlayColor(T entity, float tickDelta, boolean alwaysRender) {
        float f = entity.getBrightness(tickDelta);
        int i = this.getOverlayColor(entity, f, tickDelta);
        boolean flag = (i >> 24 & 0xFF) > 0;
        boolean flag1 = entity.damagedTimer > 0 || entity.deathTicks > 0;
        if (!flag && !flag1) {
            return false;
        }

        if (!flag && !alwaysRender) {
            return false;
        }

        GlStateManager.activeTexture(GLX.GL_TEXTURE0);
        GlStateManager.enableTexture();
        GL11.glTexEnvi(8960, 8704, GLX.GL_COMBINE);
        GL11.glTexEnvi(8960, GLX.GL_COMBINE_RGB, 8448);
        GL11.glTexEnvi(8960, GLX.GL_SOURCE0_RGB, GLX.GL_TEXTURE0);
        GL11.glTexEnvi(8960, GLX.GL_SOURCE1_RGB, GLX.GL_PRIMARY_COLOR);
        GL11.glTexEnvi(8960, GLX.GL_OPERAND0_RGB, 768);
        GL11.glTexEnvi(8960, GLX.GL_OPERAND1_RGB, 768);
        GL11.glTexEnvi(8960, GLX.GL_COMBINE_ALPHA, 7681);
        GL11.glTexEnvi(8960, GLX.GL_SOURCE0_ALPHA, GLX.GL_TEXTURE0);
        GL11.glTexEnvi(8960, GLX.GL_OPERAND0_ALPHA, 770);
        GlStateManager.activeTexture(GLX.GL_TEXTURE1);
        GlStateManager.enableTexture();
        GL11.glTexEnvi(8960, 8704, GLX.GL_COMBINE);
        GL11.glTexEnvi(8960, GLX.GL_COMBINE_RGB, GLX.GL_INTERPOLATE);
        GL11.glTexEnvi(8960, GLX.GL_SOURCE0_RGB, GLX.GL_CONSTANT);
        GL11.glTexEnvi(8960, GLX.GL_SOURCE1_RGB, GLX.GL_PREVIOUS);
        GL11.glTexEnvi(8960, GLX.GL_SOURCE2_RGB, GLX.GL_CONSTANT);
        GL11.glTexEnvi(8960, GLX.GL_OPERAND0_RGB, 768);
        GL11.glTexEnvi(8960, GLX.GL_OPERAND1_RGB, 768);
        GL11.glTexEnvi(8960, GLX.GL_OPERAND2_RGB, 770);
        GL11.glTexEnvi(8960, GLX.GL_COMBINE_ALPHA, 7681);
        GL11.glTexEnvi(8960, GLX.GL_SOURCE0_ALPHA, GLX.GL_PREVIOUS);
        GL11.glTexEnvi(8960, GLX.GL_OPERAND0_ALPHA, 770);
        ((Buffer)this.tintBuffer).position(0);
        if (flag1) {
            this.tintBuffer.put(1.0F);
            this.tintBuffer.put(0.0F);
            this.tintBuffer.put(0.0F);
            this.tintBuffer.put(0.3F);
        } else {
            float f1 = (i >> 24 & 0xFF) / 255.0F;
            float f2 = (i >> 16 & 0xFF) / 255.0F;
            float f3 = (i >> 8 & 0xFF) / 255.0F;
            float f4 = (i & 0xFF) / 255.0F;
            this.tintBuffer.put(f2);
            this.tintBuffer.put(f3);
            this.tintBuffer.put(f4);
            this.tintBuffer.put(1.0F - f1);
        }

        ((Buffer)this.tintBuffer).flip();
        GL11.glTexEnv(8960, 8705, this.tintBuffer);
        GlStateManager.activeTexture(GLX.GL_TEXTURE2);
        GlStateManager.enableTexture();
        GlStateManager.bindTexture(WHITE_TEXTURE.getGlId());
        GL11.glTexEnvi(8960, 8704, GLX.GL_COMBINE);
        GL11.glTexEnvi(8960, GLX.GL_COMBINE_RGB, 8448);
        GL11.glTexEnvi(8960, GLX.GL_SOURCE0_RGB, GLX.GL_PREVIOUS);
        GL11.glTexEnvi(8960, GLX.GL_SOURCE1_RGB, GLX.GL_TEXTURE1);
        GL11.glTexEnvi(8960, GLX.GL_OPERAND0_RGB, 768);
        GL11.glTexEnvi(8960, GLX.GL_OPERAND1_RGB, 768);
        GL11.glTexEnvi(8960, GLX.GL_COMBINE_ALPHA, 7681);
        GL11.glTexEnvi(8960, GLX.GL_SOURCE0_ALPHA, GLX.GL_PREVIOUS);
        GL11.glTexEnvi(8960, GLX.GL_OPERAND0_ALPHA, 770);
        GlStateManager.activeTexture(GLX.GL_TEXTURE0);
        return true;
    }

    protected void tearDownOverlayColor() {
        GlStateManager.activeTexture(GLX.GL_TEXTURE0);
        GlStateManager.enableTexture();
        GL11.glTexEnvi(8960, 8704, GLX.GL_COMBINE);
        GL11.glTexEnvi(8960, GLX.GL_COMBINE_RGB, 8448);
        GL11.glTexEnvi(8960, GLX.GL_SOURCE0_RGB, GLX.GL_TEXTURE0);
        GL11.glTexEnvi(8960, GLX.GL_SOURCE1_RGB, GLX.GL_PRIMARY_COLOR);
        GL11.glTexEnvi(8960, GLX.GL_OPERAND0_RGB, 768);
        GL11.glTexEnvi(8960, GLX.GL_OPERAND1_RGB, 768);
        GL11.glTexEnvi(8960, GLX.GL_COMBINE_ALPHA, 8448);
        GL11.glTexEnvi(8960, GLX.GL_SOURCE0_ALPHA, GLX.GL_TEXTURE0);
        GL11.glTexEnvi(8960, GLX.GL_SOURCE1_ALPHA, GLX.GL_PRIMARY_COLOR);
        GL11.glTexEnvi(8960, GLX.GL_OPERAND0_ALPHA, 770);
        GL11.glTexEnvi(8960, GLX.GL_OPERAND1_ALPHA, 770);
        GlStateManager.activeTexture(GLX.GL_TEXTURE1);
        GL11.glTexEnvi(8960, 8704, GLX.GL_COMBINE);
        GL11.glTexEnvi(8960, GLX.GL_COMBINE_RGB, 8448);
        GL11.glTexEnvi(8960, GLX.GL_OPERAND0_RGB, 768);
        GL11.glTexEnvi(8960, GLX.GL_OPERAND1_RGB, 768);
        GL11.glTexEnvi(8960, GLX.GL_SOURCE0_RGB, 5890);
        GL11.glTexEnvi(8960, GLX.GL_SOURCE1_RGB, GLX.GL_PREVIOUS);
        GL11.glTexEnvi(8960, GLX.GL_COMBINE_ALPHA, 8448);
        GL11.glTexEnvi(8960, GLX.GL_OPERAND0_ALPHA, 770);
        GL11.glTexEnvi(8960, GLX.GL_SOURCE0_ALPHA, 5890);
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.activeTexture(GLX.GL_TEXTURE2);
        GlStateManager.disableTexture();
        GlStateManager.bindTexture(0);
        GL11.glTexEnvi(8960, 8704, GLX.GL_COMBINE);
        GL11.glTexEnvi(8960, GLX.GL_COMBINE_RGB, 8448);
        GL11.glTexEnvi(8960, GLX.GL_OPERAND0_RGB, 768);
        GL11.glTexEnvi(8960, GLX.GL_OPERAND1_RGB, 768);
        GL11.glTexEnvi(8960, GLX.GL_SOURCE0_RGB, 5890);
        GL11.glTexEnvi(8960, GLX.GL_SOURCE1_RGB, GLX.GL_PREVIOUS);
        GL11.glTexEnvi(8960, GLX.GL_COMBINE_ALPHA, 8448);
        GL11.glTexEnvi(8960, GLX.GL_OPERAND0_ALPHA, 770);
        GL11.glTexEnvi(8960, GLX.GL_SOURCE0_ALPHA, 5890);
        GlStateManager.activeTexture(GLX.GL_TEXTURE0);
    }

    protected void applyTranslation(T entity, double dx, double dy, double dz) {
        GlStateManager.translatef((float)dx, (float)dy, (float)dz);
    }

    protected void applyRotation(T entity, float bob, float bodyYaw, float tickDelta) {
        GlStateManager.rotatef(180.0F - bodyYaw, 0.0F, 1.0F, 0.0F);
        if (entity.deathTicks > 0) {
            float f = (entity.deathTicks + tickDelta - 1.0F) / 20.0F * 1.6F;
            f = MathHelper.sqrt(f);
            if (f > 1.0F) {
                f = 1.0F;
            }

            GlStateManager.rotatef(f * this.getDeathYaw(entity), 0.0F, 0.0F, 1.0F);
        } else {
            String s = Formatting.strip(entity.getName());
            if (s != null
                && (s.equals("Dinnerbone") || s.equals("Grumm"))
                && (!(entity instanceof PlayerEntity) || ((PlayerEntity)entity).isModelPartVisible(PlayerModelPart.CAPE))) {
                GlStateManager.translatef(0.0F, entity.height + 0.1F, 0.0F);
                GlStateManager.rotatef(180.0F, 0.0F, 0.0F, 1.0F);
            }
        }
    }

    protected float getAttackAnimationProgress(T entity, float tickDelta) {
        return entity.getAttackAnimationProgress(tickDelta);
    }

    protected float getBob(T entity, float tickDelta) {
        return entity.ticks + tickDelta;
    }

    protected void renderLayers(
        T entity, float walkAnimationProgress, float walkAnimationSpeed, float tickDelta, float bob, float yaw, float pitch, float scale
    ) {
        for (EntityRenderLayer<T> entityrenderlayer : this.layers) {
            boolean flag = this.setupOverlayColor(entity, tickDelta, entityrenderlayer.colorsWhenDamaged());
            entityrenderlayer.render(entity, walkAnimationProgress, walkAnimationSpeed, tickDelta, bob, yaw, pitch, scale);
            if (flag) {
                this.tearDownOverlayColor();
            }
        }
    }

    protected float getDeathYaw(T entity) {
        return 90.0F;
    }

    protected int getOverlayColor(T entity, float brightness, float timeDelta) {
        return 0;
    }

    protected void applyScale(T entity, float scale) {
    }

    public void renderNameTag(T entity, double dx, double dy, double dz) {
        if (this.shouldRenderNameTag(entity)) {
            double d0 = entity.squaredDistanceTo(this.dispatcher.camera);
            float f = entity.isSneaking() ? 32.0F : 64.0F;
            if (!(d0 >= f * f)) {
                String s = entity.getDisplayName().getFormattedString();
                float f1 = 0.02666667F;
                GlStateManager.alphaFunc(516, 0.1F);
                if (entity.isSneaking()) {
                    TextRenderer textrenderer = this.getTextRenderer();
                    GlStateManager.pushMatrix();
                    GlStateManager.translatef((float)dx, (float)dy + entity.height + 0.5F - (entity.isBaby() ? entity.height / 2.0F : 0.0F), (float)dz);
                    GL11.glNormal3f(0.0F, 1.0F, 0.0F);
                    GlStateManager.rotatef(-this.dispatcher.cameraYaw, 0.0F, 1.0F, 0.0F);
                    GlStateManager.rotatef(this.dispatcher.cameraPitch, 1.0F, 0.0F, 0.0F);
                    GlStateManager.scalef(-0.02666667F, -0.02666667F, 0.02666667F);
                    GlStateManager.translatef(0.0F, 9.374999F, 0.0F);
                    GlStateManager.disableLighting();
                    GlStateManager.depthMask(false);
                    GlStateManager.enableBlend();
                    GlStateManager.disableTexture();
                    GlStateManager.blendFuncSeparate(770, 771, 1, 0);
                    int i = textrenderer.getWidth(s) / 2;
                    Tesselator tesselator = Tesselator.getInstance();
                    BufferBuilder bufferbuilder = tesselator.getBuffer();
                    bufferbuilder.begin(7, DefaultVertexFormat.POSITION_COLOR);
                    bufferbuilder.vertex(-i - 1, -1.0, 0.0).color(0.0F, 0.0F, 0.0F, 0.25F).nextVertex();
                    bufferbuilder.vertex(-i - 1, 8.0, 0.0).color(0.0F, 0.0F, 0.0F, 0.25F).nextVertex();
                    bufferbuilder.vertex(i + 1, 8.0, 0.0).color(0.0F, 0.0F, 0.0F, 0.25F).nextVertex();
                    bufferbuilder.vertex(i + 1, -1.0, 0.0).color(0.0F, 0.0F, 0.0F, 0.25F).nextVertex();
                    tesselator.end();
                    GlStateManager.enableTexture();
                    GlStateManager.depthMask(true);
                    textrenderer.draw(s, -textrenderer.getWidth(s) / 2, 0, 553648127);
                    GlStateManager.enableLighting();
                    GlStateManager.disableBlend();
                    GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
                    GlStateManager.popMatrix();
                } else {
                    this.renderNameTag(entity, dx, dy - (entity.isBaby() ? entity.height / 2.0F : 0.0), dz, s, 0.02666667F, d0);
                }
            }
        }
    }

    protected boolean shouldRenderNameTag(T entity) {
        LocalClientPlayerEntity localclientplayerentity = Minecraft.getInstance().player;
        if (entity instanceof PlayerEntity && entity != localclientplayerentity) {
            AbstractTeam abstractteam = entity.getScoreboardTeam();
            AbstractTeam abstractteam1 = localclientplayerentity.getScoreboardTeam();
            if (abstractteam != null) {
                AbstractTeam.Visibility abstractteam$visibility = abstractteam.getNameTagVisibility();
                switch (abstractteam$visibility) {
                    case ALWAYS:
                        return true;
                    case NEVER:
                        return false;
                    case HIDE_FOR_OTHER_TEAMS:
                        return abstractteam1 == null || abstractteam.isAlliedTo(abstractteam1);
                    case HIDE_FOR_OWN_TEAM:
                        return abstractteam1 == null || !abstractteam.isAlliedTo(abstractteam1);
                    default:
                        return true;
                }
            }
        }

        return Minecraft.isDisplayGui() && entity != this.dispatcher.camera && !entity.isInvisibleTo(localclientplayerentity) && entity.rider == null;
    }

    public void setSolidRender(boolean solidRender) {
        this.solidRender = solidRender;
    }

    static {
        int[] aint = WHITE_TEXTURE.getPixels();

        for (int i = 0; i < 256; i++) {
            aint[i] = -1;
        }

        WHITE_TEXTURE.upload();
    }
}
