package net.minecraft.client.resource.pack;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import net.minecraft.client.resource.metadata.ResourceMetadataSection;
import net.minecraft.client.resource.metadata.ResourceMetadataSerializerRegistry;
import net.minecraft.resource.Identifier;

public interface ResourcePack {
    InputStream getResource(Identifier location) throws IOException;

    boolean hasResource(Identifier location);

    Set<String> getNamespaces();

    <T extends ResourceMetadataSection> T getMetadataSection(ResourceMetadataSerializerRegistry metadataSerializers, String name) throws IOException;

    BufferedImage getIcon() throws IOException;

    String getName();
}
