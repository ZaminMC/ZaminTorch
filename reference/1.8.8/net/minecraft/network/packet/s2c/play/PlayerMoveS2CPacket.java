package net.minecraft.network.packet.s2c.play;

import java.io.IOException;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;

public class PlayerMoveS2CPacket implements Packet<ClientPlayPacketHandler> {
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;
    private Set<PlayerMoveS2CPacket.Argument> relativeArgs;

    public PlayerMoveS2CPacket() {
    }

    public PlayerMoveS2CPacket(double x, double y, double z, float yaw, float pitch, Set<PlayerMoveS2CPacket.Argument> relativeArgs) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.relativeArgs = relativeArgs;
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.x = buffer.readDouble();
        this.y = buffer.readDouble();
        this.z = buffer.readDouble();
        this.yaw = buffer.readFloat();
        this.pitch = buffer.readFloat();
        this.relativeArgs = PlayerMoveS2CPacket.Argument.byFlags(buffer.readUnsignedByte());
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeDouble(this.x);
        buffer.writeDouble(this.y);
        buffer.writeDouble(this.z);
        buffer.writeFloat(this.yaw);
        buffer.writeFloat(this.pitch);
        buffer.writeByte(PlayerMoveS2CPacket.Argument.getFlags(this.relativeArgs));
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handlePlayerMove(this);
    }

    public double getX() {
        return this.x;
    }

    public double getY() {
        return this.y;
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

    public Set<PlayerMoveS2CPacket.Argument> getRelativeArgs() {
        return this.relativeArgs;
    }

    public enum Argument {
        X(0),
        Y(1),
        Z(2),
        YAW(3),
        PITCH(4);

        private int index;

        Argument(int index) {
            this.index = index;
        }

        private int getFlag() {
            return 1 << this.index;
        }

        private boolean isSet(int flags) {
            return (flags & this.getFlag()) == this.getFlag();
        }

        public static Set<PlayerMoveS2CPacket.Argument> byFlags(int flags) {
            Set<PlayerMoveS2CPacket.Argument> set = EnumSet.noneOf(PlayerMoveS2CPacket.Argument.class);

            for (PlayerMoveS2CPacket.Argument playermoves2cpacket$argument : values()) {
                if (playermoves2cpacket$argument.isSet(flags)) {
                    set.add(playermoves2cpacket$argument);
                }
            }

            return set;
        }

        public static int getFlags(Set<PlayerMoveS2CPacket.Argument> args) {
            int i = 0;

            for (PlayerMoveS2CPacket.Argument playermoves2cpacket$argument : args) {
                i |= playermoves2cpacket$argument.getFlag();
            }

            return i;
        }
    }
}
