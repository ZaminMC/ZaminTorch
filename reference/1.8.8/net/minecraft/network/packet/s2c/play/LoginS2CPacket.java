package net.minecraft.network.packet.s2c.play;

import java.io.IOException;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.world.Difficulty;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.gen.WorldGeneratorType;

public class LoginS2CPacket implements Packet<ClientPlayPacketHandler> {
    private int gameVersion;
    private boolean hardcore;
    private WorldSettings.GameMode gameMode;
    private int dimension;
    private Difficulty difficulty;
    private int maxPlayerCount;
    private WorldGeneratorType generatorType;
    private boolean reducedDebugInfo;

    public LoginS2CPacket() {
    }

    public LoginS2CPacket(
        int entityId,
        WorldSettings.GameMode gameMode,
        boolean hardcore,
        int dimension,
        Difficulty difficulty,
        int maxPlayerCount,
        WorldGeneratorType generatorType,
        boolean reducedDebugInfo
    ) {
        this.gameVersion = entityId;
        this.dimension = dimension;
        this.difficulty = difficulty;
        this.gameMode = gameMode;
        this.maxPlayerCount = maxPlayerCount;
        this.hardcore = hardcore;
        this.generatorType = generatorType;
        this.reducedDebugInfo = reducedDebugInfo;
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.gameVersion = buffer.readInt();
        int i = buffer.readUnsignedByte();
        this.hardcore = (i & 8) == 8;
        i &= -9;
        this.gameMode = WorldSettings.GameMode.byId(i);
        this.dimension = buffer.readByte();
        this.difficulty = Difficulty.byId(buffer.readUnsignedByte());
        this.maxPlayerCount = buffer.readUnsignedByte();
        this.generatorType = WorldGeneratorType.byKey(buffer.readString(16));
        if (this.generatorType == null) {
            this.generatorType = WorldGeneratorType.DEFAULT;
        }

        this.reducedDebugInfo = buffer.readBoolean();
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeInt(this.gameVersion);
        int i = this.gameMode.getId();
        if (this.hardcore) {
            i |= 8;
        }

        buffer.writeByte(i);
        buffer.writeByte(this.dimension);
        buffer.writeByte(this.difficulty.getId());
        buffer.writeByte(this.maxPlayerCount);
        buffer.writeString(this.generatorType.getKey());
        buffer.writeBoolean(this.reducedDebugInfo);
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handleLogin(this);
    }

    public int getEntityId() {
        return this.gameVersion;
    }

    public boolean getHardcore() {
        return this.hardcore;
    }

    public WorldSettings.GameMode getGameMode() {
        return this.gameMode;
    }

    public int getDimension() {
        return this.dimension;
    }

    public Difficulty getDifficulty() {
        return this.difficulty;
    }

    public int getMaxPlayerCount() {
        return this.maxPlayerCount;
    }

    public WorldGeneratorType getGeneratorType() {
        return this.generatorType;
    }

    public boolean getReducedDebugInfo() {
        return this.reducedDebugInfo;
    }
}
