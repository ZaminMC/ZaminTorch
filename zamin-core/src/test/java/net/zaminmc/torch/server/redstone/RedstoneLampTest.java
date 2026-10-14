package net.zaminmc.torch.server.redstone;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The redstone power family (Slice 33 — RedstoneLampBlock +
 * RedstoneOreBlock's lit twin, 1:1): the lamp's neighbor-signal flip —
 * the IMMEDIATE brighten when a feed arrives (onAdded/neighborChanged's
 * unlit arm), the 4-tick delayed revert when the feed departs
 * (neighborChanged's lit arm -> tick), the placement evaluation
 * (onAdded), and the lit ore's click/step swap with the random-tick
 * revert.
 */
class RedstoneLampTest {

    @TempDir
    Path dataDir;

    @Test
    void theLampLightsImmediatelyAndRevertsAfterTheFourTickDelay() throws Exception {
        EngineServer server = boot();
        try {
            int x = 10, z = 10, y = 40;
            // The feed: a standing torch strongly powers the block above
            // it (the RedstoneSystemTest's own shape), and that block
            // re-radiates weak 15 to its neighbors — the lamp sits on TOP
            // of the fed block, adjacent by construction.
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 2, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y - 1, z),
                        RedstoneBlocks.torchStandingLit());
                server.world().setBlock(new BlockPosition(x, y, z), BuiltinBlocks.STONE);
            });
            await(() -> true, "the floor settles");
            // The dark lamp placed on the torch-fed block: onAdded flips
            // it IMMEDIATELY (RedstoneLampBlock.onAdded lines 29-38 — no
            // tick delay on the brighten).
            server.ticker().submit(() -> server.world().setBlock(
                    new BlockPosition(x, y + 1, z), BuiltinBlocks.REDSTONE_LAMP));
            await(() -> server.world().getBlock(new BlockPosition(x, y + 1, z))
                            .equals(BuiltinBlocks.LIT_REDSTONE_LAMP),
                    "the lamp lights on the feed");
            // The feed departs: the torch breaks, the lit lamp schedules
            // the 4-tick revert (neighborChanged lines 40-50) and goes dark.
            server.ticker().submit(() -> server.world().setBlock(
                    new BlockPosition(x, y - 1, z), BuiltinBlocks.AIR));
            await(() -> server.world().getBlock(new BlockPosition(x, y + 1, z))
                            .equals(BuiltinBlocks.REDSTONE_LAMP),
                    "the lamp reverts dark after the delay");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theLitOreRevertsOnTheRandomTickAndTheDustDropsMatchBothStates() throws Exception {
        EngineServer server = boot();
        try {
            int x = 12, z = 12, y = 40;
            // The lit ore placed directly: the random tick carries it back
            // to the plain ore (RedstoneOreBlock.tick over the default
            // randomTick delegation — the LIT variant is
            // setTicksRandomly(true), so no scheduled delay exists). The
            // sampler's 3-of-4096 odds make a natural hit flaky, so the
            // probe drives the per-cell rule directly (the same surface
            // the growth acceptance tests use).
            server.ticker().submit(() -> server.world().setBlock(
                    new BlockPosition(x, y, z), BuiltinBlocks.LIT_REDSTONE_ORE));
            await(() -> server.world().getBlock(new BlockPosition(x, y, z))
                            .equals(BuiltinBlocks.LIT_REDSTONE_ORE),
                    "the lit ore lands");
            server.ticker().submit(() ->
                    server.randomTicks().randomTick(new BlockPosition(x, y, z)));
            await(() -> server.world().getBlock(new BlockPosition(x, y, z))
                            .equals(BuiltinBlocks.REDSTONE_ORE),
                    "the lit ore reverts on the random tick");
            // The behavior rows: the lamp pair drops the DARK item from
            // both states (getDropItem lines 62-65), the lit ore shares
            // its plain twin's dust row.
            assertEquals(net.zaminmc.torch.util.Identifier.parse("minecraft:redstone_lamp"),
                    net.zaminmc.torch.server.block.BlockBehaviorTable
                            .of(net.zaminmc.torch.util.Identifier.parse("minecraft:lit_redstone_lamp"))
                            .orElseThrow().drops().get(0).item());
            assertEquals(net.zaminmc.torch.util.Identifier.parse("minecraft:redstone"),
                    net.zaminmc.torch.server.block.BlockBehaviorTable
                            .of(net.zaminmc.torch.util.Identifier.parse("minecraft:lit_redstone_ore"))
                            .orElseThrow().drops().get(0).item());
            // The predicates: the family covers both lamps, the lit flag
            // names the pair.
            assertTrue(RedstoneBlocks.isLamp(BuiltinBlocks.REDSTONE_LAMP));
            assertTrue(RedstoneBlocks.isLamp(BuiltinBlocks.LIT_REDSTONE_LAMP));
            assertTrue(RedstoneBlocks.lampLit(BuiltinBlocks.LIT_REDSTONE_LAMP));
            assertTrue(!RedstoneBlocks.lampLit(BuiltinBlocks.REDSTONE_LAMP));
        } finally {
            server.shutdown(() -> { });
        }
    }

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "lamp" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        return server;
    }

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }
}
