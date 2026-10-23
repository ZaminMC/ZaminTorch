package net.zaminmc.torch.server;

import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ML-012 + ML-017 at the engine seam: the /difficulty change-time fan-out
 * reaches every session's link immediately (the byte never waits for a
 * respawn), and the join-time resource-pack hook honors the reference's own
 * url-non-empty gate over the configured rows.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OpsDifficultySyncTest {

    @org.junit.jupiter.api.io.TempDir
    Path dataDir;

    /** The capturing link: every difficulty/resource-pack push is recorded. */
    private static final class RecordingLink implements ClientLink {
        final List<Integer> difficulties = new CopyOnWriteArrayList<>();
        final List<String[]> resourcePacks = new CopyOnWriteArrayList<>();

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public void kick(String reason) {
        }

        @Override
        public void updateDifficulty(int difficultyId) {
            difficulties.add(difficultyId);
        }

        @Override
        public void sendResourcePack(String url, String hash) {
            resourcePacks.add(new String[]{url, hash});
        }
    }

    private EngineServer boot(EngineConfig config) throws InterruptedException {
        EngineServer started = new EngineServer(config);
        started.start();
        return started;
    }

    private PlayerSession join(EngineServer server, RecordingLink link, String name) {
        var result = server.joinRequest(link, name, UUID.nameUUIDFromBytes(name.getBytes()));
        PlayerSession session = ((EngineBridge.Accepted) result).session();
        // The real login's completion flip (the harness drives the same
        // transition the adapter's join walk lands).
        server.ticker().submit(session::markPlaying);
        return session;
    }

    private static void await(BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    @Test
    void difficultyChangeFansOutToEverySessionImmediately() throws Exception {
        EngineServer server = boot(new EngineConfig("127.0.0.1", 0, "ops-diff", "it",
                20, 4, 20, dataDir.resolve("diff").toString()));
        try {
            RecordingLink first = new RecordingLink();
            RecordingLink second = new RecordingLink();
            join(server, first, "OpOne");
            join(server, second, "OpTwo");
            await(() -> server.players().size() == 2, "both sessions present");

            // The change-time fan-out (ML-012): /difficulty hard pushes the
            // byte 3 to BOTH links on the command's own tick — the clients
            // re-sync now, never waiting for their next respawn.
            server.consoleCommand("difficulty hard");
            await(() -> server.difficulty() == 3, "the hard tier landed");
            await(() -> first.difficulties.size() == 1 && second.difficulties.size() == 1,
                    "both links heard the change");
            assertEquals(3, first.difficulties.get(0), "the first link's byte is 3 (hard)");
            assertEquals(3, second.difficulties.get(0), "the second link's byte is 3 (hard)");

            // A later flip re-fans (the tier id follows the command).
            server.consoleCommand("difficulty peaceful");
            await(() -> server.difficulty() == 0, "the peaceful tier landed");
            await(() -> first.difficulties.size() == 2 && second.difficulties.size() == 2,
                    "both links heard the second change");
            assertEquals(0, first.difficulties.get(1), "the peaceful byte is 0");
            assertEquals(0, second.difficulties.get(1), "the peaceful byte is 0");
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theConfiguredResourcePackReachesTheJoinerAndTheUnconfiguredStaysSilent()
            throws Exception {
        // The configured boot (the canonical rows): the join hook pushes the
        // pair to the joiner's link (the reference's own onLogin tail).
        EngineServer packed = boot(new EngineConfig("127.0.0.1", 0, "ops-pack", "it",
                20, 4, 20, dataDir.resolve("packed").toString(),
                net.zaminmc.torch.GameMode.SURVIVAL, true, "flat", false,
                EngineConfig.WHOLE_WORLD_REGION_SIZE, false,
                EngineConfig.DEFAULT_SPAWN_PROTECTION,
                false, "", EngineConfig.UNSET_REMOTE_PORT, false, EngineConfig.UNSET_REMOTE_PORT,
                "https://example.com/pack.zip", "abc123"));
        try {
            RecordingLink link = new RecordingLink();
            PlayerSession joiner = join(packed, link, "PackedJoiner");
            packed.joinResourcePack(joiner);
            assertEquals(1, link.resourcePacks.size(), "the configured pack reached the joiner");
            assertEquals("https://example.com/pack.zip", link.resourcePacks.get(0)[0]);
            assertEquals("abc123", link.resourcePacks.get(0)[1]);
        } finally {
            packed.shutdown(null);
        }

        // The unconfigured boot (the compat rows default empty): the same
        // hook stays silent — the reference's own length() > 0 gate.
        EngineServer plain = boot(new EngineConfig("127.0.0.1", 0, "ops-plain", "it",
                20, 4, 20, dataDir.resolve("plain").toString()));
        try {
            RecordingLink link = new RecordingLink();
            PlayerSession joiner = join(plain, link, "PlainJoiner");
            plain.joinResourcePack(joiner);
            assertTrue(link.resourcePacks.isEmpty(),
                    "no url configured means no pack is ever offered");
        } finally {
            plain.shutdown(null);
        }
    }
}
