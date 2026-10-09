package net.minecraft.entity.living.mob;

import com.google.common.collect.Lists;
import java.util.List;
import net.minecraft.entity.Entity;

public class MobVisibilityCache {
    MobEntity mob;
    List<Entity> visibleEntities = Lists.newArrayList();
    List<Entity> invisibleEntities = Lists.newArrayList();

    public MobVisibilityCache(MobEntity mob) {
        this.mob = mob;
    }

    public void clear() {
        this.visibleEntities.clear();
        this.invisibleEntities.clear();
    }

    public boolean canSee(Entity entity) {
        if (this.visibleEntities.contains(entity)) {
            return true;
        }

        if (this.invisibleEntities.contains(entity)) {
            return false;
        }

        this.mob.world.profiler.push("canSee");
        boolean flag = this.mob.canSee(entity);
        this.mob.world.profiler.pop();
        if (flag) {
            this.visibleEntities.add(entity);
        } else {
            this.invisibleEntities.add(entity);
        }

        return flag;
    }
}
