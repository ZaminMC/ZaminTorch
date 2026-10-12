package net.minecraft.server.network.handler;

import net.minecraft.network.handler.PacketHandler;
import net.minecraft.network.packet.c2s.query.PingC2SPacket;
import net.minecraft.network.packet.c2s.query.ServerStatusC2SPacket;

public interface ServerQueryPacketHandler extends PacketHandler {
    void handlePing(PingC2SPacket packet);

    void handleServerStatus(ServerStatusC2SPacket packet);
}
