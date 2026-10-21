package net.zaminmc.torch.server.rcon;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.PortUnreachableException;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

/**
 * The GS4 (UDP) query responder, 1:1 the reference
 * (reference/1.8.8/net/minecraft/server/rcon/QueryResponseHandler.java, 316
 * lines): the 500 ms receive-timeout loop, the magic-prefix law ({@code FE FD},
 * lines 90-91), the type-9 handshake whose challenge token is answered as
 * DECIMAL ASCII (the Query class lines 296-297 — the documented vanilla quirk)
 * but validated back as a big-endian int at offset 7 (isValidQuery line 191),
 * the exact 15-byte full-stats request length (line 97), the basic and full
 * stats byte shapes (lines 100-113 and 127-178), the 5-second cached rules
 * reply with its session patch (lines 128-137), and the 30-second challenge
 * sweep (lines 200-215).
 *
 * <p>Engine-side mappings, named: {@code MinecraftServer.getTimeMillis()} is
 * {@link System#currentTimeMillis()} (the reference's own implementation);
 * Guava's Maps is the JDK HashMap; the reference's two never-read decompile
 * fields ({@code f_2928450}, {@code f_2861636}) are omitted; the {@code log}
 * verb is the fine-level JUL record (§30's logging backend). The modern
 * ServerQueryNetworkHandler's TCP status ping (reference
 * net/minecraft/server/network/handler/ServerQueryNetworkHandler.java — a
 * STATUS-state packet pair on the game port) is a different protocol
 * entirely; this handler is the legacy UDP GS4 surface.</p>
 */
public class QueryResponseHandler extends RconBase {

    private long lastQueryTime;
    private int queryPort;
    private final int port;
    private final int maxPlayerCount;
    private final String motd;
    private final String worldSaveName;
    private DatagramSocket socket;
    private final byte[] packetBuffer = new byte[1460];
    private DatagramPacket currentPacket;
    private String hostIp;
    private String ip;
    private final Map<SocketAddress, Query> queries;
    private final DataStreamHelper dataStreamHelper;
    private long lastResponseTime;

    /**
     * The reference lines 39-79: the property reads, the ip/hostIp resolution
     * law (lines 42-65 — a configured non-wildcard server-ip becomes the
     * advertised hostIp; the wildcard arm resolves the local host address
     * and warns on failure), and the boot-time capture of the reply's
     * static fields (motd/max/world at construction — the reference's own
     * restart-for-changes shape). The {@code query.port} default arm
     * (lines 67-73) is structurally dormant like the RCON one — the
     * engine's config load already resolved the row.
     */
    public QueryResponseHandler(RconServerAccess server) {
        super(server, "Query Listener");
        this.queryPort = server.getIntProperty("query.port", 0);
        this.ip = server.getIp();
        this.port = server.getPort();
        this.motd = server.getMotd();
        this.maxPlayerCount = server.getMaxPlayerCount();
        this.worldSaveName = server.getWorldSaveName();
        this.lastResponseTime = 0L;
        this.hostIp = "0.0.0.0";
        if (0 != this.ip.length() && !this.hostIp.equals(this.ip)) {
            this.hostIp = this.ip;
        } else {
            this.ip = "0.0.0.0";

            try {
                InetAddress inetaddress = InetAddress.getLocalHost();
                this.hostIp = inetaddress.getHostAddress();
            } catch (UnknownHostException unknownhostexception) {
                this.warn(
                    "Unable to determine local host IP, please set server-ip in '"
                        + server.getPropertiesFilePath()
                        + "' : "
                        + unknownhostexception.getMessage()
                );
            }
        }

        if (0 == this.queryPort) {
            this.queryPort = this.port;
            this.info("Setting default query port to " + this.queryPort);
            server.setProperty("query.port", this.queryPort);
            server.setProperty("debug", false);
            server.saveProperties();
        }

        this.dataStreamHelper = new DataStreamHelper(1460);
        this.queries = new HashMap<>();
    }

    /** The reference line 81-83: the one-datagram reply. */
    private void reply(byte[] buffer, DatagramPacket packet) throws IOException {
        this.socket.send(new DatagramPacket(buffer, buffer.length, packet.getSocketAddress()));
    }

    /**
     * The reference lines 85-125: the packet law — at least three bytes, the
     * {@code FE FD} magic (bytes -2, -3), then the type switch: type 0
     * (stats) requires a valid challenge and splits on the exact 15-byte
     * full-stats request length, type 9 is the handshake, anything else
     * falls through the default and answers nothing.
     */
    private boolean handle(DatagramPacket packet) throws IOException {
        byte[] abyte = packet.getData();
        int i = packet.getLength();
        SocketAddress socketaddress = packet.getSocketAddress();
        this.log("Packet len " + i + " [" + socketaddress + "]");
        if (3 <= i && -2 == abyte[0] && -3 == abyte[1]) {
            this.log("Packet '" + BufferHelper.toHex(abyte[2]) + "' [" + socketaddress + "]");
            switch (abyte[2]) {
                case 0:
                    if (!this.isValidQuery(packet)) {
                        this.log("Invalid challenge [" + socketaddress + "]");
                        return false;
                    } else if (15 == i) {
                        this.reply(this.createRulesReply(packet), packet);
                        this.log("Rules [" + socketaddress + "]");
                    } else {
                        DataStreamHelper datastreamhelper = new DataStreamHelper(1460);
                        datastreamhelper.write(0);
                        datastreamhelper.write(this.getMessageBytes(packet.getSocketAddress()));
                        datastreamhelper.writeBytes(this.motd);
                        datastreamhelper.writeBytes("SMP");
                        datastreamhelper.writeBytes(this.worldSaveName);
                        datastreamhelper.writeBytes(Integer.toString(this.getCurrentPlayerCount()));
                        datastreamhelper.writeBytes(Integer.toString(this.maxPlayerCount));
                        datastreamhelper.writeShort((short) this.port);
                        datastreamhelper.writeBytes(this.hostIp);
                        this.reply(datastreamhelper.bytes(), packet);
                        this.log("Status [" + socketaddress + "]");
                    }
                default:
                    return true;
                case 9:
                    this.createQuery(packet);
                    this.log("Challenge [" + socketaddress + "]");
                    return true;
            }
        } else {
            this.log("Invalid packet [" + socketaddress + "]");
            return false;
        }
    }

    /**
     * The reference lines 127-178: the full-stats reply — the 5-second
     * cached arm (the same buffer patched with the new session bytes at
     * offsets 1-4, the reference's own stale-numbers window) and the full
     * rebuild: the type byte, the session, {@code splitnum}, the 0x80 0x00
     * pair, the ten key/value rows, then the player section
     * ({@code 00 01 "player_" 00}, the names, the terminating NUL).
     */
    private byte[] createRulesReply(DatagramPacket packet) throws IOException {
        long i = System.currentTimeMillis();
        if (i < this.lastResponseTime + 5000L) {
            byte[] abyte = this.dataStreamHelper.bytes();
            byte[] abyte1 = this.getMessageBytes(packet.getSocketAddress());
            abyte[1] = abyte1[0];
            abyte[2] = abyte1[1];
            abyte[3] = abyte1[2];
            abyte[4] = abyte1[3];
            return abyte;
        }

        this.lastResponseTime = i;
        this.dataStreamHelper.reset();
        this.dataStreamHelper.write(0);
        this.dataStreamHelper.write(this.getMessageBytes(packet.getSocketAddress()));
        this.dataStreamHelper.writeBytes("splitnum");
        this.dataStreamHelper.write(128);
        this.dataStreamHelper.write(0);
        this.dataStreamHelper.writeBytes("hostname");
        this.dataStreamHelper.writeBytes(this.motd);
        this.dataStreamHelper.writeBytes("gametype");
        this.dataStreamHelper.writeBytes("SMP");
        this.dataStreamHelper.writeBytes("game_id");
        this.dataStreamHelper.writeBytes("MINECRAFT");
        this.dataStreamHelper.writeBytes("version");
        this.dataStreamHelper.writeBytes(this.server.getGameVersion());
        this.dataStreamHelper.writeBytes("plugins");
        this.dataStreamHelper.writeBytes(this.server.getPlugins());
        this.dataStreamHelper.writeBytes("map");
        this.dataStreamHelper.writeBytes(this.worldSaveName);
        this.dataStreamHelper.writeBytes("numplayers");
        this.dataStreamHelper.writeBytes("" + this.getCurrentPlayerCount());
        this.dataStreamHelper.writeBytes("maxplayers");
        this.dataStreamHelper.writeBytes("" + this.maxPlayerCount);
        this.dataStreamHelper.writeBytes("hostport");
        this.dataStreamHelper.writeBytes("" + this.port);
        this.dataStreamHelper.writeBytes("hostip");
        this.dataStreamHelper.writeBytes(this.hostIp);
        this.dataStreamHelper.write(0);
        this.dataStreamHelper.write(1);
        this.dataStreamHelper.writeBytes("player_");
        this.dataStreamHelper.write(0);
        String[] astring = this.server.getPlayerNames();

        for (String s : astring) {
            this.dataStreamHelper.writeBytes(s);
        }

        this.dataStreamHelper.write(0);
        return this.dataStreamHelper.bytes();
    }

    /** The reference lines 180-182: the session bytes of a validated query. */
    private byte[] getMessageBytes(SocketAddress socketAddress) {
        return this.queries.get(socketAddress).getMessageBytes();
    }

    /**
     * The reference lines 184-192: the challenge check — the address must
     * have completed a handshake, and the request's offset-7 big-endian int
     * must equal the decimal-ASCII token the handshake answered.
     */
    private boolean isValidQuery(DatagramPacket datagramPacket) {
        SocketAddress socketaddress = datagramPacket.getSocketAddress();
        if (!this.queries.containsKey(socketaddress)) {
            return false;
        }

        byte[] abyte = datagramPacket.getData();
        return this.queries.get(socketaddress).getId()
                != BufferHelper.getIntBE(abyte, 7, datagramPacket.getLength()) ? false : true;
    }

    /** The reference lines 194-198: one handshake per address, newest wins. */
    private void createQuery(DatagramPacket datagramPacket) throws IOException {
        QueryResponseHandler.Query queryresponsehandler$query = new QueryResponseHandler.Query(datagramPacket);
        this.queries.put(datagramPacket.getSocketAddress(), queryresponsehandler$query);
        this.reply(queryresponsehandler$query.getReplyBuf(), datagramPacket);
    }

    /**
     * The reference lines 200-215: the sweep — every 30 seconds ALL
     * challenges started before the sweep moment are dropped (the
     * reference's own aggressive law: {@code startedBefore(now)} is true
     * for every existing entry, so the whole map empties).
     */
    private void cleanUp() {
        if (this.running) {
            long i = System.currentTimeMillis();
            if (i >= this.lastQueryTime + 30000L) {
                this.lastQueryTime = i;
                Iterator<Map.Entry<SocketAddress, QueryResponseHandler.Query>> iterator =
                        this.queries.entrySet().iterator();

                while (iterator.hasNext()) {
                    Map.Entry<SocketAddress, QueryResponseHandler.Query> entry = iterator.next();
                    if (entry.getValue().startedBefore(i)) {
                        iterator.remove();
                    }
                }
            }
        }
    }

    /** The reference lines 217-239: the receive loop. */
    @Override
    public void run() {
        this.info("Query running on " + this.ip + ":" + this.queryPort);
        this.lastQueryTime = System.currentTimeMillis();
        this.currentPacket = new DatagramPacket(this.packetBuffer, this.packetBuffer.length);

        try {
            while (this.running) {
                try {
                    this.socket.receive(this.currentPacket);
                    this.cleanUp();
                    this.handle(this.currentPacket);
                } catch (SocketTimeoutException sockettimeoutexception) {
                    this.cleanUp();
                } catch (PortUnreachableException portunreachableexception) {
                } catch (IOException ioexception) {
                    this.handleIoException(ioexception);
                }
            }
        } finally {
            this.forceClose();
        }
    }

    /**
     * The reference lines 241-252: the port range law (warn and disable,
     * never a boot failure) and the bind.
     */
    @Override
    public void start() {
        if (!this.running) {
            if (0 < this.queryPort && 65535 >= this.queryPort) {
                if (this.initialize()) {
                    super.start();
                }
            } else {
                this.warn("Invalid query port " + this.queryPort + " found in '"
                        + this.server.getPropertiesFilePath() + "' (queries disabled)");
            }
        }
    }

    /** The reference lines 254-262: a receive failure re-inits once, then gives up. */
    private void handleIoException(Exception e) {
        if (this.running) {
            this.warn("Unexpected exception, buggy JRE? (" + e.toString() + ")");
            if (!this.initialize()) {
                this.logError("Failed to recover from buggy JRE, shutting down!");
                this.running = false;
            }
        }
    }

    /**
     * The reference lines 264-279: the datagram bind with the 500 ms
     * receive timeout; a bind failure warns and degrades.
     */
    private boolean initialize() {
        try {
            this.socket = new DatagramSocket(this.queryPort, InetAddress.getByName(this.ip));
            this.registerSocket(this.socket);
            this.socket.setSoTimeout(500);
            return true;
        } catch (SocketException socketexception) {
            this.warn("Unable to initialise query system on " + this.ip + ":"
                    + this.queryPort + " (Socket): " + socketexception.getMessage());
        } catch (UnknownHostException unknownhostexception) {
            this.warn("Unable to initialise query system on " + this.ip + ":"
                    + this.queryPort + " (Unknown Host): " + unknownhostexception.getMessage());
        } catch (Exception exception) {
            this.warn("Unable to initialise query system on " + this.ip + ":"
                    + this.queryPort + " (E): " + exception.getMessage());
        }

        return false;
    }

    /**
     * The engine's lifecycle addition (the reference has no stop verb): the
     * flag flip plus the datagram close — the close is what unblocks the
     * parked receive; the loop's own finally re-runs the idempotent
     * forceClose.
     */
    @Override
    public void stop() {
        super.stop();
        this.forceClose();
    }

    /**
     * The reference lines 281-316: one challenge state per handshake — the
     * four session bytes echoed raw by every stats reply, the 24-bit random
     * token ({@code nextInt(16777216)}), and the handshake reply built as
     * {@code "\t%s%d\u0000"} — the tab IS the 0x09 type byte, the session
     * rides the string round-trip, and the token lands as DECIMAL ASCII
     * digits (the vanilla quirk GS4 clients are built to parse).
     */
    class Query {
        private final long startTime = new Date().getTime();
        private final int id;
        private final byte[] messageBytes;
        private final byte[] replyBuf;

        public Query(DatagramPacket packet) {
            byte[] abyte = packet.getData();
            this.messageBytes = new byte[4];
            this.messageBytes[0] = abyte[3];
            this.messageBytes[1] = abyte[4];
            this.messageBytes[2] = abyte[5];
            this.messageBytes[3] = abyte[6];
            String message = new String(this.messageBytes);
            this.id = new Random().nextInt(16777216);
            this.replyBuf = String.format("\t%s%d\u0000", message, this.id).getBytes();
        }

        /** The reference lines 300-302. */
        public boolean startedBefore(long lastQueryTime) {
            return this.startTime < lastQueryTime;
        }

        /** The reference lines 304-306. */
        public int getId() {
            return this.id;
        }

        /** The reference lines 308-310. */
        public byte[] getReplyBuf() {
            return this.replyBuf;
        }

        /** The reference lines 312-314. */
        public byte[] getMessageBytes() {
            return this.messageBytes;
        }
    }
}
