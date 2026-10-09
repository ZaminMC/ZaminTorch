package net.minecraft.network.packet.s2c.play;

import java.io.IOException;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.world.World;

public class EntityHeadAnglesS2CPacket implements Packet<ClientPlayPacketHandler> {
    private int id;
    private byte headYaw;

    public EntityHeadAnglesS2CPacket() {
    }

    public EntityHeadAnglesS2CPacket(Entity entity, byte headYaw) {
        this.id = entity.getNetworkId();
        this.headYaw = headYaw;
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.id = buffer.readVarInt();
        this.headYaw = buffer.readByte();
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeVarInt(this.id);
        buffer.writeByte(this.headYaw);
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handleEntityHeadAngles(this);
    }

    public Entity getEntity(World world) {
        return world.getEntity(this.id);
    }

    public byte getHeadYaw() {
        return this.headYaw;
    }
}
