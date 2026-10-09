package net.minecraft.realms;

public class RealmsClickableScrolledSelectionList {
    private final RealmsClickableScrolledSelectionListProxy proxy;

    public RealmsClickableScrolledSelectionList(int width, int height, int minY, int maxY, int entryHeight) {
        this.proxy = new RealmsClickableScrolledSelectionListProxy(this, width, height, minY, maxY, entryHeight);
    }

    public void render(int mouseX, int mouseY, float tickDelta) {
        this.proxy.render(mouseX, mouseY, tickDelta);
    }

    public int width() {
        return this.proxy.getWidth();
    }

    public int getMouseY() {
        return this.proxy.getMouseY();
    }

    public int getMouseX() {
        return this.proxy.getMouseX();
    }

    protected void renderItem(int index, int x, int y, int height, Tezzelator tezzelator, int mouseX, int mouseY) {
    }

    public void renderItem(int index, int x, int y, int height, int mosueX, int museY) {
        this.renderItem(index, x, y, height, Tezzelator.instance, mosueX, museY);
    }

    public int getItemCount() {
        return 0;
    }

    public void selectItem(int index, boolean doubleClick, int mouseX, int mouseY) {
    }

    public boolean isSelectedItem(int index) {
        return false;
    }

    public void renderBackground() {
    }

    public int getMaxPosition() {
        return 0;
    }

    public int getScrollbarPosition() {
        return this.proxy.getWidth() / 2 + 124;
    }

    public void mouseEvent() {
        this.proxy.handleMouse();
    }

    public void customMouseEvent(int minY, int maxY, int headerHeight, float scrollAmount, int entryHeight) {
    }

    public void scroll(int amount) {
        this.proxy.scroll(amount);
    }

    public int getScroll() {
        return this.proxy.getScrollAmount();
    }

    protected void renderList(int i, int j, int k, int l) {
    }

    public void itemClicked(int i, int j, int k, int l, int m) {
    }

    public void renderSelected(int index, int x, int y, Tezzelator tezzelator) {
    }

    public void setLeftPos(int minX) {
        this.proxy.setX(minX);
    }
}
