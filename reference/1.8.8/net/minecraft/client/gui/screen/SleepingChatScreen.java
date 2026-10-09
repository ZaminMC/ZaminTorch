package net.minecraft.client.gui.screen;

import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.handler.ClientPlayNetworkHandler;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.network.packet.c2s.play.PlayerMovementActionC2SPacket;

public class SleepingChatScreen extends ChatScreen {
    @Override
    public void init() {
        super.init();
        this.buttons.add(new ButtonWidget(1, this.width / 2 - 100, this.height - 40, I18n.translate("multiplayer.stopSleeping")));
    }

    @Override
    protected void keyPressed(char chr, int key) {
        if (key == 1) {
            this.stopSleeping();
        } else if (key != 28 && key != 156) {
            super.keyPressed(chr, key);
        } else {
            String s = this.chatField.getText().trim();
            if (!s.isEmpty()) {
                this.minecraft.player.sendChat(s);
            }

            this.chatField.setText("");
            this.minecraft.gui.getChat().resetScroll();
        }
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        if (button.id == 1) {
            this.stopSleeping();
        } else {
            super.buttonClicked(button);
        }
    }

    private void stopSleeping() {
        ClientPlayNetworkHandler clientplaynetworkhandler = this.minecraft.player.networkHandler;
        clientplaynetworkhandler.sendPacket(new PlayerMovementActionC2SPacket(this.minecraft.player, PlayerMovementActionC2SPacket.Action.STOP_SLEEPING));
    }
}
