package net.minecraft.server.network.handler;

import com.google.common.collect.Lists;
import com.google.common.primitives.Doubles;
import com.google.common.primitives.Floats;
import com.google.common.util.concurrent.Futures;
import io.netty.buffer.Unpooled;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import net.minecraft.SharedConstants;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.CommandBlockBlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.living.mob.passive.animal.HorseBaseEntity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.vehicle.CommandBlockMinecartEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.menu.AnvilMenu;
import net.minecraft.inventory.menu.BeaconMenu;
import net.minecraft.inventory.menu.InventoryMenu;
import net.minecraft.inventory.menu.TraderMenu;
import net.minecraft.inventory.slot.InventorySlot;
import net.minecraft.item.BookAndQuillItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.WrittenBookItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.PacketUtils;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ArmSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientSettingsC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseInventoryMenuC2SPacket;
import net.minecraft.network.packet.c2s.play.CommandSuggestionsC2SPacket;
import net.minecraft.network.packet.c2s.play.CreativeMenuSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.CustomPayloadC2SPacket;
import net.minecraft.network.packet.c2s.play.InventoryMenuClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.InventoryMenuConfirmC2SPacket;
import net.minecraft.network.packet.c2s.play.KeepAliveC2SPacket;
import net.minecraft.network.packet.c2s.play.MenuClickButtonC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerAbilitiesC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerHandActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMovementActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerSpectateC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerUseC2SPacket;
import net.minecraft.network.packet.c2s.play.ResourcePackC2SPacket;
import net.minecraft.network.packet.c2s.play.SelectSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.SignUpdateC2SPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChatMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.CommandSuggestionsS2CPacket;
import net.minecraft.network.packet.s2c.play.DisconnectS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityTeleportS2CPacket;
import net.minecraft.network.packet.s2c.play.InventoryMenuConfirmS2CPacket;
import net.minecraft.network.packet.s2c.play.InventoryMenuSlotContentS2CPacket;
import net.minecraft.network.packet.s2c.play.KeepAliveS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerMoveS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerBanEntry;
import net.minecraft.server.command.source.CommandExecutor;
import net.minecraft.server.entity.living.player.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.stat.achievement.Achievements;
import net.minecraft.text.Formatting;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Int2ObjectHashMap;
import net.minecraft.util.Tickable;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ServerPlayNetworkHandler implements ServerPlayPacketHandler, Tickable {
    private static final Logger LOGGER = LogManager.getLogger();
    public final Connection connection;
    private final MinecraftServer server;
    public ServerPlayerEntity player;
    private int ticks;
    private int requestedTeleportTime;
    private int floatingTime;
    private boolean moved;
    private int savedKeepAliveTime;
    private long keepAliveTime;
    private long lastKeepAliveUpdateTime;
    private int messageCooldown;
    private int dropItemCooldown;
    private Int2ObjectHashMap<Short> transactions = new Int2ObjectHashMap<>();
    private double teleportTargetX;
    private double teleportTargetY;
    private double teleportTargetZ;
    private boolean teleported = true;

    public ServerPlayNetworkHandler(MinecraftServer server, Connection connection, ServerPlayerEntity player) {
        this.server = server;
        this.connection = connection;
        connection.setListener(this);
        this.player = player;
        player.networkHandler = this;
    }

    @Override
    public void tick() {
        this.moved = false;
        this.ticks++;
        this.server.profiler.push("keepAlive");
        if (this.ticks - this.lastKeepAliveUpdateTime > 40L) {
            this.lastKeepAliveUpdateTime = this.ticks;
            this.keepAliveTime = this.getTimeMillis();
            this.savedKeepAliveTime = (int)this.keepAliveTime;
            this.sendPacket(new KeepAliveS2CPacket(this.savedKeepAliveTime));
        }

        this.server.profiler.pop();
        if (this.messageCooldown > 0) {
            this.messageCooldown--;
        }

        if (this.dropItemCooldown > 0) {
            this.dropItemCooldown--;
        }

        if (this.player.getLastActionTime() > 0L
            && this.server.getPlayerIdleTimeout() > 0
            && MinecraftServer.getTimeMillis() - this.player.getLastActionTime() > this.server.getPlayerIdleTimeout() * 1000 * 60) {
            this.disconnect("You have been idle for too long!");
        }
    }

    public Connection getConnection() {
        return this.connection;
    }

    public void disconnect(String reason) {
        final LiteralText literaltext = new LiteralText(reason);
        this.connection.send(new DisconnectS2CPacket(literaltext), new GenericFutureListener<Future<? super Void>>() {
            @Override
            public void operationComplete(Future<? super Void> future) throws Exception {
                ServerPlayNetworkHandler.this.connection.disconnect(literaltext);
            }
        });
        this.connection.disableAutoRead();
        Futures.getUnchecked(this.server.execute(new Runnable() {
            @Override
            public void run() {
                ServerPlayNetworkHandler.this.connection.handleDisconnection();
            }
        }));
    }

    @Override
    public void handlePlayerInput(PlayerInputC2SPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.player.getServerWorld());
        this.player.setPlayerInput(packet.getSidewaysSpeed(), packet.getForwardSpeed(), packet.getJumping(), packet.getSneaking());
    }

    private boolean isInvalidMove(PlayerMoveC2SPacket packet) {
        return !Doubles.isFinite(packet.getX())
            || !Doubles.isFinite(packet.getMinY())
            || !Doubles.isFinite(packet.getZ())
            || !Floats.isFinite(packet.getPitch())
            || !Floats.isFinite(packet.getYaw());
    }

    @Override
    public void handlePlayerMove(PlayerMoveC2SPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.player.getServerWorld());
        if (this.isInvalidMove(packet)) {
            this.disconnect("Invalid move packet received");
        } else {
            ServerWorld serverworld = this.server.getWorld(this.player.dimension);
            this.moved = true;
            if (!this.player.leavingTheEnd) {
                double d0 = this.player.x;
                double d1 = this.player.y;
                double d2 = this.player.z;
                double d3 = 0.0;
                double d4 = packet.getX() - this.teleportTargetX;
                double d5 = packet.getMinY() - this.teleportTargetY;
                double d6 = packet.getZ() - this.teleportTargetZ;
                if (packet.hasPos()) {
                    d3 = d4 * d4 + d5 * d5 + d6 * d6;
                    if (!this.teleported && d3 < 0.25) {
                        this.teleported = true;
                    }
                }

                if (this.teleported) {
                    this.requestedTeleportTime = this.ticks;
                    if (this.player.vehicle != null) {
                        float f4 = this.player.yaw;
                        float f = this.player.pitch;
                        this.player.vehicle.updateRiderPositon();
                        double d17 = this.player.x;
                        double d18 = this.player.y;
                        double d19 = this.player.z;
                        if (packet.hasAngles()) {
                            f4 = packet.getYaw();
                            f = packet.getPitch();
                        }

                        this.player.onGround = packet.getOnGround();
                        this.player.tickPlayer();
                        this.player.updatePositionAndAngles(d17, d18, d19, f4, f);
                        if (this.player.vehicle != null) {
                            this.player.vehicle.updateRiderPositon();
                        }

                        this.server.getPlayerManager().move(this.player);
                        if (this.player.vehicle != null) {
                            if (d3 > 4.0) {
                                Entity entity = this.player.vehicle;
                                this.player.networkHandler.sendPacket(new EntityTeleportS2CPacket(entity));
                                this.teleport(this.player.x, this.player.y, this.player.z, this.player.yaw, this.player.pitch);
                            }

                            this.player.vehicle.velocityDirty = true;
                        }

                        if (this.teleported) {
                            this.teleportTargetX = this.player.x;
                            this.teleportTargetY = this.player.y;
                            this.teleportTargetZ = this.player.z;
                        }

                        serverworld.tickEntity(this.player);
                        return;
                    }

                    if (this.player.isSleeping()) {
                        this.player.tickPlayer();
                        this.player
                            .updatePositionAndAngles(this.teleportTargetX, this.teleportTargetY, this.teleportTargetZ, this.player.yaw, this.player.pitch);
                        serverworld.tickEntity(this.player);
                        return;
                    }

                    double d7 = this.player.y;
                    this.teleportTargetX = this.player.x;
                    this.teleportTargetY = this.player.y;
                    this.teleportTargetZ = this.player.z;
                    double d8 = this.player.x;
                    double d9 = this.player.y;
                    double d10 = this.player.z;
                    float f1 = this.player.yaw;
                    float f2 = this.player.pitch;
                    if (packet.hasPos() && packet.getMinY() == -999.0) {
                        packet.setHasPos(false);
                    }

                    if (packet.hasPos()) {
                        d8 = packet.getX();
                        d9 = packet.getMinY();
                        d10 = packet.getZ();
                        if (Math.abs(packet.getX()) > 3.0E7 || Math.abs(packet.getZ()) > 3.0E7) {
                            this.disconnect("Illegal position");
                            return;
                        }
                    }

                    if (packet.hasAngles()) {
                        f1 = packet.getYaw();
                        f2 = packet.getPitch();
                    }

                    this.player.tickPlayer();
                    this.player.updatePositionAndAngles(this.teleportTargetX, this.teleportTargetY, this.teleportTargetZ, f1, f2);
                    if (!this.teleported) {
                        return;
                    }

                    double d11 = d8 - this.player.x;
                    double d12 = d9 - this.player.y;
                    double d13 = d10 - this.player.z;
                    double d14 = this.player.velocityX * this.player.velocityX
                        + this.player.velocityY * this.player.velocityY
                        + this.player.velocityZ * this.player.velocityZ;
                    double d15 = d11 * d11 + d12 * d12 + d13 * d13;
                    if (d15 - d14 > 100.0 && (!this.server.isSingleplayer() || !this.server.getUsername().equals(this.player.getName()))) {
                        LOGGER.warn(this.player.getName() + " moved too quickly! " + d11 + "," + d12 + "," + d13 + " (" + d11 + ", " + d12 + ", " + d13 + ")");
                        this.teleport(this.teleportTargetX, this.teleportTargetY, this.teleportTargetZ, this.player.yaw, this.player.pitch);
                        return;
                    }

                    float f3 = 0.0625F;
                    boolean flag = serverworld.getCollisions(this.player, this.player.getShape().contract(f3, f3, f3)).isEmpty();
                    if (this.player.onGround && !packet.getOnGround() && d12 > 0.0) {
                        this.player.jump();
                    }

                    this.player.move(d11, d12, d13);
                    this.player.onGround = packet.getOnGround();
                    double d16 = d12;
                    d11 = d8 - this.player.x;
                    d12 = d9 - this.player.y;
                    if (d12 > -0.5 || d12 < 0.5) {
                        d12 = 0.0;
                    }

                    d13 = d10 - this.player.z;
                    d15 = d11 * d11 + d12 * d12 + d13 * d13;
                    boolean flag1 = false;
                    if (d15 > 0.0625 && !this.player.isSleeping() && !this.player.interactionManager.isCreative()) {
                        flag1 = true;
                        LOGGER.warn(this.player.getName() + " moved wrongly!");
                    }

                    this.player.updatePositionAndAngles(d8, d9, d10, f1, f2);
                    this.player.tickNonRidingMovementRelatedStats(this.player.x - d0, this.player.y - d1, this.player.z - d2);
                    if (!this.player.noClip) {
                        boolean flag2 = serverworld.getCollisions(this.player, this.player.getShape().contract(f3, f3, f3)).isEmpty();
                        if (flag && (flag1 || !flag2) && !this.player.isSleeping()) {
                            this.teleport(this.teleportTargetX, this.teleportTargetY, this.teleportTargetZ, f1, f2);
                            return;
                        }
                    }

                    Box box = this.player.getShape().grown(f3, f3, f3).expanded(0.0, -0.55, 0.0);
                    if (this.server.isFlightEnabled() || this.player.abilities.canFly || serverworld.containsNonAir(box)) {
                        this.floatingTime = 0;
                    } else if (d16 >= -0.03125) {
                        this.floatingTime++;
                        if (this.floatingTime > 80) {
                            LOGGER.warn(this.player.getName() + " was kicked for floating too long!");
                            this.disconnect("Flying is not enabled on this server");
                            return;
                        }
                    }

                    this.player.onGround = packet.getOnGround();
                    this.server.getPlayerManager().move(this.player);
                    this.player.handleFall(this.player.y - d7, packet.getOnGround());
                } else if (this.ticks - this.requestedTeleportTime > 20) {
                    this.teleport(this.teleportTargetX, this.teleportTargetY, this.teleportTargetZ, this.player.yaw, this.player.pitch);
                }
            }
        }
    }

    public void teleport(double x, double y, double z, float yaw, float pitch) {
        this.teleport(x, y, z, yaw, pitch, Collections.emptySet());
    }

    public void teleport(double x, double y, double z, float yaw, float pitch, Set<PlayerMoveS2CPacket.Argument> relativeArgs) {
        this.teleported = false;
        this.teleportTargetX = x;
        this.teleportTargetY = y;
        this.teleportTargetZ = z;
        if (relativeArgs.contains(PlayerMoveS2CPacket.Argument.X)) {
            this.teleportTargetX = this.teleportTargetX + this.player.x;
        }

        if (relativeArgs.contains(PlayerMoveS2CPacket.Argument.Y)) {
            this.teleportTargetY = this.teleportTargetY + this.player.y;
        }

        if (relativeArgs.contains(PlayerMoveS2CPacket.Argument.Z)) {
            this.teleportTargetZ = this.teleportTargetZ + this.player.z;
        }

        float f = yaw;
        float f1 = pitch;
        if (relativeArgs.contains(PlayerMoveS2CPacket.Argument.YAW)) {
            f += this.player.yaw;
        }

        if (relativeArgs.contains(PlayerMoveS2CPacket.Argument.PITCH)) {
            f1 += this.player.pitch;
        }

        this.player.updatePositionAndAngles(this.teleportTargetX, this.teleportTargetY, this.teleportTargetZ, f, f1);
        this.player.networkHandler.sendPacket(new PlayerMoveS2CPacket(x, y, z, yaw, pitch, relativeArgs));
    }

    @Override
    public void handlePlayerHandAction(PlayerHandActionC2SPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.player.getServerWorld());
        ServerWorld serverworld = this.server.getWorld(this.player.dimension);
        BlockPos blockpos = packet.getPos();
        this.player.updateLastActionTime();
        switch (packet.getAction()) {
            case DROP_ITEM:
                if (!this.player.isSpectator()) {
                    this.player.dropItem(false);
                }

                return;
            case DROP_ALL_ITEMS:
                if (!this.player.isSpectator()) {
                    this.player.dropItem(true);
                }

                return;
            case RELEASE_USE_ITEM:
                this.player.stopUsingItem();
                return;
            case START_DESTROY_BLOCK:
            case ABORT_DESTROY_BLOCK:
            case STOP_DESTROY_BLOCK:
                double d0 = this.player.x - (blockpos.getX() + 0.5);
                double d1 = this.player.y - (blockpos.getY() + 0.5) + 1.5;
                double d2 = this.player.z - (blockpos.getZ() + 0.5);
                double d3 = d0 * d0 + d1 * d1 + d2 * d2;
                if (d3 > 36.0) {
                    return;
                } else if (blockpos.getY() >= this.server.getWorldHeight()) {
                    return;
                } else {
                    if (packet.getAction() == PlayerHandActionC2SPacket.Action.START_DESTROY_BLOCK) {
                        if (!this.server.isSpawnProtected(serverworld, blockpos, this.player) && serverworld.getWorldBorder().contains(blockpos)) {
                            this.player.interactionManager.startMiningBlock(blockpos, packet.getFace());
                        } else {
                            this.player.networkHandler.sendPacket(new BlockUpdateS2CPacket(serverworld, blockpos));
                        }
                    } else {
                        if (packet.getAction() == PlayerHandActionC2SPacket.Action.STOP_DESTROY_BLOCK) {
                            this.player.interactionManager.finishMiningBlock(blockpos);
                        } else if (packet.getAction() == PlayerHandActionC2SPacket.Action.ABORT_DESTROY_BLOCK) {
                            this.player.interactionManager.stopMiningBlock();
                        }

                        if (serverworld.getBlockState(blockpos).getBlock().getMaterial() != Material.AIR) {
                            this.player.networkHandler.sendPacket(new BlockUpdateS2CPacket(serverworld, blockpos));
                        }
                    }

                    return;
                }
            default:
                throw new IllegalArgumentException("Invalid player action");
        }
    }

    @Override
    public void handlePlayerUse(PlayerUseC2SPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.player.getServerWorld());
        ServerWorld serverworld = this.server.getWorld(this.player.dimension);
        ItemStack itemstack = this.player.inventory.getSelectedItem();
        boolean flag = false;
        BlockPos blockpos = packet.getPos();
        Direction direction = Direction.byId(packet.getFace());
        this.player.updateLastActionTime();
        if (packet.getFace() == 255) {
            if (itemstack == null) {
                return;
            }

            this.player.interactionManager.useItem(this.player, serverworld, itemstack);
        } else if (blockpos.getY() < this.server.getWorldHeight() - 1 || direction != Direction.UP && blockpos.getY() < this.server.getWorldHeight()) {
            if (this.teleported
                && this.player.squaredDistanceTo(blockpos.getX() + 0.5, blockpos.getY() + 0.5, blockpos.getZ() + 0.5) < 64.0
                && !this.server.isSpawnProtected(serverworld, blockpos, this.player)
                && serverworld.getWorldBorder().contains(blockpos)) {
                this.player
                    .interactionManager
                    .useBlock(this.player, serverworld, itemstack, blockpos, direction, packet.getFaceX(), packet.getFaceY(), packet.getFaceZ());
            }

            flag = true;
        } else {
            TranslatableText translatabletext = new TranslatableText("build.tooHigh", this.server.getWorldHeight());
            translatabletext.getStyle().setColor(Formatting.RED);
            this.player.networkHandler.sendPacket(new ChatMessageS2CPacket(translatabletext));
            flag = true;
        }

        if (flag) {
            this.player.networkHandler.sendPacket(new BlockUpdateS2CPacket(serverworld, blockpos));
            this.player.networkHandler.sendPacket(new BlockUpdateS2CPacket(serverworld, blockpos.offset(direction)));
        }

        itemstack = this.player.inventory.getSelectedItem();
        if (itemstack != null && itemstack.size == 0) {
            this.player.inventory.items[this.player.inventory.selectedSlot] = null;
            itemstack = null;
        }

        if (itemstack == null || itemstack.getUseDuration() == 0) {
            this.player.useItemCooldown = true;
            this.player.inventory.items[this.player.inventory.selectedSlot] = ItemStack.copyOf(this.player.inventory.items[this.player.inventory.selectedSlot]);
            InventorySlot inventoryslot = this.player.menu.getSlot(this.player.inventory, this.player.inventory.selectedSlot);
            this.player.menu.updateListeners();
            this.player.useItemCooldown = false;
            if (!ItemStack.matches(this.player.inventory.getSelectedItem(), packet.getItemInHand())) {
                this.sendPacket(new InventoryMenuSlotContentS2CPacket(this.player.menu.networkId, inventoryslot.index, this.player.inventory.getSelectedItem()));
            }
        }
    }

    @Override
    public void handlePlayerSpectate(PlayerSpectateC2SPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.player.getServerWorld());
        if (this.player.isSpectator()) {
            Entity entity = null;

            for (ServerWorld serverworld : this.server.worlds) {
                if (serverworld != null) {
                    entity = packet.getSpectateTarget(serverworld);
                    if (entity != null) {
                        break;
                    }
                }
            }

            if (entity != null) {
                this.player.setCamera(this.player);
                this.player.startRiding(null);
                if (entity.world != this.player.world) {
                    ServerWorld serverworld1 = this.player.getServerWorld();
                    ServerWorld serverworld2 = (ServerWorld)entity.world;
                    this.player.dimension = entity.dimension;
                    this.sendPacket(
                        new PlayerRespawnS2CPacket(
                            this.player.dimension,
                            serverworld1.getDifficulty(),
                            serverworld1.getData().getGeneratorType(),
                            this.player.interactionManager.getGameMode()
                        )
                    );
                    serverworld1.removeEntityNow(this.player);
                    this.player.removed = false;
                    this.player.setPositionAndAngles(entity.x, entity.y, entity.z, entity.yaw, entity.pitch);
                    if (this.player.isAlive()) {
                        serverworld1.tickEntity(this.player, false);
                        serverworld2.addEntity(this.player);
                        serverworld2.tickEntity(this.player, false);
                    }

                    this.player.setWorld(serverworld2);
                    this.server.getPlayerManager().onChangedDimension(this.player, serverworld1);
                    this.player.teleport(entity.x, entity.y, entity.z);
                    this.player.interactionManager.setWorld(serverworld2);
                    this.server.getPlayerManager().sendWorldInfo(this.player, serverworld2);
                    this.server.getPlayerManager().sendPlayerInfo(this.player);
                } else {
                    this.player.teleport(entity.x, entity.y, entity.z);
                }
            }
        }
    }

    @Override
    public void handleResourcePackResponse(ResourcePackC2SPacket packet) {
    }

    @Override
    public void onDisconnect(Text reason) {
        LOGGER.info(this.player.getName() + " lost connection: " + reason);
        this.server.forcePlayerSampleUpdate();
        TranslatableText translatabletext = new TranslatableText("multiplayer.player.left", this.player.getDisplayName());
        translatabletext.getStyle().setColor(Formatting.YELLOW);
        this.server.getPlayerManager().sendSystemMessage(translatabletext);
        this.player.onDisconnect();
        this.server.getPlayerManager().remove(this.player);
        if (this.server.isSingleplayer() && this.player.getName().equals(this.server.getUsername())) {
            LOGGER.info("Stopping singleplayer server as player logged out");
            this.server.stop();
        }
    }

    public void sendPacket(Packet packet) {
        if (packet instanceof ChatMessageS2CPacket) {
            ChatMessageS2CPacket chatmessages2cpacket = (ChatMessageS2CPacket)packet;
            PlayerEntity.ChatVisibility playerentity$chatvisibility = this.player.getChatVisibility();
            if (playerentity$chatvisibility == PlayerEntity.ChatVisibility.HIDDEN) {
                return;
            }

            if (playerentity$chatvisibility == PlayerEntity.ChatVisibility.SYSTEM && !chatmessages2cpacket.isSystemMessage()) {
                return;
            }
        }

        try {
            this.connection.send(packet);
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.of(throwable, "Sending packet");
            CrashReportCategory crashreportcategory = crashreport.addCategory("Packet being sent");
            crashreportcategory.add("Packet class", new Callable<String>() {
                public String call() throws Exception {
                    return packet.getClass().getCanonicalName();
                }
            });
            throw new CrashException(crashreport);
        }
    }

    @Override
    public void handleSelectSlot(SelectSlotC2SPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.player.getServerWorld());
        if (packet.getSlot() >= 0 && packet.getSlot() < PlayerInventory.getHotbarSize()) {
            this.player.inventory.selectedSlot = packet.getSlot();
            this.player.updateLastActionTime();
        } else {
            LOGGER.warn(this.player.getName() + " tried to set an invalid carried item");
        }
    }

    @Override
    public void handleChatMessage(ChatMessageC2SPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.player.getServerWorld());
        if (this.player.getChatVisibility() == PlayerEntity.ChatVisibility.HIDDEN) {
            TranslatableText translatabletext = new TranslatableText("chat.cannotSend");
            translatabletext.getStyle().setColor(Formatting.RED);
            this.sendPacket(new ChatMessageS2CPacket(translatabletext));
        } else {
            this.player.updateLastActionTime();
            String s = packet.getMessage();
            s = StringUtils.normalizeSpace(s);

            for (int i = 0; i < s.length(); i++) {
                if (!SharedConstants.isValidChatChar(s.charAt(i))) {
                    this.disconnect("Illegal characters in chat");
                    return;
                }
            }

            if (s.startsWith("/")) {
                this.runCommand(s);
            } else {
                Text text = new TranslatableText("chat.type.text", this.player.getDisplayName(), s);
                this.server.getPlayerManager().sendMessage(text, false);
            }

            this.messageCooldown += 20;
            if (this.messageCooldown > 200 && !this.server.getPlayerManager().isOp(this.player.getGameProfile())) {
                this.disconnect("disconnect.spam");
            }
        }
    }

    private void runCommand(String command) {
        this.server.getCommandHandler().run(this.player, command);
    }

    @Override
    public void handleArmSwing(ArmSwingC2SPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.player.getServerWorld());
        this.player.updateLastActionTime();
        this.player.swingArm();
    }

    @Override
    public void handlePlayerMovementAction(PlayerMovementActionC2SPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.player.getServerWorld());
        this.player.updateLastActionTime();
        switch (packet.getAction()) {
            case START_SNEAKING:
                this.player.setSneaking(true);
                break;
            case STOP_SNEAKING:
                this.player.setSneaking(false);
                break;
            case START_SPRINTING:
                this.player.setSprinting(true);
                break;
            case STOP_SPRINTING:
                this.player.setSprinting(false);
                break;
            case STOP_SLEEPING:
                this.player.wakeUp(false, true, true);
                this.teleported = false;
                break;
            case RIDING_JUMP:
                if (this.player.vehicle instanceof HorseBaseEntity) {
                    ((HorseBaseEntity)this.player.vehicle).setJumpStrength(packet.getData());
                }
                break;
            case OPEN_HORSE_INVENTORY:
                if (this.player.vehicle instanceof HorseBaseEntity) {
                    ((HorseBaseEntity)this.player.vehicle).openInventory(this.player);
                }
                break;
            default:
                throw new IllegalArgumentException("Invalid client command!");
        }
    }

    @Override
    public void handleInteractEntity(PlayerInteractEntityC2SPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.player.getServerWorld());
        ServerWorld serverworld = this.server.getWorld(this.player.dimension);
        Entity entity = packet.getInteractTarget(serverworld);
        this.player.updateLastActionTime();
        if (entity != null) {
            boolean flag = this.player.canSee(entity);
            double d0 = 36.0;
            if (!flag) {
                d0 = 9.0;
            }

            if (this.player.squaredDistanceTo(entity) < d0) {
                if (packet.getAction() == PlayerInteractEntityC2SPacket.Action.INTERACT) {
                    this.player.interact(entity);
                } else if (packet.getAction() == PlayerInteractEntityC2SPacket.Action.INTERACT_AT) {
                    entity.interactAt(this.player, packet.getOffset());
                } else if (packet.getAction() == PlayerInteractEntityC2SPacket.Action.ATTACK) {
                    if (entity instanceof ItemEntity || entity instanceof ExperienceOrbEntity || entity instanceof ArrowEntity || entity == this.player) {
                        this.disconnect("Attempting to attack an invalid entity");
                        this.server.warn("Player " + this.player.getName() + " tried to attack an invalid entity");
                        return;
                    }

                    this.player.attack(entity);
                }
            }
        }
    }

    @Override
    public void handleClientStatus(ClientStatusC2SPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.player.getServerWorld());
        this.player.updateLastActionTime();
        ClientStatusC2SPacket.Status clientstatusc2spacket$status = packet.getStatus();
        switch (clientstatusc2spacket$status) {
            case PERFORM_RESPAWN:
                if (this.player.leavingTheEnd) {
                    this.player = this.server.getPlayerManager().respawn(this.player, 0, true);
                } else if (this.player.getServerWorld().getData().isHardcore()) {
                    if (this.server.isSingleplayer() && this.player.getName().equals(this.server.getUsername())) {
                        this.player.networkHandler.disconnect("You have died. Game over, man, it's game over!");
                        this.server.deleteWorldAndStop();
                    } else {
                        PlayerBanEntry playerbanentry = new PlayerBanEntry(
                            this.player.getGameProfile(), null, "(You just lost the game)", null, "Death in Hardcore"
                        );
                        this.server.getPlayerManager().getBans().add(playerbanentry);
                        this.player.networkHandler.disconnect("You have died. Game over, man, it's game over!");
                    }
                } else {
                    if (this.player.getHealth() > 0.0F) {
                        return;
                    }

                    this.player = this.server.getPlayerManager().respawn(this.player, 0, false);
                }
                break;
            case REQUEST_STATS:
                this.player.getStats().sendStats(this.player);
                break;
            case OPEN_INVENTORY_ACHIEVEMENT:
                this.player.incrementStat(Achievements.OPEN_INVENTORY);
        }
    }

    @Override
    public void handleCloseInventoryMenu(CloseInventoryMenuC2SPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.player.getServerWorld());
        this.player.doCloseMenu();
    }

    @Override
    public void handleInventoryMenuClickSlot(InventoryMenuClickSlotC2SPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.player.getServerWorld());
        this.player.updateLastActionTime();
        if (this.player.menu.networkId == packet.getMenuId() && this.player.menu.isSynced(this.player)) {
            if (this.player.isSpectator()) {
                List<ItemStack> list = Lists.newArrayList();

                for (int i = 0; i < this.player.menu.slots.size(); i++) {
                    list.add(this.player.menu.slots.get(i).getItem());
                }

                this.player.onMenuChanged(this.player.menu, list);
            } else {
                ItemStack itemstack = this.player.menu.onClickSlot(packet.getSlot(), packet.getClickData(), packet.getAction(), this.player);
                if (ItemStack.matches(packet.getItem(), itemstack)) {
                    this.player.networkHandler.sendPacket(new InventoryMenuConfirmS2CPacket(packet.getMenuId(), packet.getActionId(), true));
                    this.player.useItemCooldown = true;
                    this.player.menu.updateListeners();
                    this.player.use();
                    this.player.useItemCooldown = false;
                } else {
                    this.transactions.put(this.player.menu.networkId, packet.getActionId());
                    this.player.networkHandler.sendPacket(new InventoryMenuConfirmS2CPacket(packet.getMenuId(), packet.getActionId(), false));
                    this.player.menu.setSynced(this.player, false);
                    List<ItemStack> list1 = Lists.newArrayList();

                    for (int j = 0; j < this.player.menu.slots.size(); j++) {
                        list1.add(this.player.menu.slots.get(j).getItem());
                    }

                    this.player.onMenuChanged(this.player.menu, list1);
                }
            }
        }
    }

    @Override
    public void handleMenuClickButton(MenuClickButtonC2SPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.player.getServerWorld());
        this.player.updateLastActionTime();
        if (this.player.menu.networkId == packet.getMenuId() && this.player.menu.isSynced(this.player) && !this.player.isSpectator()) {
            this.player.menu.onButtonClick(this.player, packet.getButtonId());
            this.player.menu.updateListeners();
        }
    }

    @Override
    public void handleCreativeMenuSlot(CreativeMenuSlotC2SPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.player.getServerWorld());
        if (this.player.interactionManager.isCreative()) {
            boolean flag = packet.getSlotId() < 0;
            ItemStack itemstack = packet.getItem();
            if (itemstack != null && itemstack.hasNbt() && itemstack.getNbt().contains("BlockEntityTag", 10)) {
                NbtCompound nbtcompound = itemstack.getNbt().getCompound("BlockEntityTag");
                if (nbtcompound.contains("x") && nbtcompound.contains("y") && nbtcompound.contains("z")) {
                    BlockPos blockpos = new BlockPos(nbtcompound.getInt("x"), nbtcompound.getInt("y"), nbtcompound.getInt("z"));
                    BlockEntity blockentity = this.player.world.getBlockEntity(blockpos);
                    if (blockentity != null) {
                        NbtCompound nbtcompound1 = new NbtCompound();
                        blockentity.writeNbt(nbtcompound1);
                        nbtcompound1.remove("x");
                        nbtcompound1.remove("y");
                        nbtcompound1.remove("z");
                        itemstack.addToNbt("BlockEntityTag", nbtcompound1);
                    }
                }
            }

            boolean flag1 = packet.getSlotId() >= 1 && packet.getSlotId() < 36 + PlayerInventory.getHotbarSize();
            boolean flag2 = itemstack == null || itemstack.getItem() != null;
            boolean flag3 = itemstack == null || itemstack.getMetadata() >= 0 && itemstack.size <= 64 && itemstack.size > 0;
            if (flag1 && flag2 && flag3) {
                if (itemstack == null) {
                    this.player.playerMenu.setItem(packet.getSlotId(), null);
                } else {
                    this.player.playerMenu.setItem(packet.getSlotId(), itemstack);
                }

                this.player.playerMenu.setSynced(this.player, true);
            } else if (flag && flag2 && flag3 && this.dropItemCooldown < 200) {
                this.dropItemCooldown += 20;
                ItemEntity itementity = this.player.dropItem(itemstack, true);
                if (itementity != null) {
                    itementity.resetAge();
                }
            }
        }
    }

    @Override
    public void handleInventoryMenuConfirm(InventoryMenuConfirmC2SPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.player.getServerWorld());
        Short oshort = this.transactions.get(this.player.menu.networkId);
        if (oshort != null
            && packet.getActionId() == oshort
            && this.player.menu.networkId == packet.getMenuId()
            && !this.player.menu.isSynced(this.player)
            && !this.player.isSpectator()) {
            this.player.menu.setSynced(this.player, true);
        }
    }

    @Override
    public void handleSignUpdate(SignUpdateC2SPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.player.getServerWorld());
        this.player.updateLastActionTime();
        ServerWorld serverworld = this.server.getWorld(this.player.dimension);
        BlockPos blockpos = packet.getPos();
        if (serverworld.isChunkLoaded(blockpos)) {
            BlockEntity blockentity = serverworld.getBlockEntity(blockpos);
            if (!(blockentity instanceof SignBlockEntity)) {
                return;
            }

            SignBlockEntity signblockentity = (SignBlockEntity)blockentity;
            if (!signblockentity.isEditable() || signblockentity.getPlayer() != this.player) {
                this.server.warn("Player " + this.player.getName() + " just tried to change non-editable sign");
                return;
            }

            Text[] atext = packet.getLines();

            for (int i = 0; i < atext.length; i++) {
                signblockentity.lines[i] = new LiteralText(Formatting.strip(atext[i].getString()));
            }

            signblockentity.markDirty();
            serverworld.notifyBlockChanged(blockpos);
        }
    }

    @Override
    public void handleKeepAlive(KeepAliveC2SPacket packet) {
        if (packet.getTimeMillis() == this.savedKeepAliveTime) {
            int i = (int)(this.getTimeMillis() - this.keepAliveTime);
            this.player.ping = (this.player.ping * 3 + i) / 4;
        }
    }

    private long getTimeMillis() {
        return System.nanoTime() / 1000000L;
    }

    @Override
    public void handlePlayerAbilities(PlayerAbilitiesC2SPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.player.getServerWorld());
        this.player.abilities.flying = packet.isFlying() && this.player.abilities.canFly;
    }

    @Override
    public void handleCommandSuggestions(CommandSuggestionsC2SPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.player.getServerWorld());
        List<String> list = Lists.newArrayList();

        for (String s : this.server.getCommandSuggestions(this.player, packet.getCommand(), packet.getPos())) {
            list.add(s);
        }

        this.player.networkHandler.sendPacket(new CommandSuggestionsS2CPacket(list.toArray(new String[list.size()])));
    }

    @Override
    public void handleClientSettings(ClientSettingsC2SPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.player.getServerWorld());
        this.player.updateSettings(packet);
    }

    @Override
    public void handleCustomPayload(CustomPayloadC2SPacket packet) {
        PacketUtils.ensureOnSameThread(packet, this, this.player.getServerWorld());
        if ("MC|BEdit".equals(packet.getChannel())) {
            PacketByteBuf packetbytebuf3 = new PacketByteBuf(Unpooled.wrappedBuffer(packet.getData()));

            try {
                ItemStack itemstack1 = packetbytebuf3.readItem();
                if (itemstack1 == null) {
                    return;
                }

                if (!BookAndQuillItem.isValid(itemstack1.getNbt())) {
                    throw new IOException("Invalid book tag!");
                }

                ItemStack itemstack3 = this.player.inventory.getSelectedItem();
                if (itemstack3 != null) {
                    if (itemstack1.getItem() == Items.WRITABLE_BOOK && itemstack1.getItem() == itemstack3.getItem()) {
                        itemstack3.addToNbt("pages", itemstack1.getNbt().getList("pages", 8));
                    }

                    return;
                }
            } catch (Exception exception3) {
                LOGGER.error("Couldn't handle book info", exception3);
                return;
            } finally {
                packetbytebuf3.release();
            }

            return;
        } else if ("MC|BSign".equals(packet.getChannel())) {
            PacketByteBuf packetbytebuf2 = new PacketByteBuf(Unpooled.wrappedBuffer(packet.getData()));

            try {
                ItemStack itemstack = packetbytebuf2.readItem();
                if (itemstack == null) {
                    return;
                }

                if (!WrittenBookItem.isValid(itemstack.getNbt())) {
                    throw new IOException("Invalid book tag!");
                }

                ItemStack itemstack2 = this.player.inventory.getSelectedItem();
                if (itemstack2 != null) {
                    if (itemstack.getItem() == Items.WRITTEN_BOOK && itemstack2.getItem() == Items.WRITABLE_BOOK) {
                        itemstack2.addToNbt("author", new NbtString(this.player.getName()));
                        itemstack2.addToNbt("title", new NbtString(itemstack.getNbt().getString("title")));
                        itemstack2.addToNbt("pages", itemstack.getNbt().getList("pages", 8));
                        itemstack2.setItem(Items.WRITTEN_BOOK);
                    }

                    return;
                }
            } catch (Exception exception4) {
                LOGGER.error("Couldn't sign book", exception4);
                return;
            } finally {
                packetbytebuf2.release();
            }

            return;
        } else if ("MC|TrSel".equals(packet.getChannel())) {
            try {
                int i = packet.getData().readInt();
                InventoryMenu inventorymenu = this.player.menu;
                if (inventorymenu instanceof TraderMenu) {
                    ((TraderMenu)inventorymenu).setRecipeIndex(i);
                }
            } catch (Exception exception2) {
                LOGGER.error("Couldn't select trade", exception2);
            }
        } else if ("MC|AdvCdm".equals(packet.getChannel())) {
            if (!this.server.areCommandBlocksEnabled()) {
                this.player.sendMessage(new TranslatableText("advMode.notEnabled"));
            } else if (this.player.canUseCommand(2, "") && this.player.abilities.creativeMode) {
                PacketByteBuf packetbytebuf = packet.getData();

                try {
                    int j = packetbytebuf.readByte();
                    CommandExecutor commandexecutor = null;
                    if (j == 0) {
                        BlockEntity blockentity = this.player
                            .world
                            .getBlockEntity(new BlockPos(packetbytebuf.readInt(), packetbytebuf.readInt(), packetbytebuf.readInt()));
                        if (blockentity instanceof CommandBlockBlockEntity) {
                            commandexecutor = ((CommandBlockBlockEntity)blockentity).getCommandExecutor();
                        }
                    } else if (j == 1) {
                        Entity entity = this.player.world.getEntity(packetbytebuf.readInt());
                        if (entity instanceof CommandBlockMinecartEntity) {
                            commandexecutor = ((CommandBlockMinecartEntity)entity).getCommandExecutor();
                        }
                    }

                    String s1 = packetbytebuf.readString(packetbytebuf.readableBytes());
                    boolean flag = packetbytebuf.readBoolean();
                    if (commandexecutor != null) {
                        commandexecutor.setCommand(s1);
                        commandexecutor.setTrackOutput(flag);
                        if (!flag) {
                            commandexecutor.setLastOutput(null);
                        }

                        commandexecutor.markDirty();
                        this.player.sendMessage(new TranslatableText("advMode.setCommand.success", s1));
                    }
                } catch (Exception exception1) {
                    LOGGER.error("Couldn't set command block", exception1);
                } finally {
                    packetbytebuf.release();
                }
            } else {
                this.player.sendMessage(new TranslatableText("advMode.notAllowed"));
            }
        } else if ("MC|Beacon".equals(packet.getChannel())) {
            if (this.player.menu instanceof BeaconMenu) {
                try {
                    PacketByteBuf packetbytebuf1 = packet.getData();
                    int k = packetbytebuf1.readInt();
                    int l = packetbytebuf1.readInt();
                    BeaconMenu beaconmenu = (BeaconMenu)this.player.menu;
                    InventorySlot inventoryslot = beaconmenu.getSlot(0);
                    if (inventoryslot.hasItem()) {
                        inventoryslot.removeItem(1);
                        Inventory inventory = beaconmenu.getBeacon();
                        inventory.setData(1, k);
                        inventory.setData(2, l);
                        inventory.markDirty();
                    }
                } catch (Exception exception) {
                    LOGGER.error("Couldn't set beacon", exception);
                }
            }
        } else if ("MC|ItemName".equals(packet.getChannel()) && this.player.menu instanceof AnvilMenu) {
            AnvilMenu anvilmenu = (AnvilMenu)this.player.menu;
            if (packet.getData() != null && packet.getData().readableBytes() >= 1) {
                String s = SharedConstants.stripInvalidChars(packet.getData().readString(32767));
                if (s.length() <= 30) {
                    anvilmenu.setItemName(s);
                }
            } else {
                anvilmenu.setItemName("");
            }
        }
    }
}
