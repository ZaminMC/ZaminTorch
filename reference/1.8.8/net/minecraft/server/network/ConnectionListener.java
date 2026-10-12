package net.minecraft.server.network;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalEventLoopGroup;
import io.netty.channel.local.LocalServerChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketDecoder;
import net.minecraft.network.PacketEncoder;
import net.minecraft.network.PacketFlow;
import net.minecraft.network.SizePrepender;
import net.minecraft.network.SplitterHandler;
import net.minecraft.network.packet.s2c.play.DisconnectS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.handler.LocalHandshakeNetworkHandler;
import net.minecraft.server.network.handler.RemoteHandshakeNetworkHandler;
import net.minecraft.text.LiteralText;
import net.minecraft.util.LazySupplier;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportCategory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ConnectionListener {
    private static final Logger LOGGER = LogManager.getLogger();
    public static final LazySupplier<NioEventLoopGroup> NETWORK_GROUP = new LazySupplier<NioEventLoopGroup>() {
        protected NioEventLoopGroup load() {
            return new NioEventLoopGroup(0, new ThreadFactoryBuilder().setNameFormat("Netty Server IO #%d").setDaemon(true).build());
        }
    };
    public static final LazySupplier<EpollEventLoopGroup> EPOLL_EVENT_LOOP_GROUP = new LazySupplier<EpollEventLoopGroup>() {
        protected EpollEventLoopGroup load() {
            return new EpollEventLoopGroup(0, new ThreadFactoryBuilder().setNameFormat("Netty Epoll Server IO #%d").setDaemon(true).build());
        }
    };
    public static final LazySupplier<LocalEventLoopGroup> LOCAL_NETWORK_GROUP = new LazySupplier<LocalEventLoopGroup>() {
        protected LocalEventLoopGroup load() {
            return new LocalEventLoopGroup(0, new ThreadFactoryBuilder().setNameFormat("Netty Local Server IO #%d").setDaemon(true).build());
        }
    };
    private final MinecraftServer server;
    public volatile boolean open;
    private final List<ChannelFuture> channels = Collections.synchronizedList(Lists.newArrayList());
    private final List<Connection> connections = Collections.synchronizedList(Lists.newArrayList());

    public ConnectionListener(MinecraftServer server) {
        this.server = server;
        this.open = true;
    }

    public void bind(InetAddress address, int port) throws IOException {
        synchronized (this.channels) {
            Class<? extends ServerSocketChannel> oclass;
            LazySupplier<? extends EventLoopGroup> lazysupplier;
            if (Epoll.isAvailable() && this.server.useNativeTransport()) {
                oclass = EpollServerSocketChannel.class;
                lazysupplier = EPOLL_EVENT_LOOP_GROUP;
                LOGGER.info("Using epoll channel type");
            } else {
                oclass = NioServerSocketChannel.class;
                lazysupplier = NETWORK_GROUP;
                LOGGER.info("Using default channel type");
            }

            this.channels
                .add(
                    new ServerBootstrap()
                        .channel(oclass)
                        .childHandler(
                            new ChannelInitializer<Channel>() {
                                @Override
                                protected void initChannel(Channel chennel) throws Exception {
                                    try {
                                        chennel.config().setOption(ChannelOption.TCP_NODELAY, true);
                                    } catch (ChannelException channelexception) {
                                    }

                                    chennel.pipeline()
                                        .addLast("timeout", new ReadTimeoutHandler(30))
                                        .addLast("legacy_query", new LegacyQueryHandler(ConnectionListener.this))
                                        .addLast("splitter", new SplitterHandler())
                                        .addLast("decoder", new PacketDecoder(PacketFlow.SERVERBOUND))
                                        .addLast("prepender", new SizePrepender())
                                        .addLast("encoder", new PacketEncoder(PacketFlow.CLIENTBOUND));
                                    Connection connection = new Connection(PacketFlow.SERVERBOUND);
                                    ConnectionListener.this.connections.add(connection);
                                    chennel.pipeline().addLast("packet_handler", connection);
                                    connection.setListener(new RemoteHandshakeNetworkHandler(ConnectionListener.this.server, connection));
                                }
                            }
                        )
                        .group(lazysupplier.get())
                        .localAddress(address, port)
                        .bind()
                        .syncUninterruptibly()
                );
        }
    }

    public SocketAddress bindLocal() {
        ChannelFuture channelfuture;
        synchronized (this.channels) {
            channelfuture = new ServerBootstrap().channel(LocalServerChannel.class).childHandler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel channel) throws Exception {
                    Connection connection = new Connection(PacketFlow.SERVERBOUND);
                    connection.setListener(new LocalHandshakeNetworkHandler(ConnectionListener.this.server, connection));
                    ConnectionListener.this.connections.add(connection);
                    channel.pipeline().addLast("packet_handler", connection);
                }
            }).group(NETWORK_GROUP.get()).localAddress(LocalAddress.ANY).bind().syncUninterruptibly();
            this.channels.add(channelfuture);
        }

        return channelfuture.channel().localAddress();
    }

    public void close() {
        this.open = false;

        for (ChannelFuture channelfuture : this.channels) {
            try {
                channelfuture.channel().close().sync();
            } catch (InterruptedException interruptedexception) {
                LOGGER.error("Interrupted whilst closing channel");
            }
        }
    }

    public void tick() {
        synchronized (this.connections) {
            Iterator<Connection> iterator = this.connections.iterator();

            while (iterator.hasNext()) {
                final Connection connection = iterator.next();
                if (!connection.isConnecting()) {
                    if (!connection.isConnected()) {
                        iterator.remove();
                        connection.handleDisconnection();
                    } else {
                        try {
                            connection.tick();
                        } catch (Exception exception) {
                            if (connection.isLocal()) {
                                CrashReport crashreport = CrashReport.of(exception, "Ticking memory connection");
                                CrashReportCategory crashreportcategory = crashreport.addCategory("Ticking connection");
                                crashreportcategory.add("Connection", new Callable<String>() {
                                    public String call() throws Exception {
                                        return connection.toString();
                                    }
                                });
                                throw new CrashException(crashreport);
                            }

                            LOGGER.warn("Failed to handle packet for " + connection.getAddress(), exception);
                            final LiteralText literaltext = new LiteralText("Internal server error");
                            connection.send(new DisconnectS2CPacket(literaltext), new GenericFutureListener<Future<? super Void>>() {
                                @Override
                                public void operationComplete(Future<? super Void> future) throws Exception {
                                    connection.disconnect(literaltext);
                                }
                            });
                            connection.disableAutoRead();
                        }
                    }
                }
            }
        }
    }

    public MinecraftServer getServer() {
        return this.server;
    }
}
