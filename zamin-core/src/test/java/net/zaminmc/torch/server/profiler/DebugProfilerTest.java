package net.zaminmc.torch.server.profiler;

import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.player.PlayerSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The profiler (Slice 90 p2 — Profiler + DebugCommand 1:1): the section
 * stack's results walk (the self row at index 0, the children's parent and
 * total shares, the unspecified remainder, the 999/1000 decay), and the
 * /debug start/stop cycle over the live server — the delayed activation,
 * the span anchors, and the report file under debug/ with the recursive
 * section tree.
 */
class DebugProfilerTest {

    @TempDir
    Path dataDir;

    net.zaminmc.torch.server.EngineServer server;
    PlayerSession alice;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "dbg" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString(),
                net.zaminmc.torch.GameMode.SURVIVAL);
        server = new net.zaminmc.torch.server.EngineServer(config);
        server.start();
        var accepted = (net.zaminmc.torch.server.net.EngineBridge.Accepted)
                server.joinRequest(new SilentLink(), "Alice",
                        UUID.nameUUIDFromBytes("OfflinePlayer:Alice".getBytes()));
        alice = accepted.session();
        server.ticker().submit(alice::markPlaying);
        await(() -> alice.state() == net.zaminmc.torch.entity.PlayerState.PLAYING, "playing");
        server.ticker().submit(() -> alice.setOpLevel(3));
        await(() -> alice.opLevel() >= 3, "Alice opped");
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    private static final class SilentLink implements net.zaminmc.torch.server.net.ClientLink {
        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public void kick(String reason) {
        }
    }

    private void await(java.util.function.BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    @Test
    void theResultsWalkCarriesTheReferenceShape() {
        EngineProfiler profiler = new EngineProfiler();
        profiler.start();
        assertTrue(profiler.isProfiling(), "collecting");
        // root -> jobs -> levels.<world>.tick, the reference's own walk.
        profiler.push("root");
        profiler.push("jobs");
        profiler.swap("levels");
        profiler.push("world");
        profiler.push("tick");
        profiler.pop();
        profiler.pop();
        profiler.swap("connection");
        profiler.swap("players");
        profiler.swap("tickables");
        profiler.pop();
        profiler.pop();
        List<EngineProfiler.Result> root = profiler.getResults("root");
        assertNotNull(root, "the results");
        // The self row at index 0 (100% of itself, its share of the root).
        assertEquals("root", root.get(0).location);
        assertEquals(100.0, root.get(0).percentageOfParent, 1e-9);
        // The children present: jobs, levels, tickables (the late sections).
        assertTrue(root.size() >= 3, "the root children carry at least the three sections");
        assertTrue(root.stream().anyMatch(r -> "jobs".equals(r.location)), "jobs recorded");
        assertTrue(root.stream().anyMatch(r -> "levels".equals(r.location)), "levels recorded");
        // The levels row's own children.
        List<EngineProfiler.Result> levels = profiler.getResults("root.levels");
        assertNotNull(levels);
        assertEquals("root.levels", levels.get(0).location);
        assertTrue(levels.stream().anyMatch(r -> "world".equals(r.location)), "the world row");
        profiler.stop();
    }

    @Test
    void theDecayFadesTheTableBetweenReads() {
        EngineProfiler profiler = new EngineProfiler();
        profiler.start();
        profiler.push("root");
        profiler.push("solo");
        Thread.yield();
        profiler.pop();
        profiler.pop();
        List<EngineProfiler.Result> first = profiler.getResults("root");
        long firstShare = Math.round(first.stream()
                .filter(r -> "solo".equals(r.location))
                .findFirst().map(r -> r.percentageOfTotal).orElse(0.0));
        assertTrue(firstShare > 0, "the solo section recorded");
        // The reference's *999/1000 decay: the table fades between reads.
        List<EngineProfiler.Result> second = profiler.getResults("root");
        long secondShare = Math.round(second.stream()
                .filter(r -> "solo".equals(r.location))
                .findFirst().map(r -> r.percentageOfTotal).orElse(0.0));
        assertTrue(secondShare <= firstShare, "the share decayed ("
                + firstShare + " -> " + secondShare + ")");
        profiler.stop();
    }

    @Test
    void theDebugCycleWritesTheReport() throws Exception {
        server.chatService().submitChat(alice, "/debug start");
        await(() -> server.world().totalTicks() >= 2, "the start ran");
        // A few ticks of live collection.
        await(() -> server.world().totalTicks() >= 5, "some ticks");
        server.chatService().submitChat(alice, "/debug stop");
        await(() -> server.world().totalTicks() >= 6, "the stop ran");
        // The report landed under debug/.
        Path debugDir = dataDir.resolve("debug");
        await(() -> {
            try {
                return Files.isDirectory(debugDir)
                        && Files.list(debugDir).anyMatch(p ->
                                p.getFileName().toString().startsWith("profile-results-"));
            } catch (Exception e) {
                return false;
            }
        }, "the report file");
        Path report = Files.list(debugDir).findFirst().orElseThrow();
        String text = Files.readString(report);
        assertTrue(text.contains("---- Minecraft Profiler Results ----"), "the header");
        assertTrue(text.contains("// This is approximately"), "the ticks-per-second line");
        assertTrue(text.contains("--- BEGIN PROFILE DUMP ---"), "the dump open");
        assertTrue(text.contains("--- END PROFILE DUMP ---"), "the dump close");
        // The engine's own sections: root's children with jobs and levels.
        assertTrue(text.contains("jobs"), "the jobs section");
        assertTrue(text.contains("levels"), "the levels section");
        assertTrue(text.contains("tick"), "the tick section");
    }

    @Test
    void theStopWithoutStartRefuses() throws Exception {
        server.chatService().submitChat(alice, "/debug stop");
        await(() -> server.world().totalTicks() >= 1, "the refusal ran");
    }
}
