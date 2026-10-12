package net.minecraft.client;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.Buffer;
import java.nio.IntBuffer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.imageio.ImageIO;
import net.minecraft.client.render.pipeline.RenderTarget;
import net.minecraft.client.render.platform.GLX;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.TextureUtil;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

public class Screenshot {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss");
    private static IntBuffer pixels;
    private static int[] pixelBuffer;

    public static Text take(File gameDir, int width, int height, RenderTarget target) {
        return take(gameDir, null, width, height, target);
    }

    public static Text take(File gameDir, String name, int width, int height, RenderTarget target) {
        try {
            File file1 = new File(gameDir, "screenshots");
            file1.mkdir();
            if (GLX.useFbo()) {
                width = target.width;
                height = target.height;
            }

            int i = width * height;
            if (pixels == null || pixels.capacity() < i) {
                pixels = BufferUtils.createIntBuffer(i);
                pixelBuffer = new int[i];
            }

            GL11.glPixelStorei(3333, 1);
            GL11.glPixelStorei(3317, 1);
            ((Buffer)pixels).clear();
            if (GLX.useFbo()) {
                GlStateManager.bindTexture(target.colorTextureId);
                GL11.glGetTexImage(3553, 0, 32993, 33639, pixels);
            } else {
                GL11.glReadPixels(0, 0, width, height, 32993, 33639, pixels);
            }

            pixels.get(pixelBuffer);
            TextureUtil.copyTextureValues(pixelBuffer, width, height);
            BufferedImage bufferedimage = null;
            if (GLX.useFbo()) {
                bufferedimage = new BufferedImage(target.viewWidth, target.viewHeight, 1);
                int j = target.height - target.viewHeight;

                for (int k = j; k < target.height; k++) {
                    for (int l = 0; l < target.viewWidth; l++) {
                        bufferedimage.setRGB(l, k - j, pixelBuffer[k * target.width + l]);
                    }
                }
            } else {
                bufferedimage = new BufferedImage(width, height, 1);
                bufferedimage.setRGB(0, 0, width, height, pixelBuffer, 0, width);
            }

            File file2;
            if (name == null) {
                file2 = nextScreenshotFile(file1);
            } else {
                file2 = new File(file1, name);
            }

            ImageIO.write(bufferedimage, "png", file2);
            Text text = new LiteralText(file2.getName());
            text.getStyle().setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, file2.getAbsolutePath()));
            text.getStyle().setUnderlined(true);
            return new TranslatableText("screenshot.success", text);
        } catch (Exception exception) {
            LOGGER.warn("Couldn't save screenshot", exception);
            return new TranslatableText("screenshot.failure", exception.getMessage());
        }
    }

    private static File nextScreenshotFile(File dir) {
        String s = DATE_FORMAT.format(new Date()).toString();
        int i = 1;

        while (true) {
            File file1 = new File(dir, s + (i == 1 ? "" : "_" + i) + ".png");
            if (!file1.exists()) {
                return file1;
            }

            i++;
        }
    }
}
