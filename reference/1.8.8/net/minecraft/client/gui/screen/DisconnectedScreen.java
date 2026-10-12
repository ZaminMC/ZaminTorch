package net.minecraft.client.gui.screen;

import java.util.List;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.Text;

public class DisconnectedScreen extends Screen {
    private String title;
    private Text reason;
    private List<String> textLines;
    private final Screen parent;
    private int textHeight;

    public DisconnectedScreen(Screen parent, String name, Text reason) {
        this.parent = parent;
        this.title = I18n.translate(name);
        this.reason = reason;
    }

    @Override
    protected void keyPressed(char chr, int key) {
    }

    @Override
    public void init() {
        this.buttons.clear();
        this.textLines = this.textRenderer.split(this.reason.getFormattedString(), this.width - 50);
        this.textHeight = this.textLines.size() * this.textRenderer.fontHeight;
        this.buttons
            .add(new ButtonWidget(0, this.width / 2 - 100, this.height / 2 + this.textHeight / 2 + this.textRenderer.fontHeight, I18n.translate("gui.toMenu")));
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        if (button.id == 0) {
            this.minecraft.openScreen(this.parent);
        }
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        this.renderBackground();
        this.drawCenteredString(
            this.textRenderer, this.title, this.width / 2, this.height / 2 - this.textHeight / 2 - this.textRenderer.fontHeight * 2, 11184810
        );
        int i = this.height / 2 - this.textHeight / 2;
        if (this.textLines != null) {
            for (String s : this.textLines) {
                this.drawCenteredString(this.textRenderer, s, this.width / 2, i, 16777215);
                i += this.textRenderer.fontHeight;
            }
        }

        super.render(mouseX, mouseY, tickDelta);
    }
}
