package net.minecraft.client.resource.metadata;

import net.minecraft.text.Text;

public class ResourcePackMetadata implements ResourceMetadataSection {
    private final Text description;
    private final int format;

    public ResourcePackMetadata(Text description, int format) {
        this.description = description;
        this.format = format;
    }

    public Text getDescription() {
        return this.description;
    }

    public int getFormat() {
        return this.format;
    }
}
