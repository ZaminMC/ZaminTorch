package net.minecraft.network.packet.c2s.query;

import java.io.IOException;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.handler.ServerQueryPacketHandler;

public class PingC2SPacket implements Packet<ServerQueryPacketHandler> {
    private long time;

    public PingC2SPacket() {
    }

    public PingC2SPacket(long time) {
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

    public void handle(ServerQueryPacketHandler serverQueryPacketHandler) {
        serverQueryPacketHandler.handlePing(this);
    }

    public long getTime() {
        return this.time;
    }
}
