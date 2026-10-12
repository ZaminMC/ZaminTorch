package net.minecraft.client.gui.screen.multiplayer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.widget.EntryListWidget;
import net.minecraft.client.resource.language.I18n;

public class LanScanWidget implements EntryListWidget.Entry {
    private final Minecraft minecraft = Minecraft.getInstance();

    @Override
    public void render(int index, int x, int y, int width, int height, int mouseX, int mouseY, boolean hovered) {
        int i = y + height / 2 - this.minecraft.textRenderer.fontHeight / 2;
        this.minecraft
            .textRenderer
            .draw(
                I18n.translate("lanServer.scanning"),
                this.minecraft.screen.width / 2 - this.minecraft.textRenderer.getWidth(I18n.translate("lanServer.scanning")) / 2,
                i,
                16777215
            );
        String s;
        switch ((int)(Minecraft.getTime() / 300L % 4L)) {
            case 0:
            default:
                s = "O o o";
                break;
            case 1:
            case 3:
                s = "o O o";
                break;
            case 2:
                s = "o o O";
        }

        this.minecraft
            .textRenderer
            .draw(s, this.minecraft.screen.width / 2 - this.minecraft.textRenderer.getWidth(s) / 2, i + this.minecraft.textRenderer.fontHeight, 8421504);
    }

    @Override
    public void renderOutOfBounds(int index, int x, int y) {
    }

    @Override
    public boolean mouseClicked(int index, int mouseX, int mouseY, int button, int entryMouseX, int entryMouseY) {
        return false;
    }

    @Override
    public void mouseReleased(int index, int mouseX, int mouseY, int button, int entryMouseX, int entryMouseY) {
    }
}
