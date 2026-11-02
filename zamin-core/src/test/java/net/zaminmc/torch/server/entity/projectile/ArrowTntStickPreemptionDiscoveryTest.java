package net.zaminmc.torch.server.entity.projectile;

import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.fx.FxManager;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE VERIFIER'S VT-030 SEATS — the burning arrow's TNT consult preempts
 * the stick (the #123 review's product; FIRST NUMBERED VT-022, then
 * VT-025 by the forty-first draft, VT-028 by the forty-third
 * reconciliation — VT-030 the final number, the dimension-tails lane's
 * PR titles having taken 25 through 28 by the public-first protocol).
 *
 * <p>THE REFERENCE LAW (reference/1.8.8, ArrowEntity.tick's stick arm
 * lines 288-309): when a flight tick's rayTrace hits a block face, the
 * stick arm runs its whole ceremony BEFORE the hit-block consult — the
 * tracked cell stamped (289-295), the velocity := face−pos (296-298),
 * the 0.05 backoff (300-302), the bowhit sound (303),
 * {@code inGround = true} at <b>line 304</b>, and ONLY THEN
 * {@code block.onEntityCollision(...)} at <b>line 308</b>. For a TNT
 * hit, TntBlock.onEntityCollision (lines 91-104) ignites the block and
 * {@code world.removeBlock(pos)}s it — the tracked cell opens. The
 * arrow is STUCK at the face on the hit tick; the NEXT tick's inGround
 * branch (172-186) reads the tracked cell as AIR and un-sticks it
 * (VT-015's law: the randomized 0.2 re-arm, both clocks reset) — the
 * arrow hops out beside the TNT, never carrying its flight speed
 * through the opened cell.</p>
 *
 * <p>THE ENGINE'S PRE-FIX SHAPE (ProjectileManager.resolveCollisions'
 * block-hit arm): the burning-TNT consult PREEMPTED the stick —
 * {@code tntIgniteSink.ignite(...)} fired and the arm returned false,
 * so {@code landInGround()} never ran: the arrow kept its full flight
 * velocity and position, flying through the opened cell. The javadoc's
 * "the arrow KEEPS ITS FLIGHT" conflated not-removed with
 * flying — the reference's arrow is stuck for a tick and hops.</p>
 *
 * <p>THE CONSTRUCTION: a lone floating TNT at cell (10, 40, 10) — the
 * ONLY solid cell in the stub world, open air everywhere else. A
 * burning arrow launched flat from (8.5, 40.3, 10.5) with force 1.4:
 * tick 1's samples (9.25 / 9.9) read air, tick 2's midpoint sample
 * (≈10.59, 40.2) lands INSIDE the TNT cell — the deterministic
 * one-face hit with wide margins (the y-band [40, 41] holds from
 * 40.15 to 40.3 under the arrow's gravity and drag). The stick-arm
 * sink alone is wired ({@code setTntIgniteSink} — the real engine's
 * own wiring, EngineServer's tntSystem consult); the ungated
 * stuck-walk arm ({@code setBurningTntArm}) is left unwired so the
 * prime can only ride the vanilla stick-arm path.</p>
 *
 * <p>THE COMPOSITION (this seat's honest scope): the rest's face−0.05
 * precision is VT-014's own slice (PRs #117/#122 — their seats pin the
 * placement), and the un-stick hop is VT-015's law (PR #120 — its
 * seats pin the hop). This seat pins THIS finding's own observable
 * alone: the stick HAPPENS on the prime tick, and the flight through
 * the opened cell is retired. The rig leaves the stick-cell consult
 * unwired, so the frozen rest below is exact on this tree and on every
 * union of the open carriers alike (a cell-less stuck body never
 * consults — the un-stick seats' own documented shape).</p>
 */
class ArrowTntStickPreemptionDiscoveryTest {

    private static final FxManager FX = new FxManager();

    /** The fixed empty hit surface. */
    private static final class EmptyHits implements ProjectileManager.HitResolver {
        @Override
        public MobEntity mobAt(Position point, int dimension) {
            return null;
        }

        @Override
        public PlayerSession playerAt(Position point, int dimension) {
            return null;
        }
    }

    /** The no-op combat surface. */
    private static final class NoCombat implements ProjectileManager.CombatSink {
        @Override
        public boolean mobHit(MobEntity mob, float damage, double kbYaw, int shooterId) {
            return true;
        }

        @Override
        public void playerHit(PlayerSession player, float damage, double kbYaw) {
        }

        @Override
        public void chickenHatch(Position position, int dimension) {
        }
    }

    /**
     * The rig: the TNT cell is the stub world's only solid cell, and the
     * stick-arm sink primes it (the reference's ignite + removeBlock —
     * the cell leaves the world when primed).
     */
    private static final class Rig {
        final ProjectileManager manager;
        final List<net.zaminmc.torch.block.BlockPosition> primed = new ArrayList<>();
        final Set<net.zaminmc.torch.block.BlockPosition> solidCells = new HashSet<>();

        Rig() {
            solidCells.add(new net.zaminmc.torch.block.BlockPosition(10, 40, 10));
            manager = new ProjectileManager(
                    (x, y, z) -> solidCells.contains(new net.zaminmc.torch.block.BlockPosition(
                            (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z))),
                    new EmptyHits(), new NoCombat(), FX,
                    new Random(7), 3_000);
            manager.setTntIgniteSink((pos, dimension) -> {
                primed.add(pos);
                // The reference's world.removeBlock(pos): the primed cell
                // leaves the world, so later samples read it as air.
                solidCells.remove(pos);
                return true;
            });
        }
    }

    /**
     * THE STICK — a flame arrow shot straight at a lone TNT block primes
     * it AND sticks on the hit tick. The reference's arrow is
     * {@code inGround} at the face−0.05 rest (ArrowEntity line 304
     * precedes the consult at 308); the pre-fix engine's block-hit arm
     * ignited and returned false — the arrow never landed, keeping its
     * flight through the opened cell.
     *
     * <p>The assertion window is THE PRIME TICK alone (the un-stick hop
     * that follows the cell's opening is VT-015's NEXT-tick law — the
     * seat stays green on the #117+#120+VT-030 union where the hop
     * lands a tick later).</p>
     */
    @Test
    void theBurningArrowSticksAtTheTntFaceItPrimes() {
        Rig rig = new Rig();
        ProjectileEntity burning = rig.manager.launchVector(
                ProjectileEntity.Kind.ARROW, -1,
                new Position(8.5, 40.3, 10.5), 1.0, 0.0, 0.0, 1.4, 0.0, 0);
        assertNotNull(burning, "the shot entered the world");
        burning.setOnFireFor(2_000); // the Flame release's own clock

        int primeTick = -1;
        for (int i = 0; i < 8 && primeTick < 0; i++) {
            rig.manager.tick(List.of());
            if (!rig.primed.isEmpty()) {
                primeTick = i;
            }
        }
        assertTrue(primeTick >= 0, "the construction primes the TNT (the pre-condition — "
                + "the engine's ignite consult itself is vanilla)");
        assertTrue(rig.primed.contains(new net.zaminmc.torch.block.BlockPosition(10, 40, 10)),
                "the TNT cell the burning arrow entered was primed (both worlds agree)");
        assertTrue(burning.inGround(),
                "the reference's arrow STICKS at the face it primed — the stick arm sets "
                        + "inGround (ArrowEntity line 304) BEFORE the hit-block consult (308) "
                        + "fires TntBlock's ignite; the pre-fix engine's block-hit arm ignited "
                        + "and returned false, so the arrow never landed and kept its flight "
                        + "through the opened cell");
    }

    /**
     * THE REST — the stuck arrow's flight is RETIRED at the stick: the
     * position freezes exactly (the stick clock runs, the integration
     * gate holds) instead of carrying the flight speed through the
     * opened cell. THIS finding's own observable — the pre-fix engine's
     * arrow was still flying, advancing a full step per tick beyond the
     * opened cell (the pass-44 record's observed rest x=11.258, 1.26
     * blocks beyond the face, still moving).
     *
     * <p>The face−0.05 precision of WHERE the rest lands is VT-014's
     * slice (PRs #117/#122 — ArrowStickPlacementParityTest pins it);
     * the un-stick hop that moves the arrow a tick later is VT-015's
     * law (PR #120). This rig leaves the stick-cell consult unwired, so
     * the frozen rest is the exact observable here on every tree.</p>
     */
    @Test
    void theArrowPrimingTheTntRetiresItsFlightAtTheStick() {
        Rig rig = new Rig();
        ProjectileEntity burning = rig.manager.launchVector(
                ProjectileEntity.Kind.ARROW, -1,
                new Position(8.5, 40.3, 10.5), 1.0, 0.0, 0.0, 1.4, 0.0, 0);
        assertNotNull(burning, "the shot entered the world");
        burning.setOnFireFor(2_000);

        int primeTick = -1;
        for (int i = 0; i < 8 && primeTick < 0; i++) {
            rig.manager.tick(List.of());
            if (!rig.primed.isEmpty()) {
                primeTick = i;
            }
        }
        assertTrue(primeTick >= 0, "the construction primes the TNT (the pre-condition)");
        assertEquals(1, rig.primed.size(), "one cell, one prime");

        Position rest = burning.position();
        assertTrue(burning.inGround(), "the prime tick ended stuck (the first seat's law)");
        // Three further ticks: the stuck body's integration gate holds —
        // the position advances NOT AT ALL (the pre-fix arrow was ~1.4
        // blocks per tick beyond the cell by now).
        for (int i = 0; i < 3; i++) {
            rig.manager.tick(List.of());
        }
        Position still = burning.position();
        assertEquals(rest.x(), still.x(), 1.0e-9,
                "the stuck arrow's x is FROZEN at the stick rest — the reference's arrow "
                        + "never carries its flight through the opened cell (the pre-fix "
                        + "engine's arrow kept advancing at flight speed)");
        assertEquals(rest.y(), still.y(), 1.0e-9,
                "the stuck arrow's y is FROZEN at the stick rest (the stick clock runs, "
                        + "the integration gate holds)");
        assertEquals(rest.z(), still.z(), 1.0e-9,
                "the stuck arrow's z is FROZEN at the stick rest");
        assertTrue(burning.inGround(), "the stick holds (this rig wires no stick-cell "
                + "consult, so the un-stick hop — VT-015's law — never fires here)");
    }

    /**
     * THE CONTROL — a plain (non-burning) arrow at the same construction
     * sticks on the TNT's face: only the on-fire gate separates the two
     * arms (TntBlock.onEntityCollision lines 91-104 — the isOnFire gate
     * is the TNT block's own). GREEN on the pre-fix tree too — the
     * control isolates the burning arm's ordering as the divergence.
     */
    @Test
    void thePlainArrowSticksAtTheSameFace() {
        Rig rig = new Rig();
        ProjectileEntity plain = rig.manager.launchVector(
                ProjectileEntity.Kind.ARROW, -1,
                new Position(8.5, 40.3, 10.5), 1.0, 0.0, 0.0, 1.4, 0.0, 0);
        assertNotNull(plain, "the control entered the world");

        for (int i = 0; i < 8; i++) {
            rig.manager.tick(List.of());
        }

        assertTrue(plain.inGround(),
                "the plain arrow sticks on the TNT face (no ignite consult — the "
                        + "block-hit arm lands it; the reference agrees)");
        assertTrue(rig.primed.isEmpty(),
                "a plain arrow never primes TNT (the reference's isOnFire gate)");
    }
}
