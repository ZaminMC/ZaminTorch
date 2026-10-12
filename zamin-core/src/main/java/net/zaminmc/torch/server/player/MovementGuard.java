package net.zaminmc.torch.server.player;

import net.zaminmc.torch.util.Position;

/**
 * The server-side movement validator (the anti-cheat baseline; adopted in
 * shape from Mangolise/mango-anti-cheat, Apache-2.0 — our own implementation).
 *
 * <p>The 1.8 client is authoritative about its own position; the engine can
 * only bound what it proposes. The guard checks each movement proposal
 * against the historical physics envelope: the fastest legitimate
 * displacement per tick is a sprint-jump (≈0.35 blocks horizontally,
 * ≈0.42 blocks up) and free fall caps at ≈3.92 blocks down per tick
 * (terminal velocity). Anything far outside that envelope is a cheat or a
 * lag spike; the grace window absorbs the lag spikes (teleports, knockback,
 * join bursts) so honest clients never feel it.</p>
 *
 * <p>Rejected proposals are simply not applied; the engine re-syncs the
 * client with the authoritative position (the historical server behavior:
 * "moved wrongly" teleports back, no kick).</p>
 */
public final class MovementGuard {

    /** Horizontal step cap (blocks/tick): sprint-jump 0.352 plus a lag margin. */
    public static final double MAX_HORIZONTAL_STEP = 0.45;
    /** Vertical ascent cap (blocks/tick): the jump envelope plus a margin. */
    public static final double MAX_RISE_STEP = 0.55;
    /** Vertical descent cap (blocks/tick): terminal velocity 3.92 plus a margin. */
    public static final double MAX_FALL_STEP = 5.0;
    /** Horizontal cap while a grace window is open (teleport/lag forgiveness). */
    public static final double GRACE_HORIZONTAL_STEP = 3.0;
    /** Vertical cap while a grace window is open. */
    public static final double GRACE_VERTICAL_STEP = 12.0;

    private MovementGuard() {
    }

    /**
     * Validates one movement proposal. {@code graceOpen} suspends the speed
     * caps (a teleport or knockback is in flight); finiteness is checked by
     * the session on application regardless.
     *
     * @return true when the movement may apply.
     */
    public static boolean permits(PlayerSession session, Position from, Position to,
                                  boolean onGround, boolean inFluid) {
        double dx = to.x() - from.x();
        double dy = to.y() - from.y();
        double dz = to.z() - from.z();
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        boolean graceOpen = session.graceTicks() > 0;
        double horizontalCap = graceOpen ? GRACE_HORIZONTAL_STEP : MAX_HORIZONTAL_STEP;
        double riseCap = graceOpen ? GRACE_VERTICAL_STEP : MAX_RISE_STEP;
        double fallCap = graceOpen ? GRACE_VERTICAL_STEP : MAX_FALL_STEP;

        if (horizontal > horizontalCap) {
            return false; // speed cheat (or a burst the grace window missed)
        }
        if (dy > riseCap && !session.allowedToFly() && !session.flying()) {
            // Rising beyond the jump envelope without flight rights. Swimming
            // and ladders climb far slower; the grace window covers knockback
            // launches and teleport re-anchors.
            return false;
        }
        if (dy < -fallCap) {
            session.clearHoverTicks();
            return false; // beyond terminal velocity: teleport hack
        }
        // The hover tell: sustained airborne time without flight rights and
        // without descending. A jump is airborne ~12 ticks; water/ladders
        // descend slowly. 100 ticks (5 s) of never-falling flight in a
        // mode without flight rights is a cheat client; the grace window
        // and fluids exempt honest paths.
        if (!graceOpen && !session.allowedToFly() && !session.flying()
                && !onGround && !inFluid && dy >= -0.0784) {
            session.noteHoverTick();
            if (session.hoverTicks() >= HOVER_LIMIT_TICKS) {
                session.clearHoverTicks();
                return false;
            }
        } else {
            session.clearHoverTicks();
        }
        return true;
    }

    /** Sustained airborne non-descent (the hover flight tell) before a reject. */
    static final int HOVER_LIMIT_TICKS = 100;
}
