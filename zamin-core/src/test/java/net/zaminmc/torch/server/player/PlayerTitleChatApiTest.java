package net.zaminmc.torch.server.player;

import net.zaminmc.torch.server.config.EngineConfig;
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
 * The public Player title/chat surface (Slice 90 — Slice 87's
 * title/chat wire over §27): the title pair's own arm order (Times, the
 * title, the subtitle), the blank-text and zero-count skips, the clear,
 * and the three chat lines (the box, the system line, the raw component).
 */
class PlayerTitleChatApiTest {

    @TempDir
    Path dataDir;

    net.zaminmc.torch.server.EngineServer server;
    PlayerSession alice;
    RecordingLink link;

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "title" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString(),
                net.zaminmc.torch.GameMode.SURVIVAL);
        server = new net.zaminmc.torch.server.EngineServer(config);
        server.start();
        link = new RecordingLink();
        var accepted = (net.zaminmc.torch.server.net.EngineBridge.Accepted)
                server.joinRequest(link, "Alice",
                        UUID.nameUUIDFromBytes("OfflinePlayer:Alice".getBytes()));
        alice = accepted.session();
        server.ticker().submit(alice::markPlaying);
        await(() -> alice.state() == net.zaminmc.torch.entity.PlayerState.PLAYING, "playing");
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    /** The wire-recording link: the title family + the chat components. */
    private static final class RecordingLink implements net.zaminmc.torch.server.net.ClientLink {
        final java.util.List<int[]> times = new CopyOnWriteArrayList<>();
        final java.util.List<String> titles = new CopyOnWriteArrayList<>();
        final java.util.List<String> subtitles = new CopyOnWriteArrayList<>();
        final java.util.List<String> clears = new CopyOnWriteArrayList<>();
        final java.util.List<String> chats = new CopyOnWriteArrayList<>();

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public void kick(String reason) {
        }

        @Override
        public void titleTimes(int fadeIn, int duration, int fadeOut) {
            times.add(new int[] {fadeIn, duration, fadeOut});
        }

        @Override
        public void titleTitle(String jsonText) {
            titles.add(jsonText);
        }

        @Override
        public void titleSubtitle(String jsonText) {
            subtitles.add(jsonText);
        }

        @Override
        public void titleClear() {
            clears.add("clear");
        }

        @Override
        public void sendChatComponent(String json, int position) {
            chats.add(position + ":" + json);
        }
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

    @Test
    void theTitlePairCarriesTheReferenceArmOrder() throws Exception {
        alice.sendTitle("Hello", "World", 10, 40, 10);
        await(() -> !link.titles.isEmpty() && !link.subtitles.isEmpty(), "the pair landed");
        // Times first, then the title, then the subtitle.
        assertEquals(1, link.times.size(), "the Times arm");
        assertEquals(10, link.times.get(0)[0]);
        assertEquals(40, link.times.get(0)[1]);
        assertEquals(10, link.times.get(0)[2]);
        // The 1.8.8 serializer's own quirk: a plain literal serializes as the
        // bare JSON string ("Hello"), not an object — TextJson's own law.
        assertEquals("\"Hello\"", link.titles.get(0));
        assertEquals("\"World\"", link.subtitles.get(0));
    }

    @Test
    void theBlankArmsSkipTheirOwnWire() throws Exception {
        // A blank subtitle sends no subtitle arm; all-zero counts send no Times.
        alice.sendTitle("Only the title", "", 0, 0, 0);
        await(() -> !link.titles.isEmpty(), "the title landed");
        assertEquals(1, link.titles.size(), "one title");
        assertTrue(link.subtitles.isEmpty(), "the blank subtitle skipped");
        assertTrue(link.times.isEmpty(), "the zero counts skipped");
        // The clear.
        alice.clearTitle();
        await(() -> !link.clears.isEmpty(), "the clear landed");
    }

    @Test
    void theChatFamilyCarriesTheThreePositions() throws Exception {
        alice.sendChatLine("box line");
        alice.sendSystemLine("system line");
        alice.sendChatComponent("{\"text\":\"raw\",\"color\":\"gold\"}");
        await(() -> link.chats.size() >= 3, "the lines landed");
        assertTrue(link.chats.get(0).startsWith("0:\"box line\""), "the box position (the bare-string quirk)");
        assertTrue(link.chats.get(1).startsWith("1:\"system line\""), "the system position (the bare-string quirk)");
        assertEquals("0:{\"text\":\"raw\",\"color\":\"gold\"}", link.chats.get(2), "the raw component");
    }
}
