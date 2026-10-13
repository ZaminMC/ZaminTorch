package net.zaminmc.torch.server;

import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Path;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The peaceful difficulty's own law (Slice 19e — the reference's
 * MonsterEntity.tick / canSpawn / PlayerEntity.tick / takeDamage arms):
 * the hostile sweep (whatever path brought the body), the natural spawn
 * gate, and the player's regen family.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PeacefulDifficultyTest {

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    private EngineServer boot() throws InterruptedException {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "itest", "it", 20, 4, 20,
                dataDir.toString(), net.zaminmc.torch.GameMode.SURVIVAL);
        EngineServer started = new EngineServer(config);
        started.start();
        return started;
    }

    private PlayerSession join(EngineServer server, String name) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        var result = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        PlayerSession session = ((EngineBridge.Accepted) result).session();
        // The real login's completion flip (the client's own play-state
        // handshake lands the session in PLAYING; the harness drives the
        // same transition on the engine thread — the food economy only
        // runs for playing bodies).
        server.ticker().submit(session::markPlaying);
        return session;
    }

    private static net.zaminmc.torch.server.chat.CommandSender console() {
        return new net.zaminmc.torch.server.chat.CommandSender() {
            @Override public String name() {
                return "Console";
            }

            @Override public void sendMessage(String message) {
            }

            @Override public int opLevel() {
                return 4;
            }

            @Override public net.zaminmc.torch.server.player.PlayerSession player() {
                return null; // the console has no player body
            }
        };
    }

    private void await(BooleanSupplier condition, String description)
            throws InterruptedException {
        await(condition, description, 5_000);
    }

    private void await(BooleanSupplier condition, String description,
            long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    @Test
    void thePeacefulSweepRemovesTheHostilesAndSparesTheRest() throws Exception {
        EngineServer server = boot();
        try {
            var player = join(server, "Shepherd");
            server.chatService().dispatchCommand(console(), "/difficulty normal");
            server.chatService().submitChat(player, "/spawnmob zombie 1");
            server.chatService().submitChat(player, "/spawnmob pig 1");
            await(() -> server.mobs().all().stream()
                    .anyMatch(m -> m.type() == MobType.ZOMBIE)
                    && server.mobs().all().stream().anyMatch(m -> m.type() == MobType.PIG),
                    "both bodies present");

            server.chatService().dispatchCommand(console(), "/difficulty peaceful");
            await(() -> server.difficulty() == 0, "the peaceful tier landed");
            // MonsterEntity.tick lines 33-38: the hostile leaves the very
            // next tick whatever brought it; the pig never leaves.
            await(() -> server.mobs().all().stream()
                    .noneMatch(m -> m.type() == MobType.ZOMBIE),
                    "the zombie left on the peaceful sweep");
            assertTrue(server.mobs().all().stream()
                            .anyMatch(m -> m.type() == MobType.PIG),
                    "the animal stays on peaceful");

            // A body arriving AFTER the switch leaves the same way (the
            // per-tick sweep — the command-time removal alone would miss
            // it).
            server.chatService().submitChat(player, "/spawnmob creeper 1");
            await(() -> server.mobs().all().stream()
                    .noneMatch(m -> m.type() == MobType.CREEPER),
                    "a late hostile never survives the sweep");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void thePeacefulWorldNeverSpawnsMonsters() throws Exception {
        EngineServer server = boot();
        try {
            var player = join(server, "Watcher");
            server.chatService().dispatchCommand(console(), "/difficulty peaceful");
            await(() -> server.difficulty() == 0, "the peaceful tier landed");
            // The night hours: the monster cycle's own gate window.
            server.world().execute(() -> server.world().setTimeOfDay(14_000));
            await(() -> server.world().timeOfDay() >= 14_000
                            && server.world().timeOfDay() < 15_000,
                    "the night hours landed");
            // MonsterEntity.canSpawn line 131: no monster ever spawns on a
            // peaceful world — the whole night passes empty-handed.
            Thread.sleep(500); // ten seconds of engine ticks
            assertEquals(0, server.mobs().all().stream()
                            .filter(m -> m.type().hostile).count(),
                    "the monster category never spawns on peaceful");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void thePeacefulRegenRestoresHealthAndFood() throws Exception {
        EngineServer server = boot();
        try {
            var player = join(server, "Healer");
            server.chatService().dispatchCommand(console(), "/difficulty peaceful");
            await(() -> server.difficulty() == 0, "the peaceful tier landed");
            // A wounded, hungry body (PlayerEntity.tick lines 443-450: the
            // peaceful family heals both).
            server.world().execute(() -> player.setBody(6.0f, 3, 0.0f));
            await(() -> player.health() < 10.0f, "the wounded body landed");

            // PlayerEntity lines 443-446: a heart a second (20 ticks) — the
            // full walk from six hearts rides the vanilla clock (~14
            // seconds), both regen arms beside each other exactly as the
            // reference runs them.
            await(() -> player.health() >= 20.0f,
                    "the peaceful regen restored the health", 30_000);
            // Lines 448-449: a food point a half-second (10 ticks) — the
            // refill runs beside the heal.
            await(() -> player.food() >= 20,
                    "the peaceful family refilled the food", 30_000);
            assertFalse(player.dead(), "the body lives");
        } finally {
            server.shutdown(null);
        }
    }
}
