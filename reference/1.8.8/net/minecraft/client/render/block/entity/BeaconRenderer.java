package net.minecraft.client.render.block.entity;

import java.util.List;
import net.minecraft.block.entity.BeaconBlockEntity;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.resource.Identifier;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.opengl.GL11;

public class BeaconRenderer extends BlockEntityRenderer<BeaconBlockEntity> {
    private static final Identifier BEACON_BEAM_LOCATION = new Identifier("textures/entity/beacon_beam.png");

    public void render(BeaconBlockEntity beaconBlockEntity, double d, double e, double f, float g, int i) {
        float fx = beaconBlockEntity.getBeamAngle();
        GlStateManager.alphaFunc(516, 0.1F);
        if (fx > 0.0F) {
            Tesselator tesselator = Tesselator.getInstance();
            BufferBuilder bufferbuilder = tesselator.getBuffer();
            GlStateManager.disableFog();
            List<BeaconBlockEntity.BeamSection> list = beaconBlockEntity.getBeamSections();
            int ix = 0;

            for (int j = 0; j < list.size(); j++) {
                BeaconBlockEntity.BeamSection beaconblockentity$beamsection = list.get(j);
                int k = ix + beaconblockentity$beamsection.getHeight();
                this.bindTexture(BEACON_BEAM_LOCATION);
                GL11.glTexParameterf(3553, 10242, 10497.0F);
                GL11.glTexParameterf(3553, 10243, 10497.0F);
                GlStateManager.disableLighting();
                GlStateManager.disableCull();
                GlStateManager.disableBlend();
                GlStateManager.depthMask(true);
                GlStateManager.blendFuncSeparate(770, 1, 1, 0);
                double d0 = (double)beaconBlockEntity.getWorld().getTime() + g;
                double d1 = MathHelper.floorDiff(-d0 * 0.2 - MathHelper.floor(-d0 * 0.1));
                float f1 = beaconblockentity$beamsection.getColor()[0];
                float f2 = beaconblockentity$beamsection.getColor()[1];
                float f3 = beaconblockentity$beamsection.getColor()[2];
                double d2 = d0 * 0.025 * -1.5;
                double d3 = 0.2;
                double d4 = 0.5 + Math.cos(d2 + (Math.PI * 3.0 / 4.0)) * 0.2;
                double d5 = 0.5 + Math.sin(d2 + (Math.PI * 3.0 / 4.0)) * 0.2;
                double d6 = 0.5 + Math.cos(d2 + (Math.PI / 4)) * 0.2;
                double d7 = 0.5 + Math.sin(d2 + (Math.PI / 4)) * 0.2;
                double d8 = 0.5 + Math.cos(d2 + (Math.PI * 5.0 / 4.0)) * 0.2;
                double d9 = 0.5 + Math.sin(d2 + (Math.PI * 5.0 / 4.0)) * 0.2;
                double d10 = 0.5 + Math.cos(d2 + (Math.PI * 7.0 / 4.0)) * 0.2;
                double d11 = 0.5 + Math.sin(d2 + (Math.PI * 7.0 / 4.0)) * 0.2;
                double d12 = 0.0;
                double d13 = 1.0;
                double d14 = -1.0 + d1;
                double d15 = beaconblockentity$beamsection.getHeight() * fx * 2.5 + d14;
                bufferbuilder.begin(7, DefaultVertexFormat.POSITION_TEX_COLOR);
                bufferbuilder.vertex(d + d4, e + k, f + d5).texture(1.0, d15).color(f1, f2, f3, 1.0F).nextVertex();
                bufferbuilder.vertex(d + d4, e + ix, f + d5).texture(1.0, d14).color(f1, f2, f3, 1.0F).nextVertex();
                bufferbuilder.vertex(d + d6, e + ix, f + d7).texture(0.0, d14).color(f1, f2, f3, 1.0F).nextVertex();
                bufferbuilder.vertex(d + d6, e + k, f + d7).texture(0.0, d15).color(f1, f2, f3, 1.0F).nextVertex();
                bufferbuilder.vertex(d + d10, e + k, f + d11).texture(1.0, d15).color(f1, f2, f3, 1.0F).nextVertex();
                bufferbuilder.vertex(d + d10, e + ix, f + d11).texture(1.0, d14).color(f1, f2, f3, 1.0F).nextVertex();
                bufferbuilder.vertex(d + d8, e + ix, f + d9).texture(0.0, d14).color(f1, f2, f3, 1.0F).nextVertex();
                bufferbuilder.vertex(d + d8, e + k, f + d9).texture(0.0, d15).color(f1, f2, f3, 1.0F).nextVertex();
                bufferbuilder.vertex(d + d6, e + k, f + d7).texture(1.0, d15).color(f1, f2, f3, 1.0F).nextVertex();
                bufferbuilder.vertex(d + d6, e + ix, f + d7).texture(1.0, d14).color(f1, f2, f3, 1.0F).nextVertex();
                bufferbuilder.vertex(d + d10, e + ix, f + d11).texture(0.0, d14).color(f1, f2, f3, 1.0F).nextVertex();
                bufferbuilder.vertex(d + d10, e + k, f + d11).texture(0.0, d15).color(f1, f2, f3, 1.0F).nextVertex();
                bufferbuilder.vertex(d + d8, e + k, f + d9).texture(1.0, d15).color(f1, f2, f3, 1.0F).nextVertex();
                bufferbuilder.vertex(d + d8, e + ix, f + d9).texture(1.0, d14).color(f1, f2, f3, 1.0F).nextVertex();
                bufferbuilder.vertex(d + d4, e + ix, f + d5).texture(0.0, d14).color(f1, f2, f3, 1.0F).nextVertex();
                bufferbuilder.vertex(d + d4, e + k, f + d5).texture(0.0, d15).color(f1, f2, f3, 1.0F).nextVertex();
                tesselator.end();
                GlStateManager.enableBlend();
                GlStateManager.blendFuncSeparate(770, 771, 1, 0);
                GlStateManager.depthMask(false);
                d2 = 0.2;
                d3 = 0.2;
                d4 = 0.8;
                d5 = 0.2;
                d6 = 0.2;
                d7 = 0.8;
                d8 = 0.8;
                d9 = 0.8;
                d10 = 0.0;
                d11 = 1.0;
                d12 = -1.0 + d1;
                d13 = beaconblockentity$beamsection.getHeight() * fx + d12;
                bufferbuilder.begin(7, DefaultVertexFormat.POSITION_TEX_COLOR);
                bufferbuilder.vertex(d + 0.2, e + k, f + 0.2).texture(1.0, d13).color(f1, f2, f3, 0.125F).nextVertex();
                bufferbuilder.vertex(d + 0.2, e + ix, f + 0.2).texture(1.0, d12).color(f1, f2, f3, 0.125F).nextVertex();
                bufferbuilder.vertex(d + 0.8, e + ix, f + 0.2).texture(0.0, d12).color(f1, f2, f3, 0.125F).nextVertex();
                bufferbuilder.vertex(d + 0.8, e + k, f + 0.2).texture(0.0, d13).color(f1, f2, f3, 0.125F).nextVertex();
                bufferbuilder.vertex(d + 0.8, e + k, f + 0.8).texture(1.0, d13).color(f1, f2, f3, 0.125F).nextVertex();
                bufferbuilder.vertex(d + 0.8, e + ix, f + 0.8).texture(1.0, d12).color(f1, f2, f3, 0.125F).nextVertex();
                bufferbuilder.vertex(d + 0.2, e + ix, f + 0.8).texture(0.0, d12).color(f1, f2, f3, 0.125F).nextVertex();
                bufferbuilder.vertex(d + 0.2, e + k, f + 0.8).texture(0.0, d13).color(f1, f2, f3, 0.125F).nextVertex();
                bufferbuilder.vertex(d + 0.8, e + k, f + 0.2).texture(1.0, d13).color(f1, f2, f3, 0.125F).nextVertex();
                bufferbuilder.vertex(d + 0.8, e + ix, f + 0.2).texture(1.0, d12).color(f1, f2, f3, 0.125F).nextVertex();
                bufferbuilder.vertex(d + 0.8, e + ix, f + 0.8).texture(0.0, d12).color(f1, f2, f3, 0.125F).nextVertex();
                bufferbuilder.vertex(d + 0.8, e + k, f + 0.8).texture(0.0, d13).color(f1, f2, f3, 0.125F).nextVertex();
                bufferbuilder.vertex(d + 0.2, e + k, f + 0.8).texture(1.0, d13).color(f1, f2, f3, 0.125F).nextVertex();
                bufferbuilder.vertex(d + 0.2, e + ix, f + 0.8).texture(1.0, d12).color(f1, f2, f3, 0.125F).nextVertex();
                bufferbuilder.vertex(d + 0.2, e + ix, f + 0.2).texture(0.0, d12).color(f1, f2, f3, 0.125F).nextVertex();
                bufferbuilder.vertex(d + 0.2, e + k, f + 0.2).texture(0.0, d13).color(f1, f2, f3, 0.125F).nextVertex();
                tesselator.end();
                GlStateManager.enableLighting();
                GlStateManager.enableTexture();
                GlStateManager.depthMask(true);
                ix = k;
            }

            GlStateManager.enableFog();
        }
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }
}
