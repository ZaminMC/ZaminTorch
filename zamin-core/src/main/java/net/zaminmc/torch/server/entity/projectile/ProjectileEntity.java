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

    /** The three projectiles the engine launches this slice. */
    public enum Kind {
        /** The bow's arrow: sticks where it lands, hurts what it hits. */
        ARROW(60),
        /** The snowball: no damage, knockback only, shatters on impact. */
        SNOWBALL(61),
        /** The egg: like the snowball, but may hatch a chick. */
        EGG(62);

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

    /**
     * One physics step. Returns the position integrated through (for block
     * collision sampling the manager segments the path between).
     */
    public Position integrate(double drag, double gravity) {
        if (!inGround) {
            vx *= drag;
            vy *= drag;
            vz *= drag;
            vy -= gravity;
            position = new Position(position.x() + vx, position.y() + vy, position.z() + vz);
            age++;
        } else {
            ticksInGround++;
        }
        return position;
    }
}
