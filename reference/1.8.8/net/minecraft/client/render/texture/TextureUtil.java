package net.minecraft.client.render.texture;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.Buffer;
import java.nio.IntBuffer;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.platform.MemoryTracker;
import net.minecraft.client.resource.manager.ResourceManager;
import net.minecraft.resource.Identifier;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;

public class TextureUtil {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final IntBuffer BUFFER = MemoryTracker.createIntBuffer(4194304);
    public static final DynamicTexture MISSING_TEXTURE = new DynamicTexture(16, 16);
    public static final int[] MISSING_DATA = MISSING_TEXTURE.getPixels();
    private static final int[] MIPMAP_BUFFER;

    public static int genTextures() {
        return GlStateManager.genTextures();
    }

    public static void deleteTextures(int id) {
        GlStateManager.deleteTextures(id);
    }

    public static int uploadTexture(int id, BufferedImage image) {
        return upload(id, image, false, false);
    }

    public static void uploadTexture(int id, int[] pixels, int width, int height) {
        bind(id);
        upload(0, pixels, width, height, 0, 0, false, false, false);
    }

    public static int[][] generateMipmaps(int mipmaps, int width, int[][] pixels) {
        int[][] aint = new int[mipmaps + 1][];
        aint[0] = pixels[0];
        if (mipmaps > 0) {
            boolean flag = false;

            for (int i = 0; i < pixels.length; i++) {
                if (pixels[0][i] >> 24 == 0) {
                    flag = true;
                    break;
                }
            }

            for (int l1 = 1; l1 <= mipmaps; l1++) {
                if (pixels[l1] != null) {
                    aint[l1] = pixels[l1];
                } else {
                    int[] aint1 = aint[l1 - 1];
                    int[] aint2 = new int[aint1.length >> 2];
                    int j = width >> l1;
                    int k = aint2.length / j;
                    int l = j << 1;

                    for (int i1 = 0; i1 < j; i1++) {
                        for (int j1 = 0; j1 < k; j1++) {
                            int k1 = 2 * (i1 + j1 * l);
                            aint2[i1 + j1 * j] = blendPixels(aint1[k1 + 0], aint1[k1 + 1], aint1[k1 + 0 + l], aint1[k1 + 1 + l], flag);
                        }
                    }

                    aint[l1] = aint2;
                }
            }
        }

        return aint;
    }

    private static int blendPixels(int colorTopLeft, int colorTopRight, int colorBottomLeft, int colorBottomRight, boolean transperant) {
        if (!transperant) {
            int i1 = blendPixelComponents(colorTopLeft, colorTopRight, colorBottomLeft, colorBottomRight, 24);
            int j1 = blendPixelComponents(colorTopLeft, colorTopRight, colorBottomLeft, colorBottomRight, 16);
            int k1 = blendPixelComponents(colorTopLeft, colorTopRight, colorBottomLeft, colorBottomRight, 8);
            int l1 = blendPixelComponents(colorTopLeft, colorTopRight, colorBottomLeft, colorBottomRight, 0);
            return i1 << 24 | j1 << 16 | k1 << 8 | l1;
        }

        MIPMAP_BUFFER[0] = colorTopLeft;
        MIPMAP_BUFFER[1] = colorTopRight;
        MIPMAP_BUFFER[2] = colorBottomLeft;
        MIPMAP_BUFFER[3] = colorBottomRight;
        float f = 0.0F;
        float f1 = 0.0F;
        float f2 = 0.0F;
        float f3 = 0.0F;

        for (int i = 0; i < 4; i++) {
            if (MIPMAP_BUFFER[i] >> 24 != 0) {
                f += (float)Math.pow((MIPMAP_BUFFER[i] >> 24 & 0xFF) / 255.0F, 2.2);
                f1 += (float)Math.pow((MIPMAP_BUFFER[i] >> 16 & 0xFF) / 255.0F, 2.2);
                f2 += (float)Math.pow((MIPMAP_BUFFER[i] >> 8 & 0xFF) / 255.0F, 2.2);
                f3 += (float)Math.pow((MIPMAP_BUFFER[i] >> 0 & 0xFF) / 255.0F, 2.2);
            }
        }

        f /= 4.0F;
        f1 /= 4.0F;
        f2 /= 4.0F;
        f3 /= 4.0F;
        int i2 = (int)(Math.pow(f, 0.45454545454545453) * 255.0);
        int j = (int)(Math.pow(f1, 0.45454545454545453) * 255.0);
        int k = (int)(Math.pow(f2, 0.45454545454545453) * 255.0);
        int l = (int)(Math.pow(f3, 0.45454545454545453) * 255.0);
        if (i2 < 96) {
            i2 = 0;
        }

        return i2 << 24 | j << 16 | k << 8 | l;
    }

    private static int blendPixelComponents(int colorTopLeft, int colorTopRight, int colorBottomLeft, int colorBottomRight, int componentShift) {
        float f = (float)Math.pow((colorTopLeft >> componentShift & 0xFF) / 255.0F, 2.2);
        float f1 = (float)Math.pow((colorTopRight >> componentShift & 0xFF) / 255.0F, 2.2);
        float f2 = (float)Math.pow((colorBottomLeft >> componentShift & 0xFF) / 255.0F, 2.2);
        float f3 = (float)Math.pow((colorBottomRight >> componentShift & 0xFF) / 255.0F, 2.2);
        float f4 = (float)Math.pow((f + f1 + f2 + f3) * 0.25, 0.45454545454545453);
        return (int)(f4 * 255.0);
    }

    public static void upload(int[][] texture, int xOffset, int yOffset, int width, int height, boolean blur, boolean clamped) {
        for (int i = 0; i < texture.length; i++) {
            int[] aint = texture[i];
            upload(i, aint, xOffset >> i, yOffset >> i, width >> i, height >> i, blur, clamped, texture.length > 1);
        }
    }

    private static void upload(int level, int[] texture, int xOffset, int yOffset, int width, int height, boolean blur, boolean clamped, boolean mipmap) {
        int i = 4194304 / xOffset;
        setTextureFilter(blur, mipmap);
        setTextureClamp(clamped);
        int j = 0;

        while (j < xOffset * yOffset) {
            int k = j / xOffset;
            int l = Math.min(i, yOffset - k);
            int i1 = xOffset * l;
            putInBufferAt(texture, j, i1);
            GL11.glTexSubImage2D(3553, level, width, height + k, xOffset, l, 32993, 33639, BUFFER);
            j += xOffset * l;
        }
    }

    public static int upload(int texture, BufferedImage image, boolean blur, boolean mipmap) {
        prepare(texture, image.getWidth(), image.getHeight());
        return upload(texture, image, 0, 0, blur, mipmap);
    }

    public static void prepare(int id, int width, int height) {
        prepare(id, 0, width, height);
    }

    public static void prepare(int id, int mipmapLevel, int width, int height) {
        deleteTextures(id);
        bind(id);
        if (mipmapLevel >= 0) {
            GL11.glTexParameteri(3553, 33085, mipmapLevel);
            GL11.glTexParameterf(3553, 33082, 0.0F);
            GL11.glTexParameterf(3553, 33083, mipmapLevel);
            GL11.glTexParameterf(3553, 34049, 0.0F);
        }

        for (int i = 0; i <= mipmapLevel; i++) {
            GL11.glTexImage2D(3553, i, 6408, width >> i, height >> i, 0, 32993, 33639, (IntBuffer)null);
        }
    }

    public static int upload(int id, BufferedImage texture, int xOffset, int yOffset, boolean blur, boolean mipmap) {
        bind(id);
        upload(texture, xOffset, yOffset, blur, mipmap);
        return id;
    }

    private static void upload(BufferedImage texture, int xOffset, int yOffset, boolean blur, boolean mipmap) {
        int i = texture.getWidth();
        int j = texture.getHeight();
        int k = 4194304 / i;
        int[] aint = new int[k * i];
        setFilterWithBlur(blur);
        setTextureClamp(mipmap);

        for (int l = 0; l < i * j; l += i * k) {
            int i1 = l / i;
            int j1 = Math.min(k, j - i1);
            int k1 = i * j1;
            texture.getRGB(0, i1, i, j1, aint, 0, i);
            putInBuffer(aint, k1);
            GL11.glTexSubImage2D(3553, 0, xOffset, yOffset + i1, i, j1, 32993, 33639, BUFFER);
        }
    }

    private static void setTextureClamp(boolean clamp) {
        if (clamp) {
            GL11.glTexParameteri(3553, 10242, 10496);
            GL11.glTexParameteri(3553, 10243, 10496);
        } else {
            GL11.glTexParameteri(3553, 10242, 10497);
            GL11.glTexParameteri(3553, 10243, 10497);
        }
    }

    private static void setFilterWithBlur(boolean blur) {
        setTextureFilter(blur, false);
    }

    private static void setTextureFilter(boolean blur, boolean mipmap) {
        if (blur) {
            GL11.glTexParameteri(3553, 10241, mipmap ? 9987 : 9729);
            GL11.glTexParameteri(3553, 10240, 9729);
        } else {
            GL11.glTexParameteri(3553, 10241, mipmap ? 9986 : 9728);
            GL11.glTexParameteri(3553, 10240, 9728);
        }
    }

    private static void putInBuffer(int[] texture, int size) {
        putInBufferAt(texture, 0, size);
    }

    private static void putInBufferAt(int[] texture, int offset, int size) {
        int[] aint = texture;
        if (Minecraft.getInstance().options.anaglyph) {
            aint = getAnaglyphColors(texture);
        }

        ((Buffer)BUFFER).clear();
        BUFFER.put(aint, offset, size);
        ((Buffer)BUFFER).position(0).limit(size);
    }

    static void bind(int id) {
        GlStateManager.bindTexture(id);
    }

    public static int[] getPixels(ResourceManager resourceManager, Identifier location) throws IOException {
        BufferedImage bufferedimage = readImage(resourceManager.getResource(location).asStream());
        int i = bufferedimage.getWidth();
        int j = bufferedimage.getHeight();
        int[] aint = new int[i * j];
        bufferedimage.getRGB(0, 0, i, j, aint, 0, i);
        return aint;
    }

    public static BufferedImage readImage(InputStream is) throws IOException {
        try {
            return ImageIO.read(is);
        } finally {
            IOUtils.closeQuietly(is);
        }
    }

    public static int[] getAnaglyphColors(int[] colors) {
        int[] aint = new int[colors.length];

        for (int i = 0; i < colors.length; i++) {
            aint[i] = getAnaglyphColor(colors[i]);
        }

        return aint;
    }

    public static int getAnaglyphColor(int color) {
        int i = color >> 24 & 0xFF;
        int j = color >> 16 & 0xFF;
        int k = color >> 8 & 0xFF;
        int l = color & 0xFF;
        int i1 = (j * 30 + k * 59 + l * 11) / 100;
        int j1 = (j * 30 + k * 70) / 100;
        int k1 = (j * 30 + l * 70) / 100;
        return i << 24 | i1 << 16 | j1 << 8 | k1;
    }

    public static void copyTextureValues(int[] texture, int lenth, int pos) {
        int[] aint = new int[lenth];
        int i = pos / 2;

        for (int j = 0; j < i; j++) {
            System.arraycopy(texture, j * lenth, aint, 0, lenth);
            System.arraycopy(texture, (pos - 1 - j) * lenth, texture, j * lenth, lenth);
            System.arraycopy(aint, 0, texture, (pos - 1 - j) * lenth, lenth);
        }
    }

    static {
        int i = -16777216;
        int j = -524040;
        int[] aint = new int[]{-524040, -524040, -524040, -524040, -524040, -524040, -524040, -524040};
        int[] aint1 = new int[]{-16777216, -16777216, -16777216, -16777216, -16777216, -16777216, -16777216, -16777216};
        int k = aint.length;

        for (int l = 0; l < 16; l++) {
            System.arraycopy(l < k ? aint : aint1, 0, MISSING_DATA, 16 * l, k);
            System.arraycopy(l < k ? aint1 : aint, 0, MISSING_DATA, 16 * l + k, k);
        }

        MISSING_TEXTURE.upload();
        MIPMAP_BUFFER = new int[4];
    }
}
