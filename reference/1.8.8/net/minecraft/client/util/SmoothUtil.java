package net.minecraft.client.util;

public class SmoothUtil {
    private float actualSum;
    private float smoothedSum;
    private float movementLatency;

    public float smooth(float original, float smoother) {
        this.actualSum += original;
        original = (this.actualSum - this.smoothedSum) * smoother;
        this.movementLatency = this.movementLatency + (original - this.movementLatency) * 0.5F;
        if (original > 0.0F && original > this.movementLatency || original < 0.0F && original < this.movementLatency) {
            original = this.movementLatency;
        }

        this.smoothedSum += original;
        return original;
    }

    public void reset() {
        this.actualSum = 0.0F;
        this.smoothedSum = 0.0F;
        this.movementLatency = 0.0F;
    }
}
