package net.minecraft.network.packet.s2c.play;

import java.io.IOException;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;

public class CompressionThresholdS2CPacket implements Packet<ClientPlayPacketHandler> {
    private int compressionThreshold;

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.compressionThreshold = buffer.readVarInt();
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeVarInt(this.compressionThreshold);
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handleCompressionThreshold(this);
    }

    public int getCompressionThreshold() {
        return this.compressionThreshold;
    }
}
