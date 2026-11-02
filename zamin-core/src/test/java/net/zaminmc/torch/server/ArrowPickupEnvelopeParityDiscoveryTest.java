package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.World;
import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.entity.projectile.ProjectileEntity;
import net.zaminmc.torch.server.entity.projectile.ProjectileManager;
import net.zaminmc.torch.server.fx.FxManager;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE VERIFIER'S VT-024 SEATS — the arrow pickup's envelope shape
 * (VT-023's named follow-up, the same class): the engine's
 * center-distance envelope against the reference's box intersection.
 *
 * <p>THE REFERENCE LAW (reference/1.8.8, PlayerEntity lines 486-505):
 * the player's own box (setSize(0.6F, 1.8F) — half-width 0.3) GROWN
 * (1.0, 0.5, 1.0) — the box [px−1.3, px+1.3] × [py−0.5, py+2.3] ×
 * [pz−1.3, pz+1.3] — intersected with the ARROW's own box
 * [ax−0.25, ax+0.25] × [y, y+0.5] × [az−0.25, az+0.25]
 * (Entity.setPosition's feet law). The intersection: |dx| &lt; 1.55,
 * y+0.5 &gt; py−0.5, y &lt; py+2.3. {@code world.getEntities(this,
 * box)} then {@code onPlayerCollision} per entity (ArrowEntity lines
 * 427-440: the inGround + shake gate, the pickup mode, the inventory
 * fit).</p>
 *
 * <p>THE ENGINE'S SHAPE (ProjectileManager.withinArrowPickupRange):
 * the horizontal center-distance |dx| ≤ ARROW_HALF_WIDTH +
 * PICKUP_GROWTH_HORIZONTAL = 0.25 + 1.0 = <b>1.25</b> — the player's
 * own 0.3 half-width MISSING, 0.3 short of the reference's 1.55; the
 * vertical envelope [y−0.25, y+0.25] — the same shifted-down 0.25 as
 * VT-023's flight band (the retired box-center framing; the position
 * is the box BOTTOM) — missing [py−1.0, py−0.75] below and
 * over-reaching [py+2.3, py+2.55) above.</p>
 *
 * <p>These seats are EXPECTED RED on develop until VT-024's correction
 * lands. Do not re-pin them to the engine's current answer; the
 * finding record owns the mismatch.</p>
 *
 * <p>THE STRICT FORM (the rider adopted from the convergent -b carrier
 * {@code fix/arrow-pickup-envelope-parity-audit-b} `07c180aa`, the
 * honest-convergence protocol): the reference's {@code Box.intersects}
 * (lines 164-171) refuses exactly touching boxes on every face — the
 * cap's comparison is {@code >=} (a hair past the face never collects),
 * and the vertical touches are exactly constructible in doubles. Arms
 * D and E pin the boundaries.</p>
 */
class ArrowPickupEnvelopeParityDiscoveryTest {

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

    private ProjectileManager manager() {
        return new ProjectileManager(
                (x, y, z) -> false, new EmptyHits(), new NoCombat(), FX,
                new Random(21), 5_000);
    }

    private PlayerSession player(String name) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        PlayerSession session = new PlayerSession(
                UUID.nameUUIDFromBytes(name.getBytes()), name, link);
        session.authenticate();
        session.beginJoin(stubWorld(), Position.ZERO);
        session.markPlaying();
        session.applyMovement(new Position(0.5, 4.0, 0.5), Rotation.ZERO, true);
        session.setGamemode(GameMode.SURVIVAL);
        return session;
    }

    private World stubWorld() {
        return new World() {
            @Override public String name() { return "stub"; }
            @Override public long timeOfDay() { return 0; }
            @Override public long totalTicks() { return 0; }
            @Override public Position spawnPosition() { return Position.ZERO; }
            @Override public net.zaminmc.torch.block.BlockType getBlock(
                    net.zaminmc.torch.block.BlockPosition p) { return null; }
            @Override public boolean setBlock(
                    net.zaminmc.torch.block.BlockPosition p,
                    net.zaminmc.torch.block.BlockType t) { return false; }
            @Override public boolean isChunkLoaded(
                    net.zaminmc.torch.block.ChunkPosition p) { return true; }
            @Override public java.util.Collection<net.zaminmc.torch.entity.LivingEntity>
            entities() { return java.util.List.of(); }
            @Override public void execute(Runnable work) { work.run(); }
            @Override public void setTimeOfDay(long timeOfDay) { }
            @Override public int blockLightAt(net.zaminmc.torch.block.BlockPosition p) { return 0; }
            @Override public int skyLightAt(net.zaminmc.torch.block.BlockPosition p) { return 15; }
            @Override public String biomeAt(net.zaminmc.torch.block.BlockPosition p) {
                return "plains";
            }
        };
    }

    /** A grounded player-arrow at the given position, stuck by hand. */
    private ProjectileEntity stuckArrowAt(ProjectileManager manager, Position at) {
        ProjectileEntity arrow = manager.launch(
                ProjectileEntity.Kind.ARROW, -1, at, 0, 0, 0.0, 0);
        assertNotNull(arrow, "the arrow entered the world");
        // The cell-less hand-stick (the rig plants the arrow in the air;
        // the tracked cell is absent — the stick clock consults nothing).
        arrow.landInGround(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, null);
        arrow.setPickupStatus(1);
        return arrow;
    }

    private static int arrowCount(PlayerSession session) {
        int count = 0;
        for (int slot = 0; slot < 36; slot++) {
            var stack = session.inventory().slot(slot);
            if (!stack.isEmpty() && stack.type().identifier().value().equals("arrow")) {
                count += stack.count();
            }
        }
        return count;
    }

    /**
     * ARM A — THE HORIZONTAL SHORTFALL: an arrow stuck 1.4 blocks beside
     * the player — inside the reference's reach (the player's grown box
     * ±1.3 over the arrow's own ±0.25 = 1.55) but outside the engine's
     * 1.25 center-distance. Vanilla collects it; the engine leaves it.
     */
    @Test
    void theArrowBesideThePlayerAtVanillasReachCollects() {
        ProjectileManager manager = manager();
        PlayerSession walker = player("walker");
        // dx = 1.4: the reference |dx| < 1.55 collects; the engine's
        // 1.25 cap refuses.
        ProjectileEntity arrow = stuckArrowAt(manager, new Position(0.5 + 1.4, 4.2, 0.5));

        for (int i = 0; i < 5; i++) {
            manager.tick(List.of(walker));
        }

        assertTrue(arrow.inGround(), "the arrow stays stuck (the construction)");
        assertEquals(1, arrowCount(walker),
                "the reference collects an arrow 1.4 blocks beside the walker — the "
                        + "player's own box (half-width 0.3) grown 1.0 horizontal "
                        + "intersects the arrow's box (half-width 0.25) out to 1.55; "
                        + "the engine's center-distance cap 1.25 misses the player's "
                        + "half-width and left it");
    }

    /**
     * ARM B — THE VERTICAL MISS BELOW: an arrow stuck 0.9 below the
     * player's feet — the reference's box top y+0.5 = py−0.4 clears the
     * grown range's floor py−0.5 (collects); the engine's envelope top
     * y+0.25 = py−0.65 does not (misses).
     */
    @Test
    void theArrowBelowThePlayerAtVanillasReachCollects() {
        ProjectileManager manager = manager();
        PlayerSession walker = player("walker");
        // py = 4.0; the arrow at y = 3.1: the reference's box top 3.6 >
        // 3.5 (the grown floor) collects; the engine's envelope top 3.35
        // does not.
        ProjectileEntity arrow = stuckArrowAt(manager, new Position(0.6, 3.1, 0.5));

        for (int i = 0; i < 5; i++) {
            manager.tick(List.of(walker));
        }

        assertEquals(1, arrowCount(walker),
                "the reference collects an arrow whose box top (y+0.5 = 3.6) reaches "
                        + "the grown range's floor (py-0.5 = 3.5) — the feet law's box; "
                        + "the engine's symmetric envelope (top y+0.25 = 3.35) misses "
                        + "it and left it");
    }

    /**
     * ARM C — THE VERTICAL OVER-REACH ABOVE: an arrow stuck 2.4 above
     * the player's feet — above the reference's grown ceiling py+2.3
     * (vanilla leaves it), inside the engine's shifted envelope (the
     * bottom y−0.25 = py+2.15 dips under the ceiling — the engine
     * collects it).
     */
    @Test
    void theArrowAboveThePlayersGrownCeilingStays() {
        ProjectileManager manager = manager();
        PlayerSession walker = player("walker");
        // py = 4.0; the arrow at y = 6.4: the reference's box bottom
        // 6.4 >= the grown ceiling 6.3 — no intersection, vanilla leaves
        // it; the engine's envelope bottom 6.15 < 6.3 collects it.
        ProjectileEntity arrow = stuckArrowAt(manager, new Position(0.6, 6.4, 0.5));

        for (int i = 0; i < 5; i++) {
            manager.tick(List.of(walker));
        }

        assertTrue(arrow.inGround(),
                "the reference leaves the arrow above the grown ceiling (the box "
                        + "bottom y = 6.4 at the ceiling py+2.3 = 6.3 — no "
                        + "intersection); the engine's shifted envelope (bottom "
                        + "y-0.25 = 6.15) dipped under the ceiling and collected it");
        assertEquals(0, arrowCount(walker),
                "no arrow lands in the inventory — the arrow above the grown ceiling "
                        + "is not vanilla-reachable");
    }
    /**
     * ARM D — THE LAST HAIR INSIDE THE REACH (Phase E, the
     * correction's edge): an arrow stuck a hair (1e-9, seven orders
     * above the double rounding floor) INSIDE the corrected cap
     * |dx| &lt; 1.55 — the reference collects (the grown box's maxX
     * px+1.3 strictly right of the arrow's minX ax−0.25), the
     * unfixed engine's 1.25 cap refuses. The partner boundary arm
     * below pins the refusal at and past the face.
     */
    @Test
    void theLastHairInsideTheReachCollects() {
        ProjectileManager manager = manager();
        PlayerSession walker = player("walker");
        // dx = 1.55 - 1e-9: strictly inside the reference's reach
        // (the grown face px+1.3 vs the arrow's minX dx-0.25),
        // strictly outside the unfixed engine's 1.25 cap.
        ProjectileEntity arrow = stuckArrowAt(
                manager, new Position(0.5 + 1.55 - 1e-9, 4.2, 0.5));

        for (int i = 0; i < 5; i++) {
            manager.tick(List.of(walker));
        }

        assertEquals(1, arrowCount(walker),
                "the reference collects the arrow a hair inside the grown box's "
                        + "face — |dx| < 0.3 + 1.0 + 0.25 = 1.55 strictly; the "
                        + "unfixed engine's 1.25 cap left it");
    }

    /**
     * ARM E — THE EXACT TOUCHES NEVER COLLECT (Phase E, the strict
     * intersection's pins): the reference's Box.intersects refuses
     * exactly touching boxes on every face — the arrow's minX AT the
     * grown maxX, the arrow's top AT the grown floor, the arrow's
     * bottom AT the grown ceiling. The vertical touches are exactly
     * constructible in doubles (3.0+0.5 == 4.0−0.5 and the mirrored
     * ceiling expression 4.0+1.8+0.5); the horizontal probe sits a
     * hair OUTSIDE the cap (no double at player x=0.5 differences
     * exactly to 1.55 — the rounding through 2.05 loses the bit, a
     * named note in the finding record). All three must stay stuck.
     */
    @Test
    void exactlyTouchingBoxesNeverCollect() {
        ProjectileManager manager = manager();
        PlayerSession walker = player("walker");
        // The horizontal touch: dx = 1.55 + 1e-9 — the arrow's minX
        // ax-0.25 at/ past the grown face px+1.3.
        ProjectileEntity beside = stuckArrowAt(
                manager, new Position(0.5 + 1.55 + 1e-9, 4.2, 0.5));
        // The floor touch: the arrow's top y+0.5 EXACTLY at the
        // grown floor py-0.5 (3.0+0.5 == 4.0-0.5 in doubles).
        ProjectileEntity atFloor = stuckArrowAt(manager, new Position(0.6, 3.0, 0.5));
        // The ceiling touch: the arrow's bottom EXACTLY at the grown
        // ceiling py+2.3 (the mirrored expression the engine computes).
        ProjectileEntity atCeiling = stuckArrowAt(
                manager, new Position(0.6, 4.0 + 1.8 + 0.5, 0.5));

        for (int i = 0; i < 5; i++) {
            manager.tick(List.of(walker));
        }

        assertTrue(beside.inGround(),
                "the arrow a hair past the grown face never collects — the "
                        + "reference's intersection is strict on the horizontal");
        assertTrue(atFloor.inGround(),
                "the arrow whose top EXACTLY touches the grown floor never "
                        + "collects — Box.intersects' !(box.maxY <= this.minY) "
                        + "refuses the touching top");
        assertTrue(atCeiling.inGround(),
                "the arrow whose bottom EXACTLY touches the grown ceiling never "
                        + "collects — Box.intersects' !(box.minY >= this.maxY) "
                        + "refuses the touching bottom");
        assertEquals(0, arrowCount(walker),
                "no exactly-touching arrow lands in the inventory");
    }
}
