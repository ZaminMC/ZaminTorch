package net.zaminmc.torch.protocol.v1_8;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE HORSE-FEED INTERACT'S FX PAIR (Wave PE-33 — the pin's wire
 * seat, HorseBaseEntity.playEatingAnimation lines 346-352 over the
 * feed arm's interactMob walk): the "eating" sound at volume 1.0 with
 * the two-draw pitch jitter {@code 1.0 + (r - r) * 0.2} (the wire's
 * u8 byte band {@code round([0.8, 1.2] * 63)} = [50, 76]), and the
 * body's own SILENCE gate ({@code !isSilent()} — a silent body never
 * plays the voice, the feed itself still landing). The unit consults
 * landed with PE-28; this seat pins them over the real session.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HorseFeedSoundAcceptanceTest extends ProtocolTestBase {

    /** The feed loops wait on real ticks; keep-alives must not kick. */
    @Override
    protected long keepAliveIntervalMs() {
        return 120_000;
    }

    /**
     * Pre-seeds ops.json so the test player can run the op-gated verbs
     * (/give and /spawnmob among them): the offline-mode UUID convention
     * resolves the name deterministically before the engine boots.
     */
    @Override
    @org.junit.jupiter.api.BeforeAll
    void bootStack() throws Exception {
        java.nio.file.Files.write(dataDir.resolve("ops.json"), java.util.List.of(
                "[{\"uuid\": \"" + java.util.UUID.nameUUIDFromBytes(
                        "OfflinePlayer:Groom".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                        + "\", \"name\": \"Groom\", \"level\": 4, \"bypassesPlayerLimit\": false}]"
        ));
        super.bootStack();
    }

    private void awaitPlayer(String name) throws InterruptedException {
        awaitCondition(() -> server.playerRegistry().byName(name).isPresent(), "player joined");
    }

    /** Feeds the horse until the eating voice arrives (the wander moves the body between the walk and the interact). */
    private String[] feedUntilTheVoiceArrives(TestClient18 client, int horseId) throws Exception {
        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline) {
            var horse = server.mobs().byId(horseId);
            if (horse == null) {
                break;
            }
            client.walkTo(horse.position().x(), horse.position().y(), horse.position().z(), true);
            client.sendUseEntity(horseId, Protocol18.USE_ENTITY_INTERACT);
            long voiceDeadline = System.currentTimeMillis() + 1_500;
            while (System.currentTimeMillis() < voiceDeadline) {
                String[] sound = client.readNamedSoundFull(
                        Math.max(1, voiceDeadline - System.currentTimeMillis()));
                if ("eating".equals(sound[0])) {
                    return sound;
                }
            }
        }
        throw new AssertionError("the eating voice never arrived within 60s");
    }

    /**
     * SEAT — THE FX PAIR + THE SILENCE GATE: an untamed horse fed wheat
     * (the temper arm's own flag) plays the eating voice at 1.0/1.0 +
     * jitter over the wire; a SILENT body (the Silent row's own flag)
     * never plays it — the feed itself still landing (the temper grows).
     */
    @Test
    void theHorseFeedPlaysTheEatingVoiceAndTheSilentGateHolds() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client.sendHandshake(47, 2);
            client.sendLoginStart("Groom");
            client.readLoginSuccess();
            client.readUntilPositionAndLook();
            awaitPlayer("Groom");
            client.readWindowItems(10_000);

            client.sendChat("/time set noon");
            client.sendChat("/spawnmob horse 1");
            int[] spawn = client.readSpawnMobOfType(100, 20_000);
            int horseId = spawn[0];
            awaitCondition(() -> server.mobs().byId(horseId) != null
                    && server.mobs().byId(horseId).temper() == 0, "the fresh horse stands");

            // The wheat in the held slot.
            client.sendChat("/give Groom wheat 4");
            client.drainGiveGhostWake(150);
            client.readWindowSlotTable(10_000);

            // THE FX PAIR: the eating voice at 1.0 with the jitter band.
            String[] sound = feedUntilTheVoiceArrives(client, horseId);
            assertEquals("1.0", sound[1],
                    "playEatingAnimation line 350: the eating voice rides"
                            + " volume 1.0");
            int pitchByte = Integer.parseInt(sound[2]);
            assertTrue(pitchByte >= 50 && pitchByte <= 76,
                    "the two-draw pitch jitter 1.0 + (r - r) * 0.2 lands in the"
                            + " wire band [50, 76], got " + pitchByte);
            int temperAfterFirst = server.mobs().byId(horseId).temper();
            assertTrue(temperAfterFirst > 0,
                    "the temper arm landed the feed (the untamed aid)");

            // THE SILENCE GATE: a silent body never plays the voice — the
            // feed itself still landing (the temper grows past the first).
            server.ticker().submit(() ->
                    server.mobs().byId(horseId).setSilent(true));
            awaitCondition(() -> server.mobs().byId(horseId).isSilent(), "the silence flag stands");
            client.sendChat("/give Groom wheat 4");
            client.drainGiveGhostWake(150);
            client.readWindowSlotTable(10_000);

            long deadline = System.currentTimeMillis() + 30_000;
            boolean fed = false;
            while (!fed && System.currentTimeMillis() < deadline) {
                var horse = server.mobs().byId(horseId);
                client.walkTo(horse.position().x(), horse.position().y(), horse.position().z(), true);
                client.sendUseEntity(horseId, Protocol18.USE_ENTITY_INTERACT);
                long temperDeadline = System.currentTimeMillis() + 1_500;
                while (System.currentTimeMillis() < temperDeadline) {
                    if (server.mobs().byId(horseId).temper() > temperAfterFirst) {
                        fed = true;
                        break;
                    }
                    Thread.sleep(50);
                    client.pumpKeepAlives();
                }
            }
            assertTrue(fed, "the silent body still takes the feed (the temper grows)");
            // The voice never crossed the silence gate: every sound the
            // window carries (the wander's own step/ambient voices
            // interleave) is SOMEONE ELSE'S — none of them "eating".
            long gateDeadline = System.currentTimeMillis() + 2_500;
            boolean anyEating = false;
            while (System.currentTimeMillis() < gateDeadline) {
                try {
                    String[] other = client.readNamedSoundFull(
                            Math.max(1, gateDeadline - System.currentTimeMillis()));
                    if ("eating".equals(other[0])) {
                        anyEating = true;
                        break;
                    }
                } catch (IOException timeout) {
                    break; // the window closed with no eating voice
                }
            }
            assertTrue(!anyEating,
                    "!isSilent(): the eating voice never plays for a silent body");
        }
        awaitEmptyServer();
    }
}
