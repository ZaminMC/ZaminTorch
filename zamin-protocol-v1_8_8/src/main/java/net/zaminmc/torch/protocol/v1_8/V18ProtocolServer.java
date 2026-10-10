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
                                .addLast("connection", connection);
                        connections.put(connection, channel);
                        allChannels.add(channel);
                    }
                });
        serverChannel = bootstrap.bind(server.config().host(), server.config().port()).sync().channel();
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
        // Time Update (0x03): the periodic cycle sync and the /time command
        // ride the same listener — the client's day follows the server's.
        server.addTimeListener((totalTicks, timeOfDay) -> {
            for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                connection.sendTimeUpdateNow();
            }
        });
        // Sign text (0x33): edits fan out to every connected viewer; the
        // chunk-send path replays signs per column separately.
        server.addSignListener((position, lines) -> {
            for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                connection.sendSignUpdate(position, lines);
            }
        });
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
                    connection.sendMobSound(mob.type().hurtSound, mob.position(), 1.0f, 1.0f);
                }
            }

            @Override
            public void onMobDied(net.zaminmc.torch.server.entity.MobEntity mob) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendMobStatus(mob, net.zaminmc.torch.protocol.v1_8.Protocol18.ENTITY_STATUS_DEAD);
                    connection.sendMobSound(mob.type().deathSound, mob.position(), 1.0f, 1.0f);
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
                    connection.sendMobStatusByte(mob, Protocol18.SHEEP_STATUS_SHEARED);
                }
            }

            @Override
            public void onMobCoatRegrown(net.zaminmc.torch.server.entity.MobEntity mob) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendMobStatusByte(mob, (byte) 0);
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

            @Override
            public void onMobLoveBurst(net.zaminmc.torch.server.entity.MobEntity mob) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendMobStatus(mob, Protocol18.ENTITY_STATUS_LOVE);
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
                connection.sendExplosion(event);
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
        });
        // Vehicle sync: spawn (Spawn Object), per-tick movement (Entity
        // Teleport + the rider's seat re-anchor), mount/dismount (Attach
        // Entity + the riding flags), hurt (Entity Status) and break.
        server.addVehicleListener(new EngineServer.VehicleListener() {
            @Override
            public void onVehicleSpawned(net.zaminmc.torch.server.entity.vehicle.VehicleEntity vehicle) {
                for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
                    connection.sendVehicleSpawn(vehicle);
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
        });
        LOGGER.info(() -> "1.8.8 protocol listening on " + server.config().host() + ":" + server.config().port());
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
        long viewSq = (long) engine.config().viewDistance() * engine.config().viewDistance();
        for (V18Connection connection : connections.keySet().toArray(new V18Connection[0])) {
            PlayerSession observer = connection.currentSession();
            if (observer == null || observer == mover) {
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
