package net.minecraft.network.packet.c2s.play;

import java.io.IOException;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.handler.ServerPlayPacketHandler;

public class ClientSettingsC2SPacket implements Packet<ServerPlayPacketHandler> {
    private String language;
    private int viewDistance;
    private PlayerEntity.ChatVisibility chatVisibility;
    private boolean chatColors;
    private int modelParts;

    public ClientSettingsC2SPacket() {
    }

    public ClientSettingsC2SPacket(String language, int viewDistance, PlayerEntity.ChatVisibility chatVisibility, boolean chatColors, int modelParts) {
        this.language = language;
        this.viewDistance = viewDistance;
        this.chatVisibility = chatVisibility;
        this.chatColors = chatColors;
        this.modelParts = modelParts;
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.language = buffer.readString(7);
        this.viewDistance = buffer.readByte();
        this.chatVisibility = PlayerEntity.ChatVisibility.byIndex(buffer.readByte());
        this.chatColors = buffer.readBoolean();
        this.modelParts = buffer.readUnsignedByte();
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeString(this.language);
        buffer.writeByte(this.viewDistance);
        buffer.writeByte(this.chatVisibility.getIndex());
        buffer.writeBoolean(this.chatColors);
        buffer.writeByte(this.modelParts);
    }

    public void handle(ServerPlayPacketHandler serverPlayPacketHandler) {
        serverPlayPacketHandler.handleClientSettings(this);
    }

    public String getLanguage() {
        return this.language;
    }

    public PlayerEntity.ChatVisibility getChatVisibility() {
        return this.chatVisibility;
    }

    public boolean getChatColors() {
        return this.chatColors;
    }

    public int getModelParts() {
        return this.modelParts;
    }
}
