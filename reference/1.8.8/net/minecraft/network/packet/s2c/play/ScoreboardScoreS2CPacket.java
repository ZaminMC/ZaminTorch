package net.minecraft.network.packet.s2c.play;

import java.io.IOException;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardScore;

public class ScoreboardScoreS2CPacket implements Packet<ClientPlayPacketHandler> {
    private String owner = "";
    private String objective = "";
    private int score;
    private ScoreboardScoreS2CPacket.UpdateMode updateMode;

    public ScoreboardScoreS2CPacket() {
    }

    public ScoreboardScoreS2CPacket(ScoreboardScore score) {
        this.owner = score.getOwner();
        this.objective = score.getObjective().getName();
        this.score = score.get();
        this.updateMode = ScoreboardScoreS2CPacket.UpdateMode.CHANGE;
    }

    public ScoreboardScoreS2CPacket(String owner) {
        this.owner = owner;
        this.objective = "";
        this.score = 0;
        this.updateMode = ScoreboardScoreS2CPacket.UpdateMode.REMOVE;
    }

    public ScoreboardScoreS2CPacket(String owner, ScoreboardObjective objective) {
        this.owner = owner;
        this.objective = objective.getName();
        this.score = 0;
        this.updateMode = ScoreboardScoreS2CPacket.UpdateMode.REMOVE;
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.owner = buffer.readString(40);
        this.updateMode = buffer.readEnum(ScoreboardScoreS2CPacket.UpdateMode.class);
        this.objective = buffer.readString(16);
        if (this.updateMode != ScoreboardScoreS2CPacket.UpdateMode.REMOVE) {
            this.score = buffer.readVarInt();
        }
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeString(this.owner);
        buffer.writeEnum(this.updateMode);
        buffer.writeString(this.objective);
        if (this.updateMode != ScoreboardScoreS2CPacket.UpdateMode.REMOVE) {
            buffer.writeVarInt(this.score);
        }
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handleScoreboardScore(this);
    }

    public String getOwner() {
        return this.owner;
    }

    public String getObjective() {
        return this.objective;
    }

    public int getScore() {
        return this.score;
    }

    public ScoreboardScoreS2CPacket.UpdateMode getUpdateMode() {
        return this.updateMode;
    }

    public enum UpdateMode {
        CHANGE,
        REMOVE;
    }
}
