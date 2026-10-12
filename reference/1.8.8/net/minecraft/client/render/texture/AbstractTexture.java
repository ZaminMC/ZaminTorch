package net.minecraft.client.render.texture;

import org.lwjgl.opengl.GL11;

public abstract class AbstractTexture implements Texture {
    protected int glId = -1;
    protected boolean blur;
    protected boolean mipmap;
    protected boolean prevBlur;
    protected boolean prevMipmap;

    public void setFilter(boolean blur, boolean mipmap) {
        this.blur = blur;
        this.mipmap = mipmap;
        int i = -1;
        int j = -1;
        short short1;
        if (blur) {
            i = mipmap ? 9987 : 9729;
            short1 = 9729;
        } else {
            i = mipmap ? 9986 : 9728;
            short1 = 9728;
        }

        GL11.glTexParameteri(3553, 10241, i);
        GL11.glTexParameteri(3553, 10240, short1);
    }

    @Override
    public void pushFilter(boolean blur, boolean mipmap) {
        this.prevBlur = this.blur;
        this.prevMipmap = this.mipmap;
        this.setFilter(blur, mipmap);
    }

    @Override
    public void popFilter() {
        this.setFilter(this.prevBlur, this.prevMipmap);
    }

    @Override
    public int getGlId() {
        if (this.glId == -1) {
            this.glId = TextureUtil.genTextures();
        }

        return this.glId;
    }

    public void clearGlId() {
        if (this.glId != -1) {
            TextureUtil.deleteTextures(this.glId);
            this.glId = -1;
        }
    }
}
