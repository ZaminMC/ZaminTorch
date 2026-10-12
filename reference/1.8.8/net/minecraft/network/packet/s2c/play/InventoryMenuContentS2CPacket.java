package net.minecraft.network.packet.s2c.play;

import java.io.IOException;
import java.util.List;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;

public class InventoryMenuContentS2CPacket implements Packet<ClientPlayPacketHandler> {
    private int menuId;
    private ItemStack[] items;

    public InventoryMenuContentS2CPacket() {
    }

    public InventoryMenuContentS2CPacket(int menuId, List<ItemStack> items) {
        this.menuId = menuId;
        this.items = new ItemStack[items.size()];

        for (int i = 0; i < this.items.length; i++) {
            ItemStack itemstack = items.get(i);
            this.items[i] = itemstack == null ? null : itemstack.copy();
        }
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.menuId = buffer.readUnsignedByte();
        int i = buffer.readShort();
        this.items = new ItemStack[i];

        for (int j = 0; j < i; j++) {
            this.items[j] = buffer.readItem();
        }
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeByte(this.menuId);
        buffer.writeShort(this.items.length);

        for (ItemStack itemstack : this.items) {
            buffer.writeItem(itemstack);
        }
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handleInventoryMenuContent(this);
    }

    public int getMenuId() {
        return this.menuId;
    }

    public ItemStack[] getItems() {
        return this.items;
    }
}
