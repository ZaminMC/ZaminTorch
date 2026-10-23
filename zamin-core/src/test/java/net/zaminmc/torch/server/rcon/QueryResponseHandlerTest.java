package net.zaminmc.torch.server.rcon;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The GS4 query wire laws over a real UDP responder on an ephemeral port
 * (the established real-socket test posture): the handshake token law, the
 * basic and full stats byte shapes byte-for-byte, the challenge validation
 * (the big-endian echo at offset 7), the magic prefix law, the 5-second
 * cached rules reply, and the start() refusal law with idempotent stop.
 */
class QueryResponseHandlerTest {

    private QueryResponseHandler handler;
    private RconTestHarness.FakeAccess access;

    private void startHandler(int port) {
        access = new RconTestHarness.FakeAccess("unused", port);
        handler = new QueryResponseHandler(access);
        handler.start();
        // The liveness law (run #375's race): the flag alone cannot prove
        // the loop took its seat — the pre-fix order could start the thread
        // before the flag landed, the loop read false, and the finally close
        // the socket while isRunning() still answered true (the handshake
        // then times out with a confusing line-101 symptom). The thread
        // being alive IS the seat; a dead thread at boot now fails HERE,
        // loudly, naming the shape.
        assertTrue(handler.isRunning() && handler.thread.isAlive(),
                "the handler took its seat: the loop flag is set AND the loop thread"
                        + " is live (start()'s flag-before-thread order, run #375)");
    }

    @AfterEach
    void stopHandler() {
        if (handler != null) {
            handler.stop();
        }
    }

    private static int ephemeralUdpPort() throws Exception {
        try (DatagramSocket probe = new DatagramSocket(
                new java.net.InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))) {
            return probe.getLocalPort();
        }
    }

    // -------------------------------------------------------------- the client-side GS4 shapes

    /** The GS4 request magic + type + session (the 7-byte handshake request). */
    private static byte[] handshakeRequest(byte[] session) {
        return new byte[]{(byte) 0xFE, (byte) 0xFD, 0x09,
                session[0], session[1], session[2], session[3]};
    }

    /** The 11-byte basic-stats request, or the 15-byte full-stats request. */
    private static byte[] statsRequest(byte[] session, int challenge, boolean full)
            throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0xFE);
        out.write(0xFD);
        out.write(0);
        out.write(session);
        out.write(challenge >>> 24); // the big-endian echo the validation reads
        out.write(challenge >>> 16 & 0xFF);
        out.write(challenge >>> 8 & 0xFF);
        out.write(challenge & 0xFF);
        if (full) {
            out.write(0);
            out.write(0);
            out.write(0);
            out.write(0);
        }
        return out.toByteArray();
    }

    /** One UDP round trip; a null return means "no reply within the window". */
    private byte[] roundTrip(DatagramSocket client, byte[] request) throws Exception {
        client.send(new DatagramPacket(request, request.length,
                InetAddress.getByName("127.0.0.1"), access.queryPort));
        byte[] buf = new byte[2048];
        DatagramPacket reply = new DatagramPacket(buf, buf.length);
        try {
            client.receive(reply);
        } catch (SocketTimeoutException none) {
            return null;
        }
        return Arrays.copyOf(reply.getData(), reply.getLength());
    }

    /**
     * Performs the handshake and answers the challenge token (parsed from
     * the reply's decimal-ASCII arm — the vanilla quirk, reference Query
     * lines 296-297).
     */
    private int handshake(DatagramSocket client, byte[] session) throws Exception {
        byte[] reply = roundTrip(client, handshakeRequest(session));
        assertTrue(reply != null, "the handshake always replies");
        assertEquals(0x09, reply[0] & 0xFF, "the reply type byte is the tab");
        assertEquals(session[0], reply[1], "the session echo, byte 1");
        assertEquals(session[1], reply[2], "the session echo, byte 2");
        assertEquals(session[2], reply[3], "the session echo, byte 3");
        assertEquals(session[3], reply[4], "the session echo, byte 4");
        assertEquals(0, reply[reply.length - 1], "the reply's NUL terminator");
        String decimal = new String(reply, 5, reply.length - 6, StandardCharsets.US_ASCII);
        int challenge = Integer.parseInt(decimal);
        assertTrue(challenge >= 0 && challenge < 16777216,
                "the 24-bit token band (nextInt(16777216))");
        return challenge;
    }

    private static DatagramSocket client() throws Exception {
        DatagramSocket client = new DatagramSocket();
        client.setSoTimeout(2_000);
        return client;
    }

    // -------------------------------------------------------------- the laws

    @Test
    void theHandshakeTokenLaw() throws Exception {
        startHandler(ephemeralUdpPort());
        try (DatagramSocket client = client()) {
            handshake(client, new byte[]{0x01, 0x02, 0x03, 0x04});
        }
    }

    @Test
    void theBasicStatsByteShape() throws Exception {
        startHandler(ephemeralUdpPort());
        access.playerCount = 3;
        try (DatagramSocket client = client()) {
            byte[] session = {0x0A, 0x0B, 0x0C, 0x0D};
            int challenge = handshake(client, session);
            byte[] reply = roundTrip(client, statsRequest(session, challenge, false));
            assertTrue(reply != null, "a valid challenge answers the basic stats");
            // The reference lines 100-113: type, session, motd\0, SMP\0,
            // world\0, online\0, max\0, the little-endian port, hostIp\0.
            ByteArrayOutputStream expected = new ByteArrayOutputStream();
            expected.write(0);
            expected.write(session);
            expected.write("A Test Server".getBytes(StandardCharsets.UTF_8));
            expected.write(0);
            expected.write("SMP".getBytes(StandardCharsets.US_ASCII));
            expected.write(0);
            expected.write("world".getBytes(StandardCharsets.US_ASCII));
            expected.write(0);
            expected.write("3".getBytes(StandardCharsets.US_ASCII));
            expected.write(0);
            expected.write("20".getBytes(StandardCharsets.US_ASCII));
            expected.write(0);
            expected.write(25599 & 0xFF); // writeShort's little-endian
            expected.write(25599 >>> 8 & 0xFF);
            expected.write("127.0.0.1".getBytes(StandardCharsets.US_ASCII));
            expected.write(0);
            assertArrayEquals(expected.toByteArray(), reply,
                    "the whole basic-stats reply byte-for-byte");
        }
    }

    @Test
    void theFullStatsByteShape() throws Exception {
        startHandler(ephemeralUdpPort());
        access.playerCount = 2;
        access.playerNames = new String[]{"Alice", "Bob"};
        try (DatagramSocket client = client()) {
            byte[] session = {0x41, 0x42, 0x43, 0x44};
            int challenge = handshake(client, session);
            byte[] reply = roundTrip(client, statsRequest(session, challenge, true));
            assertTrue(reply != null, "the exact 15-byte request answers the full stats");
            // The reference createRulesReply lines 127-178: the KV block then
            // the player section.
            ByteArrayOutputStream expected = new ByteArrayOutputStream();
            expected.write(0);
            expected.write(session);
            expected.write("splitnum".getBytes(StandardCharsets.US_ASCII));
            expected.write(0);
            expected.write(128);
            expected.write(0);
            String[][] pairs = {
                    {"hostname", "A Test Server"},
                    {"gametype", "SMP"},
                    {"game_id", "MINECRAFT"},
                    {"version", "1.8.8"},
                    {"plugins", ""},
                    {"map", "world"},
                    {"numplayers", "2"},
                    {"maxplayers", "20"},
                    {"hostport", "25599"},
                    {"hostip", "127.0.0.1"},
            };
            for (String[] pair : pairs) {
                expected.write(pair[0].getBytes(StandardCharsets.US_ASCII));
                expected.write(0);
                expected.write(pair[1].getBytes(StandardCharsets.UTF_8));
                expected.write(0);
            }
            expected.write(0);
            expected.write(1);
            expected.write("player_".getBytes(StandardCharsets.US_ASCII));
            expected.write(0);
            expected.write(0);
            expected.write("Alice".getBytes(StandardCharsets.US_ASCII));
            expected.write(0);
            expected.write("Bob".getBytes(StandardCharsets.US_ASCII));
            expected.write(0);
            expected.write(0);
            assertArrayEquals(expected.toByteArray(), reply,
                    "the whole full-stats reply byte-for-byte");
        }
    }

    @Test
    void theWrongChallengeIsIgnored() throws Exception {
        startHandler(ephemeralUdpPort());
        try (DatagramSocket client = client()) {
            byte[] session = {1, 2, 3, 4};
            int challenge = handshake(client, session);
            // The offset-7 big-endian echo must equal the token: a wrong one
            // answers nothing (isValidQuery, reference lines 184-192).
            byte[] wrong = roundTrip(client, statsRequest(session, challenge + 1, false));
            assertTrue(wrong == null, "the wrong challenge never gets a reply");
        }
    }

    @Test
    void theMissingHandshakeIsIgnored() throws Exception {
        // No handshake for this address: the challenge map has no seat, so
        // even a guessed-correct token answers nothing.
        startHandler(ephemeralUdpPort());
        try (DatagramSocket client = client()) {
            byte[] session = {9, 9, 9, 9};
            byte[] none = roundTrip(client, statsRequest(session, 0, false));
            assertTrue(none == null, "an address that never handshaked never gets stats");
        }
    }

    @Test
    void theMagicPrefixLaw() throws Exception {
        startHandler(ephemeralUdpPort());
        try (DatagramSocket client = client()) {
            byte[] notGs4 = roundTrip(client, new byte[]{1, 2, 3, 4, 5});
            assertTrue(notGs4 == null, "a packet without the FE FD magic never replies");
            // The type switch has only 0 and 9 (reference lines 92-119): the
            // type-8 packet falls through the default arm without a reply.
            byte[] wrongType = roundTrip(client,
                    new byte[]{(byte) 0xFE, (byte) 0xFD, 8, 1, 2, 3, 4});
            assertTrue(wrongType == null, "an unknown type byte never replies");
        }
    }

    /**
     * The 5-second cached rules reply (createRulesReply lines 128-137): the
     * second full-stats request inside the window serves the CACHED buffer —
     * only the session bytes are patched; the census fields stay at their
     * first-answer values even though the live reads changed.
     */
    @Test
    void theRulesReplyServesTheFiveSecondCache() throws Exception {
        startHandler(ephemeralUdpPort());
        access.playerCount = 0;
        try (DatagramSocket client = client()) {
            byte[] sessionA = {1, 1, 1, 1};
            int challenge = handshake(client, sessionA);
            byte[] first = roundTrip(client, statsRequest(sessionA, challenge, true));
            assertTrue(first != null);

            // The census moves between the two answers.
            access.playerCount = 7;

            byte[] sessionB = {2, 2, 2, 2};
            // The challenge is bound to the ADDRESS, and both requests ride
            // this one client socket: the map seat (keyed by address) was
            // replaced by a fresh handshake with the same token band.
            int challengeB = handshake(client, sessionB);
            byte[] second = roundTrip(client, statsRequest(sessionB, challengeB, true));
            assertTrue(second != null);
            byte[] patched = first.clone();
            patched[1] = sessionB[0];
            patched[2] = sessionB[1];
            patched[3] = sessionB[2];
            patched[4] = sessionB[3];
            assertArrayEquals(patched, second,
                    "the cached arm patches only the session bytes — numplayers stays stale");
        }
    }

    // -------------------------------------------------------------- start()/stop

    /**
     * The vanilla charset quirk, pinned: the handshake reply round-trips the
     * session through {@code new String(bytes)} + {@code getBytes()} (Query
     * lines 295-297, the platform charset — UTF-8 on the engine's JDK 21,
     * JEP 400): a session byte above 0x7F decodes to the U+FFFD replacement
     * (0xEF 0xBF 0xBD) while the ASCII bytes survive. The STATS replies echo
     * the RAW session (messageBytes is stored verbatim, line 291-294) — only
     * the handshake token reply carries the mangling. Both are the
     * reference's own shape, not an engine defect.
     */
    @Test
    void theHighSessionByteRidesThePlatformCharsetInTheHandshakeReply() throws Exception {
        startHandler(ephemeralUdpPort());
        try (DatagramSocket client = client()) {
            byte[] session = {(byte) 0x81, 0x02, 0x03, 0x04};
            byte[] reply = roundTrip(client, handshakeRequest(session));
            assertTrue(reply != null);
            assertEquals(0x09, reply[0] & 0xFF);
            assertEquals((byte) 0xEF, reply[1], "the U+FFFD lead byte (JEP 400 UTF-8)");
            assertEquals((byte) 0xBF, reply[2]);
            assertEquals((byte) 0xBD, reply[3]);
            assertEquals(0x02, reply[4], "the tail session bytes survive");
            // And the same session still rides the challenge law: the stats
            // request validates against the raw BE int and answers with the
            // RAW session echo.
            int challenge = parseChallenge(reply);
            byte[] stats = roundTrip(client, statsRequest(session, challenge, false));
            assertTrue(stats != null, "the mangled handshake still armed the challenge");
            assertEquals(0, stats[0]);
            assertEquals((byte) 0x81, stats[1], "the stats reply echoes the RAW session");
            assertEquals(0x02, stats[2]);
            assertEquals(0x03, stats[3]);
            assertEquals(0x04, stats[4]);
        }
    }

    /**
     * Parses the challenge token out of a handshake reply: the trailing run
     * of ASCII digits before the NUL (the session echo may MANGLE through
     * the charset quirk below — the digits are always the tail).
     */
    private static int parseChallenge(byte[] reply) {
        int end = reply.length - 1;
        while (reply[end] != 0) {
            end--;
        }
        int start = end;
        while (start > 0 && reply[start - 1] >= '0' && reply[start - 1] <= '9') {
            start--;
        }
        return Integer.parseInt(new String(reply, start, end - start, StandardCharsets.US_ASCII));
    }

    @Test
    void theInvalidPortRefusalLawNeverBinds() throws Exception {
        // Inline construction — the refusal arm never starts a thread, so
        // the happy-path startHandler (with its liveness law) must not ride.
        access = new RconTestHarness.FakeAccess("unused", 70000);
        handler = new QueryResponseHandler(access);
        handler.start();
        assertFalse(handler.isRunning(),
                "QueryResponseHandler.start lines 244-249: a bad port disables");
        // The refused listener never took a UDP seat: the operator's own bind
        // succeeds (a client cannot even ADDRESS port 70000, so the proof is
        // the bind itself).
        try (DatagramSocket owner = new DatagramSocket(
                new java.net.InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))) {
            assertTrue(owner.isBound(), "the port stayed free for the next caller");
        }
    }

    @Test
    void theStopIsIdempotent() throws Exception {
        startHandler(ephemeralUdpPort());
        try (DatagramSocket client = client()) {
            handshake(client, new byte[]{1, 2, 3, 4});
            handler.stop();
            handler.stop(); // the idempotency law
            assertFalse(handler.isRunning());
            byte[] none = roundTrip(client, handshakeRequest(new byte[]{5, 6, 7, 8}));
            assertTrue(none == null, "the stopped responder never replies");
        }
    }
}
