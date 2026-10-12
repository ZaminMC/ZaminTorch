package net.minecraft.network.packet.s2c.play;

import java.io.IOException;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.criterion.ScoreboardCriterion;

public class ScoreboardObjectiveS2CPacket implements Packet<ClientPlayPacketHandler> {
    private String name;
    private String displayName;
    private ScoreboardCriterion.RenderType renderType;
    /**
     * The type of update.
     * <br>0: objective added
     * <br>1: objective removed
     * <br>2: display name changed
     */
    private int action;

    public ScoreboardObjectiveS2CPacket() {
    }

    public ScoreboardObjectiveS2CPacket(ScoreboardObjective objective, int action) {
        this.name = objective.getName();
        this.displayName = objective.getDisplayName();
        this.renderType = objective.getCriterion().getRenderType();
        this.action = action;
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.name = buffer.readString(16);
        this.action = buffer.readByte();
        if (this.action == 0 || this.action == 2) {
            this.displayName = buffer.readString(32);
            this.renderType = ScoreboardCriterion.RenderType.byKey(buffer.readString(16));
        }
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeString(this.name);
        buffer.writeByte(this.action);
        if (this.action == 0 || this.action == 2) {
            buffer.writeString(this.displayName);
            buffer.writeString(this.renderType.getKey());
        }
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handleScoreboardObjective(this);
    }

    public String getName() {
        return this.name;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public int getAction() {
        return this.action;
    }

    public ScoreboardCriterion.RenderType getRenderType() {
        return this.renderType;
    }
}
