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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE VERIFIER'S VT-019 PASS-2 SEAT — the stuck burning arrow's TNT
 * re-prime (docs/vanilla-conformance-audit.md, the thirty-sixth pass's
 * discovery, twice renumbered): a burning arrow STUCK BESIDE a TNT
 * block must leave the TNT standing.
 *
 * <p>THE REFERENCE LAW (ArrowEntity.tick, re-walked for this seat):
 * an arrow consults {@code onEntityCollision} from exactly TWO sites —
 * (1) the FLIGHT branch's tail box scan ({@code checkBlockCollisions},
 * line 382, inside the {@code else} opened at line 188; the inGround
 * branch at 172-186 has NO scan) and (2) the stick arm's own
 * single-hit-block consult (lines 307-309: {@code this.block
 * .onEntityCollision(...)} for the HIT BLOCK alone). A burning arrow
 * stuck in the STONE beside a TNT block therefore consults the TNT
 * ZERO times after the stick tick: the stick-tick scan covers only the
 * RESTING BOX (the 0.5 box at the face+0.05 rest, [y, y+0.5] under the
 * 1.8.8 setPosition feet law), and every later tick takes the inGround
 * branch. Vanilla's TNT beside a stuck burning arrow STANDS.</p>
 *
 * <p>THE ENGINE'S ROW (ProjectileManager.burningTntPass, the tick's
 * tail): NO inGround gate — every burning arrow, flying OR STUCK,
 * walks the symmetric 0.25 band around its position EVERY tick and
 * primes any TNT cell found. The stuck arrow's band reaches DOWN into
 * the floor row (the engine's [y−0.25, y+0.25] against the reference's
 * [y, y+0.5]) and SIDEWAYS into the adjacent TNT cell — the engine
 * detonates the neighbor on the stick tick itself.</p>
 *
 * <p>THE CONSTRUCTION (fully deterministic — spawnRestored's exact
 * position and velocity, no launch gaussian): a stone platform with
 * ONE TNT block embedded flush in its top row at (41, 20, 40), the
 * target stone cell beside it at (40, 20, 40). A burning arrow
 * descends straight down at x=40.9 — inside the stone column (the
 * stick cell (40, 20, 40)) but within 0.25 of the TNT cell's west
 * face (x=41.0), so the engine's band covers the TNT while the
 * arrow's own samples never enter it. THE REFERENCE'S ANSWER at its
 * face+0.05 rest (y≈21.05): the resting box [21.05, 21.55] covers the
 * AIR row above the platform alone — the TNT at row 20 is never
 * touched, before the stick (the descending boxes all sit above y=21),
 * on the stick tick, or after. THE ENGINE'S ANSWER: the band at the
 * endpoint rest (y≈20.60) covers row 20 — the TNT PRIMES on the stick
 * tick's pass.</p>
 *
 * <p>THE CONTROL (the flight-path law that must SURVIVE any fix): a
 * burning arrow flown straight down INTO the TNT cell itself — the
 * reference's stick arm consults the HIT BLOCK (TntBlock
 * .onEntityCollision, the flame-arrow priming mechanic) and the
 * engine's block-hit arm matches (the ignite consult before the stick,
 * the arrow keeping its flight through the opened cell). GREEN on
 * develop; the correction must keep it green (the gate belongs on the
 * STUCK pass, never on the flight consults).</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StuckBurningArrowTntDiscoveryTest {

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "stuckburning" + Math.abs(new java.util.Random().nextInt(100000)),
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
        EngineAwaits.awaitCondition(server.world(), condition, 240, description);
    }

    /** The 5x5 stone platform at row y=20 with ONE TNT block embedded
     *  flush at (41, 20, 40) — the target stone cell (40, 20, 40) sits
     *  directly west of it, sharing the face at x=41. */
    private static void buildPlatformWithEmbeddedTnt(EngineWorld world) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                world.setBlock(new BlockPosition(41 + dx, 20, 40 + dz),
                        BuiltinBlocks.STONE);
            }
        }
        world.setBlock(new BlockPosition(41, 20, 40), BuiltinBlocks.TNT);
    }

    /**
     * THE SEAT — the stuck burning arrow's neighbor TNT must survive:
     * the arrow sticks in the stone cell west of the embedded TNT
     * (x=40.9 keeps the stick cell at column 40 while the engine's
     * 0.25 band reaches column 41), and the TNT is asserted STANDING
     * ten ticks past the stick — long enough for the engine's ungated
     * pass to have fired, short of the 80-tick fuse's explosion.
     */
    @Test
    void theTntBesideAStuckBurningArrowStands() throws Exception {
        EngineWorld world = server.world();
        BlockPosition tntCell = new BlockPosition(41, 20, 40);

        server.ticker().submit(() -> buildPlatformWithEmbeddedTnt(world));
        await(() -> BuiltinBlocks.TNT.equals(world.getBlock(tntCell)),
                "the platform stands with the TNT embedded flush at (41, 20, 40)");

        // The exact body: a burning arrow descending straight down at
        // x=40.9 — the stick lands in the STONE column (floor(40.9)=40),
        // the samples never entering the TNT column.
        AtomicReference<ProjectileEntity> launched = new AtomicReference<>();
        server.ticker().submit(() -> {
            ProjectileEntity arrow = server.projectiles().spawnRestored(
                    ProjectileEntity.Kind.ARROW,
                    new Position(40.9, 25.0, 40.5),
                    0.0, -1.0, 0.0);
            arrow.setOnFireFor(100); // the Flame release's burning clock
            launched.set(arrow);
        });
        await(() -> launched.get() != null, "the burning arrow spawned above the platform");
        ProjectileEntity arrow = launched.get();
        await(() -> arrow.inGround(), "the burning arrow sticks in the stone beside the TNT");
        assertTrue(arrow.burning(), "the arrow is still burning through the stick window");

        // Ten ticks past the stick: the engine's ungated pass has had
        // every opportunity; the fuse (80 ticks) has not blown.
        EngineAwaits.awaitTicks(world, 10, "the stuck burning arrow's neighbor window");

        assertTrue(BuiltinBlocks.TNT.equals(world.getBlock(tntCell)),
                "VT-019: the TNT beside a STUCK burning arrow must STAND (the reference "
                        + "consults onEntityCollision from the FLIGHT branch's tail scan "
                        + "alone — ArrowEntity line 382, the inGround branch at 172-186 "
                        + "runs no scan — plus the stick arm's single-HIT-BLOCK consult "
                        + "at lines 307-309; the arrow stuck in the STONE at (40, 20, 40) "
                        + "consults the TNT zero times, and the stick tick's own scan "
                        + "covers only the resting box [y, y+0.5] — the AIR row above "
                        + "the platform at the face+0.05 rest, never row 20). Observed: "
                        + "the TNT at (41, 20, 40) is GONE — the engine's burningTntPass "
                        + "carries no inGround gate: the STUCK arrow walked its 0.25 "
                        + "band into the floor row (the engine's symmetric [y-0.25, "
                        + "y+0.25] against the reference's feet-law [y, y+0.5]) and "
                        + "primed the neighbor on the stick tick's pass.");
    }

    /**
     * THE CONTROL — the flame-arrow priming the flight path OWES: a
     * burning arrow flown straight down INTO the TNT cell itself primes
     * it (the reference's stick-arm hit-block consult — TntBlock
     * .onEntityCollision — and the engine's block-hit arm agree; the
     * arrow keeps its flight through the opened cell). GREEN on
     * develop; any VT-019 correction must keep it green (the gate
     * belongs on the stuck pass, never on the flight consults).
     */
    @Test
    void theBurningArrowFlownIntoTheTntPrimesIt() throws Exception {
        EngineWorld world = server.world();
        BlockPosition tntCell = new BlockPosition(41, 20, 40);

        server.ticker().submit(() -> buildPlatformWithEmbeddedTnt(world));
        await(() -> BuiltinBlocks.TNT.equals(world.getBlock(tntCell)),
                "the platform stands with the TNT embedded at (41, 20, 40)");

        // The exact body: straight down at the TNT's own column.
        AtomicReference<ProjectileEntity> launched = new AtomicReference<>();
        server.ticker().submit(() -> {
            ProjectileEntity arrow = server.projectiles().spawnRestored(
                    ProjectileEntity.Kind.ARROW,
                    new Position(41.5, 25.0, 40.5),
                    0.0, -1.0, 0.0);
            arrow.setOnFireFor(100);
            launched.set(arrow);
        });
        await(() -> launched.get() != null, "the burning arrow spawned above the TNT");

        // The priming: the cell opens (the block leaves, the primed
        // body rides the engine's own TNT arm).
        await(() -> !BuiltinBlocks.TNT.equals(world.getBlock(tntCell)),
                "the TNT primes when the burning arrow's flight enters its cell "
                        + "(TntBlock.onEntityCollision — the stick arm's hit-block "
                        + "consult in the reference, the block-hit arm's ignite in "
                        + "the engine)");

        assertFalse(BuiltinBlocks.TNT.equals(world.getBlock(tntCell)),
                "THE CONTROL: the flight-path priming stands (the VT-019 gate must "
                        + "never touch the flight consults — the burning arrow flown "
                        + "INTO the TNT cell primes it in the reference and the "
                        + "engine alike)");
    }
}
