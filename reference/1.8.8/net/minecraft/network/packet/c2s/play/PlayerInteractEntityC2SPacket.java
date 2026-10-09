package net.minecraft.network.packet.c2s.play;

import java.io.IOException;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.handler.ServerPlayPacketHandler;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class PlayerInteractEntityC2SPacket implements Packet<ServerPlayPacketHandler> {
    private int targetId;
    private PlayerInteractEntityC2SPacket.Action action;
    private Vec3d offset;

    public PlayerInteractEntityC2SPacket() {
    }

    public PlayerInteractEntityC2SPacket(Entity target, PlayerInteractEntityC2SPacket.Action action) {
        this.targetId = target.getNetworkId();
        this.action = action;
    }

    public PlayerInteractEntityC2SPacket(Entity target, Vec3d offset) {
        this(target, PlayerInteractEntityC2SPacket.Action.INTERACT_AT);
        this.offset = offset;
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.targetId = buffer.readVarInt();
        this.action = buffer.readEnum(PlayerInteractEntityC2SPacket.Action.class);
        if (this.action == PlayerInteractEntityC2SPacket.Action.INTERACT_AT) {
            this.offset = new Vec3d(buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
        }
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeVarInt(this.targetId);
        buffer.writeEnum(this.action);
        if (this.action == PlayerInteractEntityC2SPacket.Action.INTERACT_AT) {
            buffer.writeFloat((float)this.offset.x);
            buffer.writeFloat((float)this.offset.y);
            buffer.writeFloat((float)this.offset.z);
        }
    }

    public void handle(ServerPlayPacketHandler serverPlayPacketHandler) {
        serverPlayPacketHandler.handleInteractEntity(this);
    }

    public Entity getInteractTarget(World world) {
        return world.getEntity(this.targetId);
    }

    public PlayerInteractEntityC2SPacket.Action getAction() {
        return this.action;
    }

    public Vec3d getOffset() {
        return this.offset;
    }

    public enum Action {
        INTERACT,
        ATTACK,
        INTERACT_AT;
    }
}
