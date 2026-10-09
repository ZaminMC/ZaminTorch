package net.minecraft.network.packet.s2c.play;

import java.io.IOException;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.scoreboard.ScoreboardObjective;

public class ScoreboardDisplayS2CPacket implements Packet<ClientPlayPacketHandler> {
    private int slot;
    private String objective;

    public ScoreboardDisplayS2CPacket() {
    }

    public ScoreboardDisplayS2CPacket(int slot, ScoreboardObjective objective) {
        this.slot = slot;
        if (objective == null) {
            this.objective = "";
        } else {
            this.objective = objective.getName();
        }
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.slot = buffer.readByte();
        this.objective = buffer.readString(16);
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeByte(this.slot);
        buffer.writeString(this.objective);
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handleScoreboardDisplay(this);
    }

    public int getSlot() {
        return this.slot;
    }

    public String getObjective() {
        return this.objective;
    }
}
