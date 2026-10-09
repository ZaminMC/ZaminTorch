package net.minecraft.network.packet.s2c.play;

import java.io.IOException;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;

public class SelectSlotS2CPacket implements Packet<ClientPlayPacketHandler> {
    private int slot;

    public SelectSlotS2CPacket() {
    }

    public SelectSlotS2CPacket(int slot) {
        this.slot = slot;
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.slot = buffer.readByte();
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeByte(this.slot);
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handleSelectSlot(this);
    }

    public int getSlot() {
        return this.slot;
    }
}
