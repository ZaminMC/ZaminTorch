package net.zaminmc.torch.server;

import net.zaminmc.torch.event.DamageCause;
import net.zaminmc.torch.event.EventSubscription;
import net.zaminmc.torch.event.entity.MobDamageEvent;
import net.zaminmc.torch.event.entity.MobDeathEvent;
import net.zaminmc.torch.event.player.PlayerDamageEvent;
import net.zaminmc.torch.event.player.PlayerDeathEvent;
import net.zaminmc.torch.event.player.PlayerJoinEvent;
import net.zaminmc.torch.event.player.PlayerQuitEvent;
import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.MobType;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The lifecycle & damage event wave (Slice 14c — the §27.7 second family)
 * over the live engine: a plugin-style consumer registers through the
 * public bus — no engine internals — and observes/cancels the player
 * join/quit/death/damage walks and the mob damage/death walks.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LifecycleEventAcceptanceTest {

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

    private PlayerSession join(String name, Position at) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        var result = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        PlayerSession session = ((EngineBridge.Accepted) result).session();
        server.joinCompleted(session); // the wire's handshake completes in the harness
        return session;
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
    void theJoinFiresWithTheVanillaLine() throws Exception {
        server = boot();
        AtomicReference<PlayerJoinEvent> seen = new AtomicReference<>();
        EventSubscription sub = server.events().on(PlayerJoinEvent.class, seen::set);

        PlayerSession player = join("Joiner", new Position(0.5, 5, 0.5));
        assertNotNull(seen.get(), "the join event fired");
        assertEquals(player.uuid(), seen.get().player().uuid(), "the handle is the session");
        assertEquals("Joiner joined the game", seen.get().joinMessage(),
                "the vanilla multiplayer.player.joined line");
        sub.unsubscribe();
        server.shutdown(null);
    }

    @Test
    void theQuitFiresWhileTheHandleStillResolves() throws Exception {
        server = boot();
        PlayerSession player = join("Quitter", new Position(0.5, 5, 0.5));
        AtomicReference<PlayerQuitEvent> seen = new AtomicReference<>();
        EventSubscription sub = server.events().on(PlayerQuitEvent.class, seen::set);

        server.clientDisconnected(player, "test end");
        assertNotNull(seen.get(), "the quit event fired");
        assertEquals("Quitter", seen.get().player().name(),
                "the handle still resolves at the event");
        assertEquals("Quitter left the game", seen.get().quitMessage(),
                "the vanilla multiplayer.player.left line");
        assertFalse(server.players().stream()
                        .anyMatch(p -> p.uuid().equals(player.uuid())),
                "the registry dropped the session after the event");
        sub.unsubscribe();
        server.shutdown(null);
    }

    @Test
    void aCancelledPlayerDamageLandsNothing() throws Exception {
        server = boot();
        PlayerSession player = join("Tank", new Position(0.5, 5, 0.5));
        await(() -> player.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                "session reaches PLAYING");

        // Cancel every MELEE hit: the plugin refuses the damage bridge's
        // family (the semantic entry's documented cause).
        EventSubscription sub = server.events().on(PlayerDamageEvent.class, event -> {
            if (event.cause() == DamageCause.MELEE) {
                event.cancel();
            }
        });
        server.damage(player, 5.0f);
        Thread.sleep(300);
        assertEquals(20.0f, player.health(), 1e-6f,
                "the cancelled damage never landed");

        // With the gate open the same hit bites.
        sub.unsubscribe();
        server.damage(player, 5.0f);
        await(() -> player.health() <= 15.0f, "the damage lands after the unsubscribe");
        server.shutdown(null);
    }

    @Test
    void theDamageAmountIsMutable() throws Exception {
        server = boot();
        PlayerSession player = join("Sponge", new Position(0.5, 5, 0.5));
        await(() -> player.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                "session reaches PLAYING");

        EventSubscription sub = server.events().on(PlayerDamageEvent.class, event -> {
            if (event.cause() == DamageCause.MELEE) {
                event.amount(1.0f); // today's hits bite softly
            }
        });
        server.damage(player, 5.0f);
        await(() -> player.health() == 19.0f, "the softened damage landed exactly");
        sub.unsubscribe();
        server.shutdown(null);
    }

    @Test
    void aCancelledDeathKeepsTheInventory() throws Exception {
        server = boot();
        PlayerSession player = join("Keeper", new Position(0.5, 5, 0.5));
        await(() -> player.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                "session reaches PLAYING");
        server.ticker().submit(() -> {
            player.give(ItemStack.of(
                    net.zaminmc.torch.server.item.BuiltinItems.lookup(
                            net.zaminmc.torch.util.Identifier.parse("minecraft:stone")).orElseThrow(),
                    10));
            player.setTotalXp(500);
        });
        await(() -> player.countOf(net.zaminmc.torch.server.item.BuiltinItems.lookup(
                        net.zaminmc.torch.util.Identifier.parse("minecraft:stone")).orElseThrow()) >= 10,
                "the items are in the inventory");

        EventSubscription sub = server.events().on(PlayerDeathEvent.class,
                event -> event.setCancelled(true)); // keepInventory
        AtomicReference<PlayerDeathEvent> seen = new AtomicReference<>();
        EventSubscription seenSub = server.events().on(PlayerDeathEvent.class, seen::set);

        server.damage(player, 1000.0f);
        await(() -> player.dead(), "the body dies");
        assertNotNull(seen.get(), "the death event fired");
        assertTrue(player.health() <= 0.0f, "the health reached zero");
        assertEquals(10, player.countOf(net.zaminmc.torch.server.item.BuiltinItems.lookup(
                        net.zaminmc.torch.util.Identifier.parse("minecraft:stone")).orElseThrow()),
                "the cancelled death kept the inventory");
        assertTrue(player.totalExperience() > 0,
                "the cancelled death kept the experience");

        sub.unsubscribe();
        seenSub.unsubscribe();
        server.shutdown(null);
    }

    @Test
    void anUncancelledDeathScattersEverything() throws Exception {
        server = boot();
        PlayerSession player = join("Loser", new Position(0.5, 5, 0.5));
        await(() -> player.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                "session reaches PLAYING");
        server.ticker().submit(() -> {
            player.give(ItemStack.of(
                    net.zaminmc.torch.server.item.BuiltinItems.lookup(
                            net.zaminmc.torch.util.Identifier.parse("minecraft:stone")).orElseThrow(),
                    10));
        });
        await(() -> player.countOf(net.zaminmc.torch.server.item.BuiltinItems.lookup(
                        net.zaminmc.torch.util.Identifier.parse("minecraft:stone")).orElseThrow()) >= 10,
                "the items are in the inventory");

        server.damage(player, 1000.0f);
        await(() -> player.dead(), "the body dies");
        assertEquals(0, player.countOf(net.zaminmc.torch.server.item.BuiltinItems.lookup(
                        net.zaminmc.torch.util.Identifier.parse("minecraft:stone")).orElseThrow()),
                "the vanilla death scattered the inventory");
        server.shutdown(null);
    }

    @Test
    void theMobDamageGateCoversTheMeleeAndMagicArms() throws Exception {
        server = boot();
        var pig = server.mobs().spawnAt(MobType.PIG, new Position(0.5, 8, 0.5));
        assertNotNull(pig, "the pig spawned");
        float full = pig.health();

        AtomicInteger fired = new AtomicInteger();
        EventSubscription sub = server.events().on(MobDamageEvent.class, event -> {
            fired.incrementAndGet();
            assertEquals(DamageCause.MAGIC, event.cause(),
                    "the potion arm's cause is MAGIC");
            event.cancel();
        });
        assertFalse(server.mobs().hurtWithoutKnockback(pig, 2.0f),
                "the cancelled hit answers false (the refused takeDamage)");
        assertFalse(server.mobs().hurtWithoutKnockback(pig, 2.0f),
                "the second cancelled hit answers false too");
        // The events fired and cancelled: the health never moved.
        assertEquals(2, fired.get(), "the damage events fired");
        assertEquals(full, pig.health(), 1e-6f, "the cancelled magic hit landed nothing");
        sub.unsubscribe();

        assertTrue(server.mobs().hurtWithoutKnockback(pig, 2.0f));
        assertEquals(full - 2.0f, pig.health(), 1e-6f, "the hit lands after the unsubscribe");
        server.shutdown(null);
    }

    @Test
    void theMobDeathObservationFiresAtTheDeathWalk() throws Exception {
        server = boot();
        var pig = server.mobs().spawnAt(MobType.PIG, new Position(0.5, 8, 0.5));
        assertNotNull(pig, "the pig spawned");
        float full = pig.health();

        AtomicReference<MobDeathEvent> seen = new AtomicReference<>();
        EventSubscription sub = server.events().on(MobDeathEvent.class, seen::set);
        assertTrue(server.mobs().hurt(pig, full, 0.0));
        assertNotNull(seen.get(), "the death event fired");
        assertEquals(net.zaminmc.torch.util.Identifier.parse("minecraft:pig"),
                seen.get().entity().typeIdentifier(), "the handle resolves to the pig");
        sub.unsubscribe();
        server.shutdown(null);
    }
}
