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


import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE VERIFIER'S SEAT — the stick tick's own resting-box scan (VT-019's
 * named tail (a), docs/vanilla-conformance-audit.md; the uncarried slice
 * of the burning-arrow TNT law):
 *
 * <p><b>The law</b> (1.8.8): the stick tick IS a flight tick. When the
 * arrow's rayTrace hits a block, the stick arm records the hit and sets
 * {@code inGround = true} — but the FLIGHT branch continues to its tail:
 * the integration of the face-relative velocity (lines 328-330, landing
 * the rest at face−0.05·û), {@code setPosition} (line 381), and THEN
 * {@code checkBlockCollisions()} (line 382) — the box scan OVER THE REST
 * POSITION, on the very tick the arrow stuck. A burning arrow whose rest
 * box overlaps a TNT cell therefore primes it on the stick tick — the
 * last consult the reference ever makes for that arrow (every later tick
 * rides the inGround branch, which scans nothing).</p>
 *
 * <p><b>The engine's shape</b>: no carrier implements the stick-tick
 * scan. Develop's symmetric band misses the window (the band's top
 * [y+0.25] a half-block short of the box's top [y+0.5]); the VT-019
 * gate (PR #123) skips the stuck body entirely — including its stick
 * tick; the feet-law band (PR #128) runs only for FLYING bodies on the
 * un-gated tree, and its accidental coverage of the just-stuck arrow is
 * construction-specific (the endpoint rest, not the reference's
 * face−0.05 rest) — on the #123+#128 union the stick tick consults
 * nothing. This seat pins the reference's answer: the TNT in the rest
 * box's upper quarter is PRIMED on the stick tick.</p>
 *
 * <p><b>The construction is placement-independent</b> (both rest
 * geometries cover the TNT's cell): the burning arrow's one-tick flight
 * (velocity {@code (0.3, 0, 0)}, the spawn at {@code (59.85, 41.7,
 * 51.5)}) sticks in the stone at {@code (60, 41, 51)} — the develop
 * endpoint rest {@code (60.147, 41.65)} boxes to cells {41, 42}; the
 * VT-014-fixed rest {@code (59.951, 41.683)} boxes to cells {41, 42}
 * too. The TNT at {@code (60, 42, 51)} — atop the stone, never touched
 * by the flight (the samples ride cell 41) — sits in the box's upper
 * quarter [41.9, 42.15] under both geometries, and outside the band
 * under both. The reference primes it on the stick tick; the engine
 * never does.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArrowStickTickTntScanDiscoveryTest {

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "sticktick" + Math.abs(new java.util.Random().nextInt(100000)),
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
     * The stick-tick scan seat — the TNT in the rest box's upper
     * quarter must be primed on the stick tick (the reference's flight
     * branch running to its tail through the stick). RED on develop
     * (the symmetric band a half-block short), RED on the gate tree and
     * the union (the stuck body skipped, stick tick included), the
     * stick-tick slice's tree the GREEN carrier.
     */
    @Test
    void theStickTickPrimesTheTntInTheRestBox() throws Exception {
        EngineWorld world = server.world();
        server.ticker().submit(() -> {
            world.setBlock(new BlockPosition(60, 41, 51), BuiltinBlocks.STONE);
            world.setBlock(new BlockPosition(60, 42, 51), BuiltinBlocks.TNT);
        });
        await(() -> BuiltinBlocks.TNT.equals(world.getBlock(new BlockPosition(60, 42, 51))),
                "the TNT stands atop the stone");

        var launched = new java.util.concurrent.atomic.AtomicReference<ProjectileEntity>();
        server.ticker().submit(() -> {
            ProjectileEntity arrow = server.projectiles().spawnRestored(
                    ProjectileEntity.Kind.ARROW,
                    new Position(59.85, 41.7, 51.5), 0.3, 0.0, 0.0);
            if (arrow != null) {
                arrow.setOnFireFor(2_000);
            }
            launched.set(arrow);
        });
        await(() -> launched.get() != null, "the burning arrow spawns before the stone");
        ProjectileEntity arrow = launched.get();
        await(arrow::inGround, "the arrow sticks in the stone");
        Position stuckAt = arrow.position();

        // Five ticks: the reference's stick-tick scan would have fired on
        // the stick tick itself; the window only guards the poll.
        EngineAwaits.awaitTicks(world, 5, "the stick tick settles");

        assertTrue(world.airType().equals(world.getBlock(new BlockPosition(60, 42, 51))),
                "VT-019's named tail (a) — the stick tick's own resting-box scan: the "
                        + "reference's flight branch runs to its tail THROUGH the stick "
                        + "(the stick arm at ArrowEntity 288-309 sets inGround, the tail "
                        + "integration at 328-330 lands the rest, setPosition at 381, and "
                        + "checkBlockCollisions at 382 scans the REST BOX [y, y+0.5] on "
                        + "the very stick tick) — a burning arrow whose rest box overlaps "
                        + "a TNT cell primes it on that tick, the LAST consult the "
                        + "reference ever makes for the arrow. Observed: the TNT at "
                        + "(60, 42, 51) still STANDS with the arrow stuck at " + stuckAt
                        + " — the rest box's upper quarter [~41.9, 42.15] covers the "
                        + "cell under BOTH rest geometries (the endpoint 41.65 and the "
                        + "face-0.05 41.683 box to cells {41, 42}), but the engine "
                        + "consults nothing at the stick tick: develop's symmetric band "
                        + "tops at 41.899, the VT-019 gate skips the stuck body (stick "
                        + "tick included), and the feet-law band runs for flying bodies "
                        + "alone — the stick-tick scan slice is UNCARIED");
    }
}
