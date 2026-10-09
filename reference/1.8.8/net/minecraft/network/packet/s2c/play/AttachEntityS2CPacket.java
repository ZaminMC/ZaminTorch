package net.minecraft.network.packet.s2c.play;

import java.io.IOException;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;

public class AttachEntityS2CPacket implements Packet<ClientPlayPacketHandler> {
    /**
     * The type of attachment. The possible values are:
     * <br>0: mounting on e.g. a minecart, boat, or horse
     * <br>1: attaching a leash on an entity
     */
    private int type;
    private int id;
    private int attachedId;

    public AttachEntityS2CPacket() {
    }

    public AttachEntityS2CPacket(int type, Entity entity, Entity attached) {
        this.type = type;
        this.id = entity.getNetworkId();
        this.attachedId = attached != null ? attached.getNetworkId() : -1;
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.id = buffer.readInt();
        this.attachedId = buffer.readInt();
        this.type = buffer.readUnsignedByte();
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeInt(this.id);
        buffer.writeInt(this.attachedId);
        buffer.writeByte(this.type);
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handleAttachEntity(this);
    }

    public int getType() {
        return this.type;
    }

    public int getId() {
        return this.id;
    }

    public int getAttachedId() {
        return this.attachedId;
    }
}
