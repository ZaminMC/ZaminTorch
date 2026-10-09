package net.minecraft.network.packet.s2c.play;

import java.io.IOException;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;

public class CloseInventoryMenuS2CPacket implements Packet<ClientPlayPacketHandler> {
    private int id;

    public CloseInventoryMenuS2CPacket() {
    }

    public CloseInventoryMenuS2CPacket(int id) {
        this.id = id;
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handleCloseInventoryMenu(this);
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.id = buffer.readUnsignedByte();
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeByte(this.id);
    }
}
