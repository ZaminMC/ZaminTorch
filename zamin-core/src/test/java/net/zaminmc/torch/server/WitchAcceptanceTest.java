package net.zaminmc.torch.server;

import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The witch's engine-level combat rules (Slice 12j — the reference's
 * WitchEntity.getDamageAfterEffectsAndEnchantments lines 163-175): the
 * 85% magic shave (poison and splash potions alike) and the own-splash
 * immunity (attacker == self), both landing through the engine's potion
 * bodies on a real booted server.
 */
class WitchAcceptanceTest {

    @TempDir
    Path dataDir;

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "witch", "it", 20, 2, 20,
                dataDir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        return server;
    }

    private net.zaminmc.torch.server.player.PlayerSession join(EngineServer server, String name)
            throws InterruptedException {
        var accepted = server.joinRequest(link(), name, UUID.nameUUIDFromBytes(name.getBytes()));
        net.zaminmc.torch.server.player.PlayerSession session =
                ((EngineBridge.Accepted) accepted).session();
        await(() -> server.playerRegistry().byName(name).isPresent(), name + " registered");
        server.joinCompleted(session);
        await(() -> session.state() == net.zaminmc.torch.entity.PlayerState.PLAYING, name + " playing");
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

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    private MobEntity spawnWitch(EngineServer server) throws InterruptedException {
        var player = join(server, "Tester");
        server.chatService().submitChat(player, "/spawnmob witch 1");
        await(() -> server.mobs().all().stream()
                .anyMatch(m -> m.type() == MobType.WITCH), "witch present");
        MobEntity witch = server.mobs().all().stream()
                .filter(m -> m.type() == MobType.WITCH)
                .findFirst().orElseThrow();
        assertEquals(MobType.WITCH.maxHealth, witch.health(), 1e-6f);
        return witch;
    }

    /** Runs a splash inside the tick lane and reports the health delta. */
    private float splashDelta(EngineServer server, MobEntity witch, Object thrower)
            throws InterruptedException {
        float[] delta = new float[] {Float.NEGATIVE_INFINITY};
        Position at = witch.position();
        server.world().execute(() -> {
            float before = witch.health();
            server.potions().splash(at.x(), at.y() + 1.0, at.z(), 32732,
                    witch, thrower);
            delta[0] = before - witch.health();
        });
        await(() -> delta[0] != Float.NEGATIVE_INFINITY, "the splash ran");
        return delta[0];
    }

    @Test
    void theWitchImmunesItsOwnSplash() throws Exception {
        EngineServer server = boot();
        MobEntity witch = spawnWitch(server);

        // WitchEntity.getDamageAfterEffectsAndEnchantments: the witch's
        // own potion never lands (attacker == this → 0) — the 32732
        // harming splash would take a pig for 6 (the direct hit answers
        // proximity 1.0). The delta reads inside the tick lane so the
        // witch's own live AI (which may self-poison through the splash's
        // timed arm — the reference's own behavior) cannot pollute it.
        assertEquals(0.0f, splashDelta(server, witch, witch), 1e-6f,
                "the witch's own splash never lands");
        server.shutdown(null);
    }

    @Test
    void theWitchPaysTheMagicShave() throws Exception {
        EngineServer server = boot();
        MobEntity witch = spawnWitch(server);

        // A foreign magic source (the null thrower — the dispenser's own
        // damage source shape): the 6.0 instant damage pays the 85% shave —
        // 6 * 0.15 = 0.9 off the 26.
        assertEquals(0.9f, splashDelta(server, witch, null), 1e-4f,
                "the magic damage pays the 85% witch shave");
        server.shutdown(null);
    }

    @Test
    void theWitchesPoisonTickIsAlmostNothing() throws Exception {
        EngineServer server = boot();
        MobEntity witch = spawnWitch(server);

        // The poison's periodic tick rides DamageSource.MAGIC too: the
        // reference's 1.0 per cadence becomes 0.15 for the witch (the
        // engine's hurt carries the cause — the shave keys on magic).
        server.potions().add(witch, new net.zaminmc.torch.server.potion.StatusEffectInstance(
                net.zaminmc.torch.server.potion.StatusEffects.POISON, 100, 0));
        float before = witch.health();
        await(() -> witch.health() < before, "the poison ticked");
        // The shaved tick: 1.0 * 0.15 = 0.15 per cadence (the 25-tick
        // clock) — never the full point.
        assertTrue(before - witch.health() <= 0.15f + 1e-4f,
                "the poison tick paid the shave (was " + (before - witch.health()) + ")");
        server.shutdown(null);
    }
}
