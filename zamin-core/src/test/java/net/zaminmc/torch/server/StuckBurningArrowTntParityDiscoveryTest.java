package net.zaminmc.torch.server;

import net.zaminmc.torch.server.entity.projectile.ProjectileEntity;
import net.zaminmc.torch.server.entity.projectile.ProjectileManager;
import net.zaminmc.torch.server.fx.FxManager;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.player.PlayerSession;
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
 * THE VERIFIER'S VT-019 SEAT — the stuck burning arrow's TNT consult
 * (docs/vanilla-conformance-audit.md, the thirty-sixth pass's discovery,
 * pass 2 of 3): a burning arrow STUCK beside a TNT block must leave the
 * TNT standing.
 *
 * <p>THE REFERENCE LAW (reference/1.8.8, ArrowEntity.tick): the arrow
 * consults {@code onEntityCollision} from exactly two sites — the FLIGHT
 * branch's tail box scan ({@code checkBlockCollisions()}, line 385,
 * inside the {@code else} opened at line 188; the inGround branch at
 * 172-186 runs the stick clock alone and scans NOTHING) and the stick
 * arm's single-hit-block consult (lines 311-313, the HIT block alone).
 * The flight box is the feet law's [y, y+0.5] (Entity.setPosition line
 * 657: {@code this.y = shape.minY}; ArrowEntity's own setSize(0.5, 0.5)
 * at lines 46/52/88). A burning arrow whose flight never overlaps a TNT
 * cell and whose HIT block is not TNT primes NOTHING, before or after
 * the stick.</p>
 *
 * <p>THE ENGINE'S SHAPE (ProjectileManager.burningTntPass, the tick's
 * tail): every burning ARROW — flying OR STUCK — walks the symmetric
 * [y−0.25, y+0.25] band around its position EVERY tick and primes any
 * TNT cell found. There is no inGround gate. The stuck arrow's band
 * reaches 0.25 BELOW its rest — a TNT cell directly under the stuck
 * position's cell primes on the stick tick and every tick after.</p>
 *
 * <p>THE CONSTRUCTION: a stone cell at (10, 40, 10) (the hit cell), a
 * TNT cell directly below it at (10, 39, 10), and a burning arrow
 * launched flat along +x from (8.5, 40.30, 10.5): tick 1 lands at
 * (9.49, 40.25) — the x-band walks cell 9 alone, the TNT's column
 * untouched; tick 2's endpoint (10.47, 40.15) enters the stone cell and
 * the arrow STICKS — the stuck band [39.90, 40.40] dips into the TNT's
 * cell below. The reference's own flight box [40.15, 40.65]+ never
 * overlaps the TNT cell at any flight position (the feet law keeps the
 * box above y=40.15 throughout), the hit block is the stone, and the
 * inGround branch consults nothing: vanilla leaves the TNT standing
 * forever.</p>
 *
 * <p>These seats are EXPECTED RED on develop until VT-019's correction
 * lands (the {@code !inGround} gate on the burning pass). Do not re-pin
 * them to the engine's current answer; the finding record owns the
 * mismatch.</p>
 */
class StuckBurningArrowTntParityDiscoveryTest {

    /** The fixed empty hit surface: nothing intercepts the flight. */
    private static final class EmptyHits implements ProjectileManager.HitResolver {
        @Override
        public MobEntity mobAt(Position point, int dimension) {
            return null;
        }

        @Override
        public net.zaminmc.torch.server.player.PlayerSession playerAt(Position point, int dimension) {
            return null;
        }
    }

    /** The no-op combat surface. */
    private static final class NoCombat implements ProjectileManager.CombatSink {
        @Override
        public boolean mobHit(MobEntity mob, float damage,
                double kbYaw, int shooterId) {
            return true;
        }

        @Override
        public void playerHit(PlayerSession player, float damage,
                double kbYaw) {
        }

        @Override
        public void chickenHatch(Position position, int dimension) {
        }
    }

    /** The seat's world: the stone hit cell + the TNT cell below it. */
    private static ProjectileManager managerOver(
            Set<net.zaminmc.torch.block.BlockPosition> stoneCells,
            Set<net.zaminmc.torch.block.BlockPosition> tntCells,
            List<net.zaminmc.torch.block.BlockPosition> primed) {
        ProjectileManager manager = new ProjectileManager(
                (x, y, z) -> stoneCells.contains(new net.zaminmc.torch.block.BlockPosition(
                        (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z))),
                new EmptyHits(), new NoCombat(), new FxManager(), new Random(3), 9_000);
        manager.setBurningTntArm(new ProjectileManager.BurningTntArm() {
            @Override
            public boolean isTnt(net.zaminmc.torch.block.BlockPosition pos, int dimension) {
                return tntCells.contains(pos);
            }

            @Override
            public void ignite(net.zaminmc.torch.block.BlockPosition pos, int dimension) {
                primed.add(pos);
                // The reference's world.removeBlock(pos): the primed cell
                // leaves the world, so the next pass finds no TNT there.
                tntCells.remove(pos);
            }
        });
        return manager;
    }

    /**
     * THE FINDING SEAT: the burning arrow stuck in the stone face ABOVE a
     * TNT cell — the reference leaves the TNT standing (no consult for a
     * stuck arrow, the flight box never overlapped the cell, the hit block
     * is the stone); the engine's ungated pass primes it on the stick tick.
     */
    @Test
    void theStuckBurningArrowBesideTntLeavesItStanding() {
        List<net.zaminmc.torch.block.BlockPosition> primed = new ArrayList<>();
        Set<net.zaminmc.torch.block.BlockPosition> tntCells = new HashSet<>(Set.of(
                new net.zaminmc.torch.block.BlockPosition(10, 39, 10)));
        Set<net.zaminmc.torch.block.BlockPosition> stoneCells = new HashSet<>(Set.of(
                new net.zaminmc.torch.block.BlockPosition(10, 40, 10)));
        ProjectileManager manager = managerOver(stoneCells, tntCells, primed);

        ProjectileEntity burning = manager.launchVector(
                ProjectileEntity.Kind.ARROW, -1,
                new Position(8.5, 40.30, 10.5), 1.0, 0.0, 0.0, 1.0, 0.0, 0);
        assertNotNull(burning, "the shot entered the world");
        burning.setOnFireFor(2_000); // the Flame release's own clock

        for (int i = 0; i < 12; i++) {
            manager.tick(java.util.List.of());
        }

        // The construction pins: the arrow stuck INSIDE the stone cell,
        // its rest low enough that the engine's [y-0.25, y+0.25] band
        // dips into the TNT's cell below, and its x-band inside the
        // TNT's column.
        assertTrue(burning.inGround(), "the burning arrow stuck in the stone face");
        double restY = burning.position().y();
        assertTrue(restY >= 40.0 && restY < 40.25,
                "the stuck rest sits in the stone cell's low band (the engine's dip "
                        + "below 39.75..40.25 reaches the TNT cell at y=39); observed "
                        + restY);
        assertTrue(burning.position().x() >= 9.75 && burning.position().x() <= 11.25,
                "the stuck rest's x-band overlaps the TNT's column [10, 11]; observed "
                        + burning.position().x());

        // THE REFERENCE'S LAW: the TNT stands. The engine's ungated pass
        // has primed it (the observed failure carries the cell).
        assertTrue(primed.isEmpty(),
                "the reference leaves the TNT standing — the stuck arrow consults "
                        + "nothing (ArrowEntity.tick 172-186), the flight box never "
                        + "overlapped the cell (the feet law), and the hit block is "
                        + "the stone; the engine's burningTntPass walked the STUCK "
                        + "arrow's band and primed " + primed);
    }

    /**
     * THE PLAIN CONTROL: the same construction without fire — the engine's
     * own burning gate skips the arrow and nothing primes. Green on
     * develop; pins the seat's specificity (the prime rides the burning
     * flag alone).
     */
    @Test
    void thePlainStuckArrowNeverPrimesTheTnt() {
        List<net.zaminmc.torch.block.BlockPosition> primed = new ArrayList<>();
        Set<net.zaminmc.torch.block.BlockPosition> tntCells = new HashSet<>(Set.of(
                new net.zaminmc.torch.block.BlockPosition(10, 39, 10)));
        Set<net.zaminmc.torch.block.BlockPosition> stoneCells = new HashSet<>(Set.of(
                new net.zaminmc.torch.block.BlockPosition(10, 40, 10)));
        ProjectileManager manager = managerOver(stoneCells, tntCells, primed);

        ProjectileEntity plain = manager.launchVector(
                ProjectileEntity.Kind.ARROW, -1,
                new Position(8.5, 40.30, 10.5), 1.0, 0.0, 0.0, 1.0, 0.0, 0);
        assertNotNull(plain, "the control entered the world");

        for (int i = 0; i < 12; i++) {
            manager.tick(java.util.List.of());
        }

        assertTrue(plain.inGround(), "the plain arrow stuck in the stone face");
        assertTrue(primed.isEmpty(), "a plain arrow never primes anything");
    }

    /**
     * THE BAND-LIMIT CONTROL: a burning arrow stuck HIGHER in the same
     * stone face — the rest's band stays clear of the TNT cell below and
     * nothing primes. Green on develop; pins that the finding seat's prime
     * rides the band's geometry (the dip into the cell below), not some
     * other consult.
     */
    @Test
    void theHighStuckBurningArrowNeverPrimesTheTnt() {
        List<net.zaminmc.torch.block.BlockPosition> primed = new ArrayList<>();
        Set<net.zaminmc.torch.block.BlockPosition> tntCells = new HashSet<>(Set.of(
                new net.zaminmc.torch.block.BlockPosition(10, 39, 10)));
        Set<net.zaminmc.torch.block.BlockPosition> stoneCells = new HashSet<>(Set.of(
                new net.zaminmc.torch.block.BlockPosition(10, 40, 10)));
        ProjectileManager manager = managerOver(stoneCells, tntCells, primed);

        // The flat launch from y=40.75: the stick lands at y≈40.60 — the
        // band [40.35, 40.85] walks cell 40 alone, the TNT cell below
        // untouched.
        ProjectileEntity high = manager.launchVector(
                ProjectileEntity.Kind.ARROW, -1,
                new Position(8.5, 40.75, 10.5), 1.0, 0.0, 0.0, 1.0, 0.0, 0);
        assertNotNull(high, "the high control entered the world");
        high.setOnFireFor(2_000);

        for (int i = 0; i < 12; i++) {
            manager.tick(java.util.List.of());
        }

        assertTrue(high.inGround(), "the high burning arrow stuck in the stone face");
        assertTrue(high.position().y() >= 40.35,
                "the high rest's band stays clear of the TNT cell below; observed "
                        + high.position().y());
        assertEquals(0, primed.size(), "the band clear of the TNT primes nothing");
    }
}
