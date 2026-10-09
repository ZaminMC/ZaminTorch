package net.minecraft.client.render.model;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.platform.MemoryTracker;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.Tesselator;
import org.lwjgl.opengl.GL11;

public class ModelPart {
    public float textureWidth = 64.0F;
    public float textureHeight = 32.0F;
    private int u;
    private int v;
    public float x;
    public float y;
    public float z;
    public float rotationX;
    public float rotationY;
    public float rotationZ;
    private boolean compiled;
    private int glList;
    public boolean flipped;
    public boolean visible = true;
    public boolean invisible;
    public List<Box> boxes = Lists.newArrayList();
    public List<ModelPart> children;
    public final String id;
    private Model model;
    public float translateX;
    public float translateY;
    public float translateZ;

    public ModelPart(Model model, String id) {
        this.model = model;
        model.parts.add(this);
        this.id = id;
        this.setTextureSize(model.textureWidth, model.textureHeight);
    }

    public ModelPart(Model model) {
        this(model, null);
    }

    public ModelPart(Model model, int u, int v) {
        this(model);
        this.setTextureCoords(u, v);
    }

    public void addChild(ModelPart part) {
        if (this.children == null) {
            this.children = Lists.newArrayList();
        }

        this.children.add(part);
    }

    public ModelPart setTextureCoords(int textureU, int textureV) {
        this.u = textureU;
        this.v = textureV;
        return this;
    }

    public ModelPart addBox(String id, float x, float y, float z, int sizeX, int sizeY, int sizeZ) {
        id = this.id + "." + id;
        TexturePos texturepos = this.model.getTexturePos(id);
        this.setTextureCoords(texturepos.u, texturepos.v);
        this.boxes.add(new Box(this, this.u, this.v, x, y, z, sizeX, sizeY, sizeZ, 0.0F).setId(id));
        return this;
    }

    public ModelPart addBox(float x, float y, float z, int sizeX, int sizeY, int sizeZ) {
        this.boxes.add(new Box(this, this.u, this.v, x, y, z, sizeX, sizeY, sizeZ, 0.0F));
        return this;
    }

    public ModelPart addBox(float x, float y, float z, int sizeX, int sizeY, int sizeZ, boolean flipped) {
        this.boxes.add(new Box(this, this.u, this.v, x, y, z, sizeX, sizeY, sizeZ, 0.0F, flipped));
        return this;
    }

    public void addBox(float x, float y, float z, int sizeX, int sizeY, int sizeZ, float increase) {
        this.boxes.add(new Box(this, this.u, this.v, x, y, z, sizeX, sizeY, sizeZ, increase));
    }

    public void setPos(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public void render(float scale) {
        if (!this.invisible) {
            if (this.visible) {
                if (!this.compiled) {
                    this.compile(scale);
                }

                GlStateManager.translatef(this.translateX, this.translateY, this.translateZ);
                if (this.rotationX != 0.0F || this.rotationY != 0.0F || this.rotationZ != 0.0F) {
                    GlStateManager.pushMatrix();
                    GlStateManager.translatef(this.x * scale, this.y * scale, this.z * scale);
                    if (this.rotationZ != 0.0F) {
                        GlStateManager.rotatef(this.rotationZ * (180.0F / (float)Math.PI), 0.0F, 0.0F, 1.0F);
                    }

                    if (this.rotationY != 0.0F) {
                        GlStateManager.rotatef(this.rotationY * (180.0F / (float)Math.PI), 0.0F, 1.0F, 0.0F);
                    }

                    if (this.rotationX != 0.0F) {
                        GlStateManager.rotatef(this.rotationX * (180.0F / (float)Math.PI), 1.0F, 0.0F, 0.0F);
                    }

                    GlStateManager.callList(this.glList);
                    if (this.children != null) {
                        for (int k = 0; k < this.children.size(); k++) {
                            this.children.get(k).render(scale);
                        }
                    }

                    GlStateManager.popMatrix();
                } else if (this.x == 0.0F && this.y == 0.0F && this.z == 0.0F) {
                    GlStateManager.callList(this.glList);
                    if (this.children != null) {
                        for (int j = 0; j < this.children.size(); j++) {
                            this.children.get(j).render(scale);
                        }
                    }
                } else {
                    GlStateManager.translatef(this.x * scale, this.y * scale, this.z * scale);
                    GlStateManager.callList(this.glList);
                    if (this.children != null) {
                        for (int i = 0; i < this.children.size(); i++) {
                            this.children.get(i).render(scale);
                        }
                    }

                    GlStateManager.translatef(-this.x * scale, -this.y * scale, -this.z * scale);
                }

                GlStateManager.translatef(-this.translateX, -this.translateY, -this.translateZ);
            }
        }
    }

    public void renderForceTransform(float scale) {
        if (!this.invisible) {
            if (this.visible) {
                if (!this.compiled) {
                    this.compile(scale);
                }

                GlStateManager.pushMatrix();
                GlStateManager.translatef(this.x * scale, this.y * scale, this.z * scale);
                if (this.rotationY != 0.0F) {
                    GlStateManager.rotatef(this.rotationY * (180.0F / (float)Math.PI), 0.0F, 1.0F, 0.0F);
                }

                if (this.rotationX != 0.0F) {
                    GlStateManager.rotatef(this.rotationX * (180.0F / (float)Math.PI), 1.0F, 0.0F, 0.0F);
                }

                if (this.rotationZ != 0.0F) {
                    GlStateManager.rotatef(this.rotationZ * (180.0F / (float)Math.PI), 0.0F, 0.0F, 1.0F);
                }

                GlStateManager.callList(this.glList);
                GlStateManager.popMatrix();
            }
        }
    }

    public void transform(float scale) {
        if (!this.invisible) {
            if (this.visible) {
                if (!this.compiled) {
                    this.compile(scale);
                }

                if (this.rotationX != 0.0F || this.rotationY != 0.0F || this.rotationZ != 0.0F) {
                    GlStateManager.translatef(this.x * scale, this.y * scale, this.z * scale);
                    if (this.rotationZ != 0.0F) {
                        GlStateManager.rotatef(this.rotationZ * (180.0F / (float)Math.PI), 0.0F, 0.0F, 1.0F);
                    }

                    if (this.rotationY != 0.0F) {
                        GlStateManager.rotatef(this.rotationY * (180.0F / (float)Math.PI), 0.0F, 1.0F, 0.0F);
                    }

                    if (this.rotationX != 0.0F) {
                        GlStateManager.rotatef(this.rotationX * (180.0F / (float)Math.PI), 1.0F, 0.0F, 0.0F);
                    }
                } else if (this.x != 0.0F || this.y != 0.0F || this.z != 0.0F) {
                    GlStateManager.translatef(this.x * scale, this.y * scale, this.z * scale);
                }
            }
        }
    }

    private void compile(float scale) {
        this.glList = MemoryTracker.getLists(1);
        GL11.glNewList(this.glList, 4864);
        BufferBuilder bufferbuilder = Tesselator.getInstance().getBuffer();

        for (int i = 0; i < this.boxes.size(); i++) {
            this.boxes.get(i).compile(bufferbuilder, scale);
        }

        GL11.glEndList();
        this.compiled = true;
    }

    public ModelPart setTextureSize(int width, int height) {
        this.textureWidth = width;
        this.textureHeight = height;
        return this;
    }
}
