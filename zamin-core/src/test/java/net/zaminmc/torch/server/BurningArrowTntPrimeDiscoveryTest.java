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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE VERIFIER'S VT-019 SEAT — the STUCK BURNING ARROW's TNT re-prime
 * (docs/vanilla-conformance-audit.md, the thirty-fifth pass's discovery;
 * pass 2 of 3 — the behavioral verification).
 *
 * <p>THE REFERENCE LAW (ArrowEntity.tick, re-walked for this seat): the
 * arrow consults {@code onEntityCollision} for TNT from EXACTLY two
 * sites — the FLIGHT branch's tail box scan ({@code this.checkBlockCollisions()},
 * line 382, inside the {@code else} the {@code inGround} gate opens at
 * line 188) and the stick arm's own single-hit-block consult (lines
 * 311-313, the block the ray JUST hit — a burning arrow flying INTO a
 * TNT block primes it AT the stick, and the priming removes the block
 * so the stick never lands in it). A STUCK arrow consults NOTHING
 * beside its tracked cell: the {@code inGround} branch at lines 172-187
 * runs the stick clock and the tracked-block identity check alone. A
 * burning arrow stuck beside — or in the same cell as — a TNT block
 * leaves that TNT standing in the reference, forever.</p>
 *
 * <p>THE ENGINE'S ROW (ProjectileManager.burningTntPass, the tick's
 * tail): the pass walks EVERY burning arrow — flying OR STUCK — with
 * NO {@code inGround} gate, scanning the symmetric 0.25 band around
 * the position and priming any TNT found. A burning arrow stuck in
 * stone with TNT in its rest cell (the cell the arrow's body occupies)
 * detonates it on the NEXT tick after the placement.</p>
 *
 * <p>SEAT 1 (the finding, deterministic): a burning arrow stuck into a
 * stone platform (the rest at face+0.05, the tracked cell the stone);
 * TNT placed in the arrow's OWN rest cell AFTER the stick — the tracked
 * cell untouched, so the reference consults nothing. THE PIN: the TNT
 * still stands ten stuck ticks later.</p>
 *
 * <p>SEAT 2 (the control, the regression guard for the eventual fix):
 * a burning arrow's FLIGHT into a TNT block primes it — the engine's
 * resolveCollisions arm (the reference's stick-arm consult, lines
 * 311-313 — the cell-entered prime). The fix's {@code !inGround} gate
 * must never touch this arm.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BurningArrowTntPrimeDiscoveryTest {

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "arrowtnt" + Math.abs(new java.util.Random().nextInt(100000)),
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
        EngineAwaits.awaitCondition(server.world(), condition, 120, description);
    }

    /** A 3x3 stone platform whose TOP FACE sits at {@code topFaceY}. */
    private static void buildPlatform(EngineWorld world, int cx, int cy, int cz) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.setBlock(new BlockPosition(cx + dx, cy, cz + dz), BuiltinBlocks.STONE);
            }
        }
    }

    /**
     * SEAT 1 — the finding: the stuck burning arrow's band must consult
     * NOTHING. The arrow is stuck into the stone platform (tracked cell
     * the stone); the TNT lands in the arrow's OWN rest cell after the
     * stick — a cell the reference's stuck branch never consults. The
     * engine's ungated {@code burningTntPass} walks the 0.25 band around
     * the stuck rest on EVERY tick and detonates it.
     */
    @Test
    void theStuckBurningArrowLeavesTheTntBesideItStanding() throws Exception {
        EngineWorld world = server.world();
        int cx = 40;
        int cz = 40;
        int stoneY = 19;      // the stone row's cell (the tracked cell)
        double faceY = 20.0;  // the face the arrow crosses

        server.ticker().submit(() -> buildPlatform(world, cx, stoneY, cz));
        await(() -> BuiltinBlocks.STONE.equals(world.getBlock(new BlockPosition(cx, stoneY, cz))),
                "the stone platform stands");

        // The exact body: spawned exactly one block above the face with
        // velocity -1.0 — the crossing tick sticks it at the rest
        // face+0.05 (VT-014's law), the tracked cell the stone. The fire
        // clock outlives the seat (the reference's Flame release: 100s).
        AtomicReference<ProjectileEntity> launched = new AtomicReference<>();
        server.ticker().submit(() -> {
            ProjectileEntity arrow = server.projectiles().spawnRestored(
                    ProjectileEntity.Kind.ARROW,
                    new Position(cx + 0.5, faceY + 1.0, cz + 0.5),
                    0.0, -1.0, 0.0);
            arrow.setOnFireFor(1000);
            launched.set(arrow);
        });
        await(() -> launched.get() != null, "the burning arrow spawned above the platform");
        ProjectileEntity arrow = launched.get();
        await(() -> arrow.inGround(), "the burning arrow sticks in the platform");
        int stickClock = arrow.ticksInGround();

        // The TNT lands in the arrow's OWN rest cell — the cell the arrow's
        // body occupies (floor(rest) = the cell above the stone). The
        // tracked cell (the stone) is untouched, exactly the reference's
        // no-consult construction. The placement confirms through the
        // task's own flag: the engine's ungated pass may detonate the
        // block within the SAME tick, before any block-standing poll
        // could observe it (the defect's own signature — the immediate
        // re-prime).
        BlockPosition tntCell = new BlockPosition(cx, 20, cz);
        java.util.concurrent.atomic.AtomicBoolean placed = new java.util.concurrent.atomic.AtomicBoolean();
        server.ticker().submit(() -> {
            world.setBlock(tntCell, BuiltinBlocks.TNT);
            placed.set(true);
        });
        await(() -> placed.get(), "the TNT placement task ran");
        int placedClock = arrow.ticksInGround();

        // Ten stuck ticks past the placement — the engine's pass runs on
        // every one of them (the detonation window, had it opened).
        await(() -> arrow.ticksInGround() >= placedClock + 10,
                "ten stuck ticks pass over the placed TNT");

        // THE PIN — the reference's law: the stuck arrow consults NOTHING
        // beside its tracked cell (the stone, unchanged) — the TNT in the
        // arrow's own cell STANDS.
        assertEquals(BuiltinBlocks.TNT, world.getBlock(tntCell),
                "VT-019: the stuck burning arrow must leave the TNT beside it standing — "
                        + "the reference consults onEntityCollision from the FLIGHT branch's "
                        + "box scan alone (ArrowEntity line 382, inside the else the inGround "
                        + "gate opens at line 188) plus the stick arm's single-hit-block "
                        + "consult (lines 311-313); the inGround branch at 172-187 runs the "
                        + "stick clock and the tracked-cell identity check ONLY. The engine's "
                        + "burningTntPass walks the 0.25 band around EVERY burning arrow with "
                        + "no inGround gate — the stuck arrow detonated the TNT in its own "
                        + "rest cell (the tracked cell, the stone, untouched — the reference "
                        + "consults nothing). Observed at cell " + tntCell + ": "
                        + world.getBlock(tntCell));
        // The arrow itself stays stuck through the window (the tracked cell
        // unchanged — the seat's own precondition on the reference side).
        assertTrue(arrow.inGround(),
                "VT-019's precondition: the arrow stays stuck (the tracked cell, the "
                        + "stone, is unchanged — no un-stick fires in either world).");
        assertTrue(stickClock >= 0, "the stick clock read (the seat's tick source)");
    }

    /**
     * SEAT 2 — the control: the FLIGHT arm stands. A burning arrow flying
     * into a TNT block primes it (the reference's stick-arm consult, lines
     * 311-313 — the cell-entered prime; the engine's resolveCollisions
     * burning-TNT consult). The eventual fix's {@code !inGround} gate must
     * never touch this arm.
     */
    @Test
    void theFlyingBurningArrowPrimesTheTntItEnters() throws Exception {
        EngineWorld world = server.world();
        int tntX = 60;
        int tntY = 20;
        int tntZ = 60;

        server.ticker().submit(() ->
                world.setBlock(new BlockPosition(tntX, tntY, tntZ), BuiltinBlocks.TNT));
        await(() -> BuiltinBlocks.TNT.equals(world.getBlock(new BlockPosition(tntX, tntY, tntZ))),
                "the TNT block stands in the flight lane");

        // The exact body: horizontal, one block short of the TNT, mid-cell
        // height — the first tick's step enters the TNT's cell.
        AtomicReference<ProjectileEntity> launched = new AtomicReference<>();
        server.ticker().submit(() -> {
            ProjectileEntity arrow = server.projectiles().spawnRestored(
                    ProjectileEntity.Kind.ARROW,
                    new Position(tntX - 1.0 + 0.5, tntY + 0.5, tntZ + 0.5),
                    3.0, 0.0, 0.0);
            arrow.setOnFireFor(1000);
            launched.set(arrow);
        });
        await(() -> launched.get() != null, "the burning arrow spawned short of the TNT");

        // THE PIN — the flight arm: the TNT block is gone (primed on
        // entry — the reference's own law; the arrow keeps its flight
        // through the opened cell).
        await(() -> !BuiltinBlocks.TNT.equals(world.getBlock(new BlockPosition(tntX, tntY, tntZ))),
                "the flying burning arrow primes the TNT it enters");
    }
}
