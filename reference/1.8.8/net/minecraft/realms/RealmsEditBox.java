package net.minecraft.realms;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.widget.TextFieldWidget;

public class RealmsEditBox {
    private final TextFieldWidget editBox;

    public RealmsEditBox(int id, int x, int y, int width, int height) {
        this.editBox = new TextFieldWidget(id, Minecraft.getInstance().textRenderer, x, y, width, height);
    }

    public String getValue() {
        return this.editBox.getText();
    }

    public void tick() {
        this.editBox.tick();
    }

    public void setFocus(boolean focused) {
        this.editBox.setFocused(focused);
    }

    public void setValue(String text) {
        this.editBox.setText(text);
    }

    public void keyPressed(char chr, int code) {
        this.editBox.keyPressed(chr, code);
    }

    public boolean isFocused() {
        return this.editBox.isFocused();
    }

    public void mouseClicked(int mouseX, int mouseY, int button) {
        this.editBox.mouseClicked(mouseX, mouseY, button);
    }

    public void render() {
        this.editBox.render();
    }

    public void setMaxLength(int maximumLength) {
        this.editBox.setMaxLength(maximumLength);
    }

    public void setIsEditable(boolean editable) {
        this.editBox.setEditable(editable);
    }
}
