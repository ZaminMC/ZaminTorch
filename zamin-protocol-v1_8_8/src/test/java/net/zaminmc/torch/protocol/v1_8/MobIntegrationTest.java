package net.zaminmc.torch.protocol.v1_8;

import net.zaminmc.torch.server.entity.MobManager;
import net.zaminmc.torch.server.player.PlayerSession;
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

    /** Movement and population flows run on real ticks; keep-alives must not interfere. */
    @Override
    protected long keepAliveIntervalMs() {
        return 300_000;
    }

    private PlayerSession awaitPlayer(String name) throws InterruptedException {
        awaitCondition(() -> server.playerRegistry().byName(name).isPresent(), "player joined");
        return server.playerRegistry().byName(name).orElseThrow();
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
            client.sendChat("/give diamond_sword 1");
            client.walkTo(pig[2] / 32.0, pig[3] / 32.0, pig[4] / 32.0, true);
            client.sendUseEntity(pigId, Protocol18.USE_ENTITY_ATTACK);
            int[] hurt = client.readEntityStatus(15_000);
            assertEquals(pigId, hurt[0]);
            assertEquals(Protocol18.ENTITY_STATUS_HURT, hurt[1]);
            String[] sound = client.readNamedSound(15_000);
            assertEquals("mob.pig.say", sound[0], "the pig's historical hurt sound");

            client.walkTo(pig[2] / 32.0, pig[3] / 32.0, pig[4] / 32.0, true);
            client.sendUseEntity(pigId, Protocol18.USE_ENTITY_ATTACK);
            int[] secondHit = client.readEntityStatus(15_000);
            assertEquals(Protocol18.ENTITY_STATUS_HURT, secondHit[1]);
            int[] death = client.readEntityStatus(15_000);
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
            client.sendChat("/time set night");
            long[] time = client.readTimeUpdate(15_000);
            assertTrue(time[1] >= MobManager.NIGHT_START,
                    "the client's clock shows night after the set");
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

            // Clean up the night for whatever test runs next: the zombies
            // vaporize at dawn (the hostile-only rule this slice).
            watcher.sendChat("/time set day");
            watcher.readTimeUpdate(15_000);
            awaitCondition(() -> server.mobs().all().stream().noneMatch(m -> m.type().hostile),
                "dawn cleared the hostiles");
        }
    }
}
