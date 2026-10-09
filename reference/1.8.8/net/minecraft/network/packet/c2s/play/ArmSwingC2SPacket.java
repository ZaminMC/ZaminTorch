package net.minecraft.network.packet.c2s.play;

import java.io.IOException;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.handler.ServerPlayPacketHandler;

public class ArmSwingC2SPacket implements Packet<ServerPlayPacketHandler> {
    @Override
    public void read(PacketByteBuf buffer) throws IOException {
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
    }

    public void handle(ServerPlayPacketHandler serverPlayPacketHandler) {
        serverPlayPacketHandler.handleArmSwing(this);
    }
}
