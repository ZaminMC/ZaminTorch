package net.minecraft.network.packet.s2c.play;

import java.io.IOException;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.text.Text;

public class OpenInventoryMenuS2CPacket implements Packet<ClientPlayPacketHandler> {
    private int menuId;
    private String type;
    private Text displayName;
    private int size;
    private int ownerId;

    public OpenInventoryMenuS2CPacket() {
    }

    public OpenInventoryMenuS2CPacket(int menuId, String menuType, Text displayName) {
        this(menuId, menuType, displayName, 0);
    }

    public OpenInventoryMenuS2CPacket(int menuId, String menuType, Text displayName, int size) {
        this.menuId = menuId;
        this.type = menuType;
        this.displayName = displayName;
        this.size = size;
    }

    public OpenInventoryMenuS2CPacket(int menuId, String menuType, Text displayName, int size, int ownerId) {
        this(menuId, menuType, displayName, size);
        this.ownerId = ownerId;
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handleOpenInventoryMenu(this);
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.menuId = buffer.readUnsignedByte();
        this.type = buffer.readString(32);
        this.displayName = buffer.readText();
        this.size = buffer.readUnsignedByte();
        if (this.type.equals("EntityHorse")) {
            this.ownerId = buffer.readInt();
        }
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeByte(this.menuId);
        buffer.writeString(this.type);
        buffer.writeText(this.displayName);
        buffer.writeByte(this.size);
        if (this.type.equals("EntityHorse")) {
            buffer.writeInt(this.ownerId);
        }
    }

    public int getMenuId() {
        return this.menuId;
    }

    public String getMenuType() {
        return this.type;
    }

    public Text getDisplayName() {
        return this.displayName;
    }

    public int getSize() {
        return this.size;
    }

    public int getOwnerId() {
        return this.ownerId;
    }

    public boolean hasSize() {
        return this.size > 0;
    }
}
