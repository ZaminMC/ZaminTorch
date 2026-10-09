package net.minecraft.network.packet.s2c.play;

import java.io.IOException;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;

public class InventoryMenuDataS2CPacket implements Packet<ClientPlayPacketHandler> {
    private int menuId;
    private int dataId;
    private int value;

    public InventoryMenuDataS2CPacket() {
    }

    public InventoryMenuDataS2CPacket(int menuId, int dataId, int value) {
        this.menuId = menuId;
        this.dataId = dataId;
        this.value = value;
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handleInventoryMenuData(this);
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.menuId = buffer.readUnsignedByte();
        this.dataId = buffer.readShort();
        this.value = buffer.readShort();
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeByte(this.menuId);
        buffer.writeShort(this.dataId);
        buffer.writeShort(this.value);
    }

    public int getMenuId() {
        return this.menuId;
    }

    public int getDataId() {
        return this.dataId;
    }

    public int getValue() {
        return this.value;
    }
}
