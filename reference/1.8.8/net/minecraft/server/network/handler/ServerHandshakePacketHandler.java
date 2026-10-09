package net.minecraft.server.network.handler;

import net.minecraft.network.handler.PacketHandler;
import net.minecraft.network.packet.c2s.handshake.HandshakeC2SPacket;

public interface ServerHandshakePacketHandler extends PacketHandler {
    void handleHandshake(HandshakeC2SPacket packet);
}
