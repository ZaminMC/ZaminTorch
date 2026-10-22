package net.zaminmc.torch.server;

import net.zaminmc.torch.Server;
import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.world.anvil.AnvilFixture;
import net.zaminmc.torch.server.world.anvil.AnvilRegionFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The anvil import verb's public API acceptance (§27.10's capability test
 * — README row 13, the Wave IX exposure over the §31.4 walk): the
 * plugin-facing {@link Server#anvilImport} exercised through the api type
 * ALONE (no cast to engine internals), riding the SAME one-import gate and
 * the SAME I/O lanes as the operator command — the same fixture worlds the
 * command's acceptance family drives (the {@link AnvilFixture} writer).
 *
 * <p>The proven rows: a successful import returns the walk's own ledger
 * report (the §31.6 counted-refusal census — the installed/skipped rows
 * and the isolation reasons inside the returned string, never a second
 * report format); a refused folder answers the loud refusal row (a missing
 * level.dat is never a default world) and leaves the gate FREE (the
 * never-throw repair's liveness); the one-import gate answers a concurrent
 * second call immediately with its own row and releases on completion; and
 * the threading law holds — the calling thread parks for the report while
 * the tick lane keeps ticking (the walk's phases deliver at tick heads, the
 * same delivery-at-a-tick-head proof {@code BlockingIoApiAcceptanceTest}
 * rides), the gameplay lane never blocked.</p>
 */
class AnvilImportApiAcceptanceTest {

    @TempDir
    Path dir;

    private EngineServer engine;

    @BeforeEach
    void boot() throws InterruptedException {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "anvil-api-" + System.nanoTime() % 100000, "it",
                20, 2, 20, dir.toString());
        engine = new EngineServer(config);
        engine.start();
    }

    @AfterEach
    void stop() {
        engine.shutdown(null);
    }

    /** The api-typed server — the verb is exercised through this alone. */
    private Server api() {
        return engine;
    }

    // ------------------------------------------------------------- the fixture worlds

    /**
     * The gold-mine scene the command's own acceptance test drives: a
     * level.dat (spawn + day clock + seed), a torch chunk (8,8) with a
     * stone floor + a torch + the banner (176 — the engine's one
     * unregistered 1.8.8 family, importing as air with a counted unknown
     * state), a dirt neighbor (9,8), the already-loaded skip chunk (0,0)
     * inside the boot pregen's view, a garbage region file beside the good
     * one, an empty third region file, and a non-region file.
     */
    private Path goldMineWorld() throws Exception {
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

        byte[] dirtBlocks = new byte[4096];
        dirtBlocks[AnvilFixture.cellIndex(0, 0, 0)] = 3; // dirt at (144,0,128)
        byte[] dirtNbt = AnvilFixture.nbtDocument(AnvilFixture.chunkNbt(9, 8,
                List.of(AnvilFixture.section(0, dirtBlocks, new byte[2048], null)), null));

        byte[] skipBlocks = new byte[4096];
        skipBlocks[AnvilFixture.cellIndex(0, 4, 0)] = 41; // gold at (0,100,0)
        byte[] skipNbt = AnvilFixture.nbtDocument(AnvilFixture.chunkNbt(0, 0,
                List.of(AnvilFixture.section(6, skipBlocks, new byte[2048], null)), null));

        AnvilFixture.writeRegionFile(goldMine.resolve("region").resolve("r.0.0.mca"),
                List.of(new AnvilFixture.Entry(8, 8, AnvilRegionFile.SCHEME_ZLIB, AnvilFixture.zlib(torchNbt)),
                        new AnvilFixture.Entry(9, 8, AnvilRegionFile.SCHEME_ZLIB, AnvilFixture.zlib(dirtNbt)),
                        new AnvilFixture.Entry(0, 0, AnvilRegionFile.SCHEME_ZLIB, AnvilFixture.zlib(skipNbt))));
        // The garbage region file: the whole file isolates, counted.
        Files.write(goldMine.resolve("region").resolve("r.1.0.mca"), new byte[64]);
        // The empty third file: one more read+drain phase in the walk.
        AnvilFixture.writeRegionFile(goldMine.resolve("region").resolve("r.2.0.mca"), List.of());
        // A non-region file: counted, skipped by name.
        Files.writeString(goldMine.resolve("region").resolve("notes.txt"), "not a region");
        return goldMine;
    }

    /** The minimal well-formed world: a level.dat + one empty region file. */
    private Path emptyWorld(String name) throws Exception {
        Path empty = dir.resolve(name);
        Map<String, Object> level = AnvilFixture.compound();
        level.put("RandomSeed", 1L);
        level.put("SpawnX", 0);
        level.put("SpawnY", 64);
        level.put("SpawnZ", 0);
        level.put("Time", 0L);
        level.put("DayTime", 0L);
        level.put("LevelName", name);
        AnvilFixture.writeLevelDat(empty, level);
        AnvilFixture.writeRegionFile(empty.resolve("region").resolve("r.0.0.mca"), List.of());
        return empty;
    }

    // ------------------------------------------------------------- the tests

    /**
     * The successful import: the api verb returns the walk's own report
     * (the ledger summary with its counted-refusal census), the imported
     * world is readable through the api's own World surface, and the tick
     * lane kept ticking while the caller parked — the walk's phases
     * delivered at tick heads between the caller's submit and its answer
     * (the same delivery proof the BlockingIo acceptance family rides).
     */
    @Test
    void theApiImportsAWorldFolderAndReturnsTheLedgerReport() throws Exception {
        Path goldMine = goldMineWorld();
        Server api = api();

        // The caller parks on a FOREIGN thread (the any-thread law); the
        // tick counter is read through the api surface before + after.
        net.zaminmc.torch.World apiWorld = api.worlds().iterator().next();
        long ticksBefore = apiWorld.totalTicks();
        AtomicReference<String> report = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch answered = new CountDownLatch(1);
        Thread caller = new Thread(() -> {
            try {
                report.set(api.anvilImport(goldMine));
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                answered.countDown();
            }
        }, "anvil-api-caller");
        caller.start();
        assertTrue(answered.await(20, TimeUnit.SECONDS),
                "the parked caller received the walk's answer");
        caller.join(1000);
        assertNull(failure.get(), "the verb answered without throwing");

        // ------------------------------------------------------------ the report
        String row = report.get();
        assertNotNull(row, "the report row");
        assertTrue(row.startsWith("Anvil import finished: "),
                "the finished row (the walk's own answer): " + row);
        assertTrue(row.contains("installed 2 chunks"), "the fresh chunks: " + row);
        assertTrue(row.contains("skipped 1 (already loaded)"), "the publication rule's skip: " + row);
        assertTrue(row.contains("shorter than the 8 KiB header"),
                "the garbage region file's counted isolation: " + row);
        assertTrue(row.contains("id 176 meta 0 x1"), "the banner's counted unknown state: " + row);

        // ------------------------------------------------------------ the engine state, through the api reads
        assertEquals("minecraft:stone",
                apiWorld.getBlock(new BlockPosition(130, 0, 130)).identifier().toString(),
                "the imported floor is readable through the api world");
        assertEquals("minecraft:torch",
                apiWorld.getBlock(new BlockPosition(130, 1, 130)).identifier().toString(),
                "the imported torch is readable through the api world");
        assertEquals("minecraft:air",
                apiWorld.getBlock(new BlockPosition(131, 1, 131)).identifier().toString(),
                "the banner imported as air (the unregistered family)");
        assertEquals("minecraft:dirt",
                apiWorld.getBlock(new BlockPosition(144, 0, 128)).identifier().toString(),
                "the neighbor chunk's block");
        assertEquals("minecraft:air",
                apiWorld.getBlock(new BlockPosition(0, 100, 0)).identifier().toString(),
                "the already-loaded position keeps the live state");
        assertEquals("desert", apiWorld.biomeAt(new BlockPosition(130, 0, 130)),
                "the biome row landed");
        assertEquals(129.5, apiWorld.spawnPosition().x(), 0.0);
        assertEquals(351.5, apiWorld.spawnPosition().z(), 0.0);

        // ------------------------------------------------------------ the threading law
        // The gameplay lane never blocked: the walk's phases delivered at
        // tick heads while the caller parked (the report itself proves the
        // deliveries — a blocked tick lane could never drain them), and
        // the tick counter marched on across the walk (the poll absorbs
        // the counter's own phase inside the draining tick).
        long deadline = System.currentTimeMillis() + 2000;
        long ticksAfter = apiWorld.totalTicks();
        while (ticksAfter <= ticksBefore && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
            ticksAfter = apiWorld.totalTicks();
        }
        assertTrue(ticksAfter > ticksBefore,
                "the tick lane kept ticking across the import (before " + ticksBefore
                        + ", after " + ticksAfter + ")");
    }

    /**
     * The refused import (the counted-refusal discipline's loudest row): a
     * folder with a region/ directory but NO level.dat is refused — the
     * same row the operator command answers with, never a default world —
     * and the refusal does not strand the one-import gate (the follow-up
     * import walks: the never-throw repair's liveness row).
     */
    @Test
    void aFolderWithoutALevelDatIsRefusedLoudly() throws Exception {
        Path noLevel = dir.resolve("no-level");
        Files.createDirectories(noLevel.resolve("region"));
        AnvilFixture.writeRegionFile(noLevel.resolve("region").resolve("r.0.0.mca"), List.of());

        String row = api().anvilImport(noLevel);
        assertTrue(row.startsWith("Anvil import refused: "),
                "the loud refusal row: " + row);
        assertTrue(row.contains("no level.dat"), "the refusal names the missing file: " + row);

        // The gate is free: the next import walks and finishes.
        Path good = emptyWorld("after-refusal");
        String followUp = api().anvilImport(good);
        assertTrue(followUp.startsWith("Anvil import finished: "),
                "the gate released after the refusal (the never-throw repair): " + followUp);
    }

    /**
     * The one-import gate's concurrent answer — and the shared-gate proof:
     * the operator command's walk and the api verb ride the SAME gate (the
     * one authoritative route). The command starts the walk (its body runs
     * synchronously — the gate is held the moment it returns), and the
     * api's concurrent call answers the gate's own row IMMEDIATELY (never
     * queued, never racing the in-flight walk). The retry closes the
     * coalescing race honestly: a walk of small region files can finish
     * inside a single drain pass (~ms), and a round whose api call arrived
     * after that finish simply observed two completed walks — the next
     * round meets the gate (a miss costs one idempotent empty-world walk).
     * When the walk finishes the gate releases and the next import walks.
     */
    @Test
    void theOneImportGateRefusesASecondConcurrentCall() throws Exception {
        // The command's world: eighty region files — even a fully
        // coalesced walk spans ~ms of I/O phases, and the api call enters
        // microseconds after the command returns.
        Path big = dir.resolve("big");
        Map<String, Object> level = AnvilFixture.compound();
        level.put("RandomSeed", 9L);
        level.put("SpawnX", 0);
        level.put("SpawnY", 64);
        level.put("SpawnZ", 0);
        level.put("Time", 0L);
        level.put("DayTime", 0L);
        level.put("LevelName", "Big");
        AnvilFixture.writeLevelDat(big, level);
        List<AnvilFixture.Entry> noEntries = List.of();
        for (int region = 0; region < 80; region++) {
            AnvilFixture.writeRegionFile(
                    big.resolve("region").resolve("r." + region + ".0.mca"), noEntries);
        }
        Path good = emptyWorld("after-gate");

        String gateRow = "An Anvil import is already running; wait for its report";
        String apiAnswer = null;
        for (int round = 0; round < 8 && !gateRow.equals(apiAnswer); round++) {
            // The operator command's walk, started through the SYNCHRONOUS
            // console route (the RCON form — the command body runs on the
            // tick lane, this thread blocks for its captured reply): the
            // returned "started" row means the walk is submitted and the
            // gate is held.
            String started = engine.runRconCommand("anvilimport " + big);
            assertTrue(started.startsWith("Anvil import started: "),
                    "the command's walk started (round " + round + "): " + started);
            // The api's concurrent call: the gate's own row while the
            // walk holds it, or (the coalescing miss) a completed walk of
            // its own folder — retry the next round.
            apiAnswer = api().anvilImport(good);
        }
        assertEquals(gateRow, apiAnswer,
                "the api's concurrent call met the command's walk at the shared gate");

        // The gate releases when the in-flight walk finishes: the next api
        // import walks again (the loop absorbs the walk's own tail).
        String followUp = null;
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            followUp = api().anvilImport(good);
            if (followUp.startsWith("Anvil import finished: ")) {
                break;
            }
            Thread.sleep(5);
        }
        assertTrue(followUp != null && followUp.startsWith("Anvil import finished: "),
                "the gate released on completion: " + followUp);
    }

    /**
     * The api route's observability + the boot-gate row on a silent
     * engine: the walk's rows reach the engine log (a plugin-triggered
     * import stays operator-observable), surfacing the progress line the
     * operator channel carries.
     */
    @Test
    void theApiRoutesProgressRowsToTheEngineLog() throws Exception {
        List<String> logged = new CopyOnWriteArrayList<>();
        Handler capture = new Handler() {
            @Override
            public void publish(LogRecord record) {
                logged.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        Logger engineLogger = Logger.getLogger(EngineServer.class.getName());
        engineLogger.addHandler(capture);
        engineLogger.setLevel(Level.INFO);
        engineLogger.setUseParentHandlers(false);
        try {
            String row = api().anvilImport(goldMineWorld());
            assertTrue(row.startsWith("Anvil import finished: "), "the report: " + row);
            assertTrue(logged.stream().anyMatch(line ->
                            line.startsWith("Anvil import: level.dat applied, 3 region file(s) queued")),
                    "the progress row reached the engine log: " + logged);
            assertTrue(logged.stream().anyMatch(line ->
                            line.startsWith("Anvil import finished: ")),
                    "the report row reached the engine log: " + logged);
        } finally {
            engineLogger.removeHandler(capture);
        }
    }
}
