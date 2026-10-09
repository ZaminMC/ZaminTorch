package net.minecraft.client.resource.manager;

import com.google.common.collect.Lists;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Set;
import net.minecraft.client.resource.Resource;
import net.minecraft.client.resource.SimpleResource;
import net.minecraft.client.resource.metadata.ResourceMetadataSerializerRegistry;
import net.minecraft.client.resource.pack.ResourcePack;
import net.minecraft.resource.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FallbackResourceManager implements ResourceManager {
    private static final Logger LOGGER = LogManager.getLogger();
    protected final List<ResourcePack> fallbacks = Lists.newArrayList();
    private final ResourceMetadataSerializerRegistry metadataSerializers;

    public FallbackResourceManager(ResourceMetadataSerializerRegistry metadataSerializers) {
        this.metadataSerializers = metadataSerializers;
    }

    public void add(ResourcePack pack) {
        this.fallbacks.add(pack);
    }

    @Override
    public Set<String> getNamespaces() {
        return null;
    }

    @Override
    public Resource getResource(Identifier location) throws IOException {
        ResourcePack resourcepack = null;
        Identifier identifier = getMetadataLocation(location);

        for (int i = this.fallbacks.size() - 1; i >= 0; i--) {
            ResourcePack resourcepack1 = this.fallbacks.get(i);
            if (resourcepack == null && resourcepack1.hasResource(identifier)) {
                resourcepack = resourcepack1;
            }

            if (resourcepack1.hasResource(location)) {
                InputStream inputstream = null;
                if (resourcepack != null) {
                    inputstream = this.wrapResource(identifier, resourcepack);
                }

                return new SimpleResource(resourcepack1.getName(), location, this.wrapResource(location, resourcepack1), inputstream, this.metadataSerializers);
            }
        }

        throw new FileNotFoundException(location.toString());
    }

    protected InputStream wrapResource(Identifier location, ResourcePack pack) throws IOException {
        InputStream inputstream = pack.getResource(location);
        return LOGGER.isDebugEnabled() ? new FallbackResourceManager.WrappedResource(inputstream, location, pack.getName()) : inputstream;
    }

    @Override
    public List<Resource> getResources(Identifier location) throws IOException {
        List<Resource> list = Lists.newArrayList();
        Identifier identifier = getMetadataLocation(location);

        for (ResourcePack resourcepack : this.fallbacks) {
            if (resourcepack.hasResource(location)) {
                InputStream inputstream = resourcepack.hasResource(identifier) ? this.wrapResource(identifier, resourcepack) : null;
                list.add(new SimpleResource(resourcepack.getName(), location, this.wrapResource(location, resourcepack), inputstream, this.metadataSerializers));
            }
        }

        if (list.isEmpty()) {
            throw new FileNotFoundException(location.toString());
        } else {
            return list;
        }
    }

    static Identifier getMetadataLocation(Identifier location) {
        return new Identifier(location.getNamespace(), location.getPath() + ".mcmeta");
    }

    static class WrappedResource extends InputStream {
        private final InputStream resource;
        private final String resourceLeakWarning;
        private boolean closed = false;

        public WrappedResource(InputStream resource, Identifier location, String pack) {
            this.resource = resource;
            ByteArrayOutputStream bytearrayoutputstream = new ByteArrayOutputStream();
            new Exception().printStackTrace(new PrintStream(bytearrayoutputstream));
            this.resourceLeakWarning = "Leaked resource: '" + location + "' loaded from pack: '" + pack + "'\n" + bytearrayoutputstream.toString();
        }

        @Override
        public void close() throws IOException {
            this.resource.close();
            this.closed = true;
        }

        @Override
        protected void finalize() throws Throwable {
            if (!this.closed) {
                FallbackResourceManager.LOGGER.warn(this.resourceLeakWarning);
            }

            super.finalize();
        }

        @Override
        public int read() throws IOException {
            return this.resource.read();
        }
    }
}
