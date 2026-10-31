package net.zaminmc.torch.server;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.entity.MobManager;
import net.zaminmc.torch.server.entity.MobType;
import net.zaminmc.torch.util.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * THE BRIDGE REPAIR'S OWN PROOF (the VIII14 ambient-sound slice's find): the
 * engine's {@code MobEventDispatch} carried only 52 of the Listener
 * interface's 64 events — the seventeen missing ones fired on the
 * MobManager and DIED at the bridge, so a real boot never heard them (every
 * volume/pitch mob sound, the guardian/witch/blaze/ghast/zombie state
 * metadata, the wolf state, the sheep fleece, the enderman stare, the
 * doEntityEvent fan, the door-knock world event). The package tests never
 * caught it because their harnesses register directly on the MobManager;
 * this test goes through the REAL boot path — EngineServer's own tick loop,
 * the manager's per-mob walk, the dispatch, and a listener registered the
 * way the protocol adapter registers (server.addMobListener). The ambient
 * cadence is the carrier: a cow's first voice is guaranteed by tick 1001
 * (the hazard's own hard bound), so the await can never starve past its
 * deadline.
 */
class MobEventBridgeAcceptanceTest {

    @TempDir
    Path dataDir;

    private EngineServer server;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "bridge" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 4, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false, EngineConfig.WHOLE_WORLD_REGION_SIZE);
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    private void await(BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 90_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("timed out waiting for " + description);
    }

    @Test
    void theAmbientSoundCrossesTheRealBootBridge() throws Exception {
        // The voice itself is the polled condition, not "any sound": the
        // mob's footsteps (step.grass) ride the SAME bridge event, and a
        // wander step can beat or overwrite the first ambient voice — the
        // run 532/557 hosted-runner race (the last-write-wins slot turned
        // the any-sound await into a coin flip). Capturing the VOICE event
        // at landing keeps both asserts exact and the await honest (the
        // ambient cadence's tick-1001 hard bound still governs).
        AtomicReference<String> voice = new AtomicReference<>();
        AtomicReference<Float> voiceVolume = new AtomicReference<>();
        // The protocol adapter's own registration path: the listener the
        // dispatch must reach (the seventeen missing re-fans carry it).
        server.addMobListener(new MobManager.Listener() {
            @Override
            public void onMobSoundAt(net.zaminmc.torch.server.entity.MobEntity mob,
                                     String soundName, float vol, float pitch) {
                if ("mob.cow.say".equals(soundName)) {
                    voice.compareAndSet(null, soundName);
                    voiceVolume.compareAndSet(null, vol);
                }
            }


            @Override
            public void onMobSpawned(net.zaminmc.torch.server.entity.MobEntity mob) {
            }

            @Override
            public void onMobMoved(net.zaminmc.torch.server.entity.MobEntity mob,
                    net.zaminmc.torch.server.player.PlayerSession rider) {
            }

            @Override
            public void onMobHurt(net.zaminmc.torch.server.entity.MobEntity mob) {
            }

            @Override
            public void onMobDied(net.zaminmc.torch.server.entity.MobEntity mob) {
            }

            @Override
            public void onMobRemoved(net.zaminmc.torch.server.entity.MobEntity mob, String reason) {
            }

            @Override
            public void onMobAttackedPlayer(net.zaminmc.torch.server.entity.MobEntity mob,
                    net.zaminmc.torch.server.player.PlayerSession target, float damage) {
            }

            @Override
            public void onMobSound(net.zaminmc.torch.server.entity.MobEntity mob, String soundName) {
            }

            @Override
            public void onMobRangedAttack(net.zaminmc.torch.server.entity.MobEntity mob,
                    net.zaminmc.torch.util.Position aimPoint) {
            }

            @Override
            public void onMobFuseChanged(net.zaminmc.torch.server.entity.MobEntity mob, boolean priming) {
            }

            @Override
            public void onMobSheared(net.zaminmc.torch.server.entity.MobEntity mob, int woolCount) {
            }

            @Override
            public void onMobCoatRegrown(net.zaminmc.torch.server.entity.MobEntity mob) {
            }

            @Override
            public void onMobExploded(net.zaminmc.torch.server.entity.MobEntity mob) {
            }
        });
        server.ticker().submit(() -> {
            net.zaminmc.torch.server.entity.MobEntity cow =
                    server.mobs().spawnAt(MobType.COW, new Position(0.5, 5, 0.5));
            assertNotNull(cow, "the cow spawned");
        });
        await(() -> voice.get() != null, "the cow's ambient voice crossed the bridge");
        assertEquals("mob.cow.say", voice.get(), "the repaired idle name");
        assertEquals(0.4f, voiceVolume.get(), 1e-6f, "the cow's play volume (CowEntity line 65)");
    }

    @Test
    void thePigVoiceCarriesTheSameBridge() throws Exception {
        // A second lane on the same repaired re-fan: the pig's 1.0-volume
        // voice through the identical boot path (a different idle family,
        // the same dispatch arm). The voice-named capture, not an any-sound
        // slot — the pig's own step.grass rides the same event (see the
        // cow seat's note).
        AtomicReference<String> pigVoice = new AtomicReference<>();
        server.addMobListener(new MobManager.Listener() {
            @Override
            public void onMobSoundAt(net.zaminmc.torch.server.entity.MobEntity mob,
                                     String soundName, float vol, float pitch) {
                if ("mob.pig.say".equals(soundName)) {
                    pigVoice.compareAndSet(null, soundName);
                }
            }


            @Override
            public void onMobSpawned(net.zaminmc.torch.server.entity.MobEntity mob) {
            }

            @Override
            public void onMobMoved(net.zaminmc.torch.server.entity.MobEntity mob,
                    net.zaminmc.torch.server.player.PlayerSession rider) {
            }

            @Override
            public void onMobHurt(net.zaminmc.torch.server.entity.MobEntity mob) {
            }

            @Override
            public void onMobDied(net.zaminmc.torch.server.entity.MobEntity mob) {
            }

            @Override
            public void onMobRemoved(net.zaminmc.torch.server.entity.MobEntity mob, String reason) {
            }

            @Override
            public void onMobAttackedPlayer(net.zaminmc.torch.server.entity.MobEntity mob,
                    net.zaminmc.torch.server.player.PlayerSession target, float damage) {
            }

            @Override
            public void onMobSound(net.zaminmc.torch.server.entity.MobEntity mob, String soundName) {
            }

            @Override
            public void onMobRangedAttack(net.zaminmc.torch.server.entity.MobEntity mob,
                    net.zaminmc.torch.util.Position aimPoint) {
            }

            @Override
            public void onMobFuseChanged(net.zaminmc.torch.server.entity.MobEntity mob, boolean priming) {
            }

            @Override
            public void onMobSheared(net.zaminmc.torch.server.entity.MobEntity mob, int woolCount) {
            }

            @Override
            public void onMobCoatRegrown(net.zaminmc.torch.server.entity.MobEntity mob) {
            }

            @Override
            public void onMobExploded(net.zaminmc.torch.server.entity.MobEntity mob) {
            }
        });
        server.ticker().submit(() ->
                server.mobs().spawnAt(MobType.PIG, new Position(2.5, 5, 0.5)));
        await(() -> pigVoice.get() != null, "the pig's voice crossed the bridge");
        assertEquals("mob.pig.say", pigVoice.get());
    }
}
