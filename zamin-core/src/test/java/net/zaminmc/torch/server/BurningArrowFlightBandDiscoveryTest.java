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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE VERIFIER'S VT-023 SEATS — the burning arrow's flight band, the two
 * grazing edges (docs/vanilla-conformance-audit.md, the thirty-ninth
 * pass's pass-3 quantification; the flight-band fix's cross-tree
 * verification):
 *
 * <p><b>The law</b> (1.8.8): the flight branch's tail scan
 * ({@code checkBlockCollisions}, ArrowEntity line 382) walks the cells
 * overlapping the arrow's own box — {@code setPosition} line 246:
 * {@code Box(x−f, y, z−f, x+f, y+f1, z+f)} with the arrow's
 * {@code setSize(0.5F, 0.5F)} — the FEET-anchored
 * {@code [x±0.25] × [y, y+0.5] × [z±0.25]}, the faces shrunk by the
 * scan's own 0.001. The engine's {@code burningTntPass} walks the
 * symmetric {@code [y−0.25, y+0.25]} instead (the "box-center
 * envelope" — the position misread as the box CENTER): the windows
 * diverge at both vertical edges, in opposite directions.</p>
 *
 * <p><b>Seat 1 — the FALSE PRIME</b> (the engine primes, vanilla
 * doesn't): a burning arrow whose flight bottoms out in
 * {@code [41.0, 41.249)} above a TNT at cell 40 — the engine's band
 * reaches the cell, the reference's box (covering cell 41 alone) never
 * does. The engine detonates a block vanilla never consults.</p>
 *
 * <p><b>Seat 2 — the MISSED PRIME</b> (vanilla primes, the engine
 * doesn't): a burning arrow flying at {@code [41.501, 41.751)} under a
 * TNT at cell 42 — the reference's box touches the cell, the engine's
 * symmetric band (covering cell 41 alone) never does. The engine misses
 * a block vanilla detonates.</p>
 *
 * <p>Both seats RED on develop (the two edges of the same window
 * error); the flight-band fix's tree carries both GREEN. The flights
 * are deterministic one-tick constructions: the post-step position's
 * x/z band covers the TNT's cell on the FIRST tick, and the arrow
 * clears the TNT's column before gravity can carry it into the cell
 * (the legitimate sample consult never fires — the seats isolate the
 * band window alone).</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BurningArrowFlightBandDiscoveryTest {

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "flightband" + Math.abs(new java.util.Random().nextInt(100000)),
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
     * Seat 1 — the false prime. The burning arrow spawns at
     * {@code (59.5, 41.2, 51.5)} with velocity {@code (1.0, 0, 0)}:
     * the first tick's post-step position is {@code (60.49, 41.15)} —
     * the x/z band covers cell 60, the symmetric band's lower edge
     * (40.901) reaches the TNT's cell 40, the reference's box
     * [41.15, 41.65] covers cell 41 alone. The second tick (61.48,
     * 41.0005) clears the TNT's column before the fall reaches cell
     * 40 — the flight sample never enters the TNT cell, the sample
     * consult never fires. The seat isolates the band's window: the
     * TNT at {@code (60, 40, 51)} must STAND.
     */
    @Test
    void theFlightBandDoesNotPrimeBelowTheFeet() throws Exception {
        EngineWorld world = server.world();
        server.ticker().submit(() -> world.setBlock(
                new BlockPosition(60, 40, 51), BuiltinBlocks.TNT));
        await(() -> BuiltinBlocks.TNT.equals(world.getBlock(new BlockPosition(60, 40, 51))),
                "the TNT stands below the flight path");

        var launched = new java.util.concurrent.atomic.AtomicReference<ProjectileEntity>();
        server.ticker().submit(() -> {
            ProjectileEntity arrow = server.projectiles().spawnRestored(
                    ProjectileEntity.Kind.ARROW,
                    new Position(59.5, 41.2, 51.5), 1.0, 0.0, 0.0);
            if (arrow != null) {
                arrow.setOnFireFor(2_000);
            }
            launched.set(arrow);
        });
        await(() -> launched.get() != null, "the burning arrow spawns");

        // Three ticks: the first tick's post-step position (60.49, 41.15)
        // is the only band walk over the TNT's column; the second tick
        // clears it. The window is closed by now.
        EngineAwaits.awaitTicks(world, 3, "the flight clears the TNT's column");

        assertEquals(BuiltinBlocks.TNT, world.getBlock(new BlockPosition(60, 40, 51)),
                "VT-023 seat 1 (the false prime): the burning arrow's flight band must "
                        + "NOT reach below its feet — the reference's tail scan walks the "
                        + "arrow's own box [y, y+0.5] (setPosition line 246, the FEET the "
                        + "bottom; setSize(0.5F, 0.5F)), so a flight at y=41.15 consults "
                        + "cell 41 alone and the TNT at cell 40 is never touched. Observed: "
                        + "the TNT at (60, 40, 51) is GONE — the engine's burningTntPass "
                        + "walks the symmetric [y-0.25, y+0.25] band (the position misread "
                        + "as the box CENTER), its lower edge (40.901) reaching a full "
                        + "half-block below the reference's box bottom — the engine "
                        + "detonates a block vanilla never consults");
    }

    /**
     * Seat 2 — the missed prime. The burning arrow spawns at
     * {@code (59.5, 41.6, 51.5)} with velocity {@code (1.0, 0, 0)}:
     * the first tick's post-step position is {@code (60.49, 41.55)} —
     * the x/z band covers cell 60, the reference's box
     * [41.55, 42.05] touches the TNT's cell 42, the engine's symmetric
     * band [41.301, 41.799] covers cell 41 alone. The flight samples
     * (midpoint 59.995/41.575, endpoint 60.49/41.55) stay in cell 41 —
     * the sample consult never fires. The TNT at {@code (60, 42, 51)}
     * must be PRIMED (the block leaves the world) — the reference's
     * own answer.
     */
    @Test
    void theFlightBandPrimesAboveTheFeet() throws Exception {
        EngineWorld world = server.world();
        server.ticker().submit(() -> world.setBlock(
                new BlockPosition(60, 42, 51), BuiltinBlocks.TNT));
        await(() -> BuiltinBlocks.TNT.equals(world.getBlock(new BlockPosition(60, 42, 51))),
                "the TNT stands above the flight path");

        var launched = new java.util.concurrent.atomic.AtomicReference<ProjectileEntity>();
        server.ticker().submit(() -> {
            ProjectileEntity arrow = server.projectiles().spawnRestored(
                    ProjectileEntity.Kind.ARROW,
                    new Position(59.5, 41.6, 51.5), 1.0, 0.0, 0.0);
            if (arrow != null) {
                arrow.setOnFireFor(2_000);
            }
            launched.set(arrow);
        });
        await(() -> launched.get() != null, "the burning arrow spawns");

        // The prime, if the band is the reference's box, lands on the
        // first tick's walk; the await allows the settle.
        EngineAwaits.awaitTicks(world, 5, "the first tick's band walk settles");

        assertTrue(world.airType().equals(world.getBlock(new BlockPosition(60, 42, 51))),
                "VT-023 seat 2 (the missed prime): the burning arrow's flight band must "
                        + "REACH above its feet — the reference's tail scan walks the "
                        + "arrow's own box [y, y+0.5] (setPosition line 246), so a flight "
                        + "at y=41.55 consults cell 42 (the box top 42.05) and the TNT "
                        + "there is primed on that very tick. Observed: the TNT at "
                        + "(60, 42, 51) still STANDS — the engine's symmetric "
                        + "[y-0.25, y+0.25] band (top 41.799) covers cell 41 alone, a "
                        + "full half-block short of the reference's box top — the engine "
                        + "misses a block vanilla detonates");
    }
}
