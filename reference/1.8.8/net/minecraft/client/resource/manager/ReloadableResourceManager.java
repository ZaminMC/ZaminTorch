package net.minecraft.client.resource.manager;

import java.util.List;
import net.minecraft.client.resource.pack.ResourcePack;

public interface ReloadableResourceManager extends ResourceManager {
    void reload(List<ResourcePack> packs);

    void addListener(ResourceReloadListener listener);
}
