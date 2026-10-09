package net.zaminmc.torch.server;

import net.zaminmc.torch.entity.PlayerState;

import net.zaminmc.torch.item.ItemStack;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.entity.MobManager;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.server.item.BuiltinItems;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Melee combat and the living population over the real ticker: server-side
 * reach validation, historical damage values (diamond sword 7, easy zombie 2),
 * knockback, the 20-tick death animation ending in loot that lands as item
 * entities, night-only hostile spawning and the dawn removal.
 */
class MobCombatAcceptanceTest {

    @TempDir
    Path dataDir;

    @Test
    void swordKillsAPigThroughTheValidatedReachAndLootDrops() throws Exception {
        EngineServer server = boot();
        PlayerSession hunter = join(server, "Hunter");
        server.chatService().submitChat(hunter, "/spawnmob pig 1");
        MobEntity pig = awaitMob(server, MobType.PIG);
        assertTrue(pig.entityId() >= 1_100_000, "mob ids live in their own band");
        assertTrue(server.itemEntities().all().stream()
                        .noneMatch(e -> e.entityId() == pig.entityId()),
                "mob and item ids never collide");

        // Walk to the pig and hit it with a diamond sword: 10 hp / 7 = 2 hits.
        standNextTo(server, hunter, pig);
        server.chatService().submitChat(hunter, "/give diamond_sword 1");
        await(() -> !hunter.inventory().held().isEmpty(), "sword given");
        server.attackEntity(hunter, pig.entityId());
        await(() -> pig.health() < MobType.PIG.maxHealth, "the first hit landed");
        standNextTo(server, hunter, pig); // the hurt pop knocked it back a bit
        server.attackEntity(hunter, pig.entityId());
        await(() -> pig.dead(), "the second hit killed (7 x 2 >= 10)");

        // The 20-tick death animation, then loot + removal.
        await(() -> server.mobs().byId(pig.entityId()) == null, "the body left the world");
        await(() -> server.itemEntities().all().stream().anyMatch(e ->
                        e.stack().type().equals(BuiltinItems.PORKCHOP)),
                "the porkchop loot landed as an item entity");
        await(() -> hunter.inventory().held().type().equals(BuiltinItems.DIAMOND_SWORD)
                        && hunter.inventory().held().damage() >= 2,
                "two living-entity hits wore the sword twice");
        server.shutdown(null);
    }

    @Test
    void outOfReachAttacksAreRefused() throws Exception {
        EngineServer server = boot();
        PlayerSession attacker = join(server, "Attacker");
        server.chatService().submitChat(attacker, "/spawnmob cow 1");
        MobEntity cow = awaitMob(server, MobType.COW);

        float before = cow.health();
        // The player teleports 55 blocks away (the engine's teleport path);
        // the swing is far beyond melee reach and must be refused.
        server.teleportPlayer(attacker, new Position(0.5, 5.0, 60.0));
        await(() -> attacker.position().z() == 60.0, "the teleport landed");
        server.attackEntity(attacker, cow.entityId());
        Thread.sleep(300);
        assertEquals(before, cow.health(), "out-of-reach swings never connect");
        server.shutdown(null);
    }

    @Test
    void nightZombiesHuntAndHurtThePlayer() throws Exception {
        EngineServer server = boot();
        PlayerSession victim = join(server, "Victim");

        server.chatService().submitChat(victim, "/time set night");
        await(() -> server.world().timeOfDay() >= MobManager.NIGHT_START, "night fell");
        server.chatService().submitChat(victim, "/spawnmob zombie 1");
        MobEntity zombie = awaitMob(server, MobType.ZOMBIE);

        standNextTo(server, victim, zombie);
        await(() -> victim.health() < PlayerSession.MAX_HEALTH,
                "the zombie reached and hurt the player (easy damage 2)");
        await(() -> victim.health() <= PlayerSession.MAX_HEALTH - MobEntity.MELEE_ATTACK_DAMAGE,
                "the zombie's easy-difficulty damage landed");
        server.shutdown(null);
    }

    @Test
    void dawnIgnitesTheNightZombies() throws Exception {
        EngineServer server = boot();
        PlayerSession keeper = join(server, "Keeper");
        // Zombies exist at night only: set the night, spawn, then let dawn come.
        server.chatService().submitChat(keeper, "/time set night");
        await(() -> server.world().timeOfDay() >= MobManager.NIGHT_START, "night fell");
        server.chatService().submitChat(keeper, "/spawnmob zombie 2");
        MobEntity zombie = awaitMob(server, MobType.ZOMBIE);

        server.chatService().submitChat(keeper, "/time set day");
        // The dawn rule: the undead ignite and burn instead of vanishing.
        await(() -> zombie.burning(), "dawn set the zombie on fire");
        await(() -> server.mobs().byId(zombie.entityId()) == null
                        || server.mobs().byId(zombie.entityId()).health()
                                < net.zaminmc.torch.server.entity.MobType.ZOMBIE.maxHealth,
                "the sun's fire burns into the body");
        server.shutdown(null);
    }

    @Test
    void dayTimeCommandMovesTheWorldClock() throws Exception {
        EngineServer server = boot();
        PlayerSession clock = join(server, "Clock");
        server.chatService().submitChat(clock, "/time set noon");
        await(() -> server.world().timeOfDay() >= 6_000
                        && server.world().timeOfDay() < 6_000 + 5,
                "noon landed at 6000 (the cycle advances past the set)");
        server.chatService().submitChat(clock, "/time set 18000");
        await(() -> server.world().timeOfDay() >= 18_000
                        && server.world().timeOfDay() < 18_000 + 5,
                "raw ticks move the clock");
        server.shutdown(null);
    }

    // ------------------------------------------------------------------ harness

    private MobEntity awaitMob(EngineServer server, MobType type) throws InterruptedException {
        await(() -> server.mobs().all().stream().anyMatch(m -> m.type() == type),
                type + " present");
        Optional<MobEntity> found = server.mobs().all().stream()
                .filter(m -> m.type() == type).findFirst();
        return found.orElseThrow();
    }

    private void standNextTo(EngineServer server, PlayerSession player, MobEntity mob) {
        Position at = mob.position();
        server.teleportPlayer(player, new Position(at.x(), at.y(), at.z() + 0.8));
    }

    private EngineServer boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "mobs", "it", 20, 2, 20,
                dataDir.toString());
        EngineServer server = new EngineServer(config);
        server.start();
        return server;
    }

    private PlayerSession join(EngineServer server, String name) throws InterruptedException {
        var accepted = server.joinRequest(link(), name, UUID.randomUUID());
        PlayerSession session = ((EngineBridge.Accepted) accepted).session();
        await(() -> server.playerRegistry().byName(name).isPresent(), name + " registered");
        server.joinCompleted(session);
        await(() -> session.state() == net.zaminmc.torch.entity.PlayerState.PLAYING, name + " playing");
        return session;
    }

    private ClientLink link() {
        return new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
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
}
