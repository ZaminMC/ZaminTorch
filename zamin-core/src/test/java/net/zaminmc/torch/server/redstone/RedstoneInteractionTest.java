package net.zaminmc.torch.server.redstone;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.entity.PlayerState;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The redstone interaction layer on the live engine (Slice 9b): the dust's
 * placement gate (the solid-bed rule + the cascade filling the power on the
 * same commit), the redstone torch's attachment walk (the clicked face's
 * FACING), and the repeater's right-click delay cycle (RepeaterBlock.use
 * lines 38-45).
 */
class RedstoneInteractionTest {

    @TempDir
    Path dataDir;

    @Test
    void theDustPlacementFillsTheWireOnTheSameCommit() throws Exception {
        EngineServer server = boot();
        try {
            // The source torch under a block: placing the dust ON TOP of the
            // fed block (a top-face use) lands a wire already reading 15 —
            // the cascade rides the placement commit (the same-tick walk).
            int x = 10, z = 10, y = 40;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 2, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y - 1, z),
                        RedstoneBlocks.torchStandingLit());
                server.world().setBlock(new BlockPosition(x, y, z), BuiltinBlocks.STONE);
            });
            await(() -> solidAt(server, x, y, z), "the source block placed");
            PlayerSession player = joined(server);
            hold(player, "minecraft:redstone");
            server.teleportPlayer(player, new Position(x + 0.5, y, z + 2.5));
            await(() -> player.position().distanceSquared(new Position(x + 0.5, y, z + 2.5)) < 4.0,
                    "the player stood at the circuit");
            server.useItemOnBlock(player, new BlockPosition(x, y, z), 1,
                    java.util.Optional.empty(), 0, id -> { });
            await(() -> wirePower(server, x, y + 1, z) == 15,
                    "the placed dust reads 15 on the same commit");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theDustRefusesToPlaceWithoutItsSolidBed() throws Exception {
        EngineServer server = boot();
        try {
            int x = 20, z = 20, y = 40;
            // A lone wall with open air on every other side: the north-face
            // use targets a cell with nothing solid beneath it.
            server.ticker().submit(() ->
                    server.world().setBlock(new BlockPosition(x, y, z + 1), BuiltinBlocks.STONE));
            await(() -> solidAt(server, x, y, z + 1), "the wall placed");
            PlayerSession player = joined(server);
            hold(player, "minecraft:redstone");
            server.teleportPlayer(player, new Position(x + 0.5, y, z + 2.5));
            await(() -> player.position().distanceSquared(new Position(x + 0.5, y, z + 2.5)) < 4.0,
                    "the player stood at the wall");
            server.useItemOnBlock(player, new BlockPosition(x, y, z + 1), 2,
                    java.util.Optional.empty(), 0, id -> { });
            Thread.sleep(500);
            BlockType placed = typeAt(server, x, y, z);
            assertTrue(!RedstoneBlocks.isWire(placed),
                    "the dust never placed without its solid bed (got " + placed + ")");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theWallTorchLandsOnTheClickedFace() throws Exception {
        EngineServer server = boot();
        try {
            // A stone wall; the north-face use places the wall torch
            // pointing north (FACING 2) — hanging on the wall's north side.
            int x = 30, z = 30, y = 40;
            server.ticker().submit(() ->
                    server.world().setBlock(new BlockPosition(x, y, z), BuiltinBlocks.STONE));
            await(() -> solidAt(server, x, y, z), "the wall placed");
            PlayerSession player = joined(server);
            hold(player, "minecraft:redstone_torch");
            server.teleportPlayer(player, new Position(x + 0.5, y, z - 2.5));
            await(() -> player.position().distanceSquared(new Position(x + 0.5, y, z - 2.5)) < 4.0,
                    "the player stood at the wall");
            server.useItemOnBlock(player, new BlockPosition(x, y, z), 2,
                    java.util.Optional.empty(), 0, id -> { });
            await(() -> RedstoneBlocks.isTorch(typeAt(server, x, y, z - 1)),
                    "the wall torch placed on the clicked face");
            assertEquals(2, RedstoneBlocks.torchFacing(typeAt(server, x, y, z - 1)),
                    "the torch points north off the wall's north face");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theRepeaterUseCyclesTheDelayOneThroughFourAndBackToOne() throws Exception {
        EngineServer server = boot();
        try {
            int x = 40, z = 40, y = 40;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z),
                        RedstoneBlocks.repeaterOf(RedstoneBlocks.FACING_NORTH, 1, false));
            });
            await(() -> RedstoneBlocks.isRepeater(typeAt(server, x, y, z)), "the repeater placed");
            // Four uses: 1 -> 2 -> 3 -> 4 -> 1.
            PlayerSession user = joined(server);
            server.teleportPlayer(user, new Position(x + 0.5, y, z + 2.5));
            await(() -> user.position().distanceSquared(new Position(x + 0.5, y, z + 2.5)) < 4.0,
                    "the user stood at the repeater");
            for (int expected : new int[]{2, 3, 4, 1}) {
                server.useItemOnBlock(user, new BlockPosition(x, y, z), 1,
                        java.util.Optional.empty(), 0, id -> { });
                int finalExpected = expected;
                await(() -> RedstoneBlocks.repeaterDelay(typeAt(server, x, y, z)) == finalExpected,
                        "the delay cycled to " + expected);
            }
            // The facing rides unchanged through the cycle.
            assertEquals(RedstoneBlocks.FACING_NORTH,
                    RedstoneBlocks.repeaterFacing(typeAt(server, x, y, z)));
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // Harness
    // ------------------------------------------------------------------

    private PlayerSession joined(EngineServer server) throws InterruptedException {
        var accepted = server.joinRequest(new ClientLink() {
            @Override
            public boolean isActive() {
                return true;
            }

            @Override
            public void kick(String reason) {
            }
        }, "Wright", UUID.randomUUID());
        PlayerSession session = ((EngineBridge.Accepted) accepted).session();
        server.joinCompleted(session);
        await(() -> session.state() == PlayerState.PLAYING, "the player playing");
        return session;
    }

    private void hold(PlayerSession player, String id) {
        player.inventory().setSlot(0, net.zaminmc.torch.item.ItemStack.of(
                net.zaminmc.torch.server.item.BuiltinItems.lookup(
                        net.zaminmc.torch.util.Identifier.parse(id)).orElseThrow(), 1));
        player.inventory().selectHotbarSlot(0);
    }

    private static boolean solidAt(EngineServer server, int x, int y, int z) {
        AtomicReference<Boolean> solid = new AtomicReference<>();
        server.ticker().submit(() -> solid.set(
                net.zaminmc.torch.server.block.WorldSolidity.isSolid(
                        server.world().getBlock(new BlockPosition(x, y, z)))));
        return waitFor(solid);
    }

    private static int wirePower(EngineServer server, int x, int y, int z) {
        BlockType type = typeAt(server, x, y, z);
        return RedstoneBlocks.isWire(type) ? RedstoneBlocks.wirePower(type) : -1;
    }

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

    private static boolean waitFor(AtomicReference<Boolean> flag) {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            if (Boolean.TRUE.equals(flag.get())) {
                return true;
            }
            sleep(10);
        }
        return Boolean.TRUE.equals(flag.get());
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
                "rsi" + Math.abs(UUID.randomUUID().hashCode() % 100000),
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
