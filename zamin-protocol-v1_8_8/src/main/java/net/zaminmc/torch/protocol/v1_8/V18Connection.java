package net.zaminmc.torch.protocol.v1_8;

import net.zaminmc.torch.util.Identifier;
import net.zaminmc.torch.item.ItemStack;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.auth.GameProfile;
import net.zaminmc.torch.GameMode;
import net.zaminmc.torch.server.entity.MobEntity;
import net.zaminmc.torch.server.net.EngineBridge;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.world.EngineChunk;
import net.zaminmc.torch.server.world.EngineWorld;

import net.zaminmc.torch.block.BlockPosition;
import net.zaminmc.torch.block.BlockType;
import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.util.Position;
import net.zaminmc.torch.util.Rotation;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

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
        implements net.zaminmc.torch.server.net.ClientLink {

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

    /**
     * The connection's outbound stage (§35 — the common transport): the
     * per-connection queue, the batched drain, the flush policy. Created
     * lazily over the registered channel — the registration happens-before
     * any play-state send (the connection enters the adapter's map inside
     * initChannel), and the headless harnesses that never register a
     * channel never reach a send's write (their guards return first).
     */
    private volatile net.zaminmc.torch.transport.OutboundQueue outbound;

    /** The queue's lazy face: one queue per connection, over the registered channel. */
    private net.zaminmc.torch.transport.OutboundQueue outbound() {
        net.zaminmc.torch.transport.OutboundQueue existing = this.outbound;
        if (existing != null) {
            return existing;
        }
        Channel channel = adapter.channelOf(this);
        if (channel == null) {
            return null;
        }
        synchronized (this) {
            if (this.outbound == null) {
                this.outbound = new net.zaminmc.torch.transport.OutboundQueue(channel);
            }
            return this.outbound;
        }
    }

    /**
     * The ordinary send (§35.5): the packet joins the connection's outbound
     * queue — batched write, coalesced flush. Ownership transfers; with no
     * channel attached the buffer releases (the drop visible in the
     * transport's counters, never a leak — §35.13).
     */
    private void enqueueOut(ByteBuf out) {
        net.zaminmc.torch.transport.OutboundQueue queue = outbound();
        if (queue != null) {
            queue.enqueue(out);
        } else {
            out.release();
        }
    }

    /** The urgent send: the packet joins the tail and the drain executes at once (one flush). */
    private void enqueueOutUrgent(ByteBuf out) {
        net.zaminmc.torch.transport.OutboundQueue queue = outbound();
        if (queue != null) {
            queue.enqueueUrgent(out);
        } else {
            out.release();
        }
    }

    /**
     * One share of an encode-once payload joins this connection's queue
     * (§35.3): the ticket is an index-independent view — this pipeline's
     * read-consume cannot move another recipient's read position. An
     * unattachable connection releases its share.
     */
    void enqueueFxShare(net.zaminmc.torch.transport.EncodedPayload payload) {
        ByteBuf ticket = payload.share();
        net.zaminmc.torch.transport.OutboundQueue queue = outbound();
        if (queue != null) {
            queue.enqueue(ticket);
        } else {
            ticket.release();
        }
    }

    private volatile long pendingKeepAlive = -1;
    private volatile long keepAliveSentAt;

    private ChunkTracker chunkTracker;
    /**
     * The connection's current dimension (0 the overworld, -1 the nether):
     * every chunk read and the Respawn packet's field ride it — the
     * changeDimension walk re-points it at the arrival.
     */
    private volatile int dimension;
    /** @return the dimension this connection stands in (the Time Update filter reads it). */
    public int dimensionOfSession() {
        return dimension;
    }
    /** This client's own wire entity id (allocated at Join Game). */
    private volatile int ownEntityId = -1;
    /** The position this client was last teleported to (respawn): movement near it skips the distance sanity check. */
    private volatile net.zaminmc.torch.util.Position lastTeleportAnchor;
    // Remote player entity ids as seen by THIS client (observer-local id space).
    private final java.util.Map<UUID, Integer> remoteEntityIds = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Set<UUID> visibleRemotePlayers = java.util.Collections.synchronizedSet(new java.util.HashSet<>());
    // Furnace-window sync state: the serials last seen by this client, so the
    // per-tick view only sends what actually moved (one open container at a time).
    private int furnaceSlotsSynced = -1;
    private int furnacePropsSynced = -1;
    private final int[] furnacePropsSent = new int[Protocol18.FURNACE_PROP_COUNT];
    // Enchanting-window sync state (same shape as the furnace's): the menu
    // revision last seen (slot resync) and the seven property values last sent.
    private long enchantingRevisionSynced = -1;
    private final int[] enchantingPropsSent = new int[Protocol18.ENCHANTING_PROP_COUNT];
    // Anvil-window sync state (the same shape): the menu revision last seen
    // and the single repair-cost property last sent.
    private long anvilRevisionSynced = -1;
    private int anvilCostSent = Integer.MIN_VALUE;
    // Brewing-window sync state (Slice 11e): the slots serial last seen
    // and the brew timer last sent (the window's property 0).
    private int brewingSlotsSynced = -1;
    private int brewingTimerSent = -1;
    /** The beacon window's sync state (Slice 29b): the payment slot + the three properties. */
    private int beaconSlotsSynced = -1;
    private int beaconPropsSynced = -1;
    private final int[] beaconPropsSent = new int[3];
    // The §27 window family's re-sync state (the chest/hopper/dispenser
    // families): the container's slotsSerial last seen by this client, so
    // the per-tick view fan only re-syncs the Window Items when the
    // contents actually moved (a plugin's setSlot, the hopper system's
    // transfer, the world side's own writes — one open window at a time,
    // same shape as the furnace's).
    private int chestSlotsSynced = -1;
    private int hopperSlotsSynced = -1;
    private int dispenserSlotsSynced = -1;

    V18Connection(V18ProtocolServer adapter, EngineServer engine) {
        this.adapter = adapter;
        this.engine = engine;
        // The echo-verification nonce (the reference ServerLoginNetwork
        // //Handler's own ctor law: 4 bytes off a shared Random, once per
        // connection — the KeyC2S reply must return it RSA-encrypted).
        LOGIN_NONCE_RANDOM.nextBytes(loginNonce);
    }

    /**
     * The login walk's stage machine (the reference's own Stage enum,
     * ServerLoginNetworkHandler lines 204-211): HELLO (Login Start
     * pending) -> KEY (the Encryption Request out, the response pending)
     * -> AUTHENTICATING (the hasJoined consult in flight) -> READY (the
     * accept walk may run). The offline branch steps HELLO -> READY
     * directly — the reference handleHello's else-arm, line 134.
     */
    private enum LoginStage { HELLO, KEY, AUTHENTICATING, READY }

    private volatile LoginStage loginStage = LoginStage.HELLO;

    /** The reference's own nonce source: one shared Random, four bytes per connection. */
    private static final java.util.Random LOGIN_NONCE_RANDOM = new java.util.Random();

    /** The reference's own authenticator naming: "User Authenticator #N". */
    private static final java.util.concurrent.atomic.AtomicInteger AUTHENTICATOR_IDS =
            new java.util.concurrent.atomic.AtomicInteger();

    /** The online login's echo nonce (RSA-encrypted back by the client). */
    private final byte[] loginNonce = new byte[4];

    /** The joining name, carried across the async authentication arm. */
    private volatile String loginName;

    /**
     * The chunk-visibility key for an entity position: the chunk column from
     * x/z alone (floor division) — a body below y=0 (the reference's own
     * Entity.tick void window, the -64 kill plane of {@code voidTick}) never
     * bounds-crashes the lookup, and the y coordinate never changes which
     * 16x256x16 column a position belongs to.
     */
    static long chunkKeyOf(net.zaminmc.torch.util.Position at) {
        return net.zaminmc.torch.block.ChunkPosition.ofBlock(
                (int) Math.floor(at.x()), (int) Math.floor(at.z())).packed();
    }

    // ------------------------------------------------------------------ pipeline

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        // §35.13 — the dead channel's queue releases every queued ticket:
        // nothing retained survives the connection. The error-path shape
        // (cancel, not drain — the channel no longer accepts writes).
        net.zaminmc.torch.transport.OutboundQueue queue = this.outbound;
        if (queue != null) {
            queue.closeNow();
        }
        PlayerSession current = session;
        UUID departedUuid = null;
        if (current != null) {
            departedUuid = current.uuid();
            session = null;
            engine.clientDisconnected(current, "connection closed");
        }
        adapter.forget(this);
        adapter.playerLeft(departedUuid);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // ML-009b (the audit's B-4 row): the unclean-exit family — an abrupt
        // client TCP reset, a broken pipe, an EOF mid-read — arrives as the
        // IOException family, and the reference answers those with ONE quiet
        // disconnect line, never a stacktrace (Connection.exceptionCaught
        // lines 110-119: the "Internal Exception: <cause>" reason handed to
        // the walk; ServerPlayNetworkHandler.onDisconnect line 573 logs the
        // INFO line). With a session, that one line is the engine's own
        // "Player disconnected" record (the reason carries the cause — never
        // swallowed, just unstacked); without one (a status/login-phase
        // reset) the connection logs it itself. Genuinely unexpected
        // failures stay loud (§126/§54): WARNING + the throwable for the
        // frame that broke the connection — an invisible drop is
        // undiagnosable.
        // The read-idle reap (the reference's own "timeout" handler,
        // ConnectionListener.java line 104): a connection silent past the
        // bound is closed cleanly. This is the boundary mechanism WORKING,
        // not a protocol fault — a flood of reaped in-progress logins must
        // never spray WARNING stacks into the console (the quiet-disconnect
        // law the keep-alive timeout already follows). PLAY-state clients
        // are normally kicked by keep-alive long before this arm; this
        // arm's population is the pre-login silence.
        if (cause instanceof io.netty.handler.timeout.ReadTimeoutException) {
            if (session != null) {
                engine.clientDisconnected(session, "Timed out");
            }
            // The session field STAYS: channelInactive's walk resolves the
            // real departed uuid and runs the adapter's observer cleanup
            // (forget + playerLeft) — the reference's onDisconnect shape.
            // The engine's own walk is state-gated idempotent, so the
            // second clientDisconnected lands as a no-op.
            ctx.close();
            return;
        }

        if (cause instanceof java.io.IOException) {
            if (session != null) {
                // The session field STAYS: the close's channelInactive walk
                // resolves the REAL departed uuid and runs the adapter's
                // observer cleanup (forget + playerLeft) — the reference's
                // own onDisconnect always walks the observers
                // (PlayerManager.remove), and the engine's clientDisconnected
                // is state-gated idempotent, so the second call lands as a
                // no-op with the reason already logged once. A write that
                // lost the race against a closing socket must still leave
                // the OTHER observers cleaned.
                engine.clientDisconnected(session, "Internal Exception: " + cause);
                ctx.close();
                return;
            }
            LOGGER.info(() -> "Connection lost from " + ctx.channel().remoteAddress()
                    + ": " + cause);
            ctx.close();
            return;
        }
        LOGGER.log(Level.WARNING, "Protocol error from " + ctx.channel().remoteAddress()
                + (session != null ? " (" + session.name() + ")" : ""), cause);
        if (session != null) {
            // The session field STAYS (the same law as the other arms):
            // channelInactive's walk resolves the real departed uuid and
            // runs the adapter's observer cleanup.
            engine.clientDisconnected(session, "protocol error");
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
        if (packetId == Protocol18.C2S_LOGIN_ENCRYPTION_RESPONSE) {
            handleEncryptionResponse(channel, packet);
            return;
        }
        if (packetId != Protocol18.C2S_LOGIN_START) {
            channel.close(); // only login start (and, in the KEY stage, the
            // encryption response) is valid here
            return;
        }
        String username;
        try {
            username = ByteBufOps.readString(packet, 16);
        } catch (IllegalArgumentException e) {
            sendLoginDisconnect(channel, "Invalid username");
            return;
        }
        // Offline-mode identity: the vanilla offline player UUID convention
        // (ServerLoginNetworkHandler.createFakeProfile line 200's own law).
        UUID offlineUuid = UUID.nameUUIDFromBytes(
                ("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
        if (engine.onlineMode()) {
            // The reference handleHello's online arm (lines 130-132): the
            // stage steps to KEY and the Encryption Request rides out —
            // the identity itself is NOT resolved yet (the session service
            // owns it; the offline convention here is only the pre-auth
            // placeholder the reference's packet profile carries).
            loginStage = LoginStage.KEY;
            loginName = username;
            sendEncryptionRequest(channel);
            return;
        }
        loginStage = LoginStage.READY;
        acceptLogin(channel, GameProfile.offline(username, offlineUuid));
    }

    /**
     * The Encryption Request (the reference HelloS2CPacket's wire form,
     * handleHello line 132): the server-id string (the reference's own
     * empty key — 1.8.8's famous "" server id), the DER-encoded public
     * key as a byte array, and the 4-byte nonce as a byte array.
     */
    private void sendEncryptionRequest(Channel channel) {
        java.security.KeyPair keyPair = adapter.loginKeyPair();
        ByteBuf request = Unpooled.buffer(420);
        ByteBufOps.writeVarInt(request, Protocol18.S2C_LOGIN_ENCRYPTION_REQUEST);
        ByteBufOps.writeString(request, ""); // the reference's own empty server id
        ByteBufOps.writeByteArray(request, keyPair.getPublic().getEncoded());
        ByteBufOps.writeByteArray(request, loginNonce);
        channel.writeAndFlush(request);
    }

    /**
     * The Encryption Response walk (the reference handleKey, lines
     * 139-197): the stage gate (only KEY answers — the reference's
     * Validate.validState throw, which kills the connection as an
     * internal error), the RSA decrypt of the shared secret and the echo
     * nonce, the nonce comparison (the reference's IllegalStateException
     * "Invalid nonce!" — an internal-error close), the stream codec's
     * install (decrypt before the framing decoder, encrypt before the
     * framing encoder — Connection.setupEncryption lines 295-299), and
     * the authenticator thread ("User Authenticator #N", the reference's
     * own naming) that carries the hasJoined consult and hops the accept
     * walk back onto the event loop. Never blocks the IO thread on the
     * network — the reference's own shape.
     */
    private void handleEncryptionResponse(Channel channel, ByteBuf packet) {
        if (loginStage != LoginStage.KEY) {
            LOGGER.warning("Protocol error from " + channel.remoteAddress()
                    + ": encryption response outside the KEY stage");
            channel.close();
            return;
        }
        byte[] encryptedSecret;
        byte[] encryptedNonce;
        try {
            encryptedSecret = ByteBufOps.readByteArray(packet);
            encryptedNonce = ByteBufOps.readByteArray(packet);
        } catch (IllegalArgumentException e) {
            channel.close(); // a malformed response is not a login answer
            return;
        }
        java.security.KeyPair keyPair = adapter.loginKeyPair();
        byte[] secretBytes = TorchEncryption.decrypt(keyPair.getPrivate(), encryptedSecret);
        byte[] echoedNonce = TorchEncryption.decrypt(keyPair.getPrivate(), encryptedNonce);
        if (!TorchEncryption.nonceMatches(loginNonce, echoedNonce)) {
            // The reference's own law: the IllegalStateException reaches
            // the connection's exception walk — an internal-error close.
            LOGGER.warning("Protocol error from " + channel.remoteAddress()
                    + ": Invalid nonce!");
            channel.close();
            return;
        }
        javax.crypto.SecretKey secretKey = TorchEncryption.secretKey(secretBytes);
        loginStage = LoginStage.AUTHENTICATING;
        installEncryption(channel, secretKey);

        String name = loginName;
        Thread authenticator = new Thread(() -> {
            try {
                String serverIdHash = TorchEncryption.serverIdHash(
                        "", keyPair.getPublic(), secretKey);
                net.zaminmc.torch.server.auth.GameProfile profile =
                        adapter.sessionService().hasJoined(name, serverIdHash);
                if (profile == null) {
                    // The reference's own string (line 180).
                    channel.eventLoop().execute(() ->
                            sendLoginDisconnect(channel, "Failed to verify username!"));
                    return;
                }
                net.zaminmc.torch.server.auth.GameProfile authorized = profile;
                channel.eventLoop().execute(() -> {
                    if (loginStage != LoginStage.AUTHENTICATING) {
                        return; // the connection moved on (a reap won the race)
                    }
                    loginStage = LoginStage.READY;
                    acceptLogin(channel, authorized);
                });
            } catch (net.zaminmc.torch.server.auth.AuthenticationUnavailableException e) {
                // The reference's own string (line 190).
                channel.eventLoop().execute(() ->
                        sendLoginDisconnect(channel,
                                "Authentication servers are down. Please try again later, sorry!"));
            }
        }, "User Authenticator #" + AUTHENTICATOR_IDS.incrementAndGet());
        authenticator.setDaemon(true); // a wedged consult never blocks JVM shutdown
        authenticator.start();
    }

    /**
     * The stream codec's install (Connection.setupEncryption lines
     * 295-299: decrypt before the splitter, encrypt before the
     * prepender — mapped onto Torch's frame codec names). The
     * idempotence guards mirror the compression install's own law.
     */
    private void installEncryption(Channel channel, javax.crypto.SecretKey secretKey) {
        if (channel.pipeline().get(EncryptionCodec.DECRYPT_NAME) == null) {
            channel.pipeline().addBefore(FrameCodec.DECODER_NAME, EncryptionCodec.DECRYPT_NAME,
                    new EncryptionCodec.Decoder(TorchEncryption.streamCipher(2, secretKey)));
        }
        if (channel.pipeline().get(EncryptionCodec.ENCRYPT_NAME) == null) {
            channel.pipeline().addBefore(FrameCodec.ENCODER_NAME, EncryptionCodec.ENCRYPT_NAME,
                    new EncryptionCodec.Encoder(TorchEncryption.streamCipher(1, secretKey)));
        }
    }

    /**
     * The reference's acceptLogin walk (ServerLoginNetworkHandler lines
     * 81-113): the engine's join consult (the canLogin gates — bans,
     * whitelist, the player ceiling), then — on acceptance — the
     * compression exchange BEFORE Login Success, then the success itself
     * with the join burst in its completion. The ON-1a lane added the
     * exchange arm; the online-mode branch (the encryption pair, the
     * hasJoined consult) rides ON-1b's stage machine in front of this
     * walk.
     */
    private void acceptLogin(Channel channel, GameProfile profile) {
        EngineBridge.JoinResult result = engine.joinRequest(this, profile.name(),
                profile.id(), profile.properties());
        if (result instanceof EngineBridge.Rejected rejected) {
            sendLoginDisconnect(channel, rejected.reason());
            return;
        }
        EngineBridge.Accepted accepted = (EngineBridge.Accepted) result;
        this.session = accepted.session();

        // The compression exchange (the reference acceptLogin lines 91-101):
        // the Set Compression packet rides BEFORE Login Success whenever the
        // configured threshold is >= 0, and the codec installs only once the
        // exchange frame is out — the 0x03 itself rides uncompressed, every
        // later frame (Login Success included) carries the data-length
        // varint. The order is structural, not listener-timing luck: on the
        // event loop the write's pipeline walk completes inline before the
        // codec lands; off it (never in practice — the login walk is inbound
        // driven), the install task queues behind the queued write.
        int threshold = engine.networkCompressionThreshold();
        if (threshold >= 0) {
            ByteBuf exchange = Unpooled.buffer(8);
            ByteBufOps.writeVarInt(exchange, Protocol18.S2C_LOGIN_SET_COMPRESSION);
            ByteBufOps.writeVarInt(exchange, threshold);
            Runnable afterExchange = () -> {
                installCompression(channel, threshold);
                sendLoginSuccess(channel, accepted, profile);
            };
            if (channel.eventLoop().inEventLoop()) {
                channel.writeAndFlush(exchange);
                afterExchange.run();
            } else {
                channel.writeAndFlush(exchange)
                        .addListener(future -> channel.eventLoop().execute(afterExchange));
            }
        } else {
            sendLoginSuccess(channel, accepted, profile);
        }
    }

    /**
     * The codec install (Connection.setCompressionThreshold lines 325-346's
     * add-branches): decompress after the frame decoder, compress after
     * the frame encoder — the outbound walk (tail to head) then visits
     * compress BEFORE the length prefix while the packet hook keeps its
     * plaintext view ahead of it. Idempotent (the reference's own
     * instanceof guard law).
     */
    private void installCompression(Channel channel, int threshold) {
        if (channel.pipeline().get(CompressionCodec.DECODER_NAME) == null) {
            channel.pipeline().addAfter(FrameCodec.DECODER_NAME, CompressionCodec.DECODER_NAME,
                    new CompressionCodec.Decoder(threshold));
        }
        if (channel.pipeline().get(CompressionCodec.ENCODER_NAME) == null) {
            channel.pipeline().addAfter(FrameCodec.ENCODER_NAME, CompressionCodec.ENCODER_NAME,
                    new CompressionCodec.Encoder(threshold));
        }
    }

    private void sendLoginSuccess(Channel channel, EngineBridge.Accepted accepted,
                                   GameProfile profile) {
        ByteBuf success = Unpooled.buffer(96);
        ByteBufOps.writeVarInt(success, Protocol18.S2C_LOGIN_SUCCESS);
        ByteBufOps.writeString(success, profile.id().toString());
        ByteBufOps.writeString(success, profile.name());
        channel.writeAndFlush(success).addListener(future -> {
            if (future.isSuccess()) {
                state = WireState.PLAY;
                // Join sequence runs on this event loop; ordering below is meaningful:
                // identity -> world anchor -> time -> chunks -> authoritative position.
                sendJoinGame(channel, accepted.session());
                // ML-012 — the reference's own join order (PlayerManager
                // .onLogin line 128, right after the Login packet + brand):
                // the Server Difficulty byte follows Join Game, before the
                // spawn point. The wire form is DifficultyS2CPacket's own
                // single-byte write.
                sendServerDifficulty(engine.difficulty());
                sendSpawnPosition(channel);
                sendTimeUpdate(channel);
                chunkTracker = new ChunkTracker(this, engine, channel);
                // Center the initial view on the session's actual position: the
                // world spawn for fresh players, the saved spot for returning ones.
                chunkTracker.sendInitial(ChunkPosition.ofBlock(
                        (int) Math.floor(accepted.session().position().x()),
                        (int) Math.floor(accepted.session().position().z())));
                // The tab-list walk (the reference's PlayerManager.add — the
                // ADD broadcast + the joiner's full-list flush) sits between
                // the pre-position packets and the teleport, exactly where
                // vanilla's initializeConnectionToPlayer places it.
                engine.joinTabList(accepted.session());
                // The scoreboard join sync (PlayerManager.updateScoreboard
                // lines 168-185): every team + every displayed objective —
                // vanilla places it before the yellow join broadcast (which
                // the engine sends during accept), so here it lands right
                // after the tab-list flush, before the teleport.
                engine.joinScoreboard(accepted.session());
                sendInitialPositionAndLook(channel);
                // The world border's join sync (PlayerManager.sendWorldInfo
                // line 679): the full INITIALIZE, after the teleport like
                // the reference's join walk.
                engine.joinWorldBorder(accepted.session());
                // ML-017 — the reference's join tail (PlayerManager.onLogin
                // lines 148-150, right after sendWorldInfo): the configured
                // resource pack reaches the joiner; the engine's own gate
                // sends nothing when no url is configured.
                engine.joinResourcePack(accepted.session());
                engine.joinTabListHeaderFooter(accepted.session());
                sendWindowItems(channel, accepted.session().inventory().snapshot(),
                        accepted.session().crafting().snapshot(),
                        accepted.session().inventory().armorSnapshot(),
                        engine.craftingResult(accepted.session()));
                sendUpdateHealth(accepted.session()); // the body's authoritative baseline
                sendSetExperience(accepted.session()); // the XP bar's baseline (restored or zero)
                accepted.session().link().updateAbilities(
                        abilitiesFlagsOf(accepted.session().gamemode())); // flight/invuln grant
                engine.joinCompleted(accepted.session());
                startKeepAlive(channel);
                adapter.playerEnteredPlay(this);
                // The relog remount arm (the reference's PlayerManager.onLogin
                // "Riding" walk, lines 157-165 — after the whole join burst,
                // exactly where the reference seats the returning rider): the
                // engine consumes the ride remembered at quit and remounts on
                // the tick lane; the mount's own listeners then publish the
                // Attach Entity pair (the reference's startRiding lines
                // 516-523: the attach, then the teleport to the seat).
                engine.rejoinRestoreRide(accepted.session());
            }
        });
    }

    private void sendJoinGame(Channel channel, PlayerSession playerSession) {
        int entityId = adapter.nextEntityId();
        this.ownEntityId = entityId;
        ByteBuf out = Unpooled.buffer(32);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_JOIN_GAME);
        out.writeInt(entityId);
        out.writeByte(playerSession.gamemode().legacyId());
        out.writeByte(0);                 // dimension: overworld
        out.writeByte(engine.difficulty()); // the runtime difficulty (/difficulty)
        out.writeByte(0);                 // max players (legacy field, unused by client)
        ByteBufOps.writeString(out, engine.engineConfig().levelType()); // the configured level-type (client F3 + server list ping)
        out.writeBoolean(false);          // reduced debug info
        enqueueOut(out);
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
        enqueueOut(out);
    }

    private void sendTimeUpdate(Channel channel) {
        // The per-dimension clock (the reference's per-world WorldTimeS2CPacket):
        // a player in the nether hears the nether's own clock — the dimension
        // walk re-pointed the connection at the destination world.
        var playerWorld = engine.worldFor(dimension);
        ByteBuf out = Unpooled.buffer(24);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_TIME_UPDATE);
        out.writeLong(playerWorld.totalTicks());
        out.writeLong(playerWorld.timeOfDay());
        enqueueOut(out);
    }

    /** Re-syncs this client's clock (the periodic cycle and the /time command). Any thread. */
    void sendTimeUpdateNow() {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        sendTimeUpdate(channel);
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
        // Protocol 47's S2C tail is the RELATIVE-FLAGS byte (bit 0 = x is
        // relative, ...), not the C2S packet's onGround boolean. Writing the
        // historical boolean made every teleport anchor X-RELATIVE — a real
        // client applied x twice ("tp 400" landed at 800) and the movement
        // guard then fought the client forever. Absolute anchors: flags 0.
        out.writeByte(0x00);
        enqueueOut(out);
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
                engine.chatService().submitChat(player, message);
            }
            case Protocol18.C2S_CLIENT_SETTINGS -> {
                // 1.8 layout: locale, view distance, chat mode, colors, skin
                // parts — no main-hand field (that is 1.9+). Consumed exactly.
                ByteBufOps.readString(packet, 32);
                packet.readByte();
                ByteBufOps.readVarInt(packet);
                packet.readBoolean();
                packet.readUnsignedByte();
            }
            case Protocol18.C2S_CLIENT_STATUS -> handleClientStatus(player, packet);
            case Protocol18.C2S_HELD_ITEM_CHANGE -> {
                short slot = packet.readShort();
                engine.heldItemChange(player, slot);
            }
            case Protocol18.C2S_WINDOW_CLICK -> handleWindowClick(channel, player, packet);
            case Protocol18.C2S_CLOSE_WINDOW -> handleCloseWindow(player, packet);
            case Protocol18.C2S_PLAYER_DIGGING -> handleDigging(player, packet);
            case Protocol18.C2S_PLAYER_BLOCK_PLACEMENT -> handleBlockPlacement(player, packet);
            case Protocol18.C2S_USE_ENTITY -> handleUseEntity(player, packet);
            case Protocol18.C2S_ARM_ANIMATION -> adapter.broadcastArmSwing(this);
            case Protocol18.C2S_CONFIRM_TRANSACTION -> {
                // The client's echo of our transaction confirm: consumed, never
                // authoritative (the server's own confirm decides prediction).
                packet.readByte();
                packet.readShort();
                packet.readBoolean();
            }
            case Protocol18.C2S_ENTITY_ACTION -> {
                // Sneak/sprint/leave-bed/jump: entity id, action, jump boost.
                ByteBufOps.readVarInt(packet);
                int action = ByteBufOps.readVarInt(packet);
                ByteBufOps.readVarInt(packet);
                // Posture rides to the engine (the flags metadata + sprint
                // hunger gate); the client animates its own body meanwhile.
                PlayerSession current = session;
                if (current != null && adapter.engine() != null) {
                    adapter.engine().entityAction(current, action);
                }
            }
            case Protocol18.C2S_STEER_VEHICLE -> {
                float sideways = packet.readFloat();
                float forward = packet.readFloat();
                int flags = packet.readUnsignedByte();
                engine.steerVehicle(player, sideways, forward,
                        (flags & 0x01) != 0, (flags & 0x02) != 0);
            }
            case Protocol18.C2S_SET_CREATIVE_SLOT -> handleCreativeSlot(player, packet);
            case Protocol18.C2S_ENCHANT_ITEM -> {
                // Enchant Item (0x11, protocol 47: byte window id, byte button
                // 0-2). No action number rides the packet — the vanilla server
                // answers with state syncs, never a Transaction confirm.
                int windowId = packet.readByte();
                int button = packet.readByte();
                engine.enchantItem(player, windowId, button, accepted -> { });
            }
            case Protocol18.C2S_UPDATE_SIGN -> handleUpdateSign(player, packet);
            case Protocol18.C2S_PLAYER_ABILITIES -> {
                int flags = packet.readByte();
                packet.readFloat();
                packet.readFloat();
                if (session != null) {
                    // The flying bit (0x02): creative double-tap and /fly both
                    // announce here. The movement guard trusts this flag only
                    // when the session holds flight rights.
                    session.setFlying((flags & 0x02) != 0);
                }
            }
            case Protocol18.C2S_TAB_COMPLETE -> handleTabComplete(player, packet);
            case Protocol18.C2S_PLUGIN_MESSAGE -> {
                String pluginChannel = ByteBufOps.readString(packet, 64);
                if (Protocol18.PLUGIN_CHANNEL_TRADE_SELECT.equals(pluginChannel)
                        && packet.readableBytes() >= 4) {
                    // MC|TrSel: a single i32 — the selected trade row.
                    int offerIndex = packet.readInt();
                    engine.tradeSelect(player, offerIndex);
                }
                // MC|Beacon (Slice 29b — the BeaconScreen's Done button:
                // two i32s, the primary and the secondary) — the server's
                // own handler (ServerPlayNetworkHandler lines 1062-1080):
                // one payment unit consumes, then the choices land through
                // the reference's effect gate.
                if (Protocol18.PLUGIN_CHANNEL_BEACON.equals(pluginChannel)
                        && packet.readableBytes() >= 8) {
                    int primary = packet.readInt();
                    int secondary = packet.readInt();
                    engine.beaconSelect(player, primary, secondary);
                }
                // MC|ItemName (the reference ServerPlayNetworkHandler lines
                // 1081-1091: the anvil's rename text — the handler validates
                // the strip + the 30-character cap inside the engine's own
                // anvilItemName arm; an absent payload is the blank form).
                if (Protocol18.PLUGIN_CHANNEL_ITEM_NAME.equals(pluginChannel)) {
                    String name = packet.readableBytes() >= 1
                            ? ByteBufOps.readString(packet, 32767) : "";
                    engine.anvilItemName(player, name);
                }
                // MC|BEdit / MC|BSign (VIII12 — the reference
                // ServerPlayNetworkHandler lines 948-1008, the written-book
                // flow): each payload is ONE wire item
                // (PacketByteBuf.readItem — the legacy short id, the count
                // byte, the damage short, then the slot NBT compound; -1
                // answers the empty form, the reference's own null arm).
                // The channel gates (the payload's own isValid consult, the
                // held-item gate) live in the engine's own arms — the same
                // seam the anvil's rename rides.
                if (Protocol18.PLUGIN_CHANNEL_BOOK_EDIT.equals(pluginChannel)
                        && packet.readableBytes() >= 2) {
                    net.zaminmc.torch.item.ItemStack payload = readPluginPayloadItem(packet);
                    if (payload != null) {
                        engine.bookEdit(player, payload);
                    }
                }
                if (Protocol18.PLUGIN_CHANNEL_BOOK_SIGN.equals(pluginChannel)
                        && packet.readableBytes() >= 2) {
                    net.zaminmc.torch.item.ItemStack payload = readPluginPayloadItem(packet);
                    if (payload != null) {
                        engine.bookSign(player, payload);
                    }
                }
                // MC|AdvCdm (Slice 66 — ServerPlayNetworkHandler lines
                // 1016-1057): the command-block family's edit write. The
                // reference's gate order: areCommandBlocksEnabled first
                // (advMode.notEnabled), then the op-2 creative player
                // (advMode.notAllowed), then the payload walk — the
                // minecart arm (type byte 1) and the block arm (type
                // byte 0, Slice 72).
                if ("MC|AdvCdm".equals(pluginChannel) && packet.readableBytes() >= 7) {
                    if (!engine.areCommandBlocksEnabled()) {
                        engine.chatService().sendSystem(player,
                                "Command blocks are not enabled on this server");
                    } else if (player.opLevel() >= 2 && player.gamemode() == GameMode.CREATIVE) {
                        int type = packet.readUnsignedByte();
                        if (type == 1) {
                            int entityId = packet.readInt();
                            String command = ByteBufOps.readString(packet, 256);
                            boolean trackOutput = packet.readBoolean();
                            engine.updateCommandBlockMinecart(player, entityId,
                                    command, trackOutput);
                            engine.chatService().sendSystem(player,
                                    "Command set: " + command);
                        } else if (type == 0 && packet.readableBytes() >= 13) {
                            int x = packet.readInt();
                            int y = packet.readInt();
                            int z = packet.readInt();
                            String command = ByteBufOps.readString(packet, 256);
                            boolean trackOutput = packet.readBoolean();
                            engine.updateCommandBlock(player,
                                    new net.zaminmc.torch.block.BlockPosition(x, y, z),
                                    command, trackOutput);
                            engine.chatService().sendSystem(player,
                                    "Command set: " + command);
                        }
                    } else {
                        engine.chatService().sendSystem(player,
                                "You must be an opped player in creative mode to use command blocks");
                    }
                }
                // other channels: rest of the frame skipped by the reader
                // index (frames are length-delimited, so this is exact).
            }
            case Protocol18.C2S_SPECTATE -> {
                // The spectate hotbar's player pick (the reference's
                // PlayerSpectateC2SPacket — readUuid's two-long form, msb
                // then lsb; handlePlayerSpectate lines 516-551): the two
                // longs resolve to the target player's uuid and the engine
                // arms the camera-follow (the audit's ruling over the
                // reference's one-shot teleport — the packet that used to
                // discard here). The gates (isSpectator, the live-player
                // resolution, the self-target exit) live in the engine's own
                // walk.
                java.util.UUID targetUuid =
                        new java.util.UUID(packet.readLong(), packet.readLong());
                engine.spectateCamera(player, targetUuid);
            }
            case Protocol18.C2S_RESOURCE_PACK_STATUS -> handleResourcePackStatus(player, packet);
            default -> {
                // Unknown play packet: ignore, as the historical server does, but
                // make it observable (rate-limited by keeping it at FINE).
                LOGGER.fine(() -> "Ignored unknown play packet id 0x" + Integer.toHexString(packetId)
                        + " from " + player.name());
            }
        }
    }

    /**
     * Resource Pack Status (0x19, protocol 47 — ResourcePackC2SPacket's own
     * read, ML-017): the hash string (the ctor's own 40-character truncation
     * band) then the result varint — the reference's Response enum order
     * (SUCCESSFULLY_LOADED 0, DECLINED 1, FAILED_DOWNLOAD 2, ACCEPTED 3).
     * The reference's own server handler is empty (ServerPlayNetworkHandler
     * line 568); the engine's owed behavior is the observable log line —
     * the operator learns whether the pack landed. Any thread.
     */
    private void handleResourcePackStatus(PlayerSession player, ByteBuf packet) {
        String hash = ByteBufOps.readString(packet, 40);
        int result = ByteBufOps.readVarInt(packet);
        String verdict = switch (result) {
            case 0 -> "SUCCESSFULLY_LOADED";
            case 1 -> "DECLINED";
            case 2 -> "FAILED_DOWNLOAD";
            case 3 -> "ACCEPTED";
            default -> "UNKNOWN_" + result;
        };
        LOGGER.info(() -> player.name() + " resource pack " + verdict
                + " (hash " + hash + ")");
    }

    private void handleKeepAlive(ByteBuf packet) {
        int id = ByteBufOps.readVarInt(packet);
        if (pendingKeepAlive == id) {
            pendingKeepAlive = -1;
            // The round trip feeds the session's smoothed ping (the
            // reference's handleKeepAlive lines 910-914: the engine's own
            // (ping * 3 + roundTrip) / 4 lives in PlayerSession).
            PlayerSession current = session;
            if (current != null) {
                current.updatePing((int) (System.currentTimeMillis() - keepAliveSentAt));
            }
        }
    }

    /**
     * Use Entity (0x02, community-verified layout: varint target, varint mouse;
     * mouse 2 "interact at" additionally carries f32 x/y/z). Only the attack
     * action has gameplay semantics this slice; the interact variants are
     * consumed and ignored (their bytes are always read so the stream stays
     * aligned — the desync rule the real client taught us).
     */
    private void handleUseEntity(PlayerSession player, ByteBuf packet) {
        int target = ByteBufOps.readVarInt(packet);
        int mouse = ByteBufOps.readVarInt(packet);
        if (mouse == Protocol18.USE_ENTITY_INTERACT_AT) {
            float atX = packet.readFloat();
            float atY = packet.readFloat();
            float atZ = packet.readFloat();
            // INTERACT_AT precedes INTERACT for the same click; the armor
            // stands read the hit's own height (the equipment swap's band
            // walk — Slice 28c); every other entity ignores the offsets
            // and the interact work rides the next packet (the shear
            // path), the desync rule the real client taught us.
            engine.interactAtEntity(player, target, atX, atY, atZ);
            return;
        }
        if (mouse == Protocol18.USE_ENTITY_INTERACT) {
            engine.interactEntity(player, target);
            return;
        }
        if (mouse == Protocol18.USE_ENTITY_ATTACK) {
            // The attacker's target id lives in its own observer id space;
            // resolve a remote PLAYER first (PvP), then fall through to mobs.
            java.util.UUID remoteUuid = remotePlayerUuidOf(target);
            if (remoteUuid != null) {
                PlayerSession victim = engine.playerRegistry().byUuid(remoteUuid).orElse(null);
                if (victim != null) {
                    engine.attackPlayer(player, victim);
                    return;
                }
            }
            engine.attackEntity(player, target);
        }
    }

    /** Reverse lookup of this observer's entity id space (players only). */
    private java.util.UUID remotePlayerUuidOf(int observerEntityId) {
        for (java.util.Map.Entry<java.util.UUID, Integer> entry : remoteEntityIds.entrySet()) {
            if (entry.getValue() == observerEntityId) {
                return entry.getKey();
            }
        }
        return null;
    }

    /** Client Status (0x16): action 0 = perform respawn after dying. */
    private void handleClientStatus(PlayerSession player, ByteBuf packet) {
        int action = ByteBufOps.readVarInt(packet);
        if (action == 0) {
            engine.performRespawn(player);
        } else if (action == 1) {
            // The statistics request (ServerPlayNetworkHandler's
            // REQUEST_STATS arm): the session's dirty counters travel — the
            // client keeps its own running copy and merges the update.
            engine.requestStatistics(player);
        } else if (action == 2) {
            // The open-inventory achievement (ServerPlayNetworkHandler's
            // OPEN_INVENTORY_ACHIEVEMENT arm): the session's own increment,
            // the ride and the toast path following.
            engine.openInventoryAchievement(player);
        }
    }

    /**
     * Update Health (0x06, community-verified layout: f32 health, varint food,
     * f32 saturation). The client's hearts and hunger bar follow this packet.
     * Any thread.
     */
    void sendUpdateHealth(PlayerSession player) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        ByteBuf out = Unpooled.buffer(16);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_UPDATE_HEALTH);
        out.writeFloat(player.health());
        ByteBufOps.writeVarInt(out, player.food());
        out.writeFloat(player.saturation());
        enqueueOut(out);
    }

    /**
     * Entity Effect (0x1D — EntityStatusEffectS2CPacket's own write): varint
     * entity id, byte effect, byte amplifier, varint duration (the 32767
     * permanent cap), byte flags (1 = show particles). The client's HUD
     * timers and its speed/jump attribute modifiers ride it. Any thread.
     */
    void sendEntityEffect(int entityId, int effectId, int amplifier,
                          int duration, boolean particles) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        ByteBuf out = Unpooled.buffer(16);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_ENTITY_EFFECT);
        ByteBufOps.writeVarInt(out, entityId);
        out.writeByte(effectId & 0xFF);
        out.writeByte(amplifier & 0xFF);
        ByteBufOps.writeVarInt(out, duration > 32767 ? 32767 : duration);
        out.writeByte(particles ? 1 : 0);
        enqueueOut(out);
    }

    /**
     * Remove Entity Effect (0x1E — EntityRemoveStatusEffectS2CPacket's own
     * write): varint entity id, byte effect. Any thread.
     */
    void sendRemoveEntityEffect(int entityId, int effectId) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        ByteBuf out = Unpooled.buffer(12);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_REMOVE_ENTITY_EFFECT);
        ByteBufOps.writeVarInt(out, entityId);
        out.writeByte(effectId & 0xFF);
        enqueueOut(out);
    }

    /**
     * Combat Event (0x42) type 2 — the client shows the death screen. Vanilla
     * fields: playerId (this client's wire entity id), entityId (the killer;
     * self here — no combat yet), death message. Any thread.
     */
    void sendDeath(PlayerSession player) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        ByteBuf out = Unpooled.buffer(64);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_COMBAT_EVENT);
        ByteBufOps.writeVarInt(out, Protocol18.COMBAT_EVENT_ENTITY_DIED);
        ByteBufOps.writeVarInt(out, ownEntityId);
        out.writeInt(ownEntityId);
        ByteBufOps.writeString(out, "{\"text\":\"" + player.name() + " died\"}");
        enqueueOut(out);
    }

    /**
     * The respawn wire sequence (after the engine reset the body): Respawn
     * packet (0x07), the spawn chunk view re-sent fresh, the authoritative
     * position at spawn, health, and the emptied inventory. Any thread; the
     * engine publishes respawned first. The overworld's sequence (dimension
     * 0 — the historical shape).
     */
    void sendRespawnSequence(PlayerSession player, net.zaminmc.torch.util.Position destination) {
        sendRespawnSequence(player, destination, 0);
    }

    /**
     * The dimension-aware respawn sequence (the changeDimension walk's
     * wire arm): the Respawn packet carries the DESTINATION dimension and
     * every subsequent chunk read walks that world — the reference's
     * PlayerRespawnS2CPacket(player.dimension, ...) + sendWorldInfo shape.
     */
    void sendRespawnSequence(PlayerSession player, net.zaminmc.torch.util.Position destination,
                             int destinationDimension) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        dimension = destinationDimension;
        if (chunkTracker != null) {
            chunkTracker.rePointDimension(destinationDimension);
        }
        ByteBuf out = Unpooled.buffer(32);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_RESPAWN);
        out.writeInt(destinationDimension); // the reference's player.dimension
        out.writeByte(engine.difficulty());
        out.writeByte(player.gamemode().legacyId());
        ByteBufOps.writeString(out, engine.engineConfig().levelType()); // respawn mirrors the join's level-type
        enqueueOut(out);
        player.link().updateAbilities(abilitiesFlagsOf(player.gamemode()));

        if (chunkTracker != null && destination != null) {
            // The chunk stream re-anchors to the DESTINATION, not the world
            // spawn: a /tp's new territory must stream before the client's
            // physics run there (the "chunks don't generate after /tp"
            // real-client regression class).
            chunkTracker.respawnAt(destination.toBlockPosition().chunkPosition());
        }
        // A teleport-sized jump follows: the movement sanity check accepts the
        // client's acknowledgment at the anchor it was just given.
        lastTeleportAnchor = player.position();
        sendPositionAndLook(channel, player.position(), player.rotation(), true);
        // THE TELEPORT WINDOW (the respawn's own re-anchor — the reference's
        // respawn walk calling teleport): uses refuse until the client's
        // movement acknowledges the spawn anchor.
        player.markTeleportPending(player.position());
        sendUpdateHealth(player);
        sendSetExperience(player); // the respawned body's reset (or surviving) XP bar
        sendWindowItems(channel, player.inventory().snapshot(),
                player.crafting().snapshot(), player.inventory().armorSnapshot(),
                engine.craftingResult(player));
    }

    /**
     * Click Window (0x0E, community-verified layout: u8 window, i16 slot,
     * i8 button, i16 action, i8 mode, claimed slot). The window id routes the
     * click (0 = player inventory, containers open through Open Window). The
     * engine owns the verdict; the confirm (echoing the window id) and the
     * authoritative cursor state follow here.
     */
    private void handleWindowClick(Channel channel, PlayerSession player, ByteBuf packet) {
        int windowId = packet.readUnsignedByte();
        int wireSlot = packet.readShort();
        int button = packet.readByte();
        int actionNumber = packet.readShort();
        int mode = packet.readByte();
        readClaimedSlot(packet);    // the client's predicted stack: informational only
        engine.windowClick(player, windowId, wireSlot, button, mode, accepted -> {
            // Runs on the tick thread after the semantic operation.
            sendConfirmTransaction(channel, windowId, actionNumber, accepted);
            sendCursorSlot(channel, player);
        });
    }

    /** Close Window (0x0D): the window id decides whose carried state returns. */
    private void handleCloseWindow(PlayerSession player, ByteBuf packet) {
        int windowId = packet.readUnsignedByte();
        engine.closeWindow(player, windowId);
    }

    /**
     * Skips the 1.8 slot encoding the click claims to carry. The NBT marker is
     * one byte (0 = none, per vanilla {@code readCompoundTag}); a non-zero
     * marker starts a full NBT compound the engine does not need — the claimed
     * slot is the click packet's final field and the transport is
     * length-framed, so leaving it unread is exact and safe.
     */
    private static void readClaimedSlot(ByteBuf packet) {
        short id = packet.readShort();
        if (id == -1) {
            return;
        }
        packet.readByte();              // count
        packet.readShort();             // damage
        if (packet.isReadable()) {
            packet.readByte();          // NBT marker: 0 = none
        }
    }

    /**
     * Reads the MC|BEdit / MC|BSign payload's wire item (VIII12 — the
     * reference {@code PacketByteBuf.readItem} lines 178-189 1:1): the
     * legacy short id (-1 = the empty form, answering null like the
     * reference's own null arm), the count byte, the damage short, then the
     * slot NBT compound parsed WHOLE through {@link SlotNbt#readBoxedTag}
     * into the boxes the engine's {@code ItemNbt.readTagInto} walk reads
     * (the same arms the anvil import lands — a signed payload's
     * pages/author/title ride exactly the reference's tag rows). An
     * unmappable legacy id answers null (the reference would construct a
     * null-item stack the isValid gates refuse anyway); a malformed NBT
     * compound throws like the reference's NbtIo.read and the caller's
     * length-framed transport isolates the remains.
     */
    private static net.zaminmc.torch.item.ItemStack readPluginPayloadItem(ByteBuf packet) {
        short legacyId = packet.readShort();
        if (legacyId < 0) {
            return null; // the reference's empty arm: itemstack stays null
        }
        int count = packet.readUnsignedByte();
        int damage = packet.readShort();
        java.util.Optional<net.zaminmc.torch.util.Identifier> resolved =
                LegacyBlockIds.identifierOf(legacyId & 0xFFFF, damage)
                        .filter(id -> net.zaminmc.torch.server.item.BuiltinItems
                                .lookup(id).isPresent())
                        .or(() -> LegacyBlockIds.identifierOf(legacyId & 0xFFFF));
        net.zaminmc.torch.item.ItemType type = resolved
                .flatMap(net.zaminmc.torch.server.item.BuiltinItems::lookup)
                .orElse(null);
        if (type == null) {
            return null; // unmappable: the gates refuse an unknown item anyway
        }
        // The reference reads the compound with NbtIo.read — the same walk
        // SlotNbt.readBoxedTag owns over the wire's own bytes.
        java.util.Map<String, Object> boxed = SlotNbt.readBoxedTag(packet);
        net.zaminmc.torch.item.ItemStack stack = net.zaminmc.torch.item.ItemStack
                .of(type, Math.max(1, count)).withDamage(Math.max(0, damage));
        if (boxed != null && !boxed.isEmpty()) {
            stack = net.zaminmc.torch.server.nbt.ItemNbt.readTagInto(stack,
                    net.zaminmc.torch.server.nbt.NbtOps.fromBoxed(boxed));
        }
        return stack;
    }

    /**
     * Set Creative Slot (0x10): the creative inventory's write path. The
     * client is authoritative for its own creative inventory — the engine
     * mirrors the claimed stack into the mapped engine slot (vanilla
     * ContainerPlayer mapping). Non-creative sessions are refused silently
     * (the historical server's guard against faked creative packets).
     */
    private void handleCreativeSlot(PlayerSession player, ByteBuf packet) {
        short wireSlot = packet.readShort();
        short legacyId = packet.readShort();
        net.zaminmc.torch.item.ItemStack stack = net.zaminmc.torch.item.ItemStack.EMPTY;
        if (legacyId > 0) {
            int count = packet.readUnsignedByte();
            int damage = packet.readShort();
            SlotNbt.skip(packet); // the claimed slot's optional NBT marker + compound
            // The damage-aware reverse resolves the flattened BLOCK variant
            // first (the dye families' color members); a slot claim names
            // the ITEM though — the single damage-domain family item (Slice
            // 31: wool 35, the stained glass 95, the pane 160, the carpet
            // 171, the stained clay 159 — the variant block has no item of
            // its own), so a miss falls back to the plain id with the
            // damage preserved.
            java.util.Optional<net.zaminmc.torch.util.Identifier> claimed =
                    LegacyBlockIds.identifierOf(legacyId & 0xFFFF, damage)
                            .filter(id -> net.zaminmc.torch.server.item.BuiltinItems
                                    .lookup(id).isPresent())
                            .or(() -> LegacyBlockIds.identifierOf(legacyId & 0xFFFF));
            stack = claimed
                    .flatMap(net.zaminmc.torch.server.item.BuiltinItems::lookup)
                    .map(type -> {
                        try {
                            return net.zaminmc.torch.item.ItemStack.of(type,
                                    Math.min(64, Math.max(1, count))).withDamage(Math.max(0, damage));
                        } catch (IllegalArgumentException invalid) {
                            return net.zaminmc.torch.item.ItemStack.EMPTY;
                        }
                    })
                    .orElse(net.zaminmc.torch.item.ItemStack.EMPTY);
        }
        engine.setCreativeSlot(player, wireSlot, stack);
    }

    /**
     * Tab-Complete (0x14): the historical server-side completion. A command
     * line with no argument yet completes the command names the sender may
     * use (the op gate hides unauthorized commands, mirroring the dispatcher);
     * a line with an argument (and plain chat text) completes online player
     * names against the last token. The reply is Tab-Complete 0x3A with the
     * matched strings (protocol 47 layout: VarInt count, then strings).
     */
    private void handleTabComplete(PlayerSession player, ByteBuf packet) {
        String text = ByteBufOps.readString(packet, 256);
        if (packet.isReadable(9)) {
            packet.readByte(); // option present flag
            packet.readLong();  // the looked-at block position
        } else if (packet.isReadable()) {
            packet.readByte();
        }
        java.util.List<String> completions = new java.util.ArrayList<>();
        if (text.startsWith("/")) {
            int space = text.indexOf(' ');
            if (space < 0) {
                String prefix = text.substring(1).toLowerCase(java.util.Locale.ROOT);
                for (net.zaminmc.torch.server.chat.CommandService.Command command
                        : engine.commands().all()) {
                    if (command.name().startsWith(prefix)
                            && player.opLevel() >= command.requiredLevel()) {
                        completions.add("/" + command.name());
                    }
                }
            } else {
                // The per-command ARGUMENT completion (the reference's
                // getSuggestions arms): the resolved command's own suggester
                // answers the candidates for the being-typed last token;
                // a command without an arm (or below the required level)
                // falls back to the online player names (the engine's
                // standing stand-in for the reference's getPlayerNames arms).
                String afterName = text.substring(space + 1);
                String[] args = afterName.split("\\s+", -1);
                String commandName = text.substring(1, space).toLowerCase(java.util.Locale.ROOT);
                net.zaminmc.torch.server.chat.CommandService.Command command =
                        engine.commands().lookup(commandName);
                java.util.List<String> candidates = null;
                if (command != null && command.suggester() != null
                        && player.opLevel() >= command.requiredLevel()) {
                    net.zaminmc.torch.server.chat.CommandSender view =
                            new net.zaminmc.torch.server.chat.CommandSender() {
                                @Override public String name() { return player.name(); }
                                @Override public void sendMessage(String message) { }
                                @Override public int opLevel() { return player.opLevel(); }
                                @Override public PlayerSession player() { return player; }
                            };
                    candidates = command.suggester().apply(view, args);
                }
                if (candidates != null) {
                    completions.addAll(
                            net.zaminmc.torch.server.chat.CommandService.matchLastToken(args, candidates));
                } else {
                    completePlayerNames(player, afterName, completions);
                }
            }
        } else {
            completePlayerNames(player, text, completions);
        }
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        ByteBuf out = Unpooled.buffer(64);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_TAB_COMPLETE);
        ByteBufOps.writeVarInt(out, completions.size());
        for (String completion : completions) {
            ByteBufOps.writeString(out, completion);
        }
        enqueueOut(out);
    }

    /**
     * Update Sign (0x12): the placement editor's four lines. The engine
     * validates the target is a sign within reach, stores the text, and the
     * sign listener fans the Update Sign (0x33) replay out to viewers.
     */
    private void handleUpdateSign(PlayerSession player, ByteBuf packet) {
        long packed = packet.readLong();
        String[] lines = new String[4];
        for (int line = 0; line < 4; line++) {
            lines[line] = ByteBufOps.readString(packet, 128);
        }
        int[] coords = ByteBufOps.decodePackedBlockPosition(packed);
        BlockPosition position = new BlockPosition(coords[0], coords[1], coords[2]);
        engine.editSign(player, position, lines);
    }

    /**
     * Update Sign (0x33): the historical text replay — sign edits fan out to
     * every viewer, and the chunk-send path replays the text of signs in a
     * freshly loaded column (protocol 47 carries no tile entities in chunk
     * data). Safe from any thread.
     */
    void sendSignUpdate(BlockPosition position, String[] lines) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        enqueueOut(encodeSignUpdate(position, lines));
    }

    /** The 0x33 packet body (static: the chunk tracker replays on its own channel). */
    private static ByteBuf encodeSignUpdate(BlockPosition position, String[] lines) {
        ByteBuf out = Unpooled.buffer(64);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_UPDATE_SIGN);
        ByteBufOps.writePackedBlockPosition(out, position.x(), position.y(), position.z());
        for (int line = 0; line < 4; line++) {
            ByteBufOps.writeString(out, lines != null && line < lines.length
                    && lines[line] != null ? lines[line] : "");
        }
        return out;
    }

    /**
     * Open Sign Editor (0x36): the placement editor's own trigger —
     * the reference's openSignEditor (ServerPlayerEntity lines 547-550)
     * sends it to the placing player alone; the client's editor opens
     * only on this packet and closes into the Update Sign (0x12) write
     * path that already works. The body is the id varint + the packed
     * position long, the same packing the 0x33 writer carries. Safe
     * from any thread.
     */
    void sendOpenSignEditor(BlockPosition position) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        ByteBuf out = Unpooled.buffer(16);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_OPEN_SIGN_EDITOR);
        ByteBufOps.writePackedBlockPosition(out, position.x(), position.y(), position.z());
        enqueueOut(out);
    }

    /** Adds online players (never the asker) whose name starts the token. */
    private void completePlayerNames(PlayerSession asker, String token,
                                     java.util.List<String> completions) {
        String prefix = token.substring(token.lastIndexOf(' ') + 1)
                .toLowerCase(java.util.Locale.ROOT);
        for (PlayerSession candidate : engine.playerRegistry().all()) {
            if (!candidate.equals(asker) && candidate.name().toLowerCase(java.util.Locale.ROOT)
                    .startsWith(prefix)) {
                completions.add(candidate.name());
            }
        }
    }

    /** Confirm Transaction (0x32): the client reverts its prediction on rejection. */
    private void sendConfirmTransaction(Channel channel, int windowId, int actionNumber,
                                        boolean accepted) {
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        ByteBuf out = Unpooled.buffer(8);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_CONFIRM_TRANSACTION);
        out.writeByte(windowId);
        out.writeShort(actionNumber);
        out.writeBoolean(accepted);
        enqueueOut(out);
    }

    /** Authoritative cursor stack (Set Slot with window -1, slot -1). Any thread. */
    private void sendCursorSlot(Channel channel, PlayerSession player) {
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        ByteBuf out = Unpooled.buffer(16);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_SET_SLOT);
        out.writeByte(-1);          // window: cursor
        out.writeShort(-1);         // slot: cursor
        writeSlot(out, player.inventory().cursor());
        enqueueOut(out);
    }

    private void handleDigging(PlayerSession player, ByteBuf packet) {
        int status = packet.readByte();
        int[] pos = ByteBufOps.readPackedBlockPosition(packet);
        // The 1.8 C07 tail: the dig's own face byte — the reference's
        // startMiningBlock(pos, face) carries it (the extinguish arm's
        // face-offset cell); every dig action's packet carries it, the
        // START is the only consumer. A malformed body that omits it
        // reads the -1 sentinel (no extinguish arm, nothing throws).
        int face = packet.isReadable(1) ? packet.readUnsignedByte() : -1;
        net.zaminmc.torch.GameMode mode = player.gamemode();
        try {
            var target = new net.zaminmc.torch.block.BlockPosition(pos[0], pos[1], pos[2]);
            if (mode == net.zaminmc.torch.GameMode.CREATIVE) {
                // Creative breaking is instant on "started digging" (the 1.8
                // client sends START to break; the trailing STOP hits an
                // already-air cell). The face rides the extinguish-first
                // law (a fire at the face-offset cell consumes the click).
                if (status == 0 || status == 2) {
                    engine.blockInteraction().submitCreativeBreak(player, target, face);
                }
                return;
            }
            if (mode == net.zaminmc.torch.GameMode.ADVENTURE
                    || mode == net.zaminmc.torch.GameMode.SPECTATOR) {
                // The reference's own band (handlePlayerDig's case is shared
                // by every mode; the manager's arms refuse inside): the
                // drops guard is {@code isSpectator()} alone (DROP_ITEM/
                // DROP_ALL_ITEMS: adventure drops, spectators don't); the
                // dig actions (0/1/2) ride the shared case band below —
                // the walk's own spectator refusal and the adventure
                // CanDestroy consult answer inside, exactly the
                // reference's restrictsWorldModification walk.
                if (status == 3 || status == 4) {
                    if (mode != net.zaminmc.torch.GameMode.SPECTATOR) {
                        if (status == 3) engine.dropHeld(player, true);  // drop whole held stack (Ctrl+Q)
                        else engine.dropHeld(player, false);             // drop one held item (Q)
                    }
                    return; // the spectator's drop: the reference's isSpectator refusal
                }
                if (status == 5) {
                    engine.releaseUsingItem(player); // released a use (eat cancel)
                    return;
                }
                // 0/1/2 fall through to the shared case band.
            }
            switch (status) {
                case 0 -> engine.blockInteraction().submitMiningStart(player, target, face);
                case 1 -> engine.blockInteraction().submitMiningAborted(player, target);
                case 2 -> engine.blockInteraction().submitMiningFinished(player, target);
                case 3 -> engine.dropHeld(player, true);  // drop whole held stack (Ctrl+Q)
                case 4 -> engine.dropHeld(player, false); // drop one held item (Q)
                case 5 -> engine.releaseUsingItem(player); // released a use (eat cancel)
                default -> {
                }
            }
        } catch (IllegalArgumentException outOfWorld) {
            LOGGER.fine(() -> "Ignored digging at out-of-world position from " + player.name());
        }
    }

    private void handleBlockPlacement(PlayerSession player, ByteBuf packet) {
        long rawPosition = packet.readLong();
        int face = packet.readByte();
        // 1.8 slot: short id, byte count, short metadata, optional NBT. In creative
        // the client's claimed item is authoritative (client-side creative
        // inventory); in survival the engine validates its own inventory instead.
        // The metadata field disambiguates the slab family's shared ids.
        short heldId = packet.readShort();
        int heldDamage = 0;
        if (heldId > 0) {
            packet.readUnsignedByte();          // count
            heldDamage = packet.readShort();    // the variant metadata
            SlotNbt.skip(packet);
        }
        // The 1.8 C08 cursor bytes (the hit offset within the clicked face,
        // 16ths — the slab half's placement signal, the firework's launch
        // point (Slice 65: the per-axis fractions the useOn faceX/faceZ
        // consume)).
        float cursorX = 0.0f;
        float cursorY = 0.0f;
        float cursorZ = 0.0f;
        if (packet.isReadable(3)) {
            cursorX = packet.readByte() / 16.0f;
            cursorY = packet.readByte() / 16.0f;
            cursorZ = packet.readByte() / 16.0f;
        }
        // The -1 sentinel position (and face 255 against a block) mean "use the
        // held item" rather than "place on a block": the eating path.
        if (rawPosition == -1L || face == 255) {
            engine.useItem(player);
            return;
        }
        if (face < 0 || face > 5) {
            return; // invalid face: nothing to place
        }
        int[] clicked = ByteBufOps.decodePackedBlockPosition(rawPosition);
        net.zaminmc.torch.block.BlockPosition target =
                new net.zaminmc.torch.block.BlockPosition(clicked[0], clicked[1], clicked[2]);
        // A right-click use: the engine decides on the simulation context whether
        // the target block has a container (crafting table -> Open Window) or the
        // use degrades to a placement proposal. Creative claims its block on the
        // wire; survival leaves the decision to the authoritative inventory.
        java.util.Optional<net.zaminmc.torch.block.BlockType> creativeHeld = java.util.Optional.empty();
        if (player.gamemode() == GameMode.CREATIVE && heldId > 0) {
            net.zaminmc.torch.util.Identifier heldIdentifier =
                    LegacyBlockIds.identifierOf(heldId & 0xFFFF, heldDamage).orElse(null);
            if (heldIdentifier == null) {
                LOGGER.fine(() -> "Ignored use of unmapped item " + heldId
                        + " from " + player.name());
                return;
            }
            // Block claims resolve for the placement paths; pure item claims
            // (flint, buckets, hoes, boat, minecart) leave the block claim
            // empty — the engine's dispatch keys those uses off the real
            // held stack, which Set Creative Slot (0x10) already populated.
            creativeHeld = engine.blockRegistry().lookup(heldIdentifier);
        }
        final var held = creativeHeld;
        try {
            engine.useItemOnBlock(player, target, face, held, cursorX, cursorY, cursorZ, windowId -> {
                // The engine recorded the container kind before dispatching the
                // callback; the adapter picks the matching Open Window flavor.
                Channel channel = adapter.channelOf(this);
                switch (player.openContainerKind()) {
                    case FURNACE -> sendFurnaceWindow(channel, player, windowId);
                    case CHEST -> sendChestWindow(channel, player, windowId);
                    case ENDER_CHEST -> sendEnderChestWindow(channel, player, windowId);
                    case BEACON -> sendBeaconWindow(channel, player, windowId);
                    case CHEST_MINECART -> sendChestMinecartWindow(channel, player, windowId);
                    case HOPPER_MINECART -> sendHopperMinecartWindow(channel, player, windowId);
                    case DISPENSER -> sendDispenserWindow(channel, player, windowId);
                    case HOPPER -> sendHopperWindow(channel, player, windowId);
                    case BREWING_STAND -> sendBrewingWindow(channel, player, windowId);
                    case ENCHANTING_TABLE -> sendEnchantingWindow(channel, player, windowId);
                    case ANVIL -> sendAnvilWindow(channel, player, windowId);
                    default -> sendCraftingTableWindow(channel, player, windowId);
                }
            });
        } catch (IllegalArgumentException outOfWorld) {
            LOGGER.fine(() -> "Ignored use at out-of-world position from " + player.name());
        }
    }

    /**
     * Open Window (0x2D, community-verified layout: u8 window id, string type,
     * string title, u8 slot count) for the crafting table, followed by the
     * authoritative window contents — the order matters, the client ignores
     * Window Items for a window it has not opened. Called on the tick thread
     * by the engine's open dispatch.
     */
    private void sendCraftingTableWindow(Channel channel, PlayerSession player, int windowId) {
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        ByteBuf out = Unpooled.buffer(48);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_OPEN_WINDOW);
        out.writeByte(windowId);
        ByteBufOps.writeString(out, Protocol18.TABLE_WINDOW_TYPE);
        ByteBufOps.writeString(out, Protocol18.TABLE_WINDOW_TITLE);
        // Vanilla sends size 0 for the crafting table (ServerPlayerEntity.openMenu's
        // 3-arg ctor — the client builds the 46-slot layout itself from the type
        // string; a nonzero size pushes client variants onto the generic-chest
        // path, the "renders like a chest" real-client class).
        out.writeByte(0);
        enqueueOut(out);
        sendContainerWindowItems(channel, windowId, player);
    }

    /**
     * Open Window (0x2D) for the furnace, followed by the authoritative
     * 39-slot contents. Resets the per-tick sync state so the first view sends
     * everything. Called on the tick thread by the engine's open dispatch.
     */
    private void sendFurnaceWindow(Channel channel, PlayerSession player, int windowId) {
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        furnaceSlotsSynced = -1;
        furnacePropsSynced = -1;
        java.util.Arrays.fill(furnacePropsSent, -1);
        ByteBuf out = Unpooled.buffer(48);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_OPEN_WINDOW);
        out.writeByte(windowId);
        ByteBufOps.writeString(out, Protocol18.FURNACE_WINDOW_TYPE);
        var furnacePos = player.openContainerPosition();
        var namedFurnace = furnacePos == null ? null : engine.furnaces().peek(furnacePos);
        ByteBufOps.writeString(out, windowTitle(
                namedFurnace == null ? null : namedFurnace.customName(),
                Protocol18.FURNACE_WINDOW_TITLE));
        out.writeByte(3); // the GUI's own slots: input, fuel, output
        enqueueOut(out);
        sendContainerWindowItems(channel, windowId, player);
    }

    /**
     * Open Window (0x2D) for the chest, followed by the authoritative
     * 63-slot contents. Called on the tick thread by the engine's open dispatch.
     */
    private void sendChestWindow(Channel channel, PlayerSession player, int windowId) {
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        ByteBuf out = Unpooled.buffer(48);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_OPEN_WINDOW);
        out.writeByte(windowId);
        ByteBufOps.writeString(out, Protocol18.CHEST_WINDOW_TYPE);
        var openPos = player.openContainerPosition();
        var namedChest = openPos == null ? null : engine.chests().peek(openPos);
        ByteBufOps.writeString(out, windowTitle(
                namedChest == null ? null : namedChest.customName(),
                Protocol18.CHEST_WINDOW_TITLE));
        out.writeByte(27); // the GUI's own slots: 3 rows of 9
        enqueueOut(out);
        sendContainerWindowItems(channel, windowId, player);
        // The §27 window family: capture the serial the open just synced —
        // the per-tick view fan then re-syncs only on a later change.
        var openAt = player.openContainerPosition();
        var openedChest = openAt == null ? null : engine.chests().peek(openAt);
        if (openedChest != null) {
            chestSlotsSynced = openedChest.slotsSerial();
        }
    }

    /**
     * Open Window (0x2D) for the ender chest (Slice 29a — the reference's
     * EnderChestBlock.use: the chest GUI over the player's OWN 27-slot
     * EnderChestInventory, the "container.enderchest" title), followed by
     * the authoritative 63-slot contents — the container slots read the
     * session's private grid. Called on the tick thread by the engine's
     * open dispatch.
     */
    private void sendEnderChestWindow(Channel channel, PlayerSession player, int windowId) {
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        ByteBuf out = Unpooled.buffer(48);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_OPEN_WINDOW);
        out.writeByte(windowId);
        ByteBufOps.writeString(out, Protocol18.CHEST_WINDOW_TYPE);
        ByteBufOps.writeString(out, Protocol18.ENDER_CHEST_WINDOW_TITLE);
        out.writeByte(27); // the GUI's own slots: 3 rows of 9
        enqueueOut(out);
        sendChestWindowItems(channel, windowId, player.enderChest(), player);
        chestSlotsSynced = player.enderChest().slotsSerial();
    }

    /**
     * Open Window (0x2D) for the beacon (Slice 29b — the reference's
     * BeaconBlockEntity.getMenuType "minecraft:beacon" + BeaconMenu's 1
     * payment slot), followed by the authoritative 37-slot contents and the
     * three window properties (levels/primary/secondary — the menu's
     * addListener → updateData walk). Called on the tick thread by the
     * engine's open dispatch.
     */
    private void sendBeaconWindow(Channel channel, PlayerSession player, int windowId) {
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        var position = player.openContainerPosition();
        var beacon = position == null ? null : engine.beacons().peek(position);
        if (beacon == null) {
            return; // stale open (state discarded): nothing to show
        }
        beaconSlotsSynced = -1;
        beaconPropsSynced = -1;
        java.util.Arrays.fill(beaconPropsSent, -1);
        ByteBuf out = Unpooled.buffer(48);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_OPEN_WINDOW);
        out.writeByte(windowId);
        ByteBufOps.writeString(out, Protocol18.BEACON_WINDOW_TYPE);
        ByteBufOps.writeString(out, Protocol18.BEACON_WINDOW_TITLE);
        out.writeByte(1); // the GUI's own slots: the payment alone
        enqueueOut(out);
        sendBeaconWindowItems(channel, windowId, beacon, player);
    }

    /**
     * Full authoritative sync of the beacon window (Slice 29b): 37 slots —
     * 0 the payment, 1-27 main, 28-36 hotbar — then the three properties.
     */
    private void sendBeaconWindowItems(Channel channel, int windowId,
                                       net.zaminmc.torch.server.beacon.BeaconBlockEntity beacon,
                                       PlayerSession player) {
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        ByteBuf out = Unpooled.buffer(96);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_WINDOW_ITEMS);
        out.writeByte(windowId);
        out.writeShort(Protocol18.BEACON_WINDOW_SLOTS);
        java.util.List<net.zaminmc.torch.item.ItemStack> inventory =
                player == null ? java.util.List.of() : player.inventory().snapshot();
        for (int wireSlot = 0; wireSlot < Protocol18.BEACON_WINDOW_SLOTS; wireSlot++) {
            net.zaminmc.torch.item.ItemStack stack;
            if (wireSlot == 0) {
                stack = beacon.payment();
            } else if (wireSlot <= 27) {
                stack = inventory.get(wireSlot - 1 + 9); // main: engine 9-35
            } else {
                stack = inventory.get(wireSlot - 28); // hotbar: engine 0-8
            }
            writeSlot(out, stack);
        }
        enqueueOut(out);
        // The three properties ride the open (the menu's updateData).
        sendWindowProperty(channel, windowId, 0, beacon.levels());
        sendWindowProperty(channel, windowId, 1, beacon.primary());
        sendWindowProperty(channel, windowId, 2, beacon.secondary());
        beaconPropsSent[0] = beacon.levels();
        beaconPropsSent[1] = beacon.primary();
        beaconPropsSent[2] = beacon.secondary();
        beaconPropsSynced = beacon.propsSerial();
        beaconSlotsSynced = beacon.slotsSerial();
    }

    /**
     * Per-tick beacon view (engine fan-out to the open viewer, Slice 29b):
     * the three properties (levels/primary/secondary) whose values moved —
     * the reference's own detectAndSendChanges — and the payment-slot
     * resync when it changed. Tick-thread context.
     */
    void sendBeaconViewTick(Channel channel, int windowId,
                            net.zaminmc.torch.server.beacon.BeaconBlockEntity beacon) {
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        int[] values = {beacon.levels(), beacon.primary(), beacon.secondary()};
        for (int property = 0; property < values.length; property++) {
            if (values[property] != beaconPropsSent[property]) {
                beaconPropsSent[property] = values[property];
                sendWindowProperty(channel, windowId, property, values[property]);
            }
        }
        beaconPropsSynced = beacon.propsSerial();
        if (beacon.slotsSerial() != beaconSlotsSynced) {
            beaconSlotsSynced = beacon.slotsSerial();
            sendBeaconWindowItems(channel, windowId, beacon, session);
        }
    }

    /**
     * Update Block Entity (0x35, type 3) for one beacon
     * (BeaconBlockEntity.createUpdatePacket lines 196-200): the position,
     * the tile type byte, and the NBT compound — the reference's writeNbt
     * (Primary/Secondary/Levels + the position ints). Any thread.
     */
    /**
     * Update Block Entity (0x35, type 4) for one skull
     * (SkullBlockEntity.createUpdatePacket lines 53-56): the position, the
     * tile type byte, and the NBT compound — x/y/z + SkullType + Rot, the
     * reference's writeNbt family (the player head's profile member is out
     * of the engine's model). Any thread.
     */
    void sendSkullTile(net.zaminmc.torch.server.skull.SkullManager.SkullTile tile) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        enqueueOut(encodeSkullTile(tile));
    }

    /**
     * Update Block Entity (0x35, action 6) for one banner
     * (BannerBlockEntity.createUpdatePacket lines 71-75): the position,
     * the action byte, and the writeNbt compound — Base always, Patterns
     * when the tile carries rows. Any thread.
     */
    void sendBannerTile(net.zaminmc.torch.block.BlockPosition position,
                        net.zaminmc.torch.server.banner.BannerManager.BannerTile tile) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        enqueueOut(encodeBannerTile(position, tile));
    }

    /** The banner tile packet's own writer (the chunk-send path's form). */
    private static ByteBuf encodeBannerTile(
            net.zaminmc.torch.block.BlockPosition position,
            net.zaminmc.torch.server.banner.BannerManager.BannerTile tile) {
        ByteBuf out = Unpooled.buffer(64);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_UPDATE_BLOCK_ENTITY);
        ByteBufOps.writePackedBlockPosition(out, position.x(), position.y(), position.z());
        out.writeByte(Protocol18.BLOCK_ENTITY_TYPE_BANNER);
        out.writeByte(10);              // TAG_Compound (the root marker)
        out.writeShort(0);              // the root name: empty
        writeNbtInt(out, "x", position.x());
        writeNbtInt(out, "y", position.y());
        writeNbtInt(out, "z", position.z());
        writeNbtInt(out, "Base", tile.base());
        if (!tile.patterns().isEmpty()) {
            out.writeByte(9);           // TAG_LIST
            SlotNbt.writeShortString(out, "Patterns");
            out.writeByte(10);          // the element type: compounds
            out.writeInt(tile.patterns().size());
            for (var pattern : tile.patterns()) {
                writeNbtString(out, "Pattern", pattern.hash());
                writeNbtInt(out, "Color", pattern.color());
                out.writeByte(0);       // end of the pattern compound
            }
        }
        out.writeByte(0);               // end of the root
        return out;
    }

    /**
     * One command tile's re-publish (Slice 72 — CommandBlockBlockEntity
     * .createUpdatePacket line 75: BlockEntityUpdateS2CPacket action 2
     * over the writeNbt rows — Command/SuccessCount/TrackOutput/
     * LastOutput, the LastOutput an empty string when none).
     */
    void sendCommandTile(net.zaminmc.torch.block.BlockPosition position,
                         net.zaminmc.torch.server.commandblock.CommandBlockManager.CommandState tile) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        enqueueOut(encodeCommandTile(position, tile));
    }

    /** The tile packet's own writer (the static chunk-send path's form). */
    private static ByteBuf encodeCommandTile(
            net.zaminmc.torch.block.BlockPosition position,
            net.zaminmc.torch.server.commandblock.CommandBlockManager.CommandState tile) {
        ByteBuf out = Unpooled.buffer(96);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_UPDATE_BLOCK_ENTITY);
        ByteBufOps.writePackedBlockPosition(out, position.x(), position.y(), position.z());
        out.writeByte(Protocol18.BLOCK_ENTITY_TYPE_COMMAND_BLOCK);
        out.writeByte(10);              // TAG_Compound (the root marker)
        out.writeShort(0);              // the root name: empty
        writeNbtInt(out, "x", position.x());
        writeNbtInt(out, "y", position.y());
        writeNbtInt(out, "z", position.z());
        writeNbtString(out, "Command", tile.command());
        writeNbtInt(out, "SuccessCount", tile.successCount());
        writeNbtByte(out, "TrackOutput", tile.trackOutput() ? 1 : 0);
        writeNbtString(out, "LastOutput",
                tile.lastOutput() == null ? "" : tile.lastOutput());
        out.writeByte(0);               // end of the root
        return out;
    }

    /** The tile packet's own writer (the static chunk-send path's form). */
    private static ByteBuf encodeSkullTile(
            net.zaminmc.torch.server.skull.SkullManager.SkullTile tile) {
        net.zaminmc.torch.block.BlockPosition position = tile.position();
        ByteBuf out = Unpooled.buffer(40);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_UPDATE_BLOCK_ENTITY);
        ByteBufOps.writePackedBlockPosition(out, position.x(), position.y(), position.z());
        out.writeByte(Protocol18.BLOCK_ENTITY_TYPE_SKULL);
        out.writeByte(10);              // TAG_Compound (the root marker)
        out.writeShort(0);              // the root name: empty
        writeNbtInt(out, "x", position.x());
        writeNbtInt(out, "y", position.y());
        writeNbtInt(out, "z", position.z());
        writeNbtByte(out, "SkullType", tile.type());
        writeNbtByte(out, "Rot", tile.rotation());
        // The player head's Owner compound (Wave K — the reference's
        // createUpdatePacket writes the whole writeNbt family: the
        // profile's Name/Id/Properties rows ride the tile packet, the
        // client's own skin render reads them).
        if (tile.profile() != null) {
            out.writeByte(10);          // TAG_Compound
            SlotNbt.writeShortString(out, "Owner");
            var profile = tile.profile();
            if (profile.name() != null && !profile.name().isEmpty()) {
                writeNbtString(out, "Name", profile.name());
            }
            if (profile.id() != null) {
                writeNbtString(out, "Id", profile.id().toString());
            }
            if (profile.properties() != null && !profile.properties().isEmpty()) {
                out.writeByte(10);      // TAG_Compound
                SlotNbt.writeShortString(out, "Properties");
                for (var keyRow : profile.properties().entrySet()) {
                    out.writeByte(9);   // TAG_LIST
                    SlotNbt.writeShortString(out, keyRow.getKey());
                    out.writeByte(10);  // the element type: compounds
                    out.writeInt(keyRow.getValue().size());
                    for (var property : keyRow.getValue()) {
                        writeNbtString(out, "Value", property.value());
                        if (property.signature() != null) {
                            writeNbtString(out, "Signature", property.signature());
                        }
                        out.writeByte(0); // end of the property compound
                    }
                }
                out.writeByte(0);       // end of Properties
            }
            out.writeByte(0);           // end of Owner
        }
        out.writeByte(0);               // end of the root
        return out;
    }

    void sendBeaconTile(net.zaminmc.torch.block.BlockPosition position,
                        net.zaminmc.torch.server.beacon.BeaconBlockEntity beacon) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY
                || chunkTracker == null) {
            return;
        }
        if (!chunkTracker.hasChunk(position.chunkPosition().packed())) {
            return; // the observer cannot see that beacon
        }
        enqueueOut(encodeBeaconTile(position, beacon));
    }

    /** The tile packet's own writer (the static chunk-send path's form). */
    private static ByteBuf encodeBeaconTile(
            net.zaminmc.torch.block.BlockPosition position,
            net.zaminmc.torch.server.beacon.BeaconBlockEntity beacon) {
        ByteBuf out = Unpooled.buffer(64);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_UPDATE_BLOCK_ENTITY);
        ByteBufOps.writePackedBlockPosition(out, position.x(), position.y(), position.z());
        out.writeByte(Protocol18.BLOCK_ENTITY_TYPE_BEACON);
        out.writeByte(10);              // TAG_Compound (the root marker)
        out.writeShort(0);              // the root name: empty
        writeNbtInt(out, "x", position.x());
        writeNbtInt(out, "y", position.y());
        writeNbtInt(out, "z", position.z());
        writeNbtInt(out, "Primary", beacon.primary());
        writeNbtInt(out, "Secondary", beacon.secondary());
        writeNbtInt(out, "Levels", beacon.levels());
        out.writeByte(0);               // end of the root
        return out;
    }

    /**
     * Open Window (0x2D) for the chest minecart (Slice 21a — the reference's
     * ChestMinecartEntity.getMenuType "minecraft:chest" + the
     * "container.minecart" title): the chest GUI over the cart's own 27
     * slots, followed by the authoritative 63-slot contents. Called on the
     * tick thread by the engine's open dispatch.
     */
    void sendChestMinecartWindow(Channel channel, PlayerSession player, int windowId) {
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        ByteBuf out = Unpooled.buffer(48);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_OPEN_WINDOW);
        out.writeByte(windowId);
        ByteBufOps.writeString(out, Protocol18.CHEST_WINDOW_TYPE);
        ByteBufOps.writeString(out, Protocol18.MINECART_CHEST_WINDOW_TITLE);
        out.writeByte(27); // the GUI's own slots: the chest menu over the cart
        enqueueOut(out);
        sendContainerWindowItems(channel, windowId, player);
        var openedCart = engineVehicleChest(player.containerMountId());
        if (openedCart != null) {
            chestSlotsSynced = openedCart.slotsSerial();
        }
    }

    /**
     * Open Window (0x2D) for the hopper minecart (Slice 66 — the reference's
     * HopperMinecartEntity.getMenuType "minecraft:hopper" line 162 + the
     * entity's own display name, entity.MinecartHopper.name): the hopper GUI
     * over the cart's own 5 slots, followed by the authoritative 41-slot
     * contents. Called on the tick thread by the engine's open dispatch.
     */
    void sendHopperMinecartWindow(Channel channel, PlayerSession player, int windowId) {
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        ByteBuf out = Unpooled.buffer(48);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_OPEN_WINDOW);
        out.writeByte(windowId);
        ByteBufOps.writeString(out, Protocol18.HOPPER_WINDOW_TYPE);
        ByteBufOps.writeString(out, Protocol18.MINECART_HOPPER_WINDOW_TITLE);
        out.writeByte(5); // the GUI's own slots: the hopper menu over the cart
        enqueueOut(out);
        sendContainerWindowItems(channel, windowId, player);
        var openedHopperCart = engineVehicleHopper(player.containerMountId());
        if (openedHopperCart != null) {
            hopperSlotsSynced = openedHopperCart.slotsSerial();
        }
    }

    /**
     * Open Window (0x2D) for the dispenser/dropper (Slice 11a — the
     * reference's getMenuType split: "minecraft:dispenser" vs
     * "minecraft:dropper", nine GUI slots), followed by the authoritative
     * 45-slot contents. Called on the tick thread by the engine's open
     * dispatch.
     */
    private void sendDispenserWindow(Channel channel, PlayerSession player, int windowId) {
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        var position = player.openContainerPosition();
        var container = position == null ? null : engine.dispensers().peek(position);
        if (container == null) {
            return; // stale open (state discarded): nothing to show
        }
        boolean dropper = net.zaminmc.torch.server.dispenser.DispenserBlocks
                .isDropper(engine.world().getBlock(position));
        ByteBuf out = Unpooled.buffer(48);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_OPEN_WINDOW);
        out.writeByte(windowId);
        ByteBufOps.writeString(out, dropper
                ? Protocol18.DROPPER_WINDOW_TYPE : Protocol18.DISPENSER_WINDOW_TYPE);
        ByteBufOps.writeString(out, windowTitle(container.customName(),
                dropper ? Protocol18.DROPPER_WINDOW_TITLE : Protocol18.DISPENSER_WINDOW_TITLE));
        out.writeByte(9); // the GUI's own slots: 3 rows of 3
        enqueueOut(out);
        sendContainerWindowItems(channel, windowId, player);
        dispenserSlotsSynced = container.slotsSerial();
    }

    /**
     * Open Window (0x2D) for the hopper (the reference's HopperMenu —
     * "minecraft:hopper", five GUI slots), followed by the authoritative
     * 41-slot contents. Called on the tick thread by the engine's open
     * dispatch.
     */
    private void sendHopperWindow(Channel channel, PlayerSession player, int windowId) {
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        var position = player.openContainerPosition();
        var hopper = position == null ? null : engine.hopper().peek(position);
        if (hopper == null) {
            return; // stale open (state discarded): nothing to show
        }
        ByteBuf out = Unpooled.buffer(48);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_OPEN_WINDOW);
        out.writeByte(windowId);
        ByteBufOps.writeString(out, Protocol18.HOPPER_WINDOW_TYPE);
        ByteBufOps.writeString(out, windowTitle(hopper.customName(),
                Protocol18.HOPPER_WINDOW_TITLE));
        out.writeByte(5); // the GUI's own slots: the hopper's five
        enqueueOut(out);
        sendContainerWindowItems(channel, windowId, player);
        hopperSlotsSynced = hopper.slotsSerial();
    }

    /**
     * Open Window (0x2D) for the brewing stand (Slice 11e — the reference's
     * BrewingStandMenu), followed by the authoritative 40-slot contents.
     * The window's GUI carries four own slots (the three bottles + the
     * ingredient); the brew timer rides the property stream. Called on the
     * tick thread by the engine's open dispatch.
     */
    private void sendBrewingWindow(Channel channel, PlayerSession player, int windowId) {
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        var position = player.openContainerPosition();
        var stand = position == null ? null : engine.brewingStands().peek(position);
        if (stand == null) {
            return; // stale open (state discarded): nothing to show
        }
        brewingSlotsSynced = -1;
        brewingTimerSent = -1;
        ByteBuf out = Unpooled.buffer(48);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_OPEN_WINDOW);
        out.writeByte(windowId);
        ByteBufOps.writeString(out, Protocol18.BREWING_WINDOW_TYPE);
        ByteBufOps.writeString(out, windowTitle(stand.customName(),
                Protocol18.BREWING_WINDOW_TITLE));
        out.writeByte(4); // the GUI's own slots: three bottles + the ingredient
        enqueueOut(out);
        sendContainerWindowItems(channel, windowId, player);
    }

    /**
     * Full authoritative sync of the brewing window (Slice 11e): 40 slots —
     * 0-2 the bottles, 3 the ingredient, 4-30 main, 31-39 hotbar. Any thread;
     * owner supplies the session state.
     */
    private void sendBrewingWindowItems(Channel channel, int windowId,
                                        net.zaminmc.torch.server.brewing.BrewingStandBlockEntity stand,
                                        PlayerSession player) {
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        ByteBuf out = Unpooled.buffer(96);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_WINDOW_ITEMS);
        out.writeByte(windowId);
        out.writeShort(Protocol18.BREWING_WINDOW_SLOTS);
        var containerSlots = stand.snapshotSlots();
        java.util.List<net.zaminmc.torch.item.ItemStack> inventory =
                player == null ? java.util.List.of() : player.inventory().snapshot();
        for (int wireSlot = 0; wireSlot < Protocol18.BREWING_WINDOW_SLOTS; wireSlot++) {
            net.zaminmc.torch.item.ItemStack stack;
            if (wireSlot <= Protocol18.BREWING_WIRE_SLOT_LAST) {
                stack = containerSlots[wireSlot];
            } else if (wireSlot <= Protocol18.BREWING_WIRE_SLOT_MAIN_LAST) {
                // main: wire 4-30 -> engine 9-35
                stack = inventory.get(wireSlot + 5);
            } else {
                stack = inventory.get(wireSlot - Protocol18.BREWING_WIRE_SLOT_HOTBAR_FIRST);
            }
            writeSlot(out, stack);
        }
        enqueueOut(out);
    }

    /**
     * Per-tick brewing view (engine fan-out to the open viewer): the brew
     * timer's Window Property move (the reference's own updateListeners —
     * the changed value only) and the full 40-slot resync when the stand's
     * slots changed. Tick-thread context.
     */
    void sendBrewingViewTick(Channel channel, int windowId,
                             net.zaminmc.torch.server.brewing.BrewingStandBlockEntity stand) {
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        int timer = stand.timer();
        if (timer != brewingTimerSent) {
            brewingTimerSent = timer;
            sendWindowProperty(channel, windowId, 0, timer);
        }
        if (stand.slotsSerial() != brewingSlotsSynced) {
            brewingSlotsSynced = stand.slotsSerial();
            sendBrewingWindowItems(channel, windowId, stand, session);
        }
    }

    /**
     * Per-tick chest-family view (the §27 window family's re-sync wave,
     * driven by the engine's fan): the connection re-syncs the full
     * authoritative Window Items only when the container's slotsSerial
     * moved since the open (or the last re-sync) — a plugin's setSlot, a
     * hopper transfer into the chest, the world side's own writes. The
     * resolved chest carries whichever chest-layout window is open (the
     * world chest, the ender grid, the cart's chest). Called on the tick
     * thread by the adapter's fan-out.
     */
    void sendChestViewTick(Channel channel, int windowId,
                           net.zaminmc.torch.server.chest.ChestBlockEntity chest) {
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        if (chest.slotsSerial() != chestSlotsSynced) {
            chestSlotsSynced = chest.slotsSerial();
            sendChestWindowItems(channel, windowId, chest, session);
        }
    }

    /**
     * Per-tick hopper-family view (the same wave): the 5-slot window's
     * re-sync on the hopper's own serial — the world hopper or the hopper
     * minecart's hopper. Called on the tick thread by the adapter's fan-out.
     */
    void sendHopperViewTick(Channel channel, int windowId,
                             net.zaminmc.torch.server.hopper.HopperBlockEntity hopper) {
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        if (hopper.slotsSerial() != hopperSlotsSynced) {
            hopperSlotsSynced = hopper.slotsSerial();
            sendHopperWindowItems(channel, windowId, hopper, session);
        }
    }

    /**
     * Per-tick dispenser-family view (the same wave): the 9-slot window's
     * re-sync on the dispenser's own serial. Called on the tick thread by
     * the adapter's fan-out.
     */
    void sendDispenserViewTick(Channel channel, int windowId,
                                net.zaminmc.torch.server.dispenser.DispenserBlockEntity dispenser) {
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        if (dispenser.slotsSerial() != dispenserSlotsSynced) {
            dispenserSlotsSynced = dispenser.slotsSerial();
            sendDispenserWindowItems(channel, windowId, dispenser, session);
        }
    }

    /** One furnace window property (0x31, community-verified layout). Any thread. */
    private void sendWindowProperty(Channel channel, int windowId, int property, int value) {
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        ByteBuf out = Unpooled.buffer(8);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_WINDOW_PROPERTY);
        out.writeByte(windowId);
        out.writeShort(property);
        out.writeShort(value);
        enqueueOut(out);
    }

    /**
     * Per-tick furnace view (engine fan-out to the open viewer): sends window
     * properties whose values moved and a full 39-slot Window Items resync
     * when the furnace's slots changed. Tick-thread context.
     */
    void sendFurnaceViewTick(Channel channel, int windowId,
                             net.zaminmc.torch.server.furnace.FurnaceBlockEntity furnace) {
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        if (furnace.propsSerial() != furnacePropsSynced) {
            furnacePropsSynced = furnace.propsSerial();
            int[] values = {
                    furnace.burnTimeRemaining(),
                    furnace.burnTimeTotal(),
                    furnace.cookTime(),
                    net.zaminmc.torch.server.furnace.FurnaceRecipes.COOK_TICKS};
            for (int property = 0; property < values.length; property++) {
                if (values[property] != furnacePropsSent[property]) {
                    furnacePropsSent[property] = values[property];
                    sendWindowProperty(channel, windowId, property, values[property]);
                }
            }
        }
        if (furnace.slotsSerial() != furnaceSlotsSynced) {
            furnaceSlotsSynced = furnace.slotsSerial();
            sendFurnaceWindowItems(channel, windowId, furnace, session);
        }
    }

    /**
     * Open Window (0x2D) for the enchanting table, followed by the
     * authoritative 38-slot contents and the seven initial Window Properties
     * (the reference {@code addListener} push). Size 0 — the client builds
     * the layout from the type string. Called on the tick thread by the
     * engine's open dispatch.
     */
    private void sendEnchantingWindow(Channel channel, PlayerSession player, int windowId) {
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        enchantingRevisionSynced = -1;
        java.util.Arrays.fill(enchantingPropsSent, Integer.MIN_VALUE);
        ByteBuf out = Unpooled.buffer(48);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_OPEN_WINDOW);
        out.writeByte(windowId);
        ByteBufOps.writeString(out, Protocol18.ENCHANTING_WINDOW_TYPE);
        ByteBufOps.writeString(out, Protocol18.ENCHANTING_WINDOW_TITLE);
        out.writeByte(0); // the client builds the two-slot layout from the type
        enqueueOut(out);
        sendEnchantingWindowItems(channel, windowId, player);
        var menu = player.enchantingMenu();
        if (menu != null) {
            for (int property = 0; property < Protocol18.ENCHANTING_PROP_COUNT; property++) {
                int value = menu.property(property);
                enchantingPropsSent[property] = value;
                sendWindowProperty(channel, windowId, property, value);
            }
        }
    }

    /** The authoritative 38-slot Window Items (2 menu + 36 player). */
    private void sendEnchantingWindowItems(Channel channel, int windowId, PlayerSession player) {
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        ByteBuf out = Unpooled.buffer(96);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_WINDOW_ITEMS);
        out.writeByte(windowId);
        out.writeShort(Protocol18.ENCHANTING_WINDOW_SLOTS);
        var menu = player.enchantingMenu();
        java.util.List<net.zaminmc.torch.item.ItemStack> inventory =
                player == null ? java.util.List.of() : player.inventory().snapshot();
        for (int wireSlot = 0; wireSlot < Protocol18.ENCHANTING_WINDOW_SLOTS; wireSlot++) {
            net.zaminmc.torch.item.ItemStack stack;
            if (wireSlot == Protocol18.ENCHANTING_WIRE_SLOT_ITEM) {
                stack = menu == null ? net.zaminmc.torch.item.ItemStack.EMPTY : menu.item();
            } else if (wireSlot == Protocol18.ENCHANTING_WIRE_SLOT_LAPIS) {
                stack = menu == null ? net.zaminmc.torch.item.ItemStack.EMPTY : menu.lapis();
            } else if (wireSlot <= Protocol18.ENCHANTING_WIRE_SLOT_MAIN_LAST) {
                stack = inventory.get(wireSlot + 7); // main inventory: engine 9-35
            } else {
                stack = inventory.get(wireSlot - Protocol18.ENCHANTING_WIRE_SLOT_HOTBAR_BASE);
            }
            writeSlot(out, stack);
        }
        enqueueOut(out);
    }

    /**
     * Per-tick enchanting view (engine fan-out to the open viewer): sends the
     * seven window properties whose values moved and a full 38-slot Window
     * Items resync when the menu's revision changed (any observable slot,
     * cost, or clue mutation bumps it — the historical
     * {@code detectAndSendChanges} pairing). Tick-thread context.
     */
    void sendEnchantingViewTick(Channel channel, int windowId,
                                net.zaminmc.torch.server.enchantment.EnchantingMenu menu) {
        if (channel == null || !channel.isActive() || state != WireState.PLAY || menu == null) {
            return;
        }
        for (int property = 0; property < Protocol18.ENCHANTING_PROP_COUNT; property++) {
            int value = menu.property(property);
            if (value != enchantingPropsSent[property]) {
                enchantingPropsSent[property] = value;
                sendWindowProperty(channel, windowId, property, value);
            }
        }
        if (menu.revision() != enchantingRevisionSynced) {
            enchantingRevisionSynced = menu.revision();
            sendEnchantingWindowItems(channel, windowId, session);
        }
    }

    /**
     * Open Window (0x2D) for the anvil, followed by the authoritative
     * 39-slot contents and the single initial Window Property (the repair
     * cost — the reference {@code addListener} push of data id 0). Size 0 —
     * the 3-arg packet form the reference's openMenu sends; the client
     * builds the layout from the type string. Called on the tick thread
     * by the engine's open dispatch.
     */
    private void sendAnvilWindow(Channel channel, PlayerSession player, int windowId) {
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        anvilRevisionSynced = -1;
        anvilCostSent = Integer.MIN_VALUE;
        ByteBuf out = Unpooled.buffer(48);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_OPEN_WINDOW);
        out.writeByte(windowId);
        ByteBufOps.writeString(out, Protocol18.ANVIL_WINDOW_TYPE);
        ByteBufOps.writeString(out, Protocol18.ANVIL_WINDOW_TITLE);
        out.writeByte(0); // the client builds the three-slot layout from the type
        enqueueOut(out);
        sendAnvilWindowItems(channel, windowId, player);
        var menu = player.anvilMenu();
        if (menu != null) {
            int cost = menu.property(Protocol18.ANVIL_PROP_COST);
            anvilCostSent = cost;
            sendWindowProperty(channel, windowId, Protocol18.ANVIL_PROP_COST, cost);
        }
    }

    /** The authoritative 39-slot Window Items (2 inputs + the output + 36 player). */
    private void sendAnvilWindowItems(Channel channel, int windowId, PlayerSession player) {
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        ByteBuf out = Unpooled.buffer(96);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_WINDOW_ITEMS);
        out.writeByte(windowId);
        out.writeShort(Protocol18.ANVIL_WINDOW_SLOTS);
        var menu = player.anvilMenu();
        java.util.List<net.zaminmc.torch.item.ItemStack> inventory =
                player == null ? java.util.List.of() : player.inventory().snapshot();
        for (int wireSlot = 0; wireSlot < Protocol18.ANVIL_WINDOW_SLOTS; wireSlot++) {
            net.zaminmc.torch.item.ItemStack stack;
            if (wireSlot == 0) {
                stack = menu == null ? net.zaminmc.torch.item.ItemStack.EMPTY : menu.input();
            } else if (wireSlot == 1) {
                stack = menu == null ? net.zaminmc.torch.item.ItemStack.EMPTY : menu.ingredient();
            } else if (wireSlot == Protocol18.ANVIL_WIRE_SLOT_OUTPUT) {
                stack = menu == null ? net.zaminmc.torch.item.ItemStack.EMPTY : menu.output();
            } else if (wireSlot <= Protocol18.ANVIL_WIRE_SLOT_MAIN_LAST) {
                stack = inventory.get(wireSlot + 6); // main inventory: engine 9-35
            } else {
                stack = inventory.get(wireSlot - Protocol18.ANVIL_WIRE_SLOT_HOTBAR_BASE);
            }
            writeSlot(out, stack);
        }
        enqueueOut(out);
    }

    /**
     * Per-tick anvil view (engine fan-out to the open viewer): sends the
     * repair-cost property when it moved and a full 39-slot Window Items
     * resync when the menu's revision changed (any observable slot, cost,
     * or output mutation bumps it). Tick-thread context.
     */
    void sendAnvilViewTick(Channel channel, int windowId,
                           net.zaminmc.torch.server.enchantment.AnvilMenu menu) {
        if (channel == null || !channel.isActive() || state != WireState.PLAY || menu == null) {
            return;
        }
        int cost = menu.property(Protocol18.ANVIL_PROP_COST);
        if (cost != anvilCostSent) {
            anvilCostSent = cost;
            sendWindowProperty(channel, windowId, Protocol18.ANVIL_PROP_COST, cost);
        }
        if (menu.revision() != anvilRevisionSynced) {
            anvilRevisionSynced = menu.revision();
            sendAnvilWindowItems(channel, windowId, session);
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
        // The chunk column from x/z alone: a body below y=0 (the reference's
        // own voidTick window — the -64 kill plane) never bounds-crashes
        // the center update; y never changes chunk membership.
        ChunkPosition newChunk = net.zaminmc.torch.block.ChunkPosition.ofBlock(
                (int) Math.floor(position.x()), (int) Math.floor(position.z()));
        if (chunkTracker != null) {
            chunkTracker.updateCenter(newChunk);
        }
        adapter.broadcastMovement(player);
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
        net.zaminmc.torch.util.Position anchor = lastTeleportAnchor;
        if (anchor != null
                && Math.abs(position.x() - anchor.x()) < 2.0
                && Math.abs(position.y() - anchor.y()) < 2.0
                && Math.abs(position.z() - anchor.z()) < 2.0) {
            return true; // the acknowledgment of a server-ordered teleport
        }
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
        // The R-4 payload law (the reference's own registration line):
        // ServerPlayNetworkHandler.tick lines 132-136 send
        // (int) getTimeMillis() as the keep-alive id — the low 32 bits of
        // the send-time clock, NOT a counter. The old counter form was
        // wire-legal (the client echoes any varint) but observably foreign
        // to anything that reads the payload; the millis form is the
        // reference's own. The exact-match read below is unchanged: two
        // consecutive sends are always ≥ one poll period apart, so the
        // millis ids never repeat while one is pending.
        int id = (int) System.currentTimeMillis();
        pendingKeepAlive = id;
        keepAliveSentAt = System.currentTimeMillis();
        ByteBuf out = Unpooled.buffer(10);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_KEEP_ALIVE);
        ByteBufOps.writeVarInt(out, id);
        enqueueOut(out);
    }

    // ------------------------------------------------------------------ ClientLink

    @Override
    public boolean isActive() {
        Channel channel = adapter.channelOf(this);
        return channel != null && channel.isActive();
    }

    @Override
    public String remoteIp() {
        Channel channel = adapter.channelOf(this);
        if (channel == null || channel.remoteAddress() == null) {
            return "";
        }
        String text = channel.remoteAddress().toString();
        // InetSocketAddress prints "/host:port" (or "host/addr:port"): the
        // ban gate keys on the bare address.
        int slash = text.lastIndexOf('/');
        if (slash >= 0) {
            text = text.substring(slash + 1);
        }
        int colon = text.lastIndexOf(':');
        if (colon >= 0 && text.indexOf(':') == colon) { // ipv4 host:port
            text = text.substring(0, colon);
        }
        return text;
    }

    @Override
    public void updateXp() {
        PlayerSession player = currentSession();
        if (player != null) {
            sendSetExperience(player);
        }
    }

    /**
     * The anti-desync resync pair's own cell (handlePlayerUse lines
     * 494-497): the per-player unicast shape of the same 0x23 Block
     * Change write the committed-change broadcast owns — the engine's
     * use walk hands the authoritative post-use state of the clicked
     * cell and the face-offset cell, and the client's predicted ghost
     * from a refused or short-circuited use reverts to the truth.
     */
    @Override
    public void blockResync(net.zaminmc.torch.block.BlockPosition position,
                            net.zaminmc.torch.block.BlockType type) {
        sendBlockChange(position, type);
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
            // §35.5/§35.13 — the ordered close: the Disconnect joins the
            // queue's tail, the final drain writes the owed packets and the
            // Disconnect with ONE flush, then the channel closes. The
            // disconnect can never overtake packets the client was owed.
            net.zaminmc.torch.transport.OutboundQueue queue = outbound();
            if (queue != null) {
                queue.closeWith(out);
            } else {
                out.release();
                channel.close();
            }
        } else {
            sendLoginDisconnect(channel, reason);
        }
    }

    @Override
    public void updateGamemode(int gamemodeId) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        // Change Game State (0x2B): reason 3 = change gamemode, value = mode id.
        ByteBuf out = Unpooled.buffer(16);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_CHANGE_GAME_STATE);
        out.writeByte(Protocol18.GAME_STATE_CHANGE_GAMEMODE);
        out.writeFloat(gamemodeId);
        enqueueOut(out);
    }

    @Override
    public void updateAbilities(int flags) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        // Player Abilities (0x39): flags byte + the historical default speeds.
        ByteBuf out = Unpooled.buffer(16);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_PLAYER_ABILITIES);
        out.writeByte(flags);
        out.writeFloat(0.05f); // fly speed
        out.writeFloat(0.1f);  // field of view modifier (the historical walk speed field)
        enqueueOut(out);
    }

    @Override
    public void updateWeather(boolean raining) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        // Change Game State (0x2B): reason 1 = end rain, 2 = begin rain,
        // value 0.0 (the historical always-clear daylight state).
        ByteBuf out = Unpooled.buffer(16);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_CHANGE_GAME_STATE);
        out.writeByte(raining ? Protocol18.GAME_STATE_CHANGE_BEGIN_RAINING
                : Protocol18.GAME_STATE_CHANGE_END_RAINING);
        out.writeFloat(0.0f);
        enqueueOut(out);
    }

    /**
     * Server Difficulty (0x41 — DifficultyS2CPacket's own write, ML-012):
     * the single difficulty id byte (0 peaceful .. 3 hard). The reference's
     * {@code locked} flag never rides the protocol-47 wire (the ctor
     * carries it server-side only; the byte joined the wire in 1.9). Any
     * thread.
     */
    void sendServerDifficulty(int difficultyId) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        ByteBuf out = Unpooled.buffer(4);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_SERVER_DIFFICULTY);
        out.writeByte(difficultyId & 0xFF);
        enqueueOut(out);
    }

    /**
     * The /difficulty change-time re-sync (the engine's per-player fan-out):
     * the same single-byte Server Difficulty packet, pushed the moment the
     * runtime tier flips so no client waits for its next respawn. Any
     * thread.
     */
    @Override
    public void updateDifficulty(int difficultyId) {
        sendServerDifficulty(difficultyId);
    }

    /**
     * Resource Pack Send (0x48 — ResourcePackS2CPacket's own write,
     * ML-017): the url string then the hash string, nothing else — the
     * 1.9+ request-id varint never rides this protocol. The hash carries
     * the packet ctor's own 40-character law (the reference throws the
     * same IllegalArgumentException at its construction site). Any thread.
     */
    @Override
    public void sendResourcePack(String url, String hash) {
        if (hash.length() > 40) {
            throw new IllegalArgumentException(
                    "Hash is too long (max 40, was " + hash.length() + ")");
        }
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        ByteBuf out = Unpooled.buffer(64 + url.length());
        ByteBufOps.writeVarInt(out, Protocol18.S2C_RESOURCE_PACK_SEND);
        ByteBufOps.writeString(out, url);
        ByteBufOps.writeString(out, hash);
        enqueueOut(out);
    }

    /**
     * Map Data (0x34 — the map item family, the reference's
     * MapDataS2CPacket.write lines 64-82, 1:1): the varint MAP id (the
     * stack's damage field — the client indexes its map item renderer by
     * it, never an entity id), the scale byte, the varint-counted icon
     * rows (the packed {@code (type & 15) << 4 | rotation & 15} byte +
     * the x/y pair), the columns byte, and — when the columns byte is
     * nonzero — the rows/minX/minY bytes + the varint-length colors
     * payload. The framed-maps' gate chunk (the reference's
     * tracking-player set): a non-sentinel key skips observers whose
     * loaded window lacks the frame's chunk. Any thread.
     */
    @Override
    public void sendMapData(int mapId, byte scale,
                            java.util.List<net.zaminmc.torch.server.net.ClientLink.MapIcon> icons,
                            int minX, int minY, int width, int height, byte[] colors,
                            long gateChunk) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        if (gateChunk != Long.MIN_VALUE && chunkTracker != null
                && !chunkTracker.hasChunk(gateChunk)) {
            return; // outside this observer's loaded window (the frame gate)
        }
        ByteBuf out = Unpooled.buffer(32 + Math.max(0, colors.length));
        ByteBufOps.writeVarInt(out, Protocol18.S2C_MAP_DATA);
        ByteBufOps.writeVarInt(out, mapId);
        out.writeByte(scale);
        ByteBufOps.writeVarInt(out, icons.size());
        for (net.zaminmc.torch.server.net.ClientLink.MapIcon icon : icons) {
            out.writeByte((icon.type() & 15) << 4 | icon.rotation() & 15);
            out.writeByte(icon.x());
            out.writeByte(icon.y());
        }
        out.writeByte(width);
        if (width > 0) {
            out.writeByte(height);
            out.writeByte(minX);
            out.writeByte(minY);
            ByteBufOps.writeVarInt(out, colors.length);
            out.writeBytes(colors);
        }
        enqueueOut(out);
    }

    @Override
    public void playGameState(int reason, float value) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        // Change Game State (0x2B) with the caller's reason/value pair —
        // the elder-guardian ghost curse (reason 10, value 0) rides it.
        ByteBuf out = Unpooled.buffer(16);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_CHANGE_GAME_STATE);
        out.writeByte(reason);
        out.writeFloat(value);
        enqueueOut(out);
    }

    @Override
    public void spawnLightning(int entityId, double x, double y, double z) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        // Spawn Global Entity (0x2C, AddGlobalEntityS2CPacket): the entity
        // id, the type byte (1 = thunderbolt — the packet's only member),
        // and the fixed-point 1/32 coordinates (the packet ctor's own
        // MathHelper.floor(v * 32.0) encoding).
        ByteBuf out = Unpooled.buffer(20);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_SPAWN_GLOBAL_ENTITY);
        ByteBufOps.writeVarInt(out, entityId);
        out.writeByte(Protocol18.GLOBAL_ENTITY_THUNDERBOLT);
        out.writeInt((int) Math.floor(x * 32.0));
        out.writeInt((int) Math.floor(y * 32.0));
        out.writeInt((int) Math.floor(z * 32.0));
        enqueueOut(out);
    }

    @Override
    public void sendStatistics(java.util.Map<String, Integer> stats) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        // Client Statistics (0x37, StatsS2CPacket): the varint count then
        // the (stat-key string, varint value) pairs — the dirty set the
        // client merges into its running copy.
        ByteBuf out = Unpooled.buffer(64);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_CLIENT_STATISTICS);
        ByteBufOps.writeVarInt(out, stats.size());
        for (java.util.Map.Entry<String, Integer> stat : stats.entrySet()) {
            ByteBufOps.writeString(out, stat.getKey());
            ByteBufOps.writeVarInt(out, stat.getValue());
        }
        enqueueOut(out);
    }

    @Override
    public void tabListAdd(java.util.List<net.zaminmc.torch.server.net.ClientLink.TabListEntry> entries) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        // Player Info (0x38) action ADD_PLAYER — the reference's write walk
        // (PlayerInfoS2CPacket lines 116-140): uuid, name, varint property
        // count, per property the name string, the value string, the
        // has-signature boolean and its signature string when signed
        // (empty offline — the reference's createFakeProfile; the session
        // service's textures row online), varint game mode, varint ping,
        // boolean has-display-name (the reference's getPlayerListName()
        // answers null → always false).
        ByteBuf out = Unpooled.buffer(96);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_PLAYER_INFO);
        ByteBufOps.writeVarInt(out, Protocol18.PLAYER_INFO_ADD_PLAYER);
        ByteBufOps.writeVarInt(out, entries.size());
        for (net.zaminmc.torch.server.net.ClientLink.TabListEntry entry : entries) {
            ByteBufOps.writeUuid(out, entry.uuid());
            ByteBufOps.writeString(out, entry.name());
            ByteBufOps.writeVarInt(out, entry.properties().size());
            for (net.zaminmc.torch.server.auth.GameProfile.Property property : entry.properties()) {
                ByteBufOps.writeString(out, property.name());
                ByteBufOps.writeString(out, property.value());
                out.writeBoolean(property.hasSignature());
                if (property.hasSignature()) {
                    ByteBufOps.writeString(out, property.signature());
                }
            }
            ByteBufOps.writeVarInt(out, entry.gamemodeId());
            ByteBufOps.writeVarInt(out, entry.ping());
            out.writeBoolean(false); // no display name (getPlayerListName = null)
        }
        enqueueOut(out);
    }

    @Override
    public void tabListRemove(java.util.List<UUID> ids) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        // Player Info (0x38) action REMOVE_PLAYER — the uuid-only entries
        // (the reference's lines 158-159).
        ByteBuf out = Unpooled.buffer(48);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_PLAYER_INFO);
        ByteBufOps.writeVarInt(out, Protocol18.PLAYER_INFO_REMOVE_PLAYER);
        ByteBufOps.writeVarInt(out, ids.size());
        for (UUID id : ids) {
            ByteBufOps.writeUuid(out, id);
        }
        enqueueOut(out);
    }

    @Override
    public void tabListUpdateGamemode(UUID id, int gamemodeId) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        // Player Info (0x38) action UPDATE_GAME_MODE — uuid + varint mode
        // (the reference's lines 141-143).
        ByteBuf out = Unpooled.buffer(32);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_PLAYER_INFO);
        ByteBufOps.writeVarInt(out, Protocol18.PLAYER_INFO_UPDATE_GAME_MODE);
        ByteBufOps.writeVarInt(out, 1);
        ByteBufOps.writeUuid(out, id);
        ByteBufOps.writeVarInt(out, gamemodeId);
        enqueueOut(out);
    }

    @Override
    public void tabListUpdatePing(java.util.List<net.zaminmc.torch.server.net.ClientLink.TabListEntry> entries) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        // Player Info (0x38) action UPDATE_PING — the batched refresh (the
        // reference's lines 145-147): every entry's uuid + varint ping in
        // ONE packet.
        ByteBuf out = Unpooled.buffer(64);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_PLAYER_INFO);
        ByteBufOps.writeVarInt(out, Protocol18.PLAYER_INFO_UPDATE_PING);
        ByteBufOps.writeVarInt(out, entries.size());
        for (net.zaminmc.torch.server.net.ClientLink.TabListEntry entry : entries) {
            ByteBufOps.writeUuid(out, entry.uuid());
            ByteBufOps.writeVarInt(out, entry.ping());
        }
        enqueueOut(out);
    }

    @Override
    public void scoreboardObjective(
            net.zaminmc.torch.server.net.ClientLink.ScoreboardObjectiveData data, int action) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        // Scoreboard Objective (0x3B, ScoreboardObjectiveS2CPacket's write
        // walk lines 79-90): string name, byte action (0 add, 1 remove, 2
        // update), then display name + render type on add/update.
        ByteBuf out = Unpooled.buffer(96);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_SCOREBOARD_OBJECTIVE);
        ByteBufOps.writeString(out, data.name());
        out.writeByte(action);
        if (action == 0 || action == 2) {
            ByteBufOps.writeString(out, data.displayName());
            ByteBufOps.writeString(out, data.renderType());
        }
        enqueueOut(out);
    }

    @Override
    public void scoreboardScore(String owner, int mode, String objective, int value) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        // Update Score (0x3C, ScoreboardScoreS2CPacket's write walk lines
        // 65-75): string owner, varint mode (writeEnum ordinal: 0 CHANGE, 1
        // REMOVE), string objective, varint value on change.
        ByteBuf out = Unpooled.buffer(96);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_UPDATE_SCORE);
        ByteBufOps.writeString(out, owner);
        ByteBufOps.writeVarInt(out, mode);
        ByteBufOps.writeString(out, objective);
        if (mode == 0) {
            ByteBufOps.writeVarInt(out, value);
        }
        enqueueOut(out);
    }

    @Override
    public void scoreboardDisplay(int slot, String objectiveName) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        // Display Scoreboard (0x3D, ScoreboardDisplayS2CPacket's write walk
        // lines 26-31): byte slot, string objective name ("" = cleared).
        ByteBuf out = Unpooled.buffer(48);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_DISPLAY_SCOREBOARD);
        out.writeByte(slot);
        ByteBufOps.writeString(out, objectiveName);
        enqueueOut(out);
    }

    @Override
    public void scoreboardTeam(net.zaminmc.torch.server.net.ClientLink.ScoreboardTeamData team,
                               int action, java.util.List<String> members) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        // Teams (0x3E, TeamS2CPacket's write walk lines 80-99): string name,
        // byte action (0 create, 1 remove, 2 update, 3 join, 4 leave), the
        // full payload on create/update (display name, prefix, suffix, the
        // friendly-flags byte, the name-tag visibility key, the color byte),
        // and the varint-counted members on create/join/leave.
        ByteBuf out = Unpooled.buffer(128);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_TEAMS);
        ByteBufOps.writeString(out, team.name());
        out.writeByte(action);
        if (action == 0 || action == 2) {
            ByteBufOps.writeString(out, team.displayName());
            ByteBufOps.writeString(out, team.prefix());
            ByteBufOps.writeString(out, team.suffix());
            out.writeByte(team.friendlyFlags());
            ByteBufOps.writeString(out, team.nameTagVisibility());
            out.writeByte(team.colorId());
        }
        if (action == 0 || action == 3 || action == 4) {
            ByteBufOps.writeVarInt(out, members.size());
            for (String member : members) {
                ByteBufOps.writeString(out, member);
            }
        }
        enqueueOut(out);
    }

    @Override
    public void worldBorderSize(double sizeLerpTarget) {
        writeWorldBorderPacket(Protocol18.WORLD_BORDER_SET_SIZE, buffer -> {
            buffer.writeDouble(sizeLerpTarget);
        });
    }

    @Override
    public void worldBorderLerp(double lerpSize, double sizeLerpTarget, long lerpTimeMs) {
        writeWorldBorderPacket(Protocol18.WORLD_BORDER_LERP_SIZE, buffer -> {
            buffer.writeDouble(lerpSize);
            buffer.writeDouble(sizeLerpTarget);
            writeVarLong(buffer, lerpTimeMs);
        });
    }

    @Override
    public void worldBorderCenter(double centerX, double centerZ) {
        writeWorldBorderPacket(Protocol18.WORLD_BORDER_SET_CENTER, buffer -> {
            buffer.writeDouble(centerX);
            buffer.writeDouble(centerZ);
        });
    }

    @Override
    public void worldBorderWarningTime(int seconds) {
        writeWorldBorderPacket(Protocol18.WORLD_BORDER_SET_WARNING_TIME, buffer -> {
            ByteBufOps.writeVarInt(buffer, seconds);
        });
    }

    @Override
    public void worldBorderWarningBlocks(int blocks) {
        writeWorldBorderPacket(Protocol18.WORLD_BORDER_SET_WARNING_BLOCKS, buffer -> {
            ByteBufOps.writeVarInt(buffer, blocks);
        });
    }

    @Override
    public void worldBorderInit(double centerX, double centerZ, double lerpSize,
                                double sizeLerpTarget, long lerpTimeMs, int maxSize,
                                int warningBlocks, int warningTime) {
        writeWorldBorderPacket(Protocol18.WORLD_BORDER_INITIALIZE, buffer -> {
            buffer.writeDouble(centerX);
            buffer.writeDouble(centerZ);
            buffer.writeDouble(lerpSize);
            buffer.writeDouble(sizeLerpTarget);
            writeVarLong(buffer, lerpTimeMs);
            ByteBufOps.writeVarInt(buffer, maxSize);
            ByteBufOps.writeVarInt(buffer, warningBlocks);
            ByteBufOps.writeVarInt(buffer, warningTime);
        });
    }

    /** One World Border packet (0x44): the varint action + the per-action fields. */
    private interface BorderFieldWriter {
        void write(ByteBuf buffer);
    }

    private void writeWorldBorderPacket(int action, BorderFieldWriter fields) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        ByteBuf out = Unpooled.buffer(72);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_WORLD_BORDER);
        ByteBufOps.writeVarInt(out, action);
        fields.write(out);
        enqueueOut(out);
    }

    // ------------------------------------------------------------------ titles + tab-list header/footer

    @Override
    public void titleTitle(String jsonText) {
        writeTitlePacket(Protocol18.TITLE_TYPE_TITLE, buffer -> {
            ByteBufOps.writeString(buffer, jsonText);
        });
    }

    @Override
    public void titleSubtitle(String jsonText) {
        writeTitlePacket(Protocol18.TITLE_TYPE_SUBTITLE, buffer -> {
            ByteBufOps.writeString(buffer, jsonText);
        });
    }

    @Override
    public void titleTimes(int fadeIn, int duration, int fadeOut) {
        writeTitlePacket(Protocol18.TITLE_TYPE_TIMES, buffer -> {
            buffer.writeInt(fadeIn);
            buffer.writeInt(duration);
            buffer.writeInt(fadeOut);
        });
    }

    @Override
    public void titleClear() {
        writeTitlePacket(Protocol18.TITLE_TYPE_CLEAR, buffer -> {
        });
    }

    @Override
    public void titleReset() {
        writeTitlePacket(Protocol18.TITLE_TYPE_RESET, buffer -> {
        });
    }

    @Override
    public void tabListHeaderFooter(String headerJson, String footerJson) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        // Player List Header/Footer (0x47, TabListS2CPacket's write walk):
        // the header's then the footer's serialized component.
        ByteBuf out = Unpooled.buffer(48 + headerJson.length() + footerJson.length());
        ByteBufOps.writeVarInt(out, Protocol18.S2C_PLAYER_LIST_HEADER_FOOTER);
        ByteBufOps.writeString(out, headerJson);
        ByteBufOps.writeString(out, footerJson);
        enqueueOut(out);
    }

    /** One Title packet (0x45): the varint enum ordinal + the per-type fields. */
    private void writeTitlePacket(int type, BorderFieldWriter fields) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        // TitlesS2CPacket's own write walk: writeEnum = the varint ordinal,
        // then the text (TITLE/SUBTITLE) or the three i32s (TIMES); the
        // CLEAR/RESET arms carry nothing.
        ByteBuf out = Unpooled.buffer(40);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_TITLE);
        ByteBufOps.writeVarInt(out, type);
        fields.write(out);
        enqueueOut(out);
    }

    @Override
    public void sendChatComponent(String json, int position) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        // Chat (0x02) carrying the serialized component (PacketByteBuf
        // .writeText's own form) at the given position byte.
        ByteBuf out = Unpooled.buffer(32 + json.length());
        ByteBufOps.writeVarInt(out, Protocol18.S2C_CHAT);
        ByteBufOps.writeString(out, json);
        out.writeByte(position);
        enqueueOut(out);
    }

    @Override
    public void updateSpawnPosition(int x, int y, int z) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        // Spawn Position (0x05): the packed block position (the join burst's
        // own writer shape).
        ByteBuf out = Unpooled.buffer(16);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_SPAWN_POSITION);
        ByteBufOps.writePackedBlockPosition(out, x, y, z);
        enqueueOut(out);
    }

    @Override
    public void playSound(String name, double x, double y, double z, float volume, float pitch) {
        // /playsound's per-player Named Sound Effect (0x29) — the reference's
        // SoundEventS2CPacket ctor walk: the *8 fixed-point positions, the
        // f32 volume, the u8 pitch (63 = 1.0).
        sendSound(name, new net.zaminmc.torch.util.Position(x, y, z), volume, pitch);
    }

    @Override
    public void worldParticles(int particleId, boolean longDistance, double x, double y, double z,
                               float offsetX, float offsetY, float offsetZ,
                               float particleData, int count, int[] params) {
        // /particle's World Particles (0x2A) — the reference's
        // ServerWorld.addParticle payload, broadcast per recipient.
        sendParticles(particleId, longDistance,
                new net.zaminmc.torch.util.Position(x, y, z),
                offsetX, offsetY, offsetZ, particleData, count,
                params == null ? new int[0] : params);
    }

    /** The protocol 47 varlong (7 bits per byte, high bit = continuation). */
    private static void writeVarLong(ByteBuf out, long value) {
        while (true) {
            if ((value & ~0x7FL) == 0) {
                out.writeByte((int) value);
                return;
            }
            out.writeByte((int) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }
    }

    @Override
    public void resyncPosition() {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        PlayerSession player = session;
        if (player == null) {
            return;
        }
        // The movement guard's snap-back: re-assert the authoritative state.
        // Any thread; writeAndFlush is the channel's own ordering.
        sendPositionAndLook(channel, player.position(), player.rotation(), true);
        // THE TELEPORT WINDOW (the reference's networkHandler.teleport()
        // setting {@code teleported = false} — the wire's own teleport
        // order, both the engine's teleports and the rejected-movement
        // snap-backs): uses refuse until the client's movement
        // acknowledges the anchor (the d3 < 0.25 squared band the
        // engine's movement walk consults) — the use handler's own first
        // consult, handlePlayerUse line 475.
        player.markTeleportPending(player.position());
    }

    /** The abilities flag byte for a session's current game mode. */
    static int abilitiesFlagsOf(net.zaminmc.torch.GameMode mode) {
        int flags = 0;
        if (mode == net.zaminmc.torch.GameMode.CREATIVE
                || mode == net.zaminmc.torch.GameMode.SPECTATOR) {
            flags |= 0x01; // invulnerable
            flags |= 0x04; // may fly
            flags |= 0x08; // instant build (creative; harmless for spectators)
        }
        return flags;
    }

    // ------------------------------------------------------------------ chat + players

    /** Delivers a chat line to this client. Any thread. */
    void sendChatLine(String text, int position) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        ByteBuf out = Unpooled.buffer(64 + text.length());
        ByteBufOps.writeVarInt(out, Protocol18.S2C_CHAT);
        ByteBufOps.writeString(out, "{\"text\":\"" + jsonEscape(text) + "\"}");
        out.writeByte(position);
        enqueueOut(out);
    }

    private static String jsonEscape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }

    /**
     * Ensures this client sees {@code other} as an entity, spawning when newly visible.
     * Called during join exchange and movement sync.
     */
    void trackPlayer(PlayerSession other) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY || other == session) {
            return;
        }
        // Slice 64 — the cross-dimension gate (the reference's per-world
        // entity map): a body in another dimension is invisible, and the
        // credits body is removed from the world entirely.
        if (dimensionOfSession() != other.dimension() || other.leavingTheEnd()) {
            return;
        }
        // The spectating spectator never spawns for anyone else (the
        // reference's broadcastTo law, ServerPlayerEntity line 472: the
        // camera-following body is gated out of every other view — the
        // tracker's updatePlayer arm removes it when the gate flips). The
        // spectator's OWN view is untouched: it keeps receiving every
        // other body while its camera rides the target.
        if (other.spectatorCamera() != null) {
            return;
        }
        if (!visibleRemotePlayers.add(other.uuid())) {
            return; // already tracked
        }
        int entityId = remoteEntityIds.computeIfAbsent(other.uuid(),
                uuid -> adapter.nextEntityId());
        Position pos = other.position();
        ByteBuf out = Unpooled.buffer(48);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_NAMED_SPAWN);
        ByteBufOps.writeVarInt(out, entityId);
        // ML-021's fix: the uuid rides the wire as the reference's writeUuid
        // (two longs, 16 raw bytes — PacketByteBuf.writeUuid lines 112-115),
        // the same form the tab list already uses. The string form this site
        // carried (varint length + 36 chars) put 21 extra bytes in every
        // 0x0C: a strict 1.8.8 client reads 16 uuid bytes, then walks the
        // leftover string bytes as x/y/z/angles and enters the metadata
        // loop 21 bytes early — protodef's PartialReadError inside
        // entityMetadataItem (the cycle-3 observation), whose intermittence
        // was the luck of the garbage walk landing on the terminator.
        ByteBufOps.writeUuid(out, other.uuid());
        out.writeInt((int) Math.floor(pos.x() * 32.0));
        out.writeInt((int) Math.floor(pos.y() * 32.0));
        out.writeInt((int) Math.floor(pos.z() * 32.0));
        out.writeByte((int) (other.rotation().yaw() * 256.0f / 360.0f));
        out.writeByte((int) (other.rotation().pitch() * 256.0f / 360.0f));
        out.writeShort(0); // held item: none
        out.writeByte(Protocol18.METADATA_TERMINATOR); // no metadata entries (0x7F, protocol 47)
        enqueueOut(out);

        // The track-time attribute pair (the reference's updatePlayer
        // order — the attributes ride BEFORE the equipment, TrackedEntity
        // lines 322-328 ahead of the equipment arm at 348-355).
        sendPlayerAttributes(other);

        // The remote body's gear (held + the armor row) rides Entity
        // Equipment right after the spawn — the 1.8 rendering path.
        sendPlayerEquipment(other);
    }

    /**
     * The equipment broadcast for one remote player to this observer: held
     * item + the armor row (the wire order held, feet, legs, chest, head).
     * A no-op when this client does not track the player. Any thread.
     */
    void sendPlayerEquipment(PlayerSession other) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY
                || other == session || !visibleRemotePlayers.contains(other.uuid())) {
            return;
        }
        Integer entityId = remoteEntityIds.get(other.uuid());
        if (entityId == null) {
            return;
        }
        var inventory = other.inventory();
        writeEquipment(channel, entityId, Protocol18.EQUIPMENT_SLOT_HELD,
                inventory.held());
        writeEquipment(channel, entityId, Protocol18.EQUIPMENT_SLOT_FEET,
                inventory.armorAt(3));
        writeEquipment(channel, entityId, Protocol18.EQUIPMENT_SLOT_LEGS,
                inventory.armorAt(2));
        writeEquipment(channel, entityId, Protocol18.EQUIPMENT_SLOT_CHEST,
                inventory.armorAt(1));
        writeEquipment(channel, entityId, Protocol18.EQUIPMENT_SLOT_HEAD,
                inventory.armorAt(0));
    }

    /** One Entity Equipment packet (0x04): entity, wire slot, item slot. */
    private void writeEquipment(Channel channel, int entityId, int equipmentSlot,
                                net.zaminmc.torch.item.ItemStack stack) {
        ByteBuf out = Unpooled.buffer(24);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_ENTITY_EQUIPMENT);
        ByteBufOps.writeVarInt(out, entityId);
        out.writeShort(equipmentSlot);
        writeSlot(out, stack);
        enqueueOut(out);
    }

    // --- the attribute wire (ML-011 — Entity Properties 0x20, the
    // reference's EntityAttributesS2CPacket over the tracker's two send
    // sites: TrackedEntity.updatePlayer lines 322-328 at track time,
    // sendDataAndAttributes lines 260-274 on the change walk). ---

    /**
     * One 0x20 row: the attribute's own name, its base and the
     * equipment-applied modifier rows — the reference packet's Entry
     * (EntityAttributesS2CPacket lines 84-106: id, baseValue, modifiers).
     */
    private record WireAttribute(String name, double base,
            java.util.List<net.zaminmc.torch.item.AttributeModifier> modifiers) {
    }

    /**
     * The trackable pair the engine's bodies answer (the reference's own
     * isTrackable law — EntityAttributes lines 12-21: maxHealth and
     * movementSpeed ARE trackable, attackDamage is NOT, so only the
     * first two ever ride the 0x20): the two registrations every living
     * body carries, so the track-time send is never empty — exactly the
     * reference's getTrackable() walk over LivingEntity's registered
     * attribute map.
     */
    private static java.util.List<WireAttribute> playerWireAttributes(PlayerSession player) {
        return java.util.List.of(
                new WireAttribute(Protocol18.ATTRIBUTE_MAX_HEALTH,
                        net.zaminmc.torch.server.player.PlayerSession.MAX_HEALTH,
                        equipmentModifiers(Protocol18.ATTRIBUTE_MAX_HEALTH,
                                playerEquipment(player))),
                new WireAttribute(Protocol18.ATTRIBUTE_MOVEMENT_SPEED, 0.1,
                        // PlayerEntity line 141: the player's own registered
                        // movement-speed base.
                        equipmentModifiers(Protocol18.ATTRIBUTE_MOVEMENT_SPEED,
                                playerEquipment(player))));
    }

    /** The mob's trackable pair: the body's own health model + the walk consult (the override included). */
    private static java.util.List<WireAttribute> mobWireAttributes(
            net.zaminmc.torch.server.entity.MobEntity mob) {
        return java.util.List.of(
                new WireAttribute(Protocol18.ATTRIBUTE_MAX_HEALTH, mob.maxHealth(),
                        equipmentModifiers(Protocol18.ATTRIBUTE_MAX_HEALTH,
                                mobEquipment(mob))),
                new WireAttribute(Protocol18.ATTRIBUTE_MOVEMENT_SPEED, mob.walkSpeed(),
                        equipmentModifiers(Protocol18.ATTRIBUTE_MOVEMENT_SPEED,
                                mobEquipment(mob))));
    }

    /**
     * The player body's five equipment slots (the held slot + the armor
     * family) in the reference's getEquipment() order — held first, then
     * feet, legs, chest, head (the five-slot scan of LivingEntity.tick
     * lines 1289-1304 reads the same order).
     */
    private static net.zaminmc.torch.item.ItemStack[] playerEquipment(PlayerSession player) {
        var inventory = player.inventory();
        return new net.zaminmc.torch.item.ItemStack[] {
                inventory.held(), inventory.armorAt(3), inventory.armorAt(2),
                inventory.armorAt(1), inventory.armorAt(0)};
    }

    /** The mob body's five equipment slots — the same getEquipment() order (slot 0 held, 1..4 feet..head). */
    private static net.zaminmc.torch.item.ItemStack[] mobEquipment(
            net.zaminmc.torch.server.entity.MobEntity mob) {
        return new net.zaminmc.torch.item.ItemStack[] {
                mob.heldEquipment(), mob.equipment(1), mob.equipment(2),
                mob.equipment(3), mob.equipment(4)};
    }

    /**
     * The equipment rows naming ONE attribute (LivingEntity's equipment
     * scan applies every row to the attribute it names): held slot
     * first, the armor family after — a present-but-empty list (the
     * attribute-wipe) contributes nothing, exactly the fold's own law.
     */
    private static java.util.List<net.zaminmc.torch.item.AttributeModifier> equipmentModifiers(
            String attributeName, net.zaminmc.torch.item.ItemStack... equipment) {
        java.util.List<net.zaminmc.torch.item.AttributeModifier> modifiers =
                new java.util.ArrayList<>();
        for (net.zaminmc.torch.item.ItemStack stack : equipment) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            var rows = stack.attributeModifiers();
            if (rows == null) {
                continue; // the tagless fall-back: no rows ride the wire
            }
            for (var row : rows) {
                if (attributeName.equals(row.attributeName())) {
                    modifiers.add(row);
                }
            }
        }
        return modifiers;
    }

    /**
     * The Entity Properties body (EntityAttributesS2CPacket.write lines
     * 55-70, 1:1): VarInt entity id, Int property count, per property:
     * the name string, the Double base, the VarInt modifier count, per
     * modifier: the UUID (two big-endian longs), the Double amount, the
     * Byte operation.
     */
    private static ByteBuf encodeEntityProperties(int entityId,
            java.util.List<WireAttribute> attributes) {
        ByteBuf out = Unpooled.buffer(64);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_ENTITY_PROPERTIES);
        ByteBufOps.writeVarInt(out, entityId);
        out.writeInt(attributes.size());
        for (WireAttribute attribute : attributes) {
            ByteBufOps.writeString(out, attribute.name());
            out.writeDouble(attribute.base());
            ByteBufOps.writeVarInt(out, attribute.modifiers().size());
            for (net.zaminmc.torch.item.AttributeModifier modifier : attribute.modifiers()) {
                ByteBufOps.writeUuid(out, modifier.uuid());
                out.writeDouble(modifier.amount());
                out.writeByte(modifier.operation());
            }
        }
        return out;
    }

    /**
     * The remote player's Entity Properties (0x20 — the reference's
     * track-time arm, TrackedEntity.updatePlayer lines 322-328): the
     * trackable pair with the equipment-applied rows, resolved through
     * this observer's id space. A no-op when this client does not track
     * the player — a player never tracks its own body (the reference's
     * own updatePlayer gate). Any thread.
     */
    void sendPlayerAttributes(PlayerSession other) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY
                || other == session || !visibleRemotePlayers.contains(other.uuid())) {
            return;
        }
        Integer entityId = remoteEntityIds.get(other.uuid());
        if (entityId == null) {
            return;
        }
        enqueueOut(encodeEntityProperties(entityId, playerWireAttributes(other)));
    }

    /**
     * The mob's Entity Properties (0x20) re-publish (the reference's
     * change-time arm, sendDataAndAttributes lines 260-274 — the whole
     * trackable pair rides, the engine's whole-block re-publish
     * convention over the reference's dirty-set): the body's own health
     * + walk consult with the equipment rows. Chunk-gated like every
     * mob packet. Any thread.
     */
    void sendMobAttributes(net.zaminmc.torch.server.entity.MobEntity mob) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY
                || chunkTracker == null) {
            return;
        }
        // The mob's own dimension gates the observer (the same law as the
        // spawn: a nether body's attributes never reach an overworld view).
        if (dimensionOfSession() != mob.dimension()) {
            return;
        }
        if (!chunkTracker.hasChunk(chunkKeyOf(mob.position()))) {
            return; // observer cannot see that chunk yet
        }
        enqueueOut(encodeEntityProperties(mob.entityId(), mobWireAttributes(mob)));
    }


    /** Removes a remote player entity from this client (logout or move-away). */
    void untrackPlayer(UUID remoteUuid) {
        if (!visibleRemotePlayers.remove(remoteUuid)) {
            return;
        }
        Integer entityId = remoteEntityIds.remove(remoteUuid);
        Channel channel = adapter.channelOf(this);
        if (entityId == null || channel == null || !channel.isActive()) {
            return;
        }
        ByteBuf out = Unpooled.buffer(8);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_DESTROY_ENTITIES);
        ByteBufOps.writeVarInt(out, 1);
        ByteBufOps.writeVarInt(out, entityId);
        enqueueOut(out);
    }

    /**
     * The spectate attach's own wire (the reference's setCamera(target),
     * ServerPlayerEntity lines 916-923): the target must exist in this
     * client's world BEFORE the camera points at it — the client's
     * handleCamera consults its own entity map and drops the packet on an
     * unknown id (ClientPlayNetworkHandler lines 1174-1181) — so the
     * tracker's own spawn comes first, then the Camera 0x43 carrying the
     * target's wire id, then the world stream re-centers on the target
     * (the teleport's PlayerManager.move arm). Any thread (the listener's
     * tick-thread fan-out); ordering is the outbound queue's own FIFO.
     */
    void spectateCameraAttached(PlayerSession target) {
        trackPlayer(target);
        Integer wireId = remoteEntityIds.get(target.uuid());
        if (wireId != null) {
            sendCameraPacket(wireId);
        }
        recenterChunkView(target.position());
    }

    /**
     * The leave-spectate restore (the reference's setCamera(this)): the
     * Camera 0x43 re-points at the client's own join-game id, then the
     * authoritative Position-and-Look re-anchors the freed body where the
     * follow left it (the reference's teleport to {@code this.x/y/z}), the
     * teleport window arming exactly like the reference's teleport().
     * Any thread.
     */
    void spectateCameraDetached() {
        sendCameraPacket(ownEntityId);
        resyncPosition();
    }

    /**
     * Spectator Camera (0x43 — the reference's CameraS2CPacket.write, ONE
     * varint: the camera entity's id). Any thread.
     */
    private void sendCameraPacket(int wireEntityId) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        ByteBuf out = Unpooled.buffer(8);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_CAMERA);
        ByteBufOps.writeVarInt(out, wireEntityId);
        enqueueOut(out);
    }

    /**
     * The follow's chunk re-center: the tracker's center fields belong to
     * the movement lane (submitMovement runs on the channel's own loop), so
     * the tick-thread follow hops lanes before it moves them. Any thread.
     */
    private void recenterChunkView(Position at) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive()) {
            return;
        }
        channel.eventLoop().execute(() -> {
            if (chunkTracker != null) {
                chunkTracker.updateCenter(at.toBlockPosition().chunkPosition());
            }
        });
    }

    /**
     * The spectating camera's world-stream anchor (the engine's per-tick
     * follow — the spectating client sends no movement of its own, so the
     * target's position re-centers this observer's chunk stream every
     * tick). Any thread.
     */
    @Override
    public void spectateCameraFollow(Position targetPosition) {
        recenterChunkView(targetPosition);
    }

    /**
     * The spectator camera's own attach/restore verb (the reference's
     * setCamera pair, ServerPlayerEntity lines 916-923 — the packets ride
     * the player's own networkHandler there, the link here): the non-null
     * arm spawns the target for this client then points the Camera 0x43 at
     * it, the null arm restores the camera to this client's own body
     * (0x43 self + the authoritative re-anchor). Any thread.
     */
    @Override
    public void spectateCamera(UUID target) {
        if (target == null) {
            spectateCameraDetached();
            return;
        }
        // The mid-flight departure: the target left between the engine's
        // walk and this wire arm — nothing to point at (the engine's own
        // per-tick liveness consult restores the camera next tick).
        engine.playerRegistry().byUuid(target)
                .ifPresent(this::spectateCameraAttached);
    }

    /**
     * The remote-player movement relay over the tracked-move consult (the
     * reference's TrackedEntity bands — the players carry their angles): a
     * small move rides Rel Move Look (0x17) with the byte deltas and the
     * angle bytes, a rotation alone rides Entity Look (0x16), and the
     * window violation, the onGround flip, or the first publication keeps
     * the absolute Entity Teleport (0x18). Any thread.
     */
    void sendRemoteTeleport(PlayerSession other) {
        Channel channel = adapter.channelOf(this);
        Integer entityId = remoteEntityIds.get(other.uuid());
        if (channel == null || !channel.isActive() || entityId == null || other == session) {
            return;
        }
        Position pos = other.position();
        long x = (long) Math.floor(pos.x() * 32.0);
        long y = (long) Math.floor(pos.y() * 32.0);
        long z = (long) Math.floor(pos.z() * 32.0);
        int yaw = (int) (other.rotation().yaw() * 256.0f / 360.0f) & 0xFF;
        int pitch = (int) (other.rotation().pitch() * 256.0f / 360.0f) & 0xFF;
        long[] last = trackedMoves.get(entityId);
        if (last != null) {
            long dx = x - last[0];
            long dy = y - last[1];
            long dz = z - last[2];
            int dYaw = yaw - (int) last[4];
            int dPitch = pitch - (int) last[5];
            boolean groundFlipped = (last[3] != 0) != other.onGround();
            boolean moved = Math.abs(dx) >= 4 || Math.abs(dy) >= 4 || Math.abs(dz) >= 4;
            boolean rotated = Math.abs(dYaw) >= 4 || Math.abs(dPitch) >= 4;
            if (dx >= -128 && dx < 128 && dy >= -128 && dy < 128 && dz >= -128 && dz < 128
                    && !groundFlipped && (moved || rotated)) {
                ByteBuf out = Unpooled.buffer(16);
                if (moved && rotated) {
                    ByteBufOps.writeVarInt(out, Protocol18.S2C_REL_ENTITY_MOVE_LOOK);
                    ByteBufOps.writeVarInt(out, entityId);
                    out.writeByte((int) dx);
                    out.writeByte((int) dy);
                    out.writeByte((int) dz);
                    out.writeByte(yaw);
                    out.writeByte(pitch);
                } else if (moved) {
                    ByteBufOps.writeVarInt(out, Protocol18.S2C_REL_ENTITY_MOVE);
                    ByteBufOps.writeVarInt(out, entityId);
                    out.writeByte((int) dx);
                    out.writeByte((int) dy);
                    out.writeByte((int) dz);
                } else {
                    ByteBufOps.writeVarInt(out, Protocol18.S2C_ENTITY_LOOK);
                    ByteBufOps.writeVarInt(out, entityId);
                    out.writeByte(yaw);
                    out.writeByte(pitch);
                }
                out.writeBoolean(other.onGround());
                enqueueOut(out);
                if (moved) {
                    last[0] = x;
                    last[1] = y;
                    last[2] = z;
                }
                if (rotated) {
                    last[4] = yaw;
                    last[5] = pitch;
                }
                last[3] = other.onGround() ? 1 : 0;
                return;
            }
        }
        ByteBuf out = Unpooled.buffer(40);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_ENTITY_TELEPORT);
        ByteBufOps.writeVarInt(out, entityId);
        out.writeInt((int) x);
        out.writeInt((int) y);
        out.writeInt((int) z);
        out.writeByte(yaw);
        out.writeByte(pitch);
        out.writeBoolean(other.onGround());
        enqueueOut(out);
        trackedMoves.put(entityId, new long[] {x, y, z, other.onGround() ? 1 : 0, yaw, pitch});
    }

    UUID sessionUuid() {
        PlayerSession current = session;
        return current == null ? null : current.uuid();
    }

    PlayerSession currentSession() {
        return session;
    }

    /** Whether the connection reached the PLAY state (the composed-send gate). */
    boolean inPlayState() {
        return state == WireState.PLAY;
    }

    /**
     * One composed frame from the §27.3 construction surface: the varint
     * id + the payload, written through the shared pipeline — the
     * outbound interception walk sees it like any engine frame, then the
     * length encoder frames it. Any thread; never blocks.
     *
     * @return whether the write happened (the channel live and in PLAY)
     */
    boolean sendComposedFrame(int packetId, byte[] payload) {
        Channel wire = adapter.channelOf(this);
        if (wire == null || !wire.isActive() || state != WireState.PLAY) {
            return false;
        }
        // The shape-based ask (§35.4): a plugin payload at or past the
        // benchmark's large shape rides the pooled direct ask — the same
        // measured law the chunk-window frames follow; small payloads keep
        // the heap-unpooled ask that wins there.
        ByteBuf out = payload.length >= ChunkTracker.POOLED_ASK_THRESHOLD
                ? ChunkTracker.POOLED.directBuffer(payload.length + 8)
                : Unpooled.buffer(payload.length + 8);
        ByteBufOps.writeVarInt(out, packetId);
        out.writeBytes(payload);
        enqueueOut(out);
        return true;
    }

    // ------------------------------------------------------------------ outgoing sync

    /**
     * Pushes a committed world change to this client if the chunk is visible.
     * The immediate unicast form: the use walk's anti-desync resync cell
     * (the reference's own per-player direct send, ServerPlayNetworkHandler
     * lines 494-497 — a packet sent in the handler's own flow, never through
     * the ChunkMap accumulation). The committed-change BROADCAST queues
     * instead ({@link #queueBlockChange}); Netty writes are thread-safe,
     * tracker reads are synchronized.
     */
    void sendBlockChange(BlockPosition position, BlockType type) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        Integer legacy = LegacyBlockIds.legacyId(type.identifier()).orElse(null);
        if (legacy == null || chunkTracker == null
                || !chunkTracker.hasChunk(position.chunkPosition().packed())) {
            return;
        }
        ByteBuf out = Unpooled.buffer(16);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_BLOCK_CHANGE);
        ByteBufOps.writePackedBlockPosition(out, position.x(), position.y(), position.z());
        // Protocol 47 carries the packed block state (id << 4) | metadata —
        // community-verified against the real client's registry, where
        // blocksByStateId[54] is dirt and blocksByStateId[864] is the chest.
        ByteBufOps.writeVarInt(out, ChunkSerializer18.packedStateId(type));
        enqueueOut(out);
    }

    /**
     * ML-019 — the committed-change broadcast's queue arm (the reference's
     * ChunkMap.onBlockChanged, ChunkMap.java lines 102-109 and the
     * ChunkHolder accumulation lines 330-347): the change lands in this
     * connection's per-chunk pending buffer — a (localX << 12 | localZ << 8
     * | y) cell, deduplicated per position with the LATEST committed state
     * winning (the reference re-reads the chunk at flush; the last commit of
     * the tick is the same state), the dirty-section mask growing even past a
     * full buffer. The per-tick flush ({@link #flushPendingBlockChanges},
     * the engine's chunkMap section) picks the wire form: one cell the single
     * 0x23, 2-63 the 0x22 batch, a full 64 the partial 0x21 — the reference's
     * ChunkHolder.sendChanges arms (ChunkMap.java lines 358-401) verbatim.
     * Runs on the tick thread with the commit; tick-lane confined.
     */
    void queueBlockChange(BlockPosition position, BlockType type) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        if (LegacyBlockIds.legacyId(type.identifier()).isEmpty() || chunkTracker == null) {
            return;
        }
        chunkTracker.queueBlockChange(position, ChunkSerializer18.packedStateId(type));
    }

    /**
     * ML-019 — the per-tick drain of this connection's pending block-change
     * buffers (the engine's chunkMap section calls the adapter's registered
     * flush, the reference's ChunkMap.tick arm). Tick-lane confined like the
     * queue; a dead or non-PLAY connection drops its buffers whole.
     */
    void flushPendingBlockChanges() {
        if (chunkTracker != null) {
            chunkTracker.flushBlockChanges();
        }
    }

    /**
     * The Block Break Animation (0x28): one observer's view of another
     * player's dig. Stage 0-9 draws the cracking overlay; any other value
     * (the vanilla removal byte is 0xFF, written as {@code writeByte(-1)})
     * clears it. The digger never receives these — the vanilla client
     * predicts its own overlay locally.
     */
    void sendBlockMiningProgress(PlayerSession digger, BlockPosition position, int stage) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        PlayerSession current = session;
        if (current == null || current.uuid().equals(digger.uuid())
                || chunkTracker == null
                || !chunkTracker.hasChunk(position.chunkPosition().packed())) {
            return;
        }
        int entityId = digger.engineEntityId();
        if (entityId < 0) {
            return;
        }
        ByteBuf out = Unpooled.buffer(16);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_BLOCK_MINING_PROGRESS);
        ByteBufOps.writeVarInt(out, entityId);
        ByteBufOps.writePackedBlockPosition(out, position.x(), position.y(), position.z());
        out.writeByte(stage);
        enqueueOut(out);
    }

    /**
     * The Block Break Animation over a MOB's breaker id (VIII9 —
     * BreakDoorGoal.tick's {@code updateBlockMiningProgress(mobId, ...)},
     * the same packet the reference sends with the zombie's own network
     * id): the raw-id form over the engine's shared wire space, the
     * watcher gate the player form carries. The stage -1 clear rides the
     * same shape.
     */
    void sendMobBlockMiningProgress(int breakerEntityId, BlockPosition position, int stage) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        if (breakerEntityId < 0 || chunkTracker == null
                || !chunkTracker.hasChunk(position.chunkPosition().packed())) {
            return;
        }
        ByteBuf out = Unpooled.buffer(16);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_BLOCK_MINING_PROGRESS);
        ByteBufOps.writeVarInt(out, breakerEntityId);
        ByteBufOps.writePackedBlockPosition(out, position.x(), position.y(), position.z());
        out.writeByte(stage);
        enqueueOut(out);
    }

    // ------------------------------------------------------------------ inventory sync

    /**
     * Maps an engine inventory slot (0-8 hotbar, 9-35 main) to its wire slot in
     * the player inventory window (hotbar lives at 36-44 in protocol 47).
     */
    private static int wireSlotOf(int engineSlot) {
        return engineSlot < 9 ? Protocol18.WIRE_SLOT_HOTBAR_BASE + engineSlot : engineSlot;
    }

    /** Full authoritative inventory sync for window 0. Any thread; owner supplies the snapshot. */
    void sendWindowItems(Channel channel, java.util.List<net.zaminmc.torch.item.ItemStack> engineSlots,
                         java.util.List<net.zaminmc.torch.item.ItemStack> craftCells,
                         java.util.List<net.zaminmc.torch.item.ItemStack> armorSlots,
                         net.zaminmc.torch.item.ItemStack craftingResult) {
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        ByteBuf out = Unpooled.buffer(64 + engineSlots.size() * 6);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_WINDOW_ITEMS);
        out.writeByte(Protocol18.INVENTORY_WINDOW_ID);
        out.writeShort(Protocol18.INVENTORY_WINDOW_SLOTS);
        for (int wireSlot = 0; wireSlot < Protocol18.INVENTORY_WINDOW_SLOTS; wireSlot++) {
            // Wire 0 = crafting result preview; 1-4 = the 2x2 grid (row-major);
            // 5-8 = the armor row (head, chest, legs, feet); 9-35 = engine main
            // 9-35; 36-44 = engine hotbar 0-8.
            net.zaminmc.torch.item.ItemStack stack = engineSlotForWireSlot(wireSlot, engineSlots,
                    craftCells, armorSlots, craftingResult);
            writeSlot(out, stack);
        }
        enqueueOut(out);
    }

    private static net.zaminmc.torch.item.ItemStack engineSlotForWireSlot(
            int wireSlot, java.util.List<net.zaminmc.torch.item.ItemStack> engineSlots,
            java.util.List<net.zaminmc.torch.item.ItemStack> craftCells,
            java.util.List<net.zaminmc.torch.item.ItemStack> armorSlots,
            net.zaminmc.torch.item.ItemStack craftingResult) {
        if (wireSlot == Protocol18.WIRE_SLOT_RESULT) {
            return craftingResult;
        }
        if (wireSlot >= Protocol18.WIRE_SLOT_CRAFT_FIRST
                && wireSlot <= Protocol18.WIRE_SLOT_CRAFT_LAST) {
            return craftCells.get(wireSlot - Protocol18.WIRE_SLOT_CRAFT_FIRST);
        }
        if (wireSlot >= Protocol18.WIRE_SLOT_ARMOR_FIRST && wireSlot <= Protocol18.WIRE_SLOT_ARMOR_LAST) {
            return armorSlots.get(wireSlot - Protocol18.WIRE_SLOT_ARMOR_FIRST);
        }
        int engineSlot = switch (wireSlot) {
            case 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26,
                 27, 28, 29, 30, 31, 32, 33, 34, 35 -> wireSlot;
            case 36, 37, 38, 39, 40, 41, 42, 43, 44 -> wireSlot - Protocol18.WIRE_SLOT_HOTBAR_BASE;
            default -> -1;
        };
        return engineSlot < 0 ? net.zaminmc.torch.item.ItemStack.EMPTY : engineSlots.get(engineSlot);
    }

    /**
     * Full authoritative sync of an open container window; the layout follows
     * the container kind (crafting table 46 slots, furnace 39 slots). Any
     * thread; owner supplies the session state.
     */
    void sendContainerWindowItems(Channel channel, int windowId, PlayerSession player) {
        if (player.openContainerKind()
                == net.zaminmc.torch.server.player.PlayerSession.ContainerKind.FURNACE) {
            var position = player.openContainerPosition();
            var furnace = position == null ? null : engine.furnaces().peek(position);
            if (furnace != null) {
                sendFurnaceWindowItems(channel, windowId, furnace, player);
            }
            return;
        }
        if (player.openContainerKind()
                == net.zaminmc.torch.server.player.PlayerSession.ContainerKind.CHEST) {
            var position = player.openContainerPosition();
            var chest = position == null ? null : engine.chests().peek(position);
            if (chest != null) {
                sendChestWindowItems(channel, windowId, chest, player);
            }
            return;
        }
        if (player.openContainerKind()
                == net.zaminmc.torch.server.player.PlayerSession.ContainerKind.ENDER_CHEST) {
            // The container slots read the session's own grid (Slice 29a —
            // the block at the position is the door, not the storage).
            sendChestWindowItems(channel, windowId, player.enderChest(), player);
            return;
        }
        if (player.openContainerKind()
                == net.zaminmc.torch.server.player.PlayerSession.ContainerKind.BEACON) {
            var position = player.openContainerPosition();
            var beacon = position == null ? null : engine.beacons().peek(position);
            if (beacon != null) {
                sendBeaconWindowItems(channel, windowId, beacon, player);
            }
            return;
        }
        if (player.openContainerKind()
                == net.zaminmc.torch.server.player.PlayerSession.ContainerKind.CHEST_MINECART) {
            var cart = engineVehicleChest(player.containerMountId());
            if (cart != null) {
                sendChestWindowItems(channel, windowId, cart, player);
            }
            return;
        }
        if (player.openContainerKind()
                == net.zaminmc.torch.server.player.PlayerSession.ContainerKind.HOPPER_MINECART) {
            var cart = engineVehicleHopper(player.containerMountId());
            if (cart != null) {
                sendHopperWindowItems(channel, windowId, cart, player);
            }
            return;
        }
        if (player.openContainerKind()
                == net.zaminmc.torch.server.player.PlayerSession.ContainerKind.DISPENSER) {
            var position = player.openContainerPosition();
            var container = position == null ? null : engine.dispensers().peek(position);
            if (container != null) {
                sendDispenserWindowItems(channel, windowId, container, player);
            }
            return;
        }
        if (player.openContainerKind()
                == net.zaminmc.torch.server.player.PlayerSession.ContainerKind.HOPPER) {
            var position = player.openContainerPosition();
            var hopper = position == null ? null : engine.hopper().peek(position);
            if (hopper != null) {
                sendHopperWindowItems(channel, windowId, hopper, player);
            }
            return;
        }
        if (player.openContainerKind()
                == net.zaminmc.torch.server.player.PlayerSession.ContainerKind.BREWING_STAND) {
            var position = player.openContainerPosition();
            var stand = position == null ? null : engine.brewingStands().peek(position);
            if (stand != null) {
                sendBrewingWindowItems(channel, windowId, stand, player);
            }
            return;
        }
        if (player.openContainerKind()
                == net.zaminmc.torch.server.player.PlayerSession.ContainerKind.HORSE) {
            var mount = engine.mobs().byId(player.containerMountId());
            if (mount != null) {
                sendHorseWindowItems(channel, windowId, mount, player);
            }
            return;
        }
        if (player.openContainerKind()
                == net.zaminmc.torch.server.player.PlayerSession.ContainerKind.ENCHANTING_TABLE) {
            sendEnchantingWindowItems(channel, windowId, player);
            return;
        }
        sendCraftingTableWindowItems(channel, windowId, player);
    }

    /**
     * Open Window (0x2D) for the villager trading GUI ("minecraft:villager",
     * 3 GUI slots: buy1, buy2, result), then the MC|TrList plugin message
     * (0x3F) — protocol 47 has no dedicated Trade List packet; the offers
     * ride the plugin channel. The payload's windowId is a plain i32 (the
     * 1.8 quirk ProtocolSupport documents). Called on the tick thread.
     */
    void sendVillagerWindow(Channel channel, PlayerSession player,
                            int windowId, MobEntity villager) {
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        ByteBuf out = Unpooled.buffer(48);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_OPEN_WINDOW);
        out.writeByte(windowId);
        ByteBufOps.writeString(out, Protocol18.VILLAGER_WINDOW_TYPE);
        ByteBufOps.writeString(out, Protocol18.VILLAGER_WINDOW_TITLE);
        out.writeByte(Protocol18.VILLAGER_WINDOW_GUI_SLOTS);
        enqueueOut(out);
        sendTradeList(channel, windowId, villager);
    }

    /** The MC|TrList payload: i32 windowId, u8 count, per offer the 1.8 record. */
    private void sendTradeList(Channel channel, int windowId, MobEntity villager) {
        net.zaminmc.torch.server.entity.TradeOffer[] offers = villager.offers();
        ByteBuf payload = Unpooled.buffer(64);
        payload.writeInt(windowId); // the plain int (not a varint — the 1.8 quirk)
        payload.writeByte(offers.length);
        for (int i = 0; i < offers.length; i++) {
            net.zaminmc.torch.server.entity.TradeOffer offer = offers[i];
            writeSlot(payload, offer.buy1());
            writeSlot(payload, offer.result());
            payload.writeBoolean(offer.hasSecondBuy());
            if (offer.hasSecondBuy()) {
                writeSlot(payload, offer.buy2());
            }
            boolean spentOut = !villager.offerAvailable(i);
            payload.writeBoolean(spentOut); // disabled (the greyed row)
            payload.writeInt(villager.offerUses(i)); // uses (the live count)
            payload.writeInt(villager.offerMaxUses(i)); // maxUses (the extended budget)
        }
        ByteBuf out = Unpooled.buffer(24 + payload.readableBytes());
        ByteBufOps.writeVarInt(out, Protocol18.S2C_PLUGIN_MESSAGE);
        ByteBufOps.writeString(out, Protocol18.PLUGIN_CHANNEL_TRADE_LIST);
        out.writeBytes(payload);
        enqueueOut(out);
    }

    /** Set Slot (0x2F) on the villager window's result slot (the trade preview). */
    void sendTradeResult(PlayerSession player, int windowId,
                         net.zaminmc.torch.item.ItemStack result) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        ByteBuf out = Unpooled.buffer(24);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_SET_SLOT);
        out.writeByte(windowId);
        out.writeShort(2); // the result slot of the merchant GUI
        writeSlot(out, result);
        enqueueOut(out);
    }

    /**
     * Open Window (0x2D) for the horse inventory — the quirky "EntityHorse"
     * type string plus the trailing mount entity id (the only Open Window
     * that carries one), then the authoritative 38-slot contents. Called on
     * the tick thread by the engine's open dispatch.
     */
    void sendHorseWindow(Channel channel, PlayerSession player,
                         int windowId, MobEntity mount) {
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        ByteBuf out = Unpooled.buffer(48);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_OPEN_WINDOW);
        out.writeByte(windowId);
        ByteBufOps.writeString(out, Protocol18.HORSE_WINDOW_TYPE);
        ByteBufOps.writeString(out, Protocol18.HORSE_WINDOW_TITLE);
        // The vanilla SPacketOpenWindow carries the full menu size — the
        // HorseMenu's inventorySlots.size(): 38 bare, 53 with the chest grid.
        out.writeByte(mount.chested()
                ? Protocol18.HORSE_CHEST_WINDOW_TOTAL_SLOTS
                : Protocol18.HORSE_WINDOW_TOTAL_SLOTS);
        out.writeInt(mount.entityId()); // the trailing mount id (the EntityHorse quirk)
        enqueueOut(out);
        sendHorseWindowItems(channel, windowId, mount, player);
    }

    /**
     * Full authoritative sync of the horse window: 38 slots — 0 the saddle
     * row, 1 the armor row (both live on the mount), 2-28 main inventory
     * (engine 9-35), 29-37 hotbar (engine 0-8). A chested mount grows the
     * window to 53: the 3x5 chest grid rides 2-16 (the HorseMenu layout),
     * the player tail shifts to 17-43 main / 44-52 hotbar. Any thread.
     */
    private void sendHorseWindowItems(Channel channel, int windowId,
                                      MobEntity mount, PlayerSession player) {
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        boolean chested = mount.chested();
        int totalSlots = chested
                ? Protocol18.HORSE_CHEST_WINDOW_TOTAL_SLOTS
                : Protocol18.HORSE_WINDOW_TOTAL_SLOTS;
        ByteBuf out = Unpooled.buffer(96);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_WINDOW_ITEMS);
        out.writeByte(windowId);
        out.writeShort(totalSlots);
        var inventory = player == null ? java.util.List.<net.zaminmc.torch.item.ItemStack>of()
                : player.inventory().snapshot();
        for (int wireSlot = 0; wireSlot < totalSlots; wireSlot++) {
            net.zaminmc.torch.item.ItemStack stack;
            if (wireSlot == Protocol18.HORSE_WIRE_SLOT_SADDLE) {
                stack = mount.saddled()
                        ? net.zaminmc.torch.item.ItemStack.of(
                        net.zaminmc.torch.server.item.BuiltinItems.SADDLE, 1)
                        : net.zaminmc.torch.item.ItemStack.EMPTY;
            } else if (wireSlot == Protocol18.HORSE_WIRE_SLOT_ARMOR) {
                stack = horseArmorStack(mount.armorType());
            } else if (chested && wireSlot >= Protocol18.HORSE_CHEST_WIRE_SLOT_FIRST
                    && wireSlot <= Protocol18.HORSE_CHEST_WIRE_SLOT_LAST) {
                stack = mount.chestSlot(wireSlot - Protocol18.HORSE_CHEST_WIRE_SLOT_FIRST);
            } else if (chested) {
                // The shifted player tail: main 17-43 (engine 9-35), hotbar 44-52.
                stack = wireSlot >= Protocol18.HORSE_CHEST_WIRE_PLAYER_FIRST
                        && wireSlot <= Protocol18.HORSE_CHEST_WIRE_PLAYER_MAIN_LAST
                        ? inventory.get(wireSlot - 8) // main inventory: engine 9-35
                        : inventory.get(wireSlot - Protocol18.HORSE_CHEST_WIRE_PLAYER_HOTBAR_FIRST);
            } else if (wireSlot <= 28) {
                stack = inventory.get(wireSlot + 7); // main inventory: engine 9-35
            } else {
                stack = inventory.get(wireSlot - 29); // hotbar: engine 0-8
            }
            writeSlot(out, stack);
        }
        enqueueOut(out);
    }

    /** @return the window stack of a mount's armor row (empty when none). */
    private static net.zaminmc.torch.item.ItemStack horseArmorStack(int row) {
        return switch (row) {
            case 3 -> net.zaminmc.torch.item.ItemStack.of(
                    net.zaminmc.torch.server.item.BuiltinItems.DIAMOND_HORSE_ARMOR, 1);
            case 2 -> net.zaminmc.torch.item.ItemStack.of(
                    net.zaminmc.torch.server.item.BuiltinItems.GOLDEN_HORSE_ARMOR, 1);
            case 1 -> net.zaminmc.torch.item.ItemStack.of(
                    net.zaminmc.torch.server.item.BuiltinItems.IRON_HORSE_ARMOR, 1);
            default -> net.zaminmc.torch.item.ItemStack.EMPTY;
        };
    }

    /**
     * Full authoritative sync of the chest window: 63 slots — 0-26 the chest's
     * own state (community-verified layout), 27-53 main inventory (engine
     * 9-35), 54-62 hotbar (engine 0-8) — the viewer's inventory. Any thread.
     */
    private void sendChestWindowItems(Channel channel, int windowId,
                                      net.zaminmc.torch.server.chest.ChestBlockEntity chest,
                                      PlayerSession player) {
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        ByteBuf out = Unpooled.buffer(96);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_WINDOW_ITEMS);
        out.writeByte(windowId);
        out.writeShort(Protocol18.CHEST_WINDOW_SLOTS);
        var chestSlots = chest.snapshotSlots();
        java.util.List<net.zaminmc.torch.item.ItemStack> inventory =
                player == null ? java.util.List.of() : player.inventory().snapshot();
        for (int wireSlot = 0; wireSlot < Protocol18.CHEST_WINDOW_SLOTS; wireSlot++) {
            net.zaminmc.torch.item.ItemStack stack;
            if (wireSlot <= Protocol18.CHEST_WIRE_SLOT_LAST) {
                stack = chestSlots[wireSlot];
            } else if (wireSlot <= Protocol18.CHEST_WIRE_SLOT_MAIN_LAST) {
                stack = inventory.get(wireSlot - 18); // main inventory: engine 9-35
            } else {
                stack = inventory.get(wireSlot - Protocol18.CHEST_WIRE_SLOT_HOTBAR_BASE);
            }
            writeSlot(out, stack);
        }
        enqueueOut(out);
    }

    /**
     * Full authoritative sync of the dispenser/dropper window (Slice 11a):
     * 45 slots — 0-8 the container, 9-35 main, 36-44 hotbar. Any thread;
     * owner supplies the session state.
     */
    private void sendDispenserWindowItems(Channel channel, int windowId,
                                          net.zaminmc.torch.server.dispenser.DispenserBlockEntity container,
                                          PlayerSession player) {
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        ByteBuf out = Unpooled.buffer(96);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_WINDOW_ITEMS);
        out.writeByte(windowId);
        out.writeShort(Protocol18.DISPENSER_WINDOW_SLOTS);
        var containerSlots = container.snapshotSlots();
        java.util.List<net.zaminmc.torch.item.ItemStack> inventory =
                player == null ? java.util.List.of() : player.inventory().snapshot();
        for (int wireSlot = 0; wireSlot < Protocol18.DISPENSER_WINDOW_SLOTS; wireSlot++) {
            net.zaminmc.torch.item.ItemStack stack;
            if (wireSlot <= Protocol18.DISPENSER_WIRE_SLOT_LAST) {
                stack = containerSlots[wireSlot];
            } else if (wireSlot <= Protocol18.DISPENSER_WIRE_SLOT_MAIN_LAST) {
                // main: wire 9-35 == engine 9-35 (the layout happens to align)
                stack = inventory.get(wireSlot);
            } else {
                stack = inventory.get(wireSlot - Protocol18.DISPENSER_WIRE_SLOT_HOTBAR_FIRST);
            }
            writeSlot(out, stack);
        }
        enqueueOut(out);
    }

    /**
     * Full authoritative sync of the hopper window (the reference's
     * HopperMenu): 41 slots — 0-4 the container, 5-31 main (engine 9-35),
     * 32-40 hotbar (engine 0-8). Any thread; owner supplies the session
     * state.
     */
    private void sendHopperWindowItems(Channel channel, int windowId,
                                       net.zaminmc.torch.server.hopper.HopperBlockEntity hopper,
                                       PlayerSession player) {
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        ByteBuf out = Unpooled.buffer(96);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_WINDOW_ITEMS);
        out.writeByte(windowId);
        out.writeShort(Protocol18.HOPPER_WINDOW_SLOTS);
        var containerSlots = hopper.snapshotSlots();
        java.util.List<net.zaminmc.torch.item.ItemStack> inventory =
                player == null ? java.util.List.of() : player.inventory().snapshot();
        for (int wireSlot = 0; wireSlot < Protocol18.HOPPER_WINDOW_SLOTS; wireSlot++) {
            net.zaminmc.torch.item.ItemStack stack;
            if (wireSlot <= Protocol18.HOPPER_WIRE_SLOT_LAST) {
                stack = containerSlots[wireSlot];
            } else if (wireSlot <= Protocol18.HOPPER_WIRE_SLOT_MAIN_LAST) {
                // main: wire 5-31 == engine 9-35 (offset by the hotbar band)
                stack = inventory.get(wireSlot - Protocol18.HOPPER_WIRE_SLOT_MAIN_FIRST + 9);
            } else {
                stack = inventory.get(wireSlot - Protocol18.HOPPER_WIRE_SLOT_HOTBAR_FIRST);
            }
            writeSlot(out, stack);
        }
        enqueueOut(out);
    }

    /**
     * Full authoritative sync of the crafting-table window: 46 slots — 0
     * result preview, 1-9 the 3x3 grid, 10-36 main, 37-45 hotbar. Any thread;
     * owner supplies the session state.
     */
    private void sendCraftingTableWindowItems(Channel channel, int windowId, PlayerSession player) {
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        var engine = this.engine;
        ByteBuf out = Unpooled.buffer(96);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_WINDOW_ITEMS);
        out.writeByte(windowId);
        out.writeShort(Protocol18.TABLE_WINDOW_SLOTS);
        java.util.List<net.zaminmc.torch.item.ItemStack> slots = player.inventory().snapshot();
        java.util.List<net.zaminmc.torch.item.ItemStack> grid = player.tableCrafting().snapshot();
        net.zaminmc.torch.item.ItemStack result = engine.containerResult(player);
        for (int wireSlot = 0; wireSlot < Protocol18.TABLE_WINDOW_SLOTS; wireSlot++) {
            writeSlot(out, containerSlotForWireSlot(wireSlot, slots, grid, result));
        }
        enqueueOut(out);
    }

    /**
     * Full authoritative sync of the furnace window: 39 slots — 0 input,
     * 1 fuel, 2 output (the block entity's world state), 3-29 main, 30-38
     * hotbar (the viewer's inventory). Any thread.
     */
    private void sendFurnaceWindowItems(Channel channel, int windowId,
                                        net.zaminmc.torch.server.furnace.FurnaceBlockEntity furnace,
                                        PlayerSession player) {
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        ByteBuf out = Unpooled.buffer(96);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_WINDOW_ITEMS);
        out.writeByte(windowId);
        out.writeShort(Protocol18.FURNACE_WINDOW_SLOTS);
        var furnaceSlots = furnace.snapshotSlots();
        java.util.List<net.zaminmc.torch.item.ItemStack> inventory =
                player == null ? java.util.List.of() : player.inventory().snapshot();
        for (int wireSlot = 0; wireSlot < Protocol18.FURNACE_WINDOW_SLOTS; wireSlot++) {
            net.zaminmc.torch.item.ItemStack stack;
            if (wireSlot <= 2) {
                stack = furnaceSlots[wireSlot];
            } else if (wireSlot <= Protocol18.FURNACE_WIRE_SLOT_MAIN_LAST) {
                stack = inventory.get(wireSlot + 6); // main inventory: engine 9-35
            } else {
                stack = inventory.get(wireSlot - Protocol18.FURNACE_WIRE_SLOT_HOTBAR_BASE);
            }
            writeSlot(out, stack);
        }
        enqueueOut(out);
    }

    private static net.zaminmc.torch.item.ItemStack containerSlotForWireSlot(
            int wireSlot, java.util.List<net.zaminmc.torch.item.ItemStack> engineSlots,
            java.util.List<net.zaminmc.torch.item.ItemStack> gridCells,
            net.zaminmc.torch.item.ItemStack containerResult) {
        if (wireSlot == 0) {
            return containerResult;
        }
        if (wireSlot >= Protocol18.TABLE_WIRE_SLOT_GRID_FIRST
                && wireSlot <= Protocol18.TABLE_WIRE_SLOT_GRID_LAST) {
            return gridCells.get(wireSlot - Protocol18.TABLE_WIRE_SLOT_GRID_FIRST);
        }
        if (wireSlot >= Protocol18.TABLE_WIRE_SLOT_MAIN_FIRST
                && wireSlot <= Protocol18.TABLE_WIRE_SLOT_MAIN_LAST) {
            return engineSlots.get(wireSlot - 1); // main inventory: engine 9-35
        }
        if (wireSlot >= Protocol18.TABLE_WIRE_SLOT_HOTBAR_FIRST
                && wireSlot <= Protocol18.TABLE_WIRE_SLOT_HOTBAR_LAST) {
            return engineSlots.get(wireSlot - Protocol18.TABLE_WIRE_SLOT_HOTBAR_BASE);
        }
        return net.zaminmc.torch.item.ItemStack.EMPTY;
    }

    /** Single authoritative slot update in window 0. */
    void sendSetSlot(net.zaminmc.torch.item.ItemStack stack, int engineSlot) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        ByteBuf out = Unpooled.buffer(12);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_SET_SLOT);
        out.writeByte(Protocol18.INVENTORY_WINDOW_ID);
        out.writeShort(wireSlotOf(engineSlot));
        writeSlot(out, stack);
        enqueueOut(out);
    }

    /**
     * 1.8 slot encoding (community-verified against the real client's parser,
     * protodef's optionalNbt + vanilla {@code PacketBuffer.writeItemStackToBuffer}):
     * i16 block/item id, -1 = empty; otherwise i8 count, i16 damage, then the
     * NBT marker — a single 0x00 byte (TAG_End) when the stack carries no
     * compound, or a full NBT payload starting with its type byte. Writing the
     * historical short -1 here desyncs every real parser (mineflayer caught it).
     */
    private static void writeSlot(ByteBuf out, net.zaminmc.torch.item.ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            out.writeShort(-1);
            return;
        }
        Integer legacy = LegacyBlockIds.legacyItemId(stack.type().identifier()).orElse(null);
        if (legacy == null) {
            out.writeShort(-1); // unmappable item: represent as empty on this wire
            return;
        }
        out.writeShort(legacy);
        out.writeByte(stack.count());
        out.writeShort(stack.damage()); // durability wear / variant metadata
        if (SlotNbt.hasPayload(stack)) {
            SlotNbt.writeFor(out, stack);
        } else {
            out.writeByte(0); // no NBT: the single TAG_End marker
        }
    }

    /** Re-sends one tracked chunk column (the relight transport, §475). Tick thread. */
    void sendChunkRelight(ChunkPosition position) {
        if (chunkTracker != null) {
            chunkTracker.resendChunk(position);
        }
    }

    // ------------------------------------------------------------------ chest lid + held item

    /**
     * Block Action (0x24, community-verified layout: i64 packed position,
     * u8 action, u8 param, u32 block type) for the chest lid: action 1,
     * param 1 open / 0 close, block 54. The opener's own client animates
     * its view locally, so the engine excludes it from this fan-out.
     * Any thread.
     */
    void sendChestLid(net.zaminmc.torch.block.BlockPosition position, PlayerSession opener,
                      boolean open) {
        if (opener == session) {
            return; // the opener's client plays its own lid
        }
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY
                || chunkTracker == null) {
            return;
        }
        if (!chunkTracker.hasChunk(position.chunkPosition().packed())) {
            return; // the observer cannot see that chest
        }
        ByteBuf out = Unpooled.buffer(16);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_BLOCK_ACTION);
        ByteBufOps.writePackedBlockPosition(out, position.x(), position.y(), position.z());
        out.writeByte(Protocol18.BLOCK_ACTION_CHEST_LID);
        out.writeByte(open ? 1 : 0);
        // The carrier is the block that actually sits there (Slice 29a — the
        // ender chest's own BE broadcasts over legacy 130; the plain chest's
        // over 54; the trapped chest's over 146 — ChestBlock(1)'s own id,
        // the reference's doBlockEvent carrier).
        int carrier = Protocol18.CHEST_LEGACY_BLOCK_ID;
        if (engine.world() != null && engine.world().getBlock(position) != null) {
            String carrierValue = engine.world().getBlock(position).identifier().value();
            if (net.zaminmc.torch.server.chest.EnderChestBlocks.isFamily(
                    engine.world().getBlock(position))) {
                carrier = Protocol18.ENDER_CHEST_LEGACY_BLOCK_ID;
            } else if (carrierValue.equals("trapped_chest")
                    || carrierValue.startsWith("trapped_chest_")) {
                carrier = Protocol18.TRAPPED_CHEST_LEGACY_BLOCK_ID;
            }
        }
        out.writeInt(carrier);
        enqueueOut(out);
    }

    /**
     * Block Action (0x24, the same community-verified layout) for the note
     * block (Slice 27a — ServerWorld.doBlockEvents lines 820-842): the
     * instrument as the action byte, the note as the param byte, block 25
     * — the client plays its own sound and spawns the note particle. Any
     * thread.
     */
    void sendNoteAction(net.zaminmc.torch.block.BlockPosition position,
                        int instrument, int note) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY
                || chunkTracker == null) {
            return;
        }
        if (!chunkTracker.hasChunk(position.chunkPosition().packed())) {
            return; // the observer cannot see that note block
        }
        ByteBuf out = Unpooled.buffer(16);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_BLOCK_ACTION);
        ByteBufOps.writePackedBlockPosition(out, position.x(), position.y(), position.z());
        out.writeByte(instrument);
        out.writeByte(note);
        out.writeInt(Protocol18.NOTE_BLOCK_LEGACY_BLOCK_ID);
        enqueueOut(out);
    }

    /**
     * Update Block Entity (0x35, type 1) for one mob spawner
     * (MobSpawnerBlockEntity.createUpdatePacket lines 56-61): the position,
     * the tile type byte, and the NBT compound — the reference's writeNbt
     * family (EntityId + the timing shorts + SpawnData, SpawnPotentials
     * removed) — the client spins the display entity from it. Any thread.
     */
    void sendSpawnerTile(
            net.zaminmc.torch.server.spawner.SpawnerManager.SpawnerTile tile) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        enqueueOut(encodeSpawnerTile(tile));
    }

    /** The tile packet's own writer (the static chunk-send path's form). */
    private static ByteBuf encodeSpawnerTile(
            net.zaminmc.torch.server.spawner.SpawnerManager.SpawnerTile tile) {
        net.zaminmc.torch.block.BlockPosition position = tile.position();
        String name = wireEntityNameOf(tile.type());
        ByteBuf out = Unpooled.buffer(96);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_UPDATE_BLOCK_ENTITY);
        ByteBufOps.writePackedBlockPosition(out, position.x(), position.y(), position.z());
        out.writeByte(Protocol18.BLOCK_ENTITY_TYPE_MOB_SPAWNER);
        // The NBT compound (the root: type byte + the empty short-length
        // name, then the named tags in the reference's own order).
        out.writeByte(10);              // TAG_Compound (the root marker)
        out.writeShort(0);              // the root name: empty
        writeNbtInt(out, "x", position.x());
        writeNbtInt(out, "y", position.y());
        writeNbtInt(out, "z", position.z());
        writeNbtShort(out, "Delay", tile.delay());
        // The cadence rows (writeNbt lines 231-236) — the spawner's OWN
        // values (Slice 91 Wave C: the block-event-1 spin reset reads the
        // client's minSpawnDelay from these rows).
        writeNbtShort(out, "MinSpawnDelay", tile.minSpawnDelay());
        writeNbtShort(out, "MaxSpawnDelay", tile.maxSpawnDelay());
        writeNbtShort(out, "SpawnCount", tile.spawnCount());
        writeNbtShort(out, "MaxNearbyEntities", tile.maxNearbyEntities());
        writeNbtShort(out, "RequiredPlayerRange", tile.requiredPlayerRange());
        writeNbtShort(out, "SpawnRange", tile.spawnRange());
        writeNbtString(out, "EntityId", name);
        // SpawnData (writeNbt line 240): the next entry's properties — the
        // client's display entity reads the "id" member.
        out.writeByte(10);              // TAG_Compound
        SlotNbt.writeShortString(out, "SpawnData");
        writeNbtString(out, "id", name);
        out.writeByte(0);               // end of SpawnData
        out.writeByte(0);               // end of the root
        return out;
    }

    /** One named NBT Int (tag 3). */
    /** The TAG_Byte row (the skull's SkullType/Rot pair). */
    private static void writeNbtByte(ByteBuf out, String name, int value) {
        out.writeByte(1);              // TAG_Byte
        SlotNbt.writeShortString(out, name);
        out.writeByte(value);
    }

    private static void writeNbtInt(ByteBuf out, String name, int value) {
        out.writeByte(3);
        SlotNbt.writeShortString(out, name);
        out.writeInt(value);
    }

    /** One named NBT Short (tag 2). */
    private static void writeNbtShort(ByteBuf out, String name, int value) {
        out.writeByte(2);
        SlotNbt.writeShortString(out, name);
        out.writeShort(value);
    }

    /** One named NBT String (tag 8) — the short-length modified UTF-8. */
    private static void writeNbtString(ByteBuf out, String name, String value) {
        out.writeByte(8);
        SlotNbt.writeShortString(out, name);
        SlotNbt.writeShortString(out, value);
    }

    /**
     * The fired cycle's flames (MobSpawner.tick line 108:
     * {@code world.doEvent(2004, blockpos, 0)}): the Effect packet with the
     * spawner position — the client draws the burst itself. Chunk-gated.
     * Any thread.
     */
    void sendSpawnerFlames(net.zaminmc.torch.block.BlockPosition position) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY
                || chunkTracker == null) {
            return;
        }
        if (!chunkTracker.hasChunk(position.chunkPosition().packed())) {
            return; // the observer cannot see that spawner
        }
        ByteBuf out = Unpooled.buffer(20);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_WORLD_EVENT);
        out.writeInt(Protocol18.WORLD_EVENT_SPAWNER_FLAMES);
        ByteBufOps.writePackedBlockPosition(out, position.x(), position.y(), position.z());
        out.writeInt(0); // data
        out.writeBoolean(false);
        enqueueOut(out);
    }

    /**
     * The delay reroll's spin reset (delay line 187 → broadcastEvent(1) →
     * addBlockEvent(pos, MOB_SPAWNER, 1, 0)): the Block Action packet —
     * the client resets its spin rate to the min-delay band. Chunk-gated.
     * Any thread.
     */
    void sendSpawnerSpinReset(net.zaminmc.torch.block.BlockPosition position) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY
                || chunkTracker == null) {
            return;
        }
        if (!chunkTracker.hasChunk(position.chunkPosition().packed())) {
            return; // the observer cannot see that spawner
        }
        ByteBuf out = Unpooled.buffer(16);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_BLOCK_ACTION);
        ByteBufOps.writePackedBlockPosition(out, position.x(), position.y(), position.z());
        out.writeByte(1);  // the event: the spin reset
        out.writeByte(0);  // the param
        out.writeInt(Protocol18.SPAWNER_LEGACY_BLOCK_ID);
        enqueueOut(out);
    }

    /**
     * The reference's own entity registration names (Entities.java lines
     * 255-286): the wire's {@code EntityId} strings the 1.8.8 client
     * resolves through its own Entities table — "PigZombie" for the
     * pigman, "LavaSlime" for the magma cube, "MushroomCow" for the
     * mooshroom, "SnowMan" for the snow golem, "Ozelot" for the ocelot
     * (the reference's own spellings).
     */
    static String wireEntityNameOf(net.zaminmc.torch.server.entity.MobType type) {
        return switch (type) {
            case CREEPER -> "Creeper";
            case SKELETON -> "Skeleton";
            case SPIDER -> "Spider";
            case ZOMBIE -> "Zombie";
            case SLIME -> "Slime";
            case GHAST -> "Ghast";
            case ZOMBIE_PIGMAN -> "PigZombie";
            case ENDERMAN -> "Enderman";
            case CAVE_SPIDER -> "CaveSpider";
            case SILVERFISH -> "Silverfish";
            case BLAZE -> "Blaze";
            case MAGMA_CUBE -> "LavaSlime";
            case BAT -> "Bat";
            case WITCH -> "Witch";
            case ENDERMITE -> "Endermite";
            case GUARDIAN -> "Guardian";
            case GIANT -> "Giant";
            case PIG -> "Pig";
            case SHEEP -> "Sheep";
            case COW -> "Cow";
            case CHICKEN -> "Chicken";
            case SQUID -> "Squid";
            case WOLF -> "Wolf";
            case MOOSHROOM -> "MushroomCow";
            case SNOW_GOLEM -> "SnowMan";
            case OCELOT -> "Ozelot";
            case IRON_GOLEM -> "VillagerGolem";
            case WITHER -> "Wither";
            case ENDER_DRAGON -> "EnderDragon";
            case HORSE -> "EntityHorse";
            case RABBIT -> "Rabbit";
            case VILLAGER -> "Villager";
        };
    }

    /**
     * Held Item Change (0x09, community-verified layout: i8 slot) — the
     * server's confirm of the owner's hotbar switch. Any thread.
     */
    void sendHeldItemChangeConfirm(PlayerSession player) {
        if (player != session) {
            return; // the confirm rides only to the owning client
        }
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        ByteBuf out = Unpooled.buffer(4);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_HELD_ITEM_CHANGE);
        out.writeByte(player.inventory().heldSlot());
        enqueueOut(out);
    }

    // ------------------------------------------------------------------ experience orb sync

    /**
     * Spawn Experience Orb (0x11, community-verified layout: varint id,
     * i32 xyz in 1/32 fixed point, short count = the orb's XP bundle).
     * Chunk-gated like every engine-global entity spawn. Any thread.
     */
    void sendOrbSpawn(net.zaminmc.torch.server.experience.ExperienceOrbEntity orb) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY
                || chunkTracker == null) {
            return;
        }
        // Wave TF-10 — the body's own dimension gates the observer (the
        // TF-9 item spawn's own gate, generalized).
        if (dimensionOfSession() != orb.dimension()) {
            return;
        }
        if (!chunkTracker.hasChunk(chunkKeyOf(orb.position()))) {
            return; // observer cannot see that chunk yet
        }
        ByteBuf out = Unpooled.buffer(24);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_SPAWN_XP_ORB);
        ByteBufOps.writeVarInt(out, orb.entityId());
        out.writeInt((int) Math.floor(orb.position().x() * 32.0));
        out.writeInt((int) Math.floor(orb.position().y() * 32.0));
        out.writeInt((int) Math.floor(orb.position().z() * 32.0));
        out.writeShort(Math.min(Short.MAX_VALUE, orb.amount()));
        enqueueOut(out);
    }

    /** Absolute-position sync for a moving orb. Any thread. */
    void sendOrbTeleport(net.zaminmc.torch.server.experience.ExperienceOrbEntity orb) {
        // Wave TF-10 — the observer in another dimension never tracks this
        // body (its spawn never reached them).
        if (dimensionOfSession() != orb.dimension()) {
            return;
        }
        sendEntityTeleport(orb.entityId(), orb.position(), orb.onGround());
    }

    /**
     * The orb collect: Collect Item (0x0D) with the collector's observer-local
     * id, then Destroy — the same shape the item pickup plays.
     */
    void sendOrbCollected(int orbEntityId, PlayerSession collector) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        Integer collectorWireId = wireEntityIdOf(collector);
        if (collectorWireId == null) {
            return;
        }
        ByteBuf out = Unpooled.buffer(8);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_COLLECT_ITEM);
        ByteBufOps.writeVarInt(out, orbEntityId);
        ByteBufOps.writeVarInt(out, collectorWireId);
        enqueueOut(out);
        sendDestroyEntities(orbEntityId);
    }

    /** Removes an orb from this observer (despawn, void). Any thread. */
    void sendOrbRemoved(int orbEntityId) {
        sendDestroyEntities(orbEntityId);
    }

    /**
     * Set Experience (0x1F, protocol 47: f32 bar progress 0..1, varint level,
     * varint total XP — the 1.7 shorts became varints in 1.8, per the
     * community protocol-47 table). The client's XP bar follows this packet.
     * Any thread.
     */
    void sendSetExperience(PlayerSession player) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        int level = player.experienceLevel();
        float progress = net.zaminmc.torch.server.experience.ExperienceMath
                .progressForTotalXp(player.totalXp());
        int total = (int) Math.min(Integer.MAX_VALUE, player.totalXp());
        ByteBuf out = Unpooled.buffer(12);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_SET_EXPERIENCE);
        out.writeFloat(progress);
        ByteBufOps.writeVarInt(out, level);
        ByteBufOps.writeVarInt(out, total);
        enqueueOut(out);
    }

    // ------------------------------------------------------------------ item entity sync

    /** Spawn Entity + item metadata for a new item entity. Any thread. */
    void sendItemSpawn(net.zaminmc.torch.server.entity.ItemEntity entity) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY
                || chunkTracker == null) {
            return;
        }
        // Wave TF-9 — the body's own dimension gates the observer (the
        // same chunk coordinates exist in both worlds — a nether item
        // never spawns for an overworld observer, whatever the chunk view
        // holds).
        if (dimensionOfSession() != entity.dimension()) {
            return;
        }
        if (!chunkTracker.hasChunk(chunkKeyOf(entity.position()))) {
            return; // observer cannot see that chunk yet
        }
        Integer legacy = LegacyBlockIds.legacyItemId(entity.stack().type().identifier()).orElse(null);
        if (legacy == null) {
            return;
        }
        ByteBuf out = Unpooled.buffer(48);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_SPAWN_ENTITY);
        ByteBufOps.writeVarInt(out, entity.entityId());
        out.writeByte(Protocol18.OBJECT_ITEM);
        // Historical fixed point: 1/32 block units.
        out.writeInt((int) Math.floor(entity.position().x() * 32.0));
        out.writeInt((int) Math.floor(entity.position().y() * 32.0));
        out.writeInt((int) Math.floor(entity.position().z() * 32.0));
        out.writeByte(0); // pitch
        out.writeByte(0); // yaw
        // Historical object data for items: item id | (metadata << 16); non-zero
        // objectData also signals that a velocity vector follows.
        out.writeInt(legacy);
        out.writeShort((int) Math.floor(entity.velocityX() * 8000.0));
        out.writeShort((int) Math.floor(entity.velocityY() * 8000.0));
        out.writeShort((int) Math.floor(entity.velocityZ() * 8000.0));
        enqueueOut(out);

        // The item stack itself rides on entity metadata (type slot, index 10).
        ByteBuf meta = Unpooled.buffer(16);
        ByteBufOps.writeVarInt(meta, Protocol18.S2C_ENTITY_METADATA);
        ByteBufOps.writeVarInt(meta, entity.entityId());
        meta.writeByte((Protocol18.METADATA_TYPE_SLOT << 5) | Protocol18.ITEM_STACK_METADATA_INDEX);
        writeSlot(meta, entity.stack());
        meta.writeByte(Protocol18.METADATA_TERMINATOR);
        enqueueOut(meta);
    }

    /** Absolute-position sync for a moving item entity. Any thread. */
    void sendItemTeleport(net.zaminmc.torch.server.entity.ItemEntity entity) {
        // Wave TF-9 — the observer in another dimension never tracks this
        // body (its spawn never reached them; the teleport must not either).
        if (dimensionOfSession() != entity.dimension()) {
            return;
        }
        sendEntityTeleport(entity.entityId(), entity.position(), entity.onGround());
    }

    /** Absolute-position sync for a moving falling block. Any thread. */
    void sendFallingTeleport(net.zaminmc.torch.server.entity.FallingBlockEntity entity) {
        sendEntityTeleport(entity.entityId(), entity.position(), entity.onGround());
    }

    /**
     * The tracked-entity move consult (the reference's TrackedEntity lines
     * 154-224, the Wave VI demand row — the relative-move optimization for
     * every engine-global kind beyond the mobs' own band): the small-delta
     * moves ride Rel Entity Move (0x15) with the i8 1/32 deltas — the
     * client interpolates them, the teleport form never does — while the
     * byte-window violation, the onGround flip, and the first publication
     * after a spawn keep Entity Teleport (0x18) with the absolute fixed
     * point. Deltas below the reference's own 4/32 threshold accumulate
     * silently (no packet — {@code flag}'s band, line 166). Any thread.
     */
    void sendEntityTeleport(int entityId, net.zaminmc.torch.util.Position position, boolean onGround) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        long x = (long) Math.floor(position.x() * 32.0);
        long y = (long) Math.floor(position.y() * 32.0);
        long z = (long) Math.floor(position.z() * 32.0);
        long[] last = trackedMoves.get(entityId);
        if (last != null) {
            long dx = x - last[0];
            long dy = y - last[1];
            long dz = z - last[2];
            boolean groundFlipped = (last[3] != 0) != onGround;
            if (dx >= -128 && dx < 128 && dy >= -128 && dy < 128 && dz >= -128 && dz < 128
                    && !groundFlipped) {
                if (Math.abs(dx) < 4 && Math.abs(dy) < 4 && Math.abs(dz) < 4) {
                    return; // the sub-threshold accumulation — no packet (flag's band)
                }
                ByteBuf rel = Unpooled.buffer(12);
                ByteBufOps.writeVarInt(rel, Protocol18.S2C_REL_ENTITY_MOVE);
                ByteBufOps.writeVarInt(rel, entityId);
                rel.writeByte((int) dx);
                rel.writeByte((int) dy);
                rel.writeByte((int) dz);
                rel.writeBoolean(onGround);
                enqueueOut(rel);
                last[0] = x;
                last[1] = y;
                last[2] = z;
                last[3] = onGround ? 1 : 0;
                return;
            }
        }
        ByteBuf out = Unpooled.buffer(40);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_ENTITY_TELEPORT);
        ByteBufOps.writeVarInt(out, entityId);
        out.writeInt((int) x);
        out.writeInt((int) y);
        out.writeInt((int) z);
        out.writeByte(0);
        out.writeByte(0);
        out.writeBoolean(onGround);
        enqueueOut(out);
        trackedMoves.put(entityId, new long[] {x, y, z, onGround ? 1 : 0, 0, 0});
    }

    /** Updated item stack metadata after a partial pickup. Any thread. */
    void sendItemStackUpdate(net.zaminmc.torch.server.entity.ItemEntity entity) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        // Wave TF-9 — the body's own dimension gates the observer.
        if (dimensionOfSession() != entity.dimension()) {
            return;
        }
        ByteBuf meta = Unpooled.buffer(16);
        ByteBufOps.writeVarInt(meta, Protocol18.S2C_ENTITY_METADATA);
        ByteBufOps.writeVarInt(meta, entity.entityId());
        meta.writeByte((Protocol18.METADATA_TYPE_SLOT << 5) | Protocol18.ITEM_STACK_METADATA_INDEX);
        writeSlot(meta, entity.stack());
        meta.writeByte(Protocol18.METADATA_TERMINATOR);
        enqueueOut(meta);
    }

    /**
     * Collect animation + removal for a collected item. Every observer needs
     * it; the collector's wire entity id differs per observer (own vs remote).
     */
    void sendItemCollected(int itemEntityId, PlayerSession collector) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        Integer collectorWireId = wireEntityIdOf(collector);
        if (collectorWireId == null) {
            return;
        }
        ByteBuf out = Unpooled.buffer(8);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_COLLECT_ITEM);
        ByteBufOps.writeVarInt(out, itemEntityId);
        ByteBufOps.writeVarInt(out, collectorWireId);
        enqueueOut(out);

        ByteBuf destroy = Unpooled.buffer(8);
        ByteBufOps.writeVarInt(destroy, Protocol18.S2C_DESTROY_ENTITIES);
        ByteBufOps.writeVarInt(destroy, 1);
        ByteBufOps.writeVarInt(destroy, itemEntityId);
        enqueueOut(destroy);
    }

    /**
     * The grounded arrow's collect (ArrowEntity.onPlayerCollision line 438's
     * sendPickup): the same Collect + Destroy pair over the projectile's own
     * engine id space (the arrows ride the global wire ids, no
     * per-observer mapping).
     */
    void sendProjectileCollected(int projectileEntityId, PlayerSession collector) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        Integer collectorWireId = wireEntityIdOf(collector);
        if (collectorWireId == null) {
            return;
        }
        ByteBuf out = Unpooled.buffer(8);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_COLLECT_ITEM);
        ByteBufOps.writeVarInt(out, projectileEntityId);
        ByteBufOps.writeVarInt(out, collectorWireId);
        enqueueOut(out);

        ByteBuf destroy = Unpooled.buffer(8);
        ByteBufOps.writeVarInt(destroy, Protocol18.S2C_DESTROY_ENTITIES);
        ByteBufOps.writeVarInt(destroy, 1);
        ByteBufOps.writeVarInt(destroy, projectileEntityId);
        enqueueOut(destroy);
    }


    /**
     * A MOB's collect (Slice 92 — the pickup walk's sendPickup): the same
     * Collect + Destroy pair with the MOB's own engine id as the collector
     * (the mobs ride the engine id space, no per-observer mapping).
     */
    void sendItemCollectedByMob(int itemEntityId, int collectorEntityId) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        ByteBuf out = Unpooled.buffer(8);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_COLLECT_ITEM);
        ByteBufOps.writeVarInt(out, itemEntityId);
        ByteBufOps.writeVarInt(out, collectorEntityId);
        enqueueOut(out);

        ByteBuf destroy = Unpooled.buffer(8);
        ByteBufOps.writeVarInt(destroy, Protocol18.S2C_DESTROY_ENTITIES);
        ByteBufOps.writeVarInt(destroy, 1);
        ByteBufOps.writeVarInt(destroy, itemEntityId);
        enqueueOut(destroy);
    }

    /** Removes an item entity from this observer. Any thread. */
    void sendItemRemoved(int itemEntityId) {
        sendDestroyEntities(itemEntityId);
    }

    /** Removes a falling block from this observer. Any thread. */
    void sendFallingRemoved(int fallingEntityId) {
        sendDestroyEntities(fallingEntityId);
    }

    /**
     * Spawn Entity (0x0E) object 50 for a primed TNT body. The objectData
     * is 1 (the client reads the velocity triple after any non-zero data —
     * the standard for objects with an initial motion, like the vanilla
     * tracker's TNT packet). Chunk-gated like the other entities. Any
     * thread.
     */
    void sendTntSpawn(net.zaminmc.torch.server.tnt.TntSystem.PrimedTnt tnt) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY
                || chunkTracker == null) {
            return;
        }
        var blockPos = new net.zaminmc.torch.block.BlockPosition(
                (int) Math.floor(tnt.x()), (int) Math.floor(tnt.y()), (int) Math.floor(tnt.z()));
        if (!chunkTracker.hasChunk(blockPos.chunkPosition().packed())) {
            return; // observer cannot see that chunk yet
        }
        ByteBuf out = Unpooled.buffer(40);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_SPAWN_ENTITY);
        ByteBufOps.writeVarInt(out, tnt.entityId());
        out.writeByte(Protocol18.OBJECT_PRIMED_TNT);
        out.writeInt((int) Math.floor(tnt.x() * 32.0));
        out.writeInt((int) Math.floor(tnt.y() * 32.0));
        out.writeInt((int) Math.floor(tnt.z() * 32.0));
        out.writeByte(0); // pitch
        out.writeByte(0); // yaw
        out.writeInt(1); // objectData: the velocity flag
        out.writeShort((int) Math.round(tnt.velocityX() * 8000.0));
        out.writeShort((int) Math.round(tnt.velocityY() * 8000.0));
        out.writeShort((int) Math.round(tnt.velocityZ() * 8000.0));
        enqueueOut(out);
    }

    /**
     * The primed TNT body's tick movement over the shared tracked-move
     * consult (the reference's own tracked entity — the small hop deltas
     * ride Rel Move; the launch pop keeps the teleport). The old
     * dedicated write was SHORT one field (the 0x18 layout's trailing
     * onGround boolean — the fixed-point write never sent it); the shared
     * consult carries the whole layout. Any thread.
     */
    void sendTntMoved(net.zaminmc.torch.server.tnt.TntSystem.PrimedTnt tnt) {
        sendEntityTeleport(tnt.entityId(),
                new net.zaminmc.torch.util.Position(tnt.x(), tnt.y(), tnt.z()), false);
    }

    /** Removes a primed TNT body from this observer. Any thread. */
    void sendTntRemoved(int tntEntityId) {
        sendDestroyEntities(tntEntityId);
    }

    /** Destroy Entities (0x13) with a single id. Any thread. */
    private void sendDestroyEntities(int entityId) {
        trackedMoves.remove(entityId); // the tracked-move row retires with the body
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        ByteBuf out = Unpooled.buffer(8);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_DESTROY_ENTITIES);
        ByteBufOps.writeVarInt(out, 1);
        ByteBufOps.writeVarInt(out, entityId);
        enqueueOut(out);
    }

    // ------------------------------------------------------------------ falling block sync

    /**
     * Spawn Entity (0x0E) object 70 for a new falling block. The objectData
     * carries the legacy block id (low 12 bits) with the block metadata above
     * it — the client decodes {@code getStateById(data & 0xFFFF)} into block
     * id and metadata (verified against the 1.8.9 client's spawn handler);
     * non-zero objectData means the three velocity shorts follow, exactly like
     * the item spawn path. Spawn is chunk-gated like the other entities. Any
     * thread.
     */
    void sendFallingSpawn(net.zaminmc.torch.server.entity.FallingBlockEntity entity) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY
                || chunkTracker == null) {
            return;
        }
        if (!chunkTracker.hasChunk(chunkKeyOf(entity.position()))) {
            return; // observer cannot see that chunk yet
        }
        Integer legacy = LegacyBlockIds.legacyId(entity.blockType().identifier()).orElse(null);
        if (legacy == null) {
            return;
        }
        ByteBuf out = Unpooled.buffer(48);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_SPAWN_ENTITY);
        ByteBufOps.writeVarInt(out, entity.entityId());
        out.writeByte(Protocol18.OBJECT_FALLING_BLOCK);
        out.writeInt((int) Math.floor(entity.position().x() * 32.0));
        out.writeInt((int) Math.floor(entity.position().y() * 32.0));
        out.writeInt((int) Math.floor(entity.position().z() * 32.0));
        out.writeByte(0); // pitch
        out.writeByte(0); // yaw
        // Historical object data: legacy id | (metadata << 12). Sand and
        // gravel carry metadata 0 today; the nibble path exists so a falling
        // block of any metadata-carrying kind stays truthful on the wire.
        out.writeInt(legacy | (LegacyBlockIds.metadataOf(entity.blockType().identifier()) << 12));
        out.writeShort(0); // velocity x (falls are vertical)
        out.writeShort((int) Math.floor(entity.velocityY() * 8000.0));
        out.writeShort(0); // velocity z
        enqueueOut(out);
    }

    private Integer wireEntityIdOf(PlayerSession player) {
        if (player == session) {
            return ownEntityId >= 0 ? ownEntityId : null;
        }
        return remoteEntityIds.get(player.uuid());
    }

    /** @return this observer's wire id for a remote player, or null when untracked. */
    Integer remoteWireEntityIdOf(UUID uuid) {
        return remoteEntityIds.get(uuid);
    }

    // ------------------------------------------------------------------ mob entity sync

    /**
     * Last synced fixed-point position per mob id (1/32 units, x|y|z). The
     * rel-move-look deltas compute against this; entries clear on removal.
     * Written from the tick thread (manager listeners) and the event loop
     * (join snapshot), hence concurrent.
     */
    private final java.util.concurrent.ConcurrentHashMap<Integer, long[]> mobSyncState =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * The tracked-entity move cache for every OTHER engine-global kind (the
     * Wave VI demand row — the tracker's relative-move optimization beyond
     * the mobs' own band): the reference's {@code TrackedEntity} lines
     * 100-106 (the last-sent fixed-point positions and angles), the
     * entityId-keyed last publication the shared
     * {@link #sendEntityTeleport} and the remote-player relay consult.
     * The row layout: {x32, y32, z32, onGround, yaw256, pitch256}. The
     * reference's own bands ride the consult: the i8 window [-128, 128)
     * per axis, the 4/32 sub-threshold accumulation (below it NO packet —
     * the delta accumulates until it crosses), and the onGround flip that
     * forces the absolute form (lines 166-193). The first publication
     * after a spawn seeds through the absolute form (the lazy arm — one
     * extra 0x18 right after the spawn packet, named); the destroy path
     * forgets the row.
     */
    private final java.util.concurrent.ConcurrentHashMap<Integer, long[]> trackedMoves =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Spawn Mob (0x0F, community-verified layout: varint id, u8 type, i32 xyz
     * in 1/32 fixed point, i8 yaw, i8 pitch, i8 headPitch, vec3 i16 velocity
     * in 1/8000 blocks/tick, trailing metadata) plus the living-entity
     * metadata: flags byte (index 0) and health float (index 7, the historical
     * DataWatcher layout). Spawn is chunk-gated like the item entities. Any
     * thread.
     */
    void sendMobSpawn(net.zaminmc.torch.server.entity.MobEntity mob) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY
                || chunkTracker == null) {
            return;
        }
        // Slice 25d: the mob's own dimension gates the observer (the same
        // chunk coordinates exist in both worlds — a nether body never
        // spawns for an overworld observer, whatever the chunk view holds).
        if (dimensionOfSession() != mob.dimension()) {
            return;
        }
        if (!chunkTracker.hasChunk(chunkKeyOf(mob.position()))) {
            return; // observer cannot see that chunk yet
        }
        ByteBuf out = Unpooled.buffer(40);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_SPAWN_MOB);
        ByteBufOps.writeVarInt(out, mob.entityId());
        out.writeByte(mob.type().legacyTypeId);
        out.writeInt((int) Math.floor(mob.position().x() * 32.0));
        out.writeInt((int) Math.floor(mob.position().y() * 32.0));
        out.writeInt((int) Math.floor(mob.position().z() * 32.0));
        out.writeByte(angleBytes(mob.yaw()));
        out.writeByte(0); // pitch: ground mobs look level this slice
        out.writeByte(angleBytes(mob.headYaw()));
        out.writeShort(0); // velocity x
        out.writeShort(0); // velocity y
        out.writeShort(0); // velocity z
        // Living-entity metadata: flags byte + health float, then the kind's
        // own block (the 1.8 DataWatcher layout): the horse's index-16 flag
        // Int + subtype + variant + owner + armor, the pig's saddle byte at
        // 16, the creeper's swell / the sheep's coat byte at 16 — then the
        // terminator. Passive kinds also carry the age byte (index 12: -1
        // baby, 0 adult — the PassiveEntity register). The flags byte
        // carries the ocelot's postures beside the burn bit.
        out.writeByte((Protocol18.METADATA_TYPE_BYTE << 5) | Protocol18.LIVING_FLAGS_METADATA_INDEX);
        out.writeByte(mobLivingFlagsOf(mob));
        out.writeByte((Protocol18.METADATA_TYPE_FLOAT << 5) | Protocol18.LIVING_HEALTH_METADATA_INDEX);
        out.writeFloat(mob.health());
        // The custom-name pair (Slice 75 — EntityLiving.registerSyncedData's
        // own slots): the STRING at 2 (an absent name keeps the client's
        // own empty default — the omitted slot) and the always-render BYTE
        // at 3.
        if (mob.customName() != null) {
            out.writeByte((Protocol18.METADATA_TYPE_STRING << 5) | 2);
            ByteBufOps.writeString(out, mob.customName());
            out.writeByte((Protocol18.METADATA_TYPE_BYTE << 5) | 3);
            out.writeByte(mob.customNameVisible() ? 1 : 0);
        }
        if (!mob.type().hostile) {
            out.writeByte((Protocol18.METADATA_TYPE_BYTE << 5) | Protocol18.PASSIVE_AGE_METADATA_INDEX);
            out.writeByte(mob.isBaby() ? (byte) -1 : (byte) 0);
        }
        if (mob.type() == net.zaminmc.torch.server.entity.MobType.WITHER) {
            // The wither's block (WitherEntity.registerSyncedData lines
            // 71-77): the three heads' tracked quarry Ints at 17-19 and
            // the invulnerability countdown Int at 20 — the client aims
            // each head from its id and draws the blue shield from the
            // countdown (Slice 59). The boss rides the hostile family —
            // no passive age byte.
            int[] heads = mob.witherHeadTargets();
            out.writeByte((Protocol18.METADATA_TYPE_INT << 5)
                    | Protocol18.WITHER_HEAD_0_METADATA_INDEX);
            out.writeInt(heads.length > 0 ? heads[0] : 0);
            out.writeByte((Protocol18.METADATA_TYPE_INT << 5)
                    | Protocol18.WITHER_HEAD_1_METADATA_INDEX);
            out.writeInt(heads.length > 1 ? heads[1] : 0);
            out.writeByte((Protocol18.METADATA_TYPE_INT << 5)
                    | Protocol18.WITHER_HEAD_2_METADATA_INDEX);
            out.writeInt(heads.length > 2 ? heads[2] : 0);
            out.writeByte((Protocol18.METADATA_TYPE_INT << 5)
                    | Protocol18.WITHER_INVULN_METADATA_INDEX);
            out.writeInt(mob.witherInvulnTicks());
        } else if (mob.type() == net.zaminmc.torch.server.entity.MobType.HORSE) {
            writeHorseMetadata(out, mob);
        } else if (mob.type() == net.zaminmc.torch.server.entity.MobType.WITCH) {
            // The witch's drinking flag (the synced-data slot 21): the
            // client raises the drinking pose and the held potion from it.
            out.writeByte((Protocol18.METADATA_TYPE_BYTE << 5)
                    | Protocol18.WITCH_DRINKING_METADATA_INDEX);
            out.writeByte(mob.witchDrinking() ? 1 : 0);
        } else if (mob.type() == net.zaminmc.torch.server.entity.MobType.SKELETON) {
            // The skeleton's type byte (SkeletonEntity.registerSyncedData
            // line 72: the synced slot 13): 0 the archer, 1 the wither
            // variant — the client swaps the taller charcoal body and the
            // skull texture from it (Slice 18d).
            out.writeByte((Protocol18.METADATA_TYPE_BYTE << 5)
                    | Protocol18.SKELETON_TYPE_METADATA_INDEX);
            out.writeByte(mob.skeletonWither() ? 1 : 0);
        } else if (mob.type() == net.zaminmc.torch.server.entity.MobType.ZOMBIE) {
            // The zombie's own block (ZombieEntity.registerSyncedData lines
            // 91-96, Slice 25a): the index-12 baby byte (1 — the zombie's
            // own flag, not the passive family's -1), the 13 villager-skin
            // byte, the 14 converting byte (the cure's countdown — the
            // client swaps the villager texture and the shaking pose).
            out.writeByte((Protocol18.METADATA_TYPE_BYTE << 5)
                    | Protocol18.ZOMBIE_BABY_METADATA_INDEX);
            out.writeByte(mob.isBaby() ? 1 : 0);
            out.writeByte((Protocol18.METADATA_TYPE_BYTE << 5)
                    | Protocol18.ZOMBIE_VILLAGER_METADATA_INDEX);
            out.writeByte(mob.zombieVillager() ? 1 : 0);
            out.writeByte((Protocol18.METADATA_TYPE_BYTE << 5)
                    | Protocol18.ZOMBIE_CONVERTING_METADATA_INDEX);
            out.writeByte(mob.zombieConverting() ? 1 : 0);
        } else if (mob.type() == net.zaminmc.torch.server.entity.MobType.SLIME) {
            // The slime's size (the synced-data slot 16): the client scales
            // the whole body from the byte (1, 2, or 4).
            out.writeByte((Protocol18.METADATA_TYPE_BYTE << 5) | Protocol18.KIND_STATUS_METADATA_INDEX);
            out.writeByte(mob.slimeSize());
        } else if (mob.type() == net.zaminmc.torch.server.entity.MobType.MAGMA_CUBE) {
            // The magma cube's own size byte (the slime family's slot 16).
            out.writeByte((Protocol18.METADATA_TYPE_BYTE << 5) | Protocol18.KIND_STATUS_METADATA_INDEX);
            out.writeByte(mob.slimeSize());
        } else if (mob.type() == net.zaminmc.torch.server.entity.MobType.GHAST) {
            // The ghast's charging flag (GhastEntity's synced slot 16):
            // the client raises the mouth open from it.
            out.writeByte((Protocol18.METADATA_TYPE_BYTE << 5) | Protocol18.KIND_STATUS_METADATA_INDEX);
            out.writeByte(mob.ghastCharging() ? 1 : 0);
        } else if (mob.type() == net.zaminmc.torch.server.entity.MobType.BLAZE) {
            // The blaze's fire-active flag (BlazeEntity's synced slot 16,
            // bit 0 — registerSyncedData line 50): the client raises the
            // burning rods from it.
            out.writeByte((Protocol18.METADATA_TYPE_BYTE << 5) | Protocol18.KIND_STATUS_METADATA_INDEX);
            out.writeByte(mob.blazeFireActive() ? 1 : 0);
        } else if (mob.type() == net.zaminmc.torch.server.entity.MobType.BAT) {
            // The bat's roosting flag (BatEntity's synced slot 16, bit 0):
            // the client raises the hanging pose from it.
            out.writeByte((Protocol18.METADATA_TYPE_BYTE << 5) | Protocol18.KIND_STATUS_METADATA_INDEX);
            out.writeByte(mob.batRoosting() ? 1 : 0);
        } else if (mob.type() == net.zaminmc.torch.server.entity.MobType.SPIDER
                || mob.type() == net.zaminmc.torch.server.entity.MobType.CAVE_SPIDER) {
            // The spider family's wall flag (SpiderEntity's synced slot 16,
            // bit 0 — the tick law's post-move horizontal clip): the client
            // raises the head against the wall from it.
            out.writeByte((Protocol18.METADATA_TYPE_BYTE << 5) | Protocol18.KIND_STATUS_METADATA_INDEX);
            out.writeByte(mob.spiderClimbingWall() ? 1 : 0);
        } else if (mob.type() == net.zaminmc.torch.server.entity.MobType.GUARDIAN) {
            // The guardian's block (GuardianEntity.registerSyncedData): the
            // index-16 flag Int (the moving 0x02 bit + the elder 0x04 bit)
            // and the 17 beam-target Int (only when a beam is live — the
            // client draws the laser from the victim's id).
            out.writeByte((Protocol18.METADATA_TYPE_INT << 5) | Protocol18.KIND_STATUS_METADATA_INDEX);
            out.writeInt(guardianFlagsOf(mob));
            if (mob.guardianBeamTargetId() != 0) {
                out.writeByte((Protocol18.METADATA_TYPE_INT << 5)
                        | Protocol18.GUARDIAN_BEAM_METADATA_INDEX);
                out.writeInt(mob.guardianBeamTargetId());
            }
        } else if (mob.type() == net.zaminmc.torch.server.entity.MobType.ENDERMAN) {
            // The enderman's block (registerSyncedData lines 78-83): the
            // carried block's Short at 16 (Block.serialize's combined
            // id<<4 | meta — the client draws the held block from it) and
            // the screaming byte at 18 (the jaw texture swap). The 17 byte
            // stays unwritten like the reference itself (its own legacy
            // slot never updates in 1.8.8).
            out.writeByte((Protocol18.METADATA_TYPE_SHORT << 5)
                    | Protocol18.KIND_STATUS_METADATA_INDEX);
            out.writeShort(endermanCarriedShort(mob));
            out.writeByte((Protocol18.METADATA_TYPE_BYTE << 5)
                    | Protocol18.ENDERMAN_SCREAMING_METADATA_INDEX);
            out.writeByte(mob.endermanAngry() ? 1 : 0);
        } else if (mob.type() == net.zaminmc.torch.server.entity.MobType.RABBIT) {
            // The rabbit's coat byte (RabbitEntity.registerSyncedData line
            // 116: the synced slot 18): 0-5 the natural skins, 99 the
            // killer bunny — the client swaps the body texture and the
            // red eyes from it (Slice 21d).
            out.writeByte((Protocol18.METADATA_TYPE_BYTE << 5)
                    | Protocol18.RABBIT_TYPE_METADATA_INDEX);
            out.writeByte(mob.rabbitVariant());
        } else if (mob.type() == net.zaminmc.torch.server.entity.MobType.PIG) {
            out.writeByte((Protocol18.METADATA_TYPE_BYTE << 5) | Protocol18.PIG_SADDLE_METADATA_INDEX);
            out.writeByte(mob.pigSaddled() ? 1 : 0);
        } else if (mob.type() == net.zaminmc.torch.server.entity.MobType.VILLAGER) {
            out.writeByte((Protocol18.METADATA_TYPE_INT << 5)
                    | Protocol18.VILLAGER_PROFESSION_METADATA_INDEX);
            out.writeInt(mob.profession());
        } else if (mob.type() == net.zaminmc.torch.server.entity.MobType.WOLF) {
            // The wolf's block (registerSyncedData): the state byte 16
            // (sitting | angry << 1 | tamed << 2), the owner string 17, the
            // health float 18 (the tail-wag read), the begging byte 19, the
            // collar dye byte 20.
            writeWolfMetadata(out, mob);
        } else if (mob.type() == net.zaminmc.torch.server.entity.MobType.OCELOT) {
            // The ocelot's block: the state byte 16 (sitting | tamed — the
            // tameable family's bits, no angry), the owner string 17, and
            // the cat coat byte 18 (0 wild, 1-3 the tamed coats).
            writeOcelotMetadata(out, mob);
        } else {
            writeKindStatusMetadata(out, mob);
        }
        out.writeByte(Protocol18.METADATA_TERMINATOR);
        enqueueOut(out);

        // The track-time attribute pair (the reference's updatePlayer walk:
        // the attributes ride before the equipment, TrackedEntity lines
        // 322-328 ahead of the equipment arm at 348-355) — the same gates
        // this spawn already passed hold it (chunk + dimension).
        enqueueOut(encodeEntityProperties(mob.entityId(), mobWireAttributes(mob)));

        // The mob's gear rides Entity Equipment right after the spawn —
        // VIII4's five-slot family (the reference's TrackedEntity
        // startTracking walk, lines 348-355: held first, then feet, legs,
        // chest, head), the player body's own wire order (Slice 18d held
        // the bow alone; the armor family joins 1:1).
        writeEquipment(channel, mob.entityId(), Protocol18.EQUIPMENT_SLOT_HELD,
                mob.heldEquipment());
        writeEquipment(channel, mob.entityId(), Protocol18.EQUIPMENT_SLOT_FEET,
                mob.equipment(1));
        writeEquipment(channel, mob.entityId(), Protocol18.EQUIPMENT_SLOT_LEGS,
                mob.equipment(2));
        writeEquipment(channel, mob.entityId(), Protocol18.EQUIPMENT_SLOT_CHEST,
                mob.equipment(3));
        writeEquipment(channel, mob.entityId(), Protocol18.EQUIPMENT_SLOT_HEAD,
                mob.equipment(4));

        mobSyncState.put(mob.entityId(), new long[] {
                (long) Math.floor(mob.position().x() * 32.0),
                (long) Math.floor(mob.position().y() * 32.0),
                (long) Math.floor(mob.position().z() * 32.0)});
    }


    /**
     * The window's title row (Slice 91 Wave C p3 — the reference's
     * InventoryBlockEntity.getDisplayName: a custom name answers a
     * LiteralText of itself, over the family's own default title).
     */
    private static String windowTitle(String customName, String familyTitle) {
        if (customName == null || customName.isEmpty()) {
            return familyTitle;
        }
        return net.zaminmc.torch.server.text.TextJson.toJson(
                new net.zaminmc.torch.server.text.LiteralText(customName));
    }

    /**
     * Writes the kind-status metadata entry (creeper swell, sheep coat) —
     * and the creeper's own CHARGED byte beside it (CreeperEntity.
     * registerSyncedData line 68: syncedData 17, 1 when powered — the
     * client's charge layer draws the blue aura from this row; the
     * reference's spawn view publishes it beside the fuse direction).
     */
    private static void writeKindStatusMetadata(ByteBuf out, MobEntity mob) {
        Byte kind = kindStatusByteOf(mob);
        if (kind == null) {
            return;
        }
        out.writeByte((Protocol18.METADATA_TYPE_BYTE << 5) | Protocol18.KIND_STATUS_METADATA_INDEX);
        out.writeByte(kind);
        if (mob.type() == net.zaminmc.torch.server.entity.MobType.CREEPER
                && mob.creeperCharged()) {
            out.writeByte((Protocol18.METADATA_TYPE_BYTE << 5) | 17);
            out.writeByte(1); // powered (Slice 91 Wave C p2)
        }
    }

    /**
     * @return the guardian's index-16 flag word ({@code moving 0x02 |
     * elder 0x04} — the reference's own setDataFlagValue bits).
     */
    static int guardianFlagsOf(MobEntity mob) {
        int flags = 0;
        if (mob.guardianMoving()) {
            flags |= Protocol18.GUARDIAN_FLAG_MOVING;
        }
        if (mob.guardianElder()) {
            flags |= Protocol18.GUARDIAN_FLAG_ELDER;
        }
        return flags;
    }

    /** @return the kind-status byte value, or null for kinds without one. */
    static Byte kindStatusByteOf(MobEntity mob) {
        return switch (mob.type()) {
            case CREEPER -> mob.fuseActive()
                    ? Protocol18.CREEPER_FUSE_SWELLING : Protocol18.CREEPER_FUSE_IDLE;
            // SheepEntity.registerSyncedData's index-16 byte, 1:1: the low
            // nibble IS the fleece's DyeColor id (getColor().getId()), the
            // 0x10 bit the sheared flag (Slice 30b grew the nibble).
            case SHEEP -> (byte) ((mob.sheepFleeceColor() & 0x0F)
                    | (mob.sheared() ? 0x10 : 0));
            default -> null;
        };
    }

    /** The ocelot's metadata block: state Byte 16, owner String 17,
     * coat Byte 18 (OcelotEntity's own synced slot). */
    private static void writeOcelotMetadata(ByteBuf out, MobEntity mob) {
        out.writeByte((Protocol18.METADATA_TYPE_BYTE << 5) | Protocol18.WOLF_STATE_METADATA_INDEX);
        out.writeByte(mob.wolfFlagsRaw());
        if (!mob.ownerName().isEmpty()) {
            out.writeByte((Protocol18.METADATA_TYPE_STRING << 5) | Protocol18.WOLF_OWNER_METADATA_INDEX);
            ByteBufOps.writeString(out, mob.ownerName());
        }
        out.writeByte((Protocol18.METADATA_TYPE_BYTE << 5) | Protocol18.WOLF_HEALTH_METADATA_INDEX);
        out.writeByte(mob.catVariant()); // the coat — the ocelot's own slot 18
    }

    /** The wolf's metadata block: state Byte 16 (sitting | angry << 1 | tamed << 2),
     * owner String 17, health Float 18, begging Byte 19, collar Byte 20. */
    private static void writeWolfMetadata(ByteBuf out, MobEntity mob) {
        out.writeByte((Protocol18.METADATA_TYPE_BYTE << 5) | Protocol18.WOLF_STATE_METADATA_INDEX);
        out.writeByte(mob.wolfFlagsRaw());
        if (!mob.ownerName().isEmpty()) {
            out.writeByte((Protocol18.METADATA_TYPE_STRING << 5) | Protocol18.WOLF_OWNER_METADATA_INDEX);
            ByteBufOps.writeString(out, mob.ownerName());
        }
        out.writeByte((Protocol18.METADATA_TYPE_FLOAT << 5) | Protocol18.WOLF_HEALTH_METADATA_INDEX);
        out.writeFloat(mob.health());
        out.writeByte((Protocol18.METADATA_TYPE_BYTE << 5) | Protocol18.WOLF_BEGGING_METADATA_INDEX);
        out.writeByte(mob.wolfBegging() ? 1 : 0);
        out.writeByte((Protocol18.METADATA_TYPE_BYTE << 5) | Protocol18.WOLF_COLLAR_METADATA_INDEX);
        out.writeByte(mob.wolfCollarColor());
    }

    /** The horse's metadata block: flags Int 16, subtype Byte 19, variant Int 20,
     * owner String 21 (the vanilla UUID-as-string quirk — we carry the tamer's
     * name), armor Int 22. Order matters not; presence does. */
    private static void writeHorseMetadata(ByteBuf out, MobEntity mob) {
        out.writeByte((Protocol18.METADATA_TYPE_INT << 5) | Protocol18.HORSE_FLAGS_METADATA_INDEX);
        out.writeInt(mob.horseFlagsRaw());
        out.writeByte((Protocol18.METADATA_TYPE_BYTE << 5) | Protocol18.HORSE_SUBTYPE_METADATA_INDEX);
        out.writeByte(mob.horseSubtype());
        out.writeByte((Protocol18.METADATA_TYPE_INT << 5) | Protocol18.HORSE_VARIANT_METADATA_INDEX);
        out.writeInt(mob.variant());
        if (!mob.ownerName().isEmpty()) {
            out.writeByte((Protocol18.METADATA_TYPE_STRING << 5) | Protocol18.HORSE_OWNER_METADATA_INDEX);
            ByteBufOps.writeString(out, mob.ownerName());
        }
        out.writeByte((Protocol18.METADATA_TYPE_INT << 5) | Protocol18.HORSE_ARMOR_METADATA_INDEX);
        out.writeInt(mob.armorType());
    }

    /**
     * Set Entity Metadata (0x1C) for one kind-status change: a creeper
     * starting or dropping its swell, a sheep losing or regrowing its coat.
     * Any thread.
     */
    void sendMobStatusByte(MobEntity mob, byte value) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        // Wave TF-12 — the mob's own dimension gates the observer (the
        // same law as the spawn: a nether body's metadata never reaches an
        // overworld view).
        if (dimensionOfSession() != mob.dimension()) {
            return;
        }
        ByteBuf out = Unpooled.buffer(12);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_ENTITY_METADATA);
        ByteBufOps.writeVarInt(out, mob.entityId());
        out.writeByte((Protocol18.METADATA_TYPE_BYTE << 5) | Protocol18.KIND_STATUS_METADATA_INDEX);
        out.writeByte(value);
        out.writeByte(Protocol18.METADATA_TERMINATOR);
        enqueueOut(out);
    }

    /**
     * Set Entity Metadata (0x1C) for the tameable family's state block
     * (the wolf's whole block, the ocelot's own — the kind picks the
     * writer): the tame, the sit toggle, and the coat change all ride
     * this one writer. Any thread.
     */
    void sendWolfState(MobEntity mob) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        // Wave TF-12 — the mob's own dimension gates the observer (the
        // same law as the spawn: a nether body's metadata never reaches an
        // overworld view).
        if (dimensionOfSession() != mob.dimension()) {
            return;
        }
        ByteBuf out = Unpooled.buffer(32);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_ENTITY_METADATA);
        ByteBufOps.writeVarInt(out, mob.entityId());
        if (mob.type() == net.zaminmc.torch.server.entity.MobType.OCELOT) {
            writeOcelotMetadata(out, mob);
        } else {
            writeWolfMetadata(out, mob);
        }
        out.writeByte(Protocol18.METADATA_TERMINATOR);
        enqueueOut(out);
    }

    /** Set Entity Metadata (0x1C) for the horse's index-16 flag Int: a tame
     * flip, a saddle going on, the rear of a buck, the graze of a feed. The
     * full flag word rides every time (the Int delta needs no bit targeting).
     * Any thread.
     */
    void sendMobHorseFlags(MobEntity mob) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        // Wave TF-12 — the mob's own dimension gates the observer (the
        // same law as the spawn: a nether body's metadata never reaches an
        // overworld view).
        if (dimensionOfSession() != mob.dimension()) {
            return;
        }
        ByteBuf out = Unpooled.buffer(16);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_ENTITY_METADATA);
        ByteBufOps.writeVarInt(out, mob.entityId());
        out.writeByte((Protocol18.METADATA_TYPE_INT << 5) | Protocol18.HORSE_FLAGS_METADATA_INDEX);
        out.writeInt(mob.horseFlagsRaw());
        out.writeByte(Protocol18.METADATA_TERMINATOR);
        enqueueOut(out);
    }

    /**
     * Set Entity Metadata (0x1C) for the passive age byte (index 12): the
     * grew-up flip (the baby's -1 becomes the adult's 0 — the client drops
     * the child scale). Any thread.
     */
    void sendMobAgeMetadata(MobEntity mob) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        // Wave TF-12 — the mob's own dimension gates the observer (the
        // same law as the spawn: a nether body's metadata never reaches an
        // overworld view).
        if (dimensionOfSession() != mob.dimension()) {
            return;
        }
        ByteBuf out = Unpooled.buffer(16);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_ENTITY_METADATA);
        ByteBufOps.writeVarInt(out, mob.entityId());
        out.writeByte((Protocol18.METADATA_TYPE_BYTE << 5) | Protocol18.PASSIVE_AGE_METADATA_INDEX);
        out.writeByte(mob.isBaby() ? (byte) -1 : (byte) 0);
        out.writeByte(Protocol18.METADATA_TERMINATOR);
        enqueueOut(out);
    }

    /**
     * Set Entity Metadata (0x1C) for the pig's saddle byte (index 16): the
     * saddle going on (the mount slice). Any thread.
     */
    void sendMobPigSaddle(MobEntity mob) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        // Wave TF-12 — the mob's own dimension gates the observer (the
        // same law as the spawn: a nether body's metadata never reaches an
        // overworld view).
        if (dimensionOfSession() != mob.dimension()) {
            return;
        }
        ByteBuf out = Unpooled.buffer(12);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_ENTITY_METADATA);
        ByteBufOps.writeVarInt(out, mob.entityId());
        out.writeByte((Protocol18.METADATA_TYPE_BYTE << 5) | Protocol18.PIG_SADDLE_METADATA_INDEX);
        out.writeByte(mob.pigSaddled() ? 1 : 0);
        out.writeByte(Protocol18.METADATA_TERMINATOR);
        enqueueOut(out);
    }

    /**
     * Set Entity Metadata (0x1C) for the living-flags byte: the burn bit
     * catching or dying out on a mob body, and the ocelot's own postures
     * (the tempt creep's sneak 0x02, the flee sprint's 0x10). Any thread.
     */
    void sendMobLivingFlags(MobEntity mob) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        // Wave TF-12 — the mob's own dimension gates the observer (the
        // same law as the spawn: a nether body's metadata never reaches an
        // overworld view).
        if (dimensionOfSession() != mob.dimension()) {
            return;
        }
        ByteBuf out = Unpooled.buffer(12);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_ENTITY_METADATA);
        ByteBufOps.writeVarInt(out, mob.entityId());
        out.writeByte((Protocol18.METADATA_TYPE_BYTE << 5) | Protocol18.LIVING_FLAGS_METADATA_INDEX);
        out.writeByte(mobLivingFlagsOf(mob));
        out.writeByte(Protocol18.METADATA_TERMINATOR);
        enqueueOut(out);
    }

    /** The living-flags byte: burning | the ocelot's postures (sneak 0x02, sprint 0x10). */
    static int mobLivingFlagsOf(MobEntity mob) {
        return (mob.burning() ? Protocol18.LIVING_FLAG_BURNING : 0) | mob.postureFlags();
    }

    /**
     * The equipment's re-send (Slice 78b — the /summon Equipment row and
     * the restore's stacks land after the spawn packet went out): the
     * five Entity Equipment (0x04) writes over the reference's own order —
     * held first, then feet, legs, chest, head (VIII4; the player body's
     * sendPlayerEquipment shape, an empty slot writing the wire's own
     * -1 clear). Chunk-gated like every mob packet. Any thread.
     */
    void sendMobEquipment(MobEntity mob) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY
                || chunkTracker == null) {
            return;
        }
        // Wave TF-12 — the mob's own dimension gates the observer (the
        // same law as the spawn: a nether body's equipment never reaches
        // an overworld view).
        if (dimensionOfSession() != mob.dimension()) {
            return;
        }
        if (!chunkTracker.hasChunk(chunkKeyOf(mob.position()))) {
            return;
        }
        writeEquipment(channel, mob.entityId(), Protocol18.EQUIPMENT_SLOT_HELD,
                mob.heldEquipment());
        writeEquipment(channel, mob.entityId(), Protocol18.EQUIPMENT_SLOT_FEET,
                mob.equipment(1));
        writeEquipment(channel, mob.entityId(), Protocol18.EQUIPMENT_SLOT_LEGS,
                mob.equipment(2));
        writeEquipment(channel, mob.entityId(), Protocol18.EQUIPMENT_SLOT_CHEST,
                mob.equipment(3));
        writeEquipment(channel, mob.entityId(), Protocol18.EQUIPMENT_SLOT_HEAD,
                mob.equipment(4));
    }

    /**
     * Set Entity Metadata (0x1C) for the witch's index-21 byte: the
     * drinking flag flip (the client raises the drinking pose and the held
     * potion). Any thread.
     */
    void sendWitchDrinking(MobEntity mob) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        // Wave TF-12 — the mob's own dimension gates the observer (the
        // same law as the spawn: a nether body's metadata never reaches an
        // overworld view).
        if (dimensionOfSession() != mob.dimension()) {
            return;
        }
        ByteBuf out = Unpooled.buffer(12);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_ENTITY_METADATA);
        ByteBufOps.writeVarInt(out, mob.entityId());
        out.writeByte((Protocol18.METADATA_TYPE_BYTE << 5)
                | Protocol18.WITCH_DRINKING_METADATA_INDEX);
        out.writeByte(mob.witchDrinking() ? 1 : 0);
        out.writeByte(Protocol18.METADATA_TERMINATOR);
        enqueueOut(out);
    }

    /**
     * Set Entity Metadata (0x1C) for the ghast's index-16 byte: the
     * charging flag flip (the client raises the open mouth). Any thread.
     */
    void sendGhastCharging(MobEntity mob) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        // Wave TF-12 — the mob's own dimension gates the observer (the
        // same law as the spawn: a nether body's metadata never reaches an
        // overworld view).
        if (dimensionOfSession() != mob.dimension()) {
            return;
        }
        ByteBuf out = Unpooled.buffer(12);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_ENTITY_METADATA);
        ByteBufOps.writeVarInt(out, mob.entityId());
        out.writeByte((Protocol18.METADATA_TYPE_BYTE << 5) | Protocol18.KIND_STATUS_METADATA_INDEX);
        out.writeByte(mob.ghastCharging() ? 1 : 0);
        out.writeByte(Protocol18.METADATA_TERMINATOR);
        enqueueOut(out);
    }

    /**
     * Set Entity Metadata (0x1C) for the blaze's index-16 byte: the
     * fire-active flag flip (the client raises the burning rods). Any
     * thread.
     */
    void sendBlazeFire(MobEntity mob) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        // Wave TF-12 — the mob's own dimension gates the observer (the
        // same law as the spawn: a nether body's metadata never reaches an
        // overworld view).
        if (dimensionOfSession() != mob.dimension()) {
            return;
        }
        ByteBuf out = Unpooled.buffer(12);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_ENTITY_METADATA);
        ByteBufOps.writeVarInt(out, mob.entityId());
        out.writeByte((Protocol18.METADATA_TYPE_BYTE << 5) | Protocol18.KIND_STATUS_METADATA_INDEX);
        out.writeByte(mob.blazeFireActive() ? 1 : 0);
        out.writeByte(Protocol18.METADATA_TERMINATOR);
        enqueueOut(out);
    }

    /**
     * Set Entity Metadata (0x1C) for the wither's four Ints (Slice 59 —
     * WitherEntity.registerSyncedData lines 71-77): the three heads'
     * tracked quarry ids at 17-19 and the invulnerability countdown at
     * 20, republished when any flips (the blue window's per-tick
     * countdown rides it). Any thread.
     */
    void sendWitherMetadata(MobEntity mob) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        // Wave TF-12 — the mob's own dimension gates the observer (the
        // same law as the spawn: a nether body's metadata never reaches an
        // overworld view).
        if (dimensionOfSession() != mob.dimension()) {
            return;
        }
        ByteBuf out = Unpooled.buffer(28);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_ENTITY_METADATA);
        ByteBufOps.writeVarInt(out, mob.entityId());
        int[] heads = mob.witherHeadTargets();
        out.writeByte((Protocol18.METADATA_TYPE_INT << 5)
                | Protocol18.WITHER_HEAD_0_METADATA_INDEX);
        out.writeInt(heads.length > 0 ? heads[0] : 0);
        out.writeByte((Protocol18.METADATA_TYPE_INT << 5)
                | Protocol18.WITHER_HEAD_1_METADATA_INDEX);
        out.writeInt(heads.length > 1 ? heads[1] : 0);
        out.writeByte((Protocol18.METADATA_TYPE_INT << 5)
                | Protocol18.WITHER_HEAD_2_METADATA_INDEX);
        out.writeInt(heads.length > 2 ? heads[2] : 0);
        out.writeByte((Protocol18.METADATA_TYPE_INT << 5)
                | Protocol18.WITHER_INVULN_METADATA_INDEX);
        out.writeInt(mob.witherInvulnTicks());
        out.writeByte(Protocol18.METADATA_TERMINATOR);
        enqueueOut(out);
    }

    /**
     * The Effect packet (0x28) for a GLOBAL world event (the reference's
     * doGlobalEvent — ServerWorldEventListener line 81:
     * PlayerManager.sendPacket to EVERY connected player, no distance and
     * no dimension gate; the 1.8.8 client's WorldRenderer plays the
     * wither's 1013 spawn sound from the global flag). Any thread.
     */
    void sendWorldGlobalEvent(net.zaminmc.torch.util.Position at, int eventId) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        var blockPos = at.toBlockPosition();
        ByteBuf out = Unpooled.buffer(20);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_WORLD_EVENT);
        out.writeInt(eventId);
        ByteBufOps.writePackedBlockPosition(out, blockPos.x(), blockPos.y(), blockPos.z());
        out.writeInt(0); // data
        out.writeBoolean(true); // the global flag
        enqueueOut(out);
    }

    /**
     * Set Entity Metadata (0x1C) for the zombie villager's pair (Slice
     * 25a — ZombieEntity.registerSyncedData lines 91-96): the index-13
     * villager-skin byte and the index-14 converting byte, republished
     * when the cure arms. Any thread.
     */
    void sendZombieState(MobEntity mob) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        // Wave TF-12 — the mob's own dimension gates the observer (the
        // same law as the spawn: a nether body's metadata never reaches an
        // overworld view).
        if (dimensionOfSession() != mob.dimension()) {
            return;
        }
        ByteBuf out = Unpooled.buffer(14);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_ENTITY_METADATA);
        ByteBufOps.writeVarInt(out, mob.entityId());
        out.writeByte((Protocol18.METADATA_TYPE_BYTE << 5)
                | Protocol18.ZOMBIE_VILLAGER_METADATA_INDEX);
        out.writeByte(mob.zombieVillager() ? 1 : 0);
        out.writeByte((Protocol18.METADATA_TYPE_BYTE << 5)
                | Protocol18.ZOMBIE_CONVERTING_METADATA_INDEX);
        out.writeByte(mob.zombieConverting() ? 1 : 0);
        out.writeByte(Protocol18.METADATA_TERMINATOR);
        enqueueOut(out);
    }

    /**
     * The Effect packet (0x28) for the reference's record events — the
     * jukebox's world event 1005 ({@code MusicDiscItem.useOn} line 39's
     * {@code doEvent(null, 1005, pos, Item.getId(this))} on the insert,
     * {@code JukeboxBlock.removeRecord} line 58's {@code doEvent(1005, pos,
     * 0)} on the eject/break): the client plays "records." + the disc's own
     * name when the data resolves to a MusicDiscItem, stops the record at
     * the position otherwise (WorldRenderer lines 1923-1929). Chunk-gated
     * like every world event. Any thread.
     */
    void sendRecordEvent(net.zaminmc.torch.block.BlockPosition at,
            net.zaminmc.torch.util.Identifier disc) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY
                || chunkTracker == null) {
            return;
        }
        if (!chunkTracker.hasChunk(at.chunkPosition().packed())) {
            return; // the observer cannot hear that jukebox
        }
        int data = 0; // the stop arm
        if (disc != null) {
            java.util.Optional<Integer> legacy = LegacyBlockIds.legacyItemId(disc);
            if (legacy.isEmpty()) {
                return; // an unrepresentable disc never reaches the wire
            }
            data = legacy.get();
        }
        ByteBuf out = Unpooled.buffer(20);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_WORLD_EVENT);
        out.writeInt(Protocol18.WORLD_EVENT_PLAY_RECORD);
        ByteBufOps.writePackedBlockPosition(out, at.x(), at.y(), at.z());
        out.writeInt(data);
        out.writeBoolean(false); // not global
        enqueueOut(out);
    }

    /**
     * The Effect packet (0x28) for the reference's own world events —
     * the zombie villager's infect 1016 and un-infect 1017, the wither's
     * 1012/1014, the zombie door family's 1010/1012 ({@code
     * world.doEvent(null, id, pos, 0)}): the client plays the event's
     * sound itself from the id (WorldRenderer lines 1957-1961, the
     * volume-2.0 mob.zombie pair). The fan is the reference's own radius
     * law (README TODO's row 10 — ServerWorldEventListener.doEvent →
     * PlayerManager.sendPacket(source, x, y, z, 64.0, dimension, ...)):
     * the SAME-DIMENSION observers within the squared 4096 — NOT the
     * loaded-chunk window the stand-in gated on (a player 70 blocks out
     * with the chunk loaded hears nothing; a player 60 blocks out with
     * the chunk cold hears the event). Any thread.
     */
    void sendWorldEvent(net.zaminmc.torch.util.Position at, int eventId,
                        int dimension) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        PlayerSession observer = currentSession();
        if (observer == null || observer.dimension() != dimension) {
            return; // another world's event
        }
        double dx = at.x() - observer.position().x();
        double dy = at.y() - observer.position().y();
        double dz = at.z() - observer.position().z();
        if (dx * dx + dy * dy + dz * dz >= 64.0 * 64.0) {
            return; // beyond the reference's event radius
        }
        var blockPos = at.toBlockPosition();
        ByteBuf out = Unpooled.buffer(20);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_WORLD_EVENT);
        out.writeInt(eventId);
        ByteBufOps.writePackedBlockPosition(out, blockPos.x(), blockPos.y(), blockPos.z());
        out.writeInt(0); // data
        out.writeBoolean(false); // not global
        enqueueOut(out);
    }

    /**
     * The firework rocket's launch (Slice 65 — FireworksEntity's tracker
     * registration, EntityMap lines 90-91: (64, 10, syncVelocity)): the
     * Spawn Object 76 bare, the synced slot-8 item stack (the payload the
     * client's own burst reads), and the spawn velocity (the near-zero
     * drift + the 0.05 lift). Chunk-gated like every spawn. Any thread.
     */
    void sendFireworksLaunched(net.zaminmc.torch.server.fireworks.FireworksEntity rocket) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY
                || chunkTracker == null) {
            return;
        }
        // Wave AS's EYFW repair — the rocket's own dimension gates the
        // observer (the 25d mob shape; the join walk's central gate).
        if (dimensionOfSession() != rocket.dimension()) {
            return; // another world's rocket
        }
        var blockPos = rocket.position().toBlockPosition();
        if (!chunkTracker.hasChunk(blockPos.chunkPosition().packed())) {
            return; // outside this observer's loaded window
        }
        ByteBuf out = Unpooled.buffer(40);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_SPAWN_ENTITY);
        ByteBufOps.writeVarInt(out, rocket.entityId());
        out.writeByte(Protocol18.OBJECT_FIREWORKS);
        out.writeInt((int) Math.floor(rocket.position().x() * 32.0));
        out.writeInt((int) Math.floor(rocket.position().y() * 32.0));
        out.writeInt((int) Math.floor(rocket.position().z() * 32.0));
        out.writeByte(0); // pitch: the client re-aims from velocity
        out.writeByte(0); // yaw
        out.writeInt(0); // objectData: bare
        enqueueOut(out);
        // The synced slot-8 item (registerSyncedData line 55: the payload
        // stack the client's burst reads — METADATA_TYPE_SLOT 5).
        ByteBuf meta = Unpooled.buffer(24);
        ByteBufOps.writeVarInt(meta, Protocol18.S2C_ENTITY_METADATA);
        ByteBufOps.writeVarInt(meta, rocket.entityId());
        meta.writeByte((Protocol18.METADATA_TYPE_SLOT << 5) | 8);
        writeSlot(meta, rocket.payload());
        meta.writeByte(Protocol18.METADATA_TERMINATOR);
        enqueueOut(meta);
        // The tracker's syncVelocity arm: the launch velocity rides the
        // spawn (the client integrates its own copy between the syncs).
        ByteBuf velocity = Unpooled.buffer(16);
        ByteBufOps.writeVarInt(velocity, Protocol18.S2C_ENTITY_VELOCITY);
        ByteBufOps.writeVarInt(velocity, rocket.entityId());
        velocity.writeShort((int) Math.round(rocket.velocityX() * 8000.0));
        velocity.writeShort((int) Math.round(rocket.velocityY() * 8000.0));
        velocity.writeShort((int) Math.round(rocket.velocityZ() * 8000.0));
        enqueueOut(velocity);
    }

    /**
     * The rocket's velocity re-sync (the tracker's 10-tick interval with
     * syncVelocity — the 1.15/0.04 flight law keeps changing it). Any
     * thread.
     */
    void sendFireworksVelocity(net.zaminmc.torch.server.fireworks.FireworksEntity rocket) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY
                || chunkTracker == null) {
            return;
        }
        ByteBuf out = Unpooled.buffer(16);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_ENTITY_VELOCITY);
        ByteBufOps.writeVarInt(out, rocket.entityId());
        out.writeShort((int) Math.round(rocket.velocityX() * 8000.0));
        out.writeShort((int) Math.round(rocket.velocityY() * 8000.0));
        out.writeShort((int) Math.round(rocket.velocityZ() * 8000.0));
        enqueueOut(out);
    }

    /**
     * The rocket's burst (Slice 65 — FireworksEntity.tick line 125:
     * {@code doEntityEvent(this, (byte) 17)}): the Entity Status byte the
     * client renders the explosion from (reading the synced slot-8 item's
     * Fireworks compound). Any thread.
     */
    void sendFireworksBurst(net.zaminmc.torch.server.fireworks.FireworksEntity rocket) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY
                || chunkTracker == null) {
            return;
        }
        var blockPos = rocket.position().toBlockPosition();
        if (!chunkTracker.hasChunk(blockPos.chunkPosition().packed())) {
            return; // outside this observer's loaded window
        }
        ByteBuf out = Unpooled.buffer(12);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_ENTITY_STATUS);
        ByteBufOps.writeVarInt(out, rocket.entityId());
        out.writeByte(net.zaminmc.torch.server.fireworks.FireworksEntity.EVENT_BURST);
        enqueueOut(out);
    }

    /** Removes a burst rocket from this observer (the Destroy Entities). */
    void sendFireworksRemoved(int entityId) {
        sendDestroyEntities(entityId);
    }

    /** Removes an ended eye from this observer (the Destroy Entities). */
    void sendEyeRemoved(int entityId) {
        sendDestroyEntities(entityId);
    }

    /**
     * An eye of ender launched (the import-families slice — the
     * reference's EnderEyeEntity, EntityMap line 82: the (64, 4, true)
     * tracker): the Spawn Object 23 at the caller's own position + the
     * launch velocity + the throw's block event 1002 when
     * {@code withThrowEvent} (the reference's
     * {@code world.doEvent(null, 1002, new BlockPos(player), 0)} — the
     * eye spawns AT the player's position, so the same point carries
     * both; a join snapshot replays the spawn alone). Any thread.
     */
    void sendEyeLaunched(net.zaminmc.torch.server.endereye.EnderEyeEntity eye,
                         boolean withThrowEvent) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY
                || chunkTracker == null) {
            return;
        }
        // Wave AS's EYFW repair — the eye's own dimension gates the
        // observer (the 25d mob shape; the join walk's central gate —
        // the live adapter arm keeps its own gate, the mob family's
        // established redundancy).
        if (dimensionOfSession() != eye.dimension()) {
            return; // another world's eye
        }
        var blockPos = eye.position().toBlockPosition();
        if (!chunkTracker.hasChunk(blockPos.chunkPosition().packed())) {
            return; // outside this observer's loaded window
        }
        ByteBuf out = Unpooled.buffer(40);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_SPAWN_ENTITY);
        ByteBufOps.writeVarInt(out, eye.entityId());
        out.writeByte(Protocol18.OBJECT_ENDER_EYE);
        out.writeInt((int) Math.floor(eye.position().x() * 32.0));
        out.writeInt((int) Math.floor(eye.position().y() * 32.0));
        out.writeInt((int) Math.floor(eye.position().z() * 32.0));
        out.writeByte((int) Math.floor(eye.rotation().pitch() * 256.0 / 360.0));
        out.writeByte((int) Math.floor(eye.rotation().yaw() * 256.0 / 360.0));
        out.writeInt(0); // objectData: bare
        enqueueOut(out);
        // The tracker's syncVelocity arm (EntityMap's interval 4).
        ByteBuf velocity = Unpooled.buffer(16);
        ByteBufOps.writeVarInt(velocity, Protocol18.S2C_ENTITY_VELOCITY);
        ByteBufOps.writeVarInt(velocity, eye.entityId());
        velocity.writeShort((int) Math.round(eye.velocityX() * 8000.0));
        velocity.writeShort((int) Math.round(eye.velocityY() * 8000.0));
        velocity.writeShort((int) Math.round(eye.velocityZ() * 8000.0));
        enqueueOut(velocity);
        if (withThrowEvent) {
            // The throw's 1002 (the fire-charge smoke at the player's own
            // position — the eye's spawn point), in the EYE'S OWN WORLD
            // (Wave AS's EYFW repair: the pre-fix comment's own promise —
            // the body now carries its world, so the event rides the
            // eye's dimension, never the hardcoded overworld id).
            sendWorldEvent(eye.position(),
                    Protocol18.WORLD_EVENT_FIRE_CHARGE_SMOKE, eye.dimension());
        }
    }

    /**
     * The eye's 4-tick velocity re-sync (EntityMap's interval 4 with
     * syncVelocity — the steering keeps re-aiming it). Any thread.
     */
    void sendEyeVelocity(net.zaminmc.torch.server.endereye.EnderEyeEntity eye) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        ByteBuf out = Unpooled.buffer(16);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_ENTITY_VELOCITY);
        ByteBufOps.writeVarInt(out, eye.entityId());
        out.writeShort((int) Math.round(eye.velocityX() * 8000.0));
        out.writeShort((int) Math.round(eye.velocityY() * 8000.0));
        out.writeShort((int) Math.round(eye.velocityZ() * 8000.0));
        enqueueOut(out);
    }

    /**
     * Set Entity Metadata (0x1C) for the enderman's whole block (Slice
     * 19d): the carried block's Short at 16 and the screaming byte at
     * 18 — both republished on either flip. Any thread.
     */
    void sendEndermanState(MobEntity mob) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        // Wave TF-12 — the mob's own dimension gates the observer (the
        // same law as the spawn: a nether body's metadata never reaches an
        // overworld view).
        if (dimensionOfSession() != mob.dimension()) {
            return;
        }
        ByteBuf out = Unpooled.buffer(16);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_ENTITY_METADATA);
        ByteBufOps.writeVarInt(out, mob.entityId());
        out.writeByte((Protocol18.METADATA_TYPE_SHORT << 5)
                | Protocol18.KIND_STATUS_METADATA_INDEX);
        out.writeShort(endermanCarriedShort(mob));
        out.writeByte((Protocol18.METADATA_TYPE_BYTE << 5)
                | Protocol18.ENDERMAN_SCREAMING_METADATA_INDEX);
        out.writeByte(mob.endermanAngry() ? 1 : 0);
        out.writeByte(Protocol18.METADATA_TERMINATOR);
        enqueueOut(out);
    }

    /**
     * The carried block's wire form (setCarriedBlock line 273 —
     * {@code Block.serialize(state) & 65535}): the legacy id shifted
     * into the high nibble with the variant metadata below it; empty
     * hands answer 0 (air).
     */
    private static int endermanCarriedShort(MobEntity mob) {
        var carried = mob.endermanCarried();
        if (carried == null) {
            return 0;
        }
        var legacy = LegacyBlockIds.legacyId(carried.identifier());
        if (legacy.isEmpty()) {
            return 0;
        }
        return ((legacy.get() & 0xFFF) << 4)
                | (LegacyBlockIds.metadataOf(carried.identifier()) & 0xF);
    }

    /**
     * Set Entity Metadata (0x1C) for the guardian's whole block: the
     * index-16 flag Int (the moving bit's flip drives the client's spike
     * animation) and the index-17 beam-target Int (the laser's live victim
     * — the entry only rides when a beam is on). Any thread.
     */
    void sendGuardianState(MobEntity mob) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        // Wave TF-12 — the mob's own dimension gates the observer (the
        // same law as the spawn: a nether body's metadata never reaches an
        // overworld view).
        if (dimensionOfSession() != mob.dimension()) {
            return;
        }
        ByteBuf out = Unpooled.buffer(16);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_ENTITY_METADATA);
        ByteBufOps.writeVarInt(out, mob.entityId());
        out.writeByte((Protocol18.METADATA_TYPE_INT << 5) | Protocol18.KIND_STATUS_METADATA_INDEX);
        out.writeInt(guardianFlagsOf(mob));
        if (mob.guardianBeamTargetId() != 0) {
            out.writeByte((Protocol18.METADATA_TYPE_INT << 5)
                    | Protocol18.GUARDIAN_BEAM_METADATA_INDEX);
            out.writeInt(mob.guardianBeamTargetId());
        }
        out.writeByte(Protocol18.METADATA_TERMINATOR);
        enqueueOut(out);
    }

    /**
     * Effect / world event (0x28, protocol 47): i32 effect id, i64 packed
     * pos, i32 data, bool global — the 2002 potion splash's
     * color-carrying burst (the client derives the particle family and the
     * color from the data). Any thread.
     */
    void sendWorldEvent(int eventId, int x, int y, int z, int data, boolean global) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        enqueueOut(encodeWorldEvent(eventId, x, y, z, data, global));
    }

    /**
     * The World Event's byte form (0x21): i32 eventId, the packed block
     * position, i32 data, bool global. The encode-once primitive's builder
     * (§35.3).
     */
    private static ByteBuf encodeWorldEvent(int eventId, int x, int y, int z, int data, boolean global) {
        ByteBuf out = Unpooled.buffer(24);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_WORLD_EVENT);
        out.writeInt(eventId);
        ByteBufOps.writePackedBlockPosition(out, x, y, z);
        out.writeInt(data);
        out.writeBoolean(global);
        return out;
    }

    /**
     * Animation (0x0B) with animation 0: an entity's arm swing (the
     * skeleton's bow draw rides it — the client animates the arm). Any thread.
     */
    void sendMobSwing(MobEntity mob) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        // Wave TF-12 — the mob's own dimension gates the observer (the
        // same law as the spawn: a nether body's metadata never reaches an
        // overworld view).
        if (dimensionOfSession() != mob.dimension()) {
            return;
        }
        ByteBuf out = Unpooled.buffer(8);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_ANIMATION);
        ByteBufOps.writeVarInt(out, mob.entityId());
        out.writeByte(0); // animation 0: the arm swing
        enqueueOut(out);
    }

    /**
     * Animation (0x0B) with a caller-chosen code aimed at a mob: the attack
     * crit bursts ride it — the reference broadcasts EntityAnimationS2CPacket
     * (target, 4) for a falling crit and (target, 5) for the enchanted hit
     * (ServerPlayerEntity.addCritParticles/addEnchantedCritParticles). Any
     * thread.
     */
    void sendMobAnimation(MobEntity mob, int animation) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        // Wave TF-12 — the mob's own dimension gates the observer (the
        // same law as the spawn: a nether body's metadata never reaches an
        // overworld view).
        if (dimensionOfSession() != mob.dimension()) {
            return;
        }
        ByteBuf out = Unpooled.buffer(8);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_ANIMATION);
        ByteBufOps.writeVarInt(out, mob.entityId());
        out.writeByte(animation);
        enqueueOut(out);
    }

    /**
     * Animation (0x0B) aimed at this observer's own body: the crit burst on
     * the victim's own client (the self arm of the reference's sendPacketToAll
     * — the tracked player receives their own broadcast). Any thread.
     */
    void sendSelfAnimation(int animation) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        ByteBuf out = Unpooled.buffer(8);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_ANIMATION);
        ByteBufOps.writeVarInt(out, ownEntityId);
        out.writeByte(animation);
        enqueueOut(out);
    }

    /**
     * Animation (0x0B) for a tracked remote player (the crit burst on the
     * victim's wire body, resolved through this observer's id space). Any
     * thread.
     */
    void sendRemotePlayerAnimation(java.util.UUID remoteUuid, int animation) {
        Integer entityId = remoteEntityIds.get(remoteUuid);
        if (entityId == null) {
            return; // not visible to this observer
        }
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        ByteBuf out = Unpooled.buffer(8);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_ANIMATION);
        ByteBufOps.writeVarInt(out, entityId);
        out.writeByte(animation);
        enqueueOut(out);
    }

    /**
     * Use Bed (0x0A) aimed at this observer's own body: the sleep pose at
     * the bed head cell — the reference's ServerPlayerEntity.trySleep self
     * arm (the trailing {@code networkHandler.sendPacket(packet)} after the
     * tracker fan, lines 491-501). Any thread.
     */
    void sendSelfUseBed(net.zaminmc.torch.block.BlockPosition bedHead) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        ByteBuf out = Unpooled.buffer(16);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_USE_BED);
        ByteBufOps.writeVarInt(out, ownEntityId);
        ByteBufOps.writePackedBlockPosition(out, bedHead.x(), bedHead.y(), bedHead.z());
        enqueueOut(out);
    }

    /**
     * Use Bed (0x0A) for a tracked remote player (the reference's
     * EntityMap.sendPacket arm over the trackers — the sleeping pose the
     * observers render at the bed head cell), the entity id resolved
     * through this observer's own id space. Any thread.
     */
    void sendRemotePlayerUseBed(java.util.UUID remoteUuid,
                                net.zaminmc.torch.block.BlockPosition bedHead) {
        Integer entityId = remoteEntityIds.get(remoteUuid);
        if (entityId == null) {
            return; // not visible to this observer
        }
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        ByteBuf out = Unpooled.buffer(16);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_USE_BED);
        ByteBufOps.writeVarInt(out, entityId);
        ByteBufOps.writePackedBlockPosition(out, bedHead.x(), bedHead.y(), bedHead.z());
        enqueueOut(out);
    }

    /**
     * Explosion (0x27, community-verified layout: f32 xyz, f32 radius, i32
     * count of i8-triplet block offsets, f32 playerMotion xyz — the client
     * applies its own vector and plays the sound + particles itself). Any
     * thread.
     */
    void sendExplosion(EngineServer.ExplosionEvent event) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        PlayerSession session = currentSession();
        double[] motion = session == null ? null : event.playerMotion().get(session.uuid());
        if (motion == null) {
            motion = new double[3]; // spectators of the blast get the visuals only
        }
        ByteBuf out = Unpooled.buffer(64);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_EXPLOSION);
        out.writeFloat((float) event.x());
        out.writeFloat((float) event.y());
        out.writeFloat((float) event.z());
        out.writeFloat(event.radius());
        ByteBufOps.writeVarInt(out, event.blockOffsets().size());
        for (int[] offset : event.blockOffsets()) {
            out.writeByte(offset[0]);
            out.writeByte(offset[1]);
            out.writeByte(offset[2]);
        }
        out.writeFloat((float) motion[0]);
        out.writeFloat((float) motion[1]);
        out.writeFloat((float) motion[2]);
        enqueueOut(out);
    }

    /**
     * Movement + look sync for a walking mob: Rel Move Look (0x17) when the
     * fixed-point delta fits the historical i8 1/32 units, Entity Teleport
     * (0x18) otherwise (large pops: knockback, teleports), plus Entity Head
     * Look (0x19) whenever the head angle changed. Any thread.
     */
    void sendMobMoved(net.zaminmc.torch.server.entity.MobEntity mob) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        // Slice 25d: the body's own dimension gates the observer.
        if (dimensionOfSession() != mob.dimension()) {
            return;
        }
        long[] last = mobSyncState.get(mob.entityId());
        long x = (long) Math.floor(mob.position().x() * 32.0);
        long y = (long) Math.floor(mob.position().y() * 32.0);
        long z = (long) Math.floor(mob.position().z() * 32.0);
        if (last == null) {
            return; // not spawned for this observer yet (chunk-gated join race)
        }
        long dx = x - last[0];
        long dy = y - last[1];
        long dz = z - last[2];
        boolean moved = dx != 0 || dy != 0 || dz != 0;
        if (Math.abs(dx) > 127 || Math.abs(dy) > 127 || Math.abs(dz) > 127) {
            ByteBuf teleport = Unpooled.buffer(40);
            ByteBufOps.writeVarInt(teleport, Protocol18.S2C_ENTITY_TELEPORT);
            ByteBufOps.writeVarInt(teleport, mob.entityId());
            teleport.writeInt((int) x);
            teleport.writeInt((int) y);
            teleport.writeInt((int) z);
            teleport.writeByte(angleBytes(mob.yaw()));
            teleport.writeByte(0);
            teleport.writeBoolean(mob.onGround());
            enqueueOut(teleport);
        } else if (moved) {
            ByteBuf out = Unpooled.buffer(24);
            ByteBufOps.writeVarInt(out, Protocol18.S2C_REL_ENTITY_MOVE_LOOK);
            ByteBufOps.writeVarInt(out, mob.entityId());
            out.writeByte((int) dx);
            out.writeByte((int) dy);
            out.writeByte((int) dz);
            out.writeByte(angleBytes(mob.yaw()));
            out.writeByte(0); // pitch
            out.writeBoolean(mob.onGround());
            enqueueOut(out);
        }
        ByteBuf head = Unpooled.buffer(8);
        ByteBufOps.writeVarInt(head, Protocol18.S2C_ENTITY_HEAD_LOOK);
        ByteBufOps.writeVarInt(head, mob.entityId());
        head.writeByte(angleBytes(mob.headYaw()));
        enqueueOut(head);

        last[0] = x;
        last[1] = y;
        last[2] = z;
    }

    /** Entity Status (0x1A): the hurt flash (2) and the death fall (3). Any thread. */
    void sendMobStatus(net.zaminmc.torch.server.entity.MobEntity mob, int status) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        // Wave TF-12 — the mob's own dimension gates the observer (the
        // same law as the spawn: a nether body's metadata never reaches an
        // overworld view).
        if (dimensionOfSession() != mob.dimension()) {
            return;
        }
        ByteBuf out = Unpooled.buffer(12);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_ENTITY_STATUS);
        out.writeInt(mob.entityId());
        out.writeByte(status);
        enqueueOut(out);
    }

    /** Entity Status (0x1A) for a vehicle body (the hurt flash on a hit). */
    void sendVehicleStatus(net.zaminmc.torch.server.entity.vehicle.VehicleEntity vehicle, int status) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        ByteBuf out = Unpooled.buffer(12);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_ENTITY_STATUS);
        out.writeInt(vehicle.entityId());
        out.writeByte(status);
        enqueueOut(out);
    }

    /** Entity Status for this client's own body (the hurt flash on being hit). */
    void sendSelfStatus(int status) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        ByteBuf out = Unpooled.buffer(12);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_ENTITY_STATUS);
        out.writeInt(ownEntityId);
        out.writeByte(status);
        enqueueOut(out);
    }

    /** Entity Status for a tracked remote player (the observer's hurt flash). */
    void sendRemotePlayerStatus(java.util.UUID remoteUuid, int status) {
        Integer entityId = remoteEntityIds.get(remoteUuid);
        if (entityId == null) {
            return; // not visible to this observer
        }
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        ByteBuf out = Unpooled.buffer(12);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_ENTITY_STATUS);
        out.writeInt(entityId);
        out.writeByte(status);
        enqueueOut(out);
    }

    /**
     * Set Entity Velocity (0x12, community-verified: varint entity id, three
     * i16 motion components in 1/8000 blocks per tick) — the PvP knockback
     * rides to the VICTIM only: the client owns its physics and answers with
     * its own movement packets. Any thread.
     */
    void sendSetVelocity(double vx, double vy, double vz) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        ByteBuf out = Unpooled.buffer(12);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_ENTITY_VELOCITY);
        ByteBufOps.writeVarInt(out, ownEntityId);
        out.writeShort((int) Math.round(vx * 8000.0));
        out.writeShort((int) Math.round(vy * 8000.0));
        out.writeShort((int) Math.round(vz * 8000.0));
        enqueueOut(out);
    }

    /**
     * Named Sound Effect (0x29, community-verified layout: string name, i32
     * xyz at 1/8 block units, f32 volume, u8 pitch): mob hurt/death/chatter
     * with the historical 1.8 resource names. Any thread.
     */
    void sendMobSound(String soundName, Position position, float volume, float pitch) {
        sendSound(soundName, position, volume, pitch);
    }

    /**
     * The mob-sound fan's own ranged form (the reference's
     * {@code World.playSound(Entity, ...)} seat — every mob-emitted sound
     * crosses ServerWorldEventListener.playSound line 43's
     * {@code volume > 1.0F ? 16.0F * volume : 16.0F} band): this observer
     * hears it only inside the range the volume itself carries. Any
     * thread.
     */
    void sendMobSoundRanged(String soundName, Position position, float volume, float pitch) {
        PlayerSession observer = currentSession();
        if (observer != null && beyondRange(observer, position, soundRange(volume))) {
            return;
        }
        sendSound(soundName, position, volume, pitch);
    }

    /**
     * A mob's worn piece shattering into crumbs (the daylight wear's
     * helmet exhaustion — the reference's {@code onBrokenItem} ITEM_CRACK
     * half over the FxManager's ItemShatter burst shape: iconcrack with
     * the item's legacy id + damage nibble as the data pair, count 8,
     * the 0.3 cube, speed 0.15). Any thread.
     */
    void sendMobItemShatter(Position at, net.zaminmc.torch.item.ItemStack stack) {
        PlayerSession observer = currentSession();
        if (observer != null && beyondRange(observer, at, PARTICLE_RANGE)) {
            return;
        }
        Integer legacy = LegacyBlockIds.legacyItemId(stack.type().identifier()).orElse(null);
        if (legacy == null) {
            return;
        }
        sendParticles(Protocol18.PARTICLE_ICON_CRACK, false, at,
                0.3f, 0.3f, 0.3f, 0.15f, 8,
                new int[]{legacy & 0xFFF, stack.damage() & 0xF});
    }

    /**
     * One server-computed display particle (the ServerWorld.addParticle
     * shape, 1:1): World Particles with {@code count 0} so the client
     * renders a single particle at the exact position with the offsets
     * as its velocity — the ambient breeding heart (AnimalEntity.mobTick's
     * HEART), the wolf's shake splash and the enderman's portal cloud all
     * ride it. Distance-culled at the particle family's 16-block range. Any thread.
     */
    void sendDisplayParticle(int particleId, Position at,
                             float vx, float vy, float vz, float velocityScale) {
        if (currentSession() == null
                || beyondRange(currentSession(), at, PARTICLE_RANGE)) {
            return;
        }
        sendParticles(particleId, false, at, vx, vy, vz, velocityScale, 0, new int[0]);
    }

    /**
     * The generalized Named Sound Effect: one semantic FX bus event becomes
     * one packet. The pitch encodes into the wire's u8 (63 = 1.0). Any thread.
     */
    void sendSound(String soundName, Position position, float volume, float pitch) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        enqueueOut(encodeSound(soundName, position, volume, pitch));
    }

    /**
     * The Named Sound Effect's byte form (0x29): string name, i32 xyz at
     * 1/8 block units, f32 volume, u8 pitch. The encode-once primitive's
     * builder — the same bytes every recipient receives, whoever the
     * eligible observer is (§35.3).
     */
    private static ByteBuf encodeSound(String soundName, Position position, float volume, float pitch) {
        ByteBuf out = Unpooled.buffer(32 + soundName.length());
        ByteBufOps.writeVarInt(out, Protocol18.S2C_NAMED_SOUND_EFFECT);
        ByteBufOps.writeString(out, soundName);
        out.writeInt((int) Math.floor(position.x() * 8.0));
        out.writeInt((int) Math.floor(position.y() * 8.0));
        out.writeInt((int) Math.floor(position.z() * 8.0));
        out.writeFloat(volume);
        out.writeByte(Math.round(pitch * 63.0f)); // u8 0-63 around 1.0 = 63
        return out;
    }

    /**
     * World Particles (0x2B, community-verified layout: i32 id, bool
     * longDistance, f32 xyz, f32 offset xyz, f32 data, i32 count, then the
     * crack families' extra varints). Any thread.
     */
    void sendParticles(int particleId, boolean longDistance, Position position,
                       float offsetX, float offsetY, float offsetZ,
                       float particleData, int count, int[] data) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        enqueueOut(encodeParticles(particleId, longDistance, position,
                offsetX, offsetY, offsetZ, particleData, count, data));
    }

    /**
     * The World Particles' byte form (0x2B): i32 id, bool longDistance,
     * f32 xyz, f32 offset xyz, f32 data, i32 count, then the crack
     * families' extra varints. The encode-once primitive's builder (§35.3).
     */
    private static ByteBuf encodeParticles(int particleId, boolean longDistance, Position position,
                                           float offsetX, float offsetY, float offsetZ,
                                           float particleData, int count, int[] data) {
        ByteBuf out = Unpooled.buffer(48);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_WORLD_PARTICLES);
        out.writeInt(particleId);
        out.writeBoolean(longDistance);
        out.writeFloat((float) position.x());
        out.writeFloat((float) position.y());
        out.writeFloat((float) position.z());
        out.writeFloat(offsetX);
        out.writeFloat(offsetY);
        out.writeFloat(offsetZ);
        out.writeFloat(particleData);
        out.writeInt(count);
        if (particleId == Protocol18.PARTICLE_ICON_CRACK) {
            // The crumbs: legacy item id and its variant meta.
            ByteBufOps.writeVarInt(out, data.length > 0 ? data[0] : 0);
            ByteBufOps.writeVarInt(out, data.length > 1 ? data[1] : 0);
        } else if (particleId == Protocol18.PARTICLE_BLOCK_CRACK) {
            // The shards: one combined state varint (id | meta << 12).
            ByteBufOps.writeVarInt(out, data.length > 0 ? data[0] : 0);
        }
        return out;
    }

    /**
     * The FX event's eligibility gate for THIS observer — the recipient law
     * split from the encode (§35.7: the simulation's tracking decides
     * recipients; the transport distributes): the session must exist, the
     * Wave TF-12 dimension order (the reference's PlayerManager.sendPacket
     * lines 630-640: {@code serverplayerentity.dimension == dimension}
     * ahead of the range test — a sound, particle, or world event NEVER
     * crosses dimensions however near the same coordinates sit), the
     * exempt-session skip (the reference's playSound(Entity except, ...)
     * overload), the unknown-legacy-id families' nobody-gets-it law, and
     * the reference's own per-kind ranges (ServerWorldEventListener
     * .playSound line 44: {@code volume > 1.0F ? 16 * volume : 16} for
     * sounds; ServerWorld.addParticle lines 930-936: 16 blocks for the
     * particle family; PlayerManager.sendPacket's 64 for the world events).
     * Any thread.
     */
    boolean fxEligible(net.zaminmc.torch.server.fx.FxManager.FxEvent event,
                       net.zaminmc.torch.server.player.PlayerSession observer) {
        if (observer == null) {
            return false;
        }
        // Wave TF-12 — the reference's own gate order (the dimension test
        // inside the send loop, ahead of the squared-distance test): the
        // observer standing in another world never hears this event.
        if (event.dimension() != dimensionOfSession()) {
            return false;
        }
        Position at;
        if (event instanceof net.zaminmc.torch.server.fx.FxManager.Sound sound) {
            at = sound.position();
            return !beyondRange(observer, at, soundRange(sound.volume()));
        } else if (event instanceof net.zaminmc.torch.server.fx.FxManager.SoundExcept except) {
            // The reference's playSound(Entity except, ...) overload: the
            // exempt session's own observer never hears it (GiveCommand's
            // pop at the granted player); everyone else rides the same
            // range law and packet.
            if (observer == except.exempt()) {
                return false;
            }
            at = except.position();
            return !beyondRange(observer, at, soundRange(except.volume()));
        } else if (event instanceof net.zaminmc.torch.server.fx.FxManager.BlockShatter shatter) {
            return !beyondRange(observer, shatter.position(), PARTICLE_RANGE)
                    && LegacyBlockIds.legacyId(shatter.block().identifier()).isPresent();
        } else if (event instanceof net.zaminmc.torch.server.fx.FxManager.StandShatter burst) {
            return !beyondRange(observer, burst.position(), PARTICLE_RANGE);
        } else if (event instanceof net.zaminmc.torch.server.fx.FxManager.ItemShatter crumbs) {
            return !beyondRange(observer, crumbs.position(), PARTICLE_RANGE)
                    && LegacyBlockIds.legacyItemId(crumbs.stack().type().identifier()).isPresent();
        } else if (event instanceof net.zaminmc.torch.server.fx.FxManager.Poof poof) {
            return !beyondRange(observer, poof.position(), PARTICLE_RANGE);
        } else if (event instanceof net.zaminmc.torch.server.fx.FxManager.Smoke smoke) {
            return !beyondRange(observer, smoke.position(), PARTICLE_RANGE);
        } else if (event instanceof net.zaminmc.torch.server.fx.FxManager.RedstoneOreSpark spark) {
            // The interact burst rides the standard block-particle band
            // (the /particle law's own 16-block radius, the smoke family's
            // own gate).
            return !beyondRange(observer, spark.position(), PARTICLE_RANGE);
        } else if (event instanceof net.zaminmc.torch.server.fx.FxManager.PotionSplash splash) {
            return !beyondRange(observer, splash.position(), PARTICLE_RANGE);
        } else if (event instanceof net.zaminmc.torch.server.fx.FxManager.WorldEvent worldEvent) {
            return !beyondRange(observer, worldEvent.position(), EVENT_RANGE);
        }
        return false;
    }

    /**
     * The FX family's encode-once builder (§35.3): one semantic bus event
     * becomes ONE packet, identical for every eligible observer — the FX
     * packets carry no per-observer field (no entity ids, no per-recipient
     * state), so the encode rides once and every eligible queue takes a
     * share of the same bytes. Null = the event maps to no packet for
     * anyone (the unknown-legacy-id families). Any thread.
     */
    static ByteBuf encodeFx(net.zaminmc.torch.server.fx.FxManager.FxEvent event) {
        if (event instanceof net.zaminmc.torch.server.fx.FxManager.Sound sound) {
            return encodeSound(sound.name(), sound.position(), sound.volume(), sound.pitch());
        } else if (event instanceof net.zaminmc.torch.server.fx.FxManager.SoundExcept except) {
            return encodeSound(except.name(), except.position(), except.volume(), except.pitch());
        } else if (event instanceof net.zaminmc.torch.server.fx.FxManager.BlockShatter shatter) {
            Integer legacy = LegacyBlockIds.legacyId(shatter.block().identifier()).orElse(null);
            if (legacy == null) {
                return null;
            }
            // blockcrack's data varint = (legacy id & 0xFFF) | (meta << 12);
            // this engine's blocks carry no metadata variants yet.
            return encodeParticles(Protocol18.PARTICLE_BLOCK_CRACK, false, shatter.position(),
                    0.4f, 0.4f, 0.4f, 0.3f, 20, new int[]{legacy & 0xFFF});
        } else if (event instanceof net.zaminmc.torch.server.fx.FxManager.StandShatter burst) {
            // The armor stand's breaking burst (addBreakingParticles):
            // BLOCK_DUST over the PLANKS state (Block.serialize = 5),
            // count 10, the stand-box offsets 0.125 x 0.494 x 0.125,
            // speed 0.05.
            return encodeParticles(Protocol18.PARTICLE_BLOCK_DUST, false, burst.position(),
                    0.125f, 0.494f, 0.125f, 0.05f, 10, new int[]{5});
        } else if (event instanceof net.zaminmc.torch.server.fx.FxManager.ItemShatter crumbs) {
            Integer legacy = LegacyBlockIds.legacyItemId(crumbs.stack().type().identifier()).orElse(null);
            if (legacy == null) {
                return null;
            }
            return encodeParticles(Protocol18.PARTICLE_ICON_CRACK, false, crumbs.position(),
                    0.3f, 0.3f, 0.3f, 0.15f, 8,
                    new int[]{legacy & 0xFFF, crumbs.stack().damage() & 0xF});
        } else if (event instanceof net.zaminmc.torch.server.fx.FxManager.Poof poof) {
            return encodeParticles(Protocol18.PARTICLE_SNOWBALL_POOF, false, poof.position(),
                    0.2f, 0.2f, 0.2f, 0.2f, 8, new int[0]);
        } else if (event instanceof net.zaminmc.torch.server.fx.FxManager.Smoke smoke) {
            // The reference's own single SMOKE_NORMAL (EnderEyeItem's
            // sixteen-particle band assembles one event per particle —
            // the caller emits each at its own offset).
            return encodeParticles(Protocol18.PARTICLE_SMOKE_NORMAL, false, smoke.position(),
                    0.0f, 0.0f, 0.0f, 0.0f, 1, new int[0]);
        } else if (event instanceof net.zaminmc.torch.server.fx.FxManager.RedstoneOreSpark spark) {
            // The interact burst's own single reddust (the same
            // one-event-per-particle shape: count 0's velocity-less form
            // — the reference's addParticle(REDSTONE, x, y, z, 0, 0, 0)
            // spawns one static spark at the computed offset).
            return encodeParticles(Protocol18.PARTICLE_REDSTONE, false, spark.position(),
                    0.0f, 0.0f, 0.0f, 0.0f, 1, new int[0]);
        } else if (event instanceof net.zaminmc.torch.server.fx.FxManager.PotionSplash splash) {
            Position at = splash.position();
            // The 2002 world event (PotionEntity.onCollision's closing
            // doEvent): the data carries the potion value — the client
            // spawns the item-crack crumbs and the color-tinted spell
            // burst from it, and the instants tint the spell family.
            return encodeWorldEvent(Protocol18.WORLD_EVENT_POTION_SPLASH,
                    (int) Math.floor(at.x()), (int) Math.floor(at.y()),
                    (int) Math.floor(at.z()), splash.potionData(), false);
        } else if (event instanceof net.zaminmc.torch.server.fx.FxManager.WorldEvent worldEvent) {
            Position at = worldEvent.position();
            // The reference's own doEvent (World lines 2609-2627): the
            // WorldEvent packet over the 64-block event radius — the
            // dispense smoke's 2000 (the client's ten smoke puffs
            // oriented by the facing data) and the bone meal's 2005
            // (DyeItem.spawnParticles' fifteen happy-villager sparks).
            return encodeWorldEvent(worldEvent.eventId(),
                    (int) Math.floor(at.x()), (int) Math.floor(at.y()),
                    (int) Math.floor(at.z()), worldEvent.data(), false);
        }
        return null;
    }

    /**
     * The sound family's own range law (ServerWorldEventListener.playSound
     * line 44): {@code volume > 1.0F ? 16.0 * volume : 16.0} blocks — the
     * explosion's 4.0 carries 64, the block sounds' 1.0 carry 16, the
     * thunder's 10000 carries the whole world.
     */
    private static double soundRange(float volume) {
        return volume > 1.0f ? 16.0 * volume : 16.0;
    }

    /** The range test over the squared distance. */
    private static boolean beyondRange(PlayerSession observer, Position at, double range) {
        Position p = observer.position();
        double dx = at.x() - p.x();
        double dy = at.y() - p.y();
        double dz = at.z() - p.z();
        return dx * dx + dy * dy + dz * dz > range * range;
    }

    /** The particle family's range (ServerWorld.addParticle lines 930-936): 16. */
    private static final double PARTICLE_RANGE = 16.0;

    /** The world-event family's range (PlayerManager.sendPacket's 64.0). */
    private static final double EVENT_RANGE = 64.0;

    /** Destroy Entities (0x13) for one removed mob; drops its sync state. Any thread. */
    void sendMobRemoved(int mobEntityId) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        ByteBuf out = Unpooled.buffer(8);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_DESTROY_ENTITIES);
        ByteBufOps.writeVarInt(out, 1);
        ByteBufOps.writeVarInt(out, mobEntityId);
        enqueueOut(out);
        mobSyncState.remove(mobEntityId);
    }

    // ------------------------------------------------------------------ projectiles

    /**
     * Spawn Entity (0x0E) for a launched projectile: the object type carries
     * the kind (arrow 60, snowball 61, egg 62) and objectData the thrower's
     * id in THIS observer's id space — the thrower's own client sees its own
     * wire id, other observers see the remote player id, a mob thrower keeps
     * its engine id. The velocity triple rides along (the client simulates
     * between syncs). Any thread.
     */
    void sendProjectileSpawned(net.zaminmc.torch.server.entity.projectile.ProjectileEntity projectile) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        PlayerSession current = session;
        if (current == null || chunkTracker == null) {
            return;
        }
        // Wave TF-9 — the body's own dimension gates the observer (the
        // mob spawn's own Slice-25d gate, generalized).
        if (dimensionOfSession() != projectile.dimension()) {
            return;
        }
        var blockPos = projectile.position().toBlockPosition();
        if (!chunkTracker.hasChunk(blockPos.chunkPosition().packed())) {
            return; // outside this observer's loaded window
        }
        int objectType = switch (projectile.kind()) {
            case ARROW -> Protocol18.OBJECT_ARROW;
            case SNOWBALL -> Protocol18.OBJECT_SNOWBALL;
            case EGG -> Protocol18.OBJECT_EGG;
            case SMALL_FIREBALL -> Protocol18.OBJECT_SMALL_FIREBALL;
            case LARGE_FIREBALL -> Protocol18.OBJECT_LARGE_FIREBALL;
            case POTION -> Protocol18.OBJECT_POTION;
            case ENDER_PEARL -> Protocol18.OBJECT_ENDER_PEARL;
            case WITHER_SKULL -> Protocol18.OBJECT_WITHER_SKULL;
            case EXPERIENCE_BOTTLE -> Protocol18.OBJECT_EXPERIENCE_BOTTLE;
        };
        int throwerWireId = resolveThrowerWireId(current, projectile.throwerId());
        // The objectData field (TrackedEntity.createAddEntityPacket 1:1):
        // the arrow carries the shooter's id (its own id when the shooter
        // is gone — the reference's own fallback), the potion carries the
        // damage value (the client builds the bottle with it), the fireball
        // carries the shooter or nothing (a dispensed charge rides bare),
        // and the snowball/egg pair carries nothing at all. The velocity
        // shorts are present ONLY when objectData > 0 (the packet's own
        // read shape — a bare data-0 write with trailing shorts corrupts
        // the client's frame).
        int objectData = switch (projectile.kind()) {
            case ARROW -> throwerWireId > 0 ? throwerWireId : projectile.entityId();
            case POTION -> projectile.potionDamage();
            case SMALL_FIREBALL, LARGE_FIREBALL -> throwerWireId;
            case SNOWBALL, EGG -> 0;
            case ENDER_PEARL -> throwerWireId > 0 ? throwerWireId : 0;
            case WITHER_SKULL -> throwerWireId; // the shooter like the fireball family
            case EXPERIENCE_BOTTLE -> 0; // bare like the shard family
        };
        ByteBuf out = Unpooled.buffer(48);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_SPAWN_ENTITY);
        ByteBufOps.writeVarInt(out, projectile.entityId());
        out.writeByte(objectType);
        out.writeInt((int) Math.floor(projectile.position().x() * 32.0));
        out.writeInt((int) Math.floor(projectile.position().y() * 32.0));
        out.writeInt((int) Math.floor(projectile.position().z() * 32.0));
        out.writeByte(0); // pitch: the client re-aims the model from velocity
        out.writeByte(0); // yaw
        out.writeInt(objectData);
        if (objectData > 0) {
            // The velocity triple: i16 at 1/8000 blocks per tick.
            out.writeShort((int) Math.round(projectile.velocityX() * 8000.0));
            out.writeShort((int) Math.round(projectile.velocityY() * 8000.0));
            out.writeShort((int) Math.round(projectile.velocityZ() * 8000.0));
        }
        enqueueOut(out);
        if (projectile.kind() == net.zaminmc.torch.server.entity.projectile.ProjectileEntity.Kind.WITHER_SKULL) {
            // The skull's charged byte (WitherSkullEntity
            // .registerSyncedData line 100: the synced slot 10 — the
            // client draws the blue skull from it) rides Entity Metadata
            // right after the spawn.
            ByteBuf meta = Unpooled.buffer(10);
            ByteBufOps.writeVarInt(meta, Protocol18.S2C_ENTITY_METADATA);
            ByteBufOps.writeVarInt(meta, projectile.entityId());
            meta.writeByte((Protocol18.METADATA_TYPE_BYTE << 5) | 10);
            meta.writeByte(projectile.charged() ? 1 : 0);
            meta.writeByte(Protocol18.METADATA_TERMINATOR);
            enqueueOut(meta);
        }
    }

    /**
     * Entity Teleport (0x18) for a projectile's tick movement: the launch
     * speed crosses more than the relative-move i8 window in one tick, so the
     * absolute form is the honest transport. Any thread.
     */
    void sendProjectileMoved(net.zaminmc.torch.server.entity.projectile.ProjectileEntity projectile) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        // Wave TF-9 — the observer in another dimension never tracks this
        // body (its spawn never reached them; the teleport must not either).
        if (dimensionOfSession() != projectile.dimension()) {
            return;
        }
        ByteBuf out = Unpooled.buffer(40);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_ENTITY_TELEPORT);
        ByteBufOps.writeVarInt(out, projectile.entityId());
        out.writeInt((int) Math.floor(projectile.position().x() * 32.0));
        out.writeInt((int) Math.floor(projectile.position().y() * 32.0));
        out.writeInt((int) Math.floor(projectile.position().z() * 32.0));
        out.writeByte(0); // yaw
        out.writeByte(0); // pitch
        out.writeBoolean(true); // onGround
        enqueueOut(out);
    }

    /**
     * Set Entity Velocity (0x12) for a deflected fireball (Slice 25b — the
     * reference's velocityChanged flag → the tracker's own velocity packet):
     * the client's fireball model turns around with the punch. The three
     * i16 components ride the 1/8000 blocks-per-tick scale. Any thread.
     */
    void sendProjectileVelocity(net.zaminmc.torch.server.entity.projectile.ProjectileEntity projectile) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        ByteBuf out = Unpooled.buffer(16);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_ENTITY_VELOCITY);
        ByteBufOps.writeVarInt(out, projectile.entityId());
        out.writeShort((int) Math.round(projectile.velocityX() * 8000.0));
        out.writeShort((int) Math.round(projectile.velocityY() * 8000.0));
        out.writeShort((int) Math.round(projectile.velocityZ() * 8000.0));
        enqueueOut(out);
    }

    /**
     * The observer-local thrower id: the thrower's own client recognizes its
     * own wire id; other observers use the remote-player id they already
     * track; mob throwers keep their engine id (one id space). Unknown
     * throwers resolve to 0 (the client spawns the projectile without a
     * shooter link, which it renders identically).
     */
    private int resolveThrowerWireId(PlayerSession observer, int throwerEngineId) {
        if (throwerEngineId < 0) {
            return 0;
        }
        if (observer.engineEntityId() == throwerEngineId) {
            return ownEntityId; // the shooter IS this client
        }
        if (throwerEngineId >= EngineServer.PLAYER_ID_BASE) {
            for (V18Connection connection : adapter.connections().keySet().toArray(new V18Connection[0])) {
                PlayerSession candidate = connection.currentSession();
                if (candidate != null && candidate.engineEntityId() == throwerEngineId) {
                    Integer wireId = remoteEntityIds.get(candidate.uuid());
                    return wireId != null ? wireId : 0;
                }
            }
            return 0;
        }
        return throwerEngineId; // mobs and engine entities share the id space
    }

    /**
     * The burning flag for a Flame arrow (Entity Metadata 0x0C, index 0: the
     * shared living flags byte — the client renders the flame trail). Any
     * thread.
     */
    void sendProjectileBurningFlag(net.zaminmc.torch.server.entity.projectile.ProjectileEntity projectile) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        ByteBuf out = Unpooled.buffer(12);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_ENTITY_METADATA);
        ByteBufOps.writeVarInt(out, projectile.entityId());
        out.writeByte((Protocol18.METADATA_TYPE_BYTE << 5) | Protocol18.LIVING_FLAGS_METADATA_INDEX);
        out.writeByte(projectile.burning() ? Protocol18.LIVING_FLAG_BURNING : 0);
        out.writeByte(Protocol18.METADATA_TERMINATOR);
        enqueueOut(out);
    }

    /**
     * Destroy Entities (0x13) for a removed projectile (shatter, hit or the
     * expiry sweeps). Any thread.
     */
    void sendProjectileRemoved(net.zaminmc.torch.server.entity.projectile.ProjectileEntity projectile) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        // Wave TF-9 — the observer in another dimension never tracked this
        // body; a stray Destroy for an unknown id is noise at best.
        if (dimensionOfSession() != projectile.dimension()) {
            return;
        }
        ByteBuf out = Unpooled.buffer(8);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_DESTROY_ENTITIES);
        ByteBufOps.writeVarInt(out, 1);
        ByteBufOps.writeVarInt(out, projectile.entityId());
        enqueueOut(out);
    }

    /**
     * Spawn Entity (0x0E) for a vehicle: object type 1 = boat, 10 = minecart
     * (community entities.json); a minecart's objectData carries the type
     * index (TrackedEntity lines 413-415: 0 rideable, 1 chest, 2 furnace,
     * 3 TNT, 4 spawner, 5 hopper, 6 command block) and a nonzero index
     * also arms the velocity triple (AddEntityS2CPacket's data > 0 shape —
     * three shorts, velocity * 8000 clamped to ±3.9, Slice 21a). Chunk-gated
     * like every spawn.
     */
    void sendVehicleSpawn(net.zaminmc.torch.server.entity.vehicle.VehicleEntity vehicle) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY
                || chunkTracker == null) {
            return;
        }
        // Wave TF-11 — the body's own dimension gates the observer (the
        // TF-9 item spawn's own gate, generalized).
        if (dimensionOfSession() != vehicle.dimension()) {
            return;
        }
        var blockPos = vehicle.position().toBlockPosition();
        if (!chunkTracker.hasChunk(blockPos.chunkPosition().packed())) {
            return; // observer cannot see that chunk yet
        }
        int objectType = vehicle.kind() == net.zaminmc.torch.server.entity.vehicle.VehicleEntity.Kind.BOAT
                ? Protocol18.OBJECT_BOAT
                : Protocol18.OBJECT_MINECART;
        int objectData = vehicle instanceof net.zaminmc.torch.server.entity.vehicle.MinecartEntity cart
                ? cart.minecartTypeIndex() : 0;
        ByteBuf out = Unpooled.buffer(40);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_SPAWN_ENTITY);
        ByteBufOps.writeVarInt(out, vehicle.entityId());
        out.writeByte(objectType);
        out.writeInt((int) Math.floor(vehicle.position().x() * 32.0));
        out.writeInt((int) Math.floor(vehicle.position().y() * 32.0));
        out.writeInt((int) Math.floor(vehicle.position().z() * 32.0));
        out.writeByte((int) Math.round(vehicle.rotation().pitch() / 360.0 * 256.0));
        out.writeByte((int) Math.floor(vehicle.rotation().yaw() * 256.0 / 360.0));
        out.writeInt(objectData);
        if (objectData > 0) {
            // AddEntityS2CPacket lines 39-71: the ±3.9 clamp, the 8000
            // fixed-point, one short per axis (a fresh cart carries zeros).
            double vx = clampWireVelocity(vehicle.velocityX());
            double vy = clampWireVelocity(vehicle.velocityY());
            double vz = clampWireVelocity(vehicle.velocityZ());
            out.writeShort((int) (vx * 8000.0));
            out.writeShort((int) (vy * 8000.0));
            out.writeShort((int) (vz * 8000.0));
        }
        enqueueOut(out);
    }

    /** The reference's ±3.9 velocity clamp (AddEntityS2CPacket's d3). */
    private static double clampWireVelocity(double velocity) {
        return Math.max(-3.9, Math.min(3.9, velocity));
    }

    /**
     * The TNT cart's fuse armed (Slice 66 — TntMinecartEntity.prime lines
     * 148-152: the doEntityEvent 10 the client's fuse-flash visual rides).
     * Chunk-gated like every vehicle packet. Any thread.
     */
    void sendVehiclePrimed(net.zaminmc.torch.server.entity.vehicle.TntMinecartEntity cart) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY
                || chunkTracker == null) {
            return;
        }
        var blockPos = cart.position().toBlockPosition();
        if (!chunkTracker.hasChunk(blockPos.chunkPosition().packed())) {
            return;
        }
        ByteBuf out = Unpooled.buffer(12);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_ENTITY_STATUS);
        ByteBufOps.writeVarInt(out, cart.entityId());
        out.writeByte(10); // doEntityEvent(this, (byte) 10) — the prime
        enqueueOut(out);
    }

    /**
     * The spawner cart's delay re-rolled (Slice 66 — delay line 187's
     * broacastEvent(1) over SpawnerMinecartEntity's doEntityEvent
     * override): the Entity Status 1 the client's spin reset rides.
     * Chunk-gated like every vehicle packet. Any thread.
     */
    void sendSpawnerCartSpinReset(
            net.zaminmc.torch.server.entity.vehicle.SpawnerMinecartEntity cart) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY
                || chunkTracker == null) {
            return;
        }
        var blockPos = cart.position().toBlockPosition();
        if (!chunkTracker.hasChunk(blockPos.chunkPosition().packed())) {
            return;
        }
        ByteBuf out = Unpooled.buffer(12);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_ENTITY_STATUS);
        ByteBufOps.writeVarInt(out, cart.entityId());
        out.writeByte(1); // doEntityEvent(this, (byte) 1) — the spin reset
        enqueueOut(out);
    }

    /**
     * The bobber spawned (Slice 67 — TrackedEntity line 428:
     * AddEntityS2CPacket(entity, 90, thrower.getNetworkId()) — the
     * objectData names the ANGLER so the client draws the fishing line;
     * a nonzero objectData arms the velocity triple, exactly like the
     * minecart variants'). Chunk-gated. Any thread.
     */
    void sendBobberSpawned(net.zaminmc.torch.server.fishing.FishingSystem.Bobber bobber) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY
                || chunkTracker == null) {
            return;
        }
        var blockPos = bobber.position().toBlockPosition();
        if (!chunkTracker.hasChunk(blockPos.chunkPosition().packed())) {
            return;
        }
        ByteBuf out = Unpooled.buffer(40);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_SPAWN_ENTITY);
        ByteBufOps.writeVarInt(out, bobber.entityId());
        out.writeByte(Protocol18.OBJECT_FISHING_BOBBER);
        out.writeInt((int) Math.floor(bobber.position().x() * 32.0));
        out.writeInt((int) Math.floor(bobber.position().y() * 32.0));
        out.writeInt((int) Math.floor(bobber.position().z() * 32.0));
        out.writeByte(0); // pitch
        out.writeByte(0); // yaw
        out.writeInt(bobber.throwerId()); // the angler's id (the line's anchor)
        // The cast's velocity triple (AddEntityS2CPacket's data > 0 shape).
        out.writeShort((int) (clampWireVelocity(bobber.velocityX()) * 8000.0));
        out.writeShort((int) (clampWireVelocity(bobber.velocityY()) * 8000.0));
        out.writeShort((int) (clampWireVelocity(bobber.velocityZ()) * 8000.0));
        enqueueOut(out);
    }

    /**
     * The bobber moved (Slice 67 — the per-tick Entity Teleport, the
     * shard family's own movement). Chunk-gated. Any thread.
     */
    void sendBobberMoved(net.zaminmc.torch.server.fishing.FishingSystem.Bobber bobber) {
        sendEntityTeleport(bobber.entityId(), bobber.position(), true);
    }

    /** The bobber left (Slice 67 — Destroy). Any thread. */
    void sendBobberRemoved(net.zaminmc.torch.server.fishing.FishingSystem.Bobber bobber) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        ByteBuf out = Unpooled.buffer(8);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_DESTROY_ENTITIES);
        ByteBufOps.writeVarInt(out, 1);
        ByteBufOps.writeVarInt(out, bobber.entityId());
        enqueueOut(out);
    }

    /**
     * A vehicle's tracked state metadata (Slice 66 — the furnace cart's
     * lit byte at the synced slot 16; the command cart's command/last
     * output strings at the synced slots 23-24) plus the flags byte every
     * kind carries (Slice 78 — Entity.java line 347's {@code setFlag(0,
     * onFireTimer > 0)}: the on-fire bit at the synced slot 0, the burn's
     * own wire face; a flip rides the state-change listener exactly like
     * the furnace's lit byte). Any thread.
     */
    void sendVehicleStateMetadata(net.zaminmc.torch.server.entity.vehicle.VehicleEntity vehicle) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY
                || chunkTracker == null) {
            return;
        }
        var blockPos = vehicle.position().toBlockPosition();
        if (!chunkTracker.hasChunk(blockPos.chunkPosition().packed())) {
            return;
        }
        ByteBuf meta = Unpooled.buffer(64);
        ByteBufOps.writeVarInt(meta, Protocol18.S2C_ENTITY_METADATA);
        ByteBufOps.writeVarInt(meta, vehicle.entityId());
        // The flags byte at slot 0 (every kind): bit 0x01 = on fire.
        meta.writeByte((Protocol18.METADATA_TYPE_BYTE << 5) | 0);
        meta.writeByte(vehicle.onFire() ? 0x01 : 0x00);
        if (vehicle instanceof net.zaminmc.torch.server.entity.vehicle.FurnaceMinecartEntity furnace) {
            // registerSyncedData slot 16 — the client swaps the furnace
            // texture from it.
            meta.writeByte((Protocol18.METADATA_TYPE_BYTE << 5) | 16);
            meta.writeByte(furnace.lit() ? 1 : 0);
        }
        if (vehicle instanceof net.zaminmc.torch.server.entity.vehicle.CommandBlockMinecartEntity cart) {
            // CommandBlockMinecartEntity.registerSyncedData lines 64-68:
            // the command at slot 23, the last output's JSON at 24.
            meta.writeByte((Protocol18.METADATA_TYPE_STRING << 5) | 23);
            ByteBufOps.writeString(meta, cart.command());
            meta.writeByte((Protocol18.METADATA_TYPE_STRING << 5) | 24);
            ByteBufOps.writeString(meta,
                    cart.lastOutput() == null ? "" : "{\"text\":\"" + escapeJson(cart.lastOutput()) + "\"}");
        }
        meta.writeByte(Protocol18.METADATA_TERMINATOR);
        enqueueOut(meta);
    }

    /** The minimal JSON string escape (the last output's text component). */
    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * The open chest-minecart window's container (Slice 21a): the session's
     * mount id names the cart; a gone cart (broken while open) answers null
     * and the resync skips — the engine's own close walk drops the window.
     */
    private net.zaminmc.torch.server.chest.ChestBlockEntity engineVehicleChest(int vehicleId) {
        if (vehicleId < 0 || engine.vehicles() == null) {
            return null;
        }
        var vehicle = engine.vehicles().byId(vehicleId);
        return vehicle instanceof net.zaminmc.torch.server.entity.vehicle.ChestMinecartEntity cart
                ? cart.chest() : null;
    }

    /**
     * The open hopper-minecart window's container (Slice 66): the same
     * gone-cart contract as the chest cart's lookup.
     */
    private net.zaminmc.torch.server.hopper.HopperBlockEntity engineVehicleHopper(int vehicleId) {
        if (vehicleId < 0 || engine.vehicles() == null) {
            return null;
        }
        var vehicle = engine.vehicles().byId(vehicleId);
        return vehicle instanceof net.zaminmc.torch.server.entity.vehicle.HopperMinecartEntity cart
                ? cart.hopper() : null;
    }

    /**
     * The per-tick vehicle sync: Entity Teleport for the body; the mounted
     * rider rides along — the rider's own connection gets the absolute
     * Position-and-Look re-anchor (the server-authoritative seat), every
     * OTHER observer gets the rider's teleport in its own id space. Any thread.
     */
    void sendVehicleMoved(net.zaminmc.torch.server.entity.vehicle.VehicleEntity vehicle,
                          PlayerSession rider) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        // Wave TF-11 — the observer in another dimension never tracks this
        // body (its spawn never reached them).
        if (dimensionOfSession() != vehicle.dimension()) {
            return;
        }
        sendEntityTeleport(vehicle.entityId(), vehicle.position(), vehicle.onGround());
        if (rider == null) {
            return;
        }
        if (session == rider) {
            resyncPosition(); // the seat re-anchor: the client's body follows
            return;
        }
        Integer riderWireId = remoteEntityIds.get(rider.uuid());
        if (riderWireId != null) {
            sendEntityTeleport(riderWireId, vehicle.position(), vehicle.onGround());
        }
    }

    /** Destroy Entities for a broken/removed vehicle. Any thread. */
    void sendVehicleRemoved(net.zaminmc.torch.server.entity.vehicle.VehicleEntity vehicle) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        // Wave TF-11 — the observer in another dimension never tracked this
        // body; a stray Destroy for an unknown id is noise at best.
        if (dimensionOfSession() != vehicle.dimension()) {
            return;
        }
        ByteBuf out = Unpooled.buffer(8);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_DESTROY_ENTITIES);
        ByteBufOps.writeVarInt(out, 1);
        ByteBufOps.writeVarInt(out, vehicle.entityId());
        enqueueOut(out);
    }

    /**
     * The ridden mount's seat anchor: the rider's own client re-anchors onto
     * the seat (the body follows the mount); the other observers teleport
     * the rider body. Any thread.
     */
    void sendRiderSeatAnchor(PlayerSession rider, MobEntity mount) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        Position seat = new Position(mount.position().x(),
                mount.position().y() + mount.seatHeight(), mount.position().z());
        if (session == rider) {
            resyncPosition(); // the seat re-anchor: the client's body follows
            return;
        }
        Integer riderWireId = remoteEntityIds.get(rider.uuid());
        if (riderWireId != null) {
            sendEntityTeleport(riderWireId, seat, mount.onGround());
        }
    }

    /**
     * Attach Entity (0x1B, protocol 47): i32 the attached rider, i32 the
     * vehicle, u8 riding (1) — a rider id of -1 clears the seat. The rider's
     * id is observer-local (own entity id on the rider's own connection).
     */
    void sendAttachEntity(PlayerSession rider, int vehicleId, boolean riding) {
        PlayerSession current = session;
        Integer riderWireId;
        if (current == rider) {
            riderWireId = ownEntityId;
        } else {
            riderWireId = remoteEntityIds.get(rider.uuid());
            if (riderWireId == null) {
                return; // the rider is not visible to this observer
            }
        }
        writeAttachEntity(riderWireId, vehicleId, riding);
    }

    /**
     * Attach Entity (0x1B, protocol 47) for a non-session rider (a mob):
     * the rider carries the engine's own global wire id — the same space
     * the mob's spawn publication uses (mob entity ids are global, no
     * observer-local resolution, the vehicles' own convention).
     */
    void sendAttachEntity(int riderId, int vehicleId, boolean riding) {
        writeAttachEntity(riderId, vehicleId, riding);
    }

    /** The shared 0x1B write: i32 rider, i32 vehicle, u8 riding. */
    private void writeAttachEntity(int riderId, int vehicleId, boolean riding) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        ByteBuf out = Unpooled.buffer(12);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_ATTACH_ENTITY);
        out.writeInt(riderId);
        out.writeInt(vehicleId);
        out.writeByte(riding ? 1 : 0);
        enqueueOut(out);
    }

    /**
     * Spawn Object (0x0E) for a leash knot (type 77 — TrackedEntity's own
     * createSpawnPacket arm): the position rides the FENCE BLOCK's raw
     * corner (blockPos * 32, no +0.5 — the reference's own quirk; the
     * client anchors the knot model itself). Any thread.
     */
    void sendKnotSpawned(net.zaminmc.torch.server.leash.LeashKnot knot) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY
                || chunkTracker == null) {
            return;
        }
        if (!chunkTracker.hasChunk(knot.fence().chunkPosition().packed())) {
            return; // outside this observer's loaded window
        }
        ByteBuf out = Unpooled.buffer(32);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_SPAWN_ENTITY);
        ByteBufOps.writeVarInt(out, knot.entityId());
        out.writeByte(Protocol18.OBJECT_LEASH_KNOT);
        out.writeInt(knot.fence().x() * 32);
        out.writeInt(knot.fence().y() * 32);
        out.writeInt(knot.fence().z() * 32);
        out.writeByte(0); // pitch
        out.writeByte(0); // yaw
        out.writeInt(0); // object data
        out.writeShort(0); // velocity x
        out.writeShort(0); // velocity y
        out.writeShort(0); // velocity z
        enqueueOut(out);
    }

    /** Destroy Entities (0x13) for one removed leash knot. Any thread. */
    void sendKnotRemoved(int knotEntityId) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        ByteBuf out = Unpooled.buffer(12);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_DESTROY_ENTITIES);
        ByteBufOps.writeVarInt(out, 1);
        ByteBufOps.writeVarInt(out, knotEntityId);
        enqueueOut(out);
    }

    /**
     * Spawn Object (0x0E) for an item frame (Slice 28a — TrackedEntity
     * lines 493-502): type 71 with the frame's HORIZONTAL FACING riding
     * the objectData int, the position at the RAW wall-cell corner
     * (blockPos * 32, no +0.5 — the reference's own override, the client
     * anchors the model itself), the yaw byte the reference's own
     * facing*90 walk, followed by the frame's metadata pair (the
     * displayed stack at slot 8, the rotation byte at slot 9) so a
     * loaded frame renders on arrival. Any thread.
     */
    void sendFrameSpawned(net.zaminmc.torch.server.decoration.ItemFrame frame) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY
                || chunkTracker == null) {
            return;
        }
        if (!chunkTracker.hasChunk(frame.pos().chunkPosition().packed())) {
            return; // outside this observer's loaded window
        }
        ByteBuf out = Unpooled.buffer(32);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_SPAWN_ENTITY);
        ByteBufOps.writeVarInt(out, frame.entityId());
        out.writeByte(Protocol18.OBJECT_ITEM_FRAME);
        out.writeInt(frame.pos().x() * 32);
        out.writeInt(frame.pos().y() * 32);
        out.writeInt(frame.pos().z() * 32);
        out.writeByte(0); // pitch
        out.writeByte(frame.facing() * 64); // yaw = facing * 90 degrees, byte-scaled
        out.writeInt(frame.facing()); // objectData: the horizontal facing id
        out.writeShort(0); // velocity x
        out.writeShort(0); // velocity y
        out.writeShort(0); // velocity z
        enqueueOut(out);
        // The frame's tracked data (the reference sends it on the tracker's
        // next tick; riding the spawn keeps the join view one packet).
        ByteBuf meta = Unpooled.buffer(24);
        ByteBufOps.writeVarInt(meta, Protocol18.S2C_ENTITY_METADATA);
        ByteBufOps.writeVarInt(meta, frame.entityId());
        if (!frame.display().isEmpty()) {
            meta.writeByte((Protocol18.METADATA_TYPE_SLOT << 5) | 8);
            writeSlot(meta, frame.display());
        }
        meta.writeByte((Protocol18.METADATA_TYPE_BYTE << 5) | 9);
        meta.writeByte(frame.rotation());
        meta.writeByte(Protocol18.METADATA_TERMINATOR);
        enqueueOut(meta);
    }

    /** Destroy Entities (0x13) for one removed item frame. Any thread. */
    void sendFrameRemoved(int frameEntityId) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        ByteBuf out = Unpooled.buffer(12);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_DESTROY_ENTITIES);
        ByteBufOps.writeVarInt(out, 1);
        ByteBufOps.writeVarInt(out, frameEntityId);
        enqueueOut(out);
    }

    /**
     * Set Entity Metadata (0x1C) for a frame's displayed stack change
     * (ItemFrameEntity's synced slot 8, the item type). Any thread.
     */
    void sendFrameItemChanged(net.zaminmc.torch.server.decoration.ItemFrame frame) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY
                || chunkTracker == null
                || !chunkTracker.hasChunk(frame.pos().chunkPosition().packed())) {
            return;
        }
        ByteBuf meta = Unpooled.buffer(24);
        ByteBufOps.writeVarInt(meta, Protocol18.S2C_ENTITY_METADATA);
        ByteBufOps.writeVarInt(meta, frame.entityId());
        meta.writeByte((Protocol18.METADATA_TYPE_SLOT << 5) | 8);
        writeSlot(meta, frame.display());
        meta.writeByte(Protocol18.METADATA_TERMINATOR);
        enqueueOut(meta);
    }

    /**
     * Set Entity Metadata (0x1C) for a frame's rotation change
     * (ItemFrameEntity's synced slot 9, the byte type). Any thread.
     */
    void sendFrameRotationChanged(net.zaminmc.torch.server.decoration.ItemFrame frame) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY
                || chunkTracker == null
                || !chunkTracker.hasChunk(frame.pos().chunkPosition().packed())) {
            return;
        }
        ByteBuf meta = Unpooled.buffer(12);
        ByteBufOps.writeVarInt(meta, Protocol18.S2C_ENTITY_METADATA);
        ByteBufOps.writeVarInt(meta, frame.entityId());
        meta.writeByte((Protocol18.METADATA_TYPE_BYTE << 5) | 9);
        meta.writeByte(frame.rotation());
        meta.writeByte(Protocol18.METADATA_TERMINATOR);
        enqueueOut(meta);
    }

    /**
     * Spawn Painting (0x10 — AddPaintingS2CPacket's own write, Slice
     * 28b): varint entity id, the motive's registry string, the packed
     * wall-cell long, the horizontal facing byte. Any thread.
     */
    void sendPaintingSpawned(net.zaminmc.torch.server.decoration.Painting painting) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY
                || chunkTracker == null) {
            return;
        }
        if (!chunkTracker.hasChunk(painting.pos().chunkPosition().packed())) {
            return; // outside this observer's loaded window
        }
        long packed = ((long) (painting.pos().x() & 0x3FFFFFF) << 38)
                | ((long) (painting.pos().y() & 0xFFF) << 26)
                | (painting.pos().z() & 0x3FFFFFF);
        ByteBuf out = Unpooled.buffer(32);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_SPAWN_PAINTING);
        ByteBufOps.writeVarInt(out, painting.entityId());
        ByteBufOps.writeString(out, painting.motive().name);
        out.writeLong(packed);
        out.writeByte(painting.facing());
        enqueueOut(out);
    }

    /** Destroy Entities (0x13) for one removed painting. Any thread. */
    void sendPaintingRemoved(int paintingEntityId) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        ByteBuf out = Unpooled.buffer(12);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_DESTROY_ENTITIES);
        ByteBufOps.writeVarInt(out, 1);
        ByteBufOps.writeVarInt(out, paintingEntityId);
        enqueueOut(out);
    }

    /**
     * Spawn Object (0x0E) for an armor stand (Slice 28c — TrackedEntity
     * line 491): type 78 at the stand's REAL feet position (the entity's
     * own x/y/z, the AddEntityS2CPacket ctor's walk — unlike the frame's
     * raw-cell override), the snapped yaw byte, followed by the stand's
     * metadata (the status byte at slot 10, the six pose triples at
     * slots 11-16) and the five Entity Equipment packets. Any thread.
     */
    void sendStandSpawned(net.zaminmc.torch.server.armorstand.ArmorStand stand) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY
                || chunkTracker == null) {
            return;
        }
        // Wave AS — the stand's own dimension gates the observer (the
        // 25d mob shape: the same chunk coordinates exist in both
        // worlds — a nether stand never spawns for an overworld
        // observer, whatever the chunk view holds).
        if (dimensionOfSession() != stand.dimension()) {
            return;
        }
        net.zaminmc.torch.block.BlockPosition cell =
                new net.zaminmc.torch.block.BlockPosition(
                        (int) Math.floor(stand.x()), (int) Math.floor(stand.y()),
                        (int) Math.floor(stand.z()));
        if (!chunkTracker.hasChunk(cell.chunkPosition().packed())) {
            return; // outside this observer's loaded window
        }
        ByteBuf out = Unpooled.buffer(32);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_SPAWN_ENTITY);
        ByteBufOps.writeVarInt(out, stand.entityId());
        out.writeByte(Protocol18.OBJECT_ARMOR_STAND);
        out.writeInt((int) Math.floor(stand.x() * 32.0));
        out.writeInt((int) Math.floor(stand.y() * 32.0));
        out.writeInt((int) Math.floor(stand.z() * 32.0));
        out.writeByte(0); // pitch
        out.writeByte((byte) (stand.yaw() * 256.0f / 360.0f)); // the body yaw
        out.writeInt(0); // objectData
        out.writeShort(0); // velocity x
        out.writeShort(0); // velocity y
        out.writeShort(0); // velocity z
        enqueueOut(out);
        sendStandMetadata(channel, stand);
        // The five Entity Equipment packets (the join view's own order).
        for (int slot = 0; slot < 5; slot++) {
            writeEquipment(channel, stand.entityId(), slot, stand.equipment(slot));
        }
    }

    /**
     * The stand's tracked data (the base-flags byte at index 0 — the
     * fire bit 0x01 + the invisible bit 0x20, VT-7i — then the status
     * byte + the six pose triples).
     */
    private void sendStandMetadata(Channel channel,
                                   net.zaminmc.torch.server.armorstand.ArmorStand stand) {
        ByteBuf meta = Unpooled.buffer(96);
        ByteBufOps.writeVarInt(meta, Protocol18.S2C_ENTITY_METADATA);
        ByteBufOps.writeVarInt(meta, stand.entityId());
        meta.writeByte((Protocol18.METADATA_TYPE_BYTE << 5) | 0);
        meta.writeByte(stand.flags());
        meta.writeByte((Protocol18.METADATA_TYPE_BYTE << 5) | 10);
        meta.writeByte(stand.status());
        writePoseEntry(meta, 11, stand.head());
        writePoseEntry(meta, 12, stand.body());
        writePoseEntry(meta, 13, stand.leftArm());
        writePoseEntry(meta, 14, stand.rightArm());
        writePoseEntry(meta, 15, stand.leftLeg());
        writePoseEntry(meta, 16, stand.rightLeg());
        meta.writeByte(Protocol18.METADATA_TERMINATOR);
        enqueueOut(meta);
    }

    /** One pose entry (the wire's Rotation type: three floats). */
    private static void writePoseEntry(ByteBuf meta, int index,
                                       net.zaminmc.torch.server.armorstand.ArmorStand.Pose pose) {
        meta.writeByte((Protocol18.METADATA_TYPE_ROTATION << 5) | index);
        meta.writeFloat(pose.pitch());
        meta.writeFloat(pose.yaw());
        meta.writeFloat(pose.roll());
    }

    /** Destroy Entities (0x13) for one removed armor stand. Any thread. */
    void sendStandRemoved(int standEntityId) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        ByteBuf out = Unpooled.buffer(12);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_DESTROY_ENTITIES);
        ByteBufOps.writeVarInt(out, 1);
        ByteBufOps.writeVarInt(out, standEntityId);
        enqueueOut(out);
    }

    /**
     * The ender crystal's spawn (Slice 62 — Spawn Object 200 + the slot-8
     * Int): the crystal rides the wire only for END-dimension observers
     * whose chunk window holds the body (the same coordinates exist in
     * every world — the mob spawn's own gate shape).
     */
    void sendCrystalSpawn(net.zaminmc.torch.server.endcrystal.EnderCrystal crystal) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY
                || chunkTracker == null) {
            return;
        }
        if (dimensionOfSession() != 1) {
            return; // the crystals live only in the End
        }
        net.zaminmc.torch.block.BlockPosition cell =
                new net.zaminmc.torch.block.BlockPosition(
                        (int) Math.floor(crystal.x()), (int) Math.floor(crystal.y()),
                        (int) Math.floor(crystal.z()));
        if (!chunkTracker.hasChunk(cell.chunkPosition().packed())) {
            return; // outside this observer's loaded window
        }
        ByteBuf out = Unpooled.buffer(32);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_SPAWN_ENTITY);
        ByteBufOps.writeVarInt(out, crystal.entityId());
        out.writeByte(Protocol18.OBJECT_ENDER_CRYSTAL);
        out.writeInt((int) Math.floor(crystal.x() * 32.0));
        out.writeInt((int) Math.floor(crystal.y() * 32.0));
        out.writeInt((int) Math.floor(crystal.z() * 32.0));
        out.writeByte(0); // pitch
        out.writeByte((byte) (crystal.yaw() * 256.0f / 360.0f)); // the body yaw
        out.writeInt(0); // objectData
        out.writeShort(0); // velocity x
        out.writeShort(0); // velocity y
        out.writeShort(0); // velocity z
        enqueueOut(out);
        sendCrystalMetadata(channel, crystal);
    }

    /**
     * The crystal's tracked data (registerSyncedData's own slot 8 — the
     * damage clock Int, 5 at birth).
     */
    private void sendCrystalMetadata(Channel channel,
                                     net.zaminmc.torch.server.endcrystal.EnderCrystal crystal) {
        ByteBuf meta = Unpooled.buffer(16);
        ByteBufOps.writeVarInt(meta, Protocol18.S2C_ENTITY_METADATA);
        ByteBufOps.writeVarInt(meta, crystal.entityId());
        meta.writeByte((Protocol18.METADATA_TYPE_INT << 5) | 8);
        meta.writeInt(crystal.health());
        meta.writeByte(Protocol18.METADATA_TERMINATOR);
        enqueueOut(meta);
    }

    /** The crystal's Destroy Entities (the pop). Any thread. */
    void sendCrystalRemoved(int crystalEntityId) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        ByteBuf out = Unpooled.buffer(12);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_DESTROY_ENTITIES);
        ByteBufOps.writeVarInt(out, 1);
        ByteBufOps.writeVarInt(out, crystalEntityId);
        enqueueOut(out);
    }

    /**
     * The wound's own re-send (takeDamage line 73's zero — the synced
     * slot-8 Int rides the dirty broadcast before the destroy).
     */
    void sendCrystalHealth(net.zaminmc.torch.server.endcrystal.EnderCrystal crystal) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY
                || dimensionOfSession() != 1) {
            return;
        }
        sendCrystalMetadata(channel, crystal);
    }

    /** One Entity Equipment (0x04) update for a stand's changed slot. Any thread. */
    void sendStandEquipment(net.zaminmc.torch.server.armorstand.ArmorStand stand, int slot) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        writeEquipment(channel, stand.entityId(), slot, stand.equipment(slot));
    }

    /**
     * Attach Entity (0x1B) for a mob's leash (MobEntity.attachLeash's own
     * wire arm): the mob's engine id rides the rider slot; the vehicle is
     * the holder — a player's id in THIS observer's space (own id when the
     * observer holds the lead, the remote id otherwise) or the knot's
     * global id. A null holder clears the leash. Any thread.
     */
    void sendLeashAttach(net.zaminmc.torch.server.entity.MobEntity mob,
                         net.zaminmc.torch.server.player.PlayerSession holderPlayer,
                         net.zaminmc.torch.server.leash.LeashKnot holderKnot) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        // Wave TF-12 — the mob's own dimension gates the observer (the
        // same law as the spawn: a nether body's metadata never reaches an
        // overworld view).
        if (dimensionOfSession() != mob.dimension()) {
            return;
        }
        int vehicleId;
        if (holderPlayer != null) {
            PlayerSession current = session;
            if (current == holderPlayer) {
                vehicleId = ownEntityId;
            } else {
                Integer remote = remoteEntityIds.get(holderPlayer.uuid());
                if (remote == null) {
                    return; // the holder is not visible to this observer
                }
                vehicleId = remote;
            }
        } else if (holderKnot != null) {
            vehicleId = holderKnot.entityId();
        } else {
            vehicleId = -1; // the clear
        }
        ByteBuf out = Unpooled.buffer(14);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_ATTACH_ENTITY);
        out.writeInt(mob.entityId());
        out.writeInt(vehicleId);
        out.writeByte(1);
        enqueueOut(out);
    }

    /**
     * The posture broadcast: Entity Metadata (0x1C) for the moving player's
     * observer-local id carrying the living-flags byte (crouch 0x02, sprint
     * 0x10). Sent to every OTHER observer — the mover's client animates its
     * own body. Any thread.
     */
    void sendPostureMetadata(PlayerSession mover) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        PlayerSession current = session;
        if (current == null || current == mover) {
            return; // the mover's client knows its own posture
        }
        Integer wireId = remoteEntityIds.get(mover.uuid());
        if (wireId == null) {
            return; // not visible to this observer
        }
        int flags = (mover.sneaking() ? Protocol18.LIVING_FLAG_SNEAKING : 0)
                | (mover.sprinting() ? Protocol18.LIVING_FLAG_SPRINTING : 0)
                | (mover.burning() ? Protocol18.LIVING_FLAG_BURNING : 0)
                | (mover.ridingAny() ? Protocol18.LIVING_FLAG_RIDING : 0);
        ByteBuf out = Unpooled.buffer(12);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_ENTITY_METADATA);
        ByteBufOps.writeVarInt(out, wireId);
        out.writeByte((Protocol18.METADATA_TYPE_BYTE << 5) | Protocol18.LIVING_FLAGS_METADATA_INDEX);
        out.writeByte(flags);
        out.writeByte(Protocol18.METADATA_TERMINATOR);
        enqueueOut(out);
    }

    /** Animation (0x0B) code 0: the arm swing, aimed at one observer's id space. */
    void sendAnimation(int observerLocalEntityId) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        ByteBuf out = Unpooled.buffer(8);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_ANIMATION);
        ByteBufOps.writeVarInt(out, observerLocalEntityId);
        out.writeByte(Protocol18.ANIMATION_ARM_SWING);
        enqueueOut(out);
    }

    /** Angle float degrees -> the wire's i8 1/256-turn encoding. */
    private static int angleBytes(float degrees) {
        return (int) Math.floor(degrees * 256.0f / 360.0f);
    }

    // ------------------------------------------------------------------ chunk tracking

    /**
     * Per-connection visible-chunk bookkeeping. The world owns chunk state; this
     * tracker only decides what this client needs to receive or unload (§230).
     */
    private static final class ChunkTracker {

        /**
         * The pooled-ask threshold: payloads at or past the benchmark's
         * 16 KiB "large" shape (the size where the pooled arms won 5-10x
         * with ~120 B/packet of thread allocation against the unpooled
         * ask's whole-frame young-gen churn) ride the pooled direct ask;
         * smaller payloads keep the heap-unpooled ask that wins there.
         */
        private static final int POOLED_ASK_THRESHOLD = 16 * 1024;

        /**
         * §35.4's evidenced decision for the CHUNK-WINDOW frames (the two
         * 0x21 forms and the 0x26 bulk — the engine's large outbound
         * allocations, 16 KiB-class and up): the frames ride POOLED DIRECT
         * buffers, every other packet keeps the heap-unpooled ask its own
         * measurement favors. The allocation harness (PacketAllocation-
         * BenchmarkTest, this module's test tree) over the engine's own
         * encode shapes: at 16 KiB the pooled arms produce 685K-1.29M pps
         * with ~120 B of thread allocation per packet and zero collections,
         * against heap-unpooled's 126K pps, 16,448 B/packet of churn (the
         * whole frame becomes young-gen garbage per packet) and a 12 ms
         * collection inside the window — while at 24-192 B (every other
         * packet the engine writes) heap-unpooled WINS 2-7x (11.7M vs 1.6M
         * pps small, 2.1M vs 1.6M medium; the TLAB bump beats the arena
         * path) and the pooled arenas would only add their retained cost
         * (4 MiB measured after release). Pooled where measurement
         * demonstrates the benefit, unpooled where it demonstrates the
         * opposite — the section's own law, both halves. The serializer's
         * byte[] intermediates stay heap (the 1:1-pinned walk, its own
         * future slice if the attribution ever shows them material).
         */
        private static final io.netty.buffer.PooledByteBufAllocator POOLED =
                io.netty.buffer.PooledByteBufAllocator.DEFAULT;

        /** The owning connection — the tracker's writes join the same outbound queue (the FIFO law). */
        private final V18Connection owner;
        private final EngineServer engine;
        private final Channel channel;
        private final java.util.Set<Long> sent = new java.util.HashSet<>();
        /**
         * The dimension this tracker streams (0 the overworld, -1 the
         * nether) — the changeDimension walk re-points it at the arrival
         * (the reference's per-world ChunkMap walk).
         */
        private volatile int dimension;

        private int centerX;
        private int centerZ;

        /**
         * ML-019 — this connection's pending block changes, one buffer per
         * tracked chunk in first-dirty order (the reference's ChunkHolder
         * set held by ChunkMap's dirty list, ChunkMap.java lines 35-39 and
         * 70-76). Tick-lane confined: the world listener queues on the tick
         * thread, the engine's chunkMap section drains there.
         */
        private final java.util.Map<Long, PendingBlockChanges> pendingBlockChanges =
                new java.util.LinkedHashMap<>();

        /**
         * ML-019 — the per-chunk accumulation cell (the reference's
         * ChunkHolder fields, ChunkMap.java lines 272-274: a 64-entry ring,
         * the packed cells, the dirty-section mask). Positions pack exactly
         * as the reference packs them (line 337: localX << 12 | localZ << 8
         * | y); the state rides the LAST commit for a re-queued cell (the
         * reference re-reads the chunk at flush — the same final state).
         */
        private static final class PendingBlockChanges {
            static final int MAX_CELLS = 64;
            final short[] positions = new short[MAX_CELLS];
            final int[] stateIds = new int[MAX_CELLS];
            int count;
            int dirtySections;
        }

        ChunkTracker(V18Connection owner, EngineServer engine, Channel channel) {
            this.owner = owner;
            this.engine = engine;
            this.channel = channel;
        }

        /** Re-points the stream at the destination dimension. */
        void rePointDimension(int destinationDimension) {
            this.dimension = destinationDimension;
        }

        void sendInitial(ChunkPosition center) {
            centerX = center.x();
            centerZ = center.z();
            // ML-019 — the view-send bulk (the reference's per-player
            // pending-chunk walk, ServerPlayerEntity.java lines 233-272: the
            // ready columns of ONE walk ride a single bulk, exactly one the
            // lone single): the synchronously-loaded columns collect here; a
            // not-yet-loaded column keeps the chunk-load callback's single
            // form (the reference's own lone-ready-column case — mixed
            // emission is the law, both sides).
            java.util.List<EngineChunk> batch = new java.util.ArrayList<>();
            forEachInRadius(engine.engineConfig().viewDistance(), position -> {
                synchronized (sent) {
                    sent.add(position.packed());
                }
                EngineChunk loaded = engine.worldFor(dimension).peek(position);
                if (loaded != null) {
                    batch.add(loaded);
                    return;
                }
                // Not yet loaded: generate on the world's owner thread, then
                // serialize as the single form (sendChunk's own path).
                sendChunk(position, true);
            });
            writeChunkBatch(batch, true);
        }

        /**
         * Re-anchors the view after a respawn: everything is re-sent around
         * the new center (duplicate Chunk Data is harmless — the client
         * overwrites), and stale far-chunk unload state is dropped.
         */
        void respawnAt(ChunkPosition center) {
            synchronized (sent) {
                sent.clear();
            }
            sendInitial(center);
        }

        void updateCenter(ChunkPosition center) {
            if (center.x() == centerX && center.z() == centerZ) {
                return;
            }
            centerX = center.x();
            centerZ = center.z();
            int view = engine.engineConfig().viewDistance();

            // Unload bookkeeping: chunks that fell out of range (with one
            // chunk of hysteresis). Protocol 47 has NO unload packet — the
            // 1.8 client prunes out-of-range chunks itself (the vanilla
            // server of the era never said goodbye). Sending 0x1D here was
            // the real-client crash: the client decoded it as Entity Effect
            // and died on the leftover bytes ("packet 29, 3 bytes extra").
            synchronized (sent) {
            sent.removeIf(packed -> {
                ChunkPosition position = ChunkPosition.unpack(packed);
                return position.distanceSquared(center) > (long) (view + 1) * (view + 1);
            });

            // Send chunks that are newly visible. Vanilla always sends the
            // full chunk (ground-up) here: a real 1.8.8 client applies a
            // ground-up=false chunk only when it already holds the chunk
            // (the partial-chunk branch of doPreChunk), so partial chunks
            // for new territory silently never appear — the "chunks don't
            // generate while walking" real-client regression class.
            // ML-019 — the newly-visible columns batch like the initial
            // view's (the reference's pending-chunk walk batches WHATEVER
            // it collects: join, respawn and movement are the same drain).
            java.util.List<EngineChunk> newlyVisible = new java.util.ArrayList<>();
            forEachInRadius(view, position -> {
                boolean newlySent;
                synchronized (sent) {
                    newlySent = sent.add(position.packed());
                }
                if (!newlySent) {
                    return;
                }
                EngineChunk loaded = engine.worldFor(dimension).peek(position);
                if (loaded != null) {
                    newlyVisible.add(loaded);
                    return;
                }
                // Not yet loaded: the single form on the chunk-load callback.
                sendChunk(position, true);
            });
            writeChunkBatch(newlyVisible, true);
            }
        }

        boolean hasChunk(long packed) {
            synchronized (sent) {
                return sent.contains(packed);
            }
        }

        /**
         * Re-sends one chunk column the observer already tracks — the light
         * transport (protocol 47 has no light-only packet; a chunk re-send
         * updates blocks AND both light channels at once). Untracked chunks
         * are skipped: the observer never sees them.
         */
        void resendChunk(ChunkPosition position) {
            if (hasChunk(position.packed())) {
                sendChunk(position, false);
            }
        }

        /**
         * ML-019 — the queue arm (the reference's ChunkMap.onBlockChanged →
         * ChunkHolder.onBlockChanged, ChunkMap.java lines 330-347): the
         * packed cell (localX << 12 | localZ << 8 | y, line 337 verbatim),
         * the dedupe keeping the first slot with the LATEST state (the
         * reference re-reads the chunk at flush — the same final state),
         * the dirty-section mask growing even when the ring is full so the
         * full-buffer arm's partial column covers the overflow (lines
         * 335-346: the mask write precedes the ring bound).
         */
        void queueBlockChange(net.zaminmc.torch.block.BlockPosition position, int packedStateId) {
            if (!channel.isActive()) {
                return;
            }
            long packedChunk = position.chunkPosition().packed();
            if (!hasChunk(packedChunk)) {
                return;
            }
            PendingBlockChanges pending = pendingBlockChanges.computeIfAbsent(
                    packedChunk, key -> new PendingBlockChanges());
            int y = position.y();
            pending.dirtySections |= 1 << (y >> 4);
            if (pending.count < PendingBlockChanges.MAX_CELLS) {
                short encoded = (short) (position.localX() << 12 | position.localZ() << 8 | y);
                for (int i = 0; i < pending.count; i++) {
                    if (pending.positions[i] == encoded) {
                        pending.stateIds[i] = packedStateId;
                        return;
                    }
                }
                pending.positions[pending.count] = encoded;
                pending.stateIds[pending.count] = packedStateId;
                pending.count++;
            }
        }

        /**
         * ML-019 — the per-tick drain (the reference's ChunkHolder
         * .sendChanges dispatch, ChunkMap.java lines 358-401, driven from
         * the engine's chunkMap section): ONE pending cell the single 0x23
         * (lines 360-365), 2-63 the 0x22 batch (lines 384-385), a full ring
         * the partial 0x21 over the dirty sections (lines 369-372). Chunks
         * drain in first-dirty order (the reference's dirty list).
         */
        void flushBlockChanges() {
            if (pendingBlockChanges.isEmpty()) {
                return;
            }
            if (!channel.isActive()) {
                pendingBlockChanges.clear();
                return;
            }
            for (java.util.Map.Entry<Long, PendingBlockChanges> entry : pendingBlockChanges.entrySet()) {
                ChunkPosition position = ChunkPosition.unpack(entry.getKey());
                PendingBlockChanges pending = entry.getValue();
                if (pending.count == 1) {
                    writeSingleBlockChange(position, pending.positions[0], pending.stateIds[0]);
                } else if (pending.count == PendingBlockChanges.MAX_CELLS) {
                    EngineChunk chunk = engine.worldFor(dimension).peek(position);
                    if (chunk != null) {
                        writePartialChunk(chunk, pending.dirtySections);
                    }
                } else {
                    writeMultiBlockChange(position, pending);
                }
            }
            pendingBlockChanges.clear();
        }

        /**
         * The flush's single arm (ChunkMap.java lines 360-365): the cell
         * decodes through the reference's own bands (x from bits 12-15, y
         * from the low byte, z from bits 8-11) and rides the same 0x23 form
         * the immediate unicast sends.
         */
        private void writeSingleBlockChange(ChunkPosition position, short encoded, int packedStateId) {
            ByteBuf out = Unpooled.buffer(16);
            ByteBufOps.writeVarInt(out, Protocol18.S2C_BLOCK_CHANGE);
            ByteBufOps.writePackedBlockPosition(out,
                    position.x() * 16 + (encoded >> 12 & 15),
                    encoded & 255,
                    position.z() * 16 + (encoded >> 8 & 15));
            ByteBufOps.writeVarInt(out, packedStateId);
            owner.enqueueOut(out);
        }

        /**
         * The flush's batch arm (BlocksUpdateS2CPacket.write lines 40-49):
         * the chunk's coords, the varint record count, then each cell's u16
         * and packed-state varint in arrival order.
         */
        private void writeMultiBlockChange(ChunkPosition position, PendingBlockChanges pending) {
            ByteBuf out = Unpooled.buffer(12 + pending.count * 5);
            ByteBufOps.writeVarInt(out, Protocol18.S2C_MULTI_BLOCK_CHANGE);
            out.writeInt(position.x());
            out.writeInt(position.z());
            ByteBufOps.writeVarInt(out, pending.count);
            for (int i = 0; i < pending.count; i++) {
                out.writeShort(pending.positions[i]);
                ByteBufOps.writeVarInt(out, pending.stateIds[i]);
            }
            owner.enqueueOut(out);
        }

        /**
         * The flush's full-ring arm (ChunkMap.java lines 369-372): the
         * partial 0x21 with ground-up false and the DIRTY-section mask —
         * the client rebuilds only the named sections, the overflow cells
         * ride inside them. The effective mask intersects the request with
         * the chunk's own sections (the reference's writeChunkData null
         * drop, WorldChunkS2CPacket lines 68-74).
         */
        private void writePartialChunk(EngineChunk chunk, int dirtySections) {
            int mask = ChunkSerializer18.effectiveSectionMask(chunk, dirtySections);
            if (mask == 0) {
                return; // every dirtied section vanished: nothing to rebuild
            }
            byte[] data = ChunkSerializer18.serialize(chunk, false, mask);
            ByteBuf out = POOLED.directBuffer(16 + data.length);
            ByteBufOps.writeVarInt(out, Protocol18.S2C_CHUNK_DATA);
            out.writeInt(chunk.position().x());
            out.writeInt(chunk.position().z());
            out.writeBoolean(false);
            out.writeShort(mask);
            ByteBufOps.writeVarInt(out, data.length);
            out.writeBytes(data);
            owner.enqueueOut(out);
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
            // The dimension-aware source: the body's world reads its own
            // chunks (the changeDimension walk re-points the connection's
            // dimension at the arrival — the reference's sendWorldInfo +
            // the per-world ChunkMap walk).
            EngineWorld source = engine.worldFor(dimension);
            EngineChunk chunk = source.peek(position);
            if (chunk == null) {
                // Not yet loaded: generate on the world's owner thread, then serialize.
                engine.requestChunkLoad(dimension, position, loaded -> {
                    if (channel.isActive()) {
                        writeChunk(loaded, groundUp);
                    }
                });
                return;
            }
            writeChunk(chunk, groundUp);
        }

        private void writeChunk(EngineChunk chunk, boolean groundUp) {
            // ML-024 — the wire-publish profiler section: the whole
            // serialization+publication path (the ChunkSerializer18 walk,
            // the 0x21 build, the flush, the tile replays) rides its own
            // row so the attribution harness sees the re-stream's cost
            // exactly — the attribution pass found the storm's 17 full
            // re-streams invisible (no section covers this walk; the JFR
            // byte[] 4.15GB sample was the serialization's only trace).
            // NOT a nested push/pop section: this walk runs on three
            // lanes — this connection's netty event loop (the join
            // sequence's sendInitial, the movement lane's updateCenter,
            // the spectate follow's re-center), the tick thread's
            // pre-handler job drain (the respawn/dimension-change
            // re-streams — performRespawn's submitted job runs in
            // EngineTicker's pending-work drain, BEFORE the tick handler,
            // outside the root section's window), and the tick thread's
            // relightTransport section — and the first lane can
            // interleave with the tick thread's open sections, which the
            // profiler's single-caller stack contract forbids (a torn
            // stack answers garbage rows, worse than no data). The
            // recordSectionSpan arm merges atomically into the same table
            // pop merges into, so every lane accumulates into the one
            // root.wirePublish row (the root prefix is the reference's
            // own connection-arm placement — MinecraftServer.tick's root —
            // and the harness's differencing strips it; the row is a
            // CROSS-CUT: where the walk runs inside an open section — the
            // relight re-sends — that section keeps its wall time and
            // this row attributes the same span again). No try/finally:
            // the arm holds no stack to unbalance, so a throwing serialize
            // loses one sample, nothing else. Zero cost while profiling
            // is off (the gate answers before the nanoTime pair — the
            // section law every no-op push/pop already carries).
            net.zaminmc.torch.server.profiler.EngineProfiler wirePublishProfiler =
                    engine.profiler();
            long wirePublishStart = wirePublishProfiler.isProfiling()
                    ? System.nanoTime() : 0L;
            byte[] data = ChunkSerializer18.serialize(chunk, groundUp);
            ByteBuf out = POOLED.directBuffer(16 + data.length);
            ByteBufOps.writeVarInt(out, Protocol18.S2C_CHUNK_DATA);
            out.writeInt(chunk.position().x());
            out.writeInt(chunk.position().z());
            out.writeBoolean(groundUp);
            out.writeShort(ChunkSerializer18.sectionBitmask(chunk));
            ByteBufOps.writeVarInt(out, data.length);
            out.writeBytes(data);
            owner.enqueueOut(out);
            replayChunkTiles(chunk);
            if (wirePublishStart != 0L) {
                wirePublishProfiler.recordSectionSpan("root.wirePublish",
                        System.nanoTime() - wirePublishStart);
            }
        }

        /**
         * The tile replays one freshly sent column owes (protocol 47 chunk
         * data carries no block entities — the reference's PlayerChunkMap
         * sends each BE's createUpdatePacket with the chunk): the sign,
         * spawner, beacon, skull and command-block walks, in the established
         * single-chunk order. Shared by the 0x21 single and the 0x26 bulk
         * (each bulk column replays exactly as its own chunk data would).
         */
        private void replayChunkTiles(EngineChunk chunk) {
            // The sign text replay (protocol 47 chunk data carries no tile
            // entities): every sign in the freshly sent column re-sends its
            // lines, the historical load order. The nether dimension holds
            // no sign state (the sign manager rides the overworld) — the
            // replay is the overworld's walk.
            if (dimension == 0) {
                for (var entry : engine.signs().inChunk(chunk.position())) {
                    owner.enqueueOut(encodeSignUpdate(entry.getKey(), entry.getValue()));
                }
            }
            // The spawner tile replay (Slice 18e — the same protocol 47
            // gap): every spawner in the freshly sent column re-sends its
            // Update Block Entity (the reference's PlayerChunkMap sending
            // the block entity's createUpdatePacket with the chunk), the
            // tile's own dimension gating the walk (a chunk (x, z) exists
            // in both worlds — only this observer's dimension's tiles).
            var spawnerManager = engine.spawnerManager();
            if (spawnerManager != null) {
                for (var tile : spawnerManager.spawnersIn(chunk.position().packed())) {
                    if (tile.dimension() == dimension) {
                        owner.enqueueOut(encodeSpawnerTile(tile));
                    }
                }
            }
            // The beacon tile replay (Slice 29b — the same protocol 47 gap):
            // every beacon in the freshly sent column re-sends its Update
            // Block Entity (the reference's PlayerChunkMap sending the BE's
            // createUpdatePacket with the chunk — the client's own beam walk
            // reads the levels from it).
            if (dimension == 0 && engine.beacons() != null) {
                for (var entry : engine.beacons().inChunk(chunk.position())) {
                    owner.enqueueOut(encodeBeaconTile(entry.getKey(), entry.getValue()));
                }
            }
            // The skull tile replay (Slice 60 — the same protocol 47 gap):
            // every skull in the freshly sent column re-sends its Update
            // Block Entity (the TYPE + the rotation the client's renderer
            // reads — without it every placed skull renders as the default
            // skeleton head).
            if (engine.skulls() != null) {
                for (var tile : engine.skulls().tilesIn(chunk.position().packed())) {
                    owner.enqueueOut(encodeSkullTile(tile));
                }
            }
            // The banner tile replay (the same protocol 47 gap): every
            // banner in the freshly sent column re-sends its Update Block
            // Entity action 6 (the Base + Patterns rows the client's own
            // renderer reads — without it every placed banner renders as
            // the plain white default).
            if (engine.banners() != null) {
                for (var entry : engine.banners().tilesIn(chunk.position().packed())) {
                    owner.enqueueOut(encodeBannerTile(entry.getKey(), entry.getValue()));
                }
            }
            // The command tile replay (Slice 73 — the same protocol 47 gap):
            // every command block in the freshly sent column re-sends its
            // Update Block Entity action 2 (CommandBlockBlockEntity
            // .createUpdatePacket line 75's own rows — the reference's
            // PlayerChunkMap sending the BE's packet with the chunk; the
            // client's own edit GUI reads the live rows from it).
            if (engine.commandBlocks() != null) {
                for (var entry : engine.commandBlocks().inChunk(chunk.position())) {
                    owner.enqueueOut(encodeCommandTile(entry.getKey(), entry.getValue()));
                }
            }
        }

        /**
         * ML-019 — the reference's own bulk-vs-single threshold
         * (ServerPlayerEntity.java line 238: the pending walk collects at
         * most 10 ready columns; lines 257-262: exactly 1 keeps the single
         * 0x21, 2+ ride ONE 0x26). The 10 bound is also the wire's own
         * frame law — the reference client's SplitterHandler accepts at
         * most a 21-bit length (2,097,151 bytes), and ten full columns
         * (~196 KB each at the densest shape) stay inside it with the same
         * margin the reference itself ships.
         */
        private static final int BULK_COLUMNS_MAX = 10;

        /**
         * ML-019 — the view walk's emission pick (the reference's
         * ServerPlayerEntity.java lines 257-262): an empty collection sends
         * nothing, exactly ONE column keeps the single 0x21, and 2+ ride
         * the 0x26 bulk — capped per PACKET at the reference's own 10 (the
         * engine keeps its whole-walk-at-once pacing; the cap bounds the
         * FRAME, and a remainder of exactly one keeps the single law).
         */
        private void writeChunkBatch(java.util.List<EngineChunk> batch, boolean groundUp) {
            if (batch.isEmpty()) {
                return;
            }
            if (batch.size() == 1) {
                writeChunk(batch.get(0), groundUp);
                return;
            }
            for (int from = 0; from < batch.size(); from += BULK_COLUMNS_MAX) {
                int to = Math.min(from + BULK_COLUMNS_MAX, batch.size());
                if (to - from == 1) {
                    writeChunk(batch.get(from), groundUp); // the remainder's single law
                } else {
                    writeChunkBulk(batch.subList(from, to));
                }
            }
        }

        /**
         * ML-019 — the 0x26 Map Chunk Bulk writer (WorldChunksS2CPacket.write
         * lines 57-70): the skylight boolean, the varint column count, every
         * column's header (i32 x, i32 z, u16 section mask), THEN every
         * column's data array concatenated WITHOUT length prefixes — the
         * client sizes each array from its own mask (the reference's read,
         * lines 36-53; the single 0x21's varint length prefix is the one
         * form difference). The skylight boolean is TRUE for both
         * dimensions: the engine's single 0x21 always serializes the
         * skylight pass (ChunkSerializer18 Pass 3, both dimensions — the
         * established real-client-accepted form), so the bulk's arrays match
         * light=true sizing bit-for-bit; the reference derives the flag from
         * !hasNoSky (line 24) and omits the nether's skylight bytes — a
         * divergence the single form already carries, recorded in the
         * landing row. Each column's serialization AND tile replays ride
         * their own root.wirePublish span (the ML-024 count semantics: one
         * record per column whatever the frame shape — the harness's
         * per-column arithmetic stays exact).
         */
        private void writeChunkBulk(java.util.List<EngineChunk> chunks) {
            if (!channel.isActive()) {
                return;
            }
            net.zaminmc.torch.server.profiler.EngineProfiler profiler = engine.profiler();
            // Phase A — every column's payload under its own span (the
            // per-column wire-publish record; the bulk frame's build and
            // flush ride outside — the per-column cost is the story).
            int[] masks = new int[chunks.size()];
            byte[][] payloads = new byte[chunks.size()][];
            int total = 0;
            for (int i = 0; i < chunks.size(); i++) {
                long spanStart = profiler.isProfiling() ? System.nanoTime() : 0L;
                EngineChunk chunk = chunks.get(i);
                masks[i] = ChunkSerializer18.sectionBitmask(chunk);
                payloads[i] = ChunkSerializer18.serialize(chunk, true);
                total += payloads[i].length;
                if (spanStart != 0L) {
                    profiler.recordSectionSpan("root.wirePublish",
                            System.nanoTime() - spanStart);
                }
            }
            // Phase B — the bulk frame: headers first, then every array
            // (the reference's own two-loop write order).
            ByteBuf out = POOLED.directBuffer(16 + 10 * chunks.size() + total);
            ByteBufOps.writeVarInt(out, Protocol18.S2C_CHUNK_BULK);
            out.writeBoolean(true);
            ByteBufOps.writeVarInt(out, chunks.size());
            for (int i = 0; i < chunks.size(); i++) {
                out.writeInt(chunks.get(i).position().x());
                out.writeInt(chunks.get(i).position().z());
                out.writeShort(masks[i]);
            }
            for (byte[] payload : payloads) {
                out.writeBytes(payload);
            }
            owner.enqueueOut(out);
            // Phase C — each column's tile replays under their own span
            // (the single form's per-chunk replay order, column by column —
            // the reference sends the collected BE updates after the bulk,
            // lines 264-266).
            for (EngineChunk chunk : chunks) {
                long spanStart = profiler.isProfiling() ? System.nanoTime() : 0L;
                replayChunkTiles(chunk);
                if (spanStart != 0L) {
                    profiler.recordSectionSpan("root.wirePublish",
                            System.nanoTime() - spanStart);
                }
            }
        }
    }
}