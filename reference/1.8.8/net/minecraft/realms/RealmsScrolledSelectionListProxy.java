package net.minecraft.realms;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.widget.ListWidget;

public class RealmsScrolledSelectionListProxy extends ListWidget {
    private final RealmsScrolledSelectionList delegate;

    public RealmsScrolledSelectionListProxy(RealmsScrolledSelectionList delegate, int width, int height, int minY, int maxY, int entryHeight) {
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
    }
}
