package net.zaminmc.torch.server.weather;

import net.zaminmc.torch.server.concurrent.BlockingIoLane;
import net.zaminmc.torch.server.concurrent.ComputeSubsystem;
import net.zaminmc.torch.server.concurrent.OwnershipDomain;
import net.zaminmc.torch.server.gamerule.GameRuleDataStore;
import net.zaminmc.torch.server.world.WorldSavePipeline;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE WORLD-DATA FLAG PAIR'S PIPELINE ADOPTION'S OWN EVIDENCE (slice S5):
 * the weather store's and the game-rule store's compute-then-publish
 * splits. THE EQUIVALENCE: the split path's bytes and the historical
 * interleaved walks' bytes are BYTE-IDENTICAL; the sync files and the
 * pipeline bytes agree; the load round-trips restore the same rows. THE
 * PIPELINE: the named path through the real compute subsystem + I/O
 * lane, both stores gating independently under their own names.
 */
class WeatherGameRuleSaveSplitTest {

    @TempDir
    Path dir;

    /** The historical weather walk (the pre-S5 save body, verbatim). */
    private static byte[] legacyWeatherBytes(WeatherDataStore.WeatherState state)
            throws Exception {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(sink);
        out.writeByte('Z');
        out.writeByte('W');
        out.writeByte('D');
        out.writeByte(1);
        out.writeInt(state.clearWeatherTime());
        out.writeBoolean(state.raining());
        out.writeInt(state.rainTime());
        out.writeBoolean(state.thundering());
        out.writeInt(state.thunderTime());
        out.flush();
        return sink.toByteArray();
    }

    /** The historical game-rule walk (the pre-S5 save body, verbatim). */
    private static byte[] legacyGameRuleBytes(List<Map.Entry<String, String>> entries)
            throws Exception {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(sink);
        out.writeByte('Z');
        out.writeByte('G');
        out.writeByte('R');
        out.writeByte(1);
        out.writeShort(entries.size());
        for (Map.Entry<String, String> entry : entries) {
            out.writeUTF(entry.getKey());
            out.writeUTF(entry.getValue());
        }
        out.flush();
        return sink.toByteArray();
    }

    @Test
    void weatherSplitIsByteIdenticalToTheHistoricalWalkAndRoundTrips() throws Exception {
        WeatherDataStore.WeatherState state =
                new WeatherDataStore.WeatherState(12345, true, 678, false, 91011);

        WeatherDataStore store = new WeatherDataStore(dir.resolve("weather.bin"));

        byte[] split = store.serialize(state);
        assertArrayEquals(legacyWeatherBytes(state), split,
                "the off-tick serialization and the historical walk are byte-identical");

        store.save(state);
        assertArrayEquals(split, Files.readAllBytes(dir.resolve("weather.bin")),
                "the sync save's file carries the split path's bytes");
        WeatherDataStore.WeatherState restored = store.load();
        assertNotNull(restored);
        assertEquals(12345, restored.clearWeatherTime());
        assertTrue(restored.raining());
        assertEquals(678, restored.rainTime());
        assertEquals(false, restored.thundering());
        assertEquals(91011, restored.thunderTime());
    }

    @Test
    void gameRuleSplitIsByteIdenticalToTheHistoricalWalkAndRoundTrips() throws Exception {
        List<Map.Entry<String, String>> entries = List.of(
                Map.entry("doDaylightCycle", "true"),
                Map.entry("keepInventory", "false"),
                Map.entry("mobGriefing", "true"),
                Map.entry("randomTickSpeed", "3"));

        GameRuleDataStore store = new GameRuleDataStore(dir.resolve("gamerules.bin"));

        byte[] split = store.serialize(entries);
        assertArrayEquals(legacyGameRuleBytes(entries), split,
                "the off-tick serialization and the historical walk are byte-identical");

        store.save(entries);
        assertArrayEquals(split, Files.readAllBytes(dir.resolve("gamerules.bin")),
                "the sync save's file carries the split path's bytes");
        Set<Map.Entry<String, String>> restored = store.load();
        assertNotNull(restored);
        assertEquals(4, restored.size());
        Set<Map.Entry<String, String>> expected = new LinkedHashSet<>(entries);
        assertEquals(expected, restored, "the persisted pairs round-trip");
    }

    @Test
    void thePairRidesThePipelineThroughTheLanes() throws Exception {
        OwnershipDomain domain = OwnershipDomain.create("s5pair:test");
        ComputeSubsystem compute = new ComputeSubsystem(8, 1);
        BlockingIoLane io = new BlockingIoLane(8, 1);
        WorldSavePipeline pipeline = new WorldSavePipeline(compute, io, domain);
        try {
            WeatherDataStore weatherStore =
                    new WeatherDataStore(dir.resolve("live-weather.bin"));
            WeatherDataStore.WeatherState weatherState =
                    new WeatherDataStore.WeatherState(6000, false, 1000, true, 2000);
            byte[] weatherBytes = weatherStore.serialize(weatherState);

            GameRuleDataStore ruleStore =
                    new GameRuleDataStore(dir.resolve("live-gamerules.bin"));
            List<Map.Entry<String, String>> ruleEntries = List.of(
                    Map.entry("doFireTick", "false"),
                    Map.entry("commandBlockOutput", "false"));
            byte[] ruleBytes = ruleStore.serialize(ruleEntries);

            CountDownLatch weatherDone = pipeline.submitSave("weather", weatherState,
                    weatherStore::serialize, weatherStore::writeSerialized);
            CountDownLatch rulesDone = pipeline.submitSave("gamerule", ruleEntries,
                    ruleStore::serialize, ruleStore::writeSerialized);
            long deadline = System.currentTimeMillis() + 10_000;
            while ((!weatherDone.await(10, TimeUnit.MILLISECONDS)
                    || !rulesDone.await(10, TimeUnit.MILLISECONDS))
                    && System.currentTimeMillis() < deadline) {
                domain.enter();
                try {
                    compute.drainResults(domain);
                    io.drainCompletions(domain);
                } finally {
                    domain.exit();
                }
            }
            assertTrue(weatherDone.await(0, TimeUnit.MILLISECONDS), "the weather save landed");
            assertTrue(rulesDone.await(0, TimeUnit.MILLISECONDS), "the rule save landed");
            assertArrayEquals(weatherBytes, Files.readAllBytes(dir.resolve("live-weather.bin")),
                    "the landed weather file carries exactly the pure serializer's bytes");
            assertArrayEquals(ruleBytes, Files.readAllBytes(dir.resolve("live-gamerules.bin")),
                    "the landed rule file carries exactly the pure serializer's bytes");
            assertEquals(2000, weatherStore.load().thunderTime(),
                    "the weather rows round-trip");
            assertEquals(2, ruleStore.load().size(), "the rule rows round-trip");
        } finally {
            compute.shutdown();
            io.shutdown();
        }
    }
}
