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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cave spider (Slice 18b — CaveSpiderEntity over the spider family):
 * the small venomous body's identity, the flat 0.45 eye, and the poison
 * bite by the difficulty (CaveSpiderEntity.tryDamage lines 25-45) — the
 * normal tier's 7-second curse, the hard tier's 15-second, the weaker
 * rows nothing — landing through the engine's melee pipeline on a real
 * booted server.
 */
class CaveSpiderEntityTest {

    @TempDir
    Path dataDir;

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "cavespider", "it", 20, 2, 20,
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

    /** The console-shaped sender (level 4 — the /difficulty level gate). */
    private static net.zaminmc.torch.server.chat.CommandSender console() {
        return new net.zaminmc.torch.server.chat.CommandSender() {
            @Override public String name() {
                return "Server";
            }

            @Override public void sendMessage(String message) {
            }

            @Override public int opLevel() {
                return 4;
            }

            @Override public net.zaminmc.torch.server.player.PlayerSession player() {
                return null;
            }
        };
    }

    @Test
    void theCaveSpiderCarriesTheIdentity() {
        // Entities.java: the CaveSpider 59. CaveSpiderEntity lines 14-17:
        // the 0.7 x 0.5 body over the spider's own family.
        assertEquals(59, MobType.CAVE_SPIDER.legacyTypeId);
        assertEquals(0.7, MobType.CAVE_SPIDER.width, 1e-9);
        assertEquals(0.5, MobType.CAVE_SPIDER.height, 1e-9);
        // initAttributes line 22: MAX_HEALTH 12 over the spider's 16.
        assertEquals(12.0f, MobType.CAVE_SPIDER.maxHealth, 1e-6f);
        assertTrue(MobType.CAVE_SPIDER.hostile);
        assertTrue(MobType.CAVE_SPIDER.traits.neutralByDay(),
                "the spider family's dark-hours hunt");
        // The family's own sounds (no override).
        assertEquals("mob.spider.say", MobType.CAVE_SPIDER.idleSound);
        assertEquals("mob.spider.death", MobType.CAVE_SPIDER.deathSound);
        // MobType.getMobType line 127: the ARTHROPOD category — Bane of
        // Arthropods lands on the venomous pair.
        assertEquals(2, MobType.CAVE_SPIDER.damageCategory());
    }

    @Test
    void theEyeSitsAtTheFlatBand() {
        class FlatWorld implements MobEntity.WorldQuery {
            @Override
            public boolean isSolid(double x, double y, double z) {
                return Math.floor(y) < 8.0;
            }

            @Override
            public Position nearestPlayer(double x, double y, double z, double range) {
                return null;
            }
        }
        MobEntity spider = new MobEntity(959, MobType.CAVE_SPIDER, new Position(0.5, 8.0, 0.5),
                new java.util.Random(91L), new FlatWorld());
        // CaveSpiderEntity.getEyeHeight line 53: the flat 0.45F — not the
        // generic 0.85 share (0.425) the default would give.
        assertEquals(0.45, spider.eyePosition().y() - spider.position().y(), 1e-9);
    }

    @Test
    void theVenomousBitePoisonsByTheDifficulty() throws Exception {
        EngineServer server = boot();
        try {
            // The night hours own the spider family's hunt (the
            // neutralByDay gate) — the set rides the owner lane.
            server.world().execute(() -> server.world().setTimeOfDay(14_000));
            await(() -> server.world().timeOfDay() >= 14_000 && server.world().timeOfDay() < 15_000,
                    "the night hours landed");
            var player = join(server, "Bitten");
            server.chatService().dispatchCommand(console(), "/difficulty hard");
            await(() -> server.difficulty() == 3, "the hard tier landed");
            server.chatService().submitChat(player, "/spawnmob cave_spider 1");
            await(() -> server.mobs().all().stream()
                    .anyMatch(m -> m.type() == MobType.CAVE_SPIDER), "cave spider present");
            MobEntity spider = server.mobs().all().stream()
                    .filter(m -> m.type() == MobType.CAVE_SPIDER)
                    .findFirst().orElseThrow();

            // CaveSpiderEntity.tryDamage lines 25-45: the hard tier's
            // 15-second poison (15 * 20 = 300 ticks) rides every landed
            // bite through the melee pipeline.
            await(() -> server.potions().hasEffect(player,
                            net.zaminmc.torch.server.potion.StatusEffects.POISON),
                    "the venomous bite lands");
            var poison = server.potions().get(player,
                    net.zaminmc.torch.server.potion.StatusEffects.POISON);
            assertNotNull(poison, "the poison store carries the bite");
            assertTrue(poison.duration() <= 300 && poison.duration() > 250,
                    "the hard tier's 15-second curse (was " + poison.duration() + ")");
            assertEquals(0, poison.amplifier(), "the tier-0 poison");

            // The normal tier downgrades to the 7-second curse
            // (tryDamage lines 30-31).
            server.potions().clear(player);
            server.chatService().dispatchCommand(console(), "/difficulty normal");
            await(() -> server.difficulty() == 2, "the normal tier landed");
            server.potions().clear(player);
            await(() -> server.potions().hasEffect(player,
                            net.zaminmc.torch.server.potion.StatusEffects.POISON),
                    "the second bite lands");
            var weaker = server.potions().get(player,
                    net.zaminmc.torch.server.potion.StatusEffects.POISON);
            assertTrue(weaker.duration() <= 140 && weaker.duration() > 90,
                    "the normal tier's 7-second curse (was " + weaker.duration() + ")");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theEasyTierCarriesNoVenom() throws Exception {
        EngineServer server = boot();
        try {
            server.world().execute(() -> server.world().setTimeOfDay(14_000));
            await(() -> server.world().timeOfDay() >= 14_000 && server.world().timeOfDay() < 15_000,
                    "the night hours landed");
            var player = join(server, "Unpoisoned");
            // The engine's default difficulty is easy (1): tryDamage's
            // i stays 0 — no poison ever installs.
            assertEquals(1, server.difficulty(), "the default easy tier");
            server.chatService().submitChat(player, "/spawnmob cave_spider 1");
            await(() -> server.mobs().all().stream()
                    .anyMatch(m -> m.type() == MobType.CAVE_SPIDER), "cave spider present");

            // The melee lands (the health drops) but the store stays
            // empty: the weaker rows carry no venom.
            await(() -> player.health() < net.zaminmc.torch.server.player.PlayerSession.MAX_HEALTH,
                    "the bite lands");
            assertNull(server.potions().get(player,
                    net.zaminmc.torch.server.potion.StatusEffects.POISON),
                    "the easy tier never poisons");
            assertFalse(server.potions().hasEffect(player,
                    net.zaminmc.torch.server.potion.StatusEffects.POISON));
        } finally {
            server.shutdown(null);
        }
    }
}
