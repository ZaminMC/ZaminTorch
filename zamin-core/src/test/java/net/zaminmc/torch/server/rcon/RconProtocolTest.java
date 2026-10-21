package net.zaminmc.torch.server.rcon;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The RCON wire laws over a real listener on an ephemeral port (the
 * established real-socket test posture): the packet framing round trip, the
 * auth accept/reject id laws, the command route with the captured output,
 * the error arm, the 4096 split with its trailing-packet quirk, the length
 * and short-read disconnect laws, and the start() refusal laws
 * (RconServer.start lines 90-103) with idempotent stop.
 */
class RconProtocolTest {

    /** The reference's type constants (Source RCON; RconClient's switch values). */
    private static final int SERVERDATA_AUTH = 3;
    private static final int SERVERDATA_EXECCOMMAND = 2;

    private RconServer server;
    private RconTestHarness.FakeAccess access;

    private void startServer(String password, int port) {
        access = new RconTestHarness.FakeAccess(password, port);
        server = new RconServer(access);
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    private static void auth(Socket socket, int requestId, String password) throws Exception {
        RconTestHarness.send(socket,
                RconTestHarness.rconPacket(requestId, SERVERDATA_AUTH, password));
        RconTestHarness.readResponse(socket.getInputStream()); // the caller asserts the id law
    }

    @Test
    void theAuthAcceptEchoesTheRequestId() throws Exception {
        startServer("torch-secret", RconTestHarness.ephemeralTcpPort());
        try (Socket socket = RconTestHarness.connect(access.rconPort)) {
            RconTestHarness.send(socket,
                    RconTestHarness.rconPacket(7, SERVERDATA_AUTH, "torch-secret"));
            RconTestHarness.Response response =
                    RconTestHarness.readResponse(socket.getInputStream());
            assertEquals(7, response.id(), "auth success answers the request id (RconClient line 76)");
            assertEquals(2, response.type(), "the auth response type word (execute(l, 2, \"\"))");
            assertEquals("", response.body(), "the auth response body is empty");
            // The exact 14-byte frame (execute lines 99-110): length=10, id=7,
            // type=2, no body, the two trailing NULs.
            assertArrayEquals(new byte[]{
                    10, 0, 0, 0, 7, 0, 0, 0, 2, 0, 0, 0, 0, 0}, response.raw());
        }
    }

    @Test
    void theWrongPasswordAnswersMinusOneAndNeverExecutes() throws Exception {
        startServer("torch-secret", RconTestHarness.ephemeralTcpPort());
        try (Socket socket = RconTestHarness.connect(access.rconPort)) {
            RconTestHarness.send(socket,
                    RconTestHarness.rconPacket(9, SERVERDATA_AUTH, "not-the-secret"));
            RconTestHarness.Response rejected =
                    RconTestHarness.readResponse(socket.getInputStream());
            assertEquals(-1, rejected.id(), "auth failure answers -1 (executeUnknown, line 81)");
            assertEquals(2, rejected.type());
            assertEquals("", rejected.body());
            // An unauthenticated command answers the same refusal and never
            // reaches the console route (RconClient lines 57-70).
            RconTestHarness.send(socket,
                    RconTestHarness.rconPacket(11, SERVERDATA_EXECCOMMAND, "list"));
            RconTestHarness.Response refused =
                    RconTestHarness.readResponse(socket.getInputStream());
            assertEquals(-1, refused.id(), "the unauthenticated command refusal");
            assertEquals(2, refused.type());
            assertTrue(access.executedCommands.isEmpty(),
                    "no command may execute through a failed auth");
        }
    }

    @Test
    void theEmptyAuthPasswordIsRejectedLikeAWrongOne() throws Exception {
        // An EMPTY password in the request fails even though non-empty is the
        // only accepted shape (RconClient line 74: 0 != s.length()).
        startServer("torch-secret", RconTestHarness.ephemeralTcpPort());
        try (Socket socket = RconTestHarness.connect(access.rconPort)) {
            RconTestHarness.send(socket, RconTestHarness.rconPacket(4, SERVERDATA_AUTH, ""));
            RconTestHarness.Response rejected =
                    RconTestHarness.readResponse(socket.getInputStream());
            assertEquals(-1, rejected.id(), "the empty auth body is a failure");
            assertTrue(access.executedCommands.isEmpty());
        }
    }

    @Test
    void theExecCommandReturnsTheCapturedOutput() throws Exception {
        startServer("torch-secret", RconTestHarness.ephemeralTcpPort());
        access.cannedOutput = "There are 0 of a max of 20 players online: (none)";
        try (Socket socket = RconTestHarness.connect(access.rconPort)) {
            auth(socket, 3, "torch-secret");
            RconTestHarness.send(socket,
                    RconTestHarness.rconPacket(13, SERVERDATA_EXECCOMMAND, "list"));
            RconTestHarness.Response response =
                    RconTestHarness.readResponse(socket.getInputStream());
            assertEquals(13, response.id(), "the command response echoes the request id");
            assertEquals(0, response.type(), "command responses carry type 0 (execute(id, 0, ...))");
            assertEquals(access.cannedOutput, response.body());
            assertEquals(List.of("list"), access.executedCommands,
                    "the command reached the server route");
        }
    }

    @Test
    void theErrorArmAnswersTheReferenceSentence() throws Exception {
        startServer("torch-secret", RconTestHarness.ephemeralTcpPort());
        access.cannedFailure = "boom";
        try (Socket socket = RconTestHarness.connect(access.rconPort)) {
            auth(socket, 3, "torch-secret");
            RconTestHarness.send(socket,
                    RconTestHarness.rconPacket(5, SERVERDATA_EXECCOMMAND, "broken"));
            RconTestHarness.Response response =
                    RconTestHarness.readResponse(socket.getInputStream());
            assertEquals(5, response.id());
            assertEquals(0, response.type());
            assertEquals("Error executing: broken (boom)", response.body(),
                    "the reference's failure sentence (RconClient lines 63-65)");
        }
    }

    @Test
    void theUnknownRequestAnswersTheHexType() throws Exception {
        startServer("torch-secret", RconTestHarness.ephemeralTcpPort());
        try (Socket socket = RconTestHarness.connect(access.rconPort)) {
            RconTestHarness.send(socket, RconTestHarness.rconPacket(21, 99, "ignored"));
            RconTestHarness.Response response =
                    RconTestHarness.readResponse(socket.getInputStream());
            assertEquals(21, response.id());
            assertEquals(0, response.type());
            assertEquals("Unknown request 63", response.body(),
                    "the default arm's hex sentence (RconClient lines 83-84)");
        }
    }

    /**
     * The 4096-CHARACTER split (RconClient.execute lines 116-125 — the
     * reference splits on String length, not bytes) with the do-while's
     * trailing-packet law: at least one packet always, a 4096-char output
     * exactly one, an empty output one empty.
     */
    @Test
    void theLargeOutputSplitsAt4096Characters() throws Exception {
        startServer("torch-secret", RconTestHarness.ephemeralTcpPort());
        String alphabet = "abcdefghijklmnopqrstuvwxyz";
        StringBuilder fiveThousand = new StringBuilder();
        for (int i = 0; i < 5000; i++) {
            fiveThousand.append(alphabet.charAt(i % alphabet.length()));
        }
        access.cannedOutput = fiveThousand.toString();
        try (Socket socket = RconTestHarness.connect(access.rconPort)) {
            auth(socket, 3, "torch-secret");
            RconTestHarness.send(socket,
                    RconTestHarness.rconPacket(31, SERVERDATA_EXECCOMMAND, "big"));
            RconTestHarness.Response first =
                    RconTestHarness.readResponse(socket.getInputStream());
            RconTestHarness.Response second =
                    RconTestHarness.readResponse(socket.getInputStream());
            assertEquals(4096, first.body().length(), "the first chunk is the 4096-char cut");
            assertEquals(904, second.body().length(), "the remainder chunk");
            assertEquals(31, first.id());
            assertEquals(31, second.id(), "both chunks echo the request id");
            assertEquals(0, first.type());
            assertEquals(0, second.type());
            assertEquals(access.cannedOutput, first.body() + second.body(),
                    "the concatenation is the whole output");
        }
    }

    @Test
    void theExact4096OutputAnswersExactlyOnePacket() throws Exception {
        startServer("torch-secret", RconTestHarness.ephemeralTcpPort());
        access.cannedOutput = "x".repeat(4096);
        try (Socket socket = RconTestHarness.connect(access.rconPort)) {
            auth(socket, 3, "torch-secret");
            RconTestHarness.send(socket,
                    RconTestHarness.rconPacket(41, SERVERDATA_EXECCOMMAND, "exact"));
            RconTestHarness.Response response =
                    RconTestHarness.readResponse(socket.getInputStream());
            assertEquals(4096, response.body().length());
            socket.setSoTimeout(500);
            assertThrows(SocketTimeoutException.class,
                    () -> RconTestHarness.readResponse(socket.getInputStream()),
                    "the do-while exits after the full chunk — no trailing packet");
        }
    }

    @Test
    void theEmptyOutputAnswersOneEmptyPacket() throws Exception {
        // The broadcast quirk vanilla clients rely on: even an empty command
        // output answers exactly ONE empty packet (the do-while body runs
        // once before the length check).
        startServer("torch-secret", RconTestHarness.ephemeralTcpPort());
        access.cannedOutput = "";
        try (Socket socket = RconTestHarness.connect(access.rconPort)) {
            auth(socket, 3, "torch-secret");
            RconTestHarness.send(socket,
                    RconTestHarness.rconPacket(51, SERVERDATA_EXECCOMMAND, "silent"));
            RconTestHarness.Response response =
                    RconTestHarness.readResponse(socket.getInputStream());
            assertEquals(51, response.id());
            assertEquals(0, response.type());
            assertEquals("", response.body(), "one empty response packet, never silence");
        }
    }

    @Test
    void theLengthMismatchClosesTheConnection() throws Exception {
        // The reference's length law (RconClient line 47): the LE length
        // word must equal the bytes-after-it count, else the connection
        // closes without a reply.
        startServer("torch-secret", RconTestHarness.ephemeralTcpPort());
        try (Socket socket = RconTestHarness.connect(access.rconPort)) {
            ByteArrayOutputStream lie = new ByteArrayOutputStream();
            lie.write(100); // a claimed length that does not match
            lie.write(0);
            lie.write(0);
            lie.write(0);
            lie.write(7);
            lie.write(0);
            lie.write(0);
            lie.write(0);
            lie.write(SERVERDATA_AUTH);
            lie.write(0);
            lie.write(0);
            lie.write(0);
            lie.write(0);
            RconTestHarness.send(socket, lie.toByteArray());
            assertEquals(-1, socket.getInputStream().read(), "the mismatch law closes");
            assertTrue(access.executedCommands.isEmpty());
        }
    }

    @Test
    void theShortReadClosesTheConnection() throws Exception {
        // Fewer than ten bytes read -> close (RconClient line 41).
        startServer("torch-secret", RconTestHarness.ephemeralTcpPort());
        try (Socket socket = RconTestHarness.connect(access.rconPort)) {
            RconTestHarness.send(socket, new byte[]{1, 2, 3, 4});
            assertEquals(-1, socket.getInputStream().read(), "the short-read law closes");
        }
    }

    // ------------------------------------------------------------------ start() refusal laws

    @Test
    void theBlankPasswordRefusalLawNeverBinds() throws Exception {
        int port = RconTestHarness.ephemeralTcpPort();
        startServer("", port);
        assertFalse(server.isRunning(), "RconServer.start lines 91-92: no password, no listener");
        try (ServerSocket owner = new ServerSocket(port)) {
            assertTrue(owner.isBound(), "the port was never taken by the refused listener");
        }
    }

    @Test
    void theInvalidPortRefusalLawNeverBinds() {
        startServer("torch-secret", 70000);
        assertFalse(server.isRunning(), "RconServer.start lines 93-94: a bad port disables");
    }

    @Test
    void theBindFailureWarnsAndDegrades() throws Exception {
        int port = RconTestHarness.ephemeralTcpPort();
        try (ServerSocket squatter = new ServerSocket(port)) { // hold the port first
            startServer("torch-secret", port);
            assertFalse(server.isRunning(),
                    "RconServer.start lines 100-102: the bind failure degrades, never throws");
        }
    }

    // ------------------------------------------------------------------ lifecycle

    @Test
    void theStopIsIdempotentAndUnblocksTheClient() throws Exception {
        startServer("torch-secret", RconTestHarness.ephemeralTcpPort());
        try (Socket socket = RconTestHarness.connect(access.rconPort)) {
            auth(socket, 3, "torch-secret");
            server.stop();
            server.stop(); // the idempotency law
            assertFalse(server.isRunning());
            int read = socket.getInputStream().read();
            assertTrue(read == -1, "the client socket closed under the stopped listener");
        }
    }

    @Test
    void theAuthPasswordCompareSurvivesTheNullTerminator() throws Exception {
        // BufferHelper.getString stops at the NUL (lines 8-17): the client's
        // trailing terminator never leaks into the compared password.
        startServer("torch-secret", RconTestHarness.ephemeralTcpPort());
        try (Socket socket = RconTestHarness.connect(access.rconPort)) {
            auth(socket, 3, "torch-secret");
            RconTestHarness.send(socket,
                    RconTestHarness.rconPacket(61, SERVERDATA_EXECCOMMAND, "list"));
            RconTestHarness.Response response =
                    RconTestHarness.readResponse(socket.getInputStream());
            assertEquals(61, response.id(), "the authed session survived the terminator walk");
        }
    }
}
