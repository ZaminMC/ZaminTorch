package net.minecraft.client.resource;

import java.io.File;
import java.io.FileNotFoundException;

public class ResourceNotFoundException extends FileNotFoundException {
    public ResourceNotFoundException(File pack, String resource) {
        super(String.format("'%s' in ResourcePack '%s'", resource, pack));
    }
}
