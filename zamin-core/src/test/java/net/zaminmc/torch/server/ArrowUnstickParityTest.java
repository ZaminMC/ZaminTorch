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
 * THE PARITY PIN for VT-015's correction — the stuck arrow's
 * tracked-block UN-STICK law
 * (docs/vanilla-conformance-audit.md, the thirty-second pass; the fix
 * branch {@code fix/arrow-unstick-tracked-block-parity-audit}).
 *
 * <p>THE LAW — the reference's {@code inGround} branch
 * ({@code ArrowEntity.tick} lines 172-186): every stuck tick re-reads
 * the tracked cell ({@code blockX/Y/Z}, the cell the hit ray found —
 * lines 289-292) and compares its block against the remembered
 * {@code block}/{@code blockData} pair; a cell that no longer matches
 * un-sticks the arrow — {@code inGround = false}, the velocity re-arms
 * as {@code v * (random.nextFloat() * 0.2F)} per component (three
 * rolls, X/Y/Z order), both clocks reset — and the flight resumes on
 * the NEXT tick (the un-stick lands inside the inGround branch, after
 * the branch was chosen).</p>
 *
 * <p>THE PINS and their windows: the un-stick is a TRANSIENT state —
 * an arrow that leaves an opened cell falls and re-sticks in whatever
 * it meets below (the reference's own flight law), so the pins observe
 * the fall in a SHORT window (the un-stick tick + a few flight ticks,
 * long before the terrain sixteen blocks below) and then the lifecycle
 * re-stick in a long one. (1) the block REMOVED — the arrow un-sticks
 * and falls out of the opened cell, then re-sticks on the terrain far
 * below; (2) the block REPLACED with a different solid (stone →
 * cobblestone) — the type comparison alone un-sticks (the solidity
 * never changed), and the body re-sticks in the swapped block carrying
 * the NEW tracked pair. The stability control rides pin 1: while the
 * stone stands, the arrow stays stuck.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArrowUnstickParityTest {

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "unstickparity" + Math.abs(new java.util.Random().nextInt(100000)),
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

    /** The construction: an arrow spawned dead above a floating stone
     *  block's center at speed 1 — sticks on the fourth tick, tracked. */
    private ProjectileEntity stickInStone() throws Exception {
        EngineWorld overworld = server.world();
        server.ticker().submit(() -> overworld.setBlock(
                new BlockPosition(10, 20, 10), BuiltinBlocks.STONE));
        await(() -> BuiltinBlocks.STONE.equals(overworld.getBlock(
                        new BlockPosition(10, 20, 10))),
                "the floating stone block stands");
        var launched = new java.util.concurrent.atomic.AtomicReference<ProjectileEntity>();
        server.ticker().submit(() -> launched.set(server.projectiles().spawnRestored(
                ProjectileEntity.Kind.ARROW,
                new Position(10.5, 25.0, 10.5), 0.0, -1.0, 0.0)));
        await(() -> launched.get() != null, "the arrow spawned dead above the stone");
        ProjectileEntity arrow = launched.get();
        await(() -> arrow.inGround(), "the arrow sticks in the stone");
        return arrow;
    }

    /**
     * PIN 1 — the block REMOVED: the tracked cell opens (the dig, the
     * blast, the piston — the same consult every stuck tick runs), and
     * the arrow must un-stick and fall out of the opened cell. The
     * short window (five ticks) observes the fall itself — long before
     * the terrain below; the long window then pins the lifecycle: the
     * arrow lands and re-sticks FAR below the opened cell. The
     * stability control first: twenty ticks with the stone standing,
     * the arrow stays stuck at the unchanged position.
     */
    @Test
    void theStuckArrowUnSticksAndFallsWhenItsBlockBreaks() throws Exception {
        EngineWorld overworld = server.world();
        ProjectileEntity arrow = stickInStone();
        Position stuckAt = arrow.position();

        // THE STABILITY CONTROL: the stone stands, the remembered pair
        // matches, the stick clock alone runs.
        EngineAwaits.awaitTicks(overworld, 20, "the stability control holds");
        assertTrue(arrow.inGround(),
                "the control: the arrow stays stuck while the stone stands");
        assertTrue(arrow.position().y() == stuckAt.y()
                        && arrow.position().x() == stuckAt.x()
                        && arrow.position().z() == stuckAt.z(),
                "the control: the stuck arrow does not move while the stone stands");

        // THE TRACKED CELL OPENS.
        server.ticker().submit(() -> overworld.setBlock(
                new BlockPosition(10, 20, 10), overworld.airType()));
        await(() -> overworld.getBlock(new BlockPosition(10, 20, 10))
                        .equals(overworld.airType()),
                "the tracked cell opens (the stone removed)");

        // THE SHORT WINDOW: the un-stick tick + the first flight ticks —
        // the body leaves the opened cell downward (the column below is
        // air for sixteen blocks; nothing can re-stick it here).
        EngineAwaits.awaitTicks(overworld, 5, "the fall's short window");
        assertFalse(arrow.inGround(),
                "VT-015 pin 1a: the arrow UN-STICKS when its tracked block is removed "
                        + "(ArrowEntity lines 172-186 — the grounded tick's consult finds "
                        + "the cell no longer holding the remembered block; the flag flips, "
                        + "the velocity re-arms randomized, the clocks reset). Observed "
                        + "position: " + arrow.position() + " against the stuck rest "
                        + stuckAt);
        assertTrue(arrow.position().y() < stuckAt.y() - 0.001,
                "VT-015 pin 1a: the un-stuck arrow FALLS out of the opened cell (the "
                        + "flight resumes on the tick after the un-stick, the gravity "
                        + "term moving the body down). Observed: " + arrow.position()
                        + " against the stuck rest " + stuckAt);

        // THE LIFECYCLE: the arrow falls the sixteen-block column and
        // re-sticks on the terrain far below the opened cell.
        await(() -> arrow.inGround(), "the fallen arrow re-sticks on the terrain below");
        assertTrue(arrow.position().y() < stuckAt.y() - 2.0,
                "VT-015 pin 1b: the re-stuck arrow landed FAR below the opened cell (the "
                        + "whole column was air — only the terrain could catch it). "
                        + "Observed re-stick: " + arrow.position() + " against the opened "
                        + "cell at " + stuckAt);
    }

    /**
     * PIN 2 — the block REPLACED: the tracked cell still holds a solid
     * block, but a DIFFERENT one (stone → cobblestone). The reference's
     * consult compares the block (and metadata), never the solidity —
     * the swap alone un-sticks. The body then falls its one flight step
     * inside the swapped cell and re-sticks carrying the NEW tracked
     * pair (the fresh cobblestone memory — impossible without the
     * un-stick, the flight, and the re-stick chain).
     */
    @Test
    void theStuckArrowUnSticksWhenItsBlockIsReplacedWithAnother() throws Exception {
        EngineWorld overworld = server.world();
        ProjectileEntity arrow = stickInStone();
        Position stuckAt = arrow.position();

        // THE SWAP: stone → cobblestone, the cell never opening.
        server.ticker().submit(() -> overworld.setBlock(
                new BlockPosition(10, 20, 10), BuiltinBlocks.COBBLESTONE));
        await(() -> BuiltinBlocks.COBBLESTONE.equals(overworld.getBlock(
                        new BlockPosition(10, 20, 10))),
                "the tracked cell's block swapped (stone to cobblestone)");

        // THE WINDOW: the un-stick tick + the flight step + the re-stick.
        EngineAwaits.awaitTicks(overworld, 10, "the swap's window");
        assertTrue(BuiltinBlocks.COBBLESTONE.equals(arrow.trackedBlock()),
                "VT-015 pin 2: the re-stick carries the NEW tracked pair (cobblestone) — "
                        + "the un-stick ran (the remembered pair broke), the flight took "
                        + "its step, and the re-stick recorded the swapped block. Observed "
                        + "tracked block: " + arrow.trackedBlock() + " (the stuck rest was "
                        + stuckAt + ")");
        assertTrue(arrow.inGround(),
                "VT-015 pin 2: the popped arrow RE-STICKS in the swapped block (the "
                        + "reference's own flight law — a body leaving a cell that still "
                        + "holds a solid block embeds again on the way through; the rest "
                        + "lands at the same face, the reference's own pop-and-re-embed "
                        + "construction). Observed: " + arrow.position() + " against the "
                        + "stuck rest " + stuckAt);
    }
}
