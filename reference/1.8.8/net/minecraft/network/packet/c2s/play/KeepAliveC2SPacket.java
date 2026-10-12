package net.minecraft.network.packet.c2s.play;

import java.io.IOException;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.handler.ServerPlayPacketHandler;

public class KeepAliveC2SPacket implements Packet<ServerPlayPacketHandler> {
    private int timeMillis;

    public KeepAliveC2SPacket() {
    }

    public KeepAliveC2SPacket(int timeMillis) {
        this.timeMillis = timeMillis;
    }

    public void handle(ServerPlayPacketHandler serverPlayPacketHandler) {
        serverPlayPacketHandler.handleKeepAlive(this);
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
