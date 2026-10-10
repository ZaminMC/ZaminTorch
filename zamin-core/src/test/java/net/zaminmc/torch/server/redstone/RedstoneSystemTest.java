package net.zaminmc.torch.server.redstone;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The redstone core on the live engine (Slice 9a): the signal model (weak
 * re-radiation, the torch's strong up-arm through the block above it), the
 * wire's instant same-tick cascade with the exact decay, the torch's
 * two-tick reaction, and the repeater's delay — all pinned against the
 * reference's numbers (reference/1.8.8 RedstoneWireBlock /
 * RedstoneTorchBlock / DiodeBlock).
 *
 * <p>Every circuit uses the reference's own topologies: a torch strongly
 * powers the block ABOVE it, and that block re-radiates to the dust on top
 * of it (dust never sits on a torch — the wire's canBePlaced demands a
 * solid floor, reference RedstoneWireBlock lines 92-94).</p>
 */
class RedstoneSystemTest {

    @TempDir
    Path dataDir;

    @Test
    void theTorchUnderABlockPowersTheDustOnTopOfIt() throws Exception {
        EngineServer server = boot();
        try {
            // The reference's own arm: the standing torch's STRONG emission
            // feeds the block above it (RedstoneTorchBlock lines 134-136,
            // the dir==DOWN read), and that block re-radiates weak 15 to
            // the wire sitting on it (World.getSignal's isSolid branch).
            int x = 10, z = 10, y = 40;
            server.ticker().submit(() -> {
                // floor -> torch -> block -> dust
                server.world().setBlock(new BlockPosition(x, y - 2, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y - 1, z),
                        RedstoneBlocks.torchStandingLit());
                server.world().setBlock(new BlockPosition(x, y, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y + 1, z),
                        RedstoneBlocks.wireOfPower(0));
            });
            await(() -> wirePower(server, x, y + 1, z) == 15,
                    "the dust on the torch-fed block reads 15");
            assertEquals(15, wirePower(server, x, y + 1, z));
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theWireLineDecaysOnePerBlockAndDiesAtSixteen() throws Exception {
        EngineServer server = boot();
        try {
            // The torch-fed block at (10,40,10), then a north line of dust
            // on stone: the first cell reads 15 (the re-radiation), each
            // next one decays one, the sixteenth cell reads 0.
            int x = 10, z = 10, y = 40;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 2, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y - 1, z),
                        RedstoneBlocks.torchStandingLit());
                server.world().setBlock(new BlockPosition(x, y, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y + 1, z),
                        RedstoneBlocks.wireOfPower(0));
                for (int i = 1; i <= 18; i++) {
                    server.world().setBlock(new BlockPosition(x, y, z - i), BuiltinBlocks.STONE);
                    server.world().setBlock(new BlockPosition(x, y + 1, z - i),
                            RedstoneBlocks.wireOfPower(0));
                }
            });
            await(() -> wirePower(server, x, y + 1, z) == 15, "the first cell reads 15");
            assertEquals(15 - 1, wirePower(server, x, y + 1, z - 1));
            assertEquals(15 - 5, wirePower(server, x, y + 1, z - 5));
            assertEquals(15 - 15, wirePower(server, x, y + 1, z - 15));
            assertEquals(0, wirePower(server, x, y + 1, z - 16));
            assertEquals(0, wirePower(server, x, y + 1, z - 17));
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theClassicNotGateTurnsTheTorchOffThroughTheBlock() throws Exception {
        EngineServer server = boot();
        try {
            // The inverter: a source torch under block B, the wall torch
            // hanging on B's north face. The source's strong up-arm powers
            // B, B re-radiates, the wall torch reads its ATTACHMENT (B) and
            // goes dark after the 2-tick reaction (RedstoneTorchBlock lines
            // 88-98).
            int x = 20, z = 20, y = 40;
            server.ticker().submit(() -> {
                // B's column
                server.world().setBlock(new BlockPosition(x, y - 2, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y - 1, z),
                        RedstoneBlocks.torchStandingLit());
                server.world().setBlock(new BlockPosition(x, y, z), BuiltinBlocks.STONE);
                // The wall torch on B's north face points NORTH (FACING id
                // 2): its attachment is B at its south side.
                server.world().setBlock(new BlockPosition(x, y, z - 1),
                        RedstoneBlocks.torchOfFacing(2, true));
            });
            await(() -> typeAt(server, x, y, z - 1).identifier().value()
                            .startsWith("unlit_redstone_torch"),
                    "the inverter's wall torch went dark reading its powered attachment");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theTorchRelightsWhenTheSourceCut() throws Exception {
        EngineServer server = boot();
        try {
            int x = 21, z = 21, y = 40;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 2, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y - 1, z),
                        RedstoneBlocks.torchStandingLit());
                server.world().setBlock(new BlockPosition(x, y, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z - 1),
                        RedstoneBlocks.torchOfFacing(2, true));
            });
            await(() -> typeAt(server, x, y, z - 1).identifier().value()
                            .startsWith("unlit_redstone_torch"),
                    "the wall torch went dark");
            // Cut the source: the torch relights after its reaction.
            server.ticker().submit(() ->
                    server.world().setBlock(new BlockPosition(x, y - 1, z),
                            server.world().airType()));
            await(() -> typeAt(server, x, y, z - 1).identifier().value()
                            .startsWith("redstone_torch"),
                    "the wall torch relit after the source cut");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theRepeaterDelaysAndCutsWithItsDelayProperty() throws Exception {
        EngineServer server = boot();
        try {
            // The line runs north(-Z): the source torch under a stone at
            // (30,40,32), the dust at (30,41,31), the repeater at (30,41,30)
            // with FACING=south (its INPUT at +Z = the dust's cell).
            int x = 30, z = 32, y = 40;
            server.ticker().submit(() -> {
                // the source column at z=32
                server.world().setBlock(new BlockPosition(x, y - 2, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y - 1, z),
                        RedstoneBlocks.torchStandingLit());
                server.world().setBlock(new BlockPosition(x, y, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y + 1, z),
                        RedstoneBlocks.wireOfPower(0));
                // the dust steps north to the repeater's input cell
                server.world().setBlock(new BlockPosition(x, y, z - 1), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y + 1, z - 1),
                        RedstoneBlocks.wireOfPower(0));
                // the repeater on its own stone, FACING south (input = +Z)
                server.world().setBlock(new BlockPosition(x, y, z - 2), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y + 1, z - 2),
                        RedstoneBlocks.repeaterOf(RedstoneBlocks.FACING_SOUTH, 1, false));
            });
            await(() -> wirePower(server, x, y + 1, z - 1) > 0, "the input dust powered");
            // The repeater arms its 2-tick reaction (delay 1 = 2 game
            // ticks, RepeaterBlock.getDelay = DELAY * 2) and swaps to the
            // powered pair.
            await(() -> RedstoneBlocks.repeaterPowered(typeAt(server, x, y + 1, z - 2)),
                    "the repeater turned on after its 2-tick delay");

            // Cut the source: the repeater turns back off after its delay.
            server.ticker().submit(() ->
                    server.world().setBlock(new BlockPosition(x, y - 1, z),
                            server.world().airType()));
            await(() -> !RedstoneBlocks.repeaterPowered(typeAt(server, x, y + 1, z - 2)),
                    "the repeater turned off after the source cut");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // Harness
    // ------------------------------------------------------------------

    private static int wirePower(EngineServer server, int x, int y, int z) {
        BlockType type = typeAt(server, x, y, z);
        return RedstoneBlocks.isWire(type) ? RedstoneBlocks.wirePower(type) : -1;
    }

    /**
     * Reads the block type through the tick thread (the submit queue's
     * synchronization gives the happens-before edge the plain chunk arrays
     * don't — a test-thread read races the cascade's writes).
     */
    private static BlockType typeAt(EngineServer server, int x, int y, int z) {
        java.util.concurrent.atomic.AtomicReference<BlockType> result =
                new java.util.concurrent.atomic.AtomicReference<>();
        server.ticker().submit(() -> result.set(
                server.world().getBlock(new BlockPosition(x, y, z))));
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline && result.get() == null) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return result.get();
    }

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "rs" + Math.abs(UUID.randomUUID().hashCode() % 100000),
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
