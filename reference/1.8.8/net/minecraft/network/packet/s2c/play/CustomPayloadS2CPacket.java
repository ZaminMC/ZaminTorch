package net.minecraft.network.packet.s2c.play;

import java.io.IOException;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;

public class CustomPayloadS2CPacket implements Packet<ClientPlayPacketHandler> {
    private String channel;
    private PacketByteBuf data;

    public CustomPayloadS2CPacket() {
    }

    public CustomPayloadS2CPacket(String channel, PacketByteBuf data) {
        this.channel = channel;
        this.data = data;
        if (data.writerIndex() > 1048576) {
            throw new IllegalArgumentException("Payload may not be larger than 1048576 bytes");
        }
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.channel = buffer.readString(20);
        int i = buffer.readableBytes();
        if (i >= 0 && i <= 1048576) {
            this.data = new PacketByteBuf(buffer.readBytes(i));
        } else {
            throw new IOException("Payload may not be larger than 1048576 bytes");
        }
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeString(this.channel);
        buffer.writeBytes(this.data);
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handleCustomPayload(this);
    }

    public String getChannel() {
        return this.channel;
    }

    public PacketByteBuf getData() {
        return this.data;
    }
}
