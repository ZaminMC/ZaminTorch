package net.minecraft.entity.ai.goal;

import java.util.List;
import java.util.Random;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.living.mob.passive.PassiveEntity;
import net.minecraft.entity.living.mob.passive.animal.AnimalEntity;
import net.minecraft.entity.living.mob.passive.animal.CowEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.particle.ParticleType;
import net.minecraft.stat.Stats;
import net.minecraft.stat.achievement.Achievements;
import net.minecraft.world.World;

public class AnimalBreedGoal extends Goal {
    private AnimalEntity animal;
    World world;
    private AnimalEntity mate;
    int breedTimer;
    double speedModifier;

    public AnimalBreedGoal(AnimalEntity animal, double speedModifier) {
        this.animal = animal;
        this.world = animal.world;
        this.speedModifier = speedModifier;
        this.setControls(3);
    }

    @Override
    public boolean canStart() {
        if (!this.animal.isInLove()) {
            return false;
        }

        this.mate = this.findMate();
        return this.mate != null;
    }

    @Override
    public boolean shouldContinue() {
        return this.mate.isAlive() && this.mate.isInLove() && this.breedTimer < 60;
    }

    @Override
    public void stop() {
        this.mate = null;
        this.breedTimer = 0;
    }

    @Override
    public void tick() {
        this.animal.getLookControl().setLookatValues(this.mate, 10.0F, this.animal.getLookPitchSpeed());
        this.animal.getNavigation().moveTo(this.mate, this.speedModifier);
        this.breedTimer++;
        if (this.breedTimer >= 60 && this.animal.squaredDistanceTo(this.mate) < 9.0) {
            this.breed();
        }
    }

    private AnimalEntity findMate() {
        float f = 8.0F;
        List<AnimalEntity> list = this.world.getEntitiesOfType((Class<? extends AnimalEntity>)this.animal.getClass(), this.animal.getShape().grown(f, f, f));
        double d0 = Double.MAX_VALUE;
        AnimalEntity animalentity = null;

        for (AnimalEntity animalentity1 : list) {
            if (this.animal.canBreedWith(animalentity1) && this.animal.squaredDistanceTo(animalentity1) < d0) {
                animalentity = animalentity1;
                d0 = this.animal.squaredDistanceTo(animalentity1);
            }
        }

        return animalentity;
    }

    private void breed() {
        PassiveEntity passiveentity = this.animal.makeChild(this.mate);
        if (passiveentity != null) {
            PlayerEntity playerentity = this.animal.getLoveCausingPlayer();
            if (playerentity == null && this.mate.getLoveCausingPlayer() != null) {
                playerentity = this.mate.getLoveCausingPlayer();
            }

            if (playerentity != null) {
                playerentity.incrementStat(Stats.ANIMALS_BRED);
                if (this.animal instanceof CowEntity) {
                    playerentity.incrementStat(Achievements.BREED_COW);
                }
            }

            this.animal.setBreedingAge(6000);
            this.mate.setBreedingAge(6000);
            this.animal.resetLoveTicks();
            this.mate.resetLoveTicks();
            passiveentity.setBreedingAge(-24000);
            passiveentity.setPositionAndAngles(this.animal.x, this.animal.y, this.animal.z, 0.0F, 0.0F);
            this.world.addEntity(passiveentity);
            Random random = this.animal.getRandom();

            for (int i = 0; i < 7; i++) {
                double d0 = random.nextGaussian() * 0.02;
                double d1 = random.nextGaussian() * 0.02;
                double d2 = random.nextGaussian() * 0.02;
                double d3 = random.nextDouble() * this.animal.width * 2.0 - this.animal.width;
                double d4 = 0.5 + random.nextDouble() * this.animal.height;
                double d5 = random.nextDouble() * this.animal.width * 2.0 - this.animal.width;
                this.world.addParticle(ParticleType.HEART, this.animal.x + d3, this.animal.y + d4, this.animal.z + d5, d0, d1, d2);
            }

            if (this.world.getGameRules().getBoolean("doMobLoot")) {
                this.world.addEntity(new ExperienceOrbEntity(this.world, this.animal.x, this.animal.y, this.animal.z, random.nextInt(7) + 1));
            }
        }
    }
}
