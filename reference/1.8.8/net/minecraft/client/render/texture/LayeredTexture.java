package net.minecraft.client.render.texture;

import com.google.common.collect.Lists;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import net.minecraft.client.resource.manager.ResourceManager;
import net.minecraft.resource.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LayeredTexture extends AbstractTexture {
    private static final Logger LOGGER = LogManager.getLogger();
    public final List<String> paths;

    public LayeredTexture(String... paths) {
        this.paths = Lists.newArrayList(paths);
    }

    @Override
    public void load(ResourceManager resourceManager) throws IOException {
        this.clearGlId();
        BufferedImage bufferedimage = null;

        try {
            for (String s : this.paths) {
                if (s != null) {
                    InputStream inputstream = resourceManager.getResource(new Identifier(s)).asStream();
                    BufferedImage bufferedimage1 = TextureUtil.readImage(inputstream);
                    if (bufferedimage == null) {
                        bufferedimage = new BufferedImage(bufferedimage1.getWidth(), bufferedimage1.getHeight(), 2);
                    }

                    bufferedimage.getGraphics().drawImage(bufferedimage1, 0, 0, null);
                }
            }
        } catch (IOException ioexception) {
            LOGGER.error("Couldn't load layered image", ioexception);
            return;
        }

        TextureUtil.uploadTexture(this.getGlId(), bufferedimage);
    }
}
