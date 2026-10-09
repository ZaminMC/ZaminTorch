package net.minecraft.client.network;

import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.mojang.authlib.GameProfile;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.nio.NioSocketChannel;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.handler.ClientQueryPacketHandler;
import net.minecraft.client.options.ServerListEntry;
import net.minecraft.network.Connection;
import net.minecraft.network.NetworkProtocol;
import net.minecraft.network.packet.c2s.handshake.HandshakeC2SPacket;
import net.minecraft.network.packet.c2s.query.PingC2SPacket;
import net.minecraft.network.packet.c2s.query.ServerStatusC2SPacket;
import net.minecraft.network.packet.s2c.query.PingS2CPacket;
import net.minecraft.network.packet.s2c.query.ServerStatusS2CPacket;
import net.minecraft.server.ServerStatus;
import net.minecraft.text.Formatting;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MultiplayerServerListPinger {
    private static final Splitter ZERO_SPLITTER = Splitter.on('\u0000').limit(6);
    private static final Logger LOGGER = LogManager.getLogger();
    private final List<Connection> clientConnectionList = Collections.synchronizedList(Lists.newArrayList());

    public void add(ServerListEntry serverEntry) throws UnknownHostException {
        ServerAddress serveraddress = ServerAddress.parse(serverEntry.ip);
        final Connection connection = Connection.connect(InetAddress.getByName(serveraddress.getAddress()), serveraddress.getPort(), false);
        this.clientConnectionList.add(connection);
        serverEntry.motd = "Pinging...";
        serverEntry.ping = -1L;
        serverEntry.onlinePlayers = null;
        connection.setListener(
            new ClientQueryPacketHandler() {
                private boolean success = false;
                private boolean received = false;
                private long statusReceivedTime = 0L;

                @Override
                public void handleServerStatus(ServerStatusS2CPacket packet) {
                    if (this.received) {
                        connection.disconnect(new LiteralText("Received unrequested status"));
                    } else {
                        this.received = true;
                        ServerStatus serverstatus = packet.getServerStatus();
                        if (serverstatus.getDescription() != null) {
                            serverEntry.motd = serverstatus.getDescription().getFormattedString();
                        } else {
                            serverEntry.motd = "";
                        }

                        if (serverstatus.getVersion() != null) {
                            serverEntry.version = serverstatus.getVersion().getName();
                            serverEntry.protocol = serverstatus.getVersion().getProtocol();
                        } else {
                            serverEntry.version = "Old";
                            serverEntry.protocol = 0;
                        }

                        if (serverstatus.getPlayers() != null) {
                            serverEntry.status = Formatting.GRAY
                                + ""
                                + serverstatus.getPlayers().getOnline()
                                + ""
                                + Formatting.DARK_GRAY
                                + "/"
                                + Formatting.GRAY
                                + serverstatus.getPlayers().getMax();
                            if (ArrayUtils.isNotEmpty(serverstatus.getPlayers().get())) {
                                StringBuilder stringbuilder = new StringBuilder();

                                for (GameProfile gameprofile : serverstatus.getPlayers().get()) {
                                    if (stringbuilder.length() > 0) {
                                        stringbuilder.append("\n");
                                    }

                                    stringbuilder.append(gameprofile.getName());
                                }

                                if (serverstatus.getPlayers().get().length < serverstatus.getPlayers().getOnline()) {
                                    if (stringbuilder.length() > 0) {
                                        stringbuilder.append("\n");
                                    }

                                    stringbuilder.append("... and ")
                                        .append(serverstatus.getPlayers().getOnline() - serverstatus.getPlayers().get().length)
                                        .append(" more ...");
                                }

                                serverEntry.onlinePlayers = stringbuilder.toString();
                            }
                        } else {
                            serverEntry.status = Formatting.DARK_GRAY + "???";
                        }

                        if (serverstatus.getFavicon() != null) {
                            String s = serverstatus.getFavicon();
                            if (s.startsWith("data:image/png;base64,")) {
                                serverEntry.setIcon(s.substring("data:image/png;base64,".length()));
                            } else {
                                MultiplayerServerListPinger.LOGGER.error("Invalid server icon (unknown format)");
                            }
                        } else {
                            serverEntry.setIcon(null);
                        }

                        this.statusReceivedTime = Minecraft.getTime();
                        connection.send(new PingC2SPacket(this.statusReceivedTime));
                        this.success = true;
                    }
                }

                @Override
                public void handlePing(PingS2CPacket packet) {
                    long i = this.statusReceivedTime;
                    long j = Minecraft.getTime();
                    serverEntry.ping = j - i;
                    connection.disconnect(new LiteralText("Finished"));
                }

                @Override
                public void onDisconnect(Text reason) {
                    if (!this.success) {
                        MultiplayerServerListPinger.LOGGER.error("Can't ping " + serverEntry.ip + ": " + reason.getString());
                        serverEntry.motd = Formatting.DARK_RED + "Can't connect to server.";
                        serverEntry.status = "";
                        MultiplayerServerListPinger.this.ping(serverEntry);
                    }
                }
            }
        );

        try {
            connection.send(new HandshakeC2SPacket(47, serveraddress.getAddress(), serveraddress.getPort(), NetworkProtocol.STATUS));
            connection.send(new ServerStatusC2SPacket());
        } catch (Throwable throwable) {
            LOGGER.error(throwable);
        }
    }

    private void ping(ServerListEntry serverListEntry) {
        final ServerAddress serveraddress = ServerAddress.parse(serverListEntry.ip);
        new Bootstrap().group(Connection.NETWORK_GROUP.get()).handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel channel) throws Exception {
                try {
                    channel.config().setOption(ChannelOption.TCP_NODELAY, true);
                } catch (ChannelException channelexception) {
                }

                channel.pipeline().addLast(new SimpleChannelInboundHandler<ByteBuf>() {
                    @Override
                    public void channelActive(ChannelHandlerContext channelHandlerContext) throws Exception {
                        super.channelActive(channelHandlerContext);
                        ByteBuf bytebuf = Unpooled.buffer();

                        try {
                            bytebuf.writeByte(254);
                            bytebuf.writeByte(1);
                            bytebuf.writeByte(250);
                            char[] achar = "MC|PingHost".toCharArray();
                            bytebuf.writeShort(achar.length);

                            for (char c0 : achar) {
                                bytebuf.writeChar(c0);
                            }

                            bytebuf.writeShort(7 + 2 * serveraddress.getAddress().length());
                            bytebuf.writeByte(127);
                            achar = serveraddress.getAddress().toCharArray();
                            bytebuf.writeShort(achar.length);

                            for (char c1 : achar) {
                                bytebuf.writeChar(c1);
                            }

                            bytebuf.writeInt(serveraddress.getPort());
                            channelHandlerContext.channel().writeAndFlush(bytebuf).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
                        } finally {
                            bytebuf.release();
                        }
                    }

                    protected void channelRead0(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf) throws Exception {
                        short short1 = byteBuf.readUnsignedByte();
                        if (short1 == 255) {
                            String s = new String(byteBuf.readBytes(byteBuf.readShort() * 2).array(), Charsets.UTF_16BE);
                            String[] astring = Iterables.toArray(MultiplayerServerListPinger.ZERO_SPLITTER.split(s), String.class);
                            if ("§1".equals(astring[0])) {
                                int i = MathHelper.parseInt(astring[1], 0);
                                String s1 = astring[2];
                                String s2 = astring[3];
                                int j = MathHelper.parseInt(astring[4], -1);
                                int k = MathHelper.parseInt(astring[5], -1);
                                serverListEntry.protocol = -1;
                                serverListEntry.version = s1;
                                serverListEntry.motd = s2;
                                serverListEntry.status = Formatting.GRAY + "" + j + "" + Formatting.DARK_GRAY + "/" + Formatting.GRAY + k;
                            }
                        }

                        channelHandlerContext.close();
                    }

                    @Override
                    public void exceptionCaught(ChannelHandlerContext channelHandlerContext, Throwable throwable) throws Exception {
                        channelHandlerContext.close();
                    }
                });
            }
        }).channel(NioSocketChannel.class).connect(serveraddress.getAddress(), serveraddress.getPort());
    }

    public void tick() {
        synchronized (this.clientConnectionList) {
            Iterator<Connection> iterator = this.clientConnectionList.iterator();

            while (iterator.hasNext()) {
                Connection connection = iterator.next();
                if (connection.isConnected()) {
                    connection.tick();
                } else {
                    iterator.remove();
                    connection.handleDisconnection();
                }
            }
        }
    }

    public void cancel() {
        synchronized (this.clientConnectionList) {
            Iterator<Connection> iterator = this.clientConnectionList.iterator();

            while (iterator.hasNext()) {
                Connection connection = iterator.next();
                if (connection.isConnected()) {
                    iterator.remove();
                    connection.disconnect(new LiteralText("Cancelled"));
                }
            }
        }
    }
}
