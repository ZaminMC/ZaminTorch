package net.minecraft.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.options.GameOptions;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.util.math.MathHelper;

public class OptionSliderWidget extends ButtonWidget {
    private float value = 1.0F;
    public boolean dragging;
    private GameOptions.Option option;
    private final float min;
    private final float max;

    public OptionSliderWidget(int id, int x, int y, GameOptions.Option option) {
        this(id, x, y, option, 0.0F, 1.0F);
    }

    public OptionSliderWidget(int id, int x, int y, GameOptions.Option option, float min, float max) {
        super(id, x, y, 150, 20, "");
        this.option = option;
        this.min = min;
        this.max = max;
        Minecraft minecraft = Minecraft.getInstance();
        this.value = option.normalize(minecraft.options.getFloat(option));
        this.message = minecraft.options.getAsString(option);
    }

    @Override
    protected int getYImage(boolean hovered) {
        return 0;
    }

    @Override
    protected void renderBackground(Minecraft minecraft, int mouseX, int mouseY) {
        if (this.visible) {
            if (this.dragging) {
                this.value = (float)(mouseX - (this.x + 4)) / (this.width - 8);
                this.value = MathHelper.clamp(this.value, 0.0F, 1.0F);
                float f = this.option.denormalize(this.value);
                minecraft.options.set(this.option, f);
                this.value = this.option.normalize(f);
                this.message = minecraft.options.getAsString(this.option);
            }

            minecraft.getTextureManager().bind(WIDGETS_LOCATION);
            GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            this.drawTexture(this.x + (int)(this.value * (this.width - 8)), this.y, 0, 66, 4, 20);
            this.drawTexture(this.x + (int)(this.value * (this.width - 8)) + 4, this.y, 196, 66, 4, 20);
        }
    }

    @Override
    public boolean mouseClicked(Minecraft minecraft, int mouseX, int mouseY) {
        if (super.mouseClicked(minecraft, mouseX, mouseY)) {
            this.value = (float)(mouseX - (this.x + 4)) / (this.width - 8);
            this.value = MathHelper.clamp(this.value, 0.0F, 1.0F);
            minecraft.options.set(this.option, this.option.denormalize(this.value));
            this.message = minecraft.options.getAsString(this.option);
            this.dragging = true;
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY) {
        this.dragging = false;
    }
}
