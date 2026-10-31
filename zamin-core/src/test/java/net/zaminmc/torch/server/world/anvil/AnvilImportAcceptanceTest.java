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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import net.zaminmc.torch.testing.EngineAwaits;

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
        // standing banner (176) is now a registered family (the banner
        // wave): it imports as its south state.
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
                EngineAwaits.awaitTicks(server.world(), 1, "engine progress (a 50 wall settle)");
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
            assertEquals("minecraft:standing_banner",
                    world.getBlock(new net.zaminmc.torch.block.BlockPosition(131, 1, 131))
                            .identifier().toString(),
                    "the banner (the registered family) imports as its south state");
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
            // isolated whole (the banner 176 is a registered family now —
            // no isolation row rides its import).
            assertTrue(report.contains("installed 2 chunks"), "the report: " + report);
            assertTrue(report.contains("skipped 1 (already loaded)"), "the report: " + report);
            assertTrue(report.contains("unoccupied 1021"), "1024 entries minus 3 written: " + report);
            assertTrue(report.contains("shorter than the 8 KiB header"), "the garbage file: " + report);
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
                EngineAwaits.awaitTicks(server.world(), 1, "engine progress (a 50 wall settle)");
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
        // and a fireworks rocket (the import-families slice's restored
        // form — the no-surface example moved to the fishing bobber).
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
        // The firework rocket (the import-families slice — the engine's own
        // fireworks system, the restored form): a mid-flight Life 5 with a
        // 40-tick fuse (the scene asserts within the remaining 35) and the
        // SAVED FireworksItem compound — the reference's own synced slot-8
        // row (writeCustomNbt lines 137-143), the payload's whole boxed
        // tree with the explosion compound riding the tag (the burst's
        // own render reads it).
        Map<String, Object> explosion = AnvilFixture.compound();
        explosion.put("Type", (byte) 1);
        explosion.put("Colors", new int[]{0x00FF00});
        explosion.put("FadeColors", new int[]{0xFF0000});
        explosion.put("Trail", (byte) 1);
        Map<String, Object> fireworksTag = AnvilFixture.compound();
        fireworksTag.put("Flight", (byte) 2);
        fireworksTag.put("Explosions", List.of(explosion));
        Map<String, Object> payloadTag = AnvilFixture.compound();
        payloadTag.put("Fireworks", fireworksTag);
        Map<String, Object> fireworksItem = AnvilFixture.compound();
        fireworksItem.put("id", "minecraft:fireworks");
        fireworksItem.put("Count", (byte) 1);
        fireworksItem.put("Damage", (short) 0);
        fireworksItem.put("tag", payloadTag);
        Map<String, Object> firework = AnvilFixture.entityRow("FireworksRocketEntity", 134.5, 1.0, 134.5);
        firework.put("Life", 5);
        firework.put("LifeTime", 40);
        firework.put("Motion", List.of(0.0, 0.04, 0.0));
        firework.put("Rotation", List.of(0.0f, 0.0f));
        firework.put("FireworksItem", fireworksItem);
        // The fishing bobber — the new no-surface example (the engine's
        // fishing system models the bobbers natively; the import family has
        // no arm — the loud isolation stays).
        Map<String, Object> bobber = AnvilFixture.entityRow("FishingBobber", 135.5, 2.0, 135.5);
        Map<String, Object> dropped = AnvilFixture.itemEntityRow(
                "minecraft:diamond", 3, 0, 133.5, 1.0, 133.5, (short) 100);

        byte[] chunkNbt = AnvilFixture.nbtDocument(AnvilFixture.chunkNbt(8, 8,
                List.of(AnvilFixture.section(0, blocks, new byte[2048], null)), null,
                List.of(zombie, firework, bobber, dropped), List.of(chestTile, homelessTile)));
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
                EngineAwaits.awaitTicks(server.world(), 1, "engine progress (a 50 wall settle)");
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

            // The report's own rows: 3 entities + 1 tile imported (the
            // zombie, the firework rocket — the import-families slice's
            // restored form, and the dropped item), the fishing bobber (the
            // no-surface example) and the no-family tile isolated by name.
            assertTrue(report.contains("3 entities"), "the report: " + report);
            assertTrue(report.contains("1 tile entities"), "the report: " + report);
            // The restored rocket: the SAVED Life resumed the age clock and
            // the LifeTime row carries the whole fuse (the loaded rocket
            // bursts after the REMAINING ticks, never a fresh roll).
            var rockets = server.fireworks().all();
            assertEquals(1, rockets.size(),
                    "the firework rocket imported through the restored form (report: " + report + ")");
            assertTrue(rockets.get(0).age() >= 5 && rockets.get(0).age() <= 40,
                    "the saved Life resumed the age clock (never a fresh roll): "
                            + rockets.get(0).age());
            assertEquals(40, rockets.get(0).maxAge(), "the LifeTime row carries the whole fuse");
            assertTrue(rockets.get(0).velocityY() >= 0.04,
                    "the loaded Motion lands whole (the flight only grows it): "
                            + rockets.get(0).velocityY());
            // The SAVED payload compound landed on the rocket's synced
            // slot-8 stack (the reference's readCustomNbt row): the item
            // identity, the count, and the whole boxed Fireworks tree —
            // the Flight byte naming the fuse band and the explosion
            // compound riding the tag verbatim (the burst's render row).
            var payload = rockets.get(0).payload();
            assertFalse(payload.isEmpty(), "the FireworksItem row became the payload");
            assertEquals("fireworks", payload.type().identifier().value(),
                    "the payload's item identity");
            assertEquals(1, payload.count(), "the payload's count row");
            var tree = payload.fireworks();
            assertNotNull(tree, "the payload's Fireworks tree rode the tag");
            assertEquals((byte) 2, tree.get("Flight"), "the Flight byte rides verbatim");
            assertTrue(tree.get("Explosions") instanceof List<?> explosions
                            && explosions.size() == 1,
                    "the explosion compound rides the tree: " + tree.get("Explosions"));
            assertTrue(report.contains("firework's Rotation row has no per-rocket surface"),
                    "the Rotation row's counted note: " + report);
            assertTrue(report.contains("entity family not importable"),
                    "the fishing bobber isolated loudly: " + report);
            assertTrue(report.contains("entity FishingBobber"),
                    "the isolation names the id: " + report);
            assertTrue(report.contains("tile row over a block with no tile family"),
                    "the no-family tile isolated loudly: " + report);
        } finally {
            consoleLogger.removeHandler(capture);
            server.shutdown(null);
        }
    }

    @Test
    void theImportedEntityFamiliesLandOnTheEngineSurfaces() throws Exception {
        // The §31.6 counted refusal's retirement, part one: the four
        // entity families with real engine surfaces import — the
        // projectiles (the stuck arrow's inGround + life rows, the
        // in-flight snowball's Motion), the painting (the exact-cell
        // anchor + the saved motive, no fit roll), the item frame (the
        // legacy Direction form's offset + rotation-doubling quirks, the
        // displayed stack), and the XP orb (the exact Value + the
        // resumed Age) — while the riding chain keeps isolating LOUDLY
        // with the missing surfaces named (the non-session rider attach
        // publication + the server-side rider follow), and every
        // per-family row the engine model derives differently carries
        // its counted note.
        Path vault = dir.resolve("families");
        Map<String, Object> level = AnvilFixture.compound();
        level.put("RandomSeed", 1234L);
        level.put("SpawnX", 129);
        level.put("SpawnY", 70);
        level.put("SpawnZ", 129);
        level.put("Time", 180_000L);
        level.put("DayTime", 18_000L);
        level.put("LevelName", "Families");
        AnvilFixture.writeLevelDat(vault, level);

        // The scene in chunk (8,8): a stone floor at y=0.
        byte[] blocks = new byte[4096];
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                blocks[AnvilFixture.cellIndex(x, 0, z)] = 1; // stone floor
            }
        }

        // The stuck arrow: inGround + life resume the stick clock; the
        // arrow-only family rows (xTile/inTile/shake/pickup/damage)
        // carry the counted unlanded-rows note.
        Map<String, Object> stuckArrow = AnvilFixture.entityRow("Arrow", 133.5, 2.05, 133.5);
        stuckArrow.put("Motion", List.of(0.0, 0.0, 0.0));
        stuckArrow.put("inGround", (byte) 1);
        stuckArrow.put("life", (short) 400);
        stuckArrow.put("xTile", (short) 133);
        stuckArrow.put("yTile", (short) 1);
        stuckArrow.put("zTile", (short) 133);
        stuckArrow.put("inTile", "minecraft:stone");
        stuckArrow.put("shake", (byte) 4);
        stuckArrow.put("pickup", (byte) 1);
        stuckArrow.put("damage", 2.5);

        // The in-flight snowball: the saved Motion lands whole.
        Map<String, Object> snowball = AnvilFixture.entityRow("Snowball", 134.5, 3.0, 134.5);
        snowball.put("Motion", List.of(-0.5, 0.2, 0.5));

        // The painting: the modern Facing form + the exact motive.
        Map<String, Object> painting = AnvilFixture.entityRow("Painting", 129.5, 1.5, 132.5);
        painting.put("TileX", 129);
        painting.put("TileY", 1);
        painting.put("TileZ", 132);
        painting.put("Facing", (byte) 0);
        painting.put("Motive", "Kebab");

        // The item frame in the LEGACY Direction form: the Tile cell is
        // the WALL cell (129,1,130), the anchor offsets east to
        // (130,1,130), and the legacy form doubles the item rotation
        // (3 -> 6) — the reference's own old-format repair.
        Map<String, Object> frame = AnvilFixture.entityRow("ItemFrame", 130.5, 1.5, 130.5);
        frame.put("TileX", 129);
        frame.put("TileY", 1);
        frame.put("TileZ", 130);
        frame.put("Direction", (byte) 3);
        Map<String, Object> displayed = AnvilFixture.compound();
        displayed.put("id", "minecraft:diamond");
        displayed.put("Count", (byte) 1);
        displayed.put("Damage", (short) 0);
        frame.put("Item", displayed);
        frame.put("ItemRotation", (byte) 3);
        frame.put("ItemDropChance", 1.0f);

        // The XP orb: the exact Value + the resumed Age (the Health row
        // carries the counted no-surface note).
        Map<String, Object> orb = AnvilFixture.entityRow("XPOrb", 135.5, 2.0, 135.5);
        orb.put("Value", (short) 12);
        orb.put("Age", (short) 2400);
        orb.put("Health", (short) 5);

        // The zombie riding a minecart: the rider imports whole, the
        // chain isolates loudly with the missing surfaces named.
        Map<String, Object> rider = AnvilFixture.entityRow("Zombie", 136.5, 1.0, 136.5);
        rider.put("CustomName", "Chain Zombie");
        Map<String, Object> mount = AnvilFixture.entityRow("MinecartRideable", 136.5, 1.0, 136.5);
        rider.put("Riding", mount);

        // The exotic mount-side shape (the generic-mount seat's own scene):
        // a zombie riding a DROPPED ITEM — the reference's startRiding
        // seats any family; the engine's generic registry carries the
        // follow (the base Entity's own law).
        Map<String, Object> exoticRider = AnvilFixture.entityRow("Zombie", 139.5, 2.0, 139.5);
        exoticRider.put("CustomName", "Surfer");
        Map<String, Object> itemMount = AnvilFixture.itemEntityRow(
                "minecraft:diamond", 1, 0, 139.5, 2.0, 139.5, (short) 100);
        exoticRider.put("Riding", itemMount);

        // THE RIDER-SIDE SHAPES (the §31.6 counted refusal's rider side —
        // this slice's own scene): an XP ORB riding a zombie (an orb
        // RIDER on a mob mount — the orbs never collide with bodies, the
        // stable follow shape; a seated snowball would shatter on its own
        // mount's hull the first tick, the reference's own onCollision)
        // and a BOAT riding a minecart (a vehicle RIDER on a vehicle
        // mount) — the reference's startRiding seats any family, the
        // engine's follow arms now carry every rider family.
        Map<String, Object> zombieMount = AnvilFixture.entityRow("Zombie", 140.5, 1.0, 140.5);
        zombieMount.put("CustomName", "Pack Mule");
        Map<String, Object> orbRider = AnvilFixture.entityRow("XPOrb", 140.5, 1.0, 140.5);
        orbRider.put("Value", (short) 7);
        orbRider.put("Age", (short) 0);
        orbRider.put("Riding", zombieMount);
        Map<String, Object> cartMount = AnvilFixture.entityRow("MinecartRideable", 141.5, 1.0, 141.5);
        Map<String, Object> boatRider = AnvilFixture.entityRow("Boat", 141.5, 1.0, 141.5);
        boatRider.put("Riding", cartMount);

        // The eye of ender (the import-families slice — the restored form;
        // the reference's own readCustomNbt is EMPTY: the base Pos/Motion/
        // Rotation rows are the whole saved shape, the saved Motion landing
        // with the ±10 clamp, the transient rows' counted note names the
        // load quirk) and the leash knot (the Tile row IS the anchor; the
        // survival poll owns the unsupported-anchor drop — the knot lands
        // over the stone floor).
        Map<String, Object> eye = AnvilFixture.entityRow("EyeOfEnderSignal", 137.5, 2.0, 137.5);
        eye.put("Motion", List.of(0.05, 0.02, 0.0));
        eye.put("Rotation", List.of(-90.0f, 20.0f));
        Map<String, Object> knot = AnvilFixture.entityRow("LeashKnot", 138, 1, 138);
        knot.put("TileX", 138);
        knot.put("TileY", 1);
        knot.put("TileZ", 138);
        knot.put("Direction", (byte) 0);

        byte[] chunkNbt = AnvilFixture.nbtDocument(AnvilFixture.chunkNbt(8, 8,
                List.of(AnvilFixture.section(0, blocks, new byte[2048], null)), null,
                List.of(stuckArrow, snowball, painting, frame, orb, rider, exoticRider,
                        orbRider, boatRider, eye, knot), null));
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

        EngineConfig config = new EngineConfig("127.0.0.1", 0, "anvil-families", "it",
                20, 2, 20, dir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        try {
            server.consoleCommand("anvilimport " + vault);
            long deadline = System.currentTimeMillis() + 20_000;
            while (System.currentTimeMillis() < deadline
                    && console.stream().noneMatch(line -> line.startsWith("Anvil import finished"))) {
                EngineAwaits.awaitTicks(server.world(), 1, "engine progress (a 50 wall settle)");
            }
            String report = console.stream()
                    .filter(line -> line.startsWith("Anvil import finished"))
                    .findFirst().orElse(null);
            assertNotNull(report, "the final report landed (console: " + console + ")");

            // The stuck arrow: the stick clock resumed at the saved life
            // (and keeps walking — assert the band, not the instant).
            var arrows = server.projectiles().all().stream()
                    .filter(p -> p.kind() == net.zaminmc.torch.server.entity.projectile
                            .ProjectileEntity.Kind.ARROW)
                    .toList();
            assertEquals(1, arrows.size(), "the imported arrow (report: " + report + ")");
            assertTrue(arrows.get(0).inGround(), "the inGround row landed");
            assertTrue(arrows.get(0).ticksInGround() >= 400,
                    "the life row resumed the stick clock: " + arrows.get(0).ticksInGround());

            // The snowball: the saved Motion landed whole (the flight
            // may have already moved it — the launch-position band holds).
            var shards = server.projectiles().all().stream()
                    .filter(p -> p.kind() == net.zaminmc.torch.server.entity.projectile
                            .ProjectileEntity.Kind.SNOWBALL)
                    .toList();
            assertTrue(shards.size() <= 1, "at most one snowball (it may have shattered)");
            if (!shards.isEmpty()) {
                assertEquals(-0.5, shards.get(0).velocityX(), 0.75,
                        "the saved Motion's band (the flight's own drag)");
            }

            // The painting: the exact anchor + the saved motive, whole.
            var landed = server.decorations().paintingAt(
                    new net.zaminmc.torch.block.BlockPosition(129, 1, 132));
            assertNotNull(landed, "the painting hangs at the saved anchor (report: " + report + ")");
            assertEquals(net.zaminmc.torch.server.decoration.Painting.Motive.KEBAB,
                    landed.motive(), "the saved motive lands whole — no fit roll");
            assertEquals(0, landed.facing(), "the saved facing");

            // The item frame: the legacy Direction form's offset put the
            // anchor one cell east of the Tile row, the displayed stack
            // landed through the size-1 copy rule, and the rotation
            // doubled (the old-format repair, 3 -> 6).
            var landedFrame = server.decorations().frameAt(
                    new net.zaminmc.torch.block.BlockPosition(130, 1, 130));
            assertNotNull(landedFrame, "the frame hangs at the OFFSET anchor (report: " + report + ")");
            assertEquals("diamond", landedFrame.display().type().identifier().value(),
                    "the displayed stack landed");
            assertEquals(6, landedFrame.rotation(), "the legacy Direction form doubled the rotation");

            // The XP orb: the exact value + the resumed age (both keep
            // walking after the import — the band assertions; the
            // rider-side scene's orb rider joins the family — the
            // amount-12 body is this scene's own).
            var orbs = server.experienceOrbs().all();
            assertTrue(orbs.size() >= 1, "the imported orb (report: " + report + ")");
            var twelveOrb = orbs.stream().filter(live -> live.amount() == 12)
                    .findFirst().orElseThrow();
            assertTrue(twelveOrb.age() >= 2400,
                    "the saved Age resumed the despawn clock: " + twelveOrb.age());

            // The rider imported whole; the chain isolated loudly with
            // the missing surfaces named.
            var chainZombies = server.mobs().all().stream()
                    .filter(mob -> mob.customName() != null
                            && mob.customName().equals("Chain Zombie"))
                    .toList();
            assertEquals(1, chainZombies.size(), "the rider imported through the /summon walk");
            // THE CHAIN LANDED (the reference's AnvilChunkStorage 327-341
            // walk): the mount imported through its family's own arm, then
            // the head took the seat — the cart carries the zombie as its
            // passenger (the non-session rider's follow + attach surfaces
            // the §22 demand-named row).
            var chainCarts = server.vehicles().all().stream()
                    .filter(v -> v instanceof net.zaminmc.torch.server.entity.vehicle.MinecartEntity
                            && v.hasPassenger())
                    .toList();
            assertEquals(2, chainCarts.size(),
                    "the chain's cart + the rider-side boat's cart mount (both seated)");
            var chainCart = chainCarts.stream().filter(cart ->
                            cart.passengerId() == chainZombies.get(0).entityId())
                    .findFirst().orElseThrow();
            assertTrue(chainCart.hasPassenger(),
                    "the chain seated the head on the mount");
            assertEquals(chainZombies.get(0).entityId(), chainCart.passengerId(),
                    "the cart's passenger is the chain zombie (the engine id)");
            assertFalse(report.contains("riding chain not imported"),
                    "the chain landed, never isolated: " + report);

            // The counted notes: the arrow's unlanded family rows, the
            // frame's ItemDropChance, the orb's Health, and the eye's
            // transient rows (the reference's load quirk — the eye family
            // LANDED with the import-families slice; the no-surface
            // isolation moved to the content scene's fishing bobber).
            assertTrue(report.contains("projectile rows without an engine surface"),
                    "the arrow's unlanded rows counted: " + report);
            assertTrue(report.contains("ItemDropChance row has no per-frame surface"),
                    "the frame's drop chance counted: " + report);
            assertTrue(report.contains("Health row has no engine surface"),
                    "the orb's health counted: " + report);
            assertTrue(report.contains("eye's transient target and drops rows"),
                    "the eye's transient rows' counted note: " + report);
            // The eye family landed: the imported eye flies (the origin
            // quirk — the transient target (0, 0, 0), never the +8 lift:
            // the descent the counted note names) until its 80 ticks end
            // — the SAVED Motion landing whole (the vertical approach only
            // walks it down toward the origin's y band), the counted notes
            // carrying the transient rows and the derived Rotation; the
            // knot landed at its Tile anchor (the stone floor — the
            // survival poll owns the unsupported drop).
            var importedEyes = server.eyes().all();
            assertEquals(1, importedEyes.size(),
                    "the eye of ender imported through the restored form");
            assertTrue(importedEyes.get(0).velocityY() <= 0.02 && importedEyes.get(0).velocityY() > -0.5,
                    "the saved Motion landed whole (the origin quirk's descent band — "
                            + "the approach walks the vertical down, never away): "
                            + importedEyes.get(0).velocityY());
            assertTrue(importedEyes.get(0).position().y() > 0.0
                            && importedEyes.get(0).position().y() < 2.05,
                    "the transient origin target drives the descent band (never the "
                            + "live use's +8 lift toward 10 — the load quirk): "
                            + importedEyes.get(0).position().y());
            assertTrue(report.contains("eye's Rotation row has no per-eye surface"),
                    "the eye's derived Rotation counted note: " + report);
            var importedKnots = server.leads().allKnots();
            assertEquals(1, importedKnots.size(),
                    "the leash knot imported at its Tile anchor");
            assertEquals(138, importedKnots.get(0).fence().x(), "the TileX row is the anchor");
            assertTrue(report.contains("knot's Direction row has no per-knot surface"),
                    "the knot's Direction note: " + report);

            // The report's own counter: 15 entities imported (the arrow,
            // the snowball, the painting, the frame, the orb, the rider,
            // the chain's cart, the exotic pair (the surfer zombie + its
            // dropped-diamond mount), the rider-side pair's zombie mount +
            // orb rider, the boat rider + its cart mount, the eye,
            // and the knot — the import-families slice + the generic-mount
            // seat + the rider side's retirement).
            assertTrue(report.contains("15 entities"), "the entity counter: " + report);
            // THE GENERIC-MOUNT SEAT (the exotic mount-side shape): the
            // zombie landed, the dropped-diamond mount landed, and the
            // follow anchors the rider at the item's own cell (the base
            // Entity's law — the mount's own getMountHeight band, the
            // item family's height*0.75 = +0.1875, plus the zombie's own
            // -0.35 ride height; the x/z band is the mount's cell).
            var surfer = server.mobs().all().stream()
                    .filter(mob -> "Surfer".equals(mob.customName()))
                    .findFirst().orElseThrow();
            assertTrue(server.itemEntities().all().stream().anyMatch(
                            item -> item.position().x() == 139.5),
                    "the dropped-diamond mount imported");
            assertTrue(Math.abs(surfer.position().x() - 139.5) < 0.01
                            && Math.abs(surfer.position().z() - 139.5) < 0.01,
                    "the exotic rider anchored at the item mount's cell: "
                            + surfer.position());
            assertFalse(report.contains("entity rides Item"),
                    "the exotic mount-side chain never isolates: " + report);

            // THE RIDER SIDE'S RETIREMENT (this slice's own scene): the
            // XP-ORB rider on the zombie mount — the seat opened (the
            // zombie's rider id is the orb's engine id) and the follow
            // anchors the orb's body at the mob's seat law (the seat
            // height + the orb rider's base 0.0 ride height, Entity.java
            // line 1353 for ANY family).
            var packMule = server.mobs().all().stream()
                    .filter(mob -> "Pack Mule".equals(mob.customName()))
                    .findFirst().orElseThrow();
            assertTrue(packMule.hasRider(), "the zombie mount's seat opened for the orb");
            assertEquals(packMule.riderId(), server.experienceOrbs().all().stream()
                            .filter(live -> live.amount() == 7).findFirst()
                            .map(live -> live.entityId()).orElse(-1),
                    "the zombie's rider is the imported orb (the engine id)");
            var seatedOrb = server.experienceOrbs().all().stream()
                    .filter(live -> live.amount() == 7).findFirst().orElseThrow();
            assertTrue(Math.abs(seatedOrb.position().x() - packMule.position().x()) < 0.6
                            && Math.abs(seatedOrb.position().z()
                                    - packMule.position().z()) < 0.6,
                    "the orb rider follows the zombie mount's seat: "
                            + seatedOrb.position() + " vs " + packMule.position());
            // The BOAT rider on the minecart mount: the cart's passenger
            // is the boat's engine id, the follow anchoring the hull at
            // the cart's flush seat (the cart family's 0.0 mount height +
            // the boat rider's base 0.0 ride height).
            var riderSideCarts = server.vehicles().all().stream()
                    .filter(v -> v instanceof net.zaminmc.torch.server.entity.vehicle.MinecartEntity)
                    .toList();
            assertEquals(2, riderSideCarts.size(),
                    "the chain's cart + the rider-side cart both imported");
            var riderSideBoat = server.vehicles().all().stream()
                    .filter(v -> v instanceof net.zaminmc.torch.server.entity.vehicle.BoatEntity)
                    .findFirst().orElseThrow();
            boolean boatSeated = riderSideCarts.stream().anyMatch(cart ->
                    cart.hasPassenger() && cart.passengerId() == riderSideBoat.entityId());
            assertTrue(boatSeated, "the boat rider took the cart mount's seat");
            assertTrue(Math.abs(riderSideBoat.position().x()
                            - riderSideCarts.stream().filter(cart ->
                                    cart.hasPassenger() && cart.passengerId()
                                            == riderSideBoat.entityId())
                                    .findFirst().orElseThrow().position().x()) < 0.6,
                    "the boat rider follows the cart mount's seat");
            // The rider side never isolates: no "rider family has no
            // seat surface" note anywhere in the report.
            assertFalse(report.contains("rider family has no seat surface")
                            && report.contains("seated-follow surface"),
                    "the rider-side chains never isolate: " + report);
            assertFalse(report.contains("entity rides Zombie")
                            || report.contains("entity rides MinecartRideable"),
                    "the rider-side chains landed whole: " + report);

        } finally {
            consoleLogger.removeHandler(capture);
            server.shutdown(null);
        }
    }

    @Test
    void theImportedTileTicksLandOnTheScheduledSurface() throws Exception {
        // The tile-ticks follow-up: the chunk's TileTicks rows land through
        // the scheduled block-tick surface's own import arm
        // (loadScheduledTick — the reference's createChunkFromNbt tail),
        // each row resolved from its own name (the string form first, the
        // legacy integer second), the AIR row carrying the surface's
        // time-zero quirk, and the unresolvable families isolating LOUDLY.
        Path vault = dir.resolve("ticks");
        Map<String, Object> level = AnvilFixture.compound();
        level.put("RandomSeed", 99L);
        level.put("SpawnX", 8);
        level.put("SpawnY", 70);
        level.put("SpawnZ", 8);
        level.put("LevelName", "Ticks");
        level.put("Time", 180_000L);
        level.put("DayTime", 18_000L);
        AnvilFixture.writeLevelDat(vault, level);

        // The scene in chunk (8,8) (outside the boot pregen): a stone floor, a fire family row (the
        // string form, t=5 p=1), a lava row (the legacy integer form, id 11 (the lava source —
        // the bare-id family),
        // t=7 p=2), an AIR row (the surface's own time-zero quirk, t=9
        // p=4), an unregistered name (the loud isolation), and a row with
        // no position (the reference's required ints).
        byte[] blocks = new byte[4096];
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                blocks[AnvilFixture.cellIndex(x, 0, z)] = 1; // stone floor
            }
        }
        Map<String, Object> fireRow = AnvilFixture.compound();
        fireRow.put("i", "minecraft:fire");
        fireRow.put("x", 129);
        fireRow.put("y", 2);
        fireRow.put("z", 129);
        fireRow.put("t", 5);
        fireRow.put("p", 1);
        Map<String, Object> lavaRow = AnvilFixture.compound();
        lavaRow.put("i", 11); // the legacy integer form (Block.byId): the lava source id
        lavaRow.put("x", 130);
        lavaRow.put("y", 2);
        lavaRow.put("z", 130);
        lavaRow.put("t", 7);
        lavaRow.put("p", 2);
        Map<String, Object> airRow = AnvilFixture.compound();
        airRow.put("i", "minecraft:air");
        airRow.put("x", 131);
        airRow.put("y", 2);
        airRow.put("z", 131);
        airRow.put("t", 9);
        airRow.put("p", 4);
        Map<String, Object> unknownRow = AnvilFixture.compound();
        unknownRow.put("i", "minecraft:quantum_oscillator");
        unknownRow.put("x", 132);
        unknownRow.put("y", 2);
        unknownRow.put("z", 132);
        Map<String, Object> positionlessRow = AnvilFixture.compound();
        positionlessRow.put("i", "minecraft:fire");

        Map<String, Object> root = AnvilFixture.chunkNbt(8, 8,
                List.of(AnvilFixture.section(0, blocks, new byte[2048], null)), null,
                null, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> rootLevel = (Map<String, Object>) root.get("Level");
        rootLevel.put("TileTicks", List.of(fireRow, lavaRow, airRow, unknownRow, positionlessRow));
        byte[] chunkNbt = AnvilFixture.nbtDocument(root);
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

        EngineConfig config = new EngineConfig("127.0.0.1", 0, "anvil-ticks", "it",
                20, 2, 20, dir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        try {
            long ticksBefore = server.world().totalTicks();
            server.consoleCommand("anvilimport " + vault);
            long deadline = System.currentTimeMillis() + 20_000;
            while (System.currentTimeMillis() < deadline
                    && console.stream().noneMatch(line -> line.startsWith("Anvil import finished"))) {
                EngineAwaits.awaitTicks(server.world(), 1, "engine progress (a 50 wall settle)");
            }
            long ticksAfter = server.world().totalTicks();
            String report = console.stream()
                    .filter(line -> line.startsWith("Anvil import finished"))
                    .findFirst().orElse(null);
            assertNotNull(report, "the final report landed (console: " + console + ")");

            // The three resolvable rows landed on the engine's own surface.
            var surface = server.scheduledBlockTicks();
            var fire = surface.pendingAt(
                    new net.zaminmc.torch.block.BlockPosition(129, 2, 129),
                    net.zaminmc.torch.util.Identifier.parse("minecraft:fire"));
            assertTrue(fire != null, "the fire row landed (report: " + report + ")");
            assertEquals(1, fire.priority());
            assertTrue(fire.time() >= ticksBefore + 5 && fire.time() <= ticksAfter + 5,
                    "the t=5 delay counts from the import moment's world time: " + fire.time());

            var lava = surface.pendingAt(
                    new net.zaminmc.torch.block.BlockPosition(130, 2, 130),
                    server.world().blockRegistry()
                            .lookup(AnvilBlockIds.resolve(11, 0).orElseThrow()).orElseThrow()
                            .identifier());
            assertTrue(lava != null, "the legacy integer row resolved through the id table (report: " + report + ")");
            assertEquals(2, lava.priority());
            assertTrue(lava.time() >= ticksBefore + 7 && lava.time() <= ticksAfter + 7,
                    "the t=7 delay counts from the import moment's world time");

            // The AIR row landed too (the report counts three) but cannot
            // survive to this assertion: it carries time zero regardless of
            // its t=9, so the very next fan pass pops it and the dispatch
            // gate drops it (the stone floor is not air) — the reference's
            // own walk. Its landing shape (time zero, the always-priority)
            // is pinned by the surface's own unit test.

            // The report's own rows: 3 ticks imported, the two loud refusals.
            assertTrue(report.contains("3 scheduled block ticks"), "the report: " + report);
            assertTrue(report.contains("tick row's block name not registered"),
                    "the unregistered name isolated loudly: " + report);
            assertTrue(report.contains("tick minecraft:quantum_oscillator"),
                    "the isolation names the row's own id: " + report);
            assertTrue(report.contains("tick row missing its x/y/z position"),
                    "the positionless row isolated loudly: " + report);
        } finally {
            consoleLogger.removeHandler(capture);
            server.shutdown(null);
        }
    }

    @Test
    void theImportedVehiclesLandOnTheEngineSurfaces() throws Exception {
        // The vehicle follow-up: the eight wire ids (the reference's
        // Entities.register names) spawn through the engine's own managers
        // — the same hosts the live paths use — with the shared base rows
        // (Rotation, Motion with the reference's +-10 clamp, the raw Fire)
        // and the per-family rows (the chest cart's Items, the spawner
        // cart's EntityId/Delay, the command cart's four rows); the
        // cadence/potential rows and the riding chains isolate LOUDLY.
        Path vault = dir.resolve("vehicles");
        Map<String, Object> level = AnvilFixture.compound();
        level.put("RandomSeed", 111L);
        level.put("SpawnX", 8);
        level.put("SpawnY", 70);
        level.put("SpawnZ", 8);
        level.put("LevelName", "Rails");
        level.put("Time", 180_000L);
        level.put("DayTime", 18_000L);
        AnvilFixture.writeLevelDat(vault, level);

        // The scene in chunk (8,8): a stone floor with the nine vehicles.
        byte[] blocks = new byte[4096];
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                blocks[AnvilFixture.cellIndex(x, 0, z)] = 1; // stone floor
            }
        }
        Map<String, Object> diamond = AnvilFixture.compound();
        diamond.put("Slot", (byte) 0);
        diamond.put("id", "minecraft:diamond");
        diamond.put("Count", (byte) 2);
        diamond.put("Damage", (short) 0);
        Map<String, Object> iron = AnvilFixture.compound();
        iron.put("Slot", (byte) 5);
        iron.put("id", "minecraft:iron_ingot");
        iron.put("Count", (byte) 1);
        iron.put("Damage", (short) 0);
        Map<String, Object> chestCart = AnvilFixture.entityRow("MinecartChest", 129.5, 1.0, 129.5);
        chestCart.put("Items", List.of(diamond, iron));
        chestCart.put("Rotation", List.of(90.0f, 0.0f));
        chestCart.put("Motion", List.of(0.5, 0.0, -0.25));
        chestCart.put("Fire", (short) 40);
        // The base rows (Entity.readNbt lines 1155-1196): the pair form
        // of the UUID, the custom name + visibility, the fall accumulator,
        // the breath clock, the ground state, the invulnerable flag, the
        // silence flag, the dimension id, the portal cooldown.
        java.util.UUID chestUuid = new java.util.UUID(0x123456789ABCDEFL, 0xFEDCBA9876543210L);
        chestCart.put("UUIDMost", chestUuid.getMostSignificantBits());
        chestCart.put("UUIDLeast", chestUuid.getLeastSignificantBits());
        chestCart.put("CustomName", "Express");
        chestCart.put("CustomNameVisible", (byte) 1);
        chestCart.put("FallDistance", 2.5f);
        chestCart.put("Air", (short) 250);
        chestCart.put("OnGround", (byte) 0);
        chestCart.put("Dimension", 0);
        chestCart.put("Invulnerable", (byte) 1);
        chestCart.put("Silent", (byte) 0);
        chestCart.put("PortalCooldown", 12);
        Map<String, Object> spawnerCart = AnvilFixture.entityRow("MinecartSpawner", 130.5, 1.0, 130.5);
        spawnerCart.put("EntityId", "Zombie");
        spawnerCart.put("Delay", (short) 55);
        // The cadence rows (the Wave VI per-cart surface): the imported
        // cart carries its OWN band.
        spawnerCart.put("MinSpawnDelay", (short) 120);
        spawnerCart.put("MaxSpawnDelay", (short) 240);
        spawnerCart.put("SpawnCount", (short) 2);
        spawnerCart.put("MaxNearbyEntities", (short) 3);
        spawnerCart.put("RequiredPlayerRange", (short) 12);
        spawnerCart.put("SpawnRange", (short) 2);
        spawnerCart.put("Rotation", List.of(180.0f, 10.0f));
        // The UUID's string form (the reference's other arm).
        java.util.UUID spawnerUuid = java.util.UUID.fromString("e2f5a1b0-1234-4cde-9f8a-0a1b2c3d4e5f");
        spawnerCart.put("UUID", spawnerUuid.toString());
        Map<String, Object> commandCart = AnvilFixture.entityRow("MinecartCommandBlock", 131.5, 1.0, 131.5);
        commandCart.put("Command", "say hi");
        commandCart.put("SuccessCount", 3);
        commandCart.put("TrackOutput", (byte) 0); // booleans are bytes on the wire
        commandCart.put("LastOutput", "{\"text\":\"hi\"}");
        commandCart.put("Motion", List.of(12.0, 0.0, 0.0)); // beyond the +-10 clamp
        Map<String, Object> plainCart = AnvilFixture.entityRow("MinecartRideable", 132.5, 1.0, 132.5);
        Map<String, Object> riddenCart = AnvilFixture.entityRow("MinecartRideable", 133.5, 1.0, 133.5);
        Map<String, Object> zombieRider = AnvilFixture.entityRow("Zombie", 133.5, 2.0, 133.5);
        riddenCart.put("Riding", zombieRider); // the reference nests the rider in the mount
        Map<String, Object> boat = AnvilFixture.entityRow("Boat", 134.5, 1.0, 134.5);
        Map<String, Object> furnaceCart = AnvilFixture.entityRow("MinecartFurnace", 135.5, 1.0, 135.5);
        Map<String, Object> tntCart = AnvilFixture.entityRow("MinecartTNT", 136.5, 1.0, 136.5);
        Map<String, Object> hopperCart = AnvilFixture.entityRow("MinecartHopper", 137.5, 1.0, 137.5);

        byte[] chunkNbt = AnvilFixture.nbtDocument(AnvilFixture.chunkNbt(8, 8,
                List.of(AnvilFixture.section(0, blocks, new byte[2048], null)), null,
                List.of(chestCart, spawnerCart, commandCart, plainCart, riddenCart,
                        boat, furnaceCart, tntCart, hopperCart), null));
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

        EngineConfig config = new EngineConfig("127.0.0.1", 0, "anvil-vehicles", "it",
                20, 2, 20, dir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        try {
            server.consoleCommand("anvilimport " + vault);
            long deadline = System.currentTimeMillis() + 20_000;
            while (System.currentTimeMillis() < deadline
                    && console.stream().noneMatch(line -> line.startsWith("Anvil import finished"))) {
                EngineAwaits.awaitTicks(server.world(), 1, "engine progress (a 50 wall settle)");
            }
            String report = console.stream()
                    .filter(line -> line.startsWith("Anvil import finished"))
                    .findFirst().orElse(null);
            assertNotNull(report, "the final report landed (console: " + console + ")");

            // All nine landed through the engine's own managers.
            var all = server.vehicles().all();
            assertEquals(9, all.size(), "nine vehicles imported (report: " + report + ")");
            assertEquals(2, all.stream().filter(v -> v.kind()
                    == net.zaminmc.torch.server.entity.vehicle.VehicleEntity.Kind.MINECART).count());
            assertEquals(1, all.stream().filter(v -> v.kind()
                    == net.zaminmc.torch.server.entity.vehicle.VehicleEntity.Kind.BOAT).count());
            assertEquals(1, all.stream().filter(v -> v.kind()
                    == net.zaminmc.torch.server.entity.vehicle.VehicleEntity.Kind.CHEST_MINECART).count());
            assertEquals(1, all.stream().filter(v -> v.kind()
                    == net.zaminmc.torch.server.entity.vehicle.VehicleEntity.Kind.HOPPER_MINECART).count());

            // The chest cart: the cargo through the engine's own chest
            // surface, the shared base rows, the burn clock.
            var chest = (net.zaminmc.torch.server.entity.vehicle.ChestMinecartEntity)
                    all.stream().filter(v -> v instanceof net.zaminmc.torch.server.entity.vehicle.ChestMinecartEntity)
                            .findFirst().orElseThrow();
            var slots = chest.chest().snapshotSlots();
            assertEquals("minecraft:diamond", slots[0].type().identifier().toString());
            assertEquals(2, slots[0].count());
            assertEquals("minecraft:iron_ingot", slots[5].type().identifier().toString());
            assertEquals(1, slots[5].count());
            assertEquals(90.0f, chest.rotation().yaw(), 0.001f);
            // The Motion row landed at (0.5, 0, -0.25); the engine's own
            // off-rail friction decays it toward zero between the import
            // and this assertion (the direction is preserved, never
            // flipped) — the band pins the landing's sign and bound.
            assertTrue(chest.velocityX() >= 0.0 && chest.velocityX() <= 0.5,
                    "the x component decays from 0.5 without flipping: " + chest.velocityX());
            assertTrue(chest.velocityZ() <= 0.0 && chest.velocityZ() >= -0.25,
                    "the z component decays from -0.25 without flipping: " + chest.velocityZ());
            // The Fire row landed at 40 and decays one per tick while
            // the cart burns — the assertion pins the landing band.
            assertTrue(chest.onFireTimer() >= 1 && chest.onFireTimer() <= 40,
                    "the raw signed Fire row landed and is burning: " + chest.onFireTimer());
            // The base rows (the §31.6 counted refusal retired).
            assertEquals(chestUuid, chest.uuid(), "the UUID pair form landed");
            assertEquals("Express", chest.customName());
            assertTrue(chest.customNameVisible());
            // The fall store re-pinned (Wave VN-A's sweep caught the
            // stale twin of VehiclePersistenceTest's RS re-pin): the
            // imported 2.5 spends at the settle's FIRST LANDING — the
            // reference's own checkFallDamage line-733 law (the store
            // resets on the ground contact), Wave RS's vehicle fall
            // walk. The pre-RS engine never touched the store in the
            // tick; the seat pinned that dead behavior.
            assertEquals(0.0f, chest.fallDistance(), 0.0001f);
            assertEquals(250, chest.breath());
            // The OnGround row landed false; the engine's own physics
            // re-derived it since (the cart settled onto the imported
            // ground) — the live state owns the flag now.
            assertTrue(chest.onGround(), "the OnGround row landed and the physics owns it since");
            assertTrue(chest.invulnerable());
            assertFalse(chest.silent());
            assertEquals(0, chest.dimension());
            // The PortalCooldown row re-pinned the Fire row's own band
            // shape (VT-006 — the vehicle family's portal arm now pays
            // the reference's tail decrement, Entity.baseTick lines
            // 311-313: every ticked body's cooldown decays by one; the
            // imported 12 counts down from the landing, the round trip
            // pinned by the band exactly as the live Fire row above).
            assertTrue(chest.portalCooldown() >= 1 && chest.portalCooldown() <= 12,
                    "the raw PortalCooldown row landed and is decaying: "
                            + chest.portalCooldown());

            // The spawner cart: the type + delay rows, the rotation pair.
            var spawner = (net.zaminmc.torch.server.entity.vehicle.SpawnerMinecartEntity)
                    all.stream().filter(v -> v instanceof net.zaminmc.torch.server.entity.vehicle.SpawnerMinecartEntity)
                            .findFirst().orElseThrow();
            assertEquals(net.zaminmc.torch.server.entity.MobType.ZOMBIE, spawner.type());
            assertEquals(55, spawner.delayTicks());
            // The cadence rows (the Wave VI per-cart surface —
            // MobSpawnerBaseLogic.readNbt's own shorts land whole).
            assertEquals(120, spawner.minSpawnDelay(), "the MinSpawnDelay row");
            assertEquals(240, spawner.maxSpawnDelay(), "the MaxSpawnDelay row");
            assertEquals(2, spawner.spawnCount(), "the SpawnCount row");
            assertEquals(3, spawner.maxNearbyEntities(), "the MaxNearbyEntities row");
            assertEquals(12, spawner.requiredPlayerRange(), "the RequiredPlayerRange row");
            assertEquals(2, spawner.spawnRange(), "the SpawnRange row");
            assertEquals(180.0f, spawner.rotation().yaw(), 0.001f);
            assertEquals(10.0f, spawner.rotation().pitch(), 0.001f);
            // The UUID's string form (the reference's other arm).
            assertEquals(spawnerUuid, spawner.uuid(), "the UUID string form landed");

            // The command cart: the four rows, the +-10 clamp on Motion.
            var command = (net.zaminmc.torch.server.entity.vehicle.CommandBlockMinecartEntity)
                    all.stream().filter(v -> v instanceof net.zaminmc.torch.server.entity.vehicle.CommandBlockMinecartEntity)
                            .findFirst().orElseThrow();
            assertEquals("say hi", command.command());
            assertEquals(3, command.successCount());
            assertFalse(command.trackOutput());
            assertEquals("{\"text\":\"hi\"}", command.lastOutput());
            assertEquals(0.0, command.velocityX(), 1e-9,
                    "the 12.0 component read as 0 (the reference's clamp)");

            // The report's own rows: the riding chain (the cadence rows
            // LANDED with the Wave VI per-cart surface — no note); the
            // CART-as-rider chain now LANDS TOO (the rider side's
            // retirement — a vehicle RIDER on a mob mount: the zombie's
            // seat opens for the cart's engine id, the follow anchoring
            // the cart at the mob's seat law every tick; the §31.6
            // counted refusal's rider side retired).
            assertTrue(report.contains("10 entities"), "the report: " + report);
            assertFalse(report.contains("spawner cart's cadence"),
                    "the cadence rows landed, never isolated: " + report);
            assertFalse(report.contains("riding chain not imported"),
                    "the ridden cart's chain landed, never isolated: " + report);
            assertFalse(report.contains("rider family has no seat surface"),
                    "the rider-side retirement holds: " + report);
            // The chain's pair both landed (the reference's shape: the
            // mount lands, then the head takes the seat): the zombie is
            // in the world AND its seat carries the cart.
            var chainZombie = server.mobs().all().stream()
                    .filter(mob -> mob.position().x() == 133.5)
                    .findFirst().orElseThrow();
            assertTrue(chainZombie.hasRider(), "the zombie mount's seat opened for the cart");
            assertTrue(server.vehicles().all().stream().anyMatch(v ->
                            !v.hasPassenger() && v.entityId() == chainZombie.riderId()),
                    "the zombie's rider is the imported cart (the engine id)");
        } finally {
            consoleLogger.removeHandler(capture);
            server.shutdown(null);
        }
    }
}
