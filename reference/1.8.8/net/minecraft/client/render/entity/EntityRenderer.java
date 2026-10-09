package net.minecraft.client.render.entity;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.Culler;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.client.render.texture.TextureAtlasSprite;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.mob.MobEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.resource.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;

public abstract class EntityRenderer<T extends Entity> {
    private static final Identifier SHADOW_LOCATION = new Identifier("textures/misc/shadow.png");
    protected final EntityRenderDispatcher dispatcher;
    protected float shadowSize;
    protected float shadowDarkness = 1.0F;

    protected EntityRenderer(EntityRenderDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    public boolean shouldRender(T entity, Culler view, double cameraX, double cameraY, double cameraZ) {
        Box box = entity.getShape();
        if (box.isInvalid() || box.getAverageSideLength() == 0.0) {
            box = new Box(entity.x - 2.0, entity.y - 2.0, entity.z - 2.0, entity.x + 2.0, entity.y + 2.0, entity.z + 2.0);
        }

        return entity.shouldRender(cameraX, cameraY, cameraZ) && (entity.ignoreCameraFrustum || view.isVisible(box));
    }

    public void render(T entity, double dx, double dy, double dz, float yaw, float tickDelta) {
        this.renderNameTag(entity, dx, dy, dz);
    }

    protected void renderNameTag(T entity, double dx, double dy, double dz) {
        if (this.shouldRenderNameTag(entity)) {
            this.renderNameTag(entity, entity.getDisplayName().getFormattedString(), dx, dy, dz, 64);
        }
    }

    protected boolean shouldRenderNameTag(T entity) {
        return entity.shouldShowNameTag() && entity.hasCustomName();
    }

    protected void renderNameTag(T entity, double dx, double dy, double dz, String name, float tickDelta, double squaredDistance) {
        this.renderNameTag(entity, name, dx, dy, dz, 64);
    }

    protected abstract Identifier getTextureLocation(T entity);

    protected boolean bindTexture(T entity) {
        Identifier identifier = this.getTextureLocation(entity);
        if (identifier == null) {
            return false;
        }

        this.bindTexture(identifier);
        return true;
    }

    public void bindTexture(Identifier location) {
        this.dispatcher.textureManager.bind(location);
    }

    private void renderOnFire(Entity entity, double dx, double dy, double dz, float tickDelta) {
        GlStateManager.disableLighting();
        TextureAtlas textureatlas = Minecraft.getInstance().getBlocksAtlas();
        TextureAtlasSprite textureatlassprite = textureatlas.getSprite("minecraft:blocks/fire_layer_0");
        TextureAtlasSprite textureatlassprite1 = textureatlas.getSprite("minecraft:blocks/fire_layer_1");
        GlStateManager.pushMatrix();
        GlStateManager.translatef((float)dx, (float)dy, (float)dz);
        float f = entity.width * 1.4F;
        GlStateManager.scalef(f, f, f);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuffer();
        float f1 = 0.5F;
        float f2 = 0.0F;
        float f3 = entity.height / f;
        float f4 = (float)(entity.y - entity.getShape().minY);
        GlStateManager.rotatef(-this.dispatcher.cameraYaw, 0.0F, 1.0F, 0.0F);
        GlStateManager.translatef(0.0F, 0.0F, -0.3F + (int)f3 * 0.02F);
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        float f5 = 0.0F;
        int i = 0;
        bufferbuilder.begin(7, DefaultVertexFormat.POSITION_TEX);

        while (f3 > 0.0F) {
            TextureAtlasSprite textureatlassprite2 = i % 2 == 0 ? textureatlassprite : textureatlassprite1;
            this.bindTexture(TextureAtlas.BLOCKS_LOCATION);
            float f6 = textureatlassprite2.getUMin();
            float f7 = textureatlassprite2.getVMin();
            float f8 = textureatlassprite2.getUMax();
            float f9 = textureatlassprite2.getVMax();
            if (i / 2 % 2 == 0) {
                float f10 = f8;
                f8 = f6;
                f6 = f10;
            }

            bufferbuilder.vertex(f1 - f2, 0.0F - f4, f5).texture(f8, f9).nextVertex();
            bufferbuilder.vertex(-f1 - f2, 0.0F - f4, f5).texture(f6, f9).nextVertex();
            bufferbuilder.vertex(-f1 - f2, 1.4F - f4, f5).texture(f6, f7).nextVertex();
            bufferbuilder.vertex(f1 - f2, 1.4F - f4, f5).texture(f8, f7).nextVertex();
            f3 -= 0.45F;
            f4 -= 0.45F;
            f1 *= 0.9F;
            f5 += 0.03F;
            i++;
        }

        tesselator.end();
        GlStateManager.popMatrix();
        GlStateManager.enableLighting();
    }

    private void renderShadow(Entity entity, double dx, double dy, double dz, float yaw, float tickDelta) {
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(770, 771);
        this.dispatcher.textureManager.bind(SHADOW_LOCATION);
        World world = this.getWorld();
        GlStateManager.depthMask(false);
        float f = this.shadowSize;
        if (entity instanceof MobEntity) {
            MobEntity mobentity = (MobEntity)entity;
            f *= mobentity.getShadowScale();
            if (mobentity.isBaby()) {
                f *= 0.5F;
            }
        }

        double d5 = entity.prevX + (entity.x - entity.prevX) * tickDelta;
        double d0 = entity.prevY + (entity.y - entity.prevY) * tickDelta;
        double d1 = entity.prevZ + (entity.z - entity.prevZ) * tickDelta;
        int i = MathHelper.floor(d5 - f);
        int j = MathHelper.floor(d5 + f);
        int k = MathHelper.floor(d0 - f);
        int l = MathHelper.floor(d0);
        int i1 = MathHelper.floor(d1 - f);
        int j1 = MathHelper.floor(d1 + f);
        double d2 = dx - d5;
        double d3 = dy - d0;
        double d4 = dz - d1;
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuffer();
        bufferbuilder.begin(7, DefaultVertexFormat.POSITION_TEX_COLOR);

        for (BlockPos blockpos : BlockPos.iterateRegionMutable(new BlockPos(i, k, i1), new BlockPos(j, l, j1))) {
            Block block = world.getBlockState(blockpos.down()).getBlock();
            if (block.getRenderType() != -1 && world.getRawBrightness(blockpos) > 3) {
                this.renderShadowOnBlock(block, dx, dy, dz, blockpos, yaw, f, d2, d3, d4);
            }
        }

        tesselator.end();
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableBlend();
        GlStateManager.depthMask(true);
    }

    private World getWorld() {
        return this.dispatcher.world;
    }

    private void renderShadowOnBlock(Block block, double dx, double dy, double dz, BlockPos pos, float yaw, float shadowSize, double cx, double cy, double cz) {
        if (block.isCube()) {
            Tesselator tesselator = Tesselator.getInstance();
            BufferBuilder bufferbuilder = tesselator.getBuffer();
            double d0 = (yaw - (dy - (pos.getY() + cy)) / 2.0) * 0.5 * this.getWorld().getBrightness(pos);
            if (!(d0 < 0.0)) {
                if (d0 > 1.0) {
                    d0 = 1.0;
                }

                double d1 = pos.getX() + block.getMinX() + cx;
                double d2 = pos.getX() + block.getMaxX() + cx;
                double d3 = pos.getY() + block.getMinY() + cy + 0.015625;
                double d4 = pos.getZ() + block.getMinZ() + cz;
                double d5 = pos.getZ() + block.getMaxZ() + cz;
                float f = (float)((dx - d1) / 2.0 / shadowSize + 0.5);
                float f1 = (float)((dx - d2) / 2.0 / shadowSize + 0.5);
                float f2 = (float)((dz - d4) / 2.0 / shadowSize + 0.5);
                float f3 = (float)((dz - d5) / 2.0 / shadowSize + 0.5);
                bufferbuilder.vertex(d1, d3, d4).texture(f, f2).color(1.0F, 1.0F, 1.0F, (float)d0).nextVertex();
                bufferbuilder.vertex(d1, d3, d5).texture(f, f3).color(1.0F, 1.0F, 1.0F, (float)d0).nextVertex();
                bufferbuilder.vertex(d2, d3, d5).texture(f1, f3).color(1.0F, 1.0F, 1.0F, (float)d0).nextVertex();
                bufferbuilder.vertex(d2, d3, d4).texture(f1, f2).color(1.0F, 1.0F, 1.0F, (float)d0).nextVertex();
            }
        }
    }

    public static void renderShape(Box shape, double dx, double dy, double dz) {
        GlStateManager.disableTexture();
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuffer();
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        bufferbuilder.offset(dx, dy, dz);
        bufferbuilder.begin(7, DefaultVertexFormat.POSITION_NORMAL);
        bufferbuilder.vertex(shape.minX, shape.maxY, shape.minZ).normal(0.0F, 0.0F, -1.0F).nextVertex();
        bufferbuilder.vertex(shape.maxX, shape.maxY, shape.minZ).normal(0.0F, 0.0F, -1.0F).nextVertex();
        bufferbuilder.vertex(shape.maxX, shape.minY, shape.minZ).normal(0.0F, 0.0F, -1.0F).nextVertex();
        bufferbuilder.vertex(shape.minX, shape.minY, shape.minZ).normal(0.0F, 0.0F, -1.0F).nextVertex();
        bufferbuilder.vertex(shape.minX, shape.minY, shape.maxZ).normal(0.0F, 0.0F, 1.0F).nextVertex();
        bufferbuilder.vertex(shape.maxX, shape.minY, shape.maxZ).normal(0.0F, 0.0F, 1.0F).nextVertex();
        bufferbuilder.vertex(shape.maxX, shape.maxY, shape.maxZ).normal(0.0F, 0.0F, 1.0F).nextVertex();
        bufferbuilder.vertex(shape.minX, shape.maxY, shape.maxZ).normal(0.0F, 0.0F, 1.0F).nextVertex();
        bufferbuilder.vertex(shape.minX, shape.minY, shape.minZ).normal(0.0F, -1.0F, 0.0F).nextVertex();
        bufferbuilder.vertex(shape.maxX, shape.minY, shape.minZ).normal(0.0F, -1.0F, 0.0F).nextVertex();
        bufferbuilder.vertex(shape.maxX, shape.minY, shape.maxZ).normal(0.0F, -1.0F, 0.0F).nextVertex();
        bufferbuilder.vertex(shape.minX, shape.minY, shape.maxZ).normal(0.0F, -1.0F, 0.0F).nextVertex();
        bufferbuilder.vertex(shape.minX, shape.maxY, shape.maxZ).normal(0.0F, 1.0F, 0.0F).nextVertex();
        bufferbuilder.vertex(shape.maxX, shape.maxY, shape.maxZ).normal(0.0F, 1.0F, 0.0F).nextVertex();
        bufferbuilder.vertex(shape.maxX, shape.maxY, shape.minZ).normal(0.0F, 1.0F, 0.0F).nextVertex();
        bufferbuilder.vertex(shape.minX, shape.maxY, shape.minZ).normal(0.0F, 1.0F, 0.0F).nextVertex();
        bufferbuilder.vertex(shape.minX, shape.minY, shape.maxZ).normal(-1.0F, 0.0F, 0.0F).nextVertex();
        bufferbuilder.vertex(shape.minX, shape.maxY, shape.maxZ).normal(-1.0F, 0.0F, 0.0F).nextVertex();
        bufferbuilder.vertex(shape.minX, shape.maxY, shape.minZ).normal(-1.0F, 0.0F, 0.0F).nextVertex();
        bufferbuilder.vertex(shape.minX, shape.minY, shape.minZ).normal(-1.0F, 0.0F, 0.0F).nextVertex();
        bufferbuilder.vertex(shape.maxX, shape.minY, shape.minZ).normal(1.0F, 0.0F, 0.0F).nextVertex();
        bufferbuilder.vertex(shape.maxX, shape.maxY, shape.minZ).normal(1.0F, 0.0F, 0.0F).nextVertex();
        bufferbuilder.vertex(shape.maxX, shape.maxY, shape.maxZ).normal(1.0F, 0.0F, 0.0F).nextVertex();
        bufferbuilder.vertex(shape.maxX, shape.minY, shape.maxZ).normal(1.0F, 0.0F, 0.0F).nextVertex();
        tesselator.end();
        bufferbuilder.offset(0.0, 0.0, 0.0);
        GlStateManager.enableTexture();
    }

    public void postRender(Entity entity, double dx, double dy, double dz, float yaw, float tickDelta) {
        if (this.dispatcher.options != null) {
            if (this.dispatcher.options.renderClouds && this.shadowSize > 0.0F && !entity.isInvisible() && this.dispatcher.shouldRenderShadow()) {
                double d0 = this.dispatcher.squaredDistanceToCamera(entity.x, entity.y, entity.z);
                float f = (float)((1.0 - d0 / 256.0) * this.shadowDarkness);
                if (f > 0.0F) {
                    this.renderShadow(entity, dx, dy, dz, f, tickDelta);
                }
            }

            if (entity.shouldRenderOnFire() && (!(entity instanceof PlayerEntity) || !((PlayerEntity)entity).isSpectator())) {
                this.renderOnFire(entity, dx, dy, dz, tickDelta);
            }
        }
    }

    public TextRenderer getTextRenderer() {
        return this.dispatcher.getTextRenderer();
    }

    protected void renderNameTag(T entity, String name, double dx, double dy, double dz, int distance) {
        double d0 = entity.squaredDistanceTo(this.dispatcher.camera);
        if (!(d0 > distance * distance)) {
            TextRenderer textrenderer = this.getTextRenderer();
            float f = 1.6F;
            float f1 = 0.016666668F * f;
            GlStateManager.pushMatrix();
            GlStateManager.translatef((float)dx + 0.0F, (float)dy + entity.height + 0.5F, (float)dz);
            GL11.glNormal3f(0.0F, 1.0F, 0.0F);
            GlStateManager.rotatef(-this.dispatcher.cameraYaw, 0.0F, 1.0F, 0.0F);
            GlStateManager.rotatef(this.dispatcher.cameraPitch, 1.0F, 0.0F, 0.0F);
            GlStateManager.scalef(-f1, -f1, f1);
            GlStateManager.disableLighting();
            GlStateManager.depthMask(false);
            GlStateManager.disableDepthTest();
            GlStateManager.enableBlend();
            GlStateManager.blendFuncSeparate(770, 771, 1, 0);
            Tesselator tesselator = Tesselator.getInstance();
            BufferBuilder bufferbuilder = tesselator.getBuffer();
            int i = 0;
            if (name.equals("deadmau5")) {
                i = -10;
            }

            int j = textrenderer.getWidth(name) / 2;
            GlStateManager.disableTexture();
            bufferbuilder.begin(7, DefaultVertexFormat.POSITION_COLOR);
            bufferbuilder.vertex(-j - 1, -1 + i, 0.0).color(0.0F, 0.0F, 0.0F, 0.25F).nextVertex();
            bufferbuilder.vertex(-j - 1, 8 + i, 0.0).color(0.0F, 0.0F, 0.0F, 0.25F).nextVertex();
            bufferbuilder.vertex(j + 1, 8 + i, 0.0).color(0.0F, 0.0F, 0.0F, 0.25F).nextVertex();
            bufferbuilder.vertex(j + 1, -1 + i, 0.0).color(0.0F, 0.0F, 0.0F, 0.25F).nextVertex();
            tesselator.end();
            GlStateManager.enableTexture();
            textrenderer.draw(name, -textrenderer.getWidth(name) / 2, i, 553648127);
            GlStateManager.enableDepthTest();
            GlStateManager.depthMask(true);
            textrenderer.draw(name, -textrenderer.getWidth(name) / 2, i, -1);
            GlStateManager.enableLighting();
            GlStateManager.disableBlend();
            GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.popMatrix();
        }
    }

    public EntityRenderDispatcher getDispatcher() {
        return this.dispatcher;
    }
}
