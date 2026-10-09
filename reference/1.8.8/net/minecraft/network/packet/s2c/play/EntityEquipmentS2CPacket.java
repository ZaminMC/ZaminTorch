package net.minecraft.network.packet.s2c.play;

import java.io.IOException;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;

public class EntityEquipmentS2CPacket implements Packet<ClientPlayPacketHandler> {
    private int id;
    private int slot;
    private ItemStack item;

    public EntityEquipmentS2CPacket() {
    }

    public EntityEquipmentS2CPacket(int id, int slot, ItemStack item) {
        this.id = id;
        this.slot = slot;
        this.item = item == null ? null : item.copy();
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.id = buffer.readVarInt();
        this.slot = buffer.readShort();
        this.item = buffer.readItem();
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeVarInt(this.id);
        buffer.writeShort(this.slot);
        buffer.writeItem(this.item);
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handleEntityEquipment(this);
    }

    public ItemStack getItem() {
        return this.item;
    }

    public int getId() {
        return this.id;
    }

    public int getEquipmentSlot() {
        return this.slot;
    }
}
