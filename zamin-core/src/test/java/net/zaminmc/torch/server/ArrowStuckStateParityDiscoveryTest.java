package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.projectile.ProjectileEntity;
import net.zaminmc.torch.server.world.EngineWorld;
import net.zaminmc.torch.testing.EngineAwaits;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Path;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE VERIFIER'S VT-014 / VT-015 SEATS — the stuck arrow's two state laws
 * (docs/vanilla-conformance-audit.md, the thirty-first pass's honest
 * residue after VT-013's rejection):
 *
 * <p>VT-014 — the stick PLACEMENT. The reference rests a stuck arrow at
 * the hit face {@code -0.05} along the flight path (ArrowEntity lines
 * 288-309: the velocity becomes the face-relative vector, the position
 * backs off 0.05 behind the PRE-STEP position, then the flight branch's
 * tail integration at lines 328-330 advances it by that same vector —
 * the composition lands the arrow exactly 0.05 SHORT of the hit face,
 * in the cell it was flying through). The engine's
 * {@code resolveCollisions} solid arm calls {@code landInGround()}
 * without touching the position (ProjectileManager line ~1195): the
 * stuck position is the post-step ENDPOINT — up to a full step INSIDE
 * the block, or (the thin-wall construction) PAST a one-block wall in
 * open air when the midpoint sample hit the wall but the endpoint
 * crossed it. Both seats below pin the vanilla side of the law.</p>
 *
 * <p>VT-015 — the tracked-block UN-STICK law. The reference's
 * {@code inGround} branch (ArrowEntity lines 172-186) consults the
 * tracked cell every tick: when the block there changes identity or
 * metadata (broken, blasted, piston-moved), the arrow UN-STICKS —
 * {@code inGround = false}, the velocity re-arms scaled by a fresh
 * {@code random.nextFloat() * 0.2F} per component, both clocks reset —
 * and the arrow falls out. The engine's {@code inGround} arm
 * (ProjectileManager.tick lines ~851-858) runs the stick clock ALONE:
 * no tracked cell, no consult, no un-stick — the arrow hangs until the
 * 1200-tick expiry whatever happens to the block it rests in.</p>
 *
 * <p>The deterministic construction: {@code spawnRestored} lands the
 * arrow with an EXACT position and velocity (no gaussian spread — the
 * launch walk's own noise would blur the face arithmetic). Every
 * number below is pre-computed against the engine's own constants
 * (drag 0.99, arrow gravity 0.05) and recorded in the failure
 * messages: these seats are RED on develop as the evidence record,
 * and the VT-014/VT-015 fix branches carry the GREEN parity pins.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArrowStuckStateParityDiscoveryTest {

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "stuckstate" + Math.abs(new java.util.Random().nextInt(100000)),
                "it", 20, 4, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
        EngineAwaits.awaitCondition(server.world(), condition, 600, description);
    }

    /** A 5x5 stone platform at y=20 (cells x/z 48..52): the top face sits at y=21. */
    private static void buildPlatform(EngineWorld world) {
        for (int x = 48; x <= 52; x++) {
            for (int z = 48; z <= 52; z++) {
                world.setBlock(new BlockPosition(x, 20, z), BuiltinBlocks.STONE);
            }
        }
    }

    /** A one-block-thick wall at x=60 (cells y 40..42, z 50..52): the near face sits at x=60. */
    private static void buildThinWall(EngineWorld world) {
        for (int y = 40; y <= 42; y++) {
            for (int z = 50; z <= 52; z++) {
                world.setBlock(new BlockPosition(60, y, z), BuiltinBlocks.STONE);
            }
        }
    }

    /**
     * VT-014's first seat — the plain stuck position. An arrow dropped
     * perfectly vertically at speed 0.8 crosses the platform's top face
     * (y=21) on its 20th tick: the pre-step position is y=21.7791, the
     * post-step endpoint y=20.2143 (the engine's own drag 0.99 / gravity
     * 0.05 arithmetic). The reference's ray from 21.7791 to 20.2143 hits
     * the face at 21 and rests the arrow at 21.05 — INSIDE THE AIR CELL
     * ABOVE THE FLOOR, 0.05 outside the hit face (the rendered shaft
     * stands on the surface; the pickup box overlaps a player standing
     * on the face). The engine's endpoint placement rests the same arrow
     * at 20.2143 — 0.786 INSIDE the floor block.
     */
    @Test
    void theStuckArrowRestsOutsideTheHitFace() throws Exception {
        EngineWorld world = server.world();
        server.ticker().submit(() -> buildPlatform(world));
        await(() -> BuiltinBlocks.STONE.equals(world.getBlock(new BlockPosition(50, 20, 50))),
                "the stone platform stands at y=20");

        // The deterministic vertical drop: exact position, exact velocity —
        // the 20-tick crossing lands the endpoint inside the floor block.
        var launched = new java.util.concurrent.atomic.AtomicReference<ProjectileEntity>();
        server.ticker().submit(() -> launched.set(server.projectiles().spawnRestored(
                ProjectileEntity.Kind.ARROW,
                new Position(50.5, 44.5, 50.5), 0.0, -0.8, 0.0)));
        await(() -> launched.get() != null, "the arrow spawns above the platform");
        ProjectileEntity arrow = launched.get();
        await(arrow::inGround, "the arrow sticks in the platform");

        Position stuckAt = arrow.position();
        BlockPosition stuckCell = new BlockPosition(
                (int) Math.floor(stuckAt.x()),
                (int) Math.floor(stuckAt.y()),
                (int) Math.floor(stuckAt.z()));
        assertTrue(stuckAt.y() >= 21.0,
                "VT-014 seat 1: the stuck arrow must rest OUTSIDE the hit face — the "
                        + "reference's ArrowEntity lines 288-309 rest it at the face -0.05 along "
                        + "the flight path (face y=21 -> y=21.05, the AIR cell above the floor; "
                        + "the rendered shaft stands on the surface and the pickup box overlaps "
                        + "a player standing on the face). Observed engine position "
                        + stuckAt + " (cell " + stuckCell + ") — the post-step ENDPOINT, "
                        + (21.0 - stuckAt.y()) + " blocks INSIDE the floor block "
                        + "(ProjectileManager.resolveCollisions calls landInGround() without "
                        + "touching the position)");
    }

    /**
     * VT-014's second seat — the thin-wall air-stick (the same root's
     * extreme observable). A 3-blocks-per-tick horizontal arrow at a
     * one-block-thick wall: the engine's midpoint sample (x=60.285)
     * lands INSIDE the wall cell, but the post-step endpoint
     * (x=61.77) has already CROSSED the whole wall into open air —
     * and the engine sticks the arrow at that endpoint, hanging in
     * mid-air 0.77 PAST the wall's far face. The reference's ray hits
     * the wall's near face (x=60) and rests the arrow at 59.95 —
     * before the wall, in the cell it was flying through.
     */
    @Test
    void theFastArrowSticksAtTheWallNotPastIt() throws Exception {
        EngineWorld world = server.world();
        server.ticker().submit(() -> buildThinWall(world));
        await(() -> BuiltinBlocks.STONE.equals(world.getBlock(new BlockPosition(60, 41, 51))),
                "the one-block wall stands at x=60");

        // The deterministic fast shot: 3.0 blocks/tick along +x at the wall's
        // middle row — the midpoint sample crosses the wall, the endpoint
        // lands past it in open air.
        var launched = new java.util.concurrent.atomic.AtomicReference<ProjectileEntity>();
        server.ticker().submit(() -> launched.set(server.projectiles().spawnRestored(
                ProjectileEntity.Kind.ARROW,
                new Position(58.8, 41.5, 51.5), 3.0, 0.0, 0.0)));
        await(() -> launched.get() != null, "the arrow spawns before the wall");
        ProjectileEntity arrow = launched.get();
        await(arrow::inGround, "the arrow sticks at the wall");

        Position stuckAt = arrow.position();
        BlockPosition stuckCell = new BlockPosition(
                (int) Math.floor(stuckAt.x()),
                (int) Math.floor(stuckAt.y()),
                (int) Math.floor(stuckAt.z()));
        assertTrue(stuckAt.x() < 60.0,
                "VT-014 seat 2 (the thin-wall air-stick): the stuck arrow must rest "
                        + "BEFORE the wall's near face — the reference's ray from (58.8) to "
                        + "(61.77) hits the face at x=60 and rests the arrow at x=59.95 "
                        + "(ArrowEntity lines 288-309 + the tail integration: 0.05 short of "
                        + "the face, in the cell it was flying through). Observed engine "
                        + "position " + stuckAt + " (cell " + stuckCell + ") — "
                        + (stuckAt.x() - 60.0) + " blocks PAST the far face: the arrow "
                        + "hangs in OPEN AIR behind the wall (the midpoint sample hit the "
                        + "wall, but landInGround() kept the full-step endpoint position)");
    }

    /**
     * VT-015's seat — the block-break un-stick. The same vertical drop
     * sticks the arrow in the platform's CENTER block (cell 50,20,50).
     * The tracked cell's block is then REMOVED (the blast/piston/break
     * arm of the reference's law). The reference's inGround branch
     * (ArrowEntity lines 172-186) un-sticks the arrow on the very next
     * tick — the tracked cell reads AIR, the identity check fails, the
     * velocity re-arms (a fresh random * 0.2 factor per component over
     * the stored face-relative vector), both clocks reset — and the
     * arrow FALLS OUT of the opened cell (from the rest position at
     * 21.05, gravity alone carries it ~5 blocks in 15 ticks; even the
     * zero-random draw falls well below the floor row's bottom). The
     * engine's stick-clock arm consults nothing: the arrow hangs at its
     * stuck position in mid-air until the 1200-tick expiry.
     */
    @Test
    void theStuckArrowFallsWhenItsBlockIsRemoved() throws Exception {
        EngineWorld world = server.world();
        server.ticker().submit(() -> buildPlatform(world));
        await(() -> BuiltinBlocks.STONE.equals(world.getBlock(new BlockPosition(50, 20, 50))),
                "the stone platform stands at y=20");

        var launched = new java.util.concurrent.atomic.AtomicReference<ProjectileEntity>();
        server.ticker().submit(() -> launched.set(server.projectiles().spawnRestored(
                ProjectileEntity.Kind.ARROW,
                new Position(50.5, 44.5, 50.5), 0.0, -0.8, 0.0)));
        await(() -> launched.get() != null, "the arrow spawns above the platform");
        ProjectileEntity arrow = launched.get();
        await(arrow::inGround, "the arrow sticks in the platform's center block");

        Position stuckAt = arrow.position();
        double stuckY = stuckAt.y();

        // REMOVE the block the arrow rests in — the reference's un-stick law
        // fires on the next tick's tracked-cell consult.
        server.ticker().submit(() -> world.setBlock(
                new BlockPosition(50, 20, 50), world.airType()));
        await(() -> world.airType().equals(world.getBlock(new BlockPosition(50, 20, 50))),
                "the center block under the arrow is removed");

        // Fifteen ticks: the reference's arrow has un-stuck and fallen well
        // below the floor row's bottom face (y=20) by now — even the
        // zero-random draw compounds ~0.05/tick^2 of gravity through 15
        // ticks (~5.6 blocks from the 21.05 rest).
        EngineAwaits.awaitTicks(world, 15,
                "the opened cell lets the stuck arrow fall");
        assertFalse(arrow.inGround(),
                "VT-015: the stuck arrow must UN-STICK when its tracked block is "
                        + "removed — the reference's inGround branch (ArrowEntity lines "
                        + "172-186) consults the tracked cell every tick and the identity "
                        + "check fails the moment it reads AIR (inGround=false, the "
                        + "velocity re-armed with a fresh random*0.2 factor per component, "
                        + "both clocks reset). Observed: the engine's arrow is STILL "
                        + "inGround — the stick-clock arm (ProjectileManager.tick) consults "
                        + "nothing");
        assertTrue(arrow.position().y() < 20.0,
                "VT-015: the un-stuck arrow must FALL out of the opened cell — the "
                        + "reference's re-armed velocity plus gravity carries it below the "
                        + "floor row's bottom face (y=20) within 15 ticks (from the 21.05 "
                        + "rest; even the zero-random draw falls ~5.6 blocks). Observed "
                        + "engine position " + arrow.position() + " — FROZEN at the stuck "
                        + "endpoint " + stuckY + ": the arrow hangs in mid-air where the "
                        + "block used to be, until the 1200-tick expiry");
    }
}
