package net.minecraft.client.render.texture;

import net.minecraft.client.Minecraft;
import net.minecraft.util.math.MathHelper;

public class ClockSprite extends TextureAtlasSprite {
    private double angle;
    private double angleDelta;

    public ClockSprite(String string) {
        super(string);
    }

    @Override
    public void tick() {
        if (!this.frames.isEmpty()) {
            Minecraft minecraft = Minecraft.getInstance();
            double d0 = 0.0;
            if (minecraft.world != null && minecraft.player != null) {
                d0 = minecraft.world.getTimeOfDay(1.0F);
                if (!minecraft.world.dimension.isNatural()) {
                    d0 = Math.random();
                }
            }

            double d1 = d0 - this.angle;

            while (d1 < -0.5) {
                d1++;
            }

            while (d1 >= 0.5) {
                d1--;
            }

            d1 = MathHelper.clamp(d1, -1.0, 1.0);
            this.angleDelta += d1 * 0.1;
            this.angleDelta *= 0.8;
            this.angle = this.angle + this.angleDelta;
            int i = (int)((this.angle + 1.0) * this.frames.size()) % this.frames.size();

            while (i < 0) {
                i = (i + this.frames.size()) % this.frames.size();
            }

            if (i != this.activeFrame) {
                this.activeFrame = i;
                TextureUtil.upload(this.frames.get(this.activeFrame), this.width, this.height, this.x, this.y, false, false);
            }
        }
    }
}
