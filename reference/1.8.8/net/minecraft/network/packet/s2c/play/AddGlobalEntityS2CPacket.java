package net.minecraft.network.packet.s2c.play;

import java.io.IOException;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.global.LightningBoltEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.util.math.MathHelper;

public class AddGlobalEntityS2CPacket implements Packet<ClientPlayPacketHandler> {
    private int id;
    private int x;
    private int y;
    private int z;
    private int type;

    public AddGlobalEntityS2CPacket() {
    }

    public AddGlobalEntityS2CPacket(Entity entity) {
        this.id = entity.getNetworkId();
        this.x = MathHelper.floor(entity.x * 32.0);
        this.y = MathHelper.floor(entity.y * 32.0);
        this.z = MathHelper.floor(entity.z * 32.0);
        if (entity instanceof LightningBoltEntity) {
            this.type = 1;
        }
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.id = buffer.readVarInt();
        this.type = buffer.readByte();
        this.x = buffer.readInt();
        this.y = buffer.readInt();
        this.z = buffer.readInt();
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeVarInt(this.id);
        buffer.writeByte(this.type);
        buffer.writeInt(this.x);
        buffer.writeInt(this.y);
        buffer.writeInt(this.z);
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handleAddGlobalEntity(this);
    }

    public int getId() {
        return this.id;
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public int getZ() {
        return this.z;
    }

    public int getType() {
        return this.type;
    }
}
