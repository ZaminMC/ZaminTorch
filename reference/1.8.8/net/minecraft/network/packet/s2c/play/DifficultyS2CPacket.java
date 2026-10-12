package net.minecraft.network.packet.s2c.play;

import java.io.IOException;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.world.Difficulty;

public class DifficultyS2CPacket implements Packet<ClientPlayPacketHandler> {
    private Difficulty difficulty;
    private boolean locked;

    public DifficultyS2CPacket() {
    }

    public DifficultyS2CPacket(Difficulty difficulty, boolean locked) {
        this.difficulty = difficulty;
        this.locked = locked;
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handleDifficulty(this);
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.difficulty = Difficulty.byId(buffer.readUnsignedByte());
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeByte(this.difficulty.getId());
    }

    public boolean getLocked() {
        return this.locked;
    }

    public Difficulty getDifficulty() {
        return this.difficulty;
    }
}
