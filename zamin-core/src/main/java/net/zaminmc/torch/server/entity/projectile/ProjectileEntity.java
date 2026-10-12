package net.zaminmc.torch.server.entity.projectile;

import net.zaminmc.torch.util.Position;

import java.util.Objects;

/**
 * One airborne projectile: an arrow, a thrown snowball or an egg.
 *
 * <p>State is simulation-owned (tick thread only). Physics mirrors the
 * historical thrown-entity loop: integrate motion, apply drag, apply gravity,
 * then resolve block and entity collisions. Arrows that land stick; the
 * shard-family projectiles (snowball, egg) shatter on any impact.</p>
 */
public final class ProjectileEntity {

    /** The projectiles the engine launches. */
    public enum Kind {
        /** The bow's arrow: sticks where it lands, hurts what it hits. */
        ARROW(60),
        /** The snowball: no damage, knockback only, shatters on impact. */
        SNOWBALL(61),
        /** The egg: like the snowball, but may hatch a chick. */
        EGG(62),
        /** The dispensed fire charge (Slice 11a): accelerates along its
         * direction, places fire where it lands, burns what it hits. */
        SMALL_FIREBALL(63),
        /** The ghast's charged fireball (Slice 16c — FireballEntity):
         * the same accelerating flight, the 6.0 direct hit plus the
         * power-1 explosion where it lands. */
        LARGE_FIREBALL(64),
        /** The thrown potion (Slice 11b): splashes its effect set where it
         * lands, carrying the potion metadata as the payload. */
        POTION(68),
        /** The ender pearl (Slice 17c — EnderPearlEntity): the thrower
         * teleports to the landing point with the 5.0 fall toll, and a
         * pearl-spawned endermite may ride (the 5% roll at the thrower's
         * pre-teleport feet). */
        ENDER_PEARL(65);

        /** Protocol 47's Spawn Object entity type id. */
        public final int objectType;

        Kind(int objectType) {
            this.objectType = objectType;
        }
    }

    /** The engine-global entity id (its own id band). */
    private final int entityId;
    private final Kind kind;
    /** The thrower's engine-global id (player session id or mob id), or -1. */
    private final int throwerId;
    private Position position;
    private double vx;
    private double vy;
    private double vz;
    /** Ticks alive airborne; age caps runaway projectiles. */
    private int age;
    /** Ticks a landed arrow has been stuck (its removal clock). */
    private int ticksInGround;
    /** True once the arrow struck a block and is waiting out its stick time. */
    private boolean inGround;
    /** The full-draw flag: the hit damage takes the reference's crit roll. */
    private boolean critical;
    /** The Punch level carried from the release (0 = none). */
    private int punchLevel;
    /** The Power bonus riding the damage multiplier (the j * 0.5 + 0.5 arm). */
    private double bonusDamage;
    /** The burning clock (Flame releases ignite 100 s — the whole flight). */
    private int fireTicks;
    /**
     * The potion projectile's payload (Slice 11b — the reference's
     * ItemStack): the potion metadata carrying the effect set. -1 marks
     * every non-potion kind.
     */
    private int potionDamage = -1;
    /**
     * The fireball family's per-tick acceleration (the reference
     * ProjectileEntity fields, the 6-double ctor's normalized*0.1 vector).
     * Zero for the ballistic kinds (arrows, shards); a fireball ignores
     * gravity and drag-steps at 0.95 instead.
     */
    private double accelerationX;
    private double accelerationY;
    private double accelerationZ;

    public ProjectileEntity(int entityId, Kind kind, int throwerId,
                            Position position, double vx, double vy, double vz) {
        this.entityId = entityId;
        this.kind = Objects.requireNonNull(kind, "kind");
        this.throwerId = throwerId;
        this.position = Objects.requireNonNull(position, "position");
        this.vx = vx;
        this.vy = vy;
        this.vz = vz;
    }

    /**
     * The fireball launch (the reference's 6-double ctor): starts at rest
     * and accelerates along the normalized direction scaled to 0.1 per
     * tick (ProjectileEntity lines 50-58).
     */
    public ProjectileEntity(int entityId, Kind kind, Position position,
                            double directionX, double directionY, double directionZ) {
        this(entityId, kind, -1, position, 0.0, 0.0, 0.0);
        setAccelerationFromDirection(directionX, directionY, directionZ);
    }

    /**
     * The fireball family's acceleration from a raw direction (the
     * reference's ctor normalize-and-scale walk, ProjectileEntity lines
     * 50-58): normalized, then scaled to the 0.1 per-tick push. The
     * shooter's own jitter lands on the components BEFORE this.
     */
    void setAccelerationFromDirection(double directionX, double directionY, double directionZ) {
        double length = Math.sqrt(directionX * directionX + directionY * directionY
                + directionZ * directionZ);
        if (length < 1.0E-4) {
            length = 1.0;
        }
        this.accelerationX = directionX / length * 0.1;
        this.accelerationY = directionY / length * 0.1;
        this.accelerationZ = directionZ / length * 0.1;
    }

    public int entityId() {
        return entityId;
    }

    public Kind kind() {
        return kind;
    }

    /** The thrower's engine-global id, or -1 when unknown. */
    public int throwerId() {
        return throwerId;
    }

    public Position position() {
        return position;
    }

    public double velocityX() {
        return vx;
    }

    public double velocityY() {
        return vy;
    }

    public double velocityZ() {
        return vz;
    }

    /** |v| at launch (the bow's damage scales with it); recomputed on demand. */
    public double launchSpeed() {
        return Math.sqrt(vx * vx + vy * vy + vz * vz);
    }

    public int age() {
        return age;
    }

    public int ticksInGround() {
        return ticksInGround;
    }

    public boolean inGround() {
        return inGround;
    }

    /** Marks the arrow as stuck (it stops moving and starts its removal clock). */
    public void landInGround() {
        this.inGround = true;
        this.vx = 0;
        this.vy = 0;
        this.vz = 0;
    }

    /** @return whether the full-draw flag rides (the hit's crit roll). */
    public boolean critical() {
        return critical;
    }

    /** Sets the full-draw crit flag (the release walk's setCritical arm). */
    public void setCritical(boolean critical) {
        this.critical = critical;
    }

    public int punchLevel() {
        return punchLevel;
    }

    public void setPunchLevel(int punchLevel) {
        this.punchLevel = punchLevel;
    }

    public double bonusDamage() {
        return bonusDamage;
    }

    /** Adds the Power bonus to the damage multiplier (the setDamage arm). */
    public void addBonusDamage(double bonus) {
        this.bonusDamage += bonus;
    }

    /** @return whether the arrow is burning (the Flame release, the visual flag). */
    public boolean burning() {
        return fireTicks > 0;
    }

    /** The potion payload (the metadata carrying the effect set), or -1. */
    public int potionDamage() {
        return potionDamage;
    }

    /** Attaches the potion payload (the POTION kind's flight data). */
    public void setPotionDamage(int damage) {
        this.potionDamage = damage;
    }

    /** Ignites the arrow for the given tick count (the reference's setOnFireFor). */
    public void setOnFireFor(int ticks) {
        this.fireTicks = ticks;
    }

    /** One burning-clock step (the manager's tick calls it per pass). */
    public void tickFire() {
        if (fireTicks > 0) {
            fireTicks--;
        }
    }

    /**
     * One physics step. Returns the position integrated through (for block
     * collision sampling the manager segments the path between).
     */
    public Position integrate(double drag, double gravity) {
        if (!inGround) {
            // The fireball family's own tick arm (the reference
            // ProjectileEntity.tick lines 186-191): the velocity picks up the
            // acceleration BEFORE the drag term — a fireball starts at rest
            // and speeds toward v_inf = a*drag/(1-drag). The ballistic kinds
            // carry zero acceleration and keep the plain gravity term.
            vx = (vx + accelerationX) * drag;
            vy = (vy + accelerationY) * drag;
            vz = (vz + accelerationZ) * drag;
            if (accelerationX == 0.0 && accelerationY == 0.0 && accelerationZ == 0.0) {
                vy -= gravity;
            }
            position = new Position(position.x() + vx, position.y() + vy, position.z() + vz);
            age++;
        } else {
            ticksInGround++;
        }
        return position;
    }
}
