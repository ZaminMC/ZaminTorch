package net.minecraft.network.packet.s2c.play;

import java.io.IOException;
import java.util.List;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.entity.data.SyncedData;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;

public class EntityDataS2CPacket implements Packet<ClientPlayPacketHandler> {
    private int id;
    private List<SyncedData.Entry> dataEntries;

    public EntityDataS2CPacket() {
    }

    public EntityDataS2CPacket(int id, SyncedData syncedData, boolean forceUpdateAll) {
        this.id = id;
        if (forceUpdateAll) {
            this.dataEntries = syncedData.getAll();
        } else {
            this.dataEntries = syncedData.collectDirty();
        }
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.id = buffer.readVarInt();
        this.dataEntries = SyncedData.read(buffer);
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeVarInt(this.id);
        SyncedData.write(this.dataEntries, buffer);
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handleEntityData(this);
    }

    public List<SyncedData.Entry> getDataEntries() {
        return this.dataEntries;
    }

    public int getId() {
        return this.id;
    }
}
