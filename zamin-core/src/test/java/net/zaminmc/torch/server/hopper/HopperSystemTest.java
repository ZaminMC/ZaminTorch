package net.zaminmc.torch.server.hopper;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.redstone.RedstoneBlocks;
import net.zaminmc.torch.server.world.EngineWorld;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The hopper on the live engine (Slice 10b — the reference port of
 * HopperBlock + HopperBlockEntity): the 8-tick transfer cadence, the push
 * toward the FACING container (the chest's 27 slots), the pull from the
 * container above, the furnace's sided slots (the top feeds the input, the
 * sides the fuel), the powered disable, the item-entity suck, and the
 * §11 boundary transfer across a one-chunk region seam.
 */
class HopperSystemTest {

    @TempDir
    Path dataDir;

    @Test
    void theHopperPushesIntoTheChestBelowOnTheCadence() throws Exception {
        EngineServer server = boot(EngineConfig.WHOLE_WORLD_REGION_SIZE);
        try {
            int x = 10, z = 10, y = 40;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y, z), BuiltinBlocks.CHEST);
                // The hopper above the chest, facing down.
                server.world().setBlock(new BlockPosition(x, y + 1, z),
                        HopperBlocks.hopperOf(0, true));
                // One stack rides the hopper's first slot.
                server.hopper().getOrCreate(new BlockPosition(x, y + 1, z))
                        .setItem(0, ItemStack.of(
                                net.zaminmc.torch.server.item.BuiltinItems.DIAMOND, 5));
            });
            await(() -> chestSlot(server, x, y, z, 0).count() == 1,
                    "the hopper pushed the first item into the chest");
            // The cadence: the next item lands about 8 ticks later (the
            // transfer cooldown), never faster.
            long secondObservedAt = System.currentTimeMillis();
            await(() -> chestSlot(server, x, y, z, 0).count() == 2,
                    "the second item followed on the 8-tick cadence");
            secondObservedAt = System.currentTimeMillis();
            assertEquals(3, hopperSlot(server, x, y + 1, z, 0).count(),
                    "the hopper kept the rest");
            // The cooldown holds: the third transfer cannot arrive before
            // the 8-tick cooldown (400ms) has passed since the second —
            // measured between the OBSERVED transfers (observation latency
            // only stretches the observed gap, so a lower bound of 300ms
            // proves the cadence while tolerating sampling jitter on a
            // loaded box).
            long thirdObservedAt = 0;
            long deadline = secondObservedAt + 3_000;
            while (System.currentTimeMillis() < deadline) {
                if (chestSlot(server, x, y, z, 0).count() == 3) {
                    thirdObservedAt = System.currentTimeMillis();
                    break;
                }
                sleep(10);
            }
            assertTrue(thirdObservedAt > 0, "the third item eventually followed");
            assertTrue(thirdObservedAt - secondObservedAt >= 300,
                    "the cadence held (no faster than 8 ticks): observed gap "
                            + (thirdObservedAt - secondObservedAt) + "ms");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theHopperPullsFromTheContainerAbove() throws Exception {
        EngineServer server = boot(EngineConfig.WHOLE_WORLD_REGION_SIZE);
        try {
            int x = 30, z = 30, y = 40;
            server.ticker().submit(() -> {
                // The chest above the hopper holds one stack; the hopper
                // faces down into the floor (no target): only the pull arm
                // runs.
                server.world().setBlock(new BlockPosition(x, y + 1, z), BuiltinBlocks.CHEST);
                server.chestManager().getOrCreate(new BlockPosition(x, y + 1, z))
                        .setSlot(3, ItemStack.of(
                                net.zaminmc.torch.server.item.BuiltinItems.IRON_INGOT, 7));
                server.world().setBlock(new BlockPosition(x, y, z),
                        HopperBlocks.hopperOf(0, true));
            });
            await(() -> hopperSlot(server, x, y, z, 0).count() == 1,
                    "the hopper pulled one item from the chest above");
            await(() -> hopperSlot(server, x, y, z, 0).count() == 2,
                    "the pull continued on the cadence");
            assertEquals(5, chestSlot(server, x, y + 1, z, 3).count(),
                    "the chest released exactly what landed");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theFurnaceSidedSlotsRouteThePush() throws Exception {
        EngineServer server = boot(EngineConfig.WHOLE_WORLD_REGION_SIZE);
        try {
            int x = 50, z = 50, y = 40;
            server.ticker().submit(() -> {
                // The furnace above the hopper's east side: the hopper
                // faces east into the furnace's WEST face — the SIDED slot
                // array for a side face is the fuel slot.
                server.world().setBlock(new BlockPosition(x + 1, y, z), BuiltinBlocks.FURNACE);
                server.world().setBlock(new BlockPosition(x, y, z),
                        HopperBlocks.hopperOf(5, true)); // EAST
                server.hopper().getOrCreate(new BlockPosition(x, y, z))
                        .setItem(0, ItemStack.of(
                                net.zaminmc.torch.server.item.BuiltinItems.COAL, 3));
            });
            await(() -> furnaceFuel(server, x + 1, y, z).count() == 1,
                    "the side push landed in the fuel slot");
            await(() -> furnaceFuel(server, x + 1, y, z).count() == 2,
                    "the fuel cadence continued");
            assertEquals(1, hopperSlot(server, x, y, z, 0).count());
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void thePoweredHopperStopsTransferring() throws Exception {
        EngineServer server = boot(EngineConfig.WHOLE_WORLD_REGION_SIZE);
        try {
            int x = 70, z = 70, y = 40;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y, z), BuiltinBlocks.CHEST);
                server.world().setBlock(new BlockPosition(x, y + 1, z),
                        HopperBlocks.hopperOf(0, true));
                server.hopper().getOrCreate(new BlockPosition(x, y + 1, z))
                        .setItem(0, ItemStack.of(
                                net.zaminmc.torch.server.item.BuiltinItems.DIAMOND, 5));
                // The lever's stone mounts on the hopper's side: a powered
                // lever beside the hopper disables it (the ENABLED pair
                // flips through the neighbor bus).
                server.world().setBlock(new BlockPosition(x + 1, y + 1, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x + 2, y + 1, z),
                        RedstoneBlocks.leverOf(1, false));
            });
            // Power ON: the pair flips to DISABLED.
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(x + 2, y + 1, z)));
            await(() -> typeAt(server, x, y + 1, z).identifier().value()
                            .equals("hopper_down_disabled"),
                    "the powered hopper flipped to its disabled pair");
            // The disabled hopper never transfers: the chest stays empty.
            sleep(600);
            assertEquals(true, chestSlot(server, x, y, z, 0).isEmpty(),
                    "the disabled hopper pushed nothing");
            // Power OFF: the pair re-enables and the transfer resumes.
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(x + 2, y + 1, z)));
            await(() -> typeAt(server, x, y + 1, z).identifier().value()
                            .equals("hopper_down"),
                    "the unpowered hopper re-enabled");
            await(() -> chestSlot(server, x, y, z, 0).count() == 1,
                    "the re-enabled hopper resumed the transfer");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theHopperSucksTheItemEntityAboveIt() throws Exception {
        EngineServer server = boot(EngineConfig.WHOLE_WORLD_REGION_SIZE);
        try {
            int x = 90, z = 90, y = 40;
            server.ticker().submit(() -> {
                // No container above: an item entity in the box above the
                // hopper gets sucked in.
                server.world().setBlock(new BlockPosition(x, y, z),
                        HopperBlocks.hopperOf(0, true));
            });
            await(() -> HopperBlocks.isHopper(typeAt(server, x, y, z)), "the hopper placed");
            // The drop spawns at the block above's corner (the engine's
            // spawnDropAtBlock adds its own center offset): the item lands
            // in the suck box above the hopper.
            server.ticker().submit(() -> server.itemEntities().spawnDropAtBlock(
                    new net.zaminmc.torch.util.Position(x, y + 1, z),
                    ItemStack.of(net.zaminmc.torch.server.item.BuiltinItems.REDSTONE, 4),
                    net.zaminmc.torch.server.entity.ItemEntity.PICKUP_DELAY_DROP_TICKS));
            await(() -> hopperSlot(server, x, y, z, 0).count() == 4,
                    "the hopper sucked the whole item stack");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theTransferCrossesTheRegionSeamVanillaExact() throws Exception {
        EngineServer server = boot(1); // one chunk per region
        try {
            // The hopper at x=15 (region 0,0) facing east, the chest at
            // x=16 (region 1,0): the push crosses the seam, same tick,
            // vanilla-exact — the §11 "hoppers transferring items between
            // containers with different owners" row.
            int x = 15, z = 10, y = 40;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x + 1, y, z), BuiltinBlocks.CHEST);
                server.world().setBlock(new BlockPosition(x, y, z),
                        HopperBlocks.hopperOf(5, true)); // EAST
                server.hopper().getOrCreate(new BlockPosition(x, y, z))
                        .setItem(0, ItemStack.of(
                                net.zaminmc.torch.server.item.BuiltinItems.GOLD_INGOT, 6));
            });
            await(() -> chestSlot(server, x + 1, y, z, 0).count() == 1,
                    "the cross-seam push landed in the chest");
            await(() -> chestSlot(server, x + 1, y, z, 0).count() == 2,
                    "the cross-seam cadence continued");
            assertEquals(4, hopperSlot(server, x, y, z, 0).count(),
                    "the hopper kept the rest");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theSurvivalBreakSpillsTheHoppersSlots() throws Exception {
        EngineServer server = boot(EngineConfig.WHOLE_WORLD_REGION_SIZE);
        try {
            int x = 110, z = 110, y = 40;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y, z),
                        HopperBlocks.hopperOf(0, true));
                server.hopper().getOrCreate(new BlockPosition(x, y, z))
                        .setItem(2, ItemStack.of(
                                net.zaminmc.torch.server.item.BuiltinItems.DIAMOND, 2));
            });
            await(() -> HopperBlocks.isHopper(typeAt(server, x, y, z)), "the hopper placed");
            // The engine-side break hook (the survival path's spill).
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y, z), server.world().airType());
                server.hopper().onBlockBroken(new BlockPosition(x, y, z));
            });
            await(() -> itemDropNear(server, x, y, z), "the spill dropped the slots");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // Harness
    // ------------------------------------------------------------------

    private static BlockType typeAt(EngineServer server, int x, int y, int z) {
        AtomicReference<BlockType> result = new AtomicReference<>();
        server.ticker().submit(() -> result.set(
                server.world().getBlock(new BlockPosition(x, y, z))));
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline && result.get() == null) {
            sleep(10);
        }
        return result.get();
    }

    private static ItemStack chestSlot(EngineServer server, int x, int y, int z, int slot) {
        long deadline = System.currentTimeMillis() + 2_000;
        while (System.currentTimeMillis() < deadline) {
            AtomicReference<ItemStack> result = new AtomicReference<>();
            server.ticker().submit(() -> {
                net.zaminmc.torch.server.chest.ChestBlockEntity chest =
                        server.chestManager().peek(new BlockPosition(x, y, z));
                if (chest != null) {
                    result.set(chest.snapshotSlots()[slot]);
                } else {
                    result.set(ItemStack.EMPTY);
                }
            });
            ItemStack seen = awaitRef(result);
            if (!seen.isEmpty()) {
                return seen;
            }
            sleep(50);
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack hopperSlot(EngineServer server, int x, int y, int z, int slot) {
        long deadline = System.currentTimeMillis() + 2_000;
        while (System.currentTimeMillis() < deadline) {
            AtomicReference<ItemStack> result = new AtomicReference<>();
            server.ticker().submit(() -> {
                HopperBlockEntity hopper = server.hopper().peek(new BlockPosition(x, y, z));
                result.set(hopper == null ? ItemStack.EMPTY : hopper.getItem(slot));
            });
            ItemStack seen = awaitRef(result);
            if (!seen.isEmpty()) {
                return seen;
            }
            sleep(50);
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack furnaceFuel(EngineServer server, int x, int y, int z) {
        long deadline = System.currentTimeMillis() + 2_000;
        while (System.currentTimeMillis() < deadline) {
            AtomicReference<ItemStack> result = new AtomicReference<>();
            server.ticker().submit(() -> {
                net.zaminmc.torch.server.furnace.FurnaceBlockEntity furnace =
                        server.furnaceManager().peek(new BlockPosition(x, y, z));
                result.set(furnace == null ? ItemStack.EMPTY : furnace.fuel());
            });
            ItemStack seen = awaitRef(result);
            if (!seen.isEmpty()) {
                return seen;
            }
            sleep(50);
        }
        return ItemStack.EMPTY;
    }

    /** Waits for the reference to fill, then returns it (never null). */
    private static ItemStack awaitRef(AtomicReference<ItemStack> ref) {
        long deadline = System.currentTimeMillis() + 1_000;
        while (System.currentTimeMillis() < deadline) {
            ItemStack seen = ref.get();
            if (seen != null) {
                return seen;
            }
            sleep(10);
        }
        return ItemStack.EMPTY;
    }

    private static boolean itemDropNear(EngineServer server, int x, int y, int z) {
        AtomicReference<Boolean> found = new AtomicReference<>(false);
        server.ticker().submit(() -> {
            for (net.zaminmc.torch.server.entity.ItemEntity item : server.itemEntities().all()) {
                net.zaminmc.torch.util.Position at = item.position();
                if (Math.abs(at.x() - (x + 0.5)) <= 2.0 && Math.abs(at.y() - y) <= 2.0
                        && Math.abs(at.z() - (z + 0.5)) <= 2.0
                        && item.stack().type().identifier().value().equals("diamond")) {
                    found.set(true);
                }
            }
        });
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline && !found.get()) {
            sleep(50);
        }
        return found.get();
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
                "hpt" + Math.abs(UUID.randomUUID().hashCode() % 100000),
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
