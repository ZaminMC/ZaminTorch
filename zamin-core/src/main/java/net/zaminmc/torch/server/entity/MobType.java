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

    /**
     * The hopping wanderer (Slice 12l — RabbitEntity): the 0.6x0.7 body,
     * 10 health, movement attribute 0.3 (the engine's animal band); the
     * hop cycle owns the movement (the zeroed-input model, the slime's
     * pattern). Drops: the reference's dropItems — the hide 0-1
     * (nextInt(2) + nextInt(1+looting)) and the raw rabbit 0-1 (the
     * cooked swap rides the burn arm; the foot's rare 2.5% roll ledgers
     * with the rare-loot-table slice).
     */
    RABBIT(101, 0.6, 0.7, 10.0f, 0.035, false, Traits.NONE,
            List.of(new ItemRoll(BuiltinItems.RABBIT_HIDE, 0, 1),
                    new ItemRoll(BuiltinItems.RABBIT, 0, 1)),
            "mob.rabbit.idle", "mob.rabbit.hurt", "mob.rabbit.death"),

    /**
     * The tamer's companion (Slice 15d — WolfEntity): the 0.6x0.8 body,
     * 8 health wild ({@code 20} the moment the tame lands — the reference's
     * own attribute flip), movement 0.3 (the engine's chaser band — the
     * enderman's twin, the same reference attribute). No drops
     * ({@code Item.byId(-1)} — shearing a dead wolf is not a thing). The
     * sounds: the bark/panting idle (the whine under 10 health tamed, the
     * growl angry — the dynamic walk reads the state), hurt, death. The
     * untamed body despawns past 2400 ticks ({@code canDespawn}); a tamed
     * one never does.
     */
    WOLF(95, 0.6, 0.8, 8.0f, 0.05, false, Traits.NONE,
            List.of(),
            "mob.wolf.bark", "mob.wolf.hurt", "mob.wolf.death"),

    /**
     * The jungle cat (Slice 15e — OcelotEntity): the 0.6x0.7 body, 10
     * health (the tame never changes the ceiling — the wolf's flip is its
     * own), movement 0.3 (the chaser band). Silent untamed (the ambient
     * sound returns ""); the tamed purr/meow family rides the ambient-sound
     * slice. The 3.0 melee (tryDamage's own override). No drops
     * (dropItems' empty override). The untamed body despawns past 2400
     * ticks — the wolf's own rule (both TameableEntity's canDespawn
     * shape).
     */
    OCELOT(98, 0.6, 0.7, 10.0f, 0.05, false, Traits.NONE,
            List.of(),
            "", "mob.cat.hitt", "mob.cat.hitt", 3.0f),

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
            "mob.spider.say", "mob.spider.say", "mob.spider.death"),

    /**
     * The rideable mount: temper taming, saddle, horse armor, rider steering
     * (the historical EntityHorse). Body 1.396 wide (the community dataset),
     * avg health 22 (the historical 15-30 band, fixed at the midpoint).
     * No loot: shearing a dead horse is not a thing (the vanilla rule).
     */
    HORSE(100, 1.396, 1.6, 22.0f, 0.045, false, Traits.NONE,
            List.of(),
            "mob.horse.idle", "mob.horse.hit", "mob.horse.death"),

    /** The trading villager: profession carries its offer table (the trading slice). */
    VILLAGER(120, 0.6, 1.95, 20.0f, 0.035, false, Traits.NONE,
            List.of(),
            "mob.villager.idle", "mob.villager.hit", "mob.villager.death"),

    /** The mineshaft's spawner mob (Slice 12g): the small venomous spider. */
    CAVE_SPIDER(59, 0.7, 0.5, 12.0f, 0.055, true, new Traits(false, false, true, false),
            List.of(new ItemRoll(BuiltinItems.STRING, 0, 2)),
            "mob.spider.say", "mob.spider.say", "mob.spider.death"),

    /** The stronghold's spawner mob (Slice 12g): the infestation. */
    SILVERFISH(60, 0.4, 0.3, 8.0f, 0.045, true, Traits.NONE,
            List.of(),
            "mob.silverfish.say", "mob.silverfish.hit", "mob.silverfish.kill"),

    /**
     * The fortress's spawner mob (Slice 18a — the nether's ranged fire,
     * BlazeEntity 1:1): the 0.6x1.8 fire-immune hull, the 6.0 melee
     * (initAttributes line 42), the 0.23 walk, the FOLLOW_RANGE 48, the
     * xpDrop 10 (line 29), and the rod drops through the manager's own
     * walk ({@code dropItems}: {@code nextInt(2 + looting)} single rods,
     * the player-kill gate). The fireball volley, the hover rise, and
     * the water drown ride MobEntity's blaze arms.
     */
    BLAZE(61, 0.6, 1.8, 20.0f, 0.045, true, new Traits(true, false, false, false),
            List.of(), // the custom drop walk (BlazeEntity.dropItems)
            "mob.blaze.breathe", "mob.blaze.hit", "mob.blaze.death", 6.0f),

    /**
     * The tall portal-walker (Slice 12i — the natural-spawn row): melee
     * chases, dodges projectiles, teleports randomly when hurt or in
     * daylight, blinks toward a fleeing target, and takes water damage
     * (the reference's EndermanEntity: 40 health, the 0-1 pearl drop).
     */
    ENDERMAN(58, 0.6, 2.9, 40.0f, 0.05, true, Traits.NONE,
            List.of(new ItemRoll(BuiltinItems.ENDER_PEARL, 0, 1)),
            "mob.endermen.idle", "mob.endermen.hit", "mob.endermen.death", 7.0f),

    /**
     * The potion-thrower (Slice 12i — the natural-spawn row): keeps its
     * distance and throws splash potions by the target's state, drinks
     * defensive potions (the reference's WitchEntity: 26 health, the
     * silent mob — all three sound hooks null).
     */
    WITCH(66, 0.6, 1.95, 26.0f, 0.03, true, new Traits(true, false, false, false),
            List.of(), // the custom drop walk (WitchEntity.dropItems)
            "", "", "", 0.0f),

    /**
     * The hopping splitter (Slice 12k — the natural-spawn row): the
     * smallest default body (the size-1 state — 0.51 x 0.51, 1 health);
     * the spawn-time initialize and the split-on-kill grow it through
     * the sizes 2 and 4 (the per-instance dimension + health recompute,
     * the reference's {@code setSize}). The walkSpeed field is unused —
     * the slime's movement is the hop cycle, not the walk integration.
     */
    SLIME(55, 0.51000005, 0.51000005, 1.0f, 0.03, true, Traits.NONE,
            List.of(new ItemRoll(BuiltinItems.SLIME_BALL, 0, 2)), // size 1 ONLY
            "", "mob.slime.small", "mob.slime.small"), // dynamic: big over size 1

    /**
     * The nether's splitter (Slice 16c — MagmaCubeEntity, the slime's own
     * family): the size system, the split, the contact damage and the
     * hop cycle are all the slime's — the magma's own overrides: the
     * 4x hop cadence, the size-scaled jump, the +2 damage, the fire
     * immunity, the magma-cream drop with the reference's own
     * {@code nextInt(4) - 2} count arm (size 1 only, the walk in the
     * manager's custom drop), and the magmacube sound pair. The
     * walkSpeed field is unused — the hop cycle owns the movement.
     */
    MAGMA_CUBE(62, 0.51000005, 0.51000005, 1.0f, 0.03, true, Traits.NONE,
            List.of(), // the custom drop walk (MagmaCubeEntity.dropItems)
            "", "mob.magmacube.small", "mob.magmacube.small"),

    /**
     * The nether's flyer (Slice 16c — GhastEntity over FlyingMobEntity):
     * the 4x4 body, the 10-health fire-immune hull, the ambient moan at
     * volume 10, the wandering flight (the random ±16 targets with the
     * line-of-sight bursts), and the charged fireball cycle — 20 ticks
     * of charge (the flag at index 16), the launch with the -40 cooldown,
     * the 6.0 direct hit plus the power-1 explosion where it lands. The
     * eye height 2.6 feeds the shot origin. The walkSpeed field is
     * unused — the flight owns the movement.
     */
    GHAST(56, 4.0, 4.0, 10.0f, 0.0, true, Traits.NONE,
            List.of(), // the custom drop walk (GhastEntity.dropItems)
            "mob.ghast.moan", "mob.ghast.scream", "mob.ghast.death", 0.0f),

    /**
     * The nether's neutral horde (Slice 16d — ZombiePigmanEntity over
     * the zombie's body): the fire-immune hull, the 5.0 attack, and the
     * ANGER model — the group aggro on a player's hit: the 400+nextInt(400)
     * window decaying per tick, the delayed angry sound, the +0.05 speed
     * boost while it lasts, and the propagation to every nearby pigman
     * (RevengeGoal's own setMobEntityTarget override). The hunt only runs
     * while angry (TargetPlayerGoal's isAngry gate).
     */
    ZOMBIE_PIGMAN(57, 0.6, 1.8, 20.0f, 0.037, true, Traits.NONE,
            List.of(), // the custom drop walk (ZombiePigmanEntity.dropItems)
            "mob.zombiepig.zpig", "mob.zombiepig.zpighurt", "mob.zombiepig.zpigdeath",
            5.0f),

    /**
     * The water column's swimmer (Slice 16e — SquidEntity): the silent
     * 0.95x0.95/10-health body, the tentacle-cycle swim (the direction
     * target re-rolled on the 1-in-50 cadence, the speed pulsing with
     * the stroke), the out-of-water sink, and the y 45-seaLevel water
     * spawn band. The walkSpeed field is unused — the swim owns the
     * movement.
     */
    SQUID(94, 0.95, 0.95, 10.0f, 0.0, false, Traits.NONE,
            List.of(), // the custom drop walk (SquidEntity.dropItems)
            "", "", ""),

    /**
     * The cave's hanger (Slice 16e — BatEntity): the 0.5x0.9/6-health
     * silent-mostly body, the roosting flag (index 16 bit 0 — the
     * ceiling hang), the wander flight toward the re-rolled hangPos
     * (±7/±6 cube, every 30 ticks), the un-roost on a player within 4
     * or a vanished ceiling, and the 1-in-100 re-roost on a solid block
     * above. The walkSpeed field is unused — the flight owns it.
     */
    BAT(65, 0.5, 0.9, 6.0f, 0.0, false, Traits.NONE,
            List.of(), // no drops (BatEntity's null getDropItem)
            "mob.bat.idle", "mob.bat.hurt", "mob.bat.death"),

    /**
     * The ocean monument's hunter (Slice 17a — GuardianEntity): the
     * 0.85x0.85/30-health body, movement 0.5 (the fastest hostile walk
     * in the water column — the swim owns the movement, the reference's
     * {@code moveRelative} 0.1-scale velocity update with the 0.9 decay),
     * the 6.0 melee that only ever lands through the laser's charged
     * beam (the magic + melee pair at the reference's own
     * {@code getAttackChargeTime()}), the thorns reflect on a melee hit
     * while the body stands still, and the elder variant (the 1.9975
     * hull, 80 health, 8.0, speed 0.3 — the monument's own three). The
     * land pairs ({@code mob.guardian.land.*}) and the elder sound
     * family ride the manager's dynamic reads (the in-water check).
     */
    GUARDIAN(68, 0.85, 0.85, 30.0f, 0.5, true, Traits.NONE,
            List.of(), // the custom drop walk (GuardianEntity.dropItems)
            "mob.guardian.idle", "mob.guardian.hit", "mob.guardian.death", 6.0f),

    /**
     * The mushroom cow (Slice 17b — MooshroomEntity over the cow): the
     * same 0.9x1.3/10-health body, the wheat breeding, and the leather +
     * raw beef drops — the mooshroom's own arms are the interaction pair:
     * the bowl fills with the mushroom stew (the adult only), the shears
     * shear it INTO a plain cow with five red mushrooms (the tool wear +
     * the {@code mob.sheep.shear} sound). The mycelium spawn floor rides
     * the mushroom-biome slice (ledgered: the engine's biome family
     * carries no mushroom island).
     */
    MOOSHROOM(96, 0.9, 1.3, 10.0f, 0.035, false, Traits.NONE,
            List.of(new ItemRoll(BuiltinItems.BEEF, 1, 3),
                    new ItemRoll(BuiltinItems.LEATHER, 0, 2)),
            "mob.cow.say", "mob.cow.hurt", "mob.cow.hurt"),

    /**
     * The pearl's passenger (Slice 17c — EndermiteEntity): the 0.4x0.3
     * body, 8 health, movement 0.25, the 2.0 melee — the silverfish's
     * own sound family and the ARTHROPOD damage category (Bane of
     * Arthropods lands on it). The pearl-spawned body (the 5% roll at
     * the thrower's feet) despawns after its own 2400-tick lifetime
     * unless something names it persistent; the natural spawns never
     * place one (the reference agrees — the pearl and the spawn egg are
     * the only sources).
     */
    ENDERMITE(67, 0.4, 0.3, 8.0f, 0.05, true, Traits.NONE,
            List.of(), // getDropItem's null: no drops at all
            "mob.silverfish.say", "mob.silverfish.hit", "mob.silverfish.kill", 2.0f),

    /**
     * The village's guardian (Slice 17d — IronGolemEntity): the 1.4x2.9
     * body, 100 health, movement 0.25 — the heavy melee family (the
     * {@code 7 + nextInt(15)} hit with the +0.4 launch into the air, the
     * {@code mob.irongolem.throw} sound, the Event-4 swing-up) hunting
     * the hostile kinds (the reference's TrackIronGolemTargetGoal — a
     * player-built body never turns on its builder's kind). Drops the
     * 3-5 iron ingots + the 0-2 poppies. No natural spawn rows (the
     * village's own spawn cycle ledgers with the village-defense slice).
     */
    IRON_GOLEM(99, 1.4, 2.9, 100.0f, 0.25, false, Traits.NONE,
            List.of(), // the custom drop walk (IronGolemEntity.dropItems)
            "", "mob.irongolem.hit", "mob.irongolem.death"),

    /**
     * The winter scout (Slice 17d — SnowGolemEntity): the 0.7x1.9 body, 4
     * health, movement 0.2, the 1.7 eye — the ranged family (the 20-tick
     * snowball volley at the hostile kinds within 10 blocks, the
     * {@code random.bow} launch) with the melts: the water contact costs
     * 1 per tick ({@code DROWN}), the hot biome the same ({@code
     * ON_FIRE} — the engine's desert stand-in). Drops the 0-15
     * snowballs. No natural spawn rows (the pumpkin build is the only
     * source).
     */
    SNOW_GOLEM(97, 0.7, 1.9, 4.0f, 0.2, false, Traits.NONE,
            List.of(new ItemRoll(BuiltinItems.SNOWBALL, 0, 15)), // nextInt(16) = 0-15
            "", "", ""); // GolemEntity's null family — the snow golem never overrides

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
    /**
     * The kind's melee attack damage (the reference's ATTACK_DAMAGE
     * attribute at the easy tier — the zombie's 2.0, the enderman's 7.0;
     * Slice 12i made the flat 2.0 per-kind).
     */
    public final float attackDamage;

    MobType(int legacyTypeId, double width, double height, float maxHealth,
            double walkSpeed, boolean hostile, Traits traits, List<ItemRoll> loot,
            String idleSound, String hurtSound, String deathSound) {
        this(legacyTypeId, width, height, maxHealth, walkSpeed, hostile, traits, loot,
                idleSound, hurtSound, deathSound, 2.0f);
    }

    MobType(int legacyTypeId, double width, double height, float maxHealth,
            double walkSpeed, boolean hostile, Traits traits, List<ItemRoll> loot,
            String idleSound, String hurtSound, String deathSound, float attackDamage) {
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
        this.attackDamage = attackDamage;
    }

    /** @return whether the kind is undead (burns in daylight, the historical rule). */
    public boolean undead() {
        return this == ZOMBIE || this == SKELETON;
    }

    /**
     * @return the endermite kind (EndermiteEntity's own family — the
     * lifetime despawn clock's gate).
     */
    public boolean endermiteFamily() {
        return this == ENDERMITE;
    }

    /**
     * @return whether the kind carries the reference's
     * {@code immuneToFire} — the fire block never ignites it, the fire
     * tick never damages it (the blaze, the magma cube, the ghast; the
     * zombie pigman joins with its own slice).
     */
    public boolean fireImmune() {
        return this == BLAZE || this == MAGMA_CUBE || this == GHAST || this == ZOMBIE_PIGMAN;
    }

    /** @return whether the kind rides the slime's size/hop/split family. */
    public boolean slimeFamily() {
        return this == SLIME || this == MAGMA_CUBE;
    }

    /**
     * The reference's {@code getMobType} damage category — the number the
     * damage-enchantment family reads ({@code MobType.UNDEFINED}/{@code
     * UNDEAD}/{@code ARTHROPODS}): Smite's 2.5/level lands on the undead
     * kinds, Bane of Arthropods' on the spider. Every other registered kind
     * is {@code UNDEFINED} (0) in the reference — horses, villagers and the
     * animals carry no category.
     */
    public int damageCategory() {
        return switch (this) {
            case ZOMBIE, SKELETON -> 1; // reference MobType.UNDEAD
            case SPIDER, CAVE_SPIDER, ENDERMITE, SILVERFISH -> 2; // reference MobType.ARTHROPODS
            default -> 0;               // reference MobType.UNDEFINED
        };
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
