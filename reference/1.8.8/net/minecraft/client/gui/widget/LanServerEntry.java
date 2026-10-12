package net.minecraft.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.network.LanServerQueryManager;
import net.minecraft.client.resource.language.I18n;

public class LanServerEntry implements EntryListWidget.Entry {
    private final MultiplayerScreen multiplayerScreen;
    protected final Minecraft minecraft;
    protected final LanServerQueryManager.LanServer lanServerInfo;
    private long time = 0L;

    protected LanServerEntry(MultiplayerScreen multiplayerScreen, LanServerQueryManager.LanServer lanServerInfo) {
        this.multiplayerScreen = multiplayerScreen;
        this.lanServerInfo = lanServerInfo;
        this.minecraft = Minecraft.getInstance();
    }

    @Override
    public void render(int index, int x, int y, int width, int height, int mouseX, int mouseY, boolean hovered) {
        this.minecraft.textRenderer.draw(I18n.translate("lanServer.title"), x + 32 + 3, y + 1, 16777215);
        this.minecraft.textRenderer.draw(this.lanServerInfo.getMotd(), x + 32 + 3, y + 12, 8421504);
        if (this.minecraft.options.hideServerAddress) {
            this.minecraft.textRenderer.draw(I18n.translate("selectServer.hiddenAddress"), x + 32 + 3, y + 12 + 11, 3158064);
        } else {
            this.minecraft.textRenderer.draw(this.lanServerInfo.getPort(), x + 32 + 3, y + 12 + 11, 3158064);
        }
    }

    @Override
    public boolean mouseClicked(int index, int mouseX, int mouseY, int button, int entryMouseX, int entryMouseY) {
        this.multiplayerScreen.moveToServer(index);
        if (Minecraft.getTime() - this.time < 250L) {
            this.multiplayerScreen.connect();
        }

        this.time = Minecraft.getTime();
        return false;
    }

    @Override
    public void renderOutOfBounds(int index, int x, int y) {
    }

    @Override
    public void mouseReleased(int index, int mouseX, int mouseY, int button, int entryMouseX, int entryMouseY) {
    }

    public LanServerQueryManager.LanServer getLanServerInfo() {
        return this.lanServerInfo;
    }
}
