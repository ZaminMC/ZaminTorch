package net.minecraft.network.packet.s2c.play;

import java.io.IOException;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;

public class PlayerXpS2CPacket implements Packet<ClientPlayPacketHandler> {
    private float levelProgress;
    private int xp;
    private int level;

    public PlayerXpS2CPacket() {
    }

    public PlayerXpS2CPacket(float levelProgress, int xp, int level) {
        this.levelProgress = levelProgress;
        this.xp = xp;
        this.level = level;
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.levelProgress = buffer.readFloat();
        this.level = buffer.readVarInt();
        this.xp = buffer.readVarInt();
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeFloat(this.levelProgress);
        buffer.writeVarInt(this.level);
        buffer.writeVarInt(this.xp);
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handlePlayerXp(this);
    }

    public float getLevelProgress() {
        return this.levelProgress;
    }

    public int getXp() {
        return this.xp;
    }

    public int getLevel() {
        return this.level;
    }
}
