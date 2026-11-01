package net.zaminmc.torch.protocol.v1_8;

import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.entity.MobManager;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Living mobs over the real wire, from the client's point of view: Spawn Mob
 * with the community type id and the health metadata, movement as the
 * historical rel-move-look, combat as Entity Status hurt/death plus the hurt
 * sound, loot landing as item entities, destroy on removal, the arm-swing
 * broadcast to other observers, and the /time cycle reaching the client.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MobIntegrationTest extends ProtocolTestBase {

    /**
     * The keep-alive cadence rides well UNDER the listener's own bounds
     * (the reference's own proportion: its 15s cadence against the same
     * 30s wall): the auto-answered exchange keeps the read side alive
     * while the movement waits idle, and every read below skips the
     * keep-alive frames by id — the packet-bounded scans never see them.
     * The KICK arm is the one that bites read-silent phases: a pending
     * keep-alive unanswered past interval x 3 (15s at this cadence)
     * closes the channel, so the swing loop below pumps the wire every
     * poll — a real client answers keep-alives independently of its
     * movement, and the chase can run a full 60s without one typed read
     * (run 569's broken pipe, retired at the fixture's own law).
     */
    @Override
    protected long keepAliveIntervalMs() {
        return 5_000;
    }

    private PlayerSession awaitPlayer(String name) throws InterruptedException {
        awaitCondition(() -> server.playerRegistry().byName(name).isPresent(), "player joined");
        return server.playerRegistry().byName(name).orElseThrow();
    }

    /**
     * Walks to the body's live cell and swings until the body's own state
     * answers: {@code killed} polls the death flag (the second sword hit),
     * else the health drop. A swing the reach gate refuses leaves the state
     * untouched, so the loop re-reads the cell and closes the new distance —
     * the landing tell is the server's simulation state, never wire timing
     * (the status and sound packets ride the same tick as the wound and are
     * already queued when this returns). The loop is READ-SILENT by design,
     * so every poll pumps the wire's keep-alives (the non-discarding drain:
     * answered inline, everything else re-queued in order) — the chase can
     * out-live the kick arm's 15s unanswered window, and a real client's
     * keep-alive answering never waits for its own movement to pause.
     */
    private void swingUntilTheBodyAnswers(TestClient18 client, int pigId,
            boolean killed) throws Exception {
        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline) {
            MobManager mobs = server.mobs();
            if (mobs.byId(pigId) == null) {
                return; // already removed: nothing left to swing at
            }
            Position live = mobs.byId(pigId).position();
            client.walkTo(live.x(), live.y(), live.z(), true);
            client.sendUseEntity(pigId, Protocol18.USE_ENTITY_ATTACK);
            long landed = System.currentTimeMillis() + 5_000;
            while (System.currentTimeMillis() < landed) {
                MobEntity body = server.mobs().byId(pigId);
                if (body == null || (killed ? body.dead()
                        : body.health() < body.maxHealth())) {
                    return;
                }
                Thread.sleep(50);
                client.pumpKeepAlives();
            }
            // Refused — the wander window closed: walk the new cell again.
        }
        throw new AssertionError(
                (killed ? "the killing swing" : "the first swing")
                        + " never landed within 60s");
    }


    /**
     * Pre-seeds ops.json so the test players can run the op-gated verbs
     * (/give among them — the vanilla GiveCommand sits at op level 2): the
     * offline-mode UUID convention resolves each name deterministically
     * before the engine boots.
     */
    @Override
    @org.junit.jupiter.api.BeforeAll
    void bootStack() throws Exception {
        java.nio.file.Files.write(dataDir.resolve("ops.json"), java.util.List.of(
                "[{\"uuid\": \"" + java.util.UUID.nameUUIDFromBytes(
                        "OfflinePlayer:MobHunter".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                        + "\", \"name\": \"MobHunter\", \"level\": 4, \"bypassesPlayerLimit\": false},",
                " {\"uuid\": \"" + java.util.UUID.nameUUIDFromBytes(
                        "OfflinePlayer:NightWatcher".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                        + "\", \"name\": \"NightWatcher\", \"level\": 4, \"bypassesPlayerLimit\": false},",
                " {\"uuid\": \"" + java.util.UUID.nameUUIDFromBytes(
                        "OfflinePlayer:Swinger".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                        + "\", \"name\": \"Swinger\", \"level\": 4, \"bypassesPlayerLimit\": false}]"
        ));
        super.bootStack();
    }

    @Test
    void mobsSpawnMoveFightAndLootOverTheWire() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client.sendHandshake(47, 2);
            client.sendLoginStart("MobHunter");
            client.readLoginSuccess();
            client.readUntilPositionAndLook();
            awaitPlayer("MobHunter");
            client.readWindowItems(15_000); // initial (empty) inventory sync

            // A pig of the world's population arrives as Spawn Mob 0x0F with
            // the community type id (90) and the health metadata (10).
            client.sendChat("/spawnmob pig 1");
            int[] pig = client.readSpawnMobOfType(90, 20_000);
            assertTrue(pig[0] >= 1_100_000, "mob ids live in their own band");
            assertEquals(10, pig[5], "the pig's health rides the living metadata");
            int pigId = pig[0];

            // The AI walks: a Rel Move Look (0x17) for the pig arrives (other
            // mobs of the population wander too, so chase the pig's own id).
            boolean pigMoved = false;
            long moveDeadline = System.currentTimeMillis() + 60_000;
            while (!pigMoved && System.currentTimeMillis() < moveDeadline) {
                int[] move = client.readRelMoveLook(
                        Math.max(1, moveDeadline - System.currentTimeMillis()));
                pigMoved = move[0] == pigId;
            }
            assertTrue(pigMoved, "the pig's own movement syncs");

            // Combat: two diamond-sword hits (10 hp / 7) kill. The hurt comes
            // as Entity Status 2 with the historical hurt sound; the kill
            // adds status 3 (the death fall). The client walks to the pig
            // first (and again after the pop) — reach is server-validated.
            client.sendChat("/give MobHunter diamond_sword 1");
            client.drainGiveGhostWake(150);
            // The pig wanders between any position read and the swing's
            // processing (the tick lane's catch-up ages the AI while the
            // packets sit queued, and the first hit's panic flees at ~7
            // blocks a second): each swing closes the distance to the
            // body's LIVE cell and polls the body's OWN health for the
            // landing — a refused swing leaves the state untouched and
            // the next walk closes the new distance. Runs 561 and 563
            // both timed out on the status reads exactly there; the loop
            // never weakens the reach gate, it depends on it.
            swingUntilTheBodyAnswers(client, pigId, false);
            // (The body-filtered reads — the natural spawn cycle's own hurt
            // packets interleave the stream on the loaded runner, and the
            // body-agnostic form landed whichever came first: one local run
            // read a spawn-cycle body's fall wound for entity 1100018 where
            // the pig's 1100001 was asked for. The seat's own body is the
            // one it asked for; the others' statuses are not its concern.)
            int[] hurt = client.readEntityStatusFor(pigId, 15_000);
            assertEquals(pigId, hurt[0]);
            assertEquals(Protocol18.ENTITY_STATUS_HURT, hurt[1]);
            String[] sound = client.readNamedSound(15_000);
            assertEquals("mob.pig.say", sound[0], "the pig's historical hurt sound");

            swingUntilTheBodyAnswers(client, pigId, true);
            int[] secondHit = client.readEntityStatusFor(pigId, 15_000);
            assertEquals(Protocol18.ENTITY_STATUS_HURT, secondHit[1]);
            int[] death = client.readEntityStatusFor(pigId, 15_000);
            assertEquals(pigId, death[0]);
            assertEquals(Protocol18.ENTITY_STATUS_DEAD, death[1]);

            // The 20-tick death animation ends: destroy, then loot as an item.
            boolean destroyed = false;
            long deadline = System.currentTimeMillis() + 20_000;
            while (!destroyed && System.currentTimeMillis() < deadline) {
                int[] destroy = client.readDestroyEntities(20_000);
                for (int id : destroy) {
                    destroyed |= id == pigId;
                }
            }
            assertTrue(destroyed, "the dead pig left the client's world");

            int[] loot = client.readSpawnItemOfType(319, 20_000);
            assertEquals(Protocol18.OBJECT_ITEM, loot[1], "loot rides the item-entity path");
            assertEquals(319, loot[5], "raw porkchop (community items.json id)");

            // The /time cycle reaches the client through Time Update (0x03).
            // The periodic 20-tick sync (Slice 8c) may still carry the
            // pre-command day: the wire guarantees no ordering between the
            // in-flight periodic packet and the command's application, so
            // the read walks Time Updates until the post-command night
            // lands (the same interleaving contract the chat reader
            // already handles for join/quit broadcasts).
            client.sendChat("/time set night");
            long nightDeadline = System.currentTimeMillis() + 15_000;
            boolean night = false;
            while (!night && System.currentTimeMillis() < nightDeadline) {
                long[] time = client.readTimeUpdate(
                        Math.max(1, nightDeadline - System.currentTimeMillis()));
                night = time[1] >= MobManager.NIGHT_START;
            }
            assertTrue(night, "the client's clock shows night after the set");
        }
    }

    @Test
    void nightZombiesSpawnWithTheirFullBodyAndArmSwingsBroadcast() throws Exception {
        try (TestClient18 watcher = new TestClient18("127.0.0.1", adapter.boundPort());
             TestClient18 swinger = new TestClient18("127.0.0.1", adapter.boundPort())) {
            watcher.sendHandshake(47, 2);
            watcher.sendLoginStart("NightWatcher");
            watcher.readLoginSuccess();
            watcher.readUntilPositionAndLook();
            awaitPlayer("NightWatcher");
            watcher.readWindowItems(15_000);

            swinger.sendHandshake(47, 2);
            swinger.sendLoginStart("Swinger");
            swinger.readLoginSuccess();
            swinger.readUntilPositionAndLook();
            awaitPlayer("Swinger");
            swinger.readWindowItems(15_000);
            awaitCondition(() -> server.players().size() >= 2, "both players present");

            // Night: the maintainer or the command path produces zombies.
            watcher.sendChat("/time set night");
            watcher.sendChat("/spawnmob zombie 1");
            int[] zombie = watcher.readSpawnMobOfType(54, 20_000);
            assertEquals(20, zombie[5], "the zombie's historical 20 health");

            // The other client sees the swing: Animation 0x0B aimed at the
            // observer-local id of the swinging player.
            swinger.sendArmAnimation();
            int[] swing = watcher.readAnimation(15_000);
            assertTrue(swing[0] > 0, "the swing targets a known remote entity");
            assertEquals(0, swing[1], "animation 0 = the arm swing");

            // Clean up the night for whatever test runs next: the undead
            // ignite at dawn now (they burn out on their own clock, so the
            // cleanup waits for every hostile to be burning, not gone).
            watcher.sendChat("/time set day");
            watcher.readTimeUpdate(15_000);
            awaitCondition(() -> server.mobs().all().stream()
                    .filter(m -> m.type().hostile)
                    .allMatch(net.zaminmc.torch.server.entity.MobEntity::burning),
                "dawn ignited every hostile");
        }
    }
}
