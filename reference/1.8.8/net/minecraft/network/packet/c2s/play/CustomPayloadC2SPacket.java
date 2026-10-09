package net.minecraft.network.packet.c2s.play;

import java.io.IOException;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.handler.ServerPlayPacketHandler;

public class CustomPayloadC2SPacket implements Packet<ServerPlayPacketHandler> {
    private String channel;
    private PacketByteBuf data;

    public CustomPayloadC2SPacket() {
    }

    public CustomPayloadC2SPacket(String channel, PacketByteBuf data) {
        this.channel = channel;
        this.data = data;
        if (data.writerIndex() > 32767) {
            throw new IllegalArgumentException("Payload may not be larger than 32767 bytes");
        }
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.channel = buffer.readString(20);
        int i = buffer.readableBytes();
        if (i >= 0 && i <= 32767) {
            this.data = new PacketByteBuf(buffer.readBytes(i));
        } else {
            throw new IOException("Payload may not be larger than 32767 bytes");
        }
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeString(this.channel);
        buffer.writeBytes(this.data);
    }

    public void handle(ServerPlayPacketHandler serverPlayPacketHandler) {
        serverPlayPacketHandler.handleCustomPayload(this);
    }

    public String getChannel() {
        return this.channel;
    }

    public PacketByteBuf getData() {
        return this.data;
    }
}
