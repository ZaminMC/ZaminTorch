package net.zaminmc.torch.server;

import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.entity.projectile.ProjectileEntity;
import net.zaminmc.torch.server.entity.projectile.ProjectileManager;
import net.zaminmc.torch.server.fx.FxManager;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE VERIFIER'S VT-023 SEATS — the burning arrow's FLIGHT band shape
 * (VT-019's named tail (b), elevated to its own finding): the engine's
 * symmetric [y−0.25, y+0.25] band against the reference's feet-law box
 * [y, y+0.5].
 *
 * <p>THE REFERENCE LAW (reference/1.8.8): the arrow's box is the feet
 * law's [x−0.25, x+0.25] × [y, y+0.5] × [z−0.25, z+0.25] —
 * {@code Entity.setPosition} line 240-247:
 * {@code setShape(new Box(x − width/2, y, z − width/2, x + width/2,
 * y + height, z + width/2))}, the arrow's own {@code setSize(0.5F, 0.5F)}
 * (ArrowEntity lines 46/52/88). The flight's TNT consult
 * ({@code checkBlockCollisions}, Entity lines 667-689, called from
 * ArrowEntity line 385 in the FLIGHT branch) walks the cells overlapping
 * THAT box (the 0.001 insets). The engine's arrow position IS the
 * reference's position — the box bottom — pinned by VT-014's law (the
 * stick rest {@code face − 0.05·û} is the position; all four
 * constructions' seats pin it).</p>
 *
 * <p>THE ENGINE'S SHAPE (ProjectileManager.burningTntPass, the flying
 * walk): the symmetric band [y−0.25+0.001, y+0.25−0.001] — shifted 0.25
 * DOWN from the reference's box on the vertical axis (the horizontal
 * ±0.25 matches exactly). Two divergences: the engine consults 0.25
 * BELOW the position the reference never touches (a TNT cell under the
 * flight path primes in the engine, stands in the reference), and the
 * engine MISSES the box's top quarter [y+0.25, y+0.5] (a TNT cell above
 * the flight path primes in the reference, stands in the engine).</p>
 *
 * <p>THE CONSTRUCTION: a burning arrow flying flat along +x at force
 * 2.0, its position crossing the TNT's column at a pinned height —
 * arm A at y≈40.20 (the engine's band floor 39.95 dips into the TNT
 * cell at y=39; the reference's box [40.20, 40.70] walks cell 40
 * alone), arm B at y≈39.70 (the reference's box [39.70, 40.20] walks
 * cells 39 AND 40 — the TNT's cell; the engine's band [39.45, 39.95]
 * walks cell 39 alone).</p>
 *
 * <p>These seats are RED on develop AND on the VT-019 gate's tree (PR
 * #123 — the gate skips STUCK arrows alone; the flying band's shape is
 * this finding). Do not re-pin them to the engine's current answer; the
 * finding record owns the mismatch.</p>
 */
class BurningArrowFlightBandParityDiscoveryTest {

    /** The fixed empty hit surface: nothing intercepts the flight. */
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

    /** One tick at a time, recording the position whose x-band first overlaps the column. */
    private static double flyRecordingColumnCrossing(ProjectileManager manager,
            ProjectileEntity arrow, double columnMinX, double columnMaxX,
            List<net.zaminmc.torch.block.BlockPosition> primed, int maxTicks) {
        double crossedAtY = Double.NaN;
        for (int i = 0; i < maxTicks; i++) {
            manager.tick(List.of());
            if (Double.isNaN(crossedAtY)) {
                double x = arrow.position().x();
                if (x - 0.25 < columnMaxX && x + 0.25 > columnMinX) {
                    crossedAtY = arrow.position().y();
                }
            }
            if (!primed.isEmpty()) {
                break; // the engine primed — the observable is complete
            }
        }
        return crossedAtY;
    }

    /**
     * ARM A — THE EXTRA REACH BELOW: a burning arrow flying at y≈40.20
     * over a TNT whose top face is y=40.0 — the reference's box
     * [40.20, 40.70] never overlaps the TNT's cell (the box's bottom is
     * the position; the 0.20 gap holds), the engine's band
     * [39.95, 40.45] dips 0.05 into it. Vanilla: the TNT stands; the
     * engine: primed.
     */
    @Test
    void theFlightOverTntDoesNotPrimeItInTheReference() {
        List<net.zaminmc.torch.block.BlockPosition> primed = new ArrayList<>();
        Set<net.zaminmc.torch.block.BlockPosition> tntCells = new HashSet<>(Set.of(
                new net.zaminmc.torch.block.BlockPosition(10, 39, 10)));
        ProjectileManager manager = new ProjectileManager(
                (x, y, z) -> false, new EmptyHits(), new NoCombat(),
                new FxManager(), new Random(3), 9_000);
        manager.setBurningTntArm(new ProjectileManager.BurningTntArm() {
            @Override
            public boolean isTnt(net.zaminmc.torch.block.BlockPosition pos, int dimension) {
                return tntCells.contains(pos);
            }

            @Override
            public void ignite(net.zaminmc.torch.block.BlockPosition pos, int dimension) {
                primed.add(pos);
                tntCells.remove(pos);
            }
        });

        // The flat launch from y=40.35 at force 2.0: tick 2's position
        // (10.44, 40.20) — the x-band [10.19, 10.69] inside the TNT's
        // column, the y in the divergence window [40.0, 40.25).
        ProjectileEntity arrow = manager.launchVector(
                ProjectileEntity.Kind.ARROW, -1,
                new Position(6.5, 40.35, 10.5), 1.0, 0.0, 0.0, 2.0, 0.0, 0);
        assertNotNull(arrow, "the shot entered the world");
        arrow.setOnFireFor(2_000);

        double crossedAtY = flyRecordingColumnCrossing(manager, arrow, 10.0, 11.0,
                primed, 10);

        // The construction pin: the flight crossed the TNT's column
        // inside the divergence window — the reference's box clears the
        // TNT's cell (crossedAtY >= 40.0) and the engine's band dips
        // into it (crossedAtY < 40.25).
        assertTrue(!Double.isNaN(crossedAtY), "the flight crossed the TNT's column");
        assertTrue(crossedAtY >= 40.0 && crossedAtY < 40.25,
                "the crossing height sits in the divergence window [40.0, 40.25) — "
                        + "the reference's box [y, y+0.5] clears the TNT cell at y=39, "
                        + "the engine's band [y-0.25, y+0.25] dips into it; observed "
                        + crossedAtY);

        // THE REFERENCE'S LAW: the TNT stands — the box's bottom is the
        // position and the 0.20 gap holds through the whole crossing.
        assertTrue(primed.isEmpty(),
                "the reference's flight box [y, y+0.5] never overlaps the TNT cell "
                        + "below (the feet law — Entity.setPosition line 240: the box "
                        + "spans y..y+height, the position the BOTTOM); the engine's "
                        + "symmetric band dips 0.25 below the position and primed "
                        + primed);
    }

    /**
     * ARM B — THE MISSED REACH ABOVE: a burning arrow flying at y≈39.70
     * under a TNT whose bottom face is y=40.0 — the reference's box
     * [39.70, 40.20] overlaps the TNT's cell (the box's top quarter
     * crosses the face), the engine's band [39.45, 39.95] stays below
     * it. Vanilla: the TNT PRIMES; the engine: standing.
     */
    @Test
    void theFlightUnderTntPrimesItInTheReference() {
        List<net.zaminmc.torch.block.BlockPosition> primed = new ArrayList<>();
        Set<net.zaminmc.torch.block.BlockPosition> tntCells = new HashSet<>(Set.of(
                new net.zaminmc.torch.block.BlockPosition(10, 40, 10)));
        ProjectileManager manager = new ProjectileManager(
                (x, y, z) -> false, new EmptyHits(), new NoCombat(),
                new FxManager(), new Random(3), 9_000);
        manager.setBurningTntArm(new ProjectileManager.BurningTntArm() {
            @Override
            public boolean isTnt(net.zaminmc.torch.block.BlockPosition pos, int dimension) {
                return tntCells.contains(pos);
            }

            @Override
            public void ignite(net.zaminmc.torch.block.BlockPosition pos, int dimension) {
                primed.add(pos);
                tntCells.remove(pos);
            }
        });

        // The flat launch from y=39.85 at force 2.0: tick 2's position
        // (10.44, 39.70) — the x-band inside the TNT's column, the y in
        // the divergence window (39.5, 39.75].
        ProjectileEntity arrow = manager.launchVector(
                ProjectileEntity.Kind.ARROW, -1,
                new Position(6.5, 39.85, 10.5), 1.0, 0.0, 0.0, 2.0, 0.0, 0);
        assertNotNull(arrow, "the shot entered the world");
        arrow.setOnFireFor(2_000);

        double crossedAtY = flyRecordingColumnCrossing(manager, arrow, 10.0, 11.0,
                primed, 10);

        // The construction pin: the crossing height sits in the window
        // where the reference's box reaches the TNT's cell (y > 39.5)
        // and the engine's band does not (y <= 39.75).
        assertTrue(!Double.isNaN(crossedAtY), "the flight crossed the TNT's column");
        assertTrue(crossedAtY > 39.5 && crossedAtY <= 39.75,
                "the crossing height sits in the divergence window (39.5, 39.75] — "
                        + "the reference's box top y+0.5 crosses into the TNT cell at "
                        + "y=40, the engine's band top y+0.25 stays below it; observed "
                        + crossedAtY);

        // THE REFERENCE'S LAW: the TNT PRIMES — the box's top quarter
        // [y+0.25, y+0.5] crossed the TNT's bottom face.
        assertTrue(!primed.isEmpty(),
                "the reference's flight box [y, y+0.5] overlaps the TNT cell above "
                        + "(the feet law — the box spans y..y+height) and "
                        + "checkBlockCollisions primes it; the engine's symmetric "
                        + "band [y-0.25, y+0.25] misses the box's top quarter and "
                        + "left the TNT standing");
    }
}
