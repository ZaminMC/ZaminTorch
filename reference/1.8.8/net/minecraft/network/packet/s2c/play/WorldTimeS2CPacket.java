package net.minecraft.network.packet.s2c.play;

import java.io.IOException;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;

public class WorldTimeS2CPacket implements Packet<ClientPlayPacketHandler> {
    private long time;
    private long timeOfDay;

    public WorldTimeS2CPacket() {
    }

    public WorldTimeS2CPacket(long time, long timeOfDay, boolean doDaylightCycle) {
        this.time = time;
        this.timeOfDay = timeOfDay;
        if (!doDaylightCycle) {
            this.timeOfDay = -this.timeOfDay;
            if (this.timeOfDay == 0L) {
                this.timeOfDay = -1L;
            }
        }
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.time = buffer.readLong();
        this.timeOfDay = buffer.readLong();
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeLong(this.time);
        buffer.writeLong(this.timeOfDay);
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handleWorldTime(this);
    }

    public long getTime() {
        return this.time;
    }

    public long getTimeOfDay() {
        return this.timeOfDay;
    }
}
