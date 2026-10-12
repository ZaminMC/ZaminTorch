package net.minecraft.network.packet.s2c.play;

import java.io.IOException;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.world.Difficulty;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.gen.WorldGeneratorType;

public class PlayerRespawnS2CPacket implements Packet<ClientPlayPacketHandler> {
    private int dimension;
    private Difficulty difficulty;
    private WorldSettings.GameMode gameMode;
    private WorldGeneratorType generatorType;

    public PlayerRespawnS2CPacket() {
    }

    public PlayerRespawnS2CPacket(int dimensionId, Difficulty difficulty, WorldGeneratorType generatorType, WorldSettings.GameMode gameMode) {
        this.dimension = dimensionId;
        this.difficulty = difficulty;
        this.gameMode = gameMode;
        this.generatorType = generatorType;
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handlePlayerRespawn(this);
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.dimension = buffer.readInt();
        this.difficulty = Difficulty.byId(buffer.readUnsignedByte());
        this.gameMode = WorldSettings.GameMode.byId(buffer.readUnsignedByte());
        this.generatorType = WorldGeneratorType.byKey(buffer.readString(16));
        if (this.generatorType == null) {
            this.generatorType = WorldGeneratorType.DEFAULT;
        }
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeInt(this.dimension);
        buffer.writeByte(this.difficulty.getId());
        buffer.writeByte(this.gameMode.getId());
        buffer.writeString(this.generatorType.getKey());
    }

    public int getDimension() {
        return this.dimension;
    }

    public Difficulty getDifficulty() {
        return this.difficulty;
    }

    public WorldSettings.GameMode getGameMode() {
        return this.gameMode;
    }

    public WorldGeneratorType getGeneratorType() {
        return this.generatorType;
    }
}
