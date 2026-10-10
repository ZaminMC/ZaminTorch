package net.zaminmc.torch.server;

import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.world.EngineChunk;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The first live offload of the permanent architecture (the design §13 chunk
 * contract through the Phase 3 compute subsystem): the on-demand chunk
 * generation leaves the tick — the detached terrain generates on the compute
 * pool over the immutable input, the owner installs at its drain boundary,
 * a result for an already-published chunk is discarded (never overwrite
 * newer authoritative state), and concurrent requests for one chunk
 * deduplicate into a single generation with every callback served.
 */
class ChunkGenerationOffloadTest {

    @TempDir
    Path dataDir;

    @Test
    void theTickStaysResponsiveWhileTheChunkGeneratesOffThread() throws Exception {
        EngineServer server = boot();
        ChunkPosition far = new ChunkPosition(4096, -4096); // far outside the spawn view

        AtomicBoolean markerRan = new AtomicBoolean(false);
        server.requestChunkLoad(far, chunk -> { });
        // The marker rides the same tick substrate: it runs while the chunk
        // generation is either in flight or already installed — either way
        // the tick was never blocked by the generation itself.
        server.ticker().submit(() -> markerRan.set(true));
        await(() -> markerRan.get(), "the marker task ran on the tick");
        await(() -> server.world().peek(far) != null,
                "the offloaded chunk publishes at the owner's drain");

        EngineChunk chunk = server.world().peek(far);
        assertNotNull(chunk, "the chunk is published");
        server.shutdown(null);
    }

    @Test
    void concurrentRequestsDedupeIntoOneGenerationAndEveryCallbackServes() throws Exception {
        EngineServer server = boot();
        ChunkPosition far = new ChunkPosition(-4096, 4096);

        List<EngineChunk> served = new CopyOnWriteArrayList<>();
        server.requestChunkLoad(far, served::add);
        server.requestChunkLoad(far, served::add);
        server.requestChunkLoad(far, served::add);

        await(() -> server.world().peek(far) != null, "the chunk publishes");
        await(() -> served.size() == 3, "every callback fires");
        assertSame(served.get(0), served.get(1), "the callbacks share the authoritative chunk");
        assertSame(served.get(1), served.get(2), "the callbacks share the authoritative chunk");
        assertSame(served.get(0), server.world().peek(far),
                "the served chunk is the published one");
        assertEquals(1, server.computeSubsystem().submittedCount(),
                "three requests deduplicate into one generation job");
        server.shutdown(null);
    }

    @Test
    void theExistingChunkFastPathServesSynchronously() throws Exception {
        EngineServer server = boot();
        ChunkPosition near = new ChunkPosition(0, 0); // inside the spawn pregeneration
        assertNotNull(server.world().peek(near), "the spawn chunk exists");

        List<EngineChunk> served = new CopyOnWriteArrayList<>();
        server.requestChunkLoad(near, served::add);
        assertEquals(1, served.size(), "the existing chunk serves on the spot");
        assertSame(server.world().peek(near), served.get(0));
        assertEquals(0, server.computeSubsystem().submittedCount(),
                "no compute job for an existing chunk");
        server.shutdown(null);
    }

    @Test
    void aResultForAnAlreadyPublishedChunkNeverOverwritesIt() throws Exception {
        EngineServer server = boot();
        ChunkPosition far = new ChunkPosition(4097, 4097);

        List<EngineChunk> served = new CopyOnWriteArrayList<>();
        server.requestChunkLoad(far, served::add);
        // The owner path generates the same chunk concurrently: whichever
        // result reaches publication, exactly one chunk wins and every
        // consumer sees it — the detached loser is discarded, never installed
        // over the published state.
        server.ticker().submit(() -> server.world().getOrGenerate(far));

        await(() -> server.world().peek(far) != null, "the chunk publishes");
        await(() -> !served.isEmpty(), "the callback serves");
        assertSame(server.world().peek(far), served.get(0),
                "the callback sees the authoritative chunk");
        server.shutdown(null);
    }

    // ------------------------------------------------------------------ harness

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "offload", "flat", 20, 2, 20,
                dataDir.toString());
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
