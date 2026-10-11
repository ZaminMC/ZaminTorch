package net.zaminmc.torch.server.tnt;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.redstone.RedstoneBlocks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The TNT on the live engine (Slice 9f) — the reference port of TntBlock +
 * PrimedTntEntity: the redstone ignition (a TNT that arrives into — or
 * hears — a neighbor signal primes), the fuse's 80-tick flight, the 4.0F
 * detonation destroying the blast sphere, and the flint-and-steel arm.
 */
class TntSystemTest {

    @TempDir
    Path dataDir;

    @Test
    void theRedstoneSignalPrimesTheTntAndTheFuseDetonates() throws Exception {
        EngineServer server = boot();
        try {
            int x = 10, z = 10, y = 40;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y, z), BuiltinBlocks.TNT);
                server.world().setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.STONE);
                // A lever rides the TNT's side mount: its stone first.
                server.world().setBlock(new BlockPosition(x + 1, y, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x + 2, y, z),
                        RedstoneBlocks.leverOf(1, false));
            });
            await(() -> typeAt(server, x, y, z).identifier().value().equals("tnt"),
                    "the TNT placed");
            // Power on: the neighbor signal primes the block — the cell
            // empties and the primed body takes over.
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(x + 2, y, z)));
            await(() -> typeAt(server, x, y, z).identifier().value().equals("air"),
                    "the primed TNT left its cell");
            // The fuse: 80 ticks later the blast consumes the neighborhood
            // (the lever's stone and the floor disappear).
            await(() -> !typeAt(server, x + 1, y, z).identifier().value().equals("stone"),
                    "the blast consumed the neighbor stone");
            await(() -> typeAt(server, x, y - 1, z).identifier().value().equals("air"),
                    "the blast consumed the floor below");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theFlintAndSteelPrimesTheTnt() throws Exception {
        EngineServer server = boot();
        try {
            int x = 20, z = 20, y = 40;
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x, y, z), BuiltinBlocks.TNT);
                server.world().setBlock(new BlockPosition(x, y - 1, z), BuiltinBlocks.STONE);
            });
            await(() -> typeAt(server, x, y, z).identifier().value().equals("tnt"),
                    "the TNT placed");
            // The flint on the block: the prime.
            server.ticker().submit(() -> server.tnt().useIgnite(new BlockPosition(x, y, z)));
            await(() -> typeAt(server, x, y, z).identifier().value().equals("air"),
                    "the flint primed the TNT out of its cell");
            // The fuse ends: the blast.
            await(() -> typeAt(server, x, y - 1, z).identifier().value().equals("air"),
                    "the fuse ran out and the blast consumed the floor");
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

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "tnt" + Math.abs(UUID.randomUUID().hashCode() % 100000),
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
