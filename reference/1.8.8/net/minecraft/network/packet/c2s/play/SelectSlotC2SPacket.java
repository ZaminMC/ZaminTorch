package net.minecraft.network.packet.c2s.play;

import java.io.IOException;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.handler.ServerPlayPacketHandler;

public class SelectSlotC2SPacket implements Packet<ServerPlayPacketHandler> {
    private int slot;

    public SelectSlotC2SPacket() {
    }

    public SelectSlotC2SPacket(int slot) {
        this.slot = slot;
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.slot = buffer.readShort();
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeShort(this.slot);
    }

    public void handle(ServerPlayPacketHandler serverPlayPacketHandler) {
        serverPlayPacketHandler.handleSelectSlot(this);
    }

    public int getSlot() {
        return this.slot;
    }
}
