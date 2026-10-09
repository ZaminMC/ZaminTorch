package net.minecraft.client.render;

import com.google.common.collect.Lists;
import java.io.IOException;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.pipeline.RenderTarget;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.client.resource.manager.ResourceManager;
import org.lwjgl.util.vector.Matrix4f;

public class PostPass {
    private final Effect effect;
    public final RenderTarget inTarget;
    public final RenderTarget outTarget;
    private final List<Object> auxAssets = Lists.newArrayList();
    private final List<String> auxNames = Lists.newArrayList();
    private final List<Integer> auxWidths = Lists.newArrayList();
    private final List<Integer> auxHeights = Lists.newArrayList();
    private Matrix4f shaderOrthoMatrix;

    public PostPass(ResourceManager resourceManager, String name, RenderTarget inTarget, RenderTarget outTarget) throws IOException {
        this.effect = new Effect(resourceManager, name);
        this.inTarget = inTarget;
        this.outTarget = outTarget;
    }

    public void close() {
        this.effect.close();
    }

    public void addAuxAsset(String name, Object asset, int width, int height) {
        this.auxNames.add(this.auxNames.size(), name);
        this.auxAssets.add(this.auxAssets.size(), asset);
        this.auxWidths.add(this.auxWidths.size(), width);
        this.auxHeights.add(this.auxHeights.size(), height);
    }

    private void prepareState() {
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableBlend();
        GlStateManager.disableDepthTest();
        GlStateManager.disableAlphaTest();
        GlStateManager.disableFog();
        GlStateManager.disableLighting();
        GlStateManager.disableColorMaterial();
        GlStateManager.enableTexture();
        GlStateManager.bindTexture(0);
    }

    public void setOrthoMatrix(Matrix4f matrix) {
        this.shaderOrthoMatrix = matrix;
    }

    public void process(float tickDelta) {
        this.prepareState();
        this.inTarget.unbindWrite();
        float f = this.outTarget.width;
        float f1 = this.outTarget.height;
        GlStateManager.viewport(0, 0, (int)f, (int)f1);
        this.effect.setSampler("DiffuseSampler", this.inTarget);

        for (int i = 0; i < this.auxAssets.size(); i++) {
            this.effect.setSampler(this.auxNames.get(i), this.auxAssets.get(i));
            this.effect.safeGetUniform("AuxSize" + i).set(this.auxWidths.get(i).intValue(), this.auxHeights.get(i).intValue());
        }

        this.effect.safeGetUniform("ProjMat").set(this.shaderOrthoMatrix);
        this.effect.safeGetUniform("InSize").set(this.inTarget.width, this.inTarget.height);
        this.effect.safeGetUniform("OutSize").set(f, f1);
        this.effect.safeGetUniform("Time").set(tickDelta);
        Minecraft minecraft = Minecraft.getInstance();
        this.effect.safeGetUniform("ScreenSize").set(minecraft.width, minecraft.height);
        this.effect.apply();
        this.outTarget.clear();
        this.outTarget.bindWrite(false);
        GlStateManager.depthMask(false);
        GlStateManager.colorMask(true, true, true, true);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuffer();
        bufferbuilder.begin(7, DefaultVertexFormat.POSITION_COLOR);
        bufferbuilder.vertex(0.0, f1, 500.0).color(255, 255, 255, 255).nextVertex();
        bufferbuilder.vertex(f, f1, 500.0).color(255, 255, 255, 255).nextVertex();
        bufferbuilder.vertex(f, 0.0, 500.0).color(255, 255, 255, 255).nextVertex();
        bufferbuilder.vertex(0.0, 0.0, 500.0).color(255, 255, 255, 255).nextVertex();
        tesselator.end();
        GlStateManager.depthMask(true);
        GlStateManager.colorMask(true, true, true, true);
        this.effect.clear();
        this.outTarget.unbindWrite();
        this.inTarget.unbindRead();

        for (Object object : this.auxAssets) {
            if (object instanceof RenderTarget) {
                ((RenderTarget)object).unbindRead();
            }
        }
    }

    public Effect getEffect() {
        return this.effect;
    }
}
