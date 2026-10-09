package net.minecraft.network.packet.s2c.query;

import java.io.IOException;
import net.minecraft.client.network.handler.ClientQueryPacketHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;

public class PingS2CPacket implements Packet<ClientQueryPacketHandler> {
    private long time;

    public PingS2CPacket() {
    }

    public PingS2CPacket(long time) {
        this.time = time;
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.time = buffer.readLong();
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeLong(this.time);
    }

    public void handle(ClientQueryPacketHandler clientQueryPacketHandler) {
        clientQueryPacketHandler.handlePing(this);
    }
}
