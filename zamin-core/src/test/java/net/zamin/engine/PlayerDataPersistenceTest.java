package net.zamin.engine;

import net.zamin.api.ItemStack;
import net.zamin.api.Position;
import net.zamin.api.Rotation;
import net.zamin.engine.config.EngineConfig;
import net.zamin.engine.item.BuiltinItems;
import net.zamin.engine.net.ClientLink;
import net.zamin.engine.net.EngineBridge;
import net.zamin.engine.player.PlayerSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Restart proof for players (§407 spirit, now for personal state): a leaving
 * player's position, look, inventory (including tool wear) and hotbar selection
 * survive an engine restart and are restored on the next join. Corrupt files
 * load as absent without destroying the evidence.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlayerDataPersistenceTest {

    @TempDir
    Path dataDir;

    private static final UUID PERSISTOR = UUID.nameUUIDFromBytes("OfflinePlayer:Persistor".getBytes());

    private EngineServer boot() throws InterruptedException {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "itest", "it", 20, 4, 20,
                dataDir.toString());
        EngineServer started = new EngineServer(config);
        started.start();
        return started;
    }

    private PlayerSession join(EngineServer server, String name) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes());
        var result = server.joinRequest(link, name, uuid);
        return ((EngineBridge.Accepted) result).session();
    }

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 3_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    @Test
    void survivalStateSurvivesARestart() throws Exception {
        // --- session 1: build state, leave ---
        EngineServer first = boot();
        PlayerSession player = join(first, "Persistor");
        assertEquals(PERSISTOR, player.uuid());

        assertTrue(player.inventory().pickUp(ItemStack.of(BuiltinItems.DIRT, 5)).isEmpty());
        assertTrue(player.inventory().pickUp(ItemStack.of(BuiltinItems.WOODEN_PICKAXE)).isEmpty());
        player.inventory().selectHotbarSlot(1);
        player.inventory().damageHeld(5); // a worn pickaxe must remember its wear
        player.applyMovement(new Position(12.5, 5.0, -7.25), new Rotation(97.5f, -18.0f), true);

        first.clientDisconnected(player, "test leave");
        await(() -> Files.exists(dataDir.resolve("players").resolve(PERSISTOR + ".zpd")),
                "player file written on disconnect");
        first.shutdown(null);

        // --- session 2: the same identity returns ---
        EngineServer second = boot();
        PlayerSession returning = join(second, "Persistor");
        assertEquals(new Position(12.5, 5.0, -7.25), returning.position(),
                "the saved spot replaces the spawn point");
        assertEquals(new Rotation(97.5f, -18.0f), returning.rotation());

        var slots = returning.inventory().snapshot();
        assertEquals(5, slots.get(0).count(), "dirt stack restored to hotbar slot 0");
        assertEquals(BuiltinItems.WOODEN_PICKAXE, slots.get(1).type());
        assertEquals(5, slots.get(1).damage(), "tool wear is part of the restored value");
        assertEquals(1, returning.inventory().heldSlot(), "the selected hotbar is restored");
        second.shutdown(null);
    }

    @Test
    void freshPlayersSpawnClean() throws Exception {
        EngineServer server = boot();
        PlayerSession fresh = join(server, "FirstTimer");
        assertEquals(server.world().spawnPosition(), fresh.position());
        assertTrue(fresh.inventory().held().isEmpty());
        server.shutdown(null);
    }

    private static final UUID CORRUPTED = UUID.nameUUIDFromBytes("OfflinePlayer:Corrupted".getBytes());

    @Test
    void corruptPlayerFileQuarantinesAndSpawnsClean() throws Exception {
        EngineServer server = boot();
        Path players = dataDir.resolve("players");
        Files.createDirectories(players);
        Files.writeString(players.resolve(CORRUPTED + ".zpd"), "this is not a ZPD file");

        PlayerSession player = join(server, "Corrupted");
        assertEquals(server.world().spawnPosition(), player.position(),
                "corrupt data loads as absent, not as a broken spawn");
        assertTrue(player.inventory().held().isEmpty());
        await(() -> Files.exists(players.resolve(CORRUPTED + ".zpd.corrupt")),
                "corrupt file preserved beside the storage path");
        server.shutdown(null);
    }

    @Test
    void shutdownPersistsConnectedPlayers() throws Exception {
        EngineServer server = boot();
        PlayerSession player = join(server, "ShutdownHolder");
        player.applyMovement(new Position(-3.5, 5.0, 9.5), new Rotation(0f, 0f), true);
        assertTrue(player.inventory().pickUp(ItemStack.of(BuiltinItems.COBBLESTONE, 7)).isEmpty());

        server.shutdown(null); // no explicit disconnect: the shutdown path must save

        EngineServer second = boot();
        PlayerSession returning = join(second, "ShutdownHolder");
        assertEquals(new Position(-3.5, 5.0, 9.5), returning.position(),
                "shutdown persisted the position");
        assertEquals(7, returning.inventory().snapshot().get(0).count(),
                "shutdown persisted the inventory");
        second.shutdown(null);
    }
}
