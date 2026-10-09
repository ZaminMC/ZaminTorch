package net.minecraft.stat;

public class StatCounter {
    private int value;
    private StatProgress progress;

    public int getValue() {
        return this.value;
    }

    public void setValue(int value) {
        this.value = value;
    }

    public <T extends StatProgress> T getProgress() {
        return (T)this.progress;
    }

    public void setProgress(StatProgress progress) {
        this.progress = progress;
    }
}
