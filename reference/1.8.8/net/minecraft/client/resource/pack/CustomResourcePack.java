package net.minecraft.client.resource.pack;

import com.google.common.base.Charsets;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import net.minecraft.client.render.texture.TextureUtil;
import net.minecraft.client.resource.metadata.ResourceMetadataSection;
import net.minecraft.client.resource.metadata.ResourceMetadataSerializerRegistry;
import net.minecraft.resource.Identifier;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class CustomResourcePack implements ResourcePack {
    private static final Logger LOGGER = LogManager.getLogger();
    protected final File file;

    public CustomResourcePack(File file) {
        this.file = file;
    }

    private static String getPathToResource(Identifier location) {
        return String.format("%s/%s/%s", "assets", location.getNamespace(), location.getPath());
    }

    protected static String relativize(File dir, File file) {
        return dir.toURI().relativize(file.toURI()).getPath();
    }

    @Override
    public InputStream getResource(Identifier location) throws IOException {
        return this.openResource(getPathToResource(location));
    }

    @Override
    public boolean hasResource(Identifier location) {
        return this.hasResource(getPathToResource(location));
    }

    protected abstract InputStream openResource(String path) throws IOException;

    protected abstract boolean hasResource(String path);

    protected void warnNonLowercaseNamespace(String namespace) {
        LOGGER.warn("ResourcePack: ignored non-lowercase namespace: %s in %s", namespace, this.file);
    }

    @Override
    public <T extends ResourceMetadataSection> T getMetadataSection(ResourceMetadataSerializerRegistry metadataSerializers, String name) throws IOException {
        return getMetadataSection(metadataSerializers, this.openResource("pack.mcmeta"), name);
    }

    static <T extends ResourceMetadataSection> T getMetadataSection(ResourceMetadataSerializerRegistry metadataSerializers, InputStream file, String name) {
        JsonObject jsonobject = null;
        BufferedReader bufferedreader = null;

        try {
            bufferedreader = new BufferedReader(new InputStreamReader(file, Charsets.UTF_8));
            jsonobject = new JsonParser().parse(bufferedreader).getAsJsonObject();
        } catch (RuntimeException runtimeexception) {
            throw new JsonParseException(runtimeexception);
        } finally {
            IOUtils.closeQuietly(bufferedreader);
        }

        return metadataSerializers.readMetadata(name, jsonobject);
    }

    @Override
    public BufferedImage getIcon() throws IOException {
        return TextureUtil.readImage(this.openResource("pack.png"));
    }

    @Override
    public String getName() {
        return this.file.getName();
    }
}
