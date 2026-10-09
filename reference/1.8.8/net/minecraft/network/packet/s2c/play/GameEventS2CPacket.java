package net.minecraft.network.packet.s2c.play;

import java.io.IOException;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;

public class GameEventS2CPacket implements Packet<ClientPlayPacketHandler> {
    public static final String[] EVENT_MESSAGES = new String[]{"tile.bed.notValid"};
    private int event;
    private float data;

    public GameEventS2CPacket() {
    }

    public GameEventS2CPacket(int event, float data) {
        this.event = event;
        this.data = data;
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.event = buffer.readUnsignedByte();
        this.data = buffer.readFloat();
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeByte(this.event);
        buffer.writeFloat(this.data);
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handleGameEvent(this);
    }

    public int getEvent() {
        return this.event;
    }

    public float getData() {
        return this.data;
    }
}
