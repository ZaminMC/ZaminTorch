package net.zaminmc.torch.server.command;

import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.player.PlayerSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The /title and /tellraw commands (TitleCommand/TellRawCommand 1:1) over
 * the live engine: the five title arms through the recorded 0x45 verbs, the
 * JSON parse's refusals, the selector-target and in-component resolutions,
 * and the tellraw raw-JSON chat delivery.
 */
class TitleTellRawCommandTest {

    @TempDir
    Path dataDir;

    EngineServer server;
    RecordingLink aliceLink;
    PlayerSession alice;
    PlayerSession bob;

    /** The recording link: every title/chat verb lands as a string. */
    private static final class RecordingLink implements net.zaminmc.torch.server.net.ClientLink {
        final List<String> verbs = new CopyOnWriteArrayList<>();

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public void kick(String reason) {
        }

        @Override
        public void titleTitle(String jsonText) {
            verbs.add("title:" + jsonText);
        }

        @Override
        public void titleSubtitle(String jsonText) {
            verbs.add("subtitle:" + jsonText);
        }

        @Override
        public void titleTimes(int fadeIn, int duration, int fadeOut) {
            verbs.add("times:" + fadeIn + "/" + duration + "/" + fadeOut);
        }

        @Override
        public void titleClear() {
            verbs.add("clear");
        }

        @Override
        public void titleReset() {
            verbs.add("reset");
        }

        @Override
        public void sendChatComponent(String json, int position) {
            verbs.add("chat:" + position + ":" + json);
        }
    }

    @BeforeEach
    void boot() throws Exception {
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "title" + Math.abs(UUID.randomUUID().hashCode() % 100000),
                "it", 20, 2, 20, dataDir.toString(),
                net.zaminmc.torch.GameMode.SURVIVAL);
        server = new EngineServer(config);
        server.start();
        aliceLink = new RecordingLink();
        alice = join(aliceLink, "Alice");
        bob = join(new RecordingLink(), "Bob");
        await(() -> alice.state() == net.zaminmc.torch.entity.PlayerState.PLAYING
                        && bob.state() == net.zaminmc.torch.entity.PlayerState.PLAYING,
                "both sessions playing");
        server.ticker().submit(() -> alice.setOpLevel(2));
        await(() -> alice.opLevel() >= 2, "Alice opped");
    }

    @AfterEach
    void stop() {
        server.shutdown(null);
    }

    private PlayerSession join(RecordingLink link, String name) {
        var accepted = (net.zaminmc.torch.server.net.EngineBridge.Accepted)
                server.joinRequest(link, name,
                        UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes()));
        PlayerSession session = accepted.session();
        server.ticker().submit(session::markPlaying);
        return session;
    }

    private void dispatch(String line) {
        server.chatService().submitChat(alice, line);
    }

    @Test
    void theFiveTitleArms() throws Exception {
        dispatch("/title Alice times 10 20 30");
        dispatch("/title Alice title {\"text\":\"Hello\",\"color\":\"red\"}");
        dispatch("/title Alice subtitle [\"a\",{\"text\":\"b\",\"bold\":true}]");
        dispatch("/title Alice clear");
        dispatch("/title Alice reset");
        await(() -> aliceLink.verbs.size() >= 5, "all five verbs recorded");
        assertEquals("times:10/20/30", aliceLink.verbs.get(0));
        // The styled component re-serializes in the reference's field order.
        assertEquals("title:{\"color\":\"red\",\"text\":\"Hello\"}", aliceLink.verbs.get(1));
        // The array form folds to head+extra; the sibling keeps its own style.
        assertEquals("subtitle:{\"extra\":[{\"bold\":true,\"text\":\"b\"}],\"text\":\"a\"}",
                aliceLink.verbs.get(2));
        assertEquals("clear", aliceLink.verbs.get(3));
        assertEquals("reset", aliceLink.verbs.get(4));
    }

    @Test
    void theSelectorResolutions() throws Exception {
        // The target itself can be a selector: @p lands on the sender.
        dispatch("/title @p title {\"text\":\"ViaSelector\"}");
        // An in-component selector resolves against the source: @a joins the
        // two display names by the listText walk.
        dispatch("/title Alice title {\"selector\":\"@a\"}");
        // An in-component score owner through the selector: exactly one.
        dispatch("/title Alice title {\"score\":{\"name\":\"@p\",\"objective\":\"obj\"}}");
        await(() -> aliceLink.verbs.size() >= 3, "the three payloads recorded");
        assertEquals("title:\"ViaSelector\"", aliceLink.verbs.get(0));
        String join = aliceLink.verbs.get(1);
        assertTrue(join.startsWith("title:"), join);
        assertTrue(join.contains("Alice") && join.contains("Bob"),
                "the selector component resolved both names: " + join);
        // The @p score owner resolved to the nearest player's name.
        assertTrue(aliceLink.verbs.get(2).contains("\"name\":\"Alice\""),
                aliceLink.verbs.get(2));
    }

    @Test
    void theRefusals() throws Exception {
        dispatch("/title Alice times 10 x 30");
        dispatch("/title Alice title not-json");
        dispatch("/title Nobody title {\"text\":\"Hi\"}");
        dispatch("/title Alice bogus extra args");
        // Let the chat queue drain, then assert nothing fired.
        Thread.sleep(500);
        assertTrue(aliceLink.verbs.isEmpty(), "no verb fired: " + aliceLink.verbs);
    }

    @Test
    void theTellRawDelivery() throws Exception {
        dispatch("/tellraw Alice {\"text\":\"Hi\",\"color\":\"red\",\"hoverEvent\":{\"action\":\"show_text\",\"value\":{\"text\":\"hovered\"}}}");
        dispatch("/tellraw Alice {\"selector\":\"@p\"}");
        await(() -> aliceLink.verbs.size() >= 2, "the tellraw lines recorded");
        assertEquals("chat:1:{\"color\":\"red\",\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"hovered\"},\"text\":\"Hi\"}",
                aliceLink.verbs.get(0));
        // The in-component @p resolved to the nearest player's name (the
        // listText head + the name sibling).
        assertEquals("chat:1:{\"extra\":[\"Alice\"],\"text\":\"\"}", aliceLink.verbs.get(1));
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
