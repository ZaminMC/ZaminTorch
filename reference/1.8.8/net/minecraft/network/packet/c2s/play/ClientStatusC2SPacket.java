package net.minecraft.network.packet.c2s.play;

import java.io.IOException;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.handler.ServerPlayPacketHandler;

public class ClientStatusC2SPacket implements Packet<ServerPlayPacketHandler> {
    private ClientStatusC2SPacket.Status status;

    public ClientStatusC2SPacket() {
    }

    public ClientStatusC2SPacket(ClientStatusC2SPacket.Status status) {
        this.status = status;
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.status = buffer.readEnum(ClientStatusC2SPacket.Status.class);
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeEnum(this.status);
    }

    public void handle(ServerPlayPacketHandler serverPlayPacketHandler) {
        serverPlayPacketHandler.handleClientStatus(this);
    }

    public ClientStatusC2SPacket.Status getStatus() {
        return this.status;
    }

    public enum Status {
        PERFORM_RESPAWN,
        REQUEST_STATS,
        OPEN_INVENTORY_ACHIEVEMENT;
    }
}
