package net.minecraft.client.world.color;

import java.io.IOException;
import net.minecraft.client.render.texture.TextureUtil;
import net.minecraft.client.resource.manager.ResourceManager;
import net.minecraft.client.resource.manager.ResourceReloadListener;
import net.minecraft.resource.Identifier;

public class GrassColorReloader implements ResourceReloadListener {
    private static final Identifier GRASS_LOCATION = new Identifier("textures/colormap/grass.png");

    @Override
    public void reload(ResourceManager resourceManager) {
        try {
            GrassColors.set(TextureUtil.getPixels(resourceManager, GRASS_LOCATION));
        } catch (IOException ioexception) {
        }
    }
}
