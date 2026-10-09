package net.minecraft.network.packet.s2c.play;

import java.io.IOException;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.text.Text;

public class DisconnectS2CPacket implements Packet<ClientPlayPacketHandler> {
    private Text reason;

    public DisconnectS2CPacket() {
    }

    public DisconnectS2CPacket(Text reason) {
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

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handleDisconnect(this);
    }

    public Text getReason() {
        return this.reason;
    }
}
