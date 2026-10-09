package net.minecraft.network.packet.s2c.play;

import java.io.IOException;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.text.Text;

public class TabListS2CPacket implements Packet<ClientPlayPacketHandler> {
    private Text header;
    private Text footer;

    public TabListS2CPacket() {
    }

    public TabListS2CPacket(Text header) {
        this.header = header;
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.header = buffer.readText();
        this.footer = buffer.readText();
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeText(this.header);
        buffer.writeText(this.footer);
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handleTabList(this);
    }

    public Text getHeader() {
        return this.header;
    }

    public Text getFooter() {
        return this.footer;
    }
}
