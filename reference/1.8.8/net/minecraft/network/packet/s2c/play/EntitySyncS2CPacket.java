package net.minecraft.network.packet.s2c.play;

import java.io.IOException;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.world.World;

public class EntitySyncS2CPacket implements Packet<ClientPlayPacketHandler> {
    private int id;
    private NbtCompound nbt;

    public EntitySyncS2CPacket() {
    }

    public EntitySyncS2CPacket(int id, NbtCompound nbt) {
        this.id = id;
        this.nbt = nbt;
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.id = buffer.readVarInt();
        this.nbt = buffer.readNbtCompound();
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeVarInt(this.id);
        buffer.writeNbtCompound(this.nbt);
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handleEntitySync(this);
    }

    public NbtCompound getNbt() {
        return this.nbt;
    }

    public Entity getEntity(World world) {
        return world.getEntity(this.id);
    }
}
