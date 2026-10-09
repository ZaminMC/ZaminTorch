package net.minecraft.network.packet.s2c.play;

import java.io.IOException;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.world.border.WorldBorder;

public class WorldBorderS2CPacket implements Packet<ClientPlayPacketHandler> {
    private WorldBorderS2CPacket.Type type;
    private int maxSize;
    private double centerX;
    private double centerZ;
    private double sizeLerpTarget;
    private double lerpSize;
    private long lerpTime;
    private int warningTime;
    private int warningBlocks;

    public WorldBorderS2CPacket() {
    }

    public WorldBorderS2CPacket(WorldBorder worldBorder, WorldBorderS2CPacket.Type type) {
        this.type = type;
        this.centerX = worldBorder.getCenterX();
        this.centerZ = worldBorder.getCenterZ();
        this.lerpSize = worldBorder.getLerpSize();
        this.sizeLerpTarget = worldBorder.getSizeLerpTarget();
        this.lerpTime = worldBorder.getLerpTime();
        this.maxSize = worldBorder.getMaxSize();
        this.warningBlocks = worldBorder.getWarningDistance();
        this.warningTime = worldBorder.getWarningTime();
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.type = buffer.readEnum(WorldBorderS2CPacket.Type.class);
        switch (this.type) {
            case SET_SIZE:
                this.sizeLerpTarget = buffer.readDouble();
                break;
            case LERP_SIZE:
                this.lerpSize = buffer.readDouble();
                this.sizeLerpTarget = buffer.readDouble();
                this.lerpTime = buffer.readVarLong();
                break;
            case SET_CENTER:
                this.centerX = buffer.readDouble();
                this.centerZ = buffer.readDouble();
                break;
            case SET_WARNING_BLOCKS:
                this.warningBlocks = buffer.readVarInt();
                break;
            case SET_WARNING_TIME:
                this.warningTime = buffer.readVarInt();
                break;
            case INITIALIZE:
                this.centerX = buffer.readDouble();
                this.centerZ = buffer.readDouble();
                this.lerpSize = buffer.readDouble();
                this.sizeLerpTarget = buffer.readDouble();
                this.lerpTime = buffer.readVarLong();
                this.maxSize = buffer.readVarInt();
                this.warningBlocks = buffer.readVarInt();
                this.warningTime = buffer.readVarInt();
        }
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeEnum(this.type);
        switch (this.type) {
            case SET_SIZE:
                buffer.writeDouble(this.sizeLerpTarget);
                break;
            case LERP_SIZE:
                buffer.writeDouble(this.lerpSize);
                buffer.writeDouble(this.sizeLerpTarget);
                buffer.writeVarLong(this.lerpTime);
                break;
            case SET_CENTER:
                buffer.writeDouble(this.centerX);
                buffer.writeDouble(this.centerZ);
                break;
            case SET_WARNING_BLOCKS:
                buffer.writeVarInt(this.warningBlocks);
                break;
            case SET_WARNING_TIME:
                buffer.writeVarInt(this.warningTime);
                break;
            case INITIALIZE:
                buffer.writeDouble(this.centerX);
                buffer.writeDouble(this.centerZ);
                buffer.writeDouble(this.lerpSize);
                buffer.writeDouble(this.sizeLerpTarget);
                buffer.writeVarLong(this.lerpTime);
                buffer.writeVarInt(this.maxSize);
                buffer.writeVarInt(this.warningBlocks);
                buffer.writeVarInt(this.warningTime);
        }
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handleWorldBorder(this);
    }

    public void apply(WorldBorder worldBorder) {
        switch (this.type) {
            case SET_SIZE:
                worldBorder.setSize(this.sizeLerpTarget);
                break;
            case LERP_SIZE:
                worldBorder.setSize(this.lerpSize, this.sizeLerpTarget, this.lerpTime);
                break;
            case SET_CENTER:
                worldBorder.setCenter(this.centerX, this.centerZ);
                break;
            case SET_WARNING_BLOCKS:
                worldBorder.setWarningDistance(this.warningBlocks);
                break;
            case SET_WARNING_TIME:
                worldBorder.setWarningTime(this.warningTime);
                break;
            case INITIALIZE:
                worldBorder.setCenter(this.centerX, this.centerZ);
                if (this.lerpTime > 0L) {
                    worldBorder.setSize(this.lerpSize, this.sizeLerpTarget, this.lerpTime);
                } else {
                    worldBorder.setSize(this.sizeLerpTarget);
                }

                worldBorder.setMaxSize(this.maxSize);
                worldBorder.setWarningDistance(this.warningBlocks);
                worldBorder.setWarningTime(this.warningTime);
        }
    }

    public enum Type {
        SET_SIZE,
        LERP_SIZE,
        SET_CENTER,
        INITIALIZE,
        SET_WARNING_TIME,
        SET_WARNING_BLOCKS;
    }
}
