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
public abstract class VehicleEntity implements net.zaminmc.torch.entity.Vehicle {

    public static final double GRAVITY_PER_TICK = 0.04;
    /** The vanilla kill plane: a vehicle below it is gone without a drop. */
    public static final double VOID_KILL_Y = -64.0;
    public static final float MAX_HEALTH = 4.0f;

    /**
     * The damage source kinds the vehicle death law consults (Slice 74 —
     * the reference's {@code DamageSource.isExplosive()/isFire()} over the
     * minecart family's dropItems arms: a furnace cart's furnace block and
     * a TNT cart's TNT block both skip an explosive death; a TNT cart's
     * fire/explosive/fast death detonates).
     */
    public enum DamageSource {
        MELEE, EXPLOSIVE, FIRE
    }

    /** The vehicle kinds (the wire's object types live in the adapter). */
    public enum Kind {
        BOAT(net.zaminmc.torch.entity.VehicleKind.BOAT),
        MINECART(net.zaminmc.torch.entity.VehicleKind.MINECART),
        /** The chest minecart (Slice 21a — the 27-slot rolling container). */
        CHEST_MINECART(net.zaminmc.torch.entity.VehicleKind.CHEST_MINECART),
        /** The furnace minecart (Slice 66 — the coal-fed pusher). */
        FURNACE_MINECART(net.zaminmc.torch.entity.VehicleKind.FURNACE_MINECART),
        /** The TNT minecart (Slice 66 — the 80-tick fuse). */
        TNT_MINECART(net.zaminmc.torch.entity.VehicleKind.TNT_MINECART),
        /** The hopper minecart (Slice 66 — the 5-slot rolling pickup). */
        HOPPER_MINECART(net.zaminmc.torch.entity.VehicleKind.HOPPER_MINECART),
        /** The spawner minecart (Slice 66 — the rolling spawner). */
        SPAWNER_MINECART(net.zaminmc.torch.entity.VehicleKind.SPAWNER_MINECART),
        /** The command-block minecart (Slice 66 — the rolling executor). */
        COMMAND_BLOCK_MINECART(net.zaminmc.torch.entity.VehicleKind.COMMAND_BLOCK_MINECART);

        /** The public API's kind (§27.3 — the stable surface). */
        public final net.zaminmc.torch.entity.VehicleKind apiKind;

        Kind(net.zaminmc.torch.entity.VehicleKind apiKind) {
            this.apiKind = apiKind;
        }
    }

    /** Minimal world query the vehicle needs (tick-thread context only). */
    public interface WorldQuery extends net.zaminmc.torch.server.physics.HasCollisionWorld {
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
         * @return whether the block containing this point is LAVA (Slice
         * 77 — the reference's isInLava over the vehicle family: the
         * 4.0-per-window wound + the fire). Default false so stub queries
         * keep their semantics.
         */
        default boolean inLava(double x, double y, double z) {
            return false;
        }

        /**
         * @return whether the point's cell is a fire source (Slice 78 —
         * the reference's {@code containsFireSource} family, World.java
         * lines 1475-1494: the FIRE block, the flowing lava, the still
         * lava). Default false so stub queries keep their semantics.
         */
        default boolean inFireSource(double x, double y, double z) {
            return false;
        }

        /**
         * @return whether the body at the point is in water or rain
         * (Slice 78 — the reference's {@code isInWaterOrRain}, the fire
         * family's extinguish consult). Default false so stub queries
         * keep their semantics.
         */
        default boolean inWaterOrRain(double x, double y, double z) {
            return false;
        }

        /**
         * @return the flat rail orientation of the point's cell:
         * 0 = north-south (Z travel), 1 = east-west (X travel), -1 = not rail.
         */
        default int railAxisAt(double x, double y, double z) {
            return -1;
        }

        /**
         * @return the point's cell's powered-rail walk (Slice 38 —
         * MinecartEntity.moveAlongTrack lines 345-349 + 477-494): 1 the
         * powered golden/activator boost, -1 the unpowered brake, 0 plain
         * track (the plain rail and the detector family never boost).
         */
        default int railPowerAt(double x, double y, double z) {
            return 0;
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
    /** The source of the last landed hit (Slice 74 — the death law's gate). */
    private DamageSource lastDamageSource = DamageSource.MELEE;
    /** The speed squared at the fatal hit (frozen for the next-tick drop walk). */
    private double deathSpeedSquared;
    /** The query of the current tick (set by the manager's tick loop). */
    protected WorldQuery query;

    /**
     * The grace ticks before ignition (the reference's {@code fireResistance}
     * — Entity.java line 159's {@code safeOnFireTime = 1}: one tick inside
     * a fire source before the burn arms).
     */
    public static final int FIRE_SAFE_TICKS = 1;

    /**
     * The burn clock (the reference's {@code onFireTimer}, Entity.java
     * line 108): negative = the pre-ignition grace band, positive = ticks
     * of burning left, zero never rests here (the ignite arm re-arms it
     * the moment it reads zero).
     */
    private int onFireTimer = -FIRE_SAFE_TICKS;

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
        // The chest minecart rides the cart hull (the same 0.98x0.7 body
        // the reference's MinecartEntity.setSize gives every cart).
        boolean boat = kind == Kind.BOAT;
        double halfWidth = boat ? 0.7 : 0.6;
        double height = boat ? 0.4 : 0.7;
        return Math.abs(x - position.x()) <= halfWidth
                && y >= position.y() - 0.2 && y <= position.y() + height
                && Math.abs(z - position.z()) <= halfWidth;
    }

    /** One hit lands one damage per i-frame window; zero health is the break. */
    public void hurt(float amount) {
        hurt(amount, DamageSource.MELEE);
    }

    /**
     * One hit lands one damage per i-frame window; zero health is the break.
     * The source rides along (Slice 74 — the reference's
     * {@code damage(DamageSource, float)} signature: the death law consults
     * the source — FurnaceMinecart's {@code !isExplosive()} furnace-block
     * gate, TntMinecart's {@code isFire() || isExplosive()} detonation
     * gate).
     */
    public void hurt(float amount, DamageSource source) {
        if (hurtCooldown > 0 || dead()) {
            return;
        }
        hurtCooldown = 10; // the hurt-i-frame rhythm
        health -= amount;
        this.lastDamageSource = java.util.Objects.requireNonNull(source, "source");
        if (health <= 0.0f) {
            // The death facts freeze at the fatal hit: the reference's
            // dropItems reads the live velocity inside damage(); the
            // engine's drop walk runs on the NEXT tick's break sweep —
            // the snapshot keeps the law (a halted cart must not answer
            // the TNT death-detonation's >= 0.01 gate with a fresh zero).
            this.deathSpeedSquared = velocityX * velocityX + velocityZ * velocityZ;
        }
    }

    /** @return the source of the last landed hit (MELEE before any hit). */
    public DamageSource lastDamageSource() {
        return lastDamageSource;
    }

    /** @return the speed squared at the fatal hit (the TNT cart's gate). */
    public double deathSpeedSquared() {
        return deathSpeedSquared;
    }

    // -------------------------------------------------------------
    // The fire family (Slice 78 — Entity.java's own fire law over the
    // non-living body: the burn clock, the grace counter, the wire's
    // flags byte).
    // -------------------------------------------------------------

    /** @return ticks of burning left (0 = not burning; the wire's flag 0). */
    public boolean onFire() {
        return onFireTimer > 0;
    }

    /** @return the raw burn clock (negative = the grace band; the ZVD row). */
    public int onFireTimer() {
        return onFireTimer;
    }

    /**
     * Arms the burn to at least {@code seconds} seconds (Entity.java
     * lines 365-371 — the max arm: a longer burn never shortens; the
     * protection consult is the living-family's own, the vehicle carries
     * no armor).
     */
    public void setOnFireFor(int seconds) {
        int ticks = seconds * 20;
        if (onFireTimer < ticks) {
            onFireTimer = ticks;
        }
    }

    /**
     * The burn clock's own tick (Entity.java lines 320-335, the onUpdate
     * fire arm): a burning body takes its 1.0 ON_FIRE wound every full
     * second ({@code onFireTimer % 20 == 0}) and the clock ticks down.
     * The wound itself lands through the manager's hurt (the wire's hurt
     * flash); the clock runs regardless of the i-frame's verdict, exactly
     * like the reference's attempted damage.
     *
     * @return whether this tick's ON_FIRE wound is due.
     */
    public boolean tickOnFire() {
        if (onFireTimer > 0) {
            boolean woundDue = onFireTimer % 20 == 0;
            onFireTimer--;
            return woundDue;
        }
        return false;
    }

    /**
     * The fire-source contact's grace walk (Entity.java lines 636-643,
     * the move() fire arm): a dry body inside a fire source counts the
     * grace up; the tick it crosses zero is the 8-second ignition.
     *
     * @param wet the body's water-or-rain consult (a wet body never
     *            counts the grace — the reference's {@code !flag2} gate)
     * @return whether this contact newly ignited the body.
     */
    public boolean contactFire(boolean wet) {
        if (wet) {
            return false;
        }
        onFireTimer++;
        if (onFireTimer == 0) {
            setOnFireFor(8);
            return true;
        }
        return false;
    }

    /**
     * The out-of-fire grace reset (Entity.java lines 644-646: {@code else
     * if (onFireTimer <= 0) onFireTimer = -safeOnFireTime}) — a dry body
     * away from fire sources re-arms its one-tick grace.
     */
    public void clearFireGrace() {
        if (onFireTimer <= 0) {
            onFireTimer = -FIRE_SAFE_TICKS;
        }
    }

    /**
     * The wet extinguish (Entity.java lines 648-651): a burning body in
     * water or rain drops the burn and re-arms the grace (the reference
     * also plays its random.fizz — the manager's listener carries it).
     */
    public void extinguish() {
        onFireTimer = -FIRE_SAFE_TICKS;
    }

    /**
     * The restore's own row (Entity.java line 1176, {@code Fire} short:
     * the raw signed clock — the grace band survives the restart exactly
     * as the reference reads it back).
     */
    public void restoreFireTimer(int restored) {
        this.onFireTimer = restored;
    }

    /**
     * The restore's health carry (Slice 25g — the ZVD payload lands the
     * saved health back on the fresh body, clamped inside the family's
     * own 4.0 ceiling).
     */
    public void setHealthCarried(float restored) {
        this.health = Math.max(0.0f, Math.min(MAX_HEALTH, restored));
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

    /**
     * The swept-box movement application (the physics-solver slice):
     * the vehicle's own {@code Entity.move} — the axis-by-axis clip
     * against every collision box the swept shape touches — when the
     * query provides the collision surface. The engine's centre
     * convention translates to the solver's feet convention at the
     * boundary (the bottom = position.y - halfHeight). The zeroing
     * signals land back on the velocities.
     *
     * @return the outcome, or null when the query provides no surface
     * (the caller takes its point-query walk)
     */
    protected net.zaminmc.torch.server.physics.EntityMoveSolver.Result solverMove(
            WorldQuery world, double halfHeight, double width,
            double moveX, double moveY, double moveZ) {
        net.zaminmc.torch.server.physics.EntityCollisionWorld surface =
                world.collisionWorld();
        if (surface == null) {
            return null;
        }
        net.zaminmc.torch.server.physics.EntityMoveSolver.Body body =
                new net.zaminmc.torch.server.physics.EntityMoveSolver.Body(
                        position.x(), position.y() - halfHeight, position.z(),
                        width, halfHeight * 2.0);
        body.onGround = onGround;
        net.zaminmc.torch.server.physics.EntityMoveSolver.Result result =
                net.zaminmc.torch.server.physics.EntityMoveSolver.move(
                        surface, body, moveX, moveY, moveZ,
                        net.zaminmc.torch.server.physics.EntityMoveSolver.MoveLaw.plain());
        position = new Position(body.x, body.y + halfHeight, body.z);
        onGround = result.onGround();
        if (result.zeroVelocityX()) {
            setVelocity(0.0, velocityY(), velocityZ());
        }
        if (result.zeroVelocityZ()) {
            setVelocity(velocityX(), velocityY(), 0.0);
        }
        if (result.zeroVelocityY()) {
            setVelocity(velocityX(), 0.0, velocityZ());
        }
        return result;
    }

    protected void decay(double factor) {
        velocityX *= factor;
        velocityZ *= factor;
    }

    // ------------------------------------------------------------------
    // The public API surface (§27.3 — the Vehicle handle).
    // ------------------------------------------------------------------

    /** The public kind (the engine kind's stable API mapping). */
    @Override
    public net.zaminmc.torch.entity.VehicleKind vehicleKind() {
        return kind().apiKind;
    }

    @Override
    public net.zaminmc.torch.util.Identifier typeIdentifier() {
        return kind().apiKind.identifier();
    }

    /**
     * The any-thread-consumable view (§27.6): captured on the owner
     * context inside {@code World.execute}. The vehicle's hull carries
     * real health (the 4.0 ceiling) — the only non-living family that
     * does.
     */
    @Override
    public net.zaminmc.torch.entity.EntitySnapshot snapshot() {
        return new net.zaminmc.torch.entity.EntitySnapshot(
                entityId(), kind().apiKind.identifier(), position(), rotation().yaw(),
                velocityX, velocityY, velocityZ,
                health(), MAX_HEALTH, onGround(), dead());
    }

    @Override
    public int riderEntityId() {
        return passengerId();
    }
}
