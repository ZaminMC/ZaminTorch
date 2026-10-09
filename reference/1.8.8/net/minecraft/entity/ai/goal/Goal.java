package net.minecraft.entity.ai.goal;

public abstract class Goal {
    private int controls;

    public abstract boolean canStart();

    public boolean shouldContinue() {
        return this.canStart();
    }

    public boolean canStop() {
        return true;
    }

    public void start() {
    }

    public void stop() {
    }

    public void tick() {
    }

    public void setControls(int controls) {
        this.controls = controls;
    }

    public int getControls() {
        return this.controls;
    }
}
