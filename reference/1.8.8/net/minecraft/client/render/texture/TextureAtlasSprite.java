package net.minecraft.client.render.texture;

import com.google.common.collect.Lists;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;
import net.minecraft.client.resource.metadata.AnimationFrame;
import net.minecraft.client.resource.metadata.AnimationMetadata;
import net.minecraft.resource.Identifier;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportCategory;

public class TextureAtlasSprite {
    private final String name;
    protected List<int[][]> frames = Lists.newArrayList();
    protected int[][] textureImage;
    private AnimationMetadata animation;
    protected boolean rotated;
    protected int x;
    protected int y;
    protected int width;
    protected int height;
    private float uMin;
    private float uMax;
    private float vMin;
    private float vMax;
    protected int activeFrame;
    protected int frameTicks;
    private static String clockPath = "builtin/clock";
    private static String compassPath = "builtin/compass";

    protected TextureAtlasSprite(String name) {
        this.name = name;
    }

    protected static TextureAtlasSprite builtIn(Identifier id) {
        String s = id.toString();
        if (clockPath.equals(s)) {
            return new ClockSprite(s);
        } else {
            return compassPath.equals(s) ? new CompassSprite(s) : new TextureAtlasSprite(s);
        }
    }

    public static void setClockPath(String path) {
        clockPath = path;
    }

    public static void setCompassPath(String path) {
        compassPath = path;
    }

    public void init(int u, int v, int x, int y, boolean rotated) {
        this.x = x;
        this.y = y;
        this.rotated = rotated;
        float f = (float)(0.01F / u);
        float f1 = (float)(0.01F / v);
        this.uMin = x / (float)u + f;
        this.uMax = (x + this.width) / (float)u - f;
        this.vMin = (float)y / v + f1;
        this.vMax = (float)(y + this.height) / v - f1;
    }

    public void set(TextureAtlasSprite other) {
        this.x = other.x;
        this.y = other.y;
        this.width = other.width;
        this.height = other.height;
        this.rotated = other.rotated;
        this.uMin = other.uMin;
        this.uMax = other.uMax;
        this.vMin = other.vMin;
        this.vMax = other.vMax;
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }

    public float getUMin() {
        return this.uMin;
    }

    public float getUMax() {
        return this.uMax;
    }

    public float getU(double delta) {
        float f = this.uMax - this.uMin;
        return this.uMin + f * (float)delta / 16.0F;
    }

    public float getVMin() {
        return this.vMin;
    }

    public float getVMax() {
        return this.vMax;
    }

    public float getV(double delta) {
        float f = this.vMax - this.vMin;
        return this.vMin + f * ((float)delta / 16.0F);
    }

    public String getName() {
        return this.name;
    }

    public void tick() {
        this.frameTicks++;
        if (this.frameTicks >= this.animation.getFrameTime(this.activeFrame)) {
            int i = this.animation.getFrameIndex(this.activeFrame);
            int j = this.animation.getFrameCount() == 0 ? this.frames.size() : this.animation.getFrameCount();
            this.activeFrame = (this.activeFrame + 1) % j;
            this.frameTicks = 0;
            int k = this.animation.getFrameIndex(this.activeFrame);
            if (i != k && k >= 0 && k < this.frames.size()) {
                TextureUtil.upload(this.frames.get(k), this.width, this.height, this.x, this.y, false, false);
            }
        } else if (this.animation.isInterpolated()) {
            this.animatePrismarine();
        }
    }

    private void animatePrismarine() {
        double d0 = 1.0 - (double)this.frameTicks / this.animation.getFrameTime(this.activeFrame);
        int i = this.animation.getFrameIndex(this.activeFrame);
        int j = this.animation.getFrameCount() == 0 ? this.frames.size() : this.animation.getFrameCount();
        int k = this.animation.getFrameIndex((this.activeFrame + 1) % j);
        if (i != k && k >= 0 && k < this.frames.size()) {
            int[][] aint = this.frames.get(i);
            int[][] aint1 = this.frames.get(k);
            if (this.textureImage == null || this.textureImage.length != aint.length) {
                this.textureImage = new int[aint.length][];
            }

            for (int l = 0; l < aint.length; l++) {
                if (this.textureImage[l] == null) {
                    this.textureImage[l] = new int[aint[l].length];
                }

                if (l < aint1.length && aint1[l].length == aint[l].length) {
                    for (int i1 = 0; i1 < aint[l].length; i1++) {
                        int j1 = aint[l][i1];
                        int k1 = aint1[l][i1];
                        int l1 = (int)(((j1 & 0xFF0000) >> 16) * d0 + ((k1 & 0xFF0000) >> 16) * (1.0 - d0));
                        int i2 = (int)(((j1 & 0xFF00) >> 8) * d0 + ((k1 & 0xFF00) >> 8) * (1.0 - d0));
                        int j2 = (int)((j1 & 0xFF) * d0 + (k1 & 0xFF) * (1.0 - d0));
                        this.textureImage[l][i1] = j1 & 0xFF000000 | l1 << 16 | i2 << 8 | j2;
                    }
                }
            }

            TextureUtil.upload(this.textureImage, this.width, this.height, this.x, this.y, false, false);
        }
    }

    public int[][] getFrame(int frame) {
        return this.frames.get(frame);
    }

    public int getFrameCount() {
        return this.frames.size();
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public void load(BufferedImage[] image, AnimationMetadata animation) throws IOException {
        this.clear();
        int i = image[0].getWidth();
        int j = image[0].getHeight();
        this.width = i;
        this.height = j;
        int[][] aint = new int[image.length][];

        for (int k = 0; k < image.length; k++) {
            BufferedImage bufferedimage = image[k];
            if (bufferedimage != null) {
                if (k > 0 && (bufferedimage.getWidth() != i >> k || bufferedimage.getHeight() != j >> k)) {
                    throw new RuntimeException(
                        String.format(
                            "Unable to load miplevel: %d, image is size: %dx%d, expected %dx%d",
                            k,
                            bufferedimage.getWidth(),
                            bufferedimage.getHeight(),
                            i >> k,
                            j >> k
                        )
                    );
                }

                aint[k] = new int[bufferedimage.getWidth() * bufferedimage.getHeight()];
                bufferedimage.getRGB(0, 0, bufferedimage.getWidth(), bufferedimage.getHeight(), aint[k], 0, bufferedimage.getWidth());
            }
        }

        if (animation == null) {
            if (j != i) {
                throw new RuntimeException("broken aspect ratio and not an animation");
            }

            this.frames.add(aint);
        } else {
            int j1 = j / i;
            int k1 = i;
            int l = i;
            this.height = this.width;
            if (animation.getFrameCount() > 0) {
                for (int i1 : animation.getUniqueFrameIndices()) {
                    if (i1 >= j1) {
                        throw new RuntimeException("invalid frameindex " + i1);
                    }

                    this.expandFrames(i1);
                    this.frames.set(i1, resize(aint, k1, l, i1));
                }

                this.animation = animation;
            } else {
                List<AnimationFrame> list = Lists.newArrayList();

                for (int l1 = 0; l1 < j1; l1++) {
                    this.frames.add(resize(aint, k1, l, l1));
                    list.add(new AnimationFrame(l1, -1));
                }

                this.animation = new AnimationMetadata(list, this.width, this.height, animation.getDefaultFrameTime(), animation.isInterpolated());
            }
        }
    }

    public void applyMipmaps(int mipLevel) {
        List<int[][]> list = Lists.newArrayList();

        for (int i = 0; i < this.frames.size(); i++) {
            final int[][] aint = this.frames.get(i);
            if (aint != null) {
                try {
                    list.add(TextureUtil.generateMipmaps(mipLevel, this.width, aint));
                } catch (Throwable throwable) {
                    CrashReport crashreport = CrashReport.of(throwable, "Generating mipmaps for frame");
                    CrashReportCategory crashreportcategory = crashreport.addCategory("Frame being iterated");
                    crashreportcategory.add("Frame index", i);
                    crashreportcategory.add("Frame sizes", new Callable<String>() {
                        public String call() throws Exception {
                            StringBuilder stringbuilder = new StringBuilder();

                            for (int[] aint1 : aint) {
                                if (stringbuilder.length() > 0) {
                                    stringbuilder.append(", ");
                                }

                                stringbuilder.append(aint1 == null ? "null" : aint1.length);
                            }

                            return stringbuilder.toString();
                        }
                    });
                    throw new CrashException(crashreport);
                }
            }
        }

        this.setFrames(list);
    }

    private void expandFrames(int size) {
        if (this.frames.size() <= size) {
            for (int i = this.frames.size(); i <= size; i++) {
                this.frames.add(null);
            }
        }
    }

    private static int[][] resize(int[][] image, int width, int height, int size) {
        int[][] aint = new int[image.length][];

        for (int i = 0; i < image.length; i++) {
            int[] aint1 = image[i];
            if (aint1 != null) {
                aint[i] = new int[(width >> i) * (height >> i)];
                System.arraycopy(aint1, size * aint[i].length, aint[i], 0, aint[i].length);
            }
        }

        return aint;
    }

    public void clearFrames() {
        this.frames.clear();
    }

    public boolean isAnimated() {
        return this.animation != null;
    }

    public void setFrames(List<int[][]> frames) {
        this.frames = frames;
    }

    private void clear() {
        this.animation = null;
        this.setFrames(Lists.newArrayList());
        this.activeFrame = 0;
        this.frameTicks = 0;
    }

    @Override
    public String toString() {
        return "TextureAtlasSprite{name='"
            + this.name
            + '\''
            + ", frameCount="
            + this.frames.size()
            + ", rotated="
            + this.rotated
            + ", x="
            + this.x
            + ", y="
            + this.y
            + ", height="
            + this.height
            + ", width="
            + this.width
            + ", u0="
            + this.uMin
            + ", u1="
            + this.uMax
            + ", v0="
            + this.vMin
            + ", v1="
            + this.vMax
            + '}';
    }
}
