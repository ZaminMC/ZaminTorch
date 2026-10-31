package net.zaminmc.torch.server.entity;

/**
 * VT-006 — the entity-level dimension-crossing seam (the reference's
 * {@code Entity.changeDimension} walk, 1.8.8 lines 1695-1730, over
 * {@code PlayerManager.changeDimension}'s reposition): the direction, the
 * 8:1 scale, the destination neighborhood load, and the PortalForcer's
 * find-or-build placement. The engine's server owns the worlds and the
 * forcers; the crossing families (the mobs first, then the items, the
 * orbs, the vehicles, the projectiles) ride the seam one by one — every
 * non-player family's portal arm consumes the same walk the player's own
 * crossing rides.
 */
public interface DimensionCrossing {

    /** The crossing's outcome: the destination dimension + the forcer's arrival. */
    record Crossing(int toDimension,
                    net.zaminmc.torch.server.world.PortalForcer.Arrival arrival) {
    }

    /**
     * Walks the body's crossing coordinates to the destination: the
     * direction, the scale, the loads, and the forcer's placement.
     *
     * @param fromDimension the body's current dimension
     * @param x             the body's x
     * @param y             the body's y
     * @param z             the body's z
     * @param yaw           the body's yaw (the forcer's quarter-turn
     *                      rotation consumes it)
     * @param velocityX     the body's horizontal motion
     * @param velocityZ     the body's horizontal motion
     * @param memory        the body's portal entry memory (the frame
     *                      offsets the placement math consumes)
     */
    Crossing cross(int fromDimension, double x, double y, double z, float yaw,
                   double velocityX, double velocityZ,
                   net.zaminmc.torch.server.world.PortalForcer.Memory memory);
}
