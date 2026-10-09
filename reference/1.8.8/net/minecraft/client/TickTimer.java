package net.minecraft.client;

import net.minecraft.util.math.MathHelper;

public class TickTimer {
    float tps;
    private double timeSec;
    public int ticksThisFrame;
    public float partialTick;
    public float tpsScale = 1.0F;
    public float tickDelta;
    private long lastTickTime;
    private long lastCorrectionTime;
    private long cumTickTime;
    private double tickTimeCorrection = 1.0;

    public TickTimer(float tps) {
        this.tps = tps;
        this.lastTickTime = Minecraft.getTime();
        this.lastCorrectionTime = System.nanoTime() / 1000000L;
    }

    public void advance() {
        long i = Minecraft.getTime();
        long j = i - this.lastTickTime;
        long k = System.nanoTime() / 1000000L;
        double d0 = k / 1000.0;
        if (j <= 1000L && j >= 0L) {
            this.cumTickTime += j;
            if (this.cumTickTime > 1000L) {
                long l = k - this.lastCorrectionTime;
                double d1 = (double)this.cumTickTime / l;
                this.tickTimeCorrection = this.tickTimeCorrection + (d1 - this.tickTimeCorrection) * 0.2F;
                this.lastCorrectionTime = k;
                this.cumTickTime = 0L;
            }

            if (this.cumTickTime < 0L) {
                this.lastCorrectionTime = k;
            }
        } else {
            this.timeSec = d0;
        }

        this.lastTickTime = i;
        double d2 = (d0 - this.timeSec) * this.tickTimeCorrection;
        this.timeSec = d0;
        d2 = MathHelper.clamp(d2, 0.0, 1.0);
        this.tickDelta = (float)(this.tickDelta + d2 * this.tpsScale * this.tps);
        this.ticksThisFrame = (int)this.tickDelta;
        this.tickDelta = this.tickDelta - this.ticksThisFrame;
        if (this.ticksThisFrame > 10) {
            this.ticksThisFrame = 10;
        }

        this.partialTick = this.tickDelta;
    }
}
