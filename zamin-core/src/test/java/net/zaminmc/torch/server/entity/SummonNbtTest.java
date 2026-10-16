package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The /summon NBT arm (Slice 75 — SummonCommand lines 65-74 1:1): the
 * joined text parses as one compound, the engine's mob model carries the
 * reference's Entity rows (the custom-name pair, the health, the NoAI
 * pin, the persistence mark, the invulnerability flag), a parse failure
 * answers the tagError line without spawning, and the rows survive the
 * restart (the ZMD v3 block).
 */
class SummonNbtTest {

    @TempDir
    Path dataDir;

    private EngineServer boot() throws Exception {
        // The FIXED world name (the restart test's second boot must read
        // the same world directory — a random name per boot would read a
        // fresh world and restore nothing).
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "snbtworld",
                "it", 20, 2, 20, dataDir.toString(),
                net.zaminmc.torch.GameMode.SURVIVAL);
        EngineServer server = new EngineServer(config);
        server.start();
        return server;
    }

    private void await(java.util.function.BooleanSupplier condition, String description)
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

    private net.zaminmc.torch.server.player.PlayerSession opSession(EngineServer server)
            throws InterruptedException {
        var link = new net.zaminmc.torch.server.net.ClientLink() {
            @Override public boolean isActive() {
                return true;
            }

            @Override public void kick(String reason) {
            }
        };
        var accepted = (net.zaminmc.torch.server.net.EngineBridge.Accepted)
                server.joinRequest(link, "op",
                        UUID.nameUUIDFromBytes("op".getBytes()));
        net.zaminmc.torch.server.player.PlayerSession session = accepted.session();
        server.ticker().submit(session::markPlaying);
        await(() -> session.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                "the session promoted to PLAYING");
        server.ticker().submit(() -> session.setOpLevel(2));
        return session;
    }

    @Test
    void theSummonNbtRowsLandOnTheBody() throws Exception {
        EngineServer server = boot();
        try {
            var session = opSession(server);
            server.chatService().submitChat(session,
                    "/summon Zombie ~ ~ ~ {CustomName:\"Test Zombie\","
                            + "CustomNameVisible:1b,Health:5.0f,NoAI:1b,"
                            + "PersistenceRequired:1b,Invulnerable:1b}");
            await(() -> server.mobs().all().stream()
                            .anyMatch(mob -> mob.type() == MobType.ZOMBIE),
                    "the zombie summoned");
            MobEntity zombie = server.mobs().all().stream()
                    .filter(mob -> mob.type() == MobType.ZOMBIE).findFirst().orElseThrow();
            assertEquals("Test Zombie", zombie.customName(),
                    "CustomName landed");
            assertTrue(zombie.customNameVisible(),
                    "CustomNameVisible landed");
            assertEquals(5.0f, zombie.health(), 1e-6,
                    "Health landed (the zombie's own 20 replaced)");
            assertTrue(zombie.noAi(), "NoAI landed");
            assertTrue(zombie.invulnerable(), "Invulnerable landed");
            assertTrue(zombie.persistent(),
                    "PersistenceRequired landed (and the name derives it too)");
            // The invulnerable pin refuses every wound (Entity.isInvulnerable).
            server.ticker().submit(() ->
                    server.mobs().hurt(zombie, 100.0f, 0.0));
            Thread.sleep(150);
            assertFalse(zombie.dead(), "the pinned body refused the wound");
            assertEquals(5.0f, zombie.health(), 1e-6, "the health never moved");
            // The NoAI pin: the body stands exactly still (the mind walk
            // never runs — the position's fixed point never moves).
            long before = Double.doubleToLongBits(zombie.position().x())
                    ^ Double.doubleToLongBits(zombie.position().z());
            Thread.sleep(1_000); // 20 ticks
            long after = Double.doubleToLongBits(zombie.position().x())
                    ^ Double.doubleToLongBits(zombie.position().z());
            assertEquals(before, after, "the pinned body never moved");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theTagErrorRefusesWithoutSpawning() throws Exception {
        EngineServer server = boot();
        try {
            var session = opSession(server);
            server.chatService().submitChat(session,
                    "/summon Zombie ~ ~ ~ {CustomName:\"unterminated}");
            Thread.sleep(400);
            assertTrue(server.mobs().all().stream()
                            .noneMatch(mob -> mob.type() == MobType.ZOMBIE),
                    "the broken tag never spawned the body");
            // A valid tag with NO rows the engine reads still summons (the
            // reference's own shape: the parse succeeds, the create runs).
            server.chatService().submitChat(session,
                    "/summon Zombie ~ ~ ~ {SomeUnknownRow:1b}");
            await(() -> server.mobs().all().stream()
                            .anyMatch(mob -> mob.type() == MobType.ZOMBIE),
                    "the unknown-row tag still summoned");
            MobEntity zombie = server.mobs().all().stream()
                    .filter(mob -> mob.type() == MobType.ZOMBIE).findFirst().orElseThrow();
            assertEquals(null, zombie.customName(), "no name row landed");
            assertFalse(zombie.noAi(), "no pin row landed");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theNbtRowsSurviveTheRestart() throws Exception {
        // The ZMD v3 block: the rows land on the fresh body after the
        // restart (the save on shutdown + the restore at boot).
        EngineServer server = boot();
        try {
            var session = opSession(server);
            server.chatService().submitChat(session,
                    "/summon Zombie ~ ~ ~ {CustomName:\"Keeper\",NoAI:1b,"
                            + "Invulnerable:1b,PersistenceRequired:1b}");
            await(() -> server.mobs().all().stream()
                            .anyMatch(mob -> mob.type() == MobType.ZOMBIE
                                    && "Keeper".equals(mob.customName())),
                    "the named zombie summoned");
        } finally {
            server.shutdown(null); // the save walks here
        }
        EngineServer restarted = boot();
        try {
            await(() -> restarted.mobs().all().stream()
                            .anyMatch(mob -> "Keeper".equals(mob.customName())),
                    "the named zombie restored");
            MobEntity keeper = restarted.mobs().all().stream()
                    .filter(mob -> "Keeper".equals(mob.customName())).findFirst().orElseThrow();
            assertTrue(keeper.noAi(), "the NoAI pin restored");
            assertTrue(keeper.invulnerable(), "the invulnerability restored");
            assertTrue(keeper.persistent(), "the persistence restored");
        } finally {
            restarted.shutdown(null);
        }
    }

    @Test
    void theCustomNamedBodyNeverDespawns() throws Exception {
        // The reference's checkDespawn gate: a CUSTOM-NAMED body never
        // despawns (persistent() derives it live).
        EngineServer server = boot();
        try {
            server.ticker().submit(() -> {
                MobEntity named = server.mobs().spawnAt(MobType.ZOMBIE,
                        new Position(60.5, 40.0, 60.5));
                if (named != null) {
                    named.setCustomName("Stays");
                }
                // The control: an unnamed zombie beside it.
                server.mobs().spawnAt(MobType.ZOMBIE, new Position(62.5, 40.0, 62.5));
            });
            await(() -> server.mobs().all().stream()
                            .filter(mob -> mob.type() == MobType.ZOMBIE).count() >= 2,
                    "both zombies spawned");
            MobEntity named = server.mobs().all().stream()
                    .filter(mob -> "Stays".equals(mob.customName())).findFirst().orElseThrow();
            assertTrue(named.persistent(),
                    "the named body derives the persistence");
            // The store's raw row stays honest (the name derives live, the
            // raw mark is not silently written).
            assertFalse(named.rawPersistent(),
                    "the raw persistence mark is untouched by the name");
        } finally {
            server.shutdown(null);
        }
    }
}
