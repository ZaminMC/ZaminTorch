package net.minecraft.client.gui.screen;

import net.minecraft.client.network.handler.ClientPlayNetworkHandler;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.network.packet.c2s.play.KeepAliveC2SPacket;

public class DownloadingTerrainScreen extends Screen {
    private ClientPlayNetworkHandler networkHandler;
    private int ticks;

    public DownloadingTerrainScreen(ClientPlayNetworkHandler networkHandler) {
        this.networkHandler = networkHandler;
    }

    @Override
    protected void keyPressed(char chr, int key) {
    }

    @Override
    public void init() {
        this.buttons.clear();
    }

    @Override
    public void tick() {
        this.ticks++;
        if (this.ticks % 20 == 0) {
            this.networkHandler.sendPacket(new KeepAliveC2SPacket());
        }
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        this.drawBackgroundTexture(0);
        this.drawCenteredString(this.textRenderer, I18n.translate("multiplayer.downloadingTerrain"), this.width / 2, this.height / 2 - 50, 16777215);
        super.render(mouseX, mouseY, tickDelta);
    }

    @Override
    public boolean shouldPauseGame() {
        return false;
    }
}
