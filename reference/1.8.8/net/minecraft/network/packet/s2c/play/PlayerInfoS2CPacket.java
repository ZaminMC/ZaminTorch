package net.minecraft.network.packet.s2c.play;

import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import java.io.IOException;
import java.util.List;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.entity.living.player.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.WorldSettings;

public class PlayerInfoS2CPacket implements Packet<ClientPlayPacketHandler> {
    private PlayerInfoS2CPacket.Action action;
    private final List<PlayerInfoS2CPacket.Entry> entries = Lists.newArrayList();

    public PlayerInfoS2CPacket() {
    }

    public PlayerInfoS2CPacket(PlayerInfoS2CPacket.Action action, ServerPlayerEntity... players) {
        this.action = action;

        for (ServerPlayerEntity serverplayerentity : players) {
            this.entries
                .add(
                    new PlayerInfoS2CPacket.Entry(
                        serverplayerentity.getGameProfile(),
                        serverplayerentity.ping,
                        serverplayerentity.interactionManager.getGameMode(),
                        serverplayerentity.getPlayerListName()
                    )
                );
        }
    }

    public PlayerInfoS2CPacket(PlayerInfoS2CPacket.Action action, Iterable<ServerPlayerEntity> players) {
        this.action = action;

        for (ServerPlayerEntity serverplayerentity : players) {
            this.entries
                .add(
                    new PlayerInfoS2CPacket.Entry(
                        serverplayerentity.getGameProfile(),
                        serverplayerentity.ping,
                        serverplayerentity.interactionManager.getGameMode(),
                        serverplayerentity.getPlayerListName()
                    )
                );
        }
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.action = buffer.readEnum(PlayerInfoS2CPacket.Action.class);
        int i = buffer.readVarInt();

        for (int j = 0; j < i; j++) {
            GameProfile gameprofile = null;
            int k = 0;
            WorldSettings.GameMode worldsettings$gamemode = null;
            Text text = null;
            switch (this.action) {
                case ADD_PLAYER:
                    gameprofile = new GameProfile(buffer.readUuid(), buffer.readString(16));
                    int l = buffer.readVarInt();
                    int i1 = 0;

                    for (; i1 < l; i1++) {
                        String s = buffer.readString(32767);
                        String s1 = buffer.readString(32767);
                        if (buffer.readBoolean()) {
                            gameprofile.getProperties().put(s, new Property(s, s1, buffer.readString(32767)));
                        } else {
                            gameprofile.getProperties().put(s, new Property(s, s1));
                        }
                    }

                    worldsettings$gamemode = WorldSettings.GameMode.byId(buffer.readVarInt());
                    k = buffer.readVarInt();
                    if (buffer.readBoolean()) {
                        text = buffer.readText();
                    }
                    break;
                case UPDATE_GAME_MODE:
                    gameprofile = new GameProfile(buffer.readUuid(), null);
                    worldsettings$gamemode = WorldSettings.GameMode.byId(buffer.readVarInt());
                    break;
                case UPDATE_PING:
                    gameprofile = new GameProfile(buffer.readUuid(), null);
                    k = buffer.readVarInt();
                    break;
                case UPDATE_DISPLAY_NAME:
                    gameprofile = new GameProfile(buffer.readUuid(), null);
                    if (buffer.readBoolean()) {
                        text = buffer.readText();
                    }
                    break;
                case REMOVE_PLAYER:
                    gameprofile = new GameProfile(buffer.readUuid(), null);
            }

            this.entries.add(new PlayerInfoS2CPacket.Entry(gameprofile, k, worldsettings$gamemode, text));
        }
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeEnum(this.action);
        buffer.writeVarInt(this.entries.size());

        for (PlayerInfoS2CPacket.Entry playerinfos2cpacket$entry : this.entries) {
            switch (this.action) {
                case ADD_PLAYER:
                    buffer.writeUuid(playerinfos2cpacket$entry.getProfile().getId());
                    buffer.writeString(playerinfos2cpacket$entry.getProfile().getName());
                    buffer.writeVarInt(playerinfos2cpacket$entry.getProfile().getProperties().size());

                    for (Property property : playerinfos2cpacket$entry.getProfile().getProperties().values()) {
                        buffer.writeString(property.getName());
                        buffer.writeString(property.getValue());
                        if (property.hasSignature()) {
                            buffer.writeBoolean(true);
                            buffer.writeString(property.getSignature());
                        } else {
                            buffer.writeBoolean(false);
                        }
                    }

                    buffer.writeVarInt(playerinfos2cpacket$entry.getGameMode().getId());
                    buffer.writeVarInt(playerinfos2cpacket$entry.getPing());
                    if (playerinfos2cpacket$entry.getDisplayName() == null) {
                        buffer.writeBoolean(false);
                    } else {
                        buffer.writeBoolean(true);
                        buffer.writeText(playerinfos2cpacket$entry.getDisplayName());
                    }
                    break;
                case UPDATE_GAME_MODE:
                    buffer.writeUuid(playerinfos2cpacket$entry.getProfile().getId());
                    buffer.writeVarInt(playerinfos2cpacket$entry.getGameMode().getId());
                    break;
                case UPDATE_PING:
                    buffer.writeUuid(playerinfos2cpacket$entry.getProfile().getId());
                    buffer.writeVarInt(playerinfos2cpacket$entry.getPing());
                    break;
                case UPDATE_DISPLAY_NAME:
                    buffer.writeUuid(playerinfos2cpacket$entry.getProfile().getId());
                    if (playerinfos2cpacket$entry.getDisplayName() == null) {
                        buffer.writeBoolean(false);
                    } else {
                        buffer.writeBoolean(true);
                        buffer.writeText(playerinfos2cpacket$entry.getDisplayName());
                    }
                    break;
                case REMOVE_PLAYER:
                    buffer.writeUuid(playerinfos2cpacket$entry.getProfile().getId());
            }
        }
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handlePlayerInfo(this);
    }

    public List<PlayerInfoS2CPacket.Entry> getEntries() {
        return this.entries;
    }

    public PlayerInfoS2CPacket.Action getAction() {
        return this.action;
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this).add("action", this.action).add("entries", this.entries).toString();
    }

    public enum Action {
        ADD_PLAYER,
        UPDATE_GAME_MODE,
        UPDATE_PING,
        UPDATE_DISPLAY_NAME,
        REMOVE_PLAYER;
    }

    public class Entry {
        private final int ping;
        private final WorldSettings.GameMode gameMode;
        private final GameProfile profile;
        private final Text displayName;

        public Entry(GameProfile profile, int ping, WorldSettings.GameMode gameMode, Text displayName) {
            this.profile = profile;
            this.ping = ping;
            this.gameMode = gameMode;
            this.displayName = displayName;
        }

        public GameProfile getProfile() {
            return this.profile;
        }

        public int getPing() {
            return this.ping;
        }

        public WorldSettings.GameMode getGameMode() {
            return this.gameMode;
        }

        public Text getDisplayName() {
            return this.displayName;
        }

        @Override
        public String toString() {
            return Objects.toStringHelper(this)
                .add("latency", this.ping)
                .add("gameMode", this.gameMode)
                .add("profile", this.profile)
                .add("displayName", this.displayName == null ? null : Text.Serializer.toJson(this.displayName))
                .toString();
        }
    }
}
