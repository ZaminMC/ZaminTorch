package net.minecraft.client.gui.screen;

import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.Formatting;

public class DeathScreen extends Screen implements ConfirmationListener {
    private int ticksSinceDeath;
    private boolean isHardcore = false;

    @Override
    public void init() {
        this.buttons.clear();
        if (this.minecraft.world.getData().isHardcore()) {
            if (this.minecraft.isIntegratedServerRunning()) {
                this.buttons.add(new ButtonWidget(1, this.width / 2 - 100, this.height / 4 + 96, I18n.translate("deathScreen.deleteWorld")));
            } else {
                this.buttons.add(new ButtonWidget(1, this.width / 2 - 100, this.height / 4 + 96, I18n.translate("deathScreen.leaveServer")));
            }
        } else {
            this.buttons.add(new ButtonWidget(0, this.width / 2 - 100, this.height / 4 + 72, I18n.translate("deathScreen.respawn")));
            this.buttons.add(new ButtonWidget(1, this.width / 2 - 100, this.height / 4 + 96, I18n.translate("deathScreen.titleScreen")));
            if (this.minecraft.getSession() == null) {
                this.buttons.get(1).active = false;
            }
        }

        for (ButtonWidget buttonwidget : this.buttons) {
            buttonwidget.active = false;
        }
    }

    @Override
    protected void keyPressed(char chr, int key) {
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        switch (button.id) {
            case 0:
                this.minecraft.player.respawn();
                this.minecraft.openScreen(null);
                break;
            case 1:
                if (this.minecraft.world.getData().isHardcore()) {
                    this.minecraft.openScreen(new TitleScreen());
                } else {
                    ConfirmScreen confirmscreen = new ConfirmScreen(
                        this,
                        I18n.translate("deathScreen.quit.confirm"),
                        "",
                        I18n.translate("deathScreen.titleScreen"),
                        I18n.translate("deathScreen.respawn"),
                        0
                    );
                    this.minecraft.openScreen(confirmscreen);
                    confirmscreen.disableButtons(20);
                }
        }
    }

    @Override
    public void confirmResult(boolean result, int id) {
        if (result) {
            this.minecraft.world.disconnect();
            this.minecraft.setWorld(null);
            this.minecraft.openScreen(new TitleScreen());
        } else {
            this.minecraft.player.respawn();
            this.minecraft.openScreen(null);
        }
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        this.fillGradient(0, 0, this.width, this.height, 1615855616, -1602211792);
        GlStateManager.pushMatrix();
        GlStateManager.scalef(2.0F, 2.0F, 2.0F);
        boolean flag = this.minecraft.world.getData().isHardcore();
        String s = flag ? I18n.translate("deathScreen.title.hardcore") : I18n.translate("deathScreen.title");
        this.drawCenteredString(this.textRenderer, s, this.width / 2 / 2, 30, 16777215);
        GlStateManager.popMatrix();
        if (flag) {
            this.drawCenteredString(this.textRenderer, I18n.translate("deathScreen.hardcoreInfo"), this.width / 2, 144, 16777215);
        }

        this.drawCenteredString(
            this.textRenderer, I18n.translate("deathScreen.score") + ": " + Formatting.YELLOW + this.minecraft.player.getScore(), this.width / 2, 100, 16777215
        );
        super.render(mouseX, mouseY, tickDelta);
    }

    @Override
    public boolean shouldPauseGame() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        this.ticksSinceDeath++;
        if (this.ticksSinceDeath == 20) {
            for (ButtonWidget buttonwidget : this.buttons) {
                buttonwidget.active = true;
            }
        }
    }
}
