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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE VERIFIER'S VT-019 SEAT — the stuck burning arrow's TNT consult
 * (docs/vanilla-conformance-audit.md, the thirty-sixth pass's discovery,
 * twice renumbered; this lane's pass 2 — the behavioral seat):
 *
 * <p><b>The reference's law</b> (1.8.8): an arrow consults
 * {@code onEntityCollision} from exactly TWO sites and BOTH are
 * flight-branch arms — the stick arm's single HIT-BLOCK consult
 * (ArrowEntity lines 311-313, the block the rayTrace struck) and the
 * FLIGHT branch's tail box scan ({@code checkBlockCollisions()}, line
 * 382 — the last statement of the {@code else} opened at line 188). The
 * {@code inGround} branch (lines 172-186) runs the stick clock and the
 * tracked-cell check ALONE: <b>a stuck arrow's box is never walked
 * again</b>. A burning arrow stuck BESIDE a TNT block (stuck in the
 * stone next to it, the TNT merely adjacent) therefore primes NOTHING —
 * not on the stick tick (the hit block is the stone), not on any later
 * tick (no scan runs). The TNT stands.</p>
 *
 * <p><b>The engine's shape</b> (develop {@code 777b7c86}):
 * {@code ProjectileManager.burningTntPass} (lines 1004-1037, called at
 * the tail of {@code tick} line 901) walks the ±0.25 band around EVERY
 * burning arrow's position — <b>no {@code inGround} gate</b> — and
 * ignites any TNT cell found, every tick, forever. A stuck burning
 * arrow beside TNT detonates it on the stick tick itself.</p>
 *
 * <p><b>The construction is placement-independent</b> (the VT-015 seat
 * precedent): the TNT sits at {@code (60, 40, 51)} — the cell BELOW the
 * stone the arrow sticks in — at the BOTTOM edge of the engine's band
 * under BOTH rest geometries. The develop rest (the post-step endpoint
 * {@code (60.147, 41.10, 51.5)}) bands y to cells {40, 41}; the
 * VT-014-fixed rest (the reference's face−0.05·û at
 * {@code (59.951, 41.133, 51.5)}) bands y to {40, 41} too — the seat
 * stays RED on PR #117's tree, the finding is the un-gated pass alone.
 * The reference's own consult window never reaches the cell: the rest
 * box (feet law, [y, y+0.5] = [41.133, 41.633]) covers cell 41 only —
 * and it is walked on the STICK TICK alone (the flight branch's tail),
 * never after.</p>
 *
 * <p>The control seat proves the ignition wiring is live in the same
 * boot: a burning arrow landing ON a TNT block (the flight sample
 * entering the TNT cell) ignites it in BOTH models — the engine
 * through the solid arm's {@code tntIgniteSink} consult (the reference
 * lines 311-313's own law), the reference through the same hit consult.
 * A dead arm cannot hide behind the seat.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArrowStuckBurningTntDiscoveryTest {

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "stuckburn" + Math.abs(new java.util.Random().nextInt(100000)),
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

    /**
     * VT-019's seat — the stuck burning arrow beside TNT. A slow burning
     * arrow (0.3/tick, one tick of flight) sticks into the stone at
     * {@code (60, 41, 51)}: the midpoint sample (59.9985, 41.125) reads
     * the air cell before the wall, the endpoint sample (60.147, 41.10)
     * reads the stone — {@code landInGround()} fires, the arrow rests at
     * the endpoint. The TNT at {@code (60, 40, 51)} — the cell below the
     * stone, never touched by the flight — sits at the bottom edge of the
     * engine's un-gated ±0.25 band. The reference never consults it: the
     * hit block was the stone, and the inGround branch walks nothing. The
     * TNT must still stand.
     */
    @Test
    void theStuckBurningArrowLeavesTheAdjacentTntStanding() throws Exception {
        EngineWorld world = server.world();
        // The stone the arrow sticks in, with the TNT directly beneath it.
        server.ticker().submit(() -> {
            world.setBlock(new BlockPosition(60, 41, 51), BuiltinBlocks.STONE);
            world.setBlock(new BlockPosition(60, 40, 51), BuiltinBlocks.TNT);
        });
        await(() -> BuiltinBlocks.TNT.equals(world.getBlock(new BlockPosition(60, 40, 51))),
                "the TNT stands beneath the stone");

        // The deterministic one-tick shot: spawn 0.15 before the stone's
        // near face at the BOTTOM of cell 41 — the endpoint (60.147, 41.10)
        // lands inside the stone cell, the midpoint (59.9985, 41.125) still
        // in the air cell before it.
        var launched = new java.util.concurrent.atomic.AtomicReference<ProjectileEntity>();
        server.ticker().submit(() -> {
            ProjectileEntity arrow = server.projectiles().spawnRestored(
                    ProjectileEntity.Kind.ARROW,
                    new Position(59.85, 41.15, 51.5), 0.3, 0.0, 0.0);
            if (arrow != null) {
                arrow.setOnFireFor(2_000); // the Flame release's own clock
            }
            launched.set(arrow);
        });
        await(() -> launched.get() != null, "the burning arrow spawns before the stone");
        ProjectileEntity arrow = launched.get();
        await(arrow::inGround, "the arrow sticks in the stone");
        assertTrue(arrow.burning(), "the stuck arrow is still on fire (the seat's subject)");

        // Five ticks: the detonation, if the un-gated pass fires, lands on
        // the stick tick itself (burningTntPass runs at the tick's tail,
        // after the walk that stuck the arrow).
        EngineAwaits.awaitTicks(world, 5, "the stuck tick settles");

        assertEquals(BuiltinBlocks.TNT, world.getBlock(new BlockPosition(60, 40, 51)),
                "VT-019: a burning arrow STUCK BESIDE a TNT block must leave it standing — "
                        + "the reference consults onEntityCollision from the flight branch ALONE "
                        + "(ArrowEntity: the stick arm's hit-block consult lines 311-313 — the "
                        + "STONE here, never the TNT — and the tail box scan line 382, the last "
                        + "statement of the flight branch's else; the inGround branch lines "
                        + "172-186 runs the stick clock and the tracked-cell check only, NO box "
                        + "scan ever runs for a stuck arrow). Observed: the TNT at (60, 40, 51) "
                        + "is GONE — the engine's burningTntPass (ProjectileManager lines "
                        + "1004-1037) walks the ±0.25 band around EVERY burning arrow with NO "
                        + "inGround gate, so the STUCK arrow at " + arrow.position() + " primes "
                        + "the adjacent cell on the stick tick itself; vanilla leaves it standing "
                        + "for the whole 1200-tick stick clock");
    }

    /**
     * The control — the ignition wiring is live in this boot. A burning
     * arrow dropped onto a TNT block (the flight sample entering the TNT
     * cell) ignites it in BOTH models: the engine's solid arm consults
     * {@code tntIgniteSink} at the sample (the reference's stick-arm
     * hit-block consult, ArrowEntity lines 311-313 +
     * TntBlock.onEntityCollision lines 91-104 — the same law), the block
     * leaves the world. Without this control a dead arm would make the
     * seat above pass for the wrong reason.
     */
    @Test
    void theControlBurningArrowLandingOnTntIgnitesIt() throws Exception {
        EngineWorld world = server.world();
        server.ticker().submit(() -> {
            world.setBlock(new BlockPosition(62, 41, 51), BuiltinBlocks.TNT);
        });
        await(() -> BuiltinBlocks.TNT.equals(world.getBlock(new BlockPosition(62, 41, 51))),
                "the control TNT stands");

        var launched = new java.util.concurrent.atomic.AtomicReference<ProjectileEntity>();
        server.ticker().submit(() -> {
            ProjectileEntity arrow = server.projectiles().spawnRestored(
                    ProjectileEntity.Kind.ARROW,
                    new Position(62.5, 44.0, 51.5), 0.0, -1.0, 0.0);
            if (arrow != null) {
                arrow.setOnFireFor(2_000);
            }
            launched.set(arrow);
        });
        await(() -> launched.get() != null, "the control arrow spawns above the TNT");

        // The drop's samples enter the TNT cell within a few ticks (steps
        // <= 1.15/tick against the 1.0-tall cell — the midpoint/endpoint
        // pair cannot skip it); the ignition removes the block.
        await(() -> world.airType().equals(world.getBlock(new BlockPosition(62, 41, 51))),
                "the burning arrow's landing sample ignites the TNT it enters");
        assertFalse(BuiltinBlocks.TNT.equals(world.getBlock(new BlockPosition(62, 41, 51))),
                "the control: the TNT the burning arrow lands ON is primed (the reference's "
                        + "stick-arm hit-block consult, ArrowEntity lines 311-313 — the "
                        + "engine's tntIgniteSink the same law; the block leaves the world)");
    }
}
