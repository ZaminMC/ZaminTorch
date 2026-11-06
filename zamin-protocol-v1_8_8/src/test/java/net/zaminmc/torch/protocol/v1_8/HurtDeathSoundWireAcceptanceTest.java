package net.zaminmc.torch.protocol.v1_8;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE HURT/DEATH SOUND WIRE (Wave PE-32 — LivingEntity.takeDamage lines
 * 711-723): the reference's {@code playSound(sound, getSoundVolume(),
 * getSoundPitch())} consults at both wound arms — the per-kind VOLUME
 * table (getSoundVolume's overrides: the cow 0.4, the horse 0.8, the
 * bat 0.1) and the PITCH two-draw jitter {@code (r - r) * 0.2 + 1.0}
 * (the bat's own *0.95 multiplier) — and the KILLING BLOW plays the
 * death sound ALONE (the else arm: the pair never crosses). The
 * pre-fix wire played every wound at a flat 1.0/1.0 and crossed the
 * pair on the killing blow — every seat here is RED on it.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HurtDeathSoundWireAcceptanceTest extends ProtocolTestBase {

    /** The wound flows wait on real ticks; keep-alives must not kick. */
    @Override
    protected long keepAliveIntervalMs() {
        return 120_000;
    }

    /**
     * Pre-seeds ops.json so the test player can run the op-gated verbs
     * (/spawnmob among them): the offline-mode UUID convention resolves
     * the name deterministically before the engine boots.
     */
    @Override
    @org.junit.jupiter.api.BeforeAll
    void bootStack() throws Exception {
        java.nio.file.Files.write(dataDir.resolve("ops.json"), java.util.List.of(
                "[{\"uuid\": \"" + java.util.UUID.nameUUIDFromBytes(
                        "OfflinePlayer:Herdsman".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                        + "\", \"name\": \"Herdsman\", \"level\": 4, \"bypassesPlayerLimit\": false}]"
        ));
        super.bootStack();
    }

    private void awaitPlayer(String name) throws InterruptedException {
        awaitCondition(() -> server.playerRegistry().byName(name).isPresent(), "player joined");
    }

    /** Reads sounds until the expected NAME arrives (the ambient cadence interleaves). */
    private String[] readSoundNamed(TestClient18 client, String name, long timeoutMs)
            throws IOException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            String[] sound = client.readNamedSoundFull(
                    Math.max(1, deadline - System.currentTimeMillis()));
            if (name.equals(sound[0])) {
                return sound;
            }
        }
        throw new AssertionError("the " + name + " sound never arrived within " + timeoutMs + "ms");
    }

    /**
     * SEAT 1 — THE COW'S VOLUME TABLE ROW (CowEntity line 65:
     * {@code getSoundVolume() = 0.4F}): a surviving wound's hurt sound
     * carries the per-kind volume — never the pre-fix flat 1.0 — and
     * the pitch byte sits inside the two-draw jitter band
     * {@code round([0.8, 1.2] * 63)} = [50, 76].
     */
    @Test
    void theCowSHurtSoundCarriesTheVolumeTableRow() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client.sendHandshake(47, 2);
            client.sendLoginStart("Herdsman");
            client.readLoginSuccess();
            client.readUntilPositionAndLook();
            awaitPlayer("Herdsman");
            client.readWindowItems(10_000);

            client.sendChat("/time set noon");
            client.sendChat("/spawnmob cow 1");
            int[] spawn = client.readSpawnMobOfType(92, 20_000);
            int cowId = spawn[0];

            // A surviving wound (10 hp body, 3 damage): the hurt sound.
            server.ticker().submit(() ->
                    server.mobs().hurt(server.mobs().byId(cowId), 3.0f, 0.0));
            String[] sound = readSoundNamed(client, "mob.cow.hurt", 15_000);
            assertEquals("0.4", sound[1],
                    "CowEntity line 65: the cow's hurt sound rides the volume"
                            + " table's 0.4 row (the pre-fix wire played 1.0)");
            int pitchByte = Integer.parseInt(sound[2]);
            assertTrue(pitchByte >= 50 && pitchByte <= 76,
                    "the pitch byte sits in the two-draw jitter band [50, 76],"
                            + " got " + pitchByte);
        }
        awaitEmptyServer();
    }

    /**
     * SEAT 2 — THE KILLING BLOW'S SPLIT (takeDamage lines 711-723): a
     * lethal wound plays the DEATH sound alone (the reference's else
     * arm — the hurt sound never crosses the killing blow), the horse's
     * own volume row 0.8 (HorseBaseEntity line 572). The pre-fix wire
     * played the hurt sound ("mob.horse.hit") first — RED.
     */
    @Test
    void theKillingBlowPlaysTheDeathSoundAlone() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client.sendHandshake(47, 2);
            client.sendLoginStart("Herdsman");
            client.readLoginSuccess();
            client.readUntilPositionAndLook();
            awaitPlayer("Herdsman");
            client.readWindowItems(10_000);

            client.sendChat("/time set noon");
            client.sendChat("/spawnmob horse 1");
            int[] spawn = client.readSpawnMobOfType(100, 20_000);
            int horseId = spawn[0];

            // The lethal wound: the FIRST sound the wire carries is the
            // DEATH voice alone — never the hurt voice (the pre-fix wire's
            // first sound was "mob.horse.hit", RED).
            server.ticker().submit(() ->
                    server.mobs().hurt(server.mobs().byId(horseId), 1000.0f, 0.0));
            String[] sound = client.readNamedSoundFull(15_000);
            assertEquals("mob.horse.death", sound[0],
                    "takeDamage's else arm: the killing blow plays the death"
                            + " sound alone (the hurt voice never crosses)");
            assertEquals("0.8", sound[1],
                    "HorseBaseEntity line 572: the death sound rides the horse's"
                            + " 0.8 volume row");
        }
        awaitEmptyServer();
    }

    /**
     * SEAT 3 — THE BAT'S OWN PAIR (BatEntity line 29's volume 0.1 +
     * line 33's pitch {@code super.getSoundPitch() * 0.95F}): the bat's
     * hurt sound carries the 0.1 volume row and the 0.95-scaled pitch
     * band {@code round([0.76, 1.14] * 63)} = [48, 72] — the plain band
     * would reach 76.
     */
    @Test
    void theBatSHurtSoundCarriesTheVolumeAndPitchPair() throws Exception {
        try (TestClient18 client = new TestClient18("127.0.0.1", adapter.boundPort())) {
            client.sendHandshake(47, 2);
            client.sendLoginStart("Herdsman");
            client.readLoginSuccess();
            client.readUntilPositionAndLook();
            awaitPlayer("Herdsman");
            client.readWindowItems(10_000);

            client.sendChat("/time set noon");
            // The bat is not a /spawnmob kind: the engine's own spawn
            // entry seats it (the HuntScanBounds rig's form).
            int[] batId = new int[1];
            server.ticker().submit(() -> {
                net.zaminmc.torch.server.entity.MobEntity bat =
                        server.mobs().spawnAt(net.zaminmc.torch.server.entity.MobType.BAT,
                                new net.zaminmc.torch.util.Position(0.5, 40.0, 0.5), 0);
                batId[0] = bat.entityId();
            });
            awaitCondition(() -> batId[0] != 0, "the bat spawned");
            client.readSpawnMobOfType(65, 20_000);

            server.ticker().submit(() ->
                    server.mobs().hurt(server.mobs().byId(batId[0]), 2.0f, 0.0));
            String[] sound = readSoundNamed(client, "mob.bat.hurt", 15_000);
            assertEquals("0.1", sound[1],
                    "BatEntity line 29: the bat's hurt sound rides the 0.1"
                            + " volume row (the pre-fix wire played 1.0)");
            int pitchByte = Integer.parseInt(sound[2]);
            assertTrue(pitchByte >= 48 && pitchByte <= 72,
                    "the bat's pitch byte sits in the 0.95-scaled band [48, 72],"
                            + " got " + pitchByte);
        }
        awaitEmptyServer();
    }
}
