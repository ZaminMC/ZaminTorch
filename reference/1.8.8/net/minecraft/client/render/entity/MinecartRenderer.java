package net.minecraft.client.render.entity;

import net.minecraft.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.model.Model;
import net.minecraft.client.render.model.entity.MinecartModel;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.TextureAtlas;
import net.minecraft.entity.vehicle.MinecartEntity;
import net.minecraft.resource.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class MinecartRenderer<T extends MinecartEntity> extends EntityRenderer<T> {
    private static final Identifier MINECART_LOCATION = new Identifier("textures/entity/minecart.png");
    protected Model model = new MinecartModel();

    public MinecartRenderer(EntityRenderDispatcher entityRenderDispatcher) {
        super(entityRenderDispatcher);
        this.shadowSize = 0.5F;
    }

    public void render(T minecartEntity, double d, double e, double f, float g, float h) {
        GlStateManager.pushMatrix();
        this.bindTexture(minecartEntity);
        long i = minecartEntity.getNetworkId() * 493286711L;
        i = i * i * 4392167121L + i * 98761L;
        float fx = (((float)(i >> 16 & 7L) + 0.5F) / 8.0F - 0.5F) * 0.004F;
        float f1 = (((float)(i >> 20 & 7L) + 0.5F) / 8.0F - 0.5F) * 0.004F;
        float f2 = (((float)(i >> 24 & 7L) + 0.5F) / 8.0F - 0.5F) * 0.004F;
        GlStateManager.translatef(fx, f1, f2);
        double d0 = minecartEntity.prevX + (minecartEntity.x - minecartEntity.prevX) * h;
        double d1 = minecartEntity.prevY + (minecartEntity.y - minecartEntity.prevY) * h;
        double d2 = minecartEntity.prevZ + (minecartEntity.z - minecartEntity.prevZ) * h;
        double d3 = 0.3F;
        Vec3d vec3d = minecartEntity.snapPositionToRail(d0, d1, d2);
        float f3 = minecartEntity.lastPitch + (minecartEntity.pitch - minecartEntity.lastPitch) * h;
        if (vec3d != null) {
            Vec3d vec3d1 = minecartEntity.snapPositionToRailWithOffset(d0, d1, d2, d3);
            Vec3d vec3d2 = minecartEntity.snapPositionToRailWithOffset(d0, d1, d2, -d3);
            if (vec3d1 == null) {
                vec3d1 = vec3d;
            }

            if (vec3d2 == null) {
                vec3d2 = vec3d;
            }

            d += vec3d.x - d0;
            e += (vec3d1.y + vec3d2.y) / 2.0 - d1;
            f += vec3d.z - d2;
            Vec3d vec3d3 = vec3d2.add(-vec3d1.x, -vec3d1.y, -vec3d1.z);
            if (vec3d3.length() != 0.0) {
                vec3d3 = vec3d3.normalize();
                g = (float)(Math.atan2(vec3d3.z, vec3d3.x) * 180.0 / Math.PI);
                f3 = (float)(Math.atan(vec3d3.y) * 73.0);
            }
        }

        GlStateManager.translatef((float)d, (float)e + 0.375F, (float)f);
        GlStateManager.rotatef(180.0F - g, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotatef(-f3, 0.0F, 0.0F, 1.0F);
        float f5 = minecartEntity.getDamagedTimer() - h;
        float f6 = minecartEntity.getDamage() - h;
        if (f6 < 0.0F) {
            f6 = 0.0F;
        }

        if (f5 > 0.0F) {
            GlStateManager.rotatef(MathHelper.sin(f5) * f5 * f6 / 10.0F * minecartEntity.getDamagedSwingDirection(), 1.0F, 0.0F, 0.0F);
        }

        int j = minecartEntity.getDisplayBlockOffset();
        BlockState blockstate = minecartEntity.getDisplayBlock();
        if (blockstate.getBlock().getRenderType() != -1) {
            GlStateManager.pushMatrix();
            this.bindTexture(TextureAtlas.BLOCKS_LOCATION);
            float f4 = 0.75F;
            GlStateManager.scalef(f4, f4, f4);
            GlStateManager.translatef(-0.5F, (j - 8) / 16.0F, 0.5F);
            this.renderBlockInMinecart(minecartEntity, h, blockstate);
            GlStateManager.popMatrix();
            GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            this.bindTexture(minecartEntity);
        }

        GlStateManager.scalef(-1.0F, -1.0F, 1.0F);
        this.model.render(minecartEntity, 0.0F, 0.0F, -0.1F, 0.0F, 0.0F, 0.0625F);
        GlStateManager.popMatrix();
        super.render(minecartEntity, d, e, f, g, h);
    }

    protected Identifier getTextureLocation(T minecartEntity) {
        return MINECART_LOCATION;
    }

    protected void renderBlockInMinecart(T minecart, float tickDelta, BlockState state) {
        GlStateManager.pushMatrix();
        Minecraft.getInstance().getBlockRenderDispatcher().renderAsItem(state, minecart.getBrightness(tickDelta));
        GlStateManager.popMatrix();
    }
}
