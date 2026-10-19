package net.zaminmc.torch.server.world;

import net.zaminmc.torch.server.border.EngineWorldBorder;
import net.zaminmc.torch.server.border.WorldBorderDataStore;
import net.zaminmc.torch.server.concurrent.BlockingIoLane;
import net.zaminmc.torch.server.concurrent.ComputeSubsystem;
import net.zaminmc.torch.server.concurrent.OwnershipDomain;
import net.zaminmc.torch.server.player.PlayerRegistry;
import net.zaminmc.torch.server.scoreboard.EngineScoreboard;
import net.zaminmc.torch.server.scoreboard.ScoreboardCriterion;
import net.zaminmc.torch.server.scoreboard.ScoreboardDataStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE BOARD/BORDER PAIR'S PIPELINE ADOPTION'S OWN EVIDENCE (slice S3):
 * the pair's snapshot contracts (S1's own preparation) completed with
 * the split — the pure serializers (the JSON bytes OUT, no live-object
 * read, no I/O) and the file halves (the temp write + the atomic move,
 * the engine's own durability convention replacing the pair's direct
 * writes) — and the named pipeline path through the real lanes. THE
 * EQUIVALENCE: the synchronous save's file and the pure serializer's
 * bytes are identical; the load round-trip restores the same rows. THE
 * WIRING: saveAllNow's board/border saves ride the pipeline — the
 * snapshot on the tick lane, the rest off it.
 */
class BoardBorderSaveSplitTest {

    @TempDir
    Path dir;

    private static void drainUntil(OwnershipDomain domain, ComputeSubsystem compute,
                                   BlockingIoLane io, CountDownLatch done) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (!done.await(10, TimeUnit.MILLISECONDS)
                && System.currentTimeMillis() < deadline) {
            domain.enter();
            try {
                compute.drainResults(domain);
                io.drainCompletions(domain);
            } finally {
                domain.exit();
            }
        }
    }

    @Test
    void scoreboardSplitIsByteIdenticalAndRoundTrips() throws Exception {
        Path file = dir.resolve("scoreboard.json");

        EngineScoreboard source = new EngineScoreboard(new PlayerRegistry());
        source.createObjective("points", ScoreboardCriterion.DUMMY);
        source.addTeam("red");
        source.setDisplayObjective(1, source.getObjective("points"));
        source.getScore("Alice", source.getObjective("points")).set(6);
        source.addMemberToTeam("Alice", "red");

        ScoreboardDataStore store = new ScoreboardDataStore(file, source);
        byte[] pure = store.serialize(store.snapshot());
        store.save();
        assertArrayEquals(pure, Files.readAllBytes(file),
                "the sync save's file carries exactly the pure serializer's bytes");

        EngineScoreboard restored = new EngineScoreboard(new PlayerRegistry());
        new ScoreboardDataStore(file, restored).load();
        assertEquals("points", restored.getObjective("points").name(),
                "the objective restored");
        assertEquals(1, restored.getTeam("red").members().size(),
                "the team member restored");
        assertEquals(6, restored.getScore("Alice", restored.getObjective("points")).get(),
                "the score cell restored");
        assertEquals("points", restored.getDisplayObjective(1).name(),
                "the display slot restored");
    }

    @Test
    void borderSplitIsByteIdenticalAndRoundTrips() throws Exception {
        Path file = dir.resolve("worldborder.json");

        EngineWorldBorder source = new EngineWorldBorder(new PlayerRegistry());
        source.setCenter(12.5, -7.25);
        source.setSize(4321.5);
        source.setSafeZone(6.5);
        source.setDamagePerBlock(1.25);
        source.setWarningTime(11);
        source.setWarningDistance(7);

        WorldBorderDataStore store = new WorldBorderDataStore(file, source);
        byte[] pure = store.serialize(store.snapshot());
        store.save();
        assertArrayEquals(pure, Files.readAllBytes(file),
                "the sync save's file carries exactly the pure serializer's bytes");

        EngineWorldBorder restored = new EngineWorldBorder(new PlayerRegistry());
        new WorldBorderDataStore(file, restored).load();
        assertEquals(12.5, restored.getCenterX());
        assertEquals(-7.25, restored.getCenterZ());
        assertEquals(4321.5, restored.getLerpSize());
        assertEquals(6.5, restored.getSafeZone());
        assertEquals(1.25, restored.getDamagePerBlock());
        assertEquals(11, restored.getWarningTime());
        assertEquals(7, restored.getWarningDistance());
    }

    @Test
    void thePairRidesThePipelineThroughTheLanes() throws Exception {
        OwnershipDomain domain = OwnershipDomain.create("s3:test");
        ComputeSubsystem compute = new ComputeSubsystem(8, 1);
        BlockingIoLane io = new BlockingIoLane(8, 1);
        WorldSavePipeline pipeline = new WorldSavePipeline(compute, io, domain);
        try {
            Path boardFile = dir.resolve("live-scoreboard.json");
            Path borderFile = dir.resolve("live-worldborder.json");

            EngineScoreboard source = new EngineScoreboard(new PlayerRegistry());
            source.createObjective("kills", ScoreboardCriterion.DUMMY);
            source.getScore("Steve", source.getObjective("kills")).set(3);
            ScoreboardDataStore boardStore = new ScoreboardDataStore(boardFile, source);

            EngineWorldBorder border = new EngineWorldBorder(new PlayerRegistry());
            border.setCenter(1.0, 2.0);
            border.setSize(999.0);
            // a pending resize: the snapshot carries the lerp rows
            border.setSize(999.0, 2000.0, 60_000);
            WorldBorderDataStore borderStore = new WorldBorderDataStore(borderFile, border);

            // ONE capture per save (the engine's own semantics — a pending
            // lerp's live size moves with real time, the save's content is
            // the capture-time state)
            WorldBorderDataStore.Snapshot borderSnapshot = borderStore.snapshot();
            byte[] boardBytes = boardStore.serialize(boardStore.snapshot());
            byte[] borderBytes = borderStore.serialize(borderSnapshot);

            CountDownLatch boardDone = pipeline.submitSave("scoreboard",
                    boardStore.snapshot(), boardStore::serialize, boardStore::writeSerialized);
            CountDownLatch borderDone = pipeline.submitSave("border",
                    borderSnapshot, borderStore::serialize, borderStore::writeSerialized);
            drainUntil(domain, compute, io, boardDone);
            drainUntil(domain, compute, io, borderDone);
            assertTrue(boardDone.await(0, TimeUnit.MILLISECONDS), "the board save landed");
            assertTrue(borderDone.await(0, TimeUnit.MILLISECONDS), "the border save landed");

            assertArrayEquals(boardBytes, Files.readAllBytes(boardFile),
                    "the landed board file carries the serializer's bytes");
            assertArrayEquals(borderBytes, Files.readAllBytes(borderFile),
                    "the landed border file carries the serializer's bytes");

            // the landed border rows: the pending lerp survives the pipeline
            String borderJson = new String(Files.readAllBytes(borderFile),
                    StandardCharsets.UTF_8);
            assertTrue(borderJson.contains("\"sizeLerpTarget\": 2000.0"),
                    "the pending lerp target persisted");
            assertTrue(pipeline.landed() >= 2, "both publications recorded");
        } finally {
            compute.shutdown();
            io.shutdown();
        }
    }
}
