package net.minecraft.network.packet.s2c.play;

import java.io.IOException;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;

public class InventoryMenuSlotContentS2CPacket implements Packet<ClientPlayPacketHandler> {
    private int menuId;
    private int slot;
    private ItemStack item;

    public InventoryMenuSlotContentS2CPacket() {
    }

    public InventoryMenuSlotContentS2CPacket(int menuId, int slot, ItemStack item) {
        this.menuId = menuId;
        this.slot = slot;
        this.item = item == null ? null : item.copy();
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handleInventoryMenuSlotContent(this);
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.menuId = buffer.readByte();
        this.slot = buffer.readShort();
        this.item = buffer.readItem();
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeByte(this.menuId);
        buffer.writeShort(this.slot);
        buffer.writeItem(this.item);
    }

    public int getMenuId() {
        return this.menuId;
    }

    public int getSlotId() {
        return this.slot;
    }

    public ItemStack getItem() {
        return this.item;
    }
}
