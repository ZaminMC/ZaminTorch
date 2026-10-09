package net.minecraft.network.packet.s2c.play;

import java.io.IOException;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.world.World;

public class CameraS2CPacket implements Packet<ClientPlayPacketHandler> {
    public int id;

    public CameraS2CPacket() {
    }

    public CameraS2CPacket(Entity camera) {
        this.id = camera.getNetworkId();
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.id = buffer.readVarInt();
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeVarInt(this.id);
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handleCamera(this);
    }

    public Entity getCamera(World world) {
        return world.getEntity(this.id);
    }
}
