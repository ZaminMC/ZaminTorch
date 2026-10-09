package net.minecraft.client.render.texture;

import java.io.IOException;
import net.minecraft.client.resource.manager.ResourceManager;

public interface Texture {
    void pushFilter(boolean blur, boolean mipmap);

    void popFilter();

    void load(ResourceManager resourceManager) throws IOException;

    int getGlId();
}
