package net.minecraft.server.network.handler;

import net.minecraft.network.handler.PacketHandler;
import net.minecraft.network.packet.c2s.login.HelloC2SPacket;
import net.minecraft.network.packet.c2s.login.KeyC2SPacket;

public interface ServerLoginPacketHandler extends PacketHandler {
    void handleHello(HelloC2SPacket packet);

    void handleKey(KeyC2SPacket packet);
}
