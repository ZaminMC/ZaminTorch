package net.minecraft.entity.ai.control;

import net.minecraft.entity.living.mob.MobEntity;

public class JumpControl {
    private MobEntity mob;
    protected boolean active;

    public JumpControl(MobEntity mob) {
        this.mob = mob;
    }

    public void setActive() {
        this.active = true;
    }

    public void tick() {
        this.mob.setJumping(this.active);
        this.active = false;
    }
}
