package net.minecraft.network.packet.s2c.play;

import java.io.IOException;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.world.World;

public class EntityMoveS2CPacket implements Packet<ClientPlayPacketHandler> {
    protected int id;
    protected byte dx;
    protected byte dy;
    protected byte dz;
    protected byte yaw;
    protected byte pitch;
    protected boolean onGround;
    protected boolean hasAngles;

    public EntityMoveS2CPacket() {
    }

    public EntityMoveS2CPacket(int id) {
        this.id = id;
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.id = buffer.readVarInt();
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeVarInt(this.id);
    }

    public void handle(ClientPlayPacketHandler listener) {
        listener.handleEntityMove(this);
    }

    @Override
    public String toString() {
        return "Entity_" + super.toString();
    }

    public Entity getEntity(World world) {
        return world.getEntity(this.id);
    }

    public byte getDx() {
        return this.dx;
    }

    public byte getDy() {
        return this.dy;
    }

    public byte getDz() {
        return this.dz;
    }

    public byte getYaw() {
        return this.yaw;
    }

    public byte getPitch() {
        return this.pitch;
    }

    public boolean hasAngles() {
        return this.hasAngles;
    }

    public boolean getOnGround() {
        return this.onGround;
    }

    public static class Angles extends EntityMoveS2CPacket {
        public Angles() {
            this.hasAngles = true;
        }

        public Angles(int id, byte yaw, byte pitch, boolean onGround) {
            super(id);
            this.yaw = yaw;
            this.pitch = pitch;
            this.hasAngles = true;
            this.onGround = onGround;
        }

        @Override
        public void read(PacketByteBuf buffer) throws IOException {
            super.read(buffer);
            this.yaw = buffer.readByte();
            this.pitch = buffer.readByte();
            this.onGround = buffer.readBoolean();
        }

        @Override
        public void write(PacketByteBuf buffer) throws IOException {
            super.write(buffer);
            buffer.writeByte(this.yaw);
            buffer.writeByte(this.pitch);
            buffer.writeBoolean(this.onGround);
        }
    }

    public static class Position extends EntityMoveS2CPacket {
        public Position() {
        }

        public Position(int id, byte dx, byte dy, byte dz, boolean onGround) {
            super(id);
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
            this.onGround = onGround;
        }

        @Override
        public void read(PacketByteBuf buffer) throws IOException {
            super.read(buffer);
            this.dx = buffer.readByte();
            this.dy = buffer.readByte();
            this.dz = buffer.readByte();
            this.onGround = buffer.readBoolean();
        }

        @Override
        public void write(PacketByteBuf buffer) throws IOException {
            super.write(buffer);
            buffer.writeByte(this.dx);
            buffer.writeByte(this.dy);
            buffer.writeByte(this.dz);
            buffer.writeBoolean(this.onGround);
        }
    }

    public static class PositionAndAngles extends EntityMoveS2CPacket {
        public PositionAndAngles() {
            this.hasAngles = true;
        }

        public PositionAndAngles(int id, byte dx, byte dy, byte dz, byte yaw, byte pitch, boolean onGround) {
            super(id);
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
            this.yaw = yaw;
            this.pitch = pitch;
            this.onGround = onGround;
            this.hasAngles = true;
        }

        @Override
        public void read(PacketByteBuf buffer) throws IOException {
            super.read(buffer);
            this.dx = buffer.readByte();
            this.dy = buffer.readByte();
            this.dz = buffer.readByte();
            this.yaw = buffer.readByte();
            this.pitch = buffer.readByte();
            this.onGround = buffer.readBoolean();
        }

        @Override
        public void write(PacketByteBuf buffer) throws IOException {
            super.write(buffer);
            buffer.writeByte(this.dx);
            buffer.writeByte(this.dy);
            buffer.writeByte(this.dz);
            buffer.writeByte(this.yaw);
            buffer.writeByte(this.pitch);
            buffer.writeBoolean(this.onGround);
        }
    }
}
