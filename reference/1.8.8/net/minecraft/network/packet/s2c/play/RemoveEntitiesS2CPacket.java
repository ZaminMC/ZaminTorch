package net.minecraft.network.packet.s2c.play;

import java.io.IOException;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;

public class RemoveEntitiesS2CPacket implements Packet<ClientPlayPacketHandler> {
    private int[] ids;

    public RemoveEntitiesS2CPacket() {
    }

    public RemoveEntitiesS2CPacket(int... ids) {
        this.ids = ids;
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.ids = new int[buffer.readVarInt()];

        for (int i = 0; i < this.ids.length; i++) {
            this.ids[i] = buffer.readVarInt();
        }
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeVarInt(this.ids.length);

        for (int i = 0; i < this.ids.length; i++) {
            buffer.writeVarInt(this.ids[i]);
        }
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handleRemoveEntities(this);
    }

    public int[] getIds() {
        return this.ids;
    }
}
