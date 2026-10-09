package net.minecraft.client.resource.metadata;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.client.resource.metadata.serializer.ResourceMetadataSerializer;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.LowercaseEnumTypeAdapterFactory;
import net.minecraft.util.registry.MappedRegistry;
import net.minecraft.util.registry.Registry;

public class ResourceMetadataSerializerRegistry {
    private final Registry<String, ResourceMetadataSerializerRegistry.Entry<? extends ResourceMetadataSection>> registry = new MappedRegistry<>();
    private final GsonBuilder gsonBuilder = new GsonBuilder();
    private Gson gson;

    public ResourceMetadataSerializerRegistry() {
        this.gsonBuilder.registerTypeHierarchyAdapter(Text.class, new Text.Serializer());
        this.gsonBuilder.registerTypeHierarchyAdapter(Style.class, new Style.Serializer());
        this.gsonBuilder.registerTypeAdapterFactory(new LowercaseEnumTypeAdapterFactory());
    }

    public <T extends ResourceMetadataSection> void register(ResourceMetadataSerializer<T> serializer, Class<T> metadataType) {
        this.registry.put(serializer.getName(), new ResourceMetadataSerializerRegistry.Entry<>(serializer, metadataType));
        this.gsonBuilder.registerTypeAdapter(metadataType, serializer);
        this.gson = null;
    }

    public <T extends ResourceMetadataSection> T readMetadata(String name, JsonObject json) {
        if (name == null) {
            throw new IllegalArgumentException("Metadata section name cannot be null");
        } else if (!json.has(name)) {
            return null;
        } else if (!json.get(name).isJsonObject()) {
            throw new IllegalArgumentException("Invalid metadata for '" + name + "' - expected object, found " + json.get(name));
        } else {
            ResourceMetadataSerializerRegistry.Entry<?> entry = this.registry.get(name);
            if (entry == null) {
                throw new IllegalArgumentException("Don't know how to handle metadata section '" + name + "'");
            } else {
                return this.getGson().fromJson(json.getAsJsonObject(name), entry.metadataType);
            }
        }
    }

    private Gson getGson() {
        if (this.gson == null) {
            this.gson = this.gsonBuilder.create();
        }

        return this.gson;
    }

    class Entry<T extends ResourceMetadataSection> {
        final ResourceMetadataSerializer<T> metadataSerializer;
        final Class<T> metadataType;

        private Entry(ResourceMetadataSerializer<T> metadataSerializer, Class<T> metadataType) {
            this.metadataSerializer = metadataSerializer;
            this.metadataType = metadataType;
        }
    }
}
