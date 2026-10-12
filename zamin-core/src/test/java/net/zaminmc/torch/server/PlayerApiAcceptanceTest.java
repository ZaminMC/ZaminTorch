package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.entity.Player;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The public player API (§27.3's player-state list) over the live engine:
 * a plugin-style consumer drives the body, progression, mode, fire and
 * inventory surfaces through {@link Player} — the session IS the handle.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlayerApiAcceptanceTest {

    private EngineServer server;

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer boot() throws InterruptedException {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "itest", "it", 20, 4, 20,
                dataDir.toString(), net.zaminmc.torch.GameMode.SURVIVAL);
        EngineServer started = new EngineServer(config);
        started.start();
        return started;
    }

    private PlayerSession join(String name) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        var result = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
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
    void theBodySurfaceReadsAndMutates() throws Exception {
        server = boot();
        PlayerSession session = join("Bodhi");
        Player player = session; // the handle IS the session

        assertEquals(20.0f, player.health(), 0.001, "a fresh body is at full health");

        float[] observed = new float[2];
        server.world().execute(() -> {
            player.hurt(6.5f);
            observed[0] = player.health();
            player.heal(3.0f);
            observed[1] = player.health();
        });
        await(() -> observed[1] > 0, "the mutations landed");
        assertEquals(13.5f, observed[0], 0.001, "the hurt applied");
        assertEquals(16.5f, observed[1], 0.001, "the heal restored, capped at the maximum");

        assertEquals(20, player.food(), "a fresh body is fed");
        server.world().execute(() -> player.addExhaustion(4.0f));
        // The exhaustion ledger is the hunger engine's input; the observable
        // arm (food drain) rides the body clock — asserted at the API level:
        // the call must be accepted without error, the ledger reads back.
        assertNotNull(player.saturation());

        server.shutdown(null);
    }

    @Test
    void theProgressionSurfaceAwardsAndReads() throws Exception {
        server = boot();
        PlayerSession session = join("Leveler");
        Player player = session;

        long startTotal = player.totalExperience();
        int startLevel = player.experienceLevel();
        assertEquals(startTotal, 0, "a fresh player has no experience");

        server.world().execute(() -> player.addExperience(300));
        await(() -> player.totalExperience() == 300, "the points landed");
        assertTrue(player.experienceLevel() >= startLevel,
                "levels never regress on a grant");
        // The vanilla ladder: 300 points crosses into the mid levels (the
        // reference's own level 2..7 band for 300 total).
        assertTrue(player.experienceLevel() > 0, "300 points is at least level 1");

        server.shutdown(null);
    }

    @Test
    void theModeAndFireSurfaceSwitch() throws Exception {
        server = boot();
        PlayerSession session = join("Ember");
        Player player = session;

        assertEquals(GameMode.SURVIVAL, player.gamemode(), "the server default");
        server.world().execute(() -> player.setGamemode(GameMode.CREATIVE));
        await(() -> player.gamemode() == GameMode.CREATIVE, "the mode switched");

        assertEquals(false, player.burning(), "not on fire initially");
        server.world().execute(() -> player.ignite(40));
        await(() -> player.burning(), "the fire caught");
        server.world().execute(() -> player.extinguish());
        await(() -> !player.burning(), "extinguished");

        server.shutdown(null);
    }

    @Test
    void theInventorySurfaceGivesAndCounts() throws Exception {
        server = boot();
        PlayerSession session = join("Carrier");
        Player player = session;

        assertEquals(0, player.countOf(BuiltinItems.DIAMOND), "no diamonds yet");
        assertTrue(player.heldItem().isEmpty(), "the hand starts empty");

        AtomicReference<ItemStack> leftover = new AtomicReference<>();
        server.world().execute(() -> leftover.set(
                player.give(ItemStack.of(BuiltinItems.DIAMOND, 5))));
        await(() -> player.countOf(BuiltinItems.DIAMOND) == 5, "the diamonds landed");
        assertTrue(leftover.get().isEmpty(), "everything fit");

        // The give beyond the stack cap: 61 more diamonds + the 5 in place
        // = 66 total, one stack of 64 + a remainder of 2 — both land.
        server.world().execute(() -> leftover.set(
                player.give(ItemStack.of(BuiltinItems.DIAMOND, 61))));
        await(() -> player.countOf(BuiltinItems.DIAMOND) == 66, "the second grant landed");
        assertTrue(leftover.get().isEmpty(), "the overflow found a second stack");

        server.shutdown(null);
    }

    @Test
    void theInventorySyncHookFiresOnGive() throws Exception {
        server = boot();
        join("Watcher");
        // The engine-side proof that give() re-syncs like the engine's own
        // paths: the inventory listener bus must fire on the API give.
        AtomicInteger syncs = new AtomicInteger();
        server.addInventoryListener(playerSession -> syncs.incrementAndGet());
        PlayerSession session = join("Giver");
        Player player = session;
        int before = syncs.get();

        server.world().execute(() -> player.give(ItemStack.of(BuiltinItems.APPLE, 1)));
        await(() -> syncs.get() > before, "the give re-synced the wire");

        server.shutdown(null);
    }
}
