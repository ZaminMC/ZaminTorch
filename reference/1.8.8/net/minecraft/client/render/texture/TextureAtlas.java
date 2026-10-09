package net.minecraft.client.render.texture;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resource.Resource;
import net.minecraft.client.resource.manager.ResourceManager;
import net.minecraft.client.resource.metadata.AnimationMetadata;
import net.minecraft.client.resource.metadata.TextureMetadata;
import net.minecraft.client.texture.SpriteSource;
import net.minecraft.resource.Identifier;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportCategory;
import net.minecraft.util.math.MathHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TextureAtlas extends AbstractTexture implements TickableTexture {
    private static final Logger LOGGER = LogManager.getLogger();
    public static final Identifier MISSING_TEXTURE_ID = new Identifier("missingno");
    public static final Identifier BLOCKS_LOCATION = new Identifier("textures/atlas/blocks.png");
    /**
     * All animated sprites that were successfully stitched into this texture atlas.
     * Sprites from {@link sourcedSprites} that could not be loaded were not stitched.
     */
    private final List<TextureAtlasSprite> animatedSprites = Lists.newArrayList();
    /**
     * All sprites added to this texture atlas by the sprite source.
     */
    private final Map<String, TextureAtlasSprite> sourcedSprites = Maps.newHashMap();
    /**
     * All sprites that were successfully stitched into this texture atlas.
     * Sprites from {@link sourcedSprites} that could not be loaded were not stitched.
     */
    private final Map<String, TextureAtlasSprite> stitchedSprites = Maps.newHashMap();
    private final String path;
    private final SpriteSource spriteSource;
    private int maxMipLevel;
    private final TextureAtlasSprite missingSprite = new TextureAtlasSprite("missingno");

    public TextureAtlas(String path) {
        this(path, null);
    }

    public TextureAtlas(String path, SpriteSource spriteSource) {
        this.path = path;
        this.spriteSource = spriteSource;
    }

    private void loadMissingTexture() {
        int[] aint = TextureUtil.MISSING_DATA;
        this.missingSprite.setWidth(16);
        this.missingSprite.setHeight(16);
        int[][] aint1 = new int[this.maxMipLevel + 1][];
        aint1[0] = aint;
        this.missingSprite.setFrames(Lists.newArrayList(aint1));
    }

    @Override
    public void load(ResourceManager resourceManager) throws IOException {
        if (this.spriteSource != null) {
            this.load(resourceManager, this.spriteSource);
        }
    }

    public void load(ResourceManager resourceManager, SpriteSource spriteSource) {
        this.sourcedSprites.clear();
        spriteSource.registerSprites(this);
        this.loadMissingTexture();
        this.clearGlId();
        this.loadAndStitch(resourceManager);
    }

    public void loadAndStitch(ResourceManager resourceManager) {
        int i = Minecraft.getMaxTextureSize();
        Stitcher stitcher = new Stitcher(i, i, true, 0, this.maxMipLevel);
        this.stitchedSprites.clear();
        this.animatedSprites.clear();
        int j = Integer.MAX_VALUE;
        int k = 1 << this.maxMipLevel;

        for (Entry<String, TextureAtlasSprite> entry : this.sourcedSprites.entrySet()) {
            TextureAtlasSprite textureatlassprite = entry.getValue();
            Identifier identifier = new Identifier(textureatlassprite.getName());
            Identifier identifier1 = this.getResourceId(identifier, 0);

            try {
                Resource resource = resourceManager.getResource(identifier1);
                BufferedImage[] abufferedimage = new BufferedImage[1 + this.maxMipLevel];
                abufferedimage[0] = TextureUtil.readImage(resource.asStream());
                TextureMetadata texturemetadata = resource.getMetadata("texture");
                if (texturemetadata != null) {
                    List<Integer> list = texturemetadata.getMipmaps();
                    if (!list.isEmpty()) {
                        int l = abufferedimage[0].getWidth();
                        int i1 = abufferedimage[0].getHeight();
                        if (MathHelper.smallestEncompassingPowerOfTwo(l) != l || MathHelper.smallestEncompassingPowerOfTwo(i1) != i1) {
                            throw new RuntimeException("Unable to load extra miplevels, source-texture is not power of two");
                        }
                    }

                    for (int i2 : list) {
                        if (i2 > 0 && i2 < abufferedimage.length - 1 && abufferedimage[i2] == null) {
                            Identifier identifier2 = this.getResourceId(identifier, i2);

                            try {
                                abufferedimage[i2] = TextureUtil.readImage(resourceManager.getResource(identifier2).asStream());
                            } catch (IOException ioexception) {
                                LOGGER.error("Unable to load miplevel {} from: {}", i2, identifier2, ioexception);
                            }
                        }
                    }
                }

                AnimationMetadata animationmetadata = resource.getMetadata("animation");
                textureatlassprite.load(abufferedimage, animationmetadata);
            } catch (RuntimeException runtimeexception) {
                LOGGER.error("Unable to parse metadata from " + identifier1, runtimeexception);
                continue;
            } catch (IOException ioexception1) {
                LOGGER.error("Using missing texture, unable to load " + identifier1, ioexception1);
                continue;
            }

            j = Math.min(j, Math.min(textureatlassprite.getWidth(), textureatlassprite.getHeight()));
            int l1 = Math.min(Integer.lowestOneBit(textureatlassprite.getWidth()), Integer.lowestOneBit(textureatlassprite.getHeight()));
            if (l1 < k) {
                LOGGER.warn(
                    "Texture {} with size {}x{} limits mip level from {} to {}",
                    identifier1,
                    textureatlassprite.getWidth(),
                    textureatlassprite.getHeight(),
                    MathHelper.log2(k),
                    MathHelper.log2(l1)
                );
                k = l1;
            }

            stitcher.registerSprite(textureatlassprite);
        }

        int j1 = Math.min(j, k);
        int k1 = MathHelper.log2(j1);
        if (k1 < this.maxMipLevel) {
            LOGGER.warn("{}: dropping miplevel from {} to {}, because of minimum power of two: {}", this.path, this.maxMipLevel, k1, j1);
            this.maxMipLevel = k1;
        }

        for (final TextureAtlasSprite textureatlassprite1 : this.sourcedSprites.values()) {
            try {
                textureatlassprite1.applyMipmaps(this.maxMipLevel);
            } catch (Throwable throwable1) {
                CrashReport crashreport = CrashReport.of(throwable1, "Applying mipmap");
                CrashReportCategory crashreportcategory = crashreport.addCategory("Sprite being mipmapped");
                crashreportcategory.add("Sprite name", new Callable<String>() {
                    public String call() throws Exception {
                        return textureatlassprite1.getName();
                    }
                });
                crashreportcategory.add("Sprite size", new Callable<String>() {
                    public String call() throws Exception {
                        return textureatlassprite1.getWidth() + " x " + textureatlassprite1.getHeight();
                    }
                });
                crashreportcategory.add("Sprite frames", new Callable<String>() {
                    public String call() throws Exception {
                        return textureatlassprite1.getFrameCount() + " frames";
                    }
                });
                crashreportcategory.add("Mipmap levels", this.maxMipLevel);
                throw new CrashException(crashreport);
            }
        }

        this.missingSprite.applyMipmaps(this.maxMipLevel);
        stitcher.registerSprite(this.missingSprite);

        try {
            stitcher.stitch();
        } catch (StitcherException stitcherexception) {
            throw stitcherexception;
        }

        LOGGER.info("Created: {}x{} {}-atlas", stitcher.getWidth(), stitcher.getHeight(), this.path);
        TextureUtil.prepare(this.getGlId(), this.maxMipLevel, stitcher.getWidth(), stitcher.getHeight());
        Map<String, TextureAtlasSprite> map = Maps.newHashMap(this.sourcedSprites);

        for (TextureAtlasSprite textureatlassprite2 : stitcher.collectSprites()) {
            String s = textureatlassprite2.getName();
            map.remove(s);
            this.stitchedSprites.put(s, textureatlassprite2);

            try {
                TextureUtil.upload(
                    textureatlassprite2.getFrame(0),
                    textureatlassprite2.getWidth(),
                    textureatlassprite2.getHeight(),
                    textureatlassprite2.getX(),
                    textureatlassprite2.getY(),
                    false,
                    false
                );
            } catch (Throwable throwable) {
                CrashReport crashreport1 = CrashReport.of(throwable, "Stitching texture atlas");
                CrashReportCategory crashreportcategory1 = crashreport1.addCategory("Texture being stitched together");
                crashreportcategory1.add("Atlas path", this.path);
                crashreportcategory1.add("Sprite", textureatlassprite2);
                throw new CrashException(crashreport1);
            }

            if (textureatlassprite2.isAnimated()) {
                this.animatedSprites.add(textureatlassprite2);
            }
        }

        for (TextureAtlasSprite textureatlassprite3 : map.values()) {
            textureatlassprite3.set(this.missingSprite);
        }
    }

    private Identifier getResourceId(Identifier textureId, int mipmaps) {
        return mipmaps == 0
            ? new Identifier(textureId.getNamespace(), String.format("%s/%s%s", this.path, textureId.getPath(), ".png"))
            : new Identifier(textureId.getNamespace(), String.format("%s/mipmaps/%s.%d%s", this.path, textureId.getPath(), mipmaps, ".png"));
    }

    public TextureAtlasSprite getSprite(String name) {
        TextureAtlasSprite textureatlassprite = this.stitchedSprites.get(name);
        if (textureatlassprite == null) {
            textureatlassprite = this.missingSprite;
        }

        return textureatlassprite;
    }

    public void bindAndTick() {
        TextureUtil.bind(this.getGlId());

        for (TextureAtlasSprite textureatlassprite : this.animatedSprites) {
            textureatlassprite.tick();
        }
    }

    public TextureAtlasSprite registerSprite(Identifier location) {
        if (location == null) {
            throw new IllegalArgumentException("Location cannot be null!");
        }

        TextureAtlasSprite textureatlassprite = this.sourcedSprites.get(location);
        if (textureatlassprite == null) {
            textureatlassprite = TextureAtlasSprite.builtIn(location);
            this.sourcedSprites.put(location.toString(), textureatlassprite);
        }

        return textureatlassprite;
    }

    @Override
    public void tick() {
        this.bindAndTick();
    }

    public void setMaxMipLevel(int mipLevel) {
        this.maxMipLevel = mipLevel;
    }

    public TextureAtlasSprite getMissingSprite() {
        return this.missingSprite;
    }
}
