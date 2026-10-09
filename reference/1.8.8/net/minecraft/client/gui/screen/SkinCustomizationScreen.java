package net.minecraft.client.gui.screen;

import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.model.PlayerModelPart;
import net.minecraft.client.resource.language.I18n;

public class SkinCustomizationScreen extends Screen {
    private final Screen parent;
    private String title;

    public SkinCustomizationScreen(Screen parent) {
        this.parent = parent;
    }

    @Override
    public void init() {
        int i = 0;
        this.title = I18n.translate("options.skinCustomisation.title");

        for (PlayerModelPart playermodelpart : PlayerModelPart.values()) {
            this.buttons
                .add(
                    new SkinCustomizationScreen.ModelPartButtonWidget(
                        playermodelpart.getIndex(), this.width / 2 - 155 + i % 2 * 160, this.height / 6 + 24 * (i >> 1), 150, 20, playermodelpart
                    )
                );
            i++;
        }

        if (i % 2 == 1) {
            i++;
        }

        this.buttons.add(new ButtonWidget(200, this.width / 2 - 100, this.height / 6 + 24 * (i >> 1), I18n.translate("gui.done")));
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        if (button.active) {
            if (button.id == 200) {
                this.minecraft.options.save();
                this.minecraft.openScreen(this.parent);
            } else if (button instanceof SkinCustomizationScreen.ModelPartButtonWidget) {
                PlayerModelPart playermodelpart = ((SkinCustomizationScreen.ModelPartButtonWidget)button).part;
                this.minecraft.options.togglePlayerModelPart(playermodelpart);
                button.message = this.getButtonLabel(playermodelpart);
            }
        }
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        this.renderBackground();
        this.drawCenteredString(this.textRenderer, this.title, this.width / 2, 20, 16777215);
        super.render(mouseX, mouseY, tickDelta);
    }

    private String getButtonLabel(PlayerModelPart part) {
        String s;
        if (this.minecraft.options.getPlayerModelParts().contains(part)) {
            s = I18n.translate("options.on");
        } else {
            s = I18n.translate("options.off");
        }

        return part.getName().getFormattedString() + ": " + s;
    }

    class ModelPartButtonWidget extends ButtonWidget {
        private final PlayerModelPart part;

        private ModelPartButtonWidget(int id, int x, int y, int width, int height, PlayerModelPart part) {
            super(id, x, y, width, height, SkinCustomizationScreen.this.getButtonLabel(part));
            this.part = part;
        }
    }
}
