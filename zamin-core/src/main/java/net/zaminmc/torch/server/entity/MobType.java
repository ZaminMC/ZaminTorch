package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.item.ItemRoll;

import java.util.List;

/**
 * The mob kinds the engine knows. Data first (hard rule / ADR-0002): the wire
 * type ids and body sizes are the community dataset's
 * (PrismarineJS/minecraft-data {@code data/pc/1.8/entities.json}, MIT license,
 * https://github.com/PrismarineJS/minecraft-data, snapshot 2026-10) — Zombie
 * 54 (0.6x1.8), Skeleton 51 (0.6x1.8), Creeper 50 (0.6x1.8), Spider 52
 * (1.4x0.9), Pig 90 (0.9x0.9), Sheep 91 (0.9x1.3), Cow 92 (0.9x1.3), Chicken
 * 93 (0.4x0.7). The dataset carries no health or loot for pc/1.8, so those
 * mirror the canonical historical values for these mobs (Max Health: pig/cow
 * 10, sheep 8, chicken 4, spider 16, zombie/skeleton/creeper 20; the loot
 * tables below, in the ItemRoll format).
 *
 * <p>Sounds use the historical 1.8 resource names the client resolves locally
 * (EntitySkeleton/EntityCreeper/EntitySpider/EntitySheep getHurtSound and
 * friends); the wire only carries the name string (Named Sound Effect 0x29).
 * An empty idle sound marks a silent kind — the creeper creeps.</p>
 *
 * <p>AI traits ({@link Traits}) drive the goal set: {@code ranged} skeletons
 * keep their distance and shoot, {@code explodes} creepers prime and blow
 * up, {@code neutralByDay} spiders hunt only in the dark, {@code shearable}
 * sheep carry wool. Behavior shapes ported from the MobMind reference —
 * see COMMUNITY_REFERENCES.md; the implementation is the engine's own.</p>
 */
public enum MobType {

    /** Wanderer; flees when hurt. Drops porkchops (smelt into cooked ones). */
    PIG(90, 0.9, 0.9, 10.0f, 0.035, false, Traits.NONE,
            List.of(new ItemRoll(BuiltinItems.PORKCHOP, 1, 3)),
            "mob.pig.say", "mob.pig.say", "mob.pig.death"),

    /** Wanderer; flees when hurt. Drops beef + leather (the armor material). */
    COW(92, 0.9, 1.3, 10.0f, 0.035, false, Traits.NONE,
            List.of(new ItemRoll(BuiltinItems.BEEF, 1, 3),
                    new ItemRoll(BuiltinItems.LEATHER, 0, 2)),
            "mob.cow.hurt", "mob.cow.hurt", "mob.cow.hurt"),

    /** Small wanderer; flees when hurt. Drops raw chicken + feathers. */
    CHICKEN(93, 0.4, 0.7, 4.0f, 0.035, false, Traits.NONE,
            List.of(new ItemRoll(BuiltinItems.RAW_CHICKEN, 1, 1),
                    new ItemRoll(BuiltinItems.FEATHER, 0, 2)),
            "mob.chicken.hurt", "mob.chicken.hurt", "mob.chicken.hurt"),

    /** Passive wanderer; shearable for wool (the bed material). */
    SHEEP(91, 0.9, 1.3, 8.0f, 0.035, false, new Traits(false, false, false, true),
            List.of(new ItemRoll(BuiltinItems.WOOL, 0, 1),
                    new ItemRoll(BuiltinItems.MUTTON, 1, 2)),
            "mob.sheep.say", "mob.sheep.say", "mob.sheep.say"),

    /** Hostile melee: chases the nearest player in range and attacks. */
    ZOMBIE(54, 0.6, 1.8, 20.0f, 0.04, true, Traits.NONE,
            List.of(new ItemRoll(BuiltinItems.ROTTEN_FLESH, 0, 2)),
            "mob.zombie.say", "mob.zombie.hurt", "mob.zombie.death"),

    /** Hostile ranged: keeps its distance and shoots arrows. */
    SKELETON(51, 0.6, 1.8, 20.0f, 0.038, true, new Traits(true, false, false, false),
            List.of(new ItemRoll(BuiltinItems.ARROW, 0, 2),
                    new ItemRoll(BuiltinItems.BONE, 0, 2)),
            "mob.skeleton.say", "mob.skeleton.say", "mob.skeleton.say"),

    /** Hostile demolition: closes in, primes, and blows a hole in the world. */
    CREEPER(50, 0.6, 1.8, 20.0f, 0.04, true, new Traits(false, true, false, false),
            List.of(new ItemRoll(BuiltinItems.GUNPOWDER, 0, 2)),
            "", "mob.creeper.say", "mob.creeper.death"),

    /** Hostile hunter of the dark: neutral in daylight, fast at night. */
    SPIDER(52, 1.4, 0.9, 16.0f, 0.055, true, new Traits(false, false, true, false),
            List.of(new ItemRoll(BuiltinItems.STRING, 0, 2)),
            "mob.spider.say", "mob.spider.say", "mob.spider.death");

    /**
     * The AI trait set beyond the walk-chase-panic baseline.
     *
     * @param ranged        keeps distance from its target and shoots
     * @param explodes      primes and detonates at close range
     * @param neutralByDay  hostile only in the dark (the spider)
     * @param shearable     carries wool a player can shear off
     */
    public record Traits(boolean ranged, boolean explodes,
                         boolean neutralByDay, boolean shearable) {
        public static final Traits NONE = new Traits(false, false, false, false);
    }

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
    /** The AI trait set (skeleton ranges, creeper detonates, spider sleeps, sheep shears). */
    public final Traits traits;
    /** Loot rolled on death: item, min, max. */
    public final List<ItemRoll> loot;
    /** Historical 1.8 sound names: idle chatter, hurt, death ("" = silent). */
    public final String idleSound;
    public final String hurtSound;
    public final String deathSound;

    MobType(int legacyTypeId, double width, double height, float maxHealth,
            double walkSpeed, boolean hostile, Traits traits, List<ItemRoll> loot,
            String idleSound, String hurtSound, String deathSound) {
        this.legacyTypeId = legacyTypeId;
        this.width = width;
        this.height = height;
        this.maxHealth = maxHealth;
        this.walkSpeed = walkSpeed;
        this.hostile = hostile;
        this.traits = traits;
        this.loot = List.copyOf(loot);
        this.idleSound = idleSound;
        this.hurtSound = hurtSound;
        this.deathSound = deathSound;
    }

    /** @return the kind by its canonical identifier ({@code minecraft:pig}), or null. */
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
