package net.minecraft.realms;

import com.google.common.collect.Lists;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;

public class RealmsScreenProxy extends Screen {
    private RealmsScreen delegate;

    public RealmsScreenProxy(RealmsScreen delegate) {
        this.delegate = delegate;
        super.buttons = Collections.synchronizedList(Lists.newArrayList());
    }

    public RealmsScreen getDelegate() {
        return this.delegate;
    }

    @Override
    public void init() {
        this.delegate.init();
        super.init();
    }

    public void drawCenteredString(String s, int x, int y, int color) {
        super.drawCenteredString(this.textRenderer, s, x, y, color);
    }

    public void drawString(String string, int i, int j, int k) {
        super.drawString(this.textRenderer, string, i, j, k);
    }

    @Override
    public void drawTexture(int x, int y, int u, int v, int width, int height) {
        this.delegate.blit(x, y, u, v, width, height);
        super.drawTexture(x, y, u, v, width, height);
    }

    @Override
    public void fillGradient(int x1, int y1, int x2, int y2, int color1, int color2) {
        super.fillGradient(x1, y1, x2, y2, color1, color2);
    }

    @Override
    public void renderBackground() {
        super.renderBackground();
    }

    @Override
    public boolean shouldPauseGame() {
        return super.shouldPauseGame();
    }

    @Override
    public void renderBackground(int offset) {
        super.renderBackground(offset);
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        this.delegate.render(mouseX, mouseY, tickDelta);
    }

    @Override
    public void renderTooltip(ItemStack item, int mouseX, int mouseY) {
        super.renderTooltip(item, mouseX, mouseY);
    }

    @Override
    public void renderTooltip(String text, int mouseX, int mouseY) {
        super.renderTooltip(text, mouseX, mouseY);
    }

    @Override
    public void renderTooltip(List<String> text, int mouseX, int mouseY) {
        super.renderTooltip(text, mouseX, mouseY);
    }

    @Override
    public void tick() {
        this.delegate.tick();
        super.tick();
    }

    public int getFontHeight() {
        return this.textRenderer.fontHeight;
    }

    public int getStringWidth(String s) {
        return this.textRenderer.getWidth(s);
    }

    public void drawWithShadow(String s, int x, int y, int color) {
        this.textRenderer.drawWithShadow(s, x, y, color);
    }

    public List<String> wrapLines(String s, int width) {
        return this.textRenderer.split(s, width);
    }

    @Override
    public final void buttonClicked(ButtonWidget button) {
        this.delegate.buttonClicked(((RealmsButtonProxy)button).get());
    }

    public void clear() {
        super.buttons.clear();
    }

    public void addButton(RealmsButton button) {
        super.buttons.add(button.getProxy());
    }

    public List<RealmsButton> getButtons() {
        List<RealmsButton> list = Lists.newArrayListWithExpectedSize(super.buttons.size());

        for (ButtonWidget buttonwidget : super.buttons) {
            list.add(((RealmsButtonProxy)buttonwidget).get());
        }

        return list;
    }

    public void removeButton(RealmsButton button) {
        super.buttons.remove(button);
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) {
        this.delegate.mouseClicked(mouseX, mouseY, button);
        super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void handleMouse() {
        this.delegate.mouseEvent();
        super.handleMouse();
    }

    @Override
    public void handleKeyboard() {
        this.delegate.keyboardEvent();
        super.handleKeyboard();
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int button) {
        this.delegate.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void mouseDragged(int mouseX, int mouseY, int button, long duration) {
        this.delegate.mouseDragged(mouseX, mouseY, button, duration);
    }

    @Override
    public void keyPressed(char chr, int key) {
        this.delegate.keyPressed(chr, key);
    }

    @Override
    public void confirmResult(boolean result, int id) {
        this.delegate.confirmResult(result, id);
    }

    @Override
    public void removed() {
        this.delegate.removed();
        super.removed();
    }
}
