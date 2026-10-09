package net.minecraft.network.packet.s2c.play;

import java.io.IOException;
import java.util.List;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.entity.Entities;
import net.minecraft.entity.data.SyncedData;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.util.math.MathHelper;

public class AddMobS2CPacket implements Packet<ClientPlayPacketHandler> {
    private int id;
    private int type;
    private int x;
    private int y;
    private int z;
    private int velocityX;
    private int velocityY;
    private int velocityZ;
    private byte yaw;
    private byte pitch;
    private byte headYaw;
    private SyncedData syncedData;
    private List<SyncedData.Entry> dataEntries;

    public AddMobS2CPacket() {
    }

    public AddMobS2CPacket(LivingEntity entity) {
        this.id = entity.getNetworkId();
        this.type = (byte)Entities.getId(entity);
        this.x = MathHelper.floor(entity.x * 32.0);
        this.y = MathHelper.floor(entity.y * 32.0);
        this.z = MathHelper.floor(entity.z * 32.0);
        this.yaw = (byte)(entity.yaw * 256.0F / 360.0F);
        this.pitch = (byte)(entity.pitch * 256.0F / 360.0F);
        this.headYaw = (byte)(entity.headYaw * 256.0F / 360.0F);
        double d0 = 3.9;
        double d1 = entity.velocityX;
        double d2 = entity.velocityY;
        double d3 = entity.velocityZ;
        if (d1 < -d0) {
            d1 = -d0;
        }

        if (d2 < -d0) {
            d2 = -d0;
        }

        if (d3 < -d0) {
            d3 = -d0;
        }

        if (d1 > d0) {
            d1 = d0;
        }

        if (d2 > d0) {
            d2 = d0;
        }

        if (d3 > d0) {
            d3 = d0;
        }

        this.velocityX = (int)(d1 * 8000.0);
        this.velocityY = (int)(d2 * 8000.0);
        this.velocityZ = (int)(d3 * 8000.0);
        this.syncedData = entity.getSyncedData();
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.id = buffer.readVarInt();
        this.type = buffer.readByte() & 255;
        this.x = buffer.readInt();
        this.y = buffer.readInt();
        this.z = buffer.readInt();
        this.yaw = buffer.readByte();
        this.pitch = buffer.readByte();
        this.headYaw = buffer.readByte();
        this.velocityX = buffer.readShort();
        this.velocityY = buffer.readShort();
        this.velocityZ = buffer.readShort();
        this.dataEntries = SyncedData.read(buffer);
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeVarInt(this.id);
        buffer.writeByte(this.type & 0xFF);
        buffer.writeInt(this.x);
        buffer.writeInt(this.y);
        buffer.writeInt(this.z);
        buffer.writeByte(this.yaw);
        buffer.writeByte(this.pitch);
        buffer.writeByte(this.headYaw);
        buffer.writeShort(this.velocityX);
        buffer.writeShort(this.velocityY);
        buffer.writeShort(this.velocityZ);
        this.syncedData.write(buffer);
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handleAddMob(this);
    }

    public List<SyncedData.Entry> getDataEntries() {
        if (this.dataEntries == null) {
            this.dataEntries = this.syncedData.getAll();
        }

        return this.dataEntries;
    }

    public int getId() {
        return this.id;
    }

    public int getType() {
        return this.type;
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

    public int getVelocityX() {
        return this.velocityX;
    }

    public int getVelocityY() {
        return this.velocityY;
    }

    public int getVelocityZ() {
        return this.velocityZ;
    }

    public byte getYaw() {
        return this.yaw;
    }

    public byte getPitch() {
        return this.pitch;
    }

    public byte getHeadYaw() {
        return this.headYaw;
    }
}
