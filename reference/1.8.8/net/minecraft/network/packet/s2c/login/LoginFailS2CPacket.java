package net.minecraft.network.packet.s2c.login;

import java.io.IOException;
import net.minecraft.client.network.handler.ClientLoginPacketHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.text.Text;

public class LoginFailS2CPacket implements Packet<ClientLoginPacketHandler> {
    private Text reason;

    public LoginFailS2CPacket() {
    }

    public LoginFailS2CPacket(Text reason) {
        this.reason = reason;
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.reason = buffer.readText();
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeText(this.reason);
    }

    public void handle(ClientLoginPacketHandler clientLoginPacketHandler) {
        clientLoginPacketHandler.handleLoginFail(this);
    }

    public Text getReason() {
        return this.reason;
    }
}
