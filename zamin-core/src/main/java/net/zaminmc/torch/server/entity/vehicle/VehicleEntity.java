package net.zaminmc.torch.server.entity.vehicle;

import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;

import java.util.Objects;
import java.util.Random;

/**
 * A rideable vehicle in the world: the boat and the minecart share the
 * body (position, velocity, heading, health, the one passenger seat) and
 * the simplified physics frame (gravity + ground snap, the item-entity
 * model); the kind subclasses own their motion rules.
 *
 * <p>Engine state owned by the simulation context; the id is engine-global
 * (every observer must agree on it). Riding is server-authoritative: the
 * mounted player's body follows the vehicle, the client only steers.</p>
 */
public abstract class VehicleEntity {

    public static final double GRAVITY_PER_TICK = 0.04;
    /** The vanilla kill plane: a vehicle below it is gone without a drop. */
    public static final double VOID_KILL_Y = -64.0;
    public static final float MAX_HEALTH = 4.0f;

    /** The vehicle kinds (the wire's object types live in the adapter). */
    public enum Kind { BOAT, MINECART }

    /** Minimal world query the vehicle needs (tick-thread context only). */
    public interface WorldQuery {
        /** @return whether the block containing this point is solid. */
        boolean isSolid(double x, double y, double z);

        /** @return whether the point lies inside the collision shape of its cell. */
        default boolean isSolidAt(double x, double y, double z) {
            return isSolid(x, y, z);
        }

        /** @return whether the block containing this point is a fluid. */
        default boolean inFluid(double x, double y, double z) {
            return false;
        }

        /**
         * @return the flat rail orientation of the point's cell:
         * 0 = north-south (Z travel), 1 = east-west (X travel), -1 = not rail.
         */
        default int railAxisAt(double x, double y, double z) {
            return -1;
        }
    }

    /** The steering input the mounted passenger reports (Steer Vehicle 0x0C). */
    public record SteerInput(float sideways, float forward, boolean jump, boolean unmount) {
        public static final SteerInput NEUTRAL =
                new SteerInput(0.0f, 0.0f, false, false);
    }

    private final int entityId;
    private final Kind kind;
    private final Random random;
    private Position position;
    private Rotation rotation = Rotation.ZERO;
    private double velocityX;
    private double velocityY;
    private double velocityZ;
    private float health = MAX_HEALTH;
    private int passengerId = -1;
    private SteerInput steer = SteerInput.NEUTRAL;
    private boolean onGround;
    private int hurtCooldown;
    /** The query of the current tick (set by the manager's tick loop). */
    protected WorldQuery query;

    protected VehicleEntity(int entityId, Kind kind, Position position, Random random) {
        this.entityId = entityId;
        this.kind = kind;
        this.position = Objects.requireNonNull(position, "position");
        this.random = Objects.requireNonNull(random, "random");
    }

    public int entityId() {
        return entityId;
    }

    public Kind kind() {
        return kind;
    }

    public Position position() {
        return position;
    }

    public Rotation rotation() {
        return rotation;
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

    public float health() {
        return health;
    }

    public boolean dead() {
        return health <= 0.0f;
    }

    public int passengerId() {
        return passengerId;
    }

    public boolean hasPassenger() {
        return passengerId >= 0;
    }

    public boolean onGround() {
        return onGround;
    }

    /** @return the rider's steer input for this tick (then reset to neutral). */
    public SteerInput consumeSteer() {
        SteerInput input = steer;
        steer = SteerInput.NEUTRAL;
        return input;
    }

    public void steer(SteerInput input) {
        this.steer = Objects.requireNonNull(input, "input");
    }

    public void setPosition(Position position) {
        this.position = Objects.requireNonNull(position, "position");
    }

    public void setRotation(Rotation rotation) {
        this.rotation = Objects.requireNonNull(rotation, "rotation");
    }

    public void setVelocity(double vx, double vy, double vz) {
        this.velocityX = vx;
        this.velocityY = vy;
        this.velocityZ = vz;
    }

    public boolean inVoid() {
        return position.y() < VOID_KILL_Y;
    }

    /** @return whether the point lies in the vehicle's body box. */
    public boolean contains(double x, double y, double z) {
        double halfWidth = kind == Kind.BOAT ? 0.7 : 0.6;
        double height = kind == Kind.BOAT ? 0.4 : 0.7;
        return Math.abs(x - position.x()) <= halfWidth
                && y >= position.y() - 0.2 && y <= position.y() + height
                && Math.abs(z - position.z()) <= halfWidth;
    }

    /** One hit lands one damage per i-frame window; zero health is the break. */
    public void hurt(float amount) {
        if (hurtCooldown > 0 || dead()) {
            return;
        }
        hurtCooldown = 10; // the hurt-i-frame rhythm
        health -= amount;
    }

    public void tickHurtCooldown() {
        if (hurtCooldown > 0) {
            hurtCooldown--;
        }
    }

    /** The mount: takes the seat (refused when occupied). */
    public boolean mount(int riderEngineId) {
        if (hasPassenger() || dead()) {
            return false;
        }
        passengerId = riderEngineId;
        return true;
    }

    /** The dismount: the seat opens. */
    public void dismount() {
        passengerId = -1;
        steer = SteerInput.NEUTRAL;
    }

    /**
     * Advances one tick of the kind's motion rules; the subclass writes the
     * new position back through {@link #commit}.
     */
    public final void tick(WorldQuery world) {
        this.query = Objects.requireNonNull(world, "world");
        tickKind(world);
    }

    /** The kind's motion rules (the tick's body). */
    protected abstract void tickKind(WorldQuery world);

    /** Position epsilon so a resting bottom queries the block *below* the surface. */
    protected static final double GROUND_EPSILON = 1.0E-7;

    /** The shared horizontal block check at the body's mid height. */
    protected boolean blocked(WorldQuery world, double x, double z, double midY) {
        return world.isSolidAt(x, midY, z);
    }

    protected Random random() {
        return random;
    }

    protected void commit(double x, double y, double z, boolean grounded) {
        position = new Position(x, y, z);
        onGround = grounded;
    }

    protected void decay(double factor) {
        velocityX *= factor;
        velocityZ *= factor;
    }
}
