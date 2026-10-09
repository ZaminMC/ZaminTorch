package net.minecraft.network.packet.s2c.play;

import java.io.IOException;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.entity.living.effect.StatusEffectInstance;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;

public class EntityStatusEffectS2CPacket implements Packet<ClientPlayPacketHandler> {
    private int id;
    private byte effect;
    private byte amplifier;
    private int duration;
    private byte flags;

    public EntityStatusEffectS2CPacket() {
    }

    public EntityStatusEffectS2CPacket(int entityId, StatusEffectInstance effect) {
        this.id = entityId;
        this.effect = (byte)(effect.getId() & 0xFF);
        this.amplifier = (byte)(effect.getAmplifier() & 0xFF);
        if (effect.getDuration() > 32767) {
            this.duration = 32767;
        } else {
            this.duration = effect.getDuration();
        }

        this.flags = (byte)(effect.hasParticles() ? 1 : 0);
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.id = buffer.readVarInt();
        this.effect = buffer.readByte();
        this.amplifier = buffer.readByte();
        this.duration = buffer.readVarInt();
        this.flags = buffer.readByte();
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeVarInt(this.id);
        buffer.writeByte(this.effect);
        buffer.writeByte(this.amplifier);
        buffer.writeVarInt(this.duration);
        buffer.writeByte(this.flags);
    }

    public boolean isPermanent() {
        return this.duration == 32767;
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handleEntityStatusEffect(this);
    }

    public int getId() {
        return this.id;
    }

    public byte getEffect() {
        return this.effect;
    }

    public byte getAmplifier() {
        return this.amplifier;
    }

    public int getDuration() {
        return this.duration;
    }

    public boolean getParticles() {
        return this.flags != 0;
    }
}
