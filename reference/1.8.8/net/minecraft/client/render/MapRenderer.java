package net.minecraft.client.render;

import com.google.common.collect.Maps;
import java.util.Map;
import net.minecraft.block.material.MapColor;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.DynamicTexture;
import net.minecraft.client.render.texture.TextureManager;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.resource.Identifier;
import net.minecraft.world.map.MapDecoration;
import net.minecraft.world.map.SavedMapData;

public class MapRenderer {
    private static final Identifier MAP_ICONS_LOCATION = new Identifier("textures/map/map_icons.png");
    private final TextureManager textureManager;
    private final Map<String, MapRenderer.Texture> mapTextures = Maps.newHashMap();

    public MapRenderer(TextureManager textureManager) {
        this.textureManager = textureManager;
    }

    public void updateTexture(SavedMapData mapData) {
        this.getMapTexture(mapData).updateTexture();
    }

    public void draw(SavedMapData mapData, boolean inItemFrame) {
        this.getMapTexture(mapData).draw(inItemFrame);
    }

    private MapRenderer.Texture getMapTexture(SavedMapData mapData) {
        MapRenderer.Texture maprenderer$texture = this.mapTextures.get(mapData.id);
        if (maprenderer$texture == null) {
            maprenderer$texture = new MapRenderer.Texture(mapData);
            this.mapTextures.put(mapData.id, maprenderer$texture);
        }

        return maprenderer$texture;
    }

    public void clearStateTextures() {
        for (MapRenderer.Texture maprenderer$texture : this.mapTextures.values()) {
            this.textureManager.close(maprenderer$texture.currentTexture);
        }

        this.mapTextures.clear();
    }

    class Texture {
        private final SavedMapData mapData;
        private final DynamicTexture texture;
        private final Identifier currentTexture;
        private final int[] colors;

        private Texture(SavedMapData mapData) {
            this.mapData = mapData;
            this.texture = new DynamicTexture(128, 128);
            this.colors = this.texture.getPixels();
            this.currentTexture = MapRenderer.this.textureManager.register("map/" + mapData.id, this.texture);

            for (int i = 0; i < this.colors.length; i++) {
                this.colors[i] = 0;
            }
        }

        private void updateTexture() {
            for (int i = 0; i < 16384; i++) {
                int j = this.mapData.colors[i] & 255;
                if (j / 4 == 0) {
                    this.colors[i] = (i + i / 128 & 1) * 8 + 16 << 24;
                } else {
                    this.colors[i] = MapColor.BY_ID[j / 4].getColor(j & 3);
                }
            }

            this.texture.upload();
        }

        private void draw(boolean inItemFrame) {
            int i = 0;
            int j = 0;
            Tesselator tesselator = Tesselator.getInstance();
            BufferBuilder bufferbuilder = tesselator.getBuffer();
            float f = 0.0F;
            MapRenderer.this.textureManager.bind(this.currentTexture);
            GlStateManager.enableBlend();
            GlStateManager.blendFuncSeparate(1, 771, 0, 1);
            GlStateManager.disableAlphaTest();
            bufferbuilder.begin(7, DefaultVertexFormat.POSITION_TEX);
            bufferbuilder.vertex(i + 0 + f, j + 128 - f, -0.01F).texture(0.0, 1.0).nextVertex();
            bufferbuilder.vertex(i + 128 - f, j + 128 - f, -0.01F).texture(1.0, 1.0).nextVertex();
            bufferbuilder.vertex(i + 128 - f, j + 0 + f, -0.01F).texture(1.0, 0.0).nextVertex();
            bufferbuilder.vertex(i + 0 + f, j + 0 + f, -0.01F).texture(0.0, 0.0).nextVertex();
            tesselator.end();
            GlStateManager.enableAlphaTest();
            GlStateManager.disableBlend();
            MapRenderer.this.textureManager.bind(MapRenderer.MAP_ICONS_LOCATION);
            int k = 0;

            for (MapDecoration mapdecoration : this.mapData.decorations.values()) {
                if (!inItemFrame || mapdecoration.getType() == 1) {
                    GlStateManager.pushMatrix();
                    GlStateManager.translatef(i + mapdecoration.getX() / 2.0F + 64.0F, j + mapdecoration.getY() / 2.0F + 64.0F, -0.02F);
                    GlStateManager.rotatef(mapdecoration.getRotation() * 360 / 16.0F, 0.0F, 0.0F, 1.0F);
                    GlStateManager.scalef(4.0F, 4.0F, 3.0F);
                    GlStateManager.translatef(-0.125F, 0.125F, 0.0F);
                    byte b0 = mapdecoration.getType();
                    float f1 = (b0 % 4 + 0) / 4.0F;
                    float f2 = (b0 / 4 + 0) / 4.0F;
                    float f3 = (b0 % 4 + 1) / 4.0F;
                    float f4 = (b0 / 4 + 1) / 4.0F;
                    bufferbuilder.begin(7, DefaultVertexFormat.POSITION_TEX);
                    float f5 = -0.001F;
                    bufferbuilder.vertex(-1.0, 1.0, k * -0.001F).texture(f1, f2).nextVertex();
                    bufferbuilder.vertex(1.0, 1.0, k * -0.001F).texture(f3, f2).nextVertex();
                    bufferbuilder.vertex(1.0, -1.0, k * -0.001F).texture(f3, f4).nextVertex();
                    bufferbuilder.vertex(-1.0, -1.0, k * -0.001F).texture(f1, f4).nextVertex();
                    tesselator.end();
                    GlStateManager.popMatrix();
                    k++;
                }
            }

            GlStateManager.pushMatrix();
            GlStateManager.translatef(0.0F, 0.0F, -0.04F);
            GlStateManager.scalef(1.0F, 1.0F, 1.0F);
            GlStateManager.popMatrix();
        }
    }
}
