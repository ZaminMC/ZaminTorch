package net.zaminmc.torch.server.endereye;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;

/**
 * One eye of ender (the reference's EnderEyeEntity, 1:1): the 0.25 box,
 * the straight-line flight with the server-side steering toward the
 * target (the target's 12-block clamp + the +8 targetY at the far form,
 * the reference's setTarget lines 63-79), the 80-tick lifespan (the
 * reference's {@code lifespan > 80} end), and the 4-in-5 item drop at
 * the end (the reference's {@code nextInt(5) > 0} roll — a loaded eye
 * never rolls: the drops row is transient, the reference's own empty
 * writeCustomNbt).
 *
 * <p><b>Wave EYFW — the body's own world:</b> the reference's every
 * EnderEyeEntity ctor takes the {@code world} (the throw spawns it in
 * the THROWER's world, EnderEyeItem's use arm), and the end's two
 * forms read it back — the item drop is {@code new ItemEntity(this.
 * world, ...)} and the smoke is {@code this.world.doEvent(2003, ...)}
 * (tick lines 160-165). The engine carries the world as the dimension
 * int (the TF-9 shape), stamped once at the launch, read by the drop,
 * the event, and the wire's same-dimension observers.</p>
 */
public final class EnderEyeEntity {

    /** The reference's lifespan end (the {@code > 80} gate, line 152). */
    public static final int LIFESPAN_TICKS = 80;

    private final int entityId;
    private final int dimension;
    private Position position;
    private double vx;
    private double vy;
    private double vz;
    private double targetX;
    private double targetY;
    private double targetZ;
    private int lifespan;
    private boolean dropsItem;
    private Rotation rotation = new Rotation(0.0f, 0.0f);

    EnderEyeEntity(int entityId, int dimension, Position position,
                    double vx, double vy, double vz) {
        this.entityId = entityId;
        this.dimension = dimension;
        this.position = position;
        this.vx = vx;
        this.vy = vy;
        this.vz = vz;
    }

    public int entityId() {
        return entityId;
    }

    /** The world the eye flies in (the reference's {@code this.world}). */
    public int dimension() {
        return dimension;
    }

    public Position position() {
        return position;
    }

    /**
     * The seated rider's plain position set (the riding-follow surface —
     * the reference's updateRiderPositon writes the rider's position
     * whatever family it is; the eye's own steering keeps running, the
     * seat re-anchoring the body each tick after it).
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

    public int lifespan() {
        return lifespan;
    }

    /** Whether this eye drops the item at its end (the live use's 4-in-5 roll). */
    public boolean dropsItem() {
        return dropsItem;
    }

    public Rotation rotation() {
        return rotation;
    }

    /** The flight's target (the live use's setTarget; a loaded eye carries the origin — the transient quirk). */
    public Position target() {
        return new Position(targetX, targetY, targetZ);
    }

    /**
     * The target set (the reference's setTarget, lines 63-79, 1:1): a
     * target beyond 12 blocks clamps to 12 along the line with the +8
     * targetY lift (the far form); a near target lands whole. The
     * dropsItem roll rides the set (the live use's only roll site — a
     * loaded eye never passes here).
     */
    void setTarget(double x, double y, double z, java.util.Random random) {
        double dx = x - position.x();
        double dz = z - position.z();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal > 12.0) {
            targetX = position.x() + dx / horizontal * 12.0;
            targetZ = position.z() + dz / horizontal * 12.0;
            targetY = position.y() + 8.0;
        } else {
            targetX = x;
            targetY = y;
            targetZ = z;
        }
        dropsItem = random.nextInt(5) > 0;
    }

    /**
     * One flight step (the reference's tick, lines 92-164, 1:1): the
     * position moves by the velocity, the angles follow (the atan2 forms
     * with the wrap + the 0.2 damp), the server-side steering re-aims
     * the velocity toward the target (the 0.0025 closing gain, the
     * near-target 0.8 damp, the vertical 0.015 approach on targetY),
     * and the lifespan clock advances.
     */
    void advance() {
        position = new Position(position.x() + vx, position.y() + vy, position.z() + vz);
        double horizontal = Math.sqrt(vx * vx + vz * vz);
        float yaw = (float) (Math.atan2(vx, vz) * 180.0 / Math.PI);
        float pitch = (float) (Math.atan2(vy, horizontal) * 180.0 / Math.PI);
        float lastYaw = rotation.yaw();
        float lastPitch = rotation.pitch();
        while (pitch - lastPitch < -180.0f) {
            lastPitch -= 360.0f;
        }
        while (pitch - lastPitch >= 180.0f) {
            lastPitch += 360.0f;
        }
        while (yaw - lastYaw < -180.0f) {
            lastYaw -= 360.0f;
        }
        while (yaw - lastYaw >= 180.0f) {
            lastYaw += 360.0f;
        }
        pitch = lastPitch + (pitch - lastPitch) * 0.2f;
        yaw = lastYaw + (yaw - lastYaw) * 0.2f;
        rotation = new Rotation(yaw, pitch);
        // The server-side steering (the reference's !isClient arm, lines
        // 111-133): re-aim toward the target.
        double dx = targetX - position.x();
        double dz = targetZ - position.z();
        double targetDistance = Math.sqrt(dx * dx + dz * dz);
        double aim = Math.atan2(dz, dx);
        double speed = horizontal + (targetDistance - horizontal) * 0.0025;
        if (targetDistance < 1.0) {
            speed *= 0.8;
            vy *= 0.8;
        }
        vx = Math.cos(aim) * speed;
        vz = Math.sin(aim) * speed;
        if (position.y() < targetY) {
            vy = vy + (1.0 - vy) * 0.015;
        } else {
            vy = vy + (-1.0 - vy) * 0.015;
        }
        lifespan++;
    }

    /** True at the end (the reference's {@code lifespan > 80} gate). */
    public boolean expired() {
        return lifespan > LIFESPAN_TICKS;
    }

    /** The end's drop stack (the reference's fresh ENDER_EYE item, lines 155-157). */
    public ItemStack dropStack() {
        return ItemStack.of(BuiltinItems.ENDER_EYE, 1);
    }
}
