package net.zaminmc.torch.protocol.v1_8;

import net.zaminmc.torch.util.Position;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.GlobalEventExecutor;
import net.zaminmc.torch.block.ChunkPosition;
import net.zaminmc.torch.server.EngineServer;
import net.zaminmc.torch.server.player.PlayerSession;
import net.zaminmc.torch.server.net.ProtocolAdapter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * The 1.8.8 protocol adapter: Netty transport owning the wire for protocol 47.
 * This is the only place in the codebase that talks both Netty and engine;
 * everything else stays on one side of that boundary.
 */
public final class V18ProtocolServer implements ProtocolAdapter {

    private static final Logger LOGGER = Logger.getLogger(V18ProtocolServer.class.getName());

    static final long DEFAULT_KEEP_ALIVE_INTERVAL_MS = 10_000;

    private final EngineServer engine;
    private final long keepAliveIntervalMs;
    private final Map<V18Connection, Channel> connections = new ConcurrentHashMap<>();
    private final ChannelGroup allChannels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    private final AtomicInteger entityIds = new AtomicInteger();
    private final AtomicBoolean running = new AtomicBoolean(false);

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    public V18ProtocolServer(EngineServer engine) {
        this(engine, DEFAULT_KEEP_ALIVE_INTERVAL_MS);
    }

    V18ProtocolServer(EngineServer engine, long keepAliveIntervalMs) {
        this.engine = engine;
        this.keepAliveIntervalMs = keepAliveIntervalMs;
        // §27.3's packet construction: the composed-frame sender installed
        // at construction — the registry's send() resolves a player's
        // PLAY-state connection through here and writes through the shared
        // pipeline (the interception walk sees composed frames like any
        // engine frame).
        ((net.zaminmc.torch.server.net.PacketHookRegistry) engine.protocolHooks())
                .setFrameSender(this::sendComposedFrameTo);
    }

    /**
     * One composed frame from the §27.3 construction surface: finds the
     * named player's live PLAY-state connection and writes the frame
     * through it. Any thread (the channel write is non-blocking).
     *
     * @return whether a live connection carried the frame
     */
    private boolean sendComposedFrameTo(String playerName, int packetId, byte[] payload) {
        for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
            if (connection.inPlayState()) {
                net.zaminmc.torch.server.player.PlayerSession session =
                        connection.currentSession();
                if (session != null && session.name().equals(playerName)) {
                    return connection.sendComposedFrame(packetId, payload);
                }
            }
        }
        return false;
    }

    /** The engine this adapter bridges into (connection callbacks use it). */
    EngineServer engine() {
        return engine;
    }

    /** The live connection table (channel per connection), read-only view use. */
    Map<V18Connection, Channel> connections() {
        return connections;
    }

    @Override
    public String protocolName() {
        return "minecraft-1.8.8";
    }

    @Override
    public void start(EngineServer server) throws Exception {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("Adapter already started");
        }
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        ServerBootstrap bootstrap = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_REUSEADDR, true)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel channel) {
                        V18Connection connection = new V18Connection(V18ProtocolServer.this, engine);
                        channel.pipeline()
                                .addLast(FrameCodec.DECODER_NAME, new FrameCodec.Decoder())
                                .addLast(FrameCodec.ENCODER_NAME, new FrameCodec.Encoder())
                                // §27.3's interception point: every PLAY-state
                                // outbound frame walks the hook registry between
                                // the connection's writers and the length-prefix
                                // encoder — the raw wire form, inspectable.
                                .addAfter(FrameCodec.ENCODER_NAME, PacketHookOutboundHandler.NAME,
                                        new PacketHookOutboundHandler(
                                                (net.zaminmc.torch.server.net.PacketHookRegistry)
                                                        engine.protocolHooks(), connection))
                                // §27.3's inbound interception point (the C2S
                                // mirror): every PLAY-state frame a session sends
                                // walks the inbound hook registry between the
                                // length-prefix decoder and the connection's own
                                // dispatch — observe-or-drop, never re-payload.
                                // (addLast here, before the connection's own
                                // addLast below: the inbound traversal order is
                                // Decoder -> this handler -> connection.)
                                .addLast(PacketHookInboundHandler.NAME,
                                        new PacketHookInboundHandler(
                                                (net.zaminmc.torch.server.net.PacketHookRegistry)
                                                        engine.protocolHooks(), connection))
                                .addLast("connection", connection);
                        connections.put(connection, channel);
                        allChannels.add(channel);
                    }
                });
        serverChannel = bootstrap.bind(server.engineConfig().host(), server.engineConfig().port()).sync().channel();
        // Subscribe to committed world changes for client synchronization (§227):
        // the engine decides what changed, the adapter decides how to tell clients.
        server.addWorldListener((world, position, type) -> {
            for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                connection.sendBlockChange(position, type);
            }
        });
        // Relight transport (§475): protocol 47 has no light-only packet, so
        // every chunk column the LightEngine touched re-sends once per tick.
        server.addRelightListener(position -> {
            for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                connection.sendChunkRelight(position);
            }
        });
        // Chat delivery: engine decides audience/validity, adapter renders.
        server.addChatListener(new net.zaminmc.torch.server.chat.ChatListener() {
            @Override
            public void onChatMessage(net.zaminmc.torch.server.player.PlayerSession sender, String content) {
                broadcastChat("<" + sender.name() + "> " + content);
            }

            @Override
            public void onSystemMessage(net.zaminmc.torch.server.player.PlayerSession recipient, String content) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    if (connection.currentSession() == recipient) {
                        connection.sendChatLine(content, 0);
                    }
                }
            }
        });
        // Item entities: engine decides lifecycle, adapter renders protocol 47 wire.
        server.addItemListener(new net.zaminmc.torch.server.entity.ItemEntityManager.Listener() {
            @Override
            public void onItemSpawned(net.zaminmc.torch.server.entity.ItemEntity entity) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendItemSpawn(entity);
                }
            }

            @Override
            public void onItemMoved(net.zaminmc.torch.server.entity.ItemEntity entity) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendItemTeleport(entity);
                }
            }

            @Override
            public void onItemCollected(net.zaminmc.torch.server.entity.ItemEntity entity,
                                        net.zaminmc.torch.server.player.PlayerSession collector, int count) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendItemCollected(entity.entityId(), collector);
                }
            }

            @Override
            public void onItemCollectedByMob(net.zaminmc.torch.server.entity.ItemEntity entity,
                    net.zaminmc.torch.server.entity.MobEntity collector) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendItemCollectedByMob(entity.entityId(), collector.entityId());
                }
            }

            @Override
            public void onItemStackChanged(net.zaminmc.torch.server.entity.ItemEntity entity) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendItemStackUpdate(entity);
                }
            }

            @Override
            public void onItemRemoved(net.zaminmc.torch.server.entity.ItemEntity entity, String reason) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendItemRemoved(entity.entityId());
                }
            }
        });
        // Falling blocks: engine decides the §470 transition, adapter renders
        // Spawn Entity object 70, Entity Teleport and Destroy. The landing's
        // block change itself rides the world listener.
        server.addFallingListener(new net.zaminmc.torch.server.entity.FallingBlockEntityManager.Listener() {
            @Override
            public void onFallingSpawned(net.zaminmc.torch.server.entity.FallingBlockEntity entity) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendFallingSpawn(entity);
                }
            }

            @Override
            public void onFallingMoved(net.zaminmc.torch.server.entity.FallingBlockEntity entity) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendFallingTeleport(entity);
                }
            }

            @Override
            public void onFallingEnded(net.zaminmc.torch.server.entity.FallingBlockEntity entity,
                                       boolean becameBlock) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendFallingRemoved(entity.entityId());
                }
            }
        });
        // The primed TNT (Slice 9f): Spawn Entity object 50, the Entity
        // Teleport ride, Destroy at the fuse end — the explosion packet
        // itself rides the shared explosion listener.
        server.addTntListener(new net.zaminmc.torch.server.tnt.TntSystem.Listener() {
            @Override
            public void onTntPrimed(net.zaminmc.torch.server.tnt.TntSystem.PrimedTnt tnt) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendTntSpawn(tnt);
                }
            }

            @Override
            public void onTntMoved(net.zaminmc.torch.server.tnt.TntSystem.PrimedTnt tnt) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendTntMoved(tnt);
                }
            }

            @Override
            public void onTntRemoved(net.zaminmc.torch.server.tnt.TntSystem.PrimedTnt tnt) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendTntRemoved(tnt.entityId());
                }
            }
        });
        // Time Update (0x03): the periodic cycle sync and the /time command
        // ride the same listener — the client's day follows the server's.
        // The dimension filter is the reference's sendPacket(packet,
        // dimension) walk (PlayerManager lines 517-522): each world's packet
        // lands only on the players standing in that world.
        server.addTimeListener((dimension, totalTicks, timeOfDay) -> {
            for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                if (connection.dimensionOfSession() == dimension) {
                    connection.sendTimeUpdateNow();
                }
            }
        });
        // Sign text (0x33): edits fan out to every connected viewer; the
        // chunk-send path replays signs per column separately.
        server.addSignListener((position, lines) -> {
            for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                connection.sendSignUpdate(position, lines);
            }
        });
        // The spawner's broadcasts (Slice 18e): the fired cycle's flames
        // (doEvent 2004 — the Effect packet) and the delay reroll's spin
        // reset (the block event 1 — the Block Action packet), both
        // chunk-gated per connection inside the senders.
        if (server.spawnerManager() != null) {
            server.spawnerManager().addListener(
                    new net.zaminmc.torch.server.spawner.SpawnerManager.Listener() {
                @Override
                public void onSpawnerFired(int dimension,
                        net.zaminmc.torch.block.BlockPosition position) {
                    for (V18Connection connection
                            : connections.keySet().toArray(new V18Connection[0])) {
                        connection.sendSpawnerFlames(position);
                    }
                }

                @Override
                public void onSpawnerDelayRerolled(int dimension,
                        net.zaminmc.torch.block.BlockPosition position) {
                    for (V18Connection connection
                            : connections.keySet().toArray(new V18Connection[0])) {
                        connection.sendSpawnerSpinReset(position);
                    }
                }
            });
        }
        // Living mobs: engine decides lifecycle, adapter renders protocol 47.
        server.addMobListener(new net.zaminmc.torch.server.entity.MobManager.Listener() {
            @Override
            public void onMobSpawned(net.zaminmc.torch.server.entity.MobEntity mob) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendMobSpawn(mob);
                }
            }

            @Override
            public void onMobMoved(net.zaminmc.torch.server.entity.MobEntity mob,
                                   net.zaminmc.torch.server.player.PlayerSession rider) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendMobMoved(mob);
                    // The ridden seat re-anchors: the rider's own client follows
                    // the body (the vehicle rule), the others teleport the body.
                    if (rider != null) {
                        connection.sendRiderSeatAnchor(rider, mob);
                    }
                }
            }

            @Override
            public void onMobHurt(net.zaminmc.torch.server.entity.MobEntity mob) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendMobStatus(mob, net.zaminmc.torch.protocol.v1_8.Protocol18.ENTITY_STATUS_HURT);
                    connection.sendMobSound(mob.hurtSound(), mob.position(), 1.0f, 1.0f);
                }
            }

            @Override
            public void onMobDied(net.zaminmc.torch.server.entity.MobEntity mob) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendMobStatus(mob, net.zaminmc.torch.protocol.v1_8.Protocol18.ENTITY_STATUS_DEAD);
                    connection.sendMobSound(mob.deathSound(), mob.position(), 1.0f, 1.0f);
                }
            }

            @Override
            public void onMobSoundAt(net.zaminmc.torch.server.entity.MobEntity mob,
                                     String soundName, float volume, float pitch) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendSound(soundName, mob.position(), volume, pitch);
                }
            }

            @Override
            public void onMobRemoved(net.zaminmc.torch.server.entity.MobEntity mob, String reason) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendMobRemoved(mob.entityId());
                }
            }

            @Override
            public void onMobBurningChanged(net.zaminmc.torch.server.entity.MobEntity mob,
                                             boolean burning) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendMobLivingFlags(mob);
                }
            }

            /**
             * The equipment changed after the spawn (Slice 78b — the
             * /summon Equipment row + the restore's held stack): the
             * Entity Equipment re-send.
             */
            @Override
            public void onMobEquipmentChanged(net.zaminmc.torch.server.entity.MobEntity mob) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendMobHeldEquipment(mob);
                }
            }

            /** The ocelot's creep/flee postures (the shared flags byte). */
            @Override
            public void onMobPostureChanged(net.zaminmc.torch.server.entity.MobEntity mob) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendMobLivingFlags(mob);
                }
            }

            @Override
            public void onMobWitchDrinkingChanged(net.zaminmc.torch.server.entity.MobEntity mob,
                                                 boolean drinking) {
                // The index-21 metadata byte: the client raises the drinking
                // pose and the held potion from the flag (Slice 12j).
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendWitchDrinking(mob);
                }
            }

            /** The ghast's charging flag (the index-16 byte). */
            @Override
            public void onMobGhastChargingChanged(net.zaminmc.torch.server.entity.MobEntity mob) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendGhastCharging(mob);
                }
            }

            /** The blaze's fire-active flag (the index-16 byte, Slice 18a). */
            @Override
            public void onMobBlazeFireChanged(net.zaminmc.torch.server.entity.MobEntity mob) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendBlazeFire(mob);
                }
            }

            /** The wither's four Ints (the slots 17-20, Slice 59). */
            @Override
            public void onWitherMetadataChanged(net.zaminmc.torch.server.entity.MobEntity mob) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendWitherMetadata(mob);
                }
            }

            /** The wither's global 1013 spawn sound (Slice 59). */
            @Override
            public void onWitherBirth(net.zaminmc.torch.server.entity.MobEntity mob) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendWorldGlobalEvent(mob.position(), 1013);
                }
            }

            /** The wither's 1012 chunk break sound (Slice 59). */
            @Override
            public void onWitherBlockBreakSound(net.zaminmc.torch.server.entity.MobEntity mob) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendWorldEvent(mob.position(), 1012);
                }
            }

            /** The wither's 1014 skull launch sound (Slice 59). */
            @Override
            public void onWitherShootSound(net.zaminmc.torch.server.entity.MobEntity mob) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendWorldEvent(mob.position(), 1014);
                }
            }

            /** A global world event (the doGlobalEvent family — every player). */
            @Override
            public void onWorldGlobalEventAt(net.zaminmc.torch.util.Position position, int eventId) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendWorldGlobalEvent(position, eventId);
                }
            }

            /** The zombie villager's pair (the slots 13/14 bytes, Slice 25a). */
            @Override
            public void onZombieStateChanged(net.zaminmc.torch.server.entity.MobEntity mob) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendZombieState(mob);
                }
            }

            /** The reference's own world events (1016 infect, 1017 un-infect). */
            @Override
            public void onWorldEventAt(net.zaminmc.torch.util.Position position, int eventId) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendWorldEvent(position, eventId);
                }
            }

            /**
             * The enderman's whole state (the carried Short + the screaming
             * byte, Slice 19d).
             */
            @Override
            public void onEndermanStateChanged(net.zaminmc.torch.server.entity.MobEntity mob) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendEndermanState(mob);
                }
            }

            /** The guardian's state moved (the flag word + the beam pair). */
            @Override
            public void onGuardianStateChanged(net.zaminmc.torch.server.entity.MobEntity mob) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendGuardianState(mob);
                }
            }

            @Override
            public void onMobWitchAura(net.zaminmc.torch.server.entity.MobEntity mob) {
                // The Entity Event byte 15 (WitchEntity.doEvent's spell
                // swirl): the client spawns the purple particles itself.
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendMobStatus(mob, Protocol18.ENTITY_EVENT_WITCH_AURA);
                }
            }

            @Override
            public void onMobAttackedPlayer(net.zaminmc.torch.server.entity.MobEntity mob,
                                            net.zaminmc.torch.server.player.PlayerSession target, float damage) {
                // The health sync rides the survival listener; the victim's
                // hurt sound is client-side historically.
            }

            @Override
            public void onMobSound(net.zaminmc.torch.server.entity.MobEntity mob, String soundName) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendMobSound(soundName, mob.position(), 1.0f, 1.0f);
                }
            }

            @Override
            public void onMobRangedAttack(net.zaminmc.torch.server.entity.MobEntity mob,
                                          net.zaminmc.torch.util.Position aimPoint) {
                // The arrow's Spawn Entity rides the projectile listener; the
                // arm swing rides the swing observer; nothing else needed.
            }

            @Override
            public void onMobFuseChanged(net.zaminmc.torch.server.entity.MobEntity mob, boolean priming) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendMobStatusByte(mob, priming
                            ? Protocol18.CREEPER_FUSE_SWELLING : Protocol18.CREEPER_FUSE_IDLE);
                }
            }

            @Override
            public void onMobSheared(net.zaminmc.torch.server.entity.MobEntity mob, int woolCount) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendMobStatusByte(mob, V18Connection.kindStatusByteOf(mob));
                }
            }

            @Override
            public void onMobCoatRegrown(net.zaminmc.torch.server.entity.MobEntity mob) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendMobStatusByte(mob, V18Connection.kindStatusByteOf(mob));
                }
            }

            /**
             * The fleece dye's write (Slice 30b): the observers re-send the
             * index-16 byte — the color nibble with the sheared bit live.
             */
            @Override
            public void onSheepFleeceChanged(net.zaminmc.torch.server.entity.MobEntity mob) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendMobStatusByte(mob, V18Connection.kindStatusByteOf(mob));
                }
            }

            @Override
            public void onMobExploded(net.zaminmc.torch.server.entity.MobEntity mob) {
                // The blast rides the explosion listener (blocks + motion).
            }

            @Override
            public void onMobMounted(net.zaminmc.torch.server.entity.MobEntity mob,
                                     int riderEngineId) {
                net.zaminmc.torch.server.player.PlayerSession rider = server.playerByEngineId(riderEngineId);
                if (rider == null) {
                    return;
                }
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendAttachEntity(rider, mob.entityId(), true);
                    connection.sendPostureMetadata(rider); // the riding flag
                }
            }

            @Override
            public void onMobDismounted(net.zaminmc.torch.server.entity.MobEntity mob,
                                        int riderEngineId,
                                        net.zaminmc.torch.util.Position exit, boolean thrown) {
                net.zaminmc.torch.server.player.PlayerSession rider = server.playerByEngineId(riderEngineId);
                if (rider == null) {
                    return;
                }
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendAttachEntity(rider, mob.entityId(), false);
                    connection.sendPostureMetadata(rider);
                }
                // The ex-rider's own client re-anchors at the exit point.
                rider.link().resyncPosition();
            }

            @Override
            public void onHorseFlagsChanged(net.zaminmc.torch.server.entity.MobEntity mob) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    if (mob.type() == net.zaminmc.torch.server.entity.MobType.PIG) {
                        connection.sendMobPigSaddle(mob);
                    } else {
                        connection.sendMobHorseFlags(mob);
                    }
                }
            }

            @Override
            public void onHorseInventoryOpened(net.zaminmc.torch.server.player.PlayerSession player,
                                               net.zaminmc.torch.server.entity.MobEntity mob,
                                               int windowId) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    if (connection.currentSession() == player) {
                        connection.sendHorseWindow(channelOf(connection), player, windowId, mob);
                    }
                }
            }

            /** The wolf's tamed/sitting/angry/collar state moved (the metadata update). */
            @Override
            public void onWolfStateChanged(net.zaminmc.torch.server.entity.MobEntity mob) {
            for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                connection.sendWolfState(mob);
            }
        }

        /** The world's doEntityEvent byte (the wolf's taming verdicts here). */
        @Override
        public void onMobStatusEvent(net.zaminmc.torch.server.entity.MobEntity mob, int status) {
            for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                connection.sendMobStatus(mob, status);
            }
        }

        @Override
        public void onMobLoveBurst(net.zaminmc.torch.server.entity.MobEntity mob) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendMobStatus(mob, Protocol18.ENTITY_STATUS_LOVE);
                }
            }

            /** The ambient heart clock (AnimalEntity.mobTick's own addParticle). */
            @Override
            public void onMobAmbientHearts(net.zaminmc.torch.server.entity.MobEntity mob,
                                           net.zaminmc.torch.util.Position at,
                                           double vx, double vy, double vz) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendDisplayParticle(Protocol18.PARTICLE_HEART, at,
                            (float) vx, (float) vy, (float) vz, 1.0f);
                }
            }

            /**
             * The wolf's shake splash (Slice 30d — WolfEntity.tick lines
             * 212-221): one World Particles unit in the count-0 shape.
             */
            @Override
            public void onWolfShakeSplash(net.zaminmc.torch.server.entity.MobEntity mob,
                                          net.zaminmc.torch.util.Position at,
                                          double vx, double vy, double vz) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendDisplayParticle(Protocol18.PARTICLE_WATER_SPLASH, at,
                            (float) vx, (float) vy, (float) vz, 1.0f);
                }
            }

            @Override
            public void onMobGrewUp(net.zaminmc.torch.server.entity.MobEntity mob) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendMobAgeMetadata(mob);
                }
            }

            @Override
            public void onVillagerTradeOpened(net.zaminmc.torch.server.player.PlayerSession player,
                                              net.zaminmc.torch.server.entity.MobEntity villager,
                                              int windowId) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    if (connection.currentSession() == player) {
                        connection.sendVillagerWindow(channelOf(connection), player, windowId, villager);
                    }
                }
            }

            @Override
            public void onTradeSelected(net.zaminmc.torch.server.player.PlayerSession player,
                                        int windowId,
                                        net.zaminmc.torch.item.ItemStack result) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    if (connection.currentSession() == player) {
                        connection.sendTradeResult(player, windowId, result);
                    }
                }
            }
        });
        // The skeleton's bow arm (Animation 0x0B) and the creeper's blast
        // (Explosion 0x27) ride dedicated observers.
        server.addMobSwingObserver(mob -> {
            for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                connection.sendMobSwing(mob);
            }
        });
        // The attack crit bursts (Animation 0x0B codes 4/5, the reference's
        // addCritParticles / addEnchantedCritParticles) fan out the same way:
        // the mob target rides its global id, the player target resolves
        // through each observer's id space (the self arm included).
        server.addEntityAnimationObserver(new EngineServer.EntityAnimationObserver() {
            @Override
            public void onMobAnimation(net.zaminmc.torch.server.entity.MobEntity mob, int animation) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendMobAnimation(mob, animation);
                }
            }

            @Override
            public void onPlayerAnimation(net.zaminmc.torch.server.player.PlayerSession player, int animation) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    if (connection.currentSession() == player) {
                        connection.sendSelfAnimation(animation);
                    } else {
                        connection.sendRemotePlayerAnimation(player.uuid(), animation);
                    }
                }
            }
        });
        server.addExplosionListener(event -> {
            for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                // Slice 62: the blast rides only its own dimension's
                // observers (the same coordinates exist in every world).
                if (connection.dimensionOfSession() == event.dimension()) {
                    connection.sendExplosion(event);
                }
            }
        });
        // Inventory sync: full authoritative window re-sync on change (simple,
        // correct; per-slot deltas are an optimization for a later profile).
        // While a container (crafting table) is open, its window syncs instead:
        // the client's visible inventory lives in the container's slot layout.
        server.addInventoryListener(player -> {
            int openWindow = player.openContainerWindowId();
            // While the villager window is open the merchant's pseudo-slots
            // are display-only: the live inventory still rides window 0 (the
            // client's merchant GUI renders the buys from it).
            boolean tradeWindow = player.openContainerKind()
                    == net.zaminmc.torch.server.player.PlayerSession.ContainerKind.VILLAGER;
            for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                if (connection.currentSession() == player) {
                    if (openWindow > 0 && !tradeWindow) {
                        connection.sendContainerWindowItems(channelOf(connection), openWindow, player);
                    } else {
                        connection.sendWindowItems(channelOf(connection),
                                player.inventory().snapshot(),
                                player.crafting().snapshot(),
                                player.inventory().armorSnapshot(),
                                server.craftingResult(player));
                    }
                } else {
                    // The armor row rides the Entity Equipment packets to the
                    // observers (the 1.8 way: remote armor renders from
                    // equipment, never from metadata).
                    connection.sendPlayerEquipment(player);
                }
            }
        });
        // Furnace windows: the engine fans a per-tick view out to the open
        // viewer; the connection diffs the block entity's serials and sends
        // only moved properties/slots (the historical detectAndSendChanges).
        server.addFurnaceViewListener((viewer, position, furnace) -> {
            for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                if (connection.currentSession() == viewer) {
                    connection.sendFurnaceViewTick(channelOf(connection),
                            viewer.openContainerWindowId(), furnace);
                }
            }
        });
        // The §27 window family's re-sync wave (the chest/hopper/dispenser
        // families generalized over the furnace's own diff path): a plugin's
        // setSlot, the hopper system's transfers, and the world side's own
        // writes all bump the container's serial; the per-tick view fan
        // re-syncs the open viewer's Window Items only on a real change.
        server.addChestViewListener((viewer, chest) -> {
            for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                if (connection.currentSession() == viewer) {
                    connection.sendChestViewTick(channelOf(connection),
                            viewer.openContainerWindowId(), chest);
                }
            }
        });
        server.addHopperViewListener((viewer, hopper) -> {
            for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                if (connection.currentSession() == viewer) {
                    connection.sendHopperViewTick(channelOf(connection),
                            viewer.openContainerWindowId(), hopper);
                }
            }
        });
        server.addDispenserViewListener((viewer, dispenser) -> {
            for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                if (connection.currentSession() == viewer) {
                    connection.sendDispenserViewTick(channelOf(connection),
                            viewer.openContainerWindowId(), dispenser);
                }
            }
        });
        // Brewing windows (Slice 11e): the same per-tick view fan-out — the
        // brew timer's property move + the slots resync on change.
        // The beacon's state changes (Slice 29b — the reference's
        // PlayerChunkMap re-sending the BE's createUpdatePacket): every
        // observer holding the beacon's chunk re-receives the 0x35 tile
        // (the levels/effects the client's own beam walk reads).
        if (server.beacons() != null) {
            server.beacons().addListener((position, beacon) -> {
                if (position == null || beacon == null) {
                    return; // the removal arm: no tile to refresh
                }
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendBeaconTile(position, beacon);
                }
            });
        }
        // The skull tiles (Slice 60 — the same PlayerChunkMap law): every
        // placement re-publishes the 0x35 tile (the TYPE + rotation the
        // client's renderer reads); the removal arm needs no packet (the
        // block's own update carries the vanish).
        if (server.skulls() != null) {
            server.skulls().addListener(new net.zaminmc.torch.server.skull.SkullManager.Listener() {
                @Override
                public void onSkullTileChanged(net.zaminmc.torch.block.BlockPosition position,
                        net.zaminmc.torch.server.skull.SkullManager.SkullTile tile) {
                    for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                        connection.sendSkullTile(tile);
                    }
                }
            });
        }
        // The command-block tile re-publish (Slice 72 — the edit/run rows'
        // 0x35 action 2 fan); the chunk-send replay rides the chunk packet's
        // own tile walk (writeCommandTile's static form).
        if (server.commandBlocks() != null) {
            server.commandBlocks().addListener(
                    new net.zaminmc.torch.server.commandblock.CommandBlockManager.Listener() {
                        @Override
                        public void onCommandTileChanged(
                                net.zaminmc.torch.block.BlockPosition position,
                                net.zaminmc.torch.server.commandblock.CommandBlockManager.CommandState tile) {
                            for (V18Connection connection
                                    : connections.keySet().toArray(new V18Connection[0])) {
                                connection.sendCommandTile(position, tile);
                            }
                        }
                    });
        }
        // The beacon GUI's per-tick view (Slice 29b): the three properties'
        // moves + the payment resync, diffed per connection.
        server.addBeaconViewListener((viewer, beacon) -> {
            for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                if (connection.currentSession() == viewer) {
                    connection.sendBeaconViewTick(channelOf(connection),
                            viewer.openContainerWindowId(), beacon);
                }
            }
        });
        server.addBrewingViewListener((viewer, stand) -> {
            for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                if (connection.currentSession() == viewer) {
                    connection.sendBrewingViewTick(channelOf(connection),
                            viewer.openContainerWindowId(), stand);
                }
            }
        });
        // Enchanting windows: the same per-tick view fan-out, the menu state
        // living on the session (the historical per-open menu object).
        server.addEnchantingViewListener((viewer, menu) -> {
            for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                if (connection.currentSession() == viewer) {
                    connection.sendEnchantingViewTick(channelOf(connection),
                            viewer.openContainerWindowId(), menu);
                }
            }
        });
        // The leashes (Slice 11c): the knot's Spawn Object 77 / Destroy, the
        // mob's Attach Entity pair (the holder in each observer's own id
        // space), all broadcast to every connection.
        server.addLeadListener(new net.zaminmc.torch.server.leash.LeadSystem.Listener() {
            @Override
            public void onKnotSpawned(net.zaminmc.torch.server.leash.LeashKnot knot) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendKnotSpawned(knot);
                }
            }

            @Override
            public void onKnotRemoved(net.zaminmc.torch.server.leash.LeashKnot knot) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendKnotRemoved(knot.entityId());
                }
            }

            @Override
            public void onMobAttached(net.zaminmc.torch.server.entity.MobEntity mob) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendLeashAttach(mob, mob.leashHolderPlayer(), mob.leashHolderKnot());
                }
            }

            @Override
            public void onMobDetached(net.zaminmc.torch.server.entity.MobEntity mob) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendLeashAttach(mob, null, null);
                }
            }
        });
        // The decorations (Slice 28a/28b): the frame's Spawn Object 71 +
        // the metadata pair + the Destroy, the painting's Spawn Painting
        // + the Destroy, all broadcast to every connection.
        server.addDecorationListener(new net.zaminmc.torch.server.decoration.DecorationSystem.Listener() {
            @Override
            public void onFrameSpawned(net.zaminmc.torch.server.decoration.ItemFrame frame) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendFrameSpawned(frame);
                }
            }

            @Override
            public void onFrameRemoved(net.zaminmc.torch.server.decoration.ItemFrame frame) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendFrameRemoved(frame.entityId());
                }
            }

            @Override
            public void onFrameItemChanged(net.zaminmc.torch.server.decoration.ItemFrame frame) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendFrameItemChanged(frame);
                }
            }

            @Override
            public void onFrameRotationChanged(
                    net.zaminmc.torch.server.decoration.ItemFrame frame) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendFrameRotationChanged(frame);
                }
            }

            @Override
            public void onPaintingSpawned(
                    net.zaminmc.torch.server.decoration.Painting painting) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendPaintingSpawned(painting);
                }
            }

            @Override
            public void onPaintingRemoved(
                    net.zaminmc.torch.server.decoration.Painting painting) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendPaintingRemoved(painting.entityId());
                }
            }
        });
        // The armor stands (Slice 28c): the Spawn Object 78 + the pose
        // metadata + the Entity Equipment family + the Destroy, all
        // broadcast to every connection.
        server.addArmorStandListener(new net.zaminmc.torch.server.armorstand.ArmorStandSystem.Listener() {
            @Override
            public void onStandSpawned(net.zaminmc.torch.server.armorstand.ArmorStand stand) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendStandSpawned(stand);
                }
            }

            @Override
            public void onStandRemoved(net.zaminmc.torch.server.armorstand.ArmorStand stand) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendStandRemoved(stand.entityId());
                }
            }

            @Override
            public void onStandEquipmentChanged(
                    net.zaminmc.torch.server.armorstand.ArmorStand stand, int slot) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendStandEquipment(stand, slot);
                }
            }
        });
        // The ender crystals (Slice 62): the Spawn Object 200 + the slot-8
        // Int + the Destroy — the connection's own dimension gate (1) and
        // chunk window ride the send.
        server.addEnderCrystalListener(new net.zaminmc.torch.server.endcrystal.EnderCrystalSystem.Listener() {
            @Override
            public void onCrystalSpawned(net.zaminmc.torch.server.endcrystal.EnderCrystal crystal) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendCrystalSpawn(crystal);
                }
            }

            @Override
            public void onCrystalRemoved(net.zaminmc.torch.server.endcrystal.EnderCrystal crystal) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendCrystalRemoved(crystal.entityId());
                }
            }

            @Override
            public void onCrystalHealthChanged(
                    net.zaminmc.torch.server.endcrystal.EnderCrystal crystal) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendCrystalHealth(crystal);
                }
            }
        });
        // The potion effects (Slice 11b — the reference's ServerPlayerEntity
        // overrides lines 772-787): the Entity Effect / Remove Entity Effect
        // packets go to the affected player's own client only; the 600-tick
        // re-sync rides the same wire.
        server.addPotionListener(new net.zaminmc.torch.server.potion.PotionSystem.Listener() {
            @Override
            public void onEffectAdded(net.zaminmc.torch.server.player.PlayerSession player,
                                      net.zaminmc.torch.server.potion.StatusEffectInstance effect) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    if (connection.currentSession() == player) {
                        connection.sendEntityEffect(player.engineEntityId(),
                                effect.id(), effect.amplifier(),
                                effect.duration(), effect.hasParticles());
                    }
                }
            }

            @Override
            public void onEffectRemoved(net.zaminmc.torch.server.player.PlayerSession player,
                                        net.zaminmc.torch.server.potion.StatusEffectInstance effect) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    if (connection.currentSession() == player) {
                        connection.sendRemoveEntityEffect(player.engineEntityId(), effect.id());
                    }
                }
            }

            @Override
            public void onEffectResynced(net.zaminmc.torch.server.player.PlayerSession player,
                                          net.zaminmc.torch.server.potion.StatusEffectInstance effect) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    if (connection.currentSession() == player) {
                        connection.sendEntityEffect(player.engineEntityId(),
                                effect.id(), effect.amplifier(),
                                effect.duration(), effect.hasParticles());
                    }
                }
            }
        });
        // Survival body: health changes re-sync the client, deaths send the
        // combat event, respawns drive the full re-anchor sequence.
        server.addSurvivalListener(new net.zaminmc.torch.server.EngineServer.SurvivalListener() {
            @Override
            public void onBodyChanged(net.zaminmc.torch.server.player.PlayerSession player) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    if (connection.currentSession() == player) {
                        connection.sendUpdateHealth(player);
                    }
                }
            }

            @Override
            public void onDied(net.zaminmc.torch.server.player.PlayerSession player) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    if (connection.currentSession() == player) {
                        connection.sendDeath(player);
                    }
                }
            }

            @Override
            public void onRespawned(net.zaminmc.torch.server.player.PlayerSession player,
                                    net.zaminmc.torch.util.Position spawn) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    if (connection.currentSession() == player) {
                        connection.sendRespawnSequence(player, spawn);
                    }
                }
            }

            @Override
            public void onDimensionChanged(net.zaminmc.torch.server.player.PlayerSession player,
                                           net.zaminmc.torch.util.Position destination,
                                           int dimension) {
                // The changeDimension walk's wire arm: the respawn sequence
                // re-pointed at the destination dimension — the Respawn
                // packet's dimension field, the chunk view re-anchored to the
                // arrival, and every subsequent chunk read from that world.
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    if (connection.currentSession() == player) {
                        connection.sendRespawnSequence(player, destination, dimension);
                        // Slice 62: the End arrival's own world snapshot —
                        // the standing pillar crystals the arrival walks
                        // into (the join snapshot's own shape, re-sent at
                        // the dimension seam; the send's own gates carry
                        // the dimension + the chunk window).
                        if (dimension == 1) {
                            var crystals = engine.enderCrystals();
                            if (crystals != null) {
                                for (net.zaminmc.torch.server.endcrystal.EnderCrystal crystal
                                        : crystals.all()) {
                                    connection.sendCrystalSpawn(crystal);
                                }
                            }
                        }
                    }
                }
            }

            @Override
            public void onPlayerHurt(net.zaminmc.torch.server.player.PlayerSession player) {
                // The hurt flash: the victim's own client animates its body;
                // every observer animates the remote player they track.
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    if (connection.currentSession() == player) {
                        connection.sendSelfStatus(Protocol18.ENTITY_STATUS_HURT);
                    } else {
                        connection.sendRemotePlayerStatus(player.uuid(),
                                Protocol18.ENTITY_STATUS_HURT);
                    }
                }
            }

            @Override
            public void onEndCredits(net.zaminmc.torch.server.player.PlayerSession player) {
                // Slice 64 — the exit fountain's removeEntity wire: the
                // credits body leaves the End world (the reference's
                // ServerPlayerEntity.changeDimension line 447), so every
                // OTHER observer's tracked copy of the mover must go (the
                // Destroy Entity the reference's entity map sends). The
                // mover's own connection stays — it is watching the win
                // screen (the engine already sent the GameEvent 4).
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    if (connection.currentSession() != player) {
                        connection.untrackPlayer(player.uuid());
                    }
                }
            }

            @Override
            public void onKnockback(net.zaminmc.torch.server.player.PlayerSession player,
                                    double vx, double vy, double vz) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    if (connection.currentSession() == player) {
                        connection.sendSetVelocity(vx, vy, vz);
                    }
                }
            }

            @Override
            public void onPostureChanged(net.zaminmc.torch.server.player.PlayerSession player) {
                // The posture flags ride Entity Metadata to every observer
                // except the mover (their client animates itself).
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendPostureMetadata(player);
                }
            }
        });
        // Experience orbs: engine decides lifecycle, adapter renders Spawn
        // Experience Orb 0x11 / Collect / Destroy — the item-entity shape.
        server.addOrbListener(new net.zaminmc.torch.server.experience.ExperienceOrbManager.Listener() {
            @Override
            public void onOrbSpawned(net.zaminmc.torch.server.experience.ExperienceOrbEntity orb) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendOrbSpawn(orb);
                }
            }

            @Override
            public void onOrbMoved(net.zaminmc.torch.server.experience.ExperienceOrbEntity orb) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendOrbTeleport(orb);
                }
            }

            @Override
            public void onOrbCollected(net.zaminmc.torch.server.experience.ExperienceOrbEntity orb,
                                       net.zaminmc.torch.server.player.PlayerSession collector) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendOrbCollected(orb.entityId(), collector);
                }
            }

            @Override
            public void onOrbRemoved(net.zaminmc.torch.server.experience.ExperienceOrbEntity orb,
                                     String reason) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendOrbRemoved(orb.entityId());
                }
            }
        });
        // The XP bar: every experience change re-syncs the owning client's
        // Set Experience (progress, level, total).
        server.addExperienceListener(player -> {
            for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                if (connection.currentSession() == player) {
                    connection.sendSetExperience(player);
                }
            }
        });
        // The chest lid: Block Action 0x24 to every observer except the
        // opener (the historical exclude-one broadcast).
        server.addChestLidListener((position, opener, open) -> {
            for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                connection.sendChestLid(position, opener, open);
            }
        });
        // The note action: Block Action 0x24 (block 25, the instrument, the
        // note) to every chunk-holding observer (Slice 27a — the reference's
        // doBlockEvents relay).
        server.noteBlocks().addNoteActionListener((position, instrument, note) -> {
            for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                connection.sendNoteAction(position, instrument, note);
            }
        });
        // The jukebox's record events (Slice 27b — the reference's world
        // event 1005): the same Effect packet carries both directions — the
        // disc's item id plays it (the client's own "records." walk,
        // WorldRenderer lines 1923-1929), 0 stops it.
        server.jukeboxes().addRecordListener((position, disc) -> {
            for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                connection.sendRecordEvent(position, disc);
            }
        });
        // The dig progress: Block Break Animation 0x28 to every observer
        // within the vanilla 32-block radius (ServerWorldEventListener's
        // squared 1024.0 check), except the digger (their client predicts its
        // own cracking overlay).
        server.blockInteraction().setMiningProgressListener((digger, position, stage) -> {
            double bx = position.x() + 0.5;
            double by = position.y() + 0.5;
            double bz = position.z() + 0.5;
            for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                PlayerSession observer = connection.currentSession();
                if (observer == null) {
                    continue;
                }
                Position at = observer.position();
                double dx = bx - at.x();
                double dy = by - at.y();
                double dz = bz - at.z();
                if (dx * dx + dy * dy + dz * dz >= 1024.0) {
                    continue;
                }
                connection.sendBlockMiningProgress(digger, position, stage);
            }
        });
        // The held-slot switch: the owner gets the 0x09 confirm, every
        // observer re-renders the new held item through Entity Equipment.
        server.addHeldSlotListener(player -> {
            for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                connection.sendHeldItemChangeConfirm(player);
                connection.sendPlayerEquipment(player);
            }
        });
        // The FX bus: every engine sound/particle event fans out to the
        // observers inside its feedback radius, translated per event kind.
        server.fx().addListener(event -> {
            for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                connection.deliverFx(event, connection.currentSession());
            }
        });
        // Projectile sync: launch (Spawn Object + velocity), per-tick movement
        // (Entity Teleport) and removal (Destroy Entities) fan out the same way.
        server.addProjectileListener(new EngineServer.ProjectileListener() {
            @Override
            public void onProjectileSpawned(net.zaminmc.torch.server.entity.projectile.ProjectileEntity projectile) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendProjectileSpawned(projectile);
                    // The Flame arrow's flame trail rides the shared index-0
                    // flags byte (the reference's setOnFireFor(100) visual).
                    if (projectile.burning()) {
                        connection.sendProjectileBurningFlag(projectile);
                    }
                }
            }

            @Override
            public void onProjectileMoved(net.zaminmc.torch.server.entity.projectile.ProjectileEntity projectile) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendProjectileMoved(projectile);
                }
            }

            @Override
            public void onProjectileLanded(net.zaminmc.torch.server.entity.projectile.ProjectileEntity projectile) {
                // A landed arrow just stops receiving teleport syncs; the
                // clients hold the last position until the destroy arrives.
            }

            @Override
            public void onProjectileRemoved(net.zaminmc.torch.server.entity.projectile.ProjectileEntity projectile,
                                            String reason) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendProjectileRemoved(projectile);
                }
            }

            /** The deflected fireball (Slice 25b — the velocity re-publish). */
            @Override
            public void onProjectileDeflected(net.zaminmc.torch.server.entity.projectile.ProjectileEntity projectile) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendProjectileVelocity(projectile);
                }
            }

            /** The bottle's splash event (Slice 65 — the 2002 Effect). */
            @Override
            public void onWorldEventAt(net.zaminmc.torch.util.Position position, int eventId) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendWorldEvent(position, eventId);
                }
            }
        });
        // Firework rockets (Slice 65 — FireworksEntity's own tracker shape:
        // the Spawn Object 76 + the slot-8 item, the 10-tick velocity
        // re-syncs, the burst's Entity Status 17, the Destroy).
        if (engine.fireworks() != null) {
            engine.fireworks().addListener(new net.zaminmc.torch.server.fireworks.FireworksSystem.Listener() {
                @Override
                public void onFireworksLaunched(net.zaminmc.torch.server.fireworks.FireworksEntity rocket) {
                    for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                        connection.sendFireworksLaunched(rocket);
                    }
                }

                @Override
                public void onFireworksVelocity(net.zaminmc.torch.server.fireworks.FireworksEntity rocket) {
                    for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                        connection.sendFireworksVelocity(rocket);
                    }
                }

                @Override
                public void onFireworksBurst(net.zaminmc.torch.server.fireworks.FireworksEntity rocket) {
                    for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                        connection.sendFireworksBurst(rocket);
                    }
                }

                @Override
                public void onFireworksRemoved(net.zaminmc.torch.server.fireworks.FireworksEntity rocket) {
                    for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                        connection.sendFireworksRemoved(rocket.entityId());
                    }
                }
            });
        }
        // Vehicle sync: spawn (Spawn Object), per-tick movement (Entity
        // Teleport + the rider's seat re-anchor), mount/dismount (Attach
        // Entity + the riding flags), hurt (Entity Status) and break.
        server.addVehicleListener(new EngineServer.VehicleListener() {
            @Override
            public void onVehicleSpawned(net.zaminmc.torch.server.entity.vehicle.VehicleEntity vehicle) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendVehicleSpawn(vehicle);
                    // Slice 66: the furnace cart's lit byte rides the spawn
                    // (registerSyncedData slot 16 — the client swaps the
                    // furnace texture from it).
                    connection.sendVehicleStateMetadata(vehicle);
                }
            }

            @Override
            public void onVehicleMoved(net.zaminmc.torch.server.entity.vehicle.VehicleEntity vehicle,
                                       PlayerSession rider) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendVehicleMoved(vehicle, rider);
                }
            }

            @Override
            public void onVehicleHurt(net.zaminmc.torch.server.entity.vehicle.VehicleEntity vehicle) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendVehicleStatus(vehicle, Protocol18.ENTITY_STATUS_HURT);
                }
            }

            @Override
            public void onVehicleMounted(net.zaminmc.torch.server.entity.vehicle.VehicleEntity vehicle,
                                         PlayerSession rider) {
                if (rider == null) {
                    return;
                }
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendAttachEntity(rider, vehicle.entityId(), true);
                    connection.sendPostureMetadata(rider); // the riding flag
                }
            }

            @Override
            public void onVehicleDismounted(net.zaminmc.torch.server.entity.vehicle.VehicleEntity vehicle,
                                            PlayerSession rider) {
                if (rider == null) {
                    return;
                }
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendAttachEntity(rider, vehicle.entityId(), false);
                    connection.sendPostureMetadata(rider);
                }
                // The ex-rider's own client re-anchors at the exit point.
                rider.link().resyncPosition();
            }

            @Override
            public void onVehicleBroken(net.zaminmc.torch.server.entity.vehicle.VehicleEntity vehicle) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendVehicleRemoved(vehicle);
                }
            }

            /** The TNT cart's fuse armed (Slice 66 — the Entity Status 10). */
            @Override
            public void onVehiclePrimed(
                    net.zaminmc.torch.server.entity.vehicle.TntMinecartEntity cart) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendVehiclePrimed(cart);
                }
            }

            /** The tracked-state flip (Slice 66 — the lit byte re-send). */
            @Override
            public void onVehicleStateChanged(
                    net.zaminmc.torch.server.entity.vehicle.VehicleEntity vehicle) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendVehicleStateMetadata(vehicle);
                }
            }

            /** The chest minecart's chest menu opened (Slice 21a): the opener's unicast. */
            @Override
            public void onChestMinecartWindowOpened(net.zaminmc.torch.server.player.PlayerSession opener,
                                                    net.zaminmc.torch.server.entity.vehicle.VehicleEntity cart,
                                                    int windowId) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    if (connection.currentSession() == opener) {
                        connection.sendChestMinecartWindow(channelOf(connection), opener, windowId);
                    }
                }
            }

            /** The hopper minecart's hopper menu opened (Slice 66): the opener's unicast. */
            @Override
            public void onHopperMinecartWindowOpened(
                    net.zaminmc.torch.server.player.PlayerSession opener,
                    net.zaminmc.torch.server.entity.vehicle.HopperMinecartEntity cart,
                    int windowId) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    if (connection.currentSession() == opener) {
                        connection.sendHopperMinecartWindow(channelOf(connection), opener, windowId);
                    }
                }
            }

            /** The spawner cart's per-spawn flame burst (Slice 66): the Effect 2004 at the cell. */
            @Override
            public void onSpawnerCartFlames(
                    net.zaminmc.torch.server.entity.vehicle.SpawnerMinecartEntity cart,
                    double x, double y, double z) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendSpawnerFlames(
                            new net.zaminmc.torch.block.BlockPosition(
                                    (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)));
                }
            }

            /** The spawner cart's delay reroll (Slice 66): the Entity Status 1. */
            @Override
            public void onSpawnerCartSpinReset(
                    net.zaminmc.torch.server.entity.vehicle.SpawnerMinecartEntity cart) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendSpawnerCartSpinReset(cart);
                }
            }
        });
        // The fishing bobbers (Slice 67): the spawn/move/remove wire.
        server.addFishingListener(new net.zaminmc.torch.server.fishing.FishingSystem.Listener() {
            @Override
            public void onBobberSpawned(
                    net.zaminmc.torch.server.fishing.FishingSystem.Bobber bobber) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendBobberSpawned(bobber);
                }
            }

            @Override
            public void onBobberMoved(
                    net.zaminmc.torch.server.fishing.FishingSystem.Bobber bobber) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendBobberMoved(bobber);
                }
            }

            @Override
            public void onBobberRemoved(
                    net.zaminmc.torch.server.fishing.FishingSystem.Bobber bobber) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendBobberRemoved(bobber);
                }
            }
        });
        LOGGER.info(() -> "1.8.8 protocol listening on " + server.engineConfig().host() + ":" + server.engineConfig().port());
    }

    @Override
    public void shutdown() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        LOGGER.info("1.8.8 protocol adapter stopping");
        try {
            if (serverChannel != null) {
                serverChannel.close().syncUninterruptibly();
            }
            allChannels.close().syncUninterruptibly();
        } finally {
            if (bossGroup != null) {
                bossGroup.shutdownGracefully(0L, 2_000L, TimeUnit.MILLISECONDS).syncUninterruptibly();
            }
            if (workerGroup != null) {
                workerGroup.shutdownGracefully(0L, 2_000L, TimeUnit.MILLISECONDS).syncUninterruptibly();
            }
        }
        connections.clear();
        LOGGER.info("1.8.8 protocol adapter stopped");
    }

    int nextEntityId() {
        return entityIds.incrementAndGet();
    }

    /**
     * Mutual visibility exchange after a connection reached PLAY: existing
     * players are spawned for the newcomer and the newcomer for them.
     */
    void playerEnteredPlay(V18Connection newcomer) {
        for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
            if (connection == newcomer || connection.currentSession() == null) {
                continue;
            }
            newcomer.trackPlayer(connection.currentSession());
            connection.trackPlayer(newcomer.currentSession());
        }
        // The mobs already alive: the newcomer's world snapshot spawns them
        // (chunk-gated; later spawns broadcast to everyone like the items).
        var mobs = engine.mobs();
        if (mobs != null) {
            for (net.zaminmc.torch.server.entity.MobEntity mob : mobs.all()) {
                newcomer.sendMobSpawn(mob);
                // The leashed ones carry their holder into the newcomer's
                // view (the Attach Entity re-send — the reference's
                // startTracking arm).
                if (mob.isLeashed()) {
                    newcomer.sendLeashAttach(mob, mob.leashHolderPlayer(), mob.leashHolderKnot());
                }
            }
        }
        // The leash knots already tied (Slice 11c): the same world snapshot.
        var leads = engine.leads();
        if (leads != null) {
            for (net.zaminmc.torch.server.leash.LeashKnot knot : leads.allKnots()) {
                newcomer.sendKnotSpawned(knot);
            }
        }
        // The decorations already hanging (Slice 28a/28b): the frames +
        // the paintings in the same world snapshot.
        var decorations = engine.decorations();
        if (decorations != null) {
            for (net.zaminmc.torch.server.decoration.ItemFrame frame : decorations.allFrames()) {
                newcomer.sendFrameSpawned(frame);
            }
            for (net.zaminmc.torch.server.decoration.Painting painting : decorations.allPaintings()) {
                newcomer.sendPaintingSpawned(painting);
            }
        }
        // The armor stands already standing (Slice 28c): the same world
        // snapshot — the equipment rides the spawn's own five packets.
        var stands = engine.armorStands();
        if (stands != null) {
            for (net.zaminmc.torch.server.armorstand.ArmorStand stand : stands.allStands()) {
                newcomer.sendStandSpawned(stand);
            }
        }
        // The ender crystals already standing (Slice 62): the join's own
        // world snapshot — the send's own gates refuse them for the
        // overworld joiner (dimension 0); a future End-anchored join
        // streams them through this same walk.
        var crystals = engine.enderCrystals();
        if (crystals != null) {
            for (net.zaminmc.torch.server.endcrystal.EnderCrystal crystal : crystals.all()) {
                newcomer.sendCrystalSpawn(crystal);
            }
        }
        // Falling blocks mid-transition when someone joins: spawn them for the
        // newcomer the same way (a land end broadcasts Destroy to everyone).
        var falling = engine.fallingEntities();
        if (falling != null) {
            for (net.zaminmc.torch.server.entity.FallingBlockEntity entity : falling.all()) {
                newcomer.sendFallingSpawn(entity);
            }
        }
        // The item bodies already on the ground (Slice 93/E1 — the
        // persisted restore's among them): the newcomer's world snapshot
        // spawns them like every other pre-existing family (the
        // pre-E1 gap: a body dropped while nobody watched never reached
        // the next joiner).
        var itemEntities = engine.itemEntities();
        if (itemEntities != null) {
            for (net.zaminmc.torch.server.entity.ItemEntity item : itemEntities.all()) {
                newcomer.sendItemSpawn(item);
            }
        }
        // Vehicles mid-ride when someone joins: the bodies spawn for the
        // newcomer (a mount broadcast rides the Attach packet like always).
        var vehicles = engine.vehicles();
        if (vehicles != null) {
            for (net.zaminmc.torch.server.entity.vehicle.VehicleEntity vehicle : vehicles.all()) {
                newcomer.sendVehicleSpawn(vehicle);
            }
        }
    }

    /** A player swung their arm: other observers see the swing animation. */
    void broadcastArmSwing(V18Connection from) {
        PlayerSession swinger = from.currentSession();
        if (swinger == null) {
            return;
        }
        for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
            if (connection == from) {
                continue;
            }
            Integer id = connection.remoteWireEntityIdOf(swinger.uuid());
            if (id != null) {
                connection.sendAnimation(id);
            }
        }
    }

    /** Broadcasts a remote player's authoritative position to observers in range. */
    void broadcastMovement(PlayerSession mover) {
        var moverChunk = mover.position().toBlockPosition().chunkPosition();
        long viewSq = (long) engine.engineConfig().viewDistance() * engine.engineConfig().viewDistance();
        for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
            PlayerSession observer = connection.currentSession();
            if (observer == null || observer == mover) {
                continue;
            }
            // Slice 64 — the cross-dimension gate: the End's arrival cell
            // (x 100) sits inside the overworld spawn's view range, so raw
            // coordinates alone would leak players across dimensions. A
            // body in another dimension (or removed from the world for the
            // credits) is invisible — the reference's per-world entity map.
            if (observer.dimension() != mover.dimension() || mover.leavingTheEnd()) {
                connection.untrackPlayer(mover.uuid());
                continue;
            }
            var observerChunk = observer.position().toBlockPosition().chunkPosition();
            if (moverChunk.distanceSquared(observerChunk) <= viewSq) {
                connection.trackPlayer(mover);
                connection.sendRemoteTeleport(mover);
            } else {
                connection.untrackPlayer(mover.uuid()); // fell out of range
            }
        }
    }

    /** Removes a departed player's entity from every observer. */
    void playerLeft(java.util.UUID departedUuid) {
        if (departedUuid == null) {
            return;
        }
        for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
            connection.untrackPlayer(departedUuid);
        }
    }

    private void broadcastChat(String text) {
        // Paper mirrors every public chat line to the console (log4j chat
        // appender): the operator sees the conversation without a client.
        LOGGER.info(text);
        for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
            connection.sendChatLine(text, 0);
        }
    }

    Channel channelOf(V18Connection connection) {
        return connections.get(connection);
    }

    void forget(V18Connection connection) {
        connections.remove(connection);
    }

    public boolean isRunning() {
        return running.get();
    }

    long keepAliveIntervalMs() {
        return keepAliveIntervalMs;
    }

    /** @return the bound port; useful for tests that bind to an ephemeral port. */
    public int boundPort() {
        if (serverChannel == null) {
            throw new IllegalStateException("Adapter not started");
        }
        return ((java.net.InetSocketAddress) serverChannel.localAddress()).getPort();
    }
}
