package net.minecraft.client.gui.screen.resourcepack;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.widget.EntryListWidget;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.text.Formatting;

public abstract class ResourcePackListWidget extends EntryListWidget {
    protected final Minecraft minecraft;
    protected final List<ResourcePackEntry> packs;

    public ResourcePackListWidget(Minecraft minecraft, int x, int y, List<ResourcePackEntry> packs) {
        super(minecraft, x, y, 32, y - 55 + 4, 36);
        this.minecraft = minecraft;
        this.packs = packs;
        this.centerAlongY = false;
        this.setHeader(true, (int)(minecraft.textRenderer.fontHeight * 1.5F));
    }

    @Override
    protected void renderHeader(int x, int y, Tesselator tesselator) {
        String s = Formatting.UNDERLINE + "" + Formatting.BOLD + this.getTitle();
        this.minecraft.textRenderer.draw(s, x + this.width / 2 - this.minecraft.textRenderer.getWidth(s) / 2, Math.min(this.minY + 3, y), 16777215);
    }

    protected abstract String getTitle();

    public List<ResourcePackEntry> getPacks() {
        return this.packs;
    }

    @Override
    protected int size() {
        return this.getPacks().size();
    }

    public ResourcePackEntry getEntry(int i) {
        return this.getPacks().get(i);
    }

    @Override
    public int getRowWidth() {
        return this.width;
    }

    @Override
    protected int getScrollbarPosition() {
        return this.maxX - 6;
    }
}
