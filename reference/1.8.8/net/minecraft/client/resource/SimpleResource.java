package net.minecraft.client.resource;

import com.google.common.collect.Maps;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;
import net.minecraft.client.resource.metadata.ResourceMetadataSection;
import net.minecraft.client.resource.metadata.ResourceMetadataSerializerRegistry;
import net.minecraft.resource.Identifier;
import org.apache.commons.io.IOUtils;

public class SimpleResource implements Resource {
    private final Map<String, ResourceMetadataSection> metadataProviders = Maps.newHashMap();
    private final String sourceName;
    private final Identifier location;
    private final InputStream resource;
    private final InputStream metadata;
    private final ResourceMetadataSerializerRegistry metadataSerializers;
    private boolean hasReadMetadata;
    private JsonObject metadataJson;

    public SimpleResource(
        String sourceName, Identifier location, InputStream resource, InputStream metadata, ResourceMetadataSerializerRegistry metadataSerializers
    ) {
        this.sourceName = sourceName;
        this.location = location;
        this.resource = resource;
        this.metadata = metadata;
        this.metadataSerializers = metadataSerializers;
    }

    @Override
    public Identifier getLocation() {
        return this.location;
    }

    @Override
    public InputStream asStream() {
        return this.resource;
    }

    @Override
    public boolean hasMetadata() {
        return this.metadata != null;
    }

    @Override
    public <T extends ResourceMetadataSection> T getMetadata(String name) {
        if (!this.hasMetadata()) {
            return null;
        }

        if (this.metadataJson == null && !this.hasReadMetadata) {
            this.hasReadMetadata = true;
            BufferedReader bufferedreader = null;

            try {
                bufferedreader = new BufferedReader(new InputStreamReader(this.metadata));
                this.metadataJson = new JsonParser().parse(bufferedreader).getAsJsonObject();
            } finally {
                IOUtils.closeQuietly(bufferedreader);
            }
        }

        T t = (T)this.metadataProviders.get(name);
        if (t == null) {
            t = this.metadataSerializers.readMetadata(name, this.metadataJson);
        }

        return t;
    }

    @Override
    public String getSourceName() {
        return this.sourceName;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        } else if (!(object instanceof SimpleResource)) {
            return false;
        } else {
            SimpleResource simpleresource = (SimpleResource)object;
            if (this.location != null ? this.location.equals(simpleresource.location) : simpleresource.location == null) {
                return this.sourceName != null ? this.sourceName.equals(simpleresource.sourceName) : simpleresource.sourceName == null;
            } else {
                return false;
            }
        }
    }

    @Override
    public int hashCode() {
        int i = this.sourceName != null ? this.sourceName.hashCode() : 0;
        return 31 * i + (this.location != null ? this.location.hashCode() : 0);
    }
}
