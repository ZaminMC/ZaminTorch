package net.minecraft.client.render.texture;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import net.minecraft.block.material.MapColor;
import net.minecraft.client.resource.manager.ResourceManager;
import net.minecraft.item.DyeColor;
import net.minecraft.resource.Identifier;
import net.minecraft.util.math.MathHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LayeredColorMaskTexture extends AbstractTexture {
    private static final Logger LOGGER = LogManager.getLogger();
    private final Identifier baseLayerLocation;
    private final List<String> layerMaskPaths;
    private final List<DyeColor> layerColors;

    public LayeredColorMaskTexture(Identifier baseLayerLocation, List<String> layerMaskPaths, List<DyeColor> layerColors) {
        this.baseLayerLocation = baseLayerLocation;
        this.layerMaskPaths = layerMaskPaths;
        this.layerColors = layerColors;
    }

    @Override
    public void load(ResourceManager resourceManager) throws IOException {
        this.clearGlId();

        BufferedImage bufferedimage;
        try {
            BufferedImage bufferedimage1 = TextureUtil.readImage(resourceManager.getResource(this.baseLayerLocation).asStream());
            int i = bufferedimage1.getType();
            if (i == 0) {
                i = 6;
            }

            bufferedimage = new BufferedImage(bufferedimage1.getWidth(), bufferedimage1.getHeight(), i);
            Graphics graphics = bufferedimage.getGraphics();
            graphics.drawImage(bufferedimage1, 0, 0, null);

            for (int j = 0; j < 17 && j < this.layerMaskPaths.size() && j < this.layerColors.size(); j++) {
                String s = this.layerMaskPaths.get(j);
                MapColor mapcolor = this.layerColors.get(j).getMapColor();
                if (s != null) {
                    InputStream inputstream = resourceManager.getResource(new Identifier(s)).asStream();
                    BufferedImage bufferedimage2 = TextureUtil.readImage(inputstream);
                    if (bufferedimage2.getWidth() == bufferedimage.getWidth()
                        && bufferedimage2.getHeight() == bufferedimage.getHeight()
                        && bufferedimage2.getType() == 6) {
                        for (int k = 0; k < bufferedimage2.getHeight(); k++) {
                            for (int l = 0; l < bufferedimage2.getWidth(); l++) {
                                int i1 = bufferedimage2.getRGB(l, k);
                                if ((i1 & 0xFF000000) != 0) {
                                    int j1 = (i1 & 0xFF0000) << 8 & 0xFF000000;
                                    int k1 = bufferedimage1.getRGB(l, k);
                                    int l1 = MathHelper.mulARGB(k1, mapcolor.color) & 16777215;
                                    bufferedimage2.setRGB(l, k, j1 | l1);
                                }
                            }
                        }

                        bufferedimage.getGraphics().drawImage(bufferedimage2, 0, 0, null);
                    }
                }
            }
        } catch (IOException ioexception) {
            LOGGER.error("Couldn't load layered image", ioexception);
            return;
        }

        TextureUtil.uploadTexture(this.getGlId(), bufferedimage);
    }
}
