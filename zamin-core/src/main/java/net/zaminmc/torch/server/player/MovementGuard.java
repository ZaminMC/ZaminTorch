package net.zaminmc.torch.server.player;

import net.zaminmc.torch.server.physics.Aabb;
import net.zaminmc.torch.server.physics.EntityCollisionWorld;
import net.zaminmc.torch.server.physics.EntityMoveSolver;
import net.zaminmc.torch.util.Position;

import java.util.List;
import java.util.logging.Logger;

/**
 * THE VANILLA MOVEMENT CHECK — ServerPlayNetworkHandler.handlePlayerMove
 * lines 296-357 read whole, 1:1 (Wave M1, the setback repair): the
 * engine's retired per-tick speed caps (0.45 horizontal, 0.55 rise,
 * 5.0 fall — never in the reference) rejected honest sprint-jumps,
 * Speed-potion strides, ice bursts and lag-spaced packets; every
 * rejection snapped the client back. The reference's own law:
 *
 * <ol>
 * <li><b>"moved too quickly!"</b> (line 304): the proposal's squared
 * displacement MINUS the body's squared server velocity above
 * {@code 100.0} — ten blocks per packet, not 0.45 — warns and
 * teleports back (the singleplayer-owner consult retired: the engine
 * is dedicated).</li>
 * <li>the server-side <b>jump arm</b> (lines 312-314): a body the
 * server believes grounded, leaving the ground upward, pays the
 * server's own 0.42 (+ jump-boost) motion — the velocity the quickly
 * consult and the exhaustion reads carry.</li>
 * <li>the <b>collision-walk residual</b> (lines 316-331): the
 * proposal rides the full {@code Entity.move} sweep (the solver's own
 * 1:1 port, the player's 0.6 step height and the sneaking edge-guard)
 * and the walked landing is compared against the claim — a squared
 * residual above {@code 0.0625} is "moved wrongly!" (the reference's
 * own always-true Y-zeroing quirk at line 321 — {@code d12 > -0.5 ||
 * d12 < 0.5} — kept verbatim), consulted only outside creative and
 * sleep.</li>
 * <li>the <b>teleport-back band</b> (lines 333-341): the reference's
 * own gate — the body was collision-free before the move AND (moved
 * wrongly OR still free after) AND not sleeping — snaps back; the
 * spectator's noClip is handled by the engine's own early band.</li>
 * <li>the <b>floating kick</b> (lines 343-353): the probe box grown
 * 0.0625 and expanded {@code -0.55} below the feet; flight enabled,
 * flight rights or any non-air cell inside resets the counter, a
 * non-descending tick ({@code d16 >= -0.03125}) climbs it, and
 * EIGHTY ticks kick with "Flying is not enabled on this server" —
 * a disconnect, never a setback.</li>
 * </ol>
 *
 * <p>Everything else applies: the reference NEVER bounds an honest
 * stride, a fall, a climb or a burst the quickly band already
 * forgives; the fall damage, the fluids and the ladders ride their
 * own walks (the client-authoritative body, the exhaustion ledger's
 * own consults), not this gate.</p>
 */
public final class MovementGuard {

    private static final Logger LOGGER = Logger.getLogger(MovementGuard.class.getName());

    /** The player's collision shape (PlayerEntity's own 0.6 × 1.8 hull). */
    public static final double PLAYER_WIDTH = 0.6;
    /** The player's collision height (PlayerEntity's own 1.8 hull). */
    public static final double PLAYER_HEIGHT = 1.8;
    /** The player's step height (LivingEntity line 143: {@code 0.6F}). */
    public static final double PLAYER_STEP_HEIGHT = 0.6;

    /** The quickly band (line 304: {@code d15 - d14 > 100.0}). */
    public static final double QUICKLY_BAND = 100.0;
    /** The wrongly band (line 328: {@code d15 > 0.0625}). */
    public static final double WRONGLY_BAND = 0.0625;
    /** The collision consult's shrink margin (line 310: {@code 0.0625F}). */
    public static final double COLLISION_SHRINK = 0.0625;
    /** The floating probe's downward expansion (line 343: {@code -0.55}). */
    public static final double FLOAT_PROBE_DROP = 0.55;
    /** A non-descending tick's band (line 346: {@code d16 >= -0.03125}). */
    public static final double FLOAT_DESCENT_BAND = -0.03125;
    /** The floating kick's own window (line 348: {@code > 80}). */
    public static final int FLOAT_KICK_TICKS = 80;

    /**
     * The block-read surface the check consults (the reference World's
     * own arms): the collision gather (plus the solver's needs), the
     * non-air probe and the cobweb consult.
     */
    public interface MovementWorld extends EntityCollisionWorld {

        /**
         * World.getCollisions(entity, shape) (lines 912-940): every block
         * box intersecting the region. The mover-inside-border consult
         * reads true — a moving player the quickly band still tracks is
         * inside (the outer cells read STONE through the surface's own
         * {@code borderContains} exactly like the reference's flag1).
         */
        default List<Aabb> collisions(Aabb region) {
            return net.zaminmc.torch.server.physics.EntityCollisions
                    .gatherBlockBoxes(this, region, true);
        }

        /** World.containsNonAir (lines 1429-1446): any non-air cell in the box. */
        boolean containsNonAir(Aabb region);

        /**
         * The body's cobweb flag (Entity.inCobweb over the tick's
         * doBlockCollisions walk — CobwebBlock.onEntityCollision): the
         * engine consults the cells the hull spans at the body's own
         * feet.
         */
        boolean cobwebAt(Position feet);
    }

    /** The check's own verdict (the reference's three terminal bands). */
    public sealed interface Verdict {

        /** The proposal applies whole (the reference's fall-through). */
        record Accept() implements Verdict {
        }

        /** "moved too quickly!"/"moved wrongly!" — the teleport back. */
        record SnapBack(String reason) implements Verdict {
        }

        /** The floating kick — the disconnect, never a setback. */
        record Kick(String reason) implements Verdict {
        }
    }

    private MovementGuard() {
    }

    /**
     * Runs the reference's whole validation walk over one movement
     * proposal. Tick-thread context; mutates only the session's own
     * server-side fields (the jump arm's motion write, the floating
     * counter). The caller owns the apply/resync/kick follow-ups.
     *
     * @param world        the acting dimension's block surface
     * @param session      the moving body's own session
     * @param from         the server's authoritative position
     * @param to           the client's proposal
     * @param packetOnGround the proposal's own on-ground flag
     * @param jumpBoostBonus the active jump-boost bonus (0.1 × (level+1)),
     *                      0.0 with no effect — the caller's own consult
     */
    public static Verdict check(MovementWorld world, PlayerSession session,
                                Position from, Position to, boolean packetOnGround,
                                double jumpBoostBonus) {
        double d11 = to.x() - from.x();
        double d12 = to.y() - from.y();
        double d13 = to.z() - from.z();
        double d14 = session.motionX() * session.motionX()
                + session.motionY() * session.motionY()
                + session.motionZ() * session.motionZ();
        double d15 = d11 * d11 + d12 * d12 + d13 * d13;
        if (d15 - d14 > QUICKLY_BAND) {
            // Line 304-308: the warn carries the reference's own doubled
            // form verbatim ("! " + d11 + "," + d12 + "," + d13 + " (" +
            // d11 + ", " + d12 + ", " + d13 + ")").
            LOGGER.warning(session.name() + " moved too quickly! " + d11 + "," + d12 + "," + d13
                    + " (" + d11 + ", " + d12 + ", " + d13 + ")");
            return new Verdict.SnapBack("moved too quickly");
        }

        // Line 310-311: was the body collision-free before the move?
        boolean flag = world.collisions(shapeOf(from).contract(
                COLLISION_SHRINK, COLLISION_SHRINK, COLLISION_SHRINK)).isEmpty();

        // Lines 312-314: the server-side jump impulse — a body the
        // server believes grounded, leaving the ground upward, pays the
        // server's own 0.42 (+ the jump-boost ladder) so the quickly
        // consult and the exhaustion reads carry the right velocity.
        if (session.onGround() && !packetOnGround && d12 > 0.0) {
            session.serverJump(jumpBoostBonus);
        }

        // Lines 316-317: the full Entity.move sweep over the proposal's
        // deltas (the solver's 1:1 port — the player's 0.6 step height,
        // the sneaking edge-guard, the cobweb drag).
        EntityMoveSolver.Body body = new EntityMoveSolver.Body(
                from.x(), from.y(), from.z(), PLAYER_WIDTH, PLAYER_HEIGHT);
        body.onGround = session.onGround();
        EntityMoveSolver.MoveLaw law = new EntityMoveSolver.MoveLaw(
                PLAYER_STEP_HEIGHT, session.sneaking(), false, world.cobwebAt(from));
        EntityMoveSolver.move(world, body, d11, d12, d13, law);

        double d16 = d12;
        // The residual: where the server's own walk landed vs the claim.
        d11 = to.x() - body.x;
        d12 = to.y() - body.y;
        // Line 321-323 — THE REFERENCE'S OWN QUIRK, kept verbatim: the
        // guard {@code d12 > -0.5 || d12 < 0.5} is ALWAYS TRUE (any value
        // passes one side), so the residual's Y arm is always zeroed and
        // the wrongly consult reads the horizontal residual alone.
        if (d12 > -0.5 || d12 < 0.5) {
            d12 = 0.0;
        }
        d13 = to.z() - body.z;
        d15 = d11 * d11 + d12 * d12 + d13 * d13;
        boolean flag1 = false;
        if (d15 > WRONGLY_BAND && !session.sleeping()
                && session.gamemode() != net.zaminmc.torch.GameMode.CREATIVE) {
            // Line 328-331: the creative exemption rides the reference's
            // own interactionManager.isCreative() consult.
            flag1 = true;
            LOGGER.warning(session.name() + " moved wrongly!");
        }

        // Lines 333-341: the teleport-back band. The engine's own early
        // spectator band owns noClip (the reference's !this.player.noClip
        // gate — the check never runs for that mode here).
        boolean flag2 = world.collisions(shapeOf(to).contract(
                COLLISION_SHRINK, COLLISION_SHRINK, COLLISION_SHRINK)).isEmpty();
        if (flag && (flag1 || !flag2) && !session.sleeping()) {
            return new Verdict.SnapBack(flag1 ? "moved wrongly"
                    : "moved freely into a solid body");
        }

        // Lines 343-353: the floating kick. The probe: the claim's own
        // hull grown 0.0625, then expanded -0.55 below the feet.
        Aabb probe = shapeOf(to).grown(COLLISION_SHRINK, COLLISION_SHRINK, COLLISION_SHRINK)
                .expanded(0.0, -FLOAT_PROBE_DROP, 0.0);
        if (session.allowedToFly() || session.flying() || world.containsNonAir(probe)) {
            // Line 344-345: flight enabled (the engine grants rights
            // through the modes and /fly — the dedicated server's own
            // allow-flight consult rides the same rights), flight rights,
            // or solid ground below the body: the counter resets.
            session.clearFloatingTicks();
        } else if (d16 >= FLOAT_DESCENT_BAND) {
            // Line 346-352: a non-descending tick with nothing below.
            session.noteFloatingTick();
            if (session.floatingTicks() > FLOAT_KICK_TICKS) {
                LOGGER.warning(session.name() + " was kicked for floating too long!");
                session.clearFloatingTicks();
                return new Verdict.Kick("Flying is not enabled on this server");
            }
        }
        // A descending tick with nothing below: the reference's own shape
        // leaves the counter STANDING (neither arm runs) — a long fall
        // never climbs it, and a hover-pause-hover chain accumulates.
        return new Verdict.Accept();
    }

    /** The player's collision hull at a feet-centre position (getShape()). */
    private static Aabb shapeOf(Position feet) {
        double half = PLAYER_WIDTH / 2.0;
        return new Aabb(feet.x() - half, feet.y(), feet.z() - half,
                feet.x() + half, feet.y() + PLAYER_HEIGHT, feet.z() + half);
    }
}
