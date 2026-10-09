package net.minecraft.entity.living.mob;

import com.google.common.collect.Maps;
import java.util.HashMap;
import net.minecraft.entity.living.mob.ambient.BatEntity;
import net.minecraft.entity.living.mob.monster.BlazeEntity;
import net.minecraft.entity.living.mob.monster.CaveSpiderEntity;
import net.minecraft.entity.living.mob.monster.CreeperEntity;
import net.minecraft.entity.living.mob.monster.EndermanEntity;
import net.minecraft.entity.living.mob.monster.EndermiteEntity;
import net.minecraft.entity.living.mob.monster.GhastEntity;
import net.minecraft.entity.living.mob.monster.GiantEntity;
import net.minecraft.entity.living.mob.monster.GuardianEntity;
import net.minecraft.entity.living.mob.monster.MagmaCubeEntity;
import net.minecraft.entity.living.mob.monster.SilverfishEntity;
import net.minecraft.entity.living.mob.monster.SkeletonEntity;
import net.minecraft.entity.living.mob.monster.SlimeEntity;
import net.minecraft.entity.living.mob.monster.SpiderEntity;
import net.minecraft.entity.living.mob.monster.WitchEntity;
import net.minecraft.entity.living.mob.monster.ZombieEntity;
import net.minecraft.entity.living.mob.monster.ZombiePigmanEntity;
import net.minecraft.entity.living.mob.monster.boss.EnderDragonEntity;
import net.minecraft.entity.living.mob.monster.boss.WitherEntity;
import net.minecraft.entity.living.mob.passive.VillagerEntity;
import net.minecraft.entity.living.mob.passive.animal.ChickenEntity;
import net.minecraft.entity.living.mob.passive.animal.CowEntity;
import net.minecraft.entity.living.mob.passive.animal.HorseBaseEntity;
import net.minecraft.entity.living.mob.passive.animal.MooshroomEntity;
import net.minecraft.entity.living.mob.passive.animal.PigEntity;
import net.minecraft.entity.living.mob.passive.animal.RabbitEntity;
import net.minecraft.entity.living.mob.passive.animal.SheepEntity;
import net.minecraft.entity.living.mob.passive.animal.tameable.OcelotEntity;
import net.minecraft.entity.living.mob.passive.animal.tameable.WolfEntity;
import net.minecraft.entity.living.mob.water.SquidEntity;

public class SpawnPlacement {
    private static final HashMap<Class, MobEntity.SpawnEnvironment> ENVIRONMENTS = Maps.newHashMap();

    public static MobEntity.SpawnEnvironment getEnvironment(Class type) {
        return ENVIRONMENTS.get(type);
    }

    static {
        ENVIRONMENTS.put(BatEntity.class, MobEntity.SpawnEnvironment.ON_GROUND);
        ENVIRONMENTS.put(ChickenEntity.class, MobEntity.SpawnEnvironment.ON_GROUND);
        ENVIRONMENTS.put(CowEntity.class, MobEntity.SpawnEnvironment.ON_GROUND);
        ENVIRONMENTS.put(HorseBaseEntity.class, MobEntity.SpawnEnvironment.ON_GROUND);
        ENVIRONMENTS.put(MooshroomEntity.class, MobEntity.SpawnEnvironment.ON_GROUND);
        ENVIRONMENTS.put(OcelotEntity.class, MobEntity.SpawnEnvironment.ON_GROUND);
        ENVIRONMENTS.put(PigEntity.class, MobEntity.SpawnEnvironment.ON_GROUND);
        ENVIRONMENTS.put(RabbitEntity.class, MobEntity.SpawnEnvironment.ON_GROUND);
        ENVIRONMENTS.put(SheepEntity.class, MobEntity.SpawnEnvironment.ON_GROUND);
        ENVIRONMENTS.put(SnowGolemEntity.class, MobEntity.SpawnEnvironment.ON_GROUND);
        ENVIRONMENTS.put(SquidEntity.class, MobEntity.SpawnEnvironment.IN_WATER);
        ENVIRONMENTS.put(IronGolemEntity.class, MobEntity.SpawnEnvironment.ON_GROUND);
        ENVIRONMENTS.put(WolfEntity.class, MobEntity.SpawnEnvironment.ON_GROUND);
        ENVIRONMENTS.put(VillagerEntity.class, MobEntity.SpawnEnvironment.ON_GROUND);
        ENVIRONMENTS.put(EnderDragonEntity.class, MobEntity.SpawnEnvironment.ON_GROUND);
        ENVIRONMENTS.put(WitherEntity.class, MobEntity.SpawnEnvironment.ON_GROUND);
        ENVIRONMENTS.put(BlazeEntity.class, MobEntity.SpawnEnvironment.ON_GROUND);
        ENVIRONMENTS.put(CaveSpiderEntity.class, MobEntity.SpawnEnvironment.ON_GROUND);
        ENVIRONMENTS.put(CreeperEntity.class, MobEntity.SpawnEnvironment.ON_GROUND);
        ENVIRONMENTS.put(EndermanEntity.class, MobEntity.SpawnEnvironment.ON_GROUND);
        ENVIRONMENTS.put(EndermiteEntity.class, MobEntity.SpawnEnvironment.ON_GROUND);
        ENVIRONMENTS.put(GhastEntity.class, MobEntity.SpawnEnvironment.ON_GROUND);
        ENVIRONMENTS.put(GiantEntity.class, MobEntity.SpawnEnvironment.ON_GROUND);
        ENVIRONMENTS.put(GuardianEntity.class, MobEntity.SpawnEnvironment.IN_WATER);
        ENVIRONMENTS.put(MagmaCubeEntity.class, MobEntity.SpawnEnvironment.ON_GROUND);
        ENVIRONMENTS.put(ZombiePigmanEntity.class, MobEntity.SpawnEnvironment.ON_GROUND);
        ENVIRONMENTS.put(SilverfishEntity.class, MobEntity.SpawnEnvironment.ON_GROUND);
        ENVIRONMENTS.put(SkeletonEntity.class, MobEntity.SpawnEnvironment.ON_GROUND);
        ENVIRONMENTS.put(SlimeEntity.class, MobEntity.SpawnEnvironment.ON_GROUND);
        ENVIRONMENTS.put(SpiderEntity.class, MobEntity.SpawnEnvironment.ON_GROUND);
        ENVIRONMENTS.put(WitchEntity.class, MobEntity.SpawnEnvironment.ON_GROUND);
        ENVIRONMENTS.put(ZombieEntity.class, MobEntity.SpawnEnvironment.ON_GROUND);
    }
}
