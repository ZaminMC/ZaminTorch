package net.minecraft.network.packet.c2s.query;

import java.io.IOException;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.handler.ServerQueryPacketHandler;

public class ServerStatusC2SPacket implements Packet<ServerQueryPacketHandler> {
    @Override
    public void read(PacketByteBuf buffer) throws IOException {
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
    }

    public void handle(ServerQueryPacketHandler serverQueryPacketHandler) {
        serverQueryPacketHandler.handleServerStatus(this);
    }
}
