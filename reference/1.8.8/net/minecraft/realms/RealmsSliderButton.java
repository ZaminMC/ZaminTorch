package net.minecraft.realms;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.util.math.MathHelper;

public class RealmsSliderButton extends RealmsButton {
    public float value = 1.0F;
    public boolean sliding;
    private final float minValue;
    private final float maxValue;
    private int steps;

    public RealmsSliderButton(int id, int x, int y, int width, int max, int i) {
        this(id, x, y, width, i, 0, 1.0F, max);
    }

    public RealmsSliderButton(int id, int x, int y, int width, int i, int initialValue, float f, float g) {
        super(id, x, y, width, 20, "");
        this.minValue = f;
        this.maxValue = g;
        this.value = this.toPct(initialValue);
        this.getProxy().message = this.getMessage();
    }

    public String getMessage() {
        return "";
    }

    public float toPct(float value) {
        return MathHelper.clamp((this.clamp(value) - this.minValue) / (this.maxValue - this.minValue), 0.0F, 1.0F);
    }

    public float toValue(float value) {
        return this.clamp(this.minValue + (this.maxValue - this.minValue) * MathHelper.clamp(value, 0.0F, 1.0F));
    }

    public float clamp(float value) {
        value = this.clampSteps(value);
        return MathHelper.clamp(value, this.minValue, this.maxValue);
    }

    protected float clampSteps(float value) {
        if (this.steps > 0) {
            value = this.steps * Math.round(value / this.steps);
        }

        return value;
    }

    @Override
    public int getYImage(boolean bl) {
        return 0;
    }

    @Override
    public void renderBg(int i, int j) {
        if (this.getProxy().visible) {
            if (this.sliding) {
                this.value = (float)(i - (this.getProxy().x + 4)) / (this.getProxy().getWidth() - 8);
                this.value = MathHelper.clamp(this.value, 0.0F, 1.0F);
                float f = this.toValue(this.value);
                this.clicked(f);
                this.value = this.toPct(f);
                this.getProxy().message = this.getMessage();
            }

            Minecraft.getInstance().getTextureManager().bind(WIDGETS_LOCATION);
            GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
            this.blit(this.getProxy().x + (int)(this.value * (this.getProxy().getWidth() - 8)), this.getProxy().y, 0, 66, 4, 20);
            this.blit(this.getProxy().x + (int)(this.value * (this.getProxy().getWidth() - 8)) + 4, this.getProxy().y, 196, 66, 4, 20);
        }
    }

    @Override
    public void clicked(int i, int j) {
        this.value = (float)(i - (this.getProxy().x + 4)) / (this.getProxy().getWidth() - 8);
        this.value = MathHelper.clamp(this.value, 0.0F, 1.0F);
        this.clicked(this.toValue(this.value));
        this.getProxy().message = this.getMessage();
        this.sliding = true;
    }

    public void clicked(float value) {
    }

    @Override
    public void released(int i, int j) {
        this.sliding = false;
    }
}
