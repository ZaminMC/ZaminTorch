package net.minecraft.entity;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.decoration.LeadKnotEntity;
import net.minecraft.entity.decoration.PaintingEntity;
import net.minecraft.entity.global.LightningBoltEntity;
import net.minecraft.entity.living.ArmorStandEntity;
import net.minecraft.entity.living.mob.IronGolemEntity;
import net.minecraft.entity.living.mob.MobEntity;
import net.minecraft.entity.living.mob.SnowGolemEntity;
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
import net.minecraft.entity.living.mob.monster.MonsterEntity;
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
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.EggEntity;
import net.minecraft.entity.projectile.EnderPearlEntity;
import net.minecraft.entity.projectile.ExperienceBottleEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.entity.projectile.PotionEntity;
import net.minecraft.entity.projectile.SmallFireballEntity;
import net.minecraft.entity.projectile.SnowballEntity;
import net.minecraft.entity.projectile.WitherSkullEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.entity.vehicle.ChestMinecartEntity;
import net.minecraft.entity.vehicle.CommandBlockMinecartEntity;
import net.minecraft.entity.vehicle.FurnaceMinecartEntity;
import net.minecraft.entity.vehicle.HopperMinecartEntity;
import net.minecraft.entity.vehicle.MinecartEntity;
import net.minecraft.entity.vehicle.RideableMinecartEntity;
import net.minecraft.entity.vehicle.SpawnerMinecartEntity;
import net.minecraft.entity.vehicle.TntMinecartEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.stat.Stat;
import net.minecraft.stat.Stats;
import net.minecraft.world.World;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Entities {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Map<String, Class<? extends Entity>> KEY_TO_TYPE = Maps.newHashMap();
    private static final Map<Class<? extends Entity>, String> TYPE_TO_KEY = Maps.newHashMap();
    private static final Map<Integer, Class<? extends Entity>> ID_TO_TYPE = Maps.newHashMap();
    private static final Map<Class<? extends Entity>, Integer> TYPE_TO_ID = Maps.newHashMap();
    private static final Map<String, Integer> KEY_TO_ID = Maps.newHashMap();
    public static final Map<Integer, Entities.SpawnEggData> SPAWN_EGG_DATA = Maps.newLinkedHashMap();

    private static void register(Class<? extends Entity> type, String key, int id) {
        if (KEY_TO_TYPE.containsKey(key)) {
            throw new IllegalArgumentException("ID is already registered: " + key);
        }

        if (ID_TO_TYPE.containsKey(id)) {
            throw new IllegalArgumentException("ID is already registered: " + id);
        }

        if (id == 0) {
            throw new IllegalArgumentException("Cannot register to reserved id: " + id);
        }

        if (type == null) {
            throw new IllegalArgumentException("Cannot register null clazz for id: " + id);
        }

        KEY_TO_TYPE.put(key, type);
        TYPE_TO_KEY.put(type, key);
        ID_TO_TYPE.put(id, type);
        TYPE_TO_ID.put(type, id);
        KEY_TO_ID.put(key, id);
    }

    private static void registerWithSpawnEgg(Class<? extends Entity> type, String key, int id, int baseColor, int spotsColor) {
        register(type, key, id);
        SPAWN_EGG_DATA.put(id, new Entities.SpawnEggData(id, baseColor, spotsColor));
    }

    public static Entity createSilently(String key, World world) {
        Entity entity = null;

        try {
            Class<? extends Entity> oclass = KEY_TO_TYPE.get(key);
            if (oclass != null) {
                entity = oclass.getConstructor(World.class).newInstance(world);
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        }

        return entity;
    }

    public static Entity create(NbtCompound nbt, World world) {
        Entity entity = null;
        if ("Minecart".equals(nbt.getString("id"))) {
            nbt.putString("id", MinecartEntity.Type.byIndex(nbt.getInt("Type")).getName());
            nbt.remove("Type");
        }

        try {
            Class<? extends Entity> oclass = KEY_TO_TYPE.get(nbt.getString("id"));
            if (oclass != null) {
                entity = oclass.getConstructor(World.class).newInstance(world);
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        }

        if (entity != null) {
            entity.readNbt(nbt);
        } else {
            LOGGER.warn("Skipping Entity with id " + nbt.getString("id"));
        }

        return entity;
    }

    public static Entity create(int id, World world) {
        Entity entity = null;

        try {
            Class<? extends Entity> oclass = getType(id);
            if (oclass != null) {
                entity = oclass.getConstructor(World.class).newInstance(world);
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        }

        if (entity == null) {
            LOGGER.warn("Skipping Entity with id " + id);
        }

        return entity;
    }

    public static int getId(Entity entity) {
        Integer integer = TYPE_TO_ID.get(entity.getClass());
        return integer == null ? 0 : integer;
    }

    public static Class<? extends Entity> getType(int id) {
        return ID_TO_TYPE.get(id);
    }

    public static String getKey(Entity entity) {
        return TYPE_TO_KEY.get(entity.getClass());
    }

    public static int getId(String key) {
        Integer integer = KEY_TO_ID.get(key);
        return integer == null ? 90 : integer;
    }

    public static String getKey(int id) {
        return TYPE_TO_KEY.get(getType(id));
    }

    public static void init() {
    }

    public static List<String> getKeys() {
        Set<String> set = KEY_TO_TYPE.keySet();
        List<String> list = Lists.newArrayList();

        for (String s : set) {
            Class<? extends Entity> oclass = KEY_TO_TYPE.get(s);
            if ((oclass.getModifiers() & 1024) != 1024) {
                list.add(s);
            }
        }

        list.add("LightningBolt");
        return list;
    }

    public static boolean is(Entity entity, String key) {
        String s = getKey(entity);
        if (s == null && entity instanceof PlayerEntity) {
            s = "Player";
        } else if (s == null && entity instanceof LightningBoltEntity) {
            s = "LightningBolt";
        }

        return key.equals(s);
    }

    public static boolean isKey(String key) {
        return "Player".equals(key) || getKeys().contains(key);
    }

    static {
        register(ItemEntity.class, "Item", 1);
        register(ExperienceOrbEntity.class, "XPOrb", 2);
        register(EggEntity.class, "ThrownEgg", 7);
        register(LeadKnotEntity.class, "LeashKnot", 8);
        register(PaintingEntity.class, "Painting", 9);
        register(ArrowEntity.class, "Arrow", 10);
        register(SnowballEntity.class, "Snowball", 11);
        register(FireballEntity.class, "Fireball", 12);
        register(SmallFireballEntity.class, "SmallFireball", 13);
        register(EnderPearlEntity.class, "ThrownEnderpearl", 14);
        register(EnderEyeEntity.class, "EyeOfEnderSignal", 15);
        register(PotionEntity.class, "ThrownPotion", 16);
        register(ExperienceBottleEntity.class, "ThrownExpBottle", 17);
        register(ItemFrameEntity.class, "ItemFrame", 18);
        register(WitherSkullEntity.class, "WitherSkull", 19);
        register(PrimedTntEntity.class, "PrimedTnt", 20);
        register(FallingBlockEntity.class, "FallingSand", 21);
        register(FireworksEntity.class, "FireworksRocketEntity", 22);
        register(ArmorStandEntity.class, "ArmorStand", 30);
        register(BoatEntity.class, "Boat", 41);
        register(RideableMinecartEntity.class, MinecartEntity.Type.RIDEABLE.getName(), 42);
        register(ChestMinecartEntity.class, MinecartEntity.Type.CHEST.getName(), 43);
        register(FurnaceMinecartEntity.class, MinecartEntity.Type.FURNACE.getName(), 44);
        register(TntMinecartEntity.class, MinecartEntity.Type.TNT.getName(), 45);
        register(HopperMinecartEntity.class, MinecartEntity.Type.HOPPER.getName(), 46);
        register(SpawnerMinecartEntity.class, MinecartEntity.Type.SPAWNER.getName(), 47);
        register(CommandBlockMinecartEntity.class, MinecartEntity.Type.COMMAND_BLOCK.getName(), 40);
        register(MobEntity.class, "Mob", 48);
        register(MonsterEntity.class, "Monster", 49);
        registerWithSpawnEgg(CreeperEntity.class, "Creeper", 50, 894731, 0);
        registerWithSpawnEgg(SkeletonEntity.class, "Skeleton", 51, 12698049, 4802889);
        registerWithSpawnEgg(SpiderEntity.class, "Spider", 52, 3419431, 11013646);
        register(GiantEntity.class, "Giant", 53);
        registerWithSpawnEgg(ZombieEntity.class, "Zombie", 54, 44975, 7969893);
        registerWithSpawnEgg(SlimeEntity.class, "Slime", 55, 5349438, 8306542);
        registerWithSpawnEgg(GhastEntity.class, "Ghast", 56, 16382457, 12369084);
        registerWithSpawnEgg(ZombiePigmanEntity.class, "PigZombie", 57, 15373203, 5009705);
        registerWithSpawnEgg(EndermanEntity.class, "Enderman", 58, 1447446, 0);
        registerWithSpawnEgg(CaveSpiderEntity.class, "CaveSpider", 59, 803406, 11013646);
        registerWithSpawnEgg(SilverfishEntity.class, "Silverfish", 60, 7237230, 3158064);
        registerWithSpawnEgg(BlazeEntity.class, "Blaze", 61, 16167425, 16775294);
        registerWithSpawnEgg(MagmaCubeEntity.class, "LavaSlime", 62, 3407872, 16579584);
        register(EnderDragonEntity.class, "EnderDragon", 63);
        register(WitherEntity.class, "WitherBoss", 64);
        registerWithSpawnEgg(BatEntity.class, "Bat", 65, 4996656, 986895);
        registerWithSpawnEgg(WitchEntity.class, "Witch", 66, 3407872, 5349438);
        registerWithSpawnEgg(EndermiteEntity.class, "Endermite", 67, 1447446, 7237230);
        registerWithSpawnEgg(GuardianEntity.class, "Guardian", 68, 5931634, 15826224);
        registerWithSpawnEgg(PigEntity.class, "Pig", 90, 15771042, 14377823);
        registerWithSpawnEgg(SheepEntity.class, "Sheep", 91, 15198183, 16758197);
        registerWithSpawnEgg(CowEntity.class, "Cow", 92, 4470310, 10592673);
        registerWithSpawnEgg(ChickenEntity.class, "Chicken", 93, 10592673, 16711680);
        registerWithSpawnEgg(SquidEntity.class, "Squid", 94, 2243405, 7375001);
        registerWithSpawnEgg(WolfEntity.class, "Wolf", 95, 14144467, 13545366);
        registerWithSpawnEgg(MooshroomEntity.class, "MushroomCow", 96, 10489616, 12040119);
        register(SnowGolemEntity.class, "SnowMan", 97);
        registerWithSpawnEgg(OcelotEntity.class, "Ozelot", 98, 15720061, 5653556);
        register(IronGolemEntity.class, "VillagerGolem", 99);
        registerWithSpawnEgg(HorseBaseEntity.class, "EntityHorse", 100, 12623485, 15656192);
        registerWithSpawnEgg(RabbitEntity.class, "Rabbit", 101, 10051392, 7555121);
        registerWithSpawnEgg(VillagerEntity.class, "Villager", 120, 5651507, 12422002);
        register(EnderCrystalEntity.class, "EnderCrystal", 200);
    }

    public static class SpawnEggData {
        public final int id;
        public final int baseColor;
        public final int spotsColor;
        public final Stat killEntityStat;
        public final Stat entityKilledByStat;

        public SpawnEggData(int id, int baseColor, int spotsColor) {
            this.id = id;
            this.baseColor = baseColor;
            this.spotsColor = spotsColor;
            this.killEntityStat = Stats.createEntityKillStat(this);
            this.entityKilledByStat = Stats.createKilledByEntityStat(this);
        }
    }
}
