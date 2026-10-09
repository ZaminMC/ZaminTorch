package net.minecraft.client;

public class FrameTimeLogger {
    private final long[] frameTimes = new long[240];
    private int startTime;
    private int length;
    private int endTime;

    public void log(long frameTime) {
        this.frameTimes[this.endTime] = frameTime;
        this.endTime++;
        if (this.endTime == 240) {
            this.endTime = 0;
        }

        if (this.length < 240) {
            this.startTime = 0;
            this.length++;
        } else {
            this.startTime = this.wrapIndex(this.endTime + 1);
        }
    }

    public int scaleFrameTime(long frameTime, int frameRate) {
        double d0 = frameTime / 1.6666666E7;
        return (int)(d0 * frameRate);
    }

    public int getStartTime() {
        return this.startTime;
    }

    public int getEndTime() {
        return this.endTime;
    }

    public int wrapIndex(int index) {
        return index % 240;
    }

    public long[] getFrameTimes() {
        return this.frameTimes;
    }
}
