package net.minecraft.client.resource.pack;

import com.google.common.collect.ImmutableSet;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.render.texture.TextureUtil;
import net.minecraft.client.resource.metadata.ResourceMetadataSection;
import net.minecraft.client.resource.metadata.ResourceMetadataSerializerRegistry;
import net.minecraft.resource.Identifier;

public class BuiltInResourcePack implements ResourcePack {
    public static final Set<String> NAMESPACES = ImmutableSet.of("minecraft", "realms");
    private final Map<String, File> assets;

    public BuiltInResourcePack(Map<String, File> assets) {
        this.assets = assets;
    }

    @Override
    public InputStream getResource(Identifier location) throws IOException {
        InputStream inputstream = this.openResource(location);
        if (inputstream != null) {
            return inputstream;
        } else {
            InputStream inputstream1 = this.getAsset(location);
            if (inputstream1 != null) {
                return inputstream1;
            } else {
                throw new FileNotFoundException(location.getPath());
            }
        }
    }

    public InputStream getAsset(Identifier location) throws FileNotFoundException {
        File file1 = this.assets.get(location.toString());
        return file1 != null && file1.isFile() ? new FileInputStream(file1) : null;
    }

    private InputStream openResource(Identifier location) {
        return BuiltInResourcePack.class.getResourceAsStream("/assets/" + location.getNamespace() + "/" + location.getPath());
    }

    @Override
    public boolean hasResource(Identifier location) {
        return this.openResource(location) != null || this.assets.containsKey(location.toString());
    }

    @Override
    public Set<String> getNamespaces() {
        return NAMESPACES;
    }

    @Override
    public <T extends ResourceMetadataSection> T getMetadataSection(ResourceMetadataSerializerRegistry metadataSerializers, String name) throws IOException {
        try {
            InputStream inputstream = new FileInputStream(this.assets.get("pack.mcmeta"));
            return CustomResourcePack.getMetadataSection(metadataSerializers, inputstream, name);
        } catch (RuntimeException runtimeexception) {
            return null;
        } catch (FileNotFoundException filenotfoundexception) {
            return null;
        }
    }

    @Override
    public BufferedImage getIcon() throws IOException {
        return TextureUtil.readImage(BuiltInResourcePack.class.getResourceAsStream("/" + new Identifier("pack.png").getPath()));
    }

    @Override
    public String getName() {
        return "Default";
    }
}
