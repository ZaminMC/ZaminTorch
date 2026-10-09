package net.minecraft.client.gui.screen;

import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.resource.language.I18n;

public class FatalErrorScreen extends Screen {
    private String title;
    private String description;

    public FatalErrorScreen(String title, String description) {
        this.title = title;
        this.description = description;
    }

    @Override
    public void init() {
        super.init();
        this.buttons.add(new ButtonWidget(0, this.width / 2 - 100, 140, I18n.translate("gui.cancel")));
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        this.fillGradient(0, 0, this.width, this.height, -12574688, -11530224);
        this.drawCenteredString(this.textRenderer, this.title, this.width / 2, 90, 16777215);
        this.drawCenteredString(this.textRenderer, this.description, this.width / 2, 110, 16777215);
        super.render(mouseX, mouseY, tickDelta);
    }

    @Override
    protected void keyPressed(char chr, int key) {
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        this.minecraft.openScreen(null);
    }
}
