package net.minecraft.network.packet.c2s.play;

import java.io.IOException;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.handler.ServerPlayPacketHandler;

public class PlayerInputC2SPacket implements Packet<ServerPlayPacketHandler> {
    private float sidewaysSpeed;
    private float forwardSpeed;
    private boolean jumping;
    private boolean sneaking;

    public PlayerInputC2SPacket() {
    }

    public PlayerInputC2SPacket(float sidewaysSpeed, float forwardSpeed, boolean jumping, boolean sneaking) {
        this.sidewaysSpeed = sidewaysSpeed;
        this.forwardSpeed = forwardSpeed;
        this.jumping = jumping;
        this.sneaking = sneaking;
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.sidewaysSpeed = buffer.readFloat();
        this.forwardSpeed = buffer.readFloat();
        byte b0 = buffer.readByte();
        this.jumping = (b0 & 1) > 0;
        this.sneaking = (b0 & 2) > 0;
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeFloat(this.sidewaysSpeed);
        buffer.writeFloat(this.forwardSpeed);
        byte b0 = 0;
        if (this.jumping) {
            b0 = (byte)(b0 | 1);
        }

        if (this.sneaking) {
            b0 = (byte)(b0 | 2);
        }

        buffer.writeByte(b0);
    }

    public void handle(ServerPlayPacketHandler serverPlayPacketHandler) {
        serverPlayPacketHandler.handlePlayerInput(this);
    }

    public float getSidewaysSpeed() {
        return this.sidewaysSpeed;
    }

    public float getForwardSpeed() {
        return this.forwardSpeed;
    }

    public boolean getJumping() {
        return this.jumping;
    }

    public boolean getSneaking() {
        return this.sneaking;
    }
}
