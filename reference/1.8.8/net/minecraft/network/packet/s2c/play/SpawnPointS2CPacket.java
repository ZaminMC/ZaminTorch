package net.minecraft.network.packet.s2c.play;

import java.io.IOException;
import net.minecraft.client.network.handler.ClientPlayPacketHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.util.math.BlockPos;

public class SpawnPointS2CPacket implements Packet<ClientPlayPacketHandler> {
    private BlockPos pos;

    public SpawnPointS2CPacket() {
    }

    public SpawnPointS2CPacket(BlockPos pos) {
        this.pos = pos;
    }

    @Override
    public void read(PacketByteBuf buffer) throws IOException {
        this.pos = buffer.readBlockPos();
    }

    @Override
    public void write(PacketByteBuf buffer) throws IOException {
        buffer.writeBlockPos(this.pos);
    }

    public void handle(ClientPlayPacketHandler clientPlayPacketHandler) {
        clientPlayPacketHandler.handleSpawnPoint(this);
    }

    public BlockPos getPos() {
        return this.pos;
    }
}
