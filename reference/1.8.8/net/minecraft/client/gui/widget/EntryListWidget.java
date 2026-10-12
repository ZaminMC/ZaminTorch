package net.minecraft.client.gui.widget;

import net.minecraft.client.Minecraft;

public abstract class EntryListWidget extends ListWidget {
    public EntryListWidget(Minecraft minecraft, int width, int height, int minY, int maxY, int itemHeight) {
        super(minecraft, width, height, minY, maxY, itemHeight);
    }

    @Override
    protected void entryClicked(int index, boolean doubleClick, int mouseX, int mouseY) {
    }

    @Override
    protected boolean isEntrySelected(int index) {
        return false;
    }

    @Override
    protected void renderBackground() {
    }

    @Override
    protected void renderEntry(int index, int x, int y, int rowHeight, int mouseX, int mouseY) {
        this.getEntry(index).render(index, x, y, this.getRowWidth(), rowHeight, mouseX, mouseY, this.getEntryAt(mouseX, mouseY) == index);
    }

    @Override
    protected void renderEntryOutOfBounds(int index, int x, int y) {
        this.getEntry(index).renderOutOfBounds(index, x, y);
    }

    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (this.isMouseInList(mouseY)) {
            int i = this.getEntryAt(mouseX, mouseY);
            if (i >= 0) {
                int j = this.minX + this.width / 2 - this.getRowWidth() / 2 + 2;
                int k = this.minY + 4 - this.getScrollAmount() + i * this.entryHeight + this.headerHeight;
                int l = mouseX - j;
                int i1 = mouseY - k;
                if (this.getEntry(i).mouseClicked(i, mouseX, mouseY, button, l, i1)) {
                    this.setScrolling(false);
                    return true;
                }
            }
        }

        return false;
    }

    public boolean mouseReleased(int mouseX, int mouseY, int button) {
        for (int i = 0; i < this.size(); i++) {
            int j = this.minX + this.width / 2 - this.getRowWidth() / 2 + 2;
            int k = this.minY + 4 - this.getScrollAmount() + i * this.entryHeight + this.headerHeight;
            int l = mouseX - j;
            int i1 = mouseY - k;
            this.getEntry(i).mouseReleased(i, mouseX, mouseY, button, l, i1);
        }

        this.setScrolling(true);
        return false;
    }

    public abstract EntryListWidget.Entry getEntry(int index);

    public interface Entry {
        void renderOutOfBounds(int index, int x, int y);

        void render(int index, int x, int y, int width, int height, int mouseX, int mouseY, boolean hovered);

        boolean mouseClicked(int index, int mouseX, int mouseY, int button, int entryMouseX, int entryMouseY);

        void mouseReleased(int index, int mouseX, int mouseY, int button, int entryMouseX, int entryMouseY);
    }
}
