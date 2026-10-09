package net.minecraft.client.gui.screen;

import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.OptionButtonWidget;
import net.minecraft.client.resource.language.I18n;

public class OutOfMemoryScreen extends Screen {
    @Override
    public void init() {
        this.buttons.clear();
        this.buttons.add(new OptionButtonWidget(0, this.width / 2 - 155, this.height / 4 + 120 + 12, I18n.translate("gui.toTitle")));
        this.buttons.add(new OptionButtonWidget(1, this.width / 2 - 155 + 160, this.height / 4 + 120 + 12, I18n.translate("menu.quit")));
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        if (button.id == 0) {
            this.minecraft.openScreen(new TitleScreen());
        } else if (button.id == 1) {
            this.minecraft.stop();
        }
    }

    @Override
    protected void keyPressed(char chr, int key) {
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        this.renderBackground();
        this.drawCenteredString(this.textRenderer, "Out of memory!", this.width / 2, this.height / 4 - 60 + 20, 16777215);
        this.drawString(this.textRenderer, "Minecraft has run out of memory.", this.width / 2 - 140, this.height / 4 - 60 + 60 + 0, 10526880);
        this.drawString(
            this.textRenderer, "This could be caused by a bug in the game or by the", this.width / 2 - 140, this.height / 4 - 60 + 60 + 18, 10526880
        );
        this.drawString(this.textRenderer, "Java Virtual Machine not being allocated enough", this.width / 2 - 140, this.height / 4 - 60 + 60 + 27, 10526880);
        this.drawString(this.textRenderer, "memory.", this.width / 2 - 140, this.height / 4 - 60 + 60 + 36, 10526880);
        this.drawString(
            this.textRenderer, "To prevent level corruption, the current game has quit.", this.width / 2 - 140, this.height / 4 - 60 + 60 + 54, 10526880
        );
        this.drawString(
            this.textRenderer, "We've tried to free up enough memory to let you go back to", this.width / 2 - 140, this.height / 4 - 60 + 60 + 63, 10526880
        );
        this.drawString(
            this.textRenderer,
            "the main menu and back to playing, but this may not have worked.",
            this.width / 2 - 140,
            this.height / 4 - 60 + 60 + 72,
            10526880
        );
        this.drawString(
            this.textRenderer, "Please restart the game if you see this message again.", this.width / 2 - 140, this.height / 4 - 60 + 60 + 81, 10526880
        );
        super.render(mouseX, mouseY, tickDelta);
    }
}
