package net.minecraft.client.resource.manager;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import net.minecraft.client.resource.Resource;
import net.minecraft.resource.Identifier;

public interface ResourceManager {
    Set<String> getNamespaces();

    Resource getResource(Identifier location) throws IOException;

    List<Resource> getResources(Identifier location) throws IOException;
}
