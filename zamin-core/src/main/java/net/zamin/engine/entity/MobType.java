package net.zamin.engine.entity;

import net.zamin.api.Identifier;
import net.zamin.engine.item.BuiltinItems;
import net.zamin.engine.item.ItemRoll;

import java.util.List;

/**
 * The mob kinds the engine knows. Data first (hard rule / ADR-0002): the wire
 * type ids and body sizes are the community dataset's
 * (PrismarineJS/minecraft-data {@code data/pc/1.8/entities.json}, MIT license,
 * https://github.com/PrismarineJS/minecraft-data, snapshot 2026-10) — Zombie
 * 54 (0.6x1.8), Pig 90 (0.9x0.9), Cow 92 (0.9x1.3), Chicken 93 (0.4x0.7).
 * The dataset carries no health or loot for pc/1.8, so those mirror the
 * canonical historical values for these mobs (Max Health: pig/cow 10,
 * chicken 4, zombie 20; the loot tables below, in the ItemRoll format).
 *
 * <p>Sounds use the historical 1.8 resource names the client resolves locally
 * (EntityPig/EntityCow/EntityChicken/EntityZombie getHurtSound/getDeathSound);
 * the wire only carries the name string (Named Sound Effect 0x29).</p>
 */
public enum MobType {

    /** Wanderer; flees when hurt. Drops porkchops (smelt into cooked ones). */
    PIG(90, 0.9, 0.9, 10.0f, 0.035, false,
            List.of(new ItemRoll(BuiltinItems.PORKCHOP, 1, 3)),
            "mob.pig.say", "mob.pig.say", "mob.pig.death"),

    /** Wanderer; flees when hurt. Drops beef + leather (the armor material). */
    COW(92, 0.9, 1.3, 10.0f, 0.035, false,
            List.of(new ItemRoll(BuiltinItems.BEEF, 1, 3),
                    new ItemRoll(BuiltinItems.LEATHER, 0, 2)),
            "mob.cow.hurt", "mob.cow.hurt", "mob.cow.hurt"),

    /** Small wanderer; flees when hurt. Drops raw chicken + feathers. */
    CHICKEN(93, 0.4, 0.7, 4.0f, 0.035, false,
            List.of(new ItemRoll(BuiltinItems.RAW_CHICKEN, 1, 1),
                    new ItemRoll(BuiltinItems.FEATHER, 0, 2)),
            "mob.chicken.hurt", "mob.chicken.hurt", "mob.chicken.hurt"),

    /** Hostile: chases the nearest player in range and attacks in melee. */
    ZOMBIE(54, 0.6, 1.8, 20.0f, 0.04, true,
            List.of(new ItemRoll(BuiltinItems.ROTTEN_FLESH, 0, 2)),
            "mob.zombie.say", "mob.zombie.hurt", "mob.zombie.death");

    /** The 1.8 Spawn Mob (0x0F) wire type id (community entities.json). */
    public final int legacyTypeId;
    /** Body size in blocks (community entities.json width/height). */
    public final double width;
    public final double height;
    /** Historical maximum health of the mob kind. */
    public final float maxHealth;
    /** Wander speed in blocks per tick (tuned near the historical attribute). */
    public final double walkSpeed;
    /** Whether the mob hunts players (drives AI branch + night spawning). */
    public final boolean hostile;
    /** Loot rolled on death: item, min, max. */
    public final List<ItemRoll> loot;
    /** Historical 1.8 sound names: idle chatter, hurt, death. */
    public final String idleSound;
    public final String hurtSound;
    public final String deathSound;

    MobType(int legacyTypeId, double width, double height, float maxHealth,
            double walkSpeed, boolean hostile, List<ItemRoll> loot,
            String idleSound, String hurtSound, String deathSound) {
        this.legacyTypeId = legacyTypeId;
        this.width = width;
        this.height = height;
        this.maxHealth = maxHealth;
        this.walkSpeed = walkSpeed;
        this.hostile = hostile;
        this.loot = List.copyOf(loot);
        this.idleSound = idleSound;
        this.hurtSound = hurtSound;
        this.deathSound = deathSound;
    }

    /** @return the kind by its canonical identifier ({@code minecraft:pig}), or empty. */
    public static MobType byName(String name) {
        for (MobType type : values()) {
            if (type.name().equalsIgnoreCase(name)) {
                return type;
            }
        }
        return null;
    }

    /** @return the canonical identifier of the mob kind ({@code minecraft:pig}). */
    public Identifier identifier() {
        return Identifier.parse("minecraft:" + name().toLowerCase(java.util.Locale.ROOT));
    }
}
