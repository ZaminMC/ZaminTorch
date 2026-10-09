package net.minecraft.server.rcon;

import com.google.common.collect.Maps;
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
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Map.Entry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServerAccess;

public class QueryResponseHandler extends RconBase {
    private long lastQueryTime;
    private int queryPort;
    private int port;
    private int maxPlayerCount;
    private String motd;
    private String worldSaveName;
    private DatagramSocket socket;
    private byte[] packetBuffer = new byte[1460];
    private DatagramPacket currentPacket;
    private Map<SocketAddress, String> f_2928450;
    private String hostIp;
    private String ip;
    private Map<SocketAddress, QueryResponseHandler.Query> queries;
    private long f_2861636;
    private DataStreamHelper dataStreamHelper;
    private long lastResponseTime;

    public QueryResponseHandler(DedicatedServerAccess server) {
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

        this.f_2928450 = Maps.newHashMap();
        this.dataStreamHelper = new DataStreamHelper(1460);
        this.queries = Maps.newHashMap();
        this.f_2861636 = new Date().getTime();
    }

    private void reply(byte[] buffer, DatagramPacket packet) throws IOException {
        this.socket.send(new DatagramPacket(buffer, buffer.length, packet.getSocketAddress()));
    }

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
                        datastreamhelper.writeShort((short)this.port);
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

    private byte[] createRulesReply(DatagramPacket packet) throws IOException {
        long i = MinecraftServer.getTimeMillis();
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

    private byte[] getMessageBytes(SocketAddress socketAddress) {
        return this.queries.get(socketAddress).getMessageBytes();
    }

    private Boolean isValidQuery(DatagramPacket datagramPacket) {
        SocketAddress socketaddress = datagramPacket.getSocketAddress();
        if (!this.queries.containsKey(socketaddress)) {
            return false;
        }

        byte[] abyte = datagramPacket.getData();
        return this.queries.get(socketaddress).getId() != BufferHelper.getIntBE(abyte, 7, datagramPacket.getLength()) ? false : true;
    }

    private void createQuery(DatagramPacket datagramPacket) throws IOException {
        QueryResponseHandler.Query queryresponsehandler$query = new QueryResponseHandler.Query(datagramPacket);
        this.queries.put(datagramPacket.getSocketAddress(), queryresponsehandler$query);
        this.reply(queryresponsehandler$query.getReplyBuf(), datagramPacket);
    }

    private void cleanUp() {
        if (this.running) {
            long i = MinecraftServer.getTimeMillis();
            if (i >= this.lastQueryTime + 30000L) {
                this.lastQueryTime = i;
                Iterator<Entry<SocketAddress, QueryResponseHandler.Query>> iterator = this.queries.entrySet().iterator();

                while (iterator.hasNext()) {
                    Entry<SocketAddress, QueryResponseHandler.Query> entry = iterator.next();
                    if (entry.getValue().startedBefore(i)) {
                        iterator.remove();
                    }
                }
            }
        }
    }

    @Override
    public void run() {
        this.info("Query running on " + this.ip + ":" + this.queryPort);
        this.lastQueryTime = MinecraftServer.getTimeMillis();
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

    @Override
    public void start() {
        if (!this.running) {
            if (0 < this.queryPort && 65535 >= this.queryPort) {
                if (this.initialize()) {
                    super.start();
                }
            } else {
                this.warn("Invalid query port " + this.queryPort + " found in '" + this.server.getPropertiesFilePath() + "' (queries disabled)");
            }
        }
    }

    private void handleIoException(Exception e) {
        if (this.running) {
            this.warn("Unexpected exception, buggy JRE? (" + e.toString() + ")");
            if (!this.initialize()) {
                this.logError("Failed to recover from buggy JRE, shutting down!");
                this.running = false;
            }
        }
    }

    private boolean initialize() {
        try {
            this.socket = new DatagramSocket(this.queryPort, InetAddress.getByName(this.ip));
            this.registerSocket(this.socket);
            this.socket.setSoTimeout(500);
            return true;
        } catch (SocketException socketexception) {
            this.warn("Unable to initialise query system on " + this.ip + ":" + this.queryPort + " (Socket): " + socketexception.getMessage());
        } catch (UnknownHostException unknownhostexception) {
            this.warn("Unable to initialise query system on " + this.ip + ":" + this.queryPort + " (Unknown Host): " + unknownhostexception.getMessage());
        } catch (Exception exception) {
            this.warn("Unable to initialise query system on " + this.ip + ":" + this.queryPort + " (E): " + exception.getMessage());
        }

        return false;
    }

    class Query {
        private long startTime = new Date().getTime();
        private int id;
        private byte[] messageBytes;
        private byte[] replyBuf;
        private String message;

        public Query(DatagramPacket packet) {
            byte[] abyte = packet.getData();
            this.messageBytes = new byte[4];
            this.messageBytes[0] = abyte[3];
            this.messageBytes[1] = abyte[4];
            this.messageBytes[2] = abyte[5];
            this.messageBytes[3] = abyte[6];
            this.message = new String(this.messageBytes);
            this.id = new Random().nextInt(16777216);
            this.replyBuf = String.format("\t%s%d\u0000", this.message, this.id).getBytes();
        }

        public Boolean startedBefore(long lastQueryTime) {
            return this.startTime < lastQueryTime;
        }

        public int getId() {
            return this.id;
        }

        public byte[] getReplyBuf() {
            return this.replyBuf;
        }

        public byte[] getMessageBytes() {
            return this.messageBytes;
        }
    }
}
