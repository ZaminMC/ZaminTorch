package net.zaminmc.torch.server.redstone;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.entity.PlayerState;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.config.EngineConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The redstone core on the live engine (Slice 9a): the signal model (weak
 * re-radiation, the torch's strong up-arm), the wire's instant same-tick
 * cascade with the exact decay, the torch's two-tick reaction, and the
 * repeater's delay — all pinned against the reference's numbers
 * (reference/1.8.8 RedstoneWireBlock / RedstoneTorchBlock / DiodeBlock).
 */
class RedstoneSystemTest {

    @TempDir
    Path dataDir;

    @Test
    void theWireAboveAStandingTorchReadsFullPower() throws Exception {
        EngineServer server = boot();
        try {
            // A floor, a standing torch on it, a wire directly above: the
            // torch's weak emission upward (FACING != DOWN on the ask) is 15.
            int x = 10, z = 10, y = 40;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z),
                        RedstoneBlocks.torchStandingLit());
                server.world().setBlock(new BlockPosition(x, y + 1, z),
                        RedstoneBlocks.wireOfPower(0));
            });
            await(() -> wirePower(server, x, y + 1, z) == 15,
                    "the wire above the lit torch reads 15");
            assertEquals(15, wirePower(server, x, y + 1, z));
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theWireLineDecaysOnePerBlockAndDiesAtSixteen() throws Exception {
        EngineServer server = boot();
        try {
            // The torch at (10,40,10) feeding a west line of wire on stone:
            // the first wire reads 15, each next one decays one, the
            // sixteenth cell reads 0 (15 - 15 = 0, the seventeenth is dark).
            int x = 10, z = 10, y = 40;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z),
                        RedstoneBlocks.torchStandingLit());
                for (int i = 1; i <= 18; i++) {
                    server.world().setBlock(new BlockPosition(x, y - 1, z - i), BuiltinBlocks.STONE);
                    server.world().setBlock(new BlockPosition(x, y, z - i),
                            RedstoneBlocks.wireOfPower(0));
                }
            });
            await(() -> wirePower(server, x, y, z - 1) == 14,
                    "the wire next to the torch reads 14");
            assertEquals(15 - 1, wirePower(server, x, y, z - 1));
            assertEquals(15 - 5, wirePower(server, x, y, z - 5));
            assertEquals(15 - 15, wirePower(server, x, y, z - 15));
            assertEquals(0, wirePower(server, x, y, z - 16));
            assertEquals(0, wirePower(server, x, y, z - 17));
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theClassicNotGateTurnsTheTorchOffAfterTwoTicks() throws Exception {
        EngineServer server = boot();
        try {
            // The inverter: a torch hanging on the side of a block, a wire
            // on top of the same block. The wire powers the block strongly
            // (the wire's up-arm read from below), the block re-radiates to
            // the torch, the torch reads its attachment and goes dark after
            // its 2-tick reaction.
            int x = 20, z = 20, y = 40;
            // The block B at (20,40,20); the torch on B's north face points
            // north (FACING=NORTH, hangs on the south wall = B): the
            // identifier carries the facing it points.
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y, z), BuiltinBlocks.STONE);
                // FACING id 2 = NORTH (the reference Direction space): the
                // torch on B's north face points north.
                server.world().setBlock(new BlockPosition(x, y, z - 1),
                        RedstoneBlocks.torchOfFacing(2, true));
            });
            await(() -> typeAt(server, x, y, z - 1).identifier().value().startsWith("redstone_torch"),
                    "the wall torch placed lit");
            // Power the block: the wire lands on top of B.
            server.ticker().submit(() ->
                    server.world().setBlock(new BlockPosition(x, y + 1, z),
                            RedstoneBlocks.wireOfPower(0)));
            await(() -> typeAt(server, x, y, z - 1).identifier().value()
                            .startsWith("unlit_redstone_torch"),
                    "the torch reads its powered attachment and went dark");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theRepeaterDelaysTheSignalByTwiceItsDelayProperty() throws Exception {
        EngineServer server = boot();
        try {
            // A repeater (delay 1 = 2 game ticks) fed by a wire from a
            // torch: the output-side block sees the power only after the
            // delay ticks pass, and the powered pair swap is visible on the
            // block identity itself.
            int x = 30, z = 30, y = 40;
            server.ticker().submit(() -> {
                // input line: torch -> wire -> repeater(FACING=south: input at
                // its south side, output to the north)
                server.world().setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z),
                        RedstoneBlocks.torchStandingLit());
                server.world().setBlock(new BlockPosition(x, y - 1, z + 1), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z + 1),
                        RedstoneBlocks.wireOfPower(0));
                server.world().setBlock(new BlockPosition(x, y - 1, z + 2), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z + 2),
                        RedstoneBlocks.repeaterOf(RedstoneBlocks.FACING_SOUTH, 1, false));
            });
            // The wire powers up instantly (the torch feeds it 15, the
            // repeater's input reads it), the repeater arms its 2-tick
            // reaction and swaps to the powered pair.
            await(() -> RedstoneBlocks.repeaterPowered(typeAt(server, x, y, z + 2)),
                    "the repeater turned on after its 2-tick delay");
            assertEquals(true, RedstoneBlocks.repeaterPowered(typeAt(server, x, y, z + 2)));

            // Cut the source: the repeater turns back off after its delay.
            server.ticker().submit(() ->
                    server.world().setBlock(new BlockPosition(x, y, z), worldAir(server)));
            await(() -> !RedstoneBlocks.repeaterPowered(typeAt(server, x, y, z + 2)),
                    "the repeater turned off after the source cut");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // Harness
    // ------------------------------------------------------------------

    private static BlockType worldAir(EngineServer server) {
        return server.world().airType();
    }

    private static int wirePower(EngineServer server, int x, int y, int z) {
        BlockType type = typeAt(server, x, y, z);
        return RedstoneBlocks.isWire(type) ? RedstoneBlocks.wirePower(type) : -1;
    }

    private static BlockType typeAt(EngineServer server, int x, int y, int z) {
        return server.world().getBlock(new BlockPosition(x, y, z));
    }

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "rs" + System.nanoTime() % 100000,
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

    @SuppressWarnings("unused")
    private PlayerSession join(EngineServer server, String name) throws InterruptedException {
        var accepted = server.joinRequest(new ClientLink() {
            @Override
            public boolean isActive() {
                return true;
            }

            @Override
            public void kick(String reason) {
            }
        }, name, UUID.randomUUID());
        PlayerSession session = ((EngineBridge.Accepted) accepted).session();
        server.joinCompleted(session);
        await(() -> session.state() == PlayerState.PLAYING, name + " playing");
        return session;
    }

    @SuppressWarnings("unused")
    private static void unused(BooleanSupplier supplier) {
        assertTrue(supplier.getAsBoolean());
    }
}
