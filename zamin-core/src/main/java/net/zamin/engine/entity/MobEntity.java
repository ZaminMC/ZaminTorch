package net.zamin.engine.entity;

import net.zamin.api.Position;

import java.util.Objects;
import java.util.Random;

/**
 * One living mob in the world: identity, body, mind and the timers that shape
 * its behavior. The simulation owns it; the wire only observes.
 *
 * <p>AI is deliberately small and deterministic-under-seeded-random: passive
 * kinds wander (idle, then pick a direction and walk) and panic when hurt;
 * the zombie chases the nearest player within its aggro range and attacks in
 * melee with a cooldown. Physics reuses the item-entity model (gravity,
 * drag, epsilon-correct ground snap) and stays marked for the physics slice
 * (horizontal motion here is direct walk-integration, not impulse physics).</p>
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
    /** Zombie aggro radius in blocks (historical follow range). */
    public static final double ZOMBIE_AGGRO_RANGE = 16.0;
    /** Zombie melee reach in blocks (horizontal, center to center-ish). */
    public static final double ZOMBIE_ATTACK_RANGE = 1.6;
    /** Vertical spread the zombie tolerates when reaching. */
    public static final double ZOMBIE_ATTACK_VERTICAL_RANGE = 2.0;
    /** Historical easy-difficulty zombie attack damage (EntityZombie easy). */
    public static final float ZOMBIE_ATTACK_DAMAGE = 2.0f;
    /** Attack cooldown in ticks (historical attack delay). */
    public static final int ZOMBIE_ATTACK_COOLDOWN = 20;
    /** Panic speed multiplier over the kind's walk speed. */
    public static final double PANIC_SPEED_MULTIPLIER = 1.8;
    /** Position epsilon so ground queries see the block *below* the feet. */
    private static final double GROUND_EPSILON = 1.0E-7;

    /** Minimal world query the mob needs (tick-thread context only). */
    public interface WorldQuery {
        /** @return whether the block containing this point is solid. */
        boolean isSolid(double x, double y, double z);

        /** @return the nearest playing player's position within {@code range} blocks, or null. */
        Position nearestPlayer(double x, double y, double z, double range);
    }

    /** What the mob is doing (the small vanilla-style goal set of this slice). */
    public enum Mode {
        IDLE,
        WANDER,
        PANIC,
        CHASE
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
        onGround = false;
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
        if (!type.hostile) {
            panicTicks = PANIC_TICKS;
            mode = Mode.PANIC;
            modeTicks = 0;
        }
    }

    /**
     * Advances one tick: timers, mind (goal selection), then body (physics).
     * Returns true when the fixed-point position or rotation changed, so the
     * publisher decides to sync.
     */
    public boolean tick() {
        long before = fixedPoint();
        float beforeYaw = yaw;

        if (hurtFlash > 0) {
            hurtFlash--;
        }
        if (attackCooldown > 0) {
            attackCooldown--;
        }

        if (dead) {
            deathTicks++;
            velocityX = 0;
            velocityZ = 0;
            velocityY = 0;
            return fixedPoint() != before || beforeYaw != yaw;
        }

        tickMind();
        tickBody();
        return fixedPoint() != before || beforeYaw != yaw;
    }

    /** @return true when the mob produced an idle chatter sound this tick.
     * The roll re-arms itself; call exactly once per tick after {@link #tick()}.
     */
    public boolean idleSoundDue() {
        if (idleSoundTimer > 0) {
            idleSoundTimer--;
            return false;
        }
        if (!dead) {
            idleSoundTimer = 160 + random.nextInt(320); // 8-24 s
            return true;
        }
        return false;
    }

    private void tickMind() {
        modeTicks++;

        Position target = world.nearestPlayer(position.x(), position.y(), position.z(),
                type.hostile ? ZOMBIE_AGGRO_RANGE : 8.0);

        if (panicTicks > 0) {
            panicTicks--;
            if (mode != Mode.PANIC) {
                mode = Mode.PANIC;
                modeTicks = 0;
            }
            if (modeTicks == 1) {
                yaw = randomDirection();
                headYaw = yaw;
            }
            return;
        }

        if (type.hostile && target != null) {
            double dx = target.x() - position.x();
            double dz = target.z() - position.z();
            double dy = target.y() - position.y();
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            mode = Mode.CHASE;
            yaw = (float) (Math.toDegrees(Math.atan2(-dx, dz)));
            headYaw = yaw;
            if (horizontal <= ZOMBIE_ATTACK_RANGE && Math.abs(dy) <= ZOMBIE_ATTACK_VERTICAL_RANGE
                    && attackCooldown == 0) {
                attackCooldown = ZOMBIE_ATTACK_COOLDOWN;
                // The manager hears the swing through the returned tick result;
                // the attack lands through attackScheduled below.
                pendingAttack = true;
            }
            return;
        }

        if (mode == Mode.CHASE) {
            mode = Mode.IDLE;
            modeTicks = 0;
        }

        switch (mode) {
            case IDLE -> {
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

    /** Set when the zombie's cooldown elapsed inside reach; consumed by the manager. */
    private boolean pendingAttack;

    /** @return and clears whether the zombie should land a melee attack this tick. */
    public boolean consumePendingAttack() {
        boolean value = pendingAttack;
        pendingAttack = false;
        return value;
    }

    private void tickBody() {
        // Vertical: gravity + ground snap (the item-entity model).
        if (onGround) {
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
            walk = type.walkSpeed * 1.1;
        }
        double moveX = velocityX;
        double moveZ = velocityZ;
        if (walk > 0) {
            double radians = Math.toRadians(yaw);
            moveX += -Math.sin(radians) * walk;
            moveZ += Math.cos(radians) * walk;
        }

        double damping = onGround ? 0.6 : 0.98;
        velocityX *= damping;
        velocityZ *= damping;

        double newX = position.x() + moveX;
        double newY = position.y() + velocityY;
        double newZ = position.z() + moveZ;

        // Ground snap / step handling: walking mobs do not scale walls; a solid
        // block ahead stops horizontal motion (wall check at feet level).
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

    private long fixedPoint() {
        return (Math.round(position.x() * 32.0) << 42)
                | (Math.round(position.y() * 32.0) << 21)
                | Math.round(position.z() * 32.0);
    }
}
