package net.minecraft.network.packet.s2c.play;

import com.google.common.collect.Lists;
import java.io.IOException;
import java.util.Collection;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.scoreboard.team.AbstractTeam;
import net.minecraft.scoreboard.team.Team;

public class TeamS2CPacket implements Packet<ClientPlayPacketHandler> {
    private String name = "";
    private String displayName = "";
    private String prefix = "";
    private String suffix = "";
    private String nameTagVisibility = AbstractTeam.Visibility.ALWAYS.key;
    private int color = -1;
    private Collection<String> members = Lists.newArrayList();
    private int action;
    private int flags;

    public TeamS2CPacket() {
    }

    public TeamS2CPacket(Team team, int action) {
        this.name = team.getName();
        this.action = action;
        if (action == 0 || action == 2) {
            this.displayName = team.getDisplayName();
            this.prefix = team.getPrefix();
            this.suffix = team.getSuffix();
            this.flags = team.packFriendlyFlags();
            this.nameTagVisibility = team.getNameTagVisibility().key;
            this.color = team.getColor().getId();
        }

        if (action == 0) {
            this.members.addAll(team.getMembers());
        }
    }

    public TeamS2CPacket(Team team, Collection<String> members, int action) {
        if (action != 3 && action != 4) {
            throw new IllegalArgumentException("Method must be join or leave for player constructor");
        }

        if (members != null && !members.isEmpty()) {
            this.action = action;
            this.name = team.getName();
            this.members.addAll(members);
        } else {
            throw new IllegalArgumentException("Players cannot be null/empty");
        }
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.name = buffer.readString(16);
        this.action = buffer.readByte();
        if (this.action == 0 || this.action == 2) {
            this.displayName = buffer.readString(32);
            this.prefix = buffer.readString(16);
            this.suffix = buffer.readString(16);
            this.flags = buffer.readByte();
            this.nameTagVisibility = buffer.readString(32);
            this.color = buffer.readByte();
        }

        if (this.action == 0 || this.action == 3 || this.action == 4) {
            int i = buffer.readVarInt();

            for (int j = 0; j < i; j++) {
                this.members.add(buffer.readString(40));
            }
        }
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeString(this.name);
        buffer.writeByte(this.action);
        if (this.action == 0 || this.action == 2) {
            buffer.writeString(this.displayName);
            buffer.writeString(this.prefix);
            buffer.writeString(this.suffix);
            buffer.writeByte(this.flags);
            buffer.writeString(this.nameTagVisibility);
            buffer.writeByte(this.color);
        }

        if (this.action == 0 || this.action == 3 || this.action == 4) {
            buffer.writeVarInt(this.members.size());

            for (String s : this.members) {
                buffer.writeString(s);
            }
        }
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handleTeam(this);
    }

    public String getName() {
        return this.name;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public String getPrefix() {
        return this.prefix;
    }

    public String getSuffix() {
        return this.suffix;
    }

    public Collection<String> getMembers() {
        return this.members;
    }

    public int getAction() {
        return this.action;
    }

    public int getFlags() {
        return this.flags;
    }

    public int getColor() {
        return this.color;
    }

    public String getNameTagVisibility() {
        return this.nameTagVisibility;
    }
}
