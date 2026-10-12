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
 * The player-driven redstone sources on the live engine (Slice 9c): the
 * lever's toggle (the weak all-direction emission + the strong
 * attachment-side arm), the button's press-and-release (the 20-tick stone
 * / 30-tick wood auto-release), and the pressure plate's occupancy cycle
 * (press on stand, the 20-tick re-compute, release on leave).
 */
class RedstoneSourcesTest {

    @TempDir
    Path dataDir;

    @Test
    void theLeverPowersTheDustBesideItAndTheBlockBelow() throws Exception {
        EngineServer server = boot();
        try {
            // A floor lever on a stone block: the dust ADJACENT at the
            // lever's own level reads the weak all-direction 15 directly,
            // and the mount block below receives the strong arm.
            int x = 10, z = 10, y = 40;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.STONE);
                // The floor lever (up_z facing id 5, unpowered).
                server.world().setBlock(new BlockPosition(x, y, z),
                        RedstoneBlocks.leverOf(5, false));
                // The adjacent dust's bed.
                server.world().setBlock(new BlockPosition(x + 1, y - 1, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x + 1, y, z),
                        RedstoneBlocks.wireOfPower(0));
            });
            await(() -> typeAt(server, x, y, z).identifier().value().startsWith("lever_"),
                    "the lever placed");
            // The toggle: the lever powers on.
            server.ticker().submit(() -> server.redstone().useLever(new BlockPosition(x, y, z)));
            await(() -> RedstoneBlocks.leverPowered(typeAt(server, x, y, z)),
                    "the lever toggled powered");
            // The weak arm: the adjacent dust reads 15 in the same commit.
            await(() -> wirePower(server, x + 1, y, z) == 15,
                    "the adjacent dust reads the lever's weak 15");
            // The toggle back: the dust dies.
            server.ticker().submit(() -> server.redstone().useLever(new BlockPosition(x, y, z)));
            await(() -> !RedstoneBlocks.leverPowered(typeAt(server, x, y, z)),
                    "the lever toggled back off");
            await(() -> wirePower(server, x + 1, y, z) == 0,
                    "the adjacent dust reads 0 after the release");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theStoneButtonPressesAndReleasesAfterTwentyTicks() throws Exception {
        EngineServer server = boot();
        try {
            // A button on a wall with dust on top of the wall block.
            int x = 20, z = 20, y = 40;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y, z), BuiltinBlocks.STONE);
                // The button on the north face (FACING north = id 2).
                server.world().setBlock(new BlockPosition(x, y, z - 1),
                        RedstoneBlocks.buttonOf(2, false, false));
                server.world().setBlock(new BlockPosition(x, y + 1, z),
                        RedstoneBlocks.wireOfPower(0));
            });
            await(() -> RedstoneBlocks.isButton(typeAt(server, x, y, z - 1)),
                    "the button placed");
            // The press.
            server.ticker().submit(() -> server.redstone().useButton(
                    new BlockPosition(x, y, z - 1)));
            await(() -> RedstoneBlocks.buttonPowered(typeAt(server, x, y, z - 1)),
                    "the button pressed");
            // The strong arm: the button powers its mount block (FACING 2 =
            // north, the mount sits north of the button = the stone), the
            // dust on the stone reads 15.
            await(() -> wirePower(server, x, y + 1, z) == 15,
                    "the dust on the button's mount reads 15");
            // The release: the stone button auto-releases after 20 ticks.
            await(() -> !RedstoneBlocks.buttonPowered(typeAt(server, x, y, z - 1)),
                    "the stone button released after its 20-tick window");
            // The dust dies with it.
            await(() -> wirePower(server, x, y + 1, z) == 0,
                    "the dust reads 0 after the release");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void thePlatePressesUnderAStandingBody() throws Exception {
        EngineServer server = boot();
        try {
            int x = 30, z = 30, y = 40;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x, y, z),
                        RedstoneBlocks.plateOf(false, false));
                // The dust reading the mount's re-radiation: the stone under
                // the plate receives the strong UP arm, re-radiating to the
                // dust sitting beside it at the same level.
                server.world().setBlock(new BlockPosition(x + 1, y - 2, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x + 1, y - 1, z),
                        RedstoneBlocks.wireOfPower(0));
            });
            await(() -> RedstoneBlocks.isPlate(typeAt(server, x, y, z)), "the plate placed");
            // A body stands on the plate.
            PlayerSession body = joined(server);
            server.teleportPlayer(body, new Position(x + 0.5, y, z + 0.5));
            await(() -> RedstoneBlocks.platePowered(typeAt(server, x, y, z)),
                    "the plate pressed under the standing body");
            // The strong arm: the mount's re-radiation reaches the dust.
            await(() -> wirePower(server, x + 1, y - 1, z) == 15,
                    "the dust reads the mount's re-radiated 15");
            // The body steps off: the plate releases after its debounce.
            server.teleportPlayer(body, new Position(x + 0.5, y, z + 6.5));
            await(() -> !RedstoneBlocks.platePowered(typeAt(server, x, y, z)),
                    "the plate released after the body left");
            await(() -> wirePower(server, x + 1, y - 1, z) == 0,
                    "the dust reads 0 after the release");
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
        }, "Stand", UUID.randomUUID());
        PlayerSession session = ((EngineBridge.Accepted) accepted).session();
        server.joinCompleted(session);
        await(() -> session.state() == PlayerState.PLAYING, "the player playing");
        return session;
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

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "rss" + Math.abs(UUID.randomUUID().hashCode() % 100000),
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
    private static void unused(BooleanSupplier supplier) {
        assertTrue(supplier.getAsBoolean());
    }
}
