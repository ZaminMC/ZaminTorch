package net.minecraft.client.gui.screen.resourcepack;

import net.minecraft.client.gui.screen.ResourcePacksScreen;
import net.minecraft.client.resource.pack.ResourcePacks;

public class CustomResourcePackEntry extends ResourcePackEntry {
    private final ResourcePacks.Entry pack;

    public CustomResourcePackEntry(ResourcePacksScreen parent, ResourcePacks.Entry pack) {
        super(parent);
        this.pack = pack;
    }

    @Override
    protected void bindIcon() {
        this.pack.bindIconTexture(this.minecraft.getTextureManager());
    }

    @Override
    protected int getFormat() {
        return this.pack.getFormat();
    }

    @Override
    protected String getDescription() {
        return this.pack.getDescription();
    }

    @Override
    protected String getName() {
        return this.pack.getName();
    }

    public ResourcePacks.Entry getPack() {
        return this.pack;
    }
}
