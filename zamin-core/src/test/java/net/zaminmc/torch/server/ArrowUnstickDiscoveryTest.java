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
 * THE VERIFIER'S VT-015 SEAT — the tracked-block UN-STICK law
 * (docs/vanilla-conformance-audit.md, the thirty-first pass's honest
 * residue, pass 2 of 3): what happens to a stuck arrow when the block
 * it rests in CHANGES under it.
 *
 * <p>THE REFERENCE LAW (reference/1.8.8, ArrowEntity.tick lines
 * 157-186 — the head of every tick, before the {@code inGround}
 * branch): the STUCK arrow re-reads its TRACKED cell
 * ({@code blockX/blockY/blockZ} — the hit block's cell, recorded at
 * the stick, lines 290-292) every tick. When the block there no
 * longer matches the recorded identity — a piston moved it, a blast
 * cleared it, a player broke it — the else arm (lines 179-186)
 * UN-STICKS the arrow: {@code inGround = false}, each velocity
 * component multiplied by its own {@code random.nextFloat() * 0.2}
 * (a fresh random multiplier per axis), {@code inBlockTicks = 0},
 * {@code inAirTicks = 0}. The arrow falls out of the opened cell and
 * flies again (the flight branch resumes; gravity and drag re-run).
 * The un-changed arm (174-178) only counts the 1200-tick expiry.</p>
 *
 * <p>THE ENGINE'S SHAPE (ProjectileManager.tick's inGround branch,
 * lines ~853-858): the stuck arrow runs the stick clock ALONE —
 * {@code integrate} increments {@code ticksInGround}, the 1200 expiry
 * removes. NO block consult exists anywhere on the stuck path; the
 * engine's stuck arrow hangs in the air until expiry whatever happens
 * to the block it rests in.</p>
 *
 * <p>This seat is EXPECTED RED on develop until VT-015's correction
 * lands (the tracked-block consult + the randomized re-arm). Do not
 * re-pin it to the engine's current answer; the finding record owns
 * the mismatch.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArrowUnstickDiscoveryTest {

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "arrowunstick" + Math.abs(new java.util.Random().nextInt(100000)),
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
     * VT-015's seat: break the block a stuck arrow rests in — the
     * reference's tracked cell changes (the recorded identity no
     * longer matches), the un-stick arm fires, and the arrow FALLS OUT
     * of the opened cell (the randomized re-arm + the resumed gravity).
     * The engine's stuck arrow never consults the block: it hangs in
     * the air at its rest position until the 1200-tick expiry.
     *
     * <p>The construction: a 5x5 stone platform, top face y=20, an
     * arrow shot straight down into its center. The break targets the
     * platform's TOP CELL at the arrow's x/z — the hit block (the
     * reference's tracked cell; the engine's embedded cell at the
     * endpoint placement, the same cell for this construction). The
     * flat world's own terrain sits at y<=4 — the falling arrow meets
     * nothing between the platform and the ground, so ANY movement
     * after the break is the un-stick's signature; forty ticks later
     * the reference's arrow has either fallen past the platform's band
     * or re-stuck on the world's grass, both far below the rest.
     */
    @Test
    void theBrokenBlockUnderAStuckArrowUnsticksIt() throws Exception {
        EngineWorld overworld = server.world();
        int cx = 42;
        int cz = 42;
        server.ticker().submit(() -> {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    overworld.setBlock(new BlockPosition(cx + dx, 19, cz + dz),
                            BuiltinBlocks.STONE);
                }
            }
        });
        await(() -> BuiltinBlocks.STONE.equals(overworld.getBlock(
                        new BlockPosition(cx, 19, cz))),
                "the stone platform stands with its top face at y=20");

        AtomicReference<ProjectileEntity> launched = new AtomicReference<>();
        server.ticker().submit(() -> launched.set(server.projectiles().launch(
                ProjectileEntity.Kind.ARROW, -1,
                new Position(cx + 0.5, 24.5, cz + 0.5),
                180.0, 90.0, 0.8, 0)));
        await(() -> launched.get() != null, "the arrow launched straight down at the platform");
        ProjectileEntity arrow = launched.get();
        await(() -> arrow.inGround(), "the arrow sticks in the platform's top face (y=20)");

        Position rest = arrow.position();
        // The hit block: the platform's top-layer cell at the arrow's
        // x/z — the reference's tracked cell (the recorded block) and,
        // at the engine's endpoint placement, the embedded cell — the
        // same cell for this straight-down construction.
        BlockPosition hitCell = new BlockPosition(
                (int) Math.floor(rest.x()), 19, (int) Math.floor(rest.z()));

        // BREAK THE BLOCK THE ARROW RESTS IN — the player's pickaxe, the
        // blast's clearance, the piston's move: the tracked identity
        // no longer matches the cell.
        server.ticker().submit(() -> overworld.setBlock(hitCell, overworld.airType()));
        await(() -> overworld.getBlock(hitCell).equals(overworld.airType()),
                "the block under the stuck arrow is broken (cell " + hitCell + ")");

        // Forty ticks — the reference's un-stick fires on the FIRST tick
        // after the change; the fall needs a handful more. The engine's
        // answer: nothing moves.
        EngineAwaits.awaitTicks(server.world(), 40,
                "the broken cell stands open under the stuck arrow");

        // THE PIN: the arrow LEFT its rest — the un-stick's randomized
        // re-arm plus the resumed gravity carry it out of the opened
        // cell (the reference's else arm, ArrowEntity 179-186). The
        // engine's stuck arrow stays at its rest position, inGround,
        // until the 1200-tick expiry.
        assertTrue(arrow.position().y() < rest.y() - 0.5,
                "VT-015: the stuck arrow must FALL when the block it rests in is broken — "
                        + "the reference's ArrowEntity (lines 173-186) re-reads the tracked "
                        + "cell (" + hitCell + ") every tick and the changed identity fires "
                        + "the un-stick arm: inGround=false, the per-axis "
                        + "random*0.2 re-arm, the clocks reset, the fall resumes. Observed "
                        + "rest " + arrow.position() + " against the pre-break rest " + rest
                        + " — the arrow HANGS IN THE AIR at the broken cell, the stick "
                        + "clock alone running (the engine's inGround branch carries no "
                        + "block consult)");
        assertFalse(arrow.inGround() && arrow.position().y() > rest.y() - 0.5,
                "VT-015's complementary observation: the engine's arrow is still inGround "
                        + "AT THE BROKEN CELL's rest — the reference's arrow is never "
                        + "inGround there again (it un-stuck the moment the tracked cell "
                        + "changed; a re-stuck body would sit on the world's grass at "
                        + "y~5, never at the platform's band)");
    }
}
