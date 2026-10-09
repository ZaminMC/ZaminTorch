package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.util.Position;

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
public final class MobEntity {

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

        /** @return whether the block containing this point is lava. */
        default boolean inLava(double x, double y, double z) {
            return false;
        }

        /** @return whether the block containing this point is fire. */
        default boolean inFire(double x, double y, double z) {
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
    private Position visibleTarget;
    private boolean targetHuntable;

    // ------------------------------------------------ mounts (the horse/pig slice)

    /** The horse flag bits (the 1.8 DataWatcher index-16 Int, ProtocolSupport/Glowstone-verified). */
    public static final int HORSE_FLAG_TAMED = 0x02;
    public static final int HORSE_FLAG_SADDLED = 0x04;
    public static final int HORSE_FLAG_EATING = 0x20;
    public static final int HORSE_FLAG_REARING = 0x40;
    public static final int HORSE_FLAG_MOUTH_OPEN = 0x80;

    /** Horse subtypes (the 1.8 DataWatcher index-19 Byte): horse, donkey. */
    public static final int HORSE_SUBTYPE_HORSE = 0;
    public static final int HORSE_SUBTYPE_DONKEY = 1;

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

    // ------------------------------------------------ the villager's trade state

    /** The villager's profession (the DataWatcher index-16 Int, 0-4). */
    private int profession;
    /** The villager's stock offers (the trade list the client renders). */
    private TradeOffer[] offers = new TradeOffer[0];
    /** The use counters, parallel to {@link #offers} (the grey-out budget). */
    private int[] offerUses = new int[0];

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
        // The herd look: roughly one donkey in five, plain coats dominating
        // (the historical variant split; colors 0-6, markings 0-3).
        if (type == MobType.HORSE) {
            this.horseSubtype = random.nextInt(100) < 20
                    ? HORSE_SUBTYPE_DONKEY : HORSE_SUBTYPE_HORSE;
            int color = random.nextInt(7);
            int marking = random.nextInt(10) < 6 ? 0 : 1 + random.nextInt(3);
            this.variant = color | (marking << 8);
        }
        // The villager's career and stock: profession 0-4 (the DataWatcher
        // Int), the offers from the fixed career table (the trade slice).
        if (type == MobType.VILLAGER) {
            this.profession = random.nextInt(5);
            java.util.List<TradeOffer> stock = VillagerTrades.offersFor(profession);
            this.offers = stock.toArray(new TradeOffer[0]);
            this.offerUses = new int[offers.length];
        }
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

    public boolean onGround() {
        return onGround;
    }

    public Mode mode() {
        return mode;
    }

    public boolean dead() {
        return dead;
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

        if (hurtFlash > 0) {
            hurtFlash--;
        }
        if (attackCooldown > 0) {
            attackCooldown--;
        }
        // The fire clock: the historical 1 damage per second while burning.
        // Dead bodies stop burning (the death animation freezes the body).
        if (!dead && fireTicks > 0) {
            fireTicks--;
            if (++fireDamageTimer >= 20) {
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
        return fixedPoint() != before || beforeYaw != yaw || beforeHeadYaw != headYaw;
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
        if (!dead && !type.idleSound.isEmpty()) {
            idleSoundTimer = 160 + random.nextInt(320); // 8-24 s
            return true;
        }
        return false;
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

    /** @return the horse subtype (0 horse, 1 donkey — the index-19 byte). */
    public int horseSubtype() {
        return horseSubtype;
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

    /** @return the villager's stock offers (the trade list the client renders). */
    public TradeOffer[] offers() {
        return offers;
    }

    /** @return the use counter of the offer at the index. */
    public int offerUses(int index) {
        if (index < 0 || index >= offerUses.length) {
            throw new IllegalArgumentException("Offer index out of range: " + index);
        }
        return offerUses[index];
    }

    /** @return whether the offer still trades (the use budget not spent out). */
    public boolean offerAvailable(int index) {
        if (index < 0 || index >= offers.length) {
            return false;
        }
        return offerUses[index] < offers[index].maxUses();
    }

    /** Charges one use off the offer (the execution's bookkeeping). */
    public void chargeOfferUse(int index) {
        if (index < 0 || index >= offerUses.length) {
            throw new IllegalArgumentException("Offer index out of range: " + index);
        }
        offerUses[index]++;
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

        Position target = world.nearestPlayer(position.x(), position.y(), position.z(),
                type.hostile ? AGGRO_RANGE : 8.0);
        boolean canHunt = type.hostile && (!type.traits.neutralByDay() || night);
        this.visibleTarget = target;
        this.targetHuntable = canHunt;

        // The goal layer (the community PathfinderGoal architecture): float
        // and panic claim the body first, the stroll cycle only when no hunt
        // is on. What remains below is the combat mind.
        if (goals.tick(this, night)) {
            return;
        }

        if (canHunt && target != null) {
            double dx = target.x() - position.x();
            double dy = target.y() - position.y();
            double dz = target.z() - position.z();
            double horizontal = Math.sqrt(dx * dx + dz * dz);

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
            if (type.traits.ranged()) {
                tickSkeletonMind(target, horizontal, dy);
                return;
            }

            // The melee hunter (zombie, night spider).
            mode = Mode.CHASE;
            yaw = (float) (Math.toDegrees(Math.atan2(-dx, dz)));
            headYaw = yaw;
            tickPathFollowing(target);
            if (horizontal <= MELEE_ATTACK_RANGE && Math.abs(dy) <= MELEE_ATTACK_VERTICAL_RANGE
                    && attackCooldown == 0) {
                attackCooldown = MELEE_ATTACK_COOLDOWN;
                // The manager hears the swing through the pending flag; the
                // attack lands through consumePendingMeleeAttack below.
                pendingMeleeAttack = true;
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

    /** Whether a hunt target is live this tick (the stroll goal yields). */
    boolean goalHasTarget(boolean night) {
        return targetHuntable && visibleTarget != null;
    }

    void goalTickIdleWander(Position target) {
        if (mode == Mode.CHASE || mode == Mode.STRAFE || mode == Mode.FUSE) {
            mode = Mode.IDLE; // target lost (or daylight saved the spider)
            modeTicks = 0;
        }
        switch (mode) {
            case IDLE -> {
                // The historical LookAtPlayer: an idle mob glances at a
                // nearby player now and then.
                if (target != null && modeTicks % 30 == 0 && random.nextInt(3) == 0) {
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
        return new Position(position.x(), position.y() + type.height * 0.85, position.z());
    }

    private void tickBody() {
        // The void (1.8.8 EntityLivingBase): past the kill plane the
        // out-of-world damage lands every tick until death; the body keeps
        // falling because no block below the world is solid.
        if (position.y() < VOID_KILL_Y) {
            hurt(VOID_DAMAGE_PER_TICK);
        }
        // Fire contact arms the burn (the historical setFire on collision).
        if (!dead && world.inFire(position.x(), position.y(), position.z())) {
            ignite(FIRE_TICKS);
        }
        // Cactus contact pricks at the hurt-i-frame rhythm (10 ticks).
        if (!dead && world.touchingCactus(position.x(), position.y(), position.z())
                && ++cactusDamageTimer % 10 == 0) {
            hurt(1.0f);
        }
        // Vertical: gravity + ground snap (the item-entity model). A body in
        // fluid sinks slowly and bobs (the historical fluid buoyancy).
        boolean inFluid = world.inFluid(position.x(), position.y() + 0.2, position.z());
        if (inFluid && fireTicks > 0) {
            extinguish(); // the water douses the burn (the historical rule)
        }
        if (inFluid) {
            velocityY = Math.max(velocityY - GRAVITY_PER_TICK, -0.05) * 0.8;
        } else if (onGround) {
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
            return type.width;
        }

        @Override
        public double height() {
            return type.height;
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
