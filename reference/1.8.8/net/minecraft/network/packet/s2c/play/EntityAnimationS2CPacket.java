package net.minecraft.network.packet.s2c.play;

import java.io.IOException;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;

public class EntityAnimationS2CPacket implements Packet<ClientPlayPacketHandler> {
    private int id;
    private int action;

    public EntityAnimationS2CPacket() {
    }

    public EntityAnimationS2CPacket(Entity entity, int action) {
        this.id = entity.getNetworkId();
        this.action = action;
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.id = buffer.readVarInt();
        this.action = buffer.readUnsignedByte();
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeVarInt(this.id);
        buffer.writeByte(this.action);
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handleEntityAnimation(this);
    }

    public int getId() {
        return this.id;
    }

    public int getAction() {
        return this.action;
    }
}
