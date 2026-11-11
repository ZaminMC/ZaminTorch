package net.zaminmc.torch.server.chest;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.block.BuiltinBlocks;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.fx.FxManager;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WAVE TET (Paper 0022, the KigPaper census's tile-entity-ticking row) —
 * the chest's OPEN/CLOSE SOUNDS at the events themselves. The reference
 * plays {@code random.chestopen}/{@code random.chestclosed} from the tile
 * entity's per-tick animation loop (ChestBlockEntity.tick lines 245-291
 * — the open sound at the 0 -> 1 viewer transition, the close at the
 * lid's 0.5-crossing on the 1 -> 0 departure); Paper 0022 removes the
 * tick loop whole and moves both sounds to the open/close events. The
 * engine's chest never carried a tick (the destination Paper optimized
 * TO) but also never carried the SOUNDS — the seats pin the landed
 * observable at Paper's own event sites: the transition bands (only the
 * FIRST opener sounds, only the LAST closer), the sound-owner law (a
 * same-type chest to the NORTH or WEST suppresses the sound), the
 * double-chest center offsets (+0.5 x for an east twin, +0.5 z for a
 * south twin), the 3-row's all-suppressed vanilla quirk, and the ender
 * chest's own lone-chest pair.
 */
class ChestSoundParityTest {

    @TempDir
    Path dataDir;

    private EngineServer server;

    /** The recorded sound rows (name, position, volume, pitch). */
    private record Heard(String name, Position at, float volume, float pitch) {
    }

    private final List<Heard> sounds = new CopyOnWriteArrayList<>();

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "csp" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 4, 20, dataDir.toString(), net.zaminmc.torch.GameMode.CREATIVE,
                true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
        server.fx().addListener(new FxManager.Listener() {
            @Override
            public void onFx(FxManager.FxEvent event) {
                if (event instanceof FxManager.Sound sound) {
                    sounds.add(new Heard(sound.name(), sound.position(),
                            sound.volume(), sound.pitch()));
                }
            }
        });
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    private void await(BooleanSupplier condition, String description)
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

    private PlayerSession join(String name, Position at) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        var result = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        PlayerSession session = ((net.zaminmc.torch.server.net.EngineBridge.Accepted) result).session();
        server.joinCompleted(session);
        session.applyMovement(at, net.zaminmc.torch.util.Rotation.ZERO, true);
        return session;
    }

    /** The chest sounds among the recorded rows, in order. */
    private List<Heard> chestSounds() {
        List<Heard> rows = new ArrayList<>();
        for (Heard heard : sounds) {
            if (heard.name().equals("random.chestopen")
                    || heard.name().equals("random.chestclosed")) {
                rows.add(heard);
            }
        }
        return rows;
    }

    /** Opens the chest at the cell for the player; @return the window id. */
    private int open(PlayerSession player, BlockPosition at) throws InterruptedException {
        AtomicInteger windowId = new AtomicInteger(-1);
        server.useItemOnBlock(player, at, 1, java.util.Optional.empty(),
                windowId::set);
        await(() -> windowId.get() >= 0, "the window assigned");
        return windowId.get();
    }

    /**
     * SEAT 1 — THE TRANSITION BANDS over a lone chest: the FIRST open
     * sounds (volume 0.5F, the pitch {@code random * 0.1 + 0.9} band), the
     * close of the LAST viewer sounds the close, and a re-open sounds
     * again (the count's reset). ChestBlockEntity.tick lines 247-258 +
     * 261-286, Paper 0022's event placement.
     */
    @Test
    void theLoneChestSoundsAtTheTransitionBands() throws Exception {
        int x = 20, y = 40, z = 20;
        BlockPosition chest = new BlockPosition(x, y, z);
        server.ticker().submit(() -> server.world().setBlock(chest, BuiltinBlocks.CHEST));
        await(() -> server.world().getBlock(chest).identifier()
                .equals(BuiltinBlocks.CHEST.identifier()), "the chest placed");

        PlayerSession opener = join("Opener", new Position(x + 0.5, y, z + 1.5));
        int window = open(opener, chest);
        await(() -> !chestSounds().isEmpty(), "the open sound fired");
        Heard openSound = chestSounds().get(0);
        assertEquals("random.chestopen", openSound.name(), "the open sound's name");
        assertEquals(x + 0.5, openSound.at().x(), 1.0E-6, "the chest's own center x");
        assertEquals(y + 0.5, openSound.at().y(), 1.0E-6, "the chest's own center y");
        assertEquals(z + 0.5, openSound.at().z(), 1.0E-6, "the chest's own center z");
        assertEquals(0.5F, openSound.volume(), 1.0E-6F, "the reference's 0.5F volume");
        assertTrue(openSound.pitch() >= 0.9F && openSound.pitch() <= 1.0F,
                "the pitch band random * 0.1 + 0.9: " + openSound.pitch());
        assertEquals(1, chestSounds().size(), "one open sound, nothing more");

        server.closeWindow(opener, window);
        await(() -> chestSounds().size() == 2, "the close sound fired");
        Heard closeSound = chestSounds().get(1);
        assertEquals("random.chestclosed", closeSound.name(), "the close sound's name");
        assertEquals(x + 0.5, closeSound.at().x(), 1.0E-6, "the same center");
        assertEquals(0.5F, closeSound.volume(), 1.0E-6F);

        // The re-open: the count reset — the open sound again.
        open(opener, chest);
        await(() -> chestSounds().size() == 3, "the re-open sounded");
        assertEquals("random.chestopen", chestSounds().get(2).name());
    }

    /**
     * SEAT 2 — THE SECOND OPENER AND THE INTERMEDIATE CLOSER stay silent:
     * a second viewer over an open lid adds no second sound, and the
     * closer that leaves another viewer on the chest never sounds the
     * close — only the LAST closer's departure crosses the lid's band
     * (the reference's animation thresholds, Paper 0022's own condition
     * {@code numPlayersUsing > 0 && lidAngle == 0.0F}).
     */
    @Test
    void onlyTheLastCloserSoundsTheClose() throws Exception {
        int x = 30, y = 40, z = 30;
        BlockPosition chest = new BlockPosition(x, y, z);
        server.ticker().submit(() -> server.world().setBlock(chest, BuiltinBlocks.CHEST));
        await(() -> server.world().getBlock(chest).identifier()
                .equals(BuiltinBlocks.CHEST.identifier()), "the chest placed");

        PlayerSession first = join("First", new Position(x + 0.5, y, z + 1.5));
        PlayerSession second = join("Second", new Position(x - 0.5, y, z + 1.5));
        int firstWindow = open(first, chest);
        await(() -> !chestSounds().isEmpty(), "the first open sounded");
        int secondWindow = open(second, chest);
        Thread.sleep(300);
        assertEquals(1, chestSounds().size(),
                "the second opener over an open lid adds no sound");

        server.closeWindow(first, firstWindow);
        Thread.sleep(300);
        assertEquals(1, chestSounds().size(),
                "the intermediate closer (the first still watching) sounds nothing");
        server.closeWindow(second, secondWindow);
        await(() -> chestSounds().size() == 2, "the LAST closer sounded the close");
        assertEquals("random.chestclosed", chestSounds().get(1).name());
    }

    /**
     * SEAT 3 — THE DOUBLE CHEST'S ONE SOUND AT THE PAIR'S CENTER: a pair
     * along x (the twin EAST of the clicked chest) sounds ONCE — from the
     * west half's own row (the east half's west neighbor is its twin, the
     * suppression), the position carrying the +0.5 x offset (the pair's
     * center, ChestBlockEntity.tick lines 248-257).
     */
    @Test
    void theDoubleChestSoundsOnceAtThePairsCenter() throws Exception {
        int x = 40, y = 40, z = 40;
        BlockPosition west = new BlockPosition(x, y, z);
        BlockPosition east = new BlockPosition(x + 1, y, z);
        server.ticker().submit(() -> {
            server.world().setBlock(west, BuiltinBlocks.CHEST);
            server.world().setBlock(east, BuiltinBlocks.CHEST);
        });
        await(() -> server.world().getBlock(east).identifier()
                .equals(BuiltinBlocks.CHEST.identifier()), "the pair placed");

        // Opening the EAST half: the sound still comes from the WEST half
        // (the pair's sound owner) at the pair's center.
        PlayerSession opener = join("PairOpener", new Position(x + 1.5, y, z + 1.5));
        open(opener, east);
        await(() -> !chestSounds().isEmpty(), "the open sound fired");
        assertEquals(1, chestSounds().size(), "ONE sound for the pair, never two");
        Heard sound = chestSounds().get(0);
        assertEquals(x + 1.0, sound.at().x(), 1.0E-6,
                "the west half's center + the east twin's 0.5 offset (the pair's center)");
        assertEquals(z + 0.5, sound.at().z(), 1.0E-6, "no z offset for an x-axis pair");
    }

    /**
     * SEAT 4 — THE 3-ROW'S VANILLA QUIRK: three same-type chests in a
     * z-row, the SOUTH chest opened — every chest's own north/west
     * consult refuses the sound (the middle's north holds the north
     * chest, the south's north holds the middle, and the north chest's
     * own count never moved — the reference's per-tile fields, the
     * derived-carry's own quirk pinned with the family).
     */
    @Test
    void theThreeRowSouthChestOpensSilently() throws Exception {
        int x = 50, y = 40, z = 50;
        BlockPosition north = new BlockPosition(x, y, z - 1);
        BlockPosition middle = new BlockPosition(x, y, z);
        BlockPosition south = new BlockPosition(x, y, z + 1);
        server.ticker().submit(() -> {
            server.world().setBlock(north, BuiltinBlocks.CHEST);
            server.world().setBlock(middle, BuiltinBlocks.CHEST);
            server.world().setBlock(south, BuiltinBlocks.CHEST);
        });
        await(() -> server.world().getBlock(south).identifier()
                .equals(BuiltinBlocks.CHEST.identifier()), "the row placed");

        PlayerSession opener = join("RowOpener", new Position(x + 0.5, y, z + 2.5));
        open(opener, south);
        Thread.sleep(400);
        assertEquals(0, chestSounds().size(),
                "the 3-row's south open plays NOTHING (every chest suppressed — "
                        + "the vanilla quirk)");
    }

    /**
     * SEAT 5 — THE ENDER CHEST'S OWN PAIR: the lone-chest open/close
     * sounds at the block's own center (EnderChestBlockEntity.tick lines
     * 24-28 + 42-47, Paper 0022's event placement — no neighbor consult:
     * the family has no pairs), the same 0.5F volume band.
     */
    @Test
    void theEnderChestSoundsItsOwnPair() throws Exception {
        int x = 60, y = 40, z = 60;
        BlockPosition chest = new BlockPosition(x, y, z);
        server.ticker().submit(() -> server.world().setBlock(chest,
                net.zaminmc.torch.server.chest.EnderChestBlocks.enderChestOf(0)));
        await(() -> EnderChestBlocks.isFamily(server.world().getBlock(chest)),
                "the ender chest placed");

        PlayerSession opener = join("EnderOpener", new Position(x + 0.5, y, z + 1.5));
        int window = open(opener, chest);
        await(() -> !chestSounds().isEmpty(), "the ender chest's open sounded");
        Heard openSound = chestSounds().get(0);
        assertEquals("random.chestopen", openSound.name());
        assertEquals(x + 0.5, openSound.at().x(), 1.0E-6, "the lone chest's own center");
        assertEquals(z + 0.5, openSound.at().z(), 1.0E-6);
        assertEquals(0.5F, openSound.volume(), 1.0E-6F);

        server.closeWindow(opener, window);
        await(() -> chestSounds().size() == 2, "the ender chest's close sounded");
        assertEquals("random.chestclosed", chestSounds().get(1).name());
    }

    /**
     * SEAT 6 — THE TRAPPED CHEST PLAYS THE SAME NAMES: the family band
     * never changes the sound (the reference's chestopen/chestclosed are
     * family-blind — ChestBlock's own tile, the type only arms the
     * redstone probe).
     */
    @Test
    void theTrappedChestPlaysTheSameSounds() throws Exception {
        int x = 70, y = 40, z = 70;
        BlockPosition chest = new BlockPosition(x, y, z);
        server.ticker().submit(() -> server.world().setBlock(chest,
                BuiltinBlocks.TRAPPED_CHEST));
        await(() -> server.world().getBlock(chest).identifier()
                .equals(BuiltinBlocks.TRAPPED_CHEST.identifier()), "the trapped chest placed");

        PlayerSession opener = join("TrappedOpener", new Position(x + 0.5, y, z + 1.5));
        int window = open(opener, chest);
        await(() -> !chestSounds().isEmpty(), "the trapped chest's open sounded");
        assertEquals("random.chestopen", chestSounds().get(0).name(),
                "the same family-blind name");
        server.closeWindow(opener, window);
        await(() -> chestSounds().size() == 2, "the trapped chest's close sounded");
        assertEquals("random.chestclosed", chestSounds().get(1).name());
    }
}
