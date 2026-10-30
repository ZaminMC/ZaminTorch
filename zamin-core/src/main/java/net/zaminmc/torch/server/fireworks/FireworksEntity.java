package net.zaminmc.torch.server.fireworks;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.util.Position;

/**
 * One airborne firework rocket (Slice 65 — the reference port of
 * entity/FireworksEntity.java over the engine's ownership model).
 *
 * <p><b>The flight (tick lines 76-120, 1:1):</b> the horizontal velocity
 * grows 1.15x per tick, the vertical gains +0.04, the body moves by the
 * product, and the yaw/pitch follow the velocity vector (the wire's own
 * tracking; the client computes its own copy from the velocity syncs).
 * At age 0 the launch sound plays ({@code fireworks.launch}, volume 3.0,
 * pitch 1.0 — the world's every listener).</p>
 *
 * <p><b>The fuse (the ctor's maxAge law, lines 55-73):</b> {@code 10 * i
 * + rand(6) + rand(7)} ticks where i is 1 + the item's Flight NBT byte.
 * The engine's items carry no NBT model yet (the damage field is the only
 * payload), so every launched rocket flies the plain i = 1 band — 10 to
 * 22 ticks. The flight-duration and explosion-shape NBT ride the item-NBT
 * slice; the ledger records it.</p>
 *
 * <p><b>The burst (tick lines 123-127):</b> when the age passes the fuse
 * the entity event 17 fires (the Entity Status byte the client renders
 * the explosion from — reading the synced slot-8 item's Fireworks
 * compound; without NBT the client's burst is the plain dud) and the body
 * leaves the world. The rocket never collides with anything — no block
 * arm, no hit law, {@code canBePunched} false.</p>
 *
 * <p><b>Wave EYFW — the body's own world:</b> the reference's ctor takes
 * the {@code world} (the launch spawns the rocket in the PLACING
 * player's world, FireworksItem.useOn), and every world-touching site
 * reads it back — the age-0 launch sound is {@code this.world.playSound}
 * (tick line 94) and the burst's entity event is {@code this.world.
 * doEntityEvent} (tick line 112). The engine carries the world as the
 * dimension int (the TF-9 shape), stamped once at the launch, read by
 * the launch sound and the wire's same-dimension observers.</p>
 */
public final class FireworksEntity {

    /** The launch sound's volume (tick line 113: 3.0F). */
    public static final float LAUNCH_VOLUME = 3.0f;
    /** The launch sound's pitch (tick line 113: 1.0F). */
    public static final float LAUNCH_PITCH = 1.0f;
    /** The burst's entity-status byte (tick line 125: 17). */
    public static final byte EVENT_BURST = 17;
    /** The fuse's base (the ctor line 66: 10 * i with i = 1 + Flight). */
    public static final int FUSE_BASE_TICKS = 10;
    /** The ctor's own velocity: the vertical lift (line 71: 0.05). */
    public static final double LAUNCH_LIFT = 0.05;
    /** The ctor's own velocity: the horizontal gaussian sigma (line 70). */
    public static final double LAUNCH_DRIFT_SIGMA = 0.001;
    /** The horizontal growth per tick (tick line 80: *= 1.15). */
    public static final double HORIZONTAL_GROWTH = 1.15;
    /** The vertical gain per tick (tick line 82: += 0.04). */
    public static final double VERTICAL_GAIN = 0.04;

    private final int entityId;
    private final int dimension;
    private final ItemStack payload;
    private Position position;
    private double vx;
    private double vy;
    private double vz;
    private int age;
    private final int maxAge;

    FireworksEntity(int entityId, int dimension, Position position,
                    double vx, double vy, double vz, int maxAge, ItemStack payload) {
        this(entityId, dimension, position, vx, vy, vz, 0, maxAge, payload);
    }

    /**
     * The restored form (the anvil load path — the reference's
     * readCustomNbt, FireworksEntity lines 151-163): the SAVED Life
     * resumes the age clock (the loaded rocket bursts after the
     * REMAINING ticks, never a fresh roll), the LifeTime row carries the
     * whole fuse, and the loaded Motion lands as-is (the reference's
     * load never re-derives the launch drift). The restored form keeps
     * the OVERWORLD 0 (the TF-9 anvil convention — the import files are
     * overworld files).
     */
    FireworksEntity(int entityId, int dimension, Position position,
                    double vx, double vy, double vz,
                    int restoredAge, int maxAge, ItemStack payload) {
        this.entityId = entityId;
        this.dimension = dimension;
        this.position = position;
        this.vx = vx;
        this.vy = vy;
        this.vz = vz;
        this.age = restoredAge;
        this.maxAge = maxAge;
        this.payload = payload;
    }

    public int entityId() {
        return entityId;
    }

    /** The world the rocket flies in (the reference's {@code this.world}). */
    public int dimension() {
        return dimension;
    }

    public Position position() {
        return position;
    }

    /**
     * The seated rider's plain position set (the riding-follow surface —
     * the reference's updateRiderPositon writes the rider's position
     * whatever family it is; the rocket's own flight clock keeps running,
     * the seat re-anchoring the body each tick after it).
     */
    public void teleport(Position to) {
        this.position = to;
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

    public int age() {
        return age;
    }

    public int maxAge() {
        return maxAge;
    }

    /** The rocket's own item (the synced slot-8 stack the client reads). */
    public ItemStack payload() {
        return payload;
    }

    /** True when the fuse burned out (the burst's own tick). */
    public boolean expired() {
        return age > maxAge;
    }

    /**
     * One flight step (the tick's own order): the velocity growth, the
     * move, the age advance. Tick-thread context.
     */
    void advance() {
        vx *= HORIZONTAL_GROWTH;
        vz *= HORIZONTAL_GROWTH;
        vy += VERTICAL_GAIN;
        position = new Position(position.x() + vx, position.y() + vy, position.z() + vz);
        age++;
    }

    /**
     * The blast's scatter add (the reference's Explosion.damageEntities
     * lines 135-137 — {@code velocityX/Y/Z += dir * d11}, the add riding
     * outside the takeDamage verdict). Package-private: the system's
     * addVelocity verb is the only caller.
     */
    void addVelocity(double dx, double dy, double dz) {
        this.vx += dx;
        this.vy += dy;
        this.vz += dz;
    }
}
