package net.minecraft.server;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.io.File;
import java.net.SocketAddress;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.Entities;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.effect.StatusEffectInstance;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.ChatMessageS2CPacket;
import net.minecraft.network.packet.s2c.play.CustomPayloadS2CPacket;
import net.minecraft.network.packet.s2c.play.DifficultyS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusEffectS2CPacket;
import net.minecraft.network.packet.s2c.play.GameEventS2CPacket;
import net.minecraft.network.packet.s2c.play.LoginS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerAbilitiesS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerInfoS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerXpS2CPacket;
import net.minecraft.network.packet.s2c.play.SelectSlotS2CPacket;
import net.minecraft.network.packet.s2c.play.SpawnPointS2CPacket;
import net.minecraft.network.packet.s2c.play.TeamS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldBorderS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldTimeS2CPacket;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.team.AbstractTeam;
import net.minecraft.scoreboard.team.Team;
import net.minecraft.server.entity.living.player.ServerPlayerEntity;
import net.minecraft.server.network.handler.ServerPlayNetworkHandler;
import net.minecraft.server.scoreboard.ServerScoreboard;
import net.minecraft.server.stat.ServerPlayerStats;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.stat.Stats;
import net.minecraft.text.Formatting;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldData;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.border.WorldBorderListener;
import net.minecraft.world.storage.PlayerDataStorage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class PlayerManager {
    public static final File PLAYER_BANS_FILE = new File("banned-players.json");
    public static final File IP_BANS_FILE = new File("banned-ips.json");
    public static final File OPS_FILE = new File("ops.json");
    public static final File WHITELIST_FILE = new File("whitelist.json");
    private static final Logger LOGGER = LogManager.getLogger();
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd 'at' HH:mm:ss z");
    private final MinecraftServer server;
    private final List<ServerPlayerEntity> players = Lists.newArrayList();
    private final Map<UUID, ServerPlayerEntity> playersByUuid = Maps.newHashMap();
    private final PlayerBans bans = new PlayerBans(PLAYER_BANS_FILE);
    private final IpBans ipBans = new IpBans(IP_BANS_FILE);
    private final Ops ops = new Ops(OPS_FILE);
    private final Whitelist whitelist = new Whitelist(WHITELIST_FILE);
    private final Map<UUID, ServerPlayerStats> stats = Maps.newHashMap();
    private PlayerDataStorage dataStorage;
    private boolean enforceWhitelist;
    protected int maxPlayerCount;
    private int viewDistance;
    private WorldSettings.GameMode defaultGameMode;
    private boolean allowCommands;
    private int pingUpdateTime;

    public PlayerManager(MinecraftServer server) {
        this.server = server;
        this.bans.setEnabled(false);
        this.ipBans.setEnabled(false);
        this.maxPlayerCount = 8;
    }

    public void onLogin(Connection connection, ServerPlayerEntity player) {
        GameProfile gameprofile = player.getGameProfile();
        GameProfileCache gameprofilecache = this.server.getGameProfileCache();
        GameProfile gameprofile1 = gameprofilecache.get(gameprofile.getId());
        String s = gameprofile1 == null ? gameprofile.getName() : gameprofile1.getName();
        gameprofilecache.add(gameprofile);
        NbtCompound nbtcompound = this.load(player);
        player.setWorld(this.server.getWorld(player.dimension));
        player.interactionManager.setWorld((ServerWorld)player.world);
        String s1 = "local";
        if (connection.getAddress() != null) {
            s1 = connection.getAddress().toString();
        }

        LOGGER.info(
            player.getName() + "[" + s1 + "] logged in with entity id " + player.getNetworkId() + " at (" + player.x + ", " + player.y + ", " + player.z + ")"
        );
        ServerWorld serverworld = this.server.getWorld(player.dimension);
        WorldData worlddata = serverworld.getData();
        BlockPos blockpos = serverworld.getSpawnPoint();
        this.copyGameMode(player, null, serverworld);
        ServerPlayNetworkHandler serverplaynetworkhandler = new ServerPlayNetworkHandler(this.server, connection, player);
        serverplaynetworkhandler.sendPacket(
            new LoginS2CPacket(
                player.getNetworkId(),
                player.interactionManager.getGameMode(),
                worlddata.isHardcore(),
                serverworld.dimension.getId(),
                serverworld.getDifficulty(),
                this.getMaxCount(),
                worlddata.getGeneratorType(),
                serverworld.getGameRules().getBoolean("reducedDebugInfo")
            )
        );
        serverplaynetworkhandler.sendPacket(
            new CustomPayloadS2CPacket("MC|Brand", new PacketByteBuf(Unpooled.buffer()).writeString(this.getServer().getServerModName()))
        );
        serverplaynetworkhandler.sendPacket(new DifficultyS2CPacket(worlddata.getDifficulty(), worlddata.isDifficultyLocked()));
        serverplaynetworkhandler.sendPacket(new SpawnPointS2CPacket(blockpos));
        serverplaynetworkhandler.sendPacket(new PlayerAbilitiesS2CPacket(player.abilities));
        serverplaynetworkhandler.sendPacket(new SelectSlotS2CPacket(player.inventory.selectedSlot));
        player.getStats().markAllDirty();
        player.getStats().sendAchievements(player);
        this.updateScoreboard((ServerScoreboard)serverworld.getScoreboard(), player);
        this.server.forcePlayerSampleUpdate();
        TranslatableText translatabletext;
        if (!player.getName().equalsIgnoreCase(s)) {
            translatabletext = new TranslatableText("multiplayer.player.joined.renamed", player.getDisplayName(), s);
        } else {
            translatabletext = new TranslatableText("multiplayer.player.joined", player.getDisplayName());
        }

        translatabletext.getStyle().setColor(Formatting.YELLOW);
        this.sendSystemMessage(translatabletext);
        this.add(player);
        serverplaynetworkhandler.teleport(player.x, player.y, player.z, player.yaw, player.pitch);
        this.sendWorldInfo(player, serverworld);
        if (this.server.getResourcePackUrl().length() > 0) {
            player.sendResourcePack(this.server.getResourcePackUrl(), this.server.getResourcePackHash());
        }

        for (StatusEffectInstance statuseffectinstance : player.getStatusEffects()) {
            serverplaynetworkhandler.sendPacket(new EntityStatusEffectS2CPacket(player.getNetworkId(), statuseffectinstance));
        }

        player.initMenu();
        if (nbtcompound != null && nbtcompound.contains("Riding", 10)) {
            Entity entity = Entities.create(nbtcompound.getCompound("Riding"), serverworld);
            if (entity != null) {
                entity.teleporting = true;
                serverworld.addEntity(entity);
                player.startRiding(entity);
                entity.teleporting = false;
            }
        }
    }

    protected void updateScoreboard(ServerScoreboard scoreboard, ServerPlayerEntity player) {
        Set<ScoreboardObjective> set = Sets.newHashSet();

        for (Team team : scoreboard.getTeams()) {
            player.networkHandler.sendPacket(new TeamS2CPacket(team, 0));
        }

        for (int i = 0; i < 19; i++) {
            ScoreboardObjective scoreboardobjective = scoreboard.getDisplayObjective(i);
            if (scoreboardobjective != null && !set.contains(scoreboardobjective)) {
                for (Packet packet : scoreboard.createStartDisplayingObjectivePackets(scoreboardobjective)) {
                    player.networkHandler.sendPacket(packet);
                }

                set.add(scoreboardobjective);
            }
        }
    }

    public void setWorld(ServerWorld[] worlds) {
        this.dataStorage = worlds[0].getStorage().getPlayerDataStorage();
        worlds[0].getWorldBorder().addListener(new WorldBorderListener() {
            @Override
            public void onSizeChanged(WorldBorder border, double size) {
                PlayerManager.this.sendPacket(new WorldBorderS2CPacket(border, WorldBorderS2CPacket.Type.SET_SIZE));
            }

            @Override
            public void onSizeChanged(WorldBorder border, double size, double sizeLerpTarget, long sizeLerpTime) {
                PlayerManager.this.sendPacket(new WorldBorderS2CPacket(border, WorldBorderS2CPacket.Type.LERP_SIZE));
            }

            @Override
            public void onCenterChanged(WorldBorder border, double centerX, double centerZ) {
                PlayerManager.this.sendPacket(new WorldBorderS2CPacket(border, WorldBorderS2CPacket.Type.SET_CENTER));
            }

            @Override
            public void onWarningTimeChanged(WorldBorder border, int warningTime) {
                PlayerManager.this.sendPacket(new WorldBorderS2CPacket(border, WorldBorderS2CPacket.Type.SET_WARNING_TIME));
            }

            @Override
            public void onWarningBlocksChanged(WorldBorder border, int warningBlocks) {
                PlayerManager.this.sendPacket(new WorldBorderS2CPacket(border, WorldBorderS2CPacket.Type.SET_WARNING_BLOCKS));
            }

            @Override
            public void onDamagePerBlockChanged(WorldBorder border, double damagePerBlock) {
            }

            @Override
            public void onSafeZoneChanged(WorldBorder border, double safeZone) {
            }
        });
    }

    public void onChangedDimension(ServerPlayerEntity player, ServerWorld prevWorld) {
        ServerWorld serverworld = player.getServerWorld();
        if (prevWorld != null) {
            prevWorld.getChunkMap().removePlayer(player);
        }

        serverworld.getChunkMap().addPlayer(player);
        serverworld.chunkCache.loadChunk((int)player.x >> 4, (int)player.z >> 4);
    }

    public int getViewDistance() {
        return ChunkMap.getViewDistance(this.getChunkViewDistance());
    }

    public NbtCompound load(ServerPlayerEntity player) {
        NbtCompound nbtcompound = this.server.worlds[0].getData().getPlayerData();
        NbtCompound nbtcompound1;
        if (player.getName().equals(this.server.getUsername()) && nbtcompound != null) {
            player.readNbt(nbtcompound);
            nbtcompound1 = nbtcompound;
            LOGGER.debug("loading single player");
        } else {
            nbtcompound1 = this.dataStorage.loadPlayerData(player);
        }

        return nbtcompound1;
    }

    protected void save(ServerPlayerEntity player) {
        this.dataStorage.savePlayerData(player);
        ServerPlayerStats serverplayerstats = this.stats.get(player.getUuid());
        if (serverplayerstats != null) {
            serverplayerstats.save();
        }
    }

    public void add(ServerPlayerEntity player) {
        this.players.add(player);
        this.playersByUuid.put(player.getUuid(), player);
        this.sendPacket(new PlayerInfoS2CPacket(PlayerInfoS2CPacket.Action.ADD_PLAYER, player));
        ServerWorld serverworld = this.server.getWorld(player.dimension);
        serverworld.addEntity(player);
        this.onChangedDimension(player, null);

        for (int i = 0; i < this.players.size(); i++) {
            ServerPlayerEntity serverplayerentity = this.players.get(i);
            player.networkHandler.sendPacket(new PlayerInfoS2CPacket(PlayerInfoS2CPacket.Action.ADD_PLAYER, serverplayerentity));
        }
    }

    public void move(ServerPlayerEntity player) {
        player.getServerWorld().getChunkMap().movePlayer(player);
    }

    public void remove(ServerPlayerEntity player) {
        player.incrementStat(Stats.GAMES_LEFT);
        this.save(player);
        ServerWorld serverworld = player.getServerWorld();
        if (player.vehicle != null) {
            serverworld.removeEntityNow(player.vehicle);
            LOGGER.debug("removing player mount");
        }

        serverworld.removeEntity(player);
        serverworld.getChunkMap().removePlayer(player);
        this.players.remove(player);
        UUID uuid = player.getUuid();
        ServerPlayerEntity serverplayerentity = this.playersByUuid.get(uuid);
        if (serverplayerentity == player) {
            this.playersByUuid.remove(uuid);
            this.stats.remove(uuid);
        }

        this.sendPacket(new PlayerInfoS2CPacket(PlayerInfoS2CPacket.Action.REMOVE_PLAYER, player));
    }

    public String canLogin(SocketAddress address, GameProfile profile) {
        if (this.bans.isBanned(profile)) {
            PlayerBanEntry playerbanentry = this.bans.get(profile);
            String s1 = "You are banned from this server!\nReason: " + playerbanentry.getReason();
            if (playerbanentry.getExpirationDate() != null) {
                s1 = s1 + "\nYour ban will be removed on " + DATE_FORMAT.format(playerbanentry.getExpirationDate());
            }

            return s1;
        } else {
            if (!this.isWhitelisted(profile)) {
                return "You are not white-listed on this server!";
            }

            if (this.ipBans.isBanned(address)) {
                IpBanEntry ipbanentry = this.ipBans.get(address);
                String s = "Your IP address is banned from this server!\nReason: " + ipbanentry.getReason();
                if (ipbanentry.getExpirationDate() != null) {
                    s = s + "\nYour ban will be removed on " + DATE_FORMAT.format(ipbanentry.getExpirationDate());
                }

                return s;
            } else {
                return this.players.size() >= this.maxPlayerCount && !this.canBypassPlayerLimit(profile) ? "The server is full!" : null;
            }
        }
    }

    public ServerPlayerEntity createForLogin(GameProfile profile) {
        UUID uuid = PlayerEntity.getUuid(profile);
        List<ServerPlayerEntity> list = Lists.newArrayList();

        for (int i = 0; i < this.players.size(); i++) {
            ServerPlayerEntity serverplayerentity = this.players.get(i);
            if (serverplayerentity.getUuid().equals(uuid)) {
                list.add(serverplayerentity);
            }
        }

        ServerPlayerEntity serverplayerentity2 = this.playersByUuid.get(profile.getId());
        if (serverplayerentity2 != null && !list.contains(serverplayerentity2)) {
            list.add(serverplayerentity2);
        }

        for (ServerPlayerEntity serverplayerentity1 : list) {
            serverplayerentity1.networkHandler.disconnect("You logged in from another location");
        }

        ServerPlayerInteractionManager serverplayerinteractionmanager;
        if (this.server.isDemo()) {
            serverplayerinteractionmanager = new DemoServerPlayerInteractionManager(this.server.getWorld(0));
        } else {
            serverplayerinteractionmanager = new ServerPlayerInteractionManager(this.server.getWorld(0));
        }

        return new ServerPlayerEntity(this.server, this.server.getWorld(0), profile, serverplayerinteractionmanager);
    }

    public ServerPlayerEntity respawn(ServerPlayerEntity player, int dimension, boolean alive) {
        player.getServerWorld().getEntityMap().onPlayerRespawn(player);
        player.getServerWorld().getEntityMap().onEntityRemoved(player);
        player.getServerWorld().getChunkMap().removePlayer(player);
        this.players.remove(player);
        this.server.getWorld(player.dimension).removeEntityNow(player);
        BlockPos blockpos = player.getSpawnPoint();
        boolean flag = player.isRespawnForced();
        player.dimension = dimension;
        ServerPlayerInteractionManager serverplayerinteractionmanager;
        if (this.server.isDemo()) {
            serverplayerinteractionmanager = new DemoServerPlayerInteractionManager(this.server.getWorld(player.dimension));
        } else {
            serverplayerinteractionmanager = new ServerPlayerInteractionManager(this.server.getWorld(player.dimension));
        }

        ServerPlayerEntity serverplayerentity = new ServerPlayerEntity(
            this.server, this.server.getWorld(player.dimension), player.getGameProfile(), serverplayerinteractionmanager
        );
        serverplayerentity.networkHandler = player.networkHandler;
        serverplayerentity.copyFrom(player, alive);
        serverplayerentity.setNetworkId(player.getNetworkId());
        serverplayerentity.copyCommandResults(player);
        ServerWorld serverworld = this.server.getWorld(player.dimension);
        this.copyGameMode(serverplayerentity, player, serverworld);
        if (blockpos != null) {
            BlockPos blockpos1 = PlayerEntity.loadSpawnPoint(this.server.getWorld(player.dimension), blockpos, flag);
            if (blockpos1 != null) {
                serverplayerentity.setPositionAndAngles(blockpos1.getX() + 0.5F, blockpos1.getY() + 0.1F, blockpos1.getZ() + 0.5F, 0.0F, 0.0F);
                serverplayerentity.setSpawnPoint(blockpos, flag);
            } else {
                serverplayerentity.networkHandler.sendPacket(new GameEventS2CPacket(0, 0.0F));
            }
        }

        serverworld.chunkCache.loadChunk((int)serverplayerentity.x >> 4, (int)serverplayerentity.z >> 4);

        while (!serverworld.getCollisions(serverplayerentity, serverplayerentity.getShape()).isEmpty() && serverplayerentity.y < 256.0) {
            serverplayerentity.setPosition(serverplayerentity.x, serverplayerentity.y + 1.0, serverplayerentity.z);
        }

        serverplayerentity.networkHandler
            .sendPacket(
                new PlayerRespawnS2CPacket(
                    serverplayerentity.dimension,
                    serverplayerentity.world.getDifficulty(),
                    serverplayerentity.world.getData().getGeneratorType(),
                    serverplayerentity.interactionManager.getGameMode()
                )
            );
        BlockPos blockpos2 = serverworld.getSpawnPoint();
        serverplayerentity.networkHandler
            .teleport(serverplayerentity.x, serverplayerentity.y, serverplayerentity.z, serverplayerentity.yaw, serverplayerentity.pitch);
        serverplayerentity.networkHandler.sendPacket(new SpawnPointS2CPacket(blockpos2));
        serverplayerentity.networkHandler.sendPacket(new PlayerXpS2CPacket(serverplayerentity.xpProgress, serverplayerentity.xp, serverplayerentity.xpLevel));
        this.sendWorldInfo(serverplayerentity, serverworld);
        serverworld.getChunkMap().addPlayer(serverplayerentity);
        serverworld.addEntity(serverplayerentity);
        this.players.add(serverplayerentity);
        this.playersByUuid.put(serverplayerentity.getUuid(), serverplayerentity);
        serverplayerentity.initMenu();
        serverplayerentity.setHealth(serverplayerentity.getHealth());
        return serverplayerentity;
    }

    public void changeDimension(ServerPlayerEntity player, int dimension) {
        int i = player.dimension;
        ServerWorld serverworld = this.server.getWorld(player.dimension);
        player.dimension = dimension;
        ServerWorld serverworld1 = this.server.getWorld(player.dimension);
        player.networkHandler
            .sendPacket(
                new PlayerRespawnS2CPacket(
                    player.dimension, player.world.getDifficulty(), player.world.getData().getGeneratorType(), player.interactionManager.getGameMode()
                )
            );
        serverworld.removeEntityNow(player);
        player.removed = false;
        this.changeDimension(player, i, serverworld, serverworld1);
        this.onChangedDimension(player, serverworld);
        player.networkHandler.teleport(player.x, player.y, player.z, player.yaw, player.pitch);
        player.interactionManager.setWorld(serverworld1);
        this.sendWorldInfo(player, serverworld1);
        this.sendPlayerInfo(player);

        for (StatusEffectInstance statuseffectinstance : player.getStatusEffects()) {
            player.networkHandler.sendPacket(new EntityStatusEffectS2CPacket(player.getNetworkId(), statuseffectinstance));
        }
    }

    public void changeDimension(Entity entity, int dimension, ServerWorld fromWorld, ServerWorld toWorld) {
        double d0 = entity.x;
        double d1 = entity.z;
        double d2 = 8.0;
        float f = entity.yaw;
        fromWorld.profiler.push("moving");
        if (entity.dimension == -1) {
            d0 = MathHelper.clamp(d0 / d2, toWorld.getWorldBorder().getMinX() + 16.0, toWorld.getWorldBorder().getMaxX() - 16.0);
            d1 = MathHelper.clamp(d1 / d2, toWorld.getWorldBorder().getMinZ() + 16.0, toWorld.getWorldBorder().getMaxZ() - 16.0);
            entity.setPositionAndAngles(d0, entity.y, d1, entity.yaw, entity.pitch);
            if (entity.isAlive()) {
                fromWorld.tickEntity(entity, false);
            }
        } else if (entity.dimension == 0) {
            d0 = MathHelper.clamp(d0 * d2, toWorld.getWorldBorder().getMinX() + 16.0, toWorld.getWorldBorder().getMaxX() - 16.0);
            d1 = MathHelper.clamp(d1 * d2, toWorld.getWorldBorder().getMinZ() + 16.0, toWorld.getWorldBorder().getMaxZ() - 16.0);
            entity.setPositionAndAngles(d0, entity.y, d1, entity.yaw, entity.pitch);
            if (entity.isAlive()) {
                fromWorld.tickEntity(entity, false);
            }
        } else {
            BlockPos blockpos;
            if (dimension == 1) {
                blockpos = toWorld.getSpawnPoint();
            } else {
                blockpos = toWorld.getForcedSpawnPoint();
            }

            d0 = blockpos.getX();
            entity.y = blockpos.getY();
            d1 = blockpos.getZ();
            entity.setPositionAndAngles(d0, entity.y, d1, 90.0F, 0.0F);
            if (entity.isAlive()) {
                fromWorld.tickEntity(entity, false);
            }
        }

        fromWorld.profiler.pop();
        if (dimension != 1) {
            fromWorld.profiler.push("placing");
            d0 = MathHelper.clamp((int)d0, -29999872, 29999872);
            d1 = MathHelper.clamp((int)d1, -29999872, 29999872);
            if (entity.isAlive()) {
                entity.setPositionAndAngles(d0, entity.y, d1, entity.yaw, entity.pitch);
                toWorld.getPortalForcer().onDimensionChanged(entity, f);
                toWorld.addEntity(entity);
                toWorld.tickEntity(entity, false);
            }

            fromWorld.profiler.pop();
        }

        entity.setWorld(toWorld);
    }

    public void tick() {
        if (++this.pingUpdateTime > 600) {
            this.sendPacket(new PlayerInfoS2CPacket(PlayerInfoS2CPacket.Action.UPDATE_PING, this.players));
            this.pingUpdateTime = 0;
        }
    }

    public void sendPacket(Packet packet) {
        for (int i = 0; i < this.players.size(); i++) {
            this.players.get(i).networkHandler.sendPacket(packet);
        }
    }

    public void sendPacket(Packet packet, int dimension) {
        for (int i = 0; i < this.players.size(); i++) {
            ServerPlayerEntity serverplayerentity = this.players.get(i);
            if (serverplayerentity.dimension == dimension) {
                serverplayerentity.networkHandler.sendPacket(packet);
            }
        }
    }

    public void sendMessageToTeamMembers(PlayerEntity source, Text message) {
        AbstractTeam abstractteam = source.getScoreboardTeam();
        if (abstractteam != null) {
            for (String s : abstractteam.getMembers()) {
                ServerPlayerEntity serverplayerentity = this.get(s);
                if (serverplayerentity != null && serverplayerentity != source) {
                    serverplayerentity.sendMessage(message);
                }
            }
        }
    }

    public void sendMessageToNonTeamMembers(PlayerEntity source, Text message) {
        AbstractTeam abstractteam = source.getScoreboardTeam();
        if (abstractteam == null) {
            this.sendSystemMessage(message);
        } else {
            for (int i = 0; i < this.players.size(); i++) {
                ServerPlayerEntity serverplayerentity = this.players.get(i);
                if (serverplayerentity.getScoreboardTeam() != abstractteam) {
                    serverplayerentity.sendMessage(message);
                }
            }
        }
    }

    public String listNames(boolean addScoreboardNames) {
        String s = "";
        List<ServerPlayerEntity> list = Lists.newArrayList(this.players);

        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                s = s + ", ";
            }

            s = s + list.get(i).getName();
            if (addScoreboardNames) {
                s = s + " (" + list.get(i).getUuid().toString() + ")";
            }
        }

        return s;
    }

    public String[] getNames() {
        String[] astring = new String[this.players.size()];

        for (int i = 0; i < this.players.size(); i++) {
            astring[i] = this.players.get(i).getName();
        }

        return astring;
    }

    public GameProfile[] getProfiles() {
        GameProfile[] agameprofile = new GameProfile[this.players.size()];

        for (int i = 0; i < this.players.size(); i++) {
            agameprofile[i] = this.players.get(i).getGameProfile();
        }

        return agameprofile;
    }

    public PlayerBans getBans() {
        return this.bans;
    }

    public IpBans getIpBans() {
        return this.ipBans;
    }

    public void addOp(GameProfile profile) {
        this.ops.add(new OpEntry(profile, this.server.getOpPermissionLevel(), this.ops.bypassesPlayerLimit(profile)));
    }

    public void removeOp(GameProfile profile) {
        this.ops.remove(profile);
    }

    public boolean isWhitelisted(GameProfile profile) {
        return !this.enforceWhitelist || this.ops.contains(profile) || this.whitelist.contains(profile);
    }

    public boolean isOp(GameProfile profile) {
        return this.ops.contains(profile)
            || this.server.isSingleplayer() && this.server.worlds[0].getData().allowCommands() && this.server.getUsername().equalsIgnoreCase(profile.getName())
            || this.allowCommands;
    }

    public ServerPlayerEntity get(String name) {
        for (ServerPlayerEntity serverplayerentity : this.players) {
            if (serverplayerentity.getName().equalsIgnoreCase(name)) {
                return serverplayerentity;
            }
        }

        return null;
    }

    public void sendPacket(double x, double y, double z, double range, int dimension, Packet packet) {
        this.sendPacket(null, x, y, z, range, dimension, packet);
    }

    public void sendPacket(PlayerEntity source, double x, double y, double z, double range, int dimension, Packet packet) {
        for (int i = 0; i < this.players.size(); i++) {
            ServerPlayerEntity serverplayerentity = this.players.get(i);
            if (serverplayerentity != source && serverplayerentity.dimension == dimension) {
                double d0 = x - serverplayerentity.x;
                double d1 = y - serverplayerentity.y;
                double d2 = z - serverplayerentity.z;
                if (d0 * d0 + d1 * d1 + d2 * d2 < range * range) {
                    serverplayerentity.networkHandler.sendPacket(packet);
                }
            }
        }
    }

    public void saveAll() {
        for (int i = 0; i < this.players.size(); i++) {
            this.save(this.players.get(i));
        }
    }

    public void addToWhitelist(GameProfile profile) {
        this.whitelist.add(new WhitelistEntry(profile));
    }

    public void removeFromWhitelist(GameProfile profile) {
        this.whitelist.remove(profile);
    }

    public Whitelist getWhitelist() {
        return this.whitelist;
    }

    public String[] getWhitelistNames() {
        return this.whitelist.getNames();
    }

    public Ops getOps() {
        return this.ops;
    }

    public String[] getOpNames() {
        return this.ops.getNames();
    }

    public void reloadWhitelist() {
    }

    public void sendWorldInfo(ServerPlayerEntity player, ServerWorld world) {
        WorldBorder worldborder = this.server.worlds[0].getWorldBorder();
        player.networkHandler.sendPacket(new WorldBorderS2CPacket(worldborder, WorldBorderS2CPacket.Type.INITIALIZE));
        player.networkHandler.sendPacket(new WorldTimeS2CPacket(world.getTime(), world.getTimeOfDay(), world.getGameRules().getBoolean("doDaylightCycle")));
        if (world.isRaining()) {
            player.networkHandler.sendPacket(new GameEventS2CPacket(1, 0.0F));
            player.networkHandler.sendPacket(new GameEventS2CPacket(7, world.getRain(1.0F)));
            player.networkHandler.sendPacket(new GameEventS2CPacket(8, world.getThunder(1.0F)));
        }
    }

    public void sendPlayerInfo(ServerPlayerEntity player) {
        player.setMenu(player.playerMenu);
        player.markHealthDirty();
        player.networkHandler.sendPacket(new SelectSlotS2CPacket(player.inventory.selectedSlot));
    }

    public int getCount() {
        return this.players.size();
    }

    public int getMaxCount() {
        return this.maxPlayerCount;
    }

    public String[] getSavedIds() {
        return this.server.worlds[0].getStorage().getPlayerDataStorage().getSavedPlayerIds();
    }

    public void setEnforceWhitelist(boolean enforce) {
        this.enforceWhitelist = enforce;
    }

    public List<ServerPlayerEntity> getAtIp(String ip) {
        List<ServerPlayerEntity> list = Lists.newArrayList();

        for (ServerPlayerEntity serverplayerentity : this.players) {
            if (serverplayerentity.getIp().equals(ip)) {
                list.add(serverplayerentity);
            }
        }

        return list;
    }

    public int getChunkViewDistance() {
        return this.viewDistance;
    }

    public MinecraftServer getServer() {
        return this.server;
    }

    public NbtCompound getSingleplayerData() {
        return null;
    }

    public void setDefaultGameMode(WorldSettings.GameMode gameMode) {
        this.defaultGameMode = gameMode;
    }

    private void copyGameMode(ServerPlayerEntity player, ServerPlayerEntity fromPlayer, World world) {
        if (fromPlayer != null) {
            player.interactionManager.setGameMode(fromPlayer.interactionManager.getGameMode());
        } else if (this.defaultGameMode != null) {
            player.interactionManager.setGameMode(this.defaultGameMode);
        }

        player.interactionManager.setGameModeIfNotSet(world.getData().getDefaultGamemode());
    }

    public void setAllowCommands(boolean allowCommands) {
        this.allowCommands = allowCommands;
    }

    public void onServerClosed() {
        for (int i = 0; i < this.players.size(); i++) {
            this.players.get(i).networkHandler.disconnect("Server closed");
        }
    }

    public void sendMessage(Text message, boolean system) {
        this.server.sendMessage(message);
        byte b0 = (byte)(system ? 1 : 0);
        this.sendPacket(new ChatMessageS2CPacket(message, b0));
    }

    public void sendSystemMessage(Text message) {
        this.sendMessage(message, true);
    }

    public ServerPlayerStats getStats(PlayerEntity player) {
        UUID uuid = player.getUuid();
        ServerPlayerStats serverplayerstats = uuid == null ? null : this.stats.get(uuid);
        if (serverplayerstats == null) {
            File file1 = new File(this.server.getWorld(0).getStorage().getDirectory(), "stats");
            File file2 = new File(file1, uuid.toString() + ".json");
            if (!file2.exists()) {
                File file3 = new File(file1, player.getName() + ".json");
                if (file3.exists() && file3.isFile()) {
                    file3.renameTo(file2);
                }
            }

            serverplayerstats = new ServerPlayerStats(this.server, file2);
            serverplayerstats.load();
            this.stats.put(uuid, serverplayerstats);
        }

        return serverplayerstats;
    }

    public void updateViewDistance(int viewDistance) {
        this.viewDistance = viewDistance;
        if (this.server.worlds != null) {
            for (ServerWorld serverworld : this.server.worlds) {
                if (serverworld != null) {
                    serverworld.getChunkMap().updateViewDistance(viewDistance);
                }
            }
        }
    }

    public List<ServerPlayerEntity> getAll() {
        return this.players;
    }

    public ServerPlayerEntity get(UUID uuid) {
        return this.playersByUuid.get(uuid);
    }

    public boolean canBypassPlayerLimit(GameProfile profile) {
        return false;
    }
}
