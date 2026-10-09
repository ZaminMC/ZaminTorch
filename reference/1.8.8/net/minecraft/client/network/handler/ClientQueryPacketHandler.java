package net.minecraft.client.network.handler;

import net.minecraft.network.handler.PacketHandler;
import net.minecraft.network.packet.s2c.query.PingS2CPacket;
import net.minecraft.network.packet.s2c.query.ServerStatusS2CPacket;

public interface ClientQueryPacketHandler extends PacketHandler {
    void handleServerStatus(ServerStatusS2CPacket packet);

    void handlePing(PingS2CPacket packet);
}
