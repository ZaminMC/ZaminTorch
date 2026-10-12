package net.minecraft.network.packet.c2s.play;

import java.io.IOException;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.handler.ServerPlayPacketHandler;

public class MenuClickButtonC2SPacket implements Packet<ServerPlayPacketHandler> {
    private int menuId;
    private int buttonId;

    public MenuClickButtonC2SPacket() {
    }

    public MenuClickButtonC2SPacket(int menuId, int buttonId) {
        this.menuId = menuId;
        this.buttonId = buttonId;
    }

    public void handle(ServerPlayPacketHandler serverPlayPacketHandler) {
        serverPlayPacketHandler.handleMenuClickButton(this);
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.menuId = buffer.readByte();
        this.buttonId = buffer.readByte();
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeByte(this.menuId);
        buffer.writeByte(this.buttonId);
    }

    public int getMenuId() {
        return this.menuId;
    }

    public int getButtonId() {
        return this.buttonId;
    }
}
