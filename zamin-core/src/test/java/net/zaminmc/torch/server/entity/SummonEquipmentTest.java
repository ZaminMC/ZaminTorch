package net.zaminmc.torch.server.entity;

import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The /summon Equipment + Attributes rows (Slice 78b — MobEntity's
 * readCustomNbt lines 301-307 + LivingEntity's lines 419-421 over the
 * engine's mob model): the held stack lands on the body with its wire
 * re-publish, the movement-speed and attack-damage overrides drive the
 * engine's own consults, and the rows survive the restart (the ZMD v4
 * block).
 */
class SummonEquipmentTest {

    @TempDir
    Path dataDir;

    private EngineServer boot() throws Exception {
        // The FIXED world name (the restart test's second boot must read
        // the same world directory).
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "sumeqworld",
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
    void theEquipmentRowLandsTheHeldStackAndRepublishes() throws Exception {
        EngineServer server = boot();
        try {
            var session = opSession(server);
            // The equipment-change publication counter (the wire's
            // Entity Equipment re-send rides the same listener).
            AtomicInteger equipmentPublications = new AtomicInteger();
            server.mobs().addListener(new MobManager.Listener() {
                @Override public void onMobSpawned(MobEntity mob) {
                }

                @Override public void onMobMoved(MobEntity mob,
                        net.zaminmc.torch.server.player.PlayerSession rider) {
                }

                @Override public void onMobHurt(MobEntity mob) {
                }

                @Override public void onMobDied(MobEntity mob) {
                }

                @Override public void onMobRemoved(MobEntity mob, String reason) {
                }

                @Override public void onMobAttackedPlayer(MobEntity mob,
                        net.zaminmc.torch.server.player.PlayerSession target, float damage) {
                }

                @Override public void onMobSound(MobEntity mob, String soundName) {
                }

                @Override public void onMobRangedAttack(MobEntity mob, Position aimPoint) {
                }

                @Override public void onMobFuseChanged(MobEntity mob, boolean priming) {
                }

                @Override public void onMobSheared(MobEntity mob, int woolCount) {
                }

                @Override public void onMobCoatRegrown(MobEntity mob) {
                }

                @Override public void onMobExploded(MobEntity mob) {
                }

                @Override public void onMobEquipmentChanged(MobEntity mob) {
                    equipmentPublications.incrementAndGet();
                }
            });
            server.chatService().submitChat(session,
                    "/summon Zombie ~ ~ ~ {Equipment:[{id:\"minecraft:iron_sword\","
                            + "Count:1b},{},{},{},{}]}");
            await(() -> server.mobs().all().stream()
                            .anyMatch(mob -> mob.type() == MobType.ZOMBIE),
                    "the zombie summoned");
            MobEntity zombie = server.mobs().all().stream()
                    .filter(mob -> mob.type() == MobType.ZOMBIE).findFirst().orElseThrow();
            await(() -> zombie.heldEquipment() != null, "the Equipment row landed");
            assertEquals("minecraft:iron_sword",
                    zombie.heldEquipment().type().identifier().toString(),
                    "the held stack carries the row's item");
            assertEquals(1, zombie.heldEquipment().count(), "the Count row");
            await(() -> equipmentPublications.get() >= 1,
                    "the equipment change published (the wire's re-send)");
            // The armor slots (1..4) parse but land nowhere — the engine's
            // no-armor envelope: the summon still succeeded above, and the
            // body carries the held slot alone.
            assertFalse(zombie.dead(), "the body lives");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theAttributesRowsDriveTheEngineConsults() throws Exception {
        EngineServer server = boot();
        try {
            var session = opSession(server);
            server.chatService().submitChat(session,
                    "/summon Zombie ~ ~ ~ {Attributes:[{Name:\"generic.movementSpeed\","
                            + "Base:0.5d},{Name:\"generic.attackDamage\",Base:9.0d},"
                            + "{Name:\"generic.armor\",Base:4.0d}]}");
            await(() -> server.mobs().all().stream()
                            .anyMatch(mob -> mob.type() == MobType.ZOMBIE),
                    "the zombie summoned");
            MobEntity zombie = server.mobs().all().stream()
                    .filter(mob -> mob.type() == MobType.ZOMBIE).findFirst().orElseThrow();
            assertTrue(zombie.hasMovementSpeedOverride(),
                    "the movementSpeed row landed");
            assertEquals(0.5, zombie.walkSpeed(), 1e-9,
                    "the walk consult reads the override over the kind's base");
            assertTrue(zombie.hasAttackDamageOverride(),
                    "the attackDamage row landed");
            assertEquals(9.0f, zombie.attackDamage(), 1e-6,
                    "the melee consult reads the override over the kind's base");
            // The unknown attribute name (generic.armor) parses but lands
            // nowhere — the engine's per-type attribute envelope.
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theNonPositiveAttributeBaseIsRefused() throws Exception {
        EngineServer server = boot();
        try {
            var session = opSession(server);
            server.chatService().submitChat(session,
                    "/summon Zombie ~ ~ ~ {Attributes:[{Name:\"generic.movementSpeed\","
                            + "Base:0.0d}]}");
            await(() -> server.mobs().all().stream()
                            .anyMatch(mob -> mob.type() == MobType.ZOMBIE),
                    "the zombie summoned");
            MobEntity zombie = server.mobs().all().stream()
                    .filter(mob -> mob.type() == MobType.ZOMBIE).findFirst().orElseThrow();
            assertFalse(zombie.hasMovementSpeedOverride(),
                    "the non-positive base never overrode (the attribute keeps its base)");
            assertEquals(MobType.ZOMBIE.walkSpeed, zombie.walkSpeed(), 1e-9,
                    "the kind's own base answers");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theEquipmentAndAttributeRowsSurviveTheRestart() throws Exception {
        // The ZMD v4 block: the held stack + the two overrides land on the
        // fresh body after the restart (the save on shutdown + the restore
        // at boot, with the equipment's own re-publication).
        EngineServer server = boot();
        try {
            var session = opSession(server);
            server.chatService().submitChat(session,
                    "/summon Zombie ~ ~ ~ {CustomName:\"Equipped\","
                            + "Equipment:[{id:\"minecraft:iron_sword\",Count:1b}],"
                            + "Attributes:[{Name:\"generic.movementSpeed\",Base:0.4d},"
                            + "{Name:\"generic.attackDamage\",Base:7.5d}]}");
            await(() -> server.mobs().all().stream()
                            .anyMatch(mob -> "Equipped".equals(mob.customName())),
                    "the named equipped zombie summoned");
        } finally {
            server.shutdown(null); // the save walks here
        }
        EngineServer restarted = boot();
        try {
            await(() -> restarted.mobs().all().stream()
                            .anyMatch(mob -> "Equipped".equals(mob.customName())),
                    "the named zombie restored");
            MobEntity keeper = restarted.mobs().all().stream()
                    .filter(mob -> "Equipped".equals(mob.customName())).findFirst().orElseThrow();
            await(() -> keeper.heldEquipment() != null, "the held stack restored");
            assertEquals("minecraft:iron_sword",
                    keeper.heldEquipment().type().identifier().toString(),
                    "the held stack's item survived the round trip");
            assertTrue(keeper.hasMovementSpeedOverride(), "the movementSpeed row restored");
            assertEquals(0.4, keeper.walkSpeed(), 1e-9, "the restored override");
            assertTrue(keeper.hasAttackDamageOverride(), "the attackDamage row restored");
            assertEquals(7.5f, keeper.attackDamage(), 1e-6, "the restored override");
        } finally {
            restarted.shutdown(null);
        }
    }
}
