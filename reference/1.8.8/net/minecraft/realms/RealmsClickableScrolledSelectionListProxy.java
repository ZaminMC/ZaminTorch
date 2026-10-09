package net.minecraft.realms;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.widget.ListWidget;
import org.lwjgl.input.Mouse;

public class RealmsClickableScrolledSelectionListProxy extends ListWidget {
    private final RealmsClickableScrolledSelectionList delegate;

    public RealmsClickableScrolledSelectionListProxy(RealmsClickableScrolledSelectionList delegate, int width, int height, int minY, int maxY, int entryHeight) {
        super(Minecraft.getInstance(), width, height, minY, maxY, entryHeight);
        this.delegate = delegate;
    }

    @Override
    protected int size() {
        return this.delegate.getItemCount();
    }

    @Override
    protected void entryClicked(int index, boolean doubleClick, int mouseX, int mouseY) {
        this.delegate.selectItem(index, doubleClick, mouseX, mouseY);
    }

    @Override
    protected boolean isEntrySelected(int index) {
        return this.delegate.isSelectedItem(index);
    }

    @Override
    protected void renderBackground() {
        this.delegate.renderBackground();
    }

    @Override
    protected void renderEntry(int index, int x, int y, int rowHeight, int mouseX, int mouseY) {
        this.delegate.renderItem(index, x, y, rowHeight, mouseX, mouseY);
    }

    public int getWidth() {
        return super.width;
    }

    public int getMouseY() {
        return super.mouseY;
    }

    public int getMouseX() {
        return super.mouseX;
    }

    @Override
    protected int getHeight() {
        return this.delegate.getMaxPosition();
    }

    @Override
    protected int getScrollbarPosition() {
        return this.delegate.getScrollbarPosition();
    }

    @Override
    public void handleMouse() {
        super.handleMouse();
        if (this.scrollSpeedMultiplier > 0.0F && Mouse.getEventButtonState()) {
            this.delegate.customMouseEvent(this.minY, this.maxY, this.headerHeight, this.scrollAmount, this.entryHeight);
        }
    }

    public void renderSelected(int index, int x, int y, Tezzelator tesselator) {
        this.delegate.renderSelected(index, x, y, tesselator);
    }

    @Override
    protected void renderList(int x, int y, int mouseX, int mouseY) {
        int i = this.size();

        for (int j = 0; j < i; j++) {
            int k = y + j * this.entryHeight + this.headerHeight;
            int l = this.entryHeight - 4;
            if (k > this.maxY || k + l < this.minY) {
                this.renderEntryOutOfBounds(j, x, k);
            }

            if (this.renderSelectionHighlight && this.isEntrySelected(j)) {
                this.renderSelected(this.width, k, l, Tezzelator.instance);
            }

            this.renderEntry(j, x, k, l, mouseX, mouseY);
        }
    }
}
