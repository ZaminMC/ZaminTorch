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
 * The public inbound packet hooks (§27.3's interception surface, the C2S
 * mirror over the live wire): {@code Server.protocolHooks()}'s inbound
 * family — observe and drop the PLAY-state frames a session sends,
 * BEFORE the engine decodes them. Verified against a real client
 * connection; no engine internals anywhere.
 */
class PacketHookInboundApiTest extends ProtocolTestBase {

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

    /** The C2S chat packet: the observable, hand-buildable frame of this suite. */
    private static final int CHAT = Protocol18.C2S_CHAT_MESSAGE;

    private net.zaminmc.torch.protocol.InboundPacketListener cleaner;

    @AfterEach
    void unregister() {
        if (cleaner != null) {
            server.protocolHooks().removeInboundListener(cleaner);
            cleaner = null;
        }
    }

    @Test
    void theInboundListenerObservesTheChatFrameBeforeTheEngineDecodesIt() throws Exception {
        try (TestClient18 sender = new TestClient18("127.0.0.1", adapter.boundPort())) {
            sender.sendHandshake(47, 2);
            sender.sendLoginStart("Sender");
            sender.readLoginSuccess();
            sender.readUntilPositionAndLook();
            awaitPlayers("Sender");

            CountDownLatch sawChat = new CountDownLatch(1);
            AtomicReference<String> senderName = new AtomicReference<>();
            AtomicReference<Integer> seenId = new AtomicReference<>();
            AtomicReference<byte[]> seenPayload = new AtomicReference<>();
            AtomicInteger sawFrames = new AtomicInteger();
            net.zaminmc.torch.protocol.InboundPacketListener listener = packet -> {
                sawFrames.incrementAndGet();
                if (packet.packetId() == CHAT) {
                    senderName.set(packet.sender().name());
                    seenId.set(packet.packetId());
                    seenPayload.set(packet.payload());
                    sawChat.countDown();
                }
            };
            assertTrue(server.protocolHooks().addInboundListener(listener),
                    "the inbound listener registered");
            cleaner = listener;

            sender.sendChat("hook my words");
            assertTrue(sawChat.await(5, TimeUnit.SECONDS),
                    "the inbound listener saw the chat frame");
            assertEquals("Sender", senderName.get(), "the sender is the session");
            assertEquals(CHAT, seenId.get(), "the id is the wire's own");
            // The payload copy carries the message (the varint string the
            // engine's own decode would read — the inspection is pre-decode).
            String payloadText = new String(seenPayload.get(), java.nio.charset.StandardCharsets.UTF_8);
            int textAt = payloadText.indexOf("hook my words");
            assertTrue(textAt >= 0, "the payload copy carries the sent text");
            assertTrue(sawFrames.get() >= 1, "the walk saw frames");
            assertTrue(server.protocolHooks().removeInboundListener(listener),
                    "the listener left");
            assertFalse(server.protocolHooks().inboundListeners().contains(listener),
                    "the registry no longer holds it");
        }
        awaitEmptyServer();
    }

    @Test
    void theDroppedInboundFrameNeverReachesTheEngine() throws Exception {
        try (TestClient18 sender = new TestClient18("127.0.0.1", adapter.boundPort());
             TestClient18 watcher = new TestClient18("127.0.0.1", adapter.boundPort())) {
            sender.sendHandshake(47, 2);
            sender.sendLoginStart("Dropper");
            sender.readLoginSuccess();
            sender.readUntilPositionAndLook();
            watcher.sendHandshake(47, 2);
            watcher.sendLoginStart("Watcher");
            watcher.readLoginSuccess();
            watcher.readUntilPositionAndLook();
            awaitPlayers("Dropper", "Watcher");

            // The dropping listener: every inbound chat frame from the
            // sender is dropped BEFORE the engine decodes it — the chat
            // never broadcasts.
            net.zaminmc.torch.protocol.InboundPacketListener dropper = packet -> {
                if (packet.packetId() == CHAT
                        && packet.sender().name().equals("Dropper")) {
                    packet.cancel();
                }
            };
            assertTrue(server.protocolHooks().addInboundListener(dropper));
            cleaner = dropper;

            sender.sendChat("this line never decodes");
            // The watcher sees NO chat line inside the window (every read
            // times out on the silent stream — the time updates keep it
            // moving).
            long deadline = System.currentTimeMillis() + 1_500;
            boolean anyChat = false;
            while (System.currentTimeMillis() < deadline) {
                try {
                    String line = watcher.readChatLine(200);
                    if (line.contains("this line never decodes")) {
                        anyChat = true;
                        break;
                    }
                } catch (IOException silentStream) {
                    // the read's own timeout while the stream stays silent
                }
            }
            assertFalse(anyChat, "the dropped chat never reached the engine");

            // The uncancelled twin: after the listener leaves, the same
            // sender's chat broadcasts again.
            server.protocolHooks().removeInboundListener(dropper);
            cleaner = null;
            sender.sendChat("this line lands");
            String line = sender.readChatLineSkippingBroadcasts(5_000);
            assertTrue(line.contains("this line lands"),
                    "the chat broadcasts once the drop is gone (saw: " + line + ")");
        }
        awaitEmptyServer();
    }
}
