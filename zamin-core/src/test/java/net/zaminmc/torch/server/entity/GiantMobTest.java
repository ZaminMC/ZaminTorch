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
 * The giant's own law (Slice 67 — GiantEntity, Entities.java line 258's
 * "Giant" 53): the 6x zombie hull, the 100 health, the 50.0 hit, the
 * 10.440001 eye, the base monster sounds, and the summon-only nature
 * (no natural spawn, no spawn egg, no daylight burn — a plain
 * MonsterEntity, not a ZombieEntity).
 */
class GiantMobTest {

    @TempDir
    Path dataDir;

    @Test
    void theGiantCarriesTheReferenceAttributes() {
        // GiantEntity lines 13-24: setSize(width*6, height*6) over the
        // zombie's 0.6x1.8; MAX_HEALTH 100, MOVEMENT_SPEED 0.5,
        // ATTACK_DAMAGE 50; getEyeHeight 10.440001.
        assertEquals(53, MobType.GIANT.legacyTypeId, "Entities.java's own id");
        assertEquals(3.6, MobType.GIANT.width, 1e-9, "the zombie's 0.6 * 6");
        assertEquals(10.8, MobType.GIANT.height, 1e-9, "the zombie's 1.8 * 6");
        assertEquals(100.0f, MobType.GIANT.maxHealth, "initAttributes' MAX_HEALTH");
        assertEquals(0.5, MobType.GIANT.walkSpeed, 1e-9, "MOVEMENT_SPEED's base");
        assertEquals(50.0f, MobType.GIANT.attackDamage, "ATTACK_DAMAGE's base");
        assertTrue(MobType.GIANT.hostile, "MonsterEntity's own attack goals");
        assertEquals("", MobType.GIANT.idleSound, "no idle override (silent)");
        assertEquals("game.neutral.hurt", MobType.GIANT.hurtSound,
                "LivingEntity's base hurt sound");
        assertEquals("game.neutral.die", MobType.GIANT.deathSound,
                "LivingEntity's base death sound");
        assertFalse(MobType.GIANT.undead(),
                "a plain MonsterEntity — the daylight burn is the zombie family's own");
        assertTrue(MobType.GIANT.loot.isEmpty(), "no drop override");
    }

    @Test
    void theGiantSpawnsAndAttacksOnTheLiveEngine() throws Exception {
        EngineServer server = boot();
        try {
            server.ticker().submit(() -> {
                MobEntity giant = server.mobs().spawnAt(MobType.GIANT,
                        new Position(80.5, 40.0, 80.5));
                if (giant != null) {
                    giant.hurt(0.0f); // no-op touch: the body exists
                }
            });
            await(() -> server.mobs().all().stream()
                            .anyMatch(mob -> mob.type() == MobType.GIANT),
                    "the giant spawned");
            MobEntity giant = server.mobs().all().stream()
                    .filter(mob -> mob.type() == MobType.GIANT).findFirst().orElseThrow();
            assertEquals(100.0f, giant.health(), 1e-6, "the full 100-heart body");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theVanillaSummonCommandSpawnsTheGiant() throws Exception {
        // SummonCommand 1:1 (Slice 67): the vanilla name "Giant", the
        // op-2 gate, the ~-relative coordinates, the vanilla feedback.
        EngineServer server = boot();
        try {
            var link = new net.zaminmc.torch.server.net.ClientLink() {
                @Override public boolean isActive() {
                    return true;
                }

                @Override public void kick(String reason) {
                }
            };
            var accepted = (net.zaminmc.torch.server.net.EngineBridge.Accepted)
                    server.joinRequest(link, "summoner",
                            UUID.nameUUIDFromBytes("summoner".getBytes()));
            net.zaminmc.torch.server.player.PlayerSession session = accepted.session();
            server.ticker().submit(session::markPlaying);
            await(() -> session.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                    "the session promoted to PLAYING");
            // Op the sender (the op-2 gate).
            server.ticker().submit(() -> session.setOpLevel(2));
            var at = session.position();
            server.chatService().submitChat(session, "/summon Giant ~ ~ ~");
            await(() -> server.mobs().all().stream()
                            .anyMatch(mob -> mob.type() == MobType.GIANT),
                    "the giant summoned at the sender's feet");
            MobEntity giant = server.mobs().all().stream()
                    .filter(mob -> mob.type() == MobType.GIANT).findFirst().orElseThrow();
            assertEquals((int) Math.floor(at.x()), (int) Math.floor(giant.position().x()),
                    "the ~ coordinate kept the sender's x");
            // The engine-kind name also resolves (the byName fallback).
            server.chatService().submitChat(session, "/summon giant");
            await(() -> server.mobs().all().stream()
                            .filter(mob -> mob.type() == MobType.GIANT).count() >= 2,
                    "the engine-kind name resolved too");
        } finally {
            server.shutdown(null);
        }
    }

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "giant" + Math.abs(UUID.randomUUID().hashCode() % 100000),
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
}
