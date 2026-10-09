package net.minecraft.network.packet.c2s.play;

import java.io.IOException;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.handler.ServerPlayPacketHandler;

public class CloseInventoryMenuC2SPacket implements Packet<ServerPlayPacketHandler> {
    private int id;

    public CloseInventoryMenuC2SPacket() {
    }

    public CloseInventoryMenuC2SPacket(int id) {
        this.id = id;
    }

    public void handle(ServerPlayPacketHandler serverPlayPacketHandler) {
        serverPlayPacketHandler.handleCloseInventoryMenu(this);
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.id = buffer.readByte();
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeByte(this.id);
    }
}
