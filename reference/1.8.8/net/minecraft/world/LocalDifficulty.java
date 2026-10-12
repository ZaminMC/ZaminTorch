package net.minecraft.world;

import net.minecraft.util.math.MathHelper;

public class LocalDifficulty {
    private final Difficulty base;
    private final float difficulty;

    public LocalDifficulty(Difficulty base, long timeOfDay, long inhabitedTime, float moonSize) {
        this.base = base;
        this.difficulty = this.calculate(base, timeOfDay, inhabitedTime, moonSize);
    }

    public float get() {
        return this.difficulty;
    }

    public float getMultiplier() {
        if (this.difficulty < 2.0F) {
            return 0.0F;
        } else {
            return this.difficulty > 4.0F ? 1.0F : (this.difficulty - 2.0F) / 2.0F;
        }
    }

    private float calculate(Difficulty base, long timeOfDay, long inhabitedTime, float moonSize) {
        if (base == Difficulty.PEACEFUL) {
            return 0.0F;
        }

        boolean flag = base == Difficulty.HARD;
        float f = 0.75F;
        float f1 = MathHelper.clamp(((float)timeOfDay + -72000.0F) / 1440000.0F, 0.0F, 1.0F) * 0.25F;
        f += f1;
        float f2 = 0.0F;
        f2 += MathHelper.clamp((float)inhabitedTime / 3600000.0F, 0.0F, 1.0F) * (flag ? 1.0F : 0.75F);
        f2 += MathHelper.clamp(moonSize * 0.25F, 0.0F, f1);
        if (base == Difficulty.EASY) {
            f2 *= 0.5F;
        }

        f += f2;
        return base.getId() * f;
    }
}
