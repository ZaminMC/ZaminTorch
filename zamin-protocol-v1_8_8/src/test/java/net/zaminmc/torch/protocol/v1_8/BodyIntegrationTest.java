package net.zaminmc.torch.protocol.v1_8;

import net.zaminmc.torch.util.Identifier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The survival body over the real wire: Update Health follows damage and
 * falls, a lethal hit sends Combat Event 0x42 type 2, the respawn gesture
 * (Client Status 0x16 action 0) answers with the full re-anchor sequence
 * (Respawn 0x07 -> chunks -> position-and-look -> health -> window items),
 * and the eat gesture refuses at full hunger without touching the inventory.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BodyIntegrationTest extends ProtocolTestBase {

    /** The regen/fall flows wait on real ticks; keep-alives must not kick. */
    @Override
    protected long keepAliveIntervalMs() {
        return 120_000;
    }

    private static final int BEEF = 363;

    private void awaitPlayer(String name) throws InterruptedException {
        awaitCondition(() -> server.playerRegistry().byName(name).isPresent(), "player joined");
    }

    @Test
    void fallDamageFollowsMovementAndSyncsHealth() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client.sendHandshake(47, 2);
            client.sendLoginStart("Skydiver");
            client.readLoginSuccess();
            client.readUntilPositionAndLook();
            awaitPlayer("Skydiver");
            client.readWindowItems(10_000);
            double[] baseline = client.readUpdateHealth(10_000);
            assertEquals(20.0, baseline[0], "the join announces full health");
            assertEquals(20.0, baseline[1], "and full hunger");

            // Rise into the air, then land 15 blocks lower: ceil(15 - 3) = 12.
            client.sendPosition(0.5, 20.0, 0.5, false);
            client.sendPosition(0.5, 5.0, 0.5, true);
            double[] afterFall = client.readUpdateHealth(10_000);
            assertEquals(8.0, afterFall[0], 0.001, "the landing hurt ceil(15 - 3) = 12");
        }
        awaitEmptyServer();
    }

    @Test
    void deathSendsCombatEventAndRespawnReanchorsTheWire() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client.sendHandshake(47, 2);
            client.sendLoginStart("Phoenix");
            client.readLoginSuccess();
            client.readUntilPositionAndLook();
            awaitPlayer("Phoenix");
            client.readWindowItems(10_000);
            client.readUpdateHealth(10_000);

            // Stock the hotbar: the death must scatter it.
            client.sendChat("/give dirt 3");
            client.readWindowSlotTable(10_000);

            // A lethal hit straight from the engine's damage entry.
            var session = server.playerRegistry().byName("Phoenix").orElseThrow();
            server.damage(session, 100.0f);

            double[] dead = client.readUpdateHealth(10_000);
            assertEquals(0.0, dead[0], "the health hit zero");
            int[] combat = client.readCombatEvent(10_000);
            assertEquals(2, combat[0], "combat event type 2 = entity died");
            assertTrue(combat[1] > 0, "the player's wire entity id rides the event");
            // the death drop exists in the world
            awaitCondition(() -> !server.itemEntities().all().isEmpty()
                    && server.itemEntities().all().stream()
                            .anyMatch(e -> e.stack().type().identifier()
                                    .equals(net.zaminmc.torch.util.Identifier.parse("minecraft:dirt"))),
                    "the dirt scattered as item entities");

            // The client asks to respawn; the server re-anchors the wire.
            client.sendClientStatus(0);
            int[] respawn = client.readRespawn(10_000);
            assertEquals(0, respawn[0], "overworld");
            assertEquals(1, respawn[1], "easy difficulty");
            // chunks re-sent (drain the view), then the position-and-look, health, window
            client.readUntilPositionAndLook();
            double[] reborn = client.readUpdateHealth(10_000);
            assertEquals(20.0, reborn[0], "full health after respawn");
            int[] inventoryAfter = client.readWindowItems(10_000);
            assertEquals(-1, inventoryAfter[2], "the respawned inventory is empty (-1 = all empty)");
        }
        awaitEmptyServer();
    }

    @Test
    void eatGestureAtFullHungerIsRefusedWithoutConsuming() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client.sendHandshake(47, 2);
            client.sendLoginStart("Glutton");
            client.readLoginSuccess();
            client.readUntilPositionAndLook();
            awaitPlayer("Glutton");
            client.readWindowItems(10_000);
            client.readUpdateHealth(10_000);

            client.sendChat("/give beef 2");
            client.readWindowSlotTable(10_000);

            // The use-item gesture (block placement with the -1 sentinel):
            // the body is full, so the historical refusal applies — and a
            // refusal changes nothing, so the wire stays silent.
            client.sendBlockPlacement(-1, -1, -1, 255, BEEF);
            try {
                client.readWindowSlotTable(2_500, 0, true);
                throw new AssertionError("a refused eat must not sync anything");
            } catch (IOException expected) {
                // nothing arrived: the refusal is silent, exactly as refused
                // clicks would resync — here nothing changed at all
            }

            // A mid-air use of a non-food item is a silent no-op too.
            client.sendHeldItemChange(1);
            client.sendBlockPlacement(-1, -1, -1, -1, -1);
            try {
                client.readWindowSlotTable(1_500, 0, true);
                throw new AssertionError("a non-food use must not sync anything");
            } catch (IOException expected) {
                // nothing arrived
            }
        }
        awaitEmptyServer();
    }
}
