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
 * THE VERIFIER'S VT-015 SECOND-CONSTRUCTION SEAT — the tracked-block
 * UN-STICK law (docs/vanilla-conformance-audit.md, the thirty-fourth
 * pass): the independent lane's confirmation of the thirty-third
 * pass's discovery seat through a DIFFERENT construction — the exact
 * spawnRestored descent (no launch gaussian), the stuck cell pinned by
 * construction rather than observed, and the precondition robust to
 * VT-014's own correction (either rest geometry accepted: the
 * engine's endpoint rest inside the stone row, or a fixed tree's
 * face+0.05 rest outside it — the tracked cell is the HIT BLOCK in
 * both models).
 *
 * <p>THE REFERENCE LAW (ArrowEntity.tick lines 172-186, re-walked for
 * this seat): the {@code inGround} branch consults the TRACKED cell —
 * the block the ray hit, stamped at stick time as
 * {@code blockX/blockY/blockZ} + {@code block/blockData} (lines
 * 289-295) — EVERY TICK. When the cell's block or metadata no longer
 * matches the stamped pair (a piston moved it, a blast cleared it, a
 * player broke it), the arrow UN-STICKS: {@code inGround = false}, the
 * velocity re-arms with the {@code random.nextFloat() * 0.2} damped
 * components (lines 180-183), and both clocks reset
 * ({@code inBlockTicks = inAirTicks = 0}) — the arrow falls out of the
 * opened cell and flies again. (The head-of-tick containment check at
 * lines 157-166 re-arms {@code inGround} only when the tracked cell's
 * block is non-air AND its box contains the arrow's position — a
 * broken block fails the material gate and never re-arms.)</p>
 *
 * <p>THE ENGINE'S ROW (ProjectileManager.tick's inGround arm, lines
 * 853-858 — the pass-1 walk): the stuck arm runs the stick clock alone
 * ({@code integrate} over a grounded body increments
 * {@code ticksInGround}; the 1200-tick expiry removes it) — there is
 * NO tracked cell in the model at all (ProjectileEntity carries no
 * block/position stamp), no block-change consult, no un-stick. The
 * engine's stuck arrow hangs in the air exactly where its step ended,
 * whatever happens to the block it rests in, until the expiry.</p>
 *
 * <p>THE SEAT: an arrow stuck in a known stone cell; the cell is
 * broken (the canonical observable — "break the block an arrow is
 * stuck in and the vanilla arrow pops out and falls"); forty ticks
 * later the reference's arrow is airborne again and falling. The
 * engine's stays stuck — the assertion pins the un-stick + the fall,
 * robust to the randomized re-arm's exact magnitude (the reference's
 * damped 0.2 factor is a random draw; the FALL is the observable).</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArrowUnstickVerificationTest {

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
        EngineAwaits.awaitCondition(server.world(), condition, 120, description);
    }

    /** A 3x3 stone platform whose TOP FACE sits at y=20 (the stone row at y=19). */
    private static void buildPlatform(EngineWorld world, int cx, int cy, int cz) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.setBlock(new BlockPosition(cx + dx, cy, cz + dz), BuiltinBlocks.STONE);
            }
        }
    }

    /**
     * THE SEAT — the block-break un-stick: the arrow sticks in the
     * stone cell at (80, 19, 80) (the engine's endpoint rest — its
     * position cell IS the stone row; the reference's tracked cell is
     * the same row, the block the ray hit). The cell is broken to air.
     * The reference un-sticks on the very next tick (the stamped
     * stone-vs-air mismatch) and the arrow falls. The engine's arrow
     * stays stuck — no consult exists — and hangs until the 1200-tick
     * expiry.
     */
    @Test
    void theStuckArrowUnSticksWhenItsBlockBreaks() throws Exception {
        EngineWorld world = server.world();
        int cx = 80;
        int cz = 80;
        int stoneY = 19; // the stuck cell's row (both models agree: the
                         // reference tracks the hit block, the engine
                         // rests inside it)

        server.ticker().submit(() -> buildPlatform(world, cx, stoneY, cz));
        await(() -> BuiltinBlocks.STONE.equals(world.getBlock(new BlockPosition(cx, stoneY, cz))),
                "the stone platform stands");

        // The exact body: straight down, velocity exactly -1.0 — the
        // descent is purely vertical, the stick cell is the platform's
        // center column.
        AtomicReference<ProjectileEntity> launched = new AtomicReference<>();
        server.ticker().submit(() -> launched.set(server.projectiles().spawnRestored(
                ProjectileEntity.Kind.ARROW,
                new Position(cx + 0.5, 25.0, cz + 0.5),
                0.0, -1.0, 0.0)));
        await(() -> launched.get() != null, "the arrow spawned above the platform");
        ProjectileEntity arrow = launched.get();
        await(() -> arrow.inGround(), "the arrow sticks in the platform's stone row");

        Position stuckAt = arrow.position();
        double stuckY = stuckAt.y();
        assertTrue(stuckY >= 19.0 && stuckY <= 20.1,
                "the stuck rest is at the platform's stone row (the engine's endpoint "
                        + "rest inside the block, VT-014's own record) or just outside "
                        + "its face (the reference's face+0.05 rest, a fixed tree): "
                        + "observed y=" + stuckY);

        // BREAK THE BLOCK the arrow is stuck in — the canonical
        // observable. The tracked cell's stone becomes air.
        server.ticker().submit(() -> world.setBlock(
                new BlockPosition(cx, stoneY, cz), world.airType()));
        await(() -> world.getBlock(new BlockPosition(cx, stoneY, cz)).equals(world.airType()),
                "the stuck cell is broken to air");

        // Forty ticks of the opened cell: the reference's arrow
        // un-stuck on the first of them (the stamped pair stone/0 vs
        // the cell's air — the mismatch arm) and has been falling
        // since (the damped ~0.2 re-arm, then gravity's -0.05 per
        // tick; forty ticks is deep into the fall).
        EngineAwaits.awaitTicks(world, 40, "the broken cell settles");

        assertFalse(arrow.inGround(),
                "VT-015: the stuck arrow must UN-STICK when the block it is stuck "
                        + "in changes (the reference's tracked-cell consult, ArrowEntity "
                        + "lines 172-186: the inGround branch reads the stamped "
                        + "blockX/blockY/blockZ cell every tick, and a stone-to-air "
                        + "change fails the block == this.block && meta == this.blockData "
                        + "match — inGround flips false, the velocity re-arms with the "
                        + "nextFloat()*0.2 damping, the clocks reset, and the arrow "
                        + "falls out of the opened cell). Observed: the arrow is STILL "
                        + "inGround forty ticks after the cell it rests in (" + cx + ", "
                        + stoneY + ", " + cz + ") was broken to air — the engine's "
                        + "inGround arm (ProjectileManager.tick) runs the stick clock "
                        + "and the 1200-tick expiry alone; no tracked cell exists in "
                        + "the model and no consult fires. The rest position was y="
                        + stuckY + " (the engine's endpoint rest, inside the now-air "
                        + "cell).");
        assertTrue(arrow.position().y() < stuckY,
                "VT-015: the un-stuck arrow FALLS (the reference's re-armed velocity "
                        + "plus gravity — the arrow drops out of the opened cell and "
                        + "flies again). Observed: the arrow's position is unchanged "
                        + "at y=" + arrow.position().y() + " forty ticks after the "
                        + "break (stuck rest was y=" + stuckY + ") — the body hangs "
                        + "in the opened cell.");
    }
}
