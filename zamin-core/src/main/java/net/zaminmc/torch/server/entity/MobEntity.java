package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.entity.EntitySnapshot;
import net.zaminmc.torch.entity.LivingEntity;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;

import java.util.Objects;
import java.util.Random;

/**
 * One living mob in the world: identity, body, mind and the timers that shape
 * its behavior. The simulation owns it; the wire only observes.
 *
 * <p>AI is a small vanilla-style goal set, deterministic under seeded
 * random:</p>
 * <ul>
 *   <li><b>Passive kinds</b> wander (idle, then pick a direction and walk),
 *       glance at nearby players while idle (the historical LookAtPlayer),
 *       and panic when hurt. Sheep additionally carry a wool coat a player
 *       can shear off; it regrows on a timer.</li>
 *   <li><b>Zombies</b> chase the nearest player within their aggro range and
 *       attack in melee with a cooldown.</li>
 *   <li><b>Skeletons</b> hold a shooting band: they close when the target is
 *       far, back away when crowded, and loose an arrow when the line of
 *       sight is clear and the bow is off cooldown.</li>
 *   <li><b>Creepers</b> chase; at arm's length they stop and prime, and
 *       after the historical 30-tick fuse they detonate (the manager hears
 *       it and removes them). A target who retreats past the abort radius
 *       defuses the creeper.</li>
 *   <li><b>Spiders</b> hunt like zombies but only in the dark — in daylight
 *       they are neutral wanderers.</li>
 * </ul>
 *
 * <p>Walking mobs steer: a blocked walker picks a sideways detour heading
 * for a handful of ticks before re-choosing (the light-touch alternative to
 * full pathfinding — enough to slide along walls and around corners). Fluid
 * slows and buoys the body. Physics reuses the item-entity model (gravity,
 * drag, epsilon ground snap); horizontal motion is direct walk-integration,
 * not impulse physics, and stays marked for the physics slice.</p>
 */
public final class MobEntity implements LivingEntity {

    /** Historical gravity, shared with items (physics slice will unify). */
    public static final double GRAVITY_PER_TICK = 0.04;
    /** The 1.8 EntityLiving hurt animation window (red flash client-side). */
    public static final int HURT_FLASH_TICKS = 10;
    /** The 1.8 death animation: the body lies for 20 ticks, then loot + removal. */
    public static final int DEATH_TICKS = 20;
    /** Panic run length after being hurt (historical EntityAIPanic window). */
    public static final int PANIC_TICKS = 100;
    /** Idle look-around length before the wander roll re-fires. */
    public static final int IDLE_TICKS = 60;
    /** Wander leg length (walking one direction, then the roll re-fires). */
    public static final int WANDER_TICKS = 80;
    /** Hostile aggro radius in blocks (the historical follow range). */
    public static final double AGGRO_RANGE = 16.0;
    /** Zombie melee reach in blocks (horizontal, center to center-ish). */
    public static final double MELEE_ATTACK_RANGE = 1.6;
    /** Vertical spread the melee attack tolerates. */
    public static final double MELEE_ATTACK_VERTICAL_RANGE = 2.0;
    /** Historical easy-difficulty melee damage of the undead. */
    public static final float MELEE_ATTACK_DAMAGE = 2.0f;
    /** Melee cooldown in ticks (historical attack delay). */
    public static final int MELEE_ATTACK_COOLDOWN = 20;
    /** Skeleton shooting band: closer than this it backs away. */
    public static final double SKELETON_TOO_CLOSE = 6.0;
    /** Skeleton shooting band: farther than this it closes in. */
    public static final double SKELETON_TOO_FAR = 12.0;
    /** Skeleton maximum shot range (the historical bow reach). */
    public static final double SKELETON_SHOOT_RANGE = 15.0;
    /** Skeleton bow cooldown in ticks (the historical 60-tick attack delay). */
    public static final int SKELETON_ATTACK_COOLDOWN = 60;
    /** Skeleton arrow launch speed (blocks per tick). */
    public static final double SKELETON_ARROW_SPEED = 1.8;
    /** Creeper ignition distance: inside this it stops and primes. */
    public static final double CREEPER_PRIME_RANGE = 1.8;
    /** Creeper prime vertical tolerance. */
    public static final double CREEPER_PRIME_VERTICAL_RANGE = 2.0;
    /** Creeper abort radius: the target escaping this far defuses the fuse. */
    public static final double CREEPER_ABORT_RANGE = 3.5;
    /** The historical 30-tick fuse (EntityCreeper fuse, 1.5 seconds). */
    public static final int CREEPER_FUSE_TICKS = 30;
    /** Sheep wool regrow window (5-10 minutes, the historical grass-eat loop). */
    public static final int SHEEP_REGROW_MIN_TICKS = 6000;
    public static final int SHEEP_REGROW_SPREAD_TICKS = 6000;
    /** Sidestep detour length in ticks (the wall-slide steering window). */
    public static final int DETOUR_TICKS = 20;
    /** Panic speed multiplier over the kind's walk speed. */
    public static final double PANIC_SPEED_MULTIPLIER = 1.8;
    /** Position epsilon so ground queries see the block *below* the feet. */
    private static final double GROUND_EPSILON = 1.0E-7;
    /**
     * The vanilla kill plane (1.8.8 living entities): below it the
     * out-of-world damage consumes the body. Between y=0 and here the body
     * simply falls through the void (no solid block exists below the world).
     */
    public static final double VOID_KILL_Y = -64.0;
    /** The historical DamageSource.outOfWorld rate: four damage (two hearts) per tick. */
    public static final float VOID_DAMAGE_PER_TICK = 4.0f;
    /** The ignite duration standing in fire arms (the historical 8 s). */
    public static final int FIRE_TICKS = 160;
    /** The historical burn rate: one damage per second while on fire. */
    public static final float FIRE_DAMAGE_PER_SECOND = 1.0f;

    /**
     * Minimal world query the mob needs (tick-thread context only). The
     * line and fluid queries default to open/empty so tests and simple
     * worlds only implement what they use.
     */
    public interface WorldQuery {
        /** @return whether the block containing this point is solid. */
        boolean isSolid(double x, double y, double z);

        /**
         * The shape-aware point test (the collision-shape slice): whether
         * the point lies inside the collision shape of its cell — the
         * default folds to the boolean world so stub worlds keep their
         * semantics.
         */
        default boolean isSolidAt(double x, double y, double z) {
            return isSolid(x, y, z);
        }

        /**
         * The top surface of the collision shape in the point's cell (the
         * step/ground target); the default mirrors the boolean world's
         * full-cube snap. Non-solid cells answer negative infinity.
         */
        default double supportY(double x, double y, double z) {
            return isSolid(x, y, z) ? Math.floor(y) + 1.0 : Double.NEGATIVE_INFINITY;
        }

        /** @return the nearest playing player's position within {@code range} blocks, or null. */
        Position nearestPlayer(double x, double y, double z, double range);

        /**
         * @return whether nothing solid blocks the straight segment between
         * the two eye points (the shooter's line-of-sight test).
         */
        default boolean clearLine(Position from, Position to) {
            return true;
        }

        /** @return whether the block containing this point is a fluid. */
        default boolean inFluid(double x, double y, double z) {
            return false;
        }

        /**
         * The sky light level of the cell (0..15, the enderman's daylight
         * exposure read); the stub worlds answer full bright — the real
         * query maps the world's sky light.
         */
        default int skyLight(int x, int y, int z) {
            return 15;
        }

        /** @return whether the block containing this point is lava. */
        default boolean inLava(double x, double y, double z) {
            return false;
        }

        /** @return whether the block containing this point is fire. */
        default boolean inFire(double x, double y, double z) {
            return false;
        }

        /**
         * The {@code mobGriefing} rule's view (Slice 24a — the reference
         * reads it at the mob's own world: the enderman's carry pair, the
         * rabbit's garden raid, the farmland trample, the creeper's
         * block-damaging blast). The stub worlds answer the default-true
         * stand-in (the rule's own default view); the engine's query
         * overrides with the live table read.
         */
        default boolean mobGriefing() {
            return true;
        }

        /**
         * The dimension context switch (Slice 25d — the reference's
         * Entity.world: every body reads its own world). The shared query
         * flips its active dimension before each body's walk; the stub
         * worlds keep the single-dimension default.
         */
        default void setActiveDimension(int dimension) {
        }

        /**
         * Whether the cell is iron bars or a bed (Slice 25a — the zombie
         * villager's cure accelerator, ZombieEntity
         * .getTicksUntilConversionDecrement lines 549-559: the 1%-roll scan
         * counts IRON_BARS and BED cells). The stub worlds answer false;
         * the engine's query maps the live block.
         */
        default boolean isIronBarsOrBed(int x, int y, int z) {
            return false;
        }

        /** @return whether the point's block (or the one below) is a cactus. */
        default boolean touchingCactus(double x, double y, double z) {
            return false;
        }

        /**
         * The path scanner's material for the cell (the vanilla
         * {@code getMaterial}/{@code canWalkThrough} pair the
         * WalkNodeEvaluator branches on). The stub worlds derive it from
         * the point solidity at the cell center; the real query maps the
         * actual block through {@code PathBlocks}.
         */
        default net.zaminmc.torch.server.entity.ai.pathing.CellMaterial pathMaterialAt(int x, int y, int z) {
            return isSolid(x + 0.5, y + 0.5, z + 0.5)
                    ? net.zaminmc.torch.server.entity.ai.pathing.CellMaterial.SOLID
                    : net.zaminmc.torch.server.entity.ai.pathing.CellMaterial.AIR;
        }

        /**
         * The nearest player's live view within {@code range} — the
         * tempt/beg family's read (TemptGoal's {@code getNearestPlayer(mob,
         * 10.0)} + {@code getItemInHand()}). The default answers null so
         * stub worlds keep their no-player semantics: a mind without the
         * view never tempts and never begs.
         */
        default PlayerView nearestPlayerView(double x, double y, double z, double range) {
            return null;
        }

        /**
         * The nearest live mob of the kind within {@code range} — the
         * mob-vs-mob target family's read (ActiveTargetGoal's scan and
         * FleeEntityGoal's scare range). The default answers null so stub
         * worlds carry no mob-vs-mob mind.
         */
        default MobEntity nearestMobOfKind(double x, double y, double z, double range, MobType kind) {
            return null;
        }

        /**
         * The nearest live HOSTILE mob within {@code range} — the golem
         * family's read (TrackIronGolemTargetGoal + the snow golem's
         * ActiveTargetGoal over Monster.VISIBLE_MONSTER_FILTER). The
         * default answers null so stub worlds carry no golem mind.
         */
        default MobEntity nearestHostileMob(double x, double y, double z, double range) {
            return null;
        }

        /**
         * Whether the position's biome runs hot (the reference's
         * temperature > 1.0 — the snow golem's melt arm). The default
         * answers false so stub worlds stay temperate.
         */
        default boolean biomeHotAt(double x, double y, double z) {
            return false;
        }

        /**
         * The block identifier of the cell (Slice 18c — the silverfish's
         * InfestStone roll reads the adjacent block's identity). The
         * default answers null so stub worlds never host the infestation;
         * the real query maps the world's own block read.
         */
        default net.zaminmc.torch.util.Identifier blockIdAt(int x, int y, int z) {
            return null;
        }
    }

    /**
     * The player view the mind's awareness goals read (TemptGoal's
     * {@code player} reference): the live position and rotation every tick
     * (the scared-by-movement check compares them against the last seen
     * sample) plus the held item's identity (the tempt and the beg gates).
     */
    public interface PlayerView {
        /** The player's live position. */
        Position position();

        /** The player's live yaw (the scared rotation check). */
        float yaw();

        /** The player's live pitch (the scared rotation check). */
        float pitch();

        /** The held item's identifier, or null when the hand is empty. */
        String heldItem();

        /**
         * The helmet slot's identifier, or null when bare (Slice 19d —
         * the enderman's stare gate: a carved pumpkin blinds the stare
         * check, EndermanEntity.isLookedAtBy's armor[3] read). The
         * default answers null so stub worlds keep their no-armor
         * semantics.
         */
        default String helmetItem() {
            return null;
        }
    }

    /** What the mob is doing (the vanilla-style goal set of this slice). */
    public enum Mode {
        IDLE,
        WANDER,
        PANIC,
        CHASE,
        /** Skeleton: standing in its shooting band, facing the target. */
        STRAFE,
        /** Creeper: primed, fuse burning down. */
        FUSE
    }

    private final int entityId;
    private final MobType type;
    private final Random random;
    private final WorldQuery world;

    private Position position;
    private double velocityX;
    private double velocityY;
    private double velocityZ;
    private float yaw;
    private float headYaw;
    private float health;
    private boolean onGround;

    private Mode mode = Mode.IDLE;
    private int modeTicks;
    // The path-follow state (the vanilla navigation port): the route lives
    // in the navigation (Path + the walking index), the feet walk the
    // heading it reports, the repath budget re-arms on both outcomes.
    private final net.zaminmc.torch.server.entity.ai.pathing.MobNavigation navigation;
    private int repathCooldown;
    private float pathYaw;
    private boolean pathYawValid;
    private int panicTicks;
    private int hurtFlash;
    private int attackCooldown;
    private int idleSoundTimer;
    private boolean dead;
    private int deathTicks;
    /** The killer's Looting level (the reference reads it at death from the killer entity). */
    private int lootingLevel;

    // Steering: the wall-slide detour (a blocked walker slides along the wall
    // for a few ticks instead of grinding into it).
    private float detourYaw;
    private int detourTicks;

    // Sheep coat.
    private boolean sheared;
    private int regrowTimer;

    // Ranged skeleton: a shot armed by the mind, consumed by the manager.
    private boolean pendingRangedShot;
    private Position rangedTarget;

    // Primed creeper: the mind arms, the manager detonates and removes.
    private boolean pendingExplosion;

    // The fire clock: armed by fire contact (and the dawn sun for the
    // undead), deals the historical 1 damage per second while burning.
    private int fireTicks;
    private int fireDamageTimer;
    // The cactus's contact clock (the historical 1 damage per second).
    private int cactusDamageTimer;

    // The goal layer (the community PathfinderGoal architecture).
    private final GoalSelector goals = MobGoals.standard();
    // The registration surface built on first use (the public goals() view).
    private net.zaminmc.torch.entity.ai.GoalRegistration goalRegistration;
    // The owner-lane admission for mind mutations (wired by the manager at
    // spawn; null in bare harnesses — the caller then owns the context).
    private java.util.function.Consumer<Runnable> goalAdmission;
    private Position visibleTarget;
    private boolean targetHuntable;

    // ------------------------------------------------ mounts (the horse/pig slice)

    /** The horse flag bits (the 1.8 DataWatcher index-16 Int, ProtocolSupport/Glowstone-verified). */
    public static final int HORSE_FLAG_TAMED = 0x02;
    public static final int HORSE_FLAG_SADDLED = 0x04;
    public static final int HORSE_FLAG_EATING = 0x20;
    public static final int HORSE_FLAG_REARING = 0x40;
    public static final int HORSE_FLAG_MOUTH_OPEN = 0x80;

    /** Horse subtypes (the 1.8 DataWatcher index-19 Byte): horse, donkey, mule. */
    public static final int HORSE_SUBTYPE_HORSE = 0;
    public static final int HORSE_SUBTYPE_DONKEY = 1;
    public static final int HORSE_SUBTYPE_MULE = 2;

    /** Armor rows (the 1.8 DataWatcher index-22 Int): none, iron, gold, diamond. */
    public static final int HORSE_ARMOR_NONE = 0;
    public static final int HORSE_ARMOR_IRON = 1;
    public static final int HORSE_ARMOR_GOLD = 2;
    public static final int HORSE_ARMOR_DIAMOND = 3;

    /** Temper gained per mount attempt on an untamed horse (the historical +5). */
    public static final int TEMPER_PER_ATTEMPT = 5;
    /** Temper the common foods add per feeding (sugar/wheat/apple band). */
    public static final int TEMPER_PER_FEED = 3;
    /** Ticks a bucking horse keeps its rider before the throw (the rear-and-throw feel). */
    public static final int BUCK_THROW_TICKS = 20;
    /** Degrees per tick a mount turns at full sideways input (the 1.8 A/D steering). */
    public static final float RIDDEN_TURN_RATE = 3.5f;
    /** Controlled horse forward speed in blocks/tick (the canter). */
    public static final double HORSE_RIDE_SPEED = 0.115;
    /** Controlled pig forward speed in blocks/tick (slower than the horse). */
    public static final double PIG_RIDE_SPEED = 0.07;
    /** Jump impulse of a ridden horse (the ~2-block hop). */
    public static final double HORSE_JUMP_IMPULSE = 0.48;
    /** Jump impulse of a ridden pig (the small hop). */
    public static final double PIG_JUMP_IMPULSE = 0.30;
    /** Ticks between jumps of a ridden mount (the historical cooldown). */
    public static final int RIDE_JUMP_COOLDOWN = 10;
    /** Rider seat height above the mount's feet (the saddle top). */
    public static final double HORSE_SEAT_HEIGHT = 1.12;
    public static final double PIG_SEAT_HEIGHT = 0.55;

    /** The outcome of a mount attempt on an untamed horse (the vanilla temper flow). */
    public enum MountAttempt { BUCKED, TAMED, MOUNTED }

    private int horseSubtype = HORSE_SUBTYPE_HORSE;
    private int variant;
    private int horseFlags;
    private int armorType = HORSE_ARMOR_NONE;
    private int temper;
    private String ownerName = "";
    private int riderId = -1;
    private float steerSideways;
    private float steerForward;
    private boolean steerJump;
    private boolean pigSaddled;
    private int jumpCooldown;
    private int buckTicks;
    private boolean pendingBuckThrow;
    private int eatingTicks;

    // The breeding state (the vanilla AnimalEntity age/love) and the donkey
    // chest: breedingAge < 0 grows up one tick at a time to 0 (the baby),
    // > 0 is the cooldown after breeding; love runs the historical 600
    // ticks; the chest rides horse flag 0x08 and holds the 15-slot grid.
    private int breedingAge;
    private int loveTicks;
    private int lovePlayerId = -1;
    private int breedTimer;
    private boolean chested;
    private net.zaminmc.torch.item.ItemStack[] chestSlots;

    // The leash (Slice 11c — the reference's own MobEntity fields): the
    // held flag + the holder, one of the two forms — a player's hand or
    // a fence's knot. The holder never survives its own removal (the
    // tick walk's holder-gone arm).
    private boolean leashed;
    private net.zaminmc.torch.server.player.PlayerSession leashHolderPlayer;
    private net.zaminmc.torch.server.leash.LeashKnot leashHolderKnot;

    // The despawn state (Slice 11d — the reference's own fields): the
    // persistent refuse every despawn (the leashed family), the far
    // window counts up while no player stands within 32 blocks.
    private boolean persistent;
    private int farFromPlayerTicks;

    // The zombie villager's conversion state (Slice 25a — ZombieEntity's
    // own fields): the villager skin flag (the synced slot 13's byte) and
    // the cure's countdown (ticksUntilConversion; -1 = not converting —
    // the synced slot 14's byte answers that). The completing tick's flag
    // hands the conversion to the manager's own walk.
    private boolean zombieVillager;
    private int ticksUntilConversion = -1;
    private boolean pendingZombieConversionComplete;

    /**
     * The body's dimension (Slice 25d — the reference's Entity.dimension:
     * 0 the overworld, -1 the nether). The shared world query flips to
     * the body's own world before every walk (setActiveDimension) — the
     * nether mobs' physics, scans, and spawns read the nether's blocks.
     */
    private int dimension;

    /** @return the dimension the body lives in (0 overworld, -1 nether). */
    public int dimension() {
        return dimension;
    }

    /** Sets the body's dimension (the spawn walks and the restore stamp it). */
    public void setDimension(int dimension) {
        this.dimension = dimension;
    }

    /** The despawn's persistence flag (the leashed family and the tamed tameables never leave). */
    public boolean persistent() {
        return persistent || leashed || (isTameableKind() && wolfTamed());
    }

    /**
     * The reference's {@code canDespawn()} — whether the despawn rules
     * apply at all. The monsters answer true; the animals false
     * (AnimalEntity's own override — a penned cow stays penned); the wolf
     * answers true only UNTAMED and past 2400 ticks (its own override —
     * a lone wild wolf moves on after two minutes, a tamed one never
     * leaves its owner behind).
     */
    public boolean canDespawn() {
        // ZombieEntity.canDespawn (line 511): the converting body never
        // leaves — the cure's countdown outlives every despawn window.
        if (type == MobType.ZOMBIE && zombieConverting()) {
            return false;
        }
        if (isTameableKind()) {
            return !wolfTamed() && wolfTicksExisted > 2400;
        }
        return type.hostile;
    }

    /** @return whether the kind carries TameableEntity's state family (the wolf and the ocelot). */
    private boolean isTameableKind() {
        return type == MobType.WOLF || type == MobType.OCELOT;
    }

    /** Marks the body persistent (named/bred mobs ride their own slices). */
    public void setPersistent(boolean persistent) {
        this.persistent = persistent;
    }

    // ---- the public LivingEntity handle (§27.5: the engine object IS the
    // handle — every call resolves to live authoritative state) ----------

    /** The manager's removal hook, set at spawn (the despawn path). */
    private Runnable removalHook;

    /** Called once by the owning manager at spawn. */
    public void setRemovalHook(Runnable removalHook) {
        this.removalHook = removalHook;
    }

    /**
     * The owner-lane admission for mind mutations (§27.6's explicit command
     * protocol): the manager wires the world's {@code execute} at spawn, so
     * goal registrations land between ticks regardless of the calling
     * thread. Null in bare harnesses — the caller then owns the context.
     */
    public void setGoalAdmission(java.util.function.Consumer<Runnable> admission) {
        this.goalAdmission = admission;
    }

    /** The wired admission (null when bare). */
    java.util.function.Consumer<Runnable> goalAdmission() {
        return goalAdmission;
    }

    /** The mind's selector (package view for the registration surface). */
    GoalSelector goalSelector() {
        return goals;
    }

    @Override
    public net.zaminmc.torch.entity.ai.GoalRegistration goals() {
        net.zaminmc.torch.entity.ai.GoalRegistration surface = goalRegistration;
        if (surface == null) {
            surface = GoalRegistrationSurface.of(this);
            goalRegistration = surface;
        }
        return surface;
    }

    @Override
    public net.zaminmc.torch.util.Identifier typeIdentifier() {
        return type.identifier();
    }

    @Override
    public Rotation rotation() {
        return new Rotation(yaw, 0.0f);
    }

    @Override
    public EntitySnapshot snapshot() {
        return new EntitySnapshot(entityId, type.identifier(), position, yaw,
                velocityX, velocityY, velocityZ, health(), maxHealth(), onGround(), dead());
    }

    @Override
    public void despawn() {
        Runnable hook = removalHook;
        if (hook != null) {
            hook.run();
        }
    }

    /** The far-from-player window (checkDespawn's own counter). */
    public int farFromPlayerTicks() {
        return farFromPlayerTicks;
    }

    /**
     * One tick of the despawn bookkeeping (serverTickAi line 442 +
     * checkDespawn's own resets, 1:1): the window counts up EVERY tick
     * unconditionally, then the persistent reset (line 419) and the
     * near-player reset (lines 433-434, inside 32 blocks) zero it. No
     * nearest player at all: the window keeps counting (the reference's
     * own behavior — a mob alone for a thousand ticks rolls the idle
     * despawn the instant a distant player appears).
     */
    public void tickDespawnWindow(boolean persistent, Double nearestSquared) {
        this.farFromPlayerTicks++; // serverTickAi line 442: unconditional
        if (persistent) {
            this.farFromPlayerTicks = 0; // line 419: the persistent reset
        } else if (nearestSquared != null && nearestSquared < 1024.0) {
            this.farFromPlayerTicks = 0; // lines 433-434: within 32 blocks
        }
    }

    // The breed plumbing: the manager's mate scanner (the vanilla
    // getEntitiesOfType scan), the goal's confirmed mate, and the heart
    // flags the manager converts into observer effects.
    private BreedFinder breedFinder;
    private MobEntity pendingBreedMate;
    private boolean pendingLoveBurst;
    private boolean pendingAmbientHearts;
    private boolean grewUpThisTick;

    /** The mate scanner ({@code World.getEntitiesOfType} in the goal). */
    public interface BreedFinder {
        MobEntity findMate(MobEntity seeker);
    }

    // ------------------------------------------------ the villager's trade state

    /** The villager's profession (the DataWatcher index-16 Int, 0-4). */
    private int profession;
    /**
     * The villager's stock offers (the trade list the client renders) —
     * grown by the levelUp walk, one level's rows at a time.
     */
    private final java.util.List<TradeOffer> villagerStock = new java.util.ArrayList<>();
    /** The use counters, parallel to the stock (the grey-out budget). */
    private int[] offerUses = new int[0];
    /** The live use budgets, parallel to the stock (the level-up's own
     * extension — {@code increaseMaxUses} grows them past the fresh 7). */
    private int[] offerMaxUses = new int[0];
    /** The career (0 until the first open rolls it — VillagerEntity.career). */
    private int villagerCareer;
    /** The career's level (0 until the first open; grows on the level-ups). */
    private int villagerCareerLevel;
    /** The level-up countdown (mobAiTick's 40-tick walk, 0 idle). */
    private int villagerLevelUpCountdown;
    /** Whether the pending countdown ends in a level-up (the trade's flag). */
    private boolean villagerLevelUp;
    /** The mating willingness (the food arm's flag — Slice 21g's ledger). */
    private boolean villagerWilling;
    /** The villager's riches (the emerald payments absorbed, career XP). */
    private int villagerRiches;

    public MobEntity(int entityId, MobType type, Position position,
                     Random random, WorldQuery world) {
        this.entityId = entityId;
        this.type = Objects.requireNonNull(type, "type");
        this.position = Objects.requireNonNull(position, "position");
        this.random = Objects.requireNonNull(random, "random");
        this.world = Objects.requireNonNull(world, "world");
        this.health = type.maxHealth;
        this.navigation = new net.zaminmc.torch.server.entity.ai.pathing.MobNavigation(
                new PathBody(), new PathWorldView(), (float) AGGRO_RANGE);
        this.modeTicks = random.nextInt(IDLE_TICKS);
        this.idleSoundTimer = 160 + random.nextInt(320); // first chatter 8-24 s out
        // The herd look — the reference's own initialize dice
        // (HorseBaseEntity.initialize lines 1262-1280): 1 in 10 the donkey,
        // otherwise the horse coat = the color nextInt(7) | the marking
        // nextInt(5) << 8.
        if (type == MobType.HORSE) {
            this.horseSubtype = random.nextInt(10) == 0
                    ? HORSE_SUBTYPE_DONKEY : HORSE_SUBTYPE_HORSE;
            this.variant = random.nextInt(7) | random.nextInt(5) << 8;
        }
        // The villager's profession (VillagerEntity.initialize line 808:
        // {@code world.random.nextInt(5)}): the career and its stock roll
        // LAZILY at the first trade open (getOffers → levelUp — the
        // reference's own traderOffers == null gate).
        if (type == MobType.VILLAGER) {
            this.profession = random.nextInt(5);
        }
    }

    /**
     * The villager's career override (Slice 12b — the reference's
     * VillagePieces spawnVillagers → setProfession): the structure-spawned
     * villagers carry their building's profession (the blacksmith house's
     * 3, the priest's 2, ...); the career state resets so the first open
     * rolls against the new profession's table.
     */
    public void setVillagerProfession(int profession) {
        if (type != MobType.VILLAGER) {
            return;
        }
        this.profession = Math.max(0, Math.min(4, profession));
        villagerStock.clear();
        offerUses = new int[0];
        offerMaxUses = new int[0];
        villagerCareer = 0;
        villagerCareerLevel = 0;
    }

    public int entityId() {
        return entityId;
    }

    public MobType type() {
        return type;
    }

    public Position position() {
        return position;
    }

    public float yaw() {
        return yaw;
    }

    public float headYaw() {
        return headYaw;
    }

    public float health() {
        return health;
    }

    /** @return the kind's health ceiling ({@code getMaxHealth} — the tamed wolf's own 20). */
    public float maxHealth() {
        return type == MobType.WOLF && wolfTamed() ? 20.0f : type.maxHealth;
    }

    /** Restores health, clamped at the ceiling ({@code heal}). */
    public void heal(float amount) {
        if (amount <= 0 || dead) {
            return;
        }
        health = Math.min(maxHealth(), health + amount);
    }

    /**
     * The conversion's health carry ({@code setHealth(getHealth())} —
     * MooshroomEntity's shear-into-a-cow): the new body starts at the
     * old body's value, clamped into its own ceiling.
     */
    public void setHealthCarried(float value) {
        health = Math.max(0.0f, Math.min(maxHealth(), value));
    }

    public boolean onGround() {
        return onGround;
    }

    public Mode mode() {
        return mode;
    }

    public boolean dead() {
        return dead;
    }

    /** The killer's Looting level, read by the death loot walk (the reference's getLootingLevel at die()). */
    public int lootingLevel() {
        return lootingLevel;
    }

    /** Sets the killer's Looting level before the killing hurt lands. */
    public void setLootingLevel(int lootingLevel) {
        this.lootingLevel = Math.max(0, lootingLevel);
    }

    /** @return true while the death animation still plays (loot comes after). */
    public boolean dying() {
        return dead && deathTicks < DEATH_TICKS;
    }

    /** @return true once the death animation finished (removal is due). */
    public boolean deathAnimationFinished() {
        return dead && deathTicks >= DEATH_TICKS;
    }

    public boolean hurtFlashing() {
        return hurtFlash > 0;
    }

    public boolean sheared() {
        return sheared;
    }

    /** @return whether the body is in the primed state (the fuse burning). */
    public boolean fuseActive() {
        return mode == Mode.FUSE;
    }

    /** @return the armed shot's aim point (null when none is pending). */
    public Position rangedTarget() {
        return rangedTarget;
    }

    /** Clears the consumed shot's aim point (package-private: the manager calls). */
    void clearRangedTarget() {
        rangedTarget = null;
    }

    public double velocityX() {
        return velocityX;
    }

    public double velocityY() {
        return velocityY;
    }

    public double velocityZ() {
        return velocityZ;
    }

    /**
     * The reference's {@code addVelocity} walk on the raw motion — the melee
     * knockback-enchantment extra adds
     * {@code (-sin(yaw) * level * 0.5, 0.1, cos(yaw) * level * 0.5)} on top
     * of the base {@link #knockbackFrom} impulse, exactly once, after the
     * hit has landed (reference/1.8.8 PlayerEntity.attack lines 983-992).
     */
    public void addVelocity(double dx, double dy, double dz) {
        velocityX += dx;
        velocityY += dy;
        velocityZ += dz;
    }

    /** The test seam: a body already in motion (the knockback-residual cases). */
    void setVelocityForTest(double vx, double vy, double vz) {
        this.velocityX = vx;
        this.velocityY = vy;
        this.velocityZ = vz;
    }

    /**
     * The vanilla applyKnockback (reference/1.8.8 LivingEntity lines
     * 778-793) along the attacker's away-direction: the current motion
     * halves on every axis first (chained hits decay exactly as
     * historical), then the 0.4 impulse rides the normalized direction and
     * the rise is capped at 0.4. The yaw-derived direction is the unit
     * away-vector, so the impulse divides by 1 (the vanilla normalizes the
     * attacker-to-victim delta; a zero-distance delta would jitter, which a
     * swing yaw never produces). No knockback-resistance roll: the engine
     * registers no resistance attribute (the roll at 0 never fires).
     */
    public void knockbackFrom(double attackerYaw) {
        double radians = Math.toRadians(attackerYaw);
        double awayX = -Math.sin(radians);
        double awayZ = Math.cos(radians);
        velocityX = velocityX / 2.0 + awayX * 0.4;
        velocityY = Math.min(velocityY / 2.0 + 0.4, 0.4);
        velocityZ = velocityZ / 2.0 + awayZ * 0.4;
        onGround = false; // a primed creeper keeps priming; range governs the abort
    }

    /** Applies damage; clamps at zero and starts the death timer at zero health. */
    public void hurt(float amount) {
        if (amount <= 0 || dead) {
            return;
        }
        // The vanilla AnimalEntity.takeDamage arm: any hit clears the love
        // (the breeding window is fragile against mobs and players alike).
        if (isInLove()) {
            resetLove();
        }
        health = Math.max(0.0f, health - amount);
        hurtFlash = HURT_FLASH_TICKS;
        if (health <= 0) {
            dead = true;
            return;
        }
        if (!type.hostile && mode != Mode.PANIC) {
            panicTicks = PANIC_TICKS;
            mode = Mode.PANIC;
            modeTicks = 0;
        }
    }

    /**
     * Restores a persisted body: health from the save (clamped into the
     * historical 1..max range — a corrupt or stale value degrades to a fresh
     * body, never an immortal or dead one) and the saved facing.
     */
    public void restore(float restoredHealth, float restoredYaw) {
        this.health = Math.max(1.0f, Math.min(type.maxHealth, restoredHealth));
        this.yaw = restoredYaw;
        this.headYaw = restoredYaw;
    }

    /**
     * Shears the coat off a wooly kind. Returns whether wool was actually
     * cut (an already-bald sheep or a bald kind yields nothing); the coat
     * regrows after the historical 5-10 minute window.
     */
    public boolean shear() {
        if (!type.traits.shearable() || sheared || dead) {
            return false;
        }
        sheared = true;
        regrowTimer = SHEEP_REGROW_MIN_TICKS + random.nextInt(SHEEP_REGROW_SPREAD_TICKS);
        return true;
    }

    /**
     * Advances one tick: timers, mind (goal selection), then body (physics).
     * Day-neutral kinds only hunt while {@code night} is true. Returns true
     * when the fixed-point position or rotation changed, so the publisher
     * decides to sync.
     */
    public boolean tick(boolean night) {
        long before = fixedPoint();
        float beforeYaw = yaw;
        float beforeHeadYaw = headYaw;

        if (type == MobType.WOLF) {
            tickWolfClock();
            // mobTick's own clears: an angry wolf with no target stands down
            // (WolfEntity.mobTick lines 178-180) — the mob target family
            // (16b) keeps the posture through goalHasTarget.
            if (wolfAngry() && !goalHasTarget(night)) {
                setWolfAngry(false);
            }
        } else if (type == MobType.OCELOT) {
            tickWolfClock(); // the tameable family's shared clock
        } else if (type == MobType.ZOMBIE_PIGMAN) {
            tickPigmanAnger(); // the anger's decay + the delayed sound (16d)
        } else if (type == MobType.GUARDIAN) {
            tickGuardianClock(); // the elder's aura cadence (17a)
        } else if (type == MobType.ENDERMITE) {
            tickEndermiteClock(); // the lifetime despawn (17c)
        } else if (type == MobType.SNOW_GOLEM) {
            tickSnowGolemEnvironment(); // the melts (17d)
        } else if (type == MobType.SILVERFISH) {
            tickSilverfishClock(); // the wake-up + the infest rolls (18c)
        } else if (type == MobType.ZOMBIE && zombieConverting()) {
            tickZombieConversion(); // the cure's countdown (25a)
        }
        if (hurtFlash > 0) {
            hurtFlash--;
        }
        if (attackCooldown > 0) {
            attackCooldown--;
        }
        // The fire clock: the historical 1 damage per second while burning.
        // Dead bodies stop burning (the death animation freezes the body).
        // The immune body (Entity.update lines 322-327 + takeFireDamage
        // lines 739-743): the flames burn FOUR times as fast and never
        // wound — the blaze and the wither skeleton stand in their own
        // element (Slice 18d's reference-backed fix).
        if (!dead && fireTicks > 0) {
            fireTicks--;
            if (fireImmune()) {
                fireTicks -= 3; // the reference's own 4x drain
                if (fireTicks < 0) {
                    fireTicks = 0;
                }
                fireDamageTimer = 0; // no wound ever arms against the immune
            } else if (++fireDamageTimer >= 20) {
                fireDamageTimer = 0;
                hurt(FIRE_DAMAGE_PER_SECOND);
            }
        }
        if (sheared && regrowTimer > 0) {
            regrowTimer--;
            if (regrowTimer == 0) {
                sheared = false; // the coat returns; consumeRegrown() hears it
                regrownThisTick = true;
            }
        }
        // The player's hit window decays through the death animation too
        // (LivingEntity.update lines 254-255 — the walk runs before the
        // dead gate; dropRareItem reads the live remainder).
        tickPlayerHitTimer();

        if (dead) {
            deathTicks++;
            velocityX = 0;
            velocityZ = 0;
            velocityY = 0;
            return fixedPoint() != before || beforeYaw != yaw || beforeHeadYaw != headYaw;
        }

        // A ridden body parks its mind: the rider owns the reins (the vanilla
        // AI-suspension rule). The buck clock runs and the seat physics tick.
        if (hasRider()) {
            if (buckTicks > 0) {
                buckTicks--;
                if (buckTicks == 0) {
                    horseFlags &= ~HORSE_FLAG_REARING;
                    pendingBuckThrow = true;
                }
            }
            if (jumpCooldown > 0) {
                jumpCooldown--;
            }
            if (eatingTicks > 0 && --eatingTicks == 0) {
                horseFlags &= ~HORSE_FLAG_EATING;
            }
            tickRiddenBody();
            return fixedPoint() != before || beforeYaw != yaw || beforeHeadYaw != headYaw;
        }

        tickMind(night);
        tickBody();
        // The enderman's environmental arms (EndermanEntity.mobAiTick lines
        // 144-166): the water damage cadence and the daylight teleport.
        if (type == MobType.ENDERMAN) {
            tickEndermanEnvironment(night);
        }
        // The witch's defensive drink (WitchEntity.mobTick lines 96-131).
        if (type == MobType.WITCH) {
            tickWitchDrink(night);
        }
        return fixedPoint() != before || beforeYaw != yaw || beforeHeadYaw != headYaw;
    }

    /**
     * The enderman's environment walk: standing in fluid costs 1 health on
     * the hurt window's own ~10-tick cadence (the reference's
     * {@code takeDamage(DROWN, 1.0F)} riding the invulnerability frames),
     * and full daylight with sky exposure teleports the body away on the
     * reference's own rate — {@code nextFloat() * 30 < (brightness -
     * 0.4) * 2}.
     */
    private void tickEndermanEnvironment(boolean night) {
        if (world.inFluid(position.x(), position.y() + 0.2, position.z())) {
            if (--waterDamageTimer <= 0) {
                waterDamageTimer = HURT_WINDOW_TICKS;
                hurt(1.0f);
            }
        } else {
            waterDamageTimer = 1; // re-arms: the first wet tick hurts fast
        }
        // The anger decay (mobAiTick lines 152-154): a body angry without
        // a player's hand behind it calms on the 1-in-100 roll.
        tickEndermanCalm();
        if (!night && !dead) {
            float brightness = Math.min(1.0f, world.skyLight(
                    (int) Math.floor(position.x()),
                    (int) Math.min(255, Math.floor(position.y()) + 1),
                    (int) Math.floor(position.z())) / 15.0f);
            if (brightness > 0.5f
                    && random.nextFloat() * 30.0f < (brightness - 0.4f) * 2.0f) {
                // The daylight blink clears the whole provocation (lines
                // 159-162): the target, the anger, and the player's hand.
                setEndermanAngry(false);
                endermanAttackedByPlayer = false;
                teleportRandomly();
            }
        }
    }

    /** The enderman's water clock (re-armed per wet tick). */
    private int waterDamageTimer = 1;

    /** The enderman's far-target window (the ChasePlayerGoal's counter). */
    private int farFromTargetTicks;

    // ---- the enderman's provocation + carry (EndermanEntity, Slice 19d) ---

    /** The reference's HOLDABLE_BLOCKS (the static block lines 328-343). */
    private static final java.util.Set<String> ENDERMAN_HOLDABLE = java.util.Set.of(
            "grass", "dirt", "sand", "gravel",
            "yellow_flower", "red_flower",
            "brown_mushroom", "red_mushroom",
            "tnt", "cactus", "clay", "pumpkin", "melon_block", "mycelium");

    /** The carried block (null = air hands). */
    private net.zaminmc.torch.block.BlockType endermanCarried;
    /** The screaming flag (the synced slot 18's byte). */
    private boolean endermanAngry;
    /** Whether the anger came from a player's own hand (the decay gate). */
    private boolean endermanAttackedByPlayer;
    /** The stare's creepy countdown (LookAtPlayerGoal's 5-tick window). */
    private int endermanCreepyTicks;
    /** A pending stare scream (the manager drains it for the sound). */
    private boolean endermanStareScream;
    /** A pending pickup intent: the cell the enderman lifted a block from. */
    private int endermanPickupX;
    private int endermanPickupY;
    private int endermanPickupZ;
    private boolean endermanPickupPending;
    /** A pending place intent: the cell the carried block goes to. */
    private int endermanPlaceX;
    private int endermanPlaceY;
    private int endermanPlaceZ;
    private boolean endermanPlacePending;
    /** The wire's state-dirty flag (the carried block or the scream flipped). */
    private boolean endermanStateDirty;

    /** The carried block, or null for empty hands. */
    public net.zaminmc.torch.block.BlockType endermanCarried() {
        return endermanCarried;
    }

    /** The screaming flag (isAngry, the synced slot 18). */
    public boolean endermanAngry() {
        return endermanAngry;
    }

    /** Sets the screaming flag (the wire's slot-18 flip rides the drain). */
    public void setEndermanAngry(boolean angry) {
        if (endermanAngry != angry) {
            endermanAngry = angry;
            endermanStateDirty = true;
        }
    }

    /** The player-hand anger marker (the decay gate, takeDamage line 295). */
    public void setEndermanAttackedByPlayer(boolean attacked) {
        endermanAttackedByPlayer = attacked;
    }

    /** The manager's drain: the wire's state flip. */
    public boolean consumeEndermanStateDirty() {
        if (endermanStateDirty) {
            endermanStateDirty = false;
            return true;
        }
        return false;
    }

    /** The manager's drain: the pickup intent (the cell + the block). */
    public boolean consumeEndermanPickup() {
        if (endermanPickupPending) {
            endermanPickupPending = false;
            return true;
        }
        return false;
    }

    /** The pickup intent's cell x. */
    public int endermanPickupX() {
        return endermanPickupX;
    }

    /** The pickup intent's cell y. */
    public int endermanPickupY() {
        return endermanPickupY;
    }

    /** The pickup intent's cell z. */
    public int endermanPickupZ() {
        return endermanPickupZ;
    }

    /** The manager's drain: the place intent. */
    public boolean consumeEndermanPlace() {
        if (endermanPlacePending) {
            endermanPlacePending = false;
            return true;
        }
        return false;
    }

    /** The place intent's cell x. */
    public int endermanPlaceX() {
        return endermanPlaceX;
    }

    /** The place intent's cell y. */
    public int endermanPlaceY() {
        return endermanPlaceY;
    }

    /** The place intent's cell z. */
    public int endermanPlaceZ() {
        return endermanPlaceZ;
    }

    /** The manager's drain: the stare scream (the sound plays at the body). */
    public boolean consumeEndermanStareScream() {
        if (endermanStareScream) {
            endermanStareScream = false;
            return true;
        }
        return false;
    }

    /**
     * The manager-side pickup completion (the listener's write landed):
     * the carried block becomes the picked type and the wire's state
     * flips.
     */
    public void completeEndermanPickup(net.zaminmc.torch.block.BlockType picked) {
        endermanCarried = picked;
        endermanStateDirty = true;
    }

    /** The manager-side place completion (the listener's write landed). */
    public void completeEndermanPlace() {
        endermanCarried = null;
        endermanStateDirty = true;
    }

    /**
     * The enderman's provocation walk (LookAtPlayerGoal 1:1 adapted to the
     * engine's mind): the nearest player's stare — the look vector
     * crossing the body's mid-height band, the carved pumpkin blinding
     * the check, the line-of-sight gate — counts the 5-tick creepy window
     * down, then the scream, the anger, and the hunt. While the hunt runs
     * against a staring player inside 4 blocks the body blinks randomly
     * (LookAtPlayerGoal.tick lines 418-424).
     */
    private void tickEndermanProvocation(boolean hunting) {
        if (!endermanAngry) {
            PlayerView view = world.nearestPlayerView(
                    position.x(), position.y(), position.z(), ENDERMAN_STARE_RANGE);
            if (view != null && isLookedAtBy(view)) {
                if (endermanCreepyTicks == 0) {
                    endermanCreepyTicks = 5; // start()'s own value
                }
                if (--endermanCreepyTicks <= 0) {
                    endermanCreepyTicks = 0;
                    endermanStareScream = true;
                    setEndermanAngry(true);
                    endermanAttackedByPlayer = true; // shouldContinue's own write
                }
            } else {
                endermanCreepyTicks = 0; // stop(): the window re-arms fresh
            }
            return;
        }
        // The hunted stare: a target that keeps looking from inside 4
        // blocks teleports the body away (the reference's own escape).
        if (hunting) {
            PlayerView view = world.nearestPlayerView(
                    position.x(), position.y(), position.z(), ENDERMAN_STARE_RANGE);
            if (view != null && isLookedAtBy(view)
                    && position.distanceSquared(view.position()) < 16.0) {
                teleportRandomly();
            }
        }
    }

    /** The stare scan's range (the reference's FOLLOW_RANGE 64). */
    private static final double ENDERMAN_STARE_RANGE = 64.0;

    /**
     * The stare check (isLookedAtBy lines 106-118): the player's look
     * vector against the normalized body-to-eye direction, the 0.025 band
     * over the distance, the carved pumpkin blinding, the line of sight.
     */
    private boolean isLookedAtBy(PlayerView player) {
        String helmet = player.helmetItem();
        if (helmet != null && (helmet.equals("minecraft:pumpkin")
                || helmet.equals("pumpkin"))) {
            return false; // the carved pumpkin's veil
        }
        Position eye = player.position();
        double lookX = -Math.sin(Math.toRadians(player.yaw()))
                * Math.cos(Math.toRadians(player.pitch()));
        double lookY = -Math.sin(Math.toRadians(player.pitch()));
        double lookZ = Math.cos(Math.toRadians(player.yaw()))
                * Math.cos(Math.toRadians(player.pitch()));
        // The band's target: the body's middle (minY + height / 2).
        double dx = position.x() - eye.x();
        double dy = position.y() + type.height / 2.0 - (eye.y() + 1.62);
        double dz = position.z() - eye.z();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance < 1.0E-4) {
            return true; // inside the body: every look crosses it
        }
        double magnitude = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double dot = (lookX * dx + lookY * dy + lookZ * dz) / magnitude;
        if (dot <= 1.0 - 0.025 / distance) {
            return false;
        }
        // canSee: the eye-to-mid segment crosses nothing solid.
        return world.clearLine(eye,
                new Position(position.x(), position.y() + type.height / 2.0, position.z()));
    }

    /**
     * The carry arms (PickUpBlockGoal + PlaceBlockGoal 1:1): the
     * 1-in-20 pickup roll over the ±2/±3/±2 box around the body (the
     * holdable set, the world write riding the manager's drain), and the
     * 1-in-2000 place roll over the ±1/±2/±1 box (air at the cell, a
     * solid full cube below).
     */
    private void tickEndermanCarry() {
        // mobGriefing (Slice 24a — PickUpBlockGoal.canStart line 446 +
        // PlaceBlockGoal.canStart line 476): the whole carry pair gates
        // on the rule — a no-grief world's endermen neither pick up nor
        // put down blocks (the carried body keeps what it holds).
        if (!world.mobGriefing()) {
            return;
        }
        if (endermanCarried == null) {
            if (random.nextInt(20) != 0) {
                return;
            }
            int x = (int) Math.floor(position.x() - 2.0 + random.nextDouble() * 4.0);
            int y = (int) Math.floor(position.y() + random.nextDouble() * 3.0);
            int z = (int) Math.floor(position.z() - 2.0 + random.nextDouble() * 4.0);
            net.zaminmc.torch.util.Identifier block = world.blockIdAt(x, y, z);
            if (block != null && ENDERMAN_HOLDABLE.contains(block.value())) {
                endermanPickupX = x;
                endermanPickupY = y;
                endermanPickupZ = z;
                endermanPickupPending = true;
            }
        } else {
            if (random.nextInt(2000) != 0) {
                return;
            }
            int x = (int) Math.floor(position.x() - 1.0 + random.nextDouble() * 2.0);
            int y = (int) Math.floor(position.y() + random.nextDouble() * 2.0);
            int z = (int) Math.floor(position.z() - 1.0 + random.nextDouble() * 2.0);
            net.zaminmc.torch.util.Identifier at = world.blockIdAt(x, y, z);
            net.zaminmc.torch.util.Identifier below = world.blockIdAt(x, y - 1, z);
            boolean airAt = at == null || at.value().equals("air");
            boolean solidBelow = below != null && !below.value().equals("air")
                    && world.isSolid(x + 0.5, y - 0.5, z + 0.5);
            if (airAt && solidBelow) {
                endermanPlaceX = x;
                endermanPlaceY = y;
                endermanPlaceZ = z;
                endermanPlacePending = true;
            }
        }
    }

    /** The anger decay (mobAiTick lines 152-154): the 1-in-100 calm-down. */
    private void tickEndermanCalm() {
        if (endermanAngry && !endermanAttackedByPlayer && random.nextInt(100) == 0) {
            setEndermanAngry(false);
        }
    }

    // ---- the witch's drink (WitchEntity.mobTick lines 96-131) -----------

    /** The drinking potion's metadata (0 = not drinking; the drink is not splash). */
    private int witchDrink;
    /** The drink timer (the useDuration: 32 ticks). */
    private int witchDrinkTicks;

    /** Whether the witch is mid-drink (the aggressive flag). */
    public boolean witchDrinking() {
        return witchDrink > 0;
    }

    /**
     * The witch's defensive drink roll (the reference's own ladder): the
     * 15% water breathing when submerged, the 15% fire resistance when
     * burning, the 5% instant health when hurt, the 25% speed when the
     * target is far — each gated on the witch not already carrying the
     * effect (the reference's own {@code hasStatusEffect} clauses). The
     * engine's walk runs inside the body tick; the MANAGER applies the
     * effect when the timer completes (the potion system is engine-side).
     */
    private void tickWitchDrink(boolean night) {
        if (witchDrink > 0) {
            // The reference's own post-decrement (mobTick line 90): the
            // timer starts at 32 and completes when the check reads 0 —
            // the aggressive window spans 33 ticks, the effects landing
            // on the thirty-third.
            if (witchDrinkTicks-- <= 0) {
                completedWitchDrink = witchDrink;
                witchDrink = 0;
            }
        } else {
            int choice = 0;
            if (random.nextFloat() < 0.15F && world.inFluid(position.x(), position.y() + 0.2, position.z())
                    && !witchHasEffect(net.zaminmc.torch.server.potion.StatusEffects.WATER_BREATHING)) {
                choice = WITCH_WATER_BREATHING;
            } else if (random.nextFloat() < 0.15F && burning()
                    && !witchHasEffect(net.zaminmc.torch.server.potion.StatusEffects.FIRE_RESISTANCE)) {
                choice = WITCH_FIRE_RESISTANCE;
            } else if (random.nextFloat() < 0.05F && health < type.maxHealth) {
                choice = WITCH_INSTANT_HEALTH;
            } else if (random.nextFloat() < 0.25F && visibleTarget != null
                    && !witchHasEffect(net.zaminmc.torch.server.potion.StatusEffects.SPEED)
                    && position.distanceSquared(visibleTarget) > 121.0) {
                choice = WITCH_SPEED;
            }
            if (choice > 0) {
                witchDrink = choice;
                witchDrinkTicks = WITCH_DRINK_DURATION_TICKS;
            }
        }
        // The idle spell swirl (mobTick line 135): the 0.075% roll every
        // tick, drinking or not — the wire rides the Entity Event byte 15.
        if (random.nextFloat() < 0.00075F) {
            pendingWitchAura = true;
        }
    }

    /** Set when the witch's aura roll fired; consumed by the manager. */
    private boolean pendingWitchAura;

    /** @return whether the spell-swirl roll fired this tick (the event 15). */
    public boolean consumeWitchAura() {
        boolean value = pendingWitchAura;
        pendingWitchAura = false;
        return value;
    }

    /**
     * The witch's effect query (the reference's {@code hasStatusEffect}
     * clauses in the drink ladder): wired by the manager at spawn so the
     * roll sees the potion system's live store. Null (or a null effect
     * store) means the witch carries nothing.
     */
    private java.util.function.IntPredicate witchEffectQuery;

    /** Wires the drink ladder's effect query (the manager at spawn). */
    public void setWitchEffectQuery(java.util.function.IntPredicate query) {
        this.witchEffectQuery = query;
    }

    /** @return whether the witch carries the effect (no store = absent). */
    private boolean witchHasEffect(int effectId) {
        java.util.function.IntPredicate query = witchEffectQuery;
        return query != null && query.test(effectId);
    }

    // ---- the slime (SlimeEntity 1:1 — Slice 12k) -----------------------

    /**
     * The slime's size (the synced-data slot 16): 1, 2, or 4 (the
     * initialize's {@code 1 << nextInt(3)} family). The body's dimensions
     * ({@code 0.51000005 * size}), the max health ({@code size * size}),
     * the movement speed ({@code 0.2 + 0.1 * size}), the XP drop and the
     * contact damage all ride it (setSize lines 51-59).
     */
    private int slimeSize = 1;

    // ------------------------------------------------ the wolf (Slice 15d)

    /** TameableEntity's index-16 bits: sitting 0x1, angry (the wolf's own) 0x2, tamed 0x4. */
    private static final int WOLF_FLAG_SITTING = 0x1;
    private static final int WOLF_FLAG_ANGRY = 0x2;
    private static final int WOLF_FLAG_TAMED = 0x4;
    /** The wolf's own ticks-lived counter ({@code this.ticks} — the 2400-tick despawn gate). */
    private int wolfTicksExisted;
    /** The wolf's state byte on the wire's index 16 (sitting | angry &lt;&lt; 1 | tamed &lt;&lt; 2). */
    private int wolfFlags;
    /** The collar color's dye metadata (the wire's index 20 — RED at spawn, the reference's own default). */
    private byte wolfCollarColor = 1; // DyeColor.RED.getId() = 1
    /** The begging head-tilt (the wire's index 19 — the bone-holding player's gaze). */
    private boolean wolfBegging;
    /**
     * The sheep's fleece color as a DyeColor id (the wire's index-16 low
     * nibble — SheepEntity's {@code getColor().getId()}). WHITE (0) at the
     * reference's own register default; the domain differs from the collar
     * (the dye item's damage) and the dye stack (both metadata): a fleece
     * id of {@code c} is dye damage {@code 15 - c} and wool damage
     * {@code c} itself (BlockColored's damage = the DyeColor id).
     */
    private int sheepFleeceColor;

    // ---------------------------------------------- the mind's awareness (16a)

    /**
     * TemptGoal's live pair: whether the goal owns the body this tick (the
     * ocelot's tame gate reads it — {@code interactMob} only takes the fish
     * from a player the cat is currently tempted by) and the stop-penalty
     * delay ({@code 100} ticks, the reference's own re-arm).
     */
    private boolean temptActive;
    private int temptDelay;
    /**
     * TemptGoal's tracked sample: the player's last recorded position and
     * rotation. While the player stands within 6 blocks, any move beyond
     * 0.01 blocks or turn beyond 5 degrees scares the cat off the fish;
     * beyond 6 blocks the position keeps updating (the reference's own
     * asymmetric bookkeeping — the rotation sample updates every tick).
     */
    private double temptSampleX;
    private double temptSampleY;
    private double temptSampleZ;
    private float temptSampleYaw;
    private float temptSamplePitch;

    /**
     * The wolf's begging state (WolfBegGoal): the live window counts down
     * from {@code 40 + nextInt(40)} ticks while the attractive player stays
     * within the 8-block reach; the wire's index-19 flag rides it.
     */
    private int begTimer;

    /**
     * The ocelot's shared-flag postures (OcelotEntity.mobAiTick): the creep
     * (a tempt walk at the 0.6 goal speed sets sneaking) and the flee sprint
     * (the 1.33 close speed sets sprinting) — both ride the Entity flags
     * byte at index 0 (0x02 / 0x10).
     */
    private boolean postureSneaking;
    private boolean postureSprinting;

    /**
     * The goal-driven walk speed for this tick, as a multiple of the kind's
     * own base (the reference's goal {@code speed} parameter over the
     * movement attribute): the tempt's 0.6 on the ocelot's 0.3 base writes
     * 2.0; the flee's 0.8/1.33 write 2.67/4.43. NaN (the default) keeps
     * the mode-derived band (wander/panic/chase).
     */
    private double goalWalkSpeed = Double.NaN;

    // ------------------------------------- the mob-vs-mob targets (16b)

    /**
     * The single mob-vs-mob attack target (the reference's
     * {@code setAttackTarget} state — the targetSelector's whole family
     * writes it): the wolf's sheep/rabbit/skeleton hunts, the zombie's
     * villager hunt, and the revenge/owner arms all land here. The chase
     * mind runs against its position while it lives; the melee consume
     * damages it instead of the nearest player.
     */
    private MobEntity mobAttackTarget;
    /**
     * The skeleton's pending wolf scare (Slice 16b — computed before the
     * goal walk so the stroll yields through goalHasTarget): the nearest
     * live wolf within the 6-block flee range, or null.
     */
    private MobEntity pendingScareWolf;

    /**
     * The tamed body's attack damage (the reference's {@code setTamed}
     * recompute: 4.0 tamed over the 2.0 wild kind default — the per-instance
     * override the melee walk reads).
     */
    private float wolfAttackDamage;

    /** @return the wolf's raw index-16 state byte (the wire writer's view). */
    public int wolfFlagsRaw() {
        return wolfFlags;
    }

    /** @return whether the wolf is tamed (TameableEntity's bit). */
    public boolean wolfTamed() {
        return (wolfFlags & WOLF_FLAG_TAMED) != 0;
    }

    /** @return whether the wolf sits (the owner's empty-hand toggle). */
    public boolean wolfSitting() {
        return (wolfFlags & WOLF_FLAG_SITTING) != 0;
    }

    /** @return whether the wolf is angry (the untamed hostile posture). */
    public boolean wolfAngry() {
        return (wolfFlags & WOLF_FLAG_ANGRY) != 0;
    }

    /** Sets or clears the angry posture (WolfEntity.setAngry's own bit). */
    public void setWolfAngry(boolean angry) {
        if (angry) {
            wolfFlags |= WOLF_FLAG_ANGRY;
        } else {
            wolfFlags &= ~WOLF_FLAG_ANGRY;
        }
    }

    /** Toggles the sitting posture (the owner's empty-hand verb, SitGoal). */
    public void toggleWolfSitting() {
        wolfFlags ^= WOLF_FLAG_SITTING;
    }

    /** @return the collar color's dye metadata (index 20). */
    public int wolfCollarColor() {
        return wolfCollarColor & 0xFF;
    }

    /** Sets the collar color (the dye arm's write, index 20). */
    public void setWolfCollarColor(int dyeMetadata) {
        wolfCollarColor = (byte) (dyeMetadata & 0xF);
    }

    /**
     * @return the fleece color's DyeColor id (the index-16 low nibble, the
     *         wool stack's own damage — SheepEntity.getColor().getId()).
     */
    public int sheepFleeceColor() {
        return sheepFleeceColor & 0xF;
    }

    /**
     * Sets the fleece color (the dye arm's write, the index-16 nibble).
     * Takes the DyeColor id domain (0 = white, 15 = black).
     */
    public void setSheepFleeceColor(int dyeColorId) {
        sheepFleeceColor = dyeColorId & 0xF;
    }

    /** @return the begging head-tilt (index 19). */
    public boolean wolfBegging() {
        return wolfBegging;
    }

    /** Sets the begging head-tilt (WolfBegGoal's write, index 19). */
    public void setWolfBegging(boolean begging) {
        wolfBegging = begging;
    }

    /**
     * The tame lands (interactMob lines 343-351): the flag, the owner, the
     * health flip to the tamed maximum ({@code setTamed}'s own attribute
     * recompute — 20 over the wild 8), the attack-damage recompute (4.0),
     * and the sitting start.
     */
    public void tameWolf(String owner) {
        wolfFlags |= WOLF_FLAG_TAMED;
        if (owner != null && !owner.isEmpty()) {
            ownerName = owner;
        }
        wolfAttackDamage = 4.0f;
        this.health = 20.0f; // setHealth(20) under setTamed's raised ceiling
        wolfFlags |= WOLF_FLAG_SITTING; // sitGoal.setEnabledWithOwner(true)
    }

    /** The wolf's own tick clock ({@code this.ticks} — the tameable family's
     * 2400-tick despawn gate; the ocelot shares it). */
    void tickWolfClock() {
        wolfTicksExisted++;
    }

    /**
     * The cat's coat variant (OcelotEntity's own synced slot 18: 0 the
     * wild ocelot skin, 1-3 the tamed cat coats — the tame's own
     * {@code 1 + nextInt(3)} roll).
     */
    private int catVariant;

    /** @return the cat's coat variant byte (0 wild, 1-3 the tamed coats). */
    public int catVariant() {
        return catVariant;
    }

    /**
     * The ocelot's tame (interactMob lines 163-172): the flag, the owner, the
     * coat roll ({@code 1 + nextInt(3)}), and the sitting start — NO health
     * flip (the cat keeps its 10 ceiling; the wolf's 20 is its own).
     */
    public void tameOcelot(String owner, int variant) {
        wolfFlags |= WOLF_FLAG_TAMED;
        if (owner != null && !owner.isEmpty()) {
            ownerName = owner;
        }
        catVariant = variant;
        wolfFlags |= WOLF_FLAG_SITTING; // sitGoal.setEnabledWithOwner(true)
    }

    /** @return the slime's size byte (1, 2, or 4; 1 for every other kind). */
    public int slimeSize() {
        return type.slimeFamily() ? slimeSize : 1;
    }

    /**
     * @return the body's attack damage (the kind's ATTACK_DAMAGE
     * attribute — the enderman's 7.0 — or the slime's own size,
     * getDamageAmount line 215).
     */
    public float attackDamage() {
        if (type == MobType.RABBIT) {
            // tryDamage lines 255-262: the killer's 8.0 wound with the
            // mob.attack sound; the plain rabbit's 3.0 never arms (no
            // attack goal holds a target for it).
            return rabbitKiller() ? 8.0f : 3.0f;
        }
        if (type == MobType.WOLF && wolfTamed()) {
            return wolfAttackDamage; // setTamed's recompute: 4.0
        }
        if (type == MobType.SKELETON && skeletonWither) {
            // The nether initialize (lines 217-224): the wither variant's
            // own ATTACK_DAMAGE 4.0 over the archer's flat.
            return SKELETON_WITHER_DAMAGE;
        }
        if (type.slimeFamily()) {
            // getDamageAmount line 215: the size; MagmaCubeEntity's own
            // override adds 2 on top.
            return slimeSize + (type == MobType.MAGMA_CUBE ? 2 : 0);
        }
        if (type == MobType.GUARDIAN && guardianElder) {
            // setElder line 141: the elder's own ATTACK_DAMAGE 8.0 (the
            // laser's melee half reads the attribute live).
            return GUARDIAN_ELDER_DAMAGE;
        }
        if (type == MobType.IRON_GOLEM) {
            // tryDamage line 202: the heavy 7 + nextInt(15) roll — the
            // golem's own damage draw per swing (the manager's hurtByMob
            // site reads it through golemDamageRoll instead; this arm
            // answers the generic walks).
            return golemDamageRoll();
        }
        return type.attackDamage;
    }

    /**
     * Sets the slime's size and recomputes the body the reference's own
     * way (setSize lines 51-59): the dimensions, the max health
     * ({@code size * size}), and the full-heal — the split's children and
     * the spawn-time initialize both land through here.
     */
    public void setSlimeSize(int size) {
        if (!type.slimeFamily() || size < 1) {
            return;
        }
        this.slimeSize = size;
        this.health = size * size; // setHealth(getMaxHealth())
    }

    /** The spawn-time initialize (SlimeEntity.initialize lines 286-295):
     * {@code 1 << nextInt(3)} — the difficulty-dependent bump arm rides
     * the difficulty slice (the engine carries no local-difficulty
     * model; the fresh-world multiplier makes the bump rare anyway). */
    public void initializeSlimeSize() {
        setSlimeSize(1 << random.nextInt(3));
    }

    /** The slime's dynamic attack damage (getDamageAmount: the size; the
     * magma cube's own override adds 2). */
    public float slimeAttackDamage() {
        return slimeSize + (type == MobType.MAGMA_CUBE ? 2 : 0);
    }

    /**
     * The slime family's kind-dependent hurt/death sound (getHurtSound/
     * getDeathSound: {@code mob.slime.}/{@code mob.magmacube.} big over
     * size 1, small below).
     */
    public String slimeSoundName() {
        String prefix = type == MobType.MAGMA_CUBE ? "mob.magmacube." : "mob.slime.";
        return prefix + (slimeSize > 1 ? "big" : "small");
    }

    /** @return the body's hurt sound (the slime family's is size-dependent). */
    public String hurtSound() {
        if (type == MobType.GUARDIAN) {
            return guardianSoundKind() + "hit";
        }
        return type.slimeFamily() ? slimeSoundName() : type.hurtSound;
    }

    /** @return the body's death sound (the slime family's is size-dependent). */
    public String deathSound() {
        if (type == MobType.GUARDIAN) {
            return guardianSoundKind() + "death";
        }
        return type.slimeFamily() ? slimeSoundName() : type.deathSound;
    }

    /**
     * The guardian's sound family prefix (getHurtSound/getDeathSound lines
     * 210-226): the land pair when the body stands dry ({@code
     * mob.guardian.land.}), else the elder's own ({@code
     * mob.guardian.elder.}) or the plain water family.
     */
    private String guardianSoundKind() {
        if (!world.inFluid(position.x(), position.y() + 0.2, position.z())) {
            return "mob.guardian.land.";
        }
        return guardianElder ? "mob.guardian.elder." : "mob.guardian.";
    }

    /** The hop cadence counter (MovementControl's jumpCooldownTicks). */
    private int slimeHopCooldown = 1;
    /** The idle direction's re-roll counter (RandomDirectionGoal). */
    private int slimeDirectionCooldown = 1;
    /** The chase state driving the aggressive cadence (the AttackGoal). */
    private boolean slimeAggressive;
    /** The contact-damage cadence (the player's own hurt-resistance window). */
    private int slimeContactCooldown;
    /** The landing edge tracker (the wasOnGround flag). */
    private boolean wasSlimeGrounded = true;

    // ------------------------------------- the squid and the bat (16e)

    /** SquidEntity's SwimAroundGoal cadence: the 1-in-50 direction roll. */
    private static final int SQUID_DIRECTION_CHANCE = 50;
    /** The squid's tentacle-cycle speed band (1/(rand+1) * 0.2). */
    private float squidTentacleSpeed = 0.1f;
    /** The tentacle stroke's phase (the 2π walk). */
    private float squidTentacleMovement;
    /** The stroke's drive: 1.0 in the first half, decaying after. */
    private float squidSpeed;
    /** The swim direction target (the SwimAroundGoal's setTarget). */
    private float squidTargetX;
    private float squidTargetY;
    private float squidTargetZ;

    /**
     * The squid's mind (SwimAroundGoal): the 1-in-50 direction re-roll —
     * a random bearing's {@code cos * 0.2 / -0.1+rand*0.2 / sin * 0.2}
     * triple, or the stop target past the 100-tick despawn window.
     */
    private void tickSquidMind() {
        if (random.nextInt(SQUID_DIRECTION_CHANCE) == 0 || !hasSquidTarget()) {
            float bearing = random.nextFloat() * (float) Math.PI * 2.0f;
            squidTargetX = (float) Math.cos(bearing) * 0.2f;
            squidTargetY = -0.1f + random.nextFloat() * 0.2f;
            squidTargetZ = (float) Math.sin(bearing) * 0.2f;
        }
    }

    private boolean hasSquidTarget() {
        return squidTargetX != 0.0f || squidTargetY != 0.0f || squidTargetZ != 0.0f;
    }

    /**
     * The squid's body (SquidEntity.mobTick): the tentacle stroke phases
     * the drive — the first half of the 2π cycle swims at speed 1.0, the
     * second decays — and the velocity IS the direction target times the
     * drive (a direct set). Out of water the body sinks (velocityY -= 0.08
     * with the 0.98 drag, no horizontal).
     */
    private void tickSquidBody(boolean inFluid) {
        squidTentacleMovement += squidTentacleSpeed;
        if (squidTentacleMovement > Math.PI * 2) {
            squidTentacleMovement -= (float) (Math.PI * 2);
            if (random.nextInt(10) == 0) {
                squidTentacleSpeed = 1.0f / (random.nextFloat() + 1.0f) * 0.2f;
            }
        }
        if (inFluid) {
            if (squidTentacleMovement < Math.PI) {
                float phase = squidTentacleMovement / (float) Math.PI;
                if (phase > 0.75f) {
                    squidSpeed = 1.0f; // the power stroke
                }
            } else {
                squidSpeed *= 0.9f; // the recovery
            }
            velocityX = squidTargetX * squidSpeed;
            velocityY = squidTargetY * squidSpeed;
            velocityZ = squidTargetZ * squidSpeed;
        } else {
            velocityX = 0.0;
            velocityY = (velocityY - 0.08) * 0.98; // the stranded sink
            velocityZ = 0.0;
        }
    }

    /** The bat's roosting flag (BatEntity's synced slot 16, bit 0). */
    private boolean batRoosting = true;
    /** The bat's flight anchor (the re-rolled hangPos). */
    private double batHangX;
    private double batHangY;
    private double batHangZ;
    private boolean batHasHang;

    /** @return whether the bat hangs from a ceiling (the index-16 bit). */
    public boolean batRoosting() {
        return batRoosting;
    }

    /** Sets the roosting posture (the spawn starts hanging). */
    public void setBatRoosting(boolean roosting) {
        batRoosting = roosting;
    }

    /**
     * The bat's mind (BatEntity.mobAiTick): the roost holds while a solid
     * ceiling stays above and no player stands within 4 (the head glances
     * on the 1-in-200); the flight wanders toward the re-rolled hangPos
     * (±7/±6 cube, every 30 ticks or within 2 blocks), the velocity
     * lerping toward the signum band; the 1-in-100 re-roost on a solid
     * block above.
     */
    private void tickBatMind() {
        if (batRoosting) {
            boolean ceilingSolid = world.isSolidAt(position.x(), position.y() + type.height, position.z());
            if (!ceilingSolid) {
                batRoosting = false; // the 1015 event rides the sound slice
            } else if (random.nextInt(200) == 0) {
                headYaw = random.nextInt(360);
            }
            Position player = world.nearestPlayer(position.x(), position.y(), position.z(), 4.0);
            if (player != null) {
                batRoosting = false;
            }
            return;
        }
        if (!batHasHang || random.nextInt(30) == 0
                || (batHangX - position.x()) * (batHangX - position.x())
                        + (batHangY - position.y()) * (batHangY - position.y())
                        + (batHangZ - position.z()) * (batHangZ - position.z()) < 4.0) {
            batHangX = (int) position.x() + random.nextInt(7) - random.nextInt(7);
            batHangY = (int) position.y() + random.nextInt(6) - 2;
            batHangZ = (int) position.z() + random.nextInt(7) - random.nextInt(7);
            batHasHang = true;
        }
        double d0 = batHangX + 0.5 - position.x();
        double d1 = batHangY + 0.1 - position.y();
        double d2 = batHangZ + 0.5 - position.z();
        velocityX += (Math.signum(d0) * 0.5 - velocityX) * 0.1;
        velocityY += (Math.signum(d1) * 0.7 - velocityY) * 0.1;
        velocityZ += (Math.signum(d2) * 0.5 - velocityZ) * 0.1;
        yaw += Math.toDegrees(Math.atan2(velocityZ, velocityX)) - 90.0 - yaw;
        if (random.nextInt(100) == 0
                && world.isSolidAt(position.x(), position.y() + type.height, position.z())) {
            batRoosting = true; // the re-roost
        }
    }

    /**
     * The bat's body (BatEntity.tick): the roost freezes the velocity and
     * snaps the body to the ceiling (floor(y) + 1 - height); the flight
     * decays the vertical (0.6) with no gravity.
     */
    private void tickBatBody() {
        if (batRoosting) {
            velocityX = 0;
            velocityY = 0;
            velocityZ = 0;
            position = new Position(position.x(),
                    Math.floor(position.y()) + 1.0 - type.height, position.z());
            return;
        }
        velocityY *= 0.6;
        position = new Position(position.x() + velocityX, position.y() + velocityY,
                position.z() + velocityZ);
        onGround = false;
    }

    // ------------------------------------- the guardian (17a)

    /** The laser's charge time (getAttackChargeTime: the elder's own 60). */
    private static final int GUARDIAN_ATTACK_CHARGE_TICKS = 80;
    /** The elder's own charge (getAttackChargeTime's elder arm). */
    private static final int ELDER_ATTACK_CHARGE_TICKS = 60;
    /** The charge cycle's start offset (GuardianAttackEntityGoal.start: -10). */
    private static final int GUARDIAN_CHARGE_START = -10;
    /** The laser hit's magic base (tick line 517: the flat 1.0F). */
    private static final float GUARDIAN_MAGIC_BASE = 1.0f;
    /** The hard-difficulty magic bump (tick lines 509-512: +2.0F — the engine's walk adds it). */
    private static final float GUARDIAN_MAGIC_HARD_BONUS = 2.0f;
    /** The elder's own magic bump (tick lines 514-516: +2.0F). */
    private static final float GUARDIAN_MAGIC_ELDER_BONUS = 2.0f;
    /** The still body's spike reflect (takeDamage lines 422-429: 2.0F on the melee family). */
    private static final float GUARDIAN_THORNS_DAMAGE = 2.0f;
    /** The elder's hull (setElder line 139: 1.9975F on both axes). */
    private static final double GUARDIAN_ELDER_SIZE = 1.9975;
    /** The elder's health ceiling (setElder line 142: MAX_HEALTH 80). */
    private static final float GUARDIAN_ELDER_HEALTH = 80.0f;
    /** The elder's melee (setElder line 141: ATTACK_DAMAGE 8.0). */
    private static final float GUARDIAN_ELDER_DAMAGE = 8.0f;
    /** The elder's speed (setElder line 140: MOVEMENT_SPEED 0.3). */
    private static final double GUARDIAN_ELDER_WALK = 0.3;
    /** The elder's mining-fatigue aura (mobAiTick: every 1200 ticks, the body's own cadence). */
    private static final int GUARDIAN_ELDER_AURA_INTERVAL = 1200;
    /** The aura's reach (squared: 2500 = 50 blocks, squaredDistanceTo < 2500). */
    private static final double GUARDIAN_ELDER_AURA_RADIUS_SQUARED = 2500.0;
    /** The aura's own duration (6000 ticks of Fatigue III). */
    private static final int GUARDIAN_ELDER_AURA_DURATION = 6000;
    /** The aura's amplifier (the 1.8.8 elder's own III). */
    private static final int GUARDIAN_ELDER_AURA_AMPLIFIER = 2;
    /** The aura's re-application floor (the 1200-duration arm: only weaker rows re-land). */
    private static final int GUARDIAN_ELDER_AURA_REFRESH_BELOW = 1200;
    /** The target validator's beyond-the-flop gate (squaredDistanceTo > 9). */
    private static final double GUARDIAN_TARGET_MIN_SQUARED = 9.0;
    /** The wander cadence (WanderAroundGoal's interval 80 — the re-roll). */
    private static final int GUARDIAN_WANDER_INTERVAL = 80;
    /** The swim's velocity scale (moveRelative: updateVelocity(…, 0.1F)). */
    private static final double GUARDIAN_SWIM_SCALE = 0.1;
    /** The swim's per-tick decay (velocity *= 0.9). */
    private static final double GUARDIAN_SWIM_DECAY = 0.9;
    /** The idle sink (velocityY -= 0.005 when neither steering nor hunting). */
    private static final double GUARDIAN_IDLE_SINK = 0.005;
    /** The land flop's rise (mobTick line 324: velocityY += 0.5). */
    private static final double GUARDIAN_FLOP_RISE = 0.5;
    /** The land flop's horizontal band ((rand*2-1) * 0.4 per axis). */
    private static final double GUARDIAN_FLOP_BAND = 0.4;

    /** The elder flag (setElder — the monument's own three carry it). */
    private boolean guardianElder;
    /** The swim steering flag (GuardianMovementControl's moving — the wire's slot-16 bit 0x02, the thorns gate). */
    private boolean guardianMoving;
    /** The charge cycle ({@code Integer.MIN_VALUE} = idle; -10 start, 0 = the beam on, the charge time = the hit). */
    private int guardianChargeTicks = Integer.MIN_VALUE;
    /** The beam's live target id (the wire's slot 17; 0 = none — the manager resolves the session id). */
    private int guardianBeamTargetId;
    /** Set when the beam starts (the charge crosses 0); the manager resolves the id + the Event 21. */
    private boolean pendingGuardianBeamStart;
    /** Set when the laser's charge completes; the manager lands the magic + melee pair on the target. */
    private boolean pendingGuardianLaserHit;
    /** Set when the elder's aura cycle fires; the manager walks the players. */
    private boolean pendingGuardianAura;
    /** The wander's steering target (the re-rolled swim direction). */
    private double guardianSteerX;
    private double guardianSteerY;
    private double guardianSteerZ;
    private boolean guardianHasSteer;

    /** @return whether the body is the elder variant (the wire's slot-16 bit 0x04). */
    public boolean guardianElder() {
        return guardianElder;
    }

    /** @return the swim steering flag (the wire's slot-16 bit 0x02, the thorns gate). */
    public boolean guardianMoving() {
        return guardianMoving;
    }

    /** @return the beam's live target id (the wire's slot 17; 0 = none). */
    public int guardianBeamTargetId() {
        return guardianBeamTargetId;
    }

    /**
     * @return whether the charge window carries a live beam (the charge
     * crossed 0 — the wire's slot 17 carries the target's id until the
     * hit or the hunt's break clears it).
     */
    public boolean guardianBeamActive() {
        return guardianChargeTicks >= 0 && guardianChargeTicks < guardianChargeTime();
    }

    /** The manager's beam write (the session id the charge window carries). */
    public void setGuardianBeamTargetId(int targetId) {
        guardianBeamTargetId = targetId;
    }

    /** @return the laser's charge time (the elder's own 60). */
    public int guardianChargeTime() {
        return guardianElder ? ELDER_ATTACK_CHARGE_TICKS : GUARDIAN_ATTACK_CHARGE_TICKS;
    }

    /**
     * The elder flip (setElder lines 132-146): the 1.9975 hull, the
     * speed 0.3, the damage 8.0, the 80-health ceiling (healed here —
     * the reference's own {@code spawnElderGuardian} heals to the new
     * max), and the persistent never-despawn rule.
     */
    public void setGuardianElder(boolean elder) {
        guardianElder = elder;
        if (elder) {
            persistent = true; // setPersistent (the elder never despawns)
            health = GUARDIAN_ELDER_HEALTH;
        }
    }

    /**
     * @return the guardian's thorns verdict — a still body spikes its
     * melee attacker ({@code !isMoving() && !source.getMagic() &&
     * source.getSource() instanceof LivingEntity && !source.isExplosive()}).
     */
    public boolean guardianSpikesMeleeAttacker() {
        return type == MobType.GUARDIAN && !guardianMoving;
    }

    /** @return the spike reflect's damage (the flat 2.0F). */
    public float guardianThornsDamage() {
        return GUARDIAN_THORNS_DAMAGE;
    }

    /** @return and clears the beam-start verdict (the Event 21 site). */
    public boolean consumeGuardianBeamStart() {
        boolean value = pendingGuardianBeamStart;
        pendingGuardianBeamStart = false;
        return value;
    }

    /** @return and clears the completed-charge verdict (the laser hit). */
    public boolean consumeGuardianLaserHit() {
        boolean value = pendingGuardianLaserHit;
        pendingGuardianLaserHit = false;
        return value;
    }

    /** @return and clears the elder's aura verdict (the 1200-tick cadence). */
    public boolean consumeGuardianAura() {
        boolean value = pendingGuardianAura;
        pendingGuardianAura = false;
        return value;
    }

    /**
     * The guardian's own tick clock ({@code this.ticks} — the elder's aura
     * cadence: {@code (ticks + getNetworkId()) % 1200 == 0}, the per-body
     * offset the reference staggers the monuments' three by). The walk
     * itself — which players stand within the 50-block reach — is the
     * manager's (the session registry lives there).
     */
    void tickGuardianClock() {
        guardianTicks++;
        if (guardianElder && (guardianTicks + entityId) % GUARDIAN_ELDER_AURA_INTERVAL == 0) {
            pendingGuardianAura = true;
        }
    }

    /** The guardian's own ticks-lived counter (the aura's cadence input). */
    private int guardianTicks;

    // ------------------------------------- the endermite (17c)

    /** The pearl's passenger despawns after its own lifetime (EndermiteEntity
     * mobTick lines 120-127: the non-persistent clock, the 2400-tick wall). */
    private static final int ENDERMITE_LIFETIME_TICKS = 2400;

    /** The endermite's own ticks-lived counter (the lifetime clock). */
    private int endermiteTicks;

    /** Set when the lifetime wall crossed; the manager removes the body. */
    private boolean pendingEndermiteExpire;

    /**
     * The endermite's lifetime clock (mobTick lines 120-127): the
     * non-persistent body counts up to the 2400-tick wall — the removal
     * itself is the manager's (the tick walk's own removal path, no death
     * animation, no loot).
     */
    void tickEndermiteClock() {
        if (!persistent) {
            endermiteTicks++;
            if (endermiteTicks >= ENDERMITE_LIFETIME_TICKS) {
                pendingEndermiteExpire = true;
            }
        }
    }

    /** @return and clears the lifetime wall's verdict. */
    public boolean consumeEndermiteExpire() {
        boolean value = pendingEndermiteExpire;
        pendingEndermiteExpire = false;
        return value;
    }

    // ------------------------------------- the golem pair (17d)

    /** The iron golem's hunt range (GoToEntityTargetGoal's 32.0F). */
    private static final double GOLEM_HUNT_RANGE = 32.0;
    /** The iron golem's melee reach (the 1.4-wide body's swing band). */
    private static final double GOLEM_MELEE_RANGE = 2.6;
    /** The iron golem's heavy hit (tryDamage line 202: 7 + nextInt(15)). */
    private static final int GOLEM_DAMAGE_BASE = 7;
    private static final int GOLEM_DAMAGE_SPREAD = 15;
    /** The launch the landed golem hit gives (velocityY += 0.4F). */
    private static final double GOLEM_LAUNCH_Y = 0.4;
    /** The snow golem's volley cadence (ProjectileAttackGoal's interval 20). */
    private static final int SNOW_GOLEM_SHOT_INTERVAL = 20;
    /** The snow golem's volley range (ProjectileAttackGoal's 10.0F). */
    private static final double SNOW_GOLEM_RANGE = 10.0;
    /** The melt's cost per contact tick (mobTick: takeDamage 1.0F). */
    private static final float GOLEM_MELT_DAMAGE = 1.0f;

    /** The golem's live mob target (the golem family's own hunt state). */
    private MobEntity golemTarget;
    /** The snow golem's volley clock. */
    private int snowGolemShotCooldown;
    /** Set when the iron golem's swing lands this tick; consumed by the manager. */
    private boolean pendingGolemSwing;
    /** Set when the snow golem's volley fires; the manager resolves the target. */
    private boolean pendingSnowGolemShot;

    /**
     * The iron golem's mind (the goal ladder 1-8 + the target selector's
     * TrackIronGolemTargetGoal): the nearest hostile mob within 32 owns
     * the walk and the melee — the body never hunts players (the
     * canAttack gate) — else the stroll cycle holds.
     */
    private void tickIronGolemMind(boolean night) {
        golemTarget = world.nearestHostileMob(position.x(), position.y(), position.z(),
                GOLEM_HUNT_RANGE);
        if (golemTarget == null || golemTarget.dead()) {
            golemTarget = null;
            return; // the stroll cycle (the goal layer's default)
        }
        double dx = golemTarget.position().x() - position.x();
        double dy = golemTarget.position().y() - position.y();
        double dz = golemTarget.position().z() - position.z();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        yaw = angleTo(dx, dz);
        headYaw = yaw;
        if (horizontal > GOLEM_MELEE_RANGE) {
            mode = Mode.CHASE;
            tickPathFollowing(golemTarget.position());
        } else {
            mode = Mode.IDLE;
            clearPath();
            // MeleeAttackGoal's cadence: the swing lands when the cooldown
                       // allows — the heavy hit + the launch ride the manager.
            if (attackCooldown == 0) {
                attackCooldown = GOLEM_MELEE_COOLDOWN;
                pendingMeleeAttack = true;
                pendingGolemSwing = true;
            }
        }
    }

    /** The golem's own melee cadence (MeleeAttackGoal's 20-tick cycle). */
    private static final int GOLEM_MELEE_COOLDOWN = 20;

    /**
     * The snow golem's mind (ProjectileAttackGoal 1.25/20/10 + the
     * target selector's Monster filter): the nearest hostile within 10
     * takes the 20-tick snowball volley — the walk holds the distance
     * (the goal's 1.25 chase closes only past the range).
     */
    private void tickSnowGolemMind() {
        if (snowGolemShotCooldown > 0) {
            snowGolemShotCooldown--;
        }
        golemTarget = world.nearestHostileMob(position.x(), position.y(), position.z(),
                SNOW_GOLEM_RANGE);
        if (golemTarget == null || golemTarget.dead()) {
            golemTarget = null;
            return; // the plain stroll
        }
        double dx = golemTarget.position().x() - position.x();
        double dz = golemTarget.position().z() - position.z();
        yaw = angleTo(dx, dz);
        headYaw = yaw;
        if (snowGolemShotCooldown == 0) {
            snowGolemShotCooldown = SNOW_GOLEM_SHOT_INTERVAL;
            pendingSnowGolemShot = true;
        }
    }

    /**
     * The snow golem's environmental walk (mobTick lines 43-55): the water
     * contact melts a layer every tick ({@code DROWN} 1.0), the hot biome
     * the same ({@code ON_FIRE} — the engine's desert stand-in for the
     * reference's temperature > 1.0).
     */
    private void tickSnowGolemEnvironment() {
        if (world.inFluid(position.x(), position.y() + 0.2, position.z())) {
            hurt(GOLEM_MELT_DAMAGE); // isInWaterOrRain → DROWN 1.0F
        } else if (world.biomeHotAt(position.x(), position.y(), position.z())) {
            hurt(GOLEM_MELT_DAMAGE); // temperature > 1.0 → ON_FIRE 1.0F
        }
    }

    /** @return the golem's live mob target (the hunt's single write). */
    public MobEntity golemTarget() {
        return golemTarget;
    }

    // --------------------------------------------------------------
    // The blaze (Slice 18a — BlazeEntity 1:1)
    // --------------------------------------------------------------

    /** AttackGoal's step: 0 idle, 1 charging, 2-4 the volleys, the reset. */
    private int blazeStep;
    /** AttackGoal's ticks: the countdown between the steps. */
    private int blazeTicks;
    /** mobAiTick's eyeOffset (re-rolled every 100 ticks around 0.5). */
    private float blazeEyeOffset = 0.5F;
    /** mobAiTick's fireActivation: the eye-offset re-roll clock. */
    private int blazeFireActivation;
    /** The fire-active flag (BlazeEntity's synced slot 16, bit 0). */
    private boolean blazeFireActive;
    /** Set when a fireball volley fires this tick; the manager consumes. */
    private boolean pendingBlazeFireball;
    /** Set when the fire-active flag flipped; the manager consumes. */
    private boolean blazeFireDirty;

    /**
     * The blaze's own mind (BlazeEntity's goal set — no SwimGoal, no
     * panic, no shared stroll: the AttackGoal at 4 over the wander family,
     * so the custom mind preempts the standard goal layer): the reference's
     * exact cycle (AttackGoal.tick lines 202-255) — inside 4 blocks the
     * 20-tick melee with the movement follow, inside 256 the step cycle
     * (the 60-tick charge with the fire-up, the three fireball volleys 6
     * ticks apart, the 100-tick cooldown), beyond the plain follow walk.
     * The mobAiTick arms run first (lines 109-128): the water drown, the
     * eye-offset re-roll, and the hover rise toward a higher target.
     */
    private void tickBlazeMind(Position target, boolean canHunt) {
        // mobAiTick lines 110-112: the water contact drowns the body
        // (DROWN 1.0 per tick — the same shape the snow golem's melt
        // rides).
        if (world.inFluid(position.x(), position.y() + 0.2, position.z())) {
            hurt(1.0f);
        }

        // mobAiTick lines 115-119: the eye-offset re-roll every 100 ticks.
        if (--blazeFireActivation <= 0) {
            blazeFireActivation = 100;
            blazeEyeOffset = 0.5F + (float) random.nextGaussian() * 3.0F;
        }

        // mobAiTick lines 121-125: the hover rise — a target whose eye
        // band sits above the blaze's own (plus the drifting offset)
        // pulls the body up toward 0.3 (the player's eye 1.62 against the
        // blaze's own height-scaled band).
        if (target != null) {
            double targetEye = target.y() + 1.62;
            double ownEye = position.y() + eyeHeight() + blazeEyeOffset;
            if (targetEye > ownEye) {
                velocityY += (0.3 - velocityY) * 0.3;
            }
        }

        if (target == null || !canHunt) {
            // AttackGoal.stop() lines 197-199: the fire goes out; the
            // wander family owns the idle band.
            setBlazeFireActive(false);
            goalTickIdleWander(target);
            return;
        }

        double dx = target.x() - position.x();
        double dy = target.y() - position.y();
        double dz = target.z() - position.z();
        double squared = dx * dx + dy * dy + dz * dz;
        // The eyes stay on the target (setLookatValues 10.0F, 10.0F — the
        // look control runs outside the distance arms too).
        yaw = angleTo(dx, dz);
        headYaw = yaw;

        // AttackGoal.tick: the timer burns every tick first.
        blazeTicks--;

        if (squared < 4.0) {
            // lines 206-212: the close melee (d0 < 4 — the two-block
            // band) — the 20-tick cadence with the movement follow at the
            // reference's own speed.
            if (blazeTicks <= 0) {
                blazeTicks = 20;
                pendingMeleeAttack = true;
            }
            mode = Mode.CHASE;
            tickPathFollowing(target);
        } else if (squared < 256.0) {
            // lines 213-248: the step cycle (d0 < 256 — the sixteen-block
            // band). The blaze stands and volleys — only the look moves
            // (the movement control never fires inside this band).
            mode = Mode.IDLE;
            clearPath();
            if (blazeTicks <= 0) {
                blazeStep++;
                if (blazeStep == 1) {
                    blazeTicks = 60;
                    setBlazeFireActive(true);
                } else if (blazeStep <= 4) {
                    blazeTicks = 6;
                } else {
                    blazeTicks = 100;
                    blazeStep = 0;
                    setBlazeFireActive(false);
                }
                // lines 230-244: the volleys fire from step 2 on (the
                // reference's own `step > 1` gate — the reset wrote 0
                // first, so the 4th never fires).
                if (blazeStep > 1) {
                    pendingBlazeFireball = true;
                }
            }
        } else {
            // lines 249-251: beyond the volley band the navigation stops
            // and the movement control follows the target.
            mode = Mode.CHASE;
            tickPathFollowing(target);
        }
    }

    /** The synced fire-active flag's write (setFireActive lines 159-168). */
    private void setBlazeFireActive(boolean active) {
        if (blazeFireActive != active) {
            blazeFireActive = active;
            blazeFireDirty = true;
        }
    }

    /** @return the fire-active flag (the synced slot-16 bit). */
    public boolean blazeFireActive() {
        return blazeFireActive;
    }

    // --------------------------------------------------------------
    // The silverfish (Slice 18c — SilverfishEntity's egg-hosting life)
    // --------------------------------------------------------------

    /** WakeUpFriendsGoal's cooldown (armed by the hurt walk, 20 ticks). */
    private int silverfishWakeCooldown;
    /** Set when the wake-up cooldown expires; the manager consumes. */
    private boolean pendingSilverfishWake;
    /** Set when the infest roll fires; the manager consumes. */
    private boolean pendingSilverfishInfest;
    /** The infest target cell (InfestStoneGoal's dir offset). */
    private int silverfishInfestX;
    private int silverfishInfestY;
    private int silverfishInfestZ;

    /**
     * Arms the wake-up (WakeUpFriendsGoal.onDamaged lines 198-201): the
     * entity-sourced or magic hurt sets the 20-tick cooldown — the swarm
     * scan fires when it expires (the already-armed clock never resets).
     */
    public void armSilverfishWake() {
        if (silverfishWakeCooldown == 0) {
            silverfishWakeCooldown = 20;
        }
    }

    /**
     * The silverfish's clock (WakeUpFriendsGoal.tick lines 210-236 +
     * InfestStoneGoal.canStart lines 142-163): the wake-up countdown
     * ends in the scan verdict; the infest roll fires only with no
     * target in sight — a 1-in-10 chance to slip into an adjacent
     * infestable block (the body disappears into the egg).
     */
    private void tickSilverfishClock() {
        if (silverfishWakeCooldown > 0) {
            silverfishWakeCooldown--;
            if (silverfishWakeCooldown <= 0) {
                pendingSilverfishWake = true;
            }
        }
        // InfestStoneGoal.canStart: the targetless body rolls the
        // 1-in-10; the direction pick walks the six facings, the
        // adjacent cell read through the world's block identity.
        if (visibleTarget == null && random.nextInt(10) == 0) {
            int facing = random.nextInt(6);
            int dx = facing == 0 ? 1 : facing == 1 ? -1 : 0;
            int dy = facing == 2 ? 1 : facing == 3 ? -1 : 0;
            int dz = facing == 4 ? 1 : facing == 5 ? -1 : 0;
            int bx = (int) Math.floor(position.x()) + dx;
            int by = (int) Math.floor(position.y() + 0.5) + dy;
            int bz = (int) Math.floor(position.z()) + dz;
            net.zaminmc.torch.util.Identifier block = world.blockIdAt(bx, by, bz);
            if (block != null
                    && net.zaminmc.torch.server.block.InfestedBlocks.canBeInfested(block)) {
                pendingSilverfishInfest = true;
                silverfishInfestX = bx;
                silverfishInfestY = by;
                silverfishInfestZ = bz;
            }
        }
    }

    /** @return and clears the wake-up scan verdict. */
    public boolean consumeSilverfishWake() {
        boolean value = pendingSilverfishWake;
        pendingSilverfishWake = false;
        return value;
    }

    /** @return and clears the infest verdict. */
    public boolean consumeSilverfishInfest() {
        boolean value = pendingSilverfishInfest;
        pendingSilverfishInfest = false;
        return value;
    }

    /** @return the infest target cell's x. */
    public int silverfishInfestX() {
        return silverfishInfestX;
    }

    /** @return the infest target cell's y. */
    public int silverfishInfestY() {
        return silverfishInfestY;
    }

    /** @return the infest target cell's z. */
    public int silverfishInfestZ() {
        return silverfishInfestZ;
    }

    // --------------------------------------------------------------
    // The wither skeleton (Slice 18d — SkeletonEntity.setType(1))
    // --------------------------------------------------------------

    /** The wither variant's flag (the nether initialize's 4-in-5 roll). */
    private boolean skeletonWither;

    /** The wither variant's melee (initialize line 221: ATTACK_DAMAGE 4.0). */
    private static final float SKELETON_WITHER_DAMAGE = 4.0f;

    /** The wither variant's hull (setType line 281: setSize(0.72F, 2.535F)). */
    private static final double SKELETON_WITHER_HEIGHT = 2.535;

    /** The wither variant's width (setType line 281's own 0.72F). */
    private static final double SKELETON_WITHER_WIDTH = 0.72;

    /**
     * The wither effect's duration on a landed melee hit (tryDamage line
     * 99: {@code new StatusEffectInstance(StatusEffect.WITHER.id, 200)} —
     * a flat 10 seconds, no amplifier, no difficulty gate).
     */
    public static final int SKELETON_WITHER_EFFECT_TICKS = 200;

    /** The mob's held item (setEquipment slot 0 — the bow, the stone sword). */
    private ItemStack heldEquipment;

    /**
     * The player's recent-hit window (LivingEntity's own playerHitTimer,
     * lines 92/254/679: 100 ticks from the player's last wound — the
     * dropRareItem gate and the XP gate read it).
     */
    private int playerHitTimer;

    /**
     * The wither variant (setType lines 289-297): the taller 0.72x2.535
     * hull, the fire immunity, the 4.0 melee (the stone-sword arm) over
     * the archer's bow — the flag lands once at the spawn (the reference's
     * own initialize roll).
     */
    public void setSkeletonWither(boolean wither) {
        this.skeletonWither = wither;
    }

    /** @return the wither variant's flag. */
    public boolean skeletonWither() {
        return skeletonWither;
    }

    /**
     * @return the wither effect's duration on the variant's landed melee
     * hit (tryDamage line 99: the flat 200 ticks).
     */
    public int skeletonWitherEffectTicks() {
        return SKELETON_WITHER_EFFECT_TICKS;
    }

    /**
     * @return whether the body never ignites — the kind's own immunity
     * or the wither variant's (setType line 291:
     * {@code immuneToFire = type == 1}).
     */
    public boolean fireImmune() {
        return type.fireImmune() || (type == MobType.SKELETON && skeletonWither);
    }

    /**
     * The held equipment (setEquipment slot 0): the bow at the archer's
     * spawn (addRandomEquipment line 211), the stone sword at the wither
     * conversion (initialize line 220). The wire rides the Entity
     * Equipment packet; the drops ride the manager's equipment walk.
     *
     * @param held the held stack, or null for the empty hand.
     */
    public void setHeldEquipment(ItemStack held) {
        this.heldEquipment = held;
    }

    /** @return the held stack, or null for the empty hand. */
    public ItemStack heldEquipment() {
        return heldEquipment;
    }

    /**
     * The player's hit window arms (LivingEntity.takeDamage lines
     * 678-680: {@code playerHitTimer = 100} when the attacker is a
     * player — the tamed wolf's own arm rides the pet slice).
     */
    public void markPlayerHit() {
        playerHitTimer = 100;
    }

    /**
     * @return whether a player wounded the body inside the last 100
     * ticks (LivingEntity.die's dropRareItem gate, line 766).
     */
    public boolean playerHitRecently() {
        return playerHitTimer > 0;
    }

    /** The window's own decay (LivingEntity.mobTick lines 254-255). */
    private void tickPlayerHitTimer() {
        if (playerHitTimer > 0) {
            playerHitTimer--;
        }
    }

    /** @return and clears the fireball volley verdict. */
    public boolean consumeBlazeFireball() {
        boolean value = pendingBlazeFireball;
        pendingBlazeFireball = false;
        return value;
    }

    /** @return and clears the fire-flag flip verdict. */
    public boolean consumeBlazeFireDirty() {
        boolean value = blazeFireDirty;
        blazeFireDirty = false;
        return value;
    }

    /**
     * The blaze's fireball aim (AttackGoal.tick lines 214-216 + 234-243):
     * the raw direction toward the target's MID band (the shape's minY +
     * height/2 minus the blaze's own mid), with the per-volley gaussian
     * spread sigma {@code sqrt(sqrt(d0)) * 0.5} on the HORIZONTAL pair
     * only — the vertical component stays exact.
     */
    public double blazeFireballSigma(double squared) {
        return Math.sqrt(Math.sqrt(squared)) * 0.5;
    }

    /**
     * @return the iron golem's heavy hit ({@code 7 + nextInt(15)} —
     * tryDamage line 202).
     */
    public float golemDamageRoll() {
        return GOLEM_DAMAGE_BASE + random.nextInt(GOLEM_DAMAGE_SPREAD);
    }

    /** @return the launch the landed golem hit gives (velocityY += 0.4F). */
    public double golemLaunchY() {
        return GOLEM_LAUNCH_Y;
    }

    /** @return and clears the golem's swing verdict (the Event-4 site). */
    public boolean consumeGolemSwing() {
        boolean value = pendingGolemSwing;
        pendingGolemSwing = false;
        return value;
    }

    /** @return and clears the snow golem's volley verdict. */
    public boolean consumeSnowGolemShot() {
        boolean value = pendingSnowGolemShot;
        pendingSnowGolemShot = false;
        return value;
    }

    /**
     * The guardian's mind (GuardianAttackEntityGoal + the wander family):
     * the laser's charge cycle against the nearest player beyond the
     * flop band with a clear line (the target validator's {@code > 9}
     * squared), then the squid-style wander re-roll at the guardian's
     * own 80-tick cadence when the hunt is off.
     */
    private void tickGuardianMind(Position target, boolean canHunt) {
        boolean hunting = false;
        if (target != null && canHunt && world.clearLine(eyePosition(), target)) {
            double dx = target.x() - position.x();
            double dy = target.y() - position.y();
            double dz = target.z() - position.z();
            double squared = dx * dx + dy * dy + dz * dz;
            if (squared > GUARDIAN_TARGET_MIN_SQUARED) {
                hunting = true;
                // The look stays on the target every charge tick (the
                // reference's own setLookatValues(…, 90.0F, 90.0F)).
                yaw = angleTo(dx, dz);
                headYaw = yaw;
                if (guardianChargeTicks == Integer.MIN_VALUE) {
                    // start(): the -10 offset, the navigation stop, the look.
                    guardianChargeTicks = GUARDIAN_CHARGE_START;
                    clearPath();
                }
                // tick(): the charge climbs while the target stays seen.
                guardianChargeTicks++;
                if (guardianChargeTicks == 0) {
                    // setBeamTag(target id) + doEntityEvent(this, (byte)21).
                    pendingGuardianBeamStart = true;
                } else if (guardianChargeTicks >= guardianChargeTime()) {
                    // The hit: the magic base + the elder bump (the hard
                    // bump rides the engine's difficulty read), then the
                    // melee lands through the manager's pair.
                    pendingGuardianLaserHit = true;
                    guardianChargeTicks = Integer.MIN_VALUE; // stop()
                    guardianBeamTargetId = 0; // setBeamTag(0)
                }
            }
        }
        if (!hunting && guardianChargeTicks != Integer.MIN_VALUE) {
            // The hunt broke (no target, no line, inside the flop band):
            // stop() — the beam clears, the target resets.
            guardianChargeTicks = Integer.MIN_VALUE;
            guardianBeamTargetId = 0;
        }
        guardianMoving = !hunting && (guardianHasSteer
                && (guardianSteerX != 0.0 || guardianSteerY != 0.0 || guardianSteerZ != 0.0));
        if (hunting) {
            return; // the charge stands still (the navigation stop)
        }
        // The wander (WanderAroundGoal 1.0, the interval 80): the re-rolled
        // swim direction — the squid's own model at the guardian's cadence.
        // The water-favor arm (WaterPathNavigation's getPathfindingFavor:
        // the waypoints never leave the water): a dry cell re-rolls with
        // the climbing vertical.
        boolean dryCell = !world.inFluid(position.x(), position.y() + 0.2, position.z());
        if (!guardianHasSteer || random.nextInt(GUARDIAN_WANDER_INTERVAL) == 0 || dryCell) {
            float bearing = random.nextFloat() * (float) Math.PI * 2.0f;
            guardianSteerX = Math.cos(bearing) * GUARDIAN_SWIM_SCALE;
            guardianSteerY = dryCell
                    ? GUARDIAN_SWIM_SCALE / 2.0 // the climb back to the column
                    : -0.05 + random.nextFloat() * 0.1;
            guardianSteerZ = Math.sin(bearing) * GUARDIAN_SWIM_SCALE;
            guardianHasSteer = true;
            yaw = angleTo(guardianSteerX, guardianSteerZ);
        }
    }

    /**
     * The guardian's body (GuardianMovementControl + moveRelative + mobTick's
     * flop): in water the sinusoidal swim — the steering target joins the
     * {@code sin((ticks + id) * 0.5) * 0.05} horizontal drift and the
     * {@code sin((ticks + id) * 0.75) * 0.25} vertical, the position
     * integrates the velocity, then the 0.9 decay and the idle sink; on
     * land the grounded body flops (the +0.5 rise, the ±0.4 horizontal, the
     * random yaw) between the gravity falls.
     */
    private void tickGuardianBody(boolean inFluid) {
        if (inFluid) {
            double steerX = guardianMoving ? guardianSteerX : 0.0;
            double steerY = guardianMoving ? guardianSteerY : 0.0;
            double steerZ = guardianMoving ? guardianSteerZ : 0.0;
            velocityX += steerX;
            velocityY += steerY;
            velocityZ += steerZ;
            // The bobbing arms (the movement control's own: lines 553-559 —
            // the lifetime clock + the body's own network id phase them).
            double drift = Math.sin((guardianTicks + entityId) * 0.5) * 0.05;
            double cosYaw = Math.cos(Math.toRadians(yaw));
            double sinYaw = Math.sin(Math.toRadians(yaw));
            velocityX += drift * cosYaw;
            velocityZ += drift * sinYaw;
            velocityY += Math.sin((guardianTicks + entityId) * 0.75) * 0.05
                    * (sinYaw + cosYaw) * 0.25;
            double newX = position.x() + velocityX;
            double newY = position.y() + velocityY;
            double newZ = position.z() + velocityZ;
            // The seabed rest (move()'s own collision): a sinking body in
            // water stands on the solid floor instead of passing through.
            if (velocityY <= 0 && world.isSolidAt(newX, newY - GROUND_EPSILON, newZ)) {
                newY = world.supportY(newX, newY - GROUND_EPSILON, newZ);
                velocityY = 0.0;
                onGround = true;
            } else {
                onGround = false;
            }
            position = new Position(newX, newY, newZ);
            velocityX *= GUARDIAN_SWIM_DECAY;
            velocityY *= GUARDIAN_SWIM_DECAY;
            velocityZ *= GUARDIAN_SWIM_DECAY;
            if (!guardianMoving && guardianChargeTicks == Integer.MIN_VALUE) {
                velocityY -= GUARDIAN_IDLE_SINK; // the idle drift down
            }
            return;
        }
        // The land body (mobTick lines 321-330 + the standard fall): the
        // grounded flop — the +0.5 rise, the ±0.4 horizontal pair, the
        // random yaw — between the gravity falls with the ground snap.
        if (onGround) {
            velocityY += GUARDIAN_FLOP_RISE;
            velocityX += (random.nextFloat() * 2.0F - 1.0F) * GUARDIAN_FLOP_BAND;
            velocityZ += (random.nextFloat() * 2.0F - 1.0F) * GUARDIAN_FLOP_BAND;
            yaw = random.nextFloat() * 360.0F;
            onGround = false;
        } else {
            velocityY = (velocityY - GRAVITY_PER_TICK) * 0.98;
        }
        double newX = position.x() + velocityX;
        double newY = position.y() + velocityY;
        double newZ = position.z() + velocityZ;
        if (velocityY <= 0 && world.isSolidAt(newX, newY - GROUND_EPSILON, newZ)) {
            newY = world.supportY(newX, newY - GROUND_EPSILON, newZ);
            velocityY = 0.0;
            onGround = true;
        } else {
            onGround = false;
        }
        position = new Position(newX, newY, newZ);
    }

    /** The anger window's roll: {@code 400 + nextInt(400)} ticks. */
    private static final int PIGMAN_ANGER_BASE = 400;
    /** The angry sound's own delay: {@code nextInt(40)} ticks. */
    private static final int PIGMAN_ANGER_SOUND_BOUND = 40;
    /** The angry speed boost (the +0.05 attribute modifier on the 0.23
     * base, expressed as the chase multiplier). */
    private static final double PIGMAN_ANGRY_CHASE_MULTIPLIER = 1.1 * (0.28 / 0.23);
    /** The anger's group radius (RevengeGoal's call-others box: the
     * follow range 32 around the hurt body). */
    private static final double PIGMAN_ANGER_RADIUS_SQUARED = 32.0 * 32.0;

    /** The decaying anger window (0 = the calm neutral state). */
    private int pigmanAnger;
    /** The angry sound's countdown (0 = idle). */
    private int pigmanAngerSoundDelay;
    /** The anger's target player (the attacker's name — the anger keys on
     * the player, not the mob-target state). */
    private String pigmanAngerTarget = "";
    /** Set when the delayed angry sound fires; consumed by the manager. */
    private boolean pendingPigmanAngrySound;

    /** @return whether the pigman is angry (TargetPlayerGoal's gate). */
    public boolean isAngry() {
        return pigmanAnger > 0;
    }

    /**
     * The anger lands ({@code getAngryTo}): the 400+nextInt(400) window,
     * the delayed angry sound, and the attacker's identity. The group
     * propagation is the manager's own walk (the call-others family).
     */
    public void becomeAngryTo(String playerName) {
        pigmanAnger = PIGMAN_ANGER_BASE + random.nextInt(PIGMAN_ANGER_BASE);
        pigmanAngerSoundDelay = random.nextInt(PIGMAN_ANGER_SOUND_BOUND);
        if (playerName != null) {
            pigmanAngerTarget = playerName;
        }
    }

    /** @return the anger's target player name (the attacker), or "". */
    public String angerTargetName() {
        return pigmanAngerTarget;
    }

    /** @return and clears the delayed angry sound. */
    public boolean consumePigmanAngrySound() {
        boolean value = pendingPigmanAngrySound;
        pendingPigmanAngrySound = false;
        return value;
    }

    /** The anger's own per-tick walk (mobAiTick): the decay, the delayed
     * sound, and the angry speed boost through the goal-walk multiplier. */
    private void tickPigmanAnger() {
        if (pigmanAnger > 0) {
            pigmanAnger--;
            if (pigmanAngerSoundDelay > 0 && --pigmanAngerSoundDelay == 0) {
                pendingPigmanAngrySound = true;
            }
        }
    }

    /** ShootFireballGoal's charge cycle: 20 to the launch, -40 after. */
    private int ghastChargeTicks;
    /** The flight's current target (MoveAroundGoal's random ±16 pick). */
    private double ghastTargetX;
    private double ghastTargetY;
    private double ghastTargetZ;
    /** Whether the flight is steering (the movement control's moving flag). */
    private boolean ghastMoving;
    /** The movement control's burst cadence (the 2-7 tick clock). */
    private int ghastBurstCooldown;
    /** Set when the charge crossed 10 (the 1007 warning sound; the manager consumes). */
    private boolean pendingGhastChargeSound;
    /** The launch target (set at charge 20; the manager consumes and launches). */
    private Position pendingGhastFireball;

    /** @return the charging flag (GhastEntity's synced slot 16). */
    public boolean ghastCharging() {
        return ghastChargeTicks > 10;
    }

    /** @return and clears the 1007 charge warning. */
    public boolean consumeGhastChargeSound() {
        boolean value = pendingGhastChargeSound;
        pendingGhastChargeSound = false;
        return value;
    }

    /** @return and clears the fireball launch target. */
    public Position consumeGhastFireball() {
        Position value = pendingGhastFireball;
        pendingGhastFireball = null;
        return value;
    }

    /**
     * The ghast's own mind (GhastEntity's goal set): the LookAroundGoal
     * faces the drift or the target inside 64 blocks, and the
     * ShootFireballGoal runs the charge cycle — 20 ticks of charge (the
     * 1007 warning at 10), the launch, the -40 cooldown — only while the
     * target stands inside 64 blocks on a clear line.
     */
    private void tickGhastMind(Position target, boolean canHunt) {
        // LookAroundGoal (lines 153-180): the eyes face the drift, or the
        // target inside the 64-block grip.
        if (target != null) {
            double dx = target.x() - position.x();
            double dz = target.z() - position.z();
            if (dx * dx + (target.y() - position.y()) * (target.y() - position.y())
                    + dz * dz < 64.0 * 64.0) {
                yaw = angleTo(dx, dz);
                headYaw = yaw;
            }
        } else if (velocityX != 0.0 || velocityZ != 0.0) {
            yaw = (float) Math.toDegrees(Math.atan2(-velocityX, velocityZ));
            headYaw = yaw;
        }

        // ShootFireballGoal (lines 266-324).
        boolean inRange = target != null && canHunt;
        if (inRange) {
            double dx = target.x() - position.x();
            double dy = target.y() - position.y();
            double dz = target.z() - position.z();
            inRange = dx * dx + dy * dy + dz * dz < 64.0 * 64.0
                    && world.clearLine(eyePosition(), target);
        }
        if (inRange) {
            ghastChargeTicks++;
            if (ghastChargeTicks == 10) {
                pendingGhastChargeSound = true; // the 1007 warning
            }
            if (ghastChargeTicks == 20) {
                pendingGhastFireball = target; // the 1008 launch
                ghastChargeTicks = -40;
            }
        } else if (ghastChargeTicks > 0) {
            ghastChargeTicks--;
        }
    }

    /**
     * The ghast's flight body (MoveAroundGoal + the ghast's own
     * MovementControl over FlyingMobEntity): no gravity, the 0.91 drag, a
     * random ±16 target picked when the flight stalls, and the burst
     * cadence — every 2-7 ticks a 0.1 acceleration toward the target when
     * the line is clear (a blocked line stops the steering).
     */
    private void tickGhastBody() {
        if (!ghastMoving) {
            // MoveAroundGoal.start: the random ±16 cube around the body.
            ghastTargetX = position.x() + (random.nextFloat() * 2.0f - 1.0f) * 16.0;
            ghastTargetY = position.y() + (random.nextFloat() * 2.0f - 1.0f) * 16.0;
            ghastTargetZ = position.z() + (random.nextFloat() * 2.0f - 1.0f) * 16.0;
            ghastMoving = true;
            ghastBurstCooldown = 0;
        }
        double dx = ghastTargetX - position.x();
        double dy = ghastTargetY - position.y();
        double dz = ghastTargetZ - position.z();
        double squared = dx * dx + dy * dy + dz * dz;
        if (squared < 1.0 || squared > 3600.0) {
            ghastMoving = false; // the arrival (or the runaway): a new pick
        } else if (--ghastBurstCooldown <= 0) {
            ghastBurstCooldown = random.nextInt(5) + 2;
            double length = Math.sqrt(squared);
            if (world.clearLine(position(),
                    new Position(ghastTargetX, ghastTargetY, ghastTargetZ))) {
                velocityX += dx / length * 0.1;
                velocityY += dy / length * 0.1;
                velocityZ += dz / length * 0.1;
            } else {
                ghastMoving = false; // a blocked line stops the steering
            }
        }
        // FlyingMobEntity.moveRelative's own drag: 0.91 per tick.
        velocityX *= 0.91;
        velocityY *= 0.91;
        velocityZ *= 0.91;
        if (mode == Mode.CHASE || mode == Mode.STRAFE || mode == Mode.FUSE) {
            mode = Mode.IDLE;
            modeTicks = 0;
        }
    }

    /**
     * The flying body's position integration: each axis moves through its
     * own cell, a solid blocking cell stops that axis' drift (the engine's
     * per-axis stand-in for the reference's swept move).
     */
    private void integrateFlight() {
        double midY = position.y() + 2.0; // the 4-tall body's center band
        double newX = position.x() + velocityX;
        double newY = position.y() + velocityY;
        double newZ = position.z() + velocityZ;
        if (world.isSolidAt(newX, midY, position.z())) {
            newX = position.x();
            velocityX = 0.0;
            ghastMoving = false;
        }
        if (world.isSolidAt(position.x(), newY + 2.0, position.z())
                || world.isSolidAt(position.x(), newY, position.z())) {
            newY = position.y();
            velocityY = 0.0;
            ghastMoving = false;
        }
        if (world.isSolidAt(position.x(), midY, newZ)) {
            newZ = position.z();
            velocityZ = 0.0;
            ghastMoving = false;
        }
        position = new Position(newX, newY, newZ);
        onGround = false;
    }

    /**
     * The slime's hop (MovementControl.tick lines 377-410 + jump lines
     * 279-283): the movement input is ZERO between hops (the engine's
     * stand-in for the reference's speed model), and when the cadence
     * elapses on the ground the body launches — velocityY 0.42, the
     * horizontal at the reference's movement-speed attribute
     * ({@code 0.2 + 0.1 * size}) along the facing. The aggressive chase
     * divides the cadence by three (the AttackGoal's own rule); the
     * swim arm hops 80% of the ticks at the 1.2 speed
     * (SwimAroundGoal).
     */
    private void tickSlimeMovement(boolean inFluid) {
        if (inFluid) {
            if (random.nextFloat() < 0.8F) {
                launchSlimeHop(1.2);
            }
            return;
        }
        if (!onGround) {
            return; // airborne: the hop's momentum carries (the engine's own drag)
        }
        if (--slimeHopCooldown > 0) {
            return; // between hops the body is still (the zeroed input)
        }
        // getTicksUntilNextJump: the slime's 20+nextInt(10), the magma
        // cube's own 4x cadence override.
        slimeHopCooldown = (random.nextInt(20) + 10)
                * (type == MobType.MAGMA_CUBE ? 4 : 1);
        if (slimeAggressive) {
            slimeHopCooldown /= 3;
        }
        launchSlimeHop(1.0);
        pendingSlimeSound = new SlimeSound(slimeSoundName(),
                0.4F * slimeSize,
                (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F);
    }

    /** Launches one hop (jump(): velocityY 0.42 — the magma cube's own
     * override rises with the size — plus the facing impulse). */
    private void launchSlimeHop(double speedMultiplier) {
        double speed = (0.2 + 0.1 * slimeSize) * speedMultiplier;
        double radians = Math.toRadians(yaw);
        velocityX += -Math.sin(radians) * speed;
        velocityZ += Math.cos(radians) * speed;
        velocityY = type == MobType.MAGMA_CUBE
                ? 0.42 + slimeSize * 0.1 // MagmaCubeEntity.jump
                : 0.42;
    }

    // ---- the rabbit's hop cycle (Slices 12l + 21d — RabbitEntity's own
    // movement model over the engine's input lane: the Action enum 1:1) ----

    /**
     * RabbitEntity.Action's own five rows (lines 389-423): the speed
     * parameter, the jump strength, the landing delay, and the animation
     * duration — the NONE idle sit, the HOP walk, the STEP jump-control
     * hop, the SPRINT panic/flee flight, the ATTACK killer's lunge.
     */
    private enum RabbitAction {
        NONE(0.0F, 0.0F, 30, 1),
        HOP(0.8F, 0.2F, 20, 10),
        STEP(1.0F, 0.45F, 14, 14),
        SPRINT(1.75F, 0.4F, 1, 8),
        ATTACK(2.0F, 0.7F, 7, 8);

        final float speed;
        final float jumpStrength;
        final int jumpDelay;
        final int jumpDuration;

        RabbitAction(float speed, float jumpStrength, int jumpDelay, int jumpDuration) {
            this.speed = speed;
            this.jumpStrength = jumpStrength;
            this.jumpDelay = jumpDelay;
            this.jumpDuration = jumpDuration;
        }
    }

    /** The live action (mobAiTick's own field; HOP the resting state). */
    private RabbitAction rabbitAction = RabbitAction.HOP;

    /** Ticks until the rabbit's next hop (jumpDelayTicks — the zeroed-input window). */
    private int rabbitJumpDelayTicks;

    /** The animation clock (jumpTicks) — the wire's doEntityEvent(1) fires at 0. */
    private int rabbitJumpTicks;

    /** The running animation length (jumpDuration — 0 between hops). */
    private int rabbitJumpDuration;

    /** The last tick's grounded state (the landing edge). */
    private boolean rabbitLastOnGround = true;

    /**
     * The raid-farm hunger clock (RabbitEntity.ticksBeforeHungry — the
     * MoreCarrotTicks NBT): 100 after a garden eat, decaying by
     * {@code nextInt(3)} per tick; zero means hungry.
     */
    private int rabbitTicksBeforeHungry;

    /** @return the raid-farm hunger clock (0 hungry — the MoreCarrotTicks NBT). */
    public int rabbitTicksBeforeHungry() {
        return rabbitTicksBeforeHungry;
    }

    /** @return the rabbit's skin variant (the synced slot 18's byte). */
    public int rabbitVariant() {
        return rabbitVariant;
    }

    /** @return whether this is the killer bunny (RabbitType 99). */
    public boolean rabbitKiller() {
        return rabbitVariant == 99;
    }

    /**
     * RabbitEntity.setVariant (lines 320-333): the skin byte lands on the
     * synced slot 18 — the 99 arm swaps the whole mind (the flee-wolf goal
     * leaves, the EvilAttack family and the player/wolf hunts join). The
     * reference's custom "Killer Bunny" name rides the name-tag family the
     * engine's mobs do not yet carry on the wire (ledgered).
     */
    public void setRabbitVariant(int variant) {
        rabbitVariant = variant;
    }

    /** The rabbit's skin variant (the synced slot 18: 0-5 natural, 99 the killer). */
    private int rabbitVariant;

    /** Queued at each hop start: the world's doEntityEvent(this, (byte)1). */
    private boolean pendingRabbitJumpStatus;

    /** @return and clears the queued jump event (the client's hop animation). */
    public boolean consumeRabbitJumpStatus() {
        boolean value = pendingRabbitJumpStatus;
        pendingRabbitJumpStatus = false;
        return value;
    }

    /**
     * The rabbit's movement tick (RabbitEntity.mobAiTick lines 120-172 over
     * the engine's input lane): the action band from the live movement
     * speed (SPRINT above 0.8 — the panic and the killer's chase; HOP
     * otherwise; ATTACK holds until the landing's startAction(false) flips
     * it), the delay burn, the hunger decay, the landing edge's delay
     * re-roll, the killer's lunge arm (the attack target within 16 squared
     * and the delay spent: face, move, ATTACK-hop), and the launch itself —
     * the facing impulse at {@code 1.5 x the action speed} over the walk
     * band, the action's own jump strength, the hop sound at the
     * 0.8-centered pitch band (startAction lines 89-101).
     */
    private void tickRabbitHop(boolean inFluid) {
        if (inFluid) {
            // The swim arm (the reference's canSwim navigation — the body
            // keeps paddling): the reference launches hops in fluid.
            if (random.nextFloat() < 0.8F) {
                launchRabbitHop(currentRabbitAction(), Double.NaN);
            }
            return;
        }
        // mobAiTick lines 121-125: the action band. The movementControl's
        // speed parameter — the engine's mode carries it (the panic and the
        // chase run the fast goals; everything else walks).
        if (rabbitJumpDelayTicks > 0) {
            rabbitJumpDelayTicks--;
        }
        // The hunger decay (line 131-136): only a fed clock decays.
        if (rabbitTicksBeforeHungry > 0) {
            rabbitTicksBeforeHungry -= random.nextInt(3);
            if (rabbitTicksBeforeHungry < 0) {
                rabbitTicksBeforeHungry = 0;
            }
        }
        if (!onGround) {
            rabbitLastOnGround = false;
            return; // airborne: the hop's momentum carries
        }
        if (!rabbitLastOnGround) {
            // The landing edge (lines 138-142): startAction(false) — the
            // ATTACK flips back to HOP — then the delay re-rolls from the
            // action the landing left standing.
            if (rabbitAction == RabbitAction.ATTACK) {
                rabbitAction = RabbitAction.HOP;
            }
            rabbitJumpDelayTicks = rabbitAction.jumpDelay;
            rabbitJumpTicks = 0;
            rabbitJumpDuration = 0;
        }
        rabbitLastOnGround = true;
        // The killer's lunge (lines 144-152): grounded, the delay spent, and
        // a live attack target within 16 squared — the look, the movement
        // update, and the ATTACK hop.
        if (rabbitKiller() && rabbitJumpDelayTicks == 0 && visibleTarget != null
                && targetHuntable) {
            double dx = visibleTarget.x() - position.x();
            double dy = visibleTarget.y() - position.y();
            double dz = visibleTarget.z() - position.z();
            if (dx * dx + dy * dy + dz * dz < 16.0) {
                yaw = angleTo(dx, dz);
                headYaw = yaw;
                startRabbitJump(RabbitAction.ATTACK, dy);
                return;
            }
        }
        if (rabbitJumpDelayTicks > 0) {
            return; // between hops the body is still (the zeroed input)
        }
        if (mode == Mode.IDLE) {
            return; // the idle rabbit sits (the NONE action)
        }
        startRabbitJump(currentRabbitAction(), Double.NaN);
    }

    /**
     * mobAiTick's action band (lines 121-125): the movementControl speed
     * above 0.8 answers SPRINT (the reference's 1.33 panic/flee and the
     * killer's 1.4 chase — the engine's PANIC/CHASE modes carry them),
     * everything else HOP. The ATTACK holds until the landing flips it.
     */
    private RabbitAction currentRabbitAction() {
        if (rabbitAction == RabbitAction.ATTACK) {
            return RabbitAction.ATTACK;
        }
        return mode == Mode.PANIC || mode == Mode.CHASE
                ? RabbitAction.SPRINT : RabbitAction.HOP;
    }

    /**
     * startJumping (lines 103-107) + startAction's jump arm (lines 89-101):
     * the navigation speed {@code 1.5 x the action speed}, the animation
     * clock arms, the hop sound at the 0.8-centered pitch band, and the
     * launch — the facing impulse over the walk band and the action's jump
     * strength (getJumpStrength's moving-up arm: {@code 0.5} when the
     * movement target sits above the body's own y + 0.5).
     */
    private void startRabbitJump(RabbitAction action, double targetDy) {
        rabbitAction = action;
        rabbitJumpDuration = action.jumpDuration;
        rabbitJumpTicks = 0;
        pendingRabbitJumpStatus = true; // mobTick's doEntityEvent(1)
        launchRabbitHop(action, targetDy);
        pendingSlimeSound = new SlimeSound("mob.rabbit.hop",
                1.0F,
                ((random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F) * 0.8F);
    }

    /**
     * One hop launch: the facing impulse at {@code 1.5 x the action speed}
     * over the walk band, the strength rise — getJumpStrength's own
     * override (line 72-74): a movement target above the body's
     * {@code y + 0.5} answers the flat 0.5 rise whatever the action.
     */
    private void launchRabbitHop(RabbitAction action, double targetDy) {
        double radians = Math.toRadians(yaw);
        double launchSpeed = 1.5 * action.speed;
        velocityX += -Math.sin(radians) * type.walkSpeed * launchSpeed;
        velocityZ += Math.cos(radians) * type.walkSpeed * launchSpeed;
        velocityY = !Double.isNaN(targetDy) && targetDy > 0.5
                ? 0.5 : action.jumpStrength;
    }

    // ---- the rabbit's garden raid (Slice 21d — RaidFarmGoal over
    // GoToBlockGoal, lines 521-597) ----

    /** RaidFarmGoal's cooldown between scans (GoToBlockGoal.canStart's own). */
    private int rabbitFarmCooldown;

    /** The garden's farmland cell (GoToBlockGoal.target). */
    private int rabbitFarmX;
    private int rabbitFarmY;
    private int rabbitFarmZ;

    /** Whether the scan found a garden this window (RaidFarmGoal.hasTarget). */
    private boolean rabbitFarmHasTarget;

    /** The approach clock (GoToBlockGoal.activeTicks — the give-up band). */
    private int rabbitFarmActiveTicks;

    /** The window's cap (start's own {@code nextInt(nextInt(1200)+1200)+1200}). */
    private int rabbitFarmMaxTicks;

    /** Whether the body stands at the garden (hasReachedTarget). */
    private boolean rabbitFarmReached;

    /** The hungry gate's snapshot (RaidFarmGoal.hungry — taken at the scan). */
    private boolean rabbitFarmHungry;

    /** The pending garden eat (the manager's intent): the carrots' cell. */
    private boolean pendingRabbitFarmEat;

    /** @return and clears the queued garden eat (the farmland cell above). */
    public boolean consumeRabbitFarmEat() {
        boolean value = pendingRabbitFarmEat;
        pendingRabbitFarmEat = false;
        return value;
    }

    /** The eaten garden's carrots cell (the pending intent's target.up()). */
    public int rabbitFarmEatX() {
        return rabbitFarmX;
    }

    public int rabbitFarmEatY() {
        return rabbitFarmY + 1;
    }

    public int rabbitFarmEatZ() {
        return rabbitFarmZ;
    }

    /**
     * RaidFarmGoal's eat landing (onEatFromGarden line 363-376): the
     * hunger clock refills to 100 — the manager's intent completes the
     * world write and the particle.
     */
    public void completeRabbitFarmEat() {
        rabbitTicksBeforeHungry = 100;
    }

    /**
     * RaidFarmGoal.canStart/shouldContinue over GoToBlockGoal (lines
     * 532-547 + 78-96 + 66-70): a live window continues while the approach
     * clock stays inside the band and the garden still stands; a closed
     * window waits out the cooldown ({@code 200 + nextInt(200)} between
     * scans, 10 after an eat), snapshots the hungry gate, then runs the
     * spiral hunt — the reference's own ring walk over the ±16 box at the
     * y-1/y+0/y-2 bands, a farmland cell with age-7 carrots above
     * answering the target. The mobGriefing gate rides the engine's
     * default-on stand-in (no gamerule surface yet — ledgered).
     */
    boolean goalRabbitFarmCanUse() {
        if (type != MobType.RABBIT) {
            return false;
        }
        // mobGriefing (Slice 24a — RaidFarmGoal.canStart line 534's first
        // gate): a no-grief world never opens the raid window; an ALIVE
        // window continues (the reference re-checks only at canStart, the
        // garden walk honors a window opened under the rule).
        if (!world.mobGriefing()) {
            return false;
        }
        if (rabbitFarmHasTarget) {
            // shouldContinue (GoToBlockGoal lines 66-70 over the wrapper's
            // own gate): the approach band and the live garden.
            if (rabbitFarmActiveTicks >= -rabbitFarmMaxTicks
                    && rabbitFarmActiveTicks <= 1200
                    && isRabbitGardenAt(rabbitFarmX, rabbitFarmY, rabbitFarmZ)) {
                return true;
            }
            rabbitFarmHasTarget = false; // stop(): the goal leaves quietly
            return false;
        }
        if (rabbitFarmCooldown > 0) {
            rabbitFarmCooldown--;
            return false;
        }
        rabbitFarmCooldown = 200 + random.nextInt(200);
        rabbitFarmHungry = rabbitTicksBeforeHungry == 0;
        if (!rabbitFarmHungry) {
            return false;
        }
        int cx = (int) Math.floor(position.x());
        int cy = (int) Math.floor(position.y());
        int cz = (int) Math.floor(position.z());
        int range = 16;
        // GoToBlockGoal.findNearestBlock's own spiral: k the y band
        // (0, 1, -1 → y-1, y+0, y-2), l the ring radius, i1/j1 the ring's
        // own cross walk.
        for (int k = 0; k <= 1; k = k > 0 ? -k : 1 - k) {
            for (int l = 0; l < range; l++) {
                for (int i1 = 0; i1 <= l; i1 = i1 > 0 ? -i1 : 1 - i1) {
                    for (int j1 = i1 < l && i1 > -l ? l : 0; j1 <= l; j1 = j1 > 0 ? -j1 : 1 - j1) {
                        int x = cx + i1;
                        int y = cy + k - 1;
                        int z = cz + j1;
                        if (isRabbitGardenAt(x, y, z)) {
                            rabbitFarmX = x;
                            rabbitFarmY = y;
                            rabbitFarmZ = z;
                            rabbitFarmHasTarget = true;
                            // GoToBlockGoal.start (lines 79-85) — the goal
                            // arms its window the moment the scan answers:
                            // activeTicks 0 and the maxTicks roll
                            // {@code nextInt(nextInt(1200) + 1200) + 1200}.
                            rabbitFarmActiveTicks = 0;
                            rabbitFarmMaxTicks = random.nextInt(random.nextInt(1200) + 1200) + 1200;
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * RaidFarmGoal.isValidTarget (lines 583-596): a farmland cell with
     * age-7 carrots standing above it.
     */
    private boolean isRabbitGardenAt(int x, int y, int z) {
        var below = world.blockIdAt(x, y, z);
        if (below == null || !below.value().equals("farmland")) {
            return false;
        }
        var above = world.blockIdAt(x, y + 1, z);
        return above != null && above.value().equals("carrots_stage7");
    }

    /**
     * GoToBlockGoal.start + tick over RaidFarmGoal.tick (lines 545-580):
     * the walk to the farmland's up-center at the 0.7 goal speed with the
     * 40-tick re-path and the look control, the approach clock counting
     * the walk, and the eat at the standing cell (squared distance to
     * the up-center within 1.0) — the carrots vanish through the
     * manager's intent, the hunger refills, the cooldown re-arms at 10.
     */
    void goalRabbitFarmTick() {
        double tx = rabbitFarmX + 0.5;
        double ty = rabbitFarmY + 1.0;
        double tz = rabbitFarmZ + 0.5;
        double dx = tx - position.x();
        double dy = ty - position.y();
        double dz = tz - position.z();
        double squared = dx * dx + dy * dy + dz * dz;
        if (squared > 1.0) {
            // The approach (GoToBlockGoal.tick's walk arm): the clock
            // counts up, the re-path lands every 40 ticks.
            rabbitFarmReached = false;
            rabbitFarmActiveTicks++;
            headYaw = angleTo(dx, dz); // the look control's 10-degree grip
            mode = Mode.WANDER;
            modeTicks = 0;
            goalWalkSpeed = 0.7 / 0.3;
            if (rabbitFarmActiveTicks % 40 == 0) {
                tickPathFollowing(new Position(tx, ty, tz));
            }
            return;
        }
        // The standing cell (hasReachedTarget): the eat.
        rabbitFarmReached = true;
        rabbitFarmActiveTicks--;
        if (rabbitFarmHasTarget && isRabbitGardenAt(rabbitFarmX, rabbitFarmY, rabbitFarmZ)) {
            pendingRabbitFarmEat = true; // the manager drains the intent
        }
        rabbitFarmHasTarget = false;
        rabbitFarmCooldown = 10;
    }


    /** One queued slime sound (the jump's squish or the landing thud). */
    record SlimeSound(String name, float volume, float pitch) {
    }

    /** The queued jump sound (consumed by the manager's tick). */
    private SlimeSound pendingSlimeSound;

    /** The landing sound queued when a hop ends (the size > 2 rule). */
    private SlimeSound pendingSlimeLandSound;

    /** @return and clears the queued jump sound (null when none). */
    public SlimeSound consumeSlimeSound() {
        SlimeSound sound = pendingSlimeSound;
        pendingSlimeSound = null;
        return sound;
    }

    /** @return and clears the queued landing sound (null when none). */
    public SlimeSound consumeSlimeLandSound() {
        SlimeSound sound = pendingSlimeLandSound;
        pendingSlimeLandSound = null;
        return sound;
    }

    /**
     * The slime's mind (the goal family over the engine's own body): the
     * aggressive chase faces the target (the AttackGoal's lookAt + the
     * direction), the idle re-rolls the direction every 40-99 ticks
     * (RandomDirectionGoal lines 428-436), and the big body's contact
     * damage rides the melee pipeline (onPlayerCollision +
     * damageTargetEntity lines 192-204: within {@code 0.6 * size}
     * blocks with a clear line, the damage the size, on the player's own
     * 10-tick hurt-resistance cadence).
     */
    private void tickSlimeMind(Position target, boolean canHunt) {
        slimeAggressive = canHunt && target != null;
        if (slimeAggressive) {
            yaw = angleTo(target.x() - position.x(), target.z() - position.z());
            headYaw = yaw;
        } else if (--slimeDirectionCooldown <= 0) {
            slimeDirectionCooldown = 40 + random.nextInt(60);
            yaw = random.nextInt(360);
            headYaw = yaw;
        }
        mode = slimeAggressive ? Mode.CHASE : Mode.WANDER;
        // The contact damage (damageTargetEntity): only the big body.
        if (slimeContactCooldown > 0) {
            slimeContactCooldown--;
            return;
        }
        double reach = 0.6 * slimeSize;
        if (slimeSize > 1 && target != null
                && position.distanceSquared(target) < reach * reach
                && world.clearLine(position, target)) {
            slimeContactCooldown = HURT_WINDOW_TICKS; // the i-frame cadence
            pendingMeleeAttack = true;
            pendingSlimeAttackSound = true;
        }
    }

    /** Set when the contact damage lands; consumed by the manager. */
    private boolean pendingSlimeAttackSound;

    /** @return and clears the contact-damage flag (the mob.attack sound). */
    public boolean consumeSlimeAttackSound() {
        boolean value = pendingSlimeAttackSound;
        pendingSlimeAttackSound = false;
        return value;
    }

    /** The completed drink's metadata (consumed by the manager's tick). */
    private int completedWitchDrink;

    /** @return the completed drink's metadata (0 = none this tick). */
    public int consumeWitchDrink() {
        int metadata = completedWitchDrink;
        completedWitchDrink = 0;
        return metadata;
    }

    /** The reference's drink choices (WitchEntity.mobTick lines 105-127). */
    static final int WITCH_WATER_BREATHING = 8237;
    static final int WITCH_FIRE_RESISTANCE = 16307;
    static final int WITCH_INSTANT_HEALTH = 16341;
    static final int WITCH_SPEED = 16274;
    /** The potion use duration (PotionItem.getUseDuration): 32 ticks. */
    static final int WITCH_DRINK_DURATION_TICKS = 32;


    /** The hurt window's own cadence (the reference's i-frame length). */
    private static final int HURT_WINDOW_TICKS = 10;

    /**
     * The enderman's random teleport (EndermanEntity.teleport lines
     * 167-174): the +-32-block cube around the body, landing through
     * {@link #teleportTo}.
     */
    public boolean teleportRandomly() {
        double x = position.x() + (random.nextDouble() - 0.5) * 64.0;
        double y = position.y() + (random.nextInt(64) - 32);
        double z = position.z() + (random.nextDouble() - 0.5) * 64.0;
        return teleportTo(x, y, z);
    }

    /**
     * The enderman's blink toward a target (EndermanEntity.teleportTo(Entity)
     * lines 176-184): 16 blocks along the toward-target direction with the
     * +-4-block jitter (the reference's own {@code (random-0.5)*8} x/z and
     * {@code nextInt(16)-8} y bands), landing through {@link #teleportTo}.
     */
    public boolean teleportToward(Position target) {
        double dx = position.x() - target.x();
        double dy = position.y() + type.height / 2.0 - (target.y() + 1.62);
        double dz = position.z() - target.z();
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0E-4) {
            return teleportRandomly();
        }
        dx /= length;
        dy /= length;
        dz /= length;
        double reach = 16.0;
        double x = position.x() + (random.nextDouble() - 0.5) * 8.0 - dx * reach;
        double y = position.y() + (random.nextInt(16) - 8) - dy * reach;
        double z = position.z() + (random.nextDouble() - 0.5) * 8.0 - dz * reach;
        return teleportTo(x, y, z);
    }

    /**
     * The enderman's landing walk (EndermanEntity.teleportTo lines 186-238):
     * drop down to the first solid ground, require the body's own clearance
     * (the height's cells clear of solids and fluids), and commit the
     * position only when the landing is clean — otherwise the body stays
     * exactly where it was.
     */
    public boolean teleportTo(double x, double y, double z) {
        int feetY = (int) Math.floor(y);
        // The drop: fall down until the cell below is solid (the reference's
        // blocksMovement walk).
        while (feetY > 0 && !world.isSolid(x, feetY - 0.5, z)) {
            feetY--;
        }
        if (feetY <= 0) {
            return false;
        }
        // The clearance: the body's own cells (the 2.9-tall enderman needs
        // two clear cells above the ground plus most of the third).
        int bodyCells = (int) Math.ceil(type.height);
        for (int cell = 0; cell < bodyCells; cell++) {
            if (world.isSolid(x, feetY + cell + 0.5, z)
                    || world.inFluid(x, feetY + cell + 0.5, z)) {
                return false;
            }
        }
        position = new Position(x, feetY, z);
        velocityX = 0;
        velocityY = 0;
        velocityZ = 0;
        return true;
    }

    /** Day-locked convenience for tests and callers without a clock. */
    public boolean tick() {
        return tick(false);
    }

    /** @return true when the mob produced an idle chatter sound this tick.
     * The roll re-arms itself; call exactly once per tick after {@link #tick()}.
     */
    public boolean idleSoundDue() {
        if (idleSoundTimer > 0) {
            idleSoundTimer--;
            return false;
        }
        if (!dead && !effectiveIdleSound().isEmpty()) {
            // The guardian's own cadence (getAmbientSoundInterval line
            // 197): 160 ticks flat — no random spread.
            idleSoundTimer = type == MobType.GUARDIAN
                    ? 160 : 160 + random.nextInt(320); // 8-24 s
            return true;
        }
        return false;
    }

    /**
     * @return the body's idle sound. The kind's own dynamic families
     * (Slice 30c): the wolf's own ladder (WolfEntity.getAmbientSound
     * lines 138-146 — angry growls, a third of the rest whines under a
     * tamed 10-health body or pants otherwise, the rest barks) and the
     * ocelot's tamed family (OcelotEntity.getAmbientSound lines 111-121 —
     * the in-love purr, else a quarter purreow over the meow; the
     * untamed body stays silent). The guardian's own family reads the
     * water state live — the land pair, the elder's own, or the plain
     * water prefix.
     */
    public String effectiveIdleSound() {
        if (type == MobType.GUARDIAN) {
            return guardianSoundKind() + "idle";
        }
        if (type == MobType.WOLF) {
            if (wolfAngry()) {
                return "mob.wolf.growl";
            }
            if (random.nextInt(3) == 0) {
                return wolfTamed() && health() < 10.0f
                        ? "mob.wolf.whine" : "mob.wolf.panting";
            }
            return "mob.wolf.bark";
        }
        if (type == MobType.OCELOT) {
            if (!wolfTamed()) {
                return ""; // the untamed body stays silent
            }
            if (isInLove()) {
                return "mob.cat.purr";
            }
            return random.nextInt(4) == 0 ? "mob.cat.purreow" : "mob.cat.meow";
        }
        return type.idleSound;
    }

    /** @return and clears whether a skeleton shot is armed this tick. */
    public boolean consumePendingRangedShot() {
        boolean value = pendingRangedShot;
        pendingRangedShot = false;
        return value;
    }

    /** @return and clears whether a primed creeper should detonate this tick. */
    public boolean consumePendingExplosion() {
        boolean value = pendingExplosion;
        pendingExplosion = false;
        return value;
    }

    /** @return and clears whether a sheep's coat regrew this tick. */
    public boolean consumeRegrown() {
        boolean value = regrownThisTick;
        regrownThisTick = false;
        return value;
    }

    // ------------------------------------------------ mount API (horse/pig)

    /** @return whether this kind accepts a rider (the horse and the saddled pig). */
    public boolean isMountable() {
        return type == MobType.HORSE || type == MobType.PIG;
    }

    /** @return the tamed bit (pigs mount without taming, the vanilla rule). */
    public boolean tamed() {
        return type == MobType.PIG || (horseFlags & HORSE_FLAG_TAMED) != 0;
    }

    /** @return whether the saddle is on (the control gate for both kinds). */
    public boolean saddled() {
        return type == MobType.PIG ? pigSaddled : (horseFlags & HORSE_FLAG_SADDLED) != 0;
    }

    /** @return the raw index-16 horse flag word (the wire writer's view). */
    public int horseFlagsRaw() {
        return horseFlags;
    }

    /** @return whether the rear flag is up (the bucking animation). */
    public boolean rearing() {
        return (horseFlags & HORSE_FLAG_REARING) != 0;
    }

    /** @return the eating flag (the feeding graze flash). */
    public boolean eating() {
        return (horseFlags & HORSE_FLAG_EATING) != 0;
    }

    /** @return the coat variant (color | marking &lt;&lt; 8 — the index-20 int). */
    public int variant() {
        return variant;
    }

    /** @return the armor row (0 none — the index-22 int). */
    public int armorType() {
        return armorType;
    }

    /** @return the taming temper (0-100; the taming odds ride it). */
    public int temper() {
        return temper;
    }

    /** @return the tamer's name (the index-21 owner string, "" until tamed). */
    public String ownerName() {
        return ownerName;
    }

    /** @return whether the pig carries a saddle (the pig's own index-16 byte). */
    public boolean pigSaddled() {
        return pigSaddled;
    }

    /** @return the rider's engine id, or -1 when the seat is open. */
    public int riderId() {
        return riderId;
    }

    /** @return whether a rider holds the seat. */
    public boolean hasRider() {
        return riderId >= 0;
    }

    /** @return and clears whether the body must throw its rider this tick (buck flow). */
    public boolean consumeBuckThrow() {
        boolean value = pendingBuckThrow;
        pendingBuckThrow = false;
        return value;
    }

    /**
     * A mount attempt on the untamed horse rolls the vanilla temper flow:
     * temper rises, and the odds ride it. A pig or a tamed horse mounts
     * directly. The result tells the manager whether to seat, tame-and-seat,
     * or seat-and-buck.
     */
    public MountAttempt attemptMount() {
        if (type == MobType.PIG || tamed()) {
            return MountAttempt.MOUNTED;
        }
        temper = Math.min(100, temper + TEMPER_PER_ATTEMPT);
        if (random.nextInt(100) < temper) {
            tame("");
            return MountAttempt.TAMED;
        }
        // The buck: seated, rearing, thrown shortly (the vanilla feel).
        buckTicks = BUCK_THROW_TICKS + random.nextInt(20);
        horseFlags |= HORSE_FLAG_REARING;
        return MountAttempt.BUCKED;
    }

    /** Flips the tame bit and records the tamer (the hearts moment). */
    public void tame(String owner) {
        horseFlags |= HORSE_FLAG_TAMED;
        if (owner != null && !owner.isEmpty()) {
            ownerName = owner;
        }
    }

    /** Feeding raises the temper and flashes the eating flag (the graze). */
    public void feedTemper(int amount) {
        temper = Math.min(100, temper + amount);
        eatingTicks = 20;
        horseFlags |= HORSE_FLAG_EATING;
    }

    /** The horse's eating graze without a temper change ({@code setEating}). */
    public void flashEating() {
        eatingTicks = 20;
        horseFlags |= HORSE_FLAG_EATING;
    }

    // ------------------------------------------------ breeding + donkey chest

    /** The vanilla love window ({@code AnimalEntity.lovePlayer}: 600 ticks). */
    public static final int LOVE_DURATION_TICKS = 600;
    /** The parent cooldown after a successful breed ({@code setBreedingAge(6000)}). */
    public static final int ADULT_BREED_COOLDOWN_TICKS = 6000;
    /** The child's growing-up debt ({@code setBreedingAge(-24000)}: 20 minutes). */
    public static final int CHILDHOOD_TICKS = -24000;
    /** The ticks a pair stands within 3 blocks before the breed lands (AnimalBreedGoal). */
    public static final int BREED_PROXIMITY_TICKS = 60;
    /** The vanilla findMate scan radius (the grown-8 box). */
    public static final double BREED_MATE_SCAN_RADIUS = 8.0;
    /** The breed distance gate (squaredDistanceTo &lt; 9 — within 3 blocks). */
    public static final double BREED_DISTANCE_SQUARED = 9.0;
    /** The donkey chest's slot count: the vanilla getInventorySize's 17 is
     * saddle (0) + armor (1) + the 15-slot 3x5 chest grid (2-16, HorseMenu). */
    public static final int CHEST_SLOT_COUNT = 15;
    /** The chested horse flag ({@code setHorseFlag(8, hasChest)}). */
    public static final int HORSE_FLAG_CHESTED = 0x08;

    public int breedingAge() {
        return breedingAge;
    }

    public void setBreedingAge(int age) {
        this.breedingAge = age;
    }

    /** Whether the body is still a baby (a negative breeding age). */
    public boolean isBaby() {
        return breedingAge < 0;
    }

    /** The vanilla age tick: negative grows one tick toward 0, positive drains.
     * Crossing 0 raises the grew-up flag (the wire's age byte flips). */
    public void tickBreedingAge() {
        if (breedingAge < 0) {
            breedingAge++;
            if (breedingAge >= 0) {
                breedingAge = 0;
                grewUpThisTick = true;
            }
        } else if (breedingAge > 0) {
            breedingAge--;
        }
    }

    /** @return and clears whether the body became an adult this tick. */
    public boolean consumeGrewUp() {
        boolean value = grewUpThisTick;
        grewUpThisTick = false;
        return value;
    }

    /**
     * Feeding a baby shrinks its childhood ({@code PassiveEntity.growUp}):
     * the vanilla unit is 20 ticks (the wheat arm's j=20 grows 400 ticks),
     * so the age moves by {@code units * 20} and clamps at 0.
     */
    public void growUp(int units) {
        if (breedingAge < 0) {
            breedingAge = Math.min(0, breedingAge + units * 20);
        }
    }

    /** Enters love for the historical 600 ticks ({@code lovePlayer}). */
    public void enterLove(int playerId) {
        this.loveTicks = LOVE_DURATION_TICKS;
        this.lovePlayerId = playerId;
        this.pendingLoveBurst = true; // the doEntityEvent(18) heart burst
    }

    public boolean isInLove() {
        return loveTicks > 0;
    }

    /** @return the remaining love window (the tick-exact state). */
    public int loveTicksLeft() {
        return loveTicks;
    }

    public int lovePlayerId() {
        return lovePlayerId;
    }

    public void resetLove() {
        this.loveTicks = 0;
        this.lovePlayerId = -1;
        this.breedTimer = 0;
    }

    // --- the leash (Slice 11c — MobEntity's own leash arms) ---------------

    /** The player holding this mob's lead, or null (the knot form / free). */
    public net.zaminmc.torch.server.player.PlayerSession leashHolderPlayer() {
        return leashed ? leashHolderPlayer : null;
    }

    /** The knot holding this mob's lead, or null (the player form / free). */
    public net.zaminmc.torch.server.leash.LeashKnot leashHolderKnot() {
        return leashed ? leashHolderKnot : null;
    }

    /** @return whether the mob carries a live lead (isLeashed). */
    public boolean isLeashed() {
        return leashed;
    }

    /** The player's hold (attachLeash): one lead, one holder. */
    public void attachLeashToPlayer(net.zaminmc.torch.server.player.PlayerSession holder) {
        this.leashed = true;
        this.leashHolderPlayer = holder;
        this.leashHolderKnot = null;
    }

    /** The knot's hold (attachLeash): one lead, one holder. */
    public void attachLeashToKnot(net.zaminmc.torch.server.leash.LeashKnot holder) {
        this.leashed = true;
        this.leashHolderKnot = holder;
        this.leashHolderPlayer = null;
    }

    /** The state clear (detachLeash's first arm — the drop stays the system's). */
    public void clearLeash() {
        this.leashed = false;
        this.leashHolderPlayer = null;
        this.leashHolderKnot = null;
    }

    // --- the public §27.3 leash family (the LivingEntity handle's reads
    // over the state above — the engine object IS the handle, §27.5) ------

    @Override
    public boolean leashed() {
        return leashed;
    }

    @Override
    public net.zaminmc.torch.entity.LeashHolderKind leashHolderKind() {
        if (!leashed) {
            return net.zaminmc.torch.entity.LeashHolderKind.NONE;
        }
        return leashHolderPlayer != null
                ? net.zaminmc.torch.entity.LeashHolderKind.PLAYER
                : net.zaminmc.torch.entity.LeashHolderKind.KNOT;
    }

    @Override
    public java.util.Optional<java.util.UUID> leashHolderPlayerUuid() {
        net.zaminmc.torch.server.player.PlayerSession holder = leashHolderPlayer();
        return holder == null ? java.util.Optional.empty()
                : java.util.Optional.of(holder.uuid());
    }

    @Override
    public java.util.Optional<net.zaminmc.torch.block.BlockPosition> leashKnotPosition() {
        net.zaminmc.torch.server.leash.LeashKnot knot = leashHolderKnot();
        return knot == null ? java.util.Optional.empty()
                : java.util.Optional.of(knot.fence());
    }

    /** The leash system's detach hook, set at spawn (the public unleash path). */
    private java.util.function.Consumer<Boolean> leashDetacher;

    /** Called by the owning manager at spawn (and by the retro-wiring setter). */
    public void setLeashDetacher(java.util.function.Consumer<Boolean> detacher) {
        this.leashDetacher = detacher;
    }

    @Override
    public void unleash(boolean dropLead) {
        java.util.function.Consumer<Boolean> hook = leashDetacher;
        if (hook != null) {
            hook.accept(dropLead); // the system's walk: the clear + the wire + the drop
        } else {
            // The bare-wiring fallback (a manager without the leash
            // system attached): the state still clears — the reference's
            // own first arm.
            clearLeash();
        }
    }

    /**
     * The leash's navigation arm (PathFinderMobEntity line 96: the
     * {@code getNavigation().moveTo(entity, 1.0)} follow) — the walking
     * target the mob's own path walk consumes.
     */
    public void navigateToward(double x, double y, double z) {
        navigation.moveTo(x, y, z, type.walkSpeed);
    }

    /**
     * The love clock drain ({@code AnimalEntity.mobTick}): the decrement
     * first, then the every-10-ticks ambient heart particle (the vanilla
     * {@code inLoveTimer % 10 == 0} check runs on the post-decrement value).
     */
    public void tickLove() {
        if (loveTicks > 0) {
            loveTicks--;
            if (loveTicks % 10 == 0) {
                pendingAmbientHearts = true;
            }
            if (loveTicks <= 0) {
                resetLove();
            }
        }
    }

    /** @return and clears whether the love burst (event 18) is due. */
    public boolean consumePendingLoveBurst() {
        boolean value = pendingLoveBurst;
        pendingLoveBurst = false;
        return value;
    }

    /** @return and clears whether the ambient heart particle is due. */
    public boolean consumePendingAmbientHearts() {
        boolean value = pendingAmbientHearts;
        pendingAmbientHearts = false;
        return value;
    }

    /** The breed goal's landing: the mate confirmed at proximity. */
    public void requestBreedWith(MobEntity mate) {
        this.pendingBreedMate = mate;
    }

    /** @return and clears the confirmed mate (the manager executes the breed). */
    public MobEntity consumePendingBreed() {
        MobEntity mate = pendingBreedMate;
        pendingBreedMate = null;
        return mate;
    }

    /** Wires the manager's mate scanner (called once at spawn). */
    public void setBreedFinder(BreedFinder finder) {
        this.breedFinder = finder;
    }

    /** The goal's scan: the nearest legal partner (null when none/finderless). */
    MobEntity findMate() {
        return breedFinder == null ? null : breedFinder.findMate(this);
    }

    /** The mate-proximity clock ({@code AnimalBreedGoal.breedTimer}). */
    public int advanceBreedTimer() {
        return ++breedTimer;
    }

    public void resetBreedTimer() {
        breedTimer = 0;
    }

    /**
     * The vanilla canBreedWith (AnimalEntity + the HorseBaseEntity override):
     * a different body of the same kind, both in love, both adults. The
     * horse family adds {@code canBreed()}'s gates — tamed, unmounted, at
     * full health — and the mule never qualifies (its love never starts,
     * the belt here is the vanilla {@code noLove} rule made explicit).
     */
    public boolean canBreedWith(MobEntity other) {
        if (other == null || other == this || other.type() != this.type()) {
            return false;
        }
        if (!isInLove() || !other.isInLove()) {
            return false;
        }
        if (breedingAge != 0 || other.breedingAge != 0) {
            return false;
        }
        // WolfEntity.canBreedWith (and OcelotEntity's own — the same shape):
        // both bodies tamed, neither sitting.
        if (isTameableKind()) {
            if (!wolfTamed() || !other.wolfTamed()
                    || wolfSitting() || other.wolfSitting()) {
                return false;
            }
        }
        if (type == MobType.HORSE) {
            if (isMule() || other.isMule()) {
                return false;
            }
            if (!tamed() || !other.tamed()
                    || hasRider() || other.hasRider()) {
                return false;
            }
            if (health < type.maxHealth || other.health < other.type.maxHealth) {
                return false;
            }
        }
        return true;
    }

    public boolean isMule() {
        return type == MobType.HORSE && horseSubtype == HORSE_SUBTYPE_MULE;
    }

    public int horseSubtype() {
        return horseSubtype;
    }

    public void setHorseSubtype(int subtype) {
        this.horseSubtype = subtype;
    }

    /** Sets the coat directly (the group's shared color | the re-rolled marking). */
    public void setHorseVariant(int variant) {
        this.variant = variant;
    }

    /** Sets the body yaw (the spawn walks' setPositionAndAngles). */
    public void setBodyYaw(float yaw) {
        this.yaw = yaw;
    }

    /** @return the body's live yaw (the conversion's carry read). */
    public float bodyYawLive() {
        return yaw;
    }

    /** Whether a chest rides the donkey (flag 0x08, the vanilla rule). */
    public boolean chested() {
        return chested;
    }

    /** Equips or removes the chest ({@code setHasChest} — the flag + the slots). */
    public void setChested(boolean value) {
        this.chested = value;
        if (value) {
            horseFlags |= HORSE_FLAG_CHESTED;
            if (chestSlots == null) {
                chestSlots = new net.zaminmc.torch.item.ItemStack[CHEST_SLOT_COUNT];
                java.util.Arrays.fill(chestSlots, net.zaminmc.torch.item.ItemStack.EMPTY);
            }
        } else {
            horseFlags &= ~HORSE_FLAG_CHESTED;
            chestSlots = null;
        }
    }

    /**
     * The chest grid for the window click engine ({@link WindowClicks} plays
     * its historical cursor semantics straight onto it). Null when no chest.
     */
    public net.zaminmc.torch.item.ItemStack[] chestGrid() {
        return chested ? chestSlots : null;
    }

    /** @return the chest stack in the slot (or empty — the vanilla null). */
    public net.zaminmc.torch.item.ItemStack chestSlot(int index) {
        if (chestSlots == null || index < 0 || index >= CHEST_SLOT_COUNT) {
            return net.zaminmc.torch.item.ItemStack.EMPTY;
        }
        net.zaminmc.torch.item.ItemStack stack = chestSlots[index];
        return stack == null ? net.zaminmc.torch.item.ItemStack.EMPTY : stack;
    }

    /** Puts a stack in the chest slot. */
    public void setChestSlot(int index, net.zaminmc.torch.item.ItemStack stack) {
        if (chestSlots == null) {
            chestSlots = new net.zaminmc.torch.item.ItemStack[CHEST_SLOT_COUNT];
            java.util.Arrays.fill(chestSlots, net.zaminmc.torch.item.ItemStack.EMPTY);
        }
        chestSlots[index] = stack == null ? net.zaminmc.torch.item.ItemStack.EMPTY : stack;
    }

    /**
     * Spills the mount's worn gear and chest contents (the vanilla
     * dropInventoryAndChest on death): saddle, armor, chest, the stacks.
     */
    public java.util.List<net.zaminmc.torch.item.ItemStack> spillMountGear() {
        java.util.List<net.zaminmc.torch.item.ItemStack> dropped = new java.util.ArrayList<>();
        if (type == MobType.PIG) {
            if (pigSaddled) {
                dropped.add(net.zaminmc.torch.item.ItemStack.of(
                        net.zaminmc.torch.server.item.BuiltinItems.SADDLE, 1));
                pigSaddled = false;
            }
            return dropped;
        }
        if (saddled()) {
            dropped.add(net.zaminmc.torch.item.ItemStack.of(
                    net.zaminmc.torch.server.item.BuiltinItems.SADDLE, 1));
            horseFlags &= ~HORSE_FLAG_SADDLED;
        }
        if (armorType != HORSE_ARMOR_NONE) {
            dropped.add(switch (armorType) {
                case HORSE_ARMOR_DIAMOND -> net.zaminmc.torch.item.ItemStack.of(
                        net.zaminmc.torch.server.item.BuiltinItems.DIAMOND_HORSE_ARMOR, 1);
                case HORSE_ARMOR_GOLD -> net.zaminmc.torch.item.ItemStack.of(
                        net.zaminmc.torch.server.item.BuiltinItems.GOLDEN_HORSE_ARMOR, 1);
                default -> net.zaminmc.torch.item.ItemStack.of(
                        net.zaminmc.torch.server.item.BuiltinItems.IRON_HORSE_ARMOR, 1);
            });
            armorType = HORSE_ARMOR_NONE;
        }
        if (chested) {
            dropped.add(net.zaminmc.torch.item.ItemStack.of(
                    net.zaminmc.torch.server.item.BuiltinItems.CHEST, 1));
            if (chestSlots != null) {
                for (net.zaminmc.torch.item.ItemStack stack : chestSlots) {
                    if (stack != null && stack.type() != null) {
                        dropped.add(stack);
                    }
                }
            }
            setChested(false);
        }
        return dropped;
    }

    /** Equips the saddle (horse flags or the pig's own byte). */
    public void applySaddle() {
        if (type == MobType.PIG) {
            pigSaddled = true;
        } else {
            horseFlags |= HORSE_FLAG_SADDLED;
        }
    }

    /** Equips a horse armor row (the index-22 int, tamed horses only). */
    public void applyArmor(int type) {
        this.armorType = type;
    }

    /** Seats a rider (the engine id; inputs zeroed — the stale-rein guard). */
    public void setRider(int engineId) {
        this.riderId = engineId;
        steerSideways = 0;
        steerForward = 0;
        steerJump = false;
    }

    /** Opens the seat (the dismount; the mount wanders on). */
    public void clearRider() {
        this.riderId = -1;
        steerSideways = 0;
        steerForward = 0;
        steerJump = false;
    }

    /** The rider's rein input this tick (sideways turns, forward drives, jump hops). */
    public void steer(float sideways, float forward, boolean jump) {
        this.steerSideways = sideways;
        this.steerForward = forward;
        this.steerJump = jump;
    }

    /** @return the seat height above the mount's feet (the rider's body anchor). */
    public double seatHeight() {
        return type == MobType.PIG ? PIG_SEAT_HEIGHT : HORSE_SEAT_HEIGHT;
    }

    // ------------------------------------------------ villager API (the trade slice)

    /** @return the villager's profession (the index-16 Int, 0-4). */
    public int profession() {
        return profession;
    }

    // ------------------------------------- zombie-villager API (the conversion slice)

    /** @return whether the zombie wears the villager skin (the synced slot 13's byte). */
    public boolean zombieVillager() {
        return zombieVillager;
    }

    /**
     * setType (lines 154-156): the villager skin flag — the synced slot
     * 13's byte ({@code isVillager() ? 1 : 0}); the infection arm and the
     * 5% natural-spawn Data roll are the only setters.
     */
    public void setZombieVillager(boolean villager) {
        this.zombieVillager = villager;
    }

    /** @return whether the cure's countdown runs (isConverting — the synced slot 14's byte). */
    public boolean zombieConverting() {
        return ticksUntilConversion > -1;
    }

    /** @return the conversion countdown's remaining ticks (-1 when not converting). */
    public int zombieConversionTicks() {
        return ticksUntilConversion;
    }

    /**
     * setConversionTime (line 482): arms the cure's countdown. The
     * Weakness removal, the Strength pair, and the entity-event 16 all
     * ride the manager's own cure walk (the reference fires them around
     * this very call).
     */
    public void setZombieConversionTime(int time) {
        this.ticksUntilConversion = Math.max(0, time);
    }

    /**
     * tick's conversion walk (lines 236-243): the countdown advances every
     * tick by {@link #zombieConversionDecrement()}; the completing tick
     * hands the conversion to the manager (the spawn/remove pair is the
     * manager's own — convertToVillager).
     */
    private void tickZombieConversion() {
        ticksUntilConversion -= zombieConversionDecrement();
        if (ticksUntilConversion <= 0) {
            ticksUntilConversion = 0;
            pendingZombieConversionComplete = true;
        }
    }

    /**
     * getTicksUntilConversionDecrement (lines 540-563): normally one; a
     * 1%-per-tick roll scans the 8-cube around the body for iron bars and
     * beds — every found cell (up to 14 counted) adds a 30% chance of one
     * more decrement, the famous iron-bars cure accelerator.
     */
    private int zombieConversionDecrement() {
        int decrement = 1;
        if (random.nextFloat() < 0.01F) {
            int found = 0;
            int baseX = (int) position.x();
            int baseY = (int) position.y();
            int baseZ = (int) position.z();
            for (int x = baseX - 4; x < baseX + 4 && found < 14; x++) {
                for (int y = baseY - 4; y < baseY + 4 && found < 14; y++) {
                    for (int z = baseZ - 4; z < baseZ + 4 && found < 14; z++) {
                        if (world.isIronBarsOrBed(x, y, z)) {
                            if (random.nextFloat() < 0.3F) {
                                decrement++;
                            }
                            found++;
                        }
                    }
                }
            }
        }
        return decrement;
    }

    /** @return whether the conversion completed this tick (the manager consumes). */
    boolean consumeZombieConversionComplete() {
        if (pendingZombieConversionComplete) {
            pendingZombieConversionComplete = false;
            return true;
        }
        return false;
    }

    /** @return the villager's career (0 until the first open rolls it). */
    public int villagerCareer() {
        return villagerCareer;
    }

    /** @return the career's level (0 until the first open; grows per level-up). */
    public int villagerCareerLevel() {
        return villagerCareerLevel;
    }

    /**
     * getOffers (lines 633-639): the first consult rolls the career and its
     * opening level — the lazy stock the reference's {@code traderOffers ==
     * null} gate carries.
     */
    public TradeOffer[] offers() {
        if (villagerStock.isEmpty() && type == MobType.VILLAGER) {
            villagerLevelUp();
        }
        return villagerStock.toArray(new TradeOffer[0]);
    }

    /**
     * levelUp (lines 650-673): the first walk rolls the career
     * ({@code nextInt(careers) + 1}) at level 1; every later walk advances
     * the level and appends that level's rolled rows to the standing stock.
     */
    void villagerLevelUp() {
        if (villagerCareer != 0 && villagerCareerLevel != 0) {
            villagerCareerLevel++;
        } else {
            villagerCareer = random.nextInt(VillagerTrades.careerCount(profession)) + 1;
            villagerCareerLevel = 1;
        }
        for (VillagerTrades.TradeSource source : VillagerTrades.levelRows(
                profession, villagerCareer, villagerCareerLevel)) {
            TradeOffer offer = source.provide(random);
            villagerStock.add(offer);
            offerUses = java.util.Arrays.copyOf(offerUses, offerUses.length + 1);
            offerMaxUses = java.util.Arrays.copyOf(offerMaxUses, offerMaxUses.length + 1);
            offerMaxUses[offerMaxUses.length - 1] = offer.maxUses();
        }
    }

    /** @return the use counter of the offer at the index. */
    public int offerUses(int index) {
        if (index < 0 || index >= offerUses.length) {
            throw new IllegalArgumentException("Offer index out of range: " + index);
        }
        return offerUses[index];
    }

    /** @return the live use budget of the offer (the level-up's extension included). */
    public int offerMaxUses(int index) {
        if (index < 0 || index >= offerMaxUses.length) {
            throw new IllegalArgumentException("Offer index out of range: " + index);
        }
        return offerMaxUses[index];
    }

    /** @return whether the offer still trades (the use budget not spent out). */
    public boolean offerAvailable(int index) {
        if (index < 0 || index >= offerUses.length) {
            return false;
        }
        return offerUses[index] < offerMaxUses[index];
    }

    /**
     * The trade's own bookkeeping (VillagerEntity.trade lines 604-633): the
     * use charges, and on the first use or the 1-in-5 roll the 40-tick
     * level-up countdown arms with the flag; the emerald payments absorb
     * into the riches.
     *
     * @return the XP the trade pays out ({@code 3 + nextInt(4)}, the
     * level-up arm's +5 included) — the caller spawns the orb.
     */
    public int villagerTrade(int index) {
        if (index < 0 || index >= offerUses.length) {
            throw new IllegalArgumentException("Offer index out of range: " + index);
        }
        offerUses[index]++; // offer.use()
        int xp = 3 + random.nextInt(4);
        TradeOffer offer = villagerStock.get(index);
        if (offerUses[index] == 1 || random.nextInt(5) == 0) {
            villagerLevelUpCountdown = 40;
            villagerLevelUp = true;
            villagerWilling = true;
            xp += 5;
        }
        if (offer.buy1().type() == net.zaminmc.torch.server.item.BuiltinItems.EMERALD) {
            villagerRiches += offer.buy1().count();
        }
        return xp;
    }

    /** Charges one use off the offer (the execution's bookkeeping). */
    public void chargeOfferUse(int index) {
        if (index < 0 || index >= offerUses.length) {
            throw new IllegalArgumentException("Offer index out of range: " + index);
        }
        offerUses[index]++;
    }

    /** @return the villager's riches (the absorbed emerald payments). */
    public int villagerRiches() {
        return villagerRiches;
    }

    /**
     * The trade state's restore (Slice 25c — readCustomNbt lines 445-448:
     * Career/CareerLevel/Riches land exactly as saved; the career's stock
     * re-rolls lazily on the first open, the reference's own shape).
     * Package-private: the manager's restore walk is the only caller.
     */
    void restoreVillagerTradeState(int career, int careerLevel, int riches) {
        this.villagerCareer = career;
        this.villagerCareerLevel = careerLevel;
        this.villagerRiches = Math.max(0, riches);
    }

    /** @return whether the villager is willing to mate (the food arm's flag). */
    public boolean villagerWilling() {
        return villagerWilling;
    }

    /**
     * mobAiTick's level-up walk (VillagerEntity lines 377-405): with no
     * customer standing, the countdown burns down; at zero the armed
     * level-up lands — every SPENT-OUT offer's budget grows by
     * {@code nextInt(6) + nextInt(6) + 2} (the re-greens), the career's
     * next level appends — then the regeneration effect window (the
     * engine's mob-side effects ledger with the potion-on-mob family).
     * Called from the manager's tick (the villager branch).
     */
    public void tickVillagerLevelUp() {
        if (villagerLevelUpCountdown <= 0) {
            return;
        }
        villagerLevelUpCountdown--;
        if (villagerLevelUpCountdown > 0) {
            return;
        }
        if (villagerLevelUp) {
            for (int i = 0; i < offerUses.length; i++) {
                if (offerUses[i] >= offerMaxUses[i]) {
                    offerMaxUses[i] += random.nextInt(6) + random.nextInt(6) + 2;
                }
            }
            villagerLevelUp();
            villagerLevelUp = false;
            // The event-14 happy burst + the village reputation ride the
            // village system the engine does not carry (ledgered).
        }
        // The 200-tick Regeneration window rides the mob-side effect
        // family (the witch's own model) — ledgered.
    }

    /** @return whether this kind trades (the villager). */
    public boolean isTrader() {
        return type == MobType.VILLAGER;
    }

    /**
     * The ridden body: the seat physics (turn, drive, jump) without the AI
     * mind. Mirrors tickBody's ground/step rules so mounts walk slabs and
     * stop at walls like every other body; the fluid rules carry over too.
     */
    private void tickRiddenBody() {
        float turn = steerSideways * RIDDEN_TURN_RATE;
        if (turn != 0.0f) {
            yaw += turn;
            headYaw = yaw;
        }

        boolean inFluid = world.inFluid(position.x(), position.y() + 0.2, position.z());
        if (inFluid && fireTicks > 0) {
            extinguish();
        }

        // Drive: the manager feeds zero forward for an uncontrolled seat
        // (an unsaddled horse or a pig without the carrot on a stick).
        double walk = Math.abs(steerForward) > 0.01
                ? Math.signum(steerForward) * rideSpeed() : 0.0;
        if (inFluid) {
            walk *= 0.5; // swimming drag
        }
        if (steerJump && onGround && jumpCooldown == 0) {
            velocityY = type == MobType.PIG ? PIG_JUMP_IMPULSE : HORSE_JUMP_IMPULSE;
            jumpCooldown = RIDE_JUMP_COOLDOWN;
            onGround = false;
        }
        if (inFluid) {
            velocityY = Math.max(velocityY - GRAVITY_PER_TICK, -0.05) * 0.8;
        } else if (onGround) {
            velocityY = 0.0;
        } else {
            velocityY = (velocityY - GRAVITY_PER_TICK) * 0.98;
        }

        double radians = Math.toRadians(yaw);
        double moveX = velocityX - Math.sin(radians) * walk;
        double moveZ = velocityZ + Math.cos(radians) * walk;
        double damping = inFluid ? 0.8 : (onGround ? 0.6 : 0.98);
        velocityX *= damping;
        velocityZ *= damping;

        double newX = position.x() + moveX;
        double newY = position.y() + velocityY;
        double newZ = position.z() + moveZ;

        // Ground snap / step: the tickBody rules without the AI detour —
        // a step up when the shape allows, a stop at the fence/wall.
        if (world.isSolidAt(newX, position.y(), newZ)
                && !world.isSolidAt(newX, position.y() + 1.0, newZ)
                && onGround && Math.abs(velocityY) < 0.01) {
            newY = world.supportY(newX, position.y() + GROUND_EPSILON, newZ);
            if (newY - position.y() > 1.0 + GROUND_EPSILON
                    || world.isSolidAt(newX, newY, newZ)) {
                newX = position.x();
                newZ = position.z();
                newY = position.y();
            }
        } else if (world.isSolidAt(newX, position.y(), newZ)) {
            newX = position.x();
            newZ = position.z();
            newY = position.y();
        }

        if (velocityY <= 0 && world.isSolidAt(newX, newY - GROUND_EPSILON, newZ)) {
            newY = world.supportY(newX, newY - GROUND_EPSILON, newZ);
            velocityY = 0.0;
            onGround = true;
        } else {
            onGround = false;
        }
        position = new Position(newX, newY, newZ);
    }

    /** @return the ridden forward speed (0 when the mount answers no reins). */
    private double rideSpeed() {
        if (type == MobType.PIG) {
            return PIG_RIDE_SPEED;
        }
        return saddled() ? HORSE_RIDE_SPEED : 0.0;
    }

    // ------------------------------------------------ fire (the burning slice)

    /** @return ticks of burning left (0 = not on fire). */
    public int fireTicks() {
        return fireTicks;
    }

    /** @return true while the body is on fire (the living-flags bit 0x01). */
    public boolean burning() {
        return fireTicks > 0;
    }

    /** Arms the fire clock to at least {@code ticks} (the ignite rule). */
    public void ignite(int ticks) {
        if (ticks > fireTicks) {
            fireTicks = ticks;
        }
    }

    /** Douses the body (water contact, test seams). */
    public void extinguish() {
        fireTicks = 0;
        fireDamageTimer = 0;
    }

    /** Set when a sheep's coat regrew; consumed by the manager. */
    private boolean regrownThisTick;

    private void tickMind(boolean night) {
        modeTicks++;

        // The awareness goals' per-tick defaults clear before the goal walk:
        // the ocelot's postures only stand when the owning goal sets them
        // again this tick (the reference's mobAiTick reads the movement
        // control every tick — a body not walking a goal speed stands
        // normal), and the goal walk speed falls back to the mode band.
        postureSneaking = false;
        postureSprinting = false;
        goalWalkSpeed = Double.NaN;

        // The mob-vs-mob target state (Slice 16b) updates BEFORE the goal
        // walk — the stroll's yielding gate (goalHasTarget) reads the live
        // target, so the scan and the dead-target clear must land first.
        MobEntity mobTarget = tickMobTargeting();
        pendingScareWolf = type == MobType.SKELETON
                ? world.nearestMobOfKind(position.x(), position.y(), position.z(),
                        SKELETON_WOLF_FLEE_RANGE, MobType.WOLF)
                : null;

        Position target = world.nearestPlayer(position.x(), position.y(), position.z(),
                type.hostile ? AGGRO_RANGE : 8.0);
        boolean canHunt = (type.hostile || (type == MobType.RABBIT && rabbitKiller()))
                && (!type.traits.neutralByDay() || night)
                && (type != MobType.ZOMBIE_PIGMAN || isAngry()) // the neutral gate (16d)
                // The enderman's own provocation gate (19d — the reference's
                // LookAtPlayerGoal/takeDamage model): the neutral body hunts
                // only the stare or the wound provoked.
                && (type != MobType.ENDERMAN || endermanAngry);
        this.visibleTarget = target;
        this.targetHuntable = canHunt;

        // The enderman's provocation + carry (19d): the stare window and
        // the block arms run whatever the hunt state (the reference's own
        // goal family runs below the hunt's).
        if (type == MobType.ENDERMAN) {
            tickEndermanProvocation(canHunt && target != null);
            tickEndermanCarry();
        }

        // The angry pigman's own speed boost (the +0.05 modifier on the
        // 0.23 base — mobAiTick's ATTACKING_SPEED_BOOST).
        if (type == MobType.ZOMBIE_PIGMAN && isAngry() && target != null) {
            goalWalkSpeed = PIGMAN_ANGRY_CHASE_MULTIPLIER;
        }

        // The custom minds (16c/16e — the ghast, the squid, the bat):
        // their reference goal sets carry NO shared goals (no float, no
        // panic, no stroll — only their own SwimAround/MoveAround/roost
        // families), so they preempt the goal layer entirely.
        if (type == MobType.GHAST) {
            tickGhastMind(target, canHunt);
            return;
        }
        if (type == MobType.SQUID) {
            tickSquidMind();
            return;
        }
        if (type == MobType.BAT) {
            tickBatMind();
            return;
        }
        if (type == MobType.GUARDIAN) {
            tickGuardianMind(target, canHunt);
            return;
        }
        // The golem pair's own minds (17d — the reverse hunt): the iron
        // golem walks down the nearest hostile, the snow golem volleys it.
        if (type == MobType.IRON_GOLEM) {
            tickIronGolemMind(night);
            return;
        }
        if (type == MobType.SNOW_GOLEM) {
            tickSnowGolemMind();
            return;
        }
        // The blaze's own mind (18a — the AttackGoal family): the volley
        // cycle, the melee close-in, and the mobAiTick arms (the drown,
        // the eye-offset clock, the hover rise).
        if (type == MobType.BLAZE) {
            tickBlazeMind(target, canHunt);
            return;
        }

        // The goal layer (the community PathfinderGoal architecture): float
        // and panic claim the body first, the stroll cycle only when no hunt
        // is on. What remains below is the combat mind.
        if (goals.tick(this, night)) {
            return;
        }

        // The skeleton's flee-from-wolves (goal priority 3 over the attack
        // family's 4): a wolf within 6 blocks owns the movement whatever
        // else the skeleton was doing (Slice 16b).
        if (tickSkeletonWolfFlee()) {
            return;
        }

        // The mob-vs-mob target family (Slice 16b — the targetSelector's
        // single target): the hunts (the wolf's sheep/rabbits/skeletons,
        // the zombie's villagers) and the revenge/owner arms. The target
        // outranks the player hunt — setAttackTarget is the single write.
        // (The state itself updated before the goal walk above; the scan
        // ran there so the stroll's yielding gate could see it.)
        if (mobTarget != null) {
            tickMobTargetChase(mobTarget);
            return;
        }

        // The slime family's own goal family (Slice 12k): the
        // direction/chase walk and the contact damage replace the standard
        // mind — the hop movement lands in the body tick below.
        if (type.slimeFamily()) {
            tickSlimeMind(target, canHunt);
            return;
        }

        if (canHunt && target != null) {
            double dx = target.x() - position.x();
            double dy = target.y() - position.y();
            double dz = target.z() - position.z();
            double horizontal = Math.sqrt(dx * dx + dz * dz);

            // The enderman's far-target blink (the reference's
            // ChasePlayerGoal line 425): a target beyond 16 blocks for
            // 30 ticks blinks the body 16 blocks closer.
            if (type == MobType.ENDERMAN) {
                if (dx * dx + dy * dy + dz * dz > 256.0) {
                    if (++farFromTargetTicks >= 30) {
                        farFromTargetTicks = 0;
                        teleportToward(target);
                    }
                } else {
                    farFromTargetTicks = 0;
                }
            }

            if (mode == Mode.CHASE || mode == Mode.STRAFE || mode == Mode.FUSE) {
                // keep mode; the hunt continues
            } else {
                mode = Mode.CHASE;
                modeTicks = 0;
            }

            if (type.traits.explodes()) {
                tickCreeperMind(target, horizontal, dy);
                return;
            }
            if (type.traits.ranged() && !skeletonWither) {
                tickSkeletonMind(target, horizontal, dy);
                return;
            }
            // The wither variant's melee (the nether initialize lines
            // 218-220: the meleeAttackGoal replaces the archer's ranged
            // goal under the stone sword).

            // The melee hunter (zombie, night spider).
            mode = Mode.CHASE;
            yaw = (float) (Math.toDegrees(Math.atan2(-dx, dz)));
            headYaw = yaw;
            tickPathFollowing(target);
            // The killer bunny's own reach (EvilAttackGoal.getReach lines
            // 438-441: {@code 4.0 + target.width} against the SQUARED
            // distance — the plain rabbit's 3.0 swing never arms); the
            // strike's mob.attack sound rides the tryDamage override (lines
            // 255-258) — the pending flag the manager plays at the swing.
            boolean killerStrike = type == MobType.RABBIT && rabbitKiller();
            boolean inReach = killerStrike
                    ? dx * dx + dy * dy + dz * dz <= 4.0 + 0.6
                    : horizontal <= MELEE_ATTACK_RANGE && Math.abs(dy) <= MELEE_ATTACK_VERTICAL_RANGE;
            if (inReach && attackCooldown == 0) {
                attackCooldown = MELEE_ATTACK_COOLDOWN;
                // The manager hears the swing through the pending flag; the
                // attack lands through consumePendingMeleeAttack below.
                pendingMeleeAttack = true;
                if (killerStrike) {
                    pendingSlimeAttackSound = true; // mob.attack at the strike
                }
            }
            return;
        }

        if (mode == Mode.CHASE || mode == Mode.STRAFE || mode == Mode.FUSE) {
            mode = Mode.IDLE; // target lost (or daylight saved the spider)
            modeTicks = 0;
            clearPath();
        }
        // No hunt: the stroll goal owns the idle band this tick.
        goalTickIdleWander(target);
    }

    /**
     * The path-follow tick (the vanilla navigation port): the navigation
     * advances its walking index over the vanilla band (width²·offset, the
     * same-level prefix) with the direct-walk shortcut and the 100-tick
     * stuck check; the feet walk the heading to its current waypoint while
     * the eyes stay on the target. The route recomputes on the 20-30 tick
     * budget (both outcomes re-arm it); a failed search drops the route so
     * the wall-slide detour steering owns the gap between attempts.
     */
    private void tickPathFollowing(Position target) {
        if (repathCooldown > 0) {
            repathCooldown--;
        }
        navigation.tick();
        if (repathCooldown == 0) {
            repath(target);
        }
        if (navigation.hasTarget) {
            pathYaw = angleTo(navigation.currentTargetX - position.x(),
                    navigation.currentTargetZ - position.z());
            pathYawValid = true;
        } else {
            pathYawValid = false;
        }
    }

    /**
     * Recomputes the route (both outcomes re-arm the budget). The vanilla
     * flow: find, then moveAlong — a null route (nothing beyond the start)
     * drops the path and the detour fallback owns the block; a route with
     * the same node sequence keeps its walking index (the vanilla reset
     * rule).
     */
    private void repath(Position target) {
        repathCooldown = 20 + random.nextInt(10);
        navigation.moveTo(target.x(), target.y(), target.z(), type.walkSpeed);
    }

    private void clearPath() {
        navigation.stop();
        pathYawValid = false;
    }

    /** @return the path heading override the feet walk (or NaN when none). */
    boolean goalHasPathHeading() {
        return pathYawValid;
    }

    float goalPathYaw() {
        return pathYaw;
    }

    // ------------------------------------------------ goal surface (package-private)

    /** The nearest player this tick (the stroll goal's look-at anchor). */
    Position nearestVisiblePlayer() {
        return visibleTarget;
    }

    boolean goalInFluid() {
        return world.inFluid(position.x(), position.y() + 0.2, position.z());
    }

    void goalSwimUp() {
        velocityY = Math.min(velocityY + 0.05, 0.08);
        onGround = false;
    }

    boolean goalPanicking() {
        return panicTicks > 0;
    }

    /**
     * The breed drive's steering (the vanilla goal tick): the eyes on the
     * mate, the feet on the path to it — the same chase pairing, without
     * the attack arm.
     */
    void goalTickBreed(MobEntity mate) {
        double dx = mate.position().x() - position.x();
        double dz = mate.position().z() - position.z();
        yaw = (float) (Math.toDegrees(Math.atan2(-dx, dz)));
        headYaw = yaw;
        tickPathFollowing(mate.position());
    }

    void goalTickPanic() {
        panicTicks--;
        if (mode != Mode.PANIC) {
            mode = Mode.PANIC;
            modeTicks = 0;
        }
        if (modeTicks == 1) {
            yaw = randomDirection();
            headYaw = yaw;
        }
    }

    // ------------------------------------- the awareness goals (Slice 16a)

    /** TemptGoal's range: {@code getNearestPlayer(mob, 10.0)}. */
    private static final double TEMPT_RANGE = 10.0;
    /** The scared window: the player's move tolerance inside 6 blocks. */
    private static final double TEMPT_SCARED_DISTANCE_SQUARED = 0.010000000000000002;
    /** The scared rotation tolerance, degrees. */
    private static final double TEMPT_SCARED_ROTATION = 5.0;
    /** TemptGoal's stop() re-arm: the cat sulks 100 ticks before trying again. */
    private static final int TEMPT_DELAY = 100;
    /** The tempt's approach stop: within 2.5 blocks the feet hold. */
    private static final double TEMPT_STOP_DISTANCE_SQUARED = 6.25;
    /** The goal speed over the ocelot's 0.3 base: the creep at 0.6. */
    private static final double TEMPT_WALK_MULTIPLIER = 0.6 / 0.3;
    /** FleeEntityGoal's reach: players within 16 blocks scare the wild cat. */
    private static final double FLEE_PLAYER_RANGE = 16.0;
    /** The flee's close band: the scary player within 7 blocks sprints. */
    private static final double FLEE_CLOSE_SQUARED = 49.0;
    /** The flee's far speed over the base (0.8 on the 0.3 ocelot). */
    private static final double FLEE_FAR_MULTIPLIER = 0.8 / 0.3;
    /** The flee's close speed over the base (1.33 on the 0.3 — the sprint). */
    private static final double FLEE_CLOSE_MULTIPLIER = 1.33 / 0.3;
    /** WolfBegGoal's reach: the bone-holder's gaze window. */
    private static final double BEG_RANGE = 8.0;
    /** WolfBegGoal's timer: {@code 40 + nextInt(40)} ticks of begging. */
    private static final int BEG_TIMER_BASE = 40;

    /** @return whether the tempt goal is live (the ocelot's tame gate reads it). */
    public boolean temptActive() {
        return temptActive;
    }

    /**
     * TemptGoal's canStart/shouldContinue pair (the ocelot's fish-follow):
     * a fresh start finds the nearest player within 10 blocks holding the
     * raw fish — the delay gate first (100 ticks after a stop). A live goal
     * runs the scared check every tick: the player within 6 blocks may not
     * move beyond 0.01 blocks or turn beyond 5 degrees; the sample updates
     * beyond that band, and the rotation sample always does. A death on
     * any road applies the stop(): the delay re-arms and the feet release.
     */
    boolean goalTemptCanUse() {
        if (type == MobType.RABBIT) {
            // The rabbit's own TemptGoal family (lines 59-61: the carrot,
            // the golden carrot, the dandelion — each its own goal, all at
            // speed 1.0 and NOT scared by movement): the shared goal carries
            // the item set; the no-scared arm means shouldContinue is only
            // the canStart re-check.
            if (temptActive) {
                PlayerView view = world.nearestPlayerView(
                        position.x(), position.y(), position.z(), TEMPT_RANGE);
                if (view == null || !rabbitTemptItem(view.heldItem())) {
                    goalTemptStop();
                    return false;
                }
                return true;
            }
            if (temptDelay > 0) {
                temptDelay--;
                return false;
            }
            PlayerView view = world.nearestPlayerView(
                    position.x(), position.y(), position.z(), TEMPT_RANGE);
            if (view == null) {
                return false;
            }
            if (!rabbitTemptItem(view.heldItem())) {
                return false;
            }
            temptActive = true;
            return true;
        }
        if (type != MobType.OCELOT) {
            return false;
        }
        if (temptActive) {
            PlayerView view = world.nearestPlayerView(
                    position.x(), position.y(), position.z(), TEMPT_RANGE);
            if (view == null) {
                goalTemptStop();
                return false;
            }
            // shouldContinue's scared check (TemptGoal lines 52-72).
            double dx = view.position().x() - position.x();
            double dy = view.position().y() - position.y();
            double dz = view.position().z() - position.z();
            if (dx * dx + dy * dy + dz * dz < 36.0) {
                double px = view.position().x() - temptSampleX;
                double py = view.position().y() - temptSampleY;
                double pz = view.position().z() - temptSampleZ;
                if (px * px + py * py + pz * pz > TEMPT_SCARED_DISTANCE_SQUARED) {
                    goalTemptStop();
                    return false;
                }
                if (Math.abs(view.yaw() - temptSampleYaw) > TEMPT_SCARED_ROTATION
                        || Math.abs(view.pitch() - temptSamplePitch) > TEMPT_SCARED_ROTATION) {
                    goalTemptStop();
                    return false;
                }
            } else {
                temptSampleX = view.position().x();
                temptSampleY = view.position().y();
                temptSampleZ = view.position().z();
            }
            temptSampleYaw = view.yaw();
            temptSamplePitch = view.pitch();
            // The canStart re-check: the player still holds the fish.
            if (!"minecraft:fish".equals(view.heldItem())) {
                goalTemptStop();
                return false;
            }
            return true;
        }
        // canStart (lines 36-49): the delay gate, then the nearest player's
        // held item — the nearest player only, not the nearest fish-holder.
        if (temptDelay > 0) {
            temptDelay--;
            return false;
        }
        PlayerView view = world.nearestPlayerView(
                position.x(), position.y(), position.z(), TEMPT_RANGE);
        if (view == null) {
            return false;
        }
        if (!"minecraft:fish".equals(view.heldItem())) {
            return false;
        }
        // start(): the sample lands, the flag flips.
        temptActive = true;
        temptSampleX = view.position().x();
        temptSampleY = view.position().y();
        temptSampleZ = view.position().z();
        temptSampleYaw = view.yaw();
        temptSamplePitch = view.pitch();
        return true;
    }

    /** TemptGoal's stop(): the delay re-arms and the feet release. */
    private void goalTemptStop() {
        temptActive = false;
        temptDelay = TEMPT_DELAY;
        clearPath();
    }

    /** RabbitEntity.isTempting (lines 298-300): the three-item family. */
    private static boolean rabbitTemptItem(String heldItem) {
        return "minecraft:carrot".equals(heldItem)
                || "minecraft:golden_carrot".equals(heldItem)
                || "minecraft:dandelion".equals(heldItem);
    }

    /**
     * TemptGoal's tick (lines 95-102): the eyes stay on the player; the
     * feet walk the path at the creep speed unless the 2.5-block hold
     * band closes. The walk rides the goal-speed multiplier (2.0 — the
     * 0.6 over the 0.3 base), and the ocelot's mobAiTick posture turns
     * that walk into the shared-flags sneak.
     */
    void goalTemptTick() {
        if (type == MobType.RABBIT) {
            // The rabbit's own tick: the 1.0 goal speed over the band, no
            // posture (the walk stands normal), the same 2.5-block hold.
            PlayerView view = world.nearestPlayerView(
                    position.x(), position.y(), position.z(), TEMPT_RANGE);
            if (view == null) {
                return;
            }
            double dx = view.position().x() - position.x();
            double dy = view.position().y() - position.y();
            double dz = view.position().z() - position.z();
            headYaw = angleTo(dx, dz);
            double squared = dx * dx + dy * dy + dz * dz;
            if (squared < TEMPT_STOP_DISTANCE_SQUARED) {
                clearPath();
                goalWalkSpeed = 0.0;
                return;
            }
            goalWalkSpeed = 1.0 / 0.3; // the 1.0 goal parameter over the base
            mode = Mode.WANDER;
            modeTicks = 0;
            tickPathFollowing(view.position());
            return;
        }
        PlayerView view = world.nearestPlayerView(
                position.x(), position.y(), position.z(), TEMPT_RANGE);
        if (view == null) {
            return;
        }
        double dx = view.position().x() - position.x();
        double dy = view.position().y() - position.y();
        double dz = view.position().z() - position.z();
        headYaw = angleTo(dx, dz); // the look control's 30-degree grip
        double squared = dx * dx + dy * dy + dz * dz;
        if (squared < TEMPT_STOP_DISTANCE_SQUARED) {
            clearPath(); // the hold: the cat sits at the fish-holder's feet
            goalWalkSpeed = 0.0;
            postureSneaking = false; // the stopped walk stands normal (mobAiTick)
            return;
        }
        goalWalkSpeed = TEMPT_WALK_MULTIPLIER;
        postureSneaking = true; // mobAiTick: the 0.6 walk is the creep
        mode = Mode.WANDER;
        modeTicks = 0;
        tickPathFollowing(view.position());
    }

    /**
     * FleeEntityGoal's canStart (the untamed ocelot, players only): the
     * 16-block scare range — any live player inside it. The tamed cat
     * never flees (onTamedChanged removes the goal).
     */
    boolean goalFleePlayerCanUse() {
        return type == MobType.OCELOT && !wolfTamed() && world.nearestPlayerView(
                position.x(), position.y(), position.z(), FLEE_PLAYER_RANGE) != null;
    }

    /**
     * The rabbit's own flee (line 66: {@code FleeEntityGoal<>(this,
     * WolfEntity.class, 16.0F, 1.33, 1.33)}): any live wolf within 16
     * blocks sends the body running — BOTH speed bands 1.33 (the
     * reference's own pair). The killer bunny's setVariant(99) removes
     * this goal (the reference's removeGoal at line 322).
     */
    boolean goalFleeWolfCanUse() {
        return type == MobType.RABBIT && !rabbitKiller() && world.nearestMobOfKind(
                position.x(), position.y(), position.z(), FLEE_PLAYER_RANGE,
                MobType.WOLF) != null;
    }

    /** The rabbit's flee tick: directly away from the wolf at 1.33 both bands. */
    void goalFleeWolfTick() {
        MobEntity wolf = world.nearestMobOfKind(
                position.x(), position.y(), position.z(), FLEE_PLAYER_RANGE,
                MobType.WOLF);
        if (wolf == null) {
            return;
        }
        double dx = position.x() - wolf.position().x();
        double dz = position.z() - wolf.position().z();
        goalWalkSpeed = 1.33 / 0.3; // both bands share the 1.33 pair
        mode = Mode.PANIC;
        modeTicks = 0;
        yaw = angleTo(dx, dz); // directly away from the wolf
        headYaw = yaw;
    }

    /**
     * FleeEntityGoal's tick: away from the scary player — the far walk at
     * 0.8 over the base, the 7-block close band sprinting at 1.33 with the
     * shared-flags sprint bit (the ocelot's mobAiTick own postures).
     */
    void goalFleePlayerTick() {
        PlayerView view = world.nearestPlayerView(
                position.x(), position.y(), position.z(), FLEE_PLAYER_RANGE);
        if (view == null) {
            return;
        }
        double dx = position.x() - view.position().x();
        double dy = position.y() - view.position().y();
        double dz = position.z() - view.position().z();
        boolean close = dx * dx + dy * dy + dz * dz < FLEE_CLOSE_SQUARED;
        goalWalkSpeed = close ? FLEE_CLOSE_MULTIPLIER : FLEE_FAR_MULTIPLIER;
        if (close) {
            postureSprinting = true; // the 1.33 sprint bit
        }
        mode = Mode.PANIC; // the fleeing body: the panic walk, no grazing
        modeTicks = 0;
        yaw = angleTo(dx, dz); // directly away from the player
        headYaw = yaw;
    }

    /**
     * WolfBegGoal's isAttractive (lines 65-71): the untamed wolf begs at a
     * bone-holder; any taming state begs at its own breeding food (the meat
     * family — isBreedingItem's table).
     */
    private boolean begAttractive(String heldItem) {
        if (heldItem == null) {
            return false;
        }
        if (!wolfTamed() && heldItem.equals("minecraft:bone")) {
            return true;
        }
        return switch (heldItem) {
            case "minecraft:porkchop", "minecraft:cooked_porkchop", "minecraft:beef",
                    "minecraft:cooked_beef", "minecraft:chicken", "minecraft:cooked_chicken",
                    "minecraft:rabbit", "minecraft:cooked_rabbit" -> true;
            default -> false;
        };
    }

    /**
     * WolfBegGoal's canStart + shouldContinue pair: the nearest player
     * within 8 blocks holding an attractive item. A fresh start rolls the
     * timer ({@code 40 + nextInt(40)}) and raises the wire's begging flag;
     * the live window only continues while the timer, the reach, and the
     * attraction hold. The stop clears the flag (the manager's flip
     * detection publishes it).
     */
    boolean goalBegCanUse() {
        if (type != MobType.WOLF) {
            return false;
        }
        PlayerView view = world.nearestPlayerView(
                position.x(), position.y(), position.z(), BEG_RANGE);
        if (view == null || !begAttractive(view.heldItem())) {
            if (wolfBegging) {
                setWolfBegging(false); // stop(): the head comes up
            }
            return false;
        }
        double dx = view.position().x() - position.x();
        double dy = view.position().y() - position.y();
        double dz = view.position().z() - position.z();
        if (dx * dx + dy * dy + dz * dz > BEG_RANGE * BEG_RANGE) {
            if (wolfBegging) {
                setWolfBegging(false);
            }
            return false;
        }
        if (!wolfBegging) {
            setWolfBegging(true); // start(): the head tilts
            begTimer = BEG_TIMER_BASE + random.nextInt(BEG_TIMER_BASE);
        }
        return true;
    }

    /**
     * WolfBegGoal's tick: the head stays on the player's eyes while the
     * timer burns; the timer out (or the player leaving) is the stop.
     */
    void goalBegTick() {
        if (--begTimer <= 0) {
            setWolfBegging(false);
            return;
        }
        PlayerView view = world.nearestPlayerView(
                position.x(), position.y(), position.z(), BEG_RANGE);
        if (view != null) {
            headYaw = angleTo(view.position().x() - position.x(),
                    view.position().z() - position.z());
        }
    }

    /** @return the ocelot's living-flag posture bits: sneak 0x02 | sprint 0x10. */
    public int postureFlags() {
        return (postureSneaking ? 0x02 : 0) | (postureSprinting ? 0x10 : 0);
    }

    /** @return the tempt creep's sneak posture (the shared flags bit). */
    public boolean postureSneaking() {
        return postureSneaking;
    }

    /** @return the flee's sprint posture (the shared flags bit). */
    public boolean postureSprinting() {
        return postureSprinting;
    }

    // ------------------------------------- the mob-vs-mob targets (Slice 16b)

    /** ActiveTargetGoal's follow range: the generic attribute base 32. */
    private static final double MOB_TARGET_FOLLOW_RANGE = 32.0;
    /** ActiveTargetGoal's canStart cadence gate: a 1-in-10 scan. */
    private static final int HUNT_SCAN_CHANCE = 10;
    /** FleeEntityGoal's wolf scare for the skeleton: 6 blocks at 1.0/1.2. */
    private static final double SKELETON_WOLF_FLEE_RANGE = 6.0;
    /** The skeleton's flee speeds over its 0.25 base: the 1.0 far walk. */
    private static final double SKELETON_WOLF_FLEE_FAR = 1.0 / 0.25;
    /** The close sprint: the reference's 1.2. */
    private static final double SKELETON_WOLF_FLEE_CLOSE = 1.2 / 0.25;
    /** The melee reach against a mob target (the same band as the player). */
    private static final double MOB_MELEE_ATTACK_RANGE = 2.0;

    /** @return the live mob-vs-mob attack target, or null. */
    public MobEntity mobAttackTarget() {
        return mobAttackTarget;
    }

    /**
     * The single-target write ({@code setAttackTarget}): the wolf's angry
     * posture follows it — a wild wolf goes angry on any target, and the
     * target's death stands it down (WolfEntity.setAttackTarget lines
     * 88-94).
     */
    public void setMobAttackTarget(MobEntity target) {
        mobAttackTarget = target;
        if (type == MobType.WOLF) {
            if (target == null) {
                setWolfAngry(false);
            } else if (!wolfTamed()) {
                setWolfAngry(true);
            }
        }
    }

    /**
     * The hunt scan (ActiveTargetGoal.canStart over the target family):
     * the 1-in-10 cadence gate, then the nearest legal prey within the
     * follow range — the wolf's sheep/rabbits (the UNTAMED arm only, the
     * reference's UntamedActiveTargetGoal) and the skeleton feud (any
     * taming state), the zombie's villagers. A live target is kept until
     * it dies (the goal's own shouldContinue — no re-targeting mid-hunt).
     *
     * @return the mob the chase mind runs against this tick, or null.
     */
    MobEntity tickMobTargeting() {
        if (mobAttackTarget != null && mobAttackTarget.dead()) {
            setMobAttackTarget(null); // shouldContinue: the target died
        }
        if (mobAttackTarget == null && (type == MobType.WOLF || type == MobType.ZOMBIE
                || (type == MobType.RABBIT && rabbitKiller()))
                && random.nextInt(HUNT_SCAN_CHANCE) == 0) {
            MobEntity found = null;
            double best = MOB_TARGET_FOLLOW_RANGE * MOB_TARGET_FOLLOW_RANGE;
            if (type == MobType.WOLF) {
                // targetSelector 5 (ActiveTargetGoal SkeletonEntity): any
                // taming state; 4 (UntamedActiveTargetGoal AnimalEntity):
                // sheep or rabbit, the untamed arm only.
                MobEntity skeleton = world.nearestMobOfKind(
                        position.x(), position.y(), position.z(),
                        MOB_TARGET_FOLLOW_RANGE, MobType.SKELETON);
                if (skeleton != null) {
                    found = skeleton;
                } else if (!wolfTamed()) {
                    MobEntity sheep = world.nearestMobOfKind(
                            position.x(), position.y(), position.z(),
                            MOB_TARGET_FOLLOW_RANGE, MobType.SHEEP);
                    MobEntity rabbit = world.nearestMobOfKind(
                            position.x(), position.y(), position.z(),
                            MOB_TARGET_FOLLOW_RANGE, MobType.RABBIT);
                    if (sheep != null && rabbit != null) {
                        found = distanceSquaredTo(sheep) <= distanceSquaredTo(rabbit) ? sheep : rabbit;
                    } else {
                        found = sheep != null ? sheep : rabbit;
                    }
                }
            } else if (type == MobType.RABBIT) {
                // The killer bunny's own target family (setVariant lines
                // 325-326: ActiveTargetGoal over the wolf — the player half
                // rides the engine's player hunt above): any wolf within the
                // follow range.
                found = world.nearestMobOfKind(position.x(), position.y(), position.z(),
                        MOB_TARGET_FOLLOW_RANGE, MobType.WOLF);
            } else { // the zombie: targetSelector 2's villager hunt
                found = world.nearestMobOfKind(position.x(), position.y(), position.z(),
                        MOB_TARGET_FOLLOW_RANGE, MobType.VILLAGER);
            }
            if (found != null && !found.dead()) {
                setMobAttackTarget(found);
            }
        }
        return mobAttackTarget;
    }

    /** @return the squared distance to the other mob's position. */
    private double distanceSquaredTo(MobEntity other) {
        double dx = other.position().x() - position.x();
        double dy = other.position().y() - position.y();
        double dz = other.position().z() - position.z();
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * The chase mind's mob-target arm (MeleeAttackGoal's tick against the
     * single target): the eyes and the feet on the prey, the melee arm
     * inside the same reach band the player hunt uses.
     */
    private void tickMobTargetChase(MobEntity target) {
        double dx = target.position().x() - position.x();
        double dy = target.position().y() - position.y();
        double dz = target.position().z() - position.z();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        mode = Mode.CHASE;
        modeTicks = 0;
        yaw = angleTo(dx, dz);
        headYaw = yaw;
        tickPathFollowing(target.position());
        if (horizontal <= MOB_MELEE_ATTACK_RANGE && Math.abs(dy) <= MELEE_ATTACK_VERTICAL_RANGE
                && attackCooldown == 0) {
            attackCooldown = MELEE_ATTACK_COOLDOWN;
            pendingMeleeAttack = true;
        }
    }

    /**
     * The skeleton's flee-from-wolves (FleeEntityGoal(WolfEntity.class,
     * 6.0F, 1.0, 1.2) at goal priority 3 — above the ranged attack's own
     * 4): a wolf within 6 blocks owns the skeleton's movement whatever
     * else it was doing. The scare itself is computed before the goal walk
     * (the stroll yields through goalHasTarget's read of the pending
     * scare).
     */
    private boolean tickSkeletonWolfFlee() {
        MobEntity wolf = pendingScareWolf;
        if (type != MobType.SKELETON || wolf == null) {
            return false;
        }
        double dx = position.x() - wolf.position().x();
        double dy = position.y() - wolf.position().y();
        double dz = position.z() - wolf.position().z();
        boolean close = dx * dx + dy * dy + dz * dz < FLEE_CLOSE_SQUARED;
        goalWalkSpeed = close ? SKELETON_WOLF_FLEE_CLOSE : SKELETON_WOLF_FLEE_FAR;
        mode = Mode.PANIC;
        modeTicks = 0;
        yaw = angleTo(dx, dz); // directly away from the wolf
        headYaw = yaw;
        clearPath();
        return true;
    }

    /** Whether a hunt target is live this tick (the stroll goal yields):
     *  the player hunt, a live mob-vs-mob target (16b — the single
     *  attackTarget state), or the skeleton's pending wolf-flee (a wolf
     *  never skeleton-flees, so the wolf's angry-clear never sees the
     *  flee arm). */
    boolean goalHasTarget(boolean night) {
        return (targetHuntable && visibleTarget != null) || mobAttackTarget != null
                || pendingScareWolf != null;
    }

    void goalTickIdleWander(Position target) {
        if (mode == Mode.CHASE || mode == Mode.STRAFE || mode == Mode.FUSE) {
            mode = Mode.IDLE; // target lost (or daylight saved the spider)
            modeTicks = 0;
        }
        switch (mode) {
            case IDLE -> {
                // The historical LookAtPlayer: an idle mob glances at a
                // nearby player now and then — unless the beg owns the look
                // (WolfBegGoal claims the LOOK control for its whole window).
                if (!wolfBegging && target != null && modeTicks % 30 == 0 && random.nextInt(3) == 0) {
                    headYaw = angleTo(target.x() - position.x(), target.z() - position.z());
                }
                if (modeTicks >= IDLE_TICKS + random.nextInt(IDLE_TICKS)) {
                    mode = Mode.WANDER;
                    modeTicks = 0;
                    yaw = randomDirection();
                    headYaw = yaw;
                }
            }
            case WANDER -> {
                if (modeTicks >= WANDER_TICKS + random.nextInt(WANDER_TICKS)) {
                    mode = Mode.IDLE;
                    modeTicks = 0;
                }
            }
            default -> {
                mode = Mode.IDLE;
                modeTicks = 0;
            }
        }
    }

    /** The creeper goal: close, stop at arm's length, prime, detonate. */
    private void tickCreeperMind(Position target, double horizontal, double dy) {
        yaw = angleTo(target.x() - position.x(), target.z() - position.z());
        headYaw = yaw;
        if (mode == Mode.FUSE) {
            if (horizontal > CREEPER_ABORT_RANGE || Math.abs(dy) > CREEPER_ABORT_RANGE) {
                mode = Mode.IDLE; // the target slipped away: the fuse dies out
                modeTicks = 0;
                clearPath();
                return;
            }
            if (modeTicks >= CREEPER_FUSE_TICKS) {
                pendingExplosion = true;
            }
            return; // priming creepers stand still (the historical swell)
        }
        if (horizontal <= CREEPER_PRIME_RANGE && Math.abs(dy) <= CREEPER_PRIME_VERTICAL_RANGE) {
            mode = Mode.FUSE;
            modeTicks = 0;
            clearPath();
            return;
        }
        tickPathFollowing(target); // the feet route around walls, the eyes stay on
    }

    /** The skeleton goal: hold the shooting band, shoot on a clear line. */
    private void tickSkeletonMind(Position target, double horizontal, double dy) {
        yaw = angleTo(target.x() - position.x(), target.z() - position.z());
        headYaw = yaw;
        if (horizontal > SKELETON_TOO_FAR) {
            mode = Mode.CHASE; // close the gap
            tickPathFollowing(target);
        } else if (horizontal < SKELETON_TOO_CLOSE) {
            mode = Mode.WANDER; // re-rolled below as a retreat heading
            yaw = angleTo(position.x() - target.x(), position.z() - target.z());
            headYaw = yaw; // the body backs off, the eyes stay on the target
            clearPath();
        } else {
            mode = Mode.STRAFE; // in the band: hold and shoot
            clearPath();
        }
        if (attackCooldown == 0
                && horizontal <= SKELETON_SHOOT_RANGE
                && world.clearLine(eyePosition(), target)) {
            attackCooldown = SKELETON_ATTACK_COOLDOWN + random.nextInt(20);
            pendingRangedShot = true;
            rangedTarget = target;
        }
    }

    /** @return and clears whether the melee hunter should land a hit this tick. */
    public boolean consumePendingMeleeAttack() {
        boolean value = pendingMeleeAttack;
        pendingMeleeAttack = false;
        return value;
    }

    /** Set when a hunter's cooldown elapsed inside reach; consumed by the manager. */
    private boolean pendingMeleeAttack;

    /** @return the eye point of the body (the ranged origin). */
    public Position eyePosition() {
        return new Position(position.x(), position.y() + eyeHeight(), position.z());
    }

    /**
     * @return the body's height (the kind's own, or the slime family's
     * size-scaled {@code 0.51000005 * size}).
     */
    public double bodyHeight() {
        if (type.slimeFamily()) {
            return 0.51000005 * slimeSize;
        }
        if (type == MobType.GUARDIAN && guardianElder) {
            // setElder line 139: the elder's own 1.9975 hull.
            return GUARDIAN_ELDER_SIZE;
        }
        if (type == MobType.SKELETON && skeletonWither) {
            // setType line 296: the wither variant's taller hull.
            return SKELETON_WITHER_HEIGHT;
        }
        return type.height;
    }

    /**
     * @return the body's width (the kind's own, or the slime family's
     * size-scaled {@code 0.51000005 * size}).
     */
    public double bodyWidth() {
        if (type.slimeFamily()) {
            return 0.51000005 * slimeSize;
        }
        if (type == MobType.GUARDIAN && guardianElder) {
            // setElder line 139: the elder's own 1.9975 hull.
            return GUARDIAN_ELDER_SIZE;
        }
        if (type == MobType.SKELETON && skeletonWither) {
            // setType line 296: the wither variant's wider hull.
            return SKELETON_WITHER_WIDTH;
        }
        return type.width;
    }

    /**
     * @return the eye height (the generic 0.85 share of the body, or the
     * slime's own {@code 0.625 * height} — getEyeHeight line 208).
     */
    private double eyeHeight() {
        if (type.slimeFamily()) {
            return 0.625 * bodyHeight();
        }
        if (type == MobType.GUARDIAN) {
            // GuardianEntity.getEyeHeight line 234: the mid-body eye —
            // height * 0.5 (the elder's own hull scales it).
            return bodyHeight() * 0.5;
        }
        if (type == MobType.ENDERMITE) {
            // EndermiteEntity.getEyeHeight line 57: the flat 0.1F.
            return 0.1;
        }
        if (type == MobType.SNOW_GOLEM) {
            // SnowGolemEntity.getEyeHeight line 97: the flat 1.7F.
            return 1.7;
        }
        if (type == MobType.SKELETON) {
            // getEyeHeight line 314: the archer's own 1.74; the wither
            // variant keeps the generic share of its OWN taller hull
            // (2.535 * 0.85 — super.getEyeHeight reads the live size).
            return skeletonWither ? bodyHeight() * 0.85 : 1.74;
        }
        if (type == MobType.CAVE_SPIDER) {
            // CaveSpiderEntity.getEyeHeight line 53: the flat 0.45F — not
            // the generic 0.85 share (0.425) the default would give.
            return 0.45;
        }
        if (type == MobType.SILVERFISH) {
            // SilverfishEntity.getEyeHeight line 45: the flat 0.1F.
            return 0.1;
        }
        return type.height * 0.85;
    }

    private void tickBody() {
        // The void (1.8.8 EntityLivingBase): past the kill plane the
        // out-of-world damage lands every tick until death; the body keeps
        // falling because no block below the world is solid.
        if (position.y() < VOID_KILL_Y) {
            hurt(VOID_DAMAGE_PER_TICK);
        }
        // The ghast's flight (Slice 16c — FlyingMobEntity): the drift owns
        // the whole vertical + horizontal integration; no gravity, no
        // ground snap, no walk.
        if (type == MobType.GHAST && !dead) {
            tickGhastBody();
            integrateFlight();
            return;
        }
        // The bat's flight (Slice 16e — BatEntity.tick): the roost snap or
        // the hangPos wander; no gravity, no walk.
        if (type == MobType.BAT && !dead) {
            tickBatBody();
            return;
        }
        // The squid's swim (Slice 16e — SquidEntity.mobTick): the tentacle
        // stroke owns the velocity; the position integrates it directly
        // (no gravity, no walk).
        if (type == MobType.SQUID && !dead) {
            boolean squidInFluid = world.inFluid(position.x(), position.y() + 0.2, position.z());
            tickSquidBody(squidInFluid);
            position = new Position(position.x() + velocityX, position.y() + velocityY,
                    position.z() + velocityZ);
            onGround = false;
            return;
        }
        // The guardian's swim (Slice 17a — GuardianEntity.moveRelative +
        // the movement control): the sinusoidal water body or the land
        // flop owns the whole integration (no walk, no ground snap).
        if (type == MobType.GUARDIAN && !dead) {
            boolean guardianInFluid = world.inFluid(position.x(), position.y() + 0.2, position.z());
            tickGuardianBody(guardianInFluid);
            return;
        }
        // Fire contact arms the burn (the historical setFire on
        // collision) — the fire-immune kinds (immuneToFire) never ignite,
        // the wither variant joining them (setType line 291).
        if (!dead && !fireImmune()
                && world.inFire(position.x(), position.y(), position.z())) {
            ignite(FIRE_TICKS);
        }
        // Cactus contact pricks at the hurt-i-frame rhythm (10 ticks).
        if (!dead && world.touchingCactus(position.x(), position.y(), position.z())
                && ++cactusDamageTimer % 10 == 0) {
            hurt(1.0f);
        }
        // The blaze's fall dampening (BlazeEntity.mobTick lines 79-82): an
        // airborne falling body drifts at 60% per tick before the gravity
        // arm — the hover's descent stays slow (takeFallDamage's empty
        // override means the landing never hurts; the engine's mob body
        // carries no fall damage to suppress).
        if (type == MobType.BLAZE && !onGround && velocityY < 0.0) {
            velocityY *= 0.6;
        }
        // Vertical: gravity + ground snap (the item-entity model). A body in
        // fluid sinks slowly and bobs (the historical fluid buoyancy).
        boolean inFluid = world.inFluid(position.x(), position.y() + 0.2, position.z());
        if (inFluid && fireTicks > 0) {
            extinguish(); // the water douses the burn (the historical rule)
        }
        if (inFluid) {
            velocityY = Math.max(velocityY - GRAVITY_PER_TICK, -0.05) * 0.8;
        } else if (onGround && velocityY <= 0.0) {
            // A grounded body at rest carries no vertical speed — but a
            // POSITIVE write (the blaze's hover, an explosion's launch)
            // lifts it off the ground (the reference's own move
            // semantics: the ground only re-engages on a fall).
            velocityY = 0.0;
        } else {
            velocityY = (velocityY - GRAVITY_PER_TICK) * 0.98;
        }

        // Horizontal: knockback velocity decays; walking integrates directly.
        double walk = 0.0;
        if (mode == Mode.WANDER) {
            walk = type.walkSpeed;
        } else if (mode == Mode.PANIC) {
            walk = type.walkSpeed * PANIC_SPEED_MULTIPLIER;
        } else if (mode == Mode.CHASE) {
            walk = type.walkSpeed * (type.traits.ranged() ? 1.0 : 1.1);
        }
        if (witchDrink > 0) {
            walk = 0.0; // the drinking speed penalty (WitchEntity's -0.25 on the 0.25 base → 0)
        }
        if (!Double.isNaN(goalWalkSpeed)) {
            // The awareness goals' own speed (TemptGoal/FleeEntityGoal's
            // parameter over the movement attribute) overrides the band.
            walk = type.walkSpeed * goalWalkSpeed;
        }
        if (type == MobType.RABBIT) {
            // The rabbit's hop cycle owns the movement (the zeroed-input
            // model — the HOP action's cadence, the reference's own
            // MovementControl rule: grounded and not jumping means no walk).
            tickRabbitHop(inFluid);
            walk = 0.0;
        }
        if (type.slimeFamily()) {
            // The landing (SlimeEntity.tick lines 101-123): the first
            // grounded tick after a hop queues the landing sound — the
            // size > 2 rule for the slime, ALWAYS for the magma cube
            // (MagmaCubeEntity.makesLandSound) — the particles ride the
            // particle slice.
            if (onGround && !wasSlimeGrounded) {
                if (slimeSize > 2 || type == MobType.MAGMA_CUBE) {
                    pendingSlimeLandSound = new SlimeSound(slimeSoundName(),
                            0.4F * slimeSize,
                            (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F);
                }
                wasSlimeGrounded = true;
            } else if (!onGround) {
                wasSlimeGrounded = false;
            }
            tickSlimeMovement(inFluid);
            walk = 0.0; // the hop cycle owns the movement (the input model)
        }
        if (inFluid && walk > 0) {
            walk *= 0.5; // wading drag
        }
        double moveX = velocityX;
        double moveZ = velocityZ;
        if (walk > 0) {
            // A detour overrides the heading while it lasts (the wall slide);
            // the A* path heading owns the walk when no detour is live.
            float heading = detourTicks > 0 ? detourYaw
                    : (pathYawValid ? pathYaw : yaw);
            double radians = Math.toRadians(heading);
            moveX += -Math.sin(radians) * walk;
            moveZ += Math.cos(radians) * walk;
            if (detourTicks > 0) {
                detourTicks--;
            }
        }

        double damping = inFluid ? 0.8 : (onGround ? 0.6 : 0.98);
        velocityX *= damping;
        velocityZ *= damping;

        double newX = position.x() + moveX;
        double newY = position.y() + velocityY;
        double newZ = position.z() + moveZ;

        // Ground snap / step handling: walking mobs do not scale walls; a
        // collision shape in the path stops horizontal motion — or the body
        // steers around it (a half- or full-block step up first, then the
        // sideways detour). The step lands on the blocking cell's shape
        // surface (the slab half or the full cube), never above it: the
        // 1.5-tall fence refuses a step like a wall.
        if (world.isSolidAt(newX, position.y(), newZ)
                && !world.isSolidAt(newX, position.y() + 1.0, newZ)
                && onGround && Math.abs(velocityY) < 0.01) {
            newY = world.supportY(newX, position.y() + GROUND_EPSILON, newZ);
            if (newY - position.y() > 1.0 + GROUND_EPSILON // the fence rule
                    || world.isSolidAt(newX, newY, newZ)) {
                newX = position.x();
                newZ = position.z();
                newY = position.y();
            }
        } else if (world.isSolidAt(newX, position.y(), newZ)) {
            newX = position.x();
            newZ = position.z();
            newY = position.y();
            if (walk > 0 && detourTicks == 0 && onGround) {
                // Blocked mid-walk: slide along the wall (a 45-90° turn for
                // a handful of ticks) before the mind re-chooses. A hunting
                // body drops its stale route so the next mind tick repaths
                // around the obstacle (the A*-light trigger).
                clearPath();
                repathCooldown = 0;
                float side = random.nextBoolean() ? 45.0f : -45.0f;
                detourYaw = yaw + side + (random.nextFloat() * 45.0f - 22.5f);
                detourTicks = DETOUR_TICKS;
            }
        }

        if (velocityY <= 0 && world.isSolidAt(newX, newY - GROUND_EPSILON, newZ)) {
            newY = world.supportY(newX, newY - GROUND_EPSILON, newZ);
            velocityY = 0.0;
            onGround = true;
        } else {
            onGround = false;
        }

        position = new Position(newX, newY, newZ);
    }

    private float randomDirection() {
        return random.nextFloat() * 360.0f;
    }

    /** The 1.8 yaw of a facing vector (yaw grows clockwise; -Z is south 0). */
    private static float angleTo(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    private long fixedPoint() {
        return (Math.round(position.x() * 32.0) << 42)
                | (Math.round(position.y() * 32.0) << 21)
                | Math.round(position.z() * 32.0);
    }

    // ------------------------------------------------ navigation adapters

    /** The navigation's view of this body (the vanilla MobView surface). */
    private final class PathBody implements
            net.zaminmc.torch.server.entity.ai.pathing.MobView {
        @Override
        public double x() {
            return position.x();
        }

        @Override
        public double y() {
            return position.y();
        }

        @Override
        public double z() {
            return position.z();
        }

        @Override
        public double width() {
            return bodyWidth();
        }

        @Override
        public double height() {
            return bodyHeight();
        }

        @Override
        public boolean onGround() {
            return onGround;
        }

        @Override
        public boolean inWater() {
            return world.inFluid(position.x(), position.y() + 0.2, position.z());
        }

        @Override
        public boolean inLava() {
            return world.inLava(position.x(), position.y() + 0.2, position.z());
        }
    }

    /** The navigation's view of the world (the cell-material query). */
    private final class PathWorldView implements
            net.zaminmc.torch.server.entity.ai.pathing.PathWorld {
        @Override
        public net.zaminmc.torch.server.entity.ai.pathing.CellMaterial materialAt(int x, int y, int z) {
            return world.pathMaterialAt(x, y, z);
        }
    }
}
