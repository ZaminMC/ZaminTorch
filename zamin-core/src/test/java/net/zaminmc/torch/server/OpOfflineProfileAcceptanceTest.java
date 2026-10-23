package net.zaminmc.torch.server;

import net.zaminmc.torch.server.chat.CommandSender;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.net.ClientLink;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.ops.OpStore;
import net.zaminmc.torch.server.player.PlayerSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * THE /OP OFFLINE-PROFILE RESOLUTION (ML-005 — the audit's B-3 row, the
 * reference walk the cycle-3 playtest verified): the reference resolves
 * {@code /op} through the profile cache (dedicated OpCommand lines
 * 30-39: the cache get, then addOp + commands.op.success, or the
 * {@code commands.op.failed} line), so an operator pre-ops a player who
 * has joined once but is currently offline. The engine's cache
 * equivalent rides ONLY persisted identities — the .zpd census, the
 * ops.json rows, this run's joins — never a new persistence format:
 *
 * <ul>
 *   <li>the same-run arm: a join-then-quit name still ops (the login's
 *       own cache add);</li>
 *   <li>the restart arm: a fresh boot's census re-reads the .zpd
 *       headers, so the offline op survives restarts;</li>
 *   <li>the ops.json seed: a registry row with no .zpd at all (the
 *       hand-maintained form) still resolves;</li>
 *   <li>the online arm unchanged: a live session ops exactly as
 *       before;</li>
 *   <li>an unknown name answers the reference's commands.op.failed
 *       line ("Could not op %s", 1.8.8 en_US.lang) and writes
 *       nothing.</li>
 * </ul>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OpOfflineProfileAcceptanceTest {

    @TempDir
    Path dataDir;

    private EngineServer boot() {
        EngineConfig config = new EngineConfig("127.0.0.1", 0, "opcache", "it",
                20, 2, 20, dataDir.toString());
        return new EngineServer(config);
    }

    private PlayerSession join(EngineServer server, String name) {
        ClientLink link = new ClientLink() {
            @Override public boolean isActive() { return true; }
            @Override public void kick(String reason) { }
        };
        UUID uuid = offlineUuid(name);
        var result = server.joinRequest(link, name, uuid);
        PlayerSession session = ((EngineBridge.Accepted) result).session();
        // The protocol layer's own post-burst walk (the rig's established
        // shape): drives the session to PLAYING.
        server.joinCompleted(session);
        return session;
    }

    private static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    private Path playerFile(String name) {
        return dataDir.resolve("opcache").resolve("playerdata")
                .resolve(offlineUuid(name) + ".zpd");
    }

    private void await(BooleanSupplier condition, String description) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }

    /** The console-shaped recording sender, dispatched on the tick lane like the real console. */
    private static final class RecordingSender implements CommandSender {
        final List<String> received = new CopyOnWriteArrayList<>();

        @Override public String name() { return "Server"; }
        @Override public void sendMessage(String message) { received.add(message); }
        @Override public int opLevel() { return 4; }
        @Override public PlayerSession player() { return null; }
    }

    /** Runs one console command on the tick lane and answers its feedback lines. */
    private List<String> console(EngineServer server, String line) throws InterruptedException {
        RecordingSender sender = new RecordingSender();
        AtomicBoolean done = new AtomicBoolean();
        server.ticker().submit(() -> {
            try {
                server.chatService().dispatchCommand(sender, "/" + line);
            } finally {
                done.set(true);
            }
        });
        await(done::get, "the command ran: " + line);
        return sender.received;
    }

    @Test
    void theSameRunArmOpsAQuitPlayerAndRefusesUnknownNames() throws Exception {
        EngineServer server = boot();
        server.start();
        try {
            PlayerSession notch = join(server, "Notch");
            server.clientDisconnected(notch, "test leave");
            await(() -> Files.exists(playerFile("Notch")), "the quit persisted Notch's .zpd");

            // THE OFFLINE OP: the reference's cache resolution — success
            // text in the engine's established style, the entry written
            // through OpStore at level 4.
            List<String> feedback = console(server, "op Notch");
            assertEquals(1, feedback.size(), "one feedback line: " + feedback);
            assertEquals("Made Notch a server operator", feedback.get(0));
            OpStore.Entry entry = server.opStore().byName("Notch");
            assertEquals(offlineUuid("Notch"), entry.uuid(), "the stored offline uuid");
            assertEquals(4, entry.level(), "the /op level");
            // The persisted form answers a fresh load of ops.json.
            OpStore onDisk = OpStore.load(dataDir.resolve("ops.json"));
            assertEquals("Notch", onDisk.byName("Notch").name(), "ops.json carries the name");

            // THE UNKNOWN NAME: the reference's commands.op.failed line,
            // nothing written.
            feedback = console(server, "op NobodyEverJoined");
            assertEquals(List.of("Could not op NobodyEverJoined"), feedback,
                    "the vanilla commands.op.failed line");
            assertNull(server.opStore().byName("NobodyEverJoined"), "no entry for the unknown name");
            assertNull(OpStore.load(dataDir.resolve("ops.json")).byName("NobodyEverJoined"),
                    "ops.json stayed clean of the unknown name");

            // THE ONLINE ARM UNCHANGED: a live session ops exactly as
            // before — the level applies to the session, the feedback
            // carries the session's own name.
            PlayerSession steve = join(server, "Steve");
            await(() -> steve.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                    "Steve reached PLAYING");
            feedback = console(server, "op Steve");
            assertEquals(List.of("Made Steve a server operator"), feedback);
            await(() -> steve.opLevel() == 4, "the live session's level applied");
            assertEquals(offlineUuid("Steve"), server.opStore().byName("Steve").uuid());
        } finally {
            server.shutdown(null);
        }
    }

    @Test
    void theBootCensusResolvesPersistedIdentitiesAcrossARestart() throws Exception {
        // --- run 1: the identity joins and leaves (the .zpd lands) ---
        EngineServer first = boot();
        first.start();
        PlayerSession notch = join(first, "Notch");
        first.clientDisconnected(notch, "test leave");
        await(() -> Files.exists(playerFile("Notch")), "the quit persisted Notch's .zpd");
        first.shutdown(null);

        // A hand-maintained ops.json row with NO .zpd behind it: the
        // registry is a resolution source of its own (the operator's own
        // record survives without a join).
        Files.writeString(dataDir.resolve("ops.json"), "[\n"
                + "  {\"uuid\": \"" + offlineUuid("LegacyOp") + "\", \"name\": \"LegacyOp\", "
                + "\"level\": 2, \"bypassesPlayerLimit\": true}\n"
                + "]\n");

        // --- run 2: the census re-reads the .zpd headers at boot ---
        EngineServer second = boot();
        second.start();
        try {
            // The .zpd census arm: Notch never joined THIS run.
            List<String> feedback = console(second, "op Notch");
            assertEquals(List.of("Made Notch a server operator"), feedback,
                    "the boot census resolved the persisted identity");
            assertEquals(offlineUuid("Notch"), second.opStore().byName("Notch").uuid(),
                    "the census carried the uuid");

            // The ops.json seed arm: a registry row without a .zpd.
            feedback = console(second, "op LegacyOp");
            assertEquals(List.of("Made LegacyOp a server operator"), feedback,
                    "the ops.json row resolved on its own");
            assertEquals(offlineUuid("LegacyOp"), second.opStore().byName("LegacyOp").uuid());

            // The unknown name still fails through the reference's line.
            feedback = console(second, "op NeverJoined");
            assertEquals(List.of("Could not op NeverJoined"), feedback);
            assertNull(second.opStore().byName("NeverJoined"));

            // The offline op is live for the next login: the returning
            // player enters at level 4 (the join walk reads ops.json).
            PlayerSession returning = join(second, "Notch");
            await(() -> returning.opLevel() == 4,
                    "the returning player entered already opped");
        } finally {
            second.shutdown(null);
        }
    }
}
