package net.zaminmc.torch.server.noteblock;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.fx.FxManager;
import net.zaminmc.torch.server.redstone.RedstoneBlocks;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import net.zaminmc.torch.testing.EngineAwaits;

/**
 * The note block family on the live engine (Slice 27a) — the reference
 * port of NoteBlock + NoteBlockBlockEntity: the material table's
 * instrument selection (the reference's own registry assignments, quirks
 * included), the use arm's tune-and-play, the survival attack arm's play,
 * the power edge's rising-only replay, the air-above gate, and the ZND
 * note persistence round trip.
 */
class NoteBlockSystemTest {

    @TempDir
    Path dataDir;

    // ------------------------------------------------------------------
    // The material table (the reference's registry assignments)
    // ------------------------------------------------------------------

    @Test
    void theMaterialTableSelectsTheReferenceInstruments() {
        // Bass drum: the STONE-material family, including the nether brick
        // fence quirk (line 1236's Material.STONE registration).
        assertEquals(1, NoteBlockSystem.instrumentOf("stone"));
        assertEquals(1, NoteBlockSystem.instrumentOf("bedrock"));
        assertEquals(1, NoteBlockSystem.instrumentOf("enchanting_table"));
        assertEquals(1, NoteBlockSystem.instrumentOf("nether_brick_fence"));
        assertEquals(1, NoteBlockSystem.instrumentOf("end_portal_frame_east"));
        // Snare: the SAND-material family — gravel and soul sand carry it
        // (FallingBlock's own material).
        assertEquals(2, NoteBlockSystem.instrumentOf("sand"));
        assertEquals(2, NoteBlockSystem.instrumentOf("gravel"));
        assertEquals(2, NoteBlockSystem.instrumentOf("soul_sand"));
        // Hat: the GLASS-material family — glowstone and the sea lantern
        // included (the reference's registrations).
        assertEquals(3, NoteBlockSystem.instrumentOf("glass"));
        assertEquals(3, NoteBlockSystem.instrumentOf("glass_pane"));
        assertEquals(3, NoteBlockSystem.instrumentOf("glowstone"));
        assertEquals(3, NoteBlockSystem.instrumentOf("sea_lantern"));
        // Bass guitar: the WOOD-material family — the note block itself
        // (Material.WOOD), the fence, the slab, the signs, the containers.
        assertEquals(4, NoteBlockSystem.instrumentOf("oak_planks"));
        assertEquals(4, NoteBlockSystem.instrumentOf("noteblock"));
        assertEquals(4, NoteBlockSystem.instrumentOf("chest"));
        assertEquals(4, NoteBlockSystem.instrumentOf("oak_fence"));
        assertEquals(4, NoteBlockSystem.instrumentOf("oak_slab"));
        assertEquals(4, NoteBlockSystem.instrumentOf("wall_sign_south"));
        // Harp: everything else — the infested family's CLAY quirk, wool
        // (CLOTH), the torch's DECORATION, iron, the pumpkin, dirt, TNT.
        assertEquals(0, NoteBlockSystem.instrumentOf("infested_stone"));
        assertEquals(0, NoteBlockSystem.instrumentOf("wool"));
        assertEquals(0, NoteBlockSystem.instrumentOf("torch"));
        assertEquals(0, NoteBlockSystem.instrumentOf("iron_block"));
        assertEquals(0, NoteBlockSystem.instrumentOf("pumpkin"));
        assertEquals(0, NoteBlockSystem.instrumentOf("dirt"));
        assertEquals(0, NoteBlockSystem.instrumentOf("tnt"));
        assertEquals(0, NoteBlockSystem.instrumentOf("bed"));
        // The reference's sound keys (the INSTRUMENTS list).
        assertEquals("harp", NoteBlockSystem.instrumentName(0));
        assertEquals("bd", NoteBlockSystem.instrumentName(1));
        assertEquals("snare", NoteBlockSystem.instrumentName(2));
        assertEquals("hat", NoteBlockSystem.instrumentName(3));
        assertEquals("bassattack", NoteBlockSystem.instrumentName(4));
    }

    // ------------------------------------------------------------------
    // The live arms
    // ------------------------------------------------------------------

    @Test
    void theUseArmTunesAndPlaysOverTheWire() throws Exception {
        EngineServer server = boot();
        try {
            int x = 10, z = 10, y = 40;
            place(server, x, y, z, BuiltinBlocks.STONE, x, y + 1, z, BuiltinBlocks.NOTE_BLOCK);
            await(server, () -> server.noteBlocks().tileAt(
                    new BlockPosition(x, y + 1, z)) != null, "the placement created the tile");
            assertEquals(0, server.noteBlocks().tileAt(new BlockPosition(x, y + 1, z)).note(),
                    "a fresh tile starts at note 0 (the reference's fresh BE)");

            ConcurrentLinkedQueue<int[]> actions = new ConcurrentLinkedQueue<>();
            ConcurrentLinkedQueue<FxManager.Sound> sounds = new ConcurrentLinkedQueue<>();
            server.noteBlocks().addNoteActionListener((position, instrument, note)
                    -> actions.add(new int[] {instrument, note}));
            server.fx().addListener(event -> {
                if (event instanceof FxManager.Sound sound
                        && sound.name().startsWith("note.")) {
                    sounds.add(sound);
                }
            });

            // One use: the tune (0 -> 1) then the play. Stone below = bass
            // drum; the pitch walk 2^((1-12)/12); the reference's volume 3.0F.
            server.ticker().submit(() -> server.noteBlocks().useFromTick(
                    new BlockPosition(x, y + 1, z)));
            await(server, () -> !actions.isEmpty(), "the use played over the wire");
            assertEquals(1, actions.size());
            assertEquals(1, actions.peek()[0], "stone below selected the bass drum");
            assertEquals(1, actions.peek()[1], "the first tune moved the note to 1");
            assertEquals(1, sounds.size(), "the doEvent sound broadcast once");
            FxManager.Sound sound = sounds.peek();
            assertEquals("note.bd", sound.name());
            assertEquals(3.0f, sound.volume(), 0.0001f);
            float expectedPitch = (float) Math.pow(2.0, (1 - 12) / 12.0);
            assertEquals(expectedPitch, sound.pitch(), 0.0001f,
                    "the reference's pitch walk 2^((note-12)/12)");

            // 24 more uses wrap the band: 25 total brings the note back to 0.
            for (int i = 0; i < 24; i++) {
                server.ticker().submit(() -> server.noteBlocks().useFromTick(
                        new BlockPosition(x, y + 1, z)));
            }
            await(server, () -> actions.size() >= 25, "every use played");
            assertEquals(25, actions.size());
            int[] last = null;
            for (int[] action : actions) {
                last = action;
            }
            assertEquals(1, last[0], "bass drum throughout (stone below)");
            assertEquals(0, last[1], "the 25th use wrapped the note band back to 0");
            // The pitch carried each walk (spot-check the middle one).
            float midPitch = (float) Math.pow(2.0, (13 - 12) / 12.0);
            FxManager.Sound midSound = null;
            int seen = 0;
            for (FxManager.Sound s : sounds) {
                seen++;
                if (seen == 13) {
                    midSound = s;
                }
            }
            assertEquals(midPitch, midSound.pitch(), 0.0001f);
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theAttackArmPlaysWithoutTuning() throws Exception {
        EngineServer server = boot();
        try {
            int x = 10, z = 10, y = 40;
            place(server, x, y, z, BuiltinBlocks.OAK_PLANKS, x, y + 1, z, BuiltinBlocks.NOTE_BLOCK);
            await(server, () -> server.noteBlocks().tileAt(
                    new BlockPosition(x, y + 1, z)) != null, "the placement created the tile");

            ConcurrentLinkedQueue<int[]> actions = new ConcurrentLinkedQueue<>();
            server.noteBlocks().addNoteActionListener((position, instrument, note)
                    -> actions.add(new int[] {instrument, note}));

            // The survival attack (startMining): the play WITHOUT the tune —
            // the note stays 0, the instrument is the bass guitar (planks
            // below are WOOD material).
            server.ticker().submit(() -> server.noteBlocks().attackFromTick(
                    new BlockPosition(x, y + 1, z)));
            await(server, () -> !actions.isEmpty(), "the attack played");
            assertEquals(1, actions.size());
            assertEquals(4, actions.peek()[0], "planks below selected the bass guitar");
            assertEquals(0, actions.peek()[1], "the attack never tunes — note stays 0");
            assertEquals(0, server.noteBlocks().tileAt(new BlockPosition(x, y + 1, z)).note());
        } finally {
            server.shutdown(() -> { });
        }
    }

    /**
     * The survival attack's own stat (NoteBlock.startMining lines 58-67): the
     * dig-start report carries the noteblockPlayed increment — the joined
     * body's real submitMiningStart walk, not the system's bare verb.
     */
    @Test
    void theSurvivalAttackIncrementsThePlayStat() throws Exception {
        EngineServer server = boot();
        try {
            net.zaminmc.torch.server.net.ClientLink link =
                    new net.zaminmc.torch.server.net.ClientLink() {
                @Override public boolean isActive() { return true; }
                @Override public void kick(String reason) { }
            };
            var result = server.joinRequest(link, "notestat",
                    UUID.nameUUIDFromBytes("notestat".getBytes()));
            net.zaminmc.torch.server.player.PlayerSession player =
                    ((net.zaminmc.torch.server.net.EngineBridge.Accepted) result).session();

            // Two cells beside the body (inside the dig handler's own
            // six-block band — the reference's d3 > 36.0 gate): the floor,
            // the note block, and a forced air cell above it (the play's
            // own air-above gate).
            Position feet = player.position();
            int nx = (int) Math.floor(feet.x()) + 2;
            int ny = (int) Math.floor(feet.y());
            int nz = (int) Math.floor(feet.z());
            BlockPosition noteAt = new BlockPosition(nx, ny, nz);
            server.ticker().submit(() -> {
                server.world().setBlock(noteAt.offset(0, -1, 0), BuiltinBlocks.OAK_PLANKS);
                server.world().setBlock(noteAt, BuiltinBlocks.NOTE_BLOCK);
                server.world().setBlock(noteAt.offset(0, 1, 0), server.world().airType());
            });
            await(server, () -> server.noteBlocks().tileAt(noteAt) != null,
                    "the placement created the tile");

            server.blockInteraction().submitMiningStart(player, noteAt);
            await(server, () -> player.stats().get(
                    net.zaminmc.torch.server.stat.EngineStats.NOTE_BLOCKS_PLAYED) > 0,
                    "the survival attack incremented the play stat");
            assertEquals(1, player.stats().get(
                    net.zaminmc.torch.server.stat.EngineStats.NOTE_BLOCKS_PLAYED),
                    "exactly one increment per dig start");
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void thePowerEdgePlaysOnlyOnTheRisingEdge() throws Exception {
        EngineServer server = boot();
        try {
            int x = 10, z = 10, y = 40;
            // A note block on dirt (harp) with a lever two cells over — the
            // same mount the TNT test rides.
            place(server, x, y, z, BuiltinBlocks.DIRT,
                    x, y + 1, z, BuiltinBlocks.NOTE_BLOCK);
            server.ticker().submit(() -> {
                server.world().setBlock(new BlockPosition(x + 1, y + 1, z), BuiltinBlocks.STONE);
                server.world().setBlock(new BlockPosition(x + 2, y + 1, z),
                        RedstoneBlocks.leverOf(1, false));
            });
            await(server, () -> server.noteBlocks().tileAt(
                    new BlockPosition(x, y + 1, z)) != null, "the placement created the tile");

            ConcurrentLinkedQueue<int[]> actions = new ConcurrentLinkedQueue<>();
            server.noteBlocks().addNoteActionListener((position, instrument, note)
                    -> actions.add(new int[] {instrument, note}));

            // Power ON: the rising edge plays (harp — dirt below).
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(x + 2, y + 1, z)));
            await(server, () -> !actions.isEmpty(), "the rising edge played");
            assertEquals(1, actions.size());
            assertEquals(0, actions.peek()[0], "dirt below selected the harp");
            assertEquals(0, actions.peek()[1], "the edge never tunes");

            // Power OFF: the falling edge stays silent.
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(x + 2, y + 1, z)));
            EngineAwaits.awaitTicks(server.world(), 6, "engine progress (a 300 wall settle)");
            assertEquals(1, actions.size(), "the falling edge never replays");

            // Power ON again: the re-rise plays again (the reference's own
            // powered-flag edge rule).
            server.ticker().submit(() -> server.redstone().useLever(
                    new BlockPosition(x + 2, y + 1, z)));
            await(server, () -> actions.size() >= 2, "the re-rise replayed");
            assertEquals(2, actions.size());
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theAirAboveGateSuppressesThePlay() throws Exception {
        EngineServer server = boot();
        try {
            int x = 10, z = 10, y = 40;
            place(server, x, y, z, BuiltinBlocks.STONE, x, y + 1, z, BuiltinBlocks.NOTE_BLOCK);
            await(server, () -> server.noteBlocks().tileAt(
                    new BlockPosition(x, y + 1, z)) != null, "the placement created the tile");

            ConcurrentLinkedQueue<int[]> actions = new ConcurrentLinkedQueue<>();
            server.noteBlocks().addNoteActionListener((position, instrument, note)
                    -> actions.add(new int[] {instrument, note}));

            // Cover the note block: the air-material gate suppresses every
            // play (NoteBlockBlockEntity.playNote's own gate).
            server.ticker().submit(() -> server.world().setBlock(
                    new BlockPosition(x, y + 2, z), BuiltinBlocks.STONE));
            await(server, () -> server.world().getBlock(new BlockPosition(x, y + 2, z))
                    .identifier().value().equals("stone"), "the cover landed");
            server.ticker().submit(() -> server.noteBlocks().useFromTick(
                    new BlockPosition(x, y + 1, z)));
            EngineAwaits.awaitTicks(server.world(), 6, "engine progress (a 300 wall settle)");
            assertTrue(actions.isEmpty(), "a covered note block never plays");
            // The tune still happened (the reference tunes regardless).
            assertEquals(1, server.noteBlocks().tileAt(new BlockPosition(x, y + 1, z)).note());

            // Uncover: the next use plays again.
            server.ticker().submit(() -> server.world().setBlock(
                    new BlockPosition(x, y + 2, z), server.world().airType()));
            await(server, () -> server.world().getBlock(new BlockPosition(x, y + 2, z))
                    .identifier().value().equals("air"), "the cover left");
            server.ticker().submit(() -> server.noteBlocks().useFromTick(
                    new BlockPosition(x, y + 1, z)));
            await(server, () -> !actions.isEmpty(), "the uncovered play went out");
            assertEquals(1, actions.size());
        } finally {
            server.shutdown(() -> { });
        }
    }

    @Test
    void theBreakClearsTheTileAndTheZndRoundTrips() throws Exception {
        EngineServer server = boot();
        try {
            int x = 10, z = 10, y = 40;
            place(server, x, y, z, BuiltinBlocks.STONE, x, y + 1, z, BuiltinBlocks.NOTE_BLOCK);
            BlockPosition noteAt = new BlockPosition(x, y + 1, z);
            await(server, () -> server.noteBlocks().tileAt(noteAt) != null, "the tile exists");

            // Tune to 7.
            for (int i = 0; i < 7; i++) {
                server.ticker().submit(() -> server.noteBlocks().useFromTick(noteAt));
            }
            await(server, () -> server.noteBlocks().tileAt(noteAt).note() == 7,
                    "seven tunes moved the note to 7");

            // The ZND round trip through a real file (Wave FL4-GAP2 Slice
            // C: the union save, each row stamped with its world).
            Path znd = dataDir.resolve("noteblocks.bin");
            NoteDataStore store = new NoteDataStore(znd);
            store.save(java.util.Map.of(0, server.noteBlocks().snapshotTiles()));
            assertTrue(Files.exists(znd), "the store wrote the file");
            NoteDataStore reloaded = new NoteDataStore(znd);
            NoteDataStore.Loaded noteRow = reloaded.load().stream().filter(
                    row -> row.position().equals(noteAt)).findFirst().orElseThrow();
            assertEquals(0, noteRow.dimension(), "the row stamps the overworld");
            assertEquals(Byte.valueOf((byte) 7), noteRow.note(),
                    "the note byte survived the round trip");

            // The break (the real flow: the block leaves the world and the
            // engine's break listener clears the tile); the re-place makes a
            // fresh tile at note 0.
            server.ticker().submit(() -> {
                server.world().setBlock(noteAt, server.world().airType());
                server.noteBlocks().onBlockBroken(noteAt);
            });
            await(server, () -> server.noteBlocks().tileAt(noteAt) == null, "the tile died");
            await(server, () -> server.world().getBlock(noteAt)
                    .identifier().value().equals("air"), "the cell is empty");
            server.ticker().submit(() -> server.world().setBlock(noteAt, BuiltinBlocks.NOTE_BLOCK));
            await(server, () -> server.noteBlocks().tileAt(noteAt) != null, "the re-place made a tile");
            assertEquals(0, server.noteBlocks().tileAt(noteAt).note(),
                    "the fresh tile starts at note 0 (the reference's fresh BE)");

            // A corrupt ZND loads empty (preserved, not destroyed).
            Path corrupt = dataDir.resolve("corrupt.bin");
            Files.writeString(corrupt, "not a znd file");
            NoteDataStore corruptStore = new NoteDataStore(corrupt);
            assertTrue(corruptStore.load().isEmpty());
            assertTrue(Files.exists(corrupt), "the corrupt file is preserved");
        } finally {
            server.shutdown(() -> { });
        }
    }

    // ------------------------------------------------------------------
    // Harness
    // ------------------------------------------------------------------

    /** Places the floor then the note block above it, on the tick thread. */
    private static void place(EngineServer server, int fx, int fy, int fz,
                              net.zaminmc.torch.block.BlockType floor,
                              int nx, int ny, int nz,
                              net.zaminmc.torch.block.BlockType note) {
        server.ticker().submit(() -> {
            server.world().setBlock(new BlockPosition(fx, fy, fz), floor);
            server.world().setBlock(new BlockPosition(nx, ny, nz), note);
        });
    }

    private static void await(EngineServer server, BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            AtomicReference<Boolean> ran = new AtomicReference<>();
            server.ticker().submit(() -> ran.set(condition.getAsBoolean()));
            long inner = System.currentTimeMillis() + 200;
            while (System.currentTimeMillis() < inner && ran.get() == null) {
                Thread.sleep(5);
            }
            if (Boolean.TRUE.equals(ran.get())) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "note" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        return server;
    }
}
