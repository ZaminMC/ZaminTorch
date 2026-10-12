package net.minecraft.network.packet.s2c.play;

import java.io.IOException;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.util.math.BlockPos;

public class WorldEventS2CPacket implements Packet<ClientPlayPacketHandler> {
    private int event;
    private BlockPos pos;
    private int data;
    private boolean global;

    public WorldEventS2CPacket() {
    }

    public WorldEventS2CPacket(int event, BlockPos pos, int data, boolean global) {
        this.event = event;
        this.pos = pos;
        this.data = data;
        this.global = global;
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.event = buffer.readInt();
        this.pos = buffer.readBlockPos();
        this.data = buffer.readInt();
        this.global = buffer.readBoolean();
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeInt(this.event);
        buffer.writeBlockPos(this.pos);
        buffer.writeInt(this.data);
        buffer.writeBoolean(this.global);
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handleWorldEvent(this);
    }

    public boolean isGlobal() {
        return this.global;
    }

    public int getEvent() {
        return this.event;
    }

    public int getData() {
        return this.data;
    }

    public BlockPos getPos() {
        return this.pos;
    }
}
