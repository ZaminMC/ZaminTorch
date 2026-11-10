package net.zaminmc.torch.server.hopper;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WAVE KO-A — THE PER-CHUNK ITEM CENSUS (the engine-native port of
 * KigPaper/Paper 0089 "Avoid hopper searches if there are no items"):
 * the hopper suck and the minecart scans gate their candidate walks on
 * a live per-(dimension, chunk) count of item bodies, skipping the
 * walk — and the {@code all()} snapshot it would take — whenever the
 * chunks the query box covers hold zero items. The census is provably
 * EXACT: a zero count over the covered chunks means the walk's box
 * tests would answer nothing, so the skip changes no output, only the
 * work.
 *
 * <p>The seats pin the exactness where it can break: the SPAWN hook
 * (a body that never joins its chunk's count would make the hopper
 * skip a real candidate), the CROSSING re-key (a body that crosses a
 * chunk border inside the tick walk must leave its old count and join
 * the new one's — the arriving item must still be sucked), and the
 * far-item guard (an item in an uncovered chunk never feeds the idle
 * hopper, and the same hopper still works afterwards).</p>
 */
class KigPaperHopperCensusParityTest {

    @TempDir
    Path dataDir;

    /**
     * THE SPAWN-HOOK SEAT: an item spawned above the hopper — the cell
     * above's center — reaches the hopper through the census: the
     * spawn-site hook keyed its chunk, the span consult answers true,
     * the walk runs and the suck lands. RED on the no-op patch (the
     * spawn hook removed): the census reads the chunk empty, the skip
     * fires, and the item sits above a working hopper forever.
     */
    @Test
    void theSpawnedItemReachesTheHopperThroughTheCensus() throws Exception {
        EngineServer server = boot();
        try {
            int x = 10, z = 10, y = 40;
            placeHopper(server, x, y, z);
            drop(server, x + 0.5, y + 1.5, z + 0.5, 4);
            await(() -> hopperItemCount(server, x, y, z) == 4,
                    "the hopper sucked the spawned item through the census");
        } finally {
            server.shutdown(() -> { });
        }
    }

    /**
     * THE CROSSING-RE-KEY SEAT: an item spawned in the NEIGHBOR chunk
     * (x = -0.5, chunk -1) with an eastward flight that carries it
     * across the border into the hopper's cell-above band — the tick
     * walk's re-key must move its count into the hopper's chunk (0) or
     * the span consult keeps reading the hopper's own chunk empty and
     * the arriving item is never sucked. RED on the no-op patch (the
     * re-key removed): the item flies past and settles in the open
     * world while the working hopper idles.
     */
    @Test
    void theCrossingItemIsTrackedIntoTheHoppersChunk() throws Exception {
        EngineServer server = boot();
        try {
            // The hopper sits at x=1 (chunk 0, one block off the border);
            // its suck band spans (0.875, 2.125) — wholly inside chunk 0.
            int x = 1, z = 10, y = 40;
            placeHopper(server, x, y, z);
            // The item starts mid-air at x=-0.5 (chunk -1), flying east:
            // the flight crosses the border and passes through the band
            // at the cell-above height (center y 41.3, inside the strict
            // (by+0.875, by+2.125) y range).
            server.ticker().submit(() -> {
                var item = server.itemEntities().spawnRestored(
                        new Position(-0.5, 41.3, z + 0.5),
                        ItemStack.of(net.zaminmc.torch.server.item.BuiltinItems.REDSTONE, 3));
                if (item != null) {
                    item.setVelocity(2.0, 0.0, 0.0);
                }
            });
            await(() -> hopperItemCount(server, x, y, z) == 3,
                    "the crossing item was tracked into the hopper's chunk and sucked");
        } finally {
            server.shutdown(() -> { });
        }
    }

    /**
     * THE FAR-ITEM GUARD (the exactness seat, green on both sides by
     * design): an item dropped in a FAR chunk — far outside the hopper's
     * census span — is never sucked (the walk it would be skipped from
     * would not have taken it either: the box tests answer nothing), and
     * the same hopper still sucks a fresh item dropped above it right
     * after: the skip path leaves the hopper fully functional.
     */
    @Test
    void theFarItemNeverFeedsTheIdleHopper() throws Exception {
        EngineServer server = boot();
        try {
            int x = 10, z = 10, y = 40;
            placeHopper(server, x, y, z);
            // An item 90 blocks east: another chunk, another census span.
            drop(server, 100.5, 45.5, z + 0.5, 7);
            // A full second of hopper ticks: the far item never lands in
            // the hopper (and never disappears).
            assertNeverWithin(1_000, () -> hopperItemCount(server, x, y, z) > 0,
                    "the far item never fed the idle hopper");
            assertEquals(7, redstoneCount(server),
                    "the far item still stands in the world");
            // The same hopper, right after: a fresh item above it IS
            // sucked — the skip path cost nothing.
            drop(server, x + 0.5, y + 1.5, z + 0.5, 4);
            await(() -> hopperItemCount(server, x, y, z) == 4,
                    "the working hopper still sucks after the idle span");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------ harness

    private void placeHopper(EngineServer server, int x, int y, int z)
            throws InterruptedException {
        server.ticker().submit(() -> server.world().setBlock(new BlockPosition(x, y, z),
                HopperBlocks.hopperOf(0, true)));
        await(() -> HopperBlocks.isHopper(
                server.world().getBlock(new BlockPosition(x, y, z))),
                "the hopper placed");
    }

    private void drop(EngineServer server, double x, double y, double z, int count) {
        server.ticker().submit(() -> server.itemEntities().spawnRestored(
                new Position(x, y, z),
                ItemStack.of(net.zaminmc.torch.server.item.BuiltinItems.REDSTONE, count)));
    }

    private int hopperItemCount(EngineServer server, int x, int y, int z) {
        AtomicReference<Integer> result = new AtomicReference<>();
        server.ticker().submit(() -> {
            HopperBlockEntity hopper = server.hopper().peek(new BlockPosition(x, y, z));
            int total = 0;
            if (hopper != null) {
                for (ItemStack stack : hopper.snapshotSlots()) {
                    if (!stack.isEmpty()) {
                        total += stack.count();
                    }
                }
            }
            result.set(total);
        });
        long deadline = System.currentTimeMillis() + 2_000;
        while (System.currentTimeMillis() < deadline && result.get() == null) {
            sleep(10);
        }
        return result.get() == null ? 0 : result.get();
    }

    private int redstoneCount(EngineServer server) {
        AtomicReference<Integer> result = new AtomicReference<>();
        server.ticker().submit(() -> {
            int sum = 0;
            for (var item : server.itemEntities().all()) {
                if (item.stack().type().identifier().value().equals("redstone")) {
                    sum += item.stack().count();
                }
            }
            result.set(sum);
        });
        long deadline = System.currentTimeMillis() + 2_000;
        while (System.currentTimeMillis() < deadline && result.get() == null) {
            sleep(10);
        }
        return result.get() == null ? 0 : result.get();
    }

    private static void assertNeverWithin(long windowMillis, BooleanSupplier condition,
                                          String description) throws InterruptedException {
        long deadline = System.currentTimeMillis() + windowMillis;
        while (System.currentTimeMillis() < deadline) {
            assertFalse(condition.getAsBoolean(),
                    "FAIL: " + description + " (the condition became true within "
                            + windowMillis + "ms)");
            Thread.sleep(100);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "kohop" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false,
                EngineConfig.WHOLE_WORLD_REGION_SIZE);
        EngineServer server = new EngineServer(config);
        server.start();
        return server;
    }

    private void await(BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }
}
