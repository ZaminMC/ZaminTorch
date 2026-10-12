package net.minecraft.network.packet.s2c.play;

import java.io.IOException;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;

public class KeepAliveS2CPacket implements Packet<ClientPlayPacketHandler> {
    private int timeMillis;

    public KeepAliveS2CPacket() {
    }

    public KeepAliveS2CPacket(int timeMillis) {
        this.timeMillis = timeMillis;
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handleKeepAlive(this);
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.timeMillis = buffer.readVarInt();
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeVarInt(this.timeMillis);
    }

    public int getTimeMillis() {
        return this.timeMillis;
    }
}
