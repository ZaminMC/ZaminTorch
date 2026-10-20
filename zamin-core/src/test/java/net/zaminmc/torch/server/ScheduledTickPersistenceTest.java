package net.zaminmc.torch.server;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.block.FireBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.util.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;


import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The scheduled block-tick surface's own persistence (the in-memory
 * envelope retired): the pending rows — a fire's first tick and a sand
 * fuse, both armed on the scheduled surface — survive a restart through
 * the ZST store, and fire exactly as the reference's own chunk-save rows
 * do (the same load arm the anvil import uses).
 */
class ScheduledTickPersistenceTest {

    @TempDir
    Path dataDir;

    private EngineServer boot() throws InterruptedException {
        // A FIXED world name: both boots address the same world's storage
        // (the deltas and the ZST rows live under the name).
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "zst-restart", "it", 20, 2, 20, dataDir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        return server;
    }

    private void await(BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 8_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    @Test
    void thePendingRowsSurviveTheRestart() throws Exception {
        // Boot one: a fire on stone (the placement listener arms 30-39
        // out) and a dragon egg over air (the wake arms the five-tick fuse
        // on the next fan tick — the egg's own band, wider than the sand
        // family's two, keeps the save clear of the fuse's own firing).
        // Save, shut down.
        BlockPosition floor = new BlockPosition(21, 40, 21);
        BlockPosition flame = floor.offset(0, 1, 0);
        // The egg two cells above the stone floor (air 44-45 cleared): the
        // fuse fires tryFall into the open gap.
        BlockPosition eggAt = new BlockPosition(23, 46, 21);
        var egg = net.zaminmc.torch.server.block.DragonEggBlocks.dragonEgg();
        java.util.List<net.zaminmc.torch.server.block.ScheduledTickDataStore.TickRow> saved = null;
        {
            EngineServer first = boot();
            try {
                AtomicReference<Boolean> placed = new AtomicReference<>();
                first.ticker().submit(() -> {
                    first.world().setBlock(floor, BuiltinBlocks.STONE);
                    first.world().setBlock(new BlockPosition(23, 43, 21), BuiltinBlocks.STONE);
                    first.world().setBlock(new BlockPosition(23, 44, 21), first.world().airType());
                    first.world().setBlock(new BlockPosition(23, 45, 21), first.world().airType());
                    first.world().setBlock(eggAt, egg);
                    first.world().setBlock(flame, BuiltinBlocks.FIRE);
                    placed.set(true);
                });
                await(first, () -> placed.get() != null, "the scene placed");

                // Both rows ride the scheduled surface before the save.
                await(first, () -> first.scheduledBlockTicks().pendingAt(
                                flame, Identifier.parse("minecraft:fire")) != null,
                        "the fire's first tick armed");
                await(first, () -> first.scheduledBlockTicks().pendingAt(
                                eggAt, Identifier.parse("minecraft:dragon_egg")) != null,
                        "the egg's fuse armed");
                first.saveAllNow();
                // The saved rows carry both families (the race gate: if the
                // fuse ever fired before the snapshot, this names it).
                saved = new net.zaminmc.torch.server.block.ScheduledTickDataStore(
                        dataDir.resolve("zst-restart/data/scheduledticks.bin")).load();
                boolean fireRow = saved.stream().anyMatch(
                        row -> row.blockId().equals("minecraft:fire") && row.x() == flame.x());
                boolean eggRow = saved.stream().anyMatch(
                        row -> row.blockId().equals("minecraft:dragon_egg") && row.x() == eggAt.x());
                assertTrue(fireRow, "the fire's row saved ("
                        + saved.stream().map(net.zaminmc.torch.server.block.ScheduledTickDataStore.TickRow::blockId).toList() + ")");
                assertTrue(eggRow, "the egg's row saved");
            } finally {
                first.shutdown(null);
            }
        }

        // Boot two: the rows ride back in through the same load arm the
        // anvil import uses — the egg converts on its restored fuse, the
        // fire ages on its restored first tick.
        EngineServer second = boot();
        try {
            await(second, () -> second.world().getBlock(eggAt)
                            .identifier().equals(second.world().airType().identifier()),
                    "the egg converted on the restored fuse");
            await(second, () -> {
                var at = second.world().getBlock(flame).identifier();
                if (!FireBlocks.isFire(at)) {
                    return false;
                }
                int age = FireBlocks.ageOf(at);
                if (age != 0 && age != 1) {
                    return false;
                }
                // The restored first tick fired and re-armed: the CURRENT
                // state's family carries a pending row again.
                return second.scheduledBlockTicks().pendingAt(
                        flame, second.world().getBlock(flame).identifier()) != null;
            }, "the fire aged on its restored first tick and re-armed");
            assertNotNull(second.scheduledBlockTicks(),
                    "the surface stands on the second boot");
            assertTrue(second.world().getBlock(new BlockPosition(23, 43, 21))
                            .identifier().value().equals("stone"),
                    "the egg's floor survived the restart");
        } finally {
            second.shutdown(null);
        }
    }

    private void await(EngineServer server, BooleanSupplier condition, String description)
            throws InterruptedException {
        await(condition, description);
    }
}
