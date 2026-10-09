package net.minecraft.client.render.entity;

import java.util.Random;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.entity.global.LightningBoltEntity;
import net.minecraft.resource.Identifier;

public class LightningBoltRenderer extends EntityRenderer<LightningBoltEntity> {
    public LightningBoltRenderer(EntityRenderDispatcher entityRenderDispatcher) {
        super(entityRenderDispatcher);
    }

    public void render(LightningBoltEntity lightningBoltEntity, double d, double e, double f, float g, float h) {
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuffer();
        GlStateManager.disableTexture();
        GlStateManager.disableLighting();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(770, 1);
        double[] adouble = new double[8];
        double[] adouble1 = new double[8];
        double d0 = 0.0;
        double d1 = 0.0;
        Random random = new Random(lightningBoltEntity.seed);

        for (int i = 7; i >= 0; i--) {
            adouble[i] = d0;
            adouble1[i] = d1;
            d0 += random.nextInt(11) - 5;
            d1 += random.nextInt(11) - 5;
        }

        for (int k1 = 0; k1 < 4; k1++) {
            Random random1 = new Random(lightningBoltEntity.seed);

            for (int j = 0; j < 3; j++) {
                int k = 7;
                int l = 0;
                if (j > 0) {
                    k = 7 - j;
                }

                if (j > 0) {
                    l = k - 2;
                }

                double d2 = adouble[k] - d0;
                double d3 = adouble1[k] - d1;

                for (int i1 = k; i1 >= l; i1--) {
                    double d4 = d2;
                    double d5 = d3;
                    if (j == 0) {
                        d2 += random1.nextInt(11) - 5;
                        d3 += random1.nextInt(11) - 5;
                    } else {
                        d2 += random1.nextInt(31) - 15;
                        d3 += random1.nextInt(31) - 15;
                    }

                    bufferbuilder.begin(5, DefaultVertexFormat.POSITION_COLOR);
                    float fx = 0.5F;
                    float f1 = 0.45F;
                    float f2 = 0.45F;
                    float f3 = 0.5F;
                    double d6 = 0.1 + k1 * 0.2;
                    if (j == 0) {
                        d6 *= i1 * 0.1 + 1.0;
                    }

                    double d7 = 0.1 + k1 * 0.2;
                    if (j == 0) {
                        d7 *= (i1 - 1) * 0.1 + 1.0;
                    }

                    for (int j1 = 0; j1 < 5; j1++) {
                        double d8 = d + 0.5 - d6;
                        double d9 = f + 0.5 - d6;
                        if (j1 == 1 || j1 == 2) {
                            d8 += d6 * 2.0;
                        }

                        if (j1 == 2 || j1 == 3) {
                            d9 += d6 * 2.0;
                        }

                        double d10 = d + 0.5 - d7;
                        double d11 = f + 0.5 - d7;
                        if (j1 == 1 || j1 == 2) {
                            d10 += d7 * 2.0;
                        }

                        if (j1 == 2 || j1 == 3) {
                            d11 += d7 * 2.0;
                        }

                        bufferbuilder.vertex(d10 + d2, e + i1 * 16, d11 + d3).color(0.45F, 0.45F, 0.5F, 0.3F).nextVertex();
                        bufferbuilder.vertex(d8 + d4, e + (i1 + 1) * 16, d9 + d5).color(0.45F, 0.45F, 0.5F, 0.3F).nextVertex();
                    }

                    tesselator.end();
                }
            }
        }

        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.enableTexture();
    }

    protected Identifier getTextureLocation(LightningBoltEntity lightningBoltEntity) {
        return null;
    }
}
