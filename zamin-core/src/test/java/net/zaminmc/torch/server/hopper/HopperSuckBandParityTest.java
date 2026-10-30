package net.zaminmc.torch.server.hopper;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.ItemEntity;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The block hopper's item-suck BAND against the reference's own box
 * (the eighteenth verifier pass's F-HP1 seats): the suck box is the
 * reference's {@code getItem(world, hopper.getX(), hopper.getY() + 1.0,
 * hopper.getZ())} — the Box ±0.5 around the mouth point
 * {@code pos + 0.5} lifted one block — i.e. the FULL CELL ABOVE the
 * hopper, gathered by box intersection with each item's own 0.25 box
 * (strict bounds): the item-CENTER band
 * {@code (by+0.875, by+2.125)} x {@code (bx-0.125, bx+1.125)} x
 * {@code (bz-0.125, bz+1.125)}.
 *
 * <p>The two discriminating seats: the item resting HIGH in the cell
 * above (on a bottom slab — center {@code by+1.625}, inside the
 * reference box, above the pre-fix {@code [by+0.5, by+1.5]} band) MUST
 * be sucked; the item SETTLED in the hopper's own funnel mouth (center
 * {@code by+0.75}, below the reference box's strict bottom
 * {@code by+0.875}) must NEVER be sucked — the pre-fix band ate settled
 * mouth items, vanilla only takes items whose box still reaches the
 * cell above (the descent and the {@code motionY *= -0.5D} bounce
 * window).</p>
 *
 * <p>The guard seats: the mid-fall catch (the vanilla mouth-eating
 * mechanism — a falling item is taken while airborne), the shipped
 * boundary spawn (the cell-above center, {@code by+1.5} — inside both
 * laws), and the strict top (an item resting on a top slab filling the
 * cell above's upper half touches the box top at exactly
 * {@code by+2} — no intersection, refused).</p>
 */
class HopperSuckBandParityTest {

    @TempDir
    Path dataDir;

    /**
     * THE HIGH-BAND POSITIVE (the discriminating seat): a bottom slab in
     * the cell above the hopper, the item resting on it at center
     * {@code by+1.625} — the reference's box
     * {@code [by+1, by+2]} contains the item's whole 0.25 box, so vanilla
     * collects it; the pre-fix {@code [by+0.5, by+1.5]} band never
     * reached above 1.5 and the item sat on the slab forever.
     */
    @Test
    void theSuckBandReachesTheItemRestingHighInTheCellAbove() throws Exception {
        EngineServer server = boot(EngineConfig.WHOLE_WORLD_REGION_SIZE);
        try {
            int x = 10, z = 10, y = 40;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y, z),
                        HopperBlocks.hopperOf(0, true));
                // A bottom slab fills the cell above's lower half: its
                // top surface sits at by+1.5.
                server.world().setBlock(new BlockPosition(x, y + 1, z),
                        BuiltinBlocks.STONE_SLAB);
            });
            awaitHopper(server, x, y, z);
            // The item AT REST on the slab: feet by+1.5, center by+1.625 —
            // spawned exactly at rest (no pop, no fall).
            server.ticker().submit(() -> server.itemEntities().spawnRestored(
                    new Position(x + 0.5, y + 1.625, z + 0.5),
                    ItemStack.of(net.zaminmc.torch.server.item.BuiltinItems.REDSTONE, 4)));
            await(() -> hopperFirstSlotCount(server, x, y, z) == 4,
                    "the hopper sucked the item resting high in the cell above "
                            + "(the reference box's upper half)");
        } finally {
            server.shutdown(() -> { });
        }
    }

    /**
     * THE MOUTH NEGATIVE (the discriminating seat): an item settled in
     * the hopper's own funnel mouth — resting on the collision slab at
     * center {@code by+0.75}, its whole box below the reference box's
     * strict bottom {@code by+0.875}. Vanilla NEVER takes it (the
     * settled mouth item waits for a knock — only the descent/bounce
     * window feeds the hopper); the pre-fix band
     * {@code [by+0.5, by+1.5]} ate it on the very next transfer.
     */
    @Test
    void theSuckBandRefusesTheSettledFunnelMouthItem() throws Exception {
        EngineServer server = boot(EngineConfig.WHOLE_WORLD_REGION_SIZE);
        try {
            int x = 30, z = 30, y = 40;
            server.ticker().submit(() ->
                    server.world().setBlock(new BlockPosition(x, y, z),
                            HopperBlocks.hopperOf(0, true)));
            awaitHopper(server, x, y, z);
            // The item AT REST in the mouth: feet by+0.625 (the hopper's
            // own collision-slab top), center by+0.75.
            server.ticker().submit(() -> server.itemEntities().spawnRestored(
                    new Position(x + 0.5, y + 0.75, z + 0.5),
                    ItemStack.of(net.zaminmc.torch.server.item.BuiltinItems.REDSTONE, 4)));
            // Three full seconds — many 8-tick transfer cycles: the
            // hopper never takes the settled mouth item, and the item
            // stays in the world with its whole stack.
            assertNeverWithin(3_000, () ->
                            hopperFirstSlotCount(server, x, y, z) == 4
                                    || redstoneItemsNear(server, x, y, z) == 0,
                    "the settled funnel-mouth item was never taken (below the "
                            + "reference box's strict bottom)");
            assertEquals(4, redstoneItemsNear(server, x, y, z),
                    "the mouth item stayed in the world whole");
        } finally {
            server.shutdown(() -> { });
        }
    }

    /**
     * THE DESCENT GUARD: a falling item passes through the cell above
     * airborne and is taken MID-FLIGHT — the vanilla mechanism behind
     * "hoppers eat dropped items" (the box never needs the item to
     * land; the reference catches it while its box still overlaps the
     * cell above).
     */
    @Test
    void theSuckBandCatchesTheFallingItemDuringItsDescent() throws Exception {
        EngineServer server = boot(EngineConfig.WHOLE_WORLD_REGION_SIZE);
        try {
            int x = 50, z = 50, y = 40;
            server.ticker().submit(() ->
                    server.world().setBlock(new BlockPosition(x, y, z),
                            HopperBlocks.hopperOf(0, true)));
            awaitHopper(server, x, y, z);
            // Spawned a block and a half above the box top: the item
            // falls through the whole cell above.
            server.ticker().submit(() -> server.itemEntities().spawnRestored(
                    new Position(x + 0.5, y + 2.5, z + 0.5),
                    ItemStack.of(net.zaminmc.torch.server.item.BuiltinItems.REDSTONE, 2)));
            await(() -> hopperFirstSlotCount(server, x, y, z) == 2,
                    "the hopper caught the item during its descent through "
                            + "the cell above");
        } finally {
            server.shutdown(() -> { });
        }
    }

    /**
     * THE SHIPPED BOUNDARY GUARD: the cell-above center
     * ({@code by+1.5}) — the exact spawn of the shipped suck seat — sits
     * inside BOTH laws and must keep collecting.
     */
    @Test
    void theSuckBandKeepsTheShippedCellAboveCenterSpawn() throws Exception {
        EngineServer server = boot(EngineConfig.WHOLE_WORLD_REGION_SIZE);
        try {
            int x = 70, z = 70, y = 40;
            server.ticker().submit(() ->
                    server.world().setBlock(new BlockPosition(x, y, z),
                            HopperBlocks.hopperOf(0, true)));
            awaitHopper(server, x, y, z);
            server.ticker().submit(() -> server.itemEntities().spawnRestored(
                    new Position(x + 0.5, y + 1.5, z + 0.5),
                    ItemStack.of(net.zaminmc.torch.server.item.BuiltinItems.REDSTONE, 3)));
            await(() -> hopperFirstSlotCount(server, x, y, z) == 3,
                    "the cell-above center spawn still collects");
        } finally {
            server.shutdown(() -> { });
        }
    }

    /**
     * THE STRICT TOP GUARD: an item resting on a TOP slab filling the
     * cell above's upper half sits at box {@code [by+2, by+2.25]} —
     * touching the reference box's top face at exactly {@code by+2}.
     * The reference's {@code Box.intersectsWith} is strict (touching is
     * not intersecting): refused — and the pre-fix band refused it too.
     */
    @Test
    void theSuckBandRefusesTheItemTouchingTheBoxTopExactly() throws Exception {
        EngineServer server = boot(EngineConfig.WHOLE_WORLD_REGION_SIZE);
        try {
            int x = 90, z = 90, y = 40;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y, z),
                        HopperBlocks.hopperOf(0, true));
                server.world().setBlock(new BlockPosition(x, y + 1, z),
                        server.world().airType());
            });
            awaitHopper(server, x, y, z);
            // The top slab's own cell: [y+1.5, y+2]; the item rests on
            // it at center by+2.125 — the touching case.
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y + 1, z),
                        net.zaminmc.torch.server.block.BuiltinBlocks.STONE_SLAB_TOP);
                server.itemEntities().spawnRestored(
                        new Position(x + 0.5, y + 2.125, z + 0.5),
                        ItemStack.of(net.zaminmc.torch.server.item.BuiltinItems.REDSTONE, 4));
            });
            assertNeverWithin(3_000,
                    () -> hopperFirstSlotCount(server, x, y, z) == 4,
                    "the item touching the box top exactly is refused "
                            + "(strict bounds)");
            assertEquals(4, redstoneItemsNear(server, x, y, z),
                    "the touching item stayed in the world whole");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // Helpers (the shipped file's own shapes)
    // ------------------------------------------------------------------

    private static void awaitHopper(EngineServer server, int x, int y, int z)
            throws InterruptedException {
        AtomicReference<Boolean> placed = new AtomicReference<>(null);
        server.ticker().submit(() -> placed.set(HopperBlocks.isHopper(
                server.world().getBlock(new BlockPosition(x, y, z)))));
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline && placed.get() == null) {
            sleep(10);
        }
        assertTrue(Boolean.TRUE.equals(placed.get()), "the hopper placed");
    }

    private static int hopperFirstSlotCount(EngineServer server, int x, int y, int z) {
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

    private static int redstoneItemsNear(EngineServer server, int x, int y, int z) {
        AtomicInteger total = new AtomicInteger(0);
        server.ticker().submit(() -> {
            int sum = 0;
            for (ItemEntity item : server.itemEntities().all()) {
                Position at = item.position();
                if (Math.abs(at.x() - (x + 0.5)) <= 1.0
                        && Math.abs(at.y() - (y + 1.0)) <= 1.5
                        && Math.abs(at.z() - (z + 0.5)) <= 1.0
                        && item.stack().type().identifier().value().equals("redstone")) {
                    sum += item.stack().count();
                }
            }
            total.set(sum);
        });
        long deadline = System.currentTimeMillis() + 2_000;
        while (System.currentTimeMillis() < deadline && total.get() == 0) {
            sleep(10);
        }
        return total.get();
    }

    /** Sleeps the full window, then asserts the condition NEVER became true. */
    private static void assertNeverWithin(long windowMillis, BooleanSupplier condition,
                                           String description) throws InterruptedException {
        long deadline = System.currentTimeMillis() + windowMillis;
        while (System.currentTimeMillis() < deadline) {
            assertFalse(condition.getAsBoolean(),
                    "FAIL: " + description + " (the condition became true within "
                            + windowMillis + "ms)");
            sleep(100);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private EngineServer boot(int regionSizeChunks) throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "sbt" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, regionSizeChunks);
        EngineServer server = new EngineServer(config);
        server.start();
        return server;
    }

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
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
