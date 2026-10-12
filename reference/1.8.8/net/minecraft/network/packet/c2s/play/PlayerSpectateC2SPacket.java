package net.minecraft.network.packet.c2s.play;

import java.io.IOException;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.handler.ServerPlayPacketHandler;
import net.minecraft.server.world.ServerWorld;

public class PlayerSpectateC2SPacket implements Packet<ServerPlayPacketHandler> {
    private UUID targetUuid;

    public PlayerSpectateC2SPacket() {
    }

    public PlayerSpectateC2SPacket(UUID targetUuid) {
        this.targetUuid = targetUuid;
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.targetUuid = buffer.readUuid();
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeUuid(this.targetUuid);
    }

    public void handle(ServerPlayPacketHandler serverPlayPacketHandler) {
        serverPlayPacketHandler.handlePlayerSpectate(this);
    }

    public Entity getSpectateTarget(ServerWorld world) {
        return world.getEntity(this.targetUuid);
    }
}
