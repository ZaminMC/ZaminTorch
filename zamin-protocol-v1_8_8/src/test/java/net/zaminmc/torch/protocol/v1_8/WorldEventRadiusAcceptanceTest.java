package net.zaminmc.torch.protocol.v1_8;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The doEvent family's 64-block radius over the real wire (the README
 * TODO's row 10 — the reference's ServerWorldEventListener.doEvent →
 * PlayerManager.sendPacket(source, x, y, z, 64.0, dimension, packet)):
 * the world events fan to the SAME-DIMENSION observers within the squared
 * 4096 — NOT the loaded-chunk window the stand-in gated on. The bottle o'
 * enchanting's 2002 splash is the staged member: three scripted clients —
 * the thrower beside the burst, a second observer 55 blocks out (inside
 * the radius), a third 75 blocks out (beyond it but with the event's
 * chunk STILL inside the view-distance-4 window, the exact case the old
 * chunk gate answered wrong).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WorldEventRadiusAcceptanceTest extends ProtocolTestBase {

    @Override
    protected long keepAliveIntervalMs() {
        return 300_000;
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
                        "OfflinePlayer:FarWatcher".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                        + "\", \"name\": \"FarWatcher\", \"level\": 4, \"bypassesPlayerLimit\": false},",
                " {\"uuid\": \"" + java.util.UUID.nameUUIDFromBytes(
                        "OfflinePlayer:MiddleCatcher".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                        + "\", \"name\": \"MiddleCatcher\", \"level\": 4, \"bypassesPlayerLimit\": false},",
                " {\"uuid\": \"" + java.util.UUID.nameUUIDFromBytes(
                        "OfflinePlayer:NearCatcher".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                        + "\", \"name\": \"NearCatcher\", \"level\": 4, \"bypassesPlayerLimit\": false}]"
        ));
        super.bootStack();
    }

    @Test
    void theBottleSplashFansOnlyTheSixtyFourBlockObservers() throws Exception {
        try (TestClient18 near = new TestClient18("127.0.0.1", adapter.boundPort());
             TestClient18 middle = new TestClient18("127.0.0.1", adapter.boundPort());
             TestClient18 far = new TestClient18("127.0.0.1", adapter.boundPort())) {
            near.sendHandshake(47, 2);
            near.sendLoginStart("NearCatcher");
            near.readLoginSuccess();
            near.readUntilPositionAndLook();
            middle.sendHandshake(47, 2);
            middle.sendLoginStart("MiddleCatcher");
            middle.readLoginSuccess();
            middle.readUntilPositionAndLook();
            far.sendHandshake(47, 2);
            far.sendLoginStart("FarWatcher");
            far.readLoginSuccess();
            far.readUntilPositionAndLook();
            awaitPlayers("NearCatcher", "MiddleCatcher", "FarWatcher");

            // The stations: the thrower stays at its spawn anchor; the
            // middle observer stands 55 blocks east (inside the 64-block
            // radius whatever the lob's few-block drift), the far observer
            // 75 blocks east (beyond the radius; the event's chunk is 4-5
            // chunks out — inside the view-distance-4 window, so the old
            // chunk gate would have delivered the event there too).
            var nearSession = server.playerRegistry().byName("NearCatcher").orElseThrow();
            var middleSession = server.playerRegistry().byName("MiddleCatcher").orElseThrow();
            var farSession = server.playerRegistry().byName("FarWatcher").orElseThrow();
            var anchor = nearSession.position();
            server.teleportPlayer(middleSession, new net.zaminmc.torch.util.Position(
                    anchor.x() + 55.0, anchor.y(), anchor.z()));
            server.teleportPlayer(farSession, new net.zaminmc.torch.util.Position(
                    anchor.x() + 75.0, anchor.y(), anchor.z()));
            awaitCondition(() -> middleSession.position().x() == anchor.x() + 55.0,
                    "the middle station reached");
            awaitCondition(() -> farSession.position().x() == anchor.x() + 75.0,
                    "the far station reached");

            // The bottle in the thrower's hand, then the use-in-air press:
            // the lob flies, the burst lands within a few blocks of the
            // thrower, and the 2002 event rides the radius fan.
            near.sendChat("/give NearCatcher minecraft:experience_bottle 4");
            near.drainGiveGhostWake(150);
            awaitCondition(() -> !nearSession.inventory().held().isEmpty(),
                    "the bottle granted into the held slot");
            near.sendUseItemInAir();

            // The thrower itself hears its own burst (the reference's
            // no-source doEvent form — nothing excludes the thrower).
            int[] own = near.readWorldEvent(2002, 10_000);
            assertEquals(2002, own[0], "the splash event id rides the packet");
            assertEquals(0, own[4], "the splash carries the color value 0");
            assertEquals(0, own[5], "the event is not global");

            // The 55-block observer hears the burst too — inside the
            // radius with the chunk cold at its own window's edge.
            int[] middleHeard = middle.readWorldEvent(2002, 10_000);
            assertEquals(2002, middleHeard[0], "the in-radius observer heard the event");

            // The 75-block observer hears NOTHING: beyond the 64-block
            // radius while the event's chunk sits inside its loaded window
            // — the exact row-10 distinction (the chunk gate would have
            // delivered; the radius law refuses).
            boolean leaked = false;
            try {
                far.readWorldEvent(2002, 1_500);
                leaked = true;
            } catch (java.io.IOException expectedTimeout) {
                // the reference's own silence: the radius refused the fan
            }
            assertTrue(!leaked,
                    "the beyond-radius observer never heard the event (the "
                            + "chunk window alone no longer fans)");
        }
    }
}
