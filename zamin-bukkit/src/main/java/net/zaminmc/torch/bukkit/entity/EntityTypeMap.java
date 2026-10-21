package net.zaminmc.torch.bukkit.entity;

import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.entity.ProjectileKind;
import net.zaminmc.torch.entity.VehicleKind;

import org.bukkit.entity.EntityType;

import java.util.EnumMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * The engine entity-kind → 1.8.8 {@link EntityType} translation (the
 * {@link net.zaminmc.torch.bukkit.block.MaterialMap} twin for the entity
 * family): one explicit row per engine kind, the legacy names verified
 * against the pinned spigot-api artifact's own enum (the slice's
 * javap-first law).
 *
 * <p><b>The explicit-table law (the difference from the MaterialMap's
 * bare-path resolver):</b> the legacy {@link EntityType} name forms are
 * NOT the engine identifiers — the 1.8.8 names are the wire's own
 * historical spellings ({@code Item} for the dropped body, {@code
 * XPOrb}, {@code MinecartChest}, {@code ThrownExpBottle}), and the
 * API's own {@code EntityType.fromName} resolver misses several engine
 * paths outright ("egg", "minecart", "small_fireball" resolve to null —
 * verified against the pinned artifact). Every row here is therefore
 * named explicitly; an unmapped kind logs loudly ONCE and answers null
 * — the caller's no-dispatch law, never a wrong entity.</p>
 *
 * <p>The mob rows cover every {@link MobType} constant the engine
 * carries (the mapping is total — verified by the test wave's
 * EntityTypeMap coverage assert); the projectile, vehicle and item
 * families carry their own tables.</p>
 */
public final class EntityTypeMap {

    private static final Logger LOGGER = Logger.getLogger(EntityTypeMap.class.getName());

    /** The one-row-per-kind mob table (every engine MobType has its row). */
    private static final Map<MobType, EntityType> MOBS = new EnumMap<>(MobType.class);

    /** The projectile family (the engine's nine public kinds). */
    private static final Map<ProjectileKind, EntityType> PROJECTILES =
            new EnumMap<>(ProjectileKind.class);

    /** The vehicle family (the boat + the seven minecart forms). */
    private static final Map<VehicleKind, EntityType> VEHICLES =
            new EnumMap<>(VehicleKind.class);

    static {
        // The passive + neutral kinds.
        MOBS.put(MobType.PIG, EntityType.PIG);
        MOBS.put(MobType.COW, EntityType.COW);
        MOBS.put(MobType.CHICKEN, EntityType.CHICKEN);
        MOBS.put(MobType.SHEEP, EntityType.SHEEP);
        MOBS.put(MobType.RABBIT, EntityType.RABBIT);
        MOBS.put(MobType.WOLF, EntityType.WOLF);
        MOBS.put(MobType.OCELOT, EntityType.OCELOT);
        MOBS.put(MobType.HORSE, EntityType.HORSE);
        MOBS.put(MobType.VILLAGER, EntityType.VILLAGER);
        MOBS.put(MobType.SQUID, EntityType.SQUID);
        MOBS.put(MobType.BAT, EntityType.BAT);
        MOBS.put(MobType.MOOSHROOM, EntityType.MUSHROOM_COW); // the wire's own name
        // The hostile kinds.
        MOBS.put(MobType.ZOMBIE, EntityType.ZOMBIE);
        MOBS.put(MobType.SKELETON, EntityType.SKELETON);
        MOBS.put(MobType.CREEPER, EntityType.CREEPER);
        MOBS.put(MobType.SPIDER, EntityType.SPIDER);
        MOBS.put(MobType.CAVE_SPIDER, EntityType.CAVE_SPIDER);
        MOBS.put(MobType.SILVERFISH, EntityType.SILVERFISH);
        MOBS.put(MobType.BLAZE, EntityType.BLAZE);
        MOBS.put(MobType.ENDERMAN, EntityType.ENDERMAN);
        MOBS.put(MobType.WITCH, EntityType.WITCH);
        MOBS.put(MobType.SLIME, EntityType.SLIME);
        MOBS.put(MobType.MAGMA_CUBE, EntityType.MAGMA_CUBE); // the wire's LavaSlime
        MOBS.put(MobType.GHAST, EntityType.GHAST);
        MOBS.put(MobType.ZOMBIE_PIGMAN, EntityType.PIG_ZOMBIE); // the wire's own name
        MOBS.put(MobType.GUARDIAN, EntityType.GUARDIAN);
        MOBS.put(MobType.ENDERMITE, EntityType.ENDERMITE);
        MOBS.put(MobType.GIANT, EntityType.GIANT);
        // The constructed + boss kinds.
        MOBS.put(MobType.IRON_GOLEM, EntityType.IRON_GOLEM);
        MOBS.put(MobType.SNOW_GOLEM, EntityType.SNOWMAN); // the wire's SnowMan
        MOBS.put(MobType.WITHER, EntityType.WITHER);
        MOBS.put(MobType.ENDER_DRAGON, EntityType.ENDER_DRAGON);
        // The projectile family (the historical spellings matter: the
        // large fireball is plain FIREBALL, the pearl is
        // ThrownEnderpearl, the bottle ThrownExpBottle).
        PROJECTILES.put(ProjectileKind.ARROW, EntityType.ARROW);
        PROJECTILES.put(ProjectileKind.SNOWBALL, EntityType.SNOWBALL);
        PROJECTILES.put(ProjectileKind.EGG, EntityType.EGG);
        PROJECTILES.put(ProjectileKind.SMALL_FIREBALL, EntityType.SMALL_FIREBALL);
        PROJECTILES.put(ProjectileKind.LARGE_FIREBALL, EntityType.FIREBALL);
        PROJECTILES.put(ProjectileKind.SPLASH_POTION, EntityType.SPLASH_POTION);
        PROJECTILES.put(ProjectileKind.ENDER_PEARL, EntityType.ENDER_PEARL);
        PROJECTILES.put(ProjectileKind.WITHER_SKULL, EntityType.WITHER_SKULL);
        PROJECTILES.put(ProjectileKind.EXPERIENCE_BOTTLE, EntityType.THROWN_EXP_BOTTLE);
        // The vehicle family (the wire's own minecart spellings).
        VEHICLES.put(VehicleKind.BOAT, EntityType.BOAT);
        VEHICLES.put(VehicleKind.MINECART, EntityType.MINECART);
        VEHICLES.put(VehicleKind.CHEST_MINECART, EntityType.MINECART_CHEST);
        VEHICLES.put(VehicleKind.FURNACE_MINECART, EntityType.MINECART_FURNACE);
        VEHICLES.put(VehicleKind.TNT_MINECART, EntityType.MINECART_TNT);
        VEHICLES.put(VehicleKind.HOPPER_MINECART, EntityType.MINECART_HOPPER);
        VEHICLES.put(VehicleKind.SPAWNER_MINECART, EntityType.MINECART_MOB_SPAWNER);
        VEHICLES.put(VehicleKind.COMMAND_BLOCK_MINECART, EntityType.MINECART_COMMAND);
    }

    private EntityTypeMap() {
    }

    /** The mob-kind row, or null when the engine kind has no 1.8.8 form. */
    public static EntityType of(MobType type) {
        return resolve(MOBS.get(type), "mob", type.name());
    }

    /** The projectile-kind row, or null when the kind has no 1.8.8 form. */
    public static EntityType of(ProjectileKind kind) {
        return resolve(PROJECTILES.get(kind), "projectile", kind.name());
    }

    /** The vehicle-kind row, or null when the kind has no 1.8.8 form. */
    public static EntityType of(VehicleKind kind) {
        return resolve(VEHICLES.get(kind), "vehicle", kind.name());
    }

    /** The dropped-item body's row (the wire's own "Item" name). */
    public static EntityType droppedItem() {
        return EntityType.DROPPED_ITEM;
    }

    /**
     * The mob-kind row by the engine identifier the native {@code
     * MobSpawnEvent} carries ({@code minecraft:pig}), or null when the
     * identifier names no engine kind (a foreign host's event — the
     * caller's loud-skip law).
     */
    public static EntityType ofMobIdentifier(String identifier) {
        String bare = identifier.startsWith("minecraft:")
                ? identifier.substring("minecraft:".length())
                : identifier;
        MobType type = MobType.byName(bare);
        if (type == null) {
            LOGGER.warning(() -> "EntityTypeMap: the engine mob identifier "
                    + identifier + " names no engine MobType — the event was not bridged");
            return null;
        }
        return of(type);
    }

    /** The unmapped-kind log (once per kind, never a wrong entity). */
    private static EntityType resolve(EntityType row, String family, String name) {
        if (row == null) {
            LOGGER.warning(() -> "EntityTypeMap: the engine " + family
                    + " kind " + name + " has no 1.8.8 EntityType row"
                    + " — the event was not bridged");
            return null;
        }
        return row;
    }
}
