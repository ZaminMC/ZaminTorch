package net.minecraft.client.render.texture;

import java.awt.image.BufferedImage;
import java.io.IOException;
import net.minecraft.client.resource.manager.ResourceManager;

public class DynamicTexture extends AbstractTexture {
    private final int[] pixels;
    private final int width;
    private final int height;

    public DynamicTexture(BufferedImage image) {
        this(image.getWidth(), image.getHeight());
        image.getRGB(0, 0, image.getWidth(), image.getHeight(), this.pixels, 0, image.getWidth());
        this.upload();
    }

    public DynamicTexture(int width, int height) {
        this.width = width;
        this.height = height;
        this.pixels = new int[width * height];
        TextureUtil.prepare(this.getGlId(), width, height);
    }

    @Override
    public void load(ResourceManager resourceManager) throws IOException {
    }

    public void upload() {
        TextureUtil.uploadTexture(this.getGlId(), this.pixels, this.width, this.height);
    }

    public int[] getPixels() {
        return this.pixels;
    }
}
