package net.minecraft.client.render.texture;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

public class SkinImageProcessor implements HttpImageProcessor {
    private int[] data;
    private int width;
    private int height;

    @Override
    public BufferedImage process(BufferedImage image) {
        if (image == null) {
            return null;
        }

        this.width = 64;
        this.height = 64;
        BufferedImage bufferedimage = new BufferedImage(this.width, this.height, 2);
        Graphics graphics = bufferedimage.getGraphics();
        graphics.drawImage(image, 0, 0, null);
        if (image.getHeight() == 32) {
            graphics.drawImage(bufferedimage, 24, 48, 20, 52, 4, 16, 8, 20, null);
            graphics.drawImage(bufferedimage, 28, 48, 24, 52, 8, 16, 12, 20, null);
            graphics.drawImage(bufferedimage, 20, 52, 16, 64, 8, 20, 12, 32, null);
            graphics.drawImage(bufferedimage, 24, 52, 20, 64, 4, 20, 8, 32, null);
            graphics.drawImage(bufferedimage, 28, 52, 24, 64, 0, 20, 4, 32, null);
            graphics.drawImage(bufferedimage, 32, 52, 28, 64, 12, 20, 16, 32, null);
            graphics.drawImage(bufferedimage, 40, 48, 36, 52, 44, 16, 48, 20, null);
            graphics.drawImage(bufferedimage, 44, 48, 40, 52, 48, 16, 52, 20, null);
            graphics.drawImage(bufferedimage, 36, 52, 32, 64, 48, 20, 52, 32, null);
            graphics.drawImage(bufferedimage, 40, 52, 36, 64, 44, 20, 48, 32, null);
            graphics.drawImage(bufferedimage, 44, 52, 40, 64, 40, 20, 44, 32, null);
            graphics.drawImage(bufferedimage, 48, 52, 44, 64, 52, 20, 56, 32, null);
        }

        graphics.dispose();
        this.data = ((DataBufferInt)bufferedimage.getRaster().getDataBuffer()).getData();
        this.setOpaque(0, 0, 32, 16);
        this.setTransparent(32, 0, 64, 32);
        this.setOpaque(0, 16, 64, 32);
        this.setTransparent(0, 32, 16, 48);
        this.setTransparent(16, 32, 40, 48);
        this.setTransparent(40, 32, 56, 48);
        this.setTransparent(0, 48, 16, 64);
        this.setOpaque(16, 48, 48, 64);
        this.setTransparent(48, 48, 64, 64);
        return bufferedimage;
    }

    @Override
    public void onTextureDownloaded() {
    }

    private void setTransparent(int u1, int v1, int u2, int v2) {
        if (!this.hasTransperancy(u1, v1, u2, v2)) {
            for (int i = u1; i < u2; i++) {
                for (int j = v1; j < v2; j++) {
                    this.data[i + j * this.width] = this.data[i + j * this.width] & 16777215;
                }
            }
        }
    }

    private void setOpaque(int u1, int v1, int u2, int v2) {
        for (int i = u1; i < u2; i++) {
            for (int j = v1; j < v2; j++) {
                this.data[i + j * this.width] = this.data[i + j * this.width] | 0xFF000000;
            }
        }
    }

    private boolean hasTransperancy(int u1, int v1, int u2, int v2) {
        for (int i = u1; i < u2; i++) {
            for (int j = v1; j < v2; j++) {
                int k = this.data[i + j * this.width];
                if ((k >> 24 & 0xFF) < 128) {
                    return true;
                }
            }
        }

        return false;
    }
}
