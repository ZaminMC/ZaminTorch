package net.zaminmc.torch.server;

import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.player.PlayerSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tab-list header/footer API (Slice 87 — the 0x47 native surface over
 * TabListS2CPacket's two-text walk): the broadcast reaches every live
 * session, later joins receive the current pair at their own burst, and a
 * null clear lands the wire's null-text form.
 */
class TabListHeaderFooterApiTest {

    @TempDir
    Path dataDir;

    EngineServer server;

    private static final class RecordingLink implements net.zaminmc.torch.server.net.ClientLink {
        final CopyOnWriteArrayList<String> lines = new CopyOnWriteArrayList<>();

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public void kick(String reason) {
        }

        @Override
        public void tabListHeaderFooter(String headerJson, String footerJson) {
            lines.add(headerJson + "|" + footerJson);
        }
    }

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "tabhdr" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString(),
                net.zaminmc.torch.GameMode.SURVIVAL);
        server = new EngineServer(config);
        server.start();
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    private PlayerSession join(String name, RecordingLink link) {
        var accepted = (net.zaminmc.torch.server.net.EngineBridge.Accepted)
                server.joinRequest(link, name,
                        UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes()));
        PlayerSession session = accepted.session();
        server.ticker().submit(session::markPlaying);
        return session;
    }

    @Test
    void theBroadcastAndJoinConsistency() throws Exception {
        RecordingLink aliceLink = new RecordingLink();
        RecordingLink bobLink = new RecordingLink();
        PlayerSession alice = join("Alice", aliceLink);
        PlayerSession bob = join("Bob", bobLink);
        await(() -> alice.state() == net.zaminmc.torch.entity.PlayerState.PLAYING
                        && bob.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                "both playing");

        // The broadcast reaches every live session.
        server.setTabListHeaderFooter("{\"text\":\"Welcome\"}", "{\"text\":\"Bye\"}");
        await(() -> !aliceLink.lines.isEmpty() && !bobLink.lines.isEmpty(),
                "both wires carry the pair");
        assertEquals("{\"text\":\"Welcome\"}|{\"text\":\"Bye\"}", aliceLink.lines.get(0));
        assertEquals("{\"text\":\"Welcome\"}|{\"text\":\"Bye\"}", bobLink.lines.get(0));

        // A later join receives the current pair at its own burst (the core
        // test drives the burst explicitly — the protocol adapter's own
        // order calls the same hook).
        RecordingLink carolLink = new RecordingLink();
        PlayerSession carol = join("Carol", carolLink);
        await(() -> carol.state() == net.zaminmc.torch.entity.PlayerState.PLAYING, "carol playing");
        server.joinTabListHeaderFooter(carol);
        await(() -> !carolLink.lines.isEmpty(), "the joiner got the pair");
        assertEquals("{\"text\":\"Welcome\"}|{\"text\":\"Bye\"}", carolLink.lines.get(0));

        // The null clear lands the wire's null-text form.
        server.setTabListHeaderFooter(null, null);
        await(() -> aliceLink.lines.size() >= 2, "the clear broadcast");
        assertEquals("null|null", aliceLink.lines.get(1));
    }

    private void await(java.util.function.BooleanSupplier condition, String description)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Condition not met in time: " + description);
    }
}
