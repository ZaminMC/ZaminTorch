package net.minecraft.client.render.texture;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import net.minecraft.client.resource.Resource;
import net.minecraft.client.resource.manager.ResourceManager;
import net.minecraft.client.resource.metadata.TextureMetadata;
import net.minecraft.resource.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SimpleTexture extends AbstractTexture {
    private static final Logger LOGGER = LogManager.getLogger();
    protected final Identifier location;

    public SimpleTexture(Identifier location) {
        this.location = location;
    }

    @Override
    public void load(ResourceManager resourceManager) throws IOException {
        this.clearGlId();
        InputStream inputstream = null;

        try {
            Resource resource = resourceManager.getResource(this.location);
            inputstream = resource.asStream();
            BufferedImage bufferedimage = TextureUtil.readImage(inputstream);
            boolean flag = false;
            boolean flag1 = false;
            if (resource.hasMetadata()) {
                try {
                    TextureMetadata texturemetadata = resource.getMetadata("texture");
                    if (texturemetadata != null) {
                        flag = texturemetadata.hasBlur();
                        flag1 = texturemetadata.isClamped();
                    }
                } catch (RuntimeException runtimeexception) {
                    LOGGER.warn("Failed reading metadata of: " + this.location, runtimeexception);
                }
            }

            TextureUtil.upload(this.getGlId(), bufferedimage, flag, flag1);
        } finally {
            if (inputstream != null) {
                inputstream.close();
            }
        }
    }
}
