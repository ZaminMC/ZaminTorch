package net.minecraft.network.packet.s2c.play;

import java.io.IOException;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;

public class EntityPickupS2CPacket implements Packet<ClientPlayPacketHandler> {
    private int id;
    private int collectorId;

    public EntityPickupS2CPacket() {
    }

    public EntityPickupS2CPacket(int id, int collectorId) {
        this.id = id;
        this.collectorId = collectorId;
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.id = buffer.readVarInt();
        this.collectorId = buffer.readVarInt();
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeVarInt(this.id);
        buffer.writeVarInt(this.collectorId);
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handleEntityPickup(this);
    }

    public int getId() {
        return this.id;
    }

    public int getCollectorId() {
        return this.collectorId;
    }
}
