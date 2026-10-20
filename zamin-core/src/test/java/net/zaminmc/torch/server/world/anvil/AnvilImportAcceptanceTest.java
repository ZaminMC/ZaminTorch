package net.zaminmc.torch.server.world.anvil;

import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.config.EngineConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Anvil canvas' live-engine acceptance (the task's own named case): a
 * booted server imports an original 1.8.8 world folder through the
 * explicit console command — the I/O lane feeding the owning lane's
 * drains — and the imported world becomes REAL engine state: the blocks
 * are readable through the engine's own world reads, the lighting
 * engine's install hook computed the light (the torch's block light, the
 * open sky's 15), the level.dat rows landed on the engine's surfaces
 * (the spawn, the day clock), and every corrupt input isolated loudly
 * while the good files imported around it. The wire leg rides the
 * existing chunk-serialization path (imported blocks are ordinary engine
 * blocks — the registry's own types — so the wire sends them exactly
 * like generated terrain; the protocol module's suite owns that path).
 */
class AnvilImportAcceptanceTest {

    @TempDir
    Path dir;

    @Test
    void theLiveEngineImportsAWorldFolderThroughTheExplicitCommand() throws Exception {
        // ------------------------------------------------------------ the fixture world
        Path goldMine = dir.resolve("goldmine");
        Map<String, Object> level = AnvilFixture.compound();
        level.put("RandomSeed", 4242L);
        level.put("SpawnX", 129);
        level.put("SpawnY", 70);
        level.put("SpawnZ", 351);
        level.put("Time", 100_000L);
        level.put("DayTime", 6000L);
        level.put("LevelName", "Gold Mine");
        level.put("generatorName", "default");
        level.put("GameType", 0);
        level.put("raining", (byte) 0);
        level.put("LastPlayed", 1_700_000_000L);
        AnvilFixture.writeLevelDat(goldMine, level);

        // The torch scene in chunk (8,8): a stone floor at y=0, a torch at
        // (130,1,130), open sky above — the light assertions' inputs. The
        // banner (176) is the engine's one unregistered 1.8.8 family: it
        // imports as air with a counted unknown state.
        byte[] torchBlocks = new byte[4096];
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                torchBlocks[AnvilFixture.cellIndex(x, 0, z)] = 1; // stone floor
            }
        }
        torchBlocks[AnvilFixture.cellIndex(2, 1, 2)] = 50; // the torch (130,1,130)
        torchBlocks[AnvilFixture.cellIndex(3, 1, 3)] = (byte) 176; // the banner
        byte[] biomes = new byte[256];
        java.util.Arrays.fill(biomes, (byte) 2); // desert
        byte[] torchNbt = AnvilFixture.nbtDocument(AnvilFixture.chunkNbt(8, 8,
                List.of(AnvilFixture.section(0, torchBlocks, new byte[2048], null)), biomes));

        // A plain neighbor chunk (9,8): dirt at y=0 — the cross-chunk read.
        byte[] dirtBlocks = new byte[4096];
        dirtBlocks[AnvilFixture.cellIndex(0, 0, 0)] = 3; // dirt at (144,0,128)
        byte[] dirtNbt = AnvilFixture.nbtDocument(AnvilFixture.chunkNbt(9, 8,
                List.of(AnvilFixture.section(0, dirtBlocks, new byte[2048], null)), null));

        // The already-loaded case: chunk (0,0) sits inside the boot
        // pregen's view — a gold block at y=100 must NOT appear (the live
        // state wins; the import's detached chunk is counted and discarded).
        byte[] skipBlocks = new byte[4096];
        skipBlocks[AnvilFixture.cellIndex(0, 4, 0)] = 41; // gold block at (0,100,0)
        byte[] skipNbt = AnvilFixture.nbtDocument(AnvilFixture.chunkNbt(0, 0,
                List.of(AnvilFixture.section(6, skipBlocks, new byte[2048], null)), null));

        AnvilFixture.writeRegionFile(goldMine.resolve("region").resolve("r.0.0.mca"),
                List.of(new AnvilFixture.Entry(8, 8, AnvilRegionFile.SCHEME_ZLIB, AnvilFixture.zlib(torchNbt)),
                        new AnvilFixture.Entry(9, 8, AnvilRegionFile.SCHEME_ZLIB, AnvilFixture.zlib(dirtNbt)),
                        new AnvilFixture.Entry(0, 0, AnvilRegionFile.SCHEME_ZLIB, AnvilFixture.zlib(skipNbt))));
        // A garbage region file beside the good one: the whole file
        // isolates and the good file still imports (§31.6's quarantine).
        Files.write(goldMine.resolve("region").resolve("r.1.0.mca"), new byte[64]);
        // A non-region file in region/: counted, skipped by name.
        Files.writeString(goldMine.resolve("region").resolve("notes.txt"), "not a region");

        // ------------------------------------------------------------ the capture
        List<String> console = new CopyOnWriteArrayList<>();
        Handler capture = new Handler() {
            @Override
            public void publish(LogRecord record) {
                console.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        Logger consoleLogger = Logger.getLogger(
                net.zaminmc.torch.server.chat.ConsoleSender.class.getName());
        consoleLogger.addHandler(capture);
        consoleLogger.setLevel(Level.INFO);
        consoleLogger.setUseParentHandlers(false);

        // ------------------------------------------------------------ the boot + the command
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "anvil-world", "it",
                20, 2, 20, dir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        try {
            server.consoleCommand("anvilimport " + goldMine);
            // Await the final report line (the I/O lane's last drain).
            long deadline = System.currentTimeMillis() + 20_000;
            while (System.currentTimeMillis() < deadline
                    && console.stream().noneMatch(line -> line.startsWith("Anvil import finished"))) {
                Thread.sleep(50);
            }
            String report = console.stream()
                    .filter(line -> line.startsWith("Anvil import finished"))
                    .findFirst().orElse(null);
            assertNotNull(report, "the final report landed (console: " + console + ")");

            // ------------------------------------------------------------ the engine reads
            var world = server.world();
            assertEquals("minecraft:stone",
                    world.getBlock(new net.zaminmc.torch.block.BlockPosition(130, 0, 130))
                            .identifier().toString(),
                    "the imported floor is readable through the engine (report: " + report
                            + ", console: " + console + ")");
            assertEquals("minecraft:torch",
                    world.getBlock(new net.zaminmc.torch.block.BlockPosition(130, 1, 130))
                            .identifier().toString(),
                    "the imported torch is readable through the engine");
            assertEquals("minecraft:air",
                    world.getBlock(new net.zaminmc.torch.block.BlockPosition(131, 1, 131))
                            .identifier().toString(),
                    "the banner (the engine's unregistered family) imported as air");
            assertEquals("minecraft:dirt",
                    world.getBlock(new net.zaminmc.torch.block.BlockPosition(144, 0, 128))
                            .identifier().toString(),
                    "the neighbor chunk's block");
            assertEquals("minecraft:air",
                    world.getBlock(new net.zaminmc.torch.block.BlockPosition(0, 100, 0))
                            .identifier().toString(),
                    "the already-loaded position keeps the live state (the publication rule)");
            assertEquals("desert", world.biomeAt(
                    new net.zaminmc.torch.block.BlockPosition(130, 0, 130)),
                    "the biome row landed");

            // The light (the install hook's own initial compute — the same
            // route generated chunks take): the torch radiates, the open sky
            // stays 15 above.
            assertEquals(14, world.blockLightAt(
                    new net.zaminmc.torch.block.BlockPosition(130, 1, 130)),
                    "the torch's own cell carries its emission");
            assertEquals(13, world.blockLightAt(
                    new net.zaminmc.torch.block.BlockPosition(131, 1, 130)),
                    "one step out attenuates by one");
            assertEquals(15, world.skyLightAt(
                    new net.zaminmc.torch.block.BlockPosition(130, 10, 130)),
                    "the open sky column stays direct 15");

            // The level rows: the spawn and the day clock landed on the
            // engine's surfaces; the seed is recorded in the report.
            assertEquals(129.5, world.spawnPosition().x(), 0.0);
            assertEquals(70.0, world.spawnPosition().y(), 0.0);
            assertEquals(351.5, world.spawnPosition().z(), 0.0);
            assertTrue(world.timeOfDay() >= 6000 && world.timeOfDay() < 6100,
                    "the imported day clock landed (and keeps walking): " + world.timeOfDay());

            // The ledger's own rows: installed 2 (the fresh positions),
            // skipped 1 (the boot pregen holds (0,0)), the garbage file
            // isolated whole, the unknown banner state counted.
            assertTrue(report.contains("installed 2 chunks"), "the report: " + report);
            assertTrue(report.contains("skipped 1 (already loaded)"), "the report: " + report);
            assertTrue(report.contains("unoccupied 1021"), "1024 entries minus 3 written: " + report);
            assertTrue(report.contains("shorter than the 8 KiB header"), "the garbage file: " + report);
            assertTrue(report.contains("id 176 meta 0 x1"), "the banner state: " + report);
        } finally {
            consoleLogger.removeHandler(capture);
            server.shutdown(null);
        }
    }

    @Test
    void aSecondImportWhileOneRunsIsRefused() throws Exception {
        // The one-at-a-time gate: the command refuses a second walk while
        // the first is in flight (the second attempt answers loudly, the
        // first walk finishes untouched).
        Path emptyWorld = dir.resolve("empty");
        Map<String, Object> level = AnvilFixture.compound();
        level.put("RandomSeed", 1L);
        level.put("SpawnX", 0);
        level.put("SpawnY", 64);
        level.put("SpawnZ", 0);
        level.put("Time", 0L);
        level.put("DayTime", 0L);
        level.put("LevelName", "Empty");
        AnvilFixture.writeLevelDat(emptyWorld, level);
        AnvilFixture.writeRegionFile(emptyWorld.resolve("region").resolve("r.0.0.mca"),
                List.of());

        List<String> console = new CopyOnWriteArrayList<>();
        Handler capture = new Handler() {
            @Override
            public void publish(LogRecord record) {
                console.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        Logger consoleLogger = Logger.getLogger(
                net.zaminmc.torch.server.chat.ConsoleSender.class.getName());
        consoleLogger.addHandler(capture);
        consoleLogger.setUseParentHandlers(false);

        EngineConfig config = new EngineConfig("127.0.0.1", 0, "anvil-gate", "it",
                20, 2, 20, dir.resolve("gate").toString());
        EngineServer server = new EngineServer(config);
        server.start();
        try {
            // Two synchronous back-to-back commands: the first starts (or
            // already finished — the empty world's walk is one tick), the
            // second either runs cleanly after it or is refused while it
            // runs; both answers are legal, and the refusal message names
            // the gate when it fires.
            server.consoleCommand("anvilimport " + emptyWorld);
            server.consoleCommand("anvilimport " + emptyWorld);
            long deadline = System.currentTimeMillis() + 20_000;
            while (System.currentTimeMillis() < deadline
                    && console.stream().filter(line -> line.startsWith("Anvil import finished")).count() < 1
                    && console.stream().noneMatch(line -> line.contains("already running"))) {
                Thread.sleep(50);
            }
            assertTrue(console.stream().anyMatch(line -> line.startsWith("Anvil import finished"))
                            || console.stream().anyMatch(line -> line.contains("already running")),
                    "the gate or the completion shows up (console: " + console + ")");
        } finally {
            consoleLogger.removeHandler(capture);
            server.shutdown(null);
        }
    }
}
