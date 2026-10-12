package net.minecraft.client.gui.screen;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.OptionButtonWidget;
import net.minecraft.client.resource.language.I18n;

public class ConfirmScreen extends Screen {
    protected ConfirmationListener listener;
    protected String title;
    private String description;
    private final List<String> descriptionLines = Lists.newArrayList();
    protected String confirmText;
    protected String abortText;
    protected int id;
    private int buttonEnableTimer;

    public ConfirmScreen(ConfirmationListener listener, String title, String description, int id) {
        this.listener = listener;
        this.title = title;
        this.description = description;
        this.id = id;
        this.confirmText = I18n.translate("gui.yes");
        this.abortText = I18n.translate("gui.no");
    }

    public ConfirmScreen(ConfirmationListener parent, String title, String description, String confirmText, String abortText, int id) {
        this.listener = parent;
        this.title = title;
        this.description = description;
        this.confirmText = confirmText;
        this.abortText = abortText;
        this.id = id;
    }

    @Override
    public void init() {
        this.buttons.add(new OptionButtonWidget(0, this.width / 2 - 155, this.height / 6 + 96, this.confirmText));
        this.buttons.add(new OptionButtonWidget(1, this.width / 2 - 155 + 160, this.height / 6 + 96, this.abortText));
        this.descriptionLines.clear();
        this.descriptionLines.addAll(this.textRenderer.split(this.description, this.width - 50));
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        this.listener.confirmResult(button.id == 0, this.id);
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        this.renderBackground();
        this.drawCenteredString(this.textRenderer, this.title, this.width / 2, 70, 16777215);
        int i = 90;

        for (String s : this.descriptionLines) {
            this.drawCenteredString(this.textRenderer, s, this.width / 2, i, 16777215);
            i += this.textRenderer.fontHeight;
        }

        super.render(mouseX, mouseY, tickDelta);
    }

    public void disableButtons(int duration) {
        this.buttonEnableTimer = duration;

        for (ButtonWidget buttonwidget : this.buttons) {
            buttonwidget.active = false;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (--this.buttonEnableTimer == 0) {
            for (ButtonWidget buttonwidget : this.buttons) {
                buttonwidget.active = true;
            }
        }
    }
}
