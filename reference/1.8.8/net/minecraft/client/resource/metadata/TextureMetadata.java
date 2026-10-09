package net.minecraft.client.resource.metadata;

import java.util.Collections;
import java.util.List;

public class TextureMetadata implements ResourceMetadataSection {
    private final boolean blur;
    private final boolean clamp;
    private final List<Integer> mipmaps;

    public TextureMetadata(boolean blur, boolean clamp, List<Integer> mipmaps) {
        this.blur = blur;
        this.clamp = clamp;
        this.mipmaps = mipmaps;
    }

    public boolean hasBlur() {
        return this.blur;
    }

    public boolean isClamped() {
        return this.clamp;
    }

    public List<Integer> getMipmaps() {
        return Collections.unmodifiableList(this.mipmaps);
    }
}
