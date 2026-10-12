package net.zaminmc.torch.protocol.v1_8;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.io.IOException;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The public packet hooks (Slice 15c — §27.3's interception surface over
 * the live wire): {@code Server.protocolHooks()} — observe, cancel, and
 * re-payload outbound PLAY-state frames, verified against a real client
 * connection. No engine internals anywhere: a plugin-style listener
 * registered through the public registry.
 */
class PacketHookApiTest extends ProtocolTestBase {

    /**
     * The scripted clients never answer keep-alives, and this suite's
     * settle-and-window reads run seconds — the shared default (250 ms × 3
     * factor) would kick the connections mid-test (the base's own law for
     * slow suites).
     */
    @Override
    protected long keepAliveIntervalMs() {
        return 10_000;
    }

    /** The chat packet: the observable, hand-buildable frame of this suite. */
    private static final int CHAT = Protocol18.S2C_CHAT;

    private net.zaminmc.torch.protocol.PacketListener cleaner;

    @AfterEach
    void unregister() {
        if (cleaner != null) {
            server.protocolHooks().removeListener(cleaner);
            cleaner = null;
        }
    }

    /** Builds a 1.8.8 chat payload after the id: varint-length JSON + position byte. */
    private static byte[] chatPayload(String json) {
        io.netty.buffer.ByteBuf body = io.netty.buffer.Unpooled.buffer(16 + json.length());
        ByteBufOps.writeString(body, json);
        body.writeByte(0);
        byte[] out = new byte[body.readableBytes()];
        body.readBytes(out);
        body.release();
        return out;
    }

    @Test
    void theListenerObservesTheOutboundChatFrame() throws Exception {
        try (TestClient18 sender = new TestClient18("127.0.0.1", adapter.boundPort())) {
            sender.sendHandshake(47, 2);
            sender.sendLoginStart("Hooker");
            sender.readLoginSuccess();
            sender.readUntilPositionAndLook();
            awaitPlayers("Hooker");

            CountDownLatch sawChat = new CountDownLatch(1);
            AtomicReference<String> recipientName = new AtomicReference<>();
            AtomicReference<Integer> seenId = new AtomicReference<>();
            AtomicInteger sawFrames = new AtomicInteger();
            net.zaminmc.torch.protocol.PacketListener listener = packet -> {
                sawFrames.incrementAndGet();
                if (packet.packetId() == CHAT) {
                    recipientName.set(packet.recipient().name());
                    seenId.set(packet.packetId());
                    sawChat.countDown();
                }
            };
            assertTrue(server.protocolHooks().addListener(listener),
                    "the listener registered");
            cleaner = listener;

            sender.sendChat("hook me");
            assertTrue(sawChat.await(5, TimeUnit.SECONDS), "the listener saw the chat frame");
            assertEquals("Hooker", recipientName.get(), "the recipient is the session");
            assertEquals(CHAT, seenId.get(), "the id is the wire's own");
            assertTrue(sawFrames.get() >= 1, "the walk saw frames");
            assertTrue(server.protocolHooks().removeListener(listener), "the listener left");
            assertFalse(server.protocolHooks().listeners().contains(listener),
                    "the registry no longer holds it");
        }
        awaitEmptyServer();
    }

    @Test
    void theCancelledFrameNeverReachesTheWire() throws Exception {
        try (TestClient18 sender = new TestClient18("127.0.0.1", adapter.boundPort());
             TestClient18 watcher = new TestClient18("127.0.0.1", adapter.boundPort())) {
            sender.sendHandshake(47, 2);
            sender.sendLoginStart("Canceller");
            sender.readLoginSuccess();
            sender.readUntilPositionAndLook();
            watcher.sendHandshake(47, 2);
            watcher.sendLoginStart("Watcher");
            watcher.readLoginSuccess();
            watcher.readUntilPositionAndLook();
            awaitPlayers("Canceller", "Watcher");

            net.zaminmc.torch.protocol.PacketListener canceller = packet -> {
                if (packet.packetId() == CHAT) {
                    packet.cancel();
                }
            };
            assertTrue(server.protocolHooks().addListener(canceller));
            cleaner = canceller;

            sender.sendChat("this line never lands");
            // The chat frame is cancelled for every recipient: the watcher
            // sees NO chat line inside the window (every read times out on
            // the silent stream — the time updates keep it moving).
            long deadline = System.currentTimeMillis() + 1_500;
            boolean anyChat = false;
            while (System.currentTimeMillis() < deadline) {
                try {
                    String line = watcher.readChatLine(200);
                    if (line.contains("this line never lands")) {
                        anyChat = true;
                        break;
                    }
                } catch (IOException silentStream) {
                    // the read's own timeout while the stream stays silent
                }
            }
            assertFalse(anyChat, "the cancelled chat never reached the wire");
        }
        awaitEmptyServer();
    }

    @Test
    void theReplacedPayloadRidesTheWire() throws Exception {
        try (TestClient18 sender = new TestClient18("127.0.0.1", adapter.boundPort())) {
            sender.sendHandshake(47, 2);
            sender.sendLoginStart("Replacer");
            sender.readLoginSuccess();
            sender.readUntilPositionAndLook();
            awaitPlayers("Replacer");

            net.zaminmc.torch.protocol.PacketListener replacer = packet -> {
                if (packet.packetId() == CHAT) {
                    byte[] original = packet.payload();
                    // The re-payload verb: the same frame, new bytes after the id.
                    packet.payload(chatPayload("{\"text\":\"rewritten by the hook\"}"));
                    if (original.length == 0) {
                        packet.cancel(); // a malformed edge never happens here
                    }
                }
            };
            assertTrue(server.protocolHooks().addListener(replacer));
            cleaner = replacer;

            sender.sendChat("original words");
            String line = sender.readChatLineSkippingBroadcasts(5_000);
            assertTrue(line.contains("rewritten by the hook"),
                    "the replacement rode the wire (saw: " + line + ")");
            assertFalse(line.contains("original words"),
                    "the original payload is gone");
        }
        awaitEmptyServer();
    }
}
