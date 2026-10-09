package net.minecraft.entity.living.mob.monster;

import com.google.common.base.Predicate;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SpawnableEntity;

public interface Monster extends SpawnableEntity {
    Predicate<Entity> FILTER = new Predicate<Entity>() {
        public boolean apply(Entity entity) {
            return entity instanceof Monster;
        }
    };
    Predicate<Entity> VISIBLE_MONSTER_FILTER = new Predicate<Entity>() {
        public boolean apply(Entity entity) {
            return entity instanceof Monster && !entity.isInvisible();
        }
    };
}
