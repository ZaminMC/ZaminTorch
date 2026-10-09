package net.minecraft.client.render.texture;

import java.awt.image.BufferedImage;

public interface HttpImageProcessor {
    BufferedImage process(BufferedImage image);

    void onTextureDownloaded();
}
