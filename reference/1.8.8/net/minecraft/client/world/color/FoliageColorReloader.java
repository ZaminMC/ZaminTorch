package net.minecraft.client.world.color;

import java.io.IOException;
import net.minecraft.client.render.texture.TextureUtil;
import net.minecraft.client.resource.manager.ResourceManager;
import net.minecraft.client.resource.manager.ResourceReloadListener;
import net.minecraft.resource.Identifier;

public class FoliageColorReloader implements ResourceReloadListener {
    private static final Identifier FOLIAGE_LOCATION = new Identifier("textures/colormap/foliage.png");

    @Override
    public void reload(ResourceManager resourceManager) {
        try {
            FoliageColors.set(TextureUtil.getPixels(resourceManager, FOLIAGE_LOCATION));
        } catch (IOException ioexception) {
        }
    }
}
