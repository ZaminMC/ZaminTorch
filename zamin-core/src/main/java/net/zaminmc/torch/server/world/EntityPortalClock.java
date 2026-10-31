package net.zaminmc.torch.server.world;

import net.zaminmc.torch.block.BlockPosition;

/**
 * VT-006 — the entity-level portal clock + entry memory (the reference's
 * {@code Entity} portal fields, lines 128/282-313/1428-1454): the per-body
 * state the non-player crossing families ride beside their own bodies.
 *
 * <p>THE LAW (the base {@code Entity}, the threshold the families inherit):
 * the stand threshold is {@code getMaxNetherPortalTime() = 0} (lines
 * 354-355), so a colliding body with an expired cooldown crosses on the
 * FIRST armed tick (contrast the player's 80); the cooldown arms at the
 * base 300 (lines 1457-1459; the player's own override is 10); a body out
 * of the cells decays its stand clock by 4 per tick, floored at zero; a
 * colliding body whose cooldown is LIVE re-arms it to the full store
 * (onPortalCollision lines 1429-1430 — the arrival standing in the
 * destination frame never re-fires).</p>
 *
 * <p>THE ENTRY MEMORY (onPortalCollision lines 1428-1451): a NEW portal
 * cell derives the body's offset inside its frame (the inverse-lerp of the
 * body's position across the frame's width and height — the raw reference
 * ratios, unclamped) and the entered facing; the SAME cell re-collides
 * without re-deriving. The {@link PortalForcer}'s placement math consumes
 * the snapshot.</p>
 *
 * <p>The same math the player session's and the mob's own portal walks
 * carry (their own copies, predating this holder); the four remaining
 * families (the items, the orbs, the vehicles, the projectiles) compose
 * this one. Tick-thread confined (the manager's per-body walk owns it).</p>
 */
public final class EntityPortalClock {

    /** The base cooldown store (Entity.getPortalCooldown lines 1457-1459). */
    public static final int COOLDOWN_TICKS = 300;

    private int portalCooldown;
    private int portalTime;
    private BlockPosition lastPortalPosition;
    private double offsetAlongWidth;
    private double offsetUp;
    private int lastFacingHorizontalId;

    /**
     * The portal arm's clock (Entity.baseTick lines 282-313 fed by the
     * gated collision): the cooldown's tail decrement every ticked body
     * pays, the live-cooldown re-arm on a colliding body, the base
     * threshold-0 crossing on the first armed tick, and the out-of-cells
     * decay.
     *
     * @param touching whether the gated collision matched this tick (the
     *                 caller's own riding gate already applied — the
     *                 reference's collision consult lines 158-162)
     * @return true on the crossing tick (the cooldown armed for the
     *         arrival — the caller walks the body across)
     */
    public boolean advance(boolean touching) {
        // The tail decrement (lines 311-313): every ticked body pays it.
        if (portalCooldown > 0) {
            portalCooldown--;
        }
        if (touching) {
            if (portalCooldown > 0) {
                // The collision consult's re-arm (onPortalCollision lines
                // 1429-1430): a colliding body whose cooldown is live
                // resets it to the full store.
                portalCooldown = COOLDOWN_TICKS;
                return false;
            }
            // The crossing arm (lines 286-297): the base threshold 0 —
            // the armed body crosses on the very first touching tick,
            // and the cooldown arms for the arrival.
            portalTime = 0;
            portalCooldown = COOLDOWN_TICKS;
            return true;
        }
        // The decay arm (lines 302-308).
        if (portalTime > 0) {
            portalTime = Math.max(0, portalTime - 4);
        }
        return false;
    }

    /**
     * The entry memory's derive (onPortalCollision lines 1428-1451): a NEW
     * portal cell derives the body's offset inside its frame and the
     * entered facing; the SAME cell re-collides without re-deriving.
     *
     * @param portalCell the body's feet/bottom portal cell
     * @param match      the frame's shape match at that cell
     * @param x          the body's x
     * @param y          the body's y (the family's own feet/bottom row)
     * @param z          the body's z
     */
    public void noteEntry(BlockPosition portalCell,
                          PortalFrameBuilder.PortalShapeMatch match,
                          double x, double y, double z) {
        if (portalCell.equals(lastPortalPosition)) {
            return; // the reference's !pos.equals(lastPortalPos) guard
        }
        lastPortalPosition = portalCell;
        boolean axisX = match.forwardAxis() == PortalFrameBuilder.Axis.X;
        // d0: the topLeft's coordinate on the width axis; d1: the body's
        // coordinate on the same axis, shifted one on the reference's
        // flag1 (forward.clockwiseY() NEGATIVE — ONLY SOUTH, the Z-axis
        // positive forward).
        double origin = axisX ? match.topLeftFront().z() : match.topLeftFront().x();
        double bodyCoord = axisX ? z : x;
        if (match.forwardAxis() == PortalFrameBuilder.Axis.Z
                && match.forwardPositive()) {
            bodyCoord -= 1.0;
        }
        // The reference's inverseLerp: (a - b) / (c - b), raw — no clamp.
        offsetAlongWidth = Math.abs(
                (bodyCoord - origin) / ((origin - match.width()) - origin));
        offsetUp = ((y - 1.0) - match.topLeftFront().y())
                / ((match.topLeftFront().y() - match.height()) - match.topLeftFront().y());
        lastFacingHorizontalId = match.forwardHorizontalId();
    }

    /** @return the memory snapshot the PortalForcer's placement math consumes. */
    public PortalForcer.Memory memory() {
        PortalForcer.Memory memory = new PortalForcer.Memory();
        memory.offsetAlongWidth = offsetAlongWidth;
        memory.offsetUp = offsetUp;
        memory.lastFacingHorizontalId = lastFacingHorizontalId;
        memory.known = lastPortalPosition != null;
        return memory;
    }
}
