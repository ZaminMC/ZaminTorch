package net.minecraft.network.packet.s2c.play;

import java.io.IOException;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;

public class ResourcePackS2CPacket implements Packet<ClientPlayPacketHandler> {
    private String url;
    private String hash;

    public ResourcePackS2CPacket() {
    }

    public ResourcePackS2CPacket(String url, String hash) {
        this.url = url;
        this.hash = hash;
        if (hash.length() > 40) {
            throw new IllegalArgumentException("Hash is too long (max 40, was " + hash.length() + ")");
        }
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.url = buffer.readString(32767);
        this.hash = buffer.readString(40);
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeString(this.url);
        buffer.writeString(this.hash);
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handleResourcePack(this);
    }

    public String getUrl() {
        return this.url;
    }

    public String getHash() {
        return this.hash;
    }
}
