package net.minecraft.network.packet.s2c.play;

import java.io.IOException;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;

public class InventoryMenuConfirmS2CPacket implements Packet<ClientPlayPacketHandler> {
    private int menuId;
    private short interactionId;
    private boolean accepted;

    public InventoryMenuConfirmS2CPacket() {
    }

    public InventoryMenuConfirmS2CPacket(int menuId, short interactionId, boolean accepted) {
        this.menuId = menuId;
        this.interactionId = interactionId;
        this.accepted = accepted;
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handleInventoryMenuConfirm(this);
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.menuId = buffer.readUnsignedByte();
        this.interactionId = buffer.readShort();
        this.accepted = buffer.readBoolean();
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeByte(this.menuId);
        buffer.writeShort(this.interactionId);
        buffer.writeBoolean(this.accepted);
    }

    public int getMenuId() {
        return this.menuId;
    }

    public short getActionId() {
        return this.interactionId;
    }

    public boolean getAccepted() {
        return this.accepted;
    }
}
