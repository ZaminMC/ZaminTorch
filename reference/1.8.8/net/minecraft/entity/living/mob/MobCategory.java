package net.minecraft.entity.living.mob;

import net.minecraft.block.material.Material;
import net.minecraft.entity.SpawnableEntity;
import net.minecraft.entity.living.mob.ambient.AmbientMobEntity;
import net.minecraft.entity.living.mob.monster.Monster;
import net.minecraft.entity.living.mob.passive.animal.AnimalEntity;
import net.minecraft.entity.living.mob.water.WaterMobEntity;

public enum MobCategory {
    MONSTER(Monster.class, 70, Material.AIR, false, false),
    CREATURE(AnimalEntity.class, 10, Material.AIR, true, true),
    AMBIENT(AmbientMobEntity.class, 15, Material.AIR, true, false),
    WATER_CREATURE(WaterMobEntity.class, 5, Material.WATER, true, false);

    private final Class<? extends SpawnableEntity> type;
    private final int cap;
    private final Material spawnableMaterial;
    private final boolean peaceful;
    private final boolean rare;

    MobCategory(Class<? extends SpawnableEntity> type, int cap, Material spawnableMaterial, boolean peaceful, boolean rare) {
        this.type = type;
        this.cap = cap;
        this.spawnableMaterial = spawnableMaterial;
        this.peaceful = peaceful;
        this.rare = rare;
    }

    public Class<? extends SpawnableEntity> getType() {
        return this.type;
    }

    public int getCap() {
        return this.cap;
    }

    public boolean isPeaceful() {
        return this.peaceful;
    }

    public boolean isRare() {
        return this.rare;
    }
}
