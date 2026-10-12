package net.minecraft.client.resource.manager;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.resource.Resource;
import net.minecraft.client.resource.metadata.ResourceMetadataSerializerRegistry;
import net.minecraft.client.resource.pack.ResourcePack;
import net.minecraft.resource.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SimpleReloadableResourceManager implements ReloadableResourceManager {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Joiner JOINER = Joiner.on(", ");
    private final Map<String, FallbackResourceManager> packs = Maps.newHashMap();
    private final List<ResourceReloadListener> listeners = Lists.newArrayList();
    private final Set<String> namespaces = Sets.newLinkedHashSet();
    private final ResourceMetadataSerializerRegistry metadataSerializers;

    public SimpleReloadableResourceManager(ResourceMetadataSerializerRegistry metadataSerializers) {
        this.metadataSerializers = metadataSerializers;
    }

    public void add(ResourcePack pack) {
        for (String s : pack.getNamespaces()) {
            this.namespaces.add(s);
            FallbackResourceManager fallbackresourcemanager = this.packs.get(s);
            if (fallbackresourcemanager == null) {
                fallbackresourcemanager = new FallbackResourceManager(this.metadataSerializers);
                this.packs.put(s, fallbackresourcemanager);
            }

            fallbackresourcemanager.add(pack);
        }
    }

    @Override
    public Set<String> getNamespaces() {
        return this.namespaces;
    }

    @Override
    public Resource getResource(Identifier location) throws IOException {
        ResourceManager resourcemanager = this.packs.get(location.getNamespace());
        if (resourcemanager != null) {
            return resourcemanager.getResource(location);
        } else {
            throw new FileNotFoundException(location.toString());
        }
    }

    @Override
    public List<Resource> getResources(Identifier location) throws IOException {
        ResourceManager resourcemanager = this.packs.get(location.getNamespace());
        if (resourcemanager != null) {
            return resourcemanager.getResources(location);
        } else {
            throw new FileNotFoundException(location.toString());
        }
    }

    private void clear() {
        this.packs.clear();
        this.namespaces.clear();
    }

    @Override
    public void reload(List<ResourcePack> packs) {
        this.clear();
        LOGGER.info("Reloading ResourceManager: " + JOINER.join(Iterables.transform(packs, new Function<ResourcePack, String>() {
            public String apply(ResourcePack resourcePack) {
                return resourcePack.getName();
            }
        })));

        for (ResourcePack resourcepack : packs) {
            this.add(resourcepack);
        }

        this.reloadListeners();
    }

    @Override
    public void addListener(ResourceReloadListener listener) {
        this.listeners.add(listener);
        listener.reload(this);
    }

    private void reloadListeners() {
        for (ResourceReloadListener resourcereloadlistener : this.listeners) {
            resourcereloadlistener.reload(this);
        }
    }
}
