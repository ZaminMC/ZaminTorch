package net.minecraft.network.packet.c2s.play;

import java.io.IOException;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.handler.ServerPlayPacketHandler;

public class PlayerMoveC2SPacket implements Packet<ServerPlayPacketHandler> {
    protected double x;
    protected double minY;
    protected double z;
    protected float yaw;
    protected float pitch;
    protected boolean onGround;
    protected boolean hasPos;
    protected boolean hasAngles;

    public PlayerMoveC2SPacket() {
    }

    public PlayerMoveC2SPacket(boolean onGround) {
        this.onGround = onGround;
    }

    public void handle(ServerPlayPacketHandler listener) {
        listener.handlePlayerMove(this);
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.onGround = buffer.readUnsignedByte() != 0;
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeByte(this.onGround ? 1 : 0);
    }

    public double getX() {
        return this.x;
    }

    public double getMinY() {
        return this.minY;
    }

    public double getZ() {
        return this.z;
    }

    public float getYaw() {
        return this.yaw;
    }

    public float getPitch() {
        return this.pitch;
    }

    public boolean getOnGround() {
        return this.onGround;
    }

    public boolean hasPos() {
        return this.hasPos;
    }

    public boolean hasAngles() {
        return this.hasAngles;
    }

    public void setHasPos(boolean hasPos) {
        this.hasPos = hasPos;
    }

    public static class Angles extends PlayerMoveC2SPacket {
        public Angles() {
            this.hasAngles = true;
        }

        public Angles(float yaw, float pitch, boolean onGround) {
            this.yaw = yaw;
            this.pitch = pitch;
            this.onGround = onGround;
            this.hasAngles = true;
        }

        @Override
        public void read(PacketByteBuf buffer) throws IOException {
            this.yaw = buffer.readFloat();
            this.pitch = buffer.readFloat();
            super.read(buffer);
        }

        @Override
        public void write(PacketByteBuf buffer) throws IOException {
            buffer.writeFloat(this.yaw);
            buffer.writeFloat(this.pitch);
            super.write(buffer);
        }
    }

    public static class Position extends PlayerMoveC2SPacket {
        public Position() {
            this.hasPos = true;
        }

        public Position(double x, double minY, double z, boolean onGround) {
            this.x = x;
            this.minY = minY;
            this.z = z;
            this.onGround = onGround;
            this.hasPos = true;
        }

        @Override
        public void read(PacketByteBuf buffer) throws IOException {
            this.x = buffer.readDouble();
            this.minY = buffer.readDouble();
            this.z = buffer.readDouble();
            super.read(buffer);
        }

        @Override
        public void write(PacketByteBuf buffer) throws IOException {
            buffer.writeDouble(this.x);
            buffer.writeDouble(this.minY);
            buffer.writeDouble(this.z);
            super.write(buffer);
        }
    }

    public static class PositionAndAngles extends PlayerMoveC2SPacket {
        public PositionAndAngles() {
            this.hasPos = true;
            this.hasAngles = true;
        }

        public PositionAndAngles(double x, double minY, double z, float yaw, float pitch, boolean onGround) {
            this.x = x;
            this.minY = minY;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.onGround = onGround;
            this.hasAngles = true;
            this.hasPos = true;
        }

        @Override
        public void read(PacketByteBuf buffer) throws IOException {
            this.x = buffer.readDouble();
            this.minY = buffer.readDouble();
            this.z = buffer.readDouble();
            this.yaw = buffer.readFloat();
            this.pitch = buffer.readFloat();
            super.read(buffer);
        }

        @Override
        public void write(PacketByteBuf buffer) throws IOException {
            buffer.writeDouble(this.x);
            buffer.writeDouble(this.minY);
            buffer.writeDouble(this.z);
            buffer.writeFloat(this.yaw);
            buffer.writeFloat(this.pitch);
            super.write(buffer);
        }
    }
}
