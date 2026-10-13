package net.zaminmc.torch.server.item;

import net.zaminmc.torch.server.entity.MobType;

import java.util.HashMap;
import java.util.Map;

/**
 * The spawn egg family (Slice 20a — the reference's
 * {@code Entities.registerWithSpawnEgg} table + {@code SpawnEggItem}): the
 * egg item's damage field IS the entity registration id, and only the kinds
 * the reference registered with eggs ever answer. The egg colors are a
 * client-side rendering concern (the wire carries only the item id 383 +
 * the damage); the engine needs the id → kind mapping alone.
 *
 * <p>The reference's own set (Entities.java lines 255-286): the hostile
 * band 50-68 (the creeper, the skeleton, the spider, the zombie, the slime,
 * the ghast, the pigman, the enderman, the cave spider, the silverfish, the
 * blaze, the magma cube, the bat, the witch, the endermite, the guardian),
 * the animal band 90-101 (the pig, the sheep, the cow, the chicken, the
 * squid, the wolf, the mooshroom, the ocelot, the horse, the rabbit) and
 * the villager at 120. The golems (97/99) and the boss pair carry NO eggs
 * in the reference — the mapping stays faithful to that gap.</p>
 */
public final class SpawnEggs {

    private SpawnEggs() {
    }

    /** The damage → kind table (built once over {@link MobType}'s own wire ids). */
    private static final Map<Integer, MobType> BY_EGG_ID = build();

    private static Map<Integer, MobType> build() {
        // The kinds the reference's registerWithSpawnEgg covers — keyed by
        // the same legacy ids MobType carries (the Spawn Mob packet's own
        // type byte).
        MobType[] egged = {
                MobType.CREEPER, MobType.SKELETON, MobType.SPIDER, MobType.ZOMBIE,
                MobType.SLIME, MobType.GHAST, MobType.ZOMBIE_PIGMAN, MobType.ENDERMAN,
                MobType.CAVE_SPIDER, MobType.SILVERFISH, MobType.BLAZE, MobType.MAGMA_CUBE,
                MobType.BAT, MobType.WITCH, MobType.ENDERMITE, MobType.GUARDIAN,
                MobType.PIG, MobType.SHEEP, MobType.COW, MobType.CHICKEN,
                MobType.SQUID, MobType.WOLF, MobType.MOOSHROOM, MobType.OCELOT,
                MobType.HORSE, MobType.RABBIT, MobType.VILLAGER,
        };
        Map<Integer, MobType> table = new HashMap<>();
        for (MobType type : egged) {
            table.put(type.legacyTypeId, type);
        }
        return Map.copyOf(table);
    }

    /**
     * @return the kind the egg's damage field names, or null when the
     * damage is not a registered egg id ({@code SpawnEggItem.spawnEntity}'s
     * containsKey gate — an unknown id never spawns and never consumes).
     */
    public static MobType typeOf(int damage) {
        return BY_EGG_ID.get(damage);
    }
}
