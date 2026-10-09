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

        /** @return whether the block containing this point is fire. */
        default boolean inFire(double x, double y, double z) {
            return false;
        }

        /** @return whether the point's block (or the one below) is a cactus. */
        default boolean touchingCactus(double x, double y, double z) {
            return false;
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

    public MobEntity(int entityId, MobType type, Position position,
                     Random random, WorldQuery world) {
        this.entityId = entityId;
        this.type = Objects.requireNonNull(type, "type");
        this.position = Objects.requireNonNull(position, "position");
        this.random = Objects.requireNonNull(random, "random");
        this.world = Objects.requireNonNull(world, "world");
        this.health = type.maxHealth;
        this.modeTicks = random.nextInt(IDLE_TICKS);
        this.idleSoundTimer = 160 + random.nextInt(320); // first chatter 8-24 s out
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

    /** Applies a knockback impulse along the attacker's yaw (historical feel). */
    public void knockbackFrom(double attackerYaw) {
        double radians = Math.toRadians(attackerYaw);
        velocityX = -Math.sin(radians) * 0.4;
        velocityZ = Math.cos(radians) * 0.4;
        velocityY = 0.4;
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
        }
        // No hunt: the stroll goal owns the idle band this tick.
        goalTickIdleWander(target);
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
        }
    }

    /** The skeleton goal: hold the shooting band, shoot on a clear line. */
    private void tickSkeletonMind(Position target, double horizontal, double dy) {
        yaw = angleTo(target.x() - position.x(), target.z() - position.z());
        headYaw = yaw;
        if (horizontal > SKELETON_TOO_FAR) {
            mode = Mode.CHASE; // close the gap
        } else if (horizontal < SKELETON_TOO_CLOSE) {
            mode = Mode.WANDER; // re-rolled below as a retreat heading
            yaw = angleTo(position.x() - target.x(), position.z() - target.z());
            headYaw = yaw; // the body backs off, the eyes stay on the target
        } else {
            mode = Mode.STRAFE; // in the band: hold and shoot
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
            // A detour overrides the heading while it lasts (the wall slide).
            float heading = detourTicks > 0 ? detourYaw : yaw;
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
        // solid block ahead stops horizontal motion — or the body steers
        // around it (one-block step up first, then the sideways detour).
        if (world.isSolid(newX, position.y(), newZ)
                && !world.isSolid(newX, position.y() + 1.0, newZ)
                && onGround && Math.abs(velocityY) < 0.01) {
            newY = Math.floor(newY) + 1.0; // one-block step up, the historical auto-jump
            if (world.isSolid(newX, newY, newZ)) {
                newX = position.x();
                newZ = position.z();
                newY = position.y();
            }
        } else if (world.isSolid(newX, position.y(), newZ)) {
            newX = position.x();
            newZ = position.z();
            newY = position.y();
            if (walk > 0 && detourTicks == 0 && onGround) {
                // Blocked mid-walk: slide along the wall (a 45-90° turn for
                // a handful of ticks) before the mind re-chooses.
                float side = random.nextBoolean() ? 45.0f : -45.0f;
                detourYaw = yaw + side + (random.nextFloat() * 45.0f - 22.5f);
                detourTicks = DETOUR_TICKS;
            }
        }

        if (velocityY <= 0 && world.isSolid(newX, newY - GROUND_EPSILON, newZ)) {
            newY = Math.floor(newY - GROUND_EPSILON) + 1.0;
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
}
