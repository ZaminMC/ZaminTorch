package net.minecraft.client.resource.metadata.serializer;

import com.google.gson.JsonDeserializer;
import net.minecraft.client.resource.metadata.ResourceMetadataSection;

public interface ResourceMetadataSerializer<T extends ResourceMetadataSection> extends JsonDeserializer<T> {
    String getName();
}
