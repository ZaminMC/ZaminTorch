package net.zaminmc.torch.protocol.v1_8;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tab-list family over the real wire (Slice 84 — Player Info 0x38, the
 * reference's PlayerInfoS2CPacket): the joiner lands on every other player's
 * tab list (ADD with the offline uuid, the name, the gamemode, the ping 0),
 * the joiner's own wire carries their entry TWICE (the broadcast copy + the
 * per-player loop — the reference's own add() quirk), the /gamemode command
 * drives the UPDATE_GAME_MODE broadcast on every wire, and the disconnect
 * removes the leaver (REMOVE) from whoever stays.
 */
class TabListIntegrationTest extends ProtocolTestBase {

    @Override
    protected long keepAliveIntervalMs() {
        return 120_000; // the scripted clients answer only when the test drives it
    }

    private void joinFully(TestClient18 client, String name) throws Exception {
        client.sendHandshake(47, 2);
        client.sendLoginStart(name);
        client.readLoginSuccess();
        client.readUntilPositionAndLook();
    }

    /** The join variant that COLLECTS the pre-position ADD packets (the joiner's own wire). */
    private List<TestClient18.TabEntry> joinCollectingAdds(TestClient18 client, String name)
            throws Exception {
        client.sendHandshake(47, 2);
        client.sendLoginStart(name);
        client.readLoginSuccess();
        return client.readUntilPositionCollectingTabAdds();
    }

    @Test
    void theJoinerLandsOnEveryTabListAndSeesTheirOwnEntryTwice() throws Exception {
        try (TestClient18 alice = new TestClient18("127.0.0.1", adapter.boundPort());
             TestClient18 bob = new TestClient18("127.0.0.1", adapter.boundPort())) {
            joinFully(alice, "TabAlice");
            awaitPlayers("TabAlice");

            // Bob's own wire: his entry twice + Alice's once — the reference's
            // add() shape (the broadcast self-copy + the per-player loop). The
            // ADDs arrive BEFORE Bob's position packet, so the join reader
            // collects them on the way.
            List<TestClient18.TabEntry> bobSees = joinCollectingAdds(bob, "TabBob");
            awaitPlayers("TabAlice", "TabBob");

            // Alice's wire: Bob's ADD (uuid + name + gamemode 0 + ping 0).
            List<TestClient18.TabEntry> aliceSees =
                    alice.collectTabAddsUntil("TabBob", 10_000);
            TestClient18.TabEntry bobOnAliceWire = aliceSees.stream()
                    .filter(e -> "TabBob".equals(e.name())).findFirst().orElseThrow();
            UUID bobUuid = UUID.nameUUIDFromBytes(
                    "OfflinePlayer:TabBob".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            assertEquals(bobUuid, bobOnAliceWire.uuid(), "the offline uuid");
            assertEquals(0, bobOnAliceWire.gamemode(), "survival");
            assertEquals(0, bobOnAliceWire.ping(), "a fresh joiner's ping");

            // The offline branch's profile law (Wave ON-1d's other side): the
            // offline profile carries NO properties — the count varint is 0,
            // no name/value/signature rows behind it (the reference's
            // createFakeProfile shape; the parser would have captured any).
            assertTrue(bobOnAliceWire.properties().isEmpty(),
                    "the offline ADD carries zero properties");

            long selfAdds = bobSees.stream().filter(e -> "TabBob".equals(e.name())).count();
            assertTrue(selfAdds >= 2, "the joiner's own entry twice (got " + selfAdds + ")");
            assertTrue(bobSees.stream().anyMatch(e -> "TabAlice".equals(e.name())),
                    "the existing player's ADD reached the joiner");
            // Both of the joiner's own copies ride the same empty row, and the
            // existing player's entry carries none either.
            assertTrue(bobSees.stream().filter(e -> "TabBob".equals(e.name()))
                    .allMatch(e -> e.properties().isEmpty()),
                    "every self-add carries the offline zero-property row");
            assertTrue(bobSees.stream().filter(e -> "TabAlice".equals(e.name()))
                    .allMatch(e -> e.properties().isEmpty()),
                    "the offline existing-player entry carries none either");
        }
        awaitEmptyServer();
    }

    @Test
    void theGamemodeCommandDrivesTheUpdateGamemodeBroadcast() throws Exception {
        try (TestClient18 carol = new TestClient18("127.0.0.1", adapter.boundPort());
             TestClient18 dave = new TestClient18("127.0.0.1", adapter.boundPort())) {
            joinFully(carol, "TabCarol");
            joinFully(dave, "TabDave");
            awaitPlayers("TabCarol", "TabDave");

            // Dave must be an operator to switch his own gamemode.
            server.ticker().submit(() -> server.playerRegistry()
                    .byName("TabDave").ifPresent(p -> p.setOpLevel(2)));

            carol.readPlayerInfo(Protocol18.PLAYER_INFO_ADD_PLAYER, 10_000); // drain to the tail
            dave.sendChat("/gamemode 1");

            // Carol's wire: the UPDATE_GAME_MODE for Dave's uuid, mode 1.
            List<TestClient18.TabEntry> carolUpdate = carol.readPlayerInfo(
                    Protocol18.PLAYER_INFO_UPDATE_GAME_MODE, 10_000);
            UUID daveUuid = UUID.nameUUIDFromBytes(
                    "OfflinePlayer:TabDave".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            assertEquals(1, carolUpdate.size());
            assertEquals(daveUuid, carolUpdate.get(0).uuid());
            assertEquals(1, carolUpdate.get(0).gamemode());

            // Dave's own wire carries the update too (no self-skip).
            List<TestClient18.TabEntry> daveUpdate = dave.readPlayerInfo(
                    Protocol18.PLAYER_INFO_UPDATE_GAME_MODE, 10_000);
            assertEquals(daveUuid, daveUpdate.get(0).uuid());
            assertEquals(1, daveUpdate.get(0).gamemode());
        }
        awaitEmptyServer();
    }

    @Test
    void theDisconnectRemovesTheLeaverFromEveryRemainingTabList() throws Exception {
        try (TestClient18 eve = new TestClient18("127.0.0.1", adapter.boundPort());
             TestClient18 mallory = new TestClient18("127.0.0.1", adapter.boundPort())) {
            joinFully(eve, "TabEve");
            joinFully(mallory, "TabMallory");
            awaitPlayers("TabEve", "TabMallory");

            mallory.close(); // the hard drop drives clientDisconnected
            awaitCondition(() -> server.playerRegistry().byName("TabMallory").isEmpty(),
                    "Mallory unregistered");

            // The previous test's async disconnects can leak earlier REMOVE
            // packets onto this wire (the shared adapter's class lifecycle) —
            // drain until Mallory's own uuid appears.
            UUID malloryUuid = UUID.nameUUIDFromBytes(
                    "OfflinePlayer:TabMallory".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            UUID removed = null;
            long deadline = System.currentTimeMillis() + 10_000;
            while (System.currentTimeMillis() < deadline) {
                List<TestClient18.TabEntry> removal = eve.readPlayerInfo(
                        Protocol18.PLAYER_INFO_REMOVE_PLAYER, 10_000);
                assertEquals(1, removal.size(), "one REMOVE entry per packet");
                if (removal.get(0).uuid().equals(malloryUuid)) {
                    removed = removal.get(0).uuid();
                    break;
                }
            }
            assertEquals(malloryUuid, removed, "the leaver's uuid");
        }
        awaitEmptyServer();
    }
}
