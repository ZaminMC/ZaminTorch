package net.minecraft.network.packet.s2c.play;

import java.io.IOException;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.entity.living.effect.StatusEffectInstance;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;

public class EntityRemoveStatusEffectS2CPacket implements Packet<ClientPlayPacketHandler> {
    private int id;
    private int effect;

    public EntityRemoveStatusEffectS2CPacket() {
    }

    public EntityRemoveStatusEffectS2CPacket(int entityId, StatusEffectInstance effect) {
        this.id = entityId;
        this.effect = effect.getId();
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.id = buffer.readVarInt();
        this.effect = buffer.readUnsignedByte();
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeVarInt(this.id);
        buffer.writeByte(this.effect);
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handleEntityRemoveStatusEffect(this);
    }

    public int getId() {
        return this.id;
    }

    public int getEffect() {
        return this.effect;
    }
}
