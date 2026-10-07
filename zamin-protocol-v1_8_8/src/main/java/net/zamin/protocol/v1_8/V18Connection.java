package net.zamin.protocol.v1_8;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import net.zamin.api.Position;
import net.zamin.api.Rotation;
import net.zamin.engine.EngineServer;
import net.zamin.engine.net.EngineBridge;
import net.zamin.engine.player.PlayerSession;
import net.zamin.engine.world.EngineChunk;

import net.zamin.api.ChunkPosition;
import net.zamin.api.Position;
import net.zamin.api.Rotation;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * One 1.8.8 client connection: explicit protocol state machine
 * (HANDSHAKE -&gt; STATUS | LOGIN -&gt; PLAY), packet translation, and the
 * engine {@link ClientLink} implementation.
 *
 * <p>Threading: all packet handling runs on the channel's event loop, which
 * gives per-connection ordering for free. Engine access happens only through
 * {@link EngineBridge} or thread-safe engine reads. Chunk state reads are safe
 * because published chunks are immutable at rest in Slice #1.</p>
 */
public final class V18Connection extends SimpleChannelInboundHandler<ByteBuf>
        implements net.zamin.engine.net.ClientLink {

    private static final Logger LOGGER = Logger.getLogger(V18Connection.class.getName());

    /** Explicit protocol states; each defines the set of valid incoming packets (§514). */
    enum WireState {
        HANDSHAKE,
        STATUS,
        LOGIN,
        PLAY
    }

    private static final long KEEP_ALIVE_TIMEOUT_FACTOR = 3;
    private static final double MAX_COORD_ABS = 3.2E7;
    private static final double MAX_MOVE_DELTA = 100.0;

    private final V18ProtocolServer adapter;
    private final EngineServer engine;

    private volatile WireState state = WireState.HANDSHAKE;
    private volatile PlayerSession session;
    private volatile boolean disconnectSent;

    private final AtomicInteger keepAliveCounter = new AtomicInteger();
    private volatile long pendingKeepAlive = -1;
    private volatile long keepAliveSentAt;

    private ChunkTracker chunkTracker;

    V18Connection(V18ProtocolServer adapter, EngineServer engine) {
        this.adapter = adapter;
        this.engine = engine;
    }

    // ------------------------------------------------------------------ pipeline

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        PlayerSession current = session;
        if (current != null) {
            session = null;
            engine.clientDisconnected(current, "connection closed");
        }
        adapter.forget(this);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // Malformed or broken input fails this connection predictably, never the server (§126).
        LOGGER.log(Level.FINE, "Connection error from " + ctx.channel().remoteAddress(), cause);
        if (session != null) {
            PlayerSession current = session;
            session = null;
            engine.clientDisconnected(current, "protocol error");
        }
        ctx.close();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf packet) {
        switch (state) {
            case HANDSHAKE -> handleHandshake(ctx.channel(), packet);
            case STATUS -> handleStatus(ctx.channel(), packet);
            case LOGIN -> handleLogin(ctx.channel(), packet);
            case PLAY -> handlePlay(ctx.channel(), packet);
        }
    }

    // ------------------------------------------------------------------ handshake/status

    private void handleHandshake(Channel channel, ByteBuf packet) {
        int packetId = ByteBufOps.readVarInt(packet);
        if (packetId != Protocol18.C2S_HANDSHAKE) {
            channel.close();
            return;
        }
        int protocolVersion = ByteBufOps.readVarInt(packet);
        ByteBufOps.readString(packet, 255);          // server host, unused beyond validation
        packet.readUnsignedShort();                  // port, unused (we know our bind address)
        int nextState = ByteBufOps.readVarInt(packet);

        switch (nextState) {
            case Protocol18.NEXT_STATE_STATUS -> {
                state = WireState.STATUS;
            }
            case Protocol18.NEXT_STATE_LOGIN -> {
                if (protocolVersion != Protocol18.PROTOCOL_VERSION) {
                    sendLoginDisconnect(channel, "Outdated client! Please use "
                            + Protocol18.VERSION_NAME + " (protocol " + Protocol18.PROTOCOL_VERSION + ")");
                    return;
                }
                state = WireState.LOGIN;
            }
            default -> channel.close(); // unknown next state: not a client we talk to
        }
    }

    private void handleStatus(Channel channel, ByteBuf packet) {
        int packetId = ByteBufOps.readVarInt(packet);
        switch (packetId) {
            case Protocol18.C2S_STATUS_REQUEST -> sendStatusResponse(channel);
            case Protocol18.C2S_STATUS_PING -> {
                long payload = packet.readLong();
                ByteBuf pong = Unpooled.buffer(9);
                ByteBufOps.writeVarInt(pong, Protocol18.S2C_STATUS_PONG);
                pong.writeLong(payload);
                channel.writeAndFlush(pong).addListener(ChannelFutureListener.CLOSE);
            }
            default -> channel.close(); // invalid packet for STATUS state
        }
    }

    private void sendStatusResponse(Channel channel) {
        ByteBuf response = Unpooled.buffer(256);
        ByteBufOps.writeVarInt(response, Protocol18.S2C_STATUS_RESPONSE);
        ByteBufOps.writeString(response, StatusResponse.build(engine, engine.players().size()));
        channel.writeAndFlush(response);
    }

    private void sendLoginDisconnect(Channel channel, String reason) {
        ByteBuf out = Unpooled.buffer(64);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_LOGIN_DISCONNECT);
        ByteBufOps.writeString(out, StatusResponse.disconnectReason(reason));
        channel.writeAndFlush(out).addListener(ChannelFutureListener.CLOSE);
    }

    // ------------------------------------------------------------------ login

    private void handleLogin(Channel channel, ByteBuf packet) {
        int packetId = ByteBufOps.readVarInt(packet);
        if (packetId != Protocol18.C2S_LOGIN_START) {
            channel.close(); // only login start is valid here
            return;
        }
        String username;
        try {
            username = ByteBufOps.readString(packet, 16);
        } catch (IllegalArgumentException e) {
            sendLoginDisconnect(channel, "Invalid username");
            return;
        }
        // Offline-mode identity: the vanilla offline player UUID convention.
        UUID offlineUuid = UUID.nameUUIDFromBytes(
                ("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));

        EngineBridge.JoinResult result = engine.joinRequest(this, username, offlineUuid);
        if (result instanceof EngineBridge.Rejected rejected) {
            sendLoginDisconnect(channel, rejected.reason());
            return;
        }
        EngineBridge.Accepted accepted = (EngineBridge.Accepted) result;
        this.session = accepted.session();

        ByteBuf success = Unpooled.buffer(96);
        ByteBufOps.writeVarInt(success, Protocol18.S2C_LOGIN_SUCCESS);
        ByteBufOps.writeString(success, offlineUuid.toString());
        ByteBufOps.writeString(success, username);
        channel.writeAndFlush(success).addListener(future -> {
            if (future.isSuccess()) {
                state = WireState.PLAY;
                // Join sequence runs on this event loop; ordering below is meaningful:
                // identity -> world anchor -> time -> chunks -> authoritative position.
                sendJoinGame(channel, accepted.session());
                sendSpawnPosition(channel);
                sendTimeUpdate(channel);
                chunkTracker = new ChunkTracker(engine, channel);
                chunkTracker.sendInitial(engine.world().spawnPosition().toBlockPosition().chunkPosition());
                sendInitialPositionAndLook(channel);
                engine.joinCompleted(accepted.session());
                startKeepAlive(channel);
            }
        });
    }

    private void sendJoinGame(Channel channel, PlayerSession playerSession) {
        int entityId = adapter.nextEntityId();
        ByteBuf out = Unpooled.buffer(32);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_JOIN_GAME);
        out.writeInt(entityId);
        out.writeByte(Protocol18.GAMEMODE_CREATIVE);
        out.writeByte(0);                 // dimension: overworld
        out.writeByte(0);                 // difficulty: peaceful (no mob expectations yet)
        out.writeByte(0);                 // max players (legacy field, unused by client)
        ByteBufOps.writeString(out, Protocol18.LEVEL_TYPE_FLAT);
        out.writeBoolean(false);          // reduced debug info
        channel.writeAndFlush(out);
        LOGGER.fine(() -> "Join game sent to " + playerSession.name() + " (entity " + entityId + ")");
    }

    private void sendSpawnPosition(Channel channel) {
        Position spawn = engine.world().spawnPosition();
        ByteBuf out = Unpooled.buffer(16);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_SPAWN_POSITION);
        ByteBufOps.writePackedBlockPosition(out,
                spawn.toBlockPosition().x(),
                spawn.toBlockPosition().y(),
                spawn.toBlockPosition().z());
        channel.writeAndFlush(out);
    }

    private void sendTimeUpdate(Channel channel) {
        ByteBuf out = Unpooled.buffer(20);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_TIME_UPDATE);
        out.writeLong(engine.world().totalTicks());
        out.writeLong(engine.world().timeOfDay());
        channel.writeAndFlush(out);
    }

    private void sendInitialPositionAndLook(Channel channel) {
        PlayerSession player = session;
        if (player == null) {
            return;
        }
        sendPositionAndLook(channel, player.position(), player.rotation(), true);
    }

    private void sendPositionAndLook(Channel channel, Position position, Rotation rotation, boolean onGround) {
        ByteBuf out = Unpooled.buffer(56);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_PLAYER_POSITION_AND_LOOK);
        out.writeDouble(position.x());
        out.writeDouble(position.y());
        out.writeDouble(position.z());
        out.writeFloat(rotation.yaw());
        out.writeFloat(rotation.pitch());
        out.writeBoolean(onGround);
        channel.writeAndFlush(out);
    }

    // ------------------------------------------------------------------ play

    private void handlePlay(Channel channel, ByteBuf packet) {
        PlayerSession player = session;
        if (player == null) {
            channel.close(); // PLAY state without a session is inconsistent; drop it
            return;
        }
        int packetId = ByteBufOps.readVarInt(packet);
        switch (packetId) {
            case Protocol18.C2S_KEEP_ALIVE -> handleKeepAlive(packet);
            case Protocol18.C2S_PLAYER_POSITION -> applyPosition(channel, player, packet);
            case Protocol18.C2S_PLAYER_LOOK -> applyLook(player, packet);
            case Protocol18.C2S_PLAYER_POSITION_AND_LOOK -> applyPositionAndLook(channel, player, packet);
            case Protocol18.C2S_PLAYER -> packet.readBoolean(); // ground state only; no position change
            case Protocol18.C2S_CHAT_MESSAGE -> {
                String message = ByteBufOps.readString(packet, 256);
                LOGGER.info(() -> "Chat (no broadcast in slice 1) from " + player.name() + ": " + message);
            }
            case Protocol18.C2S_CLIENT_SETTINGS, Protocol18.C2S_HELD_ITEM_CHANGE,
                 Protocol18.C2S_CLIENT_STATUS -> {
                // Accepted and ignored: they carry no slice-1 gameplay semantics yet.
            }
            case Protocol18.C2S_PLAYER_DIGGING, Protocol18.C2S_PLAYER_BLOCK_PLACEMENT -> {
                // Slice #2: interaction intents will route through the engine, not here.
            }
            default -> {
                // Unknown play packet: ignore, as the historical server does, but make it observable.
                LOGGER.fine(() -> "Ignored play packet id 0x" + Integer.toHexString(packetId)
                        + " from " + player.name());
            }
        }
    }

    private void handleKeepAlive(ByteBuf packet) {
        int id = ByteBufOps.readVarInt(packet);
        if (pendingKeepAlive == id) {
            pendingKeepAlive = -1;
        }
    }

    private void applyPosition(Channel channel, PlayerSession player, ByteBuf packet) {
        double x = packet.readDouble();
        double y = packet.readDouble();
        double z = packet.readDouble();
        boolean onGround = packet.readBoolean();
        submitMovement(channel, player, new Position(x, y, z), player.rotation(), onGround);
    }

    private void applyLook(PlayerSession player, ByteBuf packet) {
        float yaw = packet.readFloat();
        float pitch = packet.readFloat();
        boolean onGround = packet.readBoolean();
        engine.movementProposal(player, player.position(), new Rotation(yaw, pitch), onGround);
    }

    private void applyPositionAndLook(Channel channel, PlayerSession player, ByteBuf packet) {
        double x = packet.readDouble();
        double y = packet.readDouble();
        double z = packet.readDouble();
        float yaw = packet.readFloat();
        float pitch = packet.readFloat();
        boolean onGround = packet.readBoolean();
        submitMovement(channel, player, new Position(x, y, z), new Rotation(yaw, pitch), onGround);
    }

    private void submitMovement(Channel channel, PlayerSession player,
                                Position position, Rotation rotation, boolean onGround) {
        if (!isSaneMovement(player, position, rotation)) {
            // Rejected safely: state unchanged, no disconnect (matches lenient historical behavior).
            LOGGER.fine(() -> "Rejected movement proposal from " + player.name());
            return;
        }
        engine.movementProposal(player, position, rotation, onGround);
        ChunkPosition newChunk = position.toBlockPosition().chunkPosition();
        if (chunkTracker != null) {
            chunkTracker.updateCenter(newChunk);
        }
    }

    private boolean isSaneMovement(PlayerSession player, Position position, Rotation rotation) {
        if (!position.isFinite() || !rotation.isFinite()) {
            return false;
        }
        if (Math.abs(position.x()) > MAX_COORD_ABS || Math.abs(position.z()) > MAX_COORD_ABS
                || position.y() < -64 || position.y() > 1024) {
            return false;
        }
        Position current = player.position();
        if (position.distanceSquared(current) > MAX_MOVE_DELTA * MAX_MOVE_DELTA) {
            return false; // teleport-sized jumps are not movement; slice 1 rejects silently
        }
        return true;
    }

    // ------------------------------------------------------------------ keep alive loop

    /** Starts the keep-alive cycle on the channel's event loop. Called once after join. */
    void startKeepAlive(Channel channel) {
        final long interval = adapter.keepAliveIntervalMs();
        channel.eventLoop().scheduleAtFixedRate(() -> {
            if (!channel.isActive()) {
                return;
            }
            long pending = pendingKeepAlive;
            long timeout = adapter.keepAliveIntervalMs() * KEEP_ALIVE_TIMEOUT_FACTOR;
            if (pending != -1 && System.currentTimeMillis() - keepAliveSentAt > timeout) {
                kick("Timed out");
                return;
            }
            if (pending == -1) {
                sendKeepAlive(channel);
            }
        }, interval, Math.max(50, interval / 4), TimeUnit.MILLISECONDS);
    }

    private void sendKeepAlive(Channel channel) {
        int id = keepAliveCounter.incrementAndGet();
        pendingKeepAlive = id;
        keepAliveSentAt = System.currentTimeMillis();
        ByteBuf out = Unpooled.buffer(8);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_KEEP_ALIVE);
        ByteBufOps.writeVarInt(out, id);
        channel.writeAndFlush(out);
    }

    // ------------------------------------------------------------------ ClientLink

    @Override
    public boolean isActive() {
        Channel channel = adapter.channelOf(this);
        return channel != null && channel.isActive();
    }

    @Override
    public void kick(String reason) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || disconnectSent) {
            return;
        }
        disconnectSent = true;
        if (state == WireState.PLAY) {
            ByteBuf out = Unpooled.buffer(64);
            ByteBufOps.writeVarInt(out, Protocol18.S2C_DISCONNECT);
            ByteBufOps.writeString(out, StatusResponse.disconnectReason(reason));
            channel.writeAndFlush(out).addListener(ChannelFutureListener.CLOSE);
        } else {
            sendLoginDisconnect(channel, reason);
        }
    }

    // ------------------------------------------------------------------ chunk tracking

    /**
     * Per-connection visible-chunk bookkeeping. The world owns chunk state; this
     * tracker only decides what this client needs to receive or unload (§230).
     */
    private static final class ChunkTracker {

        private final EngineServer engine;
        private final Channel channel;
        private final java.util.Set<Long> sent = new java.util.HashSet<>();

        private int centerX;
        private int centerZ;

        ChunkTracker(EngineServer engine, Channel channel) {
            this.engine = engine;
            this.channel = channel;
        }

        void sendInitial(ChunkPosition center) {
            centerX = center.x();
            centerZ = center.z();
            forEachInRadius(engine.config().viewDistance(), position -> {
                sent.add(position.packed());
                sendChunk(position, true);
            });
        }

        void updateCenter(ChunkPosition center) {
            if (center.x() == centerX && center.z() == centerZ) {
                return;
            }
            centerX = center.x();
            centerZ = center.z();
            int view = engine.config().viewDistance();

            // Unload first: chunks that fell out of range (with one chunk of hysteresis).
            sent.removeIf(packed -> {
                ChunkPosition position = ChunkPosition.unpack(packed);
                if (position.distanceSquared(center) > (long) (view + 1) * (view + 1)) {
                    sendUnload(position);
                    return true;
                }
                return false;
            });

            // Send chunks that are newly visible.
            forEachInRadius(view, position -> {
                if (sent.add(position.packed())) {
                    sendChunk(position, false);
                }
            });
        }

        private void forEachInRadius(int radius, java.util.function.Consumer<ChunkPosition> action) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    action.accept(new ChunkPosition(centerX + dx, centerZ + dz));
                }
            }
        }

        private void sendChunk(ChunkPosition position, boolean groundUp) {
            if (!channel.isActive()) {
                return;
            }
            EngineChunk chunk = engine.world().peek(position);
            if (chunk == null) {
                // Not yet loaded: generate on the world's owner thread, then serialize.
                engine.requestChunkLoad(position, loaded -> {
                    if (channel.isActive()) {
                        writeChunk(loaded, groundUp);
                    }
                });
                return;
            }
            writeChunk(chunk, groundUp);
        }

        private void writeChunk(EngineChunk chunk, boolean groundUp) {
            byte[] data = ChunkSerializer18.serialize(chunk, groundUp);
            ByteBuf out = Unpooled.buffer(16 + data.length);
            ByteBufOps.writeVarInt(out, Protocol18.S2C_CHUNK_DATA);
            out.writeInt(chunk.position().x());
            out.writeInt(chunk.position().z());
            out.writeBoolean(groundUp);
            out.writeShort(ChunkSerializer18.sectionBitmask(chunk));
            ByteBufOps.writeVarInt(out, data.length);
            out.writeBytes(data);
            channel.writeAndFlush(out);
        }

        private void sendUnload(ChunkPosition position) {
            if (!channel.isActive()) {
                return;
            }
            ByteBuf out = Unpooled.buffer(12);
            ByteBufOps.writeVarInt(out, Protocol18.S2C_UNLOAD_CHUNK);
            out.writeInt(position.x());
            out.writeInt(position.z());
            channel.writeAndFlush(out);
        }
    }
}
