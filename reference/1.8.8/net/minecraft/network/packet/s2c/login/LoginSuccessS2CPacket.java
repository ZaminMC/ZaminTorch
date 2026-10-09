package net.minecraft.network.packet.s2c.login;

import com.mojang.authlib.GameProfile;
import java.io.IOException;
import java.util.UUID;
import net.minecraft.client.network.handler.ClientLoginPacketHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;

public class LoginSuccessS2CPacket implements Packet<ClientLoginPacketHandler> {
    private GameProfile profile;

    public LoginSuccessS2CPacket() {
    }

    public LoginSuccessS2CPacket(GameProfile profile) {
        this.profile = profile;
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        String s = buffer.readString(36);
        String s1 = buffer.readString(16);
        UUID uuid = UUID.fromString(s);
        this.profile = new GameProfile(uuid, s1);
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        UUID uuid = this.profile.getId();
        buffer.writeString(uuid == null ? "" : uuid.toString());
        buffer.writeString(this.profile.getName());
    }

    public void handle(ClientLoginPacketHandler clientLoginPacketHandler) {
        clientLoginPacketHandler.handleLoginSuccess(this);
    }

    public GameProfile getProfile() {
        return this.profile;
    }
}
