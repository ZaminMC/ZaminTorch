package net.zaminmc.torch.server.endcrystal;

import java.util.Objects;

/**
 * One ender crystal's authoritative state (Slice 62 — the reference port of
 * entity/EnderCrystalEntity.java).
 *
 * <p>The body: the 2x2x2 box centered on x/z (the reference's
 * {@code setSize(2.0F, 2.0F)}), the spin counter the renderer rides
 * ({@code renderTicks}, born at {@code random.nextInt(100000)} — the
 * reference's own birth roll, never synced: each client spins from its own
 * clock), and the damage clock at synced slot 8 ({@code health}, born at 5,
 * zeroed by the first wound — the reference's {@code registerSyncedData(8,
 * explosionCountdown)}). The crystal never moves, never saves custom NBT
 * (the reference's own empty {@code writeCustomNbt}), and dies to ANY
 * damage that lands.</p>
 */
public final class EnderCrystal {

    /** The synced-slot-8 birth value (the reference's {@code this.explosionCountdown = 5}). */
    public static final int DEFAULT_HEALTH = 5;

    private final int entityId;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    /** The render spin (the reference's {@code renderTicks++} per tick). */
    private int renderTicks;
    /** The synced damage clock (slot 8; zero by the first landed wound). */
    private int health = DEFAULT_HEALTH;

    public EnderCrystal(int entityId, double x, double y, double z, float yaw, int renderTicks) {
        this.entityId = entityId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.renderTicks = renderTicks;
    }

    public int entityId() {
        return entityId;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    /** The body's facing (the reference's {@code nextFloat() * 360} birth roll). */
    public float yaw() {
        return yaw;
    }

    public int renderTicks() {
        return renderTicks;
    }

    /** One tick of the spin (the reference's {@code this.renderTicks++}). */
    void advanceRenderTicks() {
        renderTicks++;
    }

    /** The synced slot-8 value (5 at birth, 0 once wounded). */
    public int health() {
        return health;
    }

    /**
     * The wound (takeDamage line 73: {@code this.explosionCountdown = 0}) —
     * the value the wire re-broadcasts through the synced slot.
     */
    void wound() {
        this.health = 0;
    }

    /**
     * The body's box containment (the reference's 2.0F size): x/z within
     * one block of the center, y within the two-block height from the feet.
     */
    public boolean contains(double px, double py, double pz) {
        return Math.abs(px - x) <= 1.0
                && Math.abs(pz - z) <= 1.0
                && py >= y && py <= y + 2.0;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof EnderCrystal crystal)) {
            return false;
        }
        return entityId == crystal.entityId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityId);
    }

    @Override
    public String toString() {
        return "EnderCrystal[" + entityId + "]@(" + x + ", " + y + ", " + z + ")";
    }
}
