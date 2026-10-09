package net.minecraft.entity.ai.goal;

import net.minecraft.entity.Entity;
import net.minecraft.entity.living.mob.MobEntity;

public class GoToEntityGoal extends LookAtEntityGoal {
    public GoToEntityGoal(MobEntity mobEntity, Class<? extends Entity> class_, float f, float g) {
        super(mobEntity, class_, f, g);
        this.setControls(3);
    }
}
