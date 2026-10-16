package net.zaminmc.torch.server.commandblock;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The command-block block's own law (Slice 72 — CommandBlock +
 * CommandBlockBlockEntity 1:1, block 137): the areCommandBlocksEnabled
 * gate (DedicatedServer line 378's property), the executor's rows (the
 * edit resets the success count, the untracked executor clears its last
 * output), the redstone edge walk (the rising edge flips TRIGGERED and
 * schedules the rate-1 tick — the run lands on the scheduled tick, not
 * the edge; the falling edge clears the bit), and the block executor's
 * dispatch over the real CommandService.
 */
class CommandBlockBlockTest {

    @TempDir
    Path dataDir;

    private EngineServer boot(boolean enableCommandBlocks) throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "cb" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString(),
                net.zaminmc.torch.GameMode.CREATIVE, true, "flat", false,
                EngineConfig.WHOLE_WORLD_REGION_SIZE, enableCommandBlocks);
        EngineServer server = new EngineServer(config);
        server.start();
        return server;
    }

    private void await(BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    @Test
    void theConfigGateCarriesTheVanillaDefault() {
        // DedicatedServer line 378: getBoolean("enable-command-block", false).
        assertFalse(EngineConfig.defaults().enableCommandBlocks(),
                "the vanilla dedicated default is OFF");
        var properties = new java.util.Properties();
        properties.setProperty("enable-command-block", "true");
        assertTrue(net.zaminmc.torch.server.config.ConfigLoader
                        .fromProperties(properties).enableCommandBlocks(),
                "the property flips the gate");
    }

    @Test
    void theExecutorRowsCarryTheReferenceWalk() {
        AtomicInteger runs = new AtomicInteger();
        CommandBlockManager manager = new CommandBlockManager((executor, command) -> {
            runs.incrementAndGet();
            executor.recordOutput("ran: " + command);
            return 1;
        });
        BlockPosition at = new BlockPosition(10, 40, 10);
        CommandBlockManager.CommandState executor = manager.createAt(at, true);
        assertEquals("", executor.command(), "a fresh executor carries no command");
        assertTrue(executor.trackOutput(), "the placement default rides the gamerule");
        assertEquals(0, manager.analogSignal(at), "no successes yet");

        // The edit (setCommand line 107): the command lands and the
        // success count resets.
        executor.setCommand("say hi");
        executor.setSuccessCount(5);
        manager.edit(at, "say bye", true);
        assertEquals("say bye", executor.command(), "the edit's command lands");
        assertEquals(0, executor.successCount(), "the edit resets the count");

        // The untracked executor clears its last output.
        executor.recordOutput("an old output");
        manager.edit(at, "say bye", false);
        assertFalse(executor.trackOutput(), "the track flag reads");
        assertNull(executor.lastOutput(), "the untracked executor cleared its output");

        // The run: the executor walks the host, the count lands, the
        // analog signal carries it.
        manager.edit(at, "say once", true);
        assertEquals(1, manager.run(at), "the run answers the host's count");
        assertEquals(1, runs.get(), "the host ran once");
        assertEquals(1, executor.successCount(), "the run's count lands");
        assertEquals(1, manager.analogSignal(at), "the analog signal is the count");
        assertEquals("ran: say once", executor.lastOutput(),
                "the feedback landed in the last output");

        // No tile: the silent zeros.
        assertEquals(0, manager.run(new BlockPosition(0, 1, 0)));
        assertEquals(0, manager.analogSignal(new BlockPosition(0, 1, 0)));
    }

    @Test
    void theRedstoneEdgeWalkRunsTheBlockOnTheScheduledTick() throws Exception {
        EngineServer server = boot(true);
        try {
            int x = 20, y = 40, z = 20;
            AtomicReference<net.zaminmc.torch.server.commandblock.CommandBlockManager.CommandState>
                    executor = new AtomicReference<>();
            server.ticker().submit(() -> {
                // The floor, the command block on it, and the floor lever
                // beside (the RedstoneSourcesTest topology: the lever on
                // its own stone, toggled through the engine's own use arm
                // — the redstone edge walk's own door).
                server.world().setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z), BuiltinBlocks.COMMAND_BLOCK);
                server.world().setBlock(new BlockPosition(x - 1, y - 1, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x - 1, y, z),
                        net.zaminmc.torch.server.redstone.RedstoneBlocks.leverOf(5, false));
            });
            await(() -> server.world().getBlock(new BlockPosition(x, y, z))
                    .equals(BuiltinBlocks.COMMAND_BLOCK), "the command block placed");
            // The tile + the command BEFORE the edge (the placement arm
            // creates the tile in the reference's onPlaced; the createAt
            // consult rides the placement wave — the test lands the same
            // state the placement will).
            server.ticker().submit(() -> {
                var manager = server.commandBlocks();
                if (manager != null) {
                    CommandBlockManager.CommandState state =
                            manager.createAt(new BlockPosition(x, y, z), true);
                    state.setCommand("say triggered");
                    executor.set(state);
                }
            });
            await(() -> executor.get() != null, "the tile carries the command");
            // The toggle (the lever's own use arm): the rising edge.
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(x - 1, y, z)));
            await(() -> BuiltinBlocks.COMMAND_BLOCK_TRIGGERED.equals(
                            server.world().getBlock(new BlockPosition(x, y, z))),
                    "the rising edge flipped the TRIGGERED bit");
            // The scheduled tick (rate 1) runs the executor: the success
            // count lands through the real CommandService dispatch.
            await(() -> executor.get().successCount() > 0,
                    "the scheduled tick ran the executor (the success count landed)");
            assertEquals(1, executor.get().successCount(),
                    "the known command answered one success");
            // /say broadcasts to the players, not the source — the
            // executor's last output stays null exactly like the
            // reference (the recordOutput walk itself is pinned by the
            // manager's unit test through the recording host).
            // The falling edge clears the bit (the lever toggled off).
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(x - 1, y, z)));
            await(() -> BuiltinBlocks.COMMAND_BLOCK.equals(
                            server.world().getBlock(new BlockPosition(x, y, z))),
                    "the falling edge cleared the TRIGGERED bit");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theGateRefusesTheEngineVerbNowhereButTheWireGateAnswers() throws Exception {
        EngineServer server = boot(false);
        try {
            assertFalse(server.areCommandBlocksEnabled(),
                    "the off config answers off");
        } finally {
            server.shutdown(() -> { });
        }
        EngineServer on = boot(true);
        try {
            assertTrue(on.areCommandBlocksEnabled(), "the on config answers on");
        } finally {
            on.shutdown(() -> { });
        }
    }
}
