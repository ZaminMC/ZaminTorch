package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.entity.PlayerState;
import net.zaminmc.torch.server.auth.GameProfile;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tab-list family's engine walks (Slice 84 — the reference's
 * PlayerInfoS2CPacket sends): the join announce reproduces the reference's
 * add() wire shape exactly (every other player learns the joiner once; the
 * joiner's own wire carries their entry TWICE — the broadcast's self copy +
 * the per-player loop's final iteration), the quit broadcast removes the
 * leaver from everyone who stays, the game-mode change reaches everyone
 * INCLUDING the subject, the ping cadence fires on the 601st tick (the
 * reference's own ++counter &gt; 600 off-by-one) as ONE batched packet per
 * recipient, and the ping smoothing is the reference's own
 * (ping * 3 + roundTrip) / 4. Wave ON-1d adds the profile-properties pin:
 * the join seam installs the session service's row before the burst and
 * every entry carries it whole (empty on the offline branch).
 */
class TabListServiceTest {

    private java.nio.file.Path dataDir;

    /** The recording link: every tab-list call with its payload. */
    private static final class RecordingLink implements ClientLink {
        final List<List<ClientLink.TabListEntry>> adds = new ArrayList<>();
        final List<List<UUID>> removes = new ArrayList<>();
        final List<UUID> gamemodeUpdates = new ArrayList<>();
        final List<Integer> gamemodeIds = new ArrayList<>();
        final List<List<ClientLink.TabListEntry>> pingBatches = new ArrayList<>();

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public void kick(String reason) {
        }

        @Override
        public void tabListAdd(List<ClientLink.TabListEntry> entries) {
            adds.add(List.copyOf(entries));
        }

        @Override
        public void tabListRemove(List<UUID> ids) {
            removes.add(List.copyOf(ids));
        }

        @Override
        public void tabListUpdateGamemode(UUID id, int gamemodeId) {
            gamemodeUpdates.add(id);
            gamemodeIds.add(gamemodeId);
        }

        @Override
        public void tabListUpdatePing(List<ClientLink.TabListEntry> entries) {
            pingBatches.add(List.copyOf(entries));
        }
    }

    private EngineServer server;

    private PlayerSession join(String name, RecordingLink link) throws Exception {
        var accepted = server.joinRequest(link, name,
                UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes()));
        PlayerSession session = ((EngineBridge.Accepted) accepted).session();
        await(() -> session.state() == PlayerState.JOINING || session.state() == PlayerState.PLAYING,
                name + " registered");
        server.joinTabList(session); // the adapter's mid-burst hook
        server.joinCompleted(session);
        await(() -> session.state() == PlayerState.PLAYING, name + " playing");
        return session;
    }

    @Test
    void theJoinAnnounceReproducesTheReferenceWireShape() throws Exception {
        dataDir = Files.createTempDirectory("tablist-join");
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "tabs", "it", 20, 2, 20,
                dataDir.toString());
        server = new EngineServer(config);
        server.start();
        try {
            RecordingLink aliceLink = new RecordingLink();
            PlayerSession alice = join("Alice", aliceLink);
            assertEquals(2, aliceLink.adds.size(),
                    "the lone joiner's own wire: the broadcast self-copy + the loop's self iteration");
            assertTrue(aliceLink.adds.get(0).get(0).name().equals("Alice")
                            && aliceLink.adds.get(1).get(0).name().equals("Alice"),
                    "both packets carry the joiner's own entry (the reference's double add)");
            assertEquals(1, aliceLink.adds.get(0).size(), "one entry per packet (the reference's per-player sends)");
            assertEquals(0, aliceLink.adds.get(0).get(0).ping(), "a fresh joiner's ping is 0");
            assertEquals(GameMode.SURVIVAL.legacyId(), aliceLink.adds.get(0).get(0).gamemodeId(),
                    "the wire entry carries the session's live gamemode id");

            RecordingLink bobLink = new RecordingLink();
            PlayerSession bob = join("Bob", bobLink);
            // Bob's own wire: self broadcast copy + one packet per registered
            // session (Alice + self) = 3 packets. The loop's order follows the
            // registry's concurrent map (arbitrary) — the reference's join-order
            // ArrayList is not client-observable (the 1.8 client hashes its
            // tab entries), so the assertion is the SET.
            assertEquals(3, bobLink.adds.size(), "the second joiner's own wire: self, Alice, self");
            assertEquals("Bob", bobLink.adds.get(0).get(0).name(), "the broadcast's self copy first");
            List<String> loopNames = bobLink.adds.subList(1, 3).stream()
                    .map(p -> p.get(0).name()).sorted().toList();
            assertEquals(List.of("Alice", "Bob"), loopNames,
                    "the loop covers every registered session — the joiner's own entry twice in total");
            // Alice's wire: exactly ONE new packet — Bob's ADD.
            assertEquals(3, aliceLink.adds.size(), "Alice: her own two join packets + Bob's announce");
            assertEquals("Bob", aliceLink.adds.get(2).get(0).name());
            assertEquals(bob.uuid(), aliceLink.adds.get(2).get(0).uuid(),
                    "the entry carries the joiner's offline uuid");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theQuitBroadcastRemovesTheLeaverFromEveryoneWhoStays() throws Exception {
        dataDir = Files.createTempDirectory("tablist-quit");
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "tabs", "it", 20, 2, 20,
                dataDir.toString());
        server = new EngineServer(config);
        server.start();
        try {
            RecordingLink aliceLink = new RecordingLink();
            PlayerSession alice = join("Alice", aliceLink);
            RecordingLink bobLink = new RecordingLink();
            PlayerSession bob = join("Bob", bobLink);
            aliceLink.removes.clear();
            bobLink.removes.clear();

            server.clientDisconnected(bob, "test close");
            await(() -> !aliceLink.removes.isEmpty(), "the REMOVE reached Alice");
            assertEquals(1, aliceLink.removes.get(0).size(), "one entry per REMOVE packet");
            assertEquals(bob.uuid(), aliceLink.removes.get(0).get(0),
                    "the removed entry is the leaver's uuid");
            assertTrue(bobLink.removes.isEmpty(), "the leaver's own link learns nothing");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theGamemodeBroadcastReachesEveryoneIncludingTheSubject() throws Exception {
        dataDir = Files.createTempDirectory("tablist-gm");
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "tabs", "it", 20, 2, 20,
                dataDir.toString());
        server = new EngineServer(config);
        server.start();
        try {
            RecordingLink aliceLink = new RecordingLink();
            PlayerSession alice = join("Alice", aliceLink);
            RecordingLink bobLink = new RecordingLink();
            PlayerSession bob = join("Bob", bobLink);
            aliceLink.gamemodeUpdates.clear();
            bobLink.gamemodeUpdates.clear();

            server.setGamemode(alice, GameMode.CREATIVE);
            await(() -> !aliceLink.gamemodeUpdates.isEmpty()
                    && !bobLink.gamemodeUpdates.isEmpty(), "both wires learned the change");
            assertEquals(alice.uuid(), aliceLink.gamemodeUpdates.get(0),
                    "the subject's own wire carries the update (the reference has no self-skip)");
            assertEquals(GameMode.CREATIVE.legacyId(), (int) aliceLink.gamemodeIds.get(0));
            assertEquals(alice.uuid(), bobLink.gamemodeUpdates.get(0), "the other player's wire too");
            assertEquals(GameMode.CREATIVE.legacyId(), (int) bobLink.gamemodeIds.get(0));
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void thePingSmoothingIsTheReferencesOwnFormula() throws Exception {
        dataDir = Files.createTempDirectory("tablist-ping");
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "tabs", "it", 20, 2, 20,
                dataDir.toString());
        server = new EngineServer(config);
        server.start();
        try {
            RecordingLink aliceLink = new RecordingLink();
            PlayerSession alice = join("Alice", aliceLink);
            assertEquals(0, alice.ping(), "a fresh session's ping is 0");
            alice.updatePing(100); // (0*3 + 100)/4 = 25
            assertEquals(25, alice.ping(), "the reference's (ping*3 + rtt)/4 smoothing");
            alice.updatePing(100); // (25*3 + 100)/4 = 43
            assertEquals(43, alice.ping(), "the smoothing compounds exactly like the reference");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void thePingCadenceFiresOnThe601stTickAsOneBatchedPacket() {
        // The cadence law (PlayerManager.tick lines 504-508) — driven
        // directly through the service's own counter (the engine wires this
        // walk into its tick body): the counter fires on the 601st tick,
        // not the 600th — the reference's own off-by-one.
        RecordingLink aliceLink = new RecordingLink();
        RecordingLink bobLink = new RecordingLink();
        net.zaminmc.torch.server.player.TabListService service =
                new net.zaminmc.torch.server.player.TabListService();
        net.zaminmc.torch.server.player.PlayerRegistry registry =
                new net.zaminmc.torch.server.player.PlayerRegistry();
        PlayerSession alice = new PlayerSession(UUID.randomUUID(), "Alice", aliceLink);
        PlayerSession bob = new PlayerSession(UUID.randomUUID(), "Bob", bobLink);
        registry.register(alice);
        registry.register(bob);
        for (int i = 0; i < 600; i++) {
            service.tick(registry);
        }
        assertTrue(aliceLink.pingBatches.isEmpty() && bobLink.pingBatches.isEmpty(),
                "600 ticks: the reference's ++counter > 600 has not fired yet");
        service.tick(registry); // the 601st
        assertEquals(1, aliceLink.pingBatches.size(), "the 601st tick fires ONE packet per recipient");
        assertEquals(1, bobLink.pingBatches.size(), "every online player's wire learns it");
        assertEquals(2, aliceLink.pingBatches.get(0).size(),
                "the single packet carries EVERY online player's entry");
        assertEquals(0, aliceLink.pingBatches.get(0).get(0).ping(),
                "the batch carries each session's own smoothed ping");
        for (int i = 0; i < 600; i++) {
            service.tick(registry);
        }
        assertEquals(1, aliceLink.pingBatches.size(), "the reset counter needs another 601 ticks");
        service.tick(registry);
        assertEquals(2, aliceLink.pingBatches.size(), "and fires again exactly on the next 601st");
    }

    /**
     * Wave ON-1d's engine pin: the profile's signed properties ride the
     * join seam whole — the four-arg joinRequest installs the row on the
     * session BEFORE the join burst builds the tab-list entries, every
     * ADD the join announce sends carries it (the broadcast self-copy +
     * the loop's iteration, the reference's double add over the same
     * whole profile), the offline three-arg join stays the empty row
     * (createFakeProfile), and the install is a defensive copy — a later
     * mutation of the source list never reaches the wire.
     */
    @Test
    void theProfilePropertiesRideTheJoinSeamAndTheEntriesWhole() throws Exception {
        dataDir = Files.createTempDirectory("tablist-props");
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "tabs", "it", 20, 2, 20,
                dataDir.toString());
        server = new EngineServer(config);
        server.start();
        try {
            List<GameProfile.Property> row = new ArrayList<>(List.of(
                    new GameProfile.Property("textures", "dGV4dHVyZXM=", "c2lnbmF0dXJl"),
                    new GameProfile.Property("custom", "b3RoZXI=", null)));
            RecordingLink carrieLink = new RecordingLink();
            var accepted = server.joinRequest(carrieLink, "Carrie",
                    UUID.nameUUIDFromBytes("OfflinePlayer:Carrie".getBytes()), row);
            PlayerSession carrie = ((EngineBridge.Accepted) accepted).session();
            await(() -> carrie.state() == PlayerState.JOINING
                    || carrie.state() == PlayerState.PLAYING, "Carrie registered");
            server.joinTabList(carrie); // the adapter's mid-burst hook
            server.joinCompleted(carrie);
            await(() -> carrie.state() == PlayerState.PLAYING, "Carrie playing");

            // The session's stored row: the whole property list.
            assertEquals(2, carrie.profileProperties().size(),
                    "the session stores the profile's whole property row");
            assertEquals("textures", carrie.profileProperties().get(0).name());
            assertEquals("dGV4dHVyZXM=", carrie.profileProperties().get(0).value());
            assertEquals("c2lnbmF0dXJl", carrie.profileProperties().get(0).signature());
            assertNull(carrie.profileProperties().get(1).signature(),
                    "the unsigned row stores the null signature");

            // Every ADD the join announce sent carries the row whole (both
            // of the joiner's own packets — the broadcast self-copy + the
            // loop's final iteration).
            assertEquals(2, carrieLink.adds.size(),
                    "the lone joiner's own wire: the self-copy + the loop's iteration");
            for (List<ClientLink.TabListEntry> packet : carrieLink.adds) {
                assertEquals(1, packet.size());
                assertEquals(2, packet.get(0).properties().size(),
                        "the wire entry carries the whole property row");
                assertEquals("textures", packet.get(0).properties().get(0).name());
                assertEquals("dGV4dHVyZXM=", packet.get(0).properties().get(0).value());
                assertEquals("c2lnbmF0dXJl", packet.get(0).properties().get(0).signature());
                assertNull(packet.get(0).properties().get(1).signature());
            }

            // The defensive copy: a later mutation of the source row never
            // reaches the session (the install is List.copyOf).
            row.add(new GameProfile.Property("late", "bGF0ZQ==", null));
            assertEquals(2, carrie.profileProperties().size(),
                    "the session's row is the install-time copy, never the live source");

            // The offline default: the three-arg join carries the empty row.
            RecordingLink daveLink = new RecordingLink();
            var daveAccepted = server.joinRequest(daveLink, "Dave",
                    UUID.nameUUIDFromBytes("OfflinePlayer:Dave".getBytes()));
            PlayerSession dave = ((EngineBridge.Accepted) daveAccepted).session();
            assertTrue(dave.profileProperties().isEmpty(),
                    "the offline branch's row is empty (createFakeProfile)");
        } finally {
            server.shutdown(null);
        }
    }

    private static void await(BooleanSupplier condition, String what) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("Timed out waiting for: " + what);
            }
            Thread.sleep(50);
        }
    }
}
