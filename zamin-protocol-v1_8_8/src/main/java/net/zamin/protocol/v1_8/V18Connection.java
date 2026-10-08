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
import net.zamin.engine.config.GameMode;
import net.zamin.engine.net.EngineBridge;
import net.zamin.engine.player.PlayerSession;
import net.zamin.engine.world.EngineChunk;

import net.zamin.api.BlockPosition;
import net.zamin.api.BlockType;
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
    /** This client's own wire entity id (allocated at Join Game). */
    private volatile int ownEntityId = -1;
    /** The position this client was last teleported to (respawn): movement near it skips the distance sanity check. */
    private volatile net.zamin.api.Position lastTeleportAnchor;
    // Remote player entity ids as seen by THIS client (observer-local id space).
    private final java.util.Map<UUID, Integer> remoteEntityIds = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Set<UUID> visibleRemotePlayers = java.util.Collections.synchronizedSet(new java.util.HashSet<>());
    // Furnace-window sync state: the serials last seen by this client, so the
    // per-tick view only sends what actually moved (one open container at a time).
    private int furnaceSlotsSynced = -1;
    private int furnacePropsSynced = -1;
    private final int[] furnacePropsSent = new int[Protocol18.FURNACE_PROP_COUNT];

    V18Connection(V18ProtocolServer adapter, EngineServer engine) {
        this.adapter = adapter;
        this.engine = engine;
    }

    // ------------------------------------------------------------------ pipeline

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
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
                // Center the initial view on the session's actual position: the
                // world spawn for fresh players, the saved spot for returning ones.
                chunkTracker.sendInitial(accepted.session().position()
                        .toBlockPosition().chunkPosition());
                sendInitialPositionAndLook(channel);
                sendWindowItems(channel, accepted.session().inventory().snapshot(),
                        accepted.session().crafting().snapshot(),
                        engine.craftingResult(accepted.session()));
                sendUpdateHealth(accepted.session()); // the body's authoritative baseline
                engine.joinCompleted(accepted.session());
                startKeepAlive(channel);
                adapter.playerEnteredPlay(this);
            }
        });
    }

    private void sendJoinGame(Channel channel, PlayerSession playerSession) {
        int entityId = adapter.nextEntityId();
        this.ownEntityId = entityId;
        ByteBuf out = Unpooled.buffer(32);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_JOIN_GAME);
        out.writeInt(entityId);
        out.writeByte(engine.config().gamemode().legacyId());
        out.writeByte(0);                 // dimension: overworld
        out.writeByte(Protocol18.DIFFICULTY_EASY); // easy: hunger behaves, no mobs yet
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
                engine.chatService().submitChat(player, message);
            }
            case Protocol18.C2S_CLIENT_SETTINGS -> {
                // Accepted and ignored: it carries no gameplay semantics yet.
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

    /** Client Status (0x16): action 0 = perform respawn after dying. */
    private void handleClientStatus(PlayerSession player, ByteBuf packet) {
        int action = ByteBufOps.readVarInt(packet);
        if (action == 0) {
            engine.performRespawn(player);
        }
        // 1 request stats / 2 open inventory achievement: no semantics yet.
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
        channel.writeAndFlush(out);
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
        channel.writeAndFlush(out);
    }

    /**
     * The respawn wire sequence (after the engine reset the body): Respawn
     * packet (0x07), the spawn chunk view re-sent fresh, the authoritative
     * position at spawn, health, and the emptied inventory. Any thread; the
     * engine publishes respawned first.
     */
    void sendRespawnSequence(PlayerSession player) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        ByteBuf out = Unpooled.buffer(32);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_RESPAWN);
        out.writeInt(0); // dimension: overworld
        out.writeByte(Protocol18.DIFFICULTY_EASY);
        out.writeByte(engine.config().gamemode().legacyId());
        ByteBufOps.writeString(out, Protocol18.LEVEL_TYPE_FLAT);
        channel.writeAndFlush(out);

        if (chunkTracker != null) {
            var spawn = engine.world().spawnPosition();
            chunkTracker.respawnAt(spawn.toBlockPosition().chunkPosition());
        }
        // A teleport-sized jump follows: the movement sanity check accepts the
        // client's acknowledgment at the anchor it was just given.
        lastTeleportAnchor = player.position();
        sendPositionAndLook(channel, player.position(), player.rotation(), true);
        sendUpdateHealth(player);
        sendWindowItems(channel, player.inventory().snapshot(),
                player.crafting().snapshot(), engine.craftingResult(player));
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
        channel.writeAndFlush(out);
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
        channel.writeAndFlush(out);
    }

    private void handleDigging(PlayerSession player, ByteBuf packet) {
        int status = packet.readByte();
        int[] pos = ByteBufOps.readPackedBlockPosition(packet);
        boolean creative = engine.config().gamemode() == net.zamin.engine.config.GameMode.CREATIVE;
        try {
            var target = new net.zamin.api.BlockPosition(pos[0], pos[1], pos[2]);
            if (creative) {
                // Creative breaking is instant on "started digging".
                if (status == 0 || status == 2) {
                    engine.blockInteraction().submitCreativeBreak(player, target);
                }
                return;
            }
            switch (status) {
                case 0 -> engine.blockInteraction().submitMiningStart(player, target);
                case 1 -> engine.blockInteraction().submitMiningAborted(player);
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
        short heldId = packet.readShort();
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
        net.zamin.api.BlockPosition target =
                new net.zamin.api.BlockPosition(clicked[0], clicked[1], clicked[2]);
        // A right-click use: the engine decides on the simulation context whether
        // the target block has a container (crafting table -> Open Window) or the
        // use degrades to a placement proposal. Creative claims its block on the
        // wire; survival leaves the decision to the authoritative inventory.
        java.util.Optional<net.zamin.api.BlockType> creativeHeld = java.util.Optional.empty();
        if (engine.config().gamemode() == GameMode.CREATIVE && heldId > 0) {
            net.zamin.api.Identifier heldIdentifier =
                    LegacyBlockIds.identifierOf(heldId & 0xFFFF).orElse(null);
            if (heldIdentifier == null) {
                LOGGER.fine(() -> "Ignored use of unmapped item " + heldId
                        + " from " + player.name());
                return;
            }
            creativeHeld = java.util.Optional.of(engine.blockRegistry().require(heldIdentifier));
        }
        final var held = creativeHeld;
        try {
            engine.useItemOnBlock(player, target, face, held, windowId -> {
                // The engine recorded the container kind before dispatching the
                // callback; the adapter picks the matching Open Window flavor.
                Channel channel = adapter.channelOf(this);
                switch (player.openContainerKind()) {
                    case FURNACE -> sendFurnaceWindow(channel, player, windowId);
                    case CHEST -> sendChestWindow(channel, player, windowId);
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
        out.writeByte(10); // the GUI's own slots: result + 3x3 grid
        channel.writeAndFlush(out);
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
        ByteBufOps.writeString(out, Protocol18.FURNACE_WINDOW_TITLE);
        out.writeByte(3); // the GUI's own slots: input, fuel, output
        channel.writeAndFlush(out);
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
        ByteBufOps.writeString(out, Protocol18.CHEST_WINDOW_TITLE);
        out.writeByte(27); // the GUI's own slots: 3 rows of 9
        channel.writeAndFlush(out);
        sendContainerWindowItems(channel, windowId, player);
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
        channel.writeAndFlush(out);
    }

    /**
     * Per-tick furnace view (engine fan-out to the open viewer): sends window
     * properties whose values moved and a full 39-slot Window Items resync
     * when the furnace's slots changed. Tick-thread context.
     */
    void sendFurnaceViewTick(Channel channel, int windowId,
                             net.zamin.engine.furnace.FurnaceBlockEntity furnace) {
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        if (furnace.propsSerial() != furnacePropsSynced) {
            furnacePropsSynced = furnace.propsSerial();
            int[] values = {
                    furnace.burnTimeRemaining(),
                    furnace.burnTimeTotal(),
                    furnace.cookTime(),
                    net.zamin.engine.furnace.FurnaceRecipes.COOK_TICKS};
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
        net.zamin.api.Position anchor = lastTeleportAnchor;
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
        channel.writeAndFlush(out);
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
        if (!visibleRemotePlayers.add(other.uuid())) {
            return; // already tracked
        }
        int entityId = remoteEntityIds.computeIfAbsent(other.uuid(),
                uuid -> adapter.nextEntityId());
        Position pos = other.position();
        ByteBuf out = Unpooled.buffer(48);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_NAMED_SPAWN);
        ByteBufOps.writeVarInt(out, entityId);
        ByteBufOps.writeString(out, other.uuid().toString());
        out.writeInt((int) Math.floor(pos.x() * 32.0));
        out.writeInt((int) Math.floor(pos.y() * 32.0));
        out.writeInt((int) Math.floor(pos.z() * 32.0));
        out.writeByte((int) (other.rotation().yaw() * 256.0f / 360.0f));
        out.writeByte((int) (other.rotation().pitch() * 256.0f / 360.0f));
        out.writeShort(0); // held item: none
        out.writeByte(Protocol18.METADATA_TERMINATOR); // no metadata entries (0x7F, protocol 47)
        channel.writeAndFlush(out);
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
        channel.writeAndFlush(out);
    }

    /** Sends an absolute-position teleport for a tracked remote player. */
    void sendRemoteTeleport(PlayerSession other) {
        Channel channel = adapter.channelOf(this);
        Integer entityId = remoteEntityIds.get(other.uuid());
        if (channel == null || !channel.isActive() || entityId == null || other == session) {
            return;
        }
        Position pos = other.position();
        ByteBuf out = Unpooled.buffer(40);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_ENTITY_TELEPORT);
        ByteBufOps.writeVarInt(out, entityId);
        out.writeInt((int) Math.floor(pos.x() * 32.0));
        out.writeInt((int) Math.floor(pos.y() * 32.0));
        out.writeInt((int) Math.floor(pos.z() * 32.0));
        out.writeByte((int) (other.rotation().yaw() * 256.0f / 360.0f) & 0xFF);
        out.writeByte((int) (other.rotation().pitch() * 256.0f / 360.0f) & 0xFF);
        out.writeBoolean(other.onGround());
        channel.writeAndFlush(out);
    }

    UUID sessionUuid() {
        PlayerSession current = session;
        return current == null ? null : current.uuid();
    }

    PlayerSession currentSession() {
        return session;
    }

    // ------------------------------------------------------------------ outgoing sync

    /**
     * Pushes a committed world change to this client if the chunk is visible.
     * Called on the simulation thread by the adapter's broadcast; Netty writes
     * are thread-safe, tracker reads are synchronized.
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
        channel.writeAndFlush(out);
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
    void sendWindowItems(Channel channel, java.util.List<net.zamin.api.ItemStack> engineSlots,
                         java.util.List<net.zamin.api.ItemStack> craftCells,
                         net.zamin.api.ItemStack craftingResult) {
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        ByteBuf out = Unpooled.buffer(64 + engineSlots.size() * 6);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_WINDOW_ITEMS);
        out.writeByte(Protocol18.INVENTORY_WINDOW_ID);
        out.writeShort(Protocol18.INVENTORY_WINDOW_SLOTS);
        for (int wireSlot = 0; wireSlot < Protocol18.INVENTORY_WINDOW_SLOTS; wireSlot++) {
            // Wire 0 = crafting result preview; 1-4 = the 2x2 grid (row-major);
            // 9-35 = engine main 9-35; 36-44 = engine hotbar 0-8; armor empty.
            net.zamin.api.ItemStack stack = engineSlotForWireSlot(wireSlot, engineSlots,
                    craftCells, craftingResult);
            writeSlot(out, stack);
        }
        channel.writeAndFlush(out);
    }

    private static net.zamin.api.ItemStack engineSlotForWireSlot(
            int wireSlot, java.util.List<net.zamin.api.ItemStack> engineSlots,
            java.util.List<net.zamin.api.ItemStack> craftCells,
            net.zamin.api.ItemStack craftingResult) {
        if (wireSlot == Protocol18.WIRE_SLOT_RESULT) {
            return craftingResult;
        }
        if (wireSlot >= Protocol18.WIRE_SLOT_CRAFT_FIRST
                && wireSlot <= Protocol18.WIRE_SLOT_CRAFT_LAST) {
            return craftCells.get(wireSlot - Protocol18.WIRE_SLOT_CRAFT_FIRST);
        }
        int engineSlot = switch (wireSlot) {
            case 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26,
                 27, 28, 29, 30, 31, 32, 33, 34, 35 -> wireSlot;
            case 36, 37, 38, 39, 40, 41, 42, 43, 44 -> wireSlot - Protocol18.WIRE_SLOT_HOTBAR_BASE;
            default -> -1; // armor slots are out of scope this slice
        };
        return engineSlot < 0 ? net.zamin.api.ItemStack.EMPTY : engineSlots.get(engineSlot);
    }

    /**
     * Full authoritative sync of an open container window; the layout follows
     * the container kind (crafting table 46 slots, furnace 39 slots). Any
     * thread; owner supplies the session state.
     */
    void sendContainerWindowItems(Channel channel, int windowId, PlayerSession player) {
        if (player.openContainerKind()
                == net.zamin.engine.player.PlayerSession.ContainerKind.FURNACE) {
            var position = player.openContainerPosition();
            var furnace = position == null ? null : engine.furnaces().peek(position);
            if (furnace != null) {
                sendFurnaceWindowItems(channel, windowId, furnace, player);
            }
            return;
        }
        if (player.openContainerKind()
                == net.zamin.engine.player.PlayerSession.ContainerKind.CHEST) {
            var position = player.openContainerPosition();
            var chest = position == null ? null : engine.chests().peek(position);
            if (chest != null) {
                sendChestWindowItems(channel, windowId, chest, player);
            }
            return;
        }
        sendCraftingTableWindowItems(channel, windowId, player);
    }

    /**
     * Full authoritative sync of the chest window: 63 slots — 0-26 the chest's
     * own state (community-verified layout), 27-53 main inventory (engine
     * 9-35), 54-62 hotbar (engine 0-8) — the viewer's inventory. Any thread.
     */
    private void sendChestWindowItems(Channel channel, int windowId,
                                      net.zamin.engine.chest.ChestBlockEntity chest,
                                      PlayerSession player) {
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        ByteBuf out = Unpooled.buffer(96);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_WINDOW_ITEMS);
        out.writeByte(windowId);
        out.writeShort(Protocol18.CHEST_WINDOW_SLOTS);
        var chestSlots = chest.snapshotSlots();
        java.util.List<net.zamin.api.ItemStack> inventory =
                player == null ? java.util.List.of() : player.inventory().snapshot();
        for (int wireSlot = 0; wireSlot < Protocol18.CHEST_WINDOW_SLOTS; wireSlot++) {
            net.zamin.api.ItemStack stack;
            if (wireSlot <= Protocol18.CHEST_WIRE_SLOT_LAST) {
                stack = chestSlots[wireSlot];
            } else if (wireSlot <= Protocol18.CHEST_WIRE_SLOT_MAIN_LAST) {
                stack = inventory.get(wireSlot - 18); // main inventory: engine 9-35
            } else {
                stack = inventory.get(wireSlot - Protocol18.CHEST_WIRE_SLOT_HOTBAR_BASE);
            }
            writeSlot(out, stack);
        }
        channel.writeAndFlush(out);
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
        java.util.List<net.zamin.api.ItemStack> slots = player.inventory().snapshot();
        java.util.List<net.zamin.api.ItemStack> grid = player.tableCrafting().snapshot();
        net.zamin.api.ItemStack result = engine.containerResult(player);
        for (int wireSlot = 0; wireSlot < Protocol18.TABLE_WINDOW_SLOTS; wireSlot++) {
            writeSlot(out, containerSlotForWireSlot(wireSlot, slots, grid, result));
        }
        channel.writeAndFlush(out);
    }

    /**
     * Full authoritative sync of the furnace window: 39 slots — 0 input,
     * 1 fuel, 2 output (the block entity's world state), 3-29 main, 30-38
     * hotbar (the viewer's inventory). Any thread.
     */
    private void sendFurnaceWindowItems(Channel channel, int windowId,
                                        net.zamin.engine.furnace.FurnaceBlockEntity furnace,
                                        PlayerSession player) {
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        ByteBuf out = Unpooled.buffer(96);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_WINDOW_ITEMS);
        out.writeByte(windowId);
        out.writeShort(Protocol18.FURNACE_WINDOW_SLOTS);
        var furnaceSlots = furnace.snapshotSlots();
        java.util.List<net.zamin.api.ItemStack> inventory =
                player == null ? java.util.List.of() : player.inventory().snapshot();
        for (int wireSlot = 0; wireSlot < Protocol18.FURNACE_WINDOW_SLOTS; wireSlot++) {
            net.zamin.api.ItemStack stack;
            if (wireSlot <= 2) {
                stack = furnaceSlots[wireSlot];
            } else if (wireSlot <= Protocol18.FURNACE_WIRE_SLOT_MAIN_LAST) {
                stack = inventory.get(wireSlot + 6); // main inventory: engine 9-35
            } else {
                stack = inventory.get(wireSlot - Protocol18.FURNACE_WIRE_SLOT_HOTBAR_BASE);
            }
            writeSlot(out, stack);
        }
        channel.writeAndFlush(out);
    }

    private static net.zamin.api.ItemStack containerSlotForWireSlot(
            int wireSlot, java.util.List<net.zamin.api.ItemStack> engineSlots,
            java.util.List<net.zamin.api.ItemStack> gridCells,
            net.zamin.api.ItemStack containerResult) {
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
        return net.zamin.api.ItemStack.EMPTY;
    }

    /** Single authoritative slot update in window 0. */
    void sendSetSlot(net.zamin.api.ItemStack stack, int engineSlot) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        ByteBuf out = Unpooled.buffer(12);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_SET_SLOT);
        out.writeByte(Protocol18.INVENTORY_WINDOW_ID);
        out.writeShort(wireSlotOf(engineSlot));
        writeSlot(out, stack);
        channel.writeAndFlush(out);
    }

    /**
     * 1.8 slot encoding (community-verified against the real client's parser,
     * protodef's optionalNbt + vanilla {@code PacketBuffer.writeItemStackToBuffer}):
     * i16 block/item id, -1 = empty; otherwise i8 count, i16 damage, then the
     * NBT marker — a single 0x00 byte (TAG_End) when the stack carries no
     * compound, or a full NBT payload starting with its type byte. Writing the
     * historical short -1 here desyncs every real parser (mineflayer caught it).
     */
    private static void writeSlot(ByteBuf out, net.zamin.api.ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            out.writeShort(-1);
            return;
        }
        Integer legacy = LegacyBlockIds.legacyId(stack.type().identifier()).orElse(null);
        if (legacy == null) {
            out.writeShort(-1); // unmappable item: represent as empty on this wire
            return;
        }
        out.writeShort(legacy);
        out.writeByte(stack.count());
        out.writeShort(stack.damage()); // durability wear / variant metadata
        out.writeByte(0); // no NBT: the single TAG_End marker
    }

    // ------------------------------------------------------------------ item entity sync

    /** Spawn Entity + item metadata for a new item entity. Any thread. */
    void sendItemSpawn(net.zamin.engine.entity.ItemEntity entity) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY
                || chunkTracker == null) {
            return;
        }
        var blockPos = entity.position().toBlockPosition();
        if (!chunkTracker.hasChunk(blockPos.chunkPosition().packed())) {
            return; // observer cannot see that chunk yet
        }
        Integer legacy = LegacyBlockIds.legacyId(entity.stack().type().identifier()).orElse(null);
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
        channel.writeAndFlush(out);

        // The item stack itself rides on entity metadata (type slot, index 10).
        ByteBuf meta = Unpooled.buffer(16);
        ByteBufOps.writeVarInt(meta, Protocol18.S2C_ENTITY_METADATA);
        ByteBufOps.writeVarInt(meta, entity.entityId());
        meta.writeByte((Protocol18.METADATA_TYPE_SLOT << 5) | Protocol18.ITEM_STACK_METADATA_INDEX);
        writeSlot(meta, entity.stack());
        meta.writeByte(Protocol18.METADATA_TERMINATOR);
        channel.writeAndFlush(meta);
    }

    /** Absolute-position sync for a moving item entity. Any thread. */
    void sendItemTeleport(net.zamin.engine.entity.ItemEntity entity) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        ByteBuf out = Unpooled.buffer(40);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_ENTITY_TELEPORT);
        ByteBufOps.writeVarInt(out, entity.entityId());
        out.writeInt((int) Math.floor(entity.position().x() * 32.0));
        out.writeInt((int) Math.floor(entity.position().y() * 32.0));
        out.writeInt((int) Math.floor(entity.position().z() * 32.0));
        out.writeByte(0);
        out.writeByte(0);
        out.writeBoolean(entity.onGround());
        channel.writeAndFlush(out);
    }

    /** Updated item stack metadata after a partial pickup. Any thread. */
    void sendItemStackUpdate(net.zamin.engine.entity.ItemEntity entity) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        ByteBuf meta = Unpooled.buffer(16);
        ByteBufOps.writeVarInt(meta, Protocol18.S2C_ENTITY_METADATA);
        ByteBufOps.writeVarInt(meta, entity.entityId());
        meta.writeByte((Protocol18.METADATA_TYPE_SLOT << 5) | Protocol18.ITEM_STACK_METADATA_INDEX);
        writeSlot(meta, entity.stack());
        meta.writeByte(Protocol18.METADATA_TERMINATOR);
        channel.writeAndFlush(meta);
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
        channel.writeAndFlush(out);

        ByteBuf destroy = Unpooled.buffer(8);
        ByteBufOps.writeVarInt(destroy, Protocol18.S2C_DESTROY_ENTITIES);
        ByteBufOps.writeVarInt(destroy, 1);
        ByteBufOps.writeVarInt(destroy, itemEntityId);
        channel.writeAndFlush(destroy);
    }

    /** Removes an item entity from this observer. Any thread. */
    void sendItemRemoved(int itemEntityId) {
        Channel channel = adapter.channelOf(this);
        if (channel == null || !channel.isActive() || state != WireState.PLAY) {
            return;
        }
        ByteBuf out = Unpooled.buffer(8);
        ByteBufOps.writeVarInt(out, Protocol18.S2C_DESTROY_ENTITIES);
        ByteBufOps.writeVarInt(out, 1);
        ByteBufOps.writeVarInt(out, itemEntityId);
        channel.writeAndFlush(out);
    }

    private Integer wireEntityIdOf(PlayerSession player) {
        if (player == session) {
            return ownEntityId >= 0 ? ownEntityId : null;
        }
        return remoteEntityIds.get(player.uuid());
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
                synchronized (sent) {
                    sent.add(position.packed());
                }
                sendChunk(position, true);
            });
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
            int view = engine.config().viewDistance();

            // Unload first: chunks that fell out of range (with one chunk of hysteresis).
            synchronized (sent) {
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
                boolean newlySent;
                synchronized (sent) {
                    newlySent = sent.add(position.packed());
                }
                if (newlySent) {
                    sendChunk(position, false);
                }
            });
            }
        }

        boolean hasChunk(long packed) {
            synchronized (sent) {
                return sent.contains(packed);
            }
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
