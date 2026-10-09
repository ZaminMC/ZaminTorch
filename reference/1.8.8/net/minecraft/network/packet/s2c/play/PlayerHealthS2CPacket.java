package net.minecraft.network.packet.s2c.play;

import java.io.IOException;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;

public class PlayerHealthS2CPacket implements Packet<ClientPlayPacketHandler> {
    private float health;
    private int hunger;
    private float saturation;

    public PlayerHealthS2CPacket() {
    }

    public PlayerHealthS2CPacket(float health, int hunger, float saturation) {
        this.health = health;
        this.hunger = hunger;
        this.saturation = saturation;
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.health = buffer.readFloat();
        this.hunger = buffer.readVarInt();
        this.saturation = buffer.readFloat();
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeFloat(this.health);
        buffer.writeVarInt(this.hunger);
        buffer.writeFloat(this.saturation);
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handlePlayerHealth(this);
    }

    public float getHealth() {
        return this.health;
    }

    public int getHunger() {
        return this.hunger;
    }

    public float getSaturation() {
        return this.saturation;
    }
}
