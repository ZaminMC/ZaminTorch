package net.minecraft.network.packet.c2s.play;

import java.io.IOException;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.handler.ServerPlayPacketHandler;

public class InventoryMenuConfirmC2SPacket implements Packet<ServerPlayPacketHandler> {
    private int menuId;
    private short actionId;
    private boolean accepted;

    public InventoryMenuConfirmC2SPacket() {
    }

    public InventoryMenuConfirmC2SPacket(int menuId, short actionId, boolean accepted) {
        this.menuId = menuId;
        this.actionId = actionId;
        this.accepted = accepted;
    }

    public void handle(ServerPlayPacketHandler serverPlayPacketHandler) {
        serverPlayPacketHandler.handleInventoryMenuConfirm(this);
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.menuId = buffer.readByte();
        this.actionId = buffer.readShort();
        this.accepted = buffer.readByte() != 0;
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeByte(this.menuId);
        buffer.writeShort(this.actionId);
        buffer.writeByte(this.accepted ? 1 : 0);
    }

    public int getMenuId() {
        return this.menuId;
    }

    public short getActionId() {
        return this.actionId;
    }
}
