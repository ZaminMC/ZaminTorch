package net.minecraft.network.packet.s2c.play;

import java.io.IOException;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.util.math.MathHelper;

public class EntityTeleportS2CPacket implements Packet<ClientPlayPacketHandler> {
    private int id;
    private int x;
    private int y;
    private int z;
    private byte yaw;
    private byte pitch;
    private boolean onGround;

    public EntityTeleportS2CPacket() {
    }

    public EntityTeleportS2CPacket(Entity entity) {
        this.id = entity.getNetworkId();
        this.x = MathHelper.floor(entity.x * 32.0);
        this.y = MathHelper.floor(entity.y * 32.0);
        this.z = MathHelper.floor(entity.z * 32.0);
        this.yaw = (byte)(entity.yaw * 256.0F / 360.0F);
        this.pitch = (byte)(entity.pitch * 256.0F / 360.0F);
        this.onGround = entity.onGround;
    }

    public EntityTeleportS2CPacket(int id, int x, int y, int z, byte yaw, byte pitch, boolean onGround) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.onGround = onGround;
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.id = buffer.readVarInt();
        this.x = buffer.readInt();
        this.y = buffer.readInt();
        this.z = buffer.readInt();
        this.yaw = buffer.readByte();
        this.pitch = buffer.readByte();
        this.onGround = buffer.readBoolean();
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeVarInt(this.id);
        buffer.writeInt(this.x);
        buffer.writeInt(this.y);
        buffer.writeInt(this.z);
        buffer.writeByte(this.yaw);
        buffer.writeByte(this.pitch);
        buffer.writeBoolean(this.onGround);
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handleEntityTeleport(this);
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

    public byte getYaw() {
        return this.yaw;
    }

    public byte getPitch() {
        return this.pitch;
    }

    public boolean getOnGround() {
        return this.onGround;
    }
}
