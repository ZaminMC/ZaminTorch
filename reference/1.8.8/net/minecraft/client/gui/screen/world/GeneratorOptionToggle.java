package net.minecraft.client.gui.screen.world;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.resource.language.I18n;

public class GeneratorOptionToggle extends ButtonWidget {
    private boolean value;
    private String key;
    private final OverworldGeneratorOptionsWidget.Controller controller;

    public GeneratorOptionToggle(OverworldGeneratorOptionsWidget.Controller controller, int id, int x, int y, String key, boolean value) {
        super(id, x, y, 150, 20, "");
        this.key = key;
        this.value = value;
        this.message = this.updateMessage();
        this.controller = controller;
    }

    private String updateMessage() {
        return I18n.translate(this.key) + ": " + (this.value ? I18n.translate("gui.yes") : I18n.translate("gui.no"));
    }

    public void setValue(boolean value) {
        this.value = value;
        this.message = this.updateMessage();
        this.controller.setValue(this.id, value);
    }

    @Override
    public boolean mouseClicked(Minecraft minecraft, int mouseX, int mouseY) {
        if (super.mouseClicked(minecraft, mouseX, mouseY)) {
            this.value = !this.value;
            this.message = this.updateMessage();
            this.controller.setValue(this.id, this.value);
            return true;
        } else {
            return false;
        }
    }
}
