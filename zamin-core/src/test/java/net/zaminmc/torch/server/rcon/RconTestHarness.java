package net.zaminmc.torch.server.rcon;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * The remote-admin tests' shared scaffolding: the fake server access (canned
 * outputs, the command log, the mutable census the cache laws read), the
 * standard RCON client-side packet writer, and the response reader that
 * asserts the little-endian framing byte-for-byte.
 */
final class RconTestHarness {

    private RconTestHarness() {
    }

    /** An ephemeral TCP port (bind-then-release; the listener under test binds it). */
    static int ephemeralTcpPort() throws IOException {
        try (ServerSocket probe = new ServerSocket(0)) {
            return probe.getLocalPort();
        }
    }

    /**
     * The fake DedicatedServerAccess: every status read is mutable so the
     * laws can flip them mid-session (the rules-cache window especially);
     * runRconCommand answers a canned output or a canned failure.
     */
    static final class FakeAccess implements RconServerAccess {
        final String password;
        int rconPort;
        int queryPort;
        String ip = "127.0.0.1";
        int serverPort = 25599;
        String motd = "A Test Server";
        String worldName = "world";
        int maxPlayers = 20;
        String gameVersion = "1.8.8";
        String plugins = "";
        int playerCount;
        String[] playerNames = new String[0];
        String cannedOutput = "";
        String cannedFailure;
        final List<String> executedCommands = new ArrayList<>();
        /** Every warn() the listener emitted — the refusal laws' evidence. */
        final List<String> warnings = new ArrayList<>();

        FakeAccess(String password, int port) {
            this.password = password;
            this.rconPort = port;
            this.queryPort = port;
        }

        @Override
        public int getIntProperty(String key, int defaultValue) {
            return switch (key) {
                case "rcon.port" -> rconPort;
                case "query.port" -> queryPort;
                default -> defaultValue;
            };
        }

        @Override
        public String getStringProperty(String key, String defaultValue) {
            return switch (key) {
                case "rcon.password" -> password;
                default -> defaultValue;
            };
        }

        @Override
        public void setProperty(String key, Object value) {
        }

        @Override
        public void saveProperties() {
        }

        @Override
        public String getPropertiesFilePath() {
            return "server.properties";
        }

        @Override
        public String getIp() {
            return ip;
        }

        @Override
        public int getPort() {
            return serverPort;
        }

        @Override
        public String getMotd() {
            return motd;
        }

        @Override
        public String getGameVersion() {
            return gameVersion;
        }

        @Override
        public int getPlayerCount() {
            return playerCount;
        }

        @Override
        public int getMaxPlayerCount() {
            return maxPlayers;
        }

        @Override
        public String[] getPlayerNames() {
            return playerNames;
        }

        @Override
        public String getWorldSaveName() {
            return worldName;
        }

        @Override
        public String getPlugins() {
            return plugins;
        }

        @Override
        public String runRconCommand(String command) throws Exception {
            this.executedCommands.add(command);
            if (cannedFailure != null) {
                throw new Exception(cannedFailure);
            }
            return cannedOutput;
        }

        @Override
        public boolean isDebuggingEnabled() {
            return false;
        }

        @Override
        public void info(String message) {
        }

        @Override
        public void warn(String message) {
            warnings.add(message);
        }

        @Override
        public void error(String message) {
        }

        @Override
        public void log(String message) {
        }
    }

    /** One server-to-client RCON packet, parsed from the little-endian framing. */
    record Response(int id, int type, String body, byte[] raw) {
    }

    /** The Source-RCON client frame: LE length, LE id, LE type, UTF-8 body, two NULs. */
    static byte[] rconPacket(int requestId, int type, String body) {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        int length = 4 + 4 + payload.length + 2; // id + type + body + the two NULs
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(length & 0xFF);
        out.write(length >>> 8 & 0xFF);
        out.write(length >>> 16 & 0xFF);
        out.write(length >>> 24 & 0xFF);
        out.write(requestId & 0xFF);
        out.write(requestId >>> 8 & 0xFF);
        out.write(requestId >>> 16 & 0xFF);
        out.write(requestId >>> 24 & 0xFF);
        out.write(type & 0xFF);
        out.write(type >>> 8 & 0xFF);
        out.write(type >>> 16 & 0xFF);
        out.write(type >>> 24 & 0xFF);
        out.write(payload, 0, payload.length);
        out.write(0);
        out.write(0);
        return out.toByteArray();
    }

    /**
     * Reads exactly one server response packet, asserting the reference's
     * execute() shape byte-for-byte: the LE length word equals body+10, and
     * the frame ends with the two NULs (RconClient.execute lines 99-110).
     */
    static Response readResponse(InputStream in) throws IOException {
        DataInputStream data = new DataInputStream(in);
        byte[] head = new byte[4];
        data.readFully(head);
        int length = head[0] & 0xFF | (head[1] & 0xFF) << 8
                | (head[2] & 0xFF) << 16 | head[3] << 24;
        byte[] rest = new byte[length];
        data.readFully(rest);
        int id = rest[0] & 0xFF | (rest[1] & 0xFF) << 8
                | (rest[2] & 0xFF) << 16 | rest[3] << 24;
        int type = rest[4] & 0xFF | (rest[5] & 0xFF) << 8
                | (rest[6] & 0xFF) << 16 | rest[7] << 24;
        int bodyLength = length - 10;
        if (bodyLength < 0 || rest[length - 1] != 0 || rest[length - 2] != 0) {
            throw new IOException("malformed frame: length=" + length);
        }
        String body = new String(rest, 8, bodyLength, StandardCharsets.UTF_8);
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        raw.write(head, 0, 4);
        raw.write(rest, 0, length);
        return new Response(id, type, body, raw.toByteArray());
    }

    /** Sends one request packet over the socket. */
    static void send(Socket socket, byte[] packet) throws IOException {
        OutputStream out = socket.getOutputStream();
        out.write(packet);
        out.flush();
    }

    /** A socket to the listener with a read deadline for no-reply assertions. */
    static Socket connect(int port) throws IOException {
        Socket socket = new Socket(InetAddress.getByName("127.0.0.1"), port);
        socket.setSoTimeout(5_000);
        return socket;
    }
}
