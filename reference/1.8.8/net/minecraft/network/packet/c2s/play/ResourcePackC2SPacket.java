package net.minecraft.network.packet.c2s.play;

import java.io.IOException;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.handler.ServerPlayPacketHandler;

public class ResourcePackC2SPacket implements Packet<ServerPlayPacketHandler> {
    private String hash;
    private ResourcePackC2SPacket.Response response;

    public ResourcePackC2SPacket() {
    }

    public ResourcePackC2SPacket(String hash, ResourcePackC2SPacket.Response response) {
        if (hash.length() > 40) {
            hash = hash.substring(0, 40);
        }

        this.hash = hash;
        this.response = response;
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.hash = buffer.readString(40);
        this.response = buffer.readEnum(ResourcePackC2SPacket.Response.class);
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeString(this.hash);
        buffer.writeEnum(this.response);
    }

    public void handle(ServerPlayPacketHandler serverPlayPacketHandler) {
        serverPlayPacketHandler.handleResourcePackResponse(this);
    }

    public enum Response {
        SUCCESSFULLY_LOADED,
        DECLINED,
        FAILED_DOWNLOAD,
        ACCEPTED;
    }
}
