package net.minecraft.client.gui.screen.world;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.resource.language.I18n;

public class GeneratorOptionSlider extends ButtonWidget {
    private float amount = 1.0F;
    public boolean hovered;
    private String key;
    private final float min;
    private final float max;
    private final OverworldGeneratorOptionsWidget.Controller controller;
    private GeneratorOptionSlider.ValueFormatter formatter;

    public GeneratorOptionSlider(
        OverworldGeneratorOptionsWidget.Controller controller,
        int id,
        int x,
        int y,
        String key,
        float min,
        float max,
        float defaultValue,
        GeneratorOptionSlider.ValueFormatter formatter
    ) {
        super(id, x, y, 150, 20, "");
        this.key = key;
        this.min = min;
        this.max = max;
        this.amount = (defaultValue - min) / (max - min);
        this.formatter = formatter;
        this.controller = controller;
        this.message = this.updateMessage();
    }

    public float getValue() {
        return this.min + (this.max - this.min) * this.amount;
    }

    public void setValue(float value, boolean notify) {
        this.amount = (value - this.min) / (this.max - this.min);
        this.message = this.updateMessage();
        if (notify) {
            this.controller.setValue(this.id, this.getValue());
        }
    }

    public float getAmount() {
        return this.amount;
    }

    private String updateMessage() {
        return this.formatter == null
            ? I18n.translate(this.key) + ": " + this.getValue()
            : this.formatter.formatValue(this.id, I18n.translate(this.key), this.getValue());
    }

    @Override
    protected int getYImage(boolean hovered) {
        return 0;
    }

    @Override
    protected void renderBackground(Minecraft minecraft, int mouseX, int mouseY) {
        if (this.visible) {
            if (this.hovered) {
                this.amount = (float)(mouseX - (this.x + 4)) / (this.width - 8);
                if (this.amount < 0.0F) {
                    this.amount = 0.0F;
                }

                if (this.amount > 1.0F) {
                    this.amount = 1.0F;
                }

                this.message = this.updateMessage();
                this.controller.setValue(this.id, this.getValue());
            }

            GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            this.drawTexture(this.x + (int)(this.amount * (this.width - 8)), this.y, 0, 66, 4, 20);
            this.drawTexture(this.x + (int)(this.amount * (this.width - 8)) + 4, this.y, 196, 66, 4, 20);
        }
    }

    public void setAmount(float amount) {
        this.amount = amount;
        this.message = this.updateMessage();
        this.controller.setValue(this.id, this.getValue());
    }

    @Override
    public boolean mouseClicked(Minecraft minecraft, int mouseX, int mouseY) {
        if (super.mouseClicked(minecraft, mouseX, mouseY)) {
            this.amount = (float)(mouseX - (this.x + 4)) / (this.width - 8);
            if (this.amount < 0.0F) {
                this.amount = 0.0F;
            }

            if (this.amount > 1.0F) {
                this.amount = 1.0F;
            }

            this.message = this.updateMessage();
            this.controller.setValue(this.id, this.getValue());
            this.hovered = true;
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY) {
        this.hovered = false;
    }

    public interface ValueFormatter {
        String formatValue(int id, String message, float value);
    }
}
