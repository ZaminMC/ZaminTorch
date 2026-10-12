package net.minecraft.network;

import com.google.common.collect.Queues;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalEventLoopGroup;
import io.netty.channel.local.LocalServerChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.TimeoutException;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.util.Queue;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.crypto.SecretKey;
import net.minecraft.network.encryption.EncryptionUtils;
import net.minecraft.network.encryption.PacketDecryptor;
import net.minecraft.network.encryption.PacketEncryptor;
import net.minecraft.network.handler.PacketHandler;
import net.minecraft.network.packet.Packet;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.LazySupplier;
import net.minecraft.util.Tickable;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

public class Connection extends SimpleChannelInboundHandler<Packet> {
    private static final Logger LOGGER = LogManager.getLogger();
    public static final Marker MARKER_NETWORK = MarkerManager.getMarker("NETWORK");
    public static final Marker MARKER_NETWORK_PACKETS = MarkerManager.getMarker("NETWORK_PACKETS", MARKER_NETWORK);
    public static final AttributeKey<NetworkProtocol> PROTOCOL = AttributeKey.valueOf("protocol");
    public static final LazySupplier<NioEventLoopGroup> NETWORK_GROUP = new LazySupplier<NioEventLoopGroup>() {
        protected NioEventLoopGroup load() {
            return new NioEventLoopGroup(0, new ThreadFactoryBuilder().setNameFormat("Netty Client IO #%d").setDaemon(true).build());
        }
    };
    public static final LazySupplier<EpollEventLoopGroup> EPOLL_NETWORK_GROUP = new LazySupplier<EpollEventLoopGroup>() {
        protected EpollEventLoopGroup load() {
            return new EpollEventLoopGroup(0, new ThreadFactoryBuilder().setNameFormat("Netty Epoll Client IO #%d").setDaemon(true).build());
        }
    };
    public static final LazySupplier<LocalEventLoopGroup> LOCAL_NETWORK_GROUP = new LazySupplier<LocalEventLoopGroup>() {
        protected LocalEventLoopGroup load() {
            return new LocalEventLoopGroup(0, new ThreadFactoryBuilder().setNameFormat("Netty Local Client IO #%d").setDaemon(true).build());
        }
    };
    private final PacketFlow flow;
    private final Queue<Connection.QueuedPacket> sendQueue = Queues.newConcurrentLinkedQueue();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private Channel channel;
    private SocketAddress address;
    private PacketHandler listener;
    private Text disconnectReason;
    private boolean encrypted;
    private boolean disconnected;

    public Connection(PacketFlow flow) {
        this.flow = flow;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        this.channel = ctx.channel();
        this.address = this.channel.remoteAddress();

        try {
            this.setProtocol(NetworkProtocol.HANDSHAKE);
        } catch (Throwable throwable) {
            LOGGER.fatal(throwable);
        }
    }

    public void setProtocol(NetworkProtocol protocol) {
        this.channel.attr(PROTOCOL).set(protocol);
        this.channel.config().setAutoRead(true);
        LOGGER.debug("Enabled auto read");
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        this.disconnect(new TranslatableText("disconnect.endOfStream"));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable throwable) throws Exception {
        TranslatableText translatabletext;
        if (throwable instanceof TimeoutException) {
            translatabletext = new TranslatableText("disconnect.timeout");
        } else {
            translatabletext = new TranslatableText("disconnect.genericReason", "Internal Exception: " + throwable);
        }

        this.disconnect(translatabletext);
    }

    protected void channelRead0(ChannelHandlerContext channelHandlerContext, Packet packet) throws Exception {
        if (this.channel.isOpen()) {
            try {
                packet.handle(this.listener);
            } catch (DifferentThreadException differentthreadexception) {
            }
        }
    }

    public void setListener(PacketHandler listener) {
        Validate.notNull(listener, "packetListener");
        LOGGER.debug("Set listener of {} to {}", this, listener);
        this.listener = listener;
    }

    public void send(Packet packet) {
        if (this.isConnected()) {
            this.flushQueue();
            this.doSend(packet, null);
        } else {
            this.lock.writeLock().lock();

            try {
                this.sendQueue.add(new Connection.QueuedPacket(packet, null));
            } finally {
                this.lock.writeLock().unlock();
            }
        }
    }

    public void send(
        Packet packet, GenericFutureListener<? extends Future<? super Void>> listener, GenericFutureListener<? extends Future<? super Void>>... listeners
    ) {
        if (this.isConnected()) {
            this.flushQueue();
            this.doSend(packet, ArrayUtils.add(listeners, 0, listener));
        } else {
            this.lock.writeLock().lock();

            try {
                this.sendQueue.add(new Connection.QueuedPacket(packet, ArrayUtils.add(listeners, 0, listener)));
            } finally {
                this.lock.writeLock().unlock();
            }
        }
    }

    private void doSend(Packet packet, GenericFutureListener<? extends Future<? super Void>>[] listeners) {
        final NetworkProtocol networkprotocol = NetworkProtocol.byPacket(packet);
        final NetworkProtocol networkprotocol1 = this.channel.attr(PROTOCOL).get();
        if (networkprotocol1 != networkprotocol) {
            LOGGER.debug("Disabled auto read");
            this.channel.config().setAutoRead(false);
        }

        if (this.channel.eventLoop().inEventLoop()) {
            if (networkprotocol != networkprotocol1) {
                this.setProtocol(networkprotocol);
            }

            ChannelFuture channelfuture = this.channel.writeAndFlush(packet);
            if (listeners != null) {
                channelfuture.addListeners(listeners);
            }

            channelfuture.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
        } else {
            this.channel.eventLoop().execute(new Runnable() {
                @Override
                public void run() {
                    if (networkprotocol != networkprotocol1) {
                        Connection.this.setProtocol(networkprotocol);
                    }

                    ChannelFuture channelfuture1 = Connection.this.channel.writeAndFlush(packet);
                    if (listeners != null) {
                        channelfuture1.addListeners(listeners);
                    }

                    channelfuture1.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
                }
            });
        }
    }

    private void flushQueue() {
        if (this.channel != null && this.channel.isOpen()) {
            this.lock.readLock().lock();

            try {
                while (!this.sendQueue.isEmpty()) {
                    Connection.QueuedPacket connection$queuedpacket = this.sendQueue.poll();
                    this.doSend(connection$queuedpacket.packet, connection$queuedpacket.listeners);
                }
            } finally {
                this.lock.readLock().unlock();
            }
        }
    }

    public void tick() {
        this.flushQueue();
        if (this.listener instanceof Tickable) {
            ((Tickable)this.listener).tick();
        }

        this.channel.flush();
    }

    public SocketAddress getAddress() {
        return this.address;
    }

    public void disconnect(Text reason) {
        if (this.channel.isOpen()) {
            this.channel.close().awaitUninterruptibly();
            this.disconnectReason = reason;
        }
    }

    public boolean isLocal() {
        return this.channel instanceof LocalChannel || this.channel instanceof LocalServerChannel;
    }

    public static Connection connect(InetAddress address, int port, boolean epoll) {
        final Connection connection = new Connection(PacketFlow.CLIENTBOUND);
        Class<? extends SocketChannel> oclass;
        LazySupplier<? extends EventLoopGroup> lazysupplier;
        if (Epoll.isAvailable() && epoll) {
            oclass = EpollSocketChannel.class;
            lazysupplier = EPOLL_NETWORK_GROUP;
        } else {
            oclass = NioSocketChannel.class;
            lazysupplier = NETWORK_GROUP;
        }

        new Bootstrap()
            .group(lazysupplier.get())
            .handler(
                new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel channel) throws Exception {
                        try {
                            channel.config().setOption(ChannelOption.TCP_NODELAY, true);
                        } catch (ChannelException channelexception) {
                        }

                        channel.pipeline()
                            .addLast("timeout", new ReadTimeoutHandler(30))
                            .addLast("splitter", new SplitterHandler())
                            .addLast("decoder", new PacketDecoder(PacketFlow.CLIENTBOUND))
                            .addLast("prepender", new SizePrepender())
                            .addLast("encoder", new PacketEncoder(PacketFlow.SERVERBOUND))
                            .addLast("packet_handler", connection);
                    }
                }
            )
            .channel(oclass)
            .connect(address, port)
            .syncUninterruptibly();
        return connection;
    }

    public static Connection connectLocal(SocketAddress address) {
        final Connection connection = new Connection(PacketFlow.CLIENTBOUND);
        new Bootstrap().group(LOCAL_NETWORK_GROUP.get()).handler(new ChannelInitializer<Channel>() {
            @Override
            protected void initChannel(Channel channel) throws Exception {
                channel.pipeline().addLast("packet_handler", connection);
            }
        }).channel(LocalChannel.class).connect(address).syncUninterruptibly();
        return connection;
    }

    public void setupEncryption(SecretKey key) {
        this.encrypted = true;
        this.channel.pipeline().addBefore("splitter", "decrypt", new PacketDecryptor(EncryptionUtils.crypt(2, key)));
        this.channel.pipeline().addBefore("prepender", "encrypt", new PacketEncryptor(EncryptionUtils.crypt(1, key)));
    }

    public boolean isEncrypted() {
        return this.encrypted;
    }

    public boolean isConnected() {
        return this.channel != null && this.channel.isOpen();
    }

    public boolean isConnecting() {
        return this.channel == null;
    }

    public PacketHandler getListener() {
        return this.listener;
    }

    public Text getDisconnectReason() {
        return this.disconnectReason;
    }

    public void disableAutoRead() {
        this.channel.config().setAutoRead(false);
    }

    public void setCompressionThreshold(int threshold) {
        if (threshold >= 0) {
            if (this.channel.pipeline().get("decompress") instanceof CompressionDecoder) {
                ((CompressionDecoder)this.channel.pipeline().get("decompress")).setThreshold(threshold);
            } else {
                this.channel.pipeline().addBefore("decoder", "decompress", new CompressionDecoder(threshold));
            }

            if (this.channel.pipeline().get("compress") instanceof CompressionEncoder) {
                ((CompressionEncoder)this.channel.pipeline().get("decompress")).setThreshold(threshold);
            } else {
                this.channel.pipeline().addBefore("encoder", "compress", new CompressionEncoder(threshold));
            }
        } else {
            if (this.channel.pipeline().get("decompress") instanceof CompressionDecoder) {
                this.channel.pipeline().remove("decompress");
            }

            if (this.channel.pipeline().get("compress") instanceof CompressionEncoder) {
                this.channel.pipeline().remove("compress");
            }
        }
    }

    public void handleDisconnection() {
        if (this.channel != null && !this.channel.isOpen()) {
            if (!this.disconnected) {
                this.disconnected = true;
                if (this.getDisconnectReason() != null) {
                    this.getListener().onDisconnect(this.getDisconnectReason());
                } else if (this.getListener() != null) {
                    this.getListener().onDisconnect(new LiteralText("Disconnected"));
                }
            } else {
                LOGGER.warn("handleDisconnection() called twice");
            }
        }
    }

    static class QueuedPacket {
        private final Packet packet;
        private final GenericFutureListener<? extends Future<? super Void>>[] listeners;

        public QueuedPacket(Packet packet, GenericFutureListener<? extends Future<? super Void>>... listeners) {
            this.packet = packet;
            this.listeners = listeners;
        }
    }
}
