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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE VERIFIER'S VT-028 SEATS — the burning arrow's TNT hit consult
 * preempts the stick (this lane's discovery, the PR #123 review's
 * product; FIRST NUMBERED VT-022, then VT-025 by the forty-first
 * reconciliation draft — VT-028 the final number, the toBreak's
 * PR title edit having taken 25 at 07:29:59).
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
 * <p>THE ENGINE'S SHAPE (ProjectileManager.resolveCollisions' block-hit
 * arm, lines 1188-1199): the burning-TNT consult PREEMPTS the stick —
 * {@code tntIgniteSink.ignite(...)} fires and the arm returns false,
 * so {@code landInGround()} never runs: the arrow keeps its full
 * flight velocity and position, flying through the opened cell. The
 * javadoc's "the arrow KEEPS ITS FLIGHT" conflates not-removed with
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
 * prime can only ride the vanilla block-hit path.</p>
 *
 * <p>These seats are EXPECTED RED on develop until VT-028's correction
 * lands (the correction stacks on VT-014's face rest and VT-015's
 * un-stick law — PRs #117/#122 and #120). Do not re-pin them to the
 * engine's current answer; the finding record owns the mismatch.</p>
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
     * it AND sticks at the face on the hit tick. The reference's arrow is
     * {@code inGround} at the face−0.05 rest (ArrowEntity line 304
     * precedes the consult at 308); the engine's block-hit arm ignites
     * and returns false — the arrow never lands, keeping its flight
     * through the opened cell.
     *
     * <p>The assertion window is THE PRIME TICK alone (the un-stick hop
     * that follows the cell's opening is VT-015's NEXT-tick law — the
     * seat stays green on the #117+#120+#VT-025 union where the hop
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
                        + "fires TntBlock's ignite; the engine's block-hit arm ignites and "
                        + "returns false, so the arrow never lands and keeps its flight "
                        + "through the opened cell");
    }

    /**
     * THE REST — the stuck arrow stays AT the face on the hit tick (the
     * un-stick hop is the NEXT tick's law, VT-015). The engine's arrow is
     * already far beyond the opened cell, still moving at flight speed.
     */
    @Test
    void theArrowPrimingTheTntRestsAtTheFaceNotBeyondIt() {
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
        assertTrue(Math.abs(rest.x() - 10.0) <= 1.0,
                "the reference's arrow rests at the face−0.05 (x within 1.0 of the west "
                        + "face at 10.0, still inside the air cell before the TNT); the "
                        + "engine's arrow carried its flight through the opened cell — "
                        + "observed x=" + rest.x());
        assertTrue(rest.y() >= 39.5 && rest.y() <= 41.0,
                "the rest stays in the hit cell's y-band (the face height); observed y="
                        + rest.y());
    }

    /**
     * THE CONTROL — a plain (non-burning) arrow at the same construction
     * sticks on the TNT's face: only the on-fire gate separates the two
     * arms (TntBlock.onEntityCollision lines 96-97). GREEN on develop —
     * the control isolates the burning arm's ordering as the divergence.
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
