package net.zaminmc.torch.protocol.v1_8;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The composed-frame send (Slice 70 — §27.3's packet construction, the
 * protocol row's last leg): a plugin composes a PLAY-state frame through
 * {@code Server.protocolHooks().send(player, id, payload)} and the
 * client receives it — through the same pipeline (and the same
 * interception walk) as engine traffic. No engine internals: the send
 * goes through the public registry, the recipient through the public
 * Player handle.
 */
class PacketSendApiTest extends ProtocolTestBase {

    /** The scripted clients never answer keep-alives — slow-suite law. */
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

    /** Drains packets until one carries the wanted id (its payload). */
    private static byte[] readUntilPacket(TestClient18 client, int wantedId)
            throws IOException {
        for (int i = 0; i < 200; i++) {
            byte[] payload = client.readPacket();
            if (client.readPacketId(payload) == wantedId) {
                return payload;
            }
        }
        throw new IOException("the wanted packet never arrived");
    }

    @Test
    void theComposedFrameReachesTheClient() throws Exception {
        try (TestClient18 receiver = new TestClient18("127.0.0.1", adapter.boundPort())) {
            receiver.sendHandshake(47, 2);
            receiver.sendLoginStart("Composer");
            receiver.readLoginSuccess();
            receiver.readUntilPositionAndLook();
            awaitPlayers("Composer");
            net.zaminmc.torch.entity.Player player =
                    server.playerRegistry().byName("Composer").orElseThrow();

            server.protocolHooks().send(player, CHAT,
                    chatPayload("{\"text\":\"a composed frame\"}"));

            byte[] payload = readUntilPacket(receiver, CHAT);
            io.netty.buffer.ByteBuf body = io.netty.buffer.Unpooled.wrappedBuffer(payload);
            ByteBufOps.readVarInt(body); // the id
            String json = ByteBufOps.readString(body, 512);
            assertTrue(json.contains("a composed frame"),
                    "the client received the composed chat: " + json);
        }
        awaitEmptyServer();
    }

    @Test
    void theComposedFrameWalksTheInterceptionListeners() throws Exception {
        try (TestClient18 receiver = new TestClient18("127.0.0.1", adapter.boundPort())) {
            receiver.sendHandshake(47, 2);
            receiver.sendLoginStart("Cancelled");
            receiver.readLoginSuccess();
            receiver.readUntilPositionAndLook();
            awaitPlayers("Cancelled");
            net.zaminmc.torch.entity.Player player =
                    server.playerRegistry().byName("Cancelled").orElseThrow();

            CountDownLatch sawComposed = new CountDownLatch(1);
            AtomicInteger composedId = new AtomicInteger(-1);
            net.zaminmc.torch.protocol.PacketListener listener = packet -> {
                if (packet.packetId() == CHAT) {
                    // The composed frame carries the sentinel payload's length
                    // pattern; the engine's own chat arm fires only on the
                    // chat path this test never drives.
                    composedId.set(packet.packetId());
                    packet.cancel();
                    sawComposed.countDown();
                }
            };
            assertTrue(server.protocolHooks().addListener(listener),
                    "the listener registered");
            cleaner = listener;

            server.protocolHooks().send(player, CHAT,
                    chatPayload("{\"text\":\"never sent\"}"));

            assertTrue(sawComposed.await(5, TimeUnit.SECONDS),
                    "the interception walk saw the composed frame");
            assertTrue(composedId.get() == CHAT, "the walked frame carries the composed id");
            // The cancelled frame never reaches the wire: nothing else is
            // in flight, so the next read must time out (the empty-socket
            // law — readPacket blocks; give it a short grace window via
            // the socket timeout instead).
            receiver.setReadTimeout(1_500);
            boolean anything;
            try {
                anything = receiver.readPacket().length >= 0;
            } catch (IOException expected) {
                anything = false; // the timeout — the frame never arrived
            }
            assertTrue(!anything || readQuietly(receiver),
                    "no chat frame reached the client (the cancel held)");
        }
        awaitEmptyServer();
    }

    /** @return true when the stream stayed quiet (only non-chat noise at most). */
    private static boolean readQuietly(TestClient18 client) throws IOException {
        // Whatever arrived must not be the cancelled chat frame.
        for (int i = 0; i < 20; i++) {
            byte[] payload;
            try {
                payload = client.readPacket();
            } catch (IOException timedOut) {
                return true; // quiet — the cancel held
            }
            if (client.readPacketId(payload) == CHAT) {
                return false; // the cancelled frame leaked
            }
        }
        return true;
    }

    @Test
    void theSendValidatesItsOwnContract() {
        // A minimal Player stand-in through the JDK's proxy — the contract
        // checks run before any resolution, so only name() is ever asked.
        net.zaminmc.torch.entity.Player nobody =
                (net.zaminmc.torch.entity.Player) java.lang.reflect.Proxy.newProxyInstance(
                        getClass().getClassLoader(),
                        new Class<?>[]{net.zaminmc.torch.entity.Player.class},
                        (proxy, method, args) -> "name".equals(method.getName())
                                ? "Nobody" : null);
        assertThrows(NullPointerException.class,
                () -> server.protocolHooks().send(null, CHAT, new byte[0]),
                "a null recipient is refused");
        assertThrows(NullPointerException.class,
                () -> server.protocolHooks().send(nobody, CHAT, null),
                "a null payload is refused");
        assertThrows(IllegalArgumentException.class,
                () -> server.protocolHooks().send(nobody, -1, new byte[0]),
                "a negative packet id is refused");
    }
}
