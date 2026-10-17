package net.zaminmc.torch.server;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.nbt.NbtOps;
import net.zaminmc.torch.server.nbt.PlayerNbt;
import net.zaminmc.torch.server.nbt.Snbt;
import net.zaminmc.torch.server.player.PlayerSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The placement-created tile + the player-write model (Slice 92 — the C3
 * envelope's last two landings): the moment a tile-family block lands its
 * block entity exists (the reference's {@code World.setBlockState} ->
 * {@code createBlockEntity} — the lazy getOrCreate stand-in retires), and
 * the /testfor dataTag reads a player's OWN written rows (the reference's
 * player writeNbt over the engine's model — the base rows, the living
 * rows, the inventory, the xp band, the hunger pair, the abilities
 * compound, the ender chest, the game type).
 */
class PlacementTileAndPlayerRowsTest {

    @TempDir
    Path dataDir;

    EngineServer server;
    PlayerSession alice;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "tileplayer" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString(),
                net.zaminmc.torch.GameMode.SURVIVAL);
        server = new EngineServer(config);
        server.start();
        alice = join("Alice");
        await(() -> alice.state() == net.zaminmc.torch.entity.PlayerState.PLAYING, "playing");
        server.ticker().submit(() -> alice.setOpLevel(2));
        await(() -> alice.opLevel() >= 2, "Alice opped");
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    private PlayerSession join(String name) {
        var accepted = (net.zaminmc.torch.server.net.EngineBridge.Accepted)
                server.joinRequest(new RecordingLink(), name,
                        UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes()));
        PlayerSession session = accepted.session();
        server.ticker().submit(session::markPlaying);
        return session;
    }

    private static final class RecordingLink implements net.zaminmc.torch.server.net.ClientLink {
        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public void kick(String reason) {
        }

        @Override
        public void updateSpawnPosition(int x, int y, int z) {
        }

        @Override
        public void playSound(String name, double x, double y, double z, float volume, float pitch) {
        }

        @Override
        public void worldParticles(int particleId, boolean longDistance, double x, double y, double z,
                                   float offsetX, float offsetY, float offsetZ,
                                   float particleData, int count, int[] params) {
        }
    }

    private void dispatch(String line) {
        server.chatService().submitChat(alice, line);
    }

    /** Runs the body on the tick lane and waits it out. */
    private void tick(Runnable body) throws Exception {
        java.util.concurrent.atomic.AtomicBoolean done =
                new java.util.concurrent.atomic.AtomicBoolean();
        server.ticker().submit(() -> {
            try {
                body.run();
            } finally {
                done.set(true);
            }
        });
        await(done::get, "the tick task ran");
    }

    private void await(java.util.function.BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    // ------------------------------------------------ the placement-created tile

    @Test
    void thePlacedChestCarriesItsTileFromThePlacement() throws Exception {
        // The reference's placement-created tile: no open, no /blockdata —
        // the block entity exists the moment the chest lands.
        int x = (int) Math.floor(alice.position().x());
        int y = (int) Math.floor(alice.position().y());
        int z = (int) Math.floor(alice.position().z());
        var chestPos = new BlockPosition(x + 2, y, z);
        dispatch("/setblock ~2 ~ ~ chest");
        await(() -> server.world().getBlock(chestPos).identifier().value().equals("chest"),
                "the chest landed");
        tick(() -> assertNotNull(server.chests().peek(chestPos),
                "the placement created the tile — no open needed"));
    }

    @Test
    void thePlacedFurnaceAndHopperCarryTheirTiles() throws Exception {
        int x = (int) Math.floor(alice.position().x());
        int y = (int) Math.floor(alice.position().y());
        int z = (int) Math.floor(alice.position().z());
        var furnacePos = new BlockPosition(x + 2, y, z);
        var hopperPos = new BlockPosition(x + 3, y, z);
        dispatch("/setblock ~2 ~ ~ furnace");
        dispatch("/setblock ~3 ~ ~ hopper_down");
        await(() -> server.world().getBlock(furnacePos).identifier().value().equals("furnace"),
                "the furnace landed");
        await(() -> server.world().getBlock(hopperPos).identifier().value().startsWith("hopper"),
                "the hopper landed");
        tick(() -> {
            assertNotNull(server.furnaces().peek(furnacePos), "the furnace tile exists");
            assertNotNull(server.hopper().peek(hopperPos), "the hopper tile exists");
        });
    }

    // ------------------------------------------------ the player-write model

    /** The player's written rows (the /testfor matcher's own source). */
    private Map<String, Snbt.Tag> playerRows() throws Exception {
        var rows = new java.util.LinkedHashMap<String, Snbt.Tag>();
        tick(() -> PlayerNbt.writeNbtWithoutId(alice, rows, server.potions()));
        return rows;
    }

    private boolean matches(String pattern) throws Exception {
        return NbtOps.matches(
                Snbt.Tag.ofCompound(Snbt.parseCompound(pattern)),
                Snbt.Tag.ofCompound(playerRows()), true);
    }

    @Test
    void theEmptyCompoundMatchesThePlayer() throws Exception {
        assertTrue(matches("{}"));
    }

    @Test
    void thePlayerRowsCarryTheFreshSurvivalState() throws Exception {
        // The fresh survival body: level 0, food 20, game type 0, the
        // empty inventory, the ground, the overworld.
        assertTrue(matches("{XpLevel:0}"));
        assertTrue(matches("{foodLevel:20}"));
        assertTrue(matches("{playerGameType:0}"));
        assertTrue(matches("{Dimension:0}"));
        assertTrue(matches("{OnGround:1b}"));
        assertTrue(matches("{Inventory:[]}"));
        assertTrue(matches("{SelectedItemSlot:0}"));
        assertTrue(matches("{abilities:{flying:0b}}"));
        assertTrue(matches("{abilities:{mayfly:0b}}"));
    }

    @Test
    void theMismatchingPatternFails() throws Exception {
        assertFalse(matches("{XpLevel:30}"), "a fresh player is not level 30");
        assertFalse(matches("{HealF:5.0f}"), "the fresh health is 20");
        assertFalse(matches("{playerGameType:1}"), "the fresh mode is survival");
    }

    @Test
    void theUuidRowsMatchTheSession() throws Exception {
        var rows = playerRows();
        assertTrue(rows.containsKey("UUIDMost"));
        assertTrue(rows.containsKey("UUIDLeast"));
        assertTrue(matches("{UUIDLeast:" + NbtOps.getLong(rows, "UUIDLeast") + "L,"
                + "UUIDMost:" + NbtOps.getLong(rows, "UUIDMost") + "L}"));
    }
}
