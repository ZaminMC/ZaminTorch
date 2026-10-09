package net.minecraft.network.packet.s2c.play;

import java.io.IOException;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.text.Text;

public class ChatMessageS2CPacket implements Packet<ClientPlayPacketHandler> {
    private Text message;
    /**
     * The type of message. The possible values are:
     * <br>0: chat
     * <br>1: system
     * <br>2: info
     */
    private byte type;

    public ChatMessageS2CPacket() {
    }

    public ChatMessageS2CPacket(Text message) {
        this(message, (byte)1);
    }

    public ChatMessageS2CPacket(Text message, byte type) {
        this.message = message;
        this.type = type;
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.message = buffer.readText();
        this.type = buffer.readByte();
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeText(this.message);
        buffer.writeByte(this.type);
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handleChatMessage(this);
    }

    public Text getMessage() {
        return this.message;
    }

    public boolean isSystemMessage() {
        return this.type == 1 || this.type == 2;
    }

    public byte getType() {
        return this.type;
    }
}
