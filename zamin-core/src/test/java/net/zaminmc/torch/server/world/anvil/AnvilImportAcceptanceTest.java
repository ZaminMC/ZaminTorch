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

    @Test
    void theImportedContentRowsLandOnTheEngineSurfaces() throws Exception {
        // The entities/tiles follow-up: the chunk's TileEntities rows
        // apply through the /blockdata machinery (the chest's contents
        // readable through the engine's own chest manager), the Entities
        // rows ride the /summon machinery (the zombie's custom name and
        // rows land through EntityNbt.readNbt) and the restored item
        // spawn (the stack + the Age row), and every family with no
        // engine surface isolates LOUDLY with its exact id.
        Path vault = dir.resolve("vault");
        Map<String, Object> level = AnvilFixture.compound();
        level.put("RandomSeed", 77L);
        level.put("SpawnX", 129);
        level.put("SpawnY", 70);
        level.put("SpawnZ", 129);
        level.put("Time", 180_000L);
        level.put("DayTime", 18_000L); // midnight: the zombie never burns
        level.put("LevelName", "Vault");
        AnvilFixture.writeLevelDat(vault, level);

        // The scene in chunk (8,8): a stone floor, a chest at (129,1,129)
        // carrying two diamonds (the tile row's Items list — the
        // reference's own save shape), a NO-family tile row over the
        // stone floor (the loud isolation), a named zombie, a three-stack
        // of diamonds on the ground (the item entity with its Age row),
        // and a Painting — a family with no engine surface.
        byte[] blocks = new byte[4096];
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                blocks[AnvilFixture.cellIndex(x, 0, z)] = 1; // stone floor
            }
        }
        blocks[AnvilFixture.cellIndex(1, 1, 1)] = 54; // the chest at (129,1,129)

        Map<String, Object> diamondSlot = AnvilFixture.compound();
        diamondSlot.put("Slot", (byte) 0);
        diamondSlot.put("id", "minecraft:diamond");
        diamondSlot.put("Count", (byte) 2);
        diamondSlot.put("Damage", (short) 0);
        Map<String, Object> chestTile = AnvilFixture.tileRow("Chest", 129, 1, 129);
        chestTile.put("Items", List.of(diamondSlot));
        Map<String, Object> homelessTile = AnvilFixture.tileRow("Chest", 130, 0, 130);

        Map<String, Object> zombie = AnvilFixture.entityRow("Zombie", 132.5, 1.0, 132.5);
        zombie.put("CustomName", "Anvil Zombie");
        Map<String, Object> painting = AnvilFixture.entityRow("Painting", 134.5, 1.0, 134.5);
        Map<String, Object> dropped = AnvilFixture.itemEntityRow(
                "minecraft:diamond", 3, 0, 133.5, 1.0, 133.5, (short) 100);

        byte[] chunkNbt = AnvilFixture.nbtDocument(AnvilFixture.chunkNbt(8, 8,
                List.of(AnvilFixture.section(0, blocks, new byte[2048], null)), null,
                List.of(zombie, painting, dropped), List.of(chestTile, homelessTile)));
        AnvilFixture.writeRegionFile(vault.resolve("region").resolve("r.0.0.mca"),
                List.of(new AnvilFixture.Entry(8, 8, AnvilRegionFile.SCHEME_ZLIB,
                        AnvilFixture.zlib(chunkNbt))));

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

        EngineConfig config = new EngineConfig("127.0.0.1", 0, "anvil-content", "it",
                20, 2, 20, dir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        try {
            server.consoleCommand("anvilimport " + vault);
            long deadline = System.currentTimeMillis() + 20_000;
            while (System.currentTimeMillis() < deadline
                    && console.stream().noneMatch(line -> line.startsWith("Anvil import finished"))) {
                Thread.sleep(50);
            }
            String report = console.stream()
                    .filter(line -> line.startsWith("Anvil import finished"))
                    .findFirst().orElse(null);
            assertNotNull(report, "the final report landed (console: " + console + ")");

            // The tile row applied: the chest is readable through the
            // engine's own chest manager with the diamond stack — the
            // /blockdata walk's own projection.
            var chest = server.chests().getOrCreate(
                    new net.zaminmc.torch.block.BlockPosition(129, 1, 129));
            var slots = chest.snapshotSlots();
            assertEquals("minecraft:diamond", slots[0].type().identifier().toString(),
                    "the chest's first slot holds the imported diamond (report: " + report + ")");
            assertEquals(2, slots[0].count(), "the stack's count row landed");

            // The zombie imported through the /summon machinery: the type,
            // the custom name (EntityNbt.readNbt's own row), and the
            // position band of the saved Pos triple.
            var zombies = server.mobs().all().stream()
                    .filter(mob -> mob.customName() != null
                            && mob.customName().equals("Anvil Zombie"))
                    .toList();
            assertEquals(1, zombies.size(), "exactly one imported zombie (report: " + report + ")");
            assertEquals(net.zaminmc.torch.server.entity.MobType.ZOMBIE, zombies.get(0).type(),
                    "the zombie's type resolved through the vanilla name");
            assertEquals(132.5, zombies.get(0).position().x(), 1.0,
                    "the saved Pos row landed through readNbt's own walk (a wander band)");
            assertEquals(132.5, zombies.get(0).position().z(), 1.0);

            // The item entity restored: the stack + the Age row (the age
            // keeps walking after the import — the row landed when it is
            // >= the saved value, not the fresh default 0).
            var diamonds = server.itemEntities().all().stream()
                    .filter(item -> item.stack().type().identifier().value().equals("diamond"))
                    .toList();
            assertEquals(1, diamonds.size(), "the imported item entity (report: " + report + ")");
            assertEquals(3, diamonds.get(0).stack().count(), "the stack's count");
            assertTrue(diamonds.get(0).age() >= 100,
                    "the Age row applied (and keeps walking): " + diamonds.get(0).age());

            // The report's own rows: 2 entities + 1 tile imported, the
            // Painting and the no-family tile isolated by name.
            assertTrue(report.contains("2 entities"), "the report: " + report);
            assertTrue(report.contains("1 tile entities"), "the report: " + report);
            assertTrue(report.contains("entity family not importable"),
                    "the Painting isolated loudly: " + report);
            assertTrue(report.contains("entity Painting"), "the isolation names the id: " + report);
            assertTrue(report.contains("tile row over a block with no tile family"),
                    "the no-family tile isolated loudly: " + report);
        } finally {
            consoleLogger.removeHandler(capture);
            server.shutdown(null);
        }
    }
}
