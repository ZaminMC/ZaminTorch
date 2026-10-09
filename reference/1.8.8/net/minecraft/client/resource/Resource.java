package net.minecraft.client.resource;

import java.io.InputStream;
import net.minecraft.client.resource.metadata.ResourceMetadataSection;
import net.minecraft.resource.Identifier;

public interface Resource {
    Identifier getLocation();

    InputStream asStream();

    boolean hasMetadata();

    <T extends ResourceMetadataSection> T getMetadata(String name);

    String getSourceName();
}
