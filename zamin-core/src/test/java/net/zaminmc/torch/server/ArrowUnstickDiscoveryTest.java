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
 * THE VERIFIER'S VT-015 SEAT — the stuck arrow's TRACKED-BLOCK UN-STICK
 * (docs/vanilla-conformance-audit.md, the thirty-first pass's honest
 * residue; this is the finding's PASS 2, the behavioral construction).
 *
 * <p>THE REFERENCE LAW (ArrowEntity.java lines 172-186, the
 * {@code inGround} branch that runs every stuck tick): the arrow
 * consults its TRACKED cell — the block the flight ray hit, recorded at
 * stick time as {@code blockX/Y/Z} + {@code block}/{@code blockData}
 * (lines 289-293). When that cell's block OR metadata no longer matches
 * (a piston moves it, a blast clears it, a player breaks it, the
 * arrow's own burning-TNT ignite arm opens the cell), the arrow
 * UN-STICKS: {@code inGround = false} (line 180), the velocity re-arms
 * with the ~0.2-randomized components (lines 181-183), the stick and
 * air clocks reset (lines 184-185) — and the arrow FALLS OUT and flies
 * again from the next tick.</p>
 *
 * <p>THE ENGINE'S LAW: {@code ProjectileEntity.inGround} has NO false
 * writer at all — {@code landInGround()} and {@code restoreGrounded()}
 * both set it true and nothing ever clears it; the manager's stuck arm
 * (ProjectileManager.tick's {@code inGround} branch) runs the stick
 * clock and the 1200-tick expiry ALONE. The engine's own javadoc
 * acknowledges the reference's law it does not implement
 * (ProjectileEntity.flingVelocity: "the reference's own damped 0.2
 * factor applies to them only if the stick cell's block ever changes"
 * — it never can, no consult exists).</p>
 *
 * <p>THE SEAT: an arrow stuck in a free-standing one-block pedestal
 * (the flat world's terrain ends at y=4; the pedestal row is y=25, so
 * the fall below the face is observable for dozens of ticks before any
 * terrain). Break the pedestal — the whole 3x3 row, so whichever cell
 * holds the tracked block (the engine's endpoint-inside cell or a fixed
 * engine's face-rest cell above it), it is gone. The reference un-sticks
 * and falls; the engine's arrow hangs stuck in the air until the
 * 1200-tick expiry.</p>
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
     * THE PIN — break the block a stuck arrow rests in and the arrow must
     * un-stick and fall (the reference's tracked-cell consult). The whole
     * 3x3 pedestal row goes to air so the tracked cell is certainly among
     * the removed blocks under EITHER placement model (the engine's
     * endpoint-inside rest or a corrected face+0.05 rest whose tracked
     * cell is the pedestal row below the body).
     */
    @Test
    void theStuckArrowUnsticksWhenItsBlockBreaks() throws Exception {
        EngineWorld world = server.world();
        int pedestalY = 25; // the single stone row; top face at y=26

        server.ticker().submit(() -> {
            for (int x = 39; x <= 41; x++) {
                for (int z = 39; z <= 41; z++) {
                    world.setBlock(new BlockPosition(x, pedestalY, z), BuiltinBlocks.STONE);
                }
            }
        });
        await(() -> BuiltinBlocks.STONE.equals(world.getBlock(new BlockPosition(40, pedestalY, 40))),
                "the stone pedestal stands");

        AtomicReference<ProjectileEntity> launched = new AtomicReference<>();
        server.ticker().submit(() -> launched.set(server.projectiles().launch(
                ProjectileEntity.Kind.ARROW, -1,
                new Position(40.5, 30.5, 40.5),
                180.0, 80.0, 0.8, 0)));
        await(() -> launched.get() != null, "the arrow launched above the pedestal");
        ProjectileEntity arrow = launched.get();
        await(arrow::inGround, "the arrow sticks in the pedestal");

        Position restAt = arrow.position();

        // BREAK THE PEDESTAL — the whole row, so the tracked cell (the
        // block the flight ray hit) is gone whatever the resting
        // geometry. This is the reference's un-stick trigger exactly: a
        // block/metadata change at the tracked cell.
        server.ticker().submit(() -> {
            for (int x = 39; x <= 41; x++) {
                for (int z = 39; z <= 41; z++) {
                    world.setBlock(new BlockPosition(x, pedestalY, z), world.airType());
                }
            }
        });
        await(() -> world.airType().equals(world.getBlock(new BlockPosition(40, pedestalY, 40))),
                "the pedestal is broken out from under the arrow");

        // Ten ticks: the reference un-sticks on the FIRST stuck tick
        // after the change and falls from the next (gravity -0.05/tick
        // accumulates ~2 blocks over the window; the terrain is 21
        // blocks below, so no re-stick can mask the fall).
        EngineAwaits.awaitTicks(server.world(), 10,
                "the window after the pedestal break");

        Position afterBreak = arrow.position();
        assertFalse(arrow.inGround(),
                "VT-015's pin: the stuck arrow must UN-STICK when the block it rests in "
                        + "breaks (the reference's ArrowEntity lines 172-186 — the tracked "
                        + "cell's block no longer matches the recorded block/blockData, so "
                        + "inGround flips false, the velocity re-arms ~0.2-randomized, the "
                        + "clocks reset, and the arrow falls out). Observed: the engine's "
                        + "arrow is STILL STUCK at " + afterBreak + " (rested at " + restAt
                        + " before the break) — ProjectileEntity.inGround has no false "
                        + "writer anywhere; the stuck arm runs the stick clock and the "
                        + "1200-tick expiry alone, so the arrow hangs in the air whatever "
                        + "happens to the block.");
        assertTrue(afterBreak.y() < restAt.y(),
                "VT-015's pin (the fall): the un-stuck arrow must fall once its block is "
                        + "gone. Observed rest " + afterBreak + " against the pre-break rest "
                        + restAt + " — the body never moved.");
    }
}
