package net.minecraft.network.packet.s2c.play;

import java.io.IOException;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.util.math.MathHelper;

public class AddEntityS2CPacket implements Packet<ClientPlayPacketHandler> {
    private int id;
    private int x;
    private int y;
    private int z;
    private int velocityX;
    private int velocityY;
    private int velocityZ;
    private int pitch;
    private int yaw;
    private int type;
    private int data;

    public AddEntityS2CPacket() {
    }

    public AddEntityS2CPacket(Entity entity, int type) {
        this(entity, type, 0);
    }

    public AddEntityS2CPacket(Entity entity, int type, int data) {
        this.id = entity.getNetworkId();
        this.x = MathHelper.floor(entity.x * 32.0);
        this.y = MathHelper.floor(entity.y * 32.0);
        this.z = MathHelper.floor(entity.z * 32.0);
        this.pitch = MathHelper.floor(entity.pitch * 256.0F / 360.0F);
        this.yaw = MathHelper.floor(entity.yaw * 256.0F / 360.0F);
        this.type = type;
        this.data = data;
        if (data > 0) {
            double d0 = entity.velocityX;
            double d1 = entity.velocityY;
            double d2 = entity.velocityZ;
            double d3 = 3.9;
            if (d0 < -d3) {
                d0 = -d3;
            }

            if (d1 < -d3) {
                d1 = -d3;
            }

            if (d2 < -d3) {
                d2 = -d3;
            }

            if (d0 > d3) {
                d0 = d3;
            }

            if (d1 > d3) {
                d1 = d3;
            }

            if (d2 > d3) {
                d2 = d3;
            }

            this.velocityX = (int)(d0 * 8000.0);
            this.velocityY = (int)(d1 * 8000.0);
            this.velocityZ = (int)(d2 * 8000.0);
        }
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.id = buffer.readVarInt();
        this.type = buffer.readByte();
        this.x = buffer.readInt();
        this.y = buffer.readInt();
        this.z = buffer.readInt();
        this.pitch = buffer.readByte();
        this.yaw = buffer.readByte();
        this.data = buffer.readInt();
        if (this.data > 0) {
            this.velocityX = buffer.readShort();
            this.velocityY = buffer.readShort();
            this.velocityZ = buffer.readShort();
        }
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeVarInt(this.id);
        buffer.writeByte(this.type);
        buffer.writeInt(this.x);
        buffer.writeInt(this.y);
        buffer.writeInt(this.z);
        buffer.writeByte(this.pitch);
        buffer.writeByte(this.yaw);
        buffer.writeInt(this.data);
        if (this.data > 0) {
            buffer.writeShort(this.velocityX);
            buffer.writeShort(this.velocityY);
            buffer.writeShort(this.velocityZ);
        }
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handleAddEntity(this);
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

    public int getVelocityX() {
        return this.velocityX;
    }

    public int getVelocityY() {
        return this.velocityY;
    }

    public int getVelocityZ() {
        return this.velocityZ;
    }

    public int getPitch() {
        return this.pitch;
    }

    public int getYaw() {
        return this.yaw;
    }

    public int getType() {
        return this.type;
    }

    public int getData() {
        return this.data;
    }

    public void setX(int x) {
        this.x = x;
    }

    public void setY(int y) {
        this.y = y;
    }

    public void setZ(int z) {
        this.z = z;
    }

    public void setVelocityX(int velocityX) {
        this.velocityX = velocityX;
    }

    public void setVelocityY(int velocityY) {
        this.velocityY = velocityY;
    }

    public void setVelocityZ(int velocityZ) {
        this.velocityZ = velocityZ;
    }

    public void setData(int data) {
        this.data = data;
    }
}
