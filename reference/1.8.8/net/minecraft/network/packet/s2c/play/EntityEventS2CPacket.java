package net.minecraft.network.packet.s2c.play;

import java.io.IOException;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.world.World;

public class EntityEventS2CPacket implements Packet<ClientPlayPacketHandler> {
    private int id;
    private byte event;

    public EntityEventS2CPacket() {
    }

    public EntityEventS2CPacket(Entity entity, byte event) {
        this.id = entity.getNetworkId();
        this.event = event;
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.id = buffer.readInt();
        this.event = buffer.readByte();
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeInt(this.id);
        buffer.writeByte(this.event);
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handleEntityEvent(this);
    }

    public Entity getEntity(World world) {
        return world.getEntity(this.id);
    }

    public byte getEvent() {
        return this.event;
    }
}
