package net.zaminmc.torch.server.rcon;

import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.config.EngineConfig;
import net.zaminmc.torch.server.ops.BanStore;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The remote-admin protocols through a REAL booted engine (one boot per
 * class, the established server-test harness): RCON commands ride the
 * engine's one console route (the §30 law — the same
 * ChatService.dispatchCommand walk consoleCommand submits) and their output
 * returns over the wire; the GS4 query answers from the engine's own public
 * surfaces (the same census/motd reads the server-list ping performs).
 * Both listeners boot from the config rows (DedicatedServer lines 203-216)
 * on ephemeral ports.
 */
class RconEngineRouteIntegrationTest {

    private static final String PASSWORD = "torch-secret";
    private static final String MOTD = "A Torch RCON Test";

    @TempDir
    static Path dataDir;

    private static EngineServer server;
    private static int rconPort;
    private static int queryPort;

    @BeforeAll
    static void bootEngine() throws Exception {
        rconPort = RconTestHarness.ephemeralTcpPort();
        queryPort = RconTestHarness.ephemeralTcpPort(); // a free port is a free port
        EngineConfig config = new EngineConfig("127.0.0.1", 0,
                "rcon-route-world", MOTD, 20, 2, 20, dataDir.toString(),
                GameMode.SURVIVAL, true, "flat", false,
                EngineConfig.WHOLE_WORLD_REGION_SIZE, false,
                EngineConfig.DEFAULT_SPAWN_PROTECTION,
                true, PASSWORD, rconPort, true, queryPort);
        server = new EngineServer(config);
        server.start();
        // The boot thread starts the listeners just past the world-ready
        // latch; wait for both seats.
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline
                && (server.rconServer() == null || server.queryResponseHandler() == null)) {
            Thread.sleep(20);
        }
        assertTrue(server.rconServer() != null && server.rconServer().isRunning(),
                "the enabled RCON listener booted (enable-rcon, DedicatedServer line 210)");
        assertTrue(server.queryResponseHandler() != null
                        && server.queryResponseHandler().isRunning(),
                "the enabled query listener booted (enable-query, DedicatedServer line 203)");
    }

    @AfterAll
    static void stopEngine() {
        if (server != null) {
            server.shutdown(null);
        }
    }

    /** A standard Source-RCON client session over the real engine listener. */
    private static RconTestHarness.Response rcon(int requestId, String command) throws Exception {
        try (Socket socket = RconTestHarness.connect(rconPort)) {
            RconTestHarness.send(socket, RconTestHarness.rconPacket(1, 3, PASSWORD));
            RconTestHarness.readResponse(socket.getInputStream());
            RconTestHarness.send(socket, RconTestHarness.rconPacket(requestId, 2, command));
            return RconTestHarness.readResponse(socket.getInputStream());
        }
    }

    @Test
    void theEngineCommandRouteAnswersOverTheWire() throws Exception {
        // "list" through the real console route: the same dispatcher, the
        // same executor, the captured buffer returned (DedicatedServer's
        // runRconCommand law, reference line 531).
        RconTestHarness.Response list = rcon(7, "list");
        assertEquals(7, list.id());
        assertEquals(0, list.type());
        assertEquals("There are 0 of a max of 20 players online: (none)", list.body(),
                "the engine's own /list output returned over RCON");
    }

    @Test
    void theRemoteBanPersistsLikeTheConsoleBan() throws Exception {
        // The ConsoleCommandTest harness shape: a remote ban must land in
        // the same store the console ban does — the one-command-path law's
        // proof (a second sender shape, the same dispatch route).
        RconTestHarness.Response ban = rcon(9, "ban Griefer griefing the spawn");
        assertEquals(9, ban.id());
        assertEquals("Banned Griefer", ban.body(), "the ban's own feedback");
        Path store = dataDir.resolve("banned-players.json");
        assertTrue(Files.exists(store), "the ban store file exists");
        assertFalse(BanStore.load(store).entries().isEmpty(),
                "the remote ban persisted exactly like the console's");
    }

    @Test
    void theUnknownCommandRefusalIsCapturedNotSilent() throws Exception {
        RconTestHarness.Response unknown = rcon(11, "definitelynotacommand");
        assertEquals(11, unknown.id());
        assertEquals("Unknown command. Type \"/help\" for help.", unknown.body(),
                "the dispatcher's refusal line rides the capture buffer");
    }

    @Test
    void theOperatorLevelFourGateRidesTheSameRoute() throws Exception {
        // The remote source is operator level 4 (the reference Console's
        // always-true permission law): a level-4 command answers its
        // feedback, proving the sender's level carries through the
        // dispatch gate. The cheap level-4 arm: the anvilimport usage
        // refusal (the /save-all maintenance family's own level, the
        // engine's own row — the full-save arm answers only after ~10s of
        // first-save durability on a fresh world, a cost the shutdown walk
        // already exercises; the GATE is the law here, not the save).
        RconTestHarness.Response usage = rcon(13, "anvilimport");
        assertEquals(13, usage.id());
        assertEquals("Usage: /anvilimport <world-folder>", usage.body(),
                "the level-4 gate passed and its usage line rides the wire");
    }

    // ------------------------------------------------------------------ the GS4 engine reads

    @Test
    void theQueryAnswersFromTheEngineSurfaces() throws Exception {
        try (DatagramSocket client = new DatagramSocket()) {
            client.setSoTimeout(2_000);
            byte[] session = {0x01, 0x02, 0x03, 0x04};
            client.send(new DatagramPacket(new byte[]{(byte) 0xFE, (byte) 0xFD, 9,
                    1, 2, 3, 4}, 7, InetAddress.getByName("127.0.0.1"), queryPort));
            byte[] buf = new byte[2048];
            DatagramPacket reply = new DatagramPacket(buf, buf.length);
            client.receive(reply);
            byte[] handshake = Arrays.copyOf(reply.getData(), reply.getLength());
            assertEquals(0x09, handshake[0] & 0xFF);
            int challenge = parseTrailingDecimal(handshake);

            byte[] request = new byte[11];
            request[0] = (byte) 0xFE;
            request[1] = (byte) 0xFD;
            request[2] = 0;
            System.arraycopy(session, 0, request, 3, 4);
            request[7] = (byte) (challenge >>> 24);
            request[8] = (byte) (challenge >>> 16 & 0xFF);
            request[9] = (byte) (challenge >>> 8 & 0xFF);
            request[10] = (byte) challenge;
            client.send(new DatagramPacket(request, request.length,
                    InetAddress.getByName("127.0.0.1"), queryPort));
            DatagramPacket statsPacket = new DatagramPacket(buf, buf.length);
            client.receive(statsPacket);
            byte[] stats = Arrays.copyOf(statsPacket.getData(), statsPacket.getLength());

            // The basic stats from the engine's own state: the configured
            // MOTD, SMP, the world name, the live census, the configured
            // max, the little-endian configured port, the hostIp.
            assertEquals(0, stats[0]);
            assertEquals(1, stats[1]);
            assertEquals(2, stats[2]);
            assertEquals(3, stats[3]);
            assertEquals(4, stats[4]);
            String tail = new String(stats, 5, stats.length - 5, StandardCharsets.UTF_8);
            String expected = MOTD + "\0"
                    + "SMP" + "\0"
                    + "rcon-route-world" + "\0"
                    + "0" + "\0"
                    + "20" + "\0"
                    + "127.0.0.1" + "\0";
            // The LE port word (0: the engine's configured port row) sits
            // between the max-players field and the hostIp field: portAt is
            // where hostIp starts, so the word owns [portAt-2, portAt).
            int portAt = stats.length - 1 - "127.0.0.1".length();
            int advertisedPort = stats[portAt - 2] & 0xFF | (stats[portAt - 1] & 0xFF) << 8;
            assertEquals(0, advertisedPort, "the little-endian configured port");
            String withoutPortWord = new String(stats, 5, portAt - 2 - 5, StandardCharsets.UTF_8)
                    + new String(stats, portAt, stats.length - portAt, StandardCharsets.UTF_8);
            assertEquals(expected, withoutPortWord,
                    "the engine's MOTD/world/census/hostIp answer in the reference's field order");
        }
    }

    @Test
    void theStatsRequestWithoutAHandshakeAnswersNothing() throws Exception {
        // The challenge map has no seat for this address: even a guessed
        // token answers nothing (isValidQuery, reference lines 184-192).
        try (DatagramSocket client = new DatagramSocket()) {
            client.setSoTimeout(500);
            byte[] request = new byte[11];
            request[0] = (byte) 0xFE;
            request[1] = (byte) 0xFD;
            request[2] = 0;
            client.send(new DatagramPacket(request, request.length,
                    InetAddress.getByName("127.0.0.1"), queryPort));
            try {
                client.receive(new DatagramPacket(new byte[256], 256));
                throw new AssertionError("no reply may come without a handshake");
            } catch (SocketTimeoutException expected) {
                // the silence is the law
            }
        }
    }

    private static int parseTrailingDecimal(byte[] reply) {
        int end = reply.length - 1;
        while (reply[end] != 0) {
            end--;
        }
        int start = end;
        while (start > 0 && reply[start - 1] >= '0' && reply[start - 1] <= '9') {
            start--;
        }
        return Integer.parseInt(
                new String(reply, start, end - start, StandardCharsets.US_ASCII));
    }
}
