package net.zaminmc.torch.protocol.v1_8;

import net.zaminmc.torch.entity.PlayerState;
import net.zaminmc.torch.server.player.PlayerSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wave J's live flagship — the attribute modifiers over the real wire: a
 * {@code /give} with a dataTag feeds a damage-boosted sword through the
 * read walk (the SNBT parse, the fromNbt shape, the keep-gate), the held
 * slot's fold (computeValue over the player's registered base 1.0) rides
 * the melee into the victim's health re-sync — and the WIPE: a zero-uuid
 * row skips, the emptied list never falls back to the sword's defaults,
 * the swing deals the bare fist's 1.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AttributeMeleeIntegrationTest extends ProtocolTestBase {

    @Override
    protected long keepAliveIntervalMs() {
        return 2_000; // the scripted clients answer only when the test drives it
    }

    private static final long HURT_WINDOW_MS = 700; // i-frames: 10 ticks nominal (500ms)

    private PlayerSession awaitPlayer(String name) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            PlayerSession session = server.playerRegistry().byName(name).orElse(null);
            if (session != null) {
                return session;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Player " + name + " never registered");
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
                        "OfflinePlayer:Alice".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                        + "\", \"name\": \"Alice\", \"level\": 4, \"bypassesPlayerLimit\": false},",
                " {\"uuid\": \"" + java.util.UUID.nameUUIDFromBytes(
                        "OfflinePlayer:Bob".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                        + "\", \"name\": \"Bob\", \"level\": 4, \"bypassesPlayerLimit\": false}]"
        ));
        super.bootStack();
    }

    @Test
    void theBoostedSwordFoldsItsModifiersIntoTheLiveMelee() throws Exception {
        try (TestClient18 alice = new TestClient18("127.0.0.1", adapter.boundPort());
             TestClient18 bob = new TestClient18("127.0.0.1", adapter.boundPort())) {

            // --- both join at spawn: one block apart, melee reach for sure ---
            alice.sendHandshake(47, 2);
            alice.sendLoginStart("Alice");
            alice.readLoginSuccess();
            alice.readUntilPositionAndLook();
            PlayerSession aliceSession = awaitPlayer("Alice");

            bob.sendHandshake(47, 2);
            bob.sendLoginStart("Bob");
            bob.readLoginSuccess();
            bob.readUntilPositionAndLook();
            PlayerSession bobSession = awaitPlayer("Bob");
            awaitCondition(() -> bobSession.state() == PlayerState.PLAYING
                    && aliceSession.state() == PlayerState.PLAYING, "both PLAYING");

            int aliceSeesBob = alice.readNamedSpawn(5_000)[0];
            int bobSeesAlice = bob.readNamedSpawn(5_000)[0]; // each client spawns the other exactly once

            // --- the boosted sword: Amount 10 op 0 over the base 1.0 — the
            // fold answers 11, the dataTag riding the /give's SNBT parse
            // (the 1.8 grammar's own suffixes: doubles d, longs L).
            alice.sendChat("/give Alice diamond_sword 1 0 {AttributeModifiers:"
                    + "[{AttributeName:\"generic.attackDamage\","
                    + "Name:\"Weapon modifier\",Amount:10.0d,Operation:0,"
                    + "UUIDMost:1L,UUIDLeast:2L}]}");
            alice.drainGiveGhostWake(150); // the makeFakeItem ghost's wake
            alice.readWindowItems(5_000); // the authoritative inventory sync
            assertTrue(aliceSession.inventory().held() != null
                            && !aliceSession.inventory().held().isEmpty(),
                    "the granted sword sits in the held slot");

            // --- the swing: Bob's client sees the wound's whole sequence ---
            alice.sendUseEntity(aliceSeesBob, Protocol18.USE_ENTITY_ATTACK);
            bob.readEntityStatus(5_000);
            bob.readEntityVelocity(5_000);
            double[] health = bob.readUpdateHealth(5_000);
            assertEquals(9.0, health[0], 0.01,
                    "the fold rides the melee (base 1.0 + 10.0 op-0 = 11)");

            Thread.sleep(HURT_WINDOW_MS);

            // --- the WIPE sword: a zero-uuid row (both arms zero) drops at
            // the read gate, the emptied list NEVER falls back to the
            // sword's default weapon modifier — the swing deals the bare
            // fist's 1. Bob's own first /give lands his own held slot.
            bob.sendChat("/give Bob diamond_sword 1 0 {AttributeModifiers:"
                    + "[{AttributeName:\"generic.attackDamage\","
                    + "Name:\"Wiped\",Amount:99.0d,Operation:0,"
                    + "UUIDMost:0L,UUIDLeast:0L}]}");
            bob.drainGiveGhostWake(150); // the makeFakeItem ghost's wake
            bob.readWindowItems(5_000);
            assertTrue(bobSession.inventory().held() != null
                            && !bobSession.inventory().held().isEmpty(),
                    "the wiped sword sits in Bob's held slot");
            assertTrue(bobSession.inventory().held().attributeModifiers() != null,
                    "the zero-uuid skip leaves the list PRESENT (the wipe)");
            assertTrue(bobSession.inventory().held().attributeModifiers().isEmpty(),
                    "the zero-uuid row dropped");

            // --- Bob's swing: Alice takes the fist's 1 (20 -> 19) ---
            bob.sendUseEntity(bobSeesAlice, Protocol18.USE_ENTITY_ATTACK);
            alice.readEntityStatus(5_000);
            alice.readEntityVelocity(5_000);
            double[] wiped = alice.readUpdateHealth(5_000);
            assertEquals(19.0, wiped[0], 0.01,
                    "the wipe folds the bare fist — never the sword's defaults");
        }
    }
}
