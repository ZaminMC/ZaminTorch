package net.minecraft.client.gui.screen;

import net.minecraft.client.gui.screen.menu.AchievementsScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.options.OptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.realms.RealmsBridge;

public class GameMenuScreen extends Screen {
    private int saveStep;
    private int ticks;

    @Override
    public void init() {
        this.saveStep = 0;
        this.buttons.clear();
        int i = -16;
        int j = 98;
        this.buttons.add(new ButtonWidget(1, this.width / 2 - 100, this.height / 4 + 120 + i, I18n.translate("menu.returnToMenu")));
        if (!this.minecraft.isIntegratedServerRunning()) {
            this.buttons.get(0).message = I18n.translate("menu.disconnect");
        }

        this.buttons.add(new ButtonWidget(4, this.width / 2 - 100, this.height / 4 + 24 + i, I18n.translate("menu.returnToGame")));
        this.buttons.add(new ButtonWidget(0, this.width / 2 - 100, this.height / 4 + 96 + i, 98, 20, I18n.translate("menu.options")));
        ButtonWidget buttonwidget;
        this.buttons.add(buttonwidget = new ButtonWidget(7, this.width / 2 + 2, this.height / 4 + 96 + i, 98, 20, I18n.translate("menu.shareToLan")));
        this.buttons.add(new ButtonWidget(5, this.width / 2 - 100, this.height / 4 + 48 + i, 98, 20, I18n.translate("gui.achievements")));
        this.buttons.add(new ButtonWidget(6, this.width / 2 + 2, this.height / 4 + 48 + i, 98, 20, I18n.translate("gui.stats")));
        buttonwidget.active = this.minecraft.isSingleplayer() && !this.minecraft.getServer().isPublished();
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        switch (button.id) {
            case 0:
                this.minecraft.openScreen(new OptionsScreen(this, this.minecraft.options));
                break;
            case 1:
                boolean flag = this.minecraft.isIntegratedServerRunning();
                boolean flag1 = this.minecraft.isConnectedToRealms();
                button.active = false;
                this.minecraft.world.disconnect();
                this.minecraft.setWorld(null);
                if (flag) {
                    this.minecraft.openScreen(new TitleScreen());
                } else if (flag1) {
                    RealmsBridge realmsbridge = new RealmsBridge();
                    realmsbridge.switchToRealms(new TitleScreen());
                } else {
                    this.minecraft.openScreen(new MultiplayerScreen(new TitleScreen()));
                }
            case 2:
            case 3:
            default:
                break;
            case 4:
                this.minecraft.openScreen(null);
                this.minecraft.lockMouse();
                break;
            case 5:
                this.minecraft.openScreen(new AchievementsScreen(this, this.minecraft.player.getStats()));
                break;
            case 6:
                this.minecraft.openScreen(new StatsScreen(this, this.minecraft.player.getStats()));
                break;
            case 7:
                this.minecraft.openScreen(new OpenToLanScreen(this));
        }
    }

    @Override
    public void tick() {
        super.tick();
        this.ticks++;
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        this.renderBackground();
        this.drawCenteredString(this.textRenderer, I18n.translate("menu.game"), this.width / 2, 40, 16777215);
        super.render(mouseX, mouseY, tickDelta);
    }
}
