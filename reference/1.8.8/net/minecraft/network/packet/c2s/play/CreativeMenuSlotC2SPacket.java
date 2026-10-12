package net.minecraft.network.packet.c2s.play;

import java.io.IOException;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.handler.ServerPlayPacketHandler;

public class CreativeMenuSlotC2SPacket implements Packet<ServerPlayPacketHandler> {
    private int slot;
    private ItemStack item;

    public CreativeMenuSlotC2SPacket() {
    }

    public CreativeMenuSlotC2SPacket(int slot, ItemStack item) {
        this.slot = slot;
        this.item = item != null ? item.copy() : null;
    }

    public void handle(ServerPlayPacketHandler serverPlayPacketHandler) {
        serverPlayPacketHandler.handleCreativeMenuSlot(this);
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.slot = buffer.readShort();
        this.item = buffer.readItem();
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeShort(this.slot);
        buffer.writeItem(this.item);
    }

    public int getSlotId() {
        return this.slot;
    }

    public ItemStack getItem() {
        return this.item;
    }
}
