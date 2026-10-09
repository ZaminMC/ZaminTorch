package net.minecraft.network.packet.s2c.login;

import java.io.IOException;
import net.minecraft.client.network.handler.ClientLoginPacketHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;

public class CompressionThresholdS2CPacket implements Packet<ClientLoginPacketHandler> {
    private int compressionThreshold;

    public CompressionThresholdS2CPacket() {
    }

    public CompressionThresholdS2CPacket(int compressionThreshold) {
        this.compressionThreshold = compressionThreshold;
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.compressionThreshold = buffer.readVarInt();
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeVarInt(this.compressionThreshold);
    }

    public void handle(ClientLoginPacketHandler clientLoginPacketHandler) {
        clientLoginPacketHandler.handleCompressionThreshold(this);
    }

    public int getCompressionThreshold() {
        return this.compressionThreshold;
    }
}
