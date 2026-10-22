package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.Server;
import net.zaminmc.torch.ServerState;
import net.zaminmc.torch.entity.Player;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.stat.EngineAchievements;
import net.zaminmc.torch.stat.Achievements;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The achievement family's public API surface (§27.3 — the VIII6
 * exposure, the §27.10 capability test): the 34-row table read (the
 * census with its pinned rows — the root, the parent chain, the two
 * damage-carrying icons, the translation keys, the give-all order law)
 * and the per-player unlocked set (empty for a fresh player, a key
 * unlocking through the established increment walk, the parent gate
 * refusing an orphaned achievement), exercised over the LIVE engine
 * through the api types alone — every capability read goes through
 * {@link Server} and {@link Achievements} with no cast to engine
 * internals (the engine appears in the harness alone: the boot, the
 * join walk, and the owner-lane increments that drive the scene, the
 * same form as the census test's own scene calls).
 */
class AchievementsApiAcceptanceTest {

    @TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "aaa" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString(),
                GameMode.SURVIVAL);
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() throws InterruptedException {
        server.shutdown(null);
        await(() -> server.state() == ServerState.STOPPED, "server stopped");
    }

    /**
     * The 34-row table through the public surface: the census, the
     * pinned rows (the root's own datums, the mineWood chain, the two
     * damage-carrying icons, the final row), the by-key lookup, and the
     * table's own consistency law — every parent a declared row, every
     * parent strictly before its children (the give-all walk's order),
     * exactly one root.
     */
    @Test
    void theAchievementTableReadsThroughThePublicSurface() {
        // The api handle alone — no engine types from here on.
        Server api = server;
        Achievements table = api.achievements();
        assertNotNull(table, "the api surface is wired");
        List<Achievements.Achievement> all = table.all();
        assertNotNull(all, "the table enumerates");
        assertFalse(all.isEmpty(), "the live view answers the real table (never EMPTY)");

        // THE CENSUS: the reference's own 34 rows.
        assertEquals(34, all.size(), "the reference's 34 achievements");

        // THE ROOT (Achievements.java's first row — the local one):
        // column 0, row 0, no parent, the book icon, the local flag.
        Achievements.Achievement root = all.get(0);
        assertEquals("achievement.openInventory", root.key(), "the root's stat key");
        assertEquals("openInventory", root.name(), "the root's short name");
        assertEquals(0, root.column(), "the root's page column");
        assertEquals(0, root.row(), "the root's page row");
        assertNull(root.parent(), "the root has no parent");
        assertTrue(root.local(), "the root is the one local row (no server broadcast)");
        assertFalse(root.challenge(), "the root is no challenge");
        assertEquals("minecraft", root.icon().namespace(), "the root's icon namespace");
        assertEquals("book", root.icon().value(), "the root's icon is the book");
        assertEquals(0, root.iconDamage(), "the root's icon carries no damage");
        assertEquals("achievement.openInventory", root.nameKey(), "the name translation key");
        assertEquals("achievement.openInventory.desc", root.descriptionKey(),
                "the description translation key");

        // THE KEY LOOKUP (the by-key view over the achievement band).
        var mineWood = table.byKey("achievement.mineWood");
        assertTrue(mineWood.isPresent(), "the by-key lookup finds the row");
        assertEquals("mineWood", mineWood.get().name(), "the row's short name");
        assertEquals(2, mineWood.get().column(), "the row's page column");
        assertEquals(1, mineWood.get().row(), "the row's page row");
        assertSame(root, mineWood.get().parent(),
                "the parent is the table's own root row (the mapped instance, not a copy)");
        assertFalse(mineWood.get().local(), "mineWood rides the broadcast band");
        assertEquals("achievement.mineWood", mineWood.get().nameKey(), "the name key");
        assertEquals("achievement.mineWood.desc", mineWood.get().descriptionKey(),
                "the description key");
        assertFalse(table.byKey("achievement.nope").isPresent(),
                "a key outside the band answers empty");
        assertFalse(table.byKey("stat.leaveGame").isPresent(),
                "a general stat key answers empty (the band is the achievements alone)");

        // THE DAMAGE-CARRYING ICONS (the two meta rows the reference's
        // own declarations carry): the wither skull (meta 1) and the
        // enchanted golden apple (meta 1).
        var wither = table.byKey("achievement.spawnWither");
        assertTrue(wither.isPresent(), "the skull row present");
        assertEquals("skull", wither.get().icon().value(), "the skull row's icon");
        assertEquals(1, wither.get().iconDamage(), "the skull row's icon damage");
        var overpowered = table.byKey("achievement.overpowered");
        assertTrue(overpowered.isPresent(), "the apple row present");
        assertEquals("golden_apple", overpowered.get().icon().value(), "the apple row's icon");
        assertEquals(1, overpowered.get().iconDamage(), "the apple row's icon damage");

        // THE FINAL ROW (the reference's declaration order ends here).
        Achievements.Achievement last = all.get(all.size() - 1);
        assertEquals("overpowered", last.name(), "the last declared row");
        assertTrue(last.challenge(), "the enchanted-apple row is a challenge");

        // THE PARENT-CHAIN CONSISTENCY (the give-all walk's own law):
        // every parent a declared row, every parent STRICTLY BEFORE the
        // child that gates on it (the order the give-all walk rides
        // forward), exactly one root.
        int roots = 0;
        int challenges = 0;
        for (int index = 0; index < all.size(); index++) {
            Achievements.Achievement row = all.get(index);
            if (row.challenge()) {
                challenges++;
            }
            if (row.parent() == null) {
                roots++;
                assertSame(root, row, "the table's one root is the openInventory row");
                continue;
            }
            int parentIndex = all.indexOf(row.parent());
            assertTrue(parentIndex >= 0,
                    "the parent is itself a declared row: " + row.key());
            assertTrue(parentIndex < index,
                    "the parent comes strictly before the child (the give-all order): "
                            + row.key());
        }
        assertEquals(1, roots, "exactly one root (the openInventory row)");
        assertEquals(10, challenges, "the reference's own challenge rows");
    }

    /**
     * The per-player unlocked set through the public surface: empty for
     * a fresh player, a key unlocking through the established increment
     * walk (the owner-lane increments the engine's own award sites
     * ride), the parent gate refusing an orphaned achievement (the
     * increment the engine's own condition refuses never surfaces), the
     * declaration-order answer, and the §27.5 defined behavior for a
     * handle the engine does not own.
     */
    @Test
    void thePerPlayerUnlockedSetReadsThroughThePublicSurface() throws Exception {
        Server api = server;
        Achievements table = api.achievements();
        PlayerSession alice = join("Alice");
        // The api reads take the SESSION as the api handle (the engine
        // session IS the handle — §27.5); the scene's increments ride
        // the owner lane (the established walk the award sites use).
        Player handle = alice;

        // THE FRESH PLAYER: nothing unlocked (the join walk awards no
        // achievement — the openInventory row waits for the client's
        // own open-inventory packet).
        assertTrue(table.unlocked(handle).isEmpty(),
                "a fresh player holds no unlocked achievement");

        // THE UNLOCK (the established increment walk — no parent, the
        // gate passes):
        server.ticker().submit(() ->
                alice.stats().increment(EngineAchievements.OPEN_INVENTORY.key(), 1));
        await(() -> table.unlocked(handle).size() == 1,
                "the root row unlocks through the increment walk");
        assertEquals("achievement.openInventory", table.unlocked(handle).get(0).key(),
                "the unlocked row is the root");

        // THE PARENT GATE (the orphaned refusal — PlayerStats
        // .increment's own condition): the crafting-table row gates on
        // the mineWood chain, which Alice has not earned; the walk
        // runs, the gate refuses, the api read never surfaces the row.
        CountDownLatch refused = new CountDownLatch(1);
        server.ticker().submit(() -> {
            alice.stats().increment(EngineAchievements.CRAFT_CRAFTING_TABLE.key(), 1);
            refused.countDown();
        });
        assertTrue(refused.await(10, TimeUnit.SECONDS),
                "the refused increment ran on the owner lane");
        List<Achievements.Achievement> afterRefusal = table.unlocked(handle);
        assertEquals(1, afterRefusal.size(),
                "the parent gate refused the orphaned achievement (the counter never rose)");

        // THE CHAINED UNLOCK (the parent now earned — the same walk
        // passes): the answer in the table's own declaration order.
        server.ticker().submit(() ->
                alice.stats().increment(EngineAchievements.GET_LOG.key(), 1));
        await(() -> table.unlocked(handle).size() == 2,
                "the chained row unlocks once its parent is earned");
        assertEquals(
                List.of("achievement.openInventory", "achievement.mineWood"),
                table.unlocked(handle).stream().map(Achievements.Achievement::key).toList(),
                "the unlocked set in the table's declaration order");

        // THE UNOWNED HANDLE (§27.5's defined behavior for foreign
        // implements): a fabricated Player the engine does not own
        // answers the empty set, never a throw.
        Player fabricated = (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    Class<?> returnType = method.getReturnType();
                    if (returnType == boolean.class) {
                        return false;
                    }
                    if (returnType == int.class) {
                        return 0;
                    }
                    if (returnType == float.class) {
                        return 0f;
                    }
                    if (returnType == long.class) {
                        return 0L;
                    }
                    return null;
                });
        assertTrue(table.unlocked(fabricated).isEmpty(),
                "a handle the engine does not own answers the empty set");
    }

    // ------------------------------------------------------------- harness

    private PlayerSession join(String name) throws InterruptedException {
        var accepted = server.joinRequest(link(), name,
                UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes()));
        PlayerSession session = ((EngineBridge.Accepted) accepted).session();
        server.ticker().submit(session::markPlaying);
        await(() -> session.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                name + " playing");
        return session;
    }

    private ClientLink link() {
        return new ClientLink() {
            @Override
            public boolean isActive() {
                return true;
            }

            @Override
            public void kick(String reason) {
            }
        };
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
}
