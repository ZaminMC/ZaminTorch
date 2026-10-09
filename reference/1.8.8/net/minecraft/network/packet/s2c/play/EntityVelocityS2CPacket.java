package net.minecraft.network.packet.s2c.play;

import java.io.IOException;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;

public class EntityVelocityS2CPacket implements Packet<ClientPlayPacketHandler> {
    private int id;
    private int velocityX;
    private int velocityY;
    private int velocityZ;

    public EntityVelocityS2CPacket() {
    }

    public EntityVelocityS2CPacket(Entity entity) {
        this(entity.getNetworkId(), entity.velocityX, entity.velocityY, entity.velocityZ);
    }

    public EntityVelocityS2CPacket(int id, double velocityX, double velocityY, double velocityZ) {
        this.id = id;
        double d0 = 3.9;
        if (velocityX < -d0) {
            velocityX = -d0;
        }

        if (velocityY < -d0) {
            velocityY = -d0;
        }

        if (velocityZ < -d0) {
            velocityZ = -d0;
        }

        if (velocityX > d0) {
            velocityX = d0;
        }

        if (velocityY > d0) {
            velocityY = d0;
        }

        if (velocityZ > d0) {
            velocityZ = d0;
        }

        this.velocityX = (int)(velocityX * 8000.0);
        this.velocityY = (int)(velocityY * 8000.0);
        this.velocityZ = (int)(velocityZ * 8000.0);
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.id = buffer.readVarInt();
        this.velocityX = buffer.readShort();
        this.velocityY = buffer.readShort();
        this.velocityZ = buffer.readShort();
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeVarInt(this.id);
        buffer.writeShort(this.velocityX);
        buffer.writeShort(this.velocityY);
        buffer.writeShort(this.velocityZ);
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handleEntityVelocity(this);
    }

    public int getId() {
        return this.id;
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
}
