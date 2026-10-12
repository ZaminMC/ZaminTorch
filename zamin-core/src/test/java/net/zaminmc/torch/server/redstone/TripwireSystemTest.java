package net.zaminmc.torch.server.redstone;

import net.zaminmc.torch.GameMode;
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
 * The tripwire on the live engine (Slice 10c — the reference port of
 * TripwireBlock + TripwireHookBlock): the hook pair's chain attachment (the
 * 41-cell walk finding the facing hook), the occupancy press (a body in the
 * wire's box powers the chain, the hooks answer), the strong FACING-only
 * arm, and the release when the body leaves (the 10-tick re-scan).
 */
class TripwireSystemTest {

    @TempDir
    Path dataDir;

    @Test
    void theChainAttachesAndPowersUnderAStandingBody() throws Exception {
        EngineServer server = boot();
        try {
            int y = 40, z = 10;
            server.ticker().submit(() -> {
                // The stone floor under the whole chain + the walls behind
                // each hook (the hooks mount on the blocks at the chain's
                // ends).
                for (int x = 9; x <= 17; x++) {
                    server.world().setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.STONE);
                }
                // The hooks: at 10 facing EAST (into the chain), at 16
                // facing WEST (back at it) — the mounting walls behind them.
                server.world().setBlock(new BlockPosition(9, y, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(17, y, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(10, y, z),
                        TripwireBlocks.hookOf(5, false, false)); // EAST
                server.world().setBlock(new BlockPosition(16, y, z),
                        TripwireBlocks.hookOf(4, false, false)); // WEST
                // The dust reading the far hook's weak arm: the stone mount
                // + the dust beside the hook.
                server.world().setBlock(new BlockPosition(16, y - 1, z + 1), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(16, y, z + 1),
                        RedstoneBlocks.wireOfPower(0));
                // The wire chain between the hooks.
                for (int x = 11; x <= 15; x++) {
                    server.world().setBlock(new BlockPosition(x, y, z),
                            TripwireBlocks.wireOf(false, false, false, false));
                }
            });
            // The chain completed: both hooks attach.
            await(() -> TripwireBlocks.hookAttached(typeAt(server, 10, y, z)),
                    "the near hook attached to the chain");
            await(() -> TripwireBlocks.hookAttached(typeAt(server, 16, y, z)),
                    "the far hook attached to the chain");

            // A body steps into the wire's box: the wire powers, both hooks
            // answer, the dust beside the far hook reads 15.
            PlayerSessionBody body = joined(server);
            server.teleportPlayer(body.session, new Position(13.5, y, z + 0.5));
            await(() -> TripwireBlocks.wirePowered(typeAt(server, 13, y, z)),
                    "the wire powered under the standing body");
            await(() -> TripwireBlocks.hookPowered(typeAt(server, 10, y, z)),
                    "the near hook powered");
            await(() -> TripwireBlocks.hookPowered(typeAt(server, 16, y, z)),
                    "the far hook powered");
            await(() -> wirePower(server, 16, y, z + 1) == 15,
                    "the dust beside the far hook reads the weak 15");

            // The body steps off: the 10-tick re-scan releases the chain.
            server.teleportPlayer(body.session, new Position(13.5, y, z + 6.5));
            await(() -> !TripwireBlocks.wirePowered(typeAt(server, 13, y, z)),
                    "the wire released after the body left");
            await(() -> !TripwireBlocks.hookPowered(typeAt(server, 16, y, z)),
                    "the far hook released");
            await(() -> wirePower(server, 16, y, z + 1) == 0,
                    "the dust reads 0 after the release");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theHookStrongArmPowersItsMount() throws Exception {
        EngineServer server = boot();
        try {
            int y = 40, z = 40;
            server.ticker().submit(() -> {
                // A full powered chain: hooks at 10 (facing east) and 14
                // (facing west), wires between, and the walls behind the
                // hooks. The far hook's strong arm powers the wall BEHIND
                // it (the mounted-source convention, the lever/button
                // family); the wall re-radiates to the dust on its top.
                for (int x = 9; x <= 15; x++) {
                    server.world().setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.STONE);
                }
                server.world().setBlock(new BlockPosition(9, y, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(15, y, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(10, y, z),
                        TripwireBlocks.hookOf(5, false, false)); // EAST
                server.world().setBlock(new BlockPosition(14, y, z),
                        TripwireBlocks.hookOf(4, false, false)); // WEST
                for (int x = 11; x <= 13; x++) {
                    server.world().setBlock(new BlockPosition(x, y, z),
                            TripwireBlocks.wireOf(false, false, false, false));
                }
                // The dust on the FAR WALL's top: the strong arm re-radiates
                // through the wall at (15).
                server.world().setBlock(new BlockPosition(15, y + 1, z),
                        RedstoneBlocks.wireOfPower(0));
                // The dust BESIDE the far hook (adjacent, same level): the
                // weak all-direction arm.
                server.world().setBlock(new BlockPosition(14, y - 1, z + 1), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(14, y, z + 1),
                        RedstoneBlocks.wireOfPower(0));
            });
            await(() -> TripwireBlocks.hookAttached(typeAt(server, 14, y, z)),
                    "the far hook attached");
            PlayerSessionBody body = joined(server);
            server.teleportPlayer(body.session, new Position(12.5, y, z + 0.5));
            await(() -> TripwireBlocks.hookPowered(typeAt(server, 14, y, z)),
                    "the far hook powered under the standing body");
            await(() -> wirePower(server, 14, y, z + 1) == 15,
                    "the dust beside the far hook reads the weak 15");
            await(() -> wirePower(server, 15, y + 1, z) == 15,
                    "the wall's dust reads the strong arm's re-radiation");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // Harness
    // ------------------------------------------------------------------

    /** The joined session holder (the plate test's pattern). */
    private record PlayerSessionBody(PlayerSession session) {
    }

    private PlayerSessionBody joined(EngineServer server) throws InterruptedException {
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
        return new PlayerSessionBody(session);
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

    private static int wirePower(EngineServer server, int x, int y, int z) {
        BlockType type = typeAt(server, x, y, z);
        return RedstoneBlocks.isWire(type) ? RedstoneBlocks.wirePower(type) : -1;
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
                "tws" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString());
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
