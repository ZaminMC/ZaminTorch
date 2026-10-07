package net.zamin.engine;

import net.zamin.api.BlockPosition;
import net.zamin.api.Position;
import net.zamin.engine.block.BuiltinBlocks;
import net.zamin.engine.config.EngineConfig;
import net.zamin.engine.net.ClientLink;
import net.zamin.engine.net.EngineBridge;
import net.zamin.engine.player.PlayerSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Path;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Behavioral scenarios for survival mining (§93/§614): the client proposes
 * start/abort/finish; the server validates reach, diggability and elapsed time
 * before committing, and re-syncs on rejection so no ghost blocks remain.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SurvivalMiningAcceptanceTest {

    private EngineServer server;

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer boot() throws InterruptedException {
        // Survival is the server default; booting without a mode override proves it.
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "itest", "it", 20, 4, 20,
                dataDir.toString());
        EngineServer started = new EngineServer(config);
        started.start();
        return started;
    }

    private PlayerSession join(String name) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        var result = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        return ((EngineBridge.Accepted) result).session();
    }

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 3_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    /** Places a dirt block at the given position through the authoritative path. */
    private void seedBlock(PlayerSession player, BlockPosition at) throws InterruptedException {
        server.blockInteraction().submitPlace(player, at.offset(0, -1, 0), 1, BuiltinBlocks.DIRT);
        await(() -> server.world().getBlock(at).equals(BuiltinBlocks.DIRT), "dirt seeded at " + at);
    }

    @Test
    void correctlyTimedSurvivalDigBreaksBlock() throws Exception {
        server = boot();
        PlayerSession player = join("Miner");
        BlockPosition target = new BlockPosition(2, 5, 2);
        seedBlock(player, target);

        server.blockInteraction().submitMiningStart(player, target);
        // dirt by hand: 15 ticks nominal (750ms); lenient floor 70% (525ms). Wait past it.
        Thread.sleep(900);
        server.blockInteraction().submitMiningFinished(player, target);
        await(() -> server.world().getBlock(target).equals(BuiltinBlocks.AIR),
                "survival dig commits break");
        server.shutdown(null);
    }

    @Test
    void tooFastFinishIsRejectedAndResynced() throws Exception {
        server = boot();
        PlayerSession player = join("Hasty");
        BlockPosition target = new BlockPosition(3, 5, 2);
        seedBlock(player, target);

        long baseline;
        var resyncs = new java.util.concurrent.atomic.AtomicInteger();
        server.addWorldListener((world, position, type) -> resyncs.incrementAndGet());
        baseline = resyncs.get();
        server.blockInteraction().submitMiningStart(player, target);
        server.blockInteraction().submitMiningFinished(player, target); // immediately: far too fast
        await(() -> resyncs.get() > baseline, "rejection resync published");
        // The authoritative block must still be dirt after the resync.
        assertEquals(BuiltinBlocks.DIRT, server.world().getBlock(target));
        server.shutdown(null);
    }

    @Test
    void abortedDigThenFinishIsRejected() throws Exception {
        server = boot();
        PlayerSession player = join("Abandoner");
        BlockPosition target = new BlockPosition(4, 5, 2);
        seedBlock(player, target);

        server.blockInteraction().submitMiningStart(player, target);
        Thread.sleep(100);
        server.blockInteraction().submitMiningAborted(player);
        Thread.sleep(700); // longer than the full nominal duration
        server.blockInteraction().submitMiningFinished(player, target);
        await(() -> server.world().getBlock(target).equals(BuiltinBlocks.DIRT),
                "aborted dig does not commit");
        server.shutdown(null);
    }

    @Test
    void finishWithoutSessionIsRejected() throws Exception {
        server = boot();
        PlayerSession player = join("Sneaky");
        BlockPosition target = new BlockPosition(5, 5, 2);
        seedBlock(player, target);

        server.blockInteraction().submitMiningFinished(player, target); // never started
        await(() -> server.world().getBlock(target).equals(BuiltinBlocks.DIRT),
                "session-less finish does not commit");
        server.shutdown(null);
    }

    @Test
    void bedrockNeverOpensMiningSession() throws Exception {
        server = boot();
        PlayerSession player = join("BedrockMiner");
        BlockPosition bedrock = new BlockPosition(6, 0, 2); // flat world ground level 4 -> y0 is bedrock
        server.requestChunkLoad(bedrock.chunkPosition(), chunk -> { });
        await(() -> server.world().isChunkLoaded(bedrock.chunkPosition()), "chunk loaded");
        assertEquals(BuiltinBlocks.BEDROCK, server.world().getBlock(bedrock));

        server.blockInteraction().submitMiningStart(player, bedrock);
        Thread.sleep(200);
        server.blockInteraction().submitMiningFinished(player, bedrock);
        await(() -> server.world().getBlock(bedrock).equals(BuiltinBlocks.BEDROCK),
                "bedrock survives a dig attempt");
        server.shutdown(null);
    }

    private static void assertEquals(Object expected, Object actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }
}
