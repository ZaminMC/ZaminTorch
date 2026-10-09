package net.minecraft.entity.ai.goal;

import net.minecraft.entity.Entity;
import net.minecraft.entity.living.mob.passive.VillagerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.village.Village;

public class VillagerMatingGoal extends Goal {
    private VillagerEntity villager;
    private VillagerEntity mate;
    private World world;
    private int breedTimer;
    Village village;

    public VillagerMatingGoal(VillagerEntity villager) {
        this.villager = villager;
        this.world = villager.world;
        this.setControls(3);
    }

    @Override
    public boolean canStart() {
        if (this.villager.getBreedingAge() != 0) {
            return false;
        }

        if (this.villager.getRandom().nextInt(500) != 0) {
            return false;
        }

        this.village = this.world.getVillages().getNearestVillage(new BlockPos(this.villager), 0);
        if (this.village == null) {
            return false;
        }

        if (this.checkVillageValidity() && this.villager.consumeAvailableFood(true)) {
            Entity entity = this.world.getNearestEntity(VillagerEntity.class, this.villager.getShape().grown(8.0, 3.0, 8.0), this.villager);
            if (entity == null) {
                return false;
            }

            this.mate = (VillagerEntity)entity;
            return this.mate.getBreedingAge() == 0 && this.mate.consumeAvailableFood(true);
        } else {
            return false;
        }
    }

    @Override
    public void start() {
        this.breedTimer = 300;
        this.villager.setMating(true);
    }

    @Override
    public void stop() {
        this.village = null;
        this.mate = null;
        this.villager.setMating(false);
    }

    @Override
    public boolean shouldContinue() {
        return this.breedTimer >= 0 && this.checkVillageValidity() && this.villager.getBreedingAge() == 0 && this.villager.consumeAvailableFood(false);
    }

    @Override
    public void tick() {
        this.breedTimer--;
        this.villager.getLookControl().setLookatValues(this.mate, 10.0F, 30.0F);
        if (this.villager.squaredDistanceTo(this.mate) > 2.25) {
            this.villager.getNavigation().moveTo(this.mate, 0.25);
        } else if (this.breedTimer == 0 && this.mate.getMating()) {
            this.breed();
        }

        if (this.villager.getRandom().nextInt(35) == 0) {
            this.world.doEntityEvent(this.villager, (byte)12);
        }
    }

    private boolean checkVillageValidity() {
        if (!this.village.canMate()) {
            return false;
        }

        int i = (int)(this.village.getDoorCount() * 0.35);
        return this.village.getPopulationSize() < i;
    }

    private void breed() {
        VillagerEntity villagerentity = this.villager.makeChild(this.mate);
        this.mate.setBreedingAge(6000);
        this.villager.setBreedingAge(6000);
        this.mate.setWilling(false);
        this.villager.setWilling(false);
        villagerentity.setBreedingAge(-24000);
        villagerentity.setPositionAndAngles(this.villager.x, this.villager.y, this.villager.z, 0.0F, 0.0F);
        this.world.addEntity(villagerentity);
        this.world.doEntityEvent(villagerentity, (byte)12);
    }
}
