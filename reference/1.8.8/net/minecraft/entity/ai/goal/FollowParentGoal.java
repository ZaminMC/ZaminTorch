package net.minecraft.entity.ai.goal;

import java.util.List;
import net.minecraft.entity.living.mob.passive.animal.AnimalEntity;

public class FollowParentGoal extends Goal {
    AnimalEntity childAnimal;
    AnimalEntity parentAnimalEntity;
    double speed;
    private int delay;

    public FollowParentGoal(AnimalEntity childAnimal, double speed) {
        this.childAnimal = childAnimal;
        this.speed = speed;
    }

    @Override
    public boolean canStart() {
        if (this.childAnimal.getBreedingAge() >= 0) {
            return false;
        }

        List<AnimalEntity> list = this.childAnimal
            .world
            .getEntitiesOfType((Class<? extends AnimalEntity>)this.childAnimal.getClass(), this.childAnimal.getShape().grown(8.0, 4.0, 8.0));
        AnimalEntity animalentity = null;
        double d0 = Double.MAX_VALUE;

        for (AnimalEntity animalentity1 : list) {
            if (animalentity1.getBreedingAge() >= 0) {
                double d1 = this.childAnimal.squaredDistanceTo(animalentity1);
                if (!(d1 > d0)) {
                    d0 = d1;
                    animalentity = animalentity1;
                }
            }
        }

        if (animalentity == null) {
            return false;
        }

        if (d0 < 9.0) {
            return false;
        }

        this.parentAnimalEntity = animalentity;
        return true;
    }

    @Override
    public boolean shouldContinue() {
        if (this.childAnimal.getBreedingAge() >= 0) {
            return false;
        }

        if (!this.parentAnimalEntity.isAlive()) {
            return false;
        }

        double d0 = this.childAnimal.squaredDistanceTo(this.parentAnimalEntity);
        return !(d0 < 9.0) && !(d0 > 256.0);
    }

    @Override
    public void start() {
        this.delay = 0;
    }

    @Override
    public void stop() {
        this.parentAnimalEntity = null;
    }

    @Override
    public void tick() {
        if (--this.delay <= 0) {
            this.delay = 10;
            this.childAnimal.getNavigation().moveTo(this.parentAnimalEntity, this.speed);
        }
    }
}
