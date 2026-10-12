package net.minecraft.network.packet.c2s.play;

import java.io.IOException;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.handler.ServerPlayPacketHandler;

public class PlayerMovementActionC2SPacket implements Packet<ServerPlayPacketHandler> {
    private int id;
    private PlayerMovementActionC2SPacket.Action action;
    private int data;

    public PlayerMovementActionC2SPacket() {
    }

    public PlayerMovementActionC2SPacket(Entity entity, PlayerMovementActionC2SPacket.Action action) {
        this(entity, action, 0);
    }

    public PlayerMovementActionC2SPacket(Entity entity, PlayerMovementActionC2SPacket.Action action, int data) {
        this.id = entity.getNetworkId();
        this.action = action;
        this.data = data;
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.id = buffer.readVarInt();
        this.action = buffer.readEnum(PlayerMovementActionC2SPacket.Action.class);
        this.data = buffer.readVarInt();
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeVarInt(this.id);
        buffer.writeEnum(this.action);
        buffer.writeVarInt(this.data);
    }

    public void handle(ServerPlayPacketHandler serverPlayPacketHandler) {
        serverPlayPacketHandler.handlePlayerMovementAction(this);
    }

    public PlayerMovementActionC2SPacket.Action getAction() {
        return this.action;
    }

    public int getData() {
        return this.data;
    }

    public enum Action {
        START_SNEAKING,
        STOP_SNEAKING,
        STOP_SLEEPING,
        START_SPRINTING,
        STOP_SPRINTING,
        RIDING_JUMP,
        OPEN_HORSE_INVENTORY;
    }
}
