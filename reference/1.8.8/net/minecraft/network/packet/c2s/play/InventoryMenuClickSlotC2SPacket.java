package net.minecraft.network.packet.c2s.play;

import java.io.IOException;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.handler.ServerPlayPacketHandler;

public class InventoryMenuClickSlotC2SPacket implements Packet<ServerPlayPacketHandler> {
    private int menuId;
    private int slot;
    private int clickData;
    private short interactionId;
    private ItemStack item;
    private int action;

    public InventoryMenuClickSlotC2SPacket() {
    }

    public InventoryMenuClickSlotC2SPacket(int menuId, int slotId, int clickData, int action, ItemStack item, short interactionId) {
        this.menuId = menuId;
        this.slot = slotId;
        this.clickData = clickData;
        this.item = item != null ? item.copy() : null;
        this.interactionId = interactionId;
        this.action = action;
    }

    public void handle(ServerPlayPacketHandler serverPlayPacketHandler) {
        serverPlayPacketHandler.handleInventoryMenuClickSlot(this);
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.menuId = buffer.readByte();
        this.slot = buffer.readShort();
        this.clickData = buffer.readByte();
        this.interactionId = buffer.readShort();
        this.action = buffer.readByte();
        this.item = buffer.readItem();
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeByte(this.menuId);
        buffer.writeShort(this.slot);
        buffer.writeByte(this.clickData);
        buffer.writeShort(this.interactionId);
        buffer.writeByte(this.action);
        buffer.writeItem(this.item);
    }

    public int getMenuId() {
        return this.menuId;
    }

    public int getSlot() {
        return this.slot;
    }

    public int getClickData() {
        return this.clickData;
    }

    public short getActionId() {
        return this.interactionId;
    }

    public ItemStack getItem() {
        return this.item;
    }

    public int getAction() {
        return this.action;
    }
}
